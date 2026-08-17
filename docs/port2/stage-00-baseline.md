# S0 — Baseline and branch

Branch `port-uncertainty-2`, created from `upstream-main` at `30d480db`.
All commands below were run in `~/msc-4/use-msc2026` on the housekeeping commit `d0bf18aa`.

| | |
|---|---|
| Java | `openjdk 21.0.11 2026-04-21` |
| Maven | `3.9.16` |
| Reactor | `use` 7.5.0 → `use-core`, `use-gui`, `use-assembly` |

## 1. Housekeeping — the untracked working tree

`git status` on `upstream-main` showed three groups of untracked files. Each was inspected before
being dealt with; none were carried into the port.

| Files | What they are | Disposition |
|---|---|---|
| `docs/archunit-results/{cycles,layers}-current-failure-report.txt` | Written by the ArchUnit tests during `mvn test`. `docs/archunit-results/README` (tracked) says the directory itself must stay, and its contents are regenerated. | `.gitignore` |
| `use-gui/*_cyclic_dependencies_*_results.csv` (10 files) | Written by `use-gui/src/test/java/org/tzi/use/architecture/{Ant,Maven}CyclicDependenciesGUITest.java` and `MavenCyclicDependenciesCoreTest.java`. | `.gitignore` |
| `scripts/audit/{ExprDiff.java,p3_differential.sh}` | Leftovers from the earlier port on `origin/main` — its P3.2 differential driver. Untrusted per the port-2 ground rules. | Moved out of the repository to `~/msc-4/output/prev-port-leftovers/`, retained as a hypothesis source only. |

Generator provenance was established by content grep, not assumption:

```bash
grep -rln "cyclic_dependencies\|archunit-results" --include=*.java --include=*.ps1 --include=*.xml --include=*.md . | grep -v reference-repositories
```
```
use-gui/src/test/java/org/tzi/use/architecture/MavenLayeredArchitectureTest.java
use-gui/src/test/java/org/tzi/use/architecture/AntCyclicDependenciesGUITest.java
use-gui/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesGUITest.java
use-core/src/test/java/org/tzi/use/architecture/MavenCyclicDependenciesCoreTest.java
```

Working tree is clean after the housekeeping commit.

## 2. Baseline test counts

```bash
mvn -q clean && mvn -B test
```
`BUILD SUCCESS`, total time 31.6 s, exit code 0.

**Executed: 3 test classes, 13 test methods, 0 failures, 0 errors, 0 skipped.**

| Module | Test class | Tests run |
|---|---|---|
| `use-core` | `org.tzi.use.architecture.MavenCyclicDependenciesCoreTest` | 11 |
| `use-core` | `org.tzi.use.uml.mm.ModelAPITest` | 1 |
| `use-gui` | `org.tzi.use.architecture.MavenLayeredArchitectureTest` | 1 |

Reproduce the per-class figures with:

```bash
for f in use-core/target/surefire-reports/*.txt use-gui/target/surefire-reports/*.txt; do echo "$(basename "$f" .txt): $(grep -oE 'Tests run: [0-9]+' "$f" | head -1)"; done
```

**Pre-existing failures: none.**

### Correction — `mvn test` is the wrong gate; the baseline is 143, not 13

The figures above are real but they measure the wrong thing, and this was caught later during an
audit of this stage. `mvn test` runs surefire only. The reactor also configures
**maven-failsafe-plugin**, and `mvn verify` runs a second, much larger tier that `mvn test` never
touches:

```bash
mvn -B verify -Djava.awt.headless=true
```
```
--- failsafe:2.22.2:integration-test (default) @ use-core ---
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 - in org.tzi.use.OCLExpressionIT
--- failsafe:2.22.2:integration-test (default) @ use-gui ---
Tests run: 129, Failures: 0, Errors: 0, Skipped: 0 - in org.tzi.use.main.shell.ShellIT
BUILD SUCCESS
```

| Tier | `use-core` | `use-gui` | total |
|---|---|---|---|
| surefire (`mvn test`) — the figure recorded above | 12 | 1 | **13** |
| failsafe (`mvn verify` only) | 1 | 129 | **130** |
| **true baseline** | 13 | 130 | **143** |

