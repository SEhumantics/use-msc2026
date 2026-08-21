-- Checks independence of all 5 invariants in a 0..2-Person/0..2-Company/
-- 0..2-Employment search space with attribute domains that straddle every
-- threshold (see CompanyEmployment_invIndep.properties header).
--
-- Confirmed by actually running this (not assumed): PositiveSalary,
-- StartDateNotNegative and SalaryBelowEmployerBudget all come back
-- `Independent`, while EmployeeAndEmployerAlwaysLinked and AtMostOneEmployer
-- both come back `Dependent` -- and the plugin's own source
-- (InvariantIndepChecker.trivially_unsatisfiable(), the only code path that
-- prints "Dependent" rather than "Not independent for given properties")
-- shows that outcome specifically means the negated invariant was found
-- unsatisfiable WITHOUT even invoking the SAT solver, i.e. these two are not
-- merely "coincidentally false in this bounded population" the way a
-- solver-driven "Not independent for given properties" result would be --
-- they are trivially/structurally impossible: every association-class
-- instance always has both ends bound by construction
-- (EmployeeAndEmployerAlwaysLinked), and Company[0..1]'s own end
-- multiplicity, declared directly in the .use model, already bounds
-- `employer` to at most one (AtMostOneEmployer).
mv -invIndep CompanyEmployment_invIndep.properties all

quit
