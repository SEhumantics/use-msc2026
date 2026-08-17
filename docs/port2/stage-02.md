# S2 — The port specification

Branch `port-uncertainty-2`. Written 2026-08-17; this V&V record appended the same day, after the
S0–S2 audit.

**No Maven was run for S2 or for this record.** S2 is a documentation stage: its deliverable is a
specification derived by reading two source trees and a vendored jar. Every command in this file is
`git`, `grep`, `sed`, `find`, `wc` or `python3`, and every figure below was produced by re-running
the command shown, not copied from the document being verified.

**Nothing under `*/src/main/*` was touched by S2** —
`git diff --name-status 30d480db..HEAD -- '*/src/main/*'` is empty. No upstream test was edited.

---

## 1. What S2 produced

| Artefact | Size | Commit |
|---|---|---|
| `docs/port2/specification.md` | 2 640 lines | `8c410c98` (217 lines, truncated) → `aeb4d860` (+2 293, completed) → corrections in this stage's follow-up |
| `docs/port2/spec-parts/` — **15 section files + 1 oracle script** | 12 684 lines | `37e240b9` |
| `docs/port2/upstream-test-waivers.md` | 60 lines | `ccd2d58d` (zero waivers through S2) |

```bash
$ ls -1 docs/port2/spec-parts/
10-values.md
11-types-oracle.sh
11-types.md
12-expressions.md
13-grammar.md
14-historical-tests.md
15-upstream-delta.md
16-modernization-ledger.md
17-refutation-classification.md
18-refutation-delta.md
19-open-questions.md
20-ops-SBoolean.md
20-ops-UBoolean.md
20-ops-UInteger.md
20-ops-UReal.md
20-ops-UString.md
$ wc -l docs/port2/specification.md
2640 docs/port2/specification.md
```

`specification.md` is the deliverable S3–S8 are executed from; the 15 section files are its working
papers and are retained because the synthesis compresses them. It carries: an inventory of every
uncertainty-touching class (33 new files, 26 upstream `.java` edits, 1 grammar resource edit), five
per-type operation tables, a 121-pair type lattice, the 7.5.0 shape delta, the grammar surface, the
test-oracle inventory, a 96-row modernization ledger with its 33 behaviour-changing rows separated
out, **12 blocking decisions** a human must answer before S3, and a residual-risk section
collecting every `UNVERIFIABLE`.

---

## 2. How it was verified

Three independent mechanisms, none of which trusts the extractor's own arithmetic.

### 2.1 Extractor / refuter, per type — five separate re-derivations

Each of the five per-type operation tables was produced by an **extractor** reading the historical
registry file, and then **independently re-derived by a refuter who had not seen the extractor's
table**. The two were reconciled row by row and every disagreement is printed in place in
`specification.md` §2.1–§2.5 rather than smoothed away.

**On the counts, all five agreed** (`specification.md` §2.0):

| Type | Registrations | Classes | Distinct OCL names | Extractor | Refuter | Adopted |
|---|---|---|---|---|---|---|
| `UBoolean` | 14 | 14 | 14 | 14 | 14 | **14** |
| `UReal` | 18 | 18 | 18 | 18 | 18 | **18** |
| `UInteger` | 13 | **12** | 13 | 12 cls / 13 reg | 12 cls / 13 reg | **12 / 13** |
| `UString` | **22** | **21** | 21 | 21 cls / 22 reg | 21 cls / 22 reg | **21 / 22** |
| `SBoolean` | 39 | 39 | 39 | 39 | 39 | **39** |

The disagreements were **semantic, not numeric**, and that is where the value was: the refuter
overturned the extractor on `SBoolean.xor` of two vacuous opinions (`a = |a₁ − a₂|` is not
constrained by `u == 1`), on the `deduceY` divisor list (**both** directions wrong — one claimed
hazard is guarded, one real hazard at L381 was missed), on the eight `deduceY` case blocks being
**sequential `if`s, not `else if`** — so a bug-compatible port must preserve their source order —
and on two type-inconsistent `K` numerators that read as transcription errors in the oracle. It
also corrected two fabricated identifiers (`Op_enum_toString`, `Op_sBoolean_toString`, both of which
grep to nothing) and the claim that `UReal`/`UInteger` register `confidence`/`setConfidence` (they
register `uncertainty`/`setUncertainty`; only `UBoolean` uses the other pair).

