# S10 — an independent audit of "is the port actually finished," and what it found

**Role: Record.** Written 2026-08-21 on branch `port-uncertainty-2`. Every number below comes from a
named command whose output is quoted verbatim. Nothing here is estimated.

---

## 0. What this stage did, in one paragraph

S9 closed with "nothing from the adversarial audit's original findings remains open" (`stage-09.md`
§5) — a claim scoped explicitly to the 33-row B7 ledger and the two items closed alongside it
(`uCount`/`uCountC`, M-1..M-6), not a claim that the five uncertain types were exhaustively verified.
The user asked directly whether the port of `UInteger`, `UReal`, `UString`, `UBoolean` and `SBoolean`
was *truly* finished. Per this project's own standing rule ("the verifier is never the porter"), that
question was answered by dispatching two independent audit agents — one with no memory of this
session's prior work — rather than by re-reading the existing ledger and declaring it sufficient. The
audits found two real, live GUI/print bugs (specified but never implemented), three severe
UString defects (two static-type lies, one that crashed with a raw `NullPointerException`), one
equally severe `SBoolean` defect of the identical class discovered while closing a coverage gap the
audits also found, and zero-to-thin test coverage across three types: all 6 `UReal` trig operations,
all 20 `UString` operations (no dedicated test file existed at all), and 11 named `SBoolean`
operations. ~26 dead methods were also found and removed (6 ungrammared operations, plus ~20 more in
the vendored library once the user was asked and chose to remove them too). All of it is fixed and
tested in this stage.

---

## 1. Why this stage happened, and what "independent" meant here

Every prior stage's B7 work was done and verified by the same agent across the same session — the
33-row ledger, `uCount`/`uCountC`, M-1..M-6. That is porting, not verification, however carefully each
individual fix was tested. This stage dispatched two fresh subagents with no context from this
session's prior work — one to check main-code parity, grammar reachability, and dead code against the
fork; one to run the real test suite and audit per-type coverage — each instructed to cite file:line
evidence for every claim rather than trust `docs/port2/*.md`'s own account of what was done. A third,
narrower dead-code sweep (spawned by the first agent, then continued directly) went one layer deeper,
into the vendored `org.tzi.use.uncertainty.datatypes` library itself.

