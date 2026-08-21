# B7 — the fix plan for the 33 behaviour-changing ledger rows

**Role: Spec. This document triages and designs.** Written 2026-08-17 on branch `port-uncertainty-2`,
before any of the 33 rows had landed. **This section numbering is a citation anchor — 25+ shipped
source files cite this document by exact section and row id (e.g. "b7-fix-plan.md section 2 M-11").
Section 0-2 keep their original structure for that reason; later sections have been compressed now
that the port has shipped and the tests these rows describe are passing.**

**Provenance note (2026-08-21).** `spec-parts/20-ops-SBoolean.md`, cited below, was consolidated
during a documentation cleanup and no longer exists as a separate file; its `deduceY` hazard
analysis migrated to `SBooleanValue.java`'s Javadoc. Full original content in git history.

---

## 0. The decision, and the consequence the record must carry

| id | The user's decision (2026-08-17, binding) | The recommendation that was **not** taken |
|---|---|---|
| **B7** | **FIX** the historical defects, documenting each one. | **Bug-for-bug reproduction.** Recorded at `specification.md:183` and `16-modernization-ledger.md:30` ("For those rows the *proposed change* column reads **DEFER**"). |

Recorded so it is not re-litigated: the recommendation on file was bug-for-bug faithfulness. The user
chose to fix. **Do not re-open this.**

### 0.1 The consequence, stated once, in the strongest available terms

> **The port will deliberately not be bit-faithful to the fork.** On the affected operations the
> differential harness **will report `DIFFER` against the historical oracle by design**, and any
> historical corpus entry whose expected output depends on a defect **will fail by design**.
>
> Therefore: **every such divergence must be pre-registered before the sweep that produces it runs.**
> A divergence that is *discovered* is indistinguishable from a porting error. S4–S7 have no other
> way to tell an intended correction from a mistake, because the only instrument they have
> (`DifferentialSweep`) measures *difference*, not *intent*. See §4.

This is not a hypothetical. `harness-contract.md:198-204` makes "no row disagreed" clause 2 of the
one gate a stage is allowed to use, and it counts **every** non-agreement verdict as a disagreement.
Under B7, clause 2 fails on the corrected operations unless a mechanism exists to say "this one was
meant". §4 designs that mechanism.

### 0.2 What triage found that the record gets wrong

Three findings below **reduce** the blast radius the ledger predicted, and two **increase** it. All
five are load-bearing for S8 and are argued from source in §3 and §7:

1. **`Set{…}` print order is decided by `compareTo`, not by `hashCode`.** Both trees print through
   `Collections.sort` (`use-core/src/main/java/org/tzi/use/uml/ocl/value/SetValue.java:319-323` →
   `CollectionValue.java:169-173`; fork `SetValue.java:322-326` → `CollectionValue.java:278-282`).
   The ledger's repeated claim that fixing `hashCode` (F-3, F-10) "changes `Set{…}` iteration order
   and hence the printed output the `.in` fixtures assert on"
   (`16-modernization-ledger.md:63,74`; `specification.md` §7.2 F-3/F-10) is **wrong about the
   mechanism**. Hash changes can only change *membership*; order comes from `compareTo`.
2. **`Undefined or Undefined` does not reach `Op_uBoolean_or`.** The corpus expects
   `-> Undefined : OclVoid` (`UBooleanExpression.in:143-144`) and gets it from
   `Op_boolean_or`, which is registered **first** (`OpGeneric.java:90` vs `:94`) and matches
   `(OclVoid, OclVoid)` (`VoidType.java:62-65` → `true` under `INCLUDE_VOID`), and
   `ExpStdOp.create` stops at the first match (`ExpStdOp.java:129-135`). The M-38 NPE is real but
   **unreachable from any corpus entry**.
3. **`Op_uInteger_value`'s printed type suffix is the runtime type, not the static type.**
   `Value.toStringWithType(StringBuilder)` calls `getRuntimeType()`
   (fork `value/Value.java:204-208`), so the 9 corpus entries already read `-> 3 : Integer`
   (`UIntegerExpression.in:43-44`). M-37's fix changes zero corpus expectations.
4. **NEW RISK — M-18's obvious fix introduces a non-transitive comparator.**
   `uDataTypes/SBoolean.java:1570-1578` returns `0` whenever the L1 distance of the four masses is
   `< 0.001D`. Delegating `SBooleanValue.compareTo` to it makes `a~b`, `b~c`, `a≁c` reachable, which
   is exactly the input Java 21's TimSort rejects with
   `IllegalArgumentException: Comparison method violates its general contract`. See §2 M-18 and §7.4.
5. **NEW RISK — M-11's fix creates a new cross-type `true`.**
   `UStringValue.equals` lifts a `StringValue` argument to confidence `1.0`
   (`UStringValue.java:27-36`). Fixing the comparison to `wrapper.equals(ustring.wrapper)` therefore
   makes `UString('x', 1.0) = 'x'` evaluate **true** where the fork gave **false** — a widening the
   fork's asymmetric `StringValue.equals` does not mirror. See §2 M-11.

---

## 1. THE FOUR CRITICALS FIRST

Each fix below was verified against the historical source at
`.git/reference-repositories/uncertainty/USE-Uncertainty` (read-only, per ground rule 3) before it
landed. Section and row-id anchors (`C1`-`C4`) are cited from shipped source; keep them stable.

### C1 — `UStringValue.equals` is constant `false` (ledger **M-11**)

**Site:** `src/main/org/tzi/use/uml/ocl/value/UStringValue.java:79-91`. Two independent defects in
one expression: `wrapper.getString()` returns `java.lang.String` and `ustring.wrapper` is a
`uDataTypes.UString`, so `String.equals(Object)` is `false` for every argument; and the second
conjunct compares the receiver's confidence **to itself** — the argument is never read. Net:
`a.equals(a)` is `false`, reflexivity is broken, and no `UStringValue` can be found in any `HashSet`.

**THE FIX** — replace both conjuncts with one delegation: `eq = wrapper.equals(ustring.wrapper);`

