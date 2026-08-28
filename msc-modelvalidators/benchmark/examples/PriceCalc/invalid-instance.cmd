-- Model VALIDATION of a hand-built instance that deliberately violates
-- exactly ONE invariant. Loads invalid-instance.soil (banana.doubledPrice
-- = 21, wrong for unitPrice = 10) through the plain `open' command, then
-- re-checks all class invariants directly against that loaded state.
open invalid-instance.soil

info state

-- doublePriceCheck reports FAILED for banana (10*2=20 <> 21), while
-- positiveUnitPrice still reports OK for all 3 Products.
check -v

quit
