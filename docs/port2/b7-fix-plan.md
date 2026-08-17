# B7 — the fix plan for the 33 behaviour-changing ledger rows

**Role: Spec. This document triages and designs. It implements nothing.**
Written 2026-08-17 on branch `port-uncertainty-2`. No Maven was run
(`pgrep -f '[m]vn -B'` matched **PID 494057** at the time of writing — another session owns the
build; every number below is either a source reading, a `grep`/`sed` output pasted verbatim, or is
marked **UNVERIFIABLE**). Ground rule 2 re-checked at the end of the session:

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
(no output)
```

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

Each fix below is spelled out to the statement. Each is verified against the historical source, pasted
here from `sed`, at
`/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty` (read-only,
per ground rule 3).

### C1 — `UStringValue.equals` is constant `false` (ledger **M-11**)

**Site:** `src/main/org/tzi/use/uml/ocl/value/UStringValue.java:79-91`. Verbatim:

```java
    @Override
    public boolean equals(Object obj) {
        boolean eq = false;
        if (obj instanceof Value) {
            UStringValue ustring = valueOf((Value) obj);
            if (ustring != null)
                eq = wrapper.getString().equals(ustring.wrapper) &&
                        wrapper.getsConf() == wrapper.getsConf();
        }
        return eq;
    }
```

**Two independent defects in one expression.** `wrapper.getString()` returns `java.lang.String`
(`javap -cp lib/atenearesearchgroup.uncertainty.jar uDataTypes.UString` →
`public java.lang.String getString();`) and `ustring.wrapper` is a `uDataTypes.UString`, so
`String.equals(Object)` is `false` for every argument. And the second conjunct compares the
receiver's confidence **to itself** — the argument is never read. Net: `a.equals(a)` is `false`,
reflexivity is broken, and no `UStringValue` can be found in any `HashSet`, `HashMap` or `SetValue`.

**THE FIX — replace both conjuncts with one delegation:**

```java
                eq = wrapper.equals(ustring.wrapper);
```

**Why that is the right delegate and not a guess.** The vendored library source
`.git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/UString.java:111-119`:

```java
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UString uString = (UString) o;
        if (Double.compare(uString.getsConf(), getsConf()) != 0) return false;
        return getString().equals(uString.getString());
    }
```

It already compares **both** the string and the confidence, and it does so with `Double.compare`, so
`NaN` confidences compare equal to each other — matching `hashCode`
(`UString.java:121-129`, `doubleToLongBits`). `UStringValue.hashCode` already delegates
(`UStringValue.java:73-76 return wrapper.hashCode();`), so **the fix repairs the equals/hashCode
contract at the same time, with no change to `hashCode`.** That is the single cheapest correction in
the whole ledger.

**What observable output changes.** (a) `UStringValue` becomes usable in any hash container — a
`Set{UString(...)}` of duplicates collapses where it previously did not. (b) The OCL operation
`.equals` (`Op_identical`, `StandardOperationsAny.java:163-179`, which calls Java
`args[0].equals(args[1])`) starts answering `true`. (c) **New cross-type `true`:**
`valueOf(Value)` lifts a `StringValue` to `new UStringValue(s, 1)` (`UStringValue.java:27-36`), so
`UString('x', 1.0).equals('x')` flips `false → true`. `StringValue.equals` has no `UStringValue`
arm, so the relation stays asymmetric in the *other* direction — the fix does **not** make it
symmetric, and §1.8 of `specification.md` forbids editing `StringValue`. **Record that asymmetry as a
declared residual, not as an oversight.**

**Owner:** S7 (UString). **Corpus impact: zero** — see §3, the corpus contains no `UString` token at
all.

### C2 — `UIntegerValue.hashCode` collapses to 0 whenever uncertainty is 0 (ledger **F-10**)

**Site:** `src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java:57-64`. Verbatim:

```java
    @Override
    public int hashCode() {
        //return uInteger.hashCode();
        // for collections purposes, the follow equality must hold :
        // 1 = 1.0 = UReal(1, 0) = UInteger(1, 0).
        int hash = Double.hashCode(value());
        hash *= 7 * Double.hashCode(uncertainty());
        return hash;
    }
```

`Double.hashCode(0.0) == 0` (measured, `16-modernization-ledger.md:233`), and the operator is `*=`,
not `+`. So **every** `UInteger(n, 0)` hashes to `0`. The sibling four lines away
(`URealValue.java:56-64`) has the additive, zero-guarded form the comment describes:

```java
        int hash = Double.hashCode(value());
        if (uncertainty() != 0)
            hash = hash * 7 + Double.hashCode(uncertainty());
        return hash;
```

**THE FIX — replace the two arithmetic lines of `UIntegerValue.hashCode` with exactly the
`URealValue` body above**, unchanged, so the two are textually identical and the bridge the comment
asserts (`UReal(1,0)` and `UInteger(1,0)` in one bucket) actually holds.

**Note what the fix does NOT achieve.** The comment claims `1 = 1.0 = UReal(1,0) = UInteger(1,0)`.
Even after the fix, `IntegerValue.hashCode` and `RealValue.hashCode` are upstream and unedited
(`specification.md` §1.8 lists `IntegerValue` as explicitly not changed), so `IntegerValue.valueOf(1)`
and `UIntegerValue(1,0)` still land in different buckets. **The fix aligns the two uncertain classes
with each other and no further. State that limit in the class javadoc; do not silently imply the
comment is now true.**

**What observable output changes.** Bucket layout inside `HashSet<Value>`, hence *membership* of a
`SetValue`, hence its **cardinality** — but **not** its print order, which comes from
`Collections.sort` (see §0.2 item 1). Membership changes only where two elements are `equals` and
were previously hashed apart; after the fix `UInteger(n,0)` and `UInteger(m,0)` for `n != m` stop
colliding, which is a *performance* change only, because `HashSet` still consults `equals` inside a
bucket. **Formally: this fix cannot change the contents of any set.** It changes only which bucket an
element occupies. It is nevertheless behaviour-changing in the sense the ledger means — `hashCode()`
is a public observable — and it is the input that would change if any code ever iterated a raw
`HashSet` without sorting.

**Owner:** S5 (UInteger). **Corpus impact: zero** — argued in §3.

### C3 — `Op_uBoolean_or` NPEs on `Undefined or Undefined` (ledger **M-38**)

**Site:** `src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java:470-478`
(class opens at `:427`). Verbatim, with absolute line numbers from `awk`:

```
470	        else {
471	            if (!ctx.isEnableEvalTree())
472	                v2 = args[1].eval(ctx);
473
474	            ub2 = UBooleanValue.valueOf(v2);
475
476	            if (ub2.probability() == 1)
477	                result = ub2;
478	        }
```

`UBooleanValue.valueOf(Value)` returns `null` unless the argument `isUBoolean()` or `isBoolean()`;
`UndefinedValue` is neither. Control reaches `:470` only when `v1` is undefined. If `v2` is also
undefined, `:476` dereferences `null`. Both guarded siblings are three and eighty lines away:
`Op_uBoolean_and` at `:399-403` (`if (ub2.probability() == 0)` guarded by
`if (v2.isDefined())` at `:412`) and `Op_uBoolean_implies` at `:552`.

**THE FIX — one guard, matching `and`'s shape at `:411-414` exactly:**

```java
        else {
            if (!ctx.isEnableEvalTree())
                v2 = args[1].eval(ctx);

            if (v2.isDefined()) {
                ub2 = UBooleanValue.valueOf(v2);

                if (ub2.probability() == 1)
                    result = ub2;
            }
        }
```

Use `v2.isDefined()` (as `and` does at `:412`) rather than `ub2 != null` (as the *then*-branch of
`or` does at `:466`). Rationale: it is the guard the sibling already uses on the identical control
path, so the two operations become structurally identical rather than merely both non-crashing, and
a reviewer diffing `and` against `or` sees zero difference in the undefined arm. `result` is already
initialised to `UndefinedValue.instance` at `:449`, so the guard needs no else.

**What observable output changes.** `NullPointerException` → `Undefined`. Nothing else: for every
input on which the fork did *not* throw, the new guard is satisfied and the body is byte-identical.

**Reachability, corrected against the record.** `specification.md` §7.2 M-38 and
`16-modernization-ledger.md:103` both say "so `Undefined or Undefined` throws
`NullPointerException` today". That sentence is true of the *method* and false of the *OCL
expression*, and the corpus proves it:

```
$ sed -n '143,144p' src/test/org/tzi/use/parser/uncertainty/UBooleanExpression.in
Undefined or Undefined
-> Undefined : OclVoid
```

`Op_boolean_or` is registered at `OpGeneric.java:90` (`StandardOperationsBoolean`), the uncertainty
registries at `:93-98`; `Op_boolean_or.matches` requires only
`params[i].isKindOfBoolean(INCLUDE_VOID)` (`StandardOperationsBoolean.java:42-46`) and
`VoidType.isKindOfBoolean` returns `h == INCLUDE_VOID` (`VoidType.java:62-65`); `ExpStdOp.create`
returns the **first** match (`ExpStdOp.java:129-135`). So `Undefined or Undefined` is
`Op_boolean_or`, and the corpus expectation is met without `Op_uBoolean_or` ever running.

The NPE **is** reachable, but only where at least one static operand type is `UBoolean` (so
`Op_boolean_or` fails to match) while both runtime values are `Undefined`. That is constructible,
because `ExpConstUBoolean` is typed `TypeFactory.mkUBoolean()` at construction
(`ExpConstUBoolean.java:15`) yet returns `UndefinedValue.instance` at `:44-45` and `:48-51`.
Minimal witness, and it is **not** in the corpus:

```
UBoolean(true, 3 - 5) or UBoolean(true, 3 - 5)
```

```
$ grep -nE 'UBoolean\([^)]*(/ *0|- *5|\* *3)[^)]*\).*(or|and|implies)|(or|and|implies).*UBoolean\([^)]*(/ *0|- *5|\* *3)' *.in
(no output; exit 1)
```

**Owner:** S6 (UBoolean). **Corpus impact: zero.** **S6 must add that witness as a new test** — the
fix is otherwise unobserved by anything in the historical oracle, which is precisely why the defect
survived.

### C4 — the 12 `assertEquals` sites that rebind silently under Jupiter (ledger **CF-7**)

**Sites (12, enumerated):** `src/test/org/tzi/use/uml/ocl/expr/UIntegerExpOpsTest.java:29,36,43,50,57,64,71,82,89,96`
and `src/test/org/tzi/use/parser/uncertainty/USECompilerUncertaintyTest.java:90,94`. Verbatim
(`sed -n '29p;36p;…'`):

```java
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(-5, 0.0)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(-5, 0.5)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(-5, -0.5)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(-5, 2)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(-5, -5)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(3, 39)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(0, 0)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(null, null)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(null, 0.34)", eUInteger.toString());
        assertEquals(eUInteger.toString() + ".toString()", "UInteger(5, null)", eUInteger.toString());
