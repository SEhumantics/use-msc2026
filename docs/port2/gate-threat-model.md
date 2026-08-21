# The acceptance gate's threat model — what it defends against, what it does not, and why

**Status: NORMATIVE. Written 2026-08-18, round 12, and intended to be the last word on the gate's
scope.** Referenced from [`harness-contract.md`](harness-contract.md) §0.

Four rounds of independent refutation (rounds 9b, 10, 11 and this one) hardened
`scripts/upstream-oracle-gate.sh` and the checks it drives. Each round closed real defects and each
round found more. This file exists to stop that regress by **drawing the line explicitly**: naming
the adversary the gate is for, naming the adversary it is not for, and **listing every known bypass**
so that "out of scope" can never be mistaken for "unknown".

The one-sentence version:

> **The gate defends a green build against being a lie by accident. It does not defend against an
> operator who deliberately types a `-D` to disable their own acceptance check, and it should not
> try.**

---

## 0. What the gate *is*

```bash
scripts/upstream-oracle-gate.sh          # THE acceptance gate. This, verbatim, is what a stage quotes.
```

**`scripts/upstream-oracle-gate.sh` IS the gate.** Not `mvn verify`. Not
`mvn verify -Pupstream-oracle`. Not either of them with the profile hand-typed correctly. The script
runs both lifecycle commands itself, with the profile id written down exactly once (on its
`PROFILE_ID` line), and then performs five classes of check **after Maven has exited**, where no
Maven property, plugin parameter or profile can reach them.

The in-build machinery — `scripts/UpstreamOracleFloor.java` at phases `initialize` and `verify`, and
`use-core/.../UpstreamOracleGateWiringTest.java` at phase `test` — is **defence in depth against
accidents**. It is not, and after four rounds of trying will not be, a sandbox that survives a
determined operator. Round 11's refuter put the finding exactly:

> "The gate, defined as `scripts/upstream-oracle-gate.sh`, held against every attack I constructed.
> It was the *only* thing that held on the four bypass routes below: its post-Maven, on-disk receipt
> check is doing the work that three of the four advertised mechanisms are claimed to do and do not."

Three of those four routes are now closed in-build as well (§2, G-01/G-02/G-04/G-05). The
architectural point survives the fix and is the reason for this file: **the wrapper is the gate; the
in-build binding is depth.**

**A number produced by any other invocation is not a gate result and may not be quoted as one.** A
figure from `mvn verify` typed by hand, from a `-pl` run, from `mvn test`, or from any command
carrying a forwarded `-D`, is a developer's iteration output. It is useful; it is not acceptance.

---

## 1. What the gate DOES defend against — the accident list

These are the failure modes the gate exists for. Every one of them has actually happened, or was
demonstrated on this tree, and for every one the catching mechanism is named with the round that
proved it.

