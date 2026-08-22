-- Model VALIDATION of a hand-built instance -- a distinct concern from model
-- FINDING via `mv -validate' (Kodkod search): here the object diagram is
-- constructed by hand (valid-instance.soil, plain USE SOIL statements) and
-- then simply checked, with no search involved at all.
open valid-instance.soil

info state

-- Expect every invariant to report OK: a1/c1 both have non-empty tagA,
-- b1/d1 both have non-empty tagB, and d1.tagD = 'child-d' satisfies
-- RedefinedRoleTagCheck reached through c1's redefined "b" role.
check -v

-- Confirms the redefinition is honored by USE's own interpreter
-- independently of the Kodkod path exercised in validate.cmd/query.cmd:
-- c1.b resolves to d1 (a D), not to some plain B.
? c1.b
? c1.b.tagD

quit
