-- Default config (aggregationcyclefreeness=on, forbiddensharing=on): both
-- named sections FORCE exactly the structure each toggle forbids, so both
-- are expected to come back UNSATISFIABLE.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off

-- [cycle]: forces a 2-hop cycle closing ACROSS PrimaryContains and
-- AltContains (f1 primary-parent-of f2, f2 alt-parent-of f1).
-- aggregationcyclefreeness=on should reject this.
mv -validate FileSystem_default.properties cycle

-- [sharing]: forces one MediaFile object (m1) to be part of both
-- FolderHasFile and ArchiveHasMedia at once. forbiddensharing=on should
-- reject this.
mv -validate FileSystem_default.properties sharing

-- Re-checks all class invariants against whatever (if anything) got
-- reconstructed, as an independent confirmation on top of the plugin's own
-- outcome line.
check -v

quit
