-- Reads valid-instance.soil (a hand-built, deliberately VALID instance of
-- CivilStatusWorld -- see that file's own header comment) and checks every
-- invariant against it. Confirms model VALIDATION of an already-built
-- instance, as a distinct concern from model FINDING via the Kodkod
-- ModelValidator's -validate search (see validate.cmd in this directory).

open valid-instance.soil

check -v

quit
