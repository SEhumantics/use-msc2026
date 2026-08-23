-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly one invariant (see invalid-instance.soil's header for exactly
-- which field and why). Load through the shell's `open' command, then ask
-- USE's own `check -v' to confirm it actually catches it.
open invalid-instance.soil

info state

-- Confirmed by actually running this: `check -v' reports DistinctDrink
-- violated (h1 and h2 collide on #Tea) and all other 21 invariants OK,
-- finishing with "checked 22 invariants in ...s, 1 failure.".
check -v

quit
