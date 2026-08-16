package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runs an operation on both candidates over a fixed, replayable list of argument tuples and turns
 * each application into a {@link DiffRow}.
 *
 * <p>The sweep never decides that a difference is acceptable. It records what each side did and
 * classifies it; whether a run is a pass is the caller's judgement, made against
 * {@link Result#disagreements()}.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class DifferentialSweep {

    private final Candidate reference;
    private final Candidate subject;
    private final long seed;

    /**
     * @param reference the historical side (its output populates the {@code historical} column)
     * @param subject   the ported side (its output populates the {@code ported} column)
     * @param seed      the seed that produced the inputs, recorded in the report header
     */
    public DifferentialSweep(Candidate reference, Candidate subject, long seed) {
        this.reference = reference;
        this.subject = subject;
        this.seed = seed;
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
                String note = (!refSupports ? reference.name() + " does not implement " + op.key() : "")
                        + (!refSupports && !subSupports ? "; " : "")
                        + (!subSupports ? subject.name() + " does not implement " + op.key() : "");
                rows.add(new DiffRow(i, op.key(), inputs,
                        refSupports ? "" : "UNSUPPORTED", subSupports ? "" : "UNSUPPORTED",
                        DiffVerdict.UNSUPPORTED, note));
                continue;
            }

            Outcome refOutcome = apply(reference, op, tuple);
            Outcome subOutcome = apply(subject, op, tuple);
            rows.add(classify(i, op, inputs, refOutcome, subOutcome));
        }
        return new Result(op, seed, reference.name(), subject.name(), rows);
    }

    private static Outcome apply(Candidate candidate, UOp op, List<UValue> tuple) {
        try {
            return Outcome.returned(candidate.invoke(op, tuple));
        } catch (Throwable t) {
            return Outcome.threw(t);
        }
    }

    private static DiffRow classify(int index, UOp op, List<String> inputs, Outcome ref, Outcome sub) {
        if (ref.thrown != null && sub.thrown != null) {
            boolean same = ref.thrown.getClass().getName().equals(sub.thrown.getClass().getName());
            String note = same ? "" : "reference message: " + safeMessage(ref.thrown)
                    + " / subject message: " + safeMessage(sub.thrown);
            return new DiffRow(index, op.key(), inputs, DiffRow.thrown(ref.thrown),
                    DiffRow.thrown(sub.thrown), same ? DiffVerdict.AGREE_THROWN : DiffVerdict.DIFFER_THROWN,
                    note);
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

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "(no message)" : m;
    }

    private static final class Outcome {
        final UValue value;
        final Throwable thrown;

        private Outcome(UValue value, Throwable thrown) {
            this.value = value;
            this.thrown = thrown;
        }

        static Outcome returned(UValue v) {
            return new Outcome(v, null);
        }

        static Outcome threw(Throwable t) {
            return new Outcome(null, t);
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

        /** Every row whose verdict is not an agreement. An empty list is the only clean outcome. */
        public List<DiffRow> disagreements() {
            List<DiffRow> out = new ArrayList<>();
            for (DiffRow row : rows) {
                if (!row.verdict().isAgreement()) {
                    out.add(row);
                }
            }
            return out;
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
