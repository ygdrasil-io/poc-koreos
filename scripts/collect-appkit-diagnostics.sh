#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" != "1" ]]; then
    echo "usage: $0 <output-directory>" >&2
    exit 64
fi

OUTPUT_DIRECTORY="$1"
mkdir -p "$OUTPUT_DIRECTORY"

{
    echo "== host =="
    uname -a
    sw_vers
    uname -m

    echo "== java =="
    java -version

    echo "== process =="
    id
    ps -p "$$" -o pid=,ppid=,user=,tty=,command=

    echo "== graphical session =="
    if launchctl print "gui/$(id -u)" | sed -n '1,8p'; then
        echo "launchctl gui session: available"
    else
        echo "launchctl gui session: unavailable"
    fi
    if pgrep -fl WindowServer; then
        echo "WindowServer: available"
    else
        echo "WindowServer: unavailable"
    fi
} > "$OUTPUT_DIRECTORY/environment.txt" 2>&1
