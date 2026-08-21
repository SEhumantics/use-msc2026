-- The [small] section (2-3 objects/class) is the default -- resolves to the
-- first section in CompanyER.properties, no name needed.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate CompanyER.properties

-- Re-checks all class invariants against the reconstructed state directly
-- (independent confirmation, on top of the plugin's own outcome line).
check -v

quit
