-- Model VALIDATION of a hand-built instance -- a distinct concern from model
-- FINDING via `mv -validate'/Kodkod search (see validate.cmd). No plugin
-- command is used here at all: `open' loads valid-instance.soil (a plain
-- USE SOIL script of !create/!set statements, auto-detected as a non-".use"
-- file and executed line by line), then plain USE `check -v' re-evaluates
-- every constraint in CompanyEmployment.use against the resulting state.
open valid-instance.soil

info state

-- Expected, and confirmed by actually running this: `checking invariants...'
-- reports `OK' for all 5 invariants (PositiveSalary, StartDateNotNegative,
-- SalaryBelowEmployerBudget, EmployeeAndEmployerAlwaysLinked,
-- AtMostOneEmployer) -- `check -v' is model-agnostic and doesn't care that
-- this state came from hand-written SOIL rather than a Kodkod solution.
check -v

quit
