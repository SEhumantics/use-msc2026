-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly one invariant instance (see invalid-instance.soil's header for
-- exactly which two queens were changed and which pair ends up attacking).
-- Load through the shell's `open' command, then ask USE's own `check -v'
-- to confirm it actually catches it.
open invalid-instance.soil

info state

-- Confirmed by actually running this: `check -v' reports noAttack
-- violated (USE checks a class invariant as one Queen.allInstances->forAll
-- statement, so the queen5/queen6 diagonal collision counts as a single
-- failing invariant, not two) and distinctRowIdx/distinctColIdx both OK,
-- finishing with "checked 3 invariants in ...s, 1 failure.".
check -v

quit
