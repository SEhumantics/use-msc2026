# 05 — Printed output and the historical corpus

**Role: adaptation spec for area 5 (printing / corpus).** This document changes no product source
and no test. It is written under the governing policy:

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

Everything below is either **MEASURED** (a command was run and its output is pasted), **READ FROM
SOURCE** (file:line quoted), or explicitly marked **INFERRED** / **UNVERIFIABLE**. No Maven was run.

---

## 0. Headline

**Three findings, in order of consequence.**

1. **The `Undefined` → `null` rename is the *only* printing difference between the fork and USE
   7.5.0.** This is not an estimate. `UndefinedValue.java` differs between the two trees in exactly
   one behavioural line (§2.1), and a 21-expression crisp control covering scalars, all four
   collection kinds, nesting, tuples, the empty set, `UnlimitedNatural` and the error path produces
   **five** diffs, all of them that one token (§3). Container printing, separator, sort order, type
   names, `Real`/`Integer`/`String`/`Boolean` formatting and the `" : "` suffix are byte-identical
   code in both trees (§2.2).

2. **The corpus is stale against the fork itself. 8 of the 1427 entries do not pass on the fork's own
   shipped `use.jar`** (§4). Measured: 1419 pass / 8 fail. All 8 are `UBoolean` probabilities recorded
   at ~10 decimal places before `UBooleanValue.toString` acquired `MathUtil.round(probability(), 3)`
   (`FORK/…/value/UBooleanValue.java:192-199`). **The record's S8 acceptance criterion
   "1427 entries, 1427 passes, zero expectation edits" (`b7-fix-plan.md:566-568`) is therefore
   unachievable by any correct port** and must be restated as **1427 entries, 1419 passes, 8
   pre-registered departures**.

3. **The 5 error-path entries need no adaptation** — but only because plain USE 7.5.0 *also* emits
   bare, position-less semantic errors. Measured (§5.2): 7.5.0 answers `Sequence{1..'a'}` with
   `Ranges must be of type Integer.`, no `src:line:col:` prefix, from
   `ASTCollectionItem.java:64`'s `new SemanticException(String)`. The fork's four messages use the
   same idiom. **Constraint on the port: do not attach a `SrcPos` to those four throws and do not
   correct their wording** (including the ungrammatical `"Probability must be a Integer or Real"`),
   or all 5 entries break.

---

## 1. Apparatus and provenance

### 1.1 Pins

```
$ md5sum *.in            # .git/reference-repositories/uncertainty/USE-Uncertainty/src/test/org/tzi/use/parser/uncertainty/
fc05dd52b419a575b6e02fc2a606a9aa  UBooleanExpression.in
baf531735dc8963388576acec1244c9d  UCollectionOperations.in
07bb9c9f1aa430abe8723d039dd59ed6  UIntegerExpression.in
b5820bf9f00df2568ebe5efce124b6a1  URealExpression.in

$ md5sum lib/use.jar
8645269c1eacbf8cb52bf7f694c07b21  .../USE-Uncertainty/lib/use.jar

$ java -version
openjdk version "21.0.11" 2026-04-21
```

Fork bytecode is class-file major 51 (Java 7); it runs unmodified on the JDK 21 in this environment.

### 1.2 The two probe harnesses

Both are the same source, `CorpusRun.java`, compiled twice against two classpaths. It replicates
`USECompilerUncertaintyTest`'s `readExpressionLine` (comment/blank skipping, backslash continuation,
`line.substring(3)`) and its two adjudication paths (`result.toStringWithType()`, or the last line of
the captured error stream) exactly, including the `split("\n(\r\n)")` regex and the fork's
reset-only-on-the-error-path buffer discipline. Scratch only, `/tmp`, never in the repo.

```
# fork
L=.git/reference-repositories/uncertainty/USE-Uncertainty/lib
CPF="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar:$L/guava-20.0.jar"

# plain USE 7.5.0
M=~/.m2/repository
CPN="use-core/target/classes:$M/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:\
$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar:$M/jline/jline/2.14.6/jline-2.14.6.jar"

javac -cp "$CPF" -d out CorpusRun.java && java -cp "out:$CPF" CorpusRun *.in
```

**Control that the replication is faithful.** The run was done twice — once resetting the error
buffer after every entry, once resetting only on the error path as the fork does. Verdicts were
identical on all 1427 entries:

```
$ diff <(grep "^M|" corpus-fork.txt) <(grep "^M|" corpus-fork-faithful.txt) && echo "SAME VERDICTS"
SAME VERDICTS
$ grep -c "^M| PASS" corpus-fork-faithful.txt ; grep -c "^M| FAIL" corpus-fork-faithful.txt
1419
8
```

---

## 2. Census part 1 — where the printed form is decided

### 2.1 The one line that differs

```
$ diff FORK/src/main/org/tzi/use/uml/ocl/value/UndefinedValue.java \
       use-core/src/main/java/org/tzi/use/uml/ocl/value/UndefinedValue.java
20,21d19
< // $Id: UndefinedValue.java 1759 2010-09-10 12:32:19Z lhamann $
<
29d26
<  * @version     $ProjectVersion: 0.393 $
46c43
<         return sb.append("Undefined");
---
>         return sb.append("null");
```

Upstream commit `72ab8fd7` (2019-06-27, *"changed Undefined to null"*), already on record at
`spec-parts/15-upstream-delta.md:155-169` and `specification.md:194` (**B6**).

**And it is the only producer of the token.** In the fork, outside the six generated ANTLR lexers
(where `"Undefined"` is the *input* keyword, not output):

