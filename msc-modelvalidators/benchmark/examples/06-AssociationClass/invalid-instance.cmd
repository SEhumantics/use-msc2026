-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly one invariant: Employment::PositiveSalary (adaEmployment.salary
-- is set to -100 in invalid-instance.soil). No plugin command involved --
-- `open' loads the plain SOIL script, then plain USE `check -v' re-evaluates
-- every constraint in CompanyEmployment.use against the resulting state.
open invalid-instance.soil

info state

-- Expected, and confirmed by actually running this: `check -v' reports
-- FAILED for PositiveSalary only (adaEmployment, salary = -100), listing
-- adaEmployment as the violating object -- and OK for the other 4 invariants
-- (StartDateNotNegative, SalaryBelowEmployerBudget,
-- EmployeeAndEmployerAlwaysLinked, AtMostOneEmployer), confirming the
-- violation is isolated to exactly this one invariant.
check -v

quit
