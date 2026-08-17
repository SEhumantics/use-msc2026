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

        assertEquals(subject.reviewedFullyAgreed, tally.fullyAgreedOperations().keySet(),
                "operations scored agreement on EVERY row the harness could drive, against the "
                        + "subject '" + subject.id + "' (" + subject.body + "). Each one has to be "
                        + "read and either fixed or signed off in the subject's reviewedFullyAgreed "
                        + "set with a written reason; the set above is what this run produced and "
                        + "the set below is what has been reviewed. Detail: "
                        + tally.fullyAgreedOperations());
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
     * The two operations a receiver-echoing subject is allowed to agree with completely, and why.
     *
     * <p>{@code IntegerValue.value()} is declared {@code public int value()} and
     * {@code RealValue.value()} is {@code public double value()} — checked with {@code javap} on the
     * vendored {@code use.jar}. The harness canonicalises a raw {@code int} result and an
     * {@code IntegerValue} result to the same string, {@code INTEGER(n)}, so echoing the receiver
     * really is indistinguishable from returning its value, and the agreement is genuine at the
     * level this instrument measures at.
     *
     * <p>It is also the honest statement of a <em>limit</em>: the canonical vocabulary does not
     * separate a primitive result from a boxed {@code Value} result, so a port that returns the
     * wrong one of those two would be scored {@code AGREE} here. That limit is recorded rather than
     * papered over, and pinning the set is what stops it spreading: if a third operation ever
     * becomes fully agreeable to a subject that does nothing but hand back its receiver, this test
     * fails and someone has to explain the new one.
     */
    private static final Set<String> ECHO_SUBJECT_REVIEWED = Set.of(
            "IntegerValue.value()", "RealValue.value()");

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
                            + per.rows + " rows total");
                }
            });
            return out;
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
            if (escaped != null) {
                System.out.println("ESCAPED              " + escaped
                        + "  -> the sweep ABORTED; rows above are only those completed before it");
            }
            Map<String, String> fullyAgreed = fullyAgreedOperations();
            System.out.println("fully agreed ops     " + (fullyAgreed.isEmpty() ? "(none)" : ""));
            fullyAgreed.forEach((key, detail) -> System.out.println("  *** " + key + "  (" + detail
                    + (subject.reviewedFullyAgreed.contains(key) ? "; reviewed and signed off)" : ")")));
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

    /** Every corpus {@link InputGenerator} ships, in a fixed order. */
    static Map<String, List<UValue>> corpora(InputGenerator generator) {
        Map<String, List<UValue>> out = new LinkedHashMap<>();
        out.put("uReal", generator.uRealCorpus(RANDOM_DRAWS));
        out.put("uInteger", generator.uIntegerCorpus(RANDOM_DRAWS));
        out.put("uBoolean", generator.uBooleanCorpus(RANDOM_DRAWS));
        out.put("uString", generator.uStringCorpus(RANDOM_DRAWS));
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