```
$ grep -rn '"Undefined"' FORK/src/main --include=*.java | grep -v Lexer
.../uml/ocl/value/UndefinedValue.java:46:        return sb.append("Undefined");
$ grep -rn '"Undefined"' use-core/src/main/java --include=*.java
(no output)
```

Note the corollary: **expression** text already says `null` in *both* trees —
`FORK/…/expr/ExpUndefined.java:53-55` and `use-core/…/expr/ExpUndefined.java:51-53` both
`sb.append("null")`. So the rename is confined to *value* printing; error messages that interpolate
an expression are already identical.

### 2.2 Everything else in the printing path is the same code

| printing machinery | fork | 7.5.0 | verdict |
|---|---|---|---|
| `Value.toString()` / `toStringWithType()` (`" : "` + `getRuntimeType()`) | `value/Value.java:190-208` | `value/Value.java:153-172` | identical text |
| `IntegerValue`, `RealValue`, `StringValue`, `BooleanValue`, `UnlimitedNaturalValue` `toString` | — | — | `diff` shows only `$Id$`/`@version` lines, plus the fork's added `RealValue.valueOf` (E26) |
| `SetValue.toString` (`"Set{"` + `fmtSeqBuffered(getSortedElements(), ",")` + `"}"`) | `SetValue.java:322-326` | `SetValue.java:319-323` | identical |
| `Sequence`/`Bag`/`OrderedSet` `toString` | — | — | identical |
| `CollectionValue.getSortedElements` (`Collections.sort`) | `CollectionValue.java:278-282` | `CollectionValue.java:169-173` | identical |
| `TupleValue.toString` (`"Tuple{"`, `name=value`, position-sorted, `","`) | `TupleValue.java:62-66,130-144` | `TupleValue.java:59-63,127-141` | identical |
| `VoidType.toString` → `"OclVoid"` | — | — | identical |
| all shared `type/*.java` `toString` overrides | — | — | `diff` finds whitespace/javadoc only |

So there is no separator difference, no spacing difference, no sort-order difference and no
type-name difference to census. **INFERRED** consequence: the port inherits 7.5.0's copies unchanged
and adds only the five U/S type and value classes.

### 2.3 The fork's printed grammar for uncertain values (MEASURED)

Probe output, fork jar:

| expression | printed value | printed type | source of the format |
|---|---|---|---|
| `UReal(2, 0.5)` | `UReal(2.0, 0.5)` | `UReal` | `URealValue.java:42-53` — `type()`, `round(v,10)`, `", "`, `round(u,10)`; `-0.0` guarded to `0` at `:44` |
| `UReal(1.23456789012345, 0.5)` | `UReal(1.2345678901, 0.5)` | `UReal` | 10-dp rounding, trailing zeros dropped by `Double.toString` |
| `UInteger(2, 0.5)` | `UInteger(2, 0.5)` | `UInteger` | `UIntegerValue.java:46-54` — value **unrounded** (it is a `long`), uncertainty `round(u,10)` |
| `UInteger(5, 0.123456789012)` | `UInteger(5, 0.123456789)` | `UInteger` | ditto |
| `UBoolean(true, 0.5)` | `UBoolean(true, 0.5)` | `UBoolean` | `UBooleanValue.java:192-199` — `round(p,**3**)` |
| `UBoolean(true, 0.123456)` | `UBoolean(true, 0.123)` | `UBoolean` | **the 3-dp rounding, see §4** |
| `UBoolean(false, 0.25)` | `UBoolean(true, 0.75)` | `UBoolean` | fork normalises to the confidence-of-`true`; **fork semantics, ported as-is** |
| `UString('abc', 0.5)` | `UString('abc', 0.5)` | `UString` | `UStringValue.java:67-72` — hard-coded `"UString('"`, **not** `type()`; confidence **unrounded** |
| `SBoolean(0.5, 0.2, 0.3, 0.5)` | `SBoolean(0.5, 0.2, 0.3, 0.5)` | `SBoolean` | `SBooleanValue.java:124-130` — four components, each `round(x,3)` |
| `Set{UReal(2,0.5), 1, 2.5}` | `Set{1,2.5,UReal(2.0, 0.5)}` | `Set(UReal)` | 7.5.0 container code + fork element code |
| `Set{Set{UReal(2,0.5)}, Set{UReal(1,0.25)}}` | `Set{Set{UReal(1.0, 0.25)},Set{UReal(2.0, 0.5)}}` | `Set(Set(UReal))` | nesting works through the same path |
| `Sequence{Sequence{UInteger(2,0.5)}}` | `Sequence{Sequence{UInteger(2, 0.5)}}` | `Sequence(Sequence(UInteger))` | |
| `Sequence{UReal(1,0.5), 2, 'x', true, Undefined}` | `Sequence{UReal(1.0, 0.5),2,'x',true,Undefined}` | `Sequence(OclAny)` | mixed, incl. nested `Undefined` |
| `Tuple{a:UReal(2,0.5), b:1}` | `Tuple{a=UReal(2.0, 0.5),b=1}` | `Tuple(a:UReal,b:Integer)` | |
| `Set{}` | `Set{}` | `Set(OclVoid)` | |
| `Undefined`, `1/0`, `UReal(1,0.5) / UReal(0,0)` | `Undefined` | `OclVoid` | **the divergence** |