One finding from the two main audits was cross-checked directly before acting on it (never taking a
subagent's claim as ground truth without independent confirmation): the audits disagreed about whether
`StandardOperationsSBoolean.java`'s six commented-out fusion operations were symptomatic of something
the port had broken. Direct inspection resolved it — byte-identical to the fork, both files, same
lines — before §5.1 removed the six now-orphaned wrapper methods they were the only caller of.

---

## 2. Two real, specified-but-unimplemented bugs in `uSelect`/`uSelectC`

Both were designed and documented when `uSelect`/`uSelectC` were added at S9 (`stage-09.md` §4.3h) but
never actually implemented — the design docs describe the fix; the code doesn't have it.

### 2.1 E12/K-15 — `uSelectC`'s confidence threshold silently dropped when printed

`ExpressionPrintVisitor.visitQuery` never called `getUncertaintyExpression()`, so
`col->uSelectC(e | e >= 1, 0.9)` and `col->uSelectC(e | e >= 1, 0.1)` printed identically —
`Set{1, 2, 3}->uSelectC( e:Integer | (e > 1) )`, threshold missing entirely — despite evaluating to
different collections. Reaches the `show` command, the HTML exporter
(`GenerateHTMLExpressionVisitor extends ExpressionPrintVisitor`), and the GUI evaluation-tree display.

Fixed exactly as `docs/port2/specification.md` E12 specified: the shared private `visitQuery` now
appends `", " + <threshold>` when `getUncertaintyExpression() != null`. `uSelect` has no confidence
expression, so it is unaffected — confirmed by a dedicated test, not assumed.

### 2.2 K-14 (Tier B) — the GUI evaluation-tree browser never substitutes `uSelect`/`uSelectC` nodes

`EvalNode.SubstituteVariablesExpressionVisitor` overrides `visitSelect`/`visitExists`/`visitForAll`/
`visitOne`/`visitReject`/`visitSelectByKind`/`visitExpSelectByType` — every other query/collection
node type that can appear in the interactive evaluation browser — each checking "is this exact
subexpression one I should substitute with its already-computed value?" before falling through to the
generic printer. `uSelect`/`uSelectC` never got the same two overrides, so when either appears as the
**range** of an enclosing query node (the only place `EvalNode.substituteChildExpressions()` ever
substitutes), the enclosing node's substituted print silently re-expanded the `uSelect`/`uSelectC`
subexpression's raw source instead of showing its computed value.

Confirmed the fix matters, not just cosmetically plausible: reverted it, re-ran the new regression
test, watched it fail with the raw source re-expanded instead of the computed `Set{2,3}`, then restored
the fix and watched it pass.

```
$ mvn -o -pl use-core test -Dtest=UncertainQueryAndMembershipTest
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
```

---

## 3. `UString`: zero prior OCL-level test coverage, three real defects found while adding it

Neither the fork nor the port had a dedicated `UString` test file — unlike `UBoolean`/`UReal`/
`UInteger`, none of which are missing one. `specification.md` §6.5 independently confirms no `.in`
corpus fixture contains a `UString` token. Writing `UStringExpOpsTest.java` (all 20 registered
operations, exercised through the real grammar) surfaced three genuine, pre-existing fork defects —
byte-identical in the fork's own source, not introduced by porting.

### 3.1 `indexOf`/`toString`: declared-type lie, same class as the already-fixed M-37

`Op_uString_indexOf` and `Op_uString_toString` both declared a static return type of `UString` in
`matches()` while `eval()` always returns an
`IntegerValue`/`StringValue`. Exactly the defect class the closed ledger row M-37 already fixed once
(`UInteger.value()`/`toInteger()`). Fixed the same way: `matches()` now declares `mkInteger()`/
`mkString()`, matching what `eval()` actually returns. TYPE only — no corpus entry uses either
operation, and `toString`'s own leading comment already said "-> String," agreeing with `eval()`;
`matches()` was the outlier.

### 3.2 `toInteger`/`toReal`/`toBoolean`/`toUBoolean`: a caught exception still crashed, one call up

More severe. All four caught their conversion failure, but `toInteger`/`toReal` set a bare Java `null`
instead of `UndefinedValue.instance`. `ExpStdOp.eval` (`:271-317`) passes that `null` straight through
as its own result; any enclosing expression's ordinary strict-operand check
(`if (v.isUndefined())`, `ExpStdOp.java:291`) dereferences it. Reproduced live, before the fix:

```
1 + UString('abc', 1).toInteger()
  → java.lang.NullPointerException: Cannot invoke "Value.isUndefined()" because "v" is null
```

`toBoolean`/`toUBoolean` turned out to have no equivalent failure path at all — confirmed by reading
`UString.toBoolean()` (`Boolean.parseBoolean`, never throws) and `UString.uToUBoolean()` (falls back to
`UBoolean(true, 0.5)` on no match, never throws) — so only `toInteger`/`toReal` needed the fix. Both
now `return UndefinedValue.instance;` from their catch block directly, matching the already-established
`ExpConstSBoolean`/M-30 precedent for "an unguarded or caught failure must become `Undefined`, not
escape or propagate as a raw `null`."

```
$ mvn -o -pl use-core test -Dtest=UStringExpOpsTest
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
```

Several assertions in this file were wrong on the first attempt and corrected against real observed
output before being committed — `value()`'s `StringValue` quoting (`'hello'`, not `hello`),
`substring`'s 1-indexed-lower/exclusive-upper convention, the ordering operators' confidence-product
semantics (`<=` between two `0.9`-confidence operands answers `UBoolean(true, 0.81)`, not `1.0`), and
`toBoolean`/`toUBoolean`'s never-fails behaviour. None of this was hand-derived and trusted blind.

---

## 4. `SBoolean`: 13 untested operations, and a revised decision on `conjunctiveCertainty`/`degreeOfConflict`

`projection`, `projectiveDistance`, `toUBoolean`, `toString`, `getRelativeWeight`, `isAbsolute`,
`isVacuous`, `isCertain`, `isDogmatic`, `isMaximizedUncertainty`, `isUncertain`, `certainty` had no
dedicated test anywhere. `SBooleanExpOpsTest.java` closes all 11, plus regression-tests the fix below.

### 4.1 `conjunctiveCertainty`/`degreeOfConflict`: revising an S9 decision on new evidence

`stage-09.md` §4.3m found that both operations declare `SBoolean` via `matches()` while
`SBooleanValue.conjunctiveCertainty()`/`degreeOfConflict()` actually return `RealValue`, and left it
unfixed: *"fixing a declared operation return type is its own independently-scoped decision... this
test file's job was to measure the port against a written spec, not to fix whatever it happened to
find along the way."* That was the right call for a test-writing stage. Writing `SBoolean`'s missing
operation coverage this stage produced new evidence that changes the calculus: the mismatch is not
cosmetic. `SBooleanValue.valueOf(Value)` (`:110-127`) has no fallback arm for a `RealValue` argument —
it silently returns Java `null` rather than throwing — and any genuinely `SBoolean`-only operation
chained onto `conjunctiveCertainty`/`degreeOfConflict`'s result type-checks fine against the false
declared type, then dereferences that `null` at eval time. Reproduced live, before this fix:

```
SBoolean(0.5,0.3,0.2,0.5).conjunctiveCertainty(SBoolean(0.2,0.5,0.3,0.4)).belief()
  → java.lang.NullPointerException: Cannot invoke "SBooleanValue.belief()" because "sbool" is null
```

Fixed the same way as M-37 and §3.1 above: `matches()` now declares `mkReal()` for both operations,
matching their real runtime behaviour. `.belief()` chained onto either is now a compile error — the
correct outcome for a `Real` — not a runtime crash. `MetamorphicRelationsTest`'s M-6 exclusion note
(which explained the original, now-superseded "not fixed" characterization) is updated to describe the
fix rather than the defect.

```
$ mvn -o -pl use-core test -Dtest=SBooleanExpOpsTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0

$ mvn -o -pl use-core test -Dtest=MetamorphicRelationsTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
```

---

## 5. Dead code: six operations removed, ~20 more found and left for the user

### 5.1 Removed: six ungrammared `SBoolean` fusion operations

`minimumFusion`, `majorityFusion`, `averageFusion`, `cumulativeFusion`, `epistemicCumulativeFusion`,
`weightedFusion` — each an `SBooleanValue` wrapper method called only from a matching
`StandardOperationsSBoolean.java` enum constant that was commented out byte-identically in the fork's
own source (confirmed by direct inspection, both `git diff` against the fork and reading both files —
the two audits disagreed about severity here, so this was checked directly rather than trusted). With
the registration commented out, all six had no grammar path at all — the same "ungrammared semantics
code" this project's standing dead-code rule already removed once, for `ExpDefSBoolean` (M-26/M-27,
`stage-09.md` §4.3c). No functionality is lost: each has a live, differently-named, registered sibling
(`minimumBeliefFusion` and five others), confirmed still present and exercised by
`MetamorphicRelationsTest`'s M6SimplexClosure. Confirmed zero remaining callers of any removed method,
anywhere in `use-core` or `use-gui`, before removing.

