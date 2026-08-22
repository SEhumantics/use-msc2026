-- Model VALIDATION of a deliberately-broken hand-built instance: the object
-- diagram parses and both associations are populated, but A::TagANotEmpty is
-- violated on purpose (see invalid-instance.soil).
open invalid-instance.soil

info state

-- Expect A::TagANotEmpty to report FAILED (c1.tagA = '') while
-- B::TagBNotEmpty and C::RedefinedRoleTagCheck both still report OK -- a
-- surgical, single-invariant violation, not incidental breakage.
check -v

quit