### 2.2 Citation audit — 185 citations adjudicated against source

`docs/port2/audit-02-specification.md` (543 lines, commit `3cb92468`) extracted **218**
`File.ext:NN` tokens mechanically and adjudicated **185 distinct ones** against the actual files:
100 % of §0 (blocking decisions), 100 % of §1 (inventory), 100 % of §7 (modernization ledger), plus
all of §3.1/§3.2, §3.5, §5.5/§5.6, §8.1 and a sweep of §2/§4/§6/§10.

**Verdict SOUND_WITH_CAVEATS, hit rate 175/185 = 94.6 %** (174/196 = 88.6 % under the strict reading
that counts §3.1's cite-the-method-quote-the-body convention as 11 misses).

Four blocking decisions were re-derived from scratch and all four came back **CONFIRMED**: B5 (the
`TypeTest#testSupertype` conflict — 10 of 12 assertions break, 2 survive), B6 (exactly 79 corpus
entries expecting `-> Undefined : OclVoid`), B8 (`Op_number_sqrt`/`Op_number_pow` shadowing, every
link in the chain), B11 (the `UnlimitedNatural` lattice inconsistency **and** its claim that the same
shape of defect pre-exists upstream). **No decision was refuted.**

The inventory was checked for **completeness**, not just correctness, by two reconstructions that do
not reuse the document's keyword filter: a reference graph over the 33 new fork classes (13 common
files reference them; all 13 are in the E-list) and an API-gap test over every static call those 33
files make into upstream (exactly two members absent from 7.5.0 — `MathUtil.round` and
`RealValue.valueOf` — which are exactly E25 and E26).

The one MAJOR finding is **editorial**: 163 of the 218 citations (75 %) are bare basenames with no
tree alias, and 40+ of those resolve in *both* trees with different content at the cited line.

### 2.3 Corrections applied

All ten misses have been corrected in place in `specification.md` and are marked "**corrected**"
where they occur:

| Miss | Was | Is |
|---|---|---|
| M1–M4 (**B4**, twice at §0 and twice at §5.5) | `…/imports/t133_import_*.use` | `use-gui/src/it/resources/testfiles/shell/imports/…` — the `shell/` segment was missing |
| M5 (twice) | `11-types.md:747` | `11-types.md:711` (`:747` is a blank line; `:70-76` is the correct fuller treatment) |
| M6 | `URealType` ctor `:9` | `F/uml/ocl/type/URealType.java:8` |
| M7 (**B1a**) | `HistoricalOracleIsolationTest.java:69-70` | `TT/…:70-71` |
| M8 | `ExpDefSBoolean.java:16-17` | `F/uml/ocl/expr/ExpDefSBoolean.java:15-16` |
| M9 | §7.2 header "Expression / parser layer **(7)**" | **(8)** |
| M10 | §7.2 header "Test-harness layer **(7)**" | **(6)** |

Each was re-verified here before being applied — e.g.

```bash
$ sed -n '747p' docs/port2/spec-parts/11-types.md | cat -A
$
$ sed -n '711p' docs/port2/spec-parts/11-types.md
3. **`UIntegerType()` constructor visibility** — fork has it `public`, all siblings package-private.
$ grep -n 'protected URealType' .../USE-Uncertainty/src/main/org/tzi/use/uml/ocl/type/URealType.java
8:    protected URealType() {
$ sed -n '69,71p' use-core/src/test/java/org/tzi/use/uncertainty/differential/HistoricalOracleIsolationTest.java
                "the parent supplies java.* only");
        assertTrue(IsolatedJarClassLoader.isIsolated("org.tzi.use.uml.ocl.value.URealValue"));
        assertTrue(IsolatedJarClassLoader.isIsolated("uDataTypes.UReal"));
```

M9/M10 cancel to the correct grand total of 33, re-confirmed after the edit:

