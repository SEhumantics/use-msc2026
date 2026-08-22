-- Model VALIDATION of a hand-built instance -- a distinct concern from model
-- FINDING via `mv -validate' (Kodkod search): here the object diagram is
-- constructed by hand (valid-instance.soil, plain USE SOIL statements) and
-- then simply checked, with no search involved at all.
open valid-instance.soil

info state

-- Expect every invariant to report OK: a/b/c/d were all given non-empty
-- names, and d's own levelB/levelC/depth/tag values were built to satisfy
-- D::LevelsDiffer and D::DepthMatchesTag simultaneously.
check -v

quit