### 5.2 The user was asked, and chose to remove the remaining ~20 methods too

Covariance-aware arithmetic overloads (`add(UInteger, double covariance)` and its siblings across
`UInteger`/`UReal`), `*Zero()` boolean-comparison variants, two `hashcode()` methods (lower-case, not
overriding `Object.hashCode`), three `clone()` methods, `UString.confToDist(double,int)`,
`levenshteinDist`, `calculateConf_05` (whose own Spanish comment says *"No se usa en la ultima
version"*), and a further dead subgraph in `SBoolean.java`'s own fusion methods (`union`,
`weightedUnion`, `ccFusion`, plus six more `SBoolean`-argument fusion overloads distinct from the ones
removed in §5.1). None of these has even a commented-out grammar-registration path pointing at it —
they are unused API surface of a vendored library this project has otherwise kept deliberately close
to upstream (one prior, narrowly-justified exception: `UUnlimitedNatural`, `stage-03-scope.md` §5), not
"semantics code that lost its grammar binding" in the sense the standing rule targets. Rather than
decide this unilaterally, the user was asked directly; they chose to remove all of it.

**Re-verifying before deleting caught a real flaw in the earlier audit's own methodology.** That
audit's dead/alive calls were built on grep for `receiver.method(` — a pattern blind to implicit-`this`
self-calls. Two genuinely live methods were misclassified as dead: `UInteger.equals(UInteger)` and
`.lt(UInteger)` are called by `compareTo`/`min`/`max` via a bare `this.equals(other)`, and
`.le`/`.ge(UInteger)` are called externally from `UIntegerValue.java` — none of which a
`"uInteger\.equals("`-shaped grep would ever find. Both were kept. Every other candidate was
re-confirmed with a broader search (both explicit and implicit-receiver call sites) and, for the
trickiest cluster — `SBoolean.java`'s single-argument fusion overloads, which call an
identically-named static `Collection`-argument sibling rather than each other or themselves — by
reading the actual method bodies, not just grepping their names. A full reactor compile after each
file's edits served as the final check: a real caller left behind would have produced a compile error
naming itself. None did. ~610 lines removed, 0 lines of behaviour changed, confirmed by the isolated
gate (§6) coming back with test counts exactly unchanged.

