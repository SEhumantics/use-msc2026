# S1.5 — Upstream shape-delta: fork base → USE 7.5.0

**Scope.** What the port must adapt across, subsystem by subsystem. This file's structure (sections
1-4) is now mirrored, in fuller and corrected form, by `specification.md` §4 (§4.1–§4.7 map onto this
file's §1–§7) — read there for the per-file walkthrough. What's kept here is what isn't folded in
there: §5's JPMS module-path lessons (reusable beyond this port) and §7's four-option `uDataTypes`
vendoring comparison (kept as backup evidence should that decision be revisited), plus the specific
load-bearing facts other docs still cite by `file:line` into §1–§4.

**Path shorthand used throughout:**

```
FORK   = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
FSRC   = $FORK/src/main/org/tzi/use
TSRC   = /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use
UDT    = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/uDataTypes
UP     = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/upstream-use
```

`$FORK`, `$UDT`, `$UP` are read-only reference material and are **never** build inputs.

---

## 0. Headline: the drift is far smaller than the ten-year gap suggests

> **The four OCL extension points the port needs — `Value`, `Type`/`TypeImpl`, `ExpressionVisitor`,
> `OpGeneric` — have barely moved in ten years.** `Value.java` and `OpGeneric.java` are *textually
> identical* between the fork and 7.5.0 except for the fork's own additions. The real risk is
> concentrated in (a) `MClassifier`, substantially reshaped by 2024's data-type work, (b) the
> build/module story, entirely new, and (c) the `uDataTypes` dependency, which has no Maven
> coordinates anywhere.

### 0.1 The fork's base is not 2015, it is ~2018

SVN `$Id$` keywords put the bulk of the fork's base at r5494 / 2015-02-05, but cherry-picks run to
`$FSRC/config/Options.java` at r6361 / 2018-04-05 (`RELEASE_VERSION = "0.142.0"`), and
`RealValue.java`/`IntegerValue.java` at r6289 / 2017-11-27. This matters practically: **the fork's
base already contains `Type.VoidHandling`**, which upstream introduced at `750fa544` (2015-02-05,
"Reintegrated PDM-branch, switch to USE Version 4"). So every `isKindOfX(VoidHandling)` signature in
the fork already matches 7.5.0 — eliminating what would otherwise be the single largest mechanical
adaptation in the type system.

**Language level.** The fork compiles at `source/target 1.7`; the target compiles at 21. Fork sources
use no removed constructs, but also none of Java 8+; modernising is a style decision with no
correctness content. JDK 21's `javac` cannot emit `-source 7` at all, so "copy verbatim and hope" is
not a strategy for anything that fails to compile.

---

## 1–4. Per-extension-point delta — condensed

Full per-file walkthrough for all four subsystems below is in `specification.md` §4.1–§4.4. What
follows is only the facts other docs cite directly into this section.

### 1. `Value` (`uml/ocl/value/Value.java`)

The fork adds four predicates (`isUInteger`, `isUReal`, `isUBoolean`, `isSBoolean`) to an otherwise
byte-identical `Value.java` — diff against 7.5.0 is exactly those four additions plus two dropped SVN
keyword lines. The real trap is elsewhere in the same package:
**`UndefinedValue.toString(StringBuilder)` prints `"Undefined"` in the fork, `"null"` in 7.5.0**
(upstream `72ab8fd7`, 2019-06-27, "changed Undefined to null"). This is a whole-suite systematic
offset, not a one-off: any expected-output fixture lifted verbatim from the fork's tests, or the
historical `.in` corpus (79 entries expect `-> Undefined : OclVoid`, `14-historical-tests.md` §5), is
wrong against 7.5.0 unless normalised — the decision must be recorded, because "the port prints
`null` where the oracle prints `Undefined`" is a *correct* port, not a regression. See
`specification.md` **B6**.

### 2. `Type` / `MClassifier`

`Type` gained exactly three interface members since the fork's base (`qualifiedName()`,
`isKindOfDataType(VoidHandling)`, `isTypeOfDataType()`); `TypeImpl` supplies a `false`-returning
default for every predicate a concrete type doesn't override, so a new type extending
`TypeImpl`/`BasicType` inherits correct behaviour for all three without implementing anything. The
real drift is `MClassifierImpl` (226 diff lines, `$Id`/whitespace excluded): `isSubClassOf` renamed
`isSubClassifierOf`; attributes and operations pulled up from `MClass` onto `MClassifier`
(`attributes()`, `allAttributes()`, `operations()` now declared on the interface); a new sibling
`MDataType extends MClassifier` means **`MClassifier ⇒ MClass` no longer holds** — code that assumes
it will `ClassCastException` at model-load time the moment a model declares a `dataType`.
`conformsTo(Type)` is single-dispatch, implemented per concrete type, no registry; the *second* half
of conformance, `getLeastCommonSupertype`, is what overload resolution actually goes through, not
`conformsTo` directly — forgetting to widen a new type's `allSupertypes()` type-checks fine in
isolation and then fails every overloaded operator (`=`, `+`, ...) with an opaque
`Undefined operation` message, sending the investigation to the wrong subsystem.