| # | The accident | What it would look like without the gate | Mechanism that catches it | Proved in |
|---|---|---|---|---|
| **A-1** | **A mistyped profile id.** `mvn -B verify -Pupstream-oracle-typo` | `BUILD SUCCESS`, exit 0, the floor printing **`PASS` in DEFAULT mode**, and the 40 revived classes / 287 revived methods **never collected** — one `[WARNING]` in a 1487-line log as the only signal. *One character made the gate vacuous and green.* | Four, independently: (1) the wrapper **hard-codes** the id, so a typo fails to find a script instead of degrading a gate; (2) the wrapper fails on `could not be activated` anywhere in the log; (3) check **B2** at `verify` collects every profile `<id>` declared by any reactor pom and fails on a requested id matching none; (4) since round 12, the **same check at `initialize`** (see A-4). | Round 10 **F-02** (the break); round 11 §7 rows w1/w2 (the wrapper holding); round 12 §2 G-04 |
| **A-2** | **A deleted or edited pom block — the merge accident.** The `<profiles>` element, the vintage engine inside it, `use.upstreamOracle.effective`, an exec pin, or a floor execution goes missing in a merge. | `-Pupstream-oracle` activates nothing in that module, which quietly collects the **default** build's tests; the reactor total stays plausible because `use-core`'s 351 methods dwarf `use-gui`'s 17. | Check **A (WIRING)**, which every module's run applies to **both** poms from their text — so deleting `use-gui`'s profile fails the build even in `use-core`, and even in the DEFAULT build; check **B (EFFECTIVENESS)**, which compares the reactor-wide `-P` list against a property only that module's own profile sets; and `UpstreamOracleGateWiringTest` from the `test` phase, which no `exec` property reaches. Floors are **per module and per tier** for the same reason. | Round 9 **D-01** (the defect); round 11 §7 rows t1/t2; re-run in round 12 (`regress-t1-profiles-deleted`: `BUILD FAILURE`, exit 1) |
| **A-3** | **Stale `target/surefire-reports` XML from an earlier run.** Surefire does not empty its report directory, so a previous `-Pupstream-oracle` run's 47 reports survive into a later default run. | A build that collected 8 classes is credited with 41 and printed `PASS`. | The `initialize`-phase **freshness stamp**, written *before* the tests run; the `verify`-phase count ignores every report older than it and prints `stale-ignored=N`. A missing stamp is a **failure**, never a pass. The stamp cannot be pre-`touch`ed, because it is written after any pre-existing file. | Observed on this tree, not imagined (checker header, "STALENESS"); round 11 §7 row t4 — `stale-ignored=47`/`7`, the DEFAULT run credited with exactly its own 8/80 and 1/1 |
| **A-4** | **A truncated lifecycle.** `mvn -B test …`, or `mvn compile`, typed from habit — and note that ground rule 4's dormant sibling loop issues `mvn -B clean test` unattended. | Until round 12: **`BUILD SUCCESS`, exit 0, no floor check at all, no receipt**, because everything bound at `verify`. Combined with A-1 this was a green build with no gate in it whatsoever. | The unactivatable-profile check is **also bound at `initialize`**, the second phase of the default lifecycle, which every truncatable lifecycle still reaches. Both poms' `upstream-oracle-floor-stamp` execution carries `--reactor-root`, `--requested` and `--allow-profiles`; check A and the Jupiter test each require those three tokens **twice** per pom, so removing the early copy fails the build. **The floor counts still bind only at `verify` — `mvn test` is still not a gate, and never will be; the wrapper is.** | Round 11 **G-04** (the break); round 12 §2 (`mvn -B test -Pupstream-oracle-typo` → `BUILD FAILURE`, exit 1, an 82-line log) |
| **A-5** | **A dependency that stops resolving.** `junit-vintage-engine` version mangled, repository unavailable, artifact renamed. | Under `-Pupstream-oracle` the JUnit Platform collects none of upstream's JUnit 3/4 tree and the profile silently does nothing. | Maven's own resolution failure, **plus** — for the subtler case where the engine resolves but is not on the test classpath — the **SENTINEL** check: one vintage-only class per module (`USECompilerTest`, `DirectedLineTest`, both `junit.framework.TestCase`) **must** have produced a report under the profile and **must not** have produced one in the default build. Also the count floors, which cannot be met without the engine. The checker is Java single-file source mode (JEP 330), so the gate itself has no artifact that can fail to resolve. | Round 11 §7 row t3 (`BUILD FAILURE`, exit 1); vintage absence proved by `dependency:list`, not by counts (round 11 §1) |
| **A-6** | **A partial reactor.** `-pl use-core`, `-rf use-gui`. | An unqualified `PASS` for half a gate, quotable as acceptance. | The checker reads `${session.request.selectedProjects}` and `${session.request.resumeFrom}` and prints **`PARTIAL`**, never `PASS`; the receipt records `verdict=PARTIAL`; the wrapper requires `verdict=PASS` **and** `partial-reactor=false` on disk. Exit stays 0 — `-pl` is a legitimate developer flag — but it is a refusal to be quoted as the gate. | Round 10 **F-03** |
| **A-7** | **Upstream tests silently not collected.** The population shrinks for any reason at all — a lost `<testSourceDirectory>`, a surefire include pattern, a failure surefire was told to ignore, a skip. | The headline still reads `BUILD SUCCESS`; nobody notices 41 became 8. | **Pinned, per-module, per-tier count floors**, chosen *before* the run that accepts them, `0` rejected outright, never lowered to make a run pass. Counts are **distinct classes and distinct methods from the report XML**, never surefire's headline (the 14 JUnit-3 `AllTests` aggregators inflate the headline to 1086 executions for 498 methods). `failures`, `errors` and `skipped` must each be **0** — so `-Dmaven.test.failure.ignore=true` does not help. | Round 9 **D-01** (the floor's origin); round 11 §7 row r13 — a real failing test plus `-Dmaven.test.failure.ignore=true` → `BUILD FAILURE`, `verdict=FAIL` |
| **A-8** | **The gate's own machinery removed.** The checker, the wrapper, or the Jupiter test deleted; an exec pin dropped; a floor execution unbound. | Everything above evaporates at once, quietly. | Each half asserts the others: check A fails if `scripts/UpstreamOracleFloor.java`, `scripts/upstream-oracle-gate.sh` or the wiring test is missing, or if any of the seven pins appears fewer than twice per pom; the Jupiter test re-asserts the same from the `test` phase; the wrapper requires the floor to have announced itself twice at `initialize` **and** twice at `verify`, and requires a fresh receipt per module on disk. | Round 11 §2.6 (the wrapper catching what the build did not); round 12 (`t5`: reverting one pom's `initialize` argv → 3 `WIRING` violations, exit 1) |

