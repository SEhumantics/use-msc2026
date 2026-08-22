-- Model VALIDATION catching a genuine violation in a hand-built instance
-- -- the negative case to valid-instance.cmd's all-OK run. invalid-
-- instance.soil is valid-instance.soil with exactly one number changed
-- (Alice.salary: 40000 -> -100), targeting exactly one invariant:
-- `Employee::salary_positive' (context e:Employee inv salary_positive:
-- e.salary>0).
open invalid-instance.soil

info state

-- Confirmed by actually running this: `Employee::salary_positive' is the
-- only invariant reported FAILED, with the `-v' subexpression trace
-- showing exactly why -- `e.salary > 0' is `true' for Bob (30000) and
-- `false' for Alice (-100), making the forAll as a whole `false'. The
-- other 28 invariants, including both employees' own
-- DepartmentBudget_greater_allEmployeeSalary check, still report OK --
-- see the "checking ... : FAILED." block and the summary "checked 29
-- invariants ..., 1 failure" below.
check -v

quit
