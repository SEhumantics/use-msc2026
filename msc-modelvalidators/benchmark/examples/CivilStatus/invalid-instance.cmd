-- Reads invalid-instance.soil, which deliberately violates exactly one
-- invariant -- Person::nameIsUnique (two distinct Person objects, P1 and
-- P2, are both given the "name" attribute value 'Ann'; see that file's own
-- header comment for why every other invariant still holds) -- and checks
-- every invariant against it. Confirms 'check -v' reports FAILED for
-- nameIsUnique specifically and OK for the other invariants that still
-- apply.

open invalid-instance.soil

-- `info state' is the evidence that the .soil above actually loaded: USE's -nogui
-- shell does not abort on a failed `open', so without a non-empty object count every
-- invariant below could be reported OK vacuously against an empty state (see
-- SoilValidationRunner.applyParsedOutcome).
info state

check -v

quit
