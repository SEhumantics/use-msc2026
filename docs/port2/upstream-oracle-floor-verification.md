# Round 10 — independent refutation of the upstream-oracle count floor

**Status: SUPERSEDED, kept as a stub at this exact path.** This file is cited by path from live
build tooling (`scripts/upstream-oracle-gate.sh`, `scripts/UpstreamOracleFloor.java`,
`use-core/pom.xml`, `use-gui/pom.xml`, `UpstreamOracleGateWiringTest.java`) — it cannot be deleted
without breaking those pointers. Its full original content (873 lines) is in git history; this stub
keeps only what those citations need. The complete, current, tabulated state of every finding below
is in [`upstream-oracle-profile.md`](upstream-oracle-profile.md) §5.1/§5.2/§5.2.6 — read that for
anything not reproduced here.

## 0. Summary

Round 10's verdict was **DEFECTIVE**: the count-floor mechanism's pinning/counting/freshness logic
was sound, but the build binding around it could be defeated from the command line. Six defects,
F-01 through F-06. **All six are answered in round 11 and closed in round 12** —
`upstream-oracle-profile.md` §5.2/§5.2.6 has the current, corrected state of every one. F-01 (below)
was the headline finding this file is cited for.

## 3.5–3.7. F-01 (MAJOR) — the floor was silenceable from the command line

`exec-maven-plugin`'s `commandlineArgs` parameter binds to the user property `exec.args` and, when
set, **replaces the POM's `<arguments>` list entirely** rather than appending to it
(`use-core/pom.xml:364,380` at the time). Any argument list that makes the resulting `java` process
exit 0 therefore disables the floor check outright:

```
$ mvn -B verify -Dexec.args=-version
[driver] number of [floor] lines in the log: 0
[INFO] BUILD SUCCESS
```

Zero `[floor]` lines, `BUILD SUCCESS`, exit 0, on an otherwise intact or even defective tree (e.g.
stacked with the D-01 merge accident — a deleted `<profiles>` block — the combination silently
collects nothing and still reports green). The record at the time claimed this could not be silenced
from the command line; it could.

**Fixed in round 11/12.** `exec.args` (and six other `exec:exec` user properties) are now pinned in
both POMs, with the remaining detectable ones handed back to the checker so that setting one fails
the build. `-Dexec.args=-version` is now `BUILD FAILURE`, exit 1, with the floor's own `[floor]`
diagnostic lines present — see `upstream-oracle-profile.md` §5.2 and §5.2.6 for the corrected,
current mechanism and its own verification.

## 11. Verdict

`DEFECTIVE` at the time (F-01–F-06). Superseded: all six closed, current state in
`upstream-oracle-profile.md` §5.2.6.
