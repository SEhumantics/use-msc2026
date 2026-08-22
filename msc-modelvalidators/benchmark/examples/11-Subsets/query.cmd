-- Confirms, by direct OCL query against the actual found instance, that
-- the `subsets` constraint really holds: every link in `cd` (c subsets a,
-- d subsets b) and every link in `ef` (e subsets a, f subsets b) is also a
-- link in `ab` (both ends declared `union`). See Subsets.properties'
-- header for the three things discovered while building this.

mv -config satsolver := DefaultSAT4J; bitwidth := 8; automaticDiagramExtraction := off
mv ? enable
mv -validate Subsets.properties demo

-- --- Plain OCL `?` (USE core's own evaluator, correctly union-aware) ---

-- c1's D-partner via cd, and c1's B-partner via ab -- must be the same
-- object (d1) for the subsets constraint to hold on this link.
? c1.d
? c1.b
? c1.d = c1.b

-- Same check for the second subsetting association, ef.
? e1.f
? e1.b
? e1.f = e1.b

-- Confirmed every run: both equalities evaluate to `true`. The subsets
-- constraint holds for both cd and ef in this found instance.

-- --- `mv ?` (the plugin's own Kodkod-relation query) on the SAME
-- solution -- included to document finding (1): it disagrees. ---

-- Confirmed every run: these print [[F_f1]] and [[D_d1]] -- c1 and e1's
-- partners SWAPPED relative to the plain-OCL answer above, because `mv ?`
-- reads `ab`'s own independently-solved Kodkod relation directly (never
-- constrained to relate to cd/ef at all -- see finding 1), instead of the
-- derived-union value plain `?` reports. Do not use `mv ?` to inspect a
-- `union`-declared role; use plain `?` as above.
mv ? c1.b
mv ? e1.b

-- --- Re-run against [boundIgnored]: ab_min=ab_max=0 requests `ab` be
-- forced completely empty, directly contradicting cd's own link. ---

mv -validate Subsets.properties boundIgnored

-- Confirmed: still SATISFIABLE, and plain `?` still reports the subsets
-- containment holding -- `c1.b` is `d1`, cd's own linked object, exactly
-- as if ab_min/ab_max had never been given. A union-declared role's
-- OCL-visible value cannot be forced to disagree with its subsetting
-- associations via properties-file bounds (finding 2); only cd/ef's own
-- bounds actually matter.
? c1.d
? c1.b
? c1.d = c1.b

quit
