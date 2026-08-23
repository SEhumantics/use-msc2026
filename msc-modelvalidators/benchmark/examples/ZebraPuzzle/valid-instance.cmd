-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via mv -validate/Kodkod search (see validate.cmd). Load
-- valid-instance.soil's plain USE SOIL statements (!create/!set, no
-- solver involved) through the shell's `open' command, then ask USE's own
-- `check -v' to verify the independently-derived Zebra Puzzle solution
-- against every invariant.
open valid-instance.soil

info state

-- Confirmed by actually running this: all 22 invariants active in this
-- model (PositionRange, the 6 DistinctXxx bijection invariants, the 14
-- original clues Clue02..Clue15, and the derived corollary Clue16) report
-- OK, and `check -v' finishes with "checking invariants took ... checked
-- 22 invariants, 0 failures.".
check -v

quit
