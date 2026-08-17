# Audit 03 — Did S0, S1 and S2 actually meet their own acceptance criteria?

Auditor pass. Read-only on the repository except this file. No Maven was run anywhere.
Every number below was produced by a command shown next to it, run on
`port-uncertainty-2` at `aeb4d860`. Scratch work lived in
`/tmp/claude-1000/-home-xoruser-msc-4/5a883e17-9055-4019-8f36-a743005556fa/scratchpad`.

Note on tooling: this shell rewrites bare `grep` through a proxy that strips the `./` prefix
from recursive results, which silently breaks any `grep -v "^./…"` filter. **Every grep below was
re-run as `/usr/bin/grep`.** One apparent S2 refutation evaporated when I did this; see §S2-6.

---

## Verdict summary

| Stage | Criterion, as written | Verdict |
|---|---|---|
| S0 | branch = `30d480db` + at most the housekeeping commit | **MET** (at the S0 boundary) |
| S0 | baseline counts recorded with the command that produced them | **PARTIALLY MET** — command given, output paraphrased not pasted |
| S0 | working tree clean | **MET** |
| S0 | *(attack)* is the baseline number honest? | **NOT MET** — misses the entire integration-test tier, ~130 tests, unmentioned |
| S1 | a command runs the smoke comparison and prints agreement | **MET** — independently reproduced, byte-identical |
| S1 | rows carry (input, historical, ported, verdict) + the seed | **MET** |
| S1 | fails loudly on a missing jar rather than skipping | **MET** — independently reproduced |
| S1 | *(assessment)* is S1 honestly done given verdict DEFECTIVE? | **NO** — criteria met as written; the instrument is still defective and the stage report does not say so |
| S2 | every row cites a historical file and symbol | **MET** on 8 independent spot-checks (+1 known error, B4) |
| S2 | operation counts stated and reproducible by a grep the report gives | **PARTIALLY MET** — all 5 counts reproduce; 4 of 5 blocks give an unrunnable elided path |
| S2 | *(cross-cutting)* report exists with acceptance commands + pasted output | **NOT MET** — S2 has no stage report and no acceptance section at all |

---

## S0

### S0-1 — Branch descends from `30d480db`; first commit is housekeeping only — **MET**

```
$ git merge-base --is-ancestor 30d480db HEAD && echo "ANCESTOR: YES"
ANCESTOR: YES

$ git log --oneline 30d480db..HEAD
aeb4d860 docs(port2): complete the S2 specification (was committed truncated)
ccd2d58d docs(port2): waiver ledger — zero waivers through S2
8c410c98 docs(port2): S2 specification — the work-list, operation tables, 12 blocking decisions
6074344e docs(port2): S1 empirical refutation — harness runs, but AGREE_THROWN can swallow
37e240b9 docs(port2): S2 section files and S1 static refutations
dfc3c063 S1: differential harness for the uncertainty port
bc2970a0 docs(port2): correct the S0 diagnosis, and measure the dormant tree
b7aaa99c docs(port2): S0 baseline — counts, and the dormant upstream test tree
d0bf18aa chore(port2): ignore ArchUnit test-run artifacts

$ git diff --stat 30d480db d0bf18aa
 .gitignore | 5 +++++
 1 file changed, 5 insertions(+)
```

The housekeeping commit `d0bf18aa` touches `.gitignore` and nothing else. At the S0 boundary
(`b7aaa99c`) the only non-doc delta from `30d480db` is those five ignore lines. **MET.**

Read strictly at HEAD the criterion no longer holds — HEAD is nine commits past `30d480db`,
one of which (`dfc3c063`) adds 4515 lines of test code and two binary jars. That is S1's work,
not a violation of S0; recorded so the criterion is not silently re-scoped later.

**MINOR — commit message describes an action the commit does not contain.** `d0bf18aa`'s body
says *"Also removed from the tree: scripts/audit/{ExprDiff.java,p3_differential.sh}"*, but those
paths were never tracked:

```
$ git ls-tree -r 30d480db --name-only | grep -i "scripts/audit"
(none tracked)
```

