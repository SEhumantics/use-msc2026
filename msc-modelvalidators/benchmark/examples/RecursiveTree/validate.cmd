-- The [tree] section is the default (resolves to the first section in
-- Tree.properties, no name needed). Confirmed SATISFIABLE: 7 TreeNodes,
-- 6 Parentship links (see Tree.properties header for why that forces a
-- single-rooted, fully connected instance).
mv -config satsolver := DefaultSAT4J; bitwidth := 8; automaticDiagramExtraction := off
mv -validate Tree.properties

-- Note the transform log above: an ERROR line reports that
-- `TreeNode::AcyclicParentshipRecursive' cannot be transformed because
-- `childPlusOnNodeSet' is recursive (Finding 1 in Tree.use's header) --
-- non-fatal, the plugin simply drops that one invariant from the search
-- formula and continues. Confirmed: this happens the first time this
-- model is transformed in a session -- i.e. on whichever section's
-- -validate call runs first -- since transformation is attempted for
-- every declared invariant unconditionally regardless of its
-- active/inactive setting. It does NOT repeat for [forcedCycle]/
-- [recursiveOpProbe] below in *this* session: confirmed the model
-- transformation itself is cached after the first successful
-- -validate call, and only the properties bounds differ per later
-- call ("Model configuration successful" with no repeated "Start model
-- transformation"/WARN/ERROR lines). Running recursiveOpProbe alone, in
-- its own fresh session, reproduces the same ERROR line again from
-- scratch (confirmed separately).

-- Re-checks all class invariants against the reconstructed state.
-- Expected (and confirmed): AcyclicParentshipClosure -> OK (the tree
-- really is acyclic); AcyclicParentshipRecursive -> FAILED. The FAILED
-- result is expected, not a bug in this port: as documented in Tree.use's
-- header (Finding 2), the *original upstream* invariant's own accumulator
-- seeds itself with `self` and never removes it, so it evaluates to
-- false for every TreeNode in every instance, cyclic or not -- confirmed
-- directly against the untouched upstream Tree.use/Tree.cmd/Tree2.cmd
-- (see Tree.use's header comment for the exact reproduction). Same
-- pattern Genealogy already documents for its own inactive
-- supplementary invariants: check -v re-validates *every* declared
-- invariant regardless of which ones a section's properties activated.
check -v

info state

-- [forcedCycle]: 3 objects/3 links pinned by explicit Set{} literals into
-- a pure cycle (x->y->z->x), with AcyclicParentshipClosure still active.
-- Confirmed outcome: TRIVIALLY_UNSATISFIABLE (every object and link is
-- already pinned, so the contradiction is detected with zero free Kodkod
-- variables left -- same vocabulary AggregationComposition's own
-- forced-cycle section uses). This is the actual confirmation that the
-- corrected invariant genuinely rejects cycles, not just a solution that
-- happens to be acyclic.
mv -validate Tree.properties forcedCycle

-- [recursiveOpProbe]: minimal 1-object/0-link population -- the section
-- to run FIRST/ALONE in a fresh session if you specifically want to see
-- Finding 1's transform-time ERROR line uncluttered by any other
-- structure (in *this* script it reuses the transformation already done
-- for [tree] above, so the ERROR line itself does not repeat here --
-- see the comment after the first -validate call). Confirmed outcome is
-- still SATISFIABLE overall: the plugin drops the one untransformable
-- invariant and solves the rest of the (here, trivial) model normally --
-- transformation failure for one invariant does not abort the whole run.
mv -validate Tree.properties recursiveOpProbe

quit
