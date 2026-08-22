-- NOTE: only the "mv" alias is recognized when a command comes from a .cmd
-- script file; the long form "modelvalidator ..." does not parse here.
mv -config satsolver := DefaultSAT4J; bitwidth := 4; automaticDiagramExtraction := off

-- [default]: a clean, fully-consistent population (a1-b1 via AB, c1-d1 via
-- the redefining CD). Confirmed: SATISFIABLE, and an independent `check -v`
-- re-check reports all 3 invariants OK, including RedefinedRoleTagCheck
-- (which is only true here because D_tagD genuinely is 'child-d').
mv -validate Redefines.properties default
info state
check -v

-- [translationGap]: the IDENTICAL structure, except D_tagD is pinned to a
-- value that does NOT satisfy RedefinedRoleTagCheck (c.b->forAll(x |
-- x.tagD = 'child-d')). Confirmed: the plugin's own log notices the
-- violation ("Invariant `C::RedefinedRoleTagCheck' is not fulfilled in
-- generated system state") yet still reports outcome SATISFIABLE -- it does
-- not retry or downgrade the outcome. An independent `check -v` on the same
-- reconstructed state then correctly reports RedefinedRoleTagCheck: FAILED,
-- exposing the mismatch between the plugin's own reported outcome and the
-- actual state of its own reconstructed diagram. Root cause (see
-- Redefines.use's header comment and Redefines.properties): the Kodkod
-- encoding of "c.b" never substitutes CD's "d" for a redefining C object,
-- so it evaluates over an empty relation and "empty->forAll(...)" is
-- vacuously true -- the invariant was never a real search constraint at all.
mv -validate Redefines.properties translationGap
info state
check -v

quit
