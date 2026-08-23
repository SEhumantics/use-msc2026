-- The query mechanism must be enabled BEFORE the search, so the relations
-- needed to answer the OCL queries below get built into the relational
-- model (established convention, see e.g. Library/query.cmd).
mv ? enable

mv -config satsolver := DefaultSAT4J; bitwidth := 8; automaticDiagramExtraction := off
mv -validate GraphColoring.properties

-- DISCOVERED EMPIRICALLY HERE, not documented anywhere upstream, and not
-- something this example set out to find: `mv ?`'s own query evaluator
-- returns an EMPTY result (`[]`, not `[[40]]`) for `->size()` applied to an
-- object-typed collection reconstructed from the relational solution --
-- even though the collection itself is genuinely 40 Regions (confirmed
-- three different ways directly below, in the same run, against the exact
-- same solution). This is narrower than CollectionSemantics's Bag/Set
-- collapse (a wrong-but-present answer) -- here the answer is simply
-- missing.
mv ? Region.allInstances()->size()

-- Same population, evaluated via plain USE OCL (a different code path,
-- against the same reconstructed object diagram) instead of `mv ?`:
-- correctly returns 40.
? Region.allInstances()->size()

-- `mv ?` itself is not simply broken for this population: ->notEmpty() (no
-- counting involved) evaluates correctly here too.
mv ? Region.allInstances()->notEmpty()

-- Narrowing it down further: `->size()` on a collection of primitive
-- Integer VALUES (rather than Region OBJECTS) works fine through the exact
-- same `mv ?` mechanism, in the exact same run --
mv ? Set{1,2,3}->size()
-- -- and so does `->size()` two lines below on `.color->asSet()`, which is
-- also Integer-valued (not Region-valued) despite being derived from the
-- very same allInstances() population that failed above. The break is
-- specifically "->size() of a Set of model OBJECTS" through this query
-- mechanism, not ->size() in general and not this population in general.

-- THE central claim of this example: for every Region, every one of its
-- Adjacent neighbors (both directions of every one of the 67 fixed edges)
-- has a strictly different color. A single, general query over the whole
-- population -- not spot-checking one or two edges -- confirmed [[true]].
mv ? Region.allInstances()->forAll(r | r.neighbor->forAll(n | n.color <> r.color))

-- Tightness check: exactly 3 distinct color VALUES (Integers, hence
-- ->size() works here per the note above) are actually used in the
-- solution Kodkod found -- not fewer. Combined with this same graph coming
-- back UNSATISFIABLE when only 2 colors are made available (see
-- GraphColoring.properties's header comment), this confirms 3 is genuinely
-- this graph's chromatic number, not merely a color budget the solver had
-- room to spare.
mv ? Region.allInstances().color->asSet()->size()

quit
