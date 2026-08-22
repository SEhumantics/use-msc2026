-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via `mv -validate' / Kodkod search (see query.cmd,
-- invIndep.cmd). Loads valid-instance.soil (3 Employees, all salary > 0)
-- through the plain `open' command, then re-checks all class invariants
-- directly against that loaded state.
open valid-instance.soil

info state

-- Confirmed by actually running this: both PositiveSalary and
-- NotBelowMinusOne report OK for all 3 Employee objects.
check -v

quit
