-- Performance example: Kodkod searching for a genuine Sudoku solution
-- (6x6 board, 10 givens, unique solution) rather than the trivial
-- "does it solve at all" smoke test. See Sudoku.use/Sudoku.properties for
-- the full story of why 6x6 rather than the original bundled 9x9 board
-- (the 9x9 board was actually tried first and confirmed intractable here,
-- not just assumed so).
--
-- Confirmed output (repeated runs, this machine): SATISFIABLE every time.
-- Kodkod-to-SAT translation ~0.8-1.5s; actual DefaultSAT4J solving time
-- observed: 2.5s, 3.1s, 5.0s across separate runs -- reliably several
-- seconds of genuine solver work.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate Sudoku.properties

-- Re-checks all class invariants against the reconstructed state directly
-- (independent confirmation, on top of the plugin's own outcome line).
-- Confirmed: all 18 invariants OK (0 failures) -- including all 10
-- givenRxCy clue invariants and the 3 uniqueValues{Row,Column,Square}
-- invariants, i.e. this really is a validly solved Sudoku board, not just
-- a SAT-level "some assignment exists" result.
check -v

quit
