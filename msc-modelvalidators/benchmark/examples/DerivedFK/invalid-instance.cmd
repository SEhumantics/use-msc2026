-- Model VALIDATION of a hand-built instance that deliberately breaks
-- WidgetNameMatches. Loads invalid-instance.soil (gadget2's targetname
-- 'gamma' matches no Widget) through the plain `open' command, then
-- re-checks all class invariants directly against that loaded state.
open invalid-instance.soil

info state

-- WidgetNameMatches reports FAILED (gadget2 has no matching Widget).
-- NoMatchIsUndefined ALSO reports FAILED (gadget1 still genuinely
-- matches widgetA, so its widget is not undefined) -- see
-- invalid-instance.soil's own header comment for why both fail here.
check -v

quit
