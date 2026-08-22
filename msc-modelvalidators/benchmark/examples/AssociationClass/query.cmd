-- Exercises the `mv ?` query mechanism (a separate code path from `check -v`/
-- `info state`, backed by KodkodQueryCache rather than reconstructed
-- MObject/MLinkObject state) against an association class specifically --
-- confirms Employment link objects, and navigation across them, are
-- queryable directly against the relational solution.
mv ? enable
mv -validate CompanyEmployment.properties

mv ? Employment.allInstances()->size()
mv ? Employment.allInstances()->forAll(e | e.salary > 0 and e.salary < e.employer.budget)
mv ? Person.allInstances()->exists(p | p.employer->notEmpty())

quit