```
  4 rows under ### Compile-forced but behaviour-changing (4)
 11 rows under ### Value-layer defects — reproducing vs fixing (11)
  2 rows under ### Type-layer (2)
  8 rows under ### Expression / parser layer (8)
  2 rows under ### Operations layer (2)
  6 rows under ### Test-harness layer (6)
TOTAL 33
```

Two further corrections beyond the ten: §4.3's `GenerateHTMLExpressionVisitor` row now also lists
`toString` (`:45`), and §3.1's eleven-`conformsTo` block now states its off-by-one convention
explicitly. The six `TypeTest.java` and three `StandardOperationsNumber.java` citations — the ones
**B5** and **B8** turn on — have been prefixed with their tree aliases; the remaining ~150 bare
basenames have **not**, and that is now recorded as residual risk R0b.

---

## 3. Acceptance — the stated criteria, measured

> S2's stated acceptance: *"Every row cites a historical file and symbol. Operation counts per type
> are stated and reproducible by a grep the report gives."*

### 3.1 Criterion 1 — "every row cites a historical file and symbol": **PARTIALLY MET**

Measured, not asserted:

```bash
$ python3 - <<'PY'
import re
L=open('docs/port2/specification.md').read().split('\n')
def span(a,b):
    s=next(i for i,l in enumerate(L) if l.startswith(a))
    e=next(i for i,l in enumerate(L) if i>s and l.startswith(b))
    return L[s:e]
def rows(ls):
    return [l for l in ls if l.startswith('|')
            and not re.match(r'^\|[\s\-:|]+\|$', l)
            and not re.match(r'^\|\s*#\s*\|', l)
            and not l.startswith(('| Target path','| Alias','| Type |','| Claim'))]
cite=re.compile(r'\.(java|gpart|g|use|in|md|sh|xml|jar)`?:\d|\.java`|`[A-Za-z_]+\(\)`|:\d+')
for name,a,b in [("§1 inventory","# 1. Inventory","# 2. Per-type"),
                 ("§2 operation tables","# 2. Per-type","# 3. Type lattice"),
                 ("§7 ledger","# 7. Modernization","# 8. Open questions")]:
    r=rows(span(a,b)); w=[x for x in r if cite.search(x)]
    print(f"{name}: {len(r)} table rows, {len(w)} carry a file/symbol citation ({100*len(w)//len(r)}%)")
PY
§1 inventory: 57 table rows, 54 carry a file/symbol citation (94%)
§2 operation tables: 136 table rows, 29 carry a file/symbol citation (21%)
§7 ledger: 96 table rows, 68 carry a file/symbol citation (70%)
```

**The §2 figure is not the failure it looks like, and saying so honestly matters.** §2's tables cite
the **file once per table**, in the runnable grep block that opens each subsection, and each row
then names the **symbol** — the operation. Measured on that reading:

```bash
$ python3 - <<'PY'
import re
L=open('docs/port2/specification.md').read().split('\n')
s=next(i for i,l in enumerate(L) if l.startswith('# 2. Per-type'))
e=next(i for i,l in enumerate(L) if i>s and l.startswith('# 3. Type lattice'))
tot=named=0
for l in L[s:e]:
    m=re.match(r'^\| *[0-9][0-9,–\-/ ]* *\| *(.+?) *\|', l)
    if m:
        tot+=1
        if re.search(r'`[A-Za-z_][A-Za-z0-9_]*`', m.group(1)): named+=1
print(f"numbered operation rows in §2: {tot}; rows naming a backticked symbol: {named}")
PY
numbered operation rows in §2: 85; rows naming a backticked symbol: 82
```

The three that do not are `+`, `< <= > >=` (operator glyphs — named, just not identifiers) and one
aggregate row, "28–35 | 8 collection fusions", which is the only genuinely unnamed row in §2.
Effectively **84 of 85**.

So: the criterion is met at **table** granularity for §2, at **row** granularity for §1 (94 %) and
§7 (70 %, where the uncited rows are policy rows such as "decide one policy first" rather than
findings). It is **not** met at row granularity everywhere, and the independent citation audit puts
the accuracy of the citations that do exist at **94.6 %**, with the residual 5.4 % now corrected.
**Recorded as partially met, not as met.**

