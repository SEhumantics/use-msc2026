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

### Why this matters to the port

1. **"Full suite green" is close to a vacuous acceptance gate.** It is named as the acceptance
   criterion for S3 and for each of S4–S7. On this baseline it asserts that 13 methods, 11 of which
   are ArchUnit dependency-cycle checks, still pass.

2. **It is the mechanism behind the previous port's blind spot.** The earlier port modified 41
   upstream test files — including `TypeTest.java` at +1172/−1096 — and still reported a green
   suite. It reported green because those files are not executed. The edits could not have produced
   a failure regardless of what they changed.

3. **Rule 3 is currently unenforceable by testing.** "Never edit an upstream test to make ported
   code pass" has no automatic signal behind it: an edit to a dormant test changes nothing
   observable. Rule 3 has to be enforced by diff review (`git diff --stat upstream-main -- '*/src/test/*'`),
   not by the suite.

4. **S10's non-regression step is vacuous as written.** "Run upstream-main's test tree unmodified
   against the ported `use-core` and report the delta" yields a 13-method delta unless the dormant
   tree is actually made to execute.

### Proposed remedy (raised for decision — see the S0–S2 report)

Revive the dormant upstream tree as an **out-of-tree verification oracle**: a separate,
test-only harness that adds `junit-vintage-engine` and runs upstream-main's test sources
*unmodified* against the ported `use-core` classes. This keeps all three constraints intact —
upstream tests are not edited (rule 3), the product build keeps no vintage engine (target
toolchain), and non-regression stops being vacuous. It is folded into S1 as a second oracle
alongside the historical-jar differential harness.

## 4. Acceptance

| Criterion | Status |
|---|---|
| `port-uncertainty-2` is `30d480db` plus at most the housekeeping commit | met — `d0bf18aa` housekeeping only; `git diff --stat 30d480db..HEAD` touches `.gitignore` alone |
| Baseline counts recorded with the command that produced them | met — §2 |
| Working tree clean | met |
| `main` / `upstream-main` untouched | met |