---

## 2. What the gate explicitly does NOT defend against — and why that is right

**It does not defend against the operator deliberately injecting `-D` properties to disable their own
acceptance check.**

Every surviving bypass in §3 requires a hand-typed command-line property with a payload chosen for
the purpose: `-Dexec.args='x --stamp=true'`, an `-Dexec.outputFile` value crafted to contain a
placeholder, `-Dexec.useMavenLogger=true`. **Nobody types those by accident.** They are not shapes a
CI step, an IDE, a Maven wrapper or a habit produces; each one is a person deciding to make their own
thesis's acceptance check stop looking.

Engineering against that adversary is the wrong use of effort, for three reasons.

1. **It is unwinnable inside Maven, by construction.** A build is configured by the person running
   it. `exec:exec` alone exposes 22 user-property parameters (§3, R-3); the surefire, failsafe,
   compiler and resolver plugins expose more; `settings.xml`, `.mvn/maven.config`, `MAVEN_OPTS` and
   `MAVEN_ARGS` are all outside the reactor. Pinning and detection is a treadmill with no last step.
2. **It is unnecessary, because the answer already exists and is cheap.** The gate is a committed
   script whose post-conditions are checked **after Maven has exited**, on disk, in a place no Maven
   property reaches. Round 11 attacked it on four routes and it held on all four. Adding a
   fifth in-build detector buys nothing the receipt check does not already buy.
3. **It confuses two different jobs.** Defending a *measurement* from *accident* is quality
   engineering. Defending it from its own operator is anti-fraud, and anti-fraud in a build script is
   theatre: the same operator can edit the tree, edit the floors, or simply not run the gate and type
   a number. That last limit is irreducible and is stated below.

So the rule, and it is normative:

> **`scripts/upstream-oracle-gate.sh` IS the gate. The in-build binding is defence in depth against
> accidents. A number produced by any other invocation is not a gate result and may not be quoted as
> one.**

**The two irreducible limits**, stated so nobody has to rediscover them:

* **R-0a. A sufficiently deliberate tree edit can delete any check from the tree.** Every mechanism
  here lives in the repository. The mutual assertions of §1 A-8 make a *partial* deletion loud; a
  complete, committed one is a code review problem, not a build problem.
* **R-0b. Maven will always accept a `-P` id that some `settings.xml` outside this reactor
  declares.** Check B2 collects ids from reactor poms only. This machine has no `.mvn`, no
  `~/.m2/settings.xml`, and empty `MAVEN_OPTS`/`MAVEN_ARGS` (`upstream-oracle-verification.md` §11),
  so the pom set is the complete authority *here*. On a machine that has one, declare the id in a pom
  or pass `-Duse.floor.allowProfiles=<id>` — which is now echoed in the log **and** the receipt, and
  which the wrapper rejects on an acceptance run.

---

