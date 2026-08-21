-- Configure the search: SAT4J solver, 12-bit integers (the Book.year values
-- push the required bitwidth past the plugin's 8-bit default), no automatic
-- diagram extraction from a pre-existing state (there isn't one here).
--
-- NOTE: only the "mv" alias is recognized when a command comes from a .cmd
-- script file; the long form "modelvalidator ..." does not parse here (this
-- differs from calling Shell.execute(...) directly, e.g. from a JUnit test).
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off

-- Find a model satisfying Library.properties (3 users, 3 copies, 3 books,
-- all invariants active) and reconstruct it as the current system state.
mv -validate Library.properties

-- Show the objects the model finder created.
info state

quit
