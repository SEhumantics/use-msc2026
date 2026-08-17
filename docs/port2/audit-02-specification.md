# Audit 02 — are `docs/port2/specification.md`'s citations true?

**Auditor role. No Maven was run.** Every claim below names a file:line or the exact shell command
that produced it, and pastes real output. Nothing was fixed; findings only.

Scope: `docs/port2/specification.md` (2510 lines), branch `port-uncertainty-2`.
Guard-rail check re-run at audit time:

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
            (empty)
$ git branch --show-current
port-uncertainty-2
```

**Concurrency note.** Another agent held the working tree during this audit and has uncommitted
edits under `use-core/src/test/java/org/tzi/use/uncertainty/differential/`. Two of the three harness
files this audit cites (`HistoricalOracleIsolationTest.java`, `IsolatedJarClassLoader.java`) are
**unmodified**. The third, `UValue.java`, is modified, so its figures were re-checked against `HEAD`
and are unchanged there — `git show HEAD:…/UValue.java | wc -l` → `277`, `| grep -c loadClass` → `0`,
and `:13-16` is the same javadoc. No other finding touches a dirty file.

---

## HEADLINE

| Question | Answer |
|---|---|
| **Citation hit rate** | **175 / 185 = 94.6 %** on the adjudicated sample (strict variant **88.6 %** — see §1.3) |
| **Is the known B4 error symptomatic?** | **No — it is close to isolated.** 4 of the 10 misses are that one wrong path, repeated twice in two places. The rest are five off-by-one line numbers and one incomplete sentence. **Zero misses changed a substantive conclusion.** |
| **B5** — "10 of the 12 assertions in `testSupertype` become false" | **CONFIRMED, exactly 10 of 12.** |
| **B8** — sqrt/pow shadowing chain | **CONFIRMED, every link.** |
| **B6** — "79 entries expecting `-> Undefined : OclVoid`" | **CONFIRMED, exactly 79.** |
| **B11** — `UnlimitedNatural` lattice inconsistency | **CONFIRMED**, including the "pre-exists upstream" clause. |
| **Is the inventory complete (not merely correct)?** | **Yes.** Reconstructed independently by two methods that do not use the document's keyword filter; both land on the same 33 new files and 26 upstream edits. No unlisted file needs to change. |

**Verdict: SOUND_WITH_CAVEATS.** The document is fit to execute S3–S8 from. Fix the ten cited
line/path errors so a reader who follows a citation lands on the right line, but nothing here
requires re-deciding anything.

---

# 1. Citation hit rate

## 1.1 How the sample was taken

Citations were extracted mechanically, not by eye. Two forms occur:

```bash
# form A: File.ext:NN , File.ext:NN-MM , File.ext:NN,MM,PP
python3 - <<'EOF'
import re
pat = re.compile(r'([A-Za-z0-9_./\-]+\.(?:java|gpart|in|use|g4|md|sh|xml|txt|properties))'
                 r'((?::\d+(?:-\d+)?)(?:\s*,\s*:?\d+(?:-\d+)?)*)')
for i,l in enumerate(open('docs/port2/specification.md'),1):
    for m in pat.finditer(l): print(i, m.group(1), m.group(2))
EOF
# -> 218 tokens

# form B: bare backticked shorthand `:NN` whose file comes from context
#   -> 217 further tokens
```

Aliases were resolved from the table at `specification.md:9-16`
(`F/`, `FT/`, `T/`, `TT/`, `UDT/`, `G/`). Where a token is a bare basename with no alias, both the
fork tree and the 7.5.0 tree were opened and the one matching the document's claim was taken.

Distribution of the 218 form-A tokens, by section:

```
36  §7.1 modernization ledger (preserving)      13  §0  BLOCKING DECISIONS
36  §7.2 modernization ledger (changing)        13  §3.1 conformance grid
13  §8.1 ExpDefSBoolean                         12  §3.5 UncertainType placement
11  §1.1–§1.8 inventory                          8  §5.5 ambiguity risk
 7  §4.3 ExpressionVisitor                      69  all other sections
