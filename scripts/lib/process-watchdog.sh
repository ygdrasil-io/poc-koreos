#!/usr/bin/env bash

# Runs one command in its own process group and preserves its terminal status.
run_with_timeout() {
    local timeout_seconds="$1"
    shift
    /usr/bin/perl -MPOSIX=setpgid -MErrno=EINTR,EACCES,EPERM,ESRCH -e '
        use strict;
        use warnings;
        use Time::HiRes ();

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
            if (defined($pid) && $pid > 0) {
                kill $signal_name, $group_ready ? -$pid : $pid;
            }
        }

        sub wait_for_group_exit {
            my ($group, $seconds) = @_;
            my $deadline = Time::HiRes::time() + $seconds;
            while (kill 0, -$group) {
                return 0 if Time::HiRes::time() >= $deadline;
                select undef, undef, undef, 0.01;
            }
            return 1;
        }

        sub extinguish_group {
            my ($group) = @_;

            # Let descendants that handled the terminal signal finish first.
            return if wait_for_group_exit($group, 0.1);

            my $deadline = Time::HiRes::time() + 5;
            while (kill 0, -$group) {
                kill "KILL", -$group;
                last if Time::HiRes::time() >= $deadline;
                select undef, undef, undef, 0.01;
            }
            warn "process group $group survived final KILL\n" if kill 0, -$group;
        }

        local $SIG{HUP} = sub { request_group_signal("HUP", 129) };
        local $SIG{INT} = sub { request_group_signal("INT", 130) };
        local $SIG{TERM} = sub { request_group_signal("TERM", 143) };
        local $SIG{ALRM} = sub { request_group_signal("KILL", 124) };

        pipe(my $gate_reader, my $gate_writer) or die "pipe failed: $!\n";
        $pid = fork();
        die "fork failed: $!\n" unless defined $pid;
        if ($pid == 0) {
            close $gate_writer;
            $SIG{HUP} = "DEFAULT";
            $SIG{INT} = "DEFAULT";
            $SIG{TERM} = "DEFAULT";
            $SIG{ALRM} = "DEFAULT";

            my $release = "";
            while (length($release) < 1) {
                my $read = sysread($gate_reader, $release, 1, length($release));
                next if !defined($read) && $! == EINTR;
                die "gate read failed: $!\n" unless defined $read;
                die "parent closed gate before release\n" if $read == 0;
            }
            close $gate_reader;
            POSIX::setpgid(0, 0);
            die "child setpgid failed: $!\n" if POSIX::getpgrp() != $$;
            exec @ARGV;
            die "exec failed: $!\n";
        }
        close $gate_reader;
        alarm $seconds;

        while (1) {
            my $result = POSIX::setpgid($pid, $pid);
            if (defined $result) {
                $group_ready = 1;
                last;
            }
            next if $! == EINTR;
            last if $! == ESRCH;
            die "parent setpgid failed: $!\n";
        }

        if ($group_ready && defined $pending_group_signal) {
            kill $pending_group_signal, -$pid;
        }

        local $SIG{PIPE} = "IGNORE";
        if ($group_ready) {
            my $written;
            do {
                $written = syswrite($gate_writer, "1");
            } while (!defined($written) && $! == EINTR);
        }
        close $gate_writer;

        my $waited;
        while (1) {
            $waited = waitpid($pid, 0);
            last if $waited == $pid;
            next if $waited < 0 && $! == EINTR;
            die "waitpid failed: $!\n";
        }
        my $status = $?;
        alarm 0;
        extinguish_group($pid)
            if defined($terminal_status) && $group_ready;
        exit(
            defined($terminal_status)
                ? $terminal_status
                : (($status & 127) ? 128 + ($status & 127) : $status >> 8)
        );
    ' "$timeout_seconds" "$@"
}
