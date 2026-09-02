-- Reads valid-instance.soil (a hand-built, deliberately VALID instance of
-- CivilStatusWorld -- see that file's own header comment) and checks every
-- invariant against it. Confirms model VALIDATION of an already-built
-- instance, as a distinct concern from model FINDING via the Kodkod
-- ModelValidator's -validate search (see validate.cmd in this directory).

open valid-instance.soil

-- `info state' is the evidence that the .soil above actually loaded: USE's -nogui
-- shell does not abort on a failed `open', so without a non-empty object count every
-- invariant below could be reported OK vacuously against an empty state (see
-- SoilValidationRunner.applyParsedOutcome).
info state

check -v

quit