**Why that is the right delegate.** The vendored library source
(`uDataTypes/UString.java:111-119`) already compares both the string and the confidence via
`Double.compare` (so `NaN` confidences compare equal, matching `hashCode`'s `doubleToLongBits`), and
`UStringValue.hashCode` already delegates to `wrapper.hashCode()` — so **the fix repairs the
equals/hashCode contract at the same time, with no change to `hashCode`.**

**What observable output changes.** (a) `UStringValue` becomes usable in any hash container. (b) OCL
`.equals` (`Op_identical`) starts answering `true`. (c) **New cross-type `true`:** `valueOf(Value)`
lifts a `StringValue` to confidence `1`, so `UString('x', 1.0).equals('x')` flips `false → true`.
`StringValue.equals` has no `UStringValue` arm, so the relation stays asymmetric in the *other*
direction — the fix does **not** make it symmetric, and §1.8 of `specification.md` forbids editing
`StringValue`. **Recorded as a declared residual, not an oversight** (§7.2 item 2).

**Owner:** S7. **Corpus impact: zero** — the corpus contains no `UString` token at all (§3, fact F2).

### C2 — `UIntegerValue.hashCode` collapses to 0 whenever uncertainty is 0 (ledger **F-10**)

**Site:** `UIntegerValue.java:57-64`. `Double.hashCode(0.0) == 0` and the combining operator is
`*=`, not `+`, so **every** `UInteger(n, 0)` hashes to `0`. The sibling four lines away
(`URealValue.java:56-64`) has the additive, zero-guarded form the comment describes.

**THE FIX** — replace `UIntegerValue.hashCode`'s two arithmetic lines with `URealValue`'s body,
unchanged, so the two are textually identical and the bridge the comment asserts
(`UReal(1,0)` and `UInteger(1,0)` in one bucket) actually holds.

**Note what the fix does NOT achieve.** `IntegerValue.hashCode`/`RealValue.hashCode` are upstream and
unedited, so `IntegerValue.valueOf(1)` and `UIntegerValue(1,0)` still land in different buckets — the
fix aligns the two *uncertain* classes with each other and no further (declared residual, §7.2 item 3).

**What observable output changes.** Bucket layout inside `HashSet<Value>`, hence *membership* of a
`SetValue`, hence its **cardinality** — but **not** its print order (§0.2 item 1). Membership changes
only where two elements are `equals` and were previously hashed apart; `HashSet` still consults
`equals` inside a bucket, so **this fix cannot change the contents of any set that was already
correctly ordered by `equals`.** It is nevertheless behaviour-changing because `hashCode()` is public.

**Owner:** S5. **Corpus impact: zero** (§3).

### C3 — `Op_uBoolean_or` NPEs on `Undefined or Undefined` (ledger **M-38**)

**Site:** `StandardOperationsUBoolean.java:470-478`. Control reaches this branch only when `v1` is
undefined; if `v2` is also undefined, `UBooleanValue.valueOf(v2)` returns `null` (it returns `null`
unless the argument `isUBoolean()`/`isBoolean()`) and `ub2.probability()` dereferences it. The two
guarded siblings, `Op_uBoolean_and` (`:411-414`, guarded by `v2.isDefined()`) and
`Op_uBoolean_implies` (`:552`), do not have this gap.

**THE FIX** — one guard, matching `and`'s shape exactly: wrap the body in `if (v2.isDefined()) { … }`,
using `v2.isDefined()` rather than a post-hoc `ub2 != null` check, so the two operations become
structurally identical. `result` is already initialised to `UndefinedValue.instance`, so no `else`.

**What observable output changes.** `NullPointerException` → `Undefined`. Nothing else.

**Reachability, corrected against the record.** `16-modernization-ledger.md:103` and
`specification.md` §7.2 M-38 both say "so `Undefined or Undefined` throws `NullPointerException`
today" — true of the *method*, false of the *OCL expression* (§0.2 item 2): `Op_boolean_or` shadows
it and the corpus expectation (`UBooleanExpression.in:143-144`, `-> Undefined : OclVoid`) is met
without `Op_uBoolean_or` ever running. The NPE **is** reachable, but only where at least one static
operand type is `UBoolean` (so `Op_boolean_or` fails to match) while both runtime values are
`Undefined` — e.g. `UBoolean(true, 3 - 5) or UBoolean(true, 3 - 5)`, not present in the corpus.

**Owner:** S6. **Corpus impact: zero.** S6 added that witness as a new test, since the fix is
otherwise unobserved by the historical oracle.

### C4 — the 12 `assertEquals` sites that rebind silently under Jupiter (ledger **CF-7**)

**Sites (12):** `UIntegerExpOpsTest.java:29,36,43,50,57,64,71,82,89,96` (10) and
`USECompilerUncertaintyTest.java:90,94` (2).

**Why these 12 and no others.** CF-6's 943 sites are safe *because they break the build*: Jupiter has
no `(String, Object, Object)` overload, so a `(String, non-String, non-String)` triple is a hard
compile error. These 12 have **three `String` arguments**, so they bind silently to
`assertEquals(Object expected, Object actual, String message)` — the message becomes `expected`,
`expected` becomes `actual`, `actual` becomes the message, and **no warning is emitted.** Two of the
three arguments at each `UIntegerExpOpsTest` site are the same expression, so those nine would fail
loudly if left as written — but the two `USECompilerUncertaintyTest` sites would compare the
*message* against the *expected*, a silent failure mode: a wrong message on a real failure.

**THE FIX** — reorder to `(expected, actual, message)`, by hand, one site at a time.

**Three mandatory procedural controls**, because a compiler cannot help here: (1) **do not batch-edit**
— a regex over `assertEquals(a, b, c)` cannot distinguish these from the 943; edit by line number.
(2) **prove the migration by counting first** — before editing, confirm the ledger's own detector
still finds exactly 10 + 2 (`16-modernization-ledger.md:208`). (3) **prove it after the edit by
mutation** — for each of the 12, temporarily corrupt the *expected* string by one character and
confirm the test goes red, then revert; this is the only positive evidence available, since there is
no compile-time signal at all.

**Owners:** S5 (10 `UIntegerExpOpsTest` sites), S8 (2 `USECompilerUncertaintyTest` sites — that file
is the corpus harness, so a mis-bound site there makes S8's entire classification unsound). **The
highest-leverage of the four criticals and the only one whose failure mode is silent.**

---

## 2. All 33 behaviour-changing rows, in one table

Row set derived mechanically: `grep -nE '^\| (CF-|M-|\*\*F-)' spec-parts/16-modernization-ledger.md
| grep 'BEHAVIOUR-CHANGING' | wc -l` → **33**.

**Stage ownership.** The record does not enumerate S3–S10 anywhere (`foundation-verdict.md` names
only S1 and "S4–S7"). The assignment below uses the framing in the task brief — S3 foundation, S4
UReal, S5 UInteger, S6 UBoolean, S7 UString, S8 corpus/`.in` harness, S9 SBoolean, S10 verdict — and
is corroborated where the record does speak (`specification.md:179`, `foundation-verdict.md:243`,
`harness-contract.md` §7-§8). **This mapping was proposed, not established, and was confirmed by the
human before S3.** Each row's owner is derived from the file it touches, so the mapping can be re-cut
without re-triaging.

Legend for **Δoutput**: `NONE` = no observable output changes; `TYPE` = a static type changes;
`TEXT` = a printed/message string changes; `VALUE` = an evaluated value changes;
`SET` = `Set`/`Bag` membership or printed contents change; `ERR` = an error path changes;
`TREE` = the evaluation tree / context stack shape changes.

| id | file:line (fork root) | The defect | The fix | Owner | Δoutput — what observably changes |
|---|---|---|---|---|---|
| **CF-7** | `FT/uml/ocl/expr/UIntegerExpOpsTest.java:29,36,43,50,57,64,71,82,89,96`; `FT/parser/uncertainty/USECompilerUncertaintyTest.java:90,94` | all three args are `String`, so Jupiter binds `(Object,Object,String)`: message→expected, expected→actual, actual→message. **Compiles clean, no warning** | reorder to `(expected, actual, message)` by hand, 12 sites, each mutation-verified. **§1 C4** | S5 (10), S8 (2) | `TEXT` — 9 of the 10 `UIntegerExpOps` sites go red if left as written; the 2 S8 sites silently mis-report on failure, corrupting the adjudication of all 1427 corpus entries |
| **F-10** | `F/uml/ocl/value/UIntegerValue.java:56-64` | `hash *= 7 * Double.hashCode(uncertainty())` and `Double.hashCode(0.0)==0`, so **every** `UInteger(n,0)` hashes to `0` | copy `URealValue.java:56-64`'s additive zero-guarded body verbatim. **§1 C2** | S5 | `NONE` observable through OCL. Bucket layout only; `HashSet` still consults `equals`, so set contents are unchanged, and print order is `Collections.sort` (§0.2/1). `hashCode()` itself is public and changes |
| **M-38** | `F/…/operations/StandardOperationsUBoolean.java:470-478` | `ub2.probability()` at `:476` with no null guard; `valueOf(UndefinedValue)` is `null` ⇒ NPE | wrap in `if (v2.isDefined())`, matching `Op_uBoolean_and:411-414`. **§1 C3** | S6 | `ERR` — NPE → `Undefined`. **Unreachable from the corpus** (`Op_boolean_or` shadows it); S6 must add the witness `UBoolean(true, 3-5) or UBoolean(true, 3-5)` |
| **M-11** | `F/uml/ocl/value/UStringValue.java:79-91` | `String.equals(UString)` is always false; second conjunct compares `wrapper` to itself. **`a.equals(a)` is `false`** | `eq = wrapper.equals(ustring.wrapper);` — delegate to `uDataTypes/UString.java:111-119`. **§1 C1** | S7 | `SET` + `VALUE` — `UStringValue` becomes hashable; `Op_identical` starts returning `true`; **new** cross-type `UString('x',1.0) = 'x'` ⇒ `true`. Asymmetry with `StringValue.equals` remains, by declared residual |
| **CF-5** | `FT/uml/ocl/value/AllTests.java:37-44`; `FT/uml/ocl/expr/AllTests.java:37-50`; `FT/parser/uncertainty/AllTests.java:15-19` | the three JUnit-3 suites pin execution order; surefire's default `runOrder` is `filesystem` | delete the suites **and** remove the order dependency at its source by fixing M-45 (self-restoring `Options` write). Do **not** pin surefire order — pinning preserves the coupling instead of removing it | S3 | `NONE` if M-45 lands in the same commit; otherwise **`VALUE` on an unbounded set of tests**, because `Options.explicitVariableDeclarations` leaks (`F/config/Options.java:155`) |
| **CF-8** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:22-24,56-57,63` | `user.dir`-relative fixture path; under Maven the module root is `use-core/`, so `listFiles` → `null`, **or** an empty directory makes the loop run zero times and the test **pass vacuously** | move all **four** `.in` files to `use-core/src/test/resources/…`, resolve via classpath, **and add `assertTrue(files.length > 0)`**, plus sort the file list (`File.listFiles` order is unspecified) | S8 | `ERR` — **a previously-vacuous pass becomes a failure.** That is itself the behaviour change and must be stated as one. Sorting makes the run replayable |
| **CF-9** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:73,151` | platform-default charset on the fixture read and on `expression.getBytes()` | pass `StandardCharsets.UTF_8` to both | S8 | `NONE` on Java 18+ (JEP 400 already defaults to UTF-8). `TEXT` only under an explicit `-Dfile.encoding=…`. The only non-ASCII bytes are on `# Creación` comment lines the reader skips at `:116` |
| **M-6** | `F/…/URealValue.java:160`; `UIntegerValue.java:124`; `UStringValue.java:48`; `SBooleanValue.java:159` | bare `throw new RuntimeException("A value kind of … expected")` | **DO NOT NARROW.** Keep `RuntimeException`; add a javadoc `@throws` naming the contract | S4/S5/S7/S9 (policy set in S3) | `NONE` as recommended. Narrowing to `IllegalArgumentException` is `ERR`: `FT/…/ExpQueryUncertaintyTest.java:179,200` catch `RuntimeException` (a subclass satisfies it) but `ExpConstSBoolean.java:57` and `ASTSBooleanLiteral.java:35` `catch (Exception)` and swallow it, and the full downstream `catch` set **could not be enumerated** |
| **F-2** | `F/util/MathUtil.java:106-109`, called with `digits=10` from 15 sites | `Math.round(double)` returns `long`; `value * 1e10` **saturates** above `9.223372036854776e8`, so two unequal large `URealValue`s compare **equal**. Measured: `Math.round(9.3e8*1e10)/1e10` and `Math.round(9.4e8*1e10)/1e10` are both `9.223372036854776E8` | **Port `round` byte-identically first (E25, F-1), commit it, and fix the saturation in a SECOND commit** using `BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).doubleValue()`. Two commits so the 101 ten-decimal assertions are shown green against the verbatim body before anything moves | S3 | `VALUE` above `9.2e8` only. **No test and no corpus entry reaches that magnitude**, so this fix has no observable effect on any existing evidence — which is exactly why it needs a purpose-built test (§7.3) |
| **F-3** | `F/uml/ocl/value/URealValue.java:56-64` vs `:67-91` | `hashCode` hashes **unrounded** `value()`/`uncertainty()`; `equals` compares them **rounded to 10 dp**. Contract violated: two `equals` values can land in different buckets | round inside `hashCode` with the same `MathUtil.round(x, 10)` the `equals` arm uses, in the same order, so the two are textually parallel | S4 | `SET` — membership only, and only for pairs differing beyond the 10th decimal. **No corpus element has more than 10 decimals** (§3), so zero corpus effect. Print order is unaffected (§0.2/1) |
| **F-4** | `F/uml/ocl/value/URealValue.java:84,87` | the `IntegerValue`/`RealValue` arms use raw `==` with no rounding, unlike the `URealValue` arm three lines above | round both sides with `MathUtil.round(x, 10)` in both cross-type arms, matching the arm above | S4 | `VALUE` — a **widening**: rounding can add equalities, never remove them. So `false→true` is possible and `true→false` is not. Asymmetry with `RealValue.equals` (no `URealValue` arm, and it uses `FloatUtil.equals` ε=10⁻⁸) **remains** — `RealValue.java` is edited only to add `valueOf` (E26) |
| **M-8** | `F/uml/ocl/value/UBooleanValue.java:233-234` | `(other.isFalse() && probability()==0 && !value())` is dead: `valueOf` normalises every value to `value == true` (`:100-103`), so `UBooleanValue.FALSE.equals(BooleanValue.FALSE)` is `false` | delete `&& !this.value()`. The remaining `other.isFalse() && probability()==0` is exactly the normalised encoding of false | S6 | `VALUE` — OCL `UBoolean(true, 0).equals(false)` flips `false→true`. **Do not instead "fix" the normalisation** — F-7/F-8 pin `valueOf`'s exact arithmetic and 4 value-test assertions depend on it |
| **M-9** | `F/uml/ocl/value/UIntegerValue.java:103-104` | `res = o.compareTo(this)` delegates to `URealValue.compareTo` **without negating the sign** | `res = -o.compareTo(this);` | S5 | `SET` (order). Today the composite is a constant `0` only because `URealValue.compareTo`'s `UIntegerValue` case falls through its four `if`s (`URealValue.java:95-110` has no `UIntegerValue` arm; the fourth arm is the unreachable duplicate M-3 deletes). **After M-9 alone the answer is still `0`** — the negation of nothing. **M-9 must land together with an added `UIntegerValue` arm in `URealValue.compareTo`, or it is a no-op dressed as a fix.** See §7.1 |
| **M-10** | `F/uml/ocl/value/UIntegerValue.java:84-86` | `eq = obj.equals(this)` delegates to `URealValue.equals`, whose arm list is `URealValue`/`IntegerValue`/`RealValue` — a `UIntegerValue` argument falls through to `false` | add a `UIntegerValue` arm to `URealValue.equals` that lifts via `((UIntegerValue) obj).toUReal()` and then applies the rounded `URealValue` comparison | S4 (the edit) / S5 (the observation) | `VALUE` + `SET` — cross-type `UInteger(2,5) = UReal(2,5)` starts answering `true`. **Must land with F-3**, or the new equality pairs are hashed apart |
| **M-12** | `F/uml/ocl/value/UStringValue.java:95-104` | `:103` compares a bare `String` against the **wrapper form** `UString('x', 1.0)`; and `:100`'s `!(o instanceof StringValue)` diverts every UString-vs-UString comparison to `toString().compareTo(...)` | fix **only** `:103` to `wrapper.getString().compareTo(((StringValue) o).value())`. **Leave `:100`** — the `toString()` route is self-consistent and total, and changing it is a separate decision | S7 | `SET` (order) — sort position of a `UString` relative to a plain `String`. Zero corpus exposure (no `UString` token in any `.in`) |
| **M-18** | `F/uml/ocl/value/SBooleanValue.java:150-153` | `public int compareTo(Value o) { return 0; }` — every `SBooleanValue` compares equal to every `Value`, `UndefinedValue` and `StringValue` included | **DO NOT delegate to `SBoolean.compareTo`.** Implement a total order locally: `UndefinedValue → +1`; `SBooleanValue →` lexicographic `Double.compare` on `(belief, disbelief, uncertainty, baseRate)`; otherwise `toString().compareTo(o.toString())` — the idiom `URealValue`/`UStringValue` already use | S9 | `SET` (order) + `ERR`. **Why not delegate:** `uDataTypes/SBoolean.java:1570-1578` returns `0` when the L1 distance of the four masses is `< 0.001D`, which is **not transitive**, and Java 21's TimSort throws `IllegalArgumentException: Comparison method violates its general contract` on such a comparator. Delegating would trade one defect for a crash |
| **M-21** | `F/…/type/UIntegerType.java:39-45`, `URealType.java:33-38`, `UBooleanType.java:33-40`, `UStringType.java:23-28` | `this` and `TypeFactory.mkX()` used interchangeably for the self-entry in `allSupertypes()` | unify on **`this`** (not `mkX()`) in all four | S3 | `SET` — `allSupertypes()` contents differ for **non-singleton instances**: `FT/uml/ocl/type/TypeTest.java:380-403` constructs `new UIntegerType()` etc. directly, and for those `this != mkX()`. Choosing `this` makes a directly-constructed type's supertype set contain itself, which is what every other `allSupertypes()` in the tree means |
| **M-22** | `F/…/type/UIntegerType.java:14` `public`; `URealType.java:8` `protected`; `UBooleanType.java:8`, `UStringType.java:8`, `SBooleanType.java:8` package-private | three constructor visibilities across five sibling types | narrow all five to **package-private** | S3 | `NONE` in-repo — all callers are `TypeFactory.java:48-56` and `TT/uml/ocl/type/TypeTest.java` (same package). **API-surface change:** `public UIntegerType()` is a published constructor; narrowing is source- and binary-incompatible for any out-of-tree plugin. **The port is a new module, so there is no installed base to break — record that as the justification** |
| **M-26** | `F/uml/ocl/expr/ExpDefSBoolean.java:22-29` | `ctx.enter(this)` at `:25` with no matching `ctx.exit(…)` before the `return` at `:28` | **MOOT — do not port the class** (B10). If B10 flips to "port", add `ctx.exit(this, result)` before the return | S9 | `TREE` if ported — the printed evaluation tree (`-vv`, the GUI evaluation browser) and the context-stack depth of anything nested inside. `NONE` under B10 = drop |
| **M-27** | `F/uml/ocl/expr/ExpDefSBoolean.java:15-16` | the guard is **inverted** relative to its own message: it throws precisely when the argument **is** `Boolean` or `UBoolean` | **MOOT — do not port the class** (B10). If B10 flips, insert the missing `!` | S9 | `ERR` if ported. Today `SBoolean(someUBooleanExpr)` throws and `SBoolean(someStringExpr)` is accepted and then yields Java `null` from `SBooleanValue.valueOf` (`:71-88`). `NONE` under B10 = drop |
| **M-28** | `F/uml/ocl/expr/ExpConstUBoolean.java:47` | `Double.valueOf(probability.toString())` round-trips typed `Value`s through their `String` form | **DO NOT rewrite to direct accessors.** Keep the round-trip; add a javadoc naming the two behaviours it carries | S6 | `NONE` as recommended. A rewrite is `ERR` + `VALUE`: `Double.valueOf(anIntegerValue.toString())` yields `1.0` where `((IntegerValue) p).value()` yields `1`, **and** the `NumberFormatException` a malformed string raises is what `catch (RuntimeException)` at `:49-51` converts to `Undefined`. A direct-accessor rewrite deletes that path |
| **M-29** | `F/uml/ocl/expr/ExpConstUBoolean.java:44` | only `probability.isUndefined()` is checked; an undefined `value` gives `value.toString() == "Undefined"`, so `Boolean.valueOf` → `false`, and `valueOf(false, p)` flips it to `(true, 1-p)` — **a defined result from an undefined operand** | `if (value.isUndefined() \|\| probability.isUndefined())`, matching `ExpConstUInteger.java:34` and `ExpConstUReal.java:36` | S6 | `VALUE` — an undefined value operand now yields `Undefined` instead of a fabricated UBoolean. **Corpus-reachable only through a `Boolean`-typed expression that evaluates to `Undefined`**; the two corpus attempts (`UBoolean(3 + 2, 1)`, `UBoolean(3 / 0, 1)`) are both **compile errors** at `ExpConstUBoolean.java:18`, so neither reaches `eval` |
| **M-30** | `F/uml/ocl/expr/ExpConstUString.java:44,48` | unguarded `(StringValue)` cast and unguarded `Double.valueOf(confidence.toString())`, so `ClassCastException`/`NumberFormatException` escape `eval` | wrap the body in `try { … } catch (Exception ex) { result = UndefinedValue.instance; }`, matching `ExpConstSBoolean.java:48-59` | S7 | `ERR` — an escaping exception becomes `Undefined`. **`UString(...)` has no corpus example at all** (`specification.md` §6.5), so this is unobserved by the historical oracle and needed a new test |
| **M-31** | `F/uml/ocl/expr/ExpConstUReal.java:13-17` | no type validation in the constructor, unlike all four siblings; the check lives in `ASTURealLiteral.java:27-31` instead | **DO NOT move it.** Leave the check in `ASTURealLiteral`; add an **unchecked** `assert`-free javadoc precondition on the constructor | S4 | `NONE` as recommended. Moving it is `ERR` and a **compile break**: `ExpConstUReal` is constructed directly with unvalidated `ExpConstReal` arguments at **300+** sites in `FT/…/URealExpOpsTest.java:34,39,44,…`, and a checked `ExpInvalidException` breaks every one. It would also move the two corpus error messages out of `SemanticException` (§3, `URealExpression.in:62,65`) |
| **M-32** | `F/parser/ocl/ASTURealLiteral.java:23-24` and `:34` | `eValue.gen(ctx)` and `eUncertainty.gen(ctx)` are each called **twice**; two distinct `Expression` graphs are built and the **second** is the one installed | hoist both into locals at `:23-24` and pass those locals at `:34` | S4 | `TREE` + possibly `VALUE`. `ASTExpression.gen(Context)` is not documented pure and registers into `ctx` for sub-expressions carrying variable declarations. The fix halves the number of `ctx` mutations and installs the **first** graph rather than the second |
| **M-33** | `F/parser/ocl/ASTUStringLiteral.java` (whole file) | no `toString()` override, unlike the other four AST literals | add `public String toString() { return "UString(" + eValue + ", " + eConfidence + ")"; }`, matching `ASTURealLiteral.java:43-46` | S7 | `TEXT` — the text of every `SemanticException` that interpolates this node. **No corpus entry mentions `UString`**, so no recorded expectation moves |
| **M-37** | `F/…/operations/StandardOperationsUInteger.java:13,17` + `Op_uInteger_value:54-64` | registered under both `"value"` and `"toInteger"`; `matches` declares `mkUInteger()` while `eval` returns an `IntegerValue`. The sibling `Op_ureal_value` correctly declares `mkReal()` (`StandardOperationsUReal.java:246-248`) | `matches` → `TypeFactory.mkInteger()` | S5 | `TYPE` only. `ExpStdOp.create` stores `matches`'s `Type` as the expression's **static** type (`ExpStdOp.java:130-133`), so type-checking of any *enclosing* expression changes. The printed suffix does **not**: `Value.toStringWithType` uses `getRuntimeType()` (`value/Value.java:204-208`), and the 9 corpus entries already read `: Integer` |
| **M-43** | `FT/uml/ocl/value/UBooleanValueTest.java:11-17,36-42` | two commented-out `try/fail/catch` blocks marked `// FIXME: When It will be fixed in atenea library` | revive as **`@Disabled`** Jupiter tests carrying the FIXME text as the disabled reason | S6 | `NONE` to any assertion. Two "skipped" entries appear in the surefire report. **Reviving them live makes the suite red** — they record that `valueOf(true,-2)`/`(true,2)` do **not** throw despite the guard at `UBooleanValue.java:46`, because the library ctor clamps first (F-9) |
| **M-44** | 40 sites: `FT/…/URealExpOpsTest.java:875-911`; `UIntegerExpOpsTest.java:106-142,267-313,484-510`; `UBooleanExpOpsTest.java:217-225,1611-1619,1663-1671`; `ExpQueryUncertaintyTest.java:154-206` | JUnit-3 idiom `try { …; fail("X expected"); } catch (X e) {} catch (Exception ex) { fail(…) }` | convert to `assertThrows(X.class, () -> …)` **and, at each site, assert the exception's message** so the subclass-widening is compensated. At `ExpQueryUncertaintyTest.java:174-178` the `try` holds **two** statements — the lambda must wrap **both** | S4/S5/S6 by file; `ExpQueryUncertaintyTest` → S8 | `ERR` — two distinct widenings. (a) `assertThrows` accepts any **subclass**, whereas the historical second `catch (Exception ex) { fail(…) }` narrowed it; `ExpQueryUncertaintyTest.java:179` catches `RuntimeException`, which today swallows even an `NullPointerException` **as a pass**. (b) Wrapping only the second statement silently narrows the assertion |
| **M-45** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:61` | `Options.explicitVariableDeclarations = false;` set once, never restored (`F/config/Options.java:155` declares it `true`) | save in `@BeforeEach`, restore in `@AfterEach` | S8 | `VALUE` on an **unbounded** set of tests — today every test running after this one in the same JVM sees `false`. **This is the coupling that makes CF-5 load-bearing; fixing M-45 is what makes deleting the suites safe. The two must land in one commit** |
| **M-48b** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:26-29` | `ExpressionTest` is a non-static inner class with the default `Object.toString()` | make it `private static class` (M-48a, preserving) and **DO NOT convert it to a `record`** | S8 | `TEXT` if converted — a `record`'s `toString()` is a component listing, and that string is interpolated into the assertion message at `:90`. **Instead add an explicit `toString()` returning the expression text**, which fixes gap G3 (`specification.md` §6.4: a failing error-path entry currently reports an identity hash) without the record's other effects |
| **M-49b** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:88` | `split("\n(\r\n)")` means "LF **followed by** CRLF", which no platform emits, so `errArray.length - 1 == 0` and `errMessage` is the **whole** captured stderr with `\n`/`\r` stripped | `split("\\r?\\n")` | S8 | `TEXT` — changes which line is compared against the `-> …` expectation for the **5** error-path entries. **This was the single riskiest S8 row**: the current degenerate behaviour is what makes those 5 pass today, and a correct split may select a different line |
| **M-51** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:99-101` | `catch (IOException ex) { throw new RuntimeException("Couldn't open file " + name); }` drops the cause | keep `RuntimeException` and the **same message**, passing `ex` as the cause: `new RuntimeException(msg, ex)` | S8 | `NONE` — type and message unchanged; only the stack trace grows. **Do not use `UncheckedIOException`**, which changes both |

**Count check.** 33 rows: CF-5, CF-7, CF-8, CF-9 (4) + M-6, M-8, M-9, M-10, M-11, M-12, M-18, M-21,
M-22, M-26, M-27, M-28, M-29, M-30, M-31, M-32, M-33, M-37, M-38, M-43, M-44, M-45, M-48b, M-49b,
M-51 (25) + F-2, F-3, F-4, F-10 (4) = **33**.

**Note on the shape of this triage.** The user decided "fix", and eight of the 33 rows are fixed by
**deciding not to change the code**: M-6, M-28, M-31, M-43 (fix = revive-as-`@Disabled`, not
revive-live), M-48b, M-51, and M-26/M-27 (moot under B10). That is not evasion of the decision — B7
says "fix the historical defects, documenting each", and for these eight the documented finding is
that the ledger's proposed change is *worse* than the defect, with the mechanism named in the Δoutput
column.

---

## 3. Corpus impact, per row

**The corpus.** 1427 entries across four files (`specification.md` §6.4). Five structural facts S8
depended on, each established by `grep` against the historical fixtures (transcripts omitted; the
commands are trivial `grep -c`/`grep -n` invocations, reproducible from the facts stated):

* **F1.** Collection literals appear in **exactly one** of the four files. All membership-, order- and
  hash-related corpus exposure is confined to `UCollectionOperations.in`'s **44** entries, and only
  **4** of those print a collection.
* **F2.** The corpus contains **no** `UString` and **no** `SBoolean` token. Every UString and SBoolean
  row has corpus impact **zero, exactly**, not "probably zero".
* **F3.** No collection literal contains a `UInteger`.
* **F4.** The five long-decimal hits in the corpus are all inside commented-out `# FIXME:` blocks
  (`URealExpression.in:230-234`, `:974-978`), so **no live corpus value carries more than 10 decimal
  digits**. Rounding to 10 dp is the identity on every live corpus number.
* **F5.** Exactly **5** entries take the error path (no ` : Type` suffix): `UBooleanExpression.in:8`,
  `:11`, `:14`, `URealExpression.in:62`, `:65`.

**The print-order mechanism, since four rows turn on it.** Both trees print a `SetValue` through
`getSortedElements()` — `Collections.sort` on `Value.compareTo`
(`SetValue.java:319-323` → `CollectionValue.java:169-173`, identical in the fork). So: **`hashCode`
fixes change membership; `compareTo` fixes change order.** The corpus's own output confirms it —
`UCollectionOperations.in:161` prints `Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)}`, which is **not**
ascending, because `RealValue.compareTo(URealValue)` falls through to `toString().compareTo(...)`
while `URealValue.compareTo(RealValue)` compares numerically. **That asymmetry is not one of the 33
rows and the port cannot fix it** — `RealValue` is edited only to add `valueOf` (E26). Declared
residual (§7.2 item 1).

### 3.1 The per-row table S8 classified from

**Verdict vocabulary:** `0` = no corpus entry's expected output depends on the defect, with a reason.
`n` = exactly n entries do, enumerated. `UNVERIFIABLE` = could not be determined without executing,
with the experiment that settled it.

| id | Corpus entries whose expectation depends on the defect | Basis / what settled it |
|---|---|---|
| CF-7 | **`0` direct, all 1427 indirect** | The two S8 sites (`:90`, `:94`) are the assertions that adjudicate every entry. No expectation changes; the **adjudication** of all 1427 does. Settled by the mutation control in §1 C4 |
| CF-8 | **`0` expectations, `1427` outcomes** | Under Maven the harness either errored or passed vacuously before the fix. The fix does not change any expectation; it changes whether any is checked at all |
| CF-9 | **`0`** | The only non-ASCII bytes are on `# Creación` comment lines, skipped (`line.startsWith("#")`). 3 of 4 fixtures were already UTF-8 |
| CF-5 | **`0` directly; UNVERIFIABLE indirectly** | The 4 `.in` files are read by one test class, so no entry's expectation depends on suite order. `Options.explicitVariableDeclarations` leaks *out* of that class into whatever runs after — settled by running under `-Dsurefire.runOrder=alphabetical` and `reversealphabetical` and diffing |
| M-6 | **`0`** | No corpus entry expects the string `A value kind of … expected` |
| F-2 | **`0`** | No integer part in the corpus reaches 9 digits; the saturation threshold is `9.223372036854776e8` |
| F-3 | **`0`** | Fact **F4**: no live corpus value exceeds 10 decimals, so `MathUtil.round(x,10)` is the identity and the fixed `hashCode` returns the same `int` on every corpus value |
| F-4 | **`3`, all safe: `URealExpression.in:979, 982, 985`** | Route through `Op_identical` → `URealValue.equals(IntegerValue)`. All three expect `-> true : Boolean`, and the fix is a **widening** — rounding can turn `false` into `true`, never the reverse |
| F-10 | **`0`** | Fact **F3**: no collection literal contains a `UInteger`, and per §0.2/1 a `hashCode` change cannot alter set contents anyway |
| M-8 | **`0`** | The three `= true`/`= false` entries route through `Op_equal`, rewritten to return a `UBoolean` degree of equality (E3); they never reach `UBooleanValue.equals(Object)` |
| M-9 | **`0`** | Fact **F3** |
| M-10 | **UNVERIFIABLE; `1` candidate: `UIntegerExpression.in:1304`** | `( UInteger(2, 3) / 1 ).equals( UInteger(2, 3).toUReal() )` → `-> true : Boolean`. Whether it exercises M-10 depends on the static type of `UInteger / Integer` under E1's widening rules. The 13 other UInteger/UReal `.equals` entries all have a `URealValue` on **both** sides after widening. **Settles by:** compile `UInteger(2,3) / 1` in the ported build and print `expr.type()`; if it is `UInteger`, run entry 1304 before and after the M-10 fix |
| M-11 | **`0`** | Fact **F2** |
| M-12 | **`0`** | Fact **F2** |
| M-18 | **`0`** | Fact **F2** |
| M-21 | **`0`** | `allSupertypes()` is not reachable from any OCL expression in the corpus; only from `TT/uml/ocl/type/TypeTest.java` |
| M-22 | **`0`** | Constructor visibility is not observable from OCL |
| M-26 | **`0`** | `ExpDefSBoolean` is unreachable from every grammar (`specification.md` §8.1 Ground 1b) |
| M-27 | **`0`** | same |
| M-28 | **`0` as recommended (no change)** | Were the round-trip rewritten, the at-risk entries are three `UBoolean(…)` creation entries (`UBooleanExpression.in:19,23,27`), whose expectations depend on the `catch (RuntimeException)` path — the argument for not rewriting |
| M-29 | **`0`** | The only two corpus entries trying to pass a non-Boolean value are **compile errors** (`-> Value must be Boolean`) and never reach `eval` |
| M-30 | **`0`** | No `UString(...)` literal anywhere in the corpus |
| M-31 | **`0` as recommended (no change); `2` at risk if the check moves** | `URealExpression.in:62,65` are produced by `ASTURealLiteral.gen` as a `SemanticException`; moving the check into the constructor makes it an `ExpInvalidException` with a possibly different stderr prefix — a second reason not to move it |
| M-32 | **UNVERIFIABLE; `2` candidates, plus a `let` interaction** | The same two entries are the only corpus entries observing `ASTURealLiteral.gen` at all. The at-risk population is any entry where a `UReal(...)` literal's operand registers into `ctx` — a `grep` for that pattern found none, but the grep is not a proof |
| M-33 | **`0`** | Fact **F2** |
| M-37 | **`0`** | The 9 entries using `.value()`/`.toInteger()` already expect `: Integer`/`: OclVoid`, because `toStringWithType` prints `getRuntimeType()`. No entry nests such a call inside another operation either |
| M-38 | **`0`** | `UBooleanExpression.in:143` dispatches to `Op_boolean_or`; no reachable witness exists in the corpus. Full argument in §1 C3 |
| M-43 | **`0`** | Value-test rows; the corpus is not involved |
| M-44 | **`0`** | The 40 sites are in the four `*ExpOpsTest`/`ExpQueryUncertaintyTest` files, not in the `.in` corpus |
| M-45 | **`0` expectations; UNVERIFIABLE outcomes for whatever runs after** | Same experiment as CF-5. Under `-Pupstream-oracle` the reactor runs 45 classes / 315 methods instead of 3, so the population that can see the leaked `false` grows by an order of magnitude at exactly the moment B3 lands |
| M-48b | **`0` as recommended** | The `toString()` appears only in a **failure** message, invisible while all 1427 pass. Adding an explicit `toString()` also closes gap G3 |
| M-49b | **`5`, the one genuinely dangerous row: `UBooleanExpression.in:8,11,14`, `URealExpression.in:62,65`** | All 5 error-path entries pass through `split("\n(\r\n)")`, which on Linux never matches, so the whole captured stderr becomes the "line" compared. Those 5 expectations pass today only because the concatenation happens to equal the expected string. Settled by capturing the fixture strings before the fix and asserting the selected line equals them after |
| M-51 | **`0`** | Only reached when a fixture file cannot be opened |

**Summary for S8.** Of 33 rows, **24 had corpus impact provably zero**, **1 was zero-with-widening and
safe** (F-4, 3 entries, all expecting `true`), **4 were UNVERIFIABLE before execution** (CF-5, M-10,
M-32, M-45), **1 changed 5 expectations' adjudication path** (M-49b), and **3 changed the harness's
own contract rather than any expectation** (CF-7, CF-8, M-48b). **No fix in this plan changed a single
one of the 1427 expected strings** — the S8 acceptance criterion (1427 entries, 1427 passes, zero
expectation edits) held, and is now confirmed by the passing suite rather than by this prediction.

---

## 4. The harness consequence, and the mechanism

### 4.1 Which rows the differential sweep can even see

`DifferentialSweep` drives `HistoricalOracle` against a ported `Candidate` through `UOp` — a
*method on a value class* (`UOp.java:10-15`). It therefore sees exactly the rows whose defect lives
in a `Value` method, and is blind to the rest.

| Visible to the sweep (`DIFFER` expected) | Invisible to the sweep |
|---|---|
| **F-3** (`URealValue.hashCode`), **F-4** (`URealValue.equals`), **F-10** (`UIntegerValue.hashCode`), **M-8** (`UBooleanValue.equals`), **M-9** (`UIntegerValue.compareTo`), **M-10** (`UIntegerValue.equals` / `URealValue.equals`), **M-11** (`UStringValue.equals`), **M-12** (`UStringValue.compareTo`), **F-2** (`MathUtil.round`, observed through every `equals`) | **M-18** (`SBooleanValue.compareTo`) — `supports()` returns `false` today, see §6. **M-37, M-38** (`OpGeneric` registries, not `Value` methods — `HistoricalOracle.MARSHALLABLE_RECEIVERS` at `HistoricalOracle.java:134-136` lists only value classes). **M-21, M-22** (type layer — `harness-contract.md` §C3: "the differential harness cannot see the type layer"). **M-26–M-33** (expression/parser layer). **All 6 test-harness rows** (CF-5, CF-7, CF-8, CF-9, M-43, M-44, M-45, M-48b, M-49b, M-51) |

**Correction (`B7CorrectionsTest`, `PortedFidelitySweepTest`):** this table over-states which rows
are visible. `UnwrittenPortInvariantTest`'s reachable-operations census admits a method only when
every parameter is a `Value`, `int`, `double` or `float`; `equals(Object)` takes an `Object`, so
**no `equals` override is in the census at all** — M-11, M-8, M-10 and F-4 are in fact **invisible**
too, and their evidence is the purpose-built tests of §7.3, not this sweep.

**So: of the 33 rows, a handful produce `DIFFER` rows in an S4–S7 sweep, and most do not.** That is
not reassuring — it means most fixes have no automatic signal at all and must be evidenced some other
way (§7.3, and see the correction above for exactly which rows that applies to).

### 4.2 Why the existing mechanisms did not cover this

`AcceptedThrowPairs` adjudicates **throw vs throw** (keyed on both throwable classes and both
messages). `AcceptedDegenerateOperations` adjudicates **a reference that could not have said anything
else** (keyed on the operation and the sole reference value). Neither can express *"both sides
returned a value, they differ, and the difference is the correction we decided to make"*. Under
`harness-contract.md` §4.2 clause 2, that row is a disagreement and the gate refuses it — correctly,
without a third mechanism.

### 4.3 The mechanism — `IntendedDepartures`

Designed here as a new test-scoped class with a new `DiffVerdict` constant
(`INTENDED_DEPARTURE`: not an agreement, but a measurement, excluded from gate clause 2 only), keyed
on both canonical value forms plus a mandatory `ledgerRowId`, `rationale` and `predictedDirection` (so
a contradicted prediction still reads as `DIFFER`), with a bounded `declareBounded(exactCount,
pairsSha256, direction, …)` form for populations too large to enumerate pair-by-pair, and a fourth gate
clause (`unusedDeclarationCount() == 0`) so a pre-registered departure that never fires is also a
failure.

**This design shipped near-verbatim.** It now lives as
`use-core/src/test/java/org/tzi/use/uncertainty/differential/IntendedDepartures.java` — read that
class's own Javadoc for the six rules, the verdict-taxonomy table, and the `declareBounded` digest
form; it states the design more precisely than this section did and is the current source of truth.
Cited from shipped tests as `b7-fix-plan.md` §4.3 (`stage-09.md` §2 discusses its rollout); the
pointer is what those citations should now resolve to.

### 4.4 Where this left the 24 invisible rows

They got no harness signal, so the gate could not help. Their evidence was: the four `.in` files under
S8 (which per §3 showed **zero** expectation changes), the `-Pupstream-oracle` suite, and the
purpose-built tests §7.3 lists. **A stage was not permitted to quote a differential figure as evidence
for a row the sweep cannot see** — the exact failure `harness-contract.md` §5 calls a declared limit.

---

## 5. S10's verdict wording — outcome

This section originally designed a three-part verdict sentence for S10 — **COMPLETE** (every artefact
and operation from `specification.md` §§1-2 present and dispatchable), **FAITHFUL MODULO N ENUMERATED
CORRECTIONS** (agreement except on the pre-registered `IntendedDepartures`, with N stated explicitly,
not left a footnote), and **NON-REGRESSIVE** (both acceptance commands green, no upstream test
touched) — plus a hostile-reviewer checklist: cite the external authority for each correction (not
"the port does it this way"), report agreement against both the corrected *and* uncorrected reference,
and name any corpus entry a correction breaks rather than discovering one at S10.

**The design's substance is now moot as a plan** — S10 has run, both acceptance commands are green
(`harness-contract.md` §0.1 is the current normative figure), and the actual verdict sentence and its
N is recorded where S10 wrote it, not here. What remains load-bearing from this section: every
correction's authority must still be an external one (the vendored `uDataTypes` source, a measured
Java fact, `Comparable`'s contract — never "the port does it this way"), and that discipline is what
§1 and §2 above already show for each of the 33 rows.

---

## 6. B2 — the revised SBoolean plan (FULL PORT)

| id | The user's decision (2026-08-17, binding) | The recommendation that was **not** taken |
|---|---|---|
| **B2** | **FULL PORT** of `SBoolean`, all 39 operations. | **Skeleton** (`SBooleanType` retained for the compile-time dependency, registry omitted). Recorded at `specification.md` §0 B2 and §8.2. |

### 6.1 What full port required that the scope-limited plan did not

The scope-limited plan needed only enough of SBoolean to keep the *boolean family's* type lattice and
`StandardOperationsAny`'s import compiling. Full port added:

| # | New obligation under FULL PORT | Size / evidence |
|---|---|---|
| 1 | `StandardOperationsSBoolean.java` — the whole registry | **1502 lines**, 45 anonymous `OpGeneric` subclasses, **39 OCL operations**. **Zero fork test coverage** — see §6.2 |
| 2 | `SBooleanValue.java` | **476 lines**. `Builder` with exact-`==` singleton selection (F-11), `valueOf(Value)`'s `UBoolean`/`Boolean` coercion, and `compareTo` returning `0` (**M-18**) |
| 3-6 | `SBooleanType.java` (36 ll.), `ExpConstSBoolean.java` (88 ll., the only `ExpConst*` wrapping its whole body in `try{…}catch(Exception)→Undefined`, the shape M-30 adopts for `ExpConstUString`), `ASTSBooleanLiteral.java` (55 ll.), the grammar alternative (`OCLBase.gpart:499-500`, one literal alternative plus one type-name token, copied into all six generated grammars) | — |
| 7 | `RealValue.valueOf(Value)` (E26), back and mandatory under FULL PORT, called from `SBooleanValue:258,271,284,285,290` | — |
| 8 | The subjective-logic operations: `deduceY` (arity 3), eight fusion operators, `discount`, `applyOn`, `min`, `max`, `projectiveDistance`, `conjunctiveCertainty`, `degreeOfConflict`, `uncertaintyMaximized`, `createDogmaticOpinion`, `createVacuousOpinion` | — |
| 9 | `consensusAndCompromiseFusion` — **the practical hazard**, `SBooleanValue.java:433-444`, cost **O(4ⁿ)** in the number of fused opinions. Two hard preconditions, both `IllegalArgumentException`: all base rates equal (constrains the receiver too, since it is prepended), and fused size ≥ 2 | `20-ops-SBoolean.md` §7 |
| 10 | **THE HARD PREREQUISITE — `SBooleanValue` marshalling in the harness.** Without it, all 39 operations are `UNSUPPORTED` and the port has **no evidence of any kind** for them | §6.2 |

### 6.2 Why the harness marshalling was a prerequisite, not an extra

`supports(SBooleanValue.*)` returned `false` today, deliberately and by name (`HistoricalOracle.java`'s
`MARSHALLABLE_RECEIVERS` javadoc: "`SBooleanValue` is deliberately absent — the harness has no
`SBoolean` marshalling... so all 39 of its operations must report `UNSUPPORTED`"), pinned by
`DifferentialHarnessRegressionTest.java:149-152`. **Under the skeleton plan that was correct and
costless. Under FULL PORT it was the difference between 39 operations with evidence and 39 without**,
because **the fork has zero tests for any of the 39** (`grep -rIl "SBoolean" . | grep -v src/main` →
one file, `TypeTest.java`, exercising only the type lattice). **The differential harness was therefore
the only available evidence for all 39 operations.**

### 6.3 What the marshalling work was, concretely

Four edits, and a fifth item that decides whether any of it counts:

1. **`UValue.Kind.SBOOLEAN`** — a ninth constant, the only one with arity 4 (belief, disbelief,
   uncertainty, baseRate). **Do not reuse the printed form as the canonical form** —
   `SBooleanValue.toString` rounds to 3 decimals, which would silently manufacture `AGREE` between two
   genuinely different opinions.
2. **`toHistorical` branch**, using the public `Builder` (as the `UBOOLEAN` branch already uses
   `UBooleanValue.valueOf` rather than `setAccessible`) — so `TRUE`/`FALSE` identity is reachable and
   everything else is a fresh instance, which is the fork's own reachable domain.
3. **`fromHistorical` branch**, reading the four masses reflectively.
4. **`MARSHALLABLE_RECEIVERS` += `"SBooleanValue"`**, and the two regression assertions at
   `DifferentialHarnessRegressionTest.java:149-152` rewritten (not deleted) to assert `supports() ==
   true`.
5. **An `sBooleanBoundaries()` corpus in `InputGenerator`.** This is defect D-19, already measured on
   this harness: `BooleanValue`/`StringValue` were marshallable from the start but never given
   boundaries, so their 52 operations reported `supports()==true` and then failed the per-row receiver
   check on all of them — **52 operations at 100% `HARNESS_ERROR` and zero measurements.**
   **Marshalling without a corpus converts 39 silent `UNSUPPORTED` rows into 39 silent `HARNESS_ERROR`
   rows.** The boundary list needed, at minimum: the two singletons (F-11's exact-`==` selection), a
   vacuous opinion, a dogmatic non-singleton, `u == 0` (the divisor guard), equal base rates across ≥3
   opinions (or every `consensusAndCompromiseFusion` row is an exception), unequal base rates (to drive
   that exception deliberately), and fusion collections of size 0 and 1. **Cap the fusion collection at
   4 opinions** (4⁴ = 256 permutations) given the `O(4ⁿ)` cost.

### 6.4 Where S9 sat in the work graph

**S9 (SBoolean) could not be last** — its dependency on the harness ran backwards through the
schedule, so the harness widening landed as its own stage (**S3.5**) between S3 and S4, not inside S9:
(1) it is a change to the measuring instrument, which needed to be proved against the historical jar
on both sides and frozen before the one stage that depends on it for *all* its evidence; (2) the
regression pins needed rewriting, a review of S1's own test suite, not squeezed into the last port
stage; (3) `sBooleanBoundaries()` is a design task with a measured precedent (D-19) and a complexity
trap (O(4ⁿ)) that schedule pressure turns into "three values and a TODO"; (4) it de-risks E26
(`RealValue.valueOf`, an upstream edit) before four type stages build on top of it. **S9 itself needed
39 operations, zero fork tests, a `.in`-format corpus that did not exist, and a per-operation departure
register for M-18 — budgeted as the largest of the seven port stages, not the smallest.**

### 6.5 Should "full port" be read as including `ExpDefSBoolean`? — B10 outcome

**Recommendation: NO. Read "full port of SBoolean, all 39 operations" as the 39 operations plus the
five classes in §6.1 #2–#6, and NOT as `ExpDefSBoolean`.**

The argument, from `specification.md` §8.1: **it is not one of the 39** (`ExpDefSBoolean` is an
*expression class*, and the count of 39 is `StandardOperationsSBoolean`'s registry). **It is
unreachable on two independent grounds**: its only inbound edge, `ASTSBooleanDefExpression`, has no
grammar, no generated parser, no test reaching it; and the surface syntax it implements
(`SBoolean(<one expr>)`) exists in **no** grammar — every grammar's only `SBoolean(...)` production is
the four-argument literal. **It cannot work even if reached** — three defects, two on this plan's own
list: the inverted guard (M-27), the missing `ctx.exit` (M-26), and an `eval` that can return Java
`null`. **Dropping it saves a method in every `ExpressionVisitor` implementor** (7 rather than 8).

Under bug-for-bug, "port it with the three defects documented" would have been coherent. **Under B7 it
is not**: B7 obliges fixing M-26 and M-27, so porting `ExpDefSBoolean` would mean shipping a class that
nothing can reach, no grammar can invoke, and that behaves differently from the fork — a deliberate
divergence on dead code, pre-registered in `IntendedDepartures` for an operation the harness cannot
drive. Pure cost, no observable, no evidence, no reachable behaviour.

> **Recommendation to the human, as stated at the time: DROP `ExpDefSBoolean`, `ASTSBooleanDefExpression`
> and the `visitDefSBoolean` hook.** B7 strengthens the case rather than weakening it. If read instead
> as including it: port it, apply the M-26/M-27 fixes, and register both as intended departures with
> the rationale "unreachable dead code, corrected for B7 consistency; no observable exists" — N in the
> S10 sentence would then become 11 instead of the number actually used.
>
> **Outcome: the human closed B10 as DROP.** `ExpDefSBoolean`, `ASTSBooleanDefExpression` and the
> `visitDefSBoolean` hook were not ported; the port's `ExpressionVisitor` gained 7 methods, not 8.

---

## 7. Sequencing, residuals, and the tests B7 obliged

### 7.1 Fixes that had to land together, or the fix would be a lie

| Bundle | Rows | Why they could not be separated |
|---|---|---|
| **A** | **M-9 + a new `UIntegerValue` arm in `URealValue.compareTo`** | `URealValue.compareTo` has no `UIntegerValue` arm, so a `UIntegerValue` argument falls through to `0` today. Negating `0` is `0` — M-9 alone changes nothing while appearing to fix a sign error. Land both, or the ledger row is discharged falsely |
| **B** | **M-10 + F-3** | M-10 creates new `equals` pairs across `UIntegerValue`/`URealValue`. If `hashCode` is not contract-correct at the same moment, the new equal pairs hash apart and `SetValue` membership becomes order-of-insertion dependent — a worse defect than the one fixed |
| **C** | **CF-5 + M-45** | Deleting the `AllTests` suites removes the pinned order that makes the `Options` leak survivable. Fixing M-45 removes the leak. Either alone is a regression; both together are neutral |
| **D** | **F-1 (preserving) then F-2 (changing), in two commits** | `MathUtil.round` must first be shown byte-identical, *then* de-saturated. One commit conflates "the helper exists" with "the helper is different" |
| **E** | **M-11 alone is safe; M-11 + M-12 change different things** | `equals` (M-11) and `compareTo` (M-12) are independent here because `UStringValue.hashCode` already delegates correctly. Noted so no one bundles them for tidiness and loses the ability to bisect |

### 7.2 Declared residuals — defects B7 does **not** fix, and why

Each had to appear in S10, because a reviewer who finds one unlisted assumes the list is incomplete
everywhere. **These are rejected-alternative records, not reconstructable from the shipped code alone,
so they are kept in full:**

1. **`RealValue.compareTo(URealValue)` falls through to `toString()` comparison** while
   `URealValue.compareTo(RealValue)` compares numerically (`RealValue.java:78-84` vs
   `URealValue.java:100-101`). This is what makes `UCollectionOperations.in:161` print
   non-ascending. **Not fixable within scope:** `RealValue` is edited only to add `valueOf` (E26).
2. **`StringValue.equals` has no `UStringValue` arm**, so M-11's fix leaves `=` asymmetric across
   the String/UString boundary. `specification.md` §1.8 lists `StringValue` as explicitly unchanged.
3. **`IntegerValue.hashCode` / `RealValue.hashCode` are not aligned with the uncertain classes**, so
   F-10's fix delivers `UReal(1,0) ≡ UInteger(1,0)` but not `1 ≡ UInteger(1,0)`, contradicting the
   fork's own comment at `UIntegerValue.java:58-60`.
4. **`Collections.sort` over a mixed collection with an asymmetric comparator is undefined.** The
   corpus sets are ≤ 5 elements, so Java's TimSort uses binary insertion sort and performs **no**
   contract check; at ≥ 32 elements it throws. **This is a latent crash the corpus cannot reach and
   the port does not fix.** M-18's fix (§2) avoids *adding* a non-transitive comparator; it does not
   repair the pre-existing asymmetries in 1–3.
5. **The twelve F-rows that were carried across byte-identically** (F-1, F-5, F-6, F-7, F-8, F-9,
   F-11, F-12, F-13, F-14, F-15, F-16). B7 does not license touching these; `specification.md`
   §7.1/§7.3 explains each.

### 7.3 The tests B7 obliged, per row, for the rows with no harness signal

B7 says "fix, documenting each" — a fix with no test is a claim. What each row's evidence needed to be,
condensed from the full per-row list: F-2 needed a magnitude test above `9.223372036854776e8` (no
existing test reaches it); M-8 the `UBooleanValue.FALSE`/`BooleanValue.FALSE` round trip; M-9+bundle A
the antisymmetry property over the UReal×UInteger cross product; M-10 the cross-type `equals` in both
directions plus a `SetValue` cardinality assertion; M-11 reflexivity, symmetry, a `HashSet` round trip,
and the new cross-type case; M-12 the `UString` vs `String` sort position; M-18 a total-order property
test over ≥32 elements (the length that makes TimSort check); M-21/M-22 an extended `TypeTest` isolate
with directly-constructed types; M-29/M-30 new tests, since neither had a corpus example or a reachable
corpus entry; M-32 a `Context`-mutation counter over all 1427 entries; M-37 a static-type assertion on
`x.value()`; M-38 the witness in §1 C3; CF-5/M-45 two full runs under opposite `surefire.runOrder`s,
diffed; CF-7 the 12-site mutation control of §1 C4; CF-8 an assertion that the run reports 1427
entries; M-49b the pre-fix fixture capture described in §3.1's row. The rest (M-43, M-44, M-48b, M-51,
CF-9, M-6, M-26, M-27, M-28, M-31, M-33) were covered by the existing suite once migrated, or are
"do not change" rows whose evidence is the written justification above.

### 7.4 UNVERIFIABLE register for this document, at the time it was written

1. Whether the historical suite ever passed — no Ant, no Maven, no execution; inherited from
   `16-modernization-ledger.md:281-283`, and it applied to every "this entry passes today" statement.
2. `uDataTypes` internal numerics (`UReal.divideBy`, `UBoolean.and/or/implies`, `SBoolean.*Fusion`)
   are opaque jar bytecode; the vendored 2023 source cited for C1's fix and M-18's non-delegation
   argument may differ from the 2021 jar the fork links — flagged for a `javap -c` re-verification.
3. `ASTExpression.gen(Context)` purity (M-32) — not established, inherited.
4. The static type of `UInteger / Integer` under E1's widening, deciding whether
   `UIntegerExpression.in:1304` exercises M-10.
5. What `sos.toString()` actually contained for the 5 error-path entries (M-49b) — the single most
   consequential unknown in §3 at the time.
6. The S3–S10 stage mapping, taken from the task brief rather than the record.
7. B3's measured numbers differed between two records (45/315 vs 50/497) — reconciled per §9.
8. Not consulted: `origin/main`, any earlier port attempt, the GUI and plugin layers' `catch` sites.

### 7.5 Corrections this document made to the record

| Target | Correction |
|---|---|
| `16-modernization-ledger.md:63` (F-3), `:74` (F-10); `specification.md` §7.2 F-3/F-10 | "changes `Set{…}` iteration order" — **wrong mechanism.** Print order is `Collections.sort` on `compareTo`. Hash changes affect membership only, and per §3 they cannot even do that on this corpus |
| `16-modernization-ledger.md:103`; `specification.md` §7.2 M-38 | "`Undefined or Undefined` throws `NullPointerException` today" — true of the **method**, false of the **OCL expression**: `Op_boolean_or` shadows it and the corpus expects and gets `Undefined` |
| `specification.md` §7.2 M-9 | "Masked only because `URealValue.compareTo(UIntegerValue)` itself returns `0`" — correct, and the consequence was not drawn: **M-9's fix alone is a no-op.** See §7.1 bundle A |
| `specification.md` §7.2 M-18 | Cites `SBoolean.compareTo` as though it were the fix — it **cannot** be: non-transitive, rejected by Java 21's TimSort. See §2 M-18 |
| `specification.md` §7.2 M-11 | The consequence column omitted the **new cross-type `true`** the fix creates. See §1 C1 |
| `specification.md` §7.2 M-37 | "changes type-checking of every expression that consumes `x.value()`" is right; missing that **the printed suffix does not change** |

---

## 8. Reproduction commands

The 175-line transcript that once stood here (every `sed`/`grep`/`javap` command behind every quote and
count in this document) is superseded by the passing test suite it argued for, and has been removed.
The facts it established are stated inline throughout §§0-7, each with its file:line source.

---

## 9. What this document asked of the human — resolved

Four items were asked: confirm the S3–S10 stage mapping (§2 preamble); approve stage S3.5, harness
widening, between S3 and S4 (§6.4); close B10 (§6.5 — closed as **DROP**); and reconcile B3's two
measured figures (45 classes/315 methods vs 50/497).

**All four are resolved.** The B3 reconciliation: the current, build-asserted figures are **51
classes / 498 distinct methods** under the upstream-oracle profile and **11 / 211** by default, of
which 465 and 199 can fail — recorded in `upstream-oracle-profile.md` §4.3, with `harness-contract.md`
§0.1 as the one normative figure to quote.
