-- SOIL-based model VALIDATION test (as distinct from model FINDING via
-- -validate/Kodkod search): load a hand-built, deliberately-valid family
-- (valid-instance.soil -- Vito's two children Michael/Fredo, and Michael's
-- own child Anthony; every parent/child yearB gap is >=15) through the
-- plain `open' command, then `check -v' every invariant defined in
-- Genealogy.use against it.
open valid-instance.soil

info state

check -v

-- Confirmed by actually running this: all three BASE invariants --
-- nameUnique, acyclicParenthood, parentOlderChild -- report OK.
--
-- (Five of the six supplementary invariants also happen to report OK
-- here -- grandparentOlderGrandchild and the parent_0_2_* family (four
-- invariants, two of them the EQUIVALENCE checks) -- but that is incidental, not the point of this
-- test. The supplementary `balancedBinaryTree' invariant, which demands a
-- perfectly balanced binary tree, expectedly reports FAILED: Michael has
-- exactly one child rather than 0 or 2, and Vito's two children have
-- differently-sized subtrees. This mirrors this same directory's own
-- validate.cmd, which already documents balancedBinaryTree failing on its
-- [consistency] solution for the identical reason. balancedBinaryTree and
-- the other supplementary invariants are out of scope for this SOIL test
-- -- see the three BASE invariants above.)

quit
