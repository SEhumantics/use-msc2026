-- "Off" config (aggregationcyclefreeness=off, forbiddensharing=off): the
-- SAME forced link sets as validateDefault.cmd, but this time expected to
-- come back SATISFIABLE, with the cycle / the shared MediaFile actually
-- present in the found solution.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off

-- The query mechanism must be enabled BEFORE the search (see query.cmd in
-- 02-EmployeeInvariants / 03-CompanyERSchema for the same convention).
mv ? enable

-- [cycle]: same forced 2-hop cross-association cycle as the default file's
-- [cycle] section. aggregationcyclefreeness=off should now permit it.
mv -validate FileSystem_off.properties cycle

-- Confirm the cycle is really in the reconstructed object diagram, not
-- just "some SAT solution": f2's primary parent is f1, and f1's alt parent
-- is f2 -- i.e. the two folders are each other's ancestor once both
-- composition hierarchies are combined.
mv ? f2.primaryParent = f1
mv ? f1.altParent = f2

-- [sharing]: same forced doubly-owned MediaFile as the default file's
-- [sharing] section. forbiddensharing=off should now permit it.
mv -validate FileSystem_off.properties sharing

-- Confirm m1 really is linked into both compositions simultaneously.
mv ? m1.ownerFolder = f1
mv ? m1.ownerArchive = a1

-- Re-checks all class invariants against the reconstructed state directly.
-- These are expected to still hold: the ordinary domain invariants
-- (nameNotEmpty, sizePositive, notOwnPrimaryParent) are unrelated to the
-- structural toggles and should be unaffected by turning them off.
check -v

quit
