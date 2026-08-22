-- Validates the multiply-inherited class hierarchy: D < B, C, E, where B and
-- C both < A (a genuine diamond -- D reaches A along two distinct
-- inheritance paths). Exactly one instance of every class (5 objects total),
-- matching the original bundled example's own MultipleInheritance.cmd
-- ("creates an object for every class").
mv -validate MultipleInheritance.properties

-- Re-checks all class invariants against the reconstructed state directly
-- (independent confirmation, on top of the plugin's own outcome line).
check -v

quit
