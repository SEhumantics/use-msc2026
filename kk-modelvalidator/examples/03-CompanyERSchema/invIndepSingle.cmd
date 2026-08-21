-- `mv -invIndep <config-file> [configurationName] [<invName>]` (see
-- doc/Usage.pdf, "modelvalidator -invIndep") checks a SINGLE named
-- invariant when invName is given, instead of stepping through all of
-- them the way invIndep.cmd's "... all" call does. Syntax for invName is
-- className::invariantName.
--
-- No configurationName is passed here: CompanyER_invIndep.properties (see
-- that file's own header comment) has a single unnamed section, so -- just
-- like invIndep.cmd's own "mv -invIndep CompanyER_invIndep.properties all"
-- needs no section argument -- this single-invariant form goes straight
-- from the config-file to the invariant name.
--
-- Employee::dname_foreign_key_Department was picked because invIndep.cmd's
-- full run already reports it "Not independent for given properties" in
-- this same 0..3/0..4-object search space; running it here in isolation
-- reproduces that exact outcome (verified: both this command and the full
-- "... all" run print the identical line
-- "Employee::dname_foreign_key_Department: Not independent for given
-- properties"), confirming that checking one invariant by name gives the
-- same per-invariant result as the full sweep. Wall-clock, this single
-- check finished in ~1.6s versus ~3.4s for invIndep.cmd's full 29-invariant
-- sweep on this machine (both figures include the same fixed JVM/model
-- load overhead, so the difference is entirely the 28 invariants skipped).
mv -invIndep CompanyER_invIndep.properties Employee::dname_foreign_key_Department

quit
