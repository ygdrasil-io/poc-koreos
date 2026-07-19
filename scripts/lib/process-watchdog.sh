#!/usr/bin/env bash

# Runs one command in its own process group and preserves its terminal status.
run_with_timeout() {
    local timeout_seconds="$1"
    shift
    /usr/bin/perl -MPOSIX=setpgid -MErrno=EINTR,EACCES,EPERM,ESRCH -e '
        use strict;
        use warnings;

        my $seconds = shift @ARGV;
        die "timeout must be a positive integer\n"
            unless defined($seconds) && $seconds =~ /^[1-9][0-9]*$/;
        die "missing command\n" unless @ARGV;

        my $pid;
        my $group_ready = 0;
        my $pending_group_signal;
        my $terminal_status;

        sub request_group_signal {
            my ($signal_name, $status) = @_;
            $terminal_status = $status unless defined $terminal_status;
            $pending_group_signal = $signal_name;
            kill $signal_name, -$pid if defined($pid) && $pid > 0 && $group_ready;
        }

        local $SIG{HUP} = sub { request_group_signal("HUP", 129) };
        local $SIG{INT} = sub { request_group_signal("INT", 130) };
        local $SIG{TERM} = sub { request_group_signal("TERM", 143) };
        local $SIG{ALRM} = sub { request_group_signal("KILL", 124) };

        pipe(my $ready_reader, my $ready_writer) or die "pipe failed: $!\n";
        $pid = fork();
        die "fork failed: $!\n" unless defined $pid;
        if ($pid == 0) {
            close $ready_reader;
            $SIG{HUP} = "DEFAULT";
            $SIG{INT} = "DEFAULT";
            $SIG{TERM} = "DEFAULT";
            $SIG{ALRM} = "DEFAULT";
            POSIX::setpgid(0, 0);
            die "child setpgid failed: $!\n" if POSIX::getpgrp() != $$;
            syswrite($ready_writer, "1") == 1 or die "readiness write failed: $!\n";
            close $ready_writer;
            exec @ARGV;
            die "exec failed: $!\n";
        }
        close $ready_writer;

        while (1) {
            my $result = POSIX::setpgid($pid, $pid);
            last if defined $result;
            next if $! == EINTR;
            last if $! == EACCES || $! == EPERM || $! == ESRCH;
            die "parent setpgid failed: $!\n";
        }

        my $ready = "";
        while (length($ready) < 1) {
            my $read = sysread($ready_reader, $ready, 1, length($ready));
            next if !defined($read) && $! == EINTR;
            die "readiness read failed: $!\n" unless defined $read;
            last if $read == 0;
        }
        close $ready_reader;
        $group_ready = $ready eq "1";

        if (!$group_ready) {
            my $waited;
            do {
                $waited = waitpid($pid, 0);
            } while ($waited < 0 && $! == EINTR);
            die "waitpid failed before readiness: $!\n" if $waited < 0;
            my $status = $?;
            exit(
                defined($terminal_status)
                    ? $terminal_status
                    : (($status & 127) ? 128 + ($status & 127) : $status >> 8)
            );
        }

        if (defined $pending_group_signal) {
            kill $pending_group_signal, -$pid;
        }
        alarm $seconds;

        my $waited;
        while (1) {
            $waited = waitpid($pid, 0);
            last if $waited == $pid;
            next if $waited < 0 && $! == EINTR;
            die "waitpid failed: $!\n";
        }
        alarm 0;
        my $status = $?;
        exit(
            defined($terminal_status)
                ? $terminal_status
                : (($status & 127) ? 128 + ($status & 127) : $status >> 8)
        );
    ' "$timeout_seconds" "$@"
}