### 3.2 Criterion 2 — "operation counts reproducible by a grep the report gives": **MET**

Every grep printed in `specification.md` §2.1–§2.5 was re-run against the fork tree. **All five
reproduce their stated counts exactly.**

```bash
cd .git/reference-repositories/uncertainty/USE-Uncertainty/src/main/org/tzi/use/uml/ocl/expr/operations

# UBoolean — spec §2.1 says 14 / 14 / 14
F=StandardOperationsUBoolean.java
grep -c 'OpGeneric\.registerOperation(new Op_uBoolean_' $F
grep -c '^final class Op_uBoolean_' $F
grep -o 'return "[a-zA-Z]*";' $F | sort -u | wc -l

# UReal — spec §2.2 says 18 / 18 / 18, all OPERATION, none infix
F=StandardOperationsUReal.java
grep -cE '^[[:space:]]*OpGeneric\.registerOperation\(new Op_ureal_[A-Za-z]+\(\), opmap\);$' $F
grep -cE '^final class Op_ureal_[A-Za-z]+ extends OpGeneric \{$' $F
grep -oP '(?<=return ")[^"]+(?=";)' $F | sort -u | wc -l
grep -c 'return OPERATION;' $F
grep -c 'return false;' $F

# UInteger — spec §2.3 says 12 classes / 13 registrations
F=StandardOperationsUInteger.java
grep -c '^final class Op_uInteger_[A-Za-z]* extends OpGeneric {$' $F
grep -c 'OpGeneric.registerOperation(' $F
grep -c 'return OPERATION;' $F

# UString — spec §2.4 says 21 classes / 22 registrations, uConcat registered twice
F=StandardOperationsUString.java
grep -cE '^final class Op_uString_[A-Za-z_]+ extends OpGeneric \{$' $F
grep -cE 'OpGeneric\.registerOperation\(new Op_uString_' $F
grep -c 'return OPERATION;' $F
grep -oE 'new Op_uString_[A-Za-z_]+' $F | sort | uniq -c | sort -rn | head -1

# SBoolean — spec §2.5 says 39 / 39 / 39 distinct, and warns the naive grep returns 45
F=StandardOperationsSBoolean.java
grep -cE '^\s+[A-Z][A-Z_0-9]*\(new OpGeneric\(\) \{' $F
grep -cE '^ {8}public String name\(\) \{' $F
grep -oE '^ {12}return "[a-zA-Z]+";' $F | sort -u | wc -l
grep -c 'new OpGeneric()' $F
```