```

```java
                        assertEquals("evaluate : " + expTest, expTest.expected, errMessage);
                        assertEquals("evaluate : " + expTest.expression, expTest.expected, result.toStringWithType());
```

**Why these 12 and no others.** CF-6's 943 sites are safe *because they break the build*: Jupiter has
no `(String, Object, Object)` overload, so a `(String, non-String, non-String)` triple is a hard
compile error and javac enumerates them for you. These 12 have **three `String` arguments**, so they
bind to `assertEquals(Object expected, Object actual, String message)` and compile clean. The
message becomes `expected`, `expected` becomes `actual`, and `actual` becomes the message. **No
warning is emitted.** Two of the three arguments in each of the ten `UIntegerExpOpsTest` sites are
*the same expression* (`eUInteger.toString()`), so nine of the ten would then compare
`eUInteger.toString() + ".toString()"` against `"UInteger(-5, 0.0)"` and **fail loudly** — but the
two `USECompilerUncertaintyTest` sites would compare the *message* against the *expected*, and there
the failure mode is a wrong message on a real failure, i.e. silent.

**THE FIX — reorder to `(expected, actual, message)`, by hand, one site at a time, each verified
individually:**

```java
        assertEquals("UInteger(-5, 0.0)", eUInteger.toString(), eUInteger.toString() + ".toString()");
```

```java
                        assertEquals(expTest.expected, errMessage, "evaluate : " + expTest);
                        assertEquals(expTest.expected, result.toStringWithType(),
                                     "evaluate : " + expTest.expression);
```

**Three mandatory procedural controls.** They exist because a compiler cannot help here:

1. **Do not batch-edit.** A regex over `assertEquals(a, b, c)` cannot distinguish these from the 943.
   Edit the 12 by line number from the list above.
2. **Prove the migration by counting first.** Before the edit, run the ledger's own detector and
   confirm it still finds exactly 10 + 2:
   ```sh
   grep -nE 'assertEquals\([^;]*,\s*"[^"]*"\s*,\s*[A-Za-z_][A-Za-z0-9_.]*\.toString\(\)\s*\)' \
     src/test/org/tzi/use/uml/ocl/expr/UIntegerExpOpsTest.java | wc -l   # expect 10
   ```
   (`16-modernization-ledger.md:208` records this command and its answer.)
3. **Prove it after the edit by mutation.** For each of the 12, temporarily corrupt the *expected*
   string by one character and confirm the test goes red, then revert. A site that stays green after
   corrupting `expected` is still mis-bound. This is the only positive evidence available; there is
   no compile-time signal at all.

**Owners:** S5 owns the ten `UIntegerExpOpsTest` sites; S8 owns the two `USECompilerUncertaintyTest`
sites (that file is the corpus harness). **Corpus impact: indirect but real** — the two S8 sites are
the assertions that adjudicate all 1427 corpus entries; if either is mis-bound, S8's entire
classification is unsound. **This is the highest-leverage of the four criticals and the only one
whose failure mode is silent.**

---

## 2. All 33 behaviour-changing rows, in one table

Row set derived mechanically, not by hand:

```sh
$ grep -nE '^\| (CF-|M-|\*\*F-)' docs/port2/spec-parts/16-modernization-ledger.md \
    | grep 'BEHAVIOUR-CHANGING' | wc -l
33
```

**Stage ownership.** The record does not enumerate S3–S10 anywhere
(`grep -rn 'S9\|S10' specification.md` → two hits, neither a definition; `foundation-verdict.md` names
only S1 and "S4–S7"). The assignment below therefore uses the framing in the task brief —
S3 foundation, S4 UReal, S5 UInteger, S6 UBoolean, S7 UString, S8 corpus/`.in` harness, S9 SBoolean,
S10 verdict — and is corroborated where the record does speak: `specification.md:179` names
"the acceptance gate of S3, S4, S5, S6, S7, S10", `foundation-verdict.md:243` scopes B7's work to
"S4–S7", and `harness-contract.md` §7-§8 dates the adapter obligations to S4. **The S3–S10 mapping
itself is proposed, not established; a human should confirm it before S3 starts.** Each row's owner
below is derived from the file it touches, so the mapping can be re-cut without re-triaging.

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
| **M-30** | `F/uml/ocl/expr/ExpConstUString.java:44,48` | unguarded `(StringValue)` cast and unguarded `Double.valueOf(confidence.toString())`, so `ClassCastException`/`NumberFormatException` escape `eval` | wrap the body in `try { … } catch (Exception ex) { result = UndefinedValue.instance; }`, matching `ExpConstSBoolean.java:48-59` | S7 | `ERR` — an escaping exception becomes `Undefined`. **`UString(...)` has no corpus example at all** (`specification.md` §6.5), so this is unobserved by the historical oracle and needs a new test |
| **M-31** | `F/uml/ocl/expr/ExpConstUReal.java:13-17` | no type validation in the constructor, unlike all four siblings; the check lives in `ASTURealLiteral.java:27-31` instead | **DO NOT move it.** Leave the check in `ASTURealLiteral`; add an **unchecked** `assert`-free javadoc precondition on the constructor | S4 | `NONE` as recommended. Moving it is `ERR` and a **compile break**: `ExpConstUReal` is constructed directly with unvalidated `ExpConstReal` arguments at **300+** sites in `FT/…/URealExpOpsTest.java:34,39,44,…`, and a checked `ExpInvalidException` breaks every one. It would also move the two corpus error messages out of `SemanticException` (§3, `URealExpression.in:62,65`) |
| **M-32** | `F/parser/ocl/ASTURealLiteral.java:23-24` and `:34` | `eValue.gen(ctx)` and `eUncertainty.gen(ctx)` are each called **twice**; two distinct `Expression` graphs are built and the **second** is the one installed | hoist both into locals at `:23-24` and pass those locals at `:34` | S4 | `TREE` + possibly `VALUE`. `ASTExpression.gen(Context)` is not documented pure and registers into `ctx` for sub-expressions carrying variable declarations. The fix halves the number of `ctx` mutations and installs the **first** graph rather than the second. **UNVERIFIABLE whether any corpus entry observes this** — see §3 |
| **M-33** | `F/parser/ocl/ASTUStringLiteral.java` (whole file) | no `toString()` override, unlike the other four AST literals | add `public String toString() { return "UString(" + eValue + ", " + eConfidence + ")"; }`, matching `ASTURealLiteral.java:43-46` | S7 | `TEXT` — the text of every `SemanticException` that interpolates this node. **No corpus entry mentions `UString`**, so no recorded expectation moves |
| **M-37** | `F/…/operations/StandardOperationsUInteger.java:13,17` + `Op_uInteger_value:54-64` | registered under both `"value"` and `"toInteger"`; `matches` declares `mkUInteger()` while `eval` returns an `IntegerValue`. The sibling `Op_ureal_value` correctly declares `mkReal()` (`StandardOperationsUReal.java:246-248`) | `matches` → `TypeFactory.mkInteger()` | S5 | `TYPE` only. `ExpStdOp.create` stores `matches`'s `Type` as the expression's **static** type (`ExpStdOp.java:130-133`), so type-checking of any *enclosing* expression changes. The printed suffix does **not**: `Value.toStringWithType` uses `getRuntimeType()` (`value/Value.java:204-208`), and the 9 corpus entries already read `: Integer` |
| **M-43** | `FT/uml/ocl/value/UBooleanValueTest.java:11-17,36-42` | two commented-out `try/fail/catch` blocks marked `// FIXME: When It will be fixed in atenea library` | revive as **`@Disabled`** Jupiter tests carrying the FIXME text as the disabled reason | S6 | `NONE` to any assertion. Two "skipped" entries appear in the surefire report. **Reviving them live makes the suite red** — they record that `valueOf(true,-2)`/`(true,2)` do **not** throw despite the guard at `UBooleanValue.java:46`, because the library ctor clamps first (F-9) |
| **M-44** | 40 sites: `FT/…/URealExpOpsTest.java:875-911`; `UIntegerExpOpsTest.java:106-142,267-313,484-510`; `UBooleanExpOpsTest.java:217-225,1611-1619,1663-1671`; `ExpQueryUncertaintyTest.java:154-206` | JUnit-3 idiom `try { …; fail("X expected"); } catch (X e) {} catch (Exception ex) { fail(…) }` | convert to `assertThrows(X.class, () -> …)` **and, at each site, assert the exception's message** so the subclass-widening is compensated. At `ExpQueryUncertaintyTest.java:174-178` the `try` holds **two** statements — the lambda must wrap **both** | S4/S5/S6 by file; `ExpQueryUncertaintyTest` → S8 | `ERR` — two distinct widenings. (a) `assertThrows` accepts any **subclass**, whereas the historical second `catch (Exception ex) { fail(…) }` narrowed it; `ExpQueryUncertaintyTest.java:179` catches `RuntimeException`, which today swallows even an `NullPointerException` **as a pass**. (b) Wrapping only the second statement silently narrows the assertion |
| **M-45** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:61` | `Options.explicitVariableDeclarations = false;` set once, never restored (`F/config/Options.java:155` declares it `true`) | save in `@BeforeEach`, restore in `@AfterEach` | S8 | `VALUE` on an **unbounded** set of tests — today every test running after this one in the same JVM sees `false`. **This is the coupling that makes CF-5 load-bearing; fixing M-45 is what makes deleting the suites safe. The two must land in one commit** |
| **M-48b** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:26-29` | `ExpressionTest` is a non-static inner class with the default `Object.toString()` | make it `private static class` (M-48a, preserving) and **DO NOT convert it to a `record`** | S8 | `TEXT` if converted — a `record`'s `toString()` is a component listing, and that string is interpolated into the assertion message at `:90`. **Instead add an explicit `toString()` returning the expression text**, which fixes gap G3 (`specification.md` §6.4: a failing error-path entry currently reports an identity hash) without the record's other effects |
| **M-49b** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:88` | `split("\n(\r\n)")` means "LF **followed by** CRLF", which no platform emits, so `errArray.length - 1 == 0` and `errMessage` is the **whole** captured stderr with `\n`/`\r` stripped | `split("\\r?\\n")` | S8 | `TEXT` — changes which line is compared against the `-> …` expectation for the **5** error-path entries. **This is the single riskiest S8 row**: the current degenerate behaviour is what makes those 5 pass today, and a correct split may select a different line. **UNVERIFIABLE without executing — see §3 and §7.3** |
| **M-51** | `FT/parser/uncertainty/USECompilerUncertaintyTest.java:99-101` | `catch (IOException ex) { throw new RuntimeException("Couldn't open file " + name); }` drops the cause | keep `RuntimeException` and the **same message**, passing `ex` as the cause: `new RuntimeException(msg, ex)` | S8 | `NONE` — type and message unchanged; only the stack trace grows. **Do not use `UncheckedIOException`**, which changes both |

**Count check.** 33 rows: CF-5, CF-7, CF-8, CF-9 (4) + M-6, M-8, M-9, M-10, M-11, M-12, M-18, M-21,
M-22, M-26, M-27, M-28, M-29, M-30, M-31, M-32, M-33, M-37, M-38, M-43, M-44, M-45, M-48b, M-49b,
M-51 (25) + F-2, F-3, F-4, F-10 (4) = **33**.

**Note on the shape of this triage.** The user decided "fix", and eight of the 33 rows are fixed by
**deciding not to change the code**: M-6, M-28, M-31, M-43 (fix = revive-as-`@Disabled`, not
revive-live), M-48b, M-51, and M-26/M-27 (moot under B10). That is not evasion of the decision — B7
says "fix the historical defects, documenting each", and for these eight the documented finding is
that the ledger's proposed change is *worse* than the defect, with the mechanism named in the Δoutput
column. **Each one still requires the written justification B7 mandates; none may be left silent.**

---

## 3. Corpus impact, per row

**The corpus.** 1427 entries across four files (`specification.md` §6.4). Facts established by
`grep` for this section, commands and outputs pasted:

```
$ cd .git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty
$ grep -c 'UString\|SBoolean' *.in
URealExpression.in:0
UIntegerExpression.in:0
UBooleanExpression.in:0
UCollectionOperations.in:0

