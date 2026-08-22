-- "Classifying terms" use case: descLevel0/descLevel1/descLevel2 (see
-- Genealogy.use, class Person operations) partition each Person by how
-- many generations of descendants they have -- no children (0), children
-- but no grandchildren (1), or grandchildren but no great-grandchildren
-- (2). descLevels.ct turns these into three classifying terms (OCL
-- queries counting how many Persons fall in each level for the CURRENT
-- solution), read from a file with `-f' so the run needs no interactive
-- input.
--
-- `mv -scrollingCT' pages through solutions ONE at a time, printing the
-- three term values after each. Confirmed by actually running this
-- against corleone.properties' [consistency] section (6 Person / 4..8
-- Parenthood links): the level counts genuinely differ solution to
-- solution --
--   solution 1: level0Count=4, level1Count=2, level2Count=0
--   solution 2 (`next'): level0Count=4, level1Count=1, level2Count=1
--   solution 3 (`next'): level0Count=3, level1Count=3, level2Count=0
-- -- each solution's three counts sum to 6 (every Person here happens to
-- fall in level 0/1/2; none reaches a 4th generation in this population).
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -scrollingCT -f descLevels.ct corleone.properties consistency
mv -scrollingCT next
mv -scrollingCT next

-- `mv -scrollingAllCT' instead collects every solution up front. Confirmed
-- by actually running this against corleone.properties' [scrolling]
-- section (3 Person / 2 Parenthood links -- the same tiny population used
-- in scrollingAll.cmd, where plain `mv -scrollingAll' finds 6 solutions
-- overall): restricting scrollingAllCT to only the 2 term-DISTINCT
-- solutions it reports --
--   solution 1: level0Count=1, level1Count=2, level2Count=0
--   solution 2: level0Count=2, level1Count=1, level2Count=0
-- -- i.e. of the 6 raw solutions, the classifying terms only distinguish
-- 2 equivalence classes: either 1 Person is childless (level0Count=1) or
-- 2 are (level0Count=2), given a fixed 3-Person/2-link population where a
-- 3rd generation (level2) can never be reached.
mv -scrollingAllCT -f descLevels.ct corleone.properties scrolling

quit