**Trap for the port (READ FROM SOURCE).** `UStringValue.toString` hard-codes the literal
`"UString('"` rather than going through `type()`; `SBooleanValue.toString` goes through
`MathUtil.round`, **not** the vendored library's `String.format("SBoolean(%5.3f, …)")`
(`specification.md:715`). A port that routes either through the library emits space-padded,
locale-sensitive fields. `MathUtil.round` + `Double.toString` is locale-independent; `String.format`
is not.

---

## 3. The crisp control — measuring the difference rather than arguing it

21 expressions containing **no** uncertain value were run through both engines and the raw
`toStringWithType()` outputs diffed. Every construct that the corpus can exercise is represented.

```
$ diff crisp-fork.txt crisp-750.txt
2c2
< WITHTYPE| Undefined : OclVoid
---
> WITHTYPE| null : OclVoid
24c24
< WITHTYPE| Set{Undefined} : Set(OclVoid)
---
> WITHTYPE| Set{null} : Set(OclVoid)
26c26
< WITHTYPE| Sequence{Undefined,1.0} : Sequence(Real)
---
> WITHTYPE| Sequence{null,1.0} : Sequence(Real)
32c32
< WITHTYPE| Undefined : OclVoid
---
> WITHTYPE| null : OclVoid
34c34
< WITHTYPE| Set{Undefined,1} : Set(Integer)
---
> WITHTYPE| Set{null,1} : Set(Integer)
```

The 16 non-diffing lines include `1 : Integer`, `2.5 : Real`, `'abc' : String`, `true : Boolean`,
`Set{1,2.5} : Set(Real)`, `Bag{1.0,2.5} : Bag(Real)`, `OrderedSet{2.5,1.0} : OrderedSet(Real)`,
`Set{Set{1},Set{2}} : Set(Set(Integer))`, `Sequence{Sequence{2}} : Sequence(Sequence(Integer))`,
`Set{} : Set(OclVoid)`, `Tuple{a=2.5,b=1} : Tuple(a:Real,b:Integer)`, `* : UnlimitedNatural`,
`Set{'a','b'} : Set(String)`, `3.5 : Real`, `Set{1,2} : Set(Integer)`.

**Second control, on the corpus itself.** Of the 1427 corpus entries, exactly **11** compile and
evaluate under plain USE 7.5.0 (the rest use U-type syntax 7.5.0 rejects). On those 11 the only
divergence from the recorded expectation is again the one token:

| # | expression | corpus expects | 7.5.0 prints |
|---|---|---|---|
| UBool #15 | `true and false` | `false : Boolean` | `false : Boolean` |
| UBool #20 | `Undefined and Undefined` | `Undefined : OclVoid` | **`null : OclVoid`** |
| UBool #22 | `true and Undefined` | `Undefined : OclVoid` | **`null : OclVoid`** |
| UBool #24 | `Undefined and false` | `false : Boolean` | `false : Boolean` |
| UBool #38 | `true or false` | `true : Boolean` | `true : Boolean` |
| UBool #42 | `Undefined or Undefined` | `Undefined : OclVoid` | **`null : OclVoid`** |
| UBool #44 | `true or Undefined` | `true : Boolean` | `true : Boolean` |
| UBool #46 | `Undefined or false` | `Undefined : OclVoid` | **`null : OclVoid`** |
| UBool #59 | `not Undefined` | `Undefined : OclVoid` | **`null : OclVoid`** |
| UBool #82 | `true xor false` | `true : Boolean` | `true : Boolean` |
| UBool #88 | `Undefined xor Undefined` | `Undefined : OclVoid` | **`null : OclVoid`** |

5 of 11 differ; all 5 by `Undefined`→`null`; 6 of 11 agree byte-for-byte. Note that the `Undefined`
**literal** is still in the 7.5.0 grammar (`OCLBase.gpart:551-552`), so no input-side adaptation is
needed — only output.

---

## 4. Census part 2 — every systematic printing difference, with its corpus count

The corpus records exactly two shapes of expectation: `<value> : <Type>` (1422 entries, from
`Value.toStringWithType()`) and a bare compiler message (5 entries). Distribution of the 1422:

```
$ grep -h '^->' *.in | grep -oE ' : [A-Za-z()]+$' | sort | uniq -c | sort -rn
    527  : Boolean      486  : UReal      210  : UInteger      79  : OclVoid
     72  : UBoolean      21  : Real        13  : Integer       10  : String
      4  : Set(UReal)
```

### D1 — `Undefined` → `null` (**79 entries**) — the B6 offset

```
$ for f in *.in; do printf "%-28s %s\n" "$f" "$(grep -c '^-> Undefined : OclVoid$' $f)"; done
UBooleanExpression.in        16
UCollectionOperations.in     0
UIntegerExpression.in        38
URealExpression.in           25
$ cat *.in | grep -c '^-> Undefined : OclVoid$'
79
```

**79 confirmed** — the record's number (`specification.md:194`, audit-02) reproduces.

**New and load-bearing: all 79 are the *whole* expected string.** Nothing in the corpus expects a
*nested* `Undefined`:

```
$ grep -h '^->.*Undefined' *.in | grep -vc '^-> Undefined : OclVoid$'
0
```

And the fork's *actual* output over all 1427 entries never nests it either:

```
$ grep '^A| .*Undefined' corpus-fork.txt | sort | uniq -c
     79 A| Undefined : OclVoid
```

This is what makes rule **N1** (§6) safe as a whole-string map instead of a substring rewrite.

### D2 — the four bare compiler messages (**5 entries**) — *not* a difference

```
$ grep -n '^->' *.in | grep -v ' : '
UBooleanExpression.in:8:-> Value must be Boolean
UBooleanExpression.in:11:-> Value must be Boolean
UBooleanExpression.in:14:-> Probability must be a Integer or Real
URealExpression.in:62:-> Value must be Integer or Real
URealExpression.in:65:-> Uncertainty must be Integer or Real
```

