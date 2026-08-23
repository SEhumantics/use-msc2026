-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via mv -validate/Kodkod search (see validate.cmd). Load
-- valid-instance.soil's plain USE SOIL statements (!create/!set/!insert,
-- no solver involved) through the shell's `open' command, then ask USE's
-- own `check -v' to verify all 18 invariants against that hand-built,
-- independently-computed, fully-solved 6x6 board.
open valid-instance.soil

info state

-- Confirmed by actually running this: all 18 invariants active in this
-- model (allowedValue, uniqueValuesRow, uniqueValuesColumn,
-- uniqueValuesSquare, allowedColumnIndex, columnIndexUnique,
-- allowedRowIndex, rowIndexUnique, givenR1C3 .. givenR6C1) report OK,
-- and `check -v' finishes with "checking invariants took ... checked 18
-- invariants, 0 failures.".
check -v

quit
