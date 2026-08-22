-- Reproduces the paper's own finding: acyclicParenthood is a logical
-- consequence of parentOlderChild (every parent-child edge strictly
-- increases yearB by 15+, so a return-to-self cycle becomes impossible once
-- it holds) -- confirmed by actually running this: acyclicParenthood comes
-- back "Not independent", parentOlderChild and nameUnique come back
-- "Independent". Structurally the same kind of demonstration as
-- 02-EmployeeInvariants (one invariant implies another), on a genuine
-- literature example instead of a hand-built toy. The five supplementary
-- invariants also come back "Not independent" here for reasons of their
-- own (grandparentOlderGrandchild follows transitively from
-- parentOlderChild; the parent_0_2_* family is essentially guaranteed by
-- the association's own 0..2 multiplicity, active regardless of any
-- invariant's on/off state) -- not a paper finding, just what this bounded
-- space happens to show.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -invIndep corleone_invIndep.properties all

quit