They were untracked working-tree files. The disposition was real; the commit is not the record of
it. Harmless, but a reviewer reconciling message against diff will stall on it.

### S0-2 — Baseline counts recorded with the reproducing command — **PARTIALLY MET**

`docs/port2/stage-00-baseline.md` §2 gives the command (`mvn -q clean && mvn -B test`), a
per-class table, and a second reproduction loop over `surefire-reports/*.txt`. What it does **not**
do is paste the output. The build result appears as prose — *"`BUILD SUCCESS`, total time 31.6 s,
exit code 0"* — and the counts appear as a hand-built markdown table. There is no surefire
transcript anywhere in the file.

The numbers are nonetheless corroborated by artefacts still on disk:

```
$ for f in use-core/target/surefire-reports/*.txt use-gui/target/surefire-reports/*.txt; do \
    echo "$(basename "$f" .txt): $(grep -oE 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' "$f" | head -1)"; done
org.tzi.use.architecture.MavenCyclicDependenciesCoreTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
org.tzi.use.uml.mm.ModelAPITest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
org.tzi.use.uncertainty.differential.HistoricalOracleIsolationTest: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
org.tzi.use.uncertainty.differential.UncertaintyDifferentialSmokeTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
org.tzi.use.architecture.MavenLayeredArchitectureTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

11 + 1 + 1 = 13 across exactly the three classes the report names. Consistent.

**UNVERIFIABLE — the vintage probe.** The "43 classes / 300 methods, 0 failures" figure came from a
throwaway worktree that §4 says was deleted. No surefire XML from it survives in this tree, and the
report's own dedup script reads `{use-core,use-gui}/target/surefire-reports/TEST-*.xml` from that
worktree. Nothing in the repository lets a reader re-derive 300. Under the no-Maven rule I cannot
check it; it must be re-measured before B3 is decided on it.

**Reproducibility gap — surefire is unpinned.**

```
$ /usr/bin/grep -n "surefire" pom.xml use-core/pom.xml use-gui/pom.xml
(no output)
```

`maven-failsafe-plugin` is pinned to 2.22.2 in both modules, but the surefire version — the plugin
that produced the baseline — is whatever Maven 3.9.16's super-POM binds. The baseline is not
version-anchored.

### S0-3 — Working tree clean — **MET**

```
$ git status --porcelain
(empty)
```
Run as the first command of this audit. (It is no longer empty: a parallel auditor has since
created the untracked `docs/port2/audit-04-wildcard.md`. That is not S0's doing.)

### S0-4 — ATTACK: what the baseline number misses — **NOT MET**

`mvn test` does not run the integration-test tier, and `stage-00-baseline.md` does not mention it.

```
$ /usr/bin/grep -n "src/it\|failsafe\|IT\.java\|integration" docs/port2/stage-00-baseline.md
NO MENTION of src/it, failsafe, or IT.java in stage-00-baseline.md
```

**Mechanism, established from the poms.** Both modules bind failsafe to `integration-test` and
`verify` (`use-core/pom.xml:311-322`, `use-gui/pom.xml:262-273`). `mvn test` stops at the `test`
phase, so failsafe never fires. Separately, `build-helper-maven-plugin` adds `src/it/java` as a
**test source root** at `process-resources` (`use-core/pom.xml:280-296`,
`use-gui/pom.xml:229-243`), so the ITs *compile* during `mvn test` — and then surefire's default
includes (`**/Test*.java`, `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`) do not match
`*IT.java`, so they are never selected. Both halves are visible in the existing build output:

```
$ find use-core/target/test-classes use-gui/target/test-classes -name "*IT*.class"
use-core/target/test-classes/org/tzi/use/OCLExpressionIT.class
use-gui/target/test-classes/org/tzi/use/main/shell/ShellIT.class

$ ls use-core/target/failsafe-reports/ use-gui/target/failsafe-reports/
(no failsafe reports => ITs never ran)
```

Compiled, never executed, no report, no skip line. Exactly the silent-omission failure mode the S0
report diagnoses for JUnit 3 — one tier lower, and undiagnosed.

**Volume missed.**

```
$ find use-core/src/it use-gui/src/it -name "*.java"
use-core/src/it/java/org/tzi/use/OCLExpressionIT.java
use-gui/src/it/java/org/tzi/use/main/shell/ShellIT.java

