# Static review — the `-Pupstream-oracle` profile, H21, and the four recorded decisions

**Role: static refuter. No Maven was run.** Every claim below is `git`, `grep`, `sed`, `find`, `unzip`,
or a read of a committed blob, with the command or the `file:line` named. Where a figure could only be
obtained by running Maven it is marked **NOT RE-MEASURED** and attributed to the record, never asserted.

**Commits reviewed:** `e3668a04`, `1ec7d59f`, `e73054e2`, `7696091e` on `port-uncertainty-2`.
Working tree was clean at the start of this review (`git status --porcelain` → empty).

**VERDICT: DEFECTIVE.** The pom change itself is correct and I certify it clause by clause in §1;
ground rule 3 is clean and that is the strongest result in this review (§4). But the thing that is
about to become the S3–S10 acceptance gate has **no asserted floor** — it can silently revert to
vacuity and still print `BUILD SUCCESS` (D-01) — and the decision record leaves the **not-taken
recommendations standing as the plan in four places, one of them the normative contract** (D-04, D-05,
D-06, D-07), which is exactly the failure mode the review brief names. Two of the gate document's
three "honest caveats" are additionally false about the tree they describe (D-02, D-03).

### Concurrency note

`pgrep`-style inspection at the time of review found **PID 494057** whose command line contains the
literal `mvn -B clean test` — this is the known false positive: it is a `while` loop waiting for the
build to go quiet, not a build. It also found **PID 1536339**, started during this review, running
`mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest -Duse.differential.golden.refresh=true`.
That is a **live** Maven run and it **rewrites `docs/port2/differential/*.tsv` in the working tree**.
All golden analysis in §3 was done from `git show 1ec7d59f -- docs/port2/differential/`, i.e. from
committed blobs, so it is unaffected by that run. Anyone reading worktree `.tsv` files right now
should re-check `git status` first.

---

## 1. The pom diff, clause by clause — CLEAN

`git show e3668a04 -- use-core/pom.xml use-gui/pom.xml` is 39 added lines per module: a block comment
plus one `<profiles>` element. Nothing else changes.

| Check asked for | Result | Evidence |
|---|---|---|
| dependency inside the profile and nowhere else | **PASS** | `grep -c "<profiles>" use-core/pom.xml use-gui/pom.xml` → `1` each. `grep -rn junit-vintage --include=pom.xml .` hits only `use-core/pom.xml:370` and `use-gui/pom.xml:315`, both inside the profile. |
| scope `test` | **PASS** | `use-core/pom.xml:372`, `use-gui/pom.xml:317` — `<scope>test</scope>`. |
| version consistent with `junit-jupiter` 5.7.0 (vintage/platform mismatch) | **PASS** | jupiter is 5.7.0 in both modules (`use-core/pom.xml:63-66`, `use-gui/pom.xml:146-149`). `junit-vintage-engine:5.7.0`'s own POM (`~/.m2/.../junit-vintage-engine-5.7.0.pom`) declares `org.junit.platform:junit-platform-engine:1.7.0` and `junit:junit:4.13` — the same platform train jupiter 5.7.0 resolves. **No mismatch.** |
| `<id>` matches what the documentation tells people to type | **PASS** | `<id>upstream-oracle</id>` at `use-core/pom.xml:366`, `use-gui/pom.xml:311`. Every command in `upstream-oracle-profile.md` (§2, §5, §7), `specification.md` C1 (:72-73), `foundation-verdict.md` §3.0 and both pom comments spells `-Pupstream-oracle`. No typo found anywhere: `git grep -c -- "-Pupstream-oracle"` → 8 doc/1 test/2 pom hits, all identical. |
| accidentally `<activeByDefault>` | **PASS — absent** | `grep -rn activeByDefault --include=pom.xml .` → no output. |
| applies to BOTH `use-core` and `use-gui` | **PASS** | `diff <(sed -n '/<profiles>/,/<\/profiles>/p' use-core/pom.xml) <(sed -n '/<profiles>/,/<\/profiles>/p' use-gui/pom.xml)` → **IDENTICAL**. `use-assembly` is not covered and needs no coverage: `find use-assembly -path "*src/test*" -name "*.java"` → empty. |
| consistent with what `upstream-oracle-profile.md` claims | **PASS** | §1 of the doc reproduces the exact block and names both modules. |

