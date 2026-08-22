mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate corleone.properties consistency

-- `check` re-validates EVERY invariant in Genealogy.use against the found
-- state, regardless of which ones this section's properties marked active
-- for the search -- confirmed by actually running this: `balancedBinaryTree`
-- comes back FAILED here (expected: it's inactive in [consistency] and this
-- solution's family tree isn't a balanced binary one), while the other five
-- inactive supplementary invariants happen to come back OK anyway (the
-- parent_0_2_* trio is essentially guaranteed by the association's own
-- 0..2 multiplicity; grandparentOlderGrandchild happens to follow from the
-- active parentOlderChild here). The three BASE invariants (acyclicParenthood/
-- nameUnique/parentOlderChild) are the ones that matter for this section and
-- must show OK.
check -v

quit
