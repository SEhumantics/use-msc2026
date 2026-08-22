-- Full walkthrough of the Bag/Sequence->Set collapse limitation.
--
-- The query mechanism must be enabled BEFORE the search (see
-- EmployeeInvariants/query.cmd) so the extra relations it needs get
-- built into the Kodkod model.
mv ? enable

mv -validate CollectionSemantics.properties

-- Confirm the scenario: 1 Playlist, 3 Songs, 3 Contains links, and the
-- structural invariant (`self.songs->size() = 3`) holds.
info state
check -v

-- ------------------------------------------------------------------
-- Ground truth: plain USE OCL ('?', not 'mv ?') evaluates directly
-- against the reconstructed object diagram using USE's own OCL
-- interpreter, which has full, correct Bag support. This shows what
-- the found solution's Song.album values actually are, and confirms
-- the pigeonhole guarantee: with only 2 possible album values across 3
-- Songs, at least one value repeats.
-- ------------------------------------------------------------------
? Song.allInstances().album
-- Confirmed output (exact duplicated value varies run to run, since
-- SAT4J is not guaranteed to return the same witness every time, but
-- there is *always* exactly one repeated value): e.g. Bag{1,2,2} or
-- Bag{1,1,2} : Bag(Integer) -- a genuine duplicate, as pigeonhole
-- guarantees (3 Songs, only 2 possible album values).

? Song.allInstances()->collect(s | s.album)
-- Confirmed output: same as above, e.g. Bag{1,2,2} : Bag(Integer) --
-- collect() preserves the duplicate, as the OCL standard requires
-- (collect never filters, so its result size always equals its
-- source's size).

? Song.allInstances()->collect(s | s.album)->size() = Song.allInstances()->size()
-- Confirmed output: true -- 3 = 3, Bag semantics honored correctly by
-- plain USE.

-- ------------------------------------------------------------------
-- Now the same expressions, evaluated by this plugin's own query
-- mechanism ('mv ?') directly against the *relational* Kodkod
-- solution instead of the reconstructed object diagram. Watch the log:
-- immediately before each `collect` result below, the plugin prints
--   WARN: Collect operation `Song.allInstances->collect(s : Song |
--   s.album)' results in unsupported type `Bag'. It will be
--   interpreted as `Set'.
-- -- confirming the translator itself knows the result *should* be a
-- Bag, and deliberately downgrades it to a Set anyway, because Kodkod
-- (the relational model finder underneath) has no Bag/Sequence
-- representation to translate it to.
-- ------------------------------------------------------------------
mv ? Song.allInstances()->collect(s | s.album)
-- Confirmed output: a 2-tuple relation, e.g. [[1], [2]] (Set{1,2}) --
-- always exactly 2 tuples, never 3: the duplicate value from the real
-- Bag above is gone every single time, regardless of which specific
-- value the solver happened to duplicate.

mv ? Song.allInstances()->collect(s | s.album)->size()
-- Confirmed output: [[2]] -- NOT 3. The Set collapse is not just a
-- display artifact: ->size() sees a genuinely smaller relation.

mv ? Song.allInstances()->collect(s | s.album)->size() = Song.allInstances()->size()
-- Confirmed output: false -- the exact same OCL expression that plain
-- USE (above) correctly evaluated to `true` is evaluated to `false`
-- by this plugin's relational query mechanism, for the same reconstructed
-- solution. This is the concrete, observable consequence of the
-- Bag->Set collapse: a query result that depends on collect()
-- preserving duplicates gives the wrong answer.

quit
