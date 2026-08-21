# Round 9 — independent static review of the upstream-oracle profile (no Maven run)

**Status: SUPERSEDED, kept as a stub at this exact path.** This file is cited by path from live
build tooling (`scripts/UpstreamOracleFloor.java`) — it cannot be deleted without breaking that
pointer. Its full original content (424 lines) is in git history; this stub keeps only what that
citation needs. The complete, current, tabulated state of every finding below is in
[`upstream-oracle-profile.md`](upstream-oracle-profile.md) §4.6/§5, which absorbed and corrected
each one — read that for anything not reproduced here.

## 0. Summary

Round 9's verdict was **DEFECTIVE**: the `-Pupstream-oracle` profile's pom diff and H21 code change
were structurally correct, but the profile had **no asserted floor at all** (D-01, below) — the
finding that triggered the entire floor-building effort documented across the other files in this
directory. Two of the profile document's three caveats were false about the tree (D-02/D-03), four
places still stated superseded recommendations as the plan (D-04–D-07), and six further MINORs
(D-08–D-14). All fourteen are individually corrected in place in `upstream-oracle-profile.md`.

## THE DEFECTS

### D-01 — MAJOR — the gate had no asserted floor; it could silently revert to vacuity and still be `BUILD SUCCESS`

No module declared `maven-surefire-plugin`; nothing anywhere asserted a class count, a method
count, or a minimum. Deleting the `-Pupstream-oracle` profile block from `use-gui/pom.xml` alone (a
plausible merge accident) dropped 7 revived classes / 17 methods with **both acceptance commands
still printing `BUILD SUCCESS`, 0 failures** — the same silent-vacuity shape `harness-contract.md`
§8 already refuses to tolerate elsewhere ("a floor chosen after the run is not a floor").

**Fixed.** This is the defect the whole count-floor mechanism (`UpstreamOracleFloor.java`, wired at
`initialize` and `verify`) exists to close — see `upstream-oracle-profile.md` §5.1 for the design
and §5.2.6 for its own verification history.

### D-02 through D-14 — documentation-accuracy findings, all corrected in place

Two false caveats about specific classes' vacuity risk (D-02, D-03), four stale "this is still the
plan" statements naming a recommendation that had already been superseded (D-04–D-07), and six
further MINOR cross-document inconsistencies (D-08–D-14). Every one is independently confirmed
corrected at its cited location in `upstream-oracle-profile.md` (e.g. §4.6's caveat text now carries
the correction in place, attributed).

## Verdict

`DEFECTIVE` at the time (D-01–D-14, one MAJOR). Superseded: all fourteen closed, current state in
`upstream-oracle-profile.md`.