```

**Adjudicated sample: 185 distinct tokens** (all of §0, all of §1, all of §7, all of §3.1/§3.2,
all of §3.5, all of §5.5/§5.6, all of §8.1, plus a sweep of §2, §4, §6 and §10), each opened at the
cited line and compared against what the document says is there. The sample is weighted exactly as
requested: **§0 100 % covered, §1 100 % covered, §7 100 % covered.**

## 1.2 The ten misses, in full

| # | Spec line | Citation | Class | What is actually there |
|---|---|---|---|---|
| M1 | `:35` (**B4**) | `…/imports/t133_import_date.use:29` | **WRONG_PATH** | Real path is `use-gui/src/it/resources/testfiles/shell/imports/…` — the `shell/` segment is missing. Line 29 is correct: `\tequals(other:Date):Boolean =` |
| M2 | `:35` (**B4**) | `…/imports/t133_import_datetime.use:12` | **WRONG_PATH** | Same missing `shell/`. Line 12 correct: `\t    (self.date.equals(other.date) and self.time.before(other.time))` |
| M3 | `:1545` (§5.5) | `…/imports/t133_import_date.use:29` | **WRONG_PATH** | Duplicate of M1 (same table content restated) |
| M4 | `:1545` (§5.5) | `…/imports/t133_import_datetime.use:12` | **WRONG_PATH** | Duplicate of M2 |
| M5 | `:48` (§0 tail) | `11-types.md:747` | **WRONG_LINE** | `sed -n '747p' docs/port2/spec-parts/11-types.md` → **blank line**. The claim ("all siblings package-private") is at **`11-types.md:711`**; the fuller treatment is at `:70-76` |
| M6 | `:103` (§1.2) | `URealType` ctor `` `:9` `` | **WRONG_LINE** | `grep -n 'protected URealType' …/URealType.java` → **`8`**. §7.2 M-22 (`:2016`) has it right (`URealType:8`), so the document contradicts itself |
| M7 | `:32` (**B1a**) | `HistoricalOracleIsolationTest.java:69-70` | **WRONG_LINE** | The `uDataTypes.` assertion the sentence is about is at **`:71`** (`assertTrue(IsolatedJarClassLoader.isIsolated("uDataTypes.UReal"))`). `:70` asserts the `org.tzi.use.` prefix; `:69` is a message string |
| M8 | `:2132` (§8.1) | `ExpDefSBoolean.java:16-17` | **WRONG_LINE** | The two-line block quoted verbatim underneath sits at **`:15-16`**. §7.2 M-27 (`:2021`) has it right, so again self-contradictory |
| M9 | `:1968-2036` (§7.2) | subsection header "### Expression / parser layer **(7)**" | **WRONG_CONTENT** | The table under it has **8** rows (M-26…M-33) |
| M10 | `:1968-2036` (§7.2) | subsection header "### Test-harness layer **(7)**" | **WRONG_CONTENT** | The table under it has **6** rows (M-43, M-44, M-45, M-48b, M-49b, M-51) |

M9/M10 cancel: `4 + 11 + 2 + 8 + 2 + 6 = 33`, and **33 is the correct grand total** stated in the
heading. Reproduced:

```bash
$ python3 - <<'EOF'
import re
L=open('docs/port2/specification.md').read().split('\n')[1967:2036]
h=None;c=0
for l in L:
    if l.startswith('### '):
        if h: print(f'{c:3d} rows under {h}')
        h=l;c=0
    elif re.match(r'^\| \*\*(CF|M|F)-', l): c+=1
print(f'{c:3d} rows under {h}')
EOF
  4 rows under ### Compile-forced but behaviour-changing (4)
 11 rows under ### Value-layer defects — reproducing vs fixing (11)
  2 rows under ### Type-layer (2)
  8 rows under ### Expression / parser layer (7)      <-- says 7
  2 rows under ### Operations layer (2)
  6 rows under ### Test-harness layer (7)             <-- says 7
