package KadreWatchdogChildSetupStall;

use strict;
use warnings;
use POSIX ();

my $original_setpgid = \&POSIX::setpgid;

{
    no warnings "redefine";
    *POSIX::setpgid = sub {
        my ($pid, $group) = @_;
        my $state_file = $ENV{KADRE_WATCHDOG_STALLED_SETUP_STATE};
        if ($pid == 0 && $group == 0 && defined($state_file) && $state_file ne "") {
            open(my $state, ">", $state_file) or die "open $state_file failed: $!\n";
            print {$state} "$$ ", getppid(), "\n";
            close($state) or die "close $state_file failed: $!\n";

            # Private test fixture safety cap: never stall longer than four seconds.
            local $SIG{ALRM} = sub { exit 97 };
            alarm 4;
            while (1) {
                # Intentionally remain alive before the child readiness write.
            }
        }
        return $original_setpgid->(@_);
    };
}

1;
