-- Minimal, single-purpose repro of the Bag/Sequence->Set collapse (see
-- demonstrate.cmd for the full walkthrough with ground-truth
-- comparisons, and NOTES.md for the write-up).
--
-- The query mechanism must be enabled BEFORE the search, same as
-- 02-EmployeeInvariants/query.cmd.
mv ? enable

mv -validate CollectionSemantics.properties

-- Evaluated directly against the relational solution (not the
-- reconstructed USE object diagram), same as 02-EmployeeInvariants.
-- Expect the WARN below immediately before the first two results:
--   Collect operation `Song.allInstances->collect(s : Song | s.album)'
--   results in unsupported type `Bag'. It will be interpreted as
--   `Set'.
mv ? Song.allInstances()->size()
-- Confirmed: [[3]] -- 3 Songs really exist in the solution.

mv ? Song.allInstances()->collect(s | s.album)->size()
-- Confirmed: [[2]] -- should be 3 under true Bag semantics (collect()
-- never filters), but the plugin's Set reinterpretation drops the
-- duplicate album value the pigeonhole principle guarantees exists
-- (only 2 possible album values across 3 Songs).

mv ? Song.allInstances()->collect(s | s.album)->size() = Song.allInstances()->size()
-- Confirmed: false -- would be true (3 = 3) under correct Bag
-- semantics; see demonstrate.cmd, which shows plain USE OCL ('?') on
-- the very same reconstructed solution correctly returning true.

quit
