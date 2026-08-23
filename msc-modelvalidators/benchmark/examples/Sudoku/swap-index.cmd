-- Model VALIDATION of a hand-built instance where physical Row identity
-- is deliberately decoupled from logical index (see swap-index.soil's
-- header): physical Row2 carries index=1 and physical Row1 carries
-- index=2, the reverse of valid-instance.soil's "natural" assignment,
-- with RowFields links swapped correspondingly so every givenRxCy clue
-- still holds. Load through the shell's `open' command, then ask USE's
-- own `check -v' to verify all 18 invariants against this state.
open swap-index.soil

info state

check -v

quit
