-- Reads invalid-instance.soil, which deliberately violates exactly one
-- invariant -- Person::nameIsUnique (two distinct Person objects, P1 and
-- P2, are both given the "name" attribute value 'Ann'; see that file's own
-- header comment for why every other invariant still holds) -- and checks
-- every invariant against it. Confirms 'check -v' reports FAILED for
-- nameIsUnique specifically and OK for the other invariants that still
-- apply.

open invalid-instance.soil

check -v

quit