### 3. `Expression` / `ExpressionVisitor`

`Expression` is unchanged (diff is `$Id`/Javadoc only). `ExpressionVisitor` is a plain, non-sealed
interface in both trees (49 methods in 7.5.0, 57 in the fork — the extra 8 plus one rename,
`visitObjOp`→`visitInstanceOp`, upstream `46c277e7`) — dispatch is **not exhaustive** to the compiler,
so a new `Expression` subclass whose `processWithVisitor` calls the wrong case (copy-pasted from a
sibling) compiles clean and silently mis-covers or mis-prints; there is no compiler safety net here.
The real implementor set in 7.5.0 is small — `AbstractCoverageVisitor` and `ExpressionPrintVisitor`
(plus their inheritors, `use-gui` implements the interface nowhere). The fork's third implementor,
`AbstractMetricVisitor`, belongs to an `analysis/metrics` package that does not exist in 7.5.0 and is
unrelated to uncertainty.

### 4. `OpGeneric` and the operation registry

Zero drift apart from the fork's own six lines in `OpGeneric.java` (its five `StandardOperationsU*`
registrations plus a comment). `ExpStdOp` resolves by first-match-wins over a
`ListMultimap<String, OpGeneric>` populated once from `registerOperations`. **Registration order is
significant**: the fork inserted its five `StandardOperationsU*` registrations after
`StandardOperationsBoolean` and before `StandardOperationsCollection`. (That specific claim was later
re-measured for `+(Integer,Integer)` and found *not* to move for that operator — the fork registers no
`+` for numbers at all, so the real constraint for plain arithmetic is branch order inside
`Op_number_add.matches`, a related but different mechanism; see `adaptation-policy.md` §5 record 4 for
the correction. The general first-match-wins mechanism itself is not in dispute.) **Three-way merge
required** on exactly three files where fork and upstream both independently edited the same
functions: `StandardOperationsNumber` (763 diff lines — 7.5.0 independently added
`Op_number_pow`/`Op_number_sqrt`; taking the fork's file wholesale **deletes `pow`/`sqrt` from OCL**),
`StandardOperationsAny` (158 — fork adds `Op_identical`, rewrites `Op_equal`), `StandardOperationsCollection`
(307 — fork registers `uCount`/`uCountC`). `StandardOperationsBoolean`, `StandardOperationsString`
and `BooleanOperation` have zero diff lines.

---

## 5. Build and module story — JPMS lessons (reusable beyond this port)

**Fork:** Ant, single monolithic tree, 11 checked-in jars in `lib/`, `source/target=1.7`. Generated
ANTLR lexers/parsers are checked in next to the `.gpart` grammar sources.

**7.5.0:** Maven reactor (`use-assembly`, `use-core`, `use-gui`), `source/target=21` (introduced by
`767320db`, 2021-08-01, "Maven Build"; grammars moved under
`use-core/src/main/resources/grammars/` by `99ff26c2`). Lexers/parsers are generated at
`generate-sources` by `antlr3-maven-plugin` + supporting plugins.

**JPMS: yes — both `use-core` and `use-gui` carry a `module-info.java`.** `use-core`'s declares
`module use.core` with 11 `requires` / 32 `exports`; the exports this port needs
(`org.tzi.use.uml.ocl.{type,expr,expr.operations,value}`, `org.tzi.use.uml.mm`,
`org.tzi.use.parser.{ocl,use}`) are already present. No `<repositories>`, `system` scope or
`systemPath` exists anywhere in the reactor — every third-party jar (guava, antlr-runtime, jline,
combinatoricslib, jruby-core, vtd-xml, plus test-scoped guava-testlib/junit-jupiter/archunit) is an
ordinary Maven coordinate.

### 5.1 Does adding a package or a test dependency require touching `module-info.java`?

Answered from the surefire report of a real build, which records the JVM's actual `jdk.module.path`
vs `java.class.path`: **main code runs as the named module `use.core` on the module path; test code
runs on the classpath** (the unnamed module) — there is no `module-info.java` under `src/test` or
`src/it` anywhere in the reactor. Three conclusions follow:

1. **A test-scoped dependency needs no `requires`.** junit-jupiter, archunit and guava-testlib are
   compile-visible to tests and appear nowhere in the descriptor — exactly what a differential oracle
   harness needs.
2. **A compile-scoped dependency DOES need a `requires`, and Maven derives the module path *from*
   `module-info.java`, not from the POM.** Proof: `jline` is an ordinary compile dependency with **no**
   matching `requires jline`, and it lands on `java.class.path`, not `jdk.module.path` — it is
   currently unused only because nothing imports it. **Add a compile dependency without a matching
   `requires` and it will be invisible to your code; `javac` reports "package … does not exist" while
   the jar sits happily in the dependency tree.**
3. Classes added to an already-exported package need no descriptor change. A *new* package needs
   `exports` only if `use-gui` or **test** code must read it — test classes are in the unnamed module
   and cannot read a non-exported package of a named module.

The fork's uncertainty extension touches **no GUI file at all**, so `use-gui/module-info.java` should
not need to change.

**Risk if got wrong:**

* Putting new product classes in a fresh, unexported package and then writing tests against them
  produces `IllegalAccessError` **at runtime, in surefire only** — the code compiles, the IDE is
  happy, and only `mvn test` fails. This is the single most likely JPMS trap for this port.
* A test class sharing a package name with a module package (e.g. `org.tzi.use.uml.ocl.value`) sits
  in the unnamed module while the production class is in `use.core`; package-private access across
  that boundary is an `IllegalAccessError`. Any test must touch only `public` members of exported
  packages.

---

## 6. Sizing the delta

**33 files the port must ADD** (fork-only, uncertainty-related — excludes 12 checked-in ANTLR
outputs, 15 files of the unrelated `analysis/metrics` package, and `Main.java`/`ShellReadline.java`):
`uml/ocl/value` (7: `SBooleanValue`, `UBooleanValue`, `UIntegerValue`, `URealValue`, `UStringValue`,
`UncertainBooleanValue`, `UncertainValue`), `uml/ocl/type` (7, parallel names), `uml/ocl/expr` (8:
five `ExpConstU*`/`ExpConstSBoolean`, `ExpDefSBoolean`, `ExpUSelect`, `ExpUSelectC`),
`uml/ocl/expr/operations` (5: `StandardOperations{SBoolean,UBoolean,UInteger,UReal,UString}`),
`parser/ocl` (6 AST literal classes). Only 7 of the 33 `import uDataTypes.*` directly (the five
`*Value` classes plus `StandardOperations{Number,UReal}`) — see §7.

> **CORRECTION 2026-08-18 — `stage-03-scope.md` §5.5.** This section previously stated that
> `UUnlimitedNatural`, `UEnum` and `Distribution` are "never imported but still needed on the
> classpath as transitive return types." Measured: **only `UUnlimitedNatural` is.** Neither `UEnum`
> nor `Distribution` is referenced by any class in the compile closure, nor anywhere in
> `USE-Uncertainty/src/` — the transitive set was overstated by two classes. Under
> `stage-03-scope.md` §5's purge decision, the `UUnlimitedNatural` dependency is removed too, leaving
> a vendored set of **five** types.

**23 files the port must EDIT** (present in both trees, touched by the fork). Hardest merges by diff
size: `StandardOperationsNumber` (763 diff lines — both sides rewrote independently),
`StandardOperationsCollection` (307), `MClassifierImpl` (226 — almost entirely upstream's data-type
reshape; the fork's own share is 10 trivial `false` stubs), `ExpQuery` (218), `ASTQueryExpression`
(167), `StandardOperationsAny` (158). Full per-file table and reproduction command are in
`specification.md` §4.7 / §1.6.

**Grammar drift — the fourth extension point, easy to miss.** Grammar files moved from
`$FSRC/parser/*/*.gpart` to `use-core/src/main/resources/grammars/`. The fork adds an
`identicalExpression` rule (changes operator precedence — `expression → identicalExpression →
conditionalImpliesExpression` instead of directly to the latter) and an optional confidence argument
on query expressions. Upstream independently added `importStatement`/`dataTypeDefinition`/
model-qualified operation names/`oclIsInState`, none of which the fork touches.
`OCLLexerRules.gpart` is unchanged between the trees: no new lexer token is needed for the uncertain
type names — they resolve through `TypeFactory.mkSimpleType` off an `IDENT`, like any other type name.

---

## 7. BLOCKING DESIGN DECISION — how the port gets `uDataTypes` onto a Maven build

### 7.1 Is the library in any Maven repository, under any coordinates? **No.**

Searched Maven Central by artifact, group, and fully-qualified class name (`fc:uDataTypes.UReal`,
`fc:uDataTypes.SBoolean`) — zero hits under every query. Direct path probes to
`repo1.maven.org/maven2/{es/uma/lcc/atenea,uDataTypes,atenearesearchgroup}/` all 404.
`org.tzi.use` itself is not on Central either, so the project has no precedent of consuming its own
artifacts from a public repo. **JitPack is not an escape hatch** — it builds from a Git tag and needs
a Maven or Gradle build file in the repository; the uDataTypes tree has none.

### 7.2 What the oracle jar actually is

`lib/atenearesearchgroup.uncertainty.jar`: 39 entries, 77 674 bytes, classes under `uDataTypes/`,
timestamps 2021-02-24. It is an IDE export (ships `.classpath`/`.project`/`.iml`), **not** a release
artifact — no `META-INF` at all, hence no `Automatic-Module-Name`. Class file major version 52 (Java
8), readable by JDK 21. **A byte-identical copy is already committed** at
`use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar` (md5
`a3055f54205babaa27484fa94efdda1c`), loaded through an isolated class loader by the differential
harness (`use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java`). **The oracle-side
need is already solved, on the test side, with no Maven coordinates involved.** What remains unsolved
is the *product* side: `use-core` main code must compile against `uDataTypes.*`.

### 7.3 Is the 2023 source tree a safe stand-in for the 2021 jar? **Yes, for this port's call paths.**

`$UDT/Libraries/Java/src/uDataTypes` (24 `.java` files; README states MIT licence, copyright 2023
Atenea Research group) compiles cleanly under JDK 21 and is a **strict API superset** of the jar for
`UReal`, `UInteger`, `UBoolean`, `UString`, `Distribution`, `UUnlimitedNatural` (identical public API);
`SBoolean` gained 14 lines, all additions (`weightedUnion`, `union`, 9 collection-fusion statics). A
16-expression differential probe across all five types the fork uses produced **identical** output
jar-vs-source, including `SBoolean.toString()`, `hashCode()` and all three fusion operators. The
**only** divergence found is in the covariance-taking `divideBy(x, covariance)` overloads — the 2021
jar drops the divisor's uncertainty in that branch, 2023 propagates it (a genuine bug fix) — and
**the fork never calls those overloads**; it uses only the single-argument `divideBy`/`divideByR`
forms, which are byte-identical jar-vs-source and produce identical results in the probe.

Licence: MIT is documented in the 2023 README only (no separate `LICENSE` file); the 2021 jar carries
no licence metadata of its own (no `META-INF`). MIT is GPL-2-compatible, so vendoring into GPL-2 USE
is legally sound provided the copyright/permission notice travels with the copied files.

### 7.4 The four options

| Option | What | Verdict |
|---|---|---|
| **A1** | Vendor the 2023 source, keep package name `uDataTypes` | Creates a class-name collision with the isolated oracle loader: a parent-first `URLClassLoader` (the default) would resolve the vendored 2023 class instead of the 2021 oracle class — a green differential suite that proves nothing. Avoidable (parent-last loader) but makes harness correctness a precondition for oracle validity |
| **A2** | Vendor the 2023 source, relocate package to `org.tzi.use.uncertainty.udatatypes` | Collision disappears by construction — a mechanical rewrite of the `package` line in 15 files and the `import` line in 7 |
| **B** | `mvn install:install-file` on invented coordinates | Breaks `git clone && mvn test` (needs an out-of-band manual step); no `<repositories>`/`system`-scope precedent anywhere in the reactor; needs an automatic-module `requires` (the jar has no `Automatic-Module-Name`); ships IDE cruft into the product; freezes the 2021 `divideBy` bug for all time |
| **C** | Shade/relocate the jar's bytecode (`maven-shade-plugin`) | Solves the collision like A2 but still needs the jar in a repository first (Option B's problem, unsolved) or a deprecated `system` scope; adds a build-plugin lifecycle the reactor has none of; same frozen-bug consequence as B; debugging relocated bytecode with no attached sources is worse than debugging vendored source |
| **D** | Reimplement the uncertainty arithmetic inside the port | Rejected on the spot — `UReal` is ~19.5 KB and `SBoolean` ~59 KB of subjective-logic arithmetic implementing two published papers; reimplementation would make the port's numeric behaviour a fresh research artifact, not a port |

### 7.5 Recommendation

> **Option A2 — vendor the 2023 MIT-licensed source into `use-core` under a relocated package
> (`org.tzi.use.uncertainty.udatatypes`).**

It is the only option satisfying all four constraints at once: no path under
`.git/reference-repositories` is ever a build input, `git clone && mvn test` needs no manual step,
oracle isolation cannot be defeated by classloader delegation, no `module-info.java` `requires` on an
unnamed automatic module, and the licence is clean (MIT).

Two follow-on obligations if A2 is taken: (1) **record the jar↔source `divideBy` delta as a known,
accepted difference** — §7.3 shows it is empty on every call path the fork exercises, but "port ≡
oracle" becomes a claim about *reachable* behaviour, not about the libraries, and the port and the
oracle would legitimately disagree if a future stage adds OCL surface for correlated division;
(2) **keep the oracle side on the already-committed jar**, loaded as a test *resource* (not a POM
dependency) through an isolated class loader — it needs no `requires` because it is opened by
path/URL, not declared as a dependency.

---

## 8. Gaps

* `UNVERIFIABLE` — the exact JVM flags surefire 3.5.4 passes for the modular main / non-modular test
  split (`--add-opens`, `--add-reads`, `--patch-module`). The surefire XML records the module/class
  paths but not the full argument line, and confirming it would require running Maven, forbidden here.
* `UNVERIFIABLE` — whether the 2021 jar's `uDataTypes` classes trace to a *published* source revision.
  The 2023 tree has no VCS metadata and the jar has no manifest; the two artifacts are linked only by
  the behavioural comparison in §7.3.
* `UNVERIFIABLE` — the licence status of the 2021 jar itself, as distinct from the 2023 source — the
  MIT grant is documented only in the 2023 README.
* `UNVERIFIABLE` — whether `use-gui` contains value/type dispatch (`instanceof RealValue`,
  `isTypeOfBoolean()` switches) that would need widening for uncertain values to display correctly.
  The fork touches no GUI file, so there is no fork-side evidence either way, and auditing the GUI's
  own dispatch sites was out of scope for this section.
* Not attempted, per the brief: locating the fork's base commit or producing a base diff.
