-- Performance demonstration: validates BOTH named sections of
-- NQueens.properties back to back, so the two `Solving time:` lines the
-- plugin prints can be compared directly in one run. Both are expected
-- SATISFIABLE.

mv -config satsolver := DefaultSAT4J; bitwidth := 12; automaticDiagramExtraction := off

-- [small]: 8 queens on an 8x8 board. Confirmed SATISFIABLE, solver time
-- ~300-360ms (3 repeated runs).
mv -validate NQueens.properties small

-- Re-checks all class invariants against the reconstructed state directly
-- (independent confirmation, on top of the plugin's own outcome line).
check -v

-- [large]: 17 queens on a 17x17 board. Confirmed SATISFIABLE, solver time
-- ~19.4-20.4s (3 repeated runs) -- deliberately the largest N that stayed
-- comfortably under this task's ~30s budget (N=18 was also tried in a
-- separate exploratory run: SATISFIABLE too, but ~30.5s solving time
-- alone, right at the edge -- see NQueens.properties).
mv -validate NQueens.properties large

check -v

quit
