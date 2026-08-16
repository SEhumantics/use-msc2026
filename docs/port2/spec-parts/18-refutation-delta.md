# S1.8 — Refutation pass over `15-upstream-delta.md`

**Posture.** Every claim in `15-upstream-delta.md` was treated as false until re-derived from source.
Nothing below is taken from the document being refuted, and nothing is taken from `origin/main`.
Where I could not establish something, it is marked `UNVERIFIABLE` — not "confirmed".

**Path shorthand (same as §15):**

```
FORK = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
FSRC = $FORK/src/main/org/tzi/use
TSRC = /home/xoruser/msc-4/use-msc2026/use-core/src/main/java/org/tzi/use
UDT  = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/uDataTypes
UP   = /home/xoruser/msc-4/use-msc2026/.git/reference-repositories/upstream-use
```

All of `$FORK`, `$UDT`, `$UP` were opened read-only. No Maven was run. Compilation and bytecode
probes below ran only in the session scratchpad, never into any `target/`.

---

## 0. Verdict in one paragraph

**The load-bearing technical core of §15 holds up.** I re-derived, independently and at file:line, the
abstract-member list of `Value`, the interface-ness of `Type` and its three upstream additions, the
`conformsTo` dispatch model, the complete `OpGeneric` contract, the non-sealed status of
`ExpressionVisitor` and its exact implementor set, and both `module-info.java` descriptors. Those six
focus areas are correct.

**Two findings would change a port decision**, and one is a real trap:

1. §2.2's description of `conformsTo` omits that `TypeImpl.conformsTo` is an **infinitely
   self-recursive stub**, not a `false` default. A new uncertain `*Type` that forgets to override it
   gets `StackOverflowError`, not `false`. (`$TSRC/uml/ocl/type/TypeImpl.java:78-81`)
2. §7.4/§7.5's entire A1-vs-A2 discriminator rests on a **miscited and now-stale** premise. The
   harness does not use a plain `URLClassLoader`, the file cited contains no class-loading code at
   all, and the repository's own `IsolatedJarClassLoader` already isolates the `uDataTypes.` prefix
   parent-last — and documents, with a measurement, that the doc's proposed alternative remedy
   (platform-parented `URLClassLoader`) **does not work** in this JPMS reactor.

Plus six smaller factual errors (counts and one "empty diff" that is not empty).

---

## 1. Findings — claims that are wrong or materially incomplete

### F1 — `TypeImpl.conformsTo` is not a `false` default; it is a stack overflow

**§15 claims** (§2.2): *"`conformsTo(Type other)` … The default is in `TypeImpl` and each basic type
overrides it."* §2.1 separately says `TypeImpl` *"supplies a `false`-returning default for every
predicate"*, and §2(c) item 4 says 7.5.0's `TypeImpl` *"now also demands nothing new"*.

**Source.** `$TSRC/uml/ocl/type/TypeImpl.java:78-81`:

```java
    @Override
	public boolean conformsTo(Type other) {
		return this.conformsTo(other);
	}
```

The fork is identical: `$FSRC/uml/ocl/type/TypeImpl.java:76-78`, same body.

**Why it matters.** `conformsTo` is *declared* on the interface (`$TSRC/uml/ocl/type/Type.java:58`),
so `javac` is satisfied by the inherited `TypeImpl` body. A new type — `URealType`, `UBooleanType`,
`SBooleanType`, `UncertainType`, … — that extends `TypeImpl`/`BasicType` and omits `conformsTo`
therefore **compiles clean and then recurses until the stack dies at first use**. It does not
"default to false". §15's phrasing invites exactly the omission that produces this. The port must
treat `conformsTo` as a *de facto* abstract member of `TypeImpl` and override it in all seven new
`*Type` classes.

*Note this is a pre-existing upstream defect, not drift: both trees have it. It is a refutation of
§15's characterisation, not of 7.5.0.*

**Verdict: PARTIALLY_WRONG — and this is the highest-consequence error in the document.**

---

### F2 — The `uDataTypes` classloader argument is miscited, and its factual premise is refuted by
code already in this repository

**§15 claims** (§7.4, Option A1): *"The differential harness loads the historical jar through a
`URLClassLoader` (`UValue.java:13-16`). A `URLClassLoader` delegates **parent-first** by default …
So `historicalLoader.loadClass("uDataTypes.UReal")` would return the **vendored 2023 class** … It is
avoidable — construct the isolated loader with `ClassLoader.getPlatformClassLoader()` as parent, or a
parent-last loader — but it makes harness correctness a *precondition* for oracle validity."*

This is the single reason §7.5's table gives A1 a `✘` on *"Oracle isolation cannot be defeated by
classloader delegation"* and A2 a `✔`. It is the discriminator that selects the recommendation.

**Three things are wrong with it.**

**(a) The citation is empty.** `use-core/src/test/java/org/tzi/use/uncertainty/differential/UValue.java`
lines 13-16 are Javadoc prose inside a plain immutable data class:

```
13	 * {@code java.lang.reflect} type or a {@code Class} object belonging to the isolated historical
14	 * class loader. The historical side unwraps into {@code UValue}; the ported side (from S4 onwards)
15	 * wraps into {@code UValue}; the sweep diffs {@code UValue} against {@code UValue}.
```

