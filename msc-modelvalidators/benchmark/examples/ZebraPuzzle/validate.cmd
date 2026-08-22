-- Configure the search: DefaultSAT4J, 4-bit integers (position and its
-- +/-1 neighbors only ever need to represent 0..6, well within a 4-bit
-- signed range of -8..7), no automatic diagram extraction from a
-- pre-existing state (there isn't one here).
--
-- NOTE: only the "mv" alias is recognized when a command comes from a .cmd
-- script file; the long form "modelvalidator ..." does not parse here.
mv -config satsolver := DefaultSAT4J; bitwidth := 4; automaticDiagramExtraction := off

-- Find the (unique, up to house re-labeling by attribute values) solution
-- to the Zebra Puzzle: 5 Houses satisfying all 21 invariants (6
-- all-different + 15 named clues).
mv -validate ZebraPuzzle.properties

-- Independent re-check of every invariant against the reconstructed state
-- (on top of the plugin's own SATISFIABLE/UNSATISFIABLE outcome line).
check -v

-- Show the objects the model finder created.
info state

quit
