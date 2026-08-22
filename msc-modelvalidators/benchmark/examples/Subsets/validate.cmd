-- Standard workflow: configure, search the [demo] section, inspect the
-- reconstructed instance. See query.cmd for the actual subsets/union
-- containment confirmation and the mv-?-vs-plain-? divergence it
-- surfaces; this script just establishes that the search itself succeeds
-- and that check -v has nothing to complain about (twoSubsets.use, ported
-- verbatim, declares zero class invariants -- "checked 0 invariants" below
-- is an accurate report, not a truncated one).
mv -config satsolver := DefaultSAT4J; bitwidth := 8; automaticDiagramExtraction := off
mv -validate Subsets.properties demo

-- Confirmed: SATISFIABLE, 4 objects (c1,d1,e1,f1; A/B contribute 0 direct
-- instances of their own), cd/ef each 1 link. `ab: 4` here is the
-- info-state link-count quirk documented in Subsets.properties' header
-- (finding 3) -- see query.cmd for what `ab` actually, correctly
-- evaluates to under plain OCL.
info state

-- Confirmed: "checked structure in ...", "checked 0 invariants ..., 0
-- failures" -- the ported model has no class invariants of its own, and
-- the structural check (which is where subsets containment is actually
-- computed by USE core, see MSystemState.validateSubsets) reports no
-- error either, consistent with query.cmd's direct confirmation that the
-- containment genuinely holds here.
check -v

quit
