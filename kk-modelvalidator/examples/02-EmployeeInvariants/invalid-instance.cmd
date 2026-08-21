-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly ONE invariant. Loads invalid-instance.soil (Dave.salary = 0,
-- Erin/Frank salary > 0) through the plain `open' command, then re-checks
-- all class invariants directly against that loaded state.
open invalid-instance.soil

info state

-- Confirmed by actually running this: PositiveSalary reports FAILED
-- (Dave.salary = 0 does not satisfy "salary > 0"), while NotBelowMinusOne
-- still reports OK for all 3 objects (0 > -1 holds, and so do Erin/Frank's
-- salaries) -- exactly one invariant broken, as intended.
check -v

quit