Two secondary claims in the doc's §1 that I checked because a wrong one would break the profile:

* "`junit:junit` is already on the default test classpath … `4.13.2` in `use-core` (transitively, via
  `guava-testlib`)" — **CORRECT.** `guava-testlib-33.5.0-jre.pom:41-44` declares `junit:junit:4.13.2`
  at **compile** scope with the comment "`*not* <scope>test</scope>`", and `use-core/pom.xml:57-60`
  takes `guava-testlib` at test scope, so junit 4.13.2 lands on the test classpath.
* "`4.13.1` in `use-gui` (declared)" — **CORRECT**, `use-gui/pom.xml:152-155`.

The `<profiles>` element is placed after `<build>`; the 4.0.0 POM model reader is order-insensitive
for top-level elements, so this is valid. Neither pom has a trailing newline, before or after
(`git show e3668a04^:use-core/pom.xml | tail -c 20 | xxd` ends `...</project>` with no `0a`) — pre-existing,
not introduced here.

---

## 2. Does the documentation match the build?

### 2.1 The arithmetic reconciles completely — this part is good work

I could not re-run Maven, but every internal identity in `upstream-oracle-profile.md` is checkable
statically, and **all of them hold**:

* §4.4's revived list has **exactly 40 entries** and its method column sums to **exactly 287**
  (55+38+13+12+12+12+11+11+11+10+9+8+8+6+6+6+6+4+4+4+4+4+4+4+3+2+2+2+2+2+2+2+1+1+1+1+1+1+1+1 = 287).
* §4.1's claim "28 of `use-core`'s 40 test classes are inflated … the other 12" checks out: exactly 28
  entries in §4.4 carry a parenthesised execution count; the 5 use-core entries without one plus the
  7 use-core classes already running by default = 12.
* The execution figures reconcile to the pasted headline: the 28 parenthesised figures sum to **786**,
  the 5 non-inflated revived use-core classes contribute 10+4+3+55+1 = **73**, and the default
  use-core surefire tier is **79** → **938**, which is exactly the `Tests run: 938` §4.1 pastes.
