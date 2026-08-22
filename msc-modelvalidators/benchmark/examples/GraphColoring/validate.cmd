-- Configure the search: DefaultSAT4J, bitwidth 8 (see GraphColoring.properties's
-- header comment for why 8, and not a smaller bitwidth that would be more
-- than enough to represent color's 1..3 range on paper -- bitwidths 4/5/6
-- all come back with a WRONG, near-instant UNSATISFIABLE for this exact
-- instance; only 8 recovers the correct, genuinely-searched-for answer).
--
-- NOTE: only the "mv" alias is recognized when a command comes from a .cmd
-- script file; the long form "modelvalidator ..." does not parse here.
mv -config satsolver := DefaultSAT4J; bitwidth := 8; automaticDiagramExtraction := off

-- Find a legal 3-coloring of the fixed 40-Region / 67-edge graph pinned in
-- GraphColoring.properties. Confirmed SATISFIABLE, but genuinely slow to
-- find -- solving time alone was observed at ~13.4s and ~19.2s across two
-- separate runs on this machine (JVM/solver run-to-run variance; both are
-- "several to twenty seconds", never milliseconds, every time this was
-- actually run).
mv -validate GraphColoring.properties

-- Independent re-check of every invariant against the reconstructed state
-- (on top of the plugin's own SATISFIABLE outcome line) -- confirms both
-- properColoring and noSelfLoop hold for every one of the 40 Regions, not
-- just the pair(s) the search happened to focus on.
check -v

quit
