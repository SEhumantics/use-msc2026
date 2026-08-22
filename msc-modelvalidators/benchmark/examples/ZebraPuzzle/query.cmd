-- The query mechanism must be enabled BEFORE the search, so extra relations
-- needed to answer OCL queries get built into the relational model.
mv ? enable

mv -config satsolver := DefaultSAT4J; bitwidth := 4; automaticDiagramExtraction := off
mv -validate ZebraPuzzle.properties

-- Sanity checks via the plugin's own query mechanism, evaluated directly
-- against the relational solution. Confirmed: [[5]], [[Nationality_Norwegian]]
-- (clue 10: the Norwegian lives in the first house), [[Drink_Milk]] (clue 9:
-- milk is drunk in the middle house).
mv ? House.allInstances()->size()
mv ? House.allInstances()->any(h | h.position = 1).nationality
mv ? House.allInstances()->any(h | h.position = 3).drink

-- The two questions the Zebra Puzzle is famous for: who drinks water, and
-- who owns the zebra? Both #Water and #Zebra are, by construction of the
-- puzzle itself, the *only* enum literals in their category never named
-- directly by any of the 14 clues (every other Drink/Pet value is pinned to
-- a nationality/house by some clue; Water and Zebra are determined purely
-- by elimination -- that is the whole point of asking about them).
--
-- Discovered empirically here (a sharper, root-caused instance of the
-- pattern already documented in CivilStatus's query.cmd): the plugin's
-- `mv ?` query mechanism only builds a Kodkod relation for an enum literal
-- if that literal is referenced *by name* somewhere in the model's own
-- invariants. Every Color/Nationality/Smoke literal, and every Drink/Pet
-- literal except Water/Zebra, is named in some clue above and so has a
-- relation; Water and Zebra are not (confirmed: `h.color = #Blue` and
-- `h.smoke = #Parliaments` -- also never forced true by any single clue
-- alone, but still each *named* in clue 15/14 respectively -- both resolve
-- fine, while precisely `h.drink = #Water` / `h.pet = #Zebra` throw
-- `kodkod.engine.fol2sat.UnboundLeafException: Unbound relation`, every
-- run). Expect (and this is the point, not a bug) the two errors below.
mv ? House.allInstances()->select(h | h.drink = #Water)->size()
mv ? House.allInstances()->select(h | h.pet = #Zebra)->size()

-- The actual correctness check therefore goes through plain USE OCL against
-- the reconstructed object diagram instead (a genuinely different code path
-- that does not share the relational query mechanism's literal-binding
-- limitation). Confirmed, matching the puzzle's own famous, well-known
-- answer: the Norwegian drinks water; the Japanese owns the zebra.
? House.allInstances()->any(h | h.drink = #Water).nationality
? House.allInstances()->any(h | h.pet = #Zebra).nationality

-- Full solution, houses 1..5 left to right, for the record. Confirmed to
-- match the canonical published Life International (1962) solution exactly,
-- attribute for attribute, not merely the two headline answers above:
--   1 Yellow/Norwegian/Water/Kools/Fox
--   2 Blue/Ukrainian/Tea/Chesterfields/Horse
--   3 Red/Englishman/Milk/OldGold/Snails
--   4 Ivory/Spaniard/OrangeJuice/LuckyStrike/Dog
--   5 Green/Japanese/Coffee/Parliaments/Zebra
? House.allInstances()->sortedBy(h | h.position)->collect(h | Sequence{h.position, h.color, h.nationality, h.drink, h.smoke, h.pet})

quit
