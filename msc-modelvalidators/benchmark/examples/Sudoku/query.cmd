-- Queries the solved board directly to confirm it is a genuinely valid
-- Sudoku solution: two spot-checked cells (matching two of the puzzle's
-- own givens) plus every row/column/box showing all 6 digits with no
-- duplicate.
--
-- Deliberately uses plain USE `?` (not `mv ?`) for every query here, not
-- just for style. Row/Column/Square.getValues() is implemented as
-- "fields.value->asSet()", which is an implicit `collect`, and this
-- plugin's `mv ?` query mechanism has a confirmed, documented limitation
-- (see CollectionSemantics/NOTES.md) where a `collect` that should
-- produce a Bag (duplicates included) is silently translated as a Set
-- instead -- so an `mv ? someRow.getValues()->size() = 6` check would
-- read as trivially true even in a hypothetical broken solution with a
-- real duplicate, since the duplicate would already have been lost in the
-- Kodkod-side query relation itself. Plain `?` uses USE's own OCL
-- interpreter against the reconstructed object diagram instead, which
-- does not have this limitation (confirmed below: asBag()->size() and
-- asSet()->size() over the same row both come back 6, i.e. the size-6
-- result is a genuine absence of duplicates, not a translation artifact).
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate Sudoku.properties

-- Spot-check two cells against two of the puzzle's own givens (row 1,
-- column 3 is given as 6; row 6, column 1 is given as 1).
? Row.allInstances()->any(index = 1).fields->any(column.index = 3).value
? Row.allInstances()->any(index = 6).fields->any(column.index = 1).value

-- Every row/column/2x3-box must show exactly 6 distinct values (1..6).
-- getValues() uses ->asSet(), so a real duplicate would collapse the
-- reported size below 6.
? Row.allInstances()->forAll(r | r.getValues()->size() = 6)
? Column.allInstances()->forAll(c | c.getValues()->size() = 6)
? Square.allInstances()->forAll(s | s.getValues()->size() = 6)

-- Confirms the size-6 result above is a genuine absence of duplicates,
-- not a Bag-collapsed-to-Set artifact: compare the *unreduced* Bag size
-- to the Set size for one row directly (both 6 here means all 6 raw
-- values were already pairwise distinct, not just deduplicated down to 6
-- by ->asSet() from a larger multiset).
? Row.allInstances()->any(index = 1).fields.value->asBag()->size()
? Row.allInstances()->any(index = 1).fields.value->asSet()->size()

quit