$ grep -cE '(Set|Bag|Sequence|OrderedSet)\{' UBooleanExpression.in UIntegerExpression.in URealExpression.in
UBooleanExpression.in:0
URealExpression.in:0
UIntegerExpression.in:0

$ grep -nE '(Set|Bag|Sequence|OrderedSet)\{[^}]*UInteger' *.in
(no output)

$ grep -n ': Set\|: Bag\|: Sequence\|: OrderedSet' *.in
UCollectionOperations.in:140:-> Set{2.5,UReal(3.0, 0.25),3.2} : Set(UReal)
UCollectionOperations.in:143:-> Set{1,UReal(2.0, 0.5)} : Set(UReal)
UCollectionOperations.in:161:-> Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)} : Set(UReal)
UCollectionOperations.in:164:-> Set{1,UReal(2.0, 0.5)} : Set(UReal)

$ grep -noE '[0-9]+\.[0-9]{11,}' *.in
URealExpression.in:231:1.016465997955662
URealExpression.in:231:1.0606601717798214
URealExpression.in:232:1.4142135623730951
URealExpression.in:232:1.0606601717798212
URealExpression.in:975:5.656854249492381

$ grep -hE '^-> ' *.in | grep -vE ' : [A-Za-z]' | sort | uniq -c
      1 -> Probability must be a Integer or Real
      1 -> Uncertainty must be Integer or Real
      2 -> Value must be Boolean
      1 -> Value must be Integer or Real

$ grep -nE '\.equals *\( *(true|false) *\)' *.in
(no output; exit 1)

$ grep -nE '\.equals *\( *-?[0-9]+(\.[0-9]+)? *\)' *.in
URealExpression.in:976:# ( UReal(3, 4) - UReal(3, 4) ).equals( 0 )
URealExpression.in:979:( UReal(3, 0) - 3.0 ).equals( 0 )
URealExpression.in:982:( UReal(3, 0) - 3 ).equals( 0 )
URealExpression.in:985:( 3.0 - UReal(3, 0) ).equals( 0 )
```

Five derived structural facts S8 depends on:

* **F1.** Collection literals appear in **exactly one** of the four files. All membership-, order- and
  hash-related corpus exposure is confined to `UCollectionOperations.in`'s **44** entries, and only
  **4** of those print a collection.
* **F2.** The corpus contains **no** `UString` and **no** `SBoolean` token. Every UString and SBoolean
  row has corpus impact **zero, exactly**, not "probably zero".
* **F3.** No collection literal contains a `UInteger`.
* **F4.** The five long-decimal hits are all inside commented-out `# FIXME:` blocks
  (`URealExpression.in:230-234`, `:974-978`), so **no live corpus value carries more than 10 decimal
  digits**. Rounding to 10 dp is the identity on every live corpus number.
* **F5.** Exactly **5** entries take the error path (no ` : Type` suffix): `UBooleanExpression.in:8`,
  `:11`, `:14`, `URealExpression.in:62`, `:65`.

**The print-order mechanism, since four rows turn on it.** Both trees print a `SetValue` through
`getSortedElements()`, i.e. `Collections.sort` on `Value.compareTo`:

```
$ sed -n '319,323p' use-core/src/main/java/org/tzi/use/uml/ocl/value/SetValue.java
    public StringBuilder toString(StringBuilder sb) {
        sb.append("Set{");
        StringUtil.fmtSeqBuffered(sb, this.getSortedElements().iterator(), ",");
        return sb.append("}");
    }
$ sed -n '169,173p' use-core/src/main/java/org/tzi/use/uml/ocl/value/CollectionValue.java
    public List<Value> getSortedElements() {
    	List<Value> result = new ArrayList<Value>(collection());
    	Collections.sort(result);
    	return result;
    }
```
(identical in the fork at `SetValue.java:322-326` / `CollectionValue.java:278-282`.)

So: **`hashCode` fixes change membership; `compareTo` fixes change order.** The corpus's own output
confirms it — `UCollectionOperations.in:161` prints
`Set{2.5,UReal(3.0, 0.25),3.2,UReal(2.0, 0.5)}`, which is **not** ascending, and the reason is that
`RealValue.compareTo(URealValue)` falls through to `toString().compareTo(...)`
(`use-core/.../RealValue.java:83-84`) while `URealValue.compareTo(RealValue)` compares numerically
(`F/…/URealValue.java:100-101`). **That asymmetry is not one of the 33 rows and the port cannot fix
it** — `RealValue` is edited only to add `valueOf` (E26) and `specification.md` §1.8 lists
`IntegerValue`/`StringValue` as explicitly unchanged. It is a declared residual (§7.2).

### 3.1 The per-row table S8 classifies from

**Verdict vocabulary:** `0` = no corpus entry's expected output depends on the defect, with a reason.
`n` = exactly n entries do, enumerated. `UNVERIFIABLE` = cannot be determined without executing, with
the experiment that settles it.

