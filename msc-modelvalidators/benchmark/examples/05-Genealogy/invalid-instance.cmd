-- SOIL-based model VALIDATION test: load a hand-built family that
-- deliberately violates exactly the `parentOlderChild' base invariant
-- (invalid-instance.soil -- Fredo's yearB is only 10 years after Vito's,
-- not the required >=15; nameUnique and acyclicParenthood are otherwise
-- untouched and structurally still hold) through the plain `open'
-- command, then `check -v' every invariant defined in Genealogy.use
-- against it.
open invalid-instance.soil

info state

check -v

-- Confirmed by actually running this: `parentOlderChild' reports FAILED
-- (Vito is the one failing instance -- his child Fredo is only 10 years
-- younger, not >=15), while `nameUnique' and `acyclicParenthood' -- the
-- other two BASE invariants -- both still report OK.

quit
