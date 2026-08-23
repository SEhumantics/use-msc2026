-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via mv -validate/Kodkod search (see validate.cmd). Load
-- valid-instance.soil's plain USE SOIL statements (!create/!set/!insert,
-- no solver involved) through the shell's `open' command, then ask USE's
-- own `check -v' to verify both invariants against that hand-built,
-- independently-computed 3-coloring.
open valid-instance.soil

info state

-- Confirmed by actually running this: both invariants active in this
-- model (properColoring, noSelfLoop) report OK across all 40 Regions, and
-- `check -v' finishes with "checking invariants took ... checked 2
-- invariants, 0 failures.".
check -v

quit