| id | Corpus entries whose expectation depends on the defect | Basis / what would settle it |
|---|---|---|
| CF-7 | **`0` direct, all 1427 indirect** | The two S8 sites (`:90`, `:94`) are the assertions that adjudicate every entry. No expectation changes; the **adjudication** of all 1427 does. Settled by the mutation control in §1 C4 |
| CF-8 | **`0` expectations, `1427` outcomes** | Under Maven today the harness either errors at `:63` or passes vacuously. The fix does not change any expectation; it changes whether any is checked at all. Settled the moment `mvn -B verify` runs the migrated test and reports 1427 |
| CF-9 | **`0`** | The only non-ASCII bytes are on `# Creación` comment lines, skipped at `:116` (`line.startsWith("#")`). `file(1)` on the four fixtures reports 3 of 4 as UTF-8 (`16-modernization-ledger.md:46`) |
| CF-5 | **`0` directly; UNVERIFIABLE indirectly** | The 4 `.in` files are read by one test class, so no entry's expectation depends on suite order. But `Options.explicitVariableDeclarations` leaks *out* of that class into whatever runs after. **Settles by:** run `mvn -B verify -Pupstream-oracle` with `-Dsurefire.runOrder=alphabetical` and again with `reversealphabetical` and diff the reports. If both are green, the leak is inert today |
| M-6 | **`0`** | No corpus entry expects the string `A value kind of … expected`; `grep -c 'A value kind' *.in` → 0 for all four |
| F-2 | **`0`** | `grep -oE '[0-9]{9,}' *.in` finds no integer part ≥ 9 digits; the saturation threshold is `9.223372036854776e8` |
| F-3 | **`0`** | Fact **F4**: no live corpus value exceeds 10 decimals, so `MathUtil.round(x,10)` is the identity and the fixed `hashCode` returns the same `int` as the defective one on every corpus value. Additionally: even where it differed, `HashSet` consults `equals` within a bucket, so membership would be unchanged, and order comes from `Collections.sort` |
| F-4 | **`3`, all safe: `URealExpression.in:979, 982, 985`** | Those three route through `Op_identical` → `URealValue.equals(IntegerValue)` (dispatch: `Integer conformsTo UReal`, so `args[0].equals(args[1])` at `StandardOperationsAny.java:170-171`). All three expect `-> true : Boolean`, and the fix is a **widening** — adding rounding can turn `false` into `true`, never the reverse. So all three still pass. `:976` is inside a commented FIXME block |
| F-10 | **`0`** | Fact **F3**: no collection literal contains a `UInteger`, so no `SetValue` ever holds one. And per §0.2/1 a `hashCode` change cannot alter set contents in any case |
| M-8 | **`0`** | `grep -nE '\.equals *\( *(true\|false) *\)' *.in` → no output. The three `= true`/`= false` entries (`UBooleanExpression.in:372,375,378`) route through `Op_equal`, which the fork **rewrote** to return a `UBoolean` degree of equality (E3) — they expect `-> UBoolean(true, 0.79) : UBoolean`, not a Java `equals` result, and never reach `UBooleanValue.equals(Object)` |
| M-9 | **`0`** | Fact **F3** |
| M-10 | **UNVERIFIABLE; `1` candidate: `UIntegerExpression.in:1304`** | `( UInteger(2, 3) / 1 ).equals( UInteger(2, 3).toUReal() )` → `-> true : Boolean`. Whether it exercises M-10 depends on the static type of `UInteger / Integer` under E1's widening rules — if it is `UInteger`, the left side is a `UIntegerValue` and the right a `URealValue`, which is exactly M-10's dead arm, and the entry **passes today only if some other route makes it true**. The 13 other UInteger/UReal `.equals` entries (`:484,487,501,509,512,755,763,766,999,1013,1049,1275,1278`) all have a `URealValue` on **both** sides after widening. **Settles by:** compile `UInteger(2,3) / 1` in the ported build and print `expr.type()`; if it is `UInteger`, run entry 1304 before and after the M-10 fix |
| M-11 | **`0`** | Fact **F2** |
| M-12 | **`0`** | Fact **F2** |
| M-18 | **`0`** | Fact **F2** |
| M-21 | **`0`** | `allSupertypes()` is not reachable from any OCL expression in the corpus; it is exercised only by `TT/uml/ocl/type/TypeTest.java` |
| M-22 | **`0`** | Constructor visibility is not observable from OCL |
| M-26 | **`0`** | `ExpDefSBoolean` is unreachable from every grammar (`specification.md` §8.1 Ground 1b: the only `SBoolean(...)` production is the 4-argument literal at `OCLBase.gpart:499-500`, verified again this session) |
| M-27 | **`0`** | same |
| M-28 | **`0` as recommended (no change)** | Were the round-trip rewritten, the at-risk entries are the three `UBoolean(…)` creation entries at `UBooleanExpression.in:19,23,27` whose expectations (`-> Undefined : OclVoid` / `-> UBoolean(true, 0.2) : UBoolean`) depend on the `catch (RuntimeException)` path and on `Double.valueOf(anIntegerValue.toString())` giving `1.0`. **That is the argument for not rewriting** |
| M-29 | **`0`** | The only two corpus entries that try to pass a non-Boolean value (`UBooleanExpression.in:7,10`) are **compile errors** at `ExpConstUBoolean.java:18` (`-> Value must be Boolean`) and never reach `eval`. A reachable case needs a `Boolean`-typed expression evaluating to `Undefined`; none exists in the corpus |
| M-30 | **`0`** | No `UString(...)` literal anywhere in the corpus (`specification.md` §6.5) |
| M-31 | **`0` as recommended (no change); `2` at risk if the check moves** | `URealExpression.in:62` (`-> Value must be Integer or Real`) and `:65` (`-> Uncertainty must be Integer or Real`) are produced by `ASTURealLiteral.gen` as a **`SemanticException`** (`ASTURealLiteral.java:27-31`, read verbatim this session). Moving the check into `ExpConstUReal`'s constructor makes it an `ExpInvalidException`, and the text the harness captures on stderr may gain a different prefix. **A second reason not to move it** |
| M-32 | **UNVERIFIABLE; `2` candidates, plus a `let` interaction** | The same two entries `URealExpression.in:62,65` are the only corpus entries that observe `ASTURealLiteral.gen` at all, and they observe it *before* the second `gen()` call. The at-risk population is any entry where a `UReal(...)` literal's operand registers into `ctx` — i.e. a `let`-bound or iterator variable inside a `UReal(...)` literal. `grep -n 'UReal([^)]*\b\(let\|acc\|e\|v\)\b' *.in` finds no such entry, but the grep is not a proof. **Settles by:** instrument `Context` with a mutation counter and run all 1427 entries before and after the hoist; the counter must change and the 1427 expectations must not |
| M-33 | **`0`** | Fact **F2** |
| M-37 | **`0`** | The 9 entries using `.value()`/`.toInteger()` on a `UInteger` (`UIntegerExpression.in:43,46,49,52,55,58,134,137,140`) all expect a `: Integer` or `: OclVoid` suffix already, because `Value.toStringWithType` prints `getRuntimeType()` (`F/uml/ocl/value/Value.java:204-208`). And **no** corpus entry nests such a call inside another operation — the only `.value()` used as an operand is `UReal(4, 3.3).value()` at `:37`, whose op already declares `mkReal()` correctly. So no dispatch changes either |
| M-38 | **`0`** | `UBooleanExpression.in:143` dispatches to `Op_boolean_or`; the grep for a reachable witness returns nothing. Full argument in §1 C3 |
| M-43 | **`0`** | Value-test rows; the corpus is not involved |
| M-44 | **`0`** | The 40 sites are in the four `*ExpOpsTest`/`ExpQueryUncertaintyTest` files, not in the `.in` corpus |
| M-45 | **`0` expectations; UNVERIFIABLE outcomes for whatever runs after** | Same experiment as CF-5. Note the direction: under `-Pupstream-oracle` the reactor runs **45 classes / 315 methods** (`stage-00-baseline.md` §4) instead of 3, so the population that can see the leaked `false` grows by an order of magnitude **at exactly the moment B3 lands** |
| M-48b | **`0` as recommended** | The `toString()` appears only in a **failure** message (`:90`), so it is invisible while all 1427 pass. Adding an explicit `toString()` (rather than a `record`) also closes gap G3 |
| M-49b | **`5`, and this is the one genuinely dangerous row: `UBooleanExpression.in:8,11,14`, `URealExpression.in:62,65`** | All 5 error-path entries pass through `split("\n(\r\n)")` at `:88`. On Linux the regex never matches, so `errArray.length == 1`, `errArray[0]` is the **entire** captured stderr, and `errMessage` is that buffer with all `\n`/`\r` removed. Those 5 expectations are therefore matched against a **concatenation**, and they pass today only because the concatenation happens to equal the expected string — which requires the compiler to have written exactly one line. `sos` is also **not reset on the success path** (`specification.md` §6.4), so the buffer can accumulate. **UNVERIFIABLE without executing. Settles by:** the two-run experiment in §7.3 — capture `sos.toString()` verbatim for each of the 5 entries under the *unfixed* regex, commit those five strings as a fixture, then fix the regex and assert the selected line equals the recorded expectation |
| M-51 | **`0`** | Only reached when a fixture file cannot be opened; the message is preserved by the recommended variant |

**Summary for S8.** Of 33 rows, **24 have corpus impact provably zero**, **1 is zero-with-widening
and safe** (F-4, 3 entries, all expecting `true`), **4 are UNVERIFIABLE** (CF-5, M-10, M-32, M-45),
**1 changes 5 expectations' adjudication path** (M-49b), and **3 change the harness's own contract
rather than any expectation** (CF-7, CF-8, M-48b). **No fix in this plan is predicted to change a
single one of the 1427 expected strings.** That is a strong claim and S8 must test it, not assume it:
the acceptance criterion for S8 is *1427 entries, 1427 passes, zero expectation edits*, and any
expectation S8 finds itself wanting to edit is a finding that belongs back in this document.

---

## 4. The harness consequence, and the mechanism

### 4.1 Which rows the differential sweep can even see

`DifferentialSweep` drives `HistoricalOracle` against a ported `Candidate` through `UOp` — a
*method on a value class* (`UOp.java:10-15`). It therefore sees exactly the rows whose defect lives
in a `Value` method, and is blind to the rest.

| Visible to the sweep (`DIFFER` expected) | Invisible to the sweep |
|---|---|
| **F-3** (`URealValue.hashCode`), **F-4** (`URealValue.equals`), **F-10** (`UIntegerValue.hashCode`), **M-8** (`UBooleanValue.equals`), **M-9** (`UIntegerValue.compareTo`), **M-10** (`UIntegerValue.equals` / `URealValue.equals`), **M-11** (`UStringValue.equals`), **M-12** (`UStringValue.compareTo`), **F-2** (`MathUtil.round`, observed through every `equals`) | **M-18** (`SBooleanValue.compareTo`) — `supports()` returns `false` today, see §6. **M-37, M-38** (`OpGeneric` registries, not `Value` methods — `HistoricalOracle.MARSHALLABLE_RECEIVERS` at `HistoricalOracle.java:134-136` lists only value classes). **M-21, M-22** (type layer — `harness-contract.md` §C3: "the differential harness cannot see the type layer"). **M-26–M-33** (expression/parser layer). **All 6 test-harness rows** (CF-5, CF-7, CF-8, CF-9, M-43, M-44, M-45, M-48b, M-49b, M-51) |

**So: 9 of the 33 rows will produce `DIFFER` rows in an S4–S7 sweep, and 24 will not.** That is not
reassuring — it means **24 fixes have no automatic signal at all** and must be evidenced some other
way (§7.3). It also means the pre-registration mechanism below is needed for **9 rows**, which is a
fundable number.

### 4.2 Why the existing mechanisms do not cover this

`AcceptedThrowPairs` adjudicates **throw vs throw** (`AcceptedThrowPairs.java:92-99` keys on both
throwable classes and both messages). `AcceptedDegenerateOperations` adjudicates **a reference that
could not have said anything else** (`AcceptedDegenerateOperations.java:105-110`, keyed on the
operation and the sole reference value). Neither can express *"both sides returned a value, they
differ, and the difference is the correction we decided to make"*. Under `harness-contract.md`
§4.2 clause 2, that row is a disagreement and the gate refuses — correctly, today.

### 4.3 The proposed mechanism — `IntendedDepartures`

A new test-scoped class in
`use-core/src/test/java/org/tzi/use/uncertainty/differential/IntendedDepartures.java`, plus one new
`DiffVerdict` constant. Deliberately modelled on the two existing allowlists, including their
friction.

**New verdict `INTENDED_DEPARTURE`.** Its position in the taxonomy, in the terms
`harness-contract.md` §2 uses:

| | `isAgreement()` | `isMeasurement()` | counts as a disagreement in gate clause 2 |
|---|---|---|---|
| `INTENDED_DEPARTURE` | **no** | **yes** | **no** |

Reasoning, and each clause matters:

* **Not an agreement.** The two sides did not agree; claiming they did is the `AGREE_THROWN` mistake
  that cost 21 816 rows of false green (`DiffVerdict.java:17-36`). `agreementCount()` must not move.
* **Is a measurement.** Two values were observed and compared. It belongs in `measurementCount()`,
  because it *is* evidence — evidence of a difference we predicted. This is the one point where it
  differs from `ACCEPTED_THROW`, which is an agreement that measured nothing.
* **Excluded from clause 2 only.** It does not relax clause 1 (the measurement floor) or clause 3
  (discriminating power). A sweep in which *every* row is an intended departure still fails clause 3.

**The key.** Five discriminators plus two mandatory strings:

```java
builder.declare(
    /* operationKey        */ "UStringValue.equals(value)",
    /* ledgerRowId         */ "M-11",
    /* referenceCanonical  */ "BOOLEAN(false)@Boolean",
    /* subjectCanonical    */ "BOOLEAN(true)@Boolean",
    /* rationale           */ "B7 (user decision 2026-08-17): UStringValue.equals is constant false "
                            + "in the fork because String.equals(UString) can never hold and the "
                            + "confidence conjunct compares the receiver to itself "
                            + "(F/uml/ocl/value/UStringValue.java:86-87). The port delegates to "
                            + "uDataTypes.UString.equals, which compares string and confidence "
                            + "(uDataTypes/UString.java:111-119). The reference is wrong; the "
                            + "subject is right. b7-fix-plan.md section 1 C1.");
```

