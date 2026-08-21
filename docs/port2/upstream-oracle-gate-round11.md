# Round 11 — independent refutation of the floor's invocation surface

**Status: SUPERSEDED, kept as a stub at this exact path.** This file is cited by path from live
build tooling (`scripts/UpstreamOracleFloor.java`, `use-core/pom.xml`, `use-gui/pom.xml`) — it
cannot be deleted without breaking those pointers. Its full original content (599 lines) is in git
history; this stub keeps only what those citations need. The complete, current, tabulated state of
every finding below is in [`upstream-oracle-profile.md`](upstream-oracle-profile.md) §5.2/§5.2.6,
and each is independently re-confirmed closed in `upstream-oracle-gate-round12.md` §0 — read those
for anything not reproduced here.

## 0. Summary

Round 11's verdict was **DEFECTIVE**: G-01 (CRITICAL) — injecting `--stamp=true` through any of
several Maven-interpolated properties short-circuits the entire checker before validation runs, exit
0, no receipt. G-02 (MAJOR) — the unset-test `contains("${")` misclassifies a tampered
`exec.outputFile` as unset. G-03 (MAJOR) — the `exec:exec` user-property enumeration was wrong.
G-04/G-05 (MINOR). **G-01, G-02, G-04 and G-05 are all closed; G-03's factual half was right** —
`upstream-oracle-gate-round12.md` §0.

## 2. G-01 (CRITICAL) — the checker had a bypass in its own argv, reachable from ten user properties

`exec:exec`'s `<commandlineArgs>` is one Maven-interpolated string, split into argv tokens **after**
interpolation. Ten of the interpolated values are settable from the command line
(`use.upstreamOracle.effective`, `use.floor.allowProfiles`, and the eight `exec.*` properties), and
`UpstreamOracleFloor.parseArgs` accepted any `--name=value` token whose name was in `KNOWN_OPTIONS` —
which included `stamp`, because the same program also serves the `initialize` execution. `main`
tested for stamp mode **first**, before every other check: a single injected `--stamp=true` token
made the verify-phase execution rewrite the freshness stamp and `return` with status 0, skipping
every check, the count floors, the sentinel, the verdict, and the receipt. Verified directly against
the checker (no build spent): `--stamp=true` injected via `--exec-args='${exec.args}'` produced `[floor]
wrote freshness stamp ...`, exit 0, nothing else run.

**Fixed.** See `upstream-oracle-profile.md` §5.2 for the corrected argument-handling design and
§5.2.6 for the closure record.

## 5. G-04 (MINOR) — the floor bound only at `verify`, so a truncated lifecycle had no gate

`mvn -B test -Pupstream-oracle-typo` was green, exit 0, mistyped profile only a `[WARNING]`, the
count floors never reached (they bind at `verify`), no receipt written. The record disclaims `mvn
test` as a gate in four normative places, but a dormant sibling loop in this checkout runs `mvn
clean test`, and nothing separated that from an apparently-successful acceptance run except a reader
who knew which command they were looking at.

**Fixed.** The `initialize`-phase profile guard now also runs at `test`, so a mistyped `-P` fails the
build under a truncated lifecycle too — see `upstream-oracle-profile.md` §5.2.6.

## Verdict

`DEFECTIVE` at the time (G-01–G-05, one CRITICAL). Superseded: closed/corrected, current state in
`upstream-oracle-profile.md` and re-confirmed in `upstream-oracle-gate-round12.md`.
