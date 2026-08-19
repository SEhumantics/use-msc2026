# S5–S8 — operations, expressions, grammar, arithmetic

**Status: the fork's worked example evaluates end to end, to the fork's exact value.**

```
Set{UReal(2,0.5), 1, 2.5}         = Set{1,2.5,UReal(2.0, 0.5)} : Set(UReal)
Set{UReal(2,0.5), 1, 2.5}->sum()  = UReal(5.5, 0.5) : UReal
```

Byte-identical to the strings measured on both sides in `adaptation-policy-refutation.md`. In plain
USE 7.5.0 the same text is `Undefined operation 'UReal'`.

| | |
|---|---|
| `07eb8b9d` | S5 — the five operation classes; **112 → 218** registered operations |
| *(S6)* | the expression and AST classes, and the visitor expansion they force |
| `b811c1f3` | S7 — the grammar; and a token-number rot it exposed |
| `a38e9cb9` | S8 — mixed uncertain/crisp arithmetic |
| `b78e70d8` | S8 — `Set(UReal)->sum()` |

---

## 1. Diff-driven porting was not available, and it mattered

`StandardOperationsNumber` differs from the fork's by **1610 lines**, but the fork registers
**nineteen** operations where 7.5.0 registers **twenty-one** — the fork's base predates two upstream
operations. A wholesale replacement would have silently deleted them.

So the three upstream operation files were merged **surgically**: only the blocks that carry
uncertainty semantics were spliced (`ArithOperation`, `Op_number_add/sub/mult/max/min`,
`Op_collection_sum`), and the rest of each 7.5.0 file was left untouched. The same measurement
applies to `OpGeneric`, whose 129-line difference turned out to be **entirely** base drift: the five
new operation classes compiled against 7.5.0's version with zero errors.

## 2. Two defects the port exposed in upstream code

**A token-number rot (`b811c1f3`).** `ASTIterationStatement` decided whether to warn about iterating
a non-ordered collection by comparing against `SoilLexer.T__44` / `T__48`, with the comment
*"44 is Bag, 48 is Set"*. ANTLR assigns those numbers in grammar order, so adding the five uncertain
literals renumbered them onto `Sequence`, and USE began warning that `for x in Sequence{1..9}`
iterates a non-ordered collection.

Shell test `t086` caught it — and was the only thing that could have, because **nothing exercised the
warning**: no shell test iterates a `Bag` or `Set` literal, and none asserts the message. That is how
hard-coded token numbers rot unseen.

The fork's answer was to **delete the warning**. Not adopted: the warning is 7.5.0 behaviour, not
uncertainty semantics, and its removal there was collateral damage rather than a decision. Matching
on token *text* keeps it and makes it immune to further grammar growth.
`IterationWarningTokenRotTest` pins **both** directions, since only one was ever exercised.

**A silent wrong answer (`a38e9cb9`).** Between S7 and S8, `UReal(2,0.5) + 3` typed as plain `Real`.
It compiled, it evaluated, and it discarded the uncertainty without error. A wrong answer that does
not fail is the worst outcome available, which is why it was the first S8 item and why it is pinned
by name in the test.

## 3. Why `uncertaintyType` is not redundant

7.5.0's `simpleType` is `name=IDENT`, so `UReal` already parsed as an identifier. Adding `'UReal'` to
the `literal` rule makes ANTLR lex it as a **keyword**, and a keyword no longer matches `IDENT` — so
the literals alone would have silently broken every model declaring an attribute of one of these
types. The reason is written into the grammar beside the rule.

## 4. The crisp controls are the point

Every one of these changes sits on a path crisp OCL uses. An uncertainty port that moves crisp typing
has broken the language rather than extended it, so each stage asserts the unchanged answers
explicitly — `1+2 : Integer`, `1+2.5 : Real`, `Set{1,2.5} : Set(Real)`,
`Set{1,2.5}->sum() = 3.5 : Real` — and the gate carries all 129 `ShellIT` model-parsing tests.

## 5. Open

* **The remaining collection surface.** `->sum()` is done; `StandardOperationsCollection` and
  `StandardOperationsAny` carry further uncertainty arms (`UncertainType` appears at `:104`, `:169`,
  `:401`, `:474` in the fork) that are not yet ported.
* **`ExpDefSBoolean` / `ASTSBooleanDefExpression`** are ported but have no grammar rule yet, so the
  SBoolean *definition* form is unreachable from source text.
* **The differential census** still covers 39 operations of 285; S5–S8 added operations faster than
  the sweep was widened.