* Class-set check against the filesystem: `use-core/src/test` holds 38 `*Test.java` plus
  `TestProtocolStateMachine.java` and `TestSignals.java` (which match surefire's `**/Test*.java`) = 40
  collectable non-aggregator classes, matching `classes=40`; `TestSystem.java` also matches the include
  pattern but contributes no test method, consistent with its absence. `use-gui/src/test` holds 8
  `*Test.java`, matching `classes=8`.
* The 14 `AllTests.java` aggregators **are** collected — `AllTests` matches surefire's default
  `**/*Tests.java` include — and write 0 `<testcase>` elements, which is precisely why the doc's script
  excludes them via `if distinct:`. The doc's explanation of the overcount is mechanically right.
* Cross-commit consistency: `e3668a04`'s message says headline `937 + 17` and `48 classes / 366 methods
  / 496 total`; the doc measures `938`, `48 / 367`, `497` after H21 — a delta of exactly +1, the new
  regression test. Consistent, not contradictory.
* §4.3's reconciliation against the two throwaway probes (43/300 at `b7aaa99c`, 45/315 at `8789e035`)
  matches `stage-00-baseline.md:224,270,282` verbatim, and 315 → 366 = +51 = 7 + 10 + 34, the three S2
  harness classes.
* §4.6 caveat 1 is **CORRECT and verified**: all four named ArchUnit classes have `evaluate=1 check=0`
  and zero `assert*` calls (`grep -c '\.evaluate(' / '\.check(' / '\bassert[A-Z(]'` over each file).

**Commands in the doc:** both are runnable from the reactor root as written; the profile id exists in
two reactor modules so Maven raises no "profile could not be activated" warning. The counting script's
module list `('use-core', 'use-gui')` is correct today. **No typo'd command found.**

### 2.2 But two of the three "honest caveats" are false about this tree

See **D-02** and **D-03** below. This is the sharpest finding in the review: the gate document's own
caveat section describes the *fork's* `USECompilerUncertaintyTest` while naming *upstream's*
`USECompilerTest`, and the one live cross-test-pollution hazard under the profile has its correct
`file:line` recorded nowhere.

---

## 3. H21's structural correctness — CLEAN

| Check asked for | Result | Evidence |
|---|---|---|
| provenance carried **structurally**, not parsed out of rendered text | **PASS** | `DiffRow.java:35` — `private final UValue.TypeProvenance subjectTypeProvenance;`, a typed field. `DifferentialSweep.subjectTypeProvenance(Outcome)` reads `sub.value.typeProvenance()`. No `note()` parsing exists: `grep -n "note()" DifferentialSweep.java` finds no read in either aggregate; `typeMismatchesWithSubjectProvenance` matches on `row.subjectTypeProvenance() == provenance` only. |
| obeys `DiffRow`'s own "a count derived from prose silently becomes zero" argument | **PASS** | The Javadoc at `DiffRow.java:89-108` states the argument explicitly and the implementation follows it. |
| new headers emitted **unconditionally**, including at zero | **PASS** | `DiffReportWriter.java:275-276` (file scope) and `:313-317` (per operation) are unguarded `header(...)` calls; both goldens show all four lines reading `0`. `stageStatement()` prints the split unconditionally (`DifferentialSweep.java:793-795`). Only `summary()` gates on `typeMismatched > 0`, which is documented as deliberate at `DifferentialSweep.java:991-993`. |
| two reports never byte-indistinguishable | **PASS** | The regression test's block (7) asserts an identical `# rows.javaTypeMismatch\t4` in both files and then asserts the `# rows.subjectType*` lines differ. |
| identity `observed + assumed == javaTypeMismatch` actually holds | **PASS today** | `javaTypeMismatchCount()` and `typeMismatchesWithSubjectProvenance()` use the identical predicate (`verdict()==AGREE && !historical().equals(ported())`). All three remaining null-provenance construction sites are outside that population: `DifferentialSweep.java:138` (`UNSUPPORTED`), `:202` (`HARNESS_ERROR`), `:212` (`BOTH_THREW`/`ACCEPTED_THROW`). `NONE` ⟺ `javaType == null` (`UValue.java:254`) and cannot reach `AGREE`. See **D-11** for the residual API hazard. |

`toTsv()` is genuinely untouched by `1ec7d59f` — it appears nowhere in the diff for `DiffRow.java`.

---

## 4. Did the goldens move, and is the move accounted for? — CLEAN

`git show --stat 1ec7d59f` shows `s1-smoke-ureal-add.tsv | 4 +` and `s1-smoke-ureal-minus-faulty.tsv | 4 +`
— **8 insertions, 0 deletions, 0 modifications** across both files. `git show 1ec7d59f -- docs/port2/differential/`
confirms every added line begins with `# ` (`rows.subjectTypeObserved`, `rows.subjectTypeAssumed`, and the
matching `op.URealValue.add(value).*` / `op.URealValue.minus(value).*` pair), all reading `0`.
**No data row moved**, exactly as the commit message and `DiffRow.subjectTypeProvenance()`'s Javadoc claim.
The refresh is disclosed in the commit message with the flag used and the reason.

### Ground rule 3 — never edit an upstream test: **CLEAN, and this is the strongest result here**

```
$ git log -1 --format="%H" upstream-main
30d480dbcca2f404b1350039516a56f46c1efb1f          (== the branch base 30d480db)

$ git diff --name-status 30d480db..HEAD -- '*/src/test/*' | grep -v 'uncertainty/differential'
A	use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar
A	use-core/src/test/resources/historical/use.jar

$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(empty)
```

Two `A` lines, no `M`, no `D`, no `R`. Every upstream test file that the profile revives is
**byte-for-byte the blob at `upstream-main`**. Verified, not assumed. Zero waivers remains correct
(`docs/port2/upstream-test-waivers.md`).

---

## 5. Do the four recorded decisions match what the human chose? — recorded correctly, but not everywhere

| Decision | Required record | `specification.md` §0.0 | `foundation-verdict.md` §3.0 | Not-taken recommendation named |
|---|---|---|---|---|
| **B3** | the `-Pupstream-oracle` profile | :159-161 ✔ | :241 ✔ | n/a — recommendation **was** taken, and both say so |
| **B7** | FIX, documenting each | :168 ✔ | :243 ✔ | **YES** — "Bug-for-bug reproduction was the recorded recommendation and was NOT taken" |
| **H14** | BUILD an input-domain coverage measure | pointer at :172-173 ✔ | :244 ✔ | **YES** — "prose-stated domains … not taken" |
| **B2** | FULL PORT of `SBoolean`, all 39 operations | :169 ✔ | :245 ✔ | **YES** — "Option 2 (skeleton) … was NOT taken" |

All four are recorded, dated `2026-08-17`, marked in place in both tables, and each carries the
recommendation that was not taken. `b7-fix-plan.md` §0 repeats B7's record correctly and cites
`specification.md:183` and `16-modernization-ledger.md:30`, both of which resolve.

**But the second half of the requirement — "no document still asserts the old recommendation as the
plan" — FAILS in four places.** See D-04 through D-07. `git grep -c "2026-08-17" -- docs/port2/spec-parts/`
returns **no output at all**: not one of the eleven `spec-parts/` evidence files carries any marker
that four decisions were taken.

---

## 6. THE DEFECTS

### D-01 — MAJOR — the gate has no asserted floor; it can silently revert to vacuity and still be `BUILD SUCCESS`
**`use-core/pom.xml` / `use-gui/pom.xml`; `docs/port2/upstream-oracle-profile.md` §5 (:376-397)**

`grep -rn surefire --include=pom.xml .` returns **only the two comment lines** — no module declares
`maven-surefire-plugin`, and the root pom's `pluginManagement` pins only `maven-compiler-plugin`
(`pom.xml:24-33`). Nothing anywhere asserts a class count, a method count, or a minimum. The rule in
§5 is "quote … the deduplicated class and method counts" — a **human-read** number.

*Failure scenario.* Delete the profile block from `use-gui/pom.xml` only (a plausible merge accident):
the 7 revived `use-gui` classes / 17 methods stop being collected and **both acceptance commands still
print `BUILD SUCCESS`, 0 failures**. Same outcome from bumping `junit-jupiter` past 5.7.x while vintage
stays 5.7.0, or from `guava-testlib` losing its transitive `junit:junit` — in each case the profile
collects less, or nothing, and nothing fails. That is precisely the vacuity B3 exists to abolish, and
this port refuses to tolerate the identical shape elsewhere: `harness-contract.md` §8 step 2 —
"**A floor chosen after the run is not a floor**", and `0` is "rejected outright". The upstream-oracle
gate is held to a weaker standard than the harness the same repository built.

### D-02 — MAJOR — §4.6 caveat 2 is false about the class it names; `USECompilerTest` cannot pass vacuously
**`docs/port2/upstream-oracle-profile.md:363-368`**

The caveat states: "`USECompilerTest`'s 2 methods may be vacuous, per B12. It resolves its fixtures
from `System.getProperty("user.dir") + "/src/test/…"`; under Maven the module root is `use-core/`, so
the directory listing can come back `null` or empty and the loop runs zero times."

`grep -n "user.dir" use-core/src/test/java/org/tzi/use/parser/USECompilerTest.java` → **no match**.
What the file actually does:

* `USECompilerTest.java:74-78` — `new File(ClassLoader.getSystemResource("org/tzi/use/parser").toURI())`,
  i.e. **classpath** resolution, not `user.dir`;
* `:82` — `fail("Folders including tests are missing!")` if that resource is absent;
* `:293` — `assertNotNull(files);`
* `:297-301` — `assertEquals("make sure that all test files can be found …", expected, fileList.size())`
  with `EXPECTED = 49` (`:68`);
* `:116` — a second `assertNotNull(files)` on the examples directory.

It therefore **cannot** "run zero times and pass". The `user.dir` shape belongs to the **fork's**
`USECompilerUncertaintyTest`, which is what B12/CF-8 is about everywhere else in the record
(`specification.md:188`, `specification.md:2172`, `16-modernization-ledger.md:45`).

*Why this is MAJOR and not cosmetic.* It is a mis-transposed citation in the one document that is
supposed to embody the port's "every claim names `file:line`" standard; it understates a revived
oracle; and worst, it points a future stage at an upstream test that has nothing wrong with it. Acting
on caveat 2 means editing `USECompilerTest.java` — a **ground rule 3 violation invited by the gate
document itself**.

### D-03 — MAJOR — §4.6 caveat 3 names a class absent from the reactor, so the live pollution hazard has no checkable citation
**`docs/port2/upstream-oracle-profile.md:370-374`**

"The aggregators run, so `USECompilerUncertaintyTest`'s process-global write to
`Options.explicitVariableDeclarations` … is live under this profile."

That class is **not in the reactor**. `find . -name "USECompilerUncertaintyTest*"` finds it only at
`.git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/…`,
and `git grep -ln USECompilerUncertaintyTest -- use-core use-gui` matches only the vendored
`use-core/src/test/resources/historical/use.jar`. It is not run by the profile.

The hazard is nevertheless **real and live**, in two classes the profile *does* revive:

```
use-core/src/test/java/org/tzi/use/parser/USECompilerTest.java:111:        Options.explicitVariableDeclarations = false;
use-core/src/test/java/org/tzi/use/parser/soil/StatementGenerationTest.java:64:  	    Options.explicitVariableDeclarations = false;
```

Neither restores the flag. `USECompilerTest` (2 methods) and `parser/soil/StatementGenerationTest`
(12 methods, 48 executions) are both in §4.4's revived list, and the 14 `AllTests` aggregators change
the order in which they run relative to everything else. **Those two `file:line`s appear nowhere in the
gate document.** Caveat 3 is right in substance and uncheckable as written.

### D-04 — MAJOR — `specification.md` §9 still states the single-command gate and the not-taken recommendations
**`docs/port2/specification.md:2567`, `:2570-2572`, `:2582`, `:2584`, `:2587`**

§9 presents itself as "the numbered action list" — the section a stage will actually work from — and it
was not touched by `e73054e2`:

* `:2570-2572` — "> **The acceptance gate for every one of these is `mvn -B verify -Djava.awt.headless=true`
  (baseline **143** = 13 surefire + 130 failsafe), not `mvn test`.**" This directly contradicts C1's own
  amendment 2 500 lines above it (`:68-79`, "there are TWO acceptance commands") and the B3 decision.
* `:2582` row 2 — recommendation column still reads "**skeleton (option 2)**", with no DECIDED marker.
  B2 was decided **option 3, FULL PORT**.
* `:2587` row 7 — still "decide **one policy first**, then per-row". B7 was decided **FIX**.
* `:2584` row 3 — quotes "**45 classes / 315 methods**" as the profile's yield; the measured figure is
  50 / 497. No DECIDED marker.
* `:2567` — "**Nothing in S3 should start until all twelve have an owner and a recorded answer.**"

`e73054e2` marked only §0's `B1`–`B12` table. §9 is a second, independent statement of the same
recommendations and it is entirely stale.

### D-05 — MAJOR — `19-open-questions.md` Q2 tells the reader the decided B2 option is unwarranted
**`docs/port2/spec-parts/19-open-questions.md:402`, `:415`, `:418-420`**

* `:402` — "1. **Full omission (recommended).**"
* `:415` — "3. **Full port.** Only if a thesis result depends on subjective-logic operators. **Nothing in
  the corpora suggests it does.**"
* `:418-420` — "Option 2 is the cheapest way to keep the fork's *type system* bit-identical …"

B2 was decided as option 3. This file, cited as the evidence for B2 by `specification.md:183`
("`19-open-questions.md` Q2"), flatly asserts the decided option is unjustified, with no marker.
A second problem surfaces alongside it: this file recommends **option 1**, while `specification.md` §0.0
and §8.2 (`:2481`, marked "⭐") record the recommendation as **option 2**. The record disagrees with
itself about what was recommended, which weakens the "recommendation NOT taken" bookkeeping B2 depends on.

### D-06 — MAJOR — the modernization ledger still instructs `DEFER`, and `b7-fix-plan.md` is unreachable
**`docs/port2/spec-parts/16-modernization-ledger.md:29-30`; `docs/port2/b7-fix-plan.md`**

`:29-30` — "For those rows the *proposed change* column reads **DEFER** — they are the rows a human must
rule on." 29 `DEFER` occurrences remain in that file. `git grep -c "2026-08-17" -- docs/port2/spec-parts/`
→ no output: nothing in the ledger records that B7 was decided.

And the document that supersedes it is an orphan: `git grep -ln b7-fix-plan -- docs` matches **only
`b7-fix-plan.md` itself**. Same for the H14 design document — `git grep -ln h14-coverage-design -- docs`
→ no output. Neither `specification.md`, `foundation-verdict.md` nor `harness-contract.md` links to either.

*Failure scenario.* An S4–S7 stage works its per-row list from `16-modernization-ledger.md` Tables A+B,
as `specification.md:183` tells it to, reads `DEFER` on 29 rows, and reproduces the defects — i.e.
implements bug-for-bug, the recommendation that was explicitly **not** taken — because the 1 218-line
document that triaged all 33 rows is not referenced from anywhere it would look.

### D-07 — MAJOR — `harness-contract.md` (normative) carries neither the two-command rule nor the H14 decision
**`docs/port2/harness-contract.md:267`, `:363`, `:596`**

`git grep -n -iE "acceptance|upstream-oracle|H14" -- docs/port2/harness-contract.md` → **no hit** for any
of the three. This is the normative contract, and its §8 is "**THE S4 CHECKLIST — imperative,
copy-pasteable**". `e73054e2` edited it for H21 only.

* `:363` — "Run by `mvn -B verify -Djava.awt.headless=true`." The second acceptance command appears
  nowhere in the file, and none of §8's nine imperative steps mentions it. The B3 rule lives only in
  `specification.md` C1 and in `upstream-oracle-profile.md` §5 — not in the document a stage is held to.
* `:596` — §8 step 5, figure 5: "| 5 | **the input domain, in prose** — what was covered and what was
  not | **you** (D-30) |", and `:267` — "state, in prose, which inputs the …". That is the **H14 position
  that was NOT taken**, standing as a mandatory instruction in the normative contract.

### D-08 — MINOR — §1 double-counts the JUnit 3/4 sources: "the 59" is really 47
**`docs/port2/upstream-oracle-profile.md:37`, `:52`**

45 files under `use-core/src/test` import `junit.framework` and 14 import `org.junit.Test` — both figures
verified — but **12 files import both**, so the union is **47**, not 59:

```
parser/shell/ASTConstructionTest.java   parser/shell/StatementGenerationTest.java
parser/soil/ASTConstructionTest.java    parser/soil/StatementGenerationTest.java
uml/mm/statemachines/TestProtocolStateMachine.java   uml/mm/statemachines/TestSignals.java
uml/sys/MSystemStateTest.java           uml/sys/soil/StatementEffectTest.java
utilcore/soil/StateChangesTest.java     utilcore/soil/SymbolTableTest.java
utilcore/soil/VariableEnvironmentTest.java   utilcore/soil/VariableSetTest.java
```

(`comm -12` of the two `grep -rl` lists.) `:52`'s "the 59 JUnit 3/4 test *sources* compile today" is
therefore wrong by 12.

### D-09 — MINOR — the pom comment added by `e3668a04` states a present-tense figure that was already false at commit time
**`use-core/pom.xml:346-347`, `use-gui/pom.xml:291-292`**

"…which is why the default build runs **3 test classes** out of the **41** `*Test.java` files present".
At `e3668a04` the default build runs **8** distinct surefire classes — the document's own §3.1 paste
lists 7 in `use-core` plus `MavenLayeredArchitectureTest` in `use-gui` — and **46** `*Test.java` files
are present (`find use-core/src/test -name '*Test.java' | wc -l` → 38; `use-gui` → 8). 3/41 is the S0
baseline (`stage-00-baseline.md:109-110`), which the comment cites but does not date, in a file a
build engineer reads before the docs.

### D-10 — MINOR — the acceptance command's surefire version is unpinned; the gate's collection behaviour belongs to the local Maven install
**reactor-wide**

No pom declares `maven-surefire-plugin`. The `3.5.4` the document records as an environment fact is
Maven 3.9.16's default binding:

```
$ unzip -p ~/.local/share/apache-maven-3.9.16/lib/maven-core-3.9.16.jar \
      META-INF/plexus/default-bindings.xml | grep -n surefire
82:                org.apache.maven.plugins:maven-surefire-plugin:3.5.4:test
```

The profile pins the engine (5.7.0) but not the plugin that drives it, and the profile's entire yield
depends on surefire's default `<includes>` — that is the only reason the 14 `AllTests.java` files are
collected at all (they match `**/*Tests.java`). A different Maven on the reviewer's machine is a
different gate, with nothing in the repository to say so. Also unpinned by construction: `use-core`'s
JUnit-4 version is decided by a *transitive* dependency of `guava-testlib`.

### D-11 — MINOR — `DiffRow`'s 7-argument constructor survives unmarked; a future `AGREE` row through it breaks the H21 identity silently
**`use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffRow.java:43-46`**

```java
public DiffRow(int index, String operation, List<String> inputs, String historical, String ported,
               DiffVerdict verdict, String note) {
    this(index, operation, inputs, historical, ported, verdict, note, null);
}
```

It is `public`, not `@Deprecated`, and unguarded. The identity argument in
`DifferentialSweep.Result#subjectTypeObservedCount()`'s Javadoc reasons only about `NONE` and never
about `null`. `typeMismatchesWithSubjectProvenance` matches on `== provenance`, so a `null`-provenance
`AGREE` row whose two columns differ would be counted by `javaTypeMismatchCount()` and by **neither**
half — `observed + assumed < javaTypeMismatch`, the partition the header invites a reader to trust.
Today this is unreachable (all three null sites are outside the AGREE population, §3), and the
regression test would catch it only if the new site were exercised by that test's own fixtures.

### D-12 — MINOR — `foundation-verdict.md`'s own residual-risk summary still says the decisions are unmade
**`docs/port2/foundation-verdict.md:344-345`, `:348`**

":344-345 — "What remains is that **all twelve, plus H13–H16, H18, H21 and H22, are unmade.**" and
`:348` — "H21 and H22 may be deferred past S3 but not past S4". `e73054e2` inserted a correcting
parenthetical immediately before the second sentence but left both standing, in the same document whose
§3.0 records the four decisions.

### D-13 — MINOR — `stage-02.md`'s forward-looking instructions to S3 are stale
**`docs/port2/stage-02.md:341-342`, `:348-350`**

`:341-342` — "The first real `mvn -B verify -Djava.awt.headless=true` of S3 is the first evidence"
(one command, now two). `:348-350` — "**The 12 blocking decisions are unanswered.** … **S3 must not
start until all twelve have an owner and a recorded answer**" (three are now answered). Retrospective
staleness would be forgivable; these are instructions to the next stage.

### D-14 — MINOR — the counting script hard-codes two modules
**`docs/port2/upstream-oracle-profile.md:98`**

`for mod in ('use-core', 'use-gui')`. Correct today — `use-assembly` has no `src/test` — but a third
module gaining tests would be invisible in a figure the §5 rule requires every stage from S3 to quote,
and the script does not warn.

---

## 7. Fitness as the S3–S10 acceptance gate

**The mechanism is sound; the gate is not yet self-defending, and its documentation is not yet
trustworthy on the three points it singles out as caveats.**

What it genuinely buys, and I could verify statically: the profile is off by default, scoped to `test`,
version-aligned with the platform, applied to both modules that own tests, defined once per pom, and it
touches **not one** upstream test file — verified against `upstream-main` (§4), which is the property the
whole B3 choice rests on. Every arithmetic identity in the measurement document reconciles (§2.1), which
is more than most of the numbers in this evidence base can say.

What stops it being a gate I would sign off:

1. **No floor (D-01).** A gate whose only failure mode is "a test failed" cannot detect "no tests ran".
   Delete one of the two profile blocks and both commands stay green. The port's own harness contract
   forbids exactly this shape of pass. Until a machine asserts a minimum class/method count under the
   profile, "both commands green" and "the 287 upstream methods ran" are different claims, and only the
   first is checked.
2. **The gate document cannot be trusted on its own caveats (D-02, D-03).** Two of three are wrong about
   the tree, and one of them points a future stage at editing an untouched upstream test — the one thing
   ground rule 3 forbids absolutely.
3. **The record still tells a stage to do the not-taken thing (D-04, D-06, D-07).** §9 of the
   specification gives the one-command gate and the skeleton/bug-for-bug recommendations; the ledger says
   `DEFER` on 29 rows; the *normative* contract mandates prose-stated input domains. A stage that
   follows any of those does the wrong thing while reporting a green gate — which is worse than no gate,
   because the green is real.

None of the three is expensive to fix and none invalidates the measurement. But as it stands, "S3–S10
acceptance runs both commands" is a rule stated in two documents and absent from the third, enforced by
nothing, defended by a caveat section that mis-cites its own subject.
