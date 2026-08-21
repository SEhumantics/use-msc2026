# 16 — Modernization Ledger (superseded)

**This document is fully superseded. Its 77-row analysis now lives at
`docs/port2/specification.md` §7 (§7.1 BEHAVIOUR-PRESERVING, §7.2 BEHAVIOUR-CHANGING, §7.3
float-sensitivity, §7.4 do-not-touch list), and its per-row fix plan is
`docs/port2/b7-fix-plan.md` §2. Read those two instead of this file.**

## What this was

A JUnit3→5 / Java7→21 modernization ledger for the fork's 30 in-scope uncertainty source files and 8
in-scope test files: 77 rows classified `CF-n` (compile-forced, mandatory), `M-n` (modernization
proposal, no floating-point exposure) or `F-n` (float-sensitive — a comparison, equality, singleton
selection, or confidence value rides on binary floating point at that site). This document was
**PROPOSAL ONLY** — every `BEHAVIOUR-CHANGING` row (33 of the 77) originally read `DEFER`, meaning "a
human must rule on this," pending the fidelity verdict for the corresponding stage.

## What happened to it

**The human ruled on 2026-08-17: FIX the historical defects, documenting each — the bug-for-bug
recommendation this file's `DEFER` rows recorded was *not* taken.** `b7-fix-plan.md` §2 triages all
33 behaviour-changing rows by row ID, naming the fix, the owning stage (S3–S9), and the observable
class of each change; it supersedes the `proposed change` column of every row in this file's former
Tables A and B. `specification.md` §7 carries the classification tables themselves (row IDs,
BEHAVIOUR-PRESERVING/CHANGING split, float-sensitivity grouping, do-not-touch list) in current form.

## What survives here

Only the row-ID namespace: **`CF-1`…`CF-10`, `M-1`…`M-51`, `F-1`…`F-16`** originated in this
document's now-removed Tables A and B, and other docs in `docs/port2/` still cite specific rows by
these IDs (and, in older passages, by this file's former line numbers). The IDs are stable; the
content behind them is not here — it is at `specification.md` §7 and `b7-fix-plan.md` §2, cross-
referenced by the same IDs.
