-- The query mechanism must be enabled BEFORE the search, so extra relations
-- needed to answer OCL queries get built into the relational model.
mv ? enable

mv -config satsolver := DefaultSAT4J; bitwidth := 4; automaticDiagramExtraction := off
mv -validate Redefines.properties default

-- The core demonstration: querying the redefined feature on a subclass
-- instance vs. a plain superclass instance, using ONE shared role name ("b",
-- declared on association AB between A and B).
--
-- a1 is a plain A: navigating "a1.b" uses AB directly and returns a genuine
-- B instance (b1). c1 is a C (< A) that participates in the redefining
-- association CD instead: navigating "c1.b" -- the SAME, superclass-
-- declared role name -- is silently substituted by CD's "d" end, and
-- returns a D instance (d1), not a B. Confirmed below via plain USE OCL
-- (which shares the underlying interpreter/type-checker used by `check`,
-- independent of the Kodkod solve).
? a1.b
? c1.b

-- Reaching further: b1 (linked via AB) has no tagD attribute at all --
-- tagD is declared only on D -- so "a1.b.tagD" is a genuine, rejected
-- compile error (confirmed separately: `Undefined operation 'B.tagD' in
-- shorthand notation for collect`), not merely absent at runtime. "c1.b.tagD"
-- DOES compile and evaluate, because c1's "b" statically/dynamically
-- resolves to D once the redefinition is taken into account -- reaching an
-- attribute that does not even exist on the role's DECLARED type (B).
? c1.b.tagD
? c1.d.tagD

-- Contrast: the plugin's OWN query mechanism (`mv ?`, evaluated directly
-- against the Kodkod relational solution rather than the reconstructed
-- object diagram) does NOT perform this substitution. Confirmed: querying
-- via the redefining role's own name ("d") works, but querying via the
-- inherited SUPERCLASS role name ("b") on the very same object comes back
-- empty -- silently, with no error -- because the plugin's OCL->Kodkod
-- translator has no encoding for `redefines` at all and just uses AB's own
-- (here, empty-for-c1) Kodkod relation. Same object, same role name, two
-- different answers depending on which query path is used.
mv ? a1.b
mv ? c1.b
mv ? c1.d

quit