$ find use-gui/src/it/resources/testfiles/shell -name "*.in" | wc -l
129
$ find use-core/src/it use-gui/src/it -name "*.use" | wc -l
145
```

`ShellIT.evaluateExpressionFiles()` is a `@TestFactory` (`ShellIT.java:63-85`) that walks
`testfiles/shell` and emits one `DynamicTest` per `.in` file — **129 dynamic tests**, each booting
USE's shell against a `.use` model and diffing real output. `OCLExpressionIT` adds one. So
`mvn test` omits roughly **130 executable end-to-end tests** on top of the dormant `*Test.java`
tree. I found no third tier: `use-assembly` has no `src/test` or `src/it`, and `use-core/src/it`
has no resources directory (its pom adds only `add-test-source`, not `add-test-resource`), so
`use-core`'s IT really is the single trivial `1 + 1` case.

**Why this undercuts the recorded baseline badly, not marginally.**

1. The report's headline finding — *"the upstream test tree is ~93% dormant"*, 38 of 41
   `*Test.java` — is computed over `*Test.java` only. The IT tier is invisible to that census, so
   the dormancy diagnosis is *incomplete in the direction that flatters the port*: it says the
   suite is empty, when in fact there is a large, live, end-to-end suite that a different Maven
   goal would run.
2. The 129 shell fixtures are the closest thing this repository has to a behavioural oracle for
   the OCL surface — which is precisely the surface the uncertainty port rewrites
   (`StandardOperationsAny`, the grammar, `ExpQuery`, `TypeFactory`). B4's own citation in the spec
   points into `use-gui/src/it/resources/testfiles/…`, so S2 is already reasoning about fixtures
   that S0 says nothing about.
3. Every downstream "full suite green" gate is written against `mvn test`. Under `mvn test` a
   change that breaks all 129 shell fixtures is green. Under `mvn verify` it is not. The gate
   named in S3–S7 is therefore weaker than the repository can actually offer, and S0 never
   surfaced the choice.
4. B3 offers the user a decision between vintage-in-build and vintage-in-profile. Neither option
   touches the IT tier, because S0 never noticed it. The decision is being taken on an incomplete
   picture.

**What the baseline should have said and did not:** `mvn test` = 13 methods; `mvn verify` adds
`OCLExpressionIT` (1) + `ShellIT` (129 dynamic) via failsafe; the S3–S10 gate should be `mvn verify`,
not `mvn test`.

---

## S1

Acceptance as written: *"A command runs a smoke comparison — historical `URealValue.add` vs a stub
— and prints agreement. The harness reports (input, historical result, ported result, verdict)
rows, and its seed. It fails loudly if a jar is missing rather than silently skipping."*

### S1-1 — A command runs the smoke comparison and prints agreement — **MET**

`stage-01.md` §5.1 gives `mvn -B -pl use-core test -Dtest=UncertaintyDifferentialSmokeTest` and
pastes a full transcript (seed, digests, corpus size, row count, tally, first 12 rows, report path,
surefire result). That is genuine pasted output, not a description.

Maven is off-limits, so I reproduced it **without Maven**: I compiled a 25-line driver against the
already-built `use-core/target/test-classes`, pointed the oracle at the vendored jars with
`-Duse.historical.jars.dir`, and wrote the reports into a scratch tree.

```
$ java -Duse.historical.jars.dir=…/use-core/src/test/resources/historical \
       -cp …/scratchpad/out:…/use-core/target/test-classes:…/use-core/target/classes AuditSmoke
