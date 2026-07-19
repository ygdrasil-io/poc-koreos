#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly TARGET_TIMEOUT_SECONDS=600

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

case "$(uname -s)" in
    Darwin | Linux) ;;
    *) fail "browser tests support only macOS and Linux hosts" ;;
esac

[[ -x /usr/bin/perl ]] || fail "/usr/bin/perl is required by the process watchdog and XML verifier"
/usr/bin/perl -MXML::Parser -e 1 >/dev/null 2>&1 ||
    fail "Perl XML::Parser is required for strict browser-test XML verification"

find_chrome() {
    local name
    local candidate
    local mac_candidate

    if [[ -n "${CHROME_BIN:-}" ]]; then
        [[ -x "$CHROME_BIN" ]] || fail "CHROME_BIN is not executable: $CHROME_BIN"
        return
    fi

    for name in google-chrome-stable google-chrome chromium chromium-browser; do
        if candidate="$(command -v "$name" 2>/dev/null)"; then
            CHROME_BIN="$candidate"
            return
        fi
    done

    for mac_candidate in \
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "/Applications/Chromium.app/Contents/MacOS/Chromium"
    do
        if [[ -x "$mac_candidate" ]]; then
            CHROME_BIN="$mac_candidate"
            return
        fi
    done

    fail "Chrome or Chromium was not found; set CHROME_BIN to an executable browser"
}

clear_target_xml() {
    local result_dir="$1"
    local files=()

    if [[ -d "$result_dir" ]]; then
        shopt -s nullglob
        files=("$result_dir"/TEST-*.xml)
        shopt -u nullglob
        if (( ${#files[@]} > 0 )); then
            rm -f -- "${files[@]}"
        fi
    fi
}

verify_target_xml() {
    local label="$1"
    local result_dir="$2"
    local files=()
    local summary
    local suites
    local tests
    local skipped
    local failures
    local errors

    shopt -s nullglob
    files=("$result_dir"/TEST-*.xml)
    shopt -u nullglob
    (( ${#files[@]} > 0 )) || fail "$label produced no TEST-*.xml files"

    if ! summary="$(/usr/bin/perl -MXML::Parser - "${files[@]}" <<'PERL'
use strict;
use warnings;

my %total = map { $_ => 0 } qw(tests skipped failures errors);
my $suite_count = 0;

for my $file (@ARGV) {
    my $depth = 0;
    my $root_seen = 0;
    my %declared;
    my %observed = map { $_ => 0 } qw(tests skipped failures errors);

    my $parser = XML::Parser->new(
        Handlers => {
            Start => sub {
                my ($expat, $element, %attributes) = @_;

                if ($depth == 0) {
                    die "root element is not testsuite in $file\n" unless $element eq 'testsuite';
                    die "duplicate testsuite root in $file\n" if $root_seen;
                    $root_seen = 1;
                    for my $name (qw(tests skipped failures errors)) {
                        die "missing or invalid $name attribute in $file\n"
                            unless exists($attributes{$name}) && $attributes{$name} =~ /\A[0-9]+\z/;
                        $declared{$name} = 0 + $attributes{$name};
                    }
                } elsif ($element eq 'testsuite') {
                    die "nested testsuite element in $file\n";
                }

                $observed{tests}++ if $element eq 'testcase';
                $observed{skipped}++ if $element eq 'skipped';
                $observed{failures}++ if $element eq 'failure';
                $observed{errors}++ if $element eq 'error';
                $depth++;
            },
            End => sub {
                $depth--;
            },
        },
    );

    eval { $parser->parsefile($file); 1 }
        or die "invalid XML in $file: $@";
    die "missing testsuite root in $file\n" unless $root_seen;
    die "unbalanced XML depth in $file\n" unless $depth == 0;

    for my $name (qw(tests skipped failures errors)) {
        die "$name attribute/count mismatch in $file: declared=$declared{$name} observed=$observed{$name}\n"
            unless $declared{$name} == $observed{$name};
        $total{$name} += $declared{$name};
    }
    $suite_count++;
}

print join(' ', $suite_count, @total{qw(tests skipped failures errors)}), "\n";
PERL
    )"; then
        fail "$label XML verification could not be completed"
    fi

    IFS=' ' read -r suites tests skipped failures errors <<< "$summary"
    [[ "$suites" =~ ^[1-9][0-9]*$ ]] || fail "$label has an invalid suite count: $suites"
    [[ "$tests" =~ ^[1-9][0-9]*$ ]] || fail "$label ran zero tests"
    [[ "$skipped" == "0" ]] || fail "$label skipped $skipped tests"
    [[ "$failures" == "0" ]] || fail "$label reported $failures failures"
    [[ "$errors" == "0" ]] || fail "$label reported $errors errors"

    VERIFIED_SUITE_COUNT="$suites"
    VERIFIED_TEST_COUNT="$tests"
    echo "$label: suites=$suites tests=$tests skipped=$skipped failures=$failures errors=$errors"
}

run_browser_target() {
    local label="$1"
    local task="$2"
    local result_dir="$3"
    local status

    clear_target_xml "$result_dir"
    echo "Running $label with CHROME_BIN=$CHROME_BIN (timeout ${TARGET_TIMEOUT_SECONDS}s)"

    set +e
    run_with_timeout "$TARGET_TIMEOUT_SECONDS" \
        ./gradlew "$task" --no-daemon --stacktrace --console=plain
    status=$?
    set -e

    if [[ "$status" -eq 124 ]]; then
        fail "$label exceeded the ${TARGET_TIMEOUT_SECONDS}s external timeout"
    fi
    [[ "$status" -eq 0 ]] || fail "$label command exited with status $status"

    verify_target_xml "$label" "$result_dir"
}

find_chrome
export CHROME_BIN
echo "Selected browser: $CHROME_BIN"

source "$SCRIPT_DIR/lib/process-watchdog.sh"
cd "$REPO_ROOT"

VERIFIED_SUITE_COUNT=0
VERIFIED_TEST_COUNT=0
run_browser_target \
    "JS browser tests" \
    ":kadre-web-common:jsBrowserTest" \
    "$REPO_ROOT/kadre-web-common/build/test-results/jsBrowserTest"
js_suite_count="$VERIFIED_SUITE_COUNT"
js_test_count="$VERIFIED_TEST_COUNT"

run_browser_target \
    "Wasm browser tests" \
    ":kadre-web-common:wasmJsBrowserTest" \
    "$REPO_ROOT/kadre-web-common/build/test-results/wasmJsBrowserTest"
wasm_suite_count="$VERIFIED_SUITE_COUNT"
wasm_test_count="$VERIFIED_TEST_COUNT"

[[ "$js_suite_count" -eq "$wasm_suite_count" ]] ||
    fail "JS/Wasm suite-count mismatch: JS=$js_suite_count Wasm=$wasm_suite_count"
[[ "$js_test_count" -eq "$wasm_test_count" ]] ||
    fail "JS/Wasm test-count mismatch: JS=$js_test_count Wasm=$wasm_test_count"

echo "PASS: JS/Wasm browser parity verified with $js_test_count tests per runtime"