Fork raw error stream, measured verbatim (`>>>…<<<` delimits the captured buffer):

```
EXPR| UBoolean(3 + 2, 1)      RAW-ERR|>>>Value must be Boolean
<<<
EXPR| UReal('Hola', 9.3)      RAW-ERR|>>>Value must be Integer or Real
<<<
EXPR| 1 + 'a'                 RAW-ERR|>>>probe:1:2: Undefined operation `Integer.+(String)'.
<<<
```

i.e. the fork's *ordinary* semantic errors carry a `src:line:col:` prefix and these four do not,
because they are thrown as `new SemanticException(String)` with a null `SrcPos`
(`SemanticException.java:36-39` — the class is byte-identical in both trees;
`ASTURealLiteral.java:28,31`, and `ASTUBooleanLiteral.java:31` re-wrapping `ExpConstUBoolean`'s
`ExpInvalidException`).

**Measured: 7.5.0 does the same thing.** There are 16 position-less `SemanticException(String)`
sites in 7.5.0's own `src/main` (of 141 total), one of them in the OCL expression parser:

```
$ java -cp "out:$CPN" Probe750 <<< "Sequence{1..'a'}"
ERR| Ranges must be of type Integer.
```

(`ASTCollectionItem.java:64`.) So the position-less form **is** the 7.5.0 way for this class of
error and the policy does not force a change. **Corpus impact: 0 entries, provided the port keeps
the idiom.** See the constraint in §0/3 and the risk in §8-R2.

### D3 — `UBoolean` probability rounded to 3 dp (**8 entries**) — the corpus is stale

**MEASURED: the fork fails 8 of its own 1427 entries.**

```
$ grep -c "^M| PASS" corpus-fork.txt ; grep -c "^M| FAIL" corpus-fork.txt
1419
8
```

| `.in` file:line | expression | corpus expects | **fork actually prints** |
|---|---|---|---|
| `UBooleanExpression.in:52` | `UBoolean(false, 0.55) and UBoolean(true, 0.49)` | `UBoolean(true, 0.2205) : UBoolean` | `UBoolean(true, 0.22) : UBoolean` |
| `UBooleanExpression.in:126` | `UBoolean(false, 0.45) or UBoolean(true, 0.37)` | `UBoolean(true, 0.7165) : UBoolean` | `UBoolean(true, 0.717) : UBoolean` |
| `UCollectionOperations.in:13` | `Set{1, 2, UReal(2,5)}->forAll(e \| e >= 1)` | `UBoolean(true, 0.5792596878) : UBoolean` | `UBoolean(true, 0.579) : UBoolean` |
| `UCollectionOperations.in:16` | `Set{UReal(1, 0.5),UReal(1,0.75), 1.2}->forAll(e \| e >= 1.2)` | `UBoolean(true, 0.1360612114) : UBoolean` | `UBoolean(true, 0.136) : UBoolean` |
| `UCollectionOperations.in:33` | `Set{0, 1, UReal(3, 0.5)}->exists(e \| e >= 3)` | `UBoolean(true, 0.4999999995) : UBoolean` | `UBoolean(true, 0.5) : UBoolean` |
| `UCollectionOperations.in:48` | `…->includes(UReal(2, 0.2))` | `UBoolean(true, 0.5850213691) : UBoolean` | `UBoolean(true, 0.585) : UBoolean` |
| `UCollectionOperations.in:51` | `Set{UReal(2, 0.35), UReal(2, 0.3)}->includes(UReal(2, 0.29))` | `UBoolean(true, 0.9835952315) : UBoolean` | `UBoolean(true, 0.984) : UBoolean` |
| `UCollectionOperations.in:65` | `…->includesAll(Set{2.5, UReal(3.5, 0.15)})` | `UBoolean(true, 0.758018702) : UBoolean` | `UBoolean(true, 0.758) : UBoolean` |

(line numbers are the `->` expected line; the expression is the line above.)

**Cause, read from source, and it is in both the jar and the source tree:**
`FORK/src/main/org/tzi/use/uml/ocl/value/UBooleanValue.java:192-199`

```java
    public StringBuilder toString(StringBuilder sb) {
        return sb.append(type().toString())
                .append("(").append(value()).append(", ")
                .append(MathUtil.round(probability(), 3)).append(")");
    }
```

The 8 expectations carry 4, 9 or 10 decimals; every other `UBoolean` expectation carries ≤3 and is
therefore invariant under the rounding:

```
$ # decimal-digit histogram over the 72 UBoolean expectations
   47 × 1dp    15 × 2dp    2 × 3dp   ||   2 × 4dp   1 × 9dp   5 × 10dp   <- the 8
```

The corpus was recorded before that `round(…, 3)` was introduced. The record notes the rounding
(`spec-parts/10-values.md:183`, `spec-parts/20-ops-UBoolean.md:238-239`) but **nowhere records that
8 corpus entries contradict it.**

**By contrast, the 10-dp rounding used by `UReal`/`UInteger` is invisible to the corpus** — the
deepest number anywhere in an expected string is 10 decimals:

```
$ grep -h '^->' *.in | grep -oE '[0-9]+\.[0-9]+' | awk -F. '{print length($2)}' | sort -n | uniq -c | tail -3
      1 8
      6 9
     51 10
