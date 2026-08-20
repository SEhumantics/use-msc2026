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
    private final IntendedDepartures intendedDepartures;

    /**
     * The ordinary constructor: no throw-pair is an agreement, and every difference is a porting
     * error.
     *
     * @param reference the historical side (its output populates the {@code historical} column)
     * @param subject   the ported side (its output populates the {@code ported} column)
     * @param seed      the seed that produced the inputs, recorded in the report header
     */
    public DifferentialSweep(Candidate reference, Candidate subject, long seed) {
        this(reference, subject, seed, AcceptedThrowPairs.none(), IntendedDepartures.none());
    }

    /**
     * As above, with an explicit allowlist of reviewed throw-pairs. Use this only for shared error
     * paths that a stage has read and signed off in writing — see {@link AcceptedThrowPairs}.
     */
    public DifferentialSweep(Candidate reference, Candidate subject, long seed,
                             AcceptedThrowPairs acceptedThrowPairs) {
        this(reference, subject, seed, acceptedThrowPairs, IntendedDepartures.none());
    }

    /**
     * As above, with the departures a stage pre-registered before running — the B7 mechanism. A
     * {@code DIFFER} row whose exact pair one of them names, in the direction it predicted, becomes
     * {@link DiffVerdict#INTENDED_DEPARTURE}; everything else stays a difference. See
     * {@link IntendedDepartures}.
     */
    public DifferentialSweep(Candidate reference, Candidate subject, long seed,
                             AcceptedThrowPairs acceptedThrowPairs,
                             IntendedDepartures intendedDepartures) {
        this.reference = reference;
        this.subject = subject;
        this.seed = seed;
        this.acceptedThrowPairs = Objects.requireNonNull(acceptedThrowPairs,
                "acceptedThrowPairs (use AcceptedThrowPairs.none())");
        this.intendedDepartures = Objects.requireNonNull(intendedDepartures,
                "intendedDepartures (use IntendedDepartures.none())");
    }

    /** The reviewed throw-pairs this sweep will adjudicate; empty unless a caller supplied some. */
    public AcceptedThrowPairs acceptedThrowPairs() {
        return acceptedThrowPairs;
    }

    /** The departures this sweep will adjudicate; empty unless a caller pre-registered some. */
    public IntendedDepartures intendedDepartures() {
        return intendedDepartures;
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
        return run(op, tuplesOf(domains));
    }

    /**
     * The cartesian product {@link #sweep} would drive, without driving it.
     *
     * <p>Exposed so a caller can build ONE result per operation out of several domain sets instead of
     * one result per domain set. That distinction is not cosmetic: {@link IntendedDepartures} keys a
     * population declaration on an exact row count, and a count is only meaningful over the
     * population a human actually looked at. A caller that sweeps eight corpora separately gets eight
     * results, each holding a slice, and a declaration written against the operation as a whole
     * matches none of them.
     */
    public static List<List<UValue>> tuplesOf(List<List<UValue>> domains) {
        List<List<UValue>> tuples = new ArrayList<>();
        buildTuples(domains, 0, new ArrayList<>(), tuples);
        return tuples;
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
            rows.add(classify(i, op, inputs, refOutcome, subOutcome, acceptedThrowPairs,
                    intendedDepartures));
        }
        applyPopulationDepartures(op, rows);
        return new Result(op, seed, reference.name(), subject.name(), rows, intendedDepartures);
    }

    /**
     * The second half of the {@link IntendedDepartures} mechanism, run once the population is
     * complete.
     *
     * <p>A per-pair declaration can be evaluated the instant a row is classified, because its key is
     * the row. A <em>bounded</em> declaration cannot: it names its population by an exact count and a
     * digest over the whole set, so nothing can be decided until the last row is in. Hence a
     * post-pass rather than a branch inside {@link #classify}.
     *
     * <p>It runs over the rows that are still {@link DiffVerdict#DIFFER} after per-pair adjudication,
     * which is what makes the two forms compose: a stage may write three pairs out by hand and cover
     * what remains with a population declaration, and the population is the set that actually
     * remained rather than the set before per-pair adjudication ran.
     */
    private void applyPopulationDepartures(UOp op, List<DiffRow> rows) {
        if (intendedDepartures.isEmpty()) {
            return;
        }
        List<Integer> residual = new ArrayList<>();
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            DiffRow row = rows.get(i);
            if (row.verdict() == DiffVerdict.DIFFER) {
                residual.add(i);
                pairs.add(row.historical() + '\t' + row.ported());
            }
        }
        IntendedDepartures.Declaration d = intendedDepartures.adjudicatePopulation(op.key(), pairs);
        if (d == null) {
            return;
        }
        for (int i : residual) {
            DiffRow row = rows.get(i);
            rows.set(i, new DiffRow(row.index(), row.operation(), row.inputs(), row.historical(),
                    row.ported(), DiffVerdict.INTENDED_DEPARTURE,
                    d.note() + (row.note().isEmpty() ? "" : " " + row.note()),
                    row.subjectTypeProvenance()));
        }
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
                                    AcceptedThrowPairs accepted, IntendedDepartures intended) {
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
                    lead + evidence(ref, sub), subjectTypeProvenance(sub));
        }
        if (!ref.value.carriesAnObservation() && !sub.value.carriesAnObservation()) {
            // Neither side produced a value. Comparing the two absences and finding them equal is
            // the AGREE_THROWN mistake in its third costume: for a void operation the reference's
            // VOID comes from method.getReturnType(), not from behaviour, and the subject's comes
            // from the boilerplate Candidate tells an adapter to write, so the verdict is decided
            // before either implementation runs. Note that one-sided absence is NOT routed here --
            // that is a real difference and falls through to DIFFER below.
            return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                    DiffVerdict.UNMEASURABLE, unmeasurableNote(ref, sub),
                    subjectTypeProvenance(sub));
        }
        if (ref.value.canonical().equals(sub.value.canonical())) {
            return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                    DiffVerdict.AGREE, "", subjectTypeProvenance(sub));
        }
        // A TYPE-ONLY difference is measured and reported, but it is not scored as a divergence.
        //
        // canonical() is content + "@" + simple class name, so content equality with canonical
        // inequality is exactly and only the case "the payload matches, the Java class does not".
        // That case is AGREE here and is counted by Result#javaTypeMismatchCount(), which the report
        // header and stageStatement() both publish.
        //
        // WHY (round 8, defect D-43 half (b)). Scoring it DIFFER measures the ADAPTER, not the port,
        // for as long as there is no ported implementation to observe -- and at S1 there is none: no
        // org.tzi.use.uml.ocl.value.URealValue exists in use-core/src/main, and writing one IS stage
        // S4. Measured: a CONTENT-PERFECT port whose adapter takes the factory default produced 3 445
        // DIFFER rows across 182 of 285 operations and lost 29 stage passes, numbers byte-identical to
        // a genuinely wrong-class port's. Rounds 6 and 7 both tried to fix that by giving the adapter
        // author a way to state the token; both statements could be false, and round 7's measured
        // sweep from a wrong-class port plus declaredJavaType(referenceToken, "x") was byte-identical
        // to the perfect-port control. The token is unavoidably author-influenced today, so the
        // difference belongs in its own reported dimension rather than in the verdict.
        //
        // CONTENT differences are untouched: they fall through to DIFFER below, as they always did.
        if (ref.value.content().equals(sub.value.content())) {
            return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                    DiffVerdict.AGREE, typeNote(ref.value, sub.value),
                    subjectTypeProvenance(sub));
        }
        // The last stop before a difference is reported as a porting error: did a stage write this
        // exact pair down, in this direction, before the run? An undeclared pair, or a declared pair
        // that moved the way the declaration did NOT predict, falls straight through to DIFFER.
        IntendedDepartures.Declaration declaration = intended.adjudicate(
                op.key(), ref.value.canonical(), sub.value.canonical());
        if (declaration != null) {
            String note = typeNote(ref.value, sub.value);
            return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                    DiffVerdict.INTENDED_DEPARTURE,
                    declaration.note() + (note.isEmpty() ? "" : " " + note),
                    subjectTypeProvenance(sub));
        }
        return new DiffRow(index, op.key(), inputs, ref.value.canonical(), sub.value.canonical(),
                DiffVerdict.DIFFER, typeNote(ref.value, sub.value),
                subjectTypeProvenance(sub));
    }

    /**
     * The subject's type provenance for a row, or {@code null} when the subject produced no value on
     * it. Carried onto {@link DiffRow} so that
     * {@link Result#subjectTypeObservedCount()} / {@link Result#subjectTypeAssumedCount()} are summed
     * from a field and not scraped out of the note's prose (H21).
     */
    private static UValue.TypeProvenance subjectTypeProvenance(Outcome sub) {
        return sub.value == null ? null : sub.value.typeProvenance();
    }

    /**
     * The note on a row whose two sides disagree, which is empty unless the <em>Java types</em>
     * differ — the D-18 case.
     *
     * <p>{@link UValue#canonical()} compares {@link UValue#typeToken()}, the simple class name;
     * this note carries both sides' fully-qualified names, which is the information the token drops,
     * and says outright whether the content was identical. "The port returned the right number in
     * the wrong class" and "the port returned the wrong number" are different findings and a reader
     * must not have to reconstruct which one a row is from two nearly-identical columns.
     *
     * <p>An ordinary content divergence keeps the empty note it always had: the two columns already
     * say everything, and filling every {@code DIFFER} row in the report with prose would bury the
     * rows where the note is load-bearing.
     */
    private static String typeNote(UValue ref, UValue sub) {
        String refType = ref.javaType();
        String subType = sub.javaType();
        if (java.util.Objects.equals(refType, subType)) {
            return "";
        }
        return "java type mismatch: reference returned " + describeType(ref)
                + " / subject returned " + describeType(sub) + "; the content is "
                + (ref.content().equals(sub.content()) ? "IDENTICAL -- right content, wrong Java "
                        + "type (defect D-18). This row is scored AGREE and counted in "
                        + "rows.javaTypeMismatch, not scored as a divergence: at S1 the ported side's "
                        + "class cannot be authentically observed, because no ported value class "
                        + "exists to observe, so a type-only difference measures the adapter and not "
                        + "the port (D-43)"
                        : "different as well, so this row is a divergence on its content")
                + "." + provenanceClause(ref, sub);
    }

    /**
     * <strong>How each side came by the class named above — defect D-43.</strong>
     *
     * <p>A type-mismatch row has two possible causes and they are different findings: the
     * implementation returned the wrong class, or the <em>adapter</em> never looked at what its
     * implementation returned. Both provenances are printed on every such row, unconditionally, so
     * that the row states the attribution rather than leaving a reader to reconstruct it.
     *
     * <p>This clause states what the harness knows and no more. It used to end an all-observed row
     * with "Both classes were OBSERVED from the objects the two sides returned, so this row is a
     * statement about the two implementations" — a certification the harness cannot make, because
     * {@link UValue#observedFrom(Object)} believes any object it is handed and a subject can hand it
     * one its port never returned. That sentence was measured making 1 618 false assertions in round 7
     * and is gone; what replaces it names the provenances and says outright what is not checkable.
     *
     * <p>The provenance is reported and never scored: it must not be able to move a verdict in either
     * direction, or a subject could talk its way out of a finding by admitting how it got the token.
     */
    private static String provenanceClause(UValue ref, UValue sub) {
        StringBuilder sb = new StringBuilder(" Provenance: reference ")
                .append(ref.typeProvenance()).append(", subject ").append(sub.typeProvenance())
                .append(" (OBSERVED = read off the object that side returned; ASSUMED = the factory "
                        + "default for the kind, which is wrong for 182 of 285 operations).");
        if (sub.typeProvenance() == UValue.TypeProvenance.ASSUMED) {
            sb.append(" The subject's adapter never looked at what its implementation returned, so "
                    + "this difference is a finding about the ADAPTER and not about the port (D-43); "
                    + "an adapter must attribute through UValue.observedFrom(Object).");
        }
        if (sub.typeProvenance() == UValue.TypeProvenance.OBSERVED) {
            sb.append(" Whether the object the subject observed is the one its implementation "
                    + "returned is not checkable by this harness.");
        }
        return sb.toString();
    }

    private static String describeType(UValue value) {
        return value.javaType() == null
                ? value.kind() + " (no observed class: " + value.canonical() + ")"
                : value.javaType() + " (" + value.canonical() + ")";
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
        /**
         * The pre-registration the sweep actually ran under, carried on the result so the gate
         * adjudicates against the same list that produced the verdicts. Passing a different one to
         * the gate would measure a list that never touched a row, which is how a pre-registration
         * mechanism turns into decoration.
         */
        private final IntendedDepartures intendedDepartures;

        Result(UOp op, long seed, String referenceName, String subjectName, List<DiffRow> rows,
               IntendedDepartures intendedDepartures) {
            this.op = op;
            this.seed = seed;
            this.referenceName = referenceName;
            this.subjectName = subjectName;
            this.intendedDepartures = Objects.requireNonNull(intendedDepartures,
                    "intendedDepartures (use IntendedDepartures.none())");
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

        // ------------------------------------------------------- pre-registered departures (B7)

        /** The pre-registration this sweep ran under. {@link IntendedDepartures#none()} by default. */
        public IntendedDepartures intendedDepartures() {
            return intendedDepartures;
        }

        /**
         * Rows on which the two sides differed and a pre-registration said they would, in the
         * direction it predicted.
         *
         * <p>Published separately from {@link #disagreements()} — which still contains them, because
         * they are still not agreements — so that a reader can see at a glance how much of a run was
         * adjudicated by a human in advance rather than matched by the instrument.
         */
        public int intendedDepartureCount() {
            return count(DiffVerdict.INTENDED_DEPARTURE);
        }

        /**
         * Non-agreeing rows that <em>no</em> pre-registration covers: the population gate clause 2
         * refuses on. Exactly {@link #disagreements()} minus the intended departures.
         */
        public List<DiffRow> unintendedDisagreements() {
            List<DiffRow> out = new ArrayList<>();
            for (DiffRow row : disagreements()) {
                if (row.verdict() != DiffVerdict.INTENDED_DEPARTURE) {
                    out.add(row);
                }
            }
            return out;
        }

        /**
         * Ledger row id -&gt; how many rows that row's declarations adjudicated. Sorted, and it
         * carries a zero entry for every declared row that never fired, so the report cannot print a
         * tidy list of departures that quietly omits the ones which did not happen.
         */
        public java.util.SortedMap<String, Integer> departuresByLedgerRow() {
            java.util.SortedMap<String, Integer> out = new java.util.TreeMap<>();
            for (IntendedDepartures.Declaration d : intendedDepartures.declarationsFor(op.key())) {
                out.putIfAbsent(d.ledgerRowId(), 0);
            }
            for (DiffRow row : rows) {
                if (row.verdict() != DiffVerdict.INTENDED_DEPARTURE) {
                    continue;
                }
                String id = ledgerRowOf(row.note());
                if (id != null) {
                    out.merge(id, 1, Integer::sum);
                }
            }
            return java.util.Collections.unmodifiableSortedMap(out);
        }

        /**
         * <strong>Declarations that were written and never fired.</strong> Non-empty is a stage-gate
         * failure (clause 4), and it is the half of the mechanism that is easy to leave out.
         *
         * <p>A pre-registration list that only ever <em>permits</em> differences lets an unfixed
         * defect through: declare the departure, forget to write the fix, and the sweep is green
         * because the port still agrees with the defective reference. Every entry here is one of two
         * things — a fix that did not land, or a prediction that was wrong — and both are failures.
         *
         * <p>Scoped to <em>this</em> operation: a sweep of {@code UStringValue.equals} is not held to
         * a declaration written against {@code UIntegerValue.compareTo}.
         */
        public List<IntendedDepartures.Declaration> unusedDeclarations() {
            java.util.SortedMap<String, Integer> fired = departuresByLedgerRow();
            List<IntendedDepartures.Declaration> out = new ArrayList<>();
            for (IntendedDepartures.Declaration d : intendedDepartures.declarationsFor(op.key())) {
                if (fired.getOrDefault(d.ledgerRowId(), 0) == 0) {
                    out.add(d);
                }
            }
            return java.util.Collections.unmodifiableList(out);
        }

        /**
         * Recovers the ledger row from the note {@link IntendedDepartures.Declaration#note()} wrote.
         * The note format is fixed by that method and by nothing else, so the two stay together.
         */
        private static String ledgerRowOf(String note) {
            String prefix = "intended departure ";
            if (note == null || !note.startsWith(prefix)) {
                return null;
            }
            int end = note.indexOf(' ', prefix.length());
            return end < 0 ? null : note.substring(prefix.length(), end);
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
         * The four-clause form, for a sweep that ran under a {@link IntendedDepartures}
         * pre-registration. Clause 4 is {@link #unusedDeclarations()} being empty.
         *
         * @param intended must be the same list the sweep ran under; see
         *                 {@link #requireStagePass(int, AcceptedDegenerateOperations, IntendedDepartures)}
         */
        public boolean isStagePass(int minimumMeasurements, AcceptedDegenerateOperations acknowledged,
                                   IntendedDepartures intended) {
            return stageGateFailures(minimumMeasurements, acknowledged, intended).isEmpty();
        }

        /**
         * {@link #isStagePass(int, AcceptedDegenerateOperations)}, but throws with <em>every</em>
         * failing clause and the numbers behind it rather than returning a bare {@code false}.
         *
         * <p><strong>Two clauses this call deliberately does not make, and a stage must:</strong>
         * {@code throwClassMismatchCount() == 0} and — from S4 onwards, as a dated obligation
         * (2026-08-17), once real ported value classes exist in {@code use-core/src/main} and the
         * adapter routes through {@link UValue#observedFrom(Object)} —
         * {@code javaTypeMismatchCount() == 0}. Both are populations every other figure on this class
         * is blind to. The type figure is not enforced here at S1 because there is no ported
         * implementation to observe, which would make it a measurement of the adapter; it is
         * nevertheless printed unconditionally by {@link #stageStatement(AcceptedDegenerateOperations)}
         * and by the report header, so a stage cannot quote a pass without seeing it. See
         * {@code harness-contract.md} §7.
         *
         * @return {@code this}, so it can be chained onto a sweep call
         * @throws IllegalStateException if any clause fails
         */
        public Result requireStagePass(int minimumMeasurements,
                                       AcceptedDegenerateOperations acknowledged) {
            return requireStagePass(minimumMeasurements, acknowledged, intendedDepartures);
        }

        /**
         * As above, naming the pre-registration the sweep ran under.
         *
         * <p><strong>The third parameter is never defaulted</strong>, for the same reason
         * {@link AcceptedDegenerateOperations} is not: a caller must be unable to reach a pass over
         * deliberately-diverging rows without writing down the mechanism that let them. The two-argument
         * form above therefore <em>refuses</em> a sweep that ran with a non-empty pre-registration
         * rather than silently adjudicating it — see {@link #requireSamePreregistration}.
         *
         * @param intended the same list the sweep ran under. A different one is rejected outright: it
         *                 would measure a list that never touched a row, which is how a
         *                 pre-registration mechanism turns into decoration.
         */
        public Result requireStagePass(int minimumMeasurements,
                                       AcceptedDegenerateOperations acknowledged,
                                       IntendedDepartures intended) {
            List<String> failures = stageGateFailures(minimumMeasurements, acknowledged, intended);
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
            if (!intendedDepartures.isEmpty()) {
                throw new IllegalStateException("this sweep ran under a non-empty IntendedDepartures ("
                        + intendedDepartures + "), so the two-argument stage gate must not be used: it "
                        + "would let a caller reach a pass over deliberately-diverging rows without "
                        + "naming the mechanism that permitted them. Call the three-argument form and "
                        + "pass the same pre-registration the sweep was built with.");
            }
            return stageGateFailures(minimumMeasurements, acknowledged, intendedDepartures);
        }

        /** Every clause of the four-clause stage gate this result fails, in order; empty means pass. */
        public List<String> stageGateFailures(int minimumMeasurements,
                                              AcceptedDegenerateOperations acknowledged,
                                              IntendedDepartures intended) {
            Objects.requireNonNull(acknowledged,
                    "acknowledged (use AcceptedDegenerateOperations.none())");
            requireSamePreregistration(intended);
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
            int disagreed = unintendedDisagreements().size();
            if (disagreed > 0) {
                failures.add(disagreed + " row(s) did not agree"
                        + (intendedDepartureCount() == 0 ? "."
                                : ", over and above the " + intendedDepartureCount()
                                  + " pre-registered as intended departures."));
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
            // Clause 4. The half of the pre-registration mechanism that is easy to leave out, and is
            // the whole point: a list that only ever PERMITS differences lets an unfixed defect pass
            // as agreement. Declare the departure, forget to write the fix, and the port still agrees
            // with the defective reference -- green, and wrong.
            List<IntendedDepartures.Declaration> unused = unusedDeclarations();
            if (!unused.isEmpty()) {
                StringBuilder sb = new StringBuilder(unused.size())
                        .append(" pre-registered departure(s) never fired on this operation. Each is "
                                + "either a fix that did not land or a prediction that was wrong, and "
                                + "both are failures:");
                for (IntendedDepartures.Declaration d : unused) {
                    sb.append("\n      * ").append(d.id())
                      .append(" [expected ").append(d.direction().describeExpectation()).append(']');
                }
                failures.add(sb.toString());
            }
            return failures;
        }

        /**
         * Refuses a gate call whose pre-registration is not the one the sweep ran under.
         *
         * <p>Compared by declaration identity rather than by object identity, so a list rebuilt from
         * the same source is accepted and a list with one entry added, removed or reworded is not.
         * Without this the third parameter would be a label a caller writes next to a number it did
         * not come from.
         */
        private void requireSamePreregistration(IntendedDepartures intended) {
            Objects.requireNonNull(intended, "intended (use IntendedDepartures.none())");
            if (intended == intendedDepartures) {
                return;
            }
            List<String> mine = new ArrayList<>();
            for (IntendedDepartures.Declaration d : intendedDepartures.declarations()) {
                mine.add(d.id());
            }
            List<String> theirs = new ArrayList<>();
            for (IntendedDepartures.Declaration d : intended.declarations()) {
                theirs.add(d.id());
            }
            if (!mine.equals(theirs)) {
                throw new IllegalArgumentException("the stage gate was given a different "
                        + "IntendedDepartures than the sweep ran under, so it would adjudicate a list "
                        + "that never touched a row.\n  sweep ran under: " + mine
                        + "\n  gate was given:  " + theirs);
            }
        }

        /**
         * The one-line statement a stage must publish next to any figure taken from this sweep:
         * measured rows, agreement rows, disagreements, <strong>Java-type mismatches split by the
         * subject's type provenance</strong>, distinct reference values, and — when the operation is
         * degenerate and signed off — the rationale verbatim.
         *
         * <p>The provenance split (H21) is printed here unconditionally, for the same reason the
         * mismatch total is: a stage quoting a pass must be unable to avoid seeing whether its
         * type-mismatch rows say something about the port ({@code OBSERVED}) or only about the
         * harness's own adapter ({@code ASSUMED}). See {@link #subjectTypeObservedCount()}.
         *
         * <p>There is deliberately no way to render an agreement figure from this class without the
         * discrimination figure beside it, and since round 8 no way to render one without the
         * {@link #javaTypeMismatchCount()} beside it either. The reason is the same mechanism-not-a-
         * convention argument as D-15's: a type-only difference is scored {@link DiffVerdict#AGREE}, so
         * it is invisible in every other figure on this line, and a stage that quotes a pass must see
         * how many of its agreement rows agreed only on the payload. The figure is printed
         * unconditionally, including when it is zero, because "0" is the claim a stage needs to be able
         * to make.
         */
        public String stageStatement(AcceptedDegenerateOperations acknowledged) {
            Objects.requireNonNull(acknowledged,
                    "acknowledged (use AcceptedDegenerateOperations.none())");
            StringBuilder sb = new StringBuilder(op.key())
                    .append(": ").append(rowCount()).append(" rows, ")
                    .append(measurementCount()).append(" measured, ")
                    .append(agreementCount()).append(" agreed, ")
                    .append(unintendedDisagreements().size()).append(" disagreed, ")
                    .append(intendedDepartureCount()).append(" intended departure(s)")
                    .append(departuresByLedgerRow().isEmpty() ? ""
                            : " " + departuresByLedgerRow()).append(", ")
                    .append(javaTypeMismatchCount()).append(" java-type mismatch(es) (subject token ")
                    .append("OBSERVED on ").append(subjectTypeObservedCount()).append(", ASSUMED on ")
                    .append(subjectTypeAssumedCount()).append("), ")
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
        /**
         * <strong>Rows on which the two sides' content was identical and only the Java class
         * differed</strong> — the D-18 population, in its own dimension because it is no longer a
         * verdict (defect <strong>D-43</strong>, round 8).
         *
         * <p>Deliberately shaped like {@link #throwClassMismatchCount()}: a defect class that is
         * otherwise invisible above row level gets a number, so a sweep with no golden to diff against
         * can still assert on it. Every such row is {@link DiffVerdict#AGREE}, so without this count a
         * content-perfect port and a port returning the right payload in the wrong class produce
         * identical aggregates — which is exactly the blindness D-18 was opened to close, and closing
         * it here rather than in the verdict is what keeps a factory-typed adapter from being reported
         * as a defective port.
         *
         * <p>Counted from the rendered columns, like the throw counterpart: an {@code AGREE} row whose
         * two columns differ is, by construction of {@link UValue#canonical()}, exactly a type-only
         * mismatch. A row whose content differs <em>as well</em> is a {@link DiffVerdict#DIFFER} and is
         * counted there; it is a content finding and must not be diluted into this number.
         *
         * <p><strong>What this number does and does not tell a stage.</strong> Non-zero means the two
         * sides named different classes for the same payload. Whether that is a port defect or an
         * adapter that never attributed is in the row note's provenance clause, not in this count: a
         * subject whose adapter is uniformly non-attributing produces the same 3 445 here whether its
         * port is perfect or carries a real wrong-class infidelity. From S4 onwards, when the adapter
         * routes through {@link UValue#observedFrom(Object)} and real ported classes exist, that
         * ambiguity is gone and this count becomes a gate clause — see {@code harness-contract.md} §7.
         */
        public int javaTypeMismatchCount() {
            int n = 0;
            for (DiffRow row : rows) {
                if (row.verdict() == DiffVerdict.AGREE && !row.historical().equals(row.ported())) {
                    n++;
                }
            }
            return n;
        }

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

        // ------------------------------------------------- H21: the provenance aggregate

        /**
         * <strong>Of the {@link #javaTypeMismatchCount()} rows, how many had the subject's class
         * token {@link UValue.TypeProvenance#OBSERVED}</strong> — read off an object the subject's
         * adapter actually handed {@link UValue#observedFrom(Object)}.
         *
         * <p><strong>Why this number exists (H21).</strong> {@link #javaTypeMismatchCount()} answers
         * "how many rows named different classes for the same payload" and its own Javadoc then says
         * the load-bearing follow-up question — port defect, or an adapter that never looked? — lives
         * "in the row note's provenance clause, not in this count". That made the distinction a
         * <em>per-row</em> fact with no aggregate anywhere: two reports with identical
         * {@code rows.javaTypeMismatch} could be told apart only by opening the data rows and reading
         * prose. This pair of counts is the header number, and the split is the whole finding:
         * <ul>
         *   <li>all {@code ASSUMED} — the adapter is non-attributing, so the mismatch says nothing
         *       about the port. It is a finding about the harness's own adapter (defect D-43) and the
         *       fix is {@code UValue.observedFrom(Object)}, not a change to {@code use-core/src/main}.</li>
         *   <li>all {@code OBSERVED} — the subject named a class it claims to have seen, so the
         *       mismatch is a statement about two implementations. <em>Claims</em>: {@code observedFrom}
         *       believes any object it is handed, so this is not a certification, and the row note says
         *       so outright (defect D-47).</li>
         *   <li>a mixture — the population is not homogeneous and no single sentence covers it; the two
         *       numbers say so instead of a reader assuming one cause for all of it.</li>
         * </ul>
         *
         * <p><strong>Population.</strong> Exactly {@link #javaTypeMismatchCount()}'s: {@link
         * DiffVerdict#AGREE} rows whose two rendered columns differ. It is deliberately <em>not</em>
         * every row in the sweep. A global observed/assumed tally would dilute the mismatch rows into
         * the thousands of rows that agreed on the class as well as the payload, which is precisely the
         * question this count is here to answer. {@link DiffVerdict#DIFFER} rows are excluded for the
         * same reason they are excluded from {@code javaTypeMismatchCount()}: their content differs, so
         * they are content findings and must not be diluted into a type figure.
         *
         * <p><strong>Identity.</strong> {@code subjectTypeObservedCount() + subjectTypeAssumedCount()
         * == javaTypeMismatchCount()}, and it is not an accident that can quietly stop holding: every
         * row in this population has both sides carrying a class (a value that stands for the absence
         * of a result cannot reach {@code AGREE} against a value that does not), so the subject's
         * provenance on it is {@code OBSERVED} or {@code ASSUMED} and never {@code NONE}. The identity
         * is asserted in {@code DifferentialHarnessRegressionTest}, so a future change that puts a
         * third state into the population fails a test rather than silently losing rows from both
         * counts.
         */
        public int subjectTypeObservedCount() {
            return typeMismatchesWithSubjectProvenance(UValue.TypeProvenance.OBSERVED);
        }

        /**
         * Of the {@link #javaTypeMismatchCount()} rows, how many had the subject's class token
         * {@link UValue.TypeProvenance#ASSUMED} — the factory default for the kind, which is wrong for
         * 182 of the 285 enumerated operations. Nobody looked at what the subject's implementation
         * returned, so such a row is a finding about the adapter and not about the port (D-43).
         *
         * @see #subjectTypeObservedCount()
         */
        public int subjectTypeAssumedCount() {
            return typeMismatchesWithSubjectProvenance(UValue.TypeProvenance.ASSUMED);
        }

        private int typeMismatchesWithSubjectProvenance(UValue.TypeProvenance provenance) {
            int n = 0;
            for (DiffRow row : rows) {
                if (row.verdict() == DiffVerdict.AGREE && !row.historical().equals(row.ported())
                        && row.subjectTypeProvenance() == provenance) {
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
            int typeMismatched = javaTypeMismatchCount();
            if (typeMismatched > 0) {
                // The split travels with the number it splits, and only with it: an operation with no
                // type-mismatch rows has no provenance question to answer, and printing "OBSERVED:0/
                // ASSUMED:0" on every one-line tally in the tree would bury the ones that do (H21).
                sb.append(", javaTypeMismatch=").append(typeMismatched)
                        .append(" (subjectType OBSERVED=").append(subjectTypeObservedCount())
                        .append(" ASSUMED=").append(subjectTypeAssumedCount()).append(')');
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return summary();
        }
    }
}
