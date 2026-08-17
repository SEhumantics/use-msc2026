package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * {@code java.lang.RuntimeException}s scored as a matching throw — is a different route to the same
 * false statement, "both sides did the same thing" asserted where no comparison happened. Pinning
 * each route with its own regression test is chasing instances; the instances kept coming back
 * through a route nobody had pinned yet. The invariant closes the whole family: if a subject whose
 * every method body is {@code throw new RuntimeException("TODO: port ...")} can score one single
 * agreement row, the instrument is lying, whatever the mechanism.
 *
 * <p>It also widens by itself. The operation inventory is enumerated by reflection from the
 * <em>historical jars</em> (see {@link #reachableOperations}), not from a hand-written list, and the
 * input corpora come from {@link InputGenerator}'s own accessors, so adding a boundary value or
 * reaching a new receiver type automatically enlarges what this test covers.
 *
 * <p>Measured by this very test method, on the pre-fix harness, against a subject containing no code
 * at all:
 * <pre>
 *   operations 285   rows 471471   agreement rows 21816
 *   verdict tally {AGREE_THROWN=21816, DIFFER_THROWN=8764, HARNESS_ERROR=388695, MIXED=52196}
 * </pre>
 * 21 816 rows of agreement, spread over 27 distinct operations on three receiver types. After the
 * fix, the same sweep:
 * <pre>
 *   operations 285   rows 471471   agreement rows 0
 *   verdict tally {BOTH_THREW=30580, HARNESS_ERROR=388695, MIXED=52196}
 * </pre>
 * Note that {@code 21816 + 8764 == 30580}: no row changed population, only the claim made about it.
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

    @Test
    @DisplayName("a Candidate that implements nothing produces ZERO agreement rows, everywhere")
    void anUnwrittenPortAgreesWithNothing() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        Map<String, List<UValue>> corpora = corpora(generator);

        try (HistoricalOracle oracle = HistoricalOracle.open();
             Candidate unwritten = new UnwrittenPort()) {

            List<UOp> operations = reachableOperations(oracle);
            DifferentialSweep sweep = new DifferentialSweep(oracle, unwritten, generator.seed());

            long rows = 0;
            long agreementRows = 0;
            Map<String, Long> perOperationAgreement = new TreeMap<>();
            Map<String, Long> perVerdict = new TreeMap<>();
            List<String> samples = new ArrayList<>();

            for (UOp op : operations) {
                for (Map.Entry<String, List<UValue>> corpus : corpora.entrySet()) {
                    DifferentialSweep.Result result = sweep.sweep(op, domains(op, corpora, corpus.getValue()));
                    rows += result.rowCount();
                    for (DiffVerdict v : DiffVerdict.values()) {
                        if (result.count(v) > 0) {
                            perVerdict.merge(v.name(), (long) result.count(v), Long::sum);
                        }
                    }
                    for (DiffRow row : result.rows()) {
                        if (row.verdict().isAgreement()) {
                            agreementRows++;
                            perOperationAgreement.merge(op.key() + "  [corpus " + corpus.getKey() + "]",
                                    1L, Long::sum);
                            if (samples.size() < SAMPLE_LIMIT) {
                                samples.add(row.toTsv());
                            }
                        }
                    }
                    // The Result is deliberately not retained: this sweep is six figures of rows.
                }
            }

            System.out.println("=== unwritten-port invariant ======================================");
            System.out.println("seed                 " + generator.seed());
            System.out.println("subject              " + unwritten.name()
                    + "  (every method body: throw new RuntimeException(\"TODO: port \" + op.key()))");
            System.out.println("operations           " + operations.size() + "  (enumerated from "
                    + oracle.useJarPath().getFileName() + " + "
                    + oracle.uncertaintyJarPath().getFileName() + ")");
            System.out.println("corpora              " + corpusSizes(corpora));
            System.out.println("rows                 " + rows);
            System.out.println("agreement rows       " + agreementRows);
            System.out.println("verdict tally        " + perVerdict);
            if (agreementRows > 0) {
                System.out.println("--- per-operation agreement tally ---------------------------------");
                perOperationAgreement.forEach((k, v) -> System.out.println("  " + v + "\t" + k));
                System.out.println("--- first " + samples.size() + " agreement rows -----------------------------------");
                System.out.println(DiffRow.TSV_HEADER);
                samples.forEach(System.out::println);
            }
            System.out.println("===================================================================");

            assertTrue(operations.size() >= 100,
                    "the enumeration must actually find the historical operations, got "
                            + operations.size());
            assertTrue(rows > 10_000, "the sweep must be large enough to be evidence, got " + rows);
            assertEquals(0L, agreementRows,
                    "a subject with no code in it agreed with the historical implementation on "
                            + agreementRows + " of " + rows + " rows. Every agreement row printed above "
                            + "is the harness asserting that two sides did the same thing where no "
                            + "comparison was made.");
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
        try (Candidate a = StubCandidate.faithful(); Candidate b = new UnwrittenPort()) {
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

    // ------------------------------------------------------------------ the subject

    /**
     * A port that does not exist yet: every operation is claimed, and every operation throws. This
     * is not a strawman — it is the literal state of the tree at the start of S4, and it is what
     * every fidelity claim from S4 onwards is measured against.
     */
    static final class UnwrittenPort implements Candidate {

        @Override
        public String name() {
            return "unwritten-port";
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) {
            throw new RuntimeException("TODO: port " + op.key());
        }

        @Override
        public boolean supports(UOp op) {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