```

which independently reproduces `b7-fix-plan.md` fact **F4**.

### D4 — `Undefined` inside a `String` value (**0 entries**, but a live second-order path)

10 entries assert a `: String` result produced by `toString()` on an uncertain value, e.g.

```
UInteger(5, 0.3).toString()   -> 'UInteger(5, 0.3)' : String
UReal(0.5, 3.2).toString()    -> 'UReal(0.5, 3.2)' : String
```

None of the 10 embeds `Undefined`. But they are a **second, non-obvious site where the U-value
printed grammar is pinned**: any change to `URealValue.toString`/`UIntegerValue.toString` breaks
these 10 in addition to the ~700 direct ones. Worth stating because a "printing-only" change looks
harmless.

### D5 — sort-order coupling of the rename (**0 entries; proven unreachable**)

`Set`/`Bag`/`OrderedSet` printing sorts with `Collections.sort`, and several `compareTo`
implementations fall back to `toString().compareTo(o.toString())` when the argument is of another
class. Renaming the token could therefore in principle move an element. **It cannot, and the reason
is stronger than a probe.**

**Step 1 — the rename really does flip a comparison sign.** Over every distinct element string the
fork actually printed across all 1427 entries (324 distinct), the sign of
`v.compareTo("Undefined")` equals the sign of `v.compareTo("null")` for all but one:

```
distinct element strings checked: 323
sign-flip cases: 1
('false', +1, -1)          # 'f'(102) lies between 'U'(85) and 'n'(110)
first chars present: ["'", '-', '0', '1', '2', '3', 'U', 'f', 't']
```

**Step 2 — but no value class can reach the fallback with an `UndefinedValue` argument.** Read from
source, every `compareTo` in the port's universe short-circuits first:

| class | behaviour on an `UndefinedValue` argument | site |
|---|---|---|
| `BooleanValue` | `return +1` **before** the `toString` fallback | `BooleanValue.java:99-102` |
| `StringValue` | `return +1` before the fallback | `StringValue.java:61-64` |
| `IntegerValue` | `return +1`; fallback is the final `else` | `IntegerValue.java:88-91` |
| `RealValue` | `return +1`; fallback is the final `else` | `RealValue.java:81-84` |
| `UBooleanValue` (fork) | `return +1` before the fallback | `UBooleanValue.java:254-257` |
| `UStringValue` (fork) | `return +1` before the fallback | `UStringValue.java:98-101` |
| `URealValue` (fork) | falls out of the `if`-chain and returns **`0`** — it has **no** `toString` fallback at all | `URealValue.java:95-109` |
| `UIntegerValue` (fork) | same, returns **`0`** | `UIntegerValue.java:94-107` |
| `SBooleanValue` (fork) | `return 0` unconditionally (ledger **M-18**) | `SBooleanValue.java:151-153` |
| `UndefinedValue` (both) | `return -1` against anything not undefined | `UndefinedValue.java:65-73` (fork) / `:62-72` (7.5.0) |

The one sign-flipping string, `false`, is produced by `BooleanValue`, which is in the first row.
**So the rename cannot move any element of any collection the port can print.**

**Step 3 — 12 probes agree.** `Set{false, Undefined}`, `Set{Undefined, false}`,
`Set{true, Undefined}`, `Bag{false, Undefined}`, `Set{false, Undefined, true}`,
`Set{Undefined, 1, false}`, a 41-element `Bag`, an `->asOrderedSet()` pair,
`Set{Undefined,'zzz',false,'AAA'}`, and an `EnumValue` case built directly in Java all put the
undefined element in the **same position** in both engines.

**Corpus exposure is 0 in any case.** Exactly one collection literal in the corpus mentions
`Undefined`, and it is a singleton whose result is a `UBoolean`, not the set:

```
$ grep -n "Set{\|Sequence{\|Bag{\|OrderedSet{" *.in | grep Undefined
UCollectionOperations.in:56:Set{Undefined}->includes(UReal(2, 3))
```

**Caveat for a widened corpus, not for this one.** `URealValue.compareTo(UndefinedValue)` returns
`0` while `UndefinedValue.compareTo(URealValue)` returns `-1` — non-antisymmetric, and Java 21's
TimSort raises `IllegalArgumentException: Comparison method violates its general contract` on a
large enough mixed collection. That is a *pre-existing* fork hazard (same family as ledger rows
**M-9**, **M-18**), it is not caused by the rename, and it is why N1-G (§6.1) refuses to guess
rather than substring-replacing.

### D6 — things that are *not* differences (checked so they need no rule)

* Encoding/whitespace: all 1427 expected lines are pure ASCII, no CR, no trailing whitespace.
  ```
  $ grep -h '^->' *.in | LC_ALL=C grep -c '[^ -~]'   ->  0
  $ grep -hc '^->.*[[:space:]]$' *.in                ->  0 0 0 0
  $ for f in *.in; do grep -c $'\r' $f; done         ->  0 0 0 0
  ```
  (The only non-ASCII bytes in the corpus are on `# Creación` comment lines, skipped by the reader.)
* `Infinity` / `NaN` / `-0.0`: never appear in an expected *or* an actual string
  (`grep -c "Infinity\|NaN" corpus-fork.txt` → `0`).
* Locale: the whole printing path is `StringBuilder.append(double)` = `Double.toString`, which is
  locale-independent. No `String.format` on the live path in either tree (see the SBoolean trap,
  §2.3).
* `UString` / `SBoolean`: `grep -c 'UString\|SBoolean' *.in` → `0` in all four files. Their printed
  forms are unconstrained by the corpus and must be evidenced by purpose-built tests.

---

## 5. Normalisation is needed for exactly one of those

