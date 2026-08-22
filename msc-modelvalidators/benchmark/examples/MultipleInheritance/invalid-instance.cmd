-- Model VALIDATION of a deliberately-broken hand-built instance: the object
-- diagram parses and every class is represented, but D::LevelsDiffer is
-- violated on purpose (see invalid-instance.soil).
open invalid-instance.soil

info state

-- Expect D::LevelsDiffer to report FAILED (d.levelB = d.levelC = 1) while
-- A::NameNotEmpty and D::DepthMatchesTag both still report OK -- a
-- surgical, single-invariant violation, not incidental breakage.
check -v

quit
