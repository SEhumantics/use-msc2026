-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via `mv -validate' / SMT search (see manifest.json's
-- [main]/[mismatched] sections). Loads valid-instance.soil (3 Products
-- with correctly doubled, positive prices) through the plain `open'
-- command, then re-checks all class invariants directly against that
-- loaded state.
open valid-instance.soil

info state

-- Both doublePriceCheck and positiveUnitPrice report OK for all 3
-- Product objects.
check -v

quit
