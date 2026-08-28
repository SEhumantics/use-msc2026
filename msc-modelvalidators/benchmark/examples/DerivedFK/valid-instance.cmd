-- Model VALIDATION of a hand-built instance -- a distinct concern from
-- model FINDING via `mv -validate' / SMT search (see manifest.json's
-- [main]/[mismatched] sections). Loads valid-instance.soil (two Widgets,
-- two Gadgets each genuinely matching a distinct Widget) through the
-- plain `open' command, then re-checks all class invariants directly
-- against that loaded state.
open valid-instance.soil

info state

-- WidgetNameMatches reports OK for both Gadgets. NoMatchIsUndefined
-- reports FAILED -- documented, expected (see valid-instance.soil's own
-- header comment and manifest.json's soilKnownOutOfScopeInvariants).
check -v

quit