Both integration classes are JUnit 5 Jupiter, so unlike the dormant tree in §3 they *do* execute —
just never under `mvn test`. `ShellIT` drives the 131 `.use` fixtures under
`use-gui/src/it/resources/testfiles/shell/`.

**Why this matters more than the raw number.** These 130 tests are the port's most relevant
oracle and were absent from every acceptance gate as originally written:

* `use-core/src/it/java/org/tzi/use/OCLExpressionIT.java` is an **OCL expression** test — precisely
  the subsystem the uncertainty port modifies most.
* `ShellIT`'s fixtures include the three files that make blocking decision **B4** (the `equals`
  keyword collision) a live risk: `testfiles/shell/t098.use:11` and
  `testfiles/shell/imports/t133_import_date.use:29` declare an operation named `equals`, and
  `testfiles/shell/imports/t133_import_datetime.use:12` calls it. If the fork's
  `identicalExpression` rule makes `equals` a reserved token, **`ShellIT` is what catches it** —
  and only under `mvn verify`.

**Consequence for every later stage: the acceptance command is `mvn verify`, not `mvn test`.**
Every "suite green" claim in S3–S10 must be made against `mvn verify`. Stage S1's recorded counts
were taken with `mvn test` and are correct as far as they go, but they are not the whole gate.

Post-S1 state, for reference: `mvn verify` = 28 surefire + 130 failsafe = **158 methods, 0
failures**, confirmed on a fresh `git clone` of the branch.

## 3. Load-bearing finding — the upstream test tree is ~93% dormant

The baseline is 13 methods across 3 classes. The repository contains **41 `*Test.java` files**
(plus 14 `AllTests.java` JUnit 3 aggregators). **38 of the 41 never execute.**

Cause, established directly:

* `use-core/pom.xml` declares `junit-jupiter` 5.7.0 and `archunit-junit5` 1.3.0.
* `grep -rn "vintage" pom.xml use-core/pom.xml use-gui/pom.xml use-assembly/pom.xml` → **no match**.
  There is no `junit-vintage-engine` anywhere in the reactor.
* Therefore only JUnit 5 Jupiter `@Test` methods are discovered. JUnit 3 `junit.framework.TestCase`
  subclasses and JUnit 4 `org.junit.Test` methods are silently ignored — not skipped, not reported,
  simply never collected.

Flavour census (`use-core/src/test`, 51 `.java` files):

```bash
echo "junit.framework (JUnit3): $(grep -rl 'junit\.framework' use-core/src/test --include=*.java | wc -l)"
echo "org.junit.Test  (JUnit4): $(grep -rl 'import org\.junit\.Test' use-core/src/test --include=*.java | wc -l)"
echo "org.junit.jupiter:        $(grep -rl 'org\.junit\.jupiter' use-core/src/test --include=*.java | wc -l)"
```
```
junit.framework (JUnit3): 45
org.junit.Test  (JUnit4): 14
org.junit.jupiter:         3
```

Every upstream oracle this port would be measured against is in the dormant set:

| Class | Flavour | Executes? |
|---|---|---|
| `org/tzi/use/uml/ocl/type/TypeTest.java` | `junit.framework` | no |
| `org/tzi/use/uml/ocl/expr/ExpStdOpTest.java` | `junit.framework` | no |
| `org/tzi/use/uml/ocl/value/ValueTest.java` | `junit.framework` | no |
| `org/tzi/use/parser/USECompilerTest.java` | `junit.framework` | no |
| `org/tzi/use/uml/ocl/expr/ExpQueryTest.java` | `junit.framework` | no |

A worked illustration of how quiet this failure mode is:
`use-core/src/test/java/org/tzi/use/uml/mm/MImportedModelTest.java` imports
`static org.junit.jupiter.api.Assertions.*` (line 30) but annotates with `org.junit.Test` (line 21).
It looks like a modern Jupiter test, compiles, and produces **no surefire report at all**.

Approximate dormant method count — the two greps overlap on files that import both flavours, so
these are upper bounds, not a partition:

```bash
grep -rhcE '^\s*(public\s+)?void\s+test[A-Z_]' $(grep -rl 'junit\.framework' use-core/src/test use-gui/src/test --include=*.java) | paste -sd+ | bc   # 206
grep -rhc '@Test' $(grep -rl 'import org\.junit\.Test' use-core/src/test use-gui/src/test --include=*.java) | paste -sd+ | bc                        # 148
```

