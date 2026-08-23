-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly one invariant instance (see invalid-instance.soil's header for
-- exactly which Field and why -- and for why an in-range duplicate does
-- NOT give a single-invariant violation in a filled Sudoku grid, unlike
-- GraphColoring's minimal-edge trick). Load through the shell's `open'
-- command, then ask USE's own `check -v' to confirm it actually catches
-- it.
open invalid-instance.soil

info state

-- Confirmed by actually running this: `check -v' reports allowedValue
-- violated (Field6 alone; USE checks a class invariant as one
-- Field.allInstances->forAll statement, so this counts as a single
-- failing invariant) and all other 17 invariants OK, finishing with
-- "checked 18 invariants in ...s, 1 failure.".
check -v

quit
