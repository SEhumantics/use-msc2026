# Adaptation — Part 02: value classes and the `Value` contract

**Governing policy (the user's, applied here, not debated):**

> Uncertainty meaning comes from the fork. Everything else comes from USE 7.5.0.
> Where the two collide, keep the uncertainty behaviour but express it the 7.5.0 way.

**Scope.** The seven uncertain value classes, the three upstream `org.tzi.use.uml.ocl.value` files
that had to be edited (`Value`, `CollectionValue`, `RealValue`), and `util/MathUtil`.

**Method (historical).** Every finding below was originally established by ten probe drivers
compiled and run against the historical jars (nine) and 7.5.0's built classes (one), not by
reasoning from source alone. The drivers are not committed (they lived under scratch `/tmp/probe2/`);
the numbers they produced are what is recorded here and, since B7 landed, in Javadoc on the value
classes themselves.

## 1. The `Value` contract — no signature adaptation

`Value.java` differs from the fork by exactly four added predicates (`isUInteger()`, `isUReal()`,
`isUBoolean()`, `isSBoolean()`, each `public boolean` defaulting `false`) plus a version tag —
nothing else moved, renamed, or went abstract. Every one of the seven value classes satisfies
7.5.0's `Value` obligations with the signatures it already had. `CollectionValue.java` likewise
differs only by five additive `uIncludes`/`uIncludesAll`/`uExcludes`/`uExcludesAll`/`uCountC`
methods — `SetValue`/`BagValue`/`SequenceValue`/`OrderedSetValue` need no change. `RealValue.java`
needed one additive static lift, `RealValue.valueOf(Value)`, used only by `SBooleanValue`.

## 2. Settled findings (V1–V13, V16–V18) — now Javadoc, not open questions

18 collisions between fork behaviour and a correct 7.5.0-shaped port were originally measured and
tabulated here. B7 (decided 2026-08-17) chose FIX over bug-for-bug reproduction for the whole set,
and every row below except V14 and V15 is now **implemented and documented in place** on the value
class it touches — see each class's header Javadoc and the cited `b7-fix-plan.md` section for the
full derivation. This document no longer needs to restate them:

| id | one-line finding | fixed in |
|---|---|---|
| V1 | `Undefined`→`null` printed form (B6) | upstream `UndefinedValue`, untouched; normalised at harness comparison time |
| V2 | `Value`'s four `isU*`/`isSBoolean` predicates | `Value.java` |
| V3 | three printed rounding regimes (10dp / 3dp / none) — reproduced verbatim, not unified | each `*Value.toString(StringBuilder)` |
| V4 | delegate `%5.3f` form never reaches a printed value | confirmed unreachable; no code change |
| V5 | `UStringValue` hard-codes `"UString('"` rather than `type()` | kept as-is (pins V3) |
| V6 | `compareTo(UndefinedValue)` returned `0` instead of `+1` | `URealValue`, `UIntegerValue`, `SBooleanValue` |
| V7 | `UStringValue.equals` constant `false` (M-11) | `UStringValue.java` (Javadoc cites `b7-fix-plan.md` §2 M-11) |
| V8 | `UIntegerValue.hashCode` collapsed to `0` (F-10) | `UIntegerValue.hashCode()` (Javadoc cites `b7-fix-plan.md`) |
| V9 | `UInteger↔UReal compareTo` not antisymmetric (M-9) | `UIntegerValue`/`URealValue.compareTo` |
| V10 | `UInteger.equals(UReal)` false both directions (M-10) | `URealValue.equals` gained a `UIntegerValue` arm |
| V11 | `UBoolean.FALSE.equals(BooleanValue.FALSE)` false (M-8) | `UBooleanValue.equals` |
| V12 | `SBooleanValue.compareTo` constant `0` (M-18) | `SBooleanValue.compareTo` — local total order, does **not** delegate to `SBoolean.compareTo` (non-transitive 0.001 dead band) |
| V13 | `UStringValue.compareTo(StringValue)` compared raw vs. rendered text (M-12) | `UStringValue.compareTo` |
| V16 | `URealValue.hashCode`/`equals` rounding mismatch (F-3) | `URealValue.hashCode()` (Javadoc cites `b7-fix-plan.md` §2 F-3) |
| V17 | collection literals keep raw `IntegerValue`/`RealValue` elements unlifted | confirmed intended; no change |
| V18 | 8 corpus rows print pre-M-6 (10dp) UBoolean confidences; fork code rounds to 3dp | port follows fork code (3dp), 8 rows pre-registered as known corpus non-agreements |

Verify any of the above by reading the cited class's Javadoc directly — each carries a `b7-fix-plan.md`
section reference rather than restating the derivation here.

## 3. V14 — `CollectionValue` u-ops on `SBooleanValue` elements: **partially fixed this session**

**Original finding.** `CollectionValue.uIncludes`/`uCountC` dereferenced `UBooleanValue.valueOf(Value)`
without a null check; on an `SBooleanValue` element (whose `uEquals` returns another `SBooleanValue`,
which `valueOf` cannot coerce and silently returns `null` for) this reached `aux.probability()` as an
uncaught `NullPointerException`. `uExcludes` took a different path — `UBooleanValue.and`'s own
coercion — and already threw `ClassCastException: A value kind of UBoolean expected` for the same
input.

**Current state, confirmed by reading `CollectionValue.java`.** A `requireUBoolean(Value)` helper
now wraps the coercion for `uIncludes` and `uCountC` (`uExcludes` needed no change, it already threw
the same way). All three now fail identically and predictably:

```
Set(SBoolean){TRUE}.uIncludes(SBoolean TRUE)  => ClassCastException: A value kind of UBoolean expected
Set(SBoolean){TRUE}.uExcludes(SBoolean TRUE)  => ClassCastException: A value kind of UBoolean expected  (unchanged)
Set(SBoolean){TRUE}.uCountC(SBoolean TRUE, .5)=> ClassCastException: A value kind of UBoolean expected
```

**What this is and is not.** The crash is fixed — no more `NullPointerException`. Membership
degree for `SBoolean` collection elements is still **not semantically implemented**; the operation
now fails clearly and uniformly instead of failing two different ways (NPE vs CCE) depending on
which method was called. This is not "V14 fixed" and not "V14 still broken" — it is **no longer
crashes, still not supported, fails clearly now.** Widening the fold to accept `UncertainBooleanValue`
(so an `SBoolean` element's `uEquals` result is handled rather than rejected) remains undone and is
the actual open work if `Set(SBoolean)` membership is ever needed. See `CollectionValue.java`'s
Javadoc on `uIncludes` and `requireUBoolean` for the full note.

## 4. V15 — order-dependent set membership across `equals` asymmetry: still open, unchanged

`URealValue(2,0).equals(2)` is `true` but `2.equals(URealValue(2,0))` is `false` — the crisp-side
`IntegerValue`/`RealValue.equals` were never given a `U*Value` arm (only the reverse direction was,
by M-10/V10). Consequence, measured against `HashSet`:

```
Set{1, UReal(1,0)}          -> Set{1}                       size 1 -- the UReal is silently DROPPED
Set{UReal(1,0), 1}          -> Set{1,UReal(1.0, 0.0)}       size 2 -- both kept
```

Membership depends on literal order. This is **not on the 33-row B7 list**, so B7 does not authorise
a fix, and the only symmetric remedy — adding `U*Value` arms to upstream `IntegerValue.equals`/
`RealValue.equals` — would change how *plain* USE values behave, exceeding every other recorded edit
in this area. **Still an open decision, not made this session.** Once V8's hash fix (`UIntegerValue.hashCode`,
now landed) lets `UInteger` and `Integer` share a hash bucket, the identical asymmetry appears for
`UInteger` too — so this decision cannot be deferred past `UInteger` reaching general use. See
`docs/port2/adaptation-policy.md` rows V15/K-10, DEP-22 and B13 for the fuller two-reader disagreement
record; this document takes no side on 1 vs. 2 as the "right" answer.

## 5. Corpus coverage note (context for V14)

`SBoolean` and `UString` have **zero** corpus coverage — `grep -c 'SBoolean'` / `grep -c 'UString'`
over all four `.in` fixture files return `0` for every file. V14's fix (and V7/V13's, above) is
therefore unobserved by the historical oracle; it can only be defended by purpose-built tests, never
by corpus agreement.