## 3. The residual, listed

**Out of scope is not the same as unknown.** Everything below is a route that works, or a limit that
holds, at the commit that carries this file. Nothing here is hidden by being called out of scope.

| id | Route | Effect | Why it is left open |
|---|---|---|---|
| **R-1** | `mvn` invoked by hand at all, with any `-D`, and the resulting number quoted as acceptance. | Anything. | This is the root residual and the reason the gate is a script. Closed by *policy*, stated in §0 and `harness-contract.md` §0.1: the gate is the wrapper, and a stage quotes the wrapper's own `[gate] PASS` block. |
| **R-2** | `-Dexec.outputFile=<path>` (any path). | Every `[floor]` line goes to `<path>` instead of the Maven log; the log is green and contains no `[floor]` at all. | **The check still runs, the receipt is still written, and since round 12 the TAMPERING violation fires**, so the build is red and the receipt says `verdict=FAIL`. `outputFile` is the one `exec:exec` parameter with no pinnable value ("no value" means "the log"), so detection is all there is — and detection now works. What remains is only that the *evidence* moved; the wrapper fails the run on its announce-count and receipt checks regardless. |
| **R-3** | `-Dexec.useMavenLogger=true`, and the other **thirteen** of `exec:exec`'s 22 user-property parameters that are neither pinned nor handed to check A2. | `useMavenLogger` re-prefixes every line to `[INFO] [floor] …`, which zeroes the wrapper's two **anchored** greps: its announce-count and its `FAIL`/`FATAL`/`PARTIAL` detector. With `-q` as well, the floor becomes invisible in the log while still running and still writing a `verdict=PASS` receipt. | **Deliberately not closed.** The wrapper still **fails** such a run — on the announce count, which drops to 0 — so it is not a green bypass; it degrades the wrapper's *diagnostics*, not its verdict. Closing it means pinning or detecting 13 more properties, i.e. re-entering the treadmill of §2 reason 1, to defend against a hand-typed `-D`. The count is 22, measured (`upstream-oracle-profile.md` §5.2.1). |
| **R-4** | `-Dexec.outputFile='${exec.outputFile}'` — setting the property to exactly its own placeholder. | The set/unset test reads it as *unset*, so no TAMPERING violation; `exec` writes the `[floor]` lines to a file literally named `${exec.outputFile}`. | Irreducible for a scheme that infers "unset" from an uninterpolated placeholder, which is the only signal Maven gives. Requires the operator to type the placeholder for the property they are setting. The wrapper fails the run anyway: 0 `[floor]` announcements in the log. |
| **R-5** | `-Duse.floor.allowProfiles=<id>` widens check B2 for `<id>`. | A profile id that no pom declares stops being an error. | This is a **documented escape hatch** for R-0b, not a defect. Since round 12 it is echoed in the `initialize` line, in the check header and in the receipt, flagged `<-- CHECK B2 WAS WIDENED BY THE COMMAND LINE`; and the wrapper **requires** `allow-profiles=(none)` in each receipt, so it cannot be used on an acceptance run. |
| **R-6** | Not running the gate; quoting a number from memory, from an older run, or from a truncated one. | Anything. | Not a software problem. Mitigated by the receipt (which names its own module, mode, verdict, partiality and counts, on disk) and by the rule in §4 that a stage pastes the wrapper's output verbatim. |
| **R-7** | **`mvn -B test` with *no* `-P` at all.** | `BUILD SUCCESS`, exit 0, **no count floor is checked, no sentinel, no receipt** — only the `initialize` profile guard (which has nothing to complain about) and `UpstreamOracleGateWiringTest` run. | **Irreducible, and correct.** The floors count `failsafe-reports`, which do not exist until `verify`; a floor asserted at `test` would have to be a *different, weaker* floor, and a second weaker floor is exactly the thing a reader could mistake for the gate. What G-04 closed is the case that was genuinely dangerous — `mvn test` **with a mistyped `-P`**, which *looked* like an oracle run and was not. A plain `mvn test` does not look like an acceptance run and is disclaimed as one in four normative places (`harness-contract.md` §0.1, `specification.md` §C1, `stage-00-baseline.md` §2, §4 clause 2 here). |
| **R-0a**, **R-0b** | See §2. | — | Irreducible; stated there. |
| **R-8** | **A third writer in `target/`.** Observed once: a background IDE Java language server sharing the checkout (`redhat.java`, whose own output folder for a Maven project is `target/classes`) wrote a malformed `.class` file into `use-core/target/classes` mid-run, after that run's own `mvn clean`. | The gate went **red** — `ClassFormat` error, `BUILD FAILURE` — which is the safe direction. `git status --porcelain` is empty before and after, because `target/` is git-ignored, so ground rule 4's instrument cannot see this class of interference at all. | **Not defended against, by design of what the gate can observe.** No floor, sentinel or receipt counts *bytecode provenance*, only *reports* — a stale-but-well-formed class written between `compile` and `test` would not necessarily be caught. Mitigation is procedural, not mechanical: close or disable the IDE's Java language server before an acceptance run. Round 12 §5.3. |