Order of magnitude: **a few hundred upstream test methods exist and none of them run.**

### What the previous port did about it — measured, not assumed

An earlier draft of this section asserted that the previous port's green suite was green *because*
these files never ran. **That is wrong, and the correction matters**, because it changes the
diagnosis.

`origin/main` **migrated the upstream tests from JUnit 3 to Jupiter**, which made them execute:

```bash
for f in org/tzi/use/uml/ocl/type/TypeTest org/tzi/use/uml/ocl/value/ValueTest \
         org/tzi/use/uml/ocl/expr/ExpStdOpTest org/tzi/use/parser/USECompilerTest; do
  echo "$f  junit.framework=$(git show origin/main:use-core/src/test/java/$f.java | grep -c 'junit\.framework')" \
       " jupiter=$(git show origin/main:use-core/src/test/java/$f.java | grep -c 'org\.junit\.jupiter')"
done
```
```
TypeTest         junit.framework=0  jupiter=3
ValueTest        junit.framework=0  jupiter=2
ExpStdOpTest     junit.framework=0  jupiter=3
USECompilerTest  junit.framework=0  jupiter=4
```

`origin/main` adds no `junit-vintage-engine` either, so the migration was the *only* way those
tests could run — and it did make them run. The previous port's suite was therefore **not** empty.

The blind spot is real but its mechanism is different, and worse: **the oracle was re-authored by
the same change that it was supposed to judge.** `TypeTest.java` at +1172/−1096 is a hand-rewrite of
upstream USE's own type-conformance oracle, performed as part of the port. Once the assertions
themselves have been re-typed by hand, "green" stops meaning "still behaves like upstream USE" and
starts meaning "agrees with what the porter wrote down". That is why rule 3 exists, and it is why a
migration-based revival is not an acceptable fix here.

### Why this matters to the port

1. **"Full suite green" is close to a vacuous acceptance gate on *this* branch.** It is named as
   the acceptance criterion for S3 and for each of S4–S7. On this baseline it asserts that 13
   methods, 11 of which are ArchUnit dependency-cycle checks, still pass.

2. **Rule 3 is currently unenforceable by testing.** "Never edit an upstream test to make ported
   code pass" has no automatic signal behind it: an edit to a dormant test changes nothing
   observable. On this branch rule 3 has to be enforced by diff review
   (`git diff --stat upstream-main -- '*/src/test/*'`), not by the suite.

3. **S10's non-regression step is vacuous as written.** "Run upstream-main's test tree unmodified
   against the ported `use-core` and report the delta" yields a 13-method delta unless the dormant
   tree is actually made to execute — *without* being rewritten first.

## 4. The dormant tree is not rotten — it is green. Measured.

Before proposing anything, the obvious question was measured: if those tests were made to run
against **clean upstream**, would they even pass? A throwaway `git worktree` was created at
`b7aaa99c`, `junit-vintage-engine` 5.7.0 (test scope) was added to `use-core/pom.xml` and
`use-gui/pom.xml` — **one dependency block each, no test file touched** — and the reactor was run:

```bash
git worktree add --detach /tmp/probe port-uncertainty-2
# add junit-vintage-engine 5.7.0, test scope, to use-core/pom.xml and use-gui/pom.xml
mvn -B test
```

**`BUILD SUCCESS`. Zero failures, zero errors, zero skipped.**

| | Baseline (Jupiter only) | With `junit-vintage-engine` |
|---|---|---|
| distinct test classes | 3 | **43** |
| distinct test methods | 13 | **300** |
| failures / errors | 0 / 0 | **0 / 0** |

Per module, deduplicated (surefire's raw `use-core` headline is `Tests run: 871`, inflated by the 14
`AllTests` aggregators re-running their member classes; the distinct figure is the meaningful one):

| Module | distinct classes | distinct methods | failures | errors |
|---|---|---|---|---|
| `use-core` | 35 | 283 | 0 | 0 |
| `use-gui` | 8 | 17 | 0 | 0 |

