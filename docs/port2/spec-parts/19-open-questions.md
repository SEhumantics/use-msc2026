# 19 — Open Questions (Refuter pass)

Three design questions raised against the historical fork
(`USE-Uncertainty` at commit `74acd0d`), answered during planning and now condensed to what was
asked, what was decided, and where the answer lives today. The full original investigation
(source-line citations, greps, and the independent derivation chain for each verdict) is preserved
in git history for this file; nothing below is a new claim.

---

## Q1 — Is `ExpDefSBoolean` needed?

**Asked:** is the one-argument `SBoolean(<expr>)` coercion expression (`ExpDefSBoolean`,
`ASTSBooleanDefExpression`, `ExpressionVisitor.visitDefSBoolean`) reachable from the four U-types'
behaviour, or only from SBoolean, and is it needed either way?

**Decided:** **No — dead code, unreachable on two independent grounds, and broken twice over even
if it were reachable.** Its sole construction site (`ASTSBooleanDefExpression`) is itself never
instantiated, and no grammar production emits the one-argument surface syntax it would implement
(the only `SBoolean(...)` grammar rule is the four-argument literal, which routes to
`ExpConstSBoolean` instead). Independently, its type guard is inverted (throws exactly when the
argument *is* the type it claims to expect) and its `eval` never balances `ctx.enter`/`ctx.exit`.
**Do not port it.**

**Where the answer lives now:** `docs/port2/specification.md` row **B10** (drop decision, and the
resulting new-file/method counts elsewhere in that document).

---

## Q2 — True dependency footprint of SBoolean

**Asked:** the port plan assumed the four U-types (`UBoolean`, `UReal`, `UInteger`, `UString`)
determine how much of SBoolean must be ported. Is that assumption right?

**Decided:** **Half right.** None of the four U-types' value classes or operation registries
reference SBoolean — that half of the assumption holds, and `StandardOperationsSBoolean` is
genuinely severable from the four types' own behaviour. But the dependency also runs the other
way, and that half is real: `UBooleanType` and `BooleanType` each declare `SBoolean` a supertype
and answer `isKindOfSBoolean() == true`. 28 of SBoolean's operations are guarded by the loose
`isKindOfSBoolean` rather than the strict `isTypeOfSBoolean`, so they match `UBoolean`/`Boolean`
receivers too; 21 of those op names have no boolean-side competitor and are therefore genuinely
callable on `UBoolean`/`Boolean`, returning an `SBoolean` (e.g. `UBoolean(true,0.7).min(...)` is
legal OCL under the fork). The port cannot silently inherit this — it has to make a conscious
decision about `isKindOfSBoolean` on the two boolean types.

> ## DECIDED 2026-08-17 — **B2 = option 3, FULL PORT of `SBoolean`, all 39 operations.**
>
> **Read this before anything above.** This file originally recommended **option 1 (full
> omission)** as the cheapest, most defensible choice, given that the corpora contain zero
> `SBoolean` tokens and the fork has no SBoolean test. **That recommendation was NOT taken.** The
> user chose the full port anyway, with the corpus evidence unchanged — what changed is the
> decision, not the facts.
>
> * `specification.md` §0.0/§8.2 separately recorded a different recommendation (option 2,
>   skeleton). Neither recommendation was taken; both are superseded by this decision.
> * `isKindOfSBoolean` **survives** on `UBooleanType`/`BooleanType` — the narrowing edits this file
>   once proposed (stripping it from `Type`/`TypeImpl`/`MClassifierImpl`/`VoidType` and the two
>   boolean types) are **not** to be executed.
> * Q1's `ExpDefSBoolean` drop (B10) is a separate decision, unaffected by this one: full port of
>   `SBoolean` the type/value/operations does not imply porting `ExpDefSBoolean`.
> * The full-port scope, its work items, and the new hard prerequisite (`SBooleanValue`
>   marshalling in the differential harness — without it all 39 operations are `UNSUPPORTED`) are
>   in `docs/port2/b7-fix-plan.md` §6, which supersedes any earlier costing.

**Where the answer lives now:** `docs/port2/specification.md` row **B2** (decision record) and
`docs/port2/b7-fix-plan.md` §6 (scope and prerequisites). `UBooleanType.isKindOfSBoolean()` carries
its own `@implNote` explaining the mechanism (why unconditional `true` here is intentional, not a
bug, and what it makes reachable) — that Javadoc is the live technical account; this file no longer
needs to restate it.

---

## Q3 — `UncertainBooleanValue`/`Type` vs `UBooleanValue`/`Type`

**Asked:** are `UncertainBooleanValue`/`UncertainBooleanType` a second, parallel boolean-ish type
alongside `UBooleanValue`/`UBooleanType` — i.e. redundant plumbing?

**Decided:** **No — it is a two-level hierarchy, not two parallel types.** `UncertainBooleanType`/
`UncertainBooleanValue` are abstract bases with no surface existence (no grammar token, no
`TypeFactory` entry, never instantiated); they exist solely so `UBoolean` and `SBoolean` can be
siblings under a common parent. `UBoolean*` is the concrete, corpus-exercised type; `SBoolean*` is
the other, corpus-empty child. Per Q2's decision (B2, full SBoolean port), both children survive,
so the abstract parents are **kept** — under the full-omission or skeleton options this file
originally weighed, the parents would have collapsed (each would have exactly one subclass), but
that path was not taken.

**Where the answer lives now:** `UncertainBooleanType` and `UncertainBooleanValue` each carry a
class-level Javadoc paraphrasing this verdict (abstract base joining `UBoolean*` and `SBoolean*`);
`docs/port2/specification.md` row **B2** covers the SBoolean-retention decision this question's
answer depends on.
