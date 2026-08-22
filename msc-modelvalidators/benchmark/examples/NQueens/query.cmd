-- The query mechanism must be enabled BEFORE the search, same as
-- EmployeeInvariants/query.cmd.
mv ? enable

mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off

-- Uses [small] (8 queens) so the queries below run against a solution
-- that is quick to reach.
mv -validate NQueens.properties small

-- Evaluated directly against the relational solution (not the
-- reconstructed USE object diagram), same as EmployeeInvariants.

mv ? Queen.allInstances()->size()
-- Confirmed: [[8]] -- exactly 8 queens exist, as bounded.

mv ? Row.allInstances()->forAll(r1,r2:Row | r1<>r2 implies r1.idx<>r2.idx)
-- Confirmed: [[true]] -- every queen really does sit on its own row.

mv ? Col.allInstances()->forAll(c1,c2:Col | c1<>c2 implies c1.idx<>c2.idx)
-- Confirmed: [[true]] -- every queen really does sit on its own column.

mv ? Queen.allInstances()->forAll(q1,q2:Queen | q1<>q2 implies (q1.row.idx+q1.col.idx <> q2.row.idx+q2.col.idx and q1.row.idx-q1.col.idx <> q2.row.idx-q2.col.idx))
-- Confirmed: [[true]] -- no two queens share either diagonal direction,
-- i.e. this really is a valid, attack-free N-Queens placement.

quit
