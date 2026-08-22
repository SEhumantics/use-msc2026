-- Enables the query mechanism before searching (required), validates the
-- default [tree] section, then evaluates OCL directly against the
-- reconstructed solution -- both through the plugin's own `mv ?` (query
-- cache) and, for the operations Kodkod could never transform in the
-- first place, through plain USE `?` (ordinary OCL interpreter, no
-- Kodkod involved at all).
mv -config satsolver := DefaultSAT4J; bitwidth := 8; automaticDiagramExtraction := off
mv ? enable
mv -validate Tree.properties

-- Exactly one parentless node -- confirmed single root (falls out of the
-- 7-objects/6-links bound, see Tree.properties; not a declared invariant).
mv ? TreeNode.allInstances()->select(t | t.parent->isEmpty())->size()

-- Multiplicity holds in the reconstructed instance (TreeNode[0..1] role
-- parent): every node has at most one parent.
mv ? TreeNode.allInstances()->forAll(t | t.parent->size() <= 1)

-- The corrected, closure()-based restatement of "no cycles" genuinely
-- holds across the whole reconstructed instance -- this is the actual
-- structural check the task asked for, run directly as an OCL query
-- against the solved state, independent of `check -v` above.
mv ? TreeNode.allInstances()->forAll(t | t.child->closure(child)->excludes(t))

-- Plain (non-mv) OCL queries against the concrete, already-reconstructed
-- object diagram: the ORDINARY OCL interpreter has no problem at all
-- evaluating self-recursive user operations (only the Kodkod *transform*
-- does, per Finding 1) -- all three of the original upstream operations
-- agree that the root's transitive child-set is the whole 7-node tree,
-- since this instance is a single connected tree rooted there.
? TreeNode.allInstances()->any(t | t.parent->isEmpty()).childPlus1()->size()
? TreeNode.allInstances()->any(t | t.parent->isEmpty()).childPlus2()->size()
? TreeNode.allInstances()->any(t | t.parent->isEmpty()).childPlus3()->size()

-- Live demonstration of Finding 2 (the original upstream invariant's
-- authoring quirk) directly against this concrete, genuinely acyclic
-- instance: EVERY leaf's childPlus2() already includes the leaf itself,
-- from the Set{self} seed alone -- nothing to do with any actual cycle.
? TreeNode.allInstances()->forAll(t | t.child->isEmpty() implies t.childPlus2()->includes(t))

quit
