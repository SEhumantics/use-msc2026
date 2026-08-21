-- "Partial solution completion" use case: load a hand-built, incomplete
-- family (partial-state.soil: Vito, a root ancestor, already linked as
-- parent of Michael) through the plain `open' command, then turn
-- automaticDiagramExtraction ON before `mv -validate' so the search
-- COMPLETES that exact loaded state -- rather than starting from an empty
-- one -- by adding just enough further Person objects/Parenthood links to
-- reach corleone_partial.properties' [completion] bounds while satisfying
-- the three base invariants.
open partial-state.soil

info state

mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := on
mv -validate corleone_partial.properties completion

-- Confirmed by actually running this: the search reports SATISFIABLE and
-- `info state' below shows the family grew from 2 Person/1 Parenthood to
-- exactly the 4 Person/2 Parenthood asked for in [completion].
info state

-- Confirmed by actually running this: all three checks below evaluate to
-- `true' -- Vito and Michael (both their attributes and the Parenthood
-- link between them) survive into the completed solution completely
-- unchanged, rather than being replaced by a fresh, unrelated solution.
? Vito.fName = 'Vito' and Vito.lName = 'Corleone' and Vito.yearB = 0
? Michael.fName = 'Michael' and Michael.lName = 'Corleone' and Michael.yearB = 30
? Vito.child->includes(Michael)

mv -config automaticDiagramExtraction := off

quit