| difference | entries | mechanism |
|---|---|---|
| D1 `Undefined`→`null` | **79** | **normalise** (rule N1) |
| D2 bare compiler messages | 5 | **no rule** — 7.5.0 emits the same shape; a *constraint* on the port instead (C1) |
| D3 `UBoolean` 3-dp rounding | **8** | **no rule** — pre-registered departure (§7); normalising here would hide real error |
| D4 `toString()` results | 10 | no rule; pinning constraint (C2) |
| D5 sort order | 0 | no rule; guard assertion (N1-G) |
| D6 encoding etc. | 0 | none |

---

## 6. The normalisation rules

Design invariants that apply to all rules:

* **Normalise the *expectation*, never the actual output.** The port's raw
  `toStringWithType()` is what the assertion message must show, so a reviewer always sees what the
  port really printed.
* **Every rule is a total function with a pinned firing count.** If a rule fires a number of times
  other than its declared count, the harness **fails the run** rather than proceeding. A rule that
  silently stops firing (or starts firing more) is the exact failure mode "hiding a divergence"
  takes.
* **No rule may be a substring rewrite** unless its narrowness is separately proven.

### 6.1 N1 — the B6 rename

```
    /** Exactly one rule, applied to the whole expected string, before comparison. */
    static String normalise(String expected) {
        if (expected.equals("Undefined : OclVoid")) return "null : OclVoid";
        if (expected.contains("Undefined"))
            throw new AssertionError("N1 guard: unhandled 'Undefined' in expectation: " + expected);
        return expected;
    }
```

plus, at end of run:

```
    assertEquals(79, n1Fired, "N1 must fire exactly 79 times");
```

**Why this is narrow — four independent reasons, each checkable:**

1. **Anchored to the complete string, not a substring.** It cannot touch a nested occurrence, and
   the `contains` guard makes a nested occurrence a hard error rather than a silent pass. Measured:
   0 of 1427 expectations contain `Undefined` in any other position (§4/D1).
2. **The type suffix must match too.** A port that prints the right text with the wrong runtime type
   (`null : Void`, `null : OclAny`) still fails. This is not cosmetic: `getRuntimeType()` is what
   B7's M-37 discussion turns on.
3. **The rule's justification is a one-line source diff**, not a behavioural argument (§2.1). The
   entire 7.5.0 tree contains no other producer of the token and the fork contains no other producer
   outside the input lexers.
4. **Cardinality is pinned at 79**, a number that reproduces from three independent greps (the
   corpus text, the fork's actual outputs, and the record's audit-02 count).

**How a reviewer tells this apart from hiding a divergence.** Three mutation controls, each of which
must turn the suite red:

| mutation | must produce |
|---|---|
| revert `UndefinedValue.toString` in the port to `"Undefined"` | 79 failures (N1 maps expected→`null`, actual says `Undefined`) |
| make one `Undefined`-expecting operation return a defined value instead | ≥1 failure — N1 rewrites text, never the type or the value |
| change N1 to `expected.replace("Undefined","null")` | must be rejected at review: it would also silently rewrite a *nested* occurrence, which is exactly the D5 order-flip blind spot |

**N1-G — the widening guard.** The `contains` branch above is the guard. If any future corpus entry
expects `Set{Undefined,…}`, N1 refuses to guess and the run fails; a human must then decide, with
D5 in hand, whether the element's *position* also moves.

### 6.2 C1 — constraint, not a rule: the 5 error-path entries

No normalisation. Instead, three properties the port must hold, each independently testable:

* **C1a** The four messages are byte-exact, **including** `"Probability must be a Integer or Real"`
  with its missing `n`. Grammar-tidying breaks 3 entries. Pin them as string constants in one place
  with a comment pointing at this section.
* **C1b** They are thrown as `new SemanticException(msg)` **with no `SrcPos`/`Token`** — the
  7.5.0-idiomatic form for this class of error (16 such sites upstream, incl.
  `ASTCollectionItem.java:64`, measured in §4/D2). Attaching a position prefixes `probe:1:0: ` and
  breaks all 5.
* **C1c** They must remain the **only** thing written to the error stream for those five
  expressions. The harness captures a buffer, not a line (B7 row **M-49b**); a stray `Warning:`
  line — the fork emits those, e.g. `probe:1:14: Warning: application of 'foo' to a single value
  should be done with '.' instead of '->'` — would be concatenated in. Measured today: each of the
  five buffers contains exactly one line.

Recommended S8 evidence: capture the five raw buffers verbatim as a committed fixture *before*
fixing M-49b's `split("\n(\r\n)")`, then assert the fixed selector picks the same five strings.

### 6.3 C2 — constraint: the 10 `toString()` entries

`UReal`/`UInteger` printed grammar is asserted twice — directly (696 entries) and quoted inside a
`StringValue` (10 entries). Any change to those `toString` bodies must be justified against both
populations. No normalisation.

### 6.4 Rules deliberately **not** written

| tempting rule | why it is refused |
|---|---|
| round the expected `UBoolean` probability to 3 dp before comparing | would make a port whose probability is wrong in the 4th decimal pass. The 8 entries are pre-registered instead (§7), each pinned to a **measured** replacement string, so a wrong probability still fails |
| numeric-tolerance comparison of `UReal` components | the corpus asserts *strings*; the fork's own rounding is the tolerance. A numeric epsilon would mask F-2 (`MathUtil.round` saturation above 9.2e8) and any 10-dp regression |
| strip the `" : Type"` suffix and compare values only | destroys the only signal the corpus carries about the type lattice (B5) and about M-37 |
| trim/normalise whitespace inside `Set{…}` | measured unnecessary (§2.2) and would mask a separator regression |
| `expected.replace("Undefined","null")` | see N1 reason 1 / D5 |