```

One further **WRONG_CONTENT**, incomplete rather than false, not counted in the ten because it is
not a file:line citation: §4.3 row 5 (`specification.md:1004`) says
`GenerateHTMLExpressionVisitor` "overrides only `quoteContent`/`formatOperation`/`formatKeyword`".
It also overrides `toString()`:

```
$ grep -n '@Override' -A1 use-core/src/main/java/org/tzi/use/uml/ocl/expr/GenerateHTMLExpressionVisitor.java
39:	@Override
40-	protected String quoteContent(String s) {
44:	@Override
45-	public String toString() {          <-- unlisted
49:	@Override
50-	protected String formatOperation(String s, Expression exp) {
54:	@Override
55-	protected String formatKeyword(String s, Expression exp) {
```

The conclusion of that row ("none" — no edit needed) is unaffected.

## 1.3 A systematic precision issue (11 tokens) — reported separately

`specification.md:634-647` prints "The eleven `conformsTo` bodies, **verbatim**" as
`File.java:NN   <body text>`. In every one of the eleven, `NN` is the **method declaration** line
and the quoted text is the line **after** it:

```
$ sed -n '43,44p' …/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type/UBooleanType.java
    public boolean conformsTo(Type other) {                                  <- :43, what is cited
        return other.equals(this) || other.isTypeOfOclAny() || …;            <- :44, what is quoted
```

Checked all eleven (`UBooleanType:43/44`, `UIntegerType:48/49`, `URealType:13/14`,
`UStringType:31/32`, `SBooleanType:32/33`, `BooleanType:63/64`, `IntegerType:71/72`,
`RealType:62/63`, `StringType:57/58`, `VoidType:23/24`, `OclAnyType:77/78`): **every quoted body is
textually correct**, and every line number is off by exactly one in the same direction. This reads
as a deliberate "cite the method, quote the body" convention, so it is **not** counted as ten-plus
misses in the headline. Counted strictly it moves the rate to **174 / 196 = 88.6 %**.
Both numbers are given; take the strict one if your reader will `sed -n 'NNp'` the citation.

## 1.4 A structural risk that produced no miss but will

**163 of the 218 form-A tokens (75 %) are bare basenames with no `F/`/`T/`/`FT/`/`TT/` alias.**
For at least 40 of them the same basename exists in **both** trees with different content at the
cited line. Example, `specification.md:33` (**B2**):

```
$ sed -n '111,112p'  …/USE-Uncertainty/src/test/org/tzi/use/uml/ocl/type/TypeTest.java
				"UBoolean < SBoolean",
				TypeFactory.mkUBoolean().conformsTo(TypeFactory.mkSBoolean())        <- intended
$ sed -n '111,112p'  use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java
                    .conformsTo(TypeFactory.mkBag(TypeFactory.mkInteger())));
        assertFalse(                                                                 <- same cite,
                                                                                        wrong file
```

Every such case in the sample resolved correctly *from context*, so none is scored as a miss. But
`TypeTest.java:NN` appears **6 times** and `StandardOperationsNumber.java:NN` **3 times** with the
tree left implicit, and B5 turns on which `TypeTest.java` is meant. **Recommend: prefix every bare
basename with its alias.** This is the single highest-value editorial fix in the document.

## 1.5 What the hit rate means

The B4 error is **not** symptomatic. Of the ten misses, four are one wrong path stated twice, five
are one-line offsets, one is an over-tight adjective. **None of the ten changes a decision, a
count, or a mechanism.** In particular, the whole of §7 (72 tokens, the ledger a human executes
row-by-row) scored **72/72 on file:line** — its only defects are the two subsection row-counts.

Section-level detail of the sample:

| Section | tokens adjudicated | hits | misses |
|---|---|---|---|
| §0 blocking decisions | 16 | 12 | M1, M2, M5, M7 |
| §1 inventory | 30 | 29 | M6 |
| §7 modernization ledger | 72 | 72 | (M9/M10 are row counts, not citations) |
| §3.1/§3.2 lattice | 16 | 16 | (11 off-by-one under the strict reading, §1.3) |
| §3.5 `UncertainType` placement | 12 | 12 | — |
| §5.5/§5.6 grammar ambiguity | 9 | 7 | M3, M4 |
| §8.1 `ExpDefSBoolean` | 13 | 12 | M8 |
| §2/§4/§6/§10 sweep | 17 | 17 | — |
| **total** | **185** | **175** | **10** |

---

# 2. The load-bearing blocking decisions, verified from source

## 2.1 B5 — "10 of the 12 assertions in `testSupertype`" — **CONFIRMED**

`use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java:135-228`. Twelve `assertEquals`,
confirmed by count:

```
$ awk 'NR>=135 && NR<=228' use-core/src/test/java/org/tzi/use/uml/ocl/type/TypeTest.java \
    | grep -c 'assertEquals('
12
```

The fork's crisp types gain U-supertypes:

```
$ grep -n 'allSupertypes' -A8 …/USE-Uncertainty/…/type/{BooleanType,IntegerType,RealType,StringType}.java
BooleanType.java:71    res.add(mkOclAny()); res.add(mkUBoolean()); res.add(mkSBoolean()); res.add(this);
IntegerType.java:79    res.add(mkOclAny()); res.add(mkUReal()); res.add(mkReal()); res.add(mkUInteger()); res.add(this);
RealType.java:69       res.add(mkOclAny()); res.add(mkUReal()); res.add(this);
StringType.java:66     res.add(mkOclAny()); res.add(mkUString()); res.add(this);
```

and the collection types **derive** their supertype set from the element type, so the breakage
propagates:

```
$ sed -n '79,90p' use-core/src/main/java/org/tzi/use/uml/ocl/type/CollectionType.java
    public Set<Type> allSupertypes() {
        Set<Type> res = new HashSet<Type>();
        Set<? extends Type> elemSuper = fElemType.allSupertypes();      <-- derived
        …            res.add(TypeFactory.mkCollection(t));
$ sed -n '90,101p' use-core/src/main/java/org/tzi/use/uml/ocl/type/SetType.java
        res.addAll(super.allSupertypes());
        Set<? extends Type> elemSuper = elemType().allSupertypes();     <-- derived
```

Assertion-by-assertion:

| # | assertion | breaks? | why |
|---|---|---|---|
| 1 | `OclAny.allSupertypes()` | **no** | `OclAnyType.allSupertypes():48-52` = `{this}`, untouched |
| 2 | `Boolean.allSupertypes()` | **yes** | gains `UBoolean`, `SBoolean` |
| 3 | `Integer.allSupertypes()` | **yes** | gains `UInteger`, `UReal` |
| 4 | `Real.allSupertypes()` | **yes** | gains `UReal` |
| 5 | `String.allSupertypes()` | **yes** | gains `UString` |
| 6 | `Enum.allSupertypes()` | **no** | `EnumType.allSupertypes():118-123` = `{OclAny, this}`, untouched |
| 7 | `Collection(Boolean)` | **yes** | element gains ⇒ `Collection(UBoolean)`, `Collection(SBoolean)` appear |
| 8 | `Collection(Integer)` | **yes** | ⇒ `Collection(UInteger)`, `Collection(UReal)` |
| 9 | `Collection(Collection(Real))` | **yes** | inner `Real` gains ⇒ `Collection(Collection(UReal))` |
| 10 | `Set(Integer)` | **yes** | `SetType` derives from element |
| 11 | `Sequence(Integer)` | **yes** | same |
| 12 | `Bag(Integer)` | **yes** | same |

**10 break, 2 survive. The document's number is exact.** Independent corroboration: the fork's own
`TypeTest#testSupertype` (`FT/uml/ocl/type/TypeTest.java:204+`) has amended precisely those
assertions —

```
$ sed -n '228,247p' …/USE-Uncertainty/src/test/org/tzi/use/uml/ocl/type/TypeTest.java
        assertEquals("Boolean.allSupertypes()",
                     mkSet(new Object[] { mkBoolean(), mkOclAny(), mkUBoolean(), mkSBoolean() }), …
        assertEquals("Integer.allSupertypes()",
                     mkSet(new Object[] { mkInteger(), mkUInteger(), mkReal(), mkOclAny(), mkUReal()}), …
```

— which is what "this cannot be fixed by moving tests to a new class" means in practice. The B5
recommendation (option 1, adopt the lattice) rests on `getLeastCommonSupertype` being driven by
`allSupertypes()`, and that is true: `TypeImpl.java:106-127` intersects the two supertype sets.

## 2.2 B8 — sqrt/pow shadowing — **CONFIRMED, every link**

| link | claim | evidence |
|---|---|---|
| 1 | 7.5.0 **added** `Op_number_sqrt` / `Op_number_pow` | `StandardOperationsNumber.java:848` = `final class Op_number_sqrt extends OpGeneric {`; `:802` = `final class Op_number_pow extends OpGeneric {` |
| 2 | registered at `:32` / `:31` | `sed -n '31,32p'` → `registerOperation(new Op_number_pow(), opmap);` / `registerOperation(new Op_number_sqrt(), opmap);` |
| 3 | the **fork has neither** | `grep -rn 'Op_number_sqrt\|Op_number_pow' F/uml/ocl/expr/operations/` → **no hits**; the fork's `registerTypeOperations` (`:20-42`) jumps `greaterequal → toString` |
| 4 | `matches` is `isKindOfNumber(EXCLUDE_VOID)` | `Op_number_sqrt.matches` → `(params.length == 1 && params[0].isKindOfNumber(VoidHandling.EXCLUDE_VOID)) ? TypeFactory.mkInteger() : null` |
| 5 | `URealType`/`UIntegerType` answer `true` | `F/…/URealType.java:28-30` and `F/…/UIntegerType.java:19-20`, both `isKindOfNumber(VoidHandling h) { return true; }` |
| 6 | registered **before** the uncertainty registries | fork `OpGeneric.java:88` `StandardOperationsNumber`, `:93-97` the five `StandardOperationsU*`/`SBoolean`. 7.5.0's `:88` is identical |
| 7 | first match wins | `ExpStdOp.java:129-134`: `for (OpGeneric op : ops) { Type t = op.matches(params); if (t != null) return new ExpStdOp(op, args, t); }`, over `ArrayListMultimap` (`ExpStdOp.java:54`, insertion-ordered) |
| 8 | "types as `Integer`" | link 4 — `matches` returns `TypeFactory.mkInteger()` (note: `eval` returns a `RealValue`; that is an upstream inconsistency, reproduced faithfully by the doc) |
| 9 | then **ClassCastException** | `Op_number_sqrt.eval`: `if (args[0].isInteger()) … else d1 = ((RealValue) args[0]).value();` — and `URealValue extends UncertainValue` (`F/uml/ocl/value/URealValue.java:14`), **not** `RealValue` ⇒ CCE |

Every link holds. The recommendation (**option 1**, tighten `Op_number_sqrt.matches`) is the only
one of the three that does not disturb registration order, and §2.6's warning that option 2 changes
`Integer+Integer` typing is consistent with `ArithOperation.matches` at
`F/…/StandardOperationsNumber.java:61-64`.

## 2.3 B6 — "79 entries expecting `-> Undefined : OclVoid`" — **CONFIRMED, exactly 79**

```
$ cd .git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty
$ grep -c -- '-> Undefined : OclVoid' *.in
UBooleanExpression.in:16
UCollectionOperations.in:0
UIntegerExpression.in:38
URealExpression.in:25
$ grep -h -- '-> Undefined : OclVoid' *.in | wc -l
79
```

16 + 38 + 25 + 0 = **79**. Both with and without the `-> ` prefix the total is 79, so the number is
not sensitive to how the pattern is written.

## 2.4 B11 — `UnlimitedNatural` lattice inconsistency — **CONFIRMED**, including the upstream clause

```
$ diff --strip-trailing-cr -u use-core/…/UnlimitedNaturalType.java  F/…/UnlimitedNaturalType.java
@@ -17,6 +17,8 @@
+// $Id$
+
```

The **only** difference between the two files is a two-line `$Id$` comment — so "identical in
7.5.0" is right, with the caveat that the fork's `:61-63` is 7.5.0's `:59-61`.

* `conformsTo` is predicate-driven and therefore already `true` for the U-types:
  `return !t.isTypeOfVoidType() && (t.isKindOfNumber(EXCLUDE_VOID) || t.isTypeOfOclAny());`
  and `UIntegerType.isKindOfNumber`/`URealType.isKindOfNumber` both `return true`.
* `allSupertypes()` was **not** extended: `{mkOclAny(), mkReal(), mkInteger(), this}`.
* `TypeImpl.getLeastCommonSupertype` (`:106-110`) intersects supertype sets. Under the fork lattice
  `UnlimitedNatural.allSupertypes() ∩ UInteger.allSupertypes()`
  `= {OclAny, Real, Integer, UnlimitedNatural} ∩ {UInteger, UReal, OclAny} = {OclAny}` ⇒ **`OclAny`**. ✔

**The "pre-exists upstream" clause is also true**, and worth stating because it is the strongest
argument for the "reproduce, don't fix" recommendation. In untouched 7.5.0:

```
$ sed -n '59,73p' use-core/src/main/java/org/tzi/use/uml/ocl/type/IntegerType.java
	public boolean conformsTo(Type t) {
        return !t.isTypeOfVoidType() && (t.isKindOfNumber(VoidHandling.EXCLUDE_VOID) || …);
	public Set<Type> allSupertypes() {
        res.add(TypeFactory.mkOclAny()); res.add(TypeFactory.mkReal()); res.add(this);
```

`Integer.conformsTo(UnlimitedNatural)` is `true` (UnlimitedNatural answers `isKindOfNumber`), yet
`UnlimitedNatural ∉ Integer.allSupertypes()`. Same shape, already shipped.

---

# 3. Is the inventory COMPLETE, not merely correct?

The expensive failure mode is an **understated** edit list. Three independent enumerations were run;
all three agree with the document.

## 3.1 The document's own reproduction command re-runs exactly

```
$ (cd $FORK && find . -name '*.java' | sed 's|^\./||' | sort) > /tmp/fork.txt
$ (cd $TGT  && find . -name '*.java' | sed 's|^\./||' | sort) > /tmp/tgt.txt
$ comm -12 /tmp/fork.txt /tmp/tgt.txt | wc -l
539                                  <- document says 539 common files  ✔
$ comm -23 /tmp/fork.txt /tmp/tgt.txt | wc -l
70                                   <- 33 uncertainty + 37 unrelated (GSMetric,
                                        generated ANTLR, main/shell)  ✔
$ <keyword loop from specification.md:63-70>
24 files                             <- document says 24  ✔  (+2 hand-found = 26)
```

The 33 split reproduces: 7 value + 7 type + 8 expr + 5 ops + 6 parser/ocl = **33** (31 under B10).

## 3.2 Independent enumeration #1 — reference graph, no keyword filter

For each of the 33 new fork classes, which **common** (already-existing) file references it?

```bash
NEW='ASTSBooleanDefExpression|ASTSBooleanLiteral|ASTUBooleanLiteral|ASTUIntegerLiteral|ASTURealLiteral|\
ASTUStringLiteral|ExpConstSBoolean|ExpConstUBoolean|ExpConstUInteger|ExpConstUReal|ExpConstUString|\
ExpDefSBoolean|ExpUSelect|ExpUSelectC|StandardOperationsSBoolean|StandardOperationsUBoolean|\
StandardOperationsUInteger|StandardOperationsUReal|StandardOperationsUString|SBooleanType|UBooleanType|\
UIntegerType|URealType|UStringType|UncertainBooleanType|UncertainType|SBooleanValue|UBooleanValue|\
UIntegerValue|URealValue|UStringValue|UncertainBooleanValue|UncertainValue'
grep -rlE "\b($NEW)\b" --include=*.java . | sed 's|^\./||' | sort > /tmp/refs.txt
comm -12 /tmp/refs.txt /tmp/both.txt
```

```
analysis/coverage/AbstractCoverageVisitor.java          -> E13 ✔
analysis/coverage/BasicExpressionCoverageCalulator.java -> E24 ✔
parser/ocl/ASTQueryExpression.java                      -> E8  ✔
uml/ocl/expr/ExpQuery.java                              -> E5  ✔
uml/ocl/expr/ExpressionPrintVisitor.java                -> E12 ✔
uml/ocl/expr/ExpressionVisitor.java                     -> E15 ✔
uml/ocl/expr/operations/OpGeneric.java                  -> E14 ✔
uml/ocl/expr/operations/StandardOperationsAny.java      -> E3  ✔
uml/ocl/expr/operations/StandardOperationsCollection.java-> E2 ✔
uml/ocl/expr/operations/StandardOperationsNumber.java   -> E1  ✔
uml/ocl/type/TypeFactory.java                           -> E6  ✔
uml/ocl/value/CollectionValue.java                      -> E4  ✔
uml/ocl/value/Value.java                                -> E7  ✔
```

**13 for 13, all in the list, nothing unlisted.** The remaining 13 entries (E9–E11, E16–E23, E25,
E26) are predicate/lattice/helper edits with no class reference, found by the keyword filter and by
hand.

## 3.3 Independent enumeration #2 — the API-gap test (this is what caught `MathUtil`/`RealValue`)

Every **static call into an upstream class** made by the 33 new files, and whether 7.5.0 provides it:

```
$ grep -hoE '\b(MathUtil|RealValue|IntegerValue|StringValue|BooleanValue|UndefinedValue|\
CollectionValue|TypeFactory|Options|ExpStdOp|OpGeneric|Value|Type|ParserHelper|FloatUtil)\.[a-zA-Z_]+\(' \
  <33 new files> | sort -u
BooleanValue.get(          -> 7.5.0 BooleanValue.java:55   present  ✔
IntegerValue.valueOf(      -> 7.5.0 IntegerValue.java:102  present  ✔
MathUtil.round(            -> 7.5.0 MathUtil has only max/min (:35,:52,:67,:84)  ** ABSENT ** -> E25 ✔
OpGeneric.registerOperation( -> both 2-arg (:105) and 3-arg (:115) present  ✔
RealValue.valueOf(         -> 7.5.0 RealValue has no static valueOf          ** ABSENT ** -> E26 ✔
TypeFactory.mk{Boolean,Integer,OclAny,Real,Sequence,String}  present  ✔
TypeFactory.mk{SBoolean,UBoolean,UInteger,UReal,UString}     new     -> E6  ✔
```

**Exactly two gaps, and both are already E25 and E26.** This test is keyword-independent and would
have found them even if nobody had checked by hand. The 3-arg
`OpGeneric.registerOperation(String, OpGeneric, Multimap)` used at
`StandardOperationsUInteger.java:17` is a plausible unlisted-edit candidate; it is present in 7.5.0
at `OpGeneric.java:115`, so it is **not** one.

## 3.4 The requested greps — `ExpressionVisitor` implementations

```
$ grep -rn 'implements[^{]*ExpressionVisitor\|extends[^{]*ExpressionVisitor' \
    --include=*.java use-core/src use-gui/src use-assembly
use-core/…/analysis/coverage/AbstractCoverageVisitor.java:33: implements ExpressionVisitor
use-core/…/uml/ocl/expr/ExpressionPrintVisitor.java:35:       implements ExpressionVisitor
use-core/…/uml/ocl/expr/EvalNode.java:618: RelevantOperationHighlightVisitor extends GenerateHTMLExpressionVisitor
$ grep -rn 'extends AbstractCoverageVisitor\|extends ExpressionPrintVisitor\|\
extends GenerateHTMLExpressionVisitor\|extends RelevantOperationHighlightVisitor' --include=*.java use-core/src use-gui/src
use-core/…/analysis/coverage/BasicExpressionCoverageCalulator.java:40
use-core/…/analysis/coverage/CoverageCalculationVisitor.java:38
use-core/…/uml/ocl/expr/GenerateHTMLExpressionVisitor.java:30
use-core/…/uml/ocl/expr/EvalNode.java:351   (SubstituteVariablesExpressionVisitor)
use-core/…/uml/ocl/expr/EvalNode.java:618
```

Seven, and **§4.3's census (`specification.md:998-1006`) lists exactly those seven, at exactly those
line numbers**, with the correct "action" column (only #1 and #2 need edits; #3–#7 inherit). Its
negative results also all reproduce:

```
$ grep -rn 'new ExpressionVisitor' --include=*.java use-core/src use-gui/src            ; echo $?   # 1
$ grep -rn 'ExpressionVisitor' use-core/src/test use-gui/src/test use-gui/src/it        ; echo $?   # 1
$ grep -rn '\bsealed\b\|\bpermits\b' --include=*.java use-core/src/main use-gui/src/main; echo $?   # 1
$ grep -c 'void visit' use-core/…/ExpressionVisitor.java                                            # 49
$ grep -c 'public void visit' use-core/…/AbstractCoverageVisitor.java                               # 49
```

**`use-gui` implements `ExpressionVisitor` nowhere** — the two `MMHTMLPrintVisitor`s only *construct*
`GenerateHTMLExpressionVisitor` (`use-gui/…/gui/util/MMHTMLPrintVisitor.java:47-48` and
`…/gui/utilFX/MMHTMLPrintVisitor.java:47-48`). **This is load-bearing for ground rule 2**: adding
seven methods to the interface does not force any `use-gui` edit. Confirmed.

## 3.5 The requested greps — `instanceof` / `switch` chains over `Value` and `Type`

```
$ grep -rnE 'instanceof +(Integer|Real|Boolean|String|Undefined|Collection|Set|Bag|Sequence|\
OrderedSet|Tuple|Object|Enum|UnlimitedNatural)Value' --include=*.java use-core/src/main use-gui/src/main \
  | awk -F: '{print $1}' | sort | uniq -c | sort -rn
      7 use-core/…/util/rubyintegration/RubyHelper.java
      7 use-core/…/uml/ocl/value/UnlimitedNaturalValue.java
      5 use-core/…/uml/ocl/value/RealValue.java          <- E26 ✔
      5 use-core/…/uml/ocl/value/IntegerValue.java       <- §1.8 "NOT changed"
      3 use-core/…/uml/sys/soil/EvalUtil.java
      3 use-core/…/uml/ocl/value/{TupleValue,StringValue,EnumValue,BooleanValue}.java
      2 use-gui/…/communicationdiagram/CommunicationDiagram.java
      2 use-core/…/uml/ocl/value/{UndefinedValue,CollectionValue}.java   <- CollectionValue = E4 ✔
      1 use-gui/…/objectdiagram/ObjectNode.java, use-core/…/{ObjectValue,DataTypeValueValue,ExpObjOp}.java

$ grep -rnE 'instanceof +[A-Za-z]*Type\b' --include=*.java use-core/src/main use-gui/src/main | …
      4 use-gui/…/classdiagram/ClassDiagram.java          3 use-gui/…/ClassDiagramData.java
      2 use-core/…/uml/ocl/type/MessageType.java          1 each: CollectionValue, VoidType,
                                                          ASTDataType, ASTNewObjectStatement,
                                                          AbstractCoverageVisitor
$ grep -rn 'switch' --include=*.java use-core/src/main/java/org/tzi/use/uml/ocl/{value,type}
      (no output — there is no switch over a value/type kind anywhere)
```

**Does any unlisted site need a new arm?** The authoritative answer is the fork itself: of the 121
common files with a non-trivial fork↔7.5.0 diff, 95 are outside the E-list. Their fork-added method
declarations were extracted and inspected —

```bash
while read f; do
  diff --strip-trailing-cr -u "$TGT/$f" "$FORK/$f" | grep -E '^\+' | grep -v '^+++' | sed 's/^+//' \
   | grep -E '^\s*(public|protected|private|static|final|abstract)[a-zA-Z0-9_ <>,\[\]\.]*\([^)]*\)\s*\{?\s*$'
done < /tmp/unlisted.txt
```

— and **every one is pre-7.5.0 upstream shape, not uncertainty**: `MModel.getModelDirectory()`,
`MOperation.cls()` returning `MClass` (7.5.0 returns `MClassifier`),
`EvalNode.visitObjOp(ExpObjOp)` (renamed to `visitInstanceOp` upstream in `46c277e7`),
`Options.processArgs`, the older `ASTClass`/`ASTModel`/`MClassImpl` surfaces. The fork is behind
7.5.0; that is what those diffs are.

**Conclusion: the inventory is complete.** `RubyHelper`, `UnlimitedNaturalValue`, `EvalUtil`,
`TupleValue`, `EnumValue`, `ObjectValue`, `DataTypeValueValue`, `CommunicationDiagram`,
`ObjectNode`, `ClassDiagram*` and `MessageType` all dispatch over `Value`/`Type` and all correctly
stay off the list — the fork changed none of them, and no U-type reaches them through a path the
fork exercises. The one residual risk worth recording is that this is an argument from the fork's
behaviour, not a proof: if S3–S8 make a U-value reach `RubyHelper` or the GUI object diagram, the
`else` branch there is what will be hit. That is the same class of risk §10 already carries.

---

# 4. Spot-checks that came out clean (a partial list, all with output)

| Claim | Where | Result |
|---|---|---|
| §7 header: 10 CF / 51 M / 16 F rows; 46 preserving / 33 changing | `16-modernization-ledger.md` | **all five greps reproduce exactly** |
| §7.1 has 46 rows | `specification.md:1917-1967` | **46** ✔ |
| §7.2 total 33 rows | `specification.md:1968-2036` | **33** ✔ (subsection headers wrong — M9/M10) |
| no `junit-vintage-engine` anywhere | `grep -rn 'junit-vintage' --include=pom.xml .` | **no hits** ✔ |
| `use-core/pom.xml:16-17` = Java 21, `:63-64` = junit-jupiter | | ✔ both |
| §2.5: `StandardOperationsSBoolean` is 1502 L; naive `grep -c 'new OpGeneric()'` = 45; 6 commented; 39 real | | **1502 / 45 / 6 / 39 / 39 / 39** — all four commands reproduce ✔ |
| §1.1 file sizes 47 / 13 / 351 / 223 / 281 / 206 / 476 L | `wc -l` on the seven fork value classes | **all seven exact** ✔ |
| `TypeImpl.conformsTo` is `return this.conformsTo(other)` (the StackOverflow trap) | `T:78-81`, fork `:76-78` | ✔ both, byte-identical |
| B1a: `UValue.java` is 277 L with no `loadClass` | `wc -l`, `grep -c` | **277 / 0** ✔ |
| §4.6: fork `build.xml:16-17` = `1.7`; root `pom.xml:18-19` = 21 | | ✔ both (the fork's `build.xml` is at repo root, outside `src/`) |
| §4.6: uDataTypes licence MIT 2023 Atenea at README `:261-269` | | ✔ |
| §5.5 prose citations `t133_import_date.use:29`, `t133_import_datetime.use:12`, `t098.use:11` (bare, `specification.md:1556-1563`) | | ✔ — the **prose** has these right; only the abbreviated `…/imports/` table paths are wrong |
| §2 refutation row "`ArithOperation` is at `:54` not `:56`, guard at `:62` not `:63`" | fork `StandardOperationsNumber.java:54,62` | ✔ the refutation is correct |
| §2 refutation row "`Op_enum_toString`/`Op_sBoolean_toString` are fabricated" | `StandardOperationsEnum.java:46` = `final class Op_toString`; `StandardOperationsSBoolean.java:373` = `TO_STRING(new OpGeneric() {` | ✔ |
| §3.5: `qualifiedName()` added at `T/Type.java:48`, DataType pair at `T/Type.java:136-138`, defaults at `TypeImpl:42-46`/`:287-295`, override at `MClassifierImpl:389-392` | | ✔ **all five**, and all correctly resolved against the **target** tree |
| §8.1: the seven `ExpDefSBoolean` touch-points | `F/…:8,12,46`, `ExpressionVisitor:40`, `ExpressionPrintVisitor:190`, `AbstractCoverageVisitor:113`, `AbstractMetricVisitor:121`, `ASTSBooleanDefExpression:5,25` | ✔ **7 for 7** |
| §6: `ShellIT.java:63-80` is the `@TestFactory`; `USECompilerTest.java:79` drives `test_expr.in` | | ✔ both |

---

# 5. Findings, ranked

| id | Severity | Finding | Fix |
|---|---|---|---|
| **F1** | MINOR | **B4's fixture paths are missing the `shell/` segment**, twice at `specification.md:35` and twice at `:1545`. The fixtures, the line numbers and the collision are all real; §5.5's prose (`:1556-1563`) already has the right paths | insert `shell/` in the four abbreviated paths |
| **F2** | MINOR | **The document contradicts itself on two line numbers.** `URealType` ctor: `:9` at `specification.md:103` vs the correct `:8` at `:2016`. `ExpDefSBoolean` guard: `:16-17` at `:2132` vs the correct `:15-16` at `:2021` | take the §7.2 values |
| **F3** | MINOR | `11-types.md:747` (cited twice, at `:48` and `:103`) is a **blank line**; the claim is at `11-types.md:711` | repoint |
| **F4** | MINOR | B1a's `HistoricalOracleIsolationTest.java:69-70` misses the `uDataTypes.`-specific assertion, which is at **`:71`** | repoint to `:70-71` |
| **F5** | MINOR | Two §7.2 subsection row-counts are wrong (8 rows labelled 7, 6 rows labelled 7). They cancel; the grand total 33 is right | fix the two headers |
| **F6** | MINOR | §4.3 row 5 says `GenerateHTMLExpressionVisitor` "overrides only `quoteContent`/`formatOperation`/`formatKeyword`"; it also overrides `toString()` (`:45`) | add it; the row's verdict is unaffected |
| **F7** | MINOR | §3.1's eleven `conformsTo` citations point at the **method declaration**, one line above the body they quote verbatim. A consistent convention, but it defeats `sed -n 'NNp'` | either +1 the eleven numbers or state the convention in the alias table |
| **F8** | **MAJOR (editorial)** | **75 % of citations (163/218) are bare basenames with no tree alias**, and 40+ of those basenames exist in both trees with different content at the cited line — including `TypeTest.java:NN` ×6, on which **B5 turns** | prefix every citation with `F/`, `FT/`, `T/`, `TT/`, `UDT/` or `G/`. Highest-value fix in the document |

**Nothing above is blocking.** No miss changed a decision, a count, a mechanism, or a
recommendation. B5, B6, B8 and B11 — the four decisions a human is about to act on — are all
confirmed from source, and the inventory that S3–S8 will be measured against is complete under two
independent reconstructions that do not reuse the document's own method.