**Two routes that were residual in round 11 and are now closed**, recorded here so the list is
honest in both directions: injecting `--stamp=true` through any interpolated property (G-01 — now
`FATAL`, exit 2, no receipt) and `-Dexec.outputFile` evading detection with a `${` in its value
(G-02 — now a `TAMPERING` violation, `verdict=FAIL`). See `upstream-oracle-profile.md` §5.2.6.

---

## 4. The rule for S3–S10

Four clauses. They are short because they are meant to be obeyed without interpretation.

1. **Quote the wrapper.** A stage's acceptance is
   ```bash
   scripts/upstream-oracle-gate.sh
   ```
   run with **no forwarded arguments**, and the stage document pastes the script's own
   `[gate] …` block verbatim, including its `git status --porcelain` lines and its
   `[gate] PASS — mode 'both': every check above held.` A stage that quotes only one of the two
   modes has not stated its acceptance (decision **B3**).

2. **Never hand-type `-P`.** Not to reproduce the gate, not to save a run, not "just to check".
   Profile selection is the script's job — that is defect F-02, and one character made the gate
   vacuous and green. `scripts/upstream-oracle-gate.sh default` and `… oracle` exist for iteration;
   only the two together are the gate. `mvn test` is never the gate: it never runs the 130 failsafe
   methods.

3. **Quote the asserting-method figures when the number is used to argue scrutiny**, and the totals
   only when it is used to argue collection:

   | | total collected | of which **asserting** |
   |---|---|---|
   | default | 11 classes / **211** methods | **199** methods |
   | `-Pupstream-oracle` | 51 classes / **498** methods | **465** methods |

   They differ by **12** and **33** because the **six** ArchUnit architecture classes cannot fail:
   each calls `.evaluate()` and never `.check()`, computes a violation count and prints it. Measured
   again in round 12: `.check(` = 0 and `assert*` = 0 in all six; `MavenCyclicDependenciesCoreTest`
   contributes 11 of the default build's 12 and `MavenLayeredArchitectureTest` the other 1. **211 and
   498 are not evidence of scrutiny; 199 and 465 are.**

4. **Do not lower a floor to make a run pass.** If the suite legitimately grows, raise the floor in
   the same commit that grows it, and say so — as round 11 did (`+1/+1` in one cell, for the test
   that makes the gate unsilenceable). A floor chosen after the run is not a floor, and `0` is
   rejected outright.

---

## 5. Why this file ends the argument

A gate can always be attacked one round further. What cannot be improved by another round is the
*question* — and the question was settled by round 11's own finding: the wrapper held on every route,
and the in-build binding did not. Round 12 fixed the in-build binding wherever the defect was a
genuine bug (G-01, G-02) or a genuine accident route (G-04), made the escape hatch auditable (G-05),
and corrected the arithmetic (G-03). What it deliberately did **not** do is chase the remaining
bypasses, because each of them is a person typing a `-D` to switch off their own acceptance check.

The value of this round is as much in §3 as in §2: **every one of those bypasses is written down.**
A future refuter who finds one of them has found something this record already knows, and the correct
response is to cite this section, not to reopen the decision. A refuter who finds something **not**
in §3 has found a real defect, and it should be filed.
