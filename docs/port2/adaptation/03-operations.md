# Adaptation — Part 03: the operation registries and overload resolution

**Governing policy (the user's, applied here, not debated):**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

**Scope.** `org.tzi.use.uml.ocl.expr.operations.*` (the 13 upstream registry files, the 5 fork
uncertainty registry files, `OpGeneric`, `BooleanOperation`) and `ExpStdOp`'s overload resolution.

**Provenance note (2026-08-21).** `adaptation/04-grammar.md`, cited below for the `Op_identical`
grammar-registration question, was consolidated during a documentation cleanup and no longer exists
as a separate file; its conclusion (registered via the standard-operation map, not a grammar
keyword) is already recorded in `adaptation-policy.md`'s decision rows. Full original content in
git history.

**Status.** The findings below were originally established by eleven probe drivers run against the
historical jars and 7.5.0's built classes, plus a mechanical set-difference of the two live
`ExpStdOp.opmap`s and a 74,970-cell exhaustive registry-arrangement sweep. Every per-operation
hazard this document found (the C1–C7 rows below) now has a corresponding, more compact **O-\***
row in `docs/port2/adaptation-policy.md` §"per-op hazard ledger" — that document is the place to
look up any individual operation's status; this document keeps only what needs the fuller
derivation (the registration-order experiment, §3) and the one hazard fixed this session (C6, §4).

## 1. The registries, mechanically diffed — summary

`OpGeneric.registerOperations` is the single entry point and the two trees (fork vs. 7.5.0) differ
by **exactly six lines**: the fork's insertion of the five uncertainty registry calls right after
`StandardOperationsBoolean` (`OpGeneric.java:91-97`). Of the 13 shared upstream registry files, 7 are
byte-identical, 2 have only cosmetic diffs, and 3 (`StandardOperationsAny`, `StandardOperationsCollection`,
`StandardOperationsNumber`) were rewritten in place by the fork to add uncertainty-aware `matches`/`eval`
— these are the three-way-merge targets (`O-03` in `adaptation-policy.md`).

A runtime dump-and-diff of both live `ExpStdOp.opmap`s (`(name, implementing-class)` pairs) confirms
this mechanically: **2** pairs exist only on the 7.5.0 side (`pow`→`Op_number_pow`,
`sqrt`→`Op_number_sqrt` — added upstream 2024-06-27, after the fork's base), **109** exist only on
the fork side (70 named + 39 anonymous `SBoolean` enum constants, corroborating B2's "39 SBoolean
operations"), and **110** are shared. That `pow`/`sqrt` pair is the entire set of 7.5.0 operations the
fork never saw, and it is the origin of C1/C2 below.

`OpGeneric.java` and `BooleanOperation.java` otherwise carry **no contract delta** — every abstract
member, both `registerOperation` overloads, and `evalWithArgs` are byte-identical in the two trees.
The five uncertainty registry files compile against 7.5.0's `OpGeneric` unchanged; what they need
first is part 02's `Type` predicates (`isKindOfUReal`, `isKindOfUString`, etc.), which is why part 02
must land before part 03.

## 2. C1/C2 — `Op_number_sqrt`/`Op_number_pow` shadowing: archaeology, confirmed fixed

Both ops were added by upstream in 2024, after the fork's base, with `matches` predicates
(`isKindOfNumber(EXCLUDE_VOID)`) wide enough to accept a ported `URealType`/`UIntegerType`, and are
registered before the uncertainty buckets — so, unguarded, `UReal(4,2).sqrt()` would resolve to
`Op_number_sqrt`, type as `Integer`, and throw `ClassCastException` at `eval`.

**Confirmed fixed, current source.** `StandardOperationsNumber.java`'s `Op_number_sqrt.matches` and
`Op_number_pow.matches` both now guard with `instanceof UncertainType` exclusions (see the class's
own Javadoc, and `import org.tzi.use.uml.ocl.type.UncertainType` at the top of the file). With the
guard, both ops decline an uncertain receiver and first-match-wins hands the call to
`Op_ureal_sqrt`/`Op_uInteger_sqrt`/`Op_ureal_power`/`Op_uInteger_power`. `4.sqrt()` and `4.pow(2)`
keep 7.5.0's unguarded answers unchanged. See `adaptation-policy.md` rows **O-01**/**O-02** for the
compact ledger form; no further derivation needed here.

## 3. Registration order — where the uncertainty registries had to go

`ExpStdOp.opmap` is an `ArrayListMultimap`: `opmap.get(name)` returns candidates in **registration
order**, and both lookup paths return on the **first** match — no specificity ranking, no ambiguity
error. This makes registry *slot* a real semantic choice, not cosmetics.

The registry was built three ways inside the running fork (uncertainty-first, uncertainty-last, the
fork's own slot-after-Boolean) and every `(name, signature)` cell — **74,970** of them, arities 1–3
over 17 real types — was compared pairwise:

| arrangement | cells differing from the fork's own order |
|---|---|
| fork's slot (after `StandardOperationsBoolean`) | — (baseline) |
| uncertainty registered **first** | **22** — ordinary `String` relational ops and `Boolean` logic (`and`/`or`/`not`/`implies`/`xor`) start resolving to their `U*` counterparts, because `StringType.isKindOfUString`/`BooleanType.isKindOfUBoolean` both answer `true` under the fork's lattice |
| uncertainty registered **last** | **0** |

**Decision: register the five uncertainty registries at the fork's own slot** (after
`StandardOperationsBoolean`, before `StandardOperationsCollection`). Registering them first breaks 22
cells of ordinary non-uncertain OCL, which the governing policy's second sentence forbids.
Registering them last is measured indistinguishable from the fork's slot, so the fork's slot is kept
because it is what the fork's own diff produces and it minimises the merge — not because it is
load-bearing. This decision, and the corrected/separate claim that `ArithOperation.matches` must
independently keep its own internal branch order (a different constraint that a naive re-slotting
does not fix), is fully recorded as **O-07** in `adaptation-policy.md`; this section exists only to
preserve the 74,970-cell measurement behind that row.

## 4. C6 — `Op_uString_uConcat`: fixed this session

**Original finding, two defects in one class.** (1) `Op_uString_uConcat` was registered twice under
`"+"` (harmless under `ArrayListMultimap` + first-match, but dead weight). (2) Its `matches` evaluated
`params[0].isTypeOfUString() || params[1].isTypeOfUString()` **before** checking `params.length == 2`
— and because `"+"` is also `Op_number_unaryplus`'s bucket (arity 1), any unary `+` on a non-`UString`,
non-numeric operand (`+ 'a'`, `+ true`, `+ Set{1}`, `+ UBoolean(…)`, `+ SBoolean(…)`) reached this
`matches` with a 1-element array and threw `ArrayIndexOutOfBoundsException`, where plain 7.5.0 gives a
clean `Undefined operation` compile error.

**Confirmed fixed, current source** (`StandardOperationsUString.java`):

* `Op_uString_uConcat` is registered exactly **once** (`registerTypeOperations`, line 28).
* `matches` now checks `params.length != 2` first and returns `null` immediately — an `@implNote` on
  the method records why (the `+`/unary-plus bucket-sharing mechanism above).

Both defects were B7 fixes, and the fix restores 7.5.0's own diagnostic behaviour exactly (`+ 'a'` now
gives 7.5.0's clean `Undefined operation` error instead of throwing). This entry was previously
recorded in this document as a **live, currently-shipping regression** — that status is now **stale**.
See `Op_uString_uConcat.matches`'s Javadoc in `StandardOperationsUString.java` for the mechanism, and
`adaptation-policy.md` row **O-06** for the compact ledger form.

## 5. Everything else — pointer only

C3 (the 21 upstream classes the fork rewrote, three-way-merge rule: fork's `matches`/`eval`, 7.5.0's
everything else), C4 (`Op_identical`/`equals` registration — **note:** this document previously
claimed registering it without the `identicalExpression` grammar production leaves it dead code; that
claim was **measured and refuted** in `adaptation/04-grammar.md` §3.5 and is corrected at
`adaptation-policy.md` rows **G-01**/**O-04**/**B4** — do not rely on the original claim), C5 (the two
additive `uCount`/`uCountC` collection operations, no collision), and C7 (`Op_number_pow` prints as
unreparseable `(4 pow 2)` — a pure upstream defect, record only, do not fix) are all carried as O-03,
O-04, O-05 and O-08 respectively in `adaptation-policy.md`, with the same file:line citations and
measured evidence this document originally produced. Nothing in this document adds to those four
beyond what is already there.

## 6. What is not re-derived here

The 74,970-cell sweep's driver source, the full 21-cell mixed-case probe table, and the exhaustive
list of which 21 upstream op classes admit a `UReal`/`UInteger` receiver and why were cut from this
revision as settled archaeology — every conclusion they support is carried forward into §3 above and
into `adaptation-policy.md`'s O-rows. If a future stage needs to re-run the sweep, the method is:
build `ExpStdOp.opmap` three ways (uncertainty first / last / at the fork's slot) by reflectively
invoking each `registerTypeOperations`, then call `matches` on every op for every `(name, signature)`
cell over a representative type set including at least one `PortedURealType`-shaped stand-in, and diff
the winning class per cell against the fork's own baseline arrangement.