---

## 6. The gate

`bash scripts/upstream-oracle-gate.sh both` → **PASS**.

| mode | classes | methods | executions | failures |
|---|---|---|---|---|
| default, `use-core` surefire | 70 (floor 59 → **re-pinned 70**) | 393 (floor 352 → **re-pinned 393**) | 393 | 0 |
| oracle, `use-core` surefire | 103 (floor 92 → **re-pinned 103**) | 664 (floor 623 → **re-pinned 664**) | 1252 | 0 |
| default/oracle, `use-gui` | unchanged: 1/1 surefire, 8/17 oracle surefire, 1/129 failsafe both modes | — | — | 0 |

Seven commits this stage: `ca5f727f` (uSelectC print + EvalNode substitution), `93049b15` (UReal trig
coverage), `ea05efbc` (UString: 3 defects fixed, full 20-op test), `b8fef925` (SBoolean: 13-op coverage,
conjunctiveCertainty/degreeOfConflict fix), `d6ff1f18` (dead fusion code removed), `0e1e1372` (floor
re-pin), `70728431` (~20 more vendored-library dead methods removed, §5.2). `use-gui` untouched by all
seven — no `use-gui` source file changed. Test counts (§6 table) are unchanged by `70728431`: deleting
dead main code cannot change what the test suite exercises, and the isolated gate confirmed it didn't.

**Waivers: unchanged at four** (W-01–W-04). No `.java` test file was edited to make ported code pass.

---

## 7. What "finished" does and doesn't mean, again

The B7 ledger closing at 33/33 (`stage-09.md`) meant a specific, named, adversarially-produced backlog
was empty — not that the port was exhaustively verified. This stage's two independent audits found six
real defects (two GUI/print bugs, two UString crashes, one UString type lie, one SBoolean type lie
that also crashed) and thin-to-zero coverage across 6 `UReal`, 20 `UString`, and 11 `SBoolean`
operations that the original audit's methodology never surfaced,
precisely because "the verifier is never the porter" was applied one level deeper than before: fresh
agents with no memory of this session's own work, re-deriving the port's completeness from the fork and
the actual running code rather than from this project's own accumulated documentation.

That is itself evidence for a general point, not a one-time fact about this stage: an audit finds what
its methodology can see, and a different, independent methodology will usually see something the
first one didn't. §5.2's re-verification itself is a second instance of the same point one layer
down — the deep dead-code audit's own grep-based methodology had a real blind spot (implicit-`this`
self-calls), caught only by re-deriving each claim from the actual call graph before acting on it.
Coverage is corpus- and test-conditional, same as every prior stage's own stated limitation
(`stage-03-scope.md` §8.6): a defect reachable only at an input nothing here exercises stays invisible
to this or any test-based methodology.

---

## 8. Reproduce every number in this file

```sh
cd /home/xoruser/msc-4/use-msc2026

# section 2 -- uSelectC print fix + EvalNode substitution fix
mvn -o -pl use-core test -Dtest=UncertainQueryAndMembershipTest

# section 3 -- UString: 3 defects, full 20-op coverage
mvn -o -pl use-core test -Dtest=UStringExpOpsTest

# section 4 -- SBoolean: 13-op coverage + conjunctiveCertainty/degreeOfConflict fix
mvn -o -pl use-core test -Dtest=SBooleanExpOpsTest,MetamorphicRelationsTest

# section 5.1 -- confirm the six removed methods have no remaining callers anywhere
grep -rn '\.minimumFusion(\|\.majorityFusion(\|\.averageFusion(\|\.cumulativeFusion(\|\.epistemicCumulativeFusion(\|\.weightedFusion(' \
  use-core/src use-gui/src 2>/dev/null || echo "none found"

# section 6
bash scripts/upstream-oracle-gate.sh both

# full default suite, aggregate tally
mvn -o -pl use-core test -Djava.awt.headless=true
```
