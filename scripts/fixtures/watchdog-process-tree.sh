#!/usr/bin/env bash
set -euo pipefail

role="$1"
state_dir="$2"

record_signal() {
    local signal_name="$1"

    printf '%s\n' "$signal_name" > "$state_dir/$role-signal"
    exit 0
}

trap 'record_signal HUP' HUP
trap 'record_signal INT' INT
trap 'record_signal TERM' TERM

printf '%s\n' "$$" > "$state_dir/$role-pid"
if [[ "$role" == "leader" ]]; then
    printf '%s\n' "$PPID" > "$state_dir/wrapper-pid"
    /usr/bin/perl -e '
        $SIG{HUP} = "DEFAULT";
        $SIG{INT} = "DEFAULT";
        $SIG{TERM} = "DEFAULT";
        exec @ARGV;
        die "exec failed: $!\n";
    ' "$0" member "$state_dir" &
fi

while :; do
    :
done
