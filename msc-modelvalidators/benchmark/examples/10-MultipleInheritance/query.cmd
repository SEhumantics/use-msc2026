-- The query mechanism must be enabled BEFORE the search, so extra relations
-- needed to answer OCL queries get built into the relational model.
mv ? enable

mv -validate MultipleInheritance.properties

-- Exactly one D, and it really is kind-of every one of its ancestors:
-- A and E directly-unrelated-to-each-other, plus B and C, which are
-- themselves both kind-of A -- i.e. D reaches A along two distinct
-- inheritance paths (the diamond) while D itself stays a single object.
mv ? D.allInstances()->size()
mv ? D.allInstances()->forAll(d | d.oclIsKindOf(A) and d.oclIsKindOf(B) and d.oclIsKindOf(C) and d.oclIsKindOf(E))

-- Attribute access across ALL THREE of D's direct superclasses, plus the
-- shared grandparent A at the top of the diamond:
--  - d.name  : declared on A, reached transitively through either B or C
--  - d.levelB: declared on B, D's first direct superclass
--  - d.levelC: declared on C, D's second direct superclass
--  - d.tag   : declared on E, D's third direct superclass (outside the
--              A/B/C diamond entirely)
--
-- (Discovered empirically while writing this: `d.name.size() > 0' fails
-- here too -- `ERROR: Cannot transform query. OCL operation size is not
-- supported.' -- the same String.size() gap that affects -validate
-- invariants (see MultipleInheritance.use) also reaches the `mv ?' query
-- mechanism itself. Using `<>' below instead, which the query mechanism
-- does support.)
mv ? D.allInstances()->forAll(d | d.name <> '')
mv ? D.allInstances()->collect(d | d.levelB)->asSet()
mv ? D.allInstances()->collect(d | d.levelC)->asSet()
mv ? D.allInstances()->collect(d | d.tag)->asSet()

-- D's own attribute, confirming ordinary (non-inherited) navigation still
-- works side by side with the three inherited ones above.
mv ? D.allInstances()->collect(d | d.depth)->asSet()

-- The diamond invariant in action: the two attributes reached via D's two
-- A-descended parents are forced distinct.
mv ? D.allInstances()->forAll(d | d.levelB <> d.levelC)

quit
