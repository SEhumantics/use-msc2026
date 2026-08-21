-- "Constraint implication" use case: is grandparentOlderGrandchild implied
-- by the base invariants? implication_check tries to find a counterexample
-- in a wide (broadConf) population and should come back UNSATISFIABLE if
-- the implication genuinely holds there; implication_sanity re-runs the
-- same check in a narrower (narrowConf) population as a smaller/faster
-- sanity check. See corleone.properties for exactly what each section
-- activates/negates.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate corleone.properties implication_check
mv -validate corleone.properties implication_sanity

quit
