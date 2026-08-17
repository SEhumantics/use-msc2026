# Audit 04 — wildcard: what is missing, wrong, or dangerous that nobody looked at

**Scope.** Everything outside the harness, the citation set, and the acceptance criteria.
**Posture.** Assume broken until checked. Every claim below names the command that produced it.

**Target state.** `port-uncertainty-2` @ `8789e035`.

> **The branch moved during this audit.** At audit start `git log --oneline` showed HEAD =
> `aeb4d860` (10 entries incl. `30d480db`). Minutes later a tenth branch commit had landed:
> ```
> $ git reflog -1
> 8789e035 HEAD@{0}: commit: docs(port2): correct the baseline — mvn test is the wrong gate (143, not 13)
> ```
> Commit time `2026-08-17 08:00:03 +0700`. "S0/S1/S2 complete" was therefore **not a frozen
> state** when this audit was commissioned, and the S0 baseline changed *underneath* the S2
> specification that had been declared complete five minutes earlier (`aeb4d860`, 07:55:36).
> Everything below is measured against `8789e035`.

---

## Verdict

**SOUND_WITH_CAVEATS.** No rule violation, no product change, no classpath leak, no
origin/main contamination — all of that survived a deliberate attempt to break it (§ "Checked and
could not break", below). But three things are wrong or missing in ways that matter for S3+:

1. The baseline test suite **cannot fail**. 12 of its 13 methods contain no assertion (F1).
2. The S2 specification — the document S3 is driven from — is **stale against S0's own
   correction**, and never mentions the 130-method failsafe tier at all (F2).
3. The two vendored binaries have **no recorded provenance and an unresolved licence position**
   (F5), against a claim in `stage-01.md` that provenance *is* recorded.

---

## F1 — CRITICAL. Twelve of the thirteen baseline "tests" assert nothing and cannot fail

Every stage report frames the baseline as "3 test classes, 13 test methods, 0 failures", and
`specification.md` §9 row 3 describes the gate as asserting "13 methods, 11 of which are ArchUnit
cycle checks". **They are not checks.** They are reporters.

`use-core/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesCoreTest.java` — the whole
file's assertion surface:

```
$ grep -n "assert\|Assert\|\.check(" use-core/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesCoreTest.java
173:                .should().beFreeOfCycles()
```

That line is inside `countCyclesForPackage`, which ends (`:171-180`):

```java
EvaluationResult result = SlicesRuleDefinition.slices()
        .assignedFrom(sliceAssignment)
        .should().beFreeOfCycles()
        .allowEmptyShould(true)
        .evaluate(classes);          // evaluate() returns; only check() throws
int cycleCount = result.getFailureReport().getDetails().size();
writeResultsToFile(cycleCount, result, packageName, withTests);
return cycleCount;
```

Each of the 11 `@Test` methods then does `System.out.println("… " + cycleCount);` and returns.

`use-gui/.../MavenLayeredArchitectureTest.java:39-54` is the same shape — `rule.evaluate(classes)`,
`System.out.println(violationCount)`, `writeResultsToFile(...)`, no assertion.

**Proof that they pass while violations exist**, from the last real build in this working copy:

```
$ tail -3 docs/archunit-results/cycles-current-failure-report.txt
    (151 further dependencies have been omitted...)

Cycle count: 55

$ grep -m1 "Tests run" use-core/target/surefire-reports/org.tzi.use.architecture.MavenCyclicDependenciesCoreTest.txt
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.028 s -- in org.tzi.use.architecture.MavenCyclicDependenciesCoreTest
```

**55 dependency cycles detected. Eleven tests. Zero failures.**

The only surefire method on the branch's baseline that contains assertions is
`use-core/src/test/java/org/tzi/use/uml/mm/ModelAPITest.java` (1 `@Test`, 6 `assert*` calls):

```
$ grep -c "assert" use-core/src/test/java/org/tzi/use/uml/mm/ModelAPITest.java
6
$ grep -n "@Test" use-core/src/test/java/org/tzi/use/uml/mm/ModelAPITest.java
10:    @Test
```

**Consequence.** `stage-00-baseline.md` §3 calls the gate "close to vacuous". It is stronger than
that: the surefire gate is **one assertion-bearing test method**, and it is not a
uncertainty-relevant one. An S3–S7 change that introduces arbitrary package cycles, arbitrary
layering violations, or any behaviour not touched by `ModelAPITest` produces `BUILD SUCCESS`.
Every "suite green" sentence in S0–S2 is true and means almost nothing.

**Impact on S3.** The B3 decision is currently argued as "13 methods, 11 of which are ArchUnit
cycle checks" → it should be argued as "1 assertion-bearing method". That materially strengthens
the case for B3 and it is not on the record.

---

## F2 — MAJOR. The S2 specification is stale against S0's own correction, and omits the failsafe tier entirely

`stage-00-baseline.md` (as of `8789e035`) now carries a section headed:

> `### Correction — mvn test is the wrong gate; the baseline is 143, not 13`

and concludes, in bold:

> `**Consequence for every later stage: the acceptance command is mvn verify, not mvn test.**`
> `Every "suite green" claim in S3–S10 must be made against mvn verify.`

`specification.md` has not been updated and does not know this. Line 34 (§0, decision **B3**):

> `| **B3** | … baseline is 13 methods / 3 classes. …`

Line 2381 (§9, consolidated action list, row 3):

> `| **3** | … Without it the S3–S7 "suite green" gate asserts 13 methods, 11 of which are ArchUnit cycle checks |`

And the failsafe tier appears nowhere in the specification:

```
$ grep -c "mvn verify" docs/port2/specification.md
0
$ grep -c "failsafe" docs/port2/specification.md
0
$ grep -c "OCLExpressionIT" docs/port2/specification.md
0
```

(`ShellIT` appears once, at `specification.md:1540`, as the driver of shell fixtures — not as an
acceptance gate.)

The failsafe tier is real and is configured in both modules:

```
$ grep -n "maven-failsafe-plugin" use-core/pom.xml use-gui/pom.xml
use-core/pom.xml:311:                <artifactId>maven-failsafe-plugin</artifactId>
use-gui/pom.xml:262:                <artifactId>maven-failsafe-plugin</artifactId>
$ find use-core/src/it use-gui/src/it -name '*.java'
use-core/src/it/java/org/tzi/use/OCLExpressionIT.java
use-gui/src/it/java/org/tzi/use/main/shell/ShellIT.java
$ ls use-gui/src/it/resources/testfiles/shell/*.use | wc -l
131
```

`OCLExpressionIT` is an **OCL expression** integration test — the exact subsystem the port
modifies most — and it was outside every acceptance gate written in S0, S1 and S2.

**Impact on S3.** S3 will be executed from `specification.md`. As it stands, that document tells
the implementer the gate is 13 surefire methods. The 130-method failsafe tier that contains the
port's most relevant oracle is invisible in it, including in decision **B3** and in §9, the
"numbered action list" explicitly written "to stand alone".

**Secondary.** The correction landed *after* S2 was declared complete. Any statement of the form
"S0, S1 and S2 are complete" is a statement about a baseline that has since been restated by a
factor of 11.

---

## F3 — MAJOR. B3's recommended remedy requires editing `use-gui/pom.xml`, which ground rule 5 forbids. Nobody records the conflict.

Ground rule 5: *do not touch `use-gui` or `use-assembly`.*

`stage-00-baseline.md` §4, describing the probe that produced the 43/300 figure:

> `junit-vintage-engine 5.7.0 (test scope) was added to use-core/pom.xml and use-gui/pom.xml — one dependency block each`

and the proposed remedy:

> `Add junit-vintage-engine at test scope to use-core (and use-gui) …`

`specification.md:34` (B3) repeats it: `adding vintage 5.7.0 test-scope to use-core+use-gui`.

The recommendation on record for B3 is option **(b)**, "a `-Pupstream-oracle` profile, run as part
of every stage's acceptance". A profile that revives use-gui's dormant tests **must modify
`use-gui/pom.xml`.**

Nothing on the branch reconciles this with rule 5. `upstream-test-waivers.md` states only:

> `use-gui and use-assembly carry no source change at all.`

A `pom.xml` is arguably not "source", but that reading is nowhere written down, and the waiver
ledger is precisely the document that should carry it. The ledger's forward-looking section names
only **B5** as an upcoming rule-3 waiver; **B3's rule-5 exposure is not mentioned at all.**

**Also unstated, and needed to take the decision:** confining vintage to `use-core` gives
**35 classes / 283 methods**, not 43/300 — the split is in `stage-00-baseline.md` §4's own
per-module table (use-core 35/283, use-gui 8/17), but the figure quoted in B3 and in §9 is the
combined 43/300. Whoever answers B3 under rule 5 needs the 35/283 number and is not given it.

---

## F4 — MAJOR. `mvn test` mutates tracked files inside the repository, and the committed "evidence" is regenerated by the test that produces it

`UncertaintyDifferentialSmokeTest.java:63` and `:99`:

```java
Path report = DiffReportWriter.write("s1-smoke-ureal-add.tsv", result, oracle.loadedDigests());
Path report = DiffReportWriter.write("s1-smoke-ureal-minus-faulty.tsv", result, oracle.loadedDigests());
```

`DiffReportWriter.java:42` `REPORT_DIR = "docs/port2/differential"`, resolved by
`reportDir()` (`:126-137`) by walking up from the process working directory.

**Measured, not assumed.** A probe compiled against the already-built test-classes, run from
surefire's working directory:

```
$ cd /home/xoruser/msc-4/use-msc2026/use-core && java -cp "$CP:/tmp/auditprobe" Probe
cwd        = /home/xoruser/msc-4/use-msc2026/use-core
reportDir  = /home/xoruser/msc-4/use-msc2026/docs/port2/differential
```

Both target files are **tracked** (added in `dfc3c063`):

```
$ git ls-files docs/port2/differential/
docs/port2/differential/s1-smoke-ureal-add.tsv
docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
$ ls -la docs/port2/differential/
-rw-r--r-- 1 xoruser xoruser 100086 Aug 17 04:50 s1-smoke-ureal-add.tsv
-rw-r--r-- 1 xoruser xoruser 100021 Aug 17 04:50 s1-smoke-ureal-minus-faulty.tsv
```

Mtime `Aug 17 04:50` is a test run; `git status --porcelain` is empty, so the last run reproduced
the committed bytes exactly. **Nothing enforces that.** Three consequences:

1. **A test writes into the source tree, not `target/`.** Any future non-determinism (row order,
   a new corpus, a locale-dependent format) dirties the working tree during an acceptance run,
   and the natural reflex is `git checkout --` or `git commit -am`, either of which destroys the
   signal.
2. **The committed TSVs are not a regression baseline; they are a log.** The smoke test's
   assertions (`:67-72`) check `rowCount`, `Files.isReadable(report)`, `disagreements() == []`
   and `count(AGREE) == rowCount`. **No assertion compares the report against the committed
   copy.** So the file cannot detect drift; it can only silently record it. Anything that cites
   `docs/port2/differential/*.tsv` as pinned evidence is citing a file the suite overwrites.
3. Combined with F1, the branch's build now has *two* classes of "test" that report to a file
   instead of asserting (ArchUnit's, and the differential reports) — with the difference that
   ArchUnit's destination is gitignored (F8) and the harness's is tracked.

Note the same commit (`dfc3c063`) that adds the generator also commits its output, so
`git show --stat dfc3c063` mixes hand-written test source with 1 589 lines of machine-generated
TSV. That is not a rule-4 violation (rule 4 is behaviour-vs-modernization, and no product code has
been written yet) but it is the reason the mutation went unnoticed.

---

## F5 — MAJOR. The two vendored binaries have no recorded provenance, and the licence evidence on record is for a different artifact than the one committed

`stage-01.md` §2 justifies committing the jars with:

> `Copying once, by hand, into a tracked location converts a build dependency into a committed artifact with **a recorded provenance** and a recorded digest.`

**The digest is recorded. The provenance is not.** Neither jar has an upstream URL, repository,
commit id, tag, or build reference recorded anywhere in `docs/port2/`:

```
$ grep -rn "github.com\|http" docs/port2/stage-01.md
(no output)
```

### 5a. `use.jar` — a GPL-2 object build with no corresponding source in the repository

```
$ unzip -p use-core/src/test/resources/historical/use.jar META-INF/MANIFEST.MF
Manifest-Version: 1.0
Ant-Version: Apache Ant 1.9.15
Created-By: 1.8.0_275-8u275-b01-0ubuntu1~18.04-b01 (Private Build)
Main-Class: org.tzi.use.main.Main
Class-Path: antlr-3.4-complete.jar atenearesearchgroup.uncertainty.jar …

$ unzip -l use-core/src/test/resources/historical/use.jar | grep -c '\.class$'
1057
$ unzip -l use-core/src/test/resources/historical/use.jar | grep -E "URealValue|UncertainType|StandardOperationsU"
   1649  org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.class
   1627  org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.class
   1875  org/tzi/use/uml/ocl/expr/operations/StandardOperationsUReal.class
   2207  org/tzi/use/uml/ocl/expr/operations/StandardOperationsUString.class
    190  org/tzi/use/uml/ocl/type/UncertainType.class
   5086  org/tzi/use/uml/ocl/value/URealValue.class
```

This is a 2021 build of the **uncertainty fork of USE**. The repository is GPL-2
(`COPYING` = "GNU GENERAL PUBLIC LICENSE Version 2, June 1991"). The corresponding source is
**not in the repository**:

```
$ git ls-files | grep -iE "URealValue|UncertainType|uDataTypes"
(no output)
```

It exists only in the untracked `.git/reference-repositories`. Distributing a GPL-2 work in object
form obliges the distributor to accompany it with the corresponding source or a written offer
(GPL-2 §3). No document on the branch addresses this. Nor does any document record the jar's
licence at all — `grep -in "licen" docs/port2/stage-01.md` → no output.

### 5b. `atenearesearchgroup.uncertainty.jar` — no manifest, no licence file, Eclipse cruft, and the licence on record belongs to a *different build*

```
$ unzip -p …/atenearesearchgroup.uncertainty.jar META-INF/MANIFEST.MF
caution: filename not matched:  META-INF/MANIFEST.MF
$ unzip -l …/atenearesearchgroup.uncertainty.jar | grep -iE "licen|copying|notice"
(no output)
$ unzip -l …/atenearesearchgroup.uncertainty.jar | head -8
      366  2021-02-23 00:11   .classpath
       36  2021-02-23 00:29   .gitignore
      386  2021-02-23 00:11   .project
        0  2021-02-24 19:48   .settings/
      857  2021-02-23 00:11   .settings/org.eclipse.jdt.core.prefs
```

`specification.md:1206` records:

> `**Licence:** $UDT/Libraries/Java/README.md:261-269 — **MIT**, "Copyright (c) 2023 Atenea Research group" … MIT is GPL-2-compatible, so vendoring into GPL-2 USE is legally sound provided the notice travels with the copied files.`

Two problems. (i) That evidence is the **2023 source tree's** README; the artifact actually
committed is the **2021 build**, which the spec itself elsewhere describes as having "**no
`META-INF`**" (`specification.md:31`, B1) — confirmed above. The 2021 build carries no notice, and
no notice travelled with it. (ii) The spec *does* flag "⚠ jar has no licence metadata" — but only
in the constraint table at `specification.md:1226`, as a strike against the **rejected** options B
and C. The same defect applies to the jar the S1 harness actually depends on today, and there it is
not flagged.

**Impact on S3+.** This is not a technical blocker; it is a publication/redistribution blocker for
a thesis artifact, and it is permanent — both jars are now in git history and cannot be removed
without a history rewrite. It should be a 13th blocking decision, or at minimum a recorded
provenance block in `stage-01.md` §2 naming the source repository and commit for each jar.

---

## F6 — MINOR. Cross-document contradiction: which ground rule loses its automatic signal

| Document | Claim |
|---|---|
| `specification.md:34` (B3) | `"full suite green" is a near-vacuous S3–S7 gate and **ground rule 4** has no automatic signal` |
| `stage-00-baseline.md` §3 item 2 | `**Rule 3** is currently unenforceable by testing` |
| `upstream-test-waivers.md` | `Standing caution — **rule 3** currently has no automatic signal` |

Rule 4 is the separate-commits rule (behaviour changes vs modernization); no test engine bears on
it. `specification.md` mis-numbers. Small, but §0 is the section a human reads first, and it is the
one place the rule number is wrong.

---

## F7 — MINOR. Commit `d0bf18aa`'s message describes a tree change the commit does not contain

```
$ git show --stat --format='%s%n%n%b' d0bf18aa | tail -8
Also removed from the tree: scripts/audit/{ExprDiff.java,p3_differential.sh},
leftovers from the earlier port attempt on origin/main. …

 .gitignore | 5 +++++
 1 file changed, 5 insertions(+)
```

The commit changes one file. Neither named path was ever in the tree it branched from, nor on
`origin/main`:

```
$ git ls-tree -r 30d480db  --name-only | grep -c scripts/audit   # 0
$ git ls-tree -r origin/main --name-only | grep -c scripts/audit # 0
```

`scripts/audit/*` is tracked only on `port-verification-verdict` (`c45d6612`, `82f068d9`), and the
files there are `p1_*.py/.sh` and `p2_*.py/.sh` — not `ExprDiff.java` / `p3_differential.sh`.
So the housekeeping row in `stage-00-baseline.md` §1 ("Moved out of the repository to
`~/msc-4/output/prev-port-leftovers/`") is **not verifiable from this repository**, and its
attribution ("the earlier port on `origin/main`") is wrong on the evidence available here.

This is provenance hygiene, not a rule breach — but S0's §1 table is the branch's only record of
what was in the working tree at branch time, and one of its three rows cannot be checked.

---

## F8 — MINOR. The `.gitignore` change forecloses the only remaining visibility on architecture regressions

`d0bf18aa` adds `/docs/archunit-results/*-current-failure-report.txt`. That file was never tracked
(`git ls-tree -r 30d480db --name-only | grep archunit-results` → only `docs/archunit-results/README`),
so nothing was *lost* from git. But given F1 — the ArchUnit tests never assert — that report is the
**sole artifact** that carries the cycle count (`Cycle count: 55`). Before the change it at least
showed up in `git status` as an untracked file after every run; now it is invisible.

Net position on the branch today: package cycles in `use-core` are (a) not asserted, (b) not
tracked, (c) not gitignore-visible, and (d) not mentioned as a baseline anywhere in
`specification.md` (`grep -c "Cycle count\|55 cycles" docs/port2/specification.md` → 0). The port
can add arbitrary cycles to `use-core` with no signal at all.

---

## F9 — MINOR. S2 has no stage report

```
$ ls docs/port2/
differential/  spec-parts/  specification.md  stage-00-baseline.md
stage-01-refutation-empirical.md  stage-01-refutation-fidelity.md
stage-01-refutation-isolation.md  stage-01.md  upstream-test-waivers.md
```

S0 has `stage-00-baseline.md` ending in a §5 **Acceptance** table. S1 has `stage-01.md` plus three
refutations. **S2 has no `stage-02*.md`** — no statement of what S2's acceptance criteria were, no
record that they were met, no commands. `specification.md` is the *deliverable*; it is not a stage
report, and it does not report on itself. The claim "S2 is complete" therefore has no artifact
behind it in the shape the other two stages set.

---

## F10 — MINOR. S0's central diagnosis is derived from `origin/main`, the source the port is told not to inherit from

`stage-00-baseline.md`, section "What the previous port did about it — measured, not assumed",
runs `git show origin/main:use-core/src/test/java/…` over four files and concludes:

> `That is why rule 3 exists, and it is why **a migration-based revival is not an acceptable fix here.**`

Measuring `origin/main` in order to *describe* `origin/main` is defensible. Using that measurement
to **reject a remedy for port-2** lets the untrusted source shape a port-2 decision (it is the
argument that pushes B3 toward vintage-engine rather than migration). Whether that is permitted is
a governance question, and nobody has recorded an answer either way.

---

## Checked adversarially and could not break

Each of these was an attempt to find a violation, not to confirm one.

**Rule 5 — `use-gui` / `use-assembly` untouched.**
```
$ git diff --stat 30d480db..HEAD -- use-gui use-assembly
(no output)
```

**Rule 7 — `main` and `upstream-main` never committed to.**
```
$ git rev-parse main origin/main upstream-main origin/upstream-main
3cc8e72b0a572392f7cfa66bc29d6c84e1effdf7
3cc8e72b0a572392f7cfa66bc29d6c84e1effdf7
30d480dbcca2f404b1350039516a56f46c1efb1f
30d480dbcca2f404b1350039516a56f46c1efb1f
```

**Nothing the harness added changes the product.**
```
$ git diff --stat 30d480db..HEAD -- '*/src/main/*'     # (no output)
$ git diff --stat 30d480db..HEAD -- '*pom.xml' 'pom.xml'  # (no output)
$ git log  --oneline 30d480db..HEAD -- '**/module-info.java'  # (no output)
```
The full `git diff --name-status 30d480db..HEAD` is 41 paths: 40 `A`, one `M` (`.gitignore`).

**Rule 4 (behaviour vs modernization in separate commits).** Checked all ten commits with
`git show --stat`. No commit contains product code, so rule 4 has **not yet been exercised** —
compliance is vacuously true, not demonstrated. `dfc3c063` is the only code commit and is
test-scope-only.

**The vendored jars are on no product classpath.** Four independent leak paths checked and closed:
```
$ grep -rn "historical" --include=pom.xml .        # (no output)
$ grep -rn "test-jar"   --include=pom.xml .        # (no output)
$ grep -rn "systemPath\|additionalClasspathElement" --include=pom.xml .  # (no output)
```
No shade plugin exists in the reactor. `use-assembly/src/assembly/assembly.xml`'s only `use-core`
fileSet is `${project.basedir}/../use-core/target` with `<include>*.jar</include>` — a single-segment
glob that matches `target/use-core.jar`, not `target/test-classes/historical/*.jar`. Maven places
the jars only at:
```
$ find use-core/target -name 'use.jar' -o -name 'atenearesearchgroup.uncertainty.jar'
use-core/target/test-classes/historical/use.jar
use-core/target/test-classes/historical/atenearesearchgroup.uncertainty.jar
```
`stage-01.md` §2 point 3's safety argument is correct as written.

**ArchUnit recorded cycle counts did not move.** The tracked-path report is produced with
`ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`, which excludes `target/test-classes`:
```
$ grep -c "uncertainty" docs/archunit-results/cycles-current-failure-report.txt
0
$ grep -rl "uncertainty.differential" use-core/target/archunit-results/
(no output)
```
The 15 added test methods are invisible to every recorded cycle figure.

**No `origin/main` contamination by path.** The two branches' diffs against `30d480db` overlap on
exactly one file, and its content differs:
```
$ comm -12 <(git diff --name-only 30d480db origin/main | sort) \
           <(git diff --name-only 30d480db port-uncertainty-2 | sort)
.gitignore
```
`origin/main` adds 3 lines (`use-gui/*_cyclic…`, `docs/archunit-results/*…`, one comment);
`port-uncertainty-2` adds 4 with different wording, leading `/` anchors, and an extra `use-core`
pattern. Two independent authors finding the same two build-artifact globs is not evidence of
copying, and I am not flagging it. No harness source file, doc, or structure on the branch has a
counterpart path on `origin/main`.

**Blocking-decision numbering is internally consistent.** §0 carries `B1, B1a, B2…B12`; §9 carries
`1, 1a, 2…12` mapping one-to-one; both say "twelve"; no `B`-reference anywhere in the document is
undefined (`grep -oE '\bB1[0-9]?[a-z]?\b|\bB[1-9][a-z]?\b' specification.md | sort -u` yields
exactly the defined set).

**The 33 BEHAVIOUR-CHANGING rows reproduce**, using the specification's own documented command:
```
$ F=docs/port2/spec-parts/16-modernization-ledger.md
$ grep -nE '^\| (CF-|M-|\*\*F-)' $F | grep -o 'BEHAVIOUR-CHANGING' | wc -l
33
$ grep -cE '^\| (CF-|M-|\*\*F-)' $F
77
```
The `46 + 33 = 79 vs 77` arithmetic is explicitly explained at `specification.md:1914` (M-48 and
M-49 counted in both buckets). Sound.

**The waiver ledger's "all 15 paths are `A`" is correct** for the scoped diff it actually quotes
(`-- '*/src/test/*' '*/src/main/*'`). It is not a claim about the whole diff, so the `M .gitignore`
line is not a contradiction.

**S1's `use-core` delta reproduces from the surefire reports on disk**: 11 + 1 + 9 + 6 = 27, i.e.
2 classes / 12 methods → 4 / 27, delta exactly +15. Matches `stage-01.md`.

**The harness's filesystem writes outside `docs/port2/differential` are safe.**
`HistoricalOracleIsolationTest:248-275` operates only on `Files.createTempDirectory(...)` and
deletes only paths under it; `HistoricalOracle:601` deletes only its own recorded temporaries.
No repo path is deleted anywhere.

---

## The single most damaging true thing a hostile thesis reviewer could say

> **"Your semantic oracle has never been run, and your regression gate cannot fail."**

Both halves are on the record, in the branch's own documents, and they compound:

* **The gate cannot fail.** Under `mvn test` the branch executes 13 methods; 12 of them contain no
  assertion whatsoever and pass while 55 dependency cycles are detected (F1). The remaining one is
  `ModelAPITest`. Meanwhile 38 of 41 upstream `*Test.java` are never collected
  (`stage-00-baseline.md` §3). So through S0–S2 the sentence "zero failures, suite green" is
  compatible with essentially any behaviour of the system.
* **The oracle has never been run.** The reference side of the differential harness is a 2021
  binary of the fork whose source is not in this repository (F5). The specification concedes, in
  its own residual-risk register: `R2 — **No historical test was ever run.** … **Whether all 182
  methods actually passed against lib/atenearesearchgroup.uncertainty.jar is unknown** (G9). Every
  "the fork pins X" claim means "the fork *asserts* X", not "the fork *demonstrates* X."`
  (`specification.md:2412`, and `G9` at `:1876`.)

The reviewer's conclusion writes itself: *Study A's ground truth is an unvalidated binary,
compared against a port whose non-regression evidence is a suite with one live assertion.* Every
per-operation table in the 2510-line specification is downstream of that, and the tables' quality —
which is genuinely high — does not repair it.

The good news is that both halves are already **diagnosed** on the branch (B3 for the gate, R2/G9
for the oracle). Neither is yet **fixed**, and F2 shows the gate half is currently drifting *out*
of the specification rather than into it. That is the thing to fix before S3, not after.

---

## Recommended additions to the blocking-decision set

Not fixes — decisions. Filed for the human who owns §0.

| # | Decision |
|---|---|
| **B13** | Provenance and licence of the two vendored binaries: record source repository + commit for each; resolve the GPL-2 §3 position on `use.jar`; carry the MIT notice for the *committed 2021* `atenearesearchgroup.uncertainty.jar`, or state why the 2023 README covers it (F5). |
| **B14** | Does the ArchUnit tier become assertive (a pinned cycle-count baseline that fails on increase), or is it explicitly written off as non-load-bearing? Today it is neither (F1, F8). |
| **B15** | Does `DiffReportWriter` keep writing into the tracked source tree, or move to `target/` with a separate, asserted golden-file comparison? (F4) |
| **B3 addendum** | Reconcile B3's remedy with ground rule 5, and record the `use-core`-only figure (35 classes / 283 methods) alongside the combined 43/300 (F3). |

---

*Audit performed read-only. No file under `/home/xoruser/msc-4/use-msc2026` was modified except this
report. No Maven was invoked; the one compilation performed (`javac`/`java` for the
`DiffReportWriter.reportDir()` probe in F4) ran against the pre-existing
`use-core/target/{classes,test-classes}` with all scratch output in `/tmp/auditprobe`.*
