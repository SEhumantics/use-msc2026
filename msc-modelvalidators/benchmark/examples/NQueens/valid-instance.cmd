-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via mv -validate/Kodkod search (see validate.cmd). Load
-- valid-instance.soil's plain USE SOIL statements (!create/!set/!insert,
-- no solver involved) through the shell's `open' command, then ask USE's
-- own `check -v' to verify all three invariants against that hand-built,
-- independently-computed 8-queens solution.
open valid-instance.soil

info state

-- Confirmed by actually running this: all three invariants active in this
-- model (distinctRowIdx, distinctColIdx, noAttack) report OK across all 8
-- Rows/Cols/Queens, and `check -v' finishes with "checking invariants
-- took ... checked 3 invariants, 0 failures.".
check -v

quit
