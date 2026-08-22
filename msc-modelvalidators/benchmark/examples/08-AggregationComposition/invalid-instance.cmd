-- Model VALIDATION of a hand-built instance that deliberately breaks ONE
-- named invariant, as a concern distinct from model FINDING via
-- -validate/Kodkod search.
--
-- Loads invalid-instance.soil (identical to valid-instance.soil except
-- m1.size := 0 instead of a positive value) and then runs `check -v'.
--
-- Expected: sizePositive (context fi:File inv sizePositive: fi.size > 0)
-- reports FAILED for m1 -- and ONLY for m1 -- while nameNotEmpty (f1, f2),
-- Archive's nameNotEmpty (a1), and notOwnPrimaryParent (f1, f2) all still
-- report OK.

open invalid-instance.soil

info state

check -v

quit