---

## 7. Pre-registered intended departures — entries that can never pass

**Total: 8 certain, +1 UNVERIFIABLE candidate.** Recorded here so that when S8 runs, these are
*expected* failures rather than discovered ones (`b7-fix-plan.md` §0.1).

### 7.1 The 8 stale `UBoolean` entries — **corpus staleness, not B7**

Listed in §4/D3 with their measured replacement strings. Register each as an
`(file, line, recordedExpectation, portExpectation, provenance)` tuple where `portExpectation` is
the **measured fork-jar output**, not a wildcard.

**Cause is not any B7 fix.** No ledger row touches `UBooleanValue.toString`; the rounding is present
in the fork's own `src/main` *and* its shipped jar, and the corpus contradicts both. This is a
distinct category from B7 and must not be filed under it.

**One decision remains for the user, and it is not mine to take.** Two self-consistent readings of
the policy exist:

* **(a) keep the rounding** — "uncertainty meaning comes from the fork" and the fork's *code* rounds
  to 3. 8 entries become pre-registered departures. The fork's own unit tests
  (`FORKTEST/uml/ocl/value/UBooleanValueTest.java:22,28,32`, which pin `"UBoolean(true, 0.0)"`,
  `"UBoolean(true, 0.5)"`, `"UBoolean(true, 1.0)"`) pass either way.
* **(b) drop the rounding** — the corpus, being the larger oracle, is taken as the fork's intent; 8
  more entries pass, no fork unit test breaks, but 72 `UBoolean` expectations are then being
  satisfied by a printer that exists in neither the fork's source nor its jar, and every
  ≤3-dp expectation would have to be re-verified at full precision.

**Recommendation: (a).** It is the reading in which every claim is backed by executable fork
behaviour. But (b) is defensible and the choice must be recorded, once, as a decision — not
re-litigated per entry.

### 7.2 B7-driven departures: **0 certain, 1 UNVERIFIABLE**

Independently re-checked against `b7-fix-plan.md` §3.1 from the printing/corpus side:

| B7 row | claimed corpus impact | my check |
|---|---|---|
| M-49b (5 error entries) | "5, the one genuinely dangerous row" | **the 5 *expectations* do not move.** Measured: 7.5.0 emits the same bare messages (§4/D2), so no text changes. What changes is the *selector* that picks the message out of the buffer. **0 departures**, given C1 |
| M-31 / M-32 (`URealExpression.in:62,65`) | 2 at risk *if* the check moves | recommendation is not to move it; C1b makes that binding. **0** |
| F-4 (`URealExpression.in:979,982,985`) | 3, widening-safe, all expect `true` | agreed; a widening cannot turn `true` into `false`. **0** |
| M-29 (`UBooleanExpression.in:7-14`) | 0 — the two candidates are compile errors | confirmed: entries at `:8` and `:11` are error-path (§4/D2) and never reach `eval`. **0** |
| M-28 (`UBooleanExpression.in:19,23,27`) | 0 as recommended (no rewrite) | those three expect `-> Undefined : OclVoid` and are inside D1's 79, so they are additionally covered by N1. **0** |
| M-37 (9 `.value()` entries) | 0 — suffix is the *runtime* type | confirmed from `Value.java:204-208` (fork) / `:168-172` (7.5.0): `toStringWithType` calls `getRuntimeType()`, and the two files are identical here. **0** |
| M-38 (`UBooleanExpression.in:143`) | 0 — shadowed by `Op_boolean_or` | measured: entry `Undefined or Undefined` evaluates (no NPE) and prints `Undefined : OclVoid` on the fork jar. **0** |
| **M-10** (`UIntegerExpression.in:1304`) | **UNVERIFIABLE, 1 candidate** | `( UInteger(2, 3) / 1 ).equals( UInteger(2, 3).toUReal() )` → `-> true : Boolean`. It **passes on the fork today** (measured). Whether the M-10 fix perturbs it depends on the static type of `UInteger / Integer`. Settles by the experiment already written at `b7-fix-plan.md:539`. **Register as a watch item, not a departure** |

So: **no B7 fix is predicted to change any of the 1427 expected strings** — which independently
reproduces `b7-fix-plan.md:565-566` from a different direction (running the corpus rather than
reading the ledger).

### 7.3 Restated S8 acceptance criterion

> 1427 entries; **1419** passes; **79** adjudicated through rule N1 with `n1Fired == 79`; **5**
> adjudicated through the error path with the four message constants unmodified; **8**
> pre-registered departures, each pinned to its measured fork-jar string; **0** expectation edits in
> the `.in` files themselves (the departures live in the register, not in the corpus, so the four
> files keep the md5s in §1.1).

---

## 8. Residual risks and gaps

* **R1 — the 8 departures depend on an unmade decision** (§7.1(a) vs (b)). Until it is taken, the
  S8 number is either 1419 or 1427 and the two are not interchangeable.
* **R2 — C1b is a constraint on code nobody has written yet.** Nothing mechanically prevents an
  implementer from "improving" the four throws into positioned exceptions; the 5 entries would then
  fail with a *plausible-looking* prefix. Mitigation: pin the four strings and add one test that
  asserts the captured buffer has **no** `probe:` prefix.
