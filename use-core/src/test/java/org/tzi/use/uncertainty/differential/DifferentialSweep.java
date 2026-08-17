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
 * against {@link Result#disagreements()}.
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
                String note = (!refSupports ? reference.unsupportedReason(op) : "")
                        + (!refSupports && !subSupports ? "; " : "")
                        + (!subSupports ? subject.unsupportedReason(op) : "");
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
            Throwable first = ref.harnessError != null ? ref.harnessError : sub.harnessError;
            StringBuilder note = new StringBuilder("no measurement on ");
            if (ref.harnessError != null && sub.harnessError != null) {
                note.append("either side");
            } else {
                note.append(ref.harnessError != null ? "the reference" : "the subject");
            }
            note.append("; no comparison was made. ").append(safeMessage(first));
            return new DiffRow(index, op.key(), inputs, column(ref), column(sub),
                    DiffVerdict.HARNESS_ERROR, note.toString());
        }
        if (ref.thrown != null && sub.thrown != null) {
            // The note carries both classes AND both messages, always, whether or not the classes
            // match. This class no longer forms an opinion about whether two failures are the same
            // failure; it records what it saw and hands the reader the evidence. The only way out of
            // BOTH_THREW is an entry a human wrote into AcceptedThrowPairs.
            String evidence = "reference threw " + ref.thrown.getClass().getName() + ": "
                    + safeMessage(ref.thrown)
                    + " / subject threw " + sub.thrown.getClass().getName() + ": "
                    + safeMessage(sub.thrown);
            String rationale = accepted.rationaleFor(op, ref.thrown, sub.thrown);
            return new DiffRow(index, op.key(), inputs, DiffRow.thrown(ref.thrown),
                    DiffRow.thrown(sub.thrown),
                    rationale == null ? DiffVerdict.BOTH_THREW : DiffVerdict.ACCEPTED_THROW,
                    rationale == null ? evidence : "adjudicated: " + rationale + ". " + evidence);
        }
        if (ref.thrown != null || sub.thrown != null) {
            return new DiffRow(index, op.key(), inputs,
                    ref.thrown != null ? DiffRow.thrown(ref.thrown) : ref.value.canonical(),
                    sub.thrown != null ? DiffRow.thrown(sub.thrown) : sub.value.canonical(),
                    DiffVerdict.MIXED,
                    "one side threw: " + safeMessage(ref.thrown != null ? ref.thrown : sub.thrown));
        }
        boolean agree = ref.value.canonical().equals(sub.value.canonical());
        return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                agree ? DiffVerdict.AGREE : DiffVerdict.DIFFER, "");
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

        /** One-line tally, e.g. {@code URealValue.add(value): 484 rows, AGREE=484}. */
        public String summary() {
            StringBuilder sb = new StringBuilder(op.key()).append(": ").append(rowCount()).append(" rows");
            for (Map.Entry<DiffVerdict, Integer> e : tally.entrySet()) {
                if (e.getValue() > 0) {
                    sb.append(", ").append(e.getKey()).append('=').append(e.getValue());
                }
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return summary();
        }
    }
}