`UValue.java` contains no `URLClassLoader`, no `Class.forName`, no `loadClass` — verified by reading
all 277 lines. §15's §7.2 cites the same lines correctly (as a statement of *intent*); §7.4 then
re-uses the citation as if it were evidence of a *construction*. It is not.

**(b) The harness is not a plain `URLClassLoader` and already isolates `uDataTypes.`.** The real
loader is `use-core/src/test/java/org/tzi/use/uncertainty/differential/IsolatedJarClassLoader.java`:

```
40	final class IsolatedJarClassLoader extends URLClassLoader {
51	    private static final List<String> ISOLATED_PREFIXES =
52	            List.of("org.tzi.use.", "uDataTypes.");
…
80	                if (isIsolated(name)) {
81	                    // Parent-last, and no fallback: if the jars do not have it, it does not exist.
82	                    // Falling back to the parent here would silently reintroduce self-comparison.
83	                    loaded = findClass(name);
```

`uDataTypes.` is *already* on the never-delegate list, and the policy is asserted by a test:
`HistoricalOracleIsolationTest.java:69-70` (`assertTrue(IsolatedJarClassLoader.isIsolated("uDataTypes.UReal"))`)
and `:76-78` (the application loader must not appear anywhere in the oracle's parent chain). That
test class already has a surefire report on disk
(`use-core/target/surefire-reports/TEST-org.tzi.use.uncertainty.differential.HistoricalOracleIsolationTest.xml`),
so it is live, not aspirational.

**Consequence: A1's stated fatal flaw does not obtain against the harness that exists.** The
§7.5 table row that separates A1 from A2 is, as of the current tree, false.

**(c) One of the two remedies §15 offers is explicitly refuted, with a measurement, in this
repository.** §15 says the collision *"is avoidable — construct the isolated loader with
`ClassLoader.getPlatformClassLoader()` as parent, **or** a parent-last loader"*. Only the second
works. `IsolatedJarClassLoader.java:16-26`:

> *"`new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())` looks like the fix and is **not**
> one in this repository. `use-core` has a `module-info.java`, so Maven puts it on the module path and
> `use.core` is resolved into the boot layer. `jdk.internal.loader.BuiltinClassLoader#loadClassOrNull`
> consults a package-to-module map covering *every* boot-layer module before it considers its own
> parent … This was measured, not assumed."*

`HistoricalOracleIsolationTest.platformLoaderIsNotACleanParentUnderJpms()` (lines 90-110) pins that
hazard as a regression test.

**What survives.** A2 (relocated package) may still be the better option — it is robust *without*
depending on the harness policy, which is a legitimate architectural preference. But §15 states the
A1 risk as a present-tense fact about this repository's harness, and it is not one. The
recommendation should be re-argued on its real merits (defence in depth, decoupling oracle validity
from a test-scoped classloader policy) rather than on a refuted premise.

**Verdict: PARTIALLY_WRONG (recommendation possibly still sound; stated justification is not).**

---

### F3 — `OCLLexerRules.gpart` "empty diff" is not empty; all seven 7.5.0 `.gpart` files are CRLF

**§15 claims** (§6.3): *"`OCLLexerRules.gpart` is **unchanged** between the trees (empty diff) —
confirming §2.3: no new lexer token is needed."*

**Source.**

```bash
diff $FSRC/parser/base/OCLLexerRules.gpart \
     use-core/src/main/resources/grammars/base/OCLLexerRules.gpart
# 1,127c1,127   — every line differs

file use-core/src/main/resources/grammars/base/OCLLexerRules.gpart
# ASCII text, with CRLF line terminators
file $FSRC/parser/base/OCLLexerRules.gpart
# (no CRLF; LF only)

diff <(tr -d '\r' < $FSRC/parser/base/OCLLexerRules.gpart) \
     <(tr -d '\r' < use-core/src/main/resources/grammars/base/OCLLexerRules.gpart)
# empty  → IDENTICAL after CRLF normalisation
```

The *conclusion* (no new lexer token, so §2.3's "primitive type names are `IDENT` resolved through
`buildInTypesMap`" holds) is correct. The stated evidence is not reproducible as written, and the
omission has a practical cost: **every literal diff or three-way merge of a grammar part between the
two trees reports 100 % change**. §6.3's other numbers were evidently produced with normalisation
that the document does not mention. For the record, normalised:
`diff <(tr -d '\r' < $FSRC/parser/base/OCLBase.gpart) <(tr -d '\r' < .../grammars/base/OCLBase.gpart) | grep -cE '^[<>]'` → **73**.

**Verdict: PARTIALLY_WRONG.**

---

### F4 — uDataTypes source-tree file counts are wrong in three places

| §15 claim | Location | Actual | Command |
|---|---|---|---|
| *"holds 24 `.java` files"* | §7.3 | **23** | `find $UDT/Libraries/Java -name '*.java' \| wc -l` |
| *"Copy the 15 non-test `.java` files"* | §7.4 Option A | **18** | `find … -name '*.java' \| grep -vE 'Test\|Examples' \| wc -l` |
| *"rewrite of the `package` line in 15 files"* | §7.5 A2 | **18** | same |

§15's own quoted `javac` command (`… $(find /tmp/udt/src -name '*.java' | grep -vE 'Test|Examples')`)
compiles 18 files, contradicting its own "15". The five excluded files are `ExamplesSBoolean.java`,
`SBooleanTest.java`, `SBooleanTest3.java`, `UEnumTest.java`, `UncertaintyTest.java`.

This matters only for sizing A2's mechanical rewrite (18 `package` lines, not 15), but a
20 %-understated vendoring footprint in a recommendation is worth correcting.

**Verdict: PARTIALLY_WRONG.**

---

### F5 — the `SBoolean` javap delta is 11 lines, not 14

**§15 claims** (§7.3): *"`## SBoolean  14 lines, ALL additions on the source side (weightedUnion,
union, 9 collection-fusion statics)"*.

**Re-run** (compiled `$UDT` sources under JDK 21 in scratchpad; jar =
`use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`):

```
## UReal              (identical)
## UInteger           (identical)
## UBoolean           (identical)
## UString            (identical)
## SBoolean           11 lines, all "> " (source-side additions)
## Distribution       (identical)
## UUnlimitedNatural  (identical)
```

The 11 are: `weightedUnion(SBoolean)`, `union(SBoolean)`, and the 9 statics
`aleatoryCumulativeFusion`, `averagingFusion`, `cbFusion`, `ccFusion`, `epistemicCumulativeFusion`,
`majorityFusion`, `minimumFusion`, `weightedFusion`, `weightedUnion(Collection)` — so the *parenthetical*
breakdown ("weightedUnion, union, 9 collection-fusion statics" = 11) is right and only the headline
number is wrong. **The substantive claim — strict public-API superset, nothing removed — is
CONFIRMED.**

**Verdict: PARTIALLY_WRONG (arithmetic only).**

---

### F6 — the `divideBy` grep output quoted in §7.3 is not what the command produces

**§15 quotes**, under `cd $FORK && grep -rn 'divideBy\|divideByR' --include=*.java src/main/org/tzi/use/uml/ocl/`,
exactly three lines (`UIntegerValue.java:148`, `:158`, `URealValue.java:180`).

**The command actually emits nine lines**, adding `UIntegerValue.java:146,156`, `URealValue.java:178`
(the USE-level method declarations) and `StandardOperationsNumber.java:387,404`,
`StandardOperationsUInteger.java:307` (USE-level call sites). The three quoted lines are indeed the
only calls into `uDataTypes`, so **the conclusion is CONFIRMED** — the fork never reaches the
covariance-taking overloads. But the transcript was edited without saying so, in a document whose
premise is *"names … the exact shell command that produced it"*.

**Verdict: PARTIALLY_WRONG (reproducibility; conclusion stands).**

---

### F7 — small counting errors

| § | Claim | Actual | Evidence |
|---|---|---|---|
| §1(a) | *"widened the predicate family with **five** methods"*, then a table of four | **four** (`isUInteger`, `isUReal`, `isUBoolean`, `isSBoolean`) | `$FSRC/uml/ocl/value/Value.java:67,84,100,108`; the doc says "four" correctly at §1(b) and §1(c) |
| §4.1 | *"The fork's own **six** lines"* | **seven** (`92,98d91` = comment + 5 calls + blank) | diff of `OpGeneric.java` |
| §5(a) | *"hand-maintained `lib/` with **11** checked-in jars"* | **13** in `lib/` (`ls lib/*.jar`), 18 counting `lib/plugins/` | `ls $FORK/lib` returns 18 entries: 13 jars + 4 licence files + `plugins/` |

**Verdict: PARTIALLY_WRONG (cosmetic).**

---

### F8 — line-number drift (cosmetic, listed so nobody chases a phantom)

| §15 cite | Actual |
|---|---|
| root `pom.xml:19-20` for `maven.compiler.source/target` | `pom.xml:18-19` |
| `TypeImpl.java:43-47` for `qualifiedName()` | `TypeImpl.java:42-46` (`@Override` at 42, body 43-45) |
| `TypeImpl.java:288-297` for the data-type `false` defaults | `TypeImpl.java:287-295` |
| `TypeFactory.java:50-58` for the `buildInTypesMap` static block | `TypeFactory.java:49-57` |
| `MClassifier.java:59-63` for `isQualifiedAccess`/`setQualifiedAccess` | `MClassifier.java:59,61` |
| `MClassifierImpl.java:390-392` quoted as `model.name() + "#" + name()` | actual body is `return model != null ? model.name() + "#" + name() : null;` — the null guard is dropped in the quote |

Everything else I spot-checked landed on the exact line claimed.

---

## 2. Confirmed — the six focus claims, re-derived independently

### C1 — `Value`'s abstract-member list (7.5.0) — **CONFIRMED, exact**

`$TSRC/uml/ocl/value/Value.java` is 194 lines,
`public abstract class Value implements Comparable<Value>, BufferedToString` at **:36**, single
private field `fType` at :38, constructor `protected Value(Type t)` at :40.

**Exactly three abstract members**, at exactly the lines §15 gives:

```
160	    public abstract StringBuilder toString(StringBuilder sb);   // from BufferedToString
175	    public abstract int hashCode();
183	    public abstract boolean equals(Object obj);                 // OCL semantics
```

`compareTo(Value)` is **not** re-declared abstract — confirmed, `grep -n compareTo Value.java` → no
match; the obligation arrives only via `Comparable<Value>` on :36 and so is enforced by `javac` only
at the first concrete subclass. `toString()` is `final` at **:153**. `isDefined()` :84-86 delegates
to `isUndefined()` :92-94. `type()` :44, `getRuntimeType()` :48, `toStringWithType(StringBuilder)`
:168-172, `setTypeToRuntimeType()` :190. The `isX()` family spans :56-150.

**Drift from the fork: zero on the upstream side.** The diff is exactly the five hunks §15 lists
(`20,21d19`, `35d32`, `64,71d60`, `80,88d68`, `93,108d72`) — two SVN keyword lines and the fork's
four added predicates, nothing more.

Related, and also confirmed: `UndefinedValue.toString(StringBuilder)` prints `"null"` in 7.5.0 and
`"Undefined"` in the fork —

```
diff … UndefinedValue.java
46c43
<         return sb.append("Undefined");
---
>         return sb.append("null");
```

— introduced by `72ab8fd7` (2019-06-27, *"changed Undefined to null"*), verified against `$UP`.
§15 flags this as the highest risk in §1; that judgement is sound and I endorse it.

### C2 — `Type` is an interface, and how `conformsTo` dispatches — **CONFIRMED (see F1 for the caveat)**

* **Interface in both trees.** `$TSRC/uml/ocl/type/Type.java:31` and `$FSRC/uml/ocl/type/Type.java:33`
  are both `public interface Type extends BufferedToString {`. `TypeImpl` is
  `public abstract class TypeImpl implements Type` — target :32, fork :34.
  `BasicType extends TypeImpl` at `$TSRC/uml/ocl/type/BasicType.java:28`, with `fTypename`,
  `equals`-by-`getClass()` and `hashCode`-by-`getClass()` at :48-59.
* **No generics on `Type`**; the only generic signature is `Set<? extends Type> allSupertypes()`
  (`Type.java:63`), identical in both.
* **`MClassifier extends Type, MModelElement, MNamedElement, UseFileLocatable` on line 36 of both
  trees** — verified by `sed -n '36p'` on each.
* **Exactly three upstream additions** to the interface, confirmed by full diff:
  `String qualifiedName();` (**:48**), `boolean isKindOfDataType(VoidHandling h);` (**:136**),
  `boolean isTypeOfDataType();` (**:138**). Nothing else was added and nothing removed.
* **Exactly ten fork additions**, at fork `Type.java:84,86,92,94,104,106,112,114,116,118`, in the
  order §15 lists.
* **Dispatch is single, per-concrete-type, no registry, no double dispatch.** `conformsTo` is
  declared at `Type.java:58` and overridden in 14 concrete types
  (`RealType:54`, `IntegerType:59`, `BooleanType:50`, `StringType:49`, `UnlimitedNaturalType:59`,
  `EnumType:110`, `OclAnyType:74`, `VoidType:23`, `TupleType:129`, `MessageType:77`,
  `CollectionType:62`, `SetType:74`, `BagType:73`, `SequenceType:94`, `OrderedSetType:98`).
  `$TSRC/uml/ocl/type/RealType.java:54-56` is verbatim `return equals(t) || t.isTypeOfOclAny();`,
  exactly as quoted. **But see F1: the `TypeImpl` "default" is a self-call.**
* **The `getLeastCommonSupertype` point is the important one and it is right.**
  `TypeImpl.getLeastCommonSupertype` is at `TypeImpl.java:84`. Overload resolution for `=` runs
  through it, not through `conformsTo` — `Op_equal` (`$TSRC/uml/ocl/expr/operations/StandardOperationsAny.java:32`)
  has `matches` at **:45-50**:
  ```java
  if (params.length == 2 && params[0].getLeastCommonSupertype(params[1]) != null)
      return TypeFactory.mkBoolean();
  ```
  So a new type that overrides `conformsTo` but not `allSupertypes()` fails overload resolution with
  `"Undefined operation `"` from `$TSRC/uml/ocl/expr/ExpStdOp.java:130`. Confirmed.
* **Registration mechanism unchanged**, three places in `TypeFactory`: singletons :42-48, static map
  block :49-57, `mkX()` accessors from :66. `mkSimpleType` at :132-139; sole parser entry point
  `$TSRC/parser/ocl/ASTSimpleType.java:47` → `TypeFactory.mkSimpleType(name)`. Confirmed.
* **`MClassifier` drift confirmed**: `isSubClassOf` (fork `MClassifier.java:114,123`) →
  `isSubClassifierOf` (target `:116,125`); `attributes()`/`allAttributes()`/`operation(String,boolean)`/`operations()`
  now on the interface (`:136-168`); `isQualifiedAccess`/`setQualifiedAccess` at `:59,61`;
  `hasStateMachineWhichHandles` at the end of the interface; `MMVisitor.visitDataType(MDataType e)`
  at `$TSRC/uml/mm/MMVisitor.java:39`;
  `ModelFactory.createOperation(String, VarDeclList, Type, boolean isConstructor)` at
  `$TSRC/uml/mm/ModelFactory.java:76-79` vs. the fork's 3-arg form at `$FSRC/uml/mm/ModelFactory.java:75-77`;
  `createClassInvariant(… , MClassifier cf, …)` at `:56` vs. the fork's `MClass cls` at `:55`;
  `createDataType(String, boolean)` new at `:43`.

### C3 — the `OpGeneric` contract, verbatim from 7.5.0 — **CONFIRMED, exact**

`$TSRC/uml/ocl/expr/operations/OpGeneric.java`, 118 lines. Verbatim member list with true line
numbers:

```java
24	public abstract class OpGeneric {
34	    public static final int OPERATION = 0;
36	    public static final int SPECIAL = 3;
38	    public abstract String name();
40	    public boolean isBooleanOperation() {          // concrete, returns false
44	    public abstract int kind();
46	    public abstract boolean isInfixOrPrefix();
48	    public abstract Type matches(Type params[]);
50	    public String checkWarningUnrelatedTypes(Expression args[]) { return null; }
52	    public abstract Value eval(EvalContext ctx, Value args[], Type resultType);
54	    public String stringRep(Expression args[], String atPre) {        // concrete
80	    public static void registerOperations(Multimap<String, OpGeneric> opmap)
105	    public static void registerOperation(OpGeneric op, Multimap<String, OpGeneric> opmap)
115	    public static void registerOperation(String name, OpGeneric op, Multimap<String, OpGeneric> opmap)
```

**Five abstract methods: `name()`, `kind()`, `isInfixOrPrefix()`, `matches(Type[])`,
`eval(EvalContext, Value[], Type)`.** Every line number §15 gives is correct.

`PREDICATE` appears **only** in the comment at line 32 (`// PREDICATE -> BooleanValue(false)`) in
*both* trees; there is no such constant. `grep -rn PREDICATE` over both `.../expr/operations/`
directories returns exactly those two comment lines. §15's "do not restore it" is right.

**Drift: literally zero.** The complete fork↔7.5.0 diff of `OpGeneric.java` is the single hunk
`92,98d91` containing the fork's uncertainty registrations — nothing else, no signature change.

Registry mechanism confirmed: `ExpStdOp.opmap` is a `ListMultimap` filled once at
`$TSRC/uml/ocl/expr/ExpStdOp.java:53-56` (`OpGeneric.registerOperations(opmap)` on **:55**);
resolution is first-`matches`-wins at `:120-127`, falling through to
`throw new ExpInvalidException("Undefined operation `" …)` at `:129-131`.
`addOperation`/`removeAllOperations` at `:62-74`. So §15's "registration order is significant" is
structurally correct.

`Op_number_pow` and `Op_number_sqrt` are present at
`$TSRC/uml/ocl/expr/operations/StandardOperationsNumber.java:802` and `:848`, registered at `:31-32`
— and the fork has neither. §15's warning about deleting them in a wholesale take is confirmed.
Upstream history confirmed against `$UP`: `29171370` (2024-06-20) removed them, they are back at
7.5.0.

### C4 — `ExpressionVisitor` is **not** sealed; the compile-time surface — **CONFIRMED**

* `$TSRC/uml/ocl/expr/ExpressionVisitor.java:27` — `public interface ExpressionVisitor {`, plain.
  Lines 27-77; **49** `void visit…` methods (`grep -cE '^\s*void visit'` → 49). Fork: **57**.
* **No `sealed` or `permits` anywhere in the product.**
  `grep -rn '\bsealed\b\|\bpermits\b' --include=*.java use-core/src/main use-gui/src/main` → no
  output, exit 1. So there is no exhaustiveness check: adding a new `Exp*` class whose
  `processWithVisitor` calls the wrong case compiles clean and misbehaves only at runtime.
* **Adding a method to the interface does break compilation** of every implementor, because the
  methods have no `default` bodies. That is the mechanism §15 describes and it holds.
* **Implementor census (7.5.0) — confirmed exactly two roots:**
  ```
  use-core/.../analysis/coverage/AbstractCoverageVisitor.java:33   implements ExpressionVisitor
  use-core/.../uml/ocl/expr/ExpressionPrintVisitor.java:35         implements ExpressionVisitor
  ```
  and four derived: `CoverageCalculationVisitor.java:38`, `BasicExpressionCoverageCalulator.java:40`,
  `GenerateHTMLExpressionVisitor.java:30`, plus **two** inner classes in `EvalNode` —
  `RelevantOperationHighlightVisitor` at `EvalNode.java:618` and `SubstituteVariablesExpressionVisitor`
  at `EvalNode.java:351`. §15's §3(b) grep pattern surfaces only the first of the two, while §3(d)
  correctly says "two"; both statements are true, the table is just narrower than the prose.
  A repo-wide sweep for `implements ExpressionVisitor` (including `use-gui` and all test sources)
  returns nothing further — **`use-gui` implements it nowhere**, confirmed.
* `visitObjOp` → `visitInstanceOp` rename confirmed: `ExpressionVisitor.java:51`;
  `public final class ExpObjOp extends ExpInstanceOp` at `$TSRC/uml/ocl/expr/ExpObjOp.java:35`;
  `ExpInstanceOp.processWithVisitor` calls `visitor.visitInstanceOp(this)` at
  `$TSRC/uml/ocl/expr/ExpInstanceOp.java:44-46`. Upstream commit `46c277e7` (2024-11-24) confirmed
  in `$UP`.
* `Expression`'s abstract surface confirmed at the exact claimed lines:
  `eval(EvalContext)` :79, `childExpressionRequiresPreState()` :133, `toString(StringBuilder)` :147,
  `processWithVisitor(ExpressionVisitor)` :178, `public final String toString()` :138. Fork↔7.5.0
  diff is 8 changed lines, all keyword/Javadoc — **no API change**.
* `org/tzi/use/analysis/metrics/` does not exist in 7.5.0 (`ls $TSRC/analysis/` → `coverage` only),
  so the fork's third implementor `AbstractMetricVisitor` (`$FSRC/analysis/metrics/AbstractMetricVisitor.java:32`)
  has no counterpart. Confirmed.

### C5 — `module-info.java` — **CONFIRMED, exports quoted verbatim**

```bash
find . -name module-info.java -not -path './.git/*'
./use-gui/src/main/java/module-info.java
./use-core/src/main/java/module-info.java
```

`use-core/src/main/java/module-info.java` is 45 lines, `module use.core {`, **11 `requires`**,
**32 `exports`** — counts confirmed. Verbatim:

```java
 1  module use.core {
 2      requires antlr.runtime;
 3      requires org.eclipse.jdt.annotation;
 4      requires java.naming;
 5      requires java.prefs;
 6      requires com.google.common;
 7      requires vtd.xml;
 8      requires java.scripting;
 9      requires org.jruby.dist;
10      requires combinatoricslib;
11      requires java.datatransfer;
12      requires java.desktop;
…
15      exports org.tzi.use.parser.use;
16      exports org.tzi.use.uml.mm;
…
25      exports org.tzi.use.uml.ocl.type;
…
29      exports org.tzi.use.uml.ocl.expr;
30      exports org.tzi.use.uml.ocl.value;
31      exports org.tzi.use.uml.ocl.expr.operations;
…
33      exports org.tzi.use.parser.ocl;
45  }
```

**All seven port-relevant `exports` are present at exactly the line numbers §15 gives.** There are no
`opens`, no qualified `exports`, and no `requires static` in `use-core`.
`use-gui/src/main/java/module-info.java:1-2` is `module use.gui { requires use.core; …` — confirmed;
it also has three qualified `exports … to com.google.common` and two `opens … to javafx.fxml`, which
§15 does not mention but which do not bear on the port.

**§5.1's evidence and its three conclusions — CONFIRMED.** I re-read
`use-core/target/surefire-reports/TEST-org.tzi.use.uml.mm.ModelAPITest.xml` (read-only) and got the
same split §15 reports: `jdk.module.path` = 11 entries, `java.class.path` = 21 entries.
Contents verified:

* module path: `use-core/target/classes`, guava 33.5.0-jre (+ failureaccess, jspecify,
  error_prone_annotations, j2objc-annotations), jdt.annotation, antlr-runtime, combinatoricslib,
  jruby-core, vtd-xml.
* class path: `use-core/target/test-classes`, **`jline-2.14.6.jar`**, guava-testlib, junit 4.13.2,
  hamcrest, junit-jupiter 5.7.0 (+api, params, engine, platform-commons, platform-engine),
  archunit-junit5 1.3.0 (+api, engine, engine-api, archunit), slf4j-api, opentest4j, apiguardian.
* `surefire.real.class.path` begins `surefire-booter-3.5.4.jar` — **surefire 3.5.4** confirmed, and
  `grep -rn surefire pom.xml use-core/pom.xml use-gui/pom.xml use-assembly/pom.xml` returns nothing
  (exit 1), so it really is inherited, not configured.

The `jline` proof is exact: declared compile-scope at `use-core/pom.xml:36-40`, **no** `requires
jline` in the descriptor, lands on `java.class.path`, and `grep -rn 'import jline' use-core/src/main`
→ no match. §15's conclusion — *add a compile dependency without a matching `requires` and it is
invisible to your code* — is correctly evidenced.

Reactor facts also confirmed: root `pom.xml` `org.tzi.use:use:7.5.0`, `<packaging>pom</packaging>`,
modules `use-assembly`/`use-core`/`use-gui` at `pom.xml:11-15`, `maven.compiler.source/target = 21`
at `pom.xml:18-19` **and** `use-core/pom.xml:16-17`; the full dependency block at
`use-core/pom.xml:20-74` matches the list §15 gives, version for version; and

```bash
grep -n 'systemPath\|<scope>system\|<repositories>\|<repository>\|<pluginRepositor' \
  pom.xml use-core/pom.xml use-gui/pom.xml use-assembly/pom.xml     # exit 1, no output
```

— **no `<repositories>`, no `system` scope, no `systemPath` anywhere in the reactor.** Confirmed.
Plugins: `merge-maven-plugin` at `use-core/pom.xml:80`, `antlr3-maven-plugin` at `:171`,
`copy-rename-maven-plugin` at `:200` (the `<plugins>` block runs :77-336, so §15's "78-277" is a
loose but harmless range).

Fork side: Ant, `source`/`target` `1.7` at `$FORK/build.xml:16-17`; uncertainty classpath entry at
`$FORK/build.xml:50` binding `${lib.dir}/atenearesearchgroup.uncertainty.jar` — matches the
established fact in the brief. 12 checked-in ANTLR lexers/parsers, confirmed by the fork-only file
census below.

### C6 — "Is `uDataTypes` a Maven artifact?" — **CONFIRMED: no**

I re-ran every probe with live network access:

```
q=uDataTypes           -> 0 []
q=udatatypes           -> 0 []
q=a:udatatypes         -> 0 []
q=g:atenearesearchgroup-> 0 []
q=fc:uDataTypes.UReal  -> 0 []
q=fc:uDataTypes.SBoolean -> 0 []
q=g:org.tzi.use        -> 0 []
https://repo1.maven.org/maven2/es/uma/lcc/atenea/   -> 404
https://repo1.maven.org/maven2/uDataTypes/          -> 404
https://repo1.maven.org/maven2/atenearesearchgroup/ -> 404
```

`fc:` is Central's fully-qualified-class index, so zero hits is a strong negative: no artifact on
Central under any coordinates contains `uDataTypes.UReal` or `uDataTypes.SBoolean`.
`org.tzi.use` is likewise absent from Central. JitPack is ruled out:
`find $UDT -name pom.xml -o -name 'build.gradle*'` → nothing. **All confirmed.**

**Does the recommendation respect "reference repositories are never build inputs"? — Yes, and this
is the one part of §7 that survives intact.** Option A2 *copies* 18 files out of `$UDT` into
`use-core/src/main/java/…` and rewrites their `package` line; after that, no path under
`.git/reference-repositories/` appears on any source path, classpath or module path. The oracle side
is likewise already satisfied without touching the reference tree: a byte-identical copy of the jar
is committed at `use-core/src/test/resources/historical/atenearesearchgroup.uncertainty.jar`,
md5 `a3055f54205babaa27484fa94efdda1c` on both, and `use.jar` md5
`8645269c1eacbf8cb52bf7f694c07b21` on both — I re-ran `md5sum` on all four. Options B and C, as §15
says, either point at `$FORK/lib` (a violation) or require out-of-band `install:install-file` state.
That part of the analysis is sound; only its A1-vs-A2 discriminator is not (F2).

**Supporting §7 facts I re-verified and confirm:**

* Jar: 39 entries, 77 674 bytes, classes under `uDataTypes/`, timestamps 2021-02-24, ships
  `.classpath` / `.project` / `.settings/org.eclipse.jdt.core.prefs` / `.gitignore` /
  `uDataTypes.iml`, and **zero `META-INF` entries** — so no manifest, no `Automatic-Module-Name`.
  Class-file **major version 52**. All confirmed.
* Licence: `$UDT/Libraries/Java/README.md:261-269` — `## License`, "MIT Licence",
  "Copyright (c) 2023 Atenea Research group", permission text inline; `:273` — *"This is the first
  version of this Java library (September 2023)"*. No `LICENSE` file; `find $UDT -name .git` →
  nothing. Confirmed, including §15's own `UNVERIFIABLE` about the 2021 jar's licence metadata.
* Sources compile clean under JDK 21 (`javac -nowarn`, exit 0, 18 files).
* Public-API superset for all seven checked classes (see F5 for the corrected count).
* **The `divideBy` divergence reproduces exactly.** Reflective probe against jar vs. compiled source:
  ```
                                                 JAR (2021)             SRC (2023)
  UReal(1,0).divideBy(UReal(2,0.3), 0.0)      →  UReal(0.500, 0.000)    UReal(0.500, 0.075)
  UInteger(6,0).divideBy(UInteger(3,1), 0.0)  →  UInteger(2, 0.000)     UInteger(2, 0.111)
  UInteger(6,0).divideByR(UInteger(3,1), 0.0) →  UReal(2.000, 0.000)    UReal(2.000, 0.111)
  ```
  and the **single-argument forms agree on both sides**:
  `UReal.divideBy → UReal(0.500, 0.075)`, `UInteger.divideBy → UInteger(2, 0.111)`,
  `UInteger.divideByR → UReal(2.000, 0.111)` under jar *and* source. Since the fork only ever calls
  the single-argument forms (F6), §15's conclusion — the 2023 source is a safe stand-in on every
  path the port exercises — is **CONFIRMED**.
* 7 fork files import `uDataTypes`, and exactly five types are imported
  (`SBoolean`, `UBoolean`, `UInteger`, `UReal`, `UString`) — confirmed by
  `grep -rl` / `grep -rh … | sort -u` over `$FORK/src`.

---

## 3. Other §15 claims spot-checked and confirmed

* **§0.1 fork base date.** The `$Id` histogram reproduces exactly (tail: `5991 2016-06-21`,
  `6117 2016-12-14`, `6121 2016-12-22`, `6272 2017-08-24`, `3× 6289 2017-11-27`,
  `6361 2018-04-05`); **163** files carry `r5494 2015-02-05`; `$FSRC/config/Options.java:20` is
  `r6361 2018-04-05` and `:51` declares `RELEASE_VERSION = "0.142.0"`;
  `RealValue.java`/`IntegerValue.java` both carry `r6289 2017-11-27`. All confirmed.
* **Fork base is past the `VoidHandling` boundary.** Confirmed structurally: the fork's `Type.java`
  already carries `public enum VoidHandling { INCLUDE_VOID, EXCLUDE_VOID }` and every
  `isKindOfX(VoidHandling h)` signature, and the fork↔7.5.0 `Type` diff contains no signature change
  to any of them.
* **All eight cited upstream commits exist in `$UP` with the claimed date and subject** —
  `750fa544` 2015-02-05 "- Reintegrated PDM-branch (switch to USE Version 4)",
  `72ab8fd7` 2019-06-27 "changed Undefined to null",
  `767320db` 2021-08-01 "Maven Build", `99ff26c2` 2021-07-29 "Maven Build",
  `fb866f31` 2024-04-22 "USE now supports data types",
  `29171370` 2024-06-20 "Removed support for number operations 'pow' and 'sqrt'…",
  `46c277e7` 2024-11-24 "Introduced ExpInstanceOp as parent class of ExpObjOp and ExpInstanceConstructor",
  `4dd26e4d` 2025-06-10 "…access to imported elements via model qualifier…". Confirmed.
* **§6.1's census arithmetic.** `comm -23` of the fork's `src/main` file list against the union of
  `use-core` + `use-gui` main sources gives **62** fork-only paths; removing the 15
  `analysis/metrics` files, `main/Main.java`, `util/input/ShellReadline.java` and the 12 checked-in
  ANTLR lexers/parsers leaves exactly **33**, split 7 / 7 / 8 / 5 / 6 across
  `uml/ocl/value` / `uml/ocl/type` / `uml/ocl/expr` / `uml/ocl/expr/operations` / `parser/ocl`,
  file for file as §15's table lists. Confirmed.
* **§6.3 grammar deltas.** Fork-side `identicalExpression` rule at `$FSRC/parser/base/OCLBase.gpart:128-137`
  (comment from :124), wired in via `nIdExp=identicalExpression` at :74; 7.5.0 wires
  `expression → conditionalImpliesExpression` directly. Confidence argument:
  fork `:346` `( COMMA uncerExp=additiveExpression { uncer = $uncerExp.n;} )?` feeding the 5-arg
  `new ASTQueryExpression($op, $range, decl, $nExp.n, uncer)` at `:348`, vs. 7.5.0's 4-arg form at
  `grammars/base/OCLBase.gpart:331`. Upstream-side `importStatement` / `importClause` /
  `elementIdent` / `artifact` at `grammars/base/USEBase.gpart:9-27`. Grammar parts live at
  `use-core/src/main/resources/grammars/{base,generator,ocl,shell,soil,testsuite,use}/`, 11 `.gpart`
  files, same 11 names as the fork's. All confirmed — **subject to F3's CRLF caveat.**
* **§6.2's method** reproduces; I did not re-derive every row of the 23-file table.

---

## 4. `UNVERIFIABLE`

* **The exact per-file diff counts in §6.2's table** (763 / 307 / 226 / 218 / …). I re-derived the
  method and confirmed it runs, and confirmed the qualitative claims for `OpGeneric` (7),
  `Value` (34-ish → the diff is 4 hunks / 33 removed lines), `Type`, `ExpressionVisitor`,
  `TypeImpl`, but I did not re-count all 23 rows. Nothing in the port depends on the exact integers.
* **§4.3's characterisation of *why* `StandardOperationsNumber`/`Any`/`Collection` diverge**
  ("both sides rewrote", "import-wildcard collapse"). I confirmed `Op_number_pow`/`sqrt` exist only
  on the 7.5.0 side and that `Op_equal.matches` is as quoted, but I did not audit the 763-line
  numeric diff hunk by hunk. §15's instruction to three-way-merge these three files is prudent
  regardless.
* **§5.1's surefire flag set** — §15 already marks this `UNVERIFIABLE` and it remains so; the XML
  records paths, not the argument line, and establishing it would require running Maven.
* **Whether `use-gui` contains value/type dispatch needing widening** — §15 marks this
  `UNVERIFIABLE`; I did not audit it either. I did independently confirm the input to that question:
  `grep -rlE 'UReal|UInteger|UBoolean|SBoolean|UString|uDataTypes|Uncertain' $FORK/src/main` matches
  no file under `org/tzi/use/gui`, so there is no fork-side evidence either way.
* **§7.3's per-method bytecode table** (methods-only-in-jar / changed-bodies / semantic columns).
  I confirmed the public-API diff and the three behavioural divergences by execution, which is the
  stronger evidence; I did not reproduce the constant-pool-normalised body comparison.
* **Provenance linking the 2021 jar to any published source revision** — §15 marks this
  `UNVERIFIABLE`; confirmed as still unverifiable (`find $UDT -name .git` → nothing; jar has no
  `META-INF`).

---

## 5. What the port should change as a result

1. **Treat `conformsTo` as abstract.** Override it explicitly in all seven new `*Type` classes and
   add a test that calls `conformsTo` on each, or the failure mode is `StackOverflowError` at first
   use rather than a wrong-but-recoverable `false`. (F1)
2. **Re-argue A1 vs A2 on real grounds.** The `uDataTypes.` prefix is already isolated parent-last
   (`IsolatedJarClassLoader.java:51-52,80-83`) and asserted by
   `HistoricalOracleIsolationTest.java:69-70`. If A2 is kept — and there is a good defence-in-depth
   case for it — record *that* as the reason, and delete the claim that a plain platform-parented
   loader would fix A1: this repository has measured that it would not. (F2)
3. **Normalise line endings before any grammar merge.** All 7.5.0 `.gpart` resources are CRLF, the
   fork's are LF; every naive diff shows 100 % change. (F3)
4. **Size A2 as 18 files, not 15.** (F4)
