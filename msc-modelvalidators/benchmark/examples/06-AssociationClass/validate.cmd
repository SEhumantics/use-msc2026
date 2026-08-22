-- The [small] section (2 Person, 2 Company, 0..2 Employment links) is the
-- default -- resolves to the first section in CompanyEmployment.properties,
-- no name needed.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate CompanyEmployment.properties

-- Re-checks all invariants against the reconstructed state directly
-- (independent confirmation, on top of the plugin's own outcome line) --
-- this exercises LinkObjectStrategy actually reconstructing Employment as
-- real MLinkObject instances (with salary/startDate/employee/employer all
-- populated), not just Person/Company objects.
check -v

-- Prints the reconstructed state, including the Employment link objects
-- and their attribute values, and the Employment association's link count.
info state

quit