Deduplication command, run over the probe's surefire XML:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for mod in ('use-core','use-gui'):
    seen={}
    for f in glob.glob(f'{mod}/target/surefire-reports/TEST-*.xml'):
        r=ET.parse(f).getroot(); n=r.get('name'); t=int(r.get('tests'))
        if n not in seen or t>seen[n][0]:
            seen[n]=(t,int(r.get('failures')),int(r.get('errors')))
    real={k:v for k,v in seen.items() if not k.endswith('AllTests')}
    print(mod, len(real), sum(v[0] for v in real.values()),
          sum(v[1] for v in real.values()), sum(v[2] for v in real.values()))
PY
```

The largest revived oracles are exactly the ones the port must not break:
`MImportedModelTest` 55, `TypeTest` 38, `ExpQueryTest` 13, `ExprNavigationTest` 12,
`ValueTest` 11, `ExpStdOpTest` 11, `USECompilerTest` 2 (corpus-driven).

### Re-measured, because the first probe's evidence was destroyed

The first probe's worktree was deleted immediately after measuring, so its surefire XML did not
survive and an audit correctly graded the 43/300 figure **UNVERIFIABLE** — which mattered, because
blocking decision **B3** is argued from it. It was therefore re-run at `8789e035`, this time with
the surefire reports preserved outside the repository:

```bash
git worktree add --detach <scratch>/vintage2 HEAD
# add junit-vintage-engine 5.7.0, test scope, to use-core/pom.xml and use-gui/pom.xml
mvn -B test
cp -r use-core/target/surefire-reports use-gui/target/surefire-reports <evidence-dir>/
```

**`BUILD SUCCESS`. 45 distinct test classes, 315 distinct test methods, 0 failures, 0 errors.**

| Module | distinct classes | distinct methods | failures | errors |
|---|---|---|---|---|
| `use-core` | 37 | 298 | 0 | 0 |
| `use-gui` | 8 | 17 | 0 | 0 |

This **corroborates** the original figure rather than replacing it. The first probe ran at
`b7aaa99c`, before S1; this one runs at `8789e035`, after S1 added `HistoricalOracleIsolationTest`
(9) and `UncertaintyDifferentialSmokeTest` (6):

```
43 classes + 2 = 45        300 methods + 15 = 315
```

The delta is exactly S1's own tests, which is the expected result and is why the two numbers differ.

Both probe worktrees were removed afterwards; the branch carries no vintage dependency. Note this
probe used `mvn test`, so it does **not** include the 130 failsafe tests recorded above — reviving
the dormant tree and running the integration tier are independent gains that add up.

### Proposed remedy (raised for decision — see the S0–S2 report)

Add `junit-vintage-engine` at **test scope** to `use-core` (and `use-gui`) so that upstream's own
test files execute **exactly as upstream wrote them**, and never migrate or edit them.

This is a one-line-per-module change with a measured outcome (300 green methods, zero failures) and
it is strictly additive: it removes no Jupiter test and rewrites no assertion. It makes rule 3
enforceable by the suite rather than by diff review, and it turns S10's non-regression step from a
13-method formality into a 300-method check against assertions upstream authored.

It does depart from the stated target toolchain ("JUnit 5 Jupiter, **no vintage engine**"), which is
why it is a decision for the user and not a judgement call taken unilaterally. Two ways to take it:

* **(a) In the product build** — simplest, and every stage's "suite green" gate becomes meaningful
  immediately. Cost: the reactor carries a vintage engine, contradicting the target toolchain.
* **(b) In a separate Maven profile or verification module** — the default build stays vintage-free
  as specified; non-regression runs under `mvn -Pupstream-oracle test`. Cost: a little build
  machinery, and the gate has to be run deliberately rather than by default.

Recommendation: **(b)**, with the profile run as part of every stage's acceptance. It satisfies the
toolchain constraint as written while still making the oracle real. Either way the upstream test
files themselves stay byte-for-byte untouched, which is the property that actually matters.

## 5. Acceptance

| Criterion | Status |
|---|---|
| `port-uncertainty-2` is `30d480db` plus at most the housekeeping commit | met — `d0bf18aa` housekeeping only; `git diff --stat 30d480db..HEAD` touches `.gitignore` alone |
| Baseline counts recorded with the command that produced them | met — §2 |
| Working tree clean | met |
| `main` / `upstream-main` untouched | met |