Pasted output, in the order the commands appear above (the `echo` markers are mine; the numbers are
the commands' own stdout):

```
14                              <- UBoolean registrations
14                              <- UBoolean classes
14                              <- UBoolean distinct names
-- UReal
18                              <- registrations
18                              <- classes
18                              <- distinct names
18                              <- all OPERATION
18                              <- none infix/prefix
-- UInteger
12                              <- classes
13                              <- registrations   (one more than classes)
12                              <- OPERATION
-- UString
21                              <- classes
22                              <- registrations   (one more than classes)
21                              <- OPERATION
      2 new Op_uString_uConcat  <- the duplicate
-- SBoolean
39                              <- enum constants
39                              <- name() methods
39                              <- distinct names
45                              <- the naive grep, as the spec warns
```

Every stated number reproduces, including the three **asymmetries the document flags as defects
rather than hiding**:

* `UInteger` 12 classes but 13 registrations — `Op_uInteger_value` is registered twice, under
  `value` and under the alias `toInteger`.
* `UString` 21 classes but 22 registrations — `Op_uString_uConcat` is registered at lines 19 **and**
  21; the duplicate is dead but harmless (`ArrayListMultimap` + first-match `create`).
* `SBoolean` — the naive `grep -c 'new OpGeneric()'` returns **45**, because it counts the six
  commented-out constants. The document prints this trap next to the correct grep. Confirmed: 45.

**One honest defect in the greps themselves.** Only §2.1's block is copy-pasteable as written; §2.2,
§2.3 and §2.4 open with `F=…/StandardOperations*.java` and §2.5 with `cd …/uml/ocl/expr/operations`,
where `…` is an ellipsis, not a path. They reproduce only after substituting the `F/` alias from the
document's own alias table. That is a one-token-per-block editorial defect, not a wrong count, and it
is why this section prints the resolved commands in full.

### 3.3 Verdict

| Criterion | Verdict |
|---|---|
| Every row cites a historical file and symbol | **PARTIALLY MET** — 94 % (§1) / 84-of-85 by symbol (§2) / 70 % (§7); audited citation accuracy 94.6 %, all ten misses now corrected |
| Operation counts stated and reproducible by a grep the report gives | **MET** — all five reproduce exactly; four of the five grep blocks need one path substitution first |
| *(implicit)* No `*/src/main/*` change, no upstream test edited | **MET** — `git diff --name-status 30d480db..HEAD -- '*/src/main/*'` empty |
| *(implicit)* Blocking decisions surfaced rather than taken unilaterally | **MET** — 12 decisions in §0 + §9, none of them refuted by the independent audit |

**S2 is accepted with the citation-granularity caveat recorded.** It was not accepted on the
extractor's word: the counts were re-derived five times independently and the citations audited at
94.6 %.

---

## 4. Residual risk

1. **The instrument cannot verify most of this document. `specification.md` C3.** The differential
   harness addresses one package (`org.tzi.use.uml.ocl.value.`). §3's entire type lattice,
   `uDataTypes` (**B1**), and all 39 SBoolean operations (**B2**) are structurally outside it, as
   are decisions **B5**, **B8** and **B11**. No later stage may report that work as "differentially
   verified"; it needs a different oracle, most plausibly the revived upstream tests under **B3**.
2. **~150 bare-basename citations remain (R0b).** The nine that B5 and B8 turn on are now aliased;
   the rest are not. 40+ resolve in both trees with different content at the cited line. Resolve
   from context; where context does not settle it, treat as unverified.
3. **Nothing in S2 was compiled or executed (R1).** Every compile-break claim and every behavioural
   claim about fork code is a static reading. The first real `mvn -B verify -Djava.awt.headless=true`
   of S3 is the first evidence. Budget for surprises there.
4. **No historical test was ever run (R2).** Whether the fork's 182 methods actually passed against
   `atenearesearchgroup.uncertainty.jar` is unknown. "The fork pins X" means "the fork *asserts* X".
5. **`UString` has no behavioural oracle at all**, and §6.5 states which types are under-evidenced.
   The operation *counts* are solid; the operation *semantics* for `UString` and `SBoolean` rest on
   source reading alone.
6. **The 12 blocking decisions are unanswered.** S2's job was to surface them, and it did. **S3 must
   not start until all twelve have an owner and a recorded answer** — five of them (B1, B2, B5, B8,
   B11) cannot be closed by any test the port currently owns.
7. **The audit sampled 185 of 218 citations, not all of them.** 100 % coverage of §0, §1 and §7; the
   remaining 33 tokens in §2/§4/§6/§10 were sampled, not exhausted. The 94.6 % figure is a sample
   statistic.

---

## 5. Cross-references

| Document | What it holds |
|---|---|
| `docs/port2/specification.md` | The deliverable. Standing constraints C1–C3 at the top; 12 blocking decisions in §0 and §9; residual risk in §10 |
| `docs/port2/spec-parts/*.md` | The 15 working-paper sections the specification is synthesised from |
| `docs/port2/audit-02-specification.md` | The independent citation audit (SOUND_WITH_CAVEATS, 175/185) |
| `docs/port2/stage-00-baseline.md` | The corrected baseline (143, not 13) and the vintage probe (45 classes / 315 methods) |
| `docs/port2/stage-01.md` §7 | The S1 amendment: DEFECTIVE verdict, D1, F1–F11, and the still-open D2 |
| `docs/port2/upstream-test-waivers.md` | Zero waivers through S2 |