**Six rules, each closing a specific door:**

1. **Both canonical forms are in the key, verbatim and type-bearing** — the same
   `canonical()` string the verdict compares, including the `@Class` suffix round 6 added
   (`harness-contract.md` §4.2 clause 3). A declaration therefore **lapses automatically** the moment
   either side's answer changes: a widened corpus, a different jar, a different seed, or a porting
   regression on top of the correction. This is exactly `AcceptedDegenerateOperations`'s
   "why the key includes the value" argument (`AcceptedDegenerateOperations.java:33-40`) applied to
   two columns instead of one.
2. **`ledgerRowId` is mandatory and must match `^(CF|M|F)-[0-9]+$`.** It ties every departure to a
   row of §2 of this document. A departure with no ledger row is not a decision, it is a surprise.
3. **`rationale` is mandatory and non-blank**, and is copied into the note column of every row it
   adjudicates and into the report header, exactly as `AcceptedThrowPairs` does
   (`AcceptedThrowPairs.java:79-86, 132-134`). The weakness travels with the number.
4. **No blanket form is expressible.** There is no `declareOperation(String)`, no wildcard, no
   predicate. A `Builder.declare` call with a blank argument throws
   (`AcceptedThrowPairs.java:153-159`'s `require`, copied). "Accept `DIFFER` on
   `UStringValue.equals`" cannot be written.
5. **A contradicted declaration does not apply.** Add a mandatory `predictedDirection` enum —
   `REFERENCE_WAS_WRONG` / `SUBJECT_IS_WIDER` / `SUBJECT_IS_NARROWER` — checked against the observed
   pair by a per-row predicate. If the observed pair does not match the prediction, the row stays
   `DIFFER`. **This is what converts "discovered" into "pre-registered":** the stage must state, before
   the run, not merely *that* the operation will differ but *which way*.
6. **The count is in the header.** `# rows.intendedDeparture N`, `# op.<key>.intendedDeparture n`, and
   `# intendedDeparture.<ledgerRowId> n`, all printed by `DiffReportWriter` unconditionally including
   when zero — the pattern H21 used for `rows.subjectTypeObserved` / `rows.subjectTypeAssumed`
   (`foundation-verdict.md:276`). A reader of the number cannot avoid seeing it.

**The economy problem, and the digest that solves it.** Rule 1 means one declaration per *input
pair*. For `UStringValue.equals` over `uStringBoundaries() × uStringBoundaries()` that is tens of
declarations; for `URealValue.equals` over the UReal corpus it could be hundreds. Writing them by
hand is the deliberate friction — but at some population it becomes copy-paste, which is the opposite
of review. So add a **second, bounded** form and no third:

```java
builder.declareBounded(
    /* operationKey */ "URealValue.equals(value)",
    /* ledgerRowId  */ "F-4",
    /* exactCount   */ 12,
    /* pairsSha256  */ "3f9a…",   // sha256 of the sorted "refsubj" lines, one per departing row
    /* direction    */ SUBJECT_IS_WIDER,
    /* rationale    */ "…");
```

`exactCount` and `pairsSha256` together preserve the lapse property — the declaration stops matching
if the population changes **by one row** — while costing one entry instead of twelve. It is **not** a
blanket: it names the exact set, it just names it by digest. The full pair list is printed into the
report, so the golden-file comparison (`harness-contract.md` §4.2 clause 4) still puts every pair in
front of a human as a reviewable diff. **Cap `declareBounded` at, say, 64 pairs and require the
per-pair form below that**, so the digest is a relief valve for the genuinely large populations and
not the default.

**What S4–S7 must do, in order:**

1. Before the sweep, write the `IntendedDepartures` for the row(s) that stage fixes, with the
   predicted direction and the rationale, **into the stage document**.
2. Run the sweep. `requireStagePass(floor, degenerate, intended)` — a third parameter, never
   defaulted, exactly as `AcceptedDegenerateOperations` is never supplied implicitly
   (`AcceptedDegenerateOperations.java:46-47`).
3. Any residual `DIFFER` is a porting error. Any declaration that did **not** fire is either a fix
   that did not land or a prediction that was wrong — **both are failures, and the gate must say so**.
   Add a fourth clause: `unusedDeclarationCount() == 0`. Without it, a stage can pre-register a
   departure, fail to implement the fix, and pass.

Clause 3 above is the part that is easy to leave out and is the whole point: a pre-registration
mechanism that only ever *permits* differences lets an unfixed defect through. It must also *require*
them.

### 4.4 Where this leaves the 24 invisible rows

They get no harness signal, so the gate cannot help. Their evidence is: the four `.in` files under
S8 (which per §3 should show **zero** expectation changes), the `-Pupstream-oracle` suite (45 classes
/ 315 methods, `stage-00-baseline.md` §4), and the purpose-built tests §7.3 lists. **A stage must not
quote a differential figure as evidence for a row in the right-hand column of §4.1.** That
mis-attribution is the exact failure `harness-contract.md` §5 calls a declared limit, and it would be
the easiest way for this port to look better-evidenced than it is.

---

## 5. What this does to S10's verdict wording

### 5.1 The problem, stated precisely

"Faithful" has meant "behaves as the historical implementation". Under B7 that is **false by
construction** on 9 sweep-visible rows and on 24 further rows the sweep cannot see. An S10 verdict
that says "faithful" without qualification is a false statement, and a hostile reviewer will find it
in one grep — because this document exists and is in the repository.

### 5.2 The proposed three-way wording

S10 must state three separate claims, in this order, each with its own evidence and its own failure
mode. **Never collapse them into one word.**

> **1. COMPLETE.**
> *"Every artefact enumerated in `specification.md` §1 exists in the port, and every operation
> enumerated in §2 is registered, reachable and dispatchable in the order §2.6 requires."*
>
> A claim about **presence**, not behaviour. Evidence: an inventory test that fails if a file is
> missing, plus a registration-order test. Says nothing about whether any operation is correct.
> Falsifiable by one absent file.

> **2. FAITHFUL MODULO N ENUMERATED CORRECTIONS.**
> *"On every input the differential harness could supply, the port agrees with the historical
> implementation, except on exactly N pre-registered corrections, each identified by a ledger row id
> in `b7-fix-plan.md` §2, each carrying a written rationale and the observed (reference, subject)
> value pair, and no divergence exists that was not pre-registered."*
>
> Evidence: the `IntendedDepartures` declarations, the goldens, and
> `rows.disagreement == 0 && unusedDeclarationCount() == 0`. **The number N is not a footnote — it
> belongs in the sentence.** Failure modes: a residual `DIFFER` (porting error), an unused
> declaration (an unlanded fix), or a departure the harness cannot see (which this clause must
> explicitly disclaim — see §5.4).

> **3. NON-REGRESSIVE.**
> *"`mvn -B verify -Djava.awt.headless=true` and `mvn -B verify -Pupstream-oracle
> -Djava.awt.headless=true` are both green; no upstream test was modified; `git diff --name-status`
> over the upstream test tree is empty."*
>
> Evidence: the two commands, their pasted output, and the diff. This is the only one of the three
> that is cheap to check and impossible to fudge, and it is the one that carries B3's weight: without
> `-Pupstream-oracle` it covers 3 test classes, with it 45 (`stage-00-baseline.md` §4).

### 5.3 The one sentence S10 should actually print

> **"The port is COMPLETE against `specification.md` §1–§2; FAITHFUL to the historical
> implementation MODULO 9 enumerated corrections (ledger rows F-2, F-3, F-4, F-10, M-8, M-9, M-10,
> M-11, M-12), each pre-registered in `IntendedDepartures` with a written rationale and the observed
> value pair; NON-REGRESSIVE under both acceptance commands. It is NOT bit-faithful, deliberately,
> by decision B7 of 2026-08-17, and the 24 further corrections in `b7-fix-plan.md` §2 that the
> differential harness cannot observe are evidenced by [named tests], not by any agreement figure."**

The final clause is not defensive padding. It is the sentence that stops the reader inferring
harness coverage for the type layer, the expression layer, the operation registries and the test
harness — four of the six layers this port touches.

### 5.4 What a hostile reviewer of the thesis needs to see

Study A measures agreement against this **corrected** reference (`foundation-verdict.md:81`:
"Study A is defined as agreement against this oracle"). A reviewer will make three moves. Each has a
cheap defence and an expensive one; build the cheap one now.

**Move 1: "Your ground truth is the thing you changed. Circular."**
Defence: the departure register is dated, pre-registered, and independently justified against the
*library* rather than against the port — every one of the 9 fixes in §5.3 cites either the vendored
`uDataTypes` source or a measured Java fact, not "the port does it this way".
**What must be visible:** for each of the 9, the specific external authority. F-3/F-10 → the
`equals`/`hashCode` contract in `java.lang.Object`'s javadoc; M-11 →
`uDataTypes/UString.java:111-119`; M-8 → `UBooleanValue.valueOf`'s own normalisation at `:100-103`;
F-4 → the `URealValue` arm three lines above it; M-9 → `Comparable`'s antisymmetry requirement.
**A departure whose only authority is "it looked wrong" must be reclassified as a design change, not
a defect fix.** Audit all 33 against that bar before S10.

**Move 2: "Then report agreement against the *un*corrected reference too, or the number is
unfalsifiable."**
Defence: **do exactly that, and report both.** The harness already loads the historical jars in
isolation (`HistoricalOracle`, `IsolatedJarClassLoader`), so the uncorrected reference is the same
run with `IntendedDepartures.none()`. Publish two figures per operation: agreement with the
departures registered, and the raw `DIFFER` count without them. The second number *is* the size of
the deliberate deviation, and it must appear in the thesis. **Recommendation: make this a required
S10 artefact, one table, 285 rows.**

**Move 3: "Show me a corpus entry your correction breaks."**
Defence: §3's claim that **no fix changes any of the 1427 expectations**. This is the strongest single
sentence available to the thesis and it is also the most fragile — it rests on facts F1–F5 and on
four `UNVERIFIABLE`s (CF-5, M-10, M-32, M-45) plus M-49b's five error-path entries. **If S8 finds it
false, the verdict wording in §5.3 must gain a fourth clause naming the broken entries and why the
correction is still right.** Do not discover that at S10.

A fourth move a sharper reviewer makes: **"9 of 33 have automatic signal; what evidences the other
24?"** §4.4 and §7.3 are the answer, and if §7.3's tests are not written the honest answer is "diff
review", which for 24 behaviour changes is not enough. **Fund §7.3.**

---

## 6. B2 — the revised SBoolean plan (FULL PORT)

| id | The user's decision (2026-08-17, binding) | The recommendation that was **not** taken |
|---|---|---|
| **B2** | **FULL PORT** of `SBoolean`, all 39 operations. | **Skeleton** (`SBooleanType` retained for the compile-time dependency, registry omitted). Recorded at `specification.md` §0 B2 and §8.2. |

### 6.1 What full port requires that the scope-limited plan did not

The scope-limited plan needed only enough of SBoolean to keep the *boolean family's* type lattice and
`StandardOperationsAny`'s import compiling (`specification.md` §8.2 (A) and (B): `UBooleanType` and
`BooleanType` each declare SBoolean a supertype, and `Op_equal.matches:48-68` /
`Op_notequal.matches:197-217` name `SBooleanType` explicitly). Full port adds all of the following.

| # | New obligation under FULL PORT | Size / evidence |
|---|---|---|
| 1 | `StandardOperationsSBoolean.java` — the whole registry | **1502 lines**, 45 anonymous `OpGeneric` subclasses inside an enum (`grep -c 'new OpGeneric()'` → 45, `16-modernization-ledger.md:109`), **39 OCL operations** (`specification.md` §2.5). **Zero fork test coverage** — see #10 |
| 2 | `SBooleanValue.java` | **476 lines** (`wc -l`, this session). Includes the `Builder` with exact-`==` singleton selection (F-11, `:57-68`), `valueOf(Value)`'s deliberate `UBoolean`/`Boolean` coercion (`:71-88`), and `compareTo` returning `0` (**M-18**, `:150-153`) |
| 3 | `SBooleanType.java` | 36 lines |
| 4 | `ExpConstSBoolean.java` | 88 lines. Note it is the **only** `ExpConst*` that wraps its whole body in `try { … } catch (Exception) → Undefined` (`:48-59`) — the shape M-30 asks `ExpConstUString` to adopt |
| 5 | `ASTSBooleanLiteral.java` | 55 lines |
| 6 | The grammar alternative | Already located: `F/parser/base/OCLBase.gpart:499-500` (4-argument literal → `ASTSBooleanLiteral`) plus the type name at `:633` (`name=('UReal'\|'UInteger'\|'UBoolean'\|'UString' \| 'SBoolean')`). Copied verbatim into all six generated grammars (`specification.md` §8.1 Ground 1b). **It is one alternative and one token, not a new grammar level** — materially cheaper than §5.5's risk analysis for the U-type literals |
| 7 | `RealValue.valueOf(Value)` (E26) | Under the skeleton plan this upstream edit **disappeared with `SBooleanValue`** (`specification.md` §1.6 E26: "Under **B2 = option 1 or 2** this edit disappears entirely"). Under FULL PORT it is **back and mandatory** — called from `SBooleanValue:258,271,284,285,290` |
| 8 | The subjective-logic operations | `deduceY` (arity **3**, `SBooleanValue.java:185-189` → `SBoolean.deduceY`), **eight fusion operators** plus `discount`, `applyOn`, `min`, `max`, `projectiveDistance`, `conjunctiveCertainty`, `degreeOfConflict`, `uncertaintyMaximized`, `createDogmaticOpinion`, `createVacuousOpinion` |
| 9 | `consensusAndCompromiseFusion` — **the practical hazard** | `SBooleanValue.java:433-444`. Cost is **O(4ⁿ)** in the number of fused opinions (`tabulateOptions`, library source L1264-1283; `20-ops-SBoolean.md:699-700`). Two hard preconditions, both `IllegalArgumentException`: **all base rates must be equal** (`"CCF: Base rates for CC Fusion must be the same"`, library L957-964) — and because the receiver is prepended at `:436`, this constrains the *receiver* too — and fused size ≥ 2 (`"CCF: Cannot fuse null opinions, or only one opinion was passed"`, L952-953) |
| 10 | **THE NEW HARD PREREQUISITE — `SBooleanValue` marshalling in the harness** | See §6.2. Without it, all 39 operations are `UNSUPPORTED` and the port has **no evidence of any kind** for them |

### 6.2 Why the harness marshalling is now a prerequisite and not an extra

`supports(SBooleanValue.*)` returns `false` today, deliberately and by name:

```
$ sed -n '125,136p' use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracle.java
    /**
     * Receiver simple names {@link #toHistorical(UValue)} can construct an instance for.
     *
     * <p>This set must stay in step with the {@code switch} in {@link #toHistorical(UValue)}: it is
     * the whole basis on which {@link #supports(UOp)} decides that an operation is reachable.
     * {@code SBooleanValue} is deliberately absent — the harness has no {@code SBoolean} marshalling
     * and no {@code UValue.Kind} for it, so all 39 of its operations must report
     * {@link DiffVerdict#UNSUPPORTED} rather than fail invisibly inside the marshaller.
     */
    private static final Set<String> MARSHALLABLE_RECEIVERS = Set.of(
            "URealValue", "UIntegerValue", "UBooleanValue", "UStringValue",
            "RealValue", "IntegerValue", "BooleanValue", "StringValue");
```

and the regression suite pins it (`DifferentialHarnessRegressionTest.java:149-152`:
`assertFalse(oracle.supports(UOp.binary("SBooleanValue", "and")))`).

**Under the skeleton plan that was correct and costless.** Under FULL PORT it is the difference
between 39 operations with evidence and 39 without, because:

> **The fork has zero tests for any of the 39.**
> `grep -rIl "SBoolean" . | grep -v "^./src/main" | grep -v "^./lib"` → **one** file,
> `src/test/org/tzi/use/uml/ocl/type/TypeTest.java`, and it exercises **only the type lattice**
> (`specification.md` §8.2, "What the tests say"). No `SBooleanValueTest`, no `SBooleanExpOpsTest`,
> no `SBooleanExpression.in`. `grep -c 'UString\|SBoolean' *.in` → **0** in all four corpus files
> (re-run this session).
>
> **Therefore the differential harness is the ONLY available evidence for all 39 operations.**

### 6.3 What the marshalling work is, concretely

Four edits, and one of them is the real work:

1. **`UValue.Kind.SBOOLEAN`** — a ninth constant beside the eight at `UValue.java:144-162`, carrying
   four doubles (belief, disbelief, uncertainty, baseRate) rather than the value+uncertainty pair
   every existing uncertain kind carries. **This is the only kind with arity 4**, so `UValue`'s
   accessors (`asDouble`, `uncertainty`, `probability`, `confidence`) do not cover it and three new
   accessors are needed. Its `canonical()` must be exact and locale-independent, matching the
   existing contract at `UValue.java:28` (which already documents
   `SBoolean(%5.3f, %5.3f, %5.3f, %5.3f)` as the fork's *printed* form, verified by `javap -c`).
   **Do not reuse the printed form as the canonical form** — `SBooleanValue.toString`
   rounds to 3 decimals (`SBooleanValue.java:124-130`, `MathUtil.round(…, 3)`), which would make
   two genuinely different opinions canonically equal and silently manufacture `AGREE`.
2. **`toHistorical` branch** — a `case SBOOLEAN:` in the switch at `HistoricalOracle.java:662-700`.
   The 4-double constructor `SBooleanValue(double,double,double,double)` is **package-private**
   (`SBooleanValue.java:18`), as is `SBooleanValue(SBoolean)` at `:23`, and the public route is
   `Builder` (`:28-68`) or `valueOf(Value)` (`:71-88`). Use the `Builder`, as the `UBOOLEAN` branch
   already uses `UBooleanValue.valueOf` rather than `setAccessible`
   (`HistoricalOracle.java:673-676` comments exactly this). **Consequence: the harness cannot
   construct an `SBooleanValue` that bypasses `Builder.build`'s exact-`==` singleton selection
   (F-11), so `TRUE`/`FALSE` identity is reachable and everything else is a fresh instance — which is
   the fork's own reachable domain, and therefore correct.**
3. **`fromHistorical` branch** — a `case VALUE_PKG + "SBooleanValue":` in the switch at
   `HistoricalOracle.java:730-765`, reading the four masses reflectively and calling
   `.observedFrom(result)` like every other branch (`harness-contract.md` §7's D-52 shape
   requirement: the object whose class is read must be the object the invocation returned).
4. **`MARSHALLABLE_RECEIVERS` += `"SBooleanValue"`**, and **the two regression assertions at
   `DifferentialHarnessRegressionTest.java:149-152` must be rewritten, not deleted** — they should
   flip to asserting `supports(...) == true` and that `unsupportedReason` no longer mentions
   marshalling. Deleting them removes the pin on `MARSHALLABLE_RECEIVERS`/`toHistorical` staying in
   step, which is the invariant the javadoc above declares.

**And a fifth item, which is the one most likely to be skipped and is the one that decides whether
any of it counts: an `sBooleanBoundaries()` corpus in `InputGenerator`.** This is defect D-19,
already measured on this very harness:

> `BooleanValue` and `StringValue` "were marshallable from the start, so every one of their 52
> operations reported `supports() == true`, produced rows, and then failed the per-row receiver check
> on all of them: **52 operations at 100 % `HARNESS_ERROR` and zero measurements against a perfect
> port** (defect D-19)." — `InputGenerator.java:31-37`

**Marshalling without a corpus converts 39 silent `UNSUPPORTED` rows into 39 silent
`HARNESS_ERROR` rows and measures nothing.** The boundary list must cover, at minimum: the two
singletons `(1,0,0,1)` and `(0,1,0,1)` (F-11's exact-`==` selection); a vacuous opinion `(0,0,1,a)`;
a dogmatic non-singleton; `u == 0` (the explicit zero-divisor guard at library L1001); **equal base
rates across at least three opinions** (or every `consensusAndCompromiseFusion` row is an
`IllegalArgumentException`); **unequal** base rates (to drive that exception deliberately); and a
collection of size 1 and size 0 for the fusion arity guard. Note the `O(4ⁿ)` cost: **cap the fusion
collection at 4 opinions** (4⁴ = 256 permutations) and say so in the javadoc, or the sweep will not
terminate in a sane time.

### 6.4 Where S9 sits in the work graph, and what must land earlier

**S9 (SBoolean) cannot be last.** Its dependency on the harness runs backwards through the schedule:

```
S3  types, TypeFactory, MathUtil, grammar, Value predicates
     │
     ├── S3.5  HARNESS WIDENING  ◄── NEW, and it belongs HERE, not in S9
     │         UValue.Kind.SBOOLEAN + toHistorical/fromHistorical
     │         + MARSHALLABLE_RECEIVERS + rewritten regression pins
     │         + InputGenerator.sBooleanBoundaries()
     │
     ├── S4 UReal ── S5 UInteger ── S6 UBoolean ── S7 UString
     │
     ├── S8  corpus / .in harness  (1427 entries)
     │
     └── S9  SBoolean  ── 39 operations, evidenced ONLY by the sweep
                             │
                            S10  verdict
```

**Recommendation: the harness marshalling lands as its own stage between S3 and S4, not inside S9.**
Four reasons, in order of force:

1. **It is a change to the measuring instrument, and the instrument is already through eight review
   rounds with a `SOUND_WITH_DOCUMENTED_LIMITS` verdict** (`foundation-verdict.md:67`). Widening it
   during S9 mixes an instrument change with the only stage that depends on the instrument for
   *all* its evidence. If S9's numbers look wrong, no one can tell whether the port or the harness
   moved. Land the widening first, prove it against the **historical jar on both sides** (reference
   vs reference — every row must be `AGREE`), and freeze it.
2. **The regression pins must be rewritten, not deleted** (§6.3 item 4), and that is a review of
   S1's own test suite. It belongs with S1's owner and its reviewers, not squeezed into the last
   port stage.
3. **`sBooleanBoundaries()` is a design task with a measured precedent** (D-19) and a complexity trap
   (O(4ⁿ)). Doing it under S9's schedule pressure is how it becomes three values and a `TODO`.
4. **It de-risks E26.** `RealValue.valueOf` returns under FULL PORT (§6.1 #7), and it is an
   **upstream** edit. Landing the harness first means the sweep is available to prove that edit
   additive before four type stages build on top of it.

**S9 itself then needs, and this is the honest cost:** 39 operations, zero fork tests, one
`.in`-format corpus that does not exist, and a per-operation departure register for M-18. **Budget
S9 as the largest of the seven port stages, not the smallest.** `20-ops-SBoolean.md` §7 already
records the operation-level hazards (`D10 — deduceY has six unguarded divisors`), and §7's own
refuter note R2 says the divisor list "is wrong in both directions" — i.e. S9 starts from a
specification that its own review flags as unreliable on the operation most likely to divide by zero.

### 6.5 Should "full port" be read as including `ExpDefSBoolean`?

**Recommendation: NO. Read "full port of SBoolean, all 39 operations" as the 39 operations plus the
five classes in §6.1 #2–#6, and NOT as `ExpDefSBoolean`. B10 is still open and should be closed as
"drop".**

The argument, from `specification.md` §8.1, which is unusually strong:

* **It is not one of the 39.** `ExpDefSBoolean` is an *expression class*, not an operation. The
  user's decision names a count — 39 — and that count is `StandardOperationsSBoolean`'s registry
  (`specification.md` §2.5). Reading `ExpDefSBoolean` into "all 39 operations" adds something the
  number does not contain.
* **It is unreachable on two independent grounds.** Its only inbound edge is
  `ASTSBooleanDefExpression`, and `grep -rn "ASTSBooleanDefExpression" .` returns **only its own
  declaration (`:11`) and constructor (`:15`)** — no grammar, no generated parser, no test. And the
  surface syntax it implements (`SBoolean(<one expr>)`) exists in **no** grammar: every grammar
  defines exactly one `SBoolean(...)` production and it is the four-argument literal
  (`OCLBase.gpart:499-500`, re-verified this session).
* **It cannot work even if reached** — three defects, two of which are on this plan's own list:
  the inverted guard (**M-27**, `:15-16`), the missing `ctx.exit` (**M-26**, `:22-29`), and an
  `eval` that can return Java `null` (`SBooleanValue.valueOf` returns `null` for a non-boolean
  argument, `:71-88`).
* **Dropping it saves a method in every `ExpressionVisitor` implementor** — the interface gains
  **7** methods rather than 8 (`specification.md` §4.3, §1.3 "8, or 7 under B10").

**But B7 changes the shape of the fallback, and this is the point worth surfacing to the human.**
Under bug-for-bug, "port it with the three defects documented" was a coherent option. Under B7 it is
not: B7 obliges the port to **fix** M-26 and M-27, so porting `ExpDefSBoolean` means shipping a
class that (a) nothing can reach, (b) no grammar can invoke, and (c) **behaves differently from the
fork** — a deliberate divergence on dead code, which must then be pre-registered in
`IntendedDepartures` for an operation the harness cannot drive. That is pure cost with no
observable, no evidence and no reachable behaviour.

> **Recommendation to the human, stated as the B10 answer: DROP `ExpDefSBoolean`,
> `ASTSBooleanDefExpression` and the `visitDefSBoolean` hook. Record that B7 strengthens the case
> rather than weakening it, and record the residual gap `specification.md` §8.1 already names —
> whether some artefact outside `F/src` references a 1-argument `SBoolean(x)` is UNVERIFIABLE from
> source, though the grammar evidence makes it moot because no parser in the fork accepts that
> form.**
>
> If the human instead reads "full port" as including it: port it, apply the M-26 and M-27 fixes,
> and register both as intended departures with the rationale "unreachable dead code, corrected for
> B7 consistency; no observable exists". Then say so in S10's §5.3 sentence, because N becomes 11.

---

## 7. Sequencing, residuals, and the tests B7 obliges

### 7.1 Fixes that must land together, or the fix is a lie

| Bundle | Rows | Why they cannot be separated |
|---|---|---|
| **A** | **M-9 + a new `UIntegerValue` arm in `URealValue.compareTo`** | `URealValue.compareTo` (`:95-110`) has arms for `URealValue`, `RealValue`, `IntegerValue` and an unreachable duplicate (M-3, deleted as preserving). A `UIntegerValue` argument falls through all four and returns `0`. So `UIntegerValue.compareTo(URealValue)` is `0` today, and **negating `0` is `0`** — M-9 alone changes nothing while appearing to fix a sign error. Land both, or the ledger row is discharged falsely |
| **B** | **M-10 + F-3** | M-10 creates new `equals` pairs across `UIntegerValue`/`URealValue`. If `hashCode` is not contract-correct at the same moment, the new equal pairs hash apart and `SetValue` membership becomes order-of-insertion dependent — a worse defect than the one fixed |
| **C** | **CF-5 + M-45** | Deleting the `AllTests` suites removes the pinned order that makes the `Options` leak survivable. Fixing M-45 removes the leak. Either alone is a regression; both together are neutral |
| **D** | **F-1 (preserving) then F-2 (changing), in two commits** | `MathUtil.round` must first be shown byte-identical with the 101 ten-decimal assertions green, *then* de-saturated. One commit conflates "the helper exists" with "the helper is different" |
| **E** | **M-11 alone is safe; M-11 + M-12 change different things** | `equals` (M-11) and `compareTo` (M-12) are independent here because `UStringValue.hashCode` already delegates correctly. Noted so no one bundles them for tidiness and loses the ability to bisect |

### 7.2 Declared residuals — defects B7 does **not** fix, and why

Each must appear in S10, because a reviewer who finds one unlisted will assume the list is
incomplete everywhere.

1. **`RealValue.compareTo(URealValue)` falls through to `toString()` comparison** while
   `URealValue.compareTo(RealValue)` compares numerically (`use-core/.../RealValue.java:78-84` vs
   `F/…/URealValue.java:100-101`). This is what makes `UCollectionOperations.in:161` print
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
5. **The twelve F-rows that must be carried across byte-identically** (F-1, F-5, F-6, F-7, F-8, F-9,
   F-11, F-12, F-13, F-14, F-15, F-16). B7 does not license touching these; `specification.md`
   §7.1/§7.3 explains each. **A reviewer will ask why four F-rows are fixed and twelve are not; the
   answer is in §7.3 of the specification and must be restated in S10, not merely cited.**

### 7.3 The tests B7 obliges, per row, for the 24 rows with no harness signal

B7 says "fix, documenting each". A fix with no test is a claim. Minimum set, and note how many rows
have **no existing observer at all** — that is the real cost of the decision:

| Rows | Required new evidence |
|---|---|
| F-2 | A test at `\|value\| > 9.223372036854776e8` asserting two distinct large `URealValue`s are **not** `equals`. **No existing test or corpus entry reaches this magnitude** |
| M-8 | `UBooleanValue.FALSE.equals(BooleanValue.FALSE)` → `true`, plus the OCL round trip |
| M-9 + bundle A | `sgn(a.compareTo(b)) == -sgn(b.compareTo(a))` over the UReal × UInteger cross product |
| M-10 | `UInteger(2,5).equals(UReal(2,5))` and the reverse, both `true`, plus a `SetValue` cardinality assertion |
| M-11 | Reflexivity (`a.equals(a)`), symmetry, `HashSet` round trip, and the new cross-type `UString('x',1.0) = 'x'` |
| M-12 | `UString` vs `String` sort position; assert the `toString()` route at `:100` is unchanged |
| M-18 | A total-order property test: reflexive, antisymmetric, transitive, over a mixed list of ≥ 32 elements including `UndefinedValue` — the length that makes TimSort check |
| M-21, M-22 | Extend the `TypeTest` isolate (`specification.md` §6.3 / E29) with directly-constructed (non-singleton) types |
| M-29, M-30 | New tests; **`UString(...)` has no corpus example and `ExpConstUBoolean`'s undefined-value path has no reachable corpus entry** |
| M-32 | A `Context`-mutation counter, run over all 1427 entries before and after the hoist |
| M-37 | Assert `ExpStdOp.create(...).type().isTypeOfInteger()` for `x.value()` on a `UInteger` |
| M-38 | The witness `UBoolean(true, 3-5) or UBoolean(true, 3-5)` → `Undefined` (§1 C3) |
| CF-5, M-45 | Two full runs under `-Dsurefire.runOrder=alphabetical` and `reversealphabetical`, diffed |
| CF-7 | The 12-site mutation control in §1 C4 |
| CF-8 | Assert `files.length == 4` and that the run reports 1427 entries |
| M-49b | Capture `sos.toString()` verbatim for the 5 error-path entries under the *unfixed* regex, commit as a fixture, then fix and assert the selected line |
| M-43, M-44, M-48b, M-51, CF-9, M-6, M-26, M-27, M-28, M-31, M-33 | Covered by the existing suite once migrated, or are "do not change" rows whose evidence is the written justification |

### 7.4 UNVERIFIABLE register for this document

1. **Whether the historical suite ever passed.** No Ant, no Maven, no execution. Inherited from
   `16-modernization-ledger.md:281-283` and it applies to every "this entry passes today" statement
   in §3.
2. **`uDataTypes` internal numerics.** `UReal.divideBy`, `UBoolean.and/or/implies`,
   `SBoolean.*Fusion` are opaque jar bytecode. Where this document cites the vendored 2023 source
   (`uDataTypes/UString.java:111-119`, `SBoolean.java:1570-1578`) it is citing **source that may
   differ from the 2021 jar the fork links**. Both citations are load-bearing (C1's fix and M-18's
   non-delegation argument) and **both should be re-verified by a `javap -c` probe against
   `lib/atenearesearchgroup.uncertainty.jar` before S7 and S9 respectively.**
3. **`ASTExpression.gen(Context)` purity (M-32).** Not established; inherited.
4. **The static type of `UInteger / Integer`** under E1's widening, which decides whether
   `UIntegerExpression.in:1304` exercises M-10 (§3).
5. **What `sos.toString()` actually contains** for the 5 error-path entries (M-49b). This is the
   single most consequential unknown in §3.
6. **The S3–S10 stage mapping.** Taken from the task brief; the record defines only S1 and "S4–S7"
   (§2 preamble).
7. **B3's measured numbers differ between two records.** `stage-00-baseline.md` §4 and
   `specification.md:179` give **45 classes / 315 methods**; `foundation-verdict.md:242` gives
   **50 classes / 497 distinct methods**. They are plausibly different measures (classes+methods vs
   distinct method names), but this document cites the former and **someone should reconcile them
   before either is quoted in the thesis.**
8. **Not consulted:** `origin/main`, any earlier port attempt, the GUI and plugin layers' `catch`
   sites (bearing on M-6 and M-51).

### 7.5 Corrections this document makes to the record

To be folded back by whoever owns those files — **this document does not edit them**:

| Target | Correction |
|---|---|
| `16-modernization-ledger.md:63` (F-3), `:74` (F-10); `specification.md` §7.2 F-3/F-10 | "changes `Set{…}` iteration order and hence the printed output the `.in` fixtures assert on" — **wrong mechanism.** Print order is `Collections.sort` on `compareTo` (`SetValue.java:319-323` → `CollectionValue.java:169-173`). Hash changes affect membership only, and per §3 they cannot even do that on this corpus |
| `16-modernization-ledger.md:103`; `specification.md` §7.2 M-38 | "`Undefined or Undefined` throws `NullPointerException` today" — true of the **method**, false of the **OCL expression**: `Op_boolean_or` shadows it (`OpGeneric.java:90` vs `:93-98`; `ExpStdOp.java:129-135`) and the corpus expects and gets `Undefined` (`UBooleanExpression.in:143-144`) |
| `specification.md` §7.2 M-9 | "Masked only because `URealValue.compareTo(UIntegerValue)` itself returns `0`" — correct, and the consequence is not drawn: **M-9's fix alone is a no-op.** See §7.1 bundle A |
| `specification.md` §7.2 M-18 | The row cites `SBoolean.compareTo` at `UDT/SBoolean.java:1570` as though it were the fix. It **cannot** be: it returns `0` inside an ε of `0.001` and is therefore non-transitive, which Java 21's TimSort rejects. See §2 M-18 |
| `specification.md` §7.2 M-11 | The consequence column omits the **new cross-type `true`** the fix creates via `valueOf`'s confidence-1.0 lift. See §1 C1 |
| `specification.md` §7.2 M-37 | "this changes type-checking of every expression that consumes `x.value()`" is right; what is missing is that **the printed suffix does not change** because `toStringWithType` uses `getRuntimeType()`, so the 9 corpus entries are unaffected |

---

## 8. Reproduce every count and quotation in this file

```sh
cd /home/xoruser/msc-4/use-msc2026
F=.git/reference-repositories/uncertainty/USE-Uncertainty
C=$F/src/test/org/tzi/use/parser/uncertainty
H=use-core/src/test/java/org/tzi/use/uncertainty/differential

# the 33 rows
grep -nE '^\| (CF-|M-|\*\*F-)' docs/port2/spec-parts/16-modernization-ledger.md \
  | grep 'BEHAVIOUR-CHANGING' | wc -l                      # 33

# the four criticals, at source
sed -n '79,91p'    $F/src/main/org/tzi/use/uml/ocl/value/UStringValue.java     # C1 (M-11)
sed -n '57,64p'    $F/src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java    # C2 (F-10)
sed -n '56,64p'    $F/src/main/org/tzi/use/uml/ocl/value/URealValue.java       # C2's model
awk 'NR>=470 && NR<=478' \
  $F/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUBoolean.java  # C3 (M-38)
sed -n '29p;36p;43p;50p;57p;64p;71p;82p;89p;96p' \
  $F/src/test/org/tzi/use/uml/ocl/expr/UIntegerExpOpsTest.java                # C4 (CF-7), 10 of 12
sed -n '90p;94p' $C/USECompilerUncertaintyTest.java                            # C4, the other 2

# the two library citations that C1's fix and M-18's non-delegation rest on
sed -n '111,129p' .git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/UString.java
sed -n '1570,1578p' .git/reference-repositories/uncertainty/uDataTypes/Libraries/Java/src/uDataTypes/SBoolean.java
javap -cp $F/lib/atenearesearchgroup.uncertainty.jar uDataTypes.UString | grep -E 'equals|getString|getsConf'

# the print-order mechanism (section 0.2 item 1, section 3)
sed -n '319,323p' use-core/src/main/java/org/tzi/use/uml/ocl/value/SetValue.java
sed -n '169,173p' use-core/src/main/java/org/tzi/use/uml/ocl/value/CollectionValue.java
sed -n '72,85p'   use-core/src/main/java/org/tzi/use/uml/ocl/value/RealValue.java
sed -n '95,110p'  $F/src/main/org/tzi/use/uml/ocl/value/URealValue.java

# the M-38 dispatch argument (section 1 C3)
sed -n '86,99p'   $F/src/main/org/tzi/use/uml/ocl/expr/operations/OpGeneric.java
sed -n '42,46p'   $F/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsBoolean.java
sed -n '62,65p'   $F/src/main/org/tzi/use/uml/ocl/type/VoidType.java
sed -n '128,136p' $F/src/main/org/tzi/use/uml/ocl/expr/ExpStdOp.java
sed -n '143,144p' $C/UBooleanExpression.in
grep -nE 'UBoolean\([^)]*(/ *0|- *5|\* *3)[^)]*\).*(or|and|implies)|(or|and|implies).*UBoolean\([^)]*(/ *0|- *5|\* *3)' $C/*.in   # no output

# the M-37 argument (section 3)
sed -n '198,208p' $F/src/main/org/tzi/use/uml/ocl/value/Value.java
sed -n '42,59p;133,141p' $C/UIntegerExpression.in

# corpus facts F1-F5 (section 3)
grep -c 'UString\|SBoolean' $C/*.in                                         # 0,0,0,0
grep -cE '(Set|Bag|Sequence|OrderedSet)\{' $C/UBooleanExpression.in $C/UIntegerExpression.in $C/URealExpression.in   # 0,0,0
grep -nE '(Set|Bag|Sequence|OrderedSet)\{[^}]*UInteger' $C/*.in             # no output
grep -n ': Set\|: Bag\|: Sequence\|: OrderedSet' $C/*.in                    # 4 hits, all UCollectionOperations
grep -noE '[0-9]+\.[0-9]{11,}' $C/*.in                                      # 5 hits, all inside # FIXME blocks
grep -hE '^-> ' $C/*.in | grep -vE ' : [A-Za-z]' | sort | uniq -c           # 5 error-path entries
grep -nE '\.equals *\( *(true|false) *\)' $C/*.in                           # no output (M-8)
grep -nE '\.equals *\( *-?[0-9]+(\.[0-9]+)? *\)' $C/*.in                    # 4 hits, 3 live (F-4)
sed -n '86,97p' $C/USECompilerUncertaintyTest.java                          # M-49b, the split regex

# the harness facts (sections 4 and 6)
sed -n '125,136p' $H/HistoricalOracle.java     # MARSHALLABLE_RECEIVERS, SBooleanValue deliberately absent
sed -n '464,475p' $H/HistoricalOracle.java     # supports()
sed -n '144,162p' $H/UValue.java               # the eight Kinds
sed -n '660,700p' $H/HistoricalOracle.java     # toHistorical switch
sed -n '730,765p' $H/HistoricalOracle.java     # fromHistorical switch
sed -n '147,154p' $H/DifferentialHarnessRegressionTest.java   # the two SBoolean supports() pins
sed -n '31,40p'   $H/InputGenerator.java       # D-19: marshallable but never marshalled
sed -n '10,35p'   $H/AcceptedThrowPairs.java   # the pattern IntendedDepartures copies
sed -n '33,48p'   $H/AcceptedDegenerateOperations.java  # "why the key includes the value"

# SBoolean sizing (section 6)
wc -l $F/src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java \
      $F/src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsSBoolean.java \
      $F/src/main/org/tzi/use/uml/ocl/type/SBooleanType.java \
      $F/src/main/org/tzi/use/uml/ocl/expr/ExpConstSBoolean.java \
      $F/src/main/org/tzi/use/uml/ocl/expr/ExpDefSBoolean.java \
      $F/src/main/org/tzi/use/parser/ocl/ASTSBooleanLiteral.java
# 476 / 1502 / 36 / 88 / 48 / 55
grep -n 'SBoolean' $F/src/main/org/tzi/use/parser/base/OCLBase.gpart        # :499-500 literal, :633 type name
sed -n '124,153p' $F/src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java  # toString rounds to 3dp; compareTo returns 0
sed -n '433,444p' $F/src/main/org/tzi/use/uml/ocl/value/SBooleanValue.java  # consensusAndCompromiseFusion
sed -n '675,700p' docs/port2/spec-parts/20-ops-SBoolean.md                  # O(4^n), equal base rates

# ground rule 2
git diff --name-status 30d480db..HEAD -- '*/src/main/*'                      # must be empty
```

---

## 9. What this document asks of the human

Nothing about B7 itself — that is decided and not re-opened. Four narrower items:

1. **Confirm the S3–S10 stage mapping** (§2 preamble). It is taken from the task brief; the record
   does not define it.
2. **Approve the new stage S3.5 (harness widening) between S3 and S4** (§6.4), or say where else the
   `SBooleanValue` marshalling should land. It is a change to an instrument that has passed eight
   review rounds, so it needs an owner.
3. **Close B10** (§6.5). Recommendation: **drop `ExpDefSBoolean`**, and note that B7 strengthens that
   case rather than weakening it. If the answer is "port it", N in §5.3 becomes 11.
4. **Reconcile B3's two measured figures** — 45 classes / 315 methods vs 50 classes / 497 distinct
   methods (§7.4 item 7) — before either appears in the thesis.
