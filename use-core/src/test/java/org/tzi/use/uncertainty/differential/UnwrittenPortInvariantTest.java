package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The standing invariant of the differential harness.
 *
 * <p><strong>A {@link Candidate} that implements nothing must produce zero agreement rows, over
 * every operation the harness can reach and every input corpus it ships.</strong>
 *
 * <p>This is a property, not an instance. It is written this way on purpose: every defect this
 * harness has had so far — a marshalling failure scored as a matching throw, two Java {@code null}
 * returns scored as a matching {@code NullPointerException}, two unrelated
 * {@code java.lang.RuntimeException}s scored as a matching throw, two {@code VOID} results scored as
 * a matching value — is a different route to the same false statement, "both sides did the same
 * thing" asserted where no comparison happened. Pinning each route with its own regression test is
 * chasing instances; the instances kept coming back through a route nobody had pinned yet.
 *
 * <h2>Why this test is parameterised over a family of subjects</h2>
 * The previous version instantiated <em>one</em> unwritten port — the one whose every body throws —
 * and its class comment claimed to close "the whole family". It did not, and the gap was measured:
 * a subject whose every body is empty (returning {@link UValue#voidValue()}, which is literally what
 * the {@link Candidate} contract tells an adapter author to write for a {@code void} operation)
 * scored <strong>444 agreement rows</strong> on the very same sweep, because {@code VOID} vs
 * {@code VOID} compared equal. "Implements nothing" has more than one encoding, so the test now
 * quantifies over the encodings instead of naming one:
 * <ol>
 *   <li>every body throws {@code RuntimeException} — the literal state of the tree at the start of
 *       S4;</li>
 *   <li>every body returns Java {@code null} — the natural mistake when a result maps to
 *       {@code UndefinedValue};</li>
 *   <li>every body is empty, i.e. returns {@link UValue#voidValue()} — the shape D-10 came in
 *       through;</li>
 *   <li>every body returns {@link UValue#nullValue()};</li>
 *   <li>every body returns a fixed constant;</li>
 *   <li>every body returns its first argument (the receiver) unchanged;</li>
 *   <li>every body throws {@link Error}.</li>
 * </ol>
 *
 * <p>Subjects 1-4 and 7 never put a value in front of the harness at all, so the invariant applies to
 * them in full: <em>zero</em> agreement rows. Subjects 5 and 6 do produce values — they are wrong
 * implementations rather than absent ones — so some genuine agreement is the correct verdict for
 * them and asserting zero would be asserting a falsehood. What must hold for them is the sharper,
 * per-operation form (below), which is what stops a degenerate constant from making any single
 * operation read as fully ported.
 *
 * <h2>The two assertions, and why the second one is needed</h2>
 * <ul>
 *   <li><strong>Total agreement rows == 0</strong> (non-observing subjects only). An aggregate.</li>
 *   <li><strong>No operation is fully agreed.</strong> For every operation, taking only the rows the
 *       harness could actually drive — neither {@link DiffVerdict#HARNESS_ERROR} nor
 *       {@link DiffVerdict#UNSUPPORTED}, so both sides really ran — it must not be the case that
 *       every one of them is an agreement.</li>
 * </ul>
 * The second is not implied by the first for the value-producing subjects, and it is the form that
 * catches a defect confined to one operation. D-10 is exactly that shape: 444 rows out of 471471 is
 * 0.09% of the sweep and looks like noise in an aggregate, but per operation it was
 * <em>144 of the 144 driven rows</em> of {@code URealValue.setTypeToRuntimeType()} — every row of
 * that operation the harness could reach, scored agreement against a subject containing no code.
 *
 * <p>It also widens by itself. The operation inventory is enumerated by reflection from the
 * <em>historical jars</em> (see {@link #reachableOperations}), not from a hand-written list, and the
 * input corpora come from {@link InputGenerator}'s own accessors, so adding a boundary value or
 * reaching a new receiver type automatically enlarges what every subject is measured over.
 *
 * <p>JUnit 5 Jupiter — this reactor has no junit-vintage-engine.
 */
@DisplayName("Unwritten-port invariant")
class UnwrittenPortInvariantTest {

    /**
     * Random draws appended to each boundary corpus. Small and fixed: the invariant is about the
     * boundary corpora, and the random tail is there to prove the property is not an artefact of
     * hand-picked inputs.
     */
    private static final int RANDOM_DRAWS = 2;

    /**
     * Domain size used for the second and later parameters of an operation, e.g. the two {@code int}
     * arguments of {@code UStringValue.uSubstring(int,int)}. Only the first parameter sweeps a whole
     * corpus; a full cartesian product over three or more corpora is quadratically larger for no
     * additional evidence about the invariant.
     */
    private static final int EXTRA_PARAM_SLICE = 3;

    /** How many offending rows to print when the invariant fails. */
    private static final int SAMPLE_LIMIT = 20;

    // ------------------------------------------------------------------ the invariant

    @ParameterizedTest(name = "{0}")
    @MethodSource("degenerateSubjects")
    @DisplayName("a Candidate that implements nothing produces ZERO agreement rows, everywhere")
    void anUnwrittenPortAgreesWithNothing(Subject subject) {
        Tally tally = sweep(subject);
        tally.print();

        assertTrue(tally.operations >= 100,
                "the enumeration must actually find the historical operations, got " + tally.operations);

        if (subject.abortsWithError) {
            assertNotNull(tally.escaped,
                    "a subject that throws Error must abort the sweep loudly rather than produce rows");
            assertEquals(0L, tally.agreementRows,
                    "an aborted sweep must not leave agreement rows behind");
            assertEquals(0L, tally.measuredRows,
                    "an aborted sweep measured nothing");
            return;
        }

        assertNull(tally.escaped, "no Error may escape for this subject: " + tally.escaped);
        assertTrue(tally.rows > 10_000, "the sweep must be large enough to be evidence, got " + tally.rows);

        if (subject.observes == Observability.NOTHING) {
            assertEquals(0L, tally.agreementRows,
                    "a subject with no code in it agreed with the historical implementation on "
                            + tally.agreementRows + " of " + tally.rows + " rows. Every agreement row "
                            + "printed above is the harness asserting that two sides did the same "
                            + "thing where no comparison was made.");
        } else {
            assertTrue(tally.differRows() > 0,
                    "a subject that returns wrong values must be reported as diverging, not merely "
                            + "as unmeasurable: " + tally.verdicts);
        }

        assertEquals(subject.reviewedFullyAgreed, tally.discriminatingFullyAgreedOperations().keySet(),
                "operations scored agreement on EVERY row the harness could drive, AND whose "
                        + "reference side gave more than one answer, against the subject '"
                        + subject.id + "' (" + subject.body + "). Each one has to be read and either "
                        + "fixed or signed off in the subject's reviewedFullyAgreed set with a "
                        + "written reason; the set above is what this run produced and the set below "
                        + "is what has been reviewed. Detail: "
                        + tally.discriminatingFullyAgreedOperations());

        // The other half of the split is printed, not asserted on here, and deliberately so. An
        // operation the subject agreed with on every driven row while the reference gave ONE answer
        // throughout is not a finding about the subject -- a constant is genuinely the right answer
        // for a constant operation, and every row is individually correct. It is a finding about the
        // CORPUS: that operation could not have failed, so its agreement figure is worth nothing.
        // Asserting "the degenerate bucket is degenerate" here would restate the predicate that
        // sorted it, which is the tautology D-20 is filed against. The enforcement that is NOT a
        // restatement is aNoLogicPortCannotProduceAStagePass below: it drives the real
        // Result.requireStagePass over a stage-shaped sweep and requires it to refuse.
    }

    // ------------------------------------------------------------------ the subjects

    /** What a subject is able to put in front of the harness. */
    enum Observability {
        /**
         * Never produces a value on any row: it throws, returns Java {@code null}, or returns one of
         * the {@link UValue} kinds that stand for the <em>absence</em> of a result. The invariant
         * applies in full — zero agreement rows.
         */
        NOTHING,
        /**
         * Produces values, but wrong ones. Some genuine agreement is the correct verdict (a constant
         * really is the right answer for an operation that is constant), so only the per-operation
         * form of the invariant applies.
         */
        WRONG_VALUES
    }

    /** One degenerate port, and what the harness is required to say about it. */
    static final class Subject {
        final String id;
        final String body;
        final Observability observes;
        final boolean abortsWithError;
        /**
         * Operations this subject is <em>allowed</em> to agree with on every driven row, each one
         * read by hand and justified in {@link #degenerateSubjects()}. Empty for every subject that
         * produces no values; the whole point of the per-operation assertion is that this set cannot
         * grow without someone writing down why.
         */
        final Set<String> reviewedFullyAgreed;
        private final Supplier<Candidate> factory;

        Subject(String id, String body, Observability observes, boolean abortsWithError,
                Set<String> reviewedFullyAgreed, Supplier<Candidate> factory) {
            this.id = id;
            this.body = body;
            this.observes = observes;
            this.abortsWithError = abortsWithError;
            this.reviewedFullyAgreed = reviewedFullyAgreed;
            this.factory = factory;
        }

        Candidate open() {
            return factory.get();
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /**
     * The operations a receiver-echoing subject is allowed to agree with completely <em>on every
     * driven row while the reference gave more than one answer</em>, and why each one is allowed.
     *
     * <p>All four are the same limit, stated four times. Their declared return types are raw Java,
     * checked with {@code javap -p} on the vendored {@code use.jar}:
     * <pre>
     *   public boolean       BooleanValue.value()
     *   public boolean       BooleanValue.isTrue()     // body: aload_0; getfield fValue:Z; ireturn
     *   public int           IntegerValue.value()
     *   public java.lang.String StringValue.value()
     * </pre>
     * {@code HistoricalOracle.fromHistorical} maps a raw {@code Boolean}/{@code Integer}/
     * {@code CharSequence} to the same {@link UValue.Kind} as {@code BooleanValue}/
     * {@code IntegerValue}/{@code StringValue}, so {@code BOOLEAN(true)} from a raw {@code boolean}
     * and {@code BOOLEAN(true)} from a {@code BooleanValue} are the same canonical string. Handing
     * back the receiver really is indistinguishable, at this instrument's resolution, from returning
     * the receiver's value — and for these four operations that <em>is</em> the correct answer, so
     * the agreement is genuine as far as it goes.
     *
     * <p>It is also the honest statement of the <em>limit</em>: the canonical vocabulary does not
     * separate a primitive result from a boxed {@code Value} result, so on the 193 of 285 operations
     * whose return type shares a canonical form with another Java type, a port returning the right
     * content with the wrong Java type is scored {@code AGREE} (defect D-18). Pinning this set is
     * what stops the blind spot spreading unremarked: a fifth operation becoming fully agreeable to
     * a subject that does nothing but hand back its receiver fails this test, and someone has to
     * explain it.
     *
     * <p><strong>Three of the four are new, and they are new because the corpora were widened.</strong>
     * {@code BooleanValue.*} and {@code StringValue.*} had no receiver to be driven on at all until
     * {@link InputGenerator#booleanBoundaries()} and {@link InputGenerator#stringBoundaries()} were
     * added (defect D-19); they were 100 % {@code HARNESS_ERROR} and invisible. Measuring more
     * surface found more of the limit, which is the correct direction.
     *
     * <p><strong>{@code RealValue.value()} was on this list and has been removed</strong>, and that
     * is not an improvement either. It is still fully agreed against the echoing subject; it is no
     * longer in the <em>discriminating</em> half of that set, because the shipped corpora contain
     * exactly <em>one</em> {@code RealValue} — {@code REAL(0.0)}, from
     * {@link InputGenerator#zeroDivisors()}. With one receiver, all 23 {@code RealValue.*} operations
     * have a one-point codomain by arithmetic, and nothing about them can be measured. It moves to
     * the labelled degenerate population rather than staying on a list that reads as a sign-off.
     */
    private static final Set<String> ECHO_SUBJECT_REVIEWED = Set.of(
            "BooleanValue.value()", "BooleanValue.isTrue()",
            "IntegerValue.value()", "StringValue.value()");

    static List<Subject> degenerateSubjects() {
        return List.of(
                new Subject("a-throws", "throw new RuntimeException(\"TODO: port \" + op.key())",
                        Observability.NOTHING, false, Set.of(),
                        () -> new DegeneratePort("unwritten-port", DegeneratePort.Body.THROW)),
                new Subject("b-returns-java-null", "return null",
                        Observability.NOTHING, false, Set.of(),
                        () -> new DegeneratePort("returns-java-null", DegeneratePort.Body.JAVA_NULL)),
                new Subject("c-empty-body", "{ } -- i.e. return UValue.voidValue()",
                        Observability.NOTHING, false, Set.of(),
                        () -> new DegeneratePort("do-nothing-port", DegeneratePort.Body.VOID_VALUE)),
                new Subject("d-returns-null-value", "return UValue.nullValue()",
                        Observability.NOTHING, false, Set.of(),
                        () -> new DegeneratePort("returns-null-value", DegeneratePort.Body.NULL_VALUE)),
                new Subject("e-fixed-constant", "return UValue.uBoolean(true, 1.0)",
                        Observability.WRONG_VALUES, false, Set.of(),
                        () -> new DegeneratePort("const-ubool-true", DegeneratePort.Body.CONSTANT)),
                new Subject("f-echoes-receiver", "return args.get(0)",
                        Observability.WRONG_VALUES, false, ECHO_SUBJECT_REVIEWED,
                        () -> new DegeneratePort("echoes-receiver", DegeneratePort.Body.FIRST_ARGUMENT)),
                new Subject("g-throws-error", "throw new AssertionError(\"TODO: port \" + op.key())",
                        Observability.NOTHING, true, Set.of(),
                        () -> new DegeneratePort("throws-error", DegeneratePort.Body.ERROR)));
    }

    /**
     * A port that does not exist yet, in every encoding of "does not exist" the harness has to be
     * safe against. This replaces the single throwing {@code UnwrittenPort} the invariant used to
     * name; see the class comment for why one instance was not enough.
     */
    static final class DegeneratePort implements Candidate {

        enum Body { THROW, JAVA_NULL, VOID_VALUE, NULL_VALUE, CONSTANT, FIRST_ARGUMENT, ERROR }

        private final String name;
        private final Body body;

        DegeneratePort(String name, Body body) {
            this.name = name;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            switch (body) {
                case THROW:
                    throw new RuntimeException("TODO: port " + op.key());
                case ERROR:
                    throw new AssertionError("TODO: port " + op.key());
                case JAVA_NULL:
                    return null;
                case VOID_VALUE:
                    // An empty method body in a language where every operation must return
                    // something: this is what Candidate's own Javadoc tells an adapter to write.
                    return UValue.voidValue();
                case NULL_VALUE:
                    return UValue.nullValue();
                case CONSTANT:
                    return UValue.uBoolean(true, 1.0);
                case FIRST_ARGUMENT:
                default:
                    return args.get(0);
            }
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    // ------------------------------------------------------------------ running one subject

    private static Tally sweep(Subject subject) {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        Map<String, List<UValue>> corpora = corpora(generator);
        Tally tally = new Tally(subject, generator.seed(), corpusSizes(corpora));

        try (HistoricalOracle oracle = HistoricalOracle.open();
             Candidate port = subject.open()) {

            List<UOp> operations = reachableOperations(oracle);
            tally.operations = operations.size();
            tally.jars = oracle.useJarPath().getFileName() + " + " + oracle.uncertaintyJarPath().getFileName();
            DifferentialSweep sweep = new DifferentialSweep(oracle, port, generator.seed());

            try {
                for (UOp op : operations) {
                    for (Map.Entry<String, List<UValue>> corpus : corpora.entrySet()) {
                        DifferentialSweep.Result result =
                                sweep.sweep(op, domains(op, corpora, corpus.getValue()));
                        tally.add(op, corpus.getKey(), result);
                        // The Result is deliberately not retained: this sweep is six figures of rows.
                    }
                }
            } catch (Error e) {
                // DifferentialSweep re-throws Error by design; record it rather than let it hide the
                // rows already collected.
                tally.escaped = e;
            }
        }
        return tally;
    }

    /** Per-operation counts. {@code driven} is the population an agreement claim can come from. */
    private static final class OperationTally {
        long rows;
        long driven;
        long agreed;
        long measured;
        /**
         * Every distinct canonical value the <em>reference</em> produced across this operation's
         * measured rows, unioned over the corpora. Taken from
         * {@link DifferentialSweep.Result#referenceValues()} rather than recomputed here: two
         * independently written implementations of one property is how they come to disagree.
         *
         * <p>Size 1 means the operation could not have failed, whatever its agreement rate.
         */
        final Set<String> referenceValues = new java.util.TreeSet<>();
    }

    /** Everything one subject's sweep produced, aggregated the several ways the assertions need. */
    private static final class Tally {
        final Subject subject;
        final long seed;
        final String corpora;
        String jars = "?";
        int operations;
        long rows;
        long agreementRows;
        long measuredRows;
        Throwable escaped;
        final Map<String, Long> verdicts = new TreeMap<>();
        final Map<String, OperationTally> perOperation = new TreeMap<>();
        final List<String> samples = new ArrayList<>();

        Tally(Subject subject, long seed, String corpora) {
            this.subject = subject;
            this.seed = seed;
            this.corpora = corpora;
        }

        void add(UOp op, String corpus, DifferentialSweep.Result result) {
            rows += result.rowCount();
            // Counted here row by row and also read off the Result: two independent routes to the
            // same number, so a measurementCount() that ever drifts from the verdicts is caught.
            int measuredHere = 0;
            for (DiffVerdict v : DiffVerdict.values()) {
                if (result.count(v) > 0) {
                    verdicts.merge(v.name(), (long) result.count(v), Long::sum);
                }
            }
            OperationTally per = perOperation.computeIfAbsent(op.key(), k -> new OperationTally());
            per.referenceValues.addAll(result.referenceValues());
            for (DiffRow row : result.rows()) {
                per.rows++;
                if (row.verdict() != DiffVerdict.HARNESS_ERROR && row.verdict() != DiffVerdict.UNSUPPORTED) {
                    per.driven++;
                }
                if (row.verdict() == DiffVerdict.AGREE || row.verdict() == DiffVerdict.DIFFER) {
                    per.measured++;
                    measuredRows++;
                    measuredHere++;
                }
                if (row.verdict().isAgreement()) {
                    per.agreed++;
                    agreementRows++;
                    if (samples.size() < SAMPLE_LIMIT) {
                        samples.add("[corpus " + corpus + "] " + row.toTsv());
                    }
                }
            }
            assertEquals(measuredHere, result.measurementCount(),
                    "Result.measurementCount() must agree with the verdicts it is derived from, "
                            + "for " + op.key() + " over corpus " + corpus);
            assertEquals(result.agreements().size() + result.disagreements().size(),
                    result.rowCount(), "the two partitions must still cover every row");
        }

        long differRows() {
            return verdicts.getOrDefault(DiffVerdict.DIFFER.name(), 0L);
        }

        /**
         * Operations every driven row of which was scored an agreement. The per-operation form of
         * the invariant: an aggregate of 444 out of 471471 looks like noise, while
         * "144 of the 144 rows we could drive" does not.
         */
        Map<String, String> fullyAgreedOperations() {
            Map<String, String> out = new TreeMap<>();
            perOperation.forEach((key, per) -> {
                if (per.driven > 0 && per.agreed == per.driven) {
                    out.put(key, per.agreed + "/" + per.driven + " driven rows agreed, "
                            + per.rows + " rows total, "
                            + per.referenceValues.size() + " distinct reference value(s)"
                            + (per.referenceValues.size() == 1
                                    ? " -- always " + per.referenceValues.iterator().next()
                                      + " [NOT DISCRIMINATING]"
                                    : ""));
                }
            });
            return out;
        }

        /**
         * The fully-agreed operations that could actually have failed — at least
         * {@link DifferentialSweep.Result#DISCRIMINATING_MINIMUM} distinct reference values.
         *
         * <p>This is the subset that has to be reviewed and signed off one at a time. An operation
         * that is fully agreed <em>and</em> single-valued is not a finding about the subject at all:
         * the subject guessed a constant and the constant was right, which is what
         * {@link #degenerateFullyAgreedOperations()} records instead.
         */
        Map<String, String> discriminatingFullyAgreedOperations() {
            Map<String, String> out = new TreeMap<>();
            fullyAgreedOperations().forEach((key, detail) -> {
                if (perOperation.get(key).referenceValues.size()
                        >= DifferentialSweep.Result.DISCRIMINATING_MINIMUM) {
                    out.put(key, detail);
                }
            });
            return out;
        }

        /** The fully-agreed operations whose reference answered the same thing every time. */
        Map<String, String> degenerateFullyAgreedOperations() {
            Map<String, String> out = new TreeMap<>();
            fullyAgreedOperations().forEach((key, detail) -> {
                if (perOperation.get(key).referenceValues.size()
                        < DifferentialSweep.Result.DISCRIMINATING_MINIMUM) {
                    out.put(key, detail);
                }
            });
            return out;
        }

        /**
         * The codomain census, in one line: how many operations the reference answered with 0, 1 or
         * many distinct values over this sweep.
         *
         * <p>Counted over <em>every</em> operation, driven or not, because "zero measurements" is a
         * coverage fact a reader needs as badly as the other two.
         */
        String codomainCensus() {
            int zero = 0;
            int one = 0;
            int many = 0;
            for (OperationTally per : perOperation.values()) {
                int n = per.referenceValues.size();
                if (n == 0) {
                    zero++;
                } else if (n < DifferentialSweep.Result.DISCRIMINATING_MINIMUM) {
                    one++;
                } else {
                    many++;
                }
            }
            return perOperation.size() + " operations: " + zero + " measured nothing, " + one
                    + " single-valued (NOT DISCRIMINATING), " + many + " discriminating";
        }

        void print() {
            System.out.println("=== unwritten-port invariant: " + subject.id + " =================");
            System.out.println("seed                 " + seed);
            System.out.println("subject              " + subject.id + "  (every method body: "
                    + subject.body + ")");
            System.out.println("observability        " + subject.observes);
            System.out.println("operations           " + operations + "  (enumerated from " + jars + ")");
            System.out.println("corpora              " + corpora);
            System.out.println("rows                 " + rows);
            System.out.println("measured rows        " + measuredRows + "  (AGREE + DIFFER)");
            System.out.println("agreement rows       " + agreementRows);
            System.out.println("verdict tally        " + verdicts);
            System.out.println("codomain census      " + codomainCensus());
            if (escaped != null) {
                System.out.println("ESCAPED              " + escaped
                        + "  -> the sweep ABORTED; rows above are only those completed before it");
            }
            Map<String, String> discriminating = discriminatingFullyAgreedOperations();
            Map<String, String> degenerate = degenerateFullyAgreedOperations();
            System.out.println("fully agreed ops, DISCRIMINATING (a finding about the subject)  "
                    + (discriminating.isEmpty() ? "(none)" : ""));
            discriminating.forEach((key, detail) -> System.out.println("  *** " + key + "  (" + detail
                    + (subject.reviewedFullyAgreed.contains(key) ? "; reviewed and signed off)" : ")")));
            System.out.println("fully agreed ops, NOT DISCRIMINATING (a finding about the corpus)  "
                    + (degenerate.isEmpty() ? "(none)" : degenerate.size() + " operations"));
            degenerate.forEach((key, detail) -> System.out.println("  --- " + key + "  (" + detail + ")"));
            if (agreementRows > 0) {
                System.out.println("--- per-operation agreement tally (agreed/driven/rows) ------------");
                perOperation.forEach((key, per) -> {
                    if (per.agreed > 0) {
                        System.out.println("  " + per.agreed + "/" + per.driven + "/" + per.rows
                                + "\t" + key);
                    }
                });
                System.out.println("--- first " + samples.size() + " agreement rows -----------------------------------");
                System.out.println(DiffRow.TSV_HEADER);
                samples.forEach(System.out::println);
            }
            System.out.println("===================================================================");
        }
    }

    // ------------------------------------------------------------------ D-15: the fourth door

    /**
     * <strong>The 120-literal subject, and the gate that has to refuse it.</strong>
     *
     * <p>This is the fourth shape of the one defect this class exists for, and it is the only one of
     * the four that needs no bug in {@link DifferentialSweep} at all. Rounds 1–3 were all <em>the
     * absence of a measurement scored as an agreement</em>: a harness failure, two throws, two
     * {@code VOID}s. Round 4 is two <em>real</em> values, correctly compared and correctly equal,
     * over an operation whose reference side answers the same thing on every input the corpora can
     * supply. Every row is right. The sweep-level claim — which is the level a stage reads — is not.
     *
     * <h2>What this test does</h2>
     * <ol>
     *   <li><strong>Stage-shaped domains.</strong> The parameterised invariant above sweeps every
     *       operation over the union of every corpus, so nearly every row is a
     *       {@link DiffVerdict#HARNESS_ERROR} from a receiver of the wrong type and no operation is
     *       ever free of disagreements. That is the right shape for finding cross-type defects and
     *       the wrong shape for asking "would a stage read this as a pass?". Here each operation is
     *       driven over <em>its own</em> receiver type's corpus, which is what S4 will do.</li>
     *   <li><strong>The literals.</strong> {@link #constantTable} asks the historical oracle, once
     *       per operation, what it answers — and hands that answer back verbatim on every row
     *       thereafter. That is precisely the subject the round-4 census constructed by hand: a class
     *       of one-line method bodies, no arithmetic, no branching, never reading its receiver or its
     *       arguments. Deriving the literals mechanically rather than typing them keeps the attack
     *       correct as the corpora change; it does not make the subject any less empty.</li>
     *   <li><strong>The assertions.</strong> The attack must still land (some operations really are
     *       fully agreed and {@code isClean()} against it — if that ever becomes zero, this test has
     *       stopped testing anything and someone must find out why); every one of those must be
     *       <em>refused</em> by {@link DifferentialSweep.Result#requireStagePass}; no operation at all
     *       may reach a stage pass against this subject; and — the control that stops the whole thing
     *       being a blanket refusal — a faithful subject on a discriminating operation must pass.</li>
     * </ol>
     */
    @Test
    @DisplayName("D-15: a no-logic port of hardcoded literals is refused by the stage gate, "
            + "and the gate still passes a real port")
    void aNoLogicPortCannotProduceAStagePass() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        Map<String, List<UValue>> corpora = corpora(generator);

        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            List<UOp> operations = reachableOperations(oracle);
            Map<String, UValue> literals = new TreeMap<>();
            Map<String, Set<String>> observed = new TreeMap<>();
            Map<String, List<List<UValue>>> tuplesByOp = new LinkedHashMap<>();

            for (UOp op : operations) {
                List<List<UValue>> tuples = tuples(stageDomains(op, corpora));
                tuplesByOp.put(op.key(), tuples);
                Set<String> canonicals = new java.util.TreeSet<>();
                for (List<UValue> tuple : tuples) {
                    UValue produced;
                    try {
                        produced = oracle.invoke(op, tuple);
                    } catch (Throwable t) {
                        continue; // threw, or the harness could not marshal: no value to record
                    }
                    canonicals.add(produced.canonical());
                    if (produced.carriesAnObservation() && !literals.containsKey(op.key())) {
                        literals.put(op.key(), produced);
                    }
                }
                observed.put(op.key(), canonicals);
            }

            int zeroMeasured = 0;
            int singleValued = 0;
            int discriminating = 0;
            int cleanAndDegenerate = 0;
            int refusedByTheGate = 0;
            Map<String, String> stagePasses = new TreeMap<>();
            List<String> degenerateButClean = new ArrayList<>();
            List<String> measuredNothing = new ArrayList<>();

            try (Candidate port = new ConstantTablePort("constant-table", literals)) {
                DifferentialSweep sweep = new DifferentialSweep(oracle, port, generator.seed());
                for (UOp op : operations) {
                    DifferentialSweep.Result result = sweep.run(op, tuplesByOp.get(op.key()));

                    int distinct = result.distinctReferenceValues();
                    if (distinct == 0) {
                        zeroMeasured++;
                        measuredNothing.add(op.key() + "  " + result.summary());
                    } else if (distinct < DifferentialSweep.Result.DISCRIMINATING_MINIMUM) {
                        singleValued++;
                    } else {
                        discriminating++;
                    }

                    // The harness's own referenceValues() against an independently collected set:
                    // for every operation this subject has a literal for, the subject returns on
                    // every row, so the measured rows are exactly the rows the reference returned on.
                    if (literals.containsKey(op.key())) {
                        assertEquals(observed.get(op.key()), new java.util.TreeSet<>(result.referenceValues()),
                                "Result.referenceValues() must be the reference's canonical forms over "
                                        + "the measured rows of " + op.key() + ", no more and no less");
                    }

                    if (result.isStagePass(1, AcceptedDegenerateOperations.none())) {
                        stagePasses.put(op.key(), result.stageStatement(AcceptedDegenerateOperations.none()));
                    }
                    if (result.isClean() && !result.isDiscriminating()) {
                        cleanAndDegenerate++;
                        if (degenerateButClean.size() < SAMPLE_LIMIT) {
                            degenerateButClean.add(result.stageStatement(
                                    AcceptedDegenerateOperations.none()));
                        }
                        // THE GATE. isClean() says pass; the stage predicate must not.
                        IllegalStateException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                                IllegalStateException.class,
                                () -> result.requireStagePass(1, AcceptedDegenerateOperations.none()),
                                "isClean() is true for " + op.key() + " against a subject of one "
                                        + "hardcoded literal, and the stage gate let it through");
                        assertTrue(refusal.getMessage().contains("distinct value(s)"), refusal.getMessage());
                        assertTrue(refusal.getMessage().contains("D-15"), refusal.getMessage());
                        refusedByTheGate++;
                    }
                }
            }

            System.out.println("=== D-15: the constant-literal subject, stage-shaped ==============");
            System.out.println("seed                       " + generator.seed());
            System.out.println("corpora                    " + corpusSizes(corpora));
            System.out.println("operations                 " + operations.size());
            System.out.println("literals the subject holds " + literals.size()
                    + "  (one per operation the reference ever answered with a value)");
            System.out.println("codomain census            " + operations.size() + " operations: "
                    + zeroMeasured + " measured nothing, " + singleValued
                    + " single-valued (NOT DISCRIMINATING), " + discriminating + " discriminating");
            System.out.println("isClean() AND degenerate   " + cleanAndDegenerate
                    + "   <- the size of the door: a stage asserting isClean() reads these as PASS");
            System.out.println("refused by the stage gate  " + refusedByTheGate + " of "
                    + cleanAndDegenerate);
            System.out.println("stage passes (must be 0)   " + stagePasses.size());
            stagePasses.forEach((key, detail) -> System.out.println("  *** " + detail));
            System.out.println("--- operations that measured NOTHING (" + measuredNothing.size()
                    + ") -------------------------");
            measuredNothing.forEach(s -> System.out.println("  ... " + s));
            System.out.println("--- first " + degenerateButClean.size()
                    + " clean-but-degenerate operations ------------------");
            degenerateButClean.forEach(s -> System.out.println("  --- " + s));
            System.out.println("===================================================================");

            assertTrue(cleanAndDegenerate > 0,
                    "the D-15 attack must still be constructible against this corpus. If it is not, "
                            + "this test has stopped exercising the gate and someone has to find out "
                            + "why before reading its green as reassurance.");
            assertEquals(cleanAndDegenerate, refusedByTheGate,
                    "every clean-but-degenerate sweep must be refused by the stage gate");
            assertEquals(java.util.Collections.emptyMap(), stagePasses,
                    "a subject consisting of one hardcoded literal per operation reached a STAGE PASS. "
                            + "Every entry above is an operation on which a port containing no logic "
                            + "would be reported as faithful.");

            // The control. A gate that refuses everything is not a gate, and a refusal that costs
            // nothing to satisfy is not enforcement either -- so both directions are pinned here.
            try (StubCandidate faithful = StubCandidate.faithful()) {
                UOp add = UOp.binary("URealValue", "add");
                DifferentialSweep.Result good = new DifferentialSweep(oracle, faithful, generator.seed())
                        .run(add, tuplesByOp.get(add.key()));
                System.out.println("CONTROL, faithful port     " + good.stageStatement(
                        AcceptedDegenerateOperations.none()));
                assertTrue(good.isDiscriminating(),
                        "URealValue.add(value) over the UReal corpus must produce many reference "
                                + "values, or the control proves nothing: " + good.summary());
                good.requireStagePass(100, AcceptedDegenerateOperations.none());
            }
        }
    }

    /**
     * The sign-off route, both directions: a degenerate operation passes the gate once a human has
     * written down why its constant answer is the whole of its specification, and only for the exact
     * value that was reviewed.
     */
    @Test
    @DisplayName("D-15: a degenerate operation passes only with a written, value-keyed sign-off")
    void aDegenerateOperationNeedsAWrittenSignOff() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        Map<String, List<UValue>> corpora = corpora(generator);
        UOp isUReal = UOp.unary("URealValue", "isUReal");

        try (HistoricalOracle oracle = HistoricalOracle.open()) {
            Map<String, UValue> literals = new TreeMap<>();
            literals.put(isUReal.key(), UValue.bool(true));
            try (Candidate port = new ConstantTablePort("constant-true", literals)) {
                DifferentialSweep.Result result = new DifferentialSweep(oracle, port, generator.seed())
                        .run(isUReal, tuples(stageDomains(isUReal, corpora)));

                System.out.println("=== D-15: the sign-off route ======================================");
                System.out.println("no sign-off                " + result.stageStatement(
                        AcceptedDegenerateOperations.none()));

                assertTrue(result.isClean(), "precondition: the old predicate says pass: " + result.summary());
                assertEquals(1, result.distinctReferenceValues(), result.summary());
                assertEquals("BOOLEAN(true)", result.soleReferenceValue());
                assertFalse(result.isStagePass(1, AcceptedDegenerateOperations.none()),
                        "unsigned, a single-valued operation is not a pass");

                AcceptedDegenerateOperations signed = AcceptedDegenerateOperations.builder()
                        .accept(isUReal.key(), "BOOLEAN(true)",
                                "URealValue.isUReal() is a type predicate: the historical body is "
                                        + "iconst_1/ireturn, so BOOLEAN(true) is the whole of its "
                                        + "specification and no corpus can make it answer otherwise. "
                                        + "Agreement here shows the operation exists and is reachable; "
                                        + "it is not evidence about any computation.")
                        .build();
                System.out.println("signed off                 " + result.stageStatement(signed));
                System.out.println("===================================================================");

                assertTrue(result.isStagePass(1, signed), "a written sign-off must open the gate");
                assertTrue(result.stageStatement(signed).contains("acknowledged: URealValue.isUReal() "
                        + "is a type predicate"), result.stageStatement(signed));

                // Keyed on the VALUE as well as the operation: a sign-off reviewed against one
                // answer must not survive the operation starting to answer something else.
                AcceptedDegenerateOperations wrongValue = AcceptedDegenerateOperations.builder()
                        .accept(isUReal.key(), "BOOLEAN(false)", "same operation, the other answer")
                        .build();
                assertFalse(result.isStagePass(1, wrongValue),
                        "a sign-off written against a different value must not match");
                AcceptedDegenerateOperations wrongOp = AcceptedDegenerateOperations.builder()
                        .accept("URealValue.isDefined()", "BOOLEAN(true)", "a different operation")
                        .build();
                assertFalse(result.isStagePass(1, wrongOp),
                        "a sign-off written against a different operation must not match");

                org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> AcceptedDegenerateOperations.builder()
                                .accept(isUReal.key(), "BOOLEAN(true)", "  "),
                        "a blank rationale is exactly the blanket exemption this class prevents");
            }
        }
    }

    /**
     * A port whose every method body is one hardcoded literal, looked up by operation key. No
     * arithmetic, no branching; the receiver and the arguments are never read. Operations with no
     * literal throw, so they can never be mistaken for implemented ones.
     */
    static final class ConstantTablePort implements Candidate {

        private final String name;
        private final Map<String, UValue> literals;

        ConstantTablePort(String name, Map<String, UValue> literals) {
            this.name = name;
            this.literals = literals;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            UValue literal = literals.get(op.key());
            if (literal == null) {
                throw new RuntimeException("TODO: port " + op.key());
            }
            return literal;
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }

    /**
     * The values in {@code corpora} that can be marshalled into a receiver of {@code receiverType} —
     * i.e. the corpus a stage would sweep that operation over. Derived from the kind, so a corpus
     * added to {@link #corpora(InputGenerator)} widens this automatically.
     */
    static List<UValue> receiverCorpus(String receiverType, Map<String, List<UValue>> corpora) {
        List<UValue> out = new ArrayList<>();
        for (UValue v : allValues(corpora)) {
            if (receiverType.equals(receiverTypeOf(v))) {
                out.add(v);
            }
        }
        return out;
    }

    /** The historical receiver class a value of this kind marshals to, or {@code null}. */
    private static String receiverTypeOf(UValue value) {
        switch (value.kind()) {
            case UREAL:    return "URealValue";
            case UINTEGER: return "UIntegerValue";
            case UBOOLEAN: return "UBooleanValue";
            case USTRING:  return "UStringValue";
            case REAL:     return "RealValue";
            case INTEGER:  return "IntegerValue";
            case BOOLEAN:  return "BooleanValue";
            case STRING:   return "StringValue";
            default:       return null;
        }
    }

    /**
     * Domains for a stage-shaped sweep: the operation's own receiver corpus, and arguments of the
     * same type (plus a slice of {@link InputGenerator#indexBoundaries()} for primitive parameters,
     * which is where the historical API takes raw {@code int}/{@code double}/{@code float}).
     */
    private static List<List<UValue>> stageDomains(UOp op, Map<String, List<UValue>> corpora) {
        List<UValue> receivers = receiverCorpus(op.receiverType(), corpora);
        List<List<UValue>> domains = new ArrayList<>(op.arity());
        domains.add(receivers);
        for (int i = 0; i < op.params().size(); i++) {
            List<UValue> source = op.params().get(i) == UOp.ParamKind.VALUE
                    ? receivers
                    : InputGenerator.indexBoundaries();
            domains.add(i == 0 ? source
                    : source.subList(0, Math.min(EXTRA_PARAM_SLICE, source.size())));
        }
        return domains;
    }

    /** Cartesian product, in the same order {@link DifferentialSweep#sweep} would build it. */
    private static List<List<UValue>> tuples(List<List<UValue>> domains) {
        List<List<UValue>> out = new ArrayList<>();
        out.add(new ArrayList<>());
        for (List<UValue> domain : domains) {
            List<List<UValue>> next = new ArrayList<>();
            for (List<UValue> prefix : out) {
                for (UValue v : domain) {
                    List<UValue> extended = new ArrayList<>(prefix);
                    extended.add(v);
                    next.add(extended);
                }
            }
            out = next;
        }
        return out;
    }

    // ------------------------------------------------------------------ partitioning

    @Test
    @DisplayName("exactly two verdicts are agreements, and no verdict is invisible to an accessor")
    void agreementIsOnlyEverAnObservedValue() {
        for (DiffVerdict v : DiffVerdict.values()) {
            boolean expected = v == DiffVerdict.AGREE || v == DiffVerdict.ACCEPTED_THROW;
            assertEquals(expected, v.isAgreement(),
                    v + ".isAgreement() -- only a compared pair of values, or a throw-pair that a "
                            + "human signed off through AcceptedThrowPairs, may be an agreement");
        }
        assertFalse(DiffVerdict.BOTH_THREW.isAgreement(),
                "two throws are not evidence that two implementations behave the same way");

        // agreements() and disagreements() must partition the rows: a verdict that is a
        // non-agreement in one accessor and invisible in the other is the same defect renamed.
        UOp op = UOp.binary("URealValue", "add");
        List<UValue> domain = List.of(UValue.uReal(1.0, 0.5), UValue.uReal(2.0, 0.25),
                UValue.uInteger(3, 0.5));
        try (Candidate a = StubCandidate.faithful();
             Candidate b = new DegeneratePort("unwritten-port", DegeneratePort.Body.THROW)) {
            DifferentialSweep.Result mixed = new DifferentialSweep(a, b, 1L).sweepBinary(op, domain, domain);
            assertEquals(mixed.rowCount(),
                    mixed.agreements().size() + mixed.disagreements().size(),
                    "every row must be in exactly one of the two partitions");
            assertEquals(0, mixed.agreements().size());
            int tallied = 0;
            for (DiffVerdict v : DiffVerdict.values()) {
                tallied += mixed.count(v);
            }
            assertEquals(mixed.rowCount(), tallied, "the tally must account for every row");
        }
    }

    // ------------------------------------------------------------------ inventory

    /**
     * Every operation the harness can reach, read out of the historical jars: for each receiver in
     * {@link HistoricalOracle#marshallableReceiverTypes()}, every public instance method whose
     * parameters are all expressible as a {@link UOp.ParamKind}.
     */
    static List<UOp> reachableOperations(HistoricalOracle oracle) {
        Class<?> valueClass = oracle.historicalClass("Value");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<UOp> ops = new ArrayList<>();
        for (String receiver : HistoricalOracle.marshallableReceiverTypes()) {
            for (Method m : oracle.historicalClass(receiver).getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() == Object.class) {
                    continue;
                }
                UOp.ParamKind[] kinds = kindsOf(valueClass, m);
                if (kinds == null) {
                    continue;
                }
                UOp op = UOp.of(receiver, m.getName(), kinds);
                if (seen.add(op.key())) {
                    ops.add(op);
                }
            }
        }
        ops.sort(Comparator.comparing(UOp::key));
        return ops;
    }

    /** {@code null} when any parameter type is one {@link UOp} cannot name. */
    private static UOp.ParamKind[] kindsOf(Class<?> valueClass, Method m) {
        Class<?>[] types = m.getParameterTypes();
        UOp.ParamKind[] kinds = new UOp.ParamKind[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == valueClass) {
                kinds[i] = UOp.ParamKind.VALUE;
            } else if (types[i] == int.class) {
                kinds[i] = UOp.ParamKind.INT;
            } else if (types[i] == double.class) {
                kinds[i] = UOp.ParamKind.DOUBLE;
            } else if (types[i] == float.class) {
                kinds[i] = UOp.ParamKind.FLOAT;
            } else {
                return null;
            }
        }
        return kinds;
    }

    /**
     * Every corpus {@link InputGenerator} ships, in a fixed order.
     *
     * <p>{@code boolean} and {@code string} were added after the round-4 measurement that
     * {@code BooleanValue} and {@code StringValue} are in
     * {@code HistoricalOracle.MARSHALLABLE_RECEIVERS} — so {@code supports()} said yes for all 52 of
     * their operations — while no corpus contained a single {@code BOOLEAN} or {@code STRING}, so
     * every row of all 52 was {@code HARNESS_ERROR} and the whole sweep measured them zero times
     * (defect D-19).
     */
    static Map<String, List<UValue>> corpora(InputGenerator generator) {
        Map<String, List<UValue>> out = new LinkedHashMap<>();
        out.put("uReal", generator.uRealCorpus(RANDOM_DRAWS));
        out.put("uInteger", generator.uIntegerCorpus(RANDOM_DRAWS));
        out.put("uBoolean", generator.uBooleanCorpus(RANDOM_DRAWS));
        out.put("uString", generator.uStringCorpus(RANDOM_DRAWS));
        out.put("boolean", generator.booleanCorpus(RANDOM_DRAWS));
        out.put("string", generator.stringCorpus(RANDOM_DRAWS));
        out.put("zeroDivisors", InputGenerator.zeroDivisors());
        out.put("indexBoundaries", InputGenerator.indexBoundaries());
        return out;
    }

    /**
     * Receivers are every value the harness ships, deduplicated: the receiver position is where a
     * cross-type input reaches an operation, and that is exactly the D1 shape.
     */
    private static List<UValue> allValues(Map<String, List<UValue>> corpora) {
        LinkedHashSet<UValue> out = new LinkedHashSet<>();
        corpora.values().forEach(out::addAll);
        return new ArrayList<>(out);
    }

    private static List<List<UValue>> domains(UOp op, Map<String, List<UValue>> corpora,
                                              List<UValue> argumentCorpus) {
        List<List<UValue>> domains = new ArrayList<>(op.arity());
        domains.add(allValues(corpora));
        for (int i = 0; i < op.params().size(); i++) {
            domains.add(i == 0 ? argumentCorpus
                    : argumentCorpus.subList(0, Math.min(EXTRA_PARAM_SLICE, argumentCorpus.size())));
        }
        return domains;
    }

    private static String corpusSizes(Map<String, List<UValue>> corpora) {
        StringBuilder sb = new StringBuilder();
        corpora.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ", ").append(k).append('=')
                .append(v.size()));
        return sb.append("; receivers=").append(allValues(corpora).size()).toString();
    }
}
