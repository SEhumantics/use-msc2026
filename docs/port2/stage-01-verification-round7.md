# S1 verification, round 7 — independent refutation of the D-43 fix

**Subject:** commits `4bb5b6fe` (behaviour), `aa809638` + `0902a721` (documentation) on
`port-uncertainty-2`.
**Refuter:** second party. Did not write the fix. Owns Maven for this round.
**Verdict: DEFECTIVE.**

**One sentence.** The control is intact, D-18 is intact, every probe is intact, and half (a) of D-43 —
the false-*divergence* half — is genuinely closed and independently reproduced; but half (b), which the
register states in its own words ("one line erased the check: `.asJavaType(v.javaType())` went
`DIFFER 3 445 -> 0`"), is **open and measured open**: `declaredJavaType(referenceToken, "x")` takes the
same wrong-class port to a sweep **byte-identical to the perfect-port control** and the mandated reason
appears in **0** rows, while `harness-contract.md` §7 asserts the reason "is printed into the note of any
row the declaration moved." Two further constructions are new: the fix prints a *false* provenance as
fact, and the 3 445 signature turns out to **hide** real type defects rather than merely be ambiguous.

---

## 1. Acceptance, three runs

```
$ mvn -q clean && mvn -B verify -Djava.awt.headless=true
[INFO] BUILD SUCCESS
[INFO] use ................................................ SUCCESS [  0.003 s]
[INFO] use-core ........................................... SUCCESS [01:02 min]
[INFO] use-gui ............................................ SUCCESS [ 27.229 s]
[INFO] use-assembly ....................................... SUCCESS [  4.215 s]
```

Aggregated from the surefire/failsafe XML, not from the console:

```
surefire: files=8 tests=79 failures=0 errors=0 skipped=0
failsafe: files=2 tests=130 failures=0 errors=0 skipped=0
```

**79 + 130 = 209 methods, 0 failures**, three consecutive runs, identical counts. Delta from round 6's
207 is **+2**, accounted for exactly by the two new methods the porter names:
`PortedInfidelityDetectionPowerTest.aFactoryTypedAdapterMeasuresExactlyWhatThePlantedWrongTypeDoes`
and `DifferentialHarnessRegressionTest.theTypeTokenIsObservedOrDeclaredAndTheRowSaysWhich`.

Determinism is byte-identical, not merely count-identical. The published evidence blocks, extracted
from runs 1 and 3 and hashed:

```
$ for f in verify1 verify3; do sed -n '/=== detection power: control/,/^====*$/p' $f.log | md5sum; done
c724bd19dbed9071ffc8762675584107  -
c724bd19dbed9071ffc8762675584107  -
$ for f in verify1 verify3; do sed -n '/=== D-43: two readings/,/^====*$/p' $f.log | md5sum; done
ede8c25a407a6fd0ddc2dce139d81c4f  -
ede8c25a407a6fd0ddc2dce139d81c4f  -
$ md5sum docs/port2/differential/*.tsv      # identical after runs 2 and 3
911763e378f3af2f73607b00987d5891  docs/port2/differential/s1-smoke-ureal-add.tsv
83fe50877ecd6d6a039becc7a31fc005  docs/port2/differential/s1-smoke-ureal-minus-faulty.tsv
```

```
$ git diff --name-status 30d480db..HEAD -- '*/src/main/*'
[EMPTY]
$ git status --short
[EMPTY]
$ git diff --name-status a0fc238a..HEAD -- '*.tsv'
[EMPTY]          <- goldens not refreshed, as claimed
```

Nothing pre-existing broken: `ShellIT` 129, `OCLExpressionIT` 1, `MavenCyclicDependenciesCoreTest` 11,
`MavenLayeredArchitectureTest` 1, `ModelAPITest` 1 all green.

---

## 2. Control first — INTACT

Pasted from my own acceptance run, not quoted from the porter:

```
=== detection power: control (a perfect port) =====================
seed                 20260817
operations           285  (stage-shaped domains)
rows                 19083
measured rows        17199
agreement rows       17199
verdict tally        {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
diverging operations 0   <- MUST be 0, or nothing below is attributable to a planted defect
stage passes         74 of 285  (isStagePass(1, none()))
why a PERFECT port is refused elsewhere:
    0 PASS   74
    2 refused: rows disagreed   41
    3 refused on more than one clause   51
    4 refused: not discriminating (D-15)   119
distinct throw-pairs a PERFECT port produces  154
===================================================================
```

0 `DIFFER`, 0 `MIXED`, 0 diverging operations, 74 stage passes, tally byte-identical to rounds 5 and 6.
**Control INTACT.** Everything below is attributable.

---

## 3. Detection power — no probe regressed

Extracted from my acceptance run (`verify1.log`), all eleven probes plus the wrong-type probe:

| probe | DIFFER | ops detected | stage passes (control 74) |
|---|---|---|---|
| P0-perfect (control) | 0 | 0 | 74 |
| P1-off-by-one-index | 52 (+86 MIXED) | 3 | 74 |
| P2-linear-uncertainty | 468 | 4 | 70 |
| P3-hypot-uncertainty | 24 | 4 | 70 |
| P4-le-for-lt | 280 | 6 | 70 |
| P5-round-10dp | 428 | 7 | 67 |
| P6-equals-ignores-uncertainty | 1119 | 4 | 72 |
| P7-undefined-on-zero-divisor | 105 (+62 MIXED) | 6 | 71 |
| P8-hides-behind-harness-error | 0 | **0** | 70 |
| P9-hides-behind-unsupported | 0 | **0** | 70 |
| P10-narrow-input-window | 0 | **0** | 74 |
| P11-negative-zero-collapse | 59 | 3 | 71 |
| P12-boxed-primitive (D-18) | 3445 | 182 | 45 |

Every figure matches rounds 5 and 6. Rows are 19083 for every probe. The blind-spot set is still
exactly one entry:

```
=== planted defects the harness did NOT see =======================
  ??? P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]
===================================================================
```

**No probe that was detected before is undetected now. No regression.**

---

## 4. D-18 — INTACT, not weakened

```
=== D-18: right content, wrong Java type =========================
operations           285
control  rows        19083, measured 17199, agreed 17199  {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
boxed    rows        19083, measured 17199, agreed 13754  {AGREE=13754, BOTH_THREW=910, DIFFER=3445, HARNESS_ERROR=883, UNMEASURABLE=91}
control DIFFER+MIXED 0   <- MUST be 0
boxed   DIFFER rows  3445
DETECTED on          182 of 285 operations
```

Structural confirmation that the check was not softened: `DifferentialSweep.java:243` computes
`boolean agree = ref.value.canonical().equals(sub.value.canonical())` and `typeNote` (with its new
`provenanceClause`) is called **only** on the `!agree` branch (`:245`). `canonical()` (`UValue.java:537`)
renders `content + "@" + typeToken()` and contains no provenance; `equals`/`hashCode`
(`UValue.java:616`, `:621`) delegate to `canonical()`. Provenance cannot reach a verdict. The 3445 is
also arithmetically confirmed by the reference census in the same run — `1562 Boolean + 1579 Integer +
90 Double + 214 String = 3445`.

The goldens were not refreshed and the smoke tests still pass on the committed byte digests.

---

## 5. Is D-43 closed? Half (a) yes, half (b) NO

The register (`stage-01.md` §10.5, D-43) states the defect in two halves. I reproduced both
independently, from a scratch copy of `PortedInfidelityDetectionPowerTest` reverted before commit
(`git status` empty; `md5sum` of the restored file `3a8c9a421c690c2c1b20b1381843a814`, identical to the
committed one).

### 5.1 Half (a) — the false-divergence half: CLOSED, reproduced

Reproduced the *before* state and the *after* state myself:

```
=== D-43: two readings of the same measurement ====================
  subject                              DIFFER        ops   passes notes ASSUMED
  P0-perfect                                0          0       74            0
  P12-boxed-primitive                    3445        182       45            0
  P13-factory-typed-adapter              3445        182       45         3445
  P14-observing-adapter                     0          0       74            0
```

A content-perfect port with a factory-typed adapter: 3445 `DIFFER`, 182 of 285 operations, 29 stage
passes lost. The same port with `observedFrom(returned)`: 0, 0, and the control's **exact** stage-pass
set and verdict tally. That is real and it is the porter's strongest result. **Half (a) is closed.**

### 5.2 Half (b) — the check-erasing half: OPEN. **DEFECT D-46, CRITICAL**

The register's own words for half (b): *"One line erased the check: a genuinely wrong-class port plus
`.asJavaType(v.javaType())` went `DIFFER 3 445 -> 0`."* The fix deletes `asJavaType(String)` and claims
the route now costs a disclosure. Measured, on the **same planted wrong-class port** as P12, with the
surviving route and a reason of one character:

```
=== R7b: a WRONG-CLASS port laundered two ways =====================
  subject                          DIFFER    MIXED   divOps   passes
  P0-perfect                            0        0        0       74
  P12-boxed-primitive                3445        0      182       45
  R1-fabricated-observation             0       91        8       74
  R2-false-declared-reason              0        0        0       74
  control  tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
  R2       tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, UNMEASURABLE=91}
  R2 tally == control tally ....... true
  R2 stagePasses == control ....... true
  R2 refusals   == control ........ true
  R2's declared reason was the single character "x" (non-blank, accepted)
```

`R2` is the D-18 defect the instrument exists to catch, plus
`wrong.declaredJavaType(truth.javaType(), "x")`. Its verdict tally, its stage-pass **set** and its
per-operation **refusal map** are all identical to the perfect-port control's. `DIFFER 3 445 -> 0`,
exactly as in round 6. Deleting `asJavaType` changed the arity and added an `isBlank()` check; it did
not change the outcome.

Now the disclosure that was supposed to compensate:

```
=== R7b: can a reader of the EVIDENCE see the declaration? =========
  P12 (honest wrong-class port)  rows with a non-empty note  5329
  R2  (same port, laundered)     rows with a non-empty note  1884
  R2  rows whose note carries the mandatory reason           0
```

**Zero.** `harness-contract.md:369-370` says:

> Round 7 removed the one-argument route: the only way to state a class is
> `declaredJavaType(String javaType, String why)`, a blank `why` is rejected, and **the reason is printed
> into the note of any row the declaration moved.**

That is false in the only direction that matters. The mechanism: `provenanceClause` is reachable only
from `typeNote` (`DifferentialSweep.java:274`), and `typeNote` returns `""` when the two
fully-qualified names are equal (`:263-266`) — which is precisely what a laundering declaration makes
true by construction. **The disclosure fires when a declaration *creates* a divergence and is silent
when it *erases* one.** The same sentence is repeated as a closed-defect residual in
`harness-contract.md:287` ("it just costs a sentence a reviewer reads"), `stage-01.md:1184` ("it now
costs a sentence a reviewer reads and the row says which route was taken") and
`foundation-verdict.md:101`. Measured, on the laundering construction, no row says which route was
taken.

Nor is this measured anywhere in the tree. Every committed use of `declaredJavaType` outside
`UValue`/`Candidate`/`StubCandidate` is at unit resolution
(`DifferentialHarnessRegressionTest.java:1029, 1056, 1121, 1123, 1126, 1142`). **Nothing measures
whether a declaration can erase a divergence at sweep scale.** Round 6 measured that it could; round 7
changed the call signature and stopped measuring it.

---

## 6. New defect: the fix asserts a provenance it cannot check. **D-47, MAJOR**

Round 7 added a sentence the harness prints in the indicative:

> Both classes were OBSERVED from the objects the two sides returned, so this row is a statement about
> the two implementations.

`observedFrom(Object)` believes any object. Measured, on a subject that constructs the object it
observes and never returned it from any port:

```
=== R7 ATTACK 3: what the harness says about a FABRICATED observation ===
  R3 DIFFER rows 1618, rows saying ASSUMED 0
      0	BooleanValue.compareTo(value)	...	INTEGER(0)@Integer	INTEGER(0)@AtomicInteger	DIFFER	java type mismatch: reference returned java.lang.Integer (INTEGER(0)@Integer) / subject returned java.util.concurrent.atomic.AtomicInteger (INTEGER(0)@AtomicInteger); the content is IDENTICAL -- right content, wrong Java type (defect D-18); this row is a divergence because a port of these classes must reproduce the declared result type, not only the payload. Both classes were OBSERVED from the objects the two sides returned, so this row is a statement about the two implementations.
```

1618 rows assert that the subject's class was observed from an object the subject returned. It was not.
Pre-round-7 the instrument made no such claim; the fix introduced it. `OBSERVED` is exactly as unchecked
as `DECLARED` — and it is **cheaper**: no reason is required, and the harness upgrades the row's prose
from a hedge to a certification.

Worse for the D-43 story, fabrication is the cheaper laundering route. `R1` above is the same
wrong-class port plus `observedFrom(<a boxed literal>)` — four cases, `Boolean.valueOf` /
`Integer.valueOf` / `Double.valueOf` / the `String` itself, no reflection and no knowledge of the port:

```
  R1       tally {AGREE=17199, BOTH_THREW=910, HARNESS_ERROR=883, MIXED=91}
  R1 stagePasses == control ....... true
  R1 diverging operations (8): [BooleanValue.setTypeToRuntimeType(), IntegerValue.setTypeToRuntimeType(),
      RealValue.setTypeToRuntimeType(), StringValue.setTypeToRuntimeType(), UBooleanValue.setTypeToRuntimeType(),
      UIntegerValue.setTypeToRuntimeType(), URealValue.setTypeToRuntimeType(), UStringValue.setTypeToRuntimeType()]
```

`DIFFER 0` and the control's **exact** stage-pass set, from a genuinely wrong-class port, with no
disclosure obligation of any kind. The 91 `UNMEASURABLE -> MIXED` residue is an artefact of my crude
fabricator throwing on the eight `setTypeToRuntimeType()` operations, not a property of the attack; a
fabricator that returns the value unchanged on non-primitive kinds has no residue. The porter named this
attack as his successor number (1) and judged that "constructing a plausible-but-wrong one is harder
than typing a string". For the 182 primitive-returning operations — the entire blast radius of D-43 and
D-18 — it is **one boxed literal**, and it buys a stronger claim in the evidence than the string ever
did.

---

## 7. New defect: the 3 445 signature *hides* real type defects. **D-48, MAJOR**

This is the construction round 7 did not run, and it is the answer to the brief's bullet 3 ("check
specifically whether the type comparison can now be bypassed by an adapter that simply does not
attribute").

I built the **mirror** of the planted D-18 defect: a port that returns a raw box where the historical
side returns a `Value` class. Seen through an *observing* adapter it is a real, detectable infidelity —
**401 `DIFFER` rows across 9 operations, 74 -> 70 stage passes**:

```
=== R7c: ONE wrong-class port, TWO adapters ========================
  subject                                      DIFFER   divOps   passes  ASSUMED
  P0-perfect                                        0        0       74        0
  R4-unboxing-port-OBSERVING-adapter              401        9       70        0
  R5-unboxing-port-NON-ATTRIBUTING-adapter       3445      182       45     3445
  operations the OBSERVING adapter caught the same port on: 9
  a sample of them: [UBooleanValue.equalsC(value,double), UBooleanValue.toBoolean(), UIntegerValue.toInteger(),
      UIntegerValue.toReal(), URealValue.toInteger(), URealValue.toReal(), UStringValue.at(int), UStringValue.toBoolean()]
```

Now the same port through a non-attributing adapter, against a port with **no defect at all**:

```
=== R7d: a port with NO defect vs a port with a REAL type defect ===
     ... both seen through an adapter that does not attribute
  subject                                      DIFFER   divOps   passes  ASSUMED
  P13-factory-typed-adapter                      3445      182       45     3445
  R5-unboxing-port-NON-ATTRIBUTING-adapter       3445      182       45     3445
  verdict tallies equal ......... true
  stage-pass sets equal ......... true
  refusal maps equal ............ true
  diverging-operation sets equal  true
  rows compared ................. 19083
  rows byte-identical (note incl.) 19083
  the real defect, when its adapter DOES observe: 401 DIFFER / 9 ops / 70 passes
===================================================================
```

**19083 of 19083 rows byte-identical, notes included.** A defect-free port and a port carrying a
401-row wrong-class infidelity produce the *same sweep*, down to the byte, when the adapter does not
attribute. And every one of the 3445 notes says:

> this row may be an adapter defect and not a port defect (D-43), and an adapter must attribute through
> `UValue.observedFrom(Object)`

A reader who follows that hedge — which is the whole purpose of the clause — discards 401 real
divergences. The 3 445 signature is not merely *ambiguous* between an adapter defect and a port defect,
as §10.8 has it; it is an **absorbing state** that a real type defect of this shape falls into and
cannot be recovered from. Provenance was correctly kept out of the verdict, but it was put into the
*reader's* decision procedure, which is where the D-15 / D-17 family has always lived. This mode is
reachable by pure omission — no malice, no laundering, just the adapter shape the documentation calls
natural.

---

## 8. The porter's own flagged gaps, now measured. **D-49, MAJOR**

The porter flagged two limitations separately and judged them not worth building. Measured, they
compose into one:

**The published header cannot tell the two findings apart.** I wrote full reports for P12 (a port
defect) and P13 (an adapter defect) through `DiffReportWriter.writeAll`:

```
header lines: 1885 vs 1885
=== the ONLY differing header lines ===
4c4
< # subject	P12-boxed-primitive
---
> # subject	P13-factory-typed-adapter
```

1885 header lines each, differing in **exactly one line — the name the porter chose for the subject**.
No header key mentions `OBSERVED`, `DECLARED` or `ASSUMED`.

**And the note never appears in the shipped evidence.** Both S1 goldens:

```
$ for f in s1-smoke-ureal-add.tsv s1-smoke-ureal-minus-faulty.tsv; do grep -c 'DECLARED\|OBSERVED\|ASSUMED' $f; done
0
0
```

785 lines each, 226 of them `DIFFER` rows, produced by a subject (`StubCandidate`) whose token is a
hand-written declaration — and **not one row says so**, because `typeNote` returns `""` when the FQNs
match and a matching declaration is exactly that case. The porter's framing ("agreement rows are still
unmarked") understates it: *all* rows are unmarked whenever the declaration is right, including
divergences.

Put beside §7's measurement — 19083 of 19083 rows identical **excluding** the note — this is load-bearing:
the row note is the instrument's **only** discriminator between a port defect and an adapter defect, and
it is absent from every report header, absent from every verdict count, and absent from all 1570 rows of
S1's own committed evidence. That is the D-21 shape (a file-level number hiding a per-operation fact)
applied to attribution, and the porter's judgement that a golden refresh was worse value is the one I
would reverse.

---

## 9. Smaller findings

**D-50, MINOR — two closure figures are stated, never measured.** D-44's "197 rows across 17
operations" appears at `UValue.java:71`, `DifferentialHarnessRegressionTest.java:1048` and inside an
assertion *message* at `:1077`; no assertion computes it. The Javadoc says "Asserted below so the
boundary is measured rather than assumed" — the *boundary* (that two OPAQUE canonicals differ under
relocation) is asserted; the *figure* is not. Same for D-45's "84 declare an interface or a non-final
class". Both are the D-45 shape applied to their own closures. Not a scoring defect; the porter should
either measure them or mark them as stated.

**D-51, MINOR — the worked-example snippet omits `void`.** `Candidate`'s new snippet is
`invoke` -> `if (returned == null) return UValue.nullValue();` -> `observedFrom(returned)`. For a
`void`-declared port method `Method.invoke` returns `null`, so an adapter copying the snippet literally
returns `nullValue()` where the reference returns `voidValue()` (`HistoricalOracle.invokeRaw` maps
`void.class` to `VOID_RESULT`). Consequence is nil — `DifferentialSweep:231` routes two non-observations
to `UNMEASURABLE` either way — so this is documentation incompleteness only, on the 8 void mutators.
`UValue.observedFrom`'s own Javadoc gets it right; the snippet an adapter is told to copy does not.

**Correction to the brief, not a defect.** "All seven non-port subjects still zero agreement" is not
what the tree asserts, and should not be repeated. `UnwrittenPortInvariantTest` splits them by
`Observability`: the five `NOTHING` subjects assert zero agreement and measure zero
(`a-throws` 0, `b-returns-java-null` 0, `c-empty-body` 0, `d-returns-null-value` 0, `g-throws-error` 0);
the two `WRONG_VALUES` subjects correctly assert `differRows() > 0` plus a pinned fully-agreed set, and
measure `e-fixed-constant` **8240** agreement rows and `f-echoes-receiver` **4567**. The invariant is
intact and correctly scoped; the one-line paraphrase is wrong.

---

## 10. What round 7 got right

Stated plainly, because most of it is right and the verdict should not obscure that:

* The control is intact and unmoved: 0 / 0 / 74, tally identical to rounds 5 and 6.
* D-18 is intact and not weakened. The mechanism is structurally incapable of reaching a verdict —
  verified by reading `DifferentialSweep:243-245` and `UValue:537, 616, 621`, not by trusting the claim.
* Half (a) of D-43 is genuinely closed, and `P14` reaching the control's *exact* stage-pass set and
  verdict tally is a stronger assertion than the "0" it could have settled for.
* `observedFrom` on all fourteen `fromHistorical` branches is real (`grep -c` = 14) and the symmetry
  argument for it is correct.
* Deleting `asJavaType(String)` was the right call for the reason given, even though it did not have the
  effect claimed.
* The deliberate deviation on `StubCandidate` is **sound**, and the porter was right to measure rather
  than argue it: `typeNote` compares fully-qualified names, so an observed stand-in in this package
  really would re-caption S1's 226 disagreeing golden rows as a type mismatch. I did not re-run that
  scratch measurement (the porter flags it as the one figure not reproducible from the committed tree),
  but the mechanism is visible in `DifferentialSweep:263-266` and I confirmed the 226/558 split and the
  zero type-mismatch notes in both goldens directly.
* D-44 and D-45 were folded in honestly, and D-45's withdrawal of a false Javadoc rationale is a model
  of how to close a defect of that kind.
* Every process claim checked out: counts, empty `*/src/main/*` diff, clean tree, unrefreshed goldens,
  determinism.

---

## 11. Verdict and defect register delta

**DEFECTIVE.** Not for a regression — there is none — but because a MAJOR the record calls **CLOSED** is
open in a half the record itself defines, the compensating disclosure is asserted in three documents and
measured absent, and two unforeseen modes make the instrument's new attribution machinery either false
(D-47) or actively misleading (D-48).

| id | severity | status |
|---|---|---|
| D-43 | MAJOR | **half (a) CLOSED and reproduced; half (b) OPEN** — re-open, do not re-key |
| D-46 | **CRITICAL** | new. `declaredJavaType` erases the check at sweep scale (3445 -> 0, control-identical) and discloses in 0 rows; three documents claim otherwise |
| D-47 | MAJOR | new. `observedFrom` certifies a fabricated observation; 1618 rows assert an unverifiable claim as fact; fabrication is the cheaper laundering route |
| D-48 | MAJOR | new. the 3445 signature is an absorbing state: a 401-row real defect and a defect-free port are byte-identical on 19083/19083 rows |
| D-49 | MAJOR | new (porter-flagged, now measured). headers differ only in `# subject`; 0 provenance mentions in 1570 golden rows |
| D-50 | MINOR | new. 197/17 and 84/285 are stated, never asserted |
| D-51 | MINOR | new. the `Candidate` snippet omits the `void` case |
| D-30, D-29, D-17/D-32, D-20, D-41 | — | untouched, exactly where round 6 left them |

Open MAJORs: 4 -> 5 -> 4 -> **7 (one CRITICAL)**. The rise is not deterioration of the instrument; it is
the first round in which the attribution machinery existed to be attacked.

## 12. The named fix, if the porter wants one

Smallest change that closes D-46 and D-49 together, and it is a mechanism rather than a warning:

1. **Make `typeNote` fire on provenance, not only on FQN inequality.** The current early return at
   `DifferentialSweep:263-266` is the bug. A row whose subject token is `DECLARED` or `ASSUMED` must
   carry that fact **whatever the verdict**, including `AGREE`. This changes both goldens; that is the
   price, and D-49's measurement is the argument that it is worth paying.
2. **Aggregate it.** `# rows.subjectTypeObserved / .subjectTypeDeclared / .subjectTypeAssumed` and the
   same three per operation in the `# op.<key>.*` block. Then no stage can publish a type-fidelity
   figure whose attribution a reader has to reconstruct from rows.
3. **Pin half (b).** A sweep-scale test asserting that the laundered wrong-class port
   (`declaredJavaType(referenceToken, …)`) is **distinguishable** from the control — by the aggregate
   from (2), since by construction it is not distinguishable by any verdict.
4. **Stop asserting what cannot be checked.** Replace "Both classes were OBSERVED … so this row is a
   statement about the two implementations" with a form that does not vouch for the adapter, e.g.
   "Neither side's class was declared or assumed; whether the subject observed the right object is not
   checkable by this harness."
5. Correct `harness-contract.md:369-370`, `:287`, `stage-01.md:1184` and `foundation-verdict.md:101`.

## 13. Fitness for S4

**S4 must not start on this instrument as it stands** — not because it cannot measure content fidelity
(it can, well, and the control proves it) but because the *first* thing an S4 adapter does is choose how
to obtain its type token, and today that choice is unconstrained, undisclosed in every published
artefact, and can silently absorb a real defect. Items 1-3 of §12 are the condition. They are one commit
and one deliberate golden refresh.
