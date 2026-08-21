-- The query mechanism must be enabled BEFORE the search, so extra relations
-- needed to answer OCL queries get built into the relational model.
mv ? enable

mv -validate Employee.properties

-- Evaluated directly against the relational solution (not the reconstructed
-- USE object diagram), so this works even though the query mechanism adds
-- run-time overhead only during search.
mv ? Employee.allInstances()->size()
mv ? Employee.allInstances()->forAll(e | e.salary > 0)

quit