* **R3 — D5 is closed for the *present* value set only.** The unreachability argument in §4/D5
  enumerates ten `compareTo` implementations. It has to be re-run if a sixth uncertain value class,
  or any class without an explicit `UndefinedValue` branch and *with* a `toString` fallback, is
  added. N1-G is the standing guard.
* **R4 — `UString` and `SBoolean` printed forms have zero corpus coverage** (`grep -c` = 0 in all
  four files). Under B2 (SBoolean in full, 39 operations) their printed grammar is asserted by
  nothing. `SBooleanValue.toString`'s 3-dp rounding and its divergence from the library's
  `String.format` (§2.3) are exactly the kind of thing that only a purpose-built printing test can
  catch.
* **R5 — UNVERIFIABLE: whether the fork's shipped `use.jar` is the build of the fork's
  `src/main`.** I did not decompile-and-diff. It does not affect any conclusion here, because the
  3-dp rounding is present in **both** (jar: measured `UBoolean(true, 0.579)`; source:
  `UBooleanValue.java:192-199` read verbatim), and the crisp control §3 diffed *source* files as
  well as running the jar.
* **R6 — the fork was run on JDK 21, not its contemporary JDK 7/8.** Every difference reported here
  is a string produced by `Double.toString`/`StringBuilder.append`, whose contract has not changed;
  but the 1419/8 split has not been reproduced on an older JVM.

---

## 9. Reproduction

```bash
FORK=/home/xoruser/msc-4/use-msc2026/.git/reference-repositories/uncertainty/USE-Uncertainty
D=$FORK/src/test/org/tzi/use/parser/uncertainty
L=$FORK/lib
CPF="$L/use.jar:$L/atenearesearchgroup.uncertainty.jar:$L/antlr-3.4-complete.jar:$L/guava-20.0.jar"
M=~/.m2/repository
CPN="/home/xoruser/msc-4/use-msc2026/use-core/target/classes:\
$M/org/antlr/antlr-runtime/3.5.3/antlr-runtime-3.5.3.jar:\
$M/com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar:\
$M/jline/jline/2.14.6/jline-2.14.6.jar"

# corpus against the fork  -> 1419 PASS / 8 FAIL
javac -cp "$CPF" -d /tmp/of CorpusRun.java && java -cp "/tmp/of:$CPF" CorpusRun $D/*.in
# corpus against plain 7.5.0 -> 11 evaluable, 5 pass, 6 differ by Undefined/null
javac -cp "$CPN" -d /tmp/on CorpusRun.java && java -cp "/tmp/on:$CPN" CorpusRun $D/*.in
```

`CorpusRun.java` in full — a mechanical transcription of
`USECompilerUncertaintyTest.testUncertaintyExpression` + `readExpressionLine` + `executeExpression`,
emitting one four-line record per entry (`E|` expression, `X|` recorded expectation, `A|` actual,
`M|` verdict). It compiles unchanged against both classpaths, which is itself evidence that the two
`OCLCompiler`/`Evaluator`/`Value` APIs are source-compatible:

```java
import org.tzi.use.config.Options;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystem;
import java.io.*;

public class CorpusRun {
    static class SOS extends OutputStream {                 // == the test's StringOutputStream
        StringBuilder b = new StringBuilder();
        public void write(int x) { b.append((char) x); }
        public void reset() { b = new StringBuilder(); }
        public String toString() { return b.toString(); }
    }
    static String expression, expected;

    static boolean read(BufferedReader in) throws IOException {   // == readExpressionLine
        expression = null; expected = null;
        StringBuilder eb = new StringBuilder();
        String line = in.readLine();
        while (line != null && (expression == null || expected == null)) {
            line = line.trim();
            if (line.length() != 0 && !line.startsWith("#")) {
                if (expression == null) {
                    if (line.startsWith("->")) throw new RuntimeException("missing expression");
                    if (!line.endsWith("\\")) { eb.append(line); expression = eb.toString().replace("\t"," "); }
                    else eb.append(line.substring(0, line.length()-2) + "\n");
                } else {
                    if (!line.startsWith("->")) throw new RuntimeException("missing expected result line");
                    expected = line.substring(3);
                }
            }
            if (expected == null) line = in.readLine();
        }
        return !(expected == null || expression == null);
    }

    public static void main(String[] args) throws Exception {
        MModel model = new ModelFactory().createModel("Test");
        Options.explicitVariableDeclarations = false;
        SOS sos = new SOS();
        PrintWriter pw = new PrintWriter(sos);
        for (String f : args) {
            BufferedReader in = new BufferedReader(new FileReader(f));
            int n = 0;
            while (read(in)) {
                n++;
                Value result = null; String exc = null;
                try {
                    Expression e = OCLCompiler.compileExpression(model,
                        new ByteArrayInputStream(expression.getBytes("UTF-8")), "probe", pw, new VarBindings());
                    if (e != null) result = new Evaluator().eval(e, new MSystem(model).state());
                } catch (Throwable t) { exc = t.toString(); }
                pw.flush();
                String actual;
                if (result != null) actual = result.toStringWithType();
                else if (exc != null) actual = "<<EXC>> " + exc;
                else {                                        // == the test's error path, verbatim
                    String[] a = sos.toString().split("\n(\r\n)");
                    actual = a[a.length-1].replace("\n","").replace("\r","");
                    sos.reset();
                }
                System.out.println("@@ " + new File(f).getName() + " #" + n);
                System.out.println("E| " + expression.replace("\n","\\n"));
                System.out.println("X| " + expected);
                System.out.println("A| " + actual);
                System.out.println("M| " + (expected.equals(actual) ? "PASS" : "FAIL"));
            }
            in.close();
        }
    }
}
```
