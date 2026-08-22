-- Model VALIDATION of a hand-built instance, as a concern distinct from
-- model FINDING via -validate/Kodkod search (see validateDefault.cmd /
-- validateOff.cmd in this same directory for the search-based tests).
--
-- Loads valid-instance.soil (plain !create/!set/!insert SOIL statements,
-- no plugin involved at all) and then runs USE core's own `check -v' to
-- confirm every invariant actually holds for this hand-built instance.

open valid-instance.soil

info state

-- Expected: every invariant (nameNotEmpty for Folder, sizePositive for
-- File/MediaFile, nameNotEmpty for Archive, notOwnPrimaryParent for Folder)
-- reports OK, and the overall checkStructure()/multiplicity part of the
-- report is fine too.
check -v

quit
