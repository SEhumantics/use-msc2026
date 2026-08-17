package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs an operation on both candidates over a fixed, replayable list of argument tuples and turns
 * each application into a {@link DiffRow}.
 *
 * <p>The sweep never decides that a difference is acceptable, and — since the measurement recorded
 * in {@link DiffVerdict} — it never decides that two failures are a similarity either. It records
 * what each side did and classifies it; whether a run is a pass is the caller's judgement, made
 * against {@link Result#isClean()}.
 *
 * <p>Deliberately <em>not</em> {@link Result#disagreements()} alone. That list is empty for a sweep
 * that compared nothing at all — an empty input domain, an operation that returns {@code void} on
 * every row — and "nothing disagreed" is not "everything agreed". {@link Result#measurementCount()}
 * is the size of the evidence, and {@link Result#isClean()} is the two conditions together.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class DifferentialSweep {

    private final Candidate reference;
    private final Candidate subject;
    private final long seed;
    private final AcceptedThrowPairs acceptedThrowPairs;

    /**
     * The ordinary constructor: no throw-pair is an agreement.
     *
     * @param reference the historical side (its output populates the {@code historical} column)
     * @param subject   the ported side (its output populates the {@code ported} column)
     * @param seed      the seed that produced the inputs, recorded in the report header
     */
    public DifferentialSweep(Candidate reference, Candidate subject, long seed) {
        this(reference, subject, seed, AcceptedThrowPairs.none());
    }

    /**
     * As above, with an explicit allowlist of reviewed throw-pairs. Use this only for shared error
     * paths that a stage has read and signed off in writing — see {@link AcceptedThrowPairs}.
     */
    public DifferentialSweep(Candidate reference, Candidate subject, long seed,
                             AcceptedThrowPairs acceptedThrowPairs) {
        this.reference = reference;
        this.subject = subject;
        this.seed = seed;
        this.acceptedThrowPairs = Objects.requireNonNull(acceptedThrowPairs,
                "acceptedThrowPairs (use AcceptedThrowPairs.none())");
    }

    /** The reviewed throw-pairs this sweep will adjudicate; empty unless a caller supplied some. */
    public AcceptedThrowPairs acceptedThrowPairs() {
        return acceptedThrowPairs;
    }

    public long seed() {
        return seed;
    }

    public Candidate reference() {
        return reference;
    }

    public Candidate subject() {
        return subject;
    }

    /**
     * Cartesian product sweep: applies {@code op} to every combination drawn from {@code domains},
     * where {@code domains.get(0)} supplies receivers and the rest supply arguments in order.
     */
    public Result sweep(UOp op, List<List<UValue>> domains) {
        if (domains.size() != op.arity()) {
            throw new IllegalArgumentException(
                    op.key() + " has arity " + op.arity() + " but " + domains.size() + " domains were given");
        }
        List<List<UValue>> tuples = new ArrayList<>();
        buildTuples(domains, 0, new ArrayList<>(), tuples);
        return run(op, tuples);
    }

    /** Convenience for a unary operation. */
    public Result sweepUnary(UOp op, List<UValue> receivers) {
        return sweep(op, java.util.Collections.singletonList(receivers));
    }

    /** Convenience for a binary operation over one domain used on both sides. */
    public Result sweepBinary(UOp op, List<UValue> receivers, List<UValue> arguments) {
        return sweep(op, Arrays.asList(receivers, arguments));
    }

    private static void buildTuples(List<List<UValue>> domains, int depth, List<UValue> prefix,
                                    List<List<UValue>> out) {
        if (depth == domains.size()) {
            out.add(new ArrayList<>(prefix));
            return;
        }
        for (UValue v : domains.get(depth)) {
            prefix.add(v);
            buildTuples(domains, depth + 1, prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    /** Runs {@code op} over an explicit, already-ordered list of argument tuples. */
    public Result run(UOp op, List<List<UValue>> tuples) {
        List<DiffRow> rows = new ArrayList<>(tuples.size());
        boolean refSupports = reference.supports(op);
        boolean subSupports = subject.supports(op);

        for (int i = 0; i < tuples.size(); i++) {
            List<UValue> tuple = tuples.get(i);
            List<String> inputs = new ArrayList<>(tuple.size());
            for (UValue v : tuple) {
                inputs.add(v.canonical());
            }

            if (!refSupports || !subSupports) {
                // The reason comes from the candidate, not from this class. "does not implement" was
                // asserted here for both causes, and it is false for the commonest one: the
                // historical jar does declare SBooleanValue.and(Value) -- it is this harness that
                // cannot marshal an SBoolean receiver. See Candidate.unsupportedReason.
                //
                // Both sides are named even when only one of them is the problem: an unattributed
                // reason in a two-sided note is a reason the reader has to guess the owner of.
                String note = "no measurement. "
                        + (refSupports ? "reference: could be driven"
                                       : "reference: " + reference.unsupportedReason(op))
                        + " / "
                        + (subSupports ? "subject: could be driven"
                                       : "subject: " + subject.unsupportedReason(op));
                rows.add(new DiffRow(i, op.key(), inputs,
                        refSupports ? "" : "UNSUPPORTED", subSupports ? "" : "UNSUPPORTED",
                        DiffVerdict.UNSUPPORTED, note));
                continue;
            }

            Outcome refOutcome = apply(reference, op, tuple);
            Outcome subOutcome = apply(subject, op, tuple);
            rows.add(classify(i, op, inputs, refOutcome, subOutcome, acceptedThrowPairs));
        }
        return new Result(op, seed, reference.name(), subject.name(), rows);
    }

    /**
     * Drives one candidate on one tuple and classifies the outcome into exactly one of three
     * populations.
     *
     * <ul>
     *   <li><b>returned</b> — a non-null {@link UValue}.</li>
     *   <li><b>harness error</b> — {@link HarnessMarshallingException} only, caught before
     *       {@code Exception} so it can never be scored as a throw by the code under test. The null
     *       check raises this type on purpose: a {@link Candidate} that returns Java {@code null}
     *       (the natural mistake for a ported operation whose result maps to {@code UndefinedValue})
     *       has broken its contract, and no comparable value exists. It used to raise
     *       {@link NullPointerException} instead, which fell into the throw population — so two
     *       candidates that both returned {@code null} scored as a matching throw, i.e. a contract
     *       violation on both sides was reported as agreement.</li>
     *   <li><b>threw</b> — {@link Exception} and nothing wider. {@link Error} is re-thrown:
     *       {@code StackOverflowError}, {@code AssertionError} and {@code NoClassDefFoundError}
     *       describe a broken JVM or a broken build, not a behavioural difference, and turning them
     *       into comparable report data would let a sweep agree that both sides are broken.</li>
     * </ul>
     */
    private static Outcome apply(Candidate candidate, UOp op, List<UValue> tuple) {
        try {
            UValue produced = candidate.invoke(op, tuple);
            if (produced == null) {
                throw new HarnessMarshallingException(candidate.name() + " returned Java null from "
                        + op.key() + "; a Candidate must return a UValue (use UValue.nullValue() for "
                        + "a genuine null result). No comparable value exists, so this row is not a "
                        + "measurement.");
            }
            return Outcome.returned(produced);
        } catch (HarnessMarshallingException e) {
            return Outcome.harnessError(e);
        } catch (Exception e) {
            return Outcome.threw(e);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            // Candidate.invoke is declared `throws Throwable`; anything that is neither an Exception
            // nor an Error is not comparable data either.
            throw new IllegalStateException(candidate.name() + " threw a Throwable that is neither an "
                    + "Exception nor an Error from " + op.key(), t);
        }
    }

    private static DiffRow classify(int index, UOp op, List<String> inputs, Outcome ref, Outcome sub,
                                    AcceptedThrowPairs accepted) {
        if (ref.harnessError != null || sub.harnessError != null) {
            // Checked first and never merged with the throw population below: a harness failure is
            // the absence of a measurement, not a measurement that two sides happen to share.
            String which = ref.harnessError != null && sub.harnessError != null ? "either side"
                    : ref.harnessError != null ? "the reference" : "the subject";
            return new DiffRow(index, op.key(), inputs, column(ref), column(sub),
                    DiffVerdict.HARNESS_ERROR,
                    "no measurement on " + which + "; no comparison was made. " + evidence(ref, sub));
        }
        if (ref.thrown != null && sub.thrown != null) {
            // The note carries both classes AND both messages, always, whether or not the classes
            // match. This class no longer forms an opinion about whether two failures are the same
            // failure; it records what it saw and hands the reader the evidence. The only way out of
            // BOTH_THREW is an entry a human wrote into AcceptedThrowPairs.
            String rationale = accepted.rationaleFor(op, ref.thrown, sub.thrown);
            return new DiffRow(index, op.key(), inputs, DiffRow.thrown(ref.thrown),
                    DiffRow.thrown(sub.thrown),
                    rationale == null ? DiffVerdict.BOTH_THREW : DiffVerdict.ACCEPTED_THROW,
                    rationale == null ? evidence(ref, sub)
                            : "adjudicated: " + rationale + ". " + evidence(ref, sub));
        }
        if (ref.thrown != null || sub.thrown != null) {
            // Which side threw, named in the lead clause. It used to read "one side threw and the
            // other returned", leaving the reader to recover the attribution from the columns --
            // over 52 196 rows of the standing invariant sweep. The evidence() clause that follows
            // has always been attributed; the summary sentence in front of it now is too.
            String lead = ref.thrown != null
                    ? "the reference threw and the subject returned. "
                    : "the subject threw and the reference returned. ";
            return new DiffRow(index, op.key(), inputs,
                    ref.thrown != null ? DiffRow.thrown(ref.thrown) : ref.value.canonical(),
                    sub.thrown != null ? DiffRow.thrown(sub.thrown) : sub.value.canonical(),
                    DiffVerdict.MIXED,
                    lead + evidence(ref, sub));
        }
        if (!ref.value.carriesAnObservation() && !sub.value.carriesAnObservation()) {
            // Neither side produced a value. Comparing the two absences and finding them equal is
            // the AGREE_THROWN mistake in its third costume: for a void operation the reference's
            // VOID comes from method.getReturnType(), not from behaviour, and the subject's comes
            // from the boilerplate Candidate tells an adapter to write, so the verdict is decided
            // before either implementation runs. Note that one-sided absence is NOT routed here --
            // that is a real difference and falls through to DIFFER below.
            return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                    DiffVerdict.UNMEASURABLE, unmeasurableNote(ref, sub));
        }
        boolean agree = ref.value.canonical().equals(sub.value.canonical());
        return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                agree ? DiffVerdict.AGREE : DiffVerdict.DIFFER, "");
    }

    private static String unmeasurableNote(Outcome ref, Outcome sub) {
        boolean voidOperation = ref.value.kind() == UValue.Kind.VOID
                || sub.value.kind() == UValue.Kind.VOID;
        String why = voidOperation
                ? "the operation is declared void, so it has no result, and this harness does not "
                        + "re-read the receiver after a call -- no post-state was observed on either "
                        + "side, so nothing about either implementation was measured here"
                : "neither side produced a value, and a pair of non-values is not a shared value";
        return "no measurement: " + why + ". " + evidence(ref, sub);
    }

    /**
     * What each side actually did, both sides, always. Every note on a non-agreement row ends with
     * this: the harness holds the evidence that distinguishes the two sides, and a note that carries
     * one side's message while describing both — or that carries neither — destroys the very thing
     * it exists to record. That is how a {@code BOTH_THREW} row used to reach the report with an
     * empty note, and how a two-sided {@code HARNESS_ERROR} row used to say "either side" and then
     * quote only the reference's message while the subject's appeared nowhere at all: both columns
     * read {@code HARNESS_ERROR:...HarnessMarshallingException}, so it was not recoverable from the
     * row either.
     */
    private static String evidence(Outcome ref, Outcome sub) {
        return "reference " + describe(ref) + " / subject " + describe(sub);
    }

    private static String describe(Outcome outcome) {
        if (outcome.harnessError != null) {
            return "could not be driven: " + outcome.harnessError.getClass().getName() + ": "
                    + safeMessage(outcome.harnessError);
        }
        if (outcome.thrown != null) {
            return "threw " + outcome.thrown.getClass().getName() + ": " + safeMessage(outcome.thrown);
        }
        return "returned " + outcome.value.canonical();
    }

    /** The report column for one outcome, whichever of the three populations it fell into. */
    private static String column(Outcome outcome) {
        if (outcome.harnessError != null) {
            return DiffRow.harnessError(outcome.harnessError);
        }
        if (outcome.thrown != null) {
            return DiffRow.thrown(outcome.thrown);
        }
        return outcome.value.canonical();
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "(no message)" : m;
    }

    private static final class Outcome {
        final UValue value;
        final Throwable thrown;
        /** Non-null exactly when the harness itself failed; never merged with {@link #thrown}. */
        final Throwable harnessError;

        private Outcome(UValue value, Throwable thrown, Throwable harnessError) {
            this.value = value;
            this.thrown = thrown;
            this.harnessError = harnessError;
        }

        static Outcome returned(UValue v) {
            return new Outcome(v, null, null);
        }

        static Outcome threw(Throwable t) {
            return new Outcome(null, t, null);
        }

        static Outcome harnessError(Throwable t) {
            return new Outcome(null, null, t);
        }
    }

    /** The rows produced by one sweep, plus the tallies a caller needs to decide pass or fail. */
    public static final class Result {

        /**
         * The number of distinct reference values below which an operation cannot support a fidelity
         * claim. Two: an operation that answered the same thing on every row could not have failed,
         * so agreement on it was decided before either implementation ran.
         */
        public static final int DISCRIMINATING_MINIMUM = 2;

        private final UOp op;
        private final long seed;
        private final String referenceName;
        private final String subjectName;
        private final List<DiffRow> rows;
        private final Map<DiffVerdict, Integer> tally;

        Result(UOp op, long seed, String referenceName, String subjectName, List<DiffRow> rows) {
            this.op = op;
            this.seed = seed;
            this.referenceName = referenceName;
            this.subjectName = subjectName;
            this.rows = java.util.Collections.unmodifiableList(new ArrayList<>(rows));
            Map<DiffVerdict, Integer> counts = new EnumMap<>(DiffVerdict.class);
            for (DiffVerdict v : DiffVerdict.values()) {
                counts.put(v, 0);
            }
            for (DiffRow row : this.rows) {
                counts.merge(row.verdict(), 1, Integer::sum);
            }
            this.tally = java.util.Collections.unmodifiableMap(counts);
        }

        public UOp op() {
            return op;
        }

        public long seed() {
            return seed;
        }

        public String referenceName() {
            return referenceName;
        }

        public String subjectName() {
            return subjectName;
        }

        public List<DiffRow> rows() {
            return rows;
        }

        public int rowCount() {
            return rows.size();
        }

        public Map<DiffVerdict, Integer> tally() {
            return tally;
        }

        public int count(DiffVerdict verdict) {
            return tally.getOrDefault(verdict, 0);
        }

        /**
         * Every row whose verdict is not an agreement. An empty list is the only clean outcome.
         *
         * <p>Exactly complementary to {@link #agreements()} by construction — both delegate to
         * {@link #partition(boolean)} against the single predicate {@link DiffVerdict#isAgreement()}.
         * Two independently written accessors are how a verdict comes to be a non-agreement in one
         * and invisible in the other, which is the same defect renamed.
         */
        public List<DiffRow> disagreements() {
            return partition(false);
        }

        /**
         * Every row whose verdict <em>is</em> an agreement. Exposed so a caller can assert on the
         * agreement population directly instead of inferring it from a tally, and so that
         * {@code agreements().size() + disagreements().size() == rowCount()} is checkable.
         */
        public List<DiffRow> agreements() {
            return partition(true);
        }

        private List<DiffRow> partition(boolean agreement) {
            List<DiffRow> out = new ArrayList<>();
            for (DiffRow row : rows) {
                if (row.verdict().isAgreement() == agreement) {
                    out.add(row);
                }
            }
            return out;
        }

        /** Number of rows in which two comparable values, or an adjudicated throw-pair, agreed. */
        public int agreementCount() {
            int n = 0;
            for (DiffRow row : rows) {
                if (row.verdict().isAgreement()) {
                    n++;
                }
            }
            return n;
        }

        /**
         * <strong>Rows in which a comparison actually happened</strong>: {@link DiffVerdict#AGREE}
         * plus {@link DiffVerdict#DIFFER}, the two verdicts for which the harness held one observed
         * value from each side. This, not {@link #rowCount()}, is the size of the evidence.
         *
         * <p>The distinction is not academic. A sweep of 471 471 rows against a subject whose every
         * method body throws contains <em>zero</em> measurements; so does one against a subject
         * whose every body returns Java {@code null}; so does an all-{@link DiffVerdict#UNSUPPORTED}
         * or all-{@link DiffVerdict#UNMEASURABLE} sweep. Every one of those reports a large, healthy
         * row count. Before this accessor existed the only aggregates on offer were a row count and
         * a disagreement list, and a run that measured nothing was indistinguishable, at this level,
         * from a run that measured everything and found it right.
         *
         * @see #isClean()
         */
        public int measurementCount() {
            int n = 0;
            for (DiffRow row : rows) {
                if (row.verdict().isMeasurement()) {
                    n++;
                }
            }
            return n;
        }

        /** The rows a comparison actually happened on. Complement of nothing; a strict subset. */
        public List<DiffRow> measurements() {
            List<DiffRow> out = new ArrayList<>();
            for (DiffRow row : rows) {
                if (row.verdict().isMeasurement()) {
                    out.add(row);
                }
            }
            return out;
        }

        /**
         * <strong>Necessary and not sufficient</strong>: something was measured, and nothing
         * disagreed.
         *
         * <p>{@code disagreements().isEmpty()} alone is not a pass predicate and must not be used as
         * one. It is true of a sweep whose input domain was empty, of a sweep every row of which is
         * a void operation, and of any other sweep that compared nothing — "no row disagreed" is
         * vacuously true when no row was a comparison.
         *
         * <p><strong>This predicate is not a pass predicate either, and a stage must not use it as
         * one.</strong> It says the measured rows agreed; it says nothing about whether they
         * <em>could</em> have disagreed. On the 120 operations whose reference side answers the same
         * thing on every input the shipped corpora can supply, a subject consisting of one hardcoded
         * literal is {@code isClean() == true} — measured, agreed, report written, header reading
         * {@code # rows.disagreement 0} — and not one row of it is evidence about a port. That is
         * defect D-15, and the row-level verdicts are all correct: the false statement is at sweep
         * level, which is the level a stage reads.
         *
         * <p>The stage-facing predicate is {@link #isStagePass(int, AcceptedDegenerateOperations)},
         * which adds a measurement floor and the discrimination clause. Use that. This one remains
         * because it is exactly the right question for the harness's own regression tests, where the
         * codomain of the synthetic operation is known by construction.
         *
         * @see #isDiscriminating()
         */
        public boolean isClean() {
            return measurementCount() > 0 && disagreements().isEmpty();
        }

        // -------------------------------------------------------------- discriminating power

        /**
         * <strong>The distinct canonical values the reference side produced across the measured
         * rows</strong>, in sorted order.
         *
         * <p>Counted over {@link #measurements()} — {@link DiffVerdict#AGREE} plus
         * {@link DiffVerdict#DIFFER} — because those are exactly the rows an agreement figure can
         * come from. A row on which the reference threw, or on which the harness failed, tells a
         * reader nothing about the range of answers the operation has.
         *
         * <p>This is the statistic whose absence let three separate defects look like one another's
         * opposites. An operation is only evidence of fidelity if its reference side <em>could</em>
         * have answered differently; when it could not, {@code AGREE} is decided before either
         * implementation runs, and every safeguard in this class is right to let it through.
         */
        public java.util.SortedSet<String> referenceValues() {
            java.util.SortedSet<String> out = new java.util.TreeSet<>();
            for (DiffRow row : rows) {
                if (row.verdict().isMeasurement()) {
                    out.add(row.historical());
                }
            }
            return java.util.Collections.unmodifiableSortedSet(out);
        }

        /** Size of {@link #referenceValues()}. Zero when nothing was measured. */
        public int distinctReferenceValues() {
            return referenceValues().size();
        }

        /**
         * The one value the reference gave on every measured row, or {@code null} when it gave none
         * or more than one. This is the key an {@link AcceptedDegenerateOperations} sign-off is
         * written against, so that a sign-off lapses the moment the operation's single answer
         * changes.
         */
        public String soleReferenceValue() {
            java.util.SortedSet<String> values = referenceValues();
            return values.size() == 1 ? values.first() : null;
        }

        /**
         * <strong>Could this sweep have failed?</strong> True when the reference side produced at
         * least {@link #DISCRIMINATING_MINIMUM} distinct values across the measured rows.
         *
         * <p>False has two causes and they are not the same, which is why the number and not only
         * the boolean is published: {@code distinctReferenceValues() == 0} means nothing was
         * measured at all, and {@code == 1} means the operation's codomain over this domain is a
         * single point.
         */
        public boolean isDiscriminating() {
            return distinctReferenceValues() >= DISCRIMINATING_MINIMUM;
        }

        // -------------------------------------------------------------- the stage gate

        /**
         * <strong>The stage-facing pass predicate.</strong> All three clauses, or no pass:
         *
         * <ol>
         *   <li>at least {@code minimumMeasurements} rows compared two observed values, and at least
         *       one did;</li>
         *   <li>no row disagreed;</li>
         *   <li>the operation is {@link #isDiscriminating() discriminating} — <em>or</em> it is
         *       single-valued and {@code acknowledged} carries a written, keyed sign-off for exactly
         *       this operation and exactly this value.</li>
         * </ol>
         *
         * <p>Clause 3 is the D-15 gate. It is a mechanism and not a convention on purpose: the rule
         * "quote distinct reference values alongside any agreement figure" was written into
         * {@code harness-contract.md} and a rule a human has to remember is not a property the
         * instrument enforces. {@link AcceptedDegenerateOperations#none()} is the default and must be
         * passed explicitly, so a caller cannot reach a pass on a degenerate operation without
         * naming the mechanism that let them.
         *
         * @param minimumMeasurements floor on the evidence, derived from the corpus and written down
         *                            in the stage document <em>before</em> the run
         * @param acknowledged        sign-offs for genuinely-constant operations; never {@code null}
         * @see #requireStagePass(int, AcceptedDegenerateOperations)
         */
        public boolean isStagePass(int minimumMeasurements, AcceptedDegenerateOperations acknowledged) {
            return stageGateFailures(minimumMeasurements, acknowledged).isEmpty();
        }

        /**
         * {@link #isStagePass(int, AcceptedDegenerateOperations)}, but throws with <em>every</em>
         * failing clause and the numbers behind it rather than returning a bare {@code false}.
         *
         * @return {@code this}, so it can be chained onto a sweep call
         * @throws IllegalStateException if any clause fails
         */
        public Result requireStagePass(int minimumMeasurements,
                                       AcceptedDegenerateOperations acknowledged) {
            List<String> failures = stageGateFailures(minimumMeasurements, acknowledged);
            if (!failures.isEmpty()) {
                StringBuilder sb = new StringBuilder("sweep of ").append(op.key())
                        .append(" is not a stage pass:");
                for (String failure : failures) {
                    sb.append("\n  - ").append(failure);
                }
                sb.append("\n  tally: ").append(summary());
                throw new IllegalStateException(sb.toString());
            }
            return this;
        }

        /** Every clause of the stage gate this result fails, in order; empty means pass. */
        public List<String> stageGateFailures(int minimumMeasurements,
                                              AcceptedDegenerateOperations acknowledged) {
            Objects.requireNonNull(acknowledged,
                    "acknowledged (use AcceptedDegenerateOperations.none())");
            if (minimumMeasurements < 1) {
                throw new IllegalArgumentException("minimumMeasurements must be at least 1, got "
                        + minimumMeasurements + ": a floor of zero is not a floor, and a sweep that "
                        + "measured nothing must never read as a pass");
            }
            List<String> failures = new ArrayList<>();
            int measured = measurementCount();
            if (measured < minimumMeasurements) {
                failures.add("measured " + measured + " row(s) of " + rowCount() + ", needed at least "
                        + minimumMeasurements + ". A result with too little evidence is not evidence.");
            }
            int disagreed = disagreements().size();
            if (disagreed > 0) {
                failures.add(disagreed + " row(s) did not agree.");
            }
            int distinct = distinctReferenceValues();
            if (distinct < DISCRIMINATING_MINIMUM) {
                String sole = soleReferenceValue();
                String rationale = sole == null ? null : acknowledged.rationaleFor(op.key(), sole);
                if (rationale == null) {
                    failures.add("the reference side produced " + distinct + " distinct value(s) "
                            + "across " + measured + " measured row(s)"
                            + (sole == null ? "" : ", always " + sole)
                            + ". This operation could not have failed over this domain, so agreement "
                            + "on it is decided before either implementation runs and is not evidence "
                            + "of fidelity (defect D-15). Either widen the domain until the reference "
                            + "answers differently, or sign the operation off in "
                            + "AcceptedDegenerateOperations with a written rationale — which is copied "
                            + "into the report, so the weakness travels with the number.");
                }
            }
            return failures;
        }

        /**
         * The one-line statement a stage must publish next to any figure taken from this sweep:
         * measured rows, agreement rows, distinct reference values, and — when the operation is
         * degenerate and signed off — the rationale verbatim.
         *
         * <p>There is deliberately no way to render an agreement figure from this class without the
         * discrimination figure beside it.
         */
        public String stageStatement(AcceptedDegenerateOperations acknowledged) {
            Objects.requireNonNull(acknowledged,
                    "acknowledged (use AcceptedDegenerateOperations.none())");
            StringBuilder sb = new StringBuilder(op.key())
                    .append(": ").append(rowCount()).append(" rows, ")
                    .append(measurementCount()).append(" measured, ")
                    .append(agreementCount()).append(" agreed, ")
                    .append(disagreements().size()).append(" disagreed, ")
                    .append(distinctReferenceValues()).append(" distinct reference value(s)");
            if (isDiscriminating()) {
                return sb.append(" [DISCRIMINATING]").toString();
            }
            String sole = soleReferenceValue();
            String rationale = sole == null ? null : acknowledged.rationaleFor(op.key(), sole);
            sb.append(" [NOT DISCRIMINATING");
            if (sole != null) {
                sb.append(": always ").append(sole);
            }
            if (rationale != null) {
                sb.append("; acknowledged: ").append(rationale);
            }
            return sb.append(']').toString();
        }

        /**
         * Fails loudly unless at least {@code minimum} rows were genuine comparisons. Returns
         * {@code this} so it can be chained onto a sweep call.
         *
         * @throws IllegalStateException if fewer, with the verdict tally in the message — because
         *         the interesting question at that point is always <em>what</em> the rows were
         *         instead
         */
        public Result requireMeasurements(int minimum) {
            int measured = measurementCount();
            if (measured < minimum) {
                throw new IllegalStateException("sweep of " + op.key() + " measured " + measured
                        + " row(s) but needed at least " + minimum + ": of the " + rowCount()
                        + " row(s) produced, " + measured + " compared two observed values. A result "
                        + "with no measurements is not evidence that the two sides behave alike, "
                        + "only that the sweep never got that far. Tally: " + summary());
            }
            return this;
        }

        /**
         * Rows on which both sides threw but with <em>different</em> throwable classes.
         *
         * <p>Surfaces a defect class that is otherwise invisible above row level. A port that fails
         * on the right inputs with the wrong exception type produces a tally, a row count, an
         * agreement count and a disagreement list that are bit-identical to a correct port's: every
         * such row is {@link DiffVerdict#BOTH_THREW} either way. Measured on a planted defect that
         * changed {@code IndexOutOfBoundsException} to {@code IllegalStateException} on 89 rows of
         * {@code UStringValue.at(int)}, every aggregate the harness offered was unchanged and only
         * the row text differed. The evidence was always in the columns and the note; it now has a
         * number, so a sweep with no golden to diff against can still assert on it.
         *
         * <p>Counted over {@link DiffVerdict#BOTH_THREW} and {@link DiffVerdict#ACCEPTED_THROW}
         * alike: an adjudicated pair with mismatched classes is exactly the sign-off a reviewer
         * should be asked to justify twice.
         */
        public int throwClassMismatchCount() {
            int n = 0;
            for (DiffRow row : rows) {
                if ((row.verdict() == DiffVerdict.BOTH_THREW
                        || row.verdict() == DiffVerdict.ACCEPTED_THROW)
                        && !row.historical().equals(row.ported())) {
                    n++;
                }
            }
            return n;
        }

        /**
         * One-line tally, e.g.
         * {@code URealValue.add(value): 484 rows, 484 measured, 231 distinct ref, AGREE=484}. The
         * measured count sits next to the row count on purpose: those two numbers being far apart is
         * the single most important thing about a sweep, and it used to be unreadable from here.
         *
         * <p>The distinct-reference-value count sits next to both of them for the same reason one
         * level up. {@code 30 rows, 30 measured, AGREE=30} and
         * {@code 30 rows, 30 measured, 1 distinct ref, AGREE=30} are the same sweep; only the second
         * lets a reader see that it could not have said anything else.
         */
        public String summary() {
            StringBuilder sb = new StringBuilder(op.key()).append(": ").append(rowCount())
                    .append(" rows, ").append(measurementCount()).append(" measured, ")
                    .append(distinctReferenceValues()).append(" distinct ref");
            for (Map.Entry<DiffVerdict, Integer> e : tally.entrySet()) {
                if (e.getValue() > 0) {
                    sb.append(", ").append(e.getKey()).append('=').append(e.getValue());
                }
            }
            int mismatched = throwClassMismatchCount();
            if (mismatched > 0) {
                sb.append(", throwClassMismatch=").append(mismatched);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return summary();
        }
    }
}
