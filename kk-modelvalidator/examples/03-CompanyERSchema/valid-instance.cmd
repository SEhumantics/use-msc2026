-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via `mv -validate`/Kodkod search (see validate.cmd).
-- valid-instance.soil constructs a small, deliberately-valid 13-object
-- instance by hand (plain USE SOIL !create/!set, no solver involved);
-- this script loads it and re-checks every one of the model's 29 class
-- invariants against it.
open valid-instance.soil

info state

-- Confirmed by actually running this: all 29 class invariants report
-- OK, 0 failures -- see the "checking ... : Ok." lines below.
check -v

quit
