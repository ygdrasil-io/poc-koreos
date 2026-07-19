#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: AppKit runtime tests require macOS (Darwin)." >&2
    exit 1
fi

if [[ ! -x /usr/bin/perl ]]; then
    echo "Missing host prerequisite: /usr/bin/perl is required for the external timeout." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

run_with_timeout() {
    local timeout_seconds="$1"
    shift
    /usr/bin/perl -MPOSIX=setpgid -e '
        my $seconds = shift @ARGV;
        my $pid = fork();
        die "fork failed: $!\n" unless defined $pid;
        if ($pid == 0) {
            POSIX::setpgid(0, 0);
            exec @ARGV;
            die "exec failed: $!\n";
        }
        local $SIG{ALRM} = sub {
            kill "KILL", -$pid;
            waitpid($pid, 0);
            exit 124;
        };
        alarm $seconds;
        waitpid($pid, 0);
        alarm 0;
        my $status = $?;
        exit(($status & 127) ? 128 + ($status & 127) : $status >> 8);
    ' "$timeout_seconds" "$@"
}

cd "$REPO_ROOT"
run_with_timeout 600 \
    ./gradlew :kadre-appkit:jvmTest \
    --no-daemon --stacktrace --console=plain