seed=20260817 corpus=28
digests={use.jar=80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d, atenearesearchgroup.uncertainty.jar=53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0}
rows=784 summary=URealValue.add(value): 784 rows, AGREE=784
faulty rows=784 summary=URealValue.minus(value): 784 rows, AGREE=558, DIFFER=226
```

Every figure matches `stage-01.md` §5.1 exactly. Stronger: the regenerated reports are
**byte-identical** to the committed ones.

```
$ diff …/scratchpad/run/docs/port2/differential/audit-ureal-add.tsv docs/port2/differential/s1-smoke-ureal-add.tsv && echo "IDENTICAL add"
IDENTICAL add
$ diff …/scratchpad/run/docs/port2/differential/audit-ureal-minus-faulty.tsv docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv && echo "IDENTICAL minus"
IDENTICAL minus
```

The determinism claim is therefore not merely asserted — it survives a different JVM invocation,
a different working directory, a different entry point and a different classloader parentage.
The vendored jars hash as recorded:

```
$ sha256sum use-core/src/test/resources/historical/*.jar
53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0  …/atenearesearchgroup.uncertainty.jar
80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d  …/use.jar
```

The criterion also asks for a *fault-injection* counterpart implicitly ("prints agreement" is
worthless alone); S1 supplies one unprompted and it reproduces (226 DIFFER of 784). Credit where
due.

### S1-2 — Rows carry (input, historical, ported, verdict) and the seed — **MET**

```
$ head -10 docs/port2/differential/s1-smoke-ureal-add.tsv
# harness	differential-sweep/1
# seed	20260817
# reference	historical
# subject	stub-faithful
# sha256.use.jar	80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d
# sha256.atenearesearchgroup.uncertainty.jar	53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0
# operations	URealValue.add(value)
# rows	784
# verdict.AGREE	784
index	operation	inputs	historical	ported	verdict	note
```

All four required columns are present under those exact names, plus a `note` column, and the seed
is in the header of **both** TSVs. The header tallies match the body:

```
$ awk -F'\t' '!/^#/ && $1 ~ /^[0-9]+$/ {print $6}' docs/port2/differential/s1-smoke-ureal-add.tsv | sort | uniq -c
    784 AGREE
$ awk -F'\t' '!/^#/ && $1 ~ /^[0-9]+$/ {print $6}' docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv | sort | uniq -c
    558 AGREE
    226 DIFFER
```

Header says `# verdict.AGREE 784` and `558 / 226` respectively. Self-consistent.

### S1-3 — Fails loudly on a missing jar — **MET**

`stage-01.md` §5.3 pastes a real stack trace with every path tried. I reproduced the loud failure
independently by pointing the authoritative override at an empty directory:

```
$ java -Duse.historical.jars.dir=…/scratchpad/emptyjars -cp … AuditSmoke
Exception in thread "main" org.tzi.use.uncertainty.differential.HistoricalOracle$HistoricalOracleUnavailableException: historical oracle jar 'use.jar' was not found and -Duse.historical.jars.dir is set, so no other location was consulted. Paths tried:
  -Duse.historical.jars.dir (authoritative) -> …/scratchpad/emptyjars/use.jar
	at org.tzi.use.uncertainty.differential.HistoricalOracle.materialise(HistoricalOracle.java:178)
	at org.tzi.use.uncertainty.differential.HistoricalOracle.open(HistoricalOracle.java:129)
```

Throw, not skip; every consulted path named; the override is deliberately non-fallback
(`HistoricalOracle.java:169-179`) so a mis-pointed override cannot be masked by the committed
copies. This clause is met better than it is written.

### S1-4 — ASSESSMENT: is S1 honestly "done"? — **NO**

The three written criteria are met. That is the problem: **they do not test the thing that is
broken.**

D1 is real, unfixed, and I reproduced the refuter's exact number. `DiffVerdict.isAgreement()`
counts `AGREE_THROWN` as agreement:

```java
// use-core/src/test/java/org/tzi/use/uncertainty/differential/DiffVerdict.java:32-34
public boolean isAgreement() {
    return this == AGREE || this == AGREE_THROWN;
}
```

and `DifferentialSweep.classify` decides `AGREE_THROWN` on throwable **class name alone**
(`DifferentialSweep.java:124-131`), discarding message and cause. The smoke test's pass criterion
is `assertEquals(List.of(), result.disagreements())`
(`UncertaintyDifferentialSmokeTest.java:70`). So a sweep in which the operation was never invoked
on either side passes.

I built a `Candidate` that only throws, mimicking the class the oracle throws for out-of-range
boundary inputs, and swept it against the shipped `uBooleanBoundaries()` corpus:

```
$ java … AuditD1b java.lang.IllegalStateException
oracle throws: java.lang.IllegalStateException : historical constructor threw for UBOOLEAN(false,2.0)
rows=81 summary=UBooleanValue.and(value): 81 rows, AGREE_THROWN=32
AGREE_THROWN=32
```

32 of 81 rows scored as agreement for a candidate that implements nothing. That is the refuter's
figure to the row. (Control: with a *different* throwable class the same 32 rows come back as
`DIFFER_THROWN=32, MIXED=49` — confirming the 32 are the oracle-throws cases and the verdict flips
purely on class-name identity.)

D3 and D5 also check out:

```java
// DiffReportWriter.java:62-66 — guard is on the results list, not the row count
if (results.isEmpty()) {
    throw new IllegalArgumentException("refusing to write an empty differential report: "
            + "a report with no rows would read as agreement");
}
```
```
$ git ls-files docs/port2/differential/
docs/port2/differential/s1-smoke-ureal-add.tsv
docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
```
`DiffReportWriter.REPORT_DIR = "docs/port2/differential"` (`:42`) — running the suite writes into
tracked files.

**The honesty problem is not D1 itself; it is where D1 is recorded.**

```
$ git log --oneline -- docs/port2/stage-01.md
dfc3c063 S1: differential harness for the uncertainty port

$ /usr/bin/grep -n "D1\|AGREE_THROWN\|DEFECTIVE" docs/port2/stage-01.md
(no mention)
```

`stage-01.md` was written before the refutation and **never amended**. It carries §5's
"Acceptance — commands and pasted output" and asserts the criteria met. The document that says the
instrument is DEFECTIVE is a *different file* (`stage-01-refutation-empirical.md`, committed one
commit later), and the stage report does not reference it. A reader who opens the stage report to
ask "is S1 done?" gets an unqualified yes.

S4–S7 are specified to plug the port into this `Candidate` interface and use `disagreements()` as
the gate. With D1 unfixed, the gate is *conditionally* vacuous: any input for which the harness
cannot marshal a value into the historical constructor scores free agreement for whatever the port
does. `uBooleanBoundaries()` — shipped, used by the tests — already contains 32/81 such rows for
`and`. **S1 should not be treated as closed until D1's recommended fix (a distinct
`HARNESS_ERROR` verdict, and `assertEquals(rowCount, count(AGREE))` in place of
`disagreements().isEmpty()`) is applied, or until an explicit waiver is recorded.**
`docs/port2/upstream-test-waivers.md` currently records **zero** waivers.

---

## S2

Acceptance as written: *"Every row cites a historical file and symbol. Operation counts per type
are stated and reproducible by a grep the report gives."*

### S2-1 — Operation counts reproduce — **MET (the numbers)**

I ran all five blocks from `specification.md` §2.1–§2.5 verbatim, with `/usr/bin/grep`.

| Block | Command family | Claimed | Actual |
|---|---|---|---|
| §2.1 `:261-266` | `registerOperation(new Op_uBoolean_` / `^final class Op_uBoolean_` / distinct `return "…";` | 14 / 14 / 14 | **14 / 14 / 14** |
| §2.2 `:324-331` | `Op_ureal_` reg / class / distinct names | 18 / 18 / 18 | **18 / 18 / 18** |
| §2.2 | `return OPERATION;` / `return false;` | 18 / 18 | **18 / 18** |
| §2.3 `:381-386` | `Op_uInteger_` class / `registerOperation(` / `return OPERATION;` | 12 / 13 / 12 | **12 / 13 / 12** |
| §2.4 `:428-434` | `Op_uString_` class / reg / `return OPERATION;` | 21 / 22 / 21 | **21 / 22 / 21** |
| §2.4 | duplicate-registration probe | `2 new Op_uString_uConcat` | **`2 new Op_uString_uConcat`** |
| §2.5 `:485-491` | enum constants / `name()` bodies / distinct names | 39 / 39 / 39 | **39 / 39 / 39** |
| §2.5 | the documented TRAP, `grep -c 'new OpGeneric()'` | 45 | **45** |

Raw:

```
UBoolean: reg=14 cls=14 names=14  [14 14 14]
UReal: 18 18 18 OPERATION=18 false=18  [18 18 18 18 18]
UInteger: cls=12 reg=13 OPERATION=12  [12 13 12]
UString: cls=21 reg=22 OPERATION=21  [21 22 21]
SBoolean: enum=39 name=39 distinct=39 trap=45  [39 39 39 45]
```

The equivalent per-file reproduction blocks in `spec-parts/20-ops-*.md` (which use slightly
different regexes) also reproduce: `20-ops-UString.md:785` → 21, `20-ops-UInteger.md:819` → 12,
`20-ops-SBoolean.md:1055-1057` → 39/39/39, `20-ops-UBoolean.md:930` → 14. No count in this audit
failed.

### S2-2 — …but 4 of the 5 blocks cannot be copy-pasted — **PARTIALLY MET**

The criterion is *"reproducible by a grep the report gives."* The report gives an **elided path**
in four of five cases:

```
specification.md:325   F=…/StandardOperationsUReal.java
specification.md:382   F=…/StandardOperationsUInteger.java
specification.md:429   F=…/StandardOperationsUString.java
specification.md:486   cd …/uml/ocl/expr/operations
```

Only §2.1 (`:262`) carries a runnable path. Pasting any of the other four into a shell yields
`No such file or directory`. The full paths exist in the `20-ops-*.md` section files, so the
information is recoverable — but the assembled `specification.md`, which is the artefact the
criterion is about, does not by itself give a runnable grep for four of five types. I had to
substitute the path from §2.1 to run them.

Same defect at `specification.md:1921`: it quotes *"`grep -cE '^\s+public void test'` sums to
122"* without naming the files being summed. The file list lives only in
`16-modernization-ledger.md:195-203`. Run there, the ledger's commands are exact:

```
extends TestCase [8]: 8
public void test  [122]: 122
assertEquals(     [954]: 954
CF-7 pattern      [10]: 10
F-1 decimals      [101]: 101
M-39 new OpGeneric [45]: 45
F-1 round in target MathUtil [no output]: (exit 1)
```

Summed over the wrong eight files the answer is 135, not 122 — which is exactly the trap the
assembled spec sets by omitting the list.

### S2-3 — A reproduction command that does NOT reproduce its stated output — **FINDING**

`specification.md:925-932` presents this as the `UndefinedValue` drift evidence:

```bash
diff <(sed 's/[[:space:]]*$//' F/uml/ocl/value/UndefinedValue.java) \
     <(sed 's/[[:space:]]*$//' T/uml/ocl/value/UndefinedValue.java)
# 46c43
# <         return sb.append("Undefined");
# ---
# >         return sb.append("null");
```

Run with the aliases the document defines in its own preamble (`F/`, `T/`), the actual output is:

```
20,21d19
< // $Id: UndefinedValue.java 1759 2010-09-10 12:32:19Z lhamann $
<
29d26
<  * @version     $ProjectVersion: 0.393 $
46c43
<         return sb.append("Undefined");
---
> return sb.append("null");
```

Three hunks, not one. The transcript was trimmed without saying so. **This is a second instance of
exactly the defect the document itself flags at `:1201`** — *"the grep `15-upstream-delta.md`
quotes for this actually emits nine lines, not the three shown … the transcript was edited without
saying so (refuter F6 adopted)."* That one was caught and annotated; this one was not. Where a
document's discipline is "paste what ran", two silent trims mean the discipline is aspirational,
not enforced.

The substantive conclusion survives — `Undefined` → `null` is real, and the dependent count is
right:

```
$ cat FT/parser/uncertainty/*.in | /usr/bin/grep -cE '^[[:space:]]*->.*Undefined'
79
```
matching B6's *"79 corpus entries"* (`:37`, `:936`, `:2384`).

By contrast `specification.md:1024-1028` (the `OpGeneric` diff, "one hunk, the fork's own 7 lines")
**does** reproduce exactly: one hunk, `92,98d91`, seven lines.

### S2-4 — Every row cites a historical file and symbol — **MET on spot-checks**

I picked eight citations at random across different sections and resolved each one:

| Citation | Where | Result |
|---|---|---|
| E11: `isTypeOfOclAny()` → 3 hits at `TypeImpl:313`, `MClassifierImpl:355`, `OclAnyType:33` | `:175` | **exact**, all three line numbers correct |
| E13: `grep -c "public void visit"` → 49 | `:177` | **correct** — 49 in the *target* file (the fork's is 57; the row cites `T/`, so it is right) |
| §1.2 `UncertainType` used at 11 sites in 3 files, line-by-line | `:~205` | **exact** — all 11 line numbers resolve (`StandardOperationsCollection:104,169,401,474`; `StandardOperationsNumber:351,946,1024,1101,1179`; `StandardOperationsAny:49,199`) |
| M-11: `UStringValue.java:86-87` is the broken `equals` conjunction | `:1975` | **exact** — `:86` is `wrapper.getString().equals(ustring.wrapper)`, `:87` is `wrapper.getsConf() == wrapper.getsConf()` |
| §1.1: `UStringValue` `equals` `:79` | `:93` | **exact** — `:79` is the method signature; §1.1 and §7.2 cite different anchors of the same defect, both correct |
| `OCLBase.gpart:495-500` five-way LA(1) literal dispatch | `:1440` | **exact** in the fork grammar — `:495` is the `'UString' LPAREN …` alternative, `:495-500` spans all five |
| `ExpQuery.java:179-221` = `evalUSelect` | `:1481` | **exact** — `:179` is `protected final Value evalUSelect(EvalContext ctx)` |
| `UString`/`SBoolean` absent from the corpus | `:1419`, `:1429` | **exact** — `grep -c` is 0 in all four `.in` files; same for `uCount` (G1) |

No fabricated citation found in this sample. The one known error is the lead's B4 finding
(missing `shell/` path segment), which is a path typo, not a fabricated symbol.

### S2-5 — Other stated numbers, re-derived — all reproduce

```
$ (§1 inventory command, run verbatim)
common files: 539   [spec claims 539]
touched files: 24   [spec claims 24]
new files (comm -23): 70

$ (§6.1 corpus / test census)
per-class: 5 3 3 32 39 27 14 12 46 1      [matches the §6.1 table row for row]
R2 total: 182                              [claim 182]
junit.framework.TestCase files: 10         [claim 10]
@Test per file: 0 for all ten              [claim 0]

$ (§5.2 corpus)
UBooleanExpression.in: 118 | UCollectionOperations.in: 44 | UIntegerExpression.in: 692 | URealExpression.in: 573
total: 1427                                [claim 1427]

$ (§7 ledger)
CF: 10  M: 51  F: 16  BEHAVIOUR-PRESERVING: 46  BEHAVIOUR-CHANGING: 33      [all as claimed]

$ (§1.8 / §4 negatives)
sealed|permits in src/main: no output      [as claimed]
module-info.java: exactly 2                [as claimed]
repo-wide U-type fixture grep: no output   [as claimed]
find uDataTypes -name pom.xml -o -name build.gradle*: nothing   [as claimed]
```

**§3 type lattice — the executable oracle actually runs and actually agrees.**

```
$ bash docs/port2/spec-parts/11-types-oracle.sh /tmp/…/types-oracle
===== FORK (USE-Uncertainty) =====
# conformsTo true = 47 of 144
SUP UBoolean [OclAny, SBoolean, UBoolean]
SUP UInteger [OclAny, UInteger, UReal]
…
===== 7.5.0 BASELINE =====
# conformsTo true = 22 of 49
===== classic 7x7 block: fork vs 7.5.0 =====
IDENTICAL — the uncertainty extension changes no classic-type cell
```

The script is genuinely read-only (copies sources into a workdir, `javac` only, no Maven) and its
final assertion — the one §3.1 calls *"the sanity check that constrains the whole port"* — holds.
I then rebuilt §3.1's grid from the oracle's raw `CONF` lines and it matches **cell for cell**:

```
pairs: 121   true: 39                      [spec claims "39 of the 121 ordered pairs are true"]
UBoolean    T . . . T . . . . . T
UInteger    . T T . . . . . . . T
UReal       . . T . . . . . . . T
UString     . . . T . . . . . . T
SBoolean    . . . . T . . . . . T
Boolean     T . . . T T . . . . T
Integer     . T T . . . T T . . T
Real        . . T . . . . T . . T
String      . . . T . . . . T . T
OclVoid     T T T T T T T T T T T
OclAny      . . . . . . . . . . T
```

**MINOR reproducibility nit:** the oracle prints `47 of 144` (12 types, incl. `UnlimitedNatural`);
the spec states `39 of 121` (11 types). A reader running the given command sees a different number
and must independently know to drop `UnlimitedNatural` and re-count. The document does not say so.

### S2-6 — A refutation I withdrew

`specification.md:2266-2270` claims `grep -rIl "SBoolean" . | grep -v "^./src/main" | grep -v "^./lib"`
returns one file. Through this shell's grep proxy it returned 47 — because the proxy strips the
`./` prefix and the filters stop matching. With the real binary:

```
$ /usr/bin/grep -rIl "SBoolean" . | /usr/bin/grep -v "^./src/main" | /usr/bin/grep -v "^./lib"
./src/test/org/tzi/use/uml/ocl/type/TypeTest.java
```

One file, as claimed. Recorded because it is a live hazard for anyone re-running these greps in
this environment, not a defect in S2.

### S2-7 — CROSS-CUTTING: S2 has no report and no acceptance section — **NOT MET**

The brief: *"A stage is not done until its report exists and its acceptance commands were actually
run and their output pasted in."*

```
$ ls docs/port2/
differential  spec-parts  specification.md  stage-00-baseline.md
stage-01-refutation-empirical.md  stage-01-refutation-fidelity.md
stage-01-refutation-isolation.md  stage-01.md  upstream-test-waivers.md
```

There is no `stage-02*.md`. S0 has `## 5. Acceptance` (a four-row status table);
S1 has `## 5. Acceptance — commands and pasted output` (with real transcripts). **S2 has neither.**

```
$ /usr/bin/grep -in "acceptance" docs/port2/specification.md
34:   | … the acceptance gate of S3, S4, S5, S6, S7, S10 |
1547: | … |
2077: **Tier-3 fix list … If the acceptance criterion is …
2381: | … run as part of every stage's acceptance …
```

All four hits are about *other* stages' gates. Nowhere does S2 state its own criteria and show
them satisfied.

And on "output pasted in": `specification.md` records expected values as trailing shell comments
(`# 14`, `# 18 18 18`, `# 39 DISTINCT`), not as pasted transcripts. That is a compact and honest
convention, and — as §S2-1 shows — every one of those annotations is *true*. But it is a claim
about output, not output. Where the document does paste something that looks like a transcript
(§S2-3), it turned out to be edited. So the format that would have caught the trim is precisely
the one S2 does not use.

**Consequence:** S2's own acceptance was never self-assessed. This audit is the first time those
greps were run end-to-end by anyone other than the author. They passed — but that was not knowable
from the repository before now.

---

## What a reader should take away

1. **S0's baseline number is not the repository's test coverage.** It is the coverage of one Maven
   goal. Roughly 130 integration tests exist, compile on every build, and are never selected, and
   the S0 report does not mention them. Fix the gate (`mvn verify`) before any stage is measured
   against "suite green".
2. **S1's instrument works and is reproducible to the byte — and is still defective.** D1 is real
   (32/81 free agreements reproduced). The stage report does not mention it. Close D1 or record a
   waiver before S4 plugs the port into this harness.
3. **S2's numbers are sound.** Every operation count, every census, every ledger tally and the full
   conformance grid reproduce. The failures are of *form*: four unrunnable elided paths, one
   silently trimmed transcript, and no acceptance section at all.
