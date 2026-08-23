-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly one invariant instance (see invalid-instance.soil's header for
-- exactly which edge and why). Load through the shell's `open' command,
-- then ask USE's own `check -v' to confirm it actually catches it.
open invalid-instance.soil

info state

-- Confirmed by actually running this: `check -v' reports properColoring
-- violated (USE checks a class invariant as one Region.allInstances->forAll
-- statement, so the R37/R38 collision counts as a single failing
-- invariant, not two) and noSelfLoop OK, finishing with "checked 2
-- invariants in ...s, 1 failure.".
check -v

quit
