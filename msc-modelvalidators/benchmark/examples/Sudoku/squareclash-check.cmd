-- Real run of the [squareClash] section of Sudoku.properties (UNSAT
-- counterpart to [puzzle]) to confirm empirically that mv -validate
-- still reports UNSATISFIABLE for it.
mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off
mv -validate Sudoku.properties squareClash

quit
