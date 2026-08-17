package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <strong>Detection power.</strong> The standing invariant next door asks whether the harness can be
 * made to claim agreement it never measured. This class asks the opposite and, for stages S4–S7, the
 * more consequential question:
 *
 * <blockquote><strong>if a ported U-type contains a realistic, subtle infidelity, does this harness
 * see it — and on which operations, and on how many rows?</strong></blockquote>
 *
 * <p>A harness that never lies is worthless if it never detects anything. Everything measured here
 * is the second half of that pair, and the entries it <em>cannot</em> see are the most valuable
 * output of the class.
 *
 * <h2>How a "realistic port with one subtle bug" is constructed</h2>
 * Hand-writing a near-faithful port of 285 operations in order to plant one bug in it would measure
 * the quality of the hand-written port, not the power of the instrument: every place my
 * re-implementation drifted from the historical code would show up as detection I did not plant.
 * So the subject is built the other way round. A <em>second, independent</em>
 * {@link HistoricalOracle} — its own isolated class loader, its own copy of the jars — plays the
 * part of a <strong>perfect port</strong>, and a {@link MutantPort} wrapper applies exactly one
 * named infidelity to exactly the operations that infidelity would touch. Every other operation is
 * bit-for-bit the historical behaviour.
 *
 * <p>That gives the experiment a control it would otherwise lack: probe {@code P0} is the same
 * wrapper with the identity mutation, and it must produce zero {@link DiffVerdict#DIFFER} and zero
 * {@link DiffVerdict#MIXED} rows across the whole inventory. Any row a mutant diverges on is
 * therefore attributable to the planted defect and to nothing else, and any operation a mutant does
 * <em>not</em> diverge on is a blind spot of the instrument rather than an accident of my typing.
 *
 * <p>Domains are stage-shaped — each operation over its own receiver type's corpus, which is what
 * S4 will do — via {@link UnwrittenPortInvariantTest#stageDomains}, so the numbers here are directly
 * comparable with the D-15 census in that class.
 *
 * <p>Test-scoped. Not part of the product.
 */
@DisplayName("Detection power: subtle infidelities in a ported U-type")
class PortedInfidelityDetectionPowerTest {

    /** The historical side of every sweep. */
    private static HistoricalOracle reference;

    /**
     * A second oracle, used as the <em>body</em> of every subject. Unmutated it is a perfect port;
     * {@link MutantPort} perturbs its answers on the targeted operations only.
     */
    private static HistoricalOracle perfectPort;

    private static InputGenerator generator;
    private static Map<String, List<UValue>> corpora;
    private static List<UOp> operations;
    private static Map<String, List<List<UValue>>> tuplesByOp;

    @BeforeAll
    static void openOracles() {
        reference = HistoricalOracle.open();
        perfectPort = HistoricalOracle.open();
        generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        corpora = UnwrittenPortInvariantTest.corpora(generator);
        operations = UnwrittenPortInvariantTest.reachableOperations(reference);
        tuplesByOp = new LinkedHashMap<>();
        for (UOp op : operations) {
            tuplesByOp.put(op.key(),
                    UnwrittenPortInvariantTest.tuples(
                            UnwrittenPortInvariantTest.stageDomains(op, corpora)));
        }
    }

    @AfterAll
    static void closeOracles() {
        if (perfectPort != null) {
            perfectPort.close();
        }
        if (reference != null) {
            reference.close();
        }
    }

    // ------------------------------------------------------------------ the mutations

    /** One planted infidelity, applied on top of a perfect port. */
    @FunctionalInterface
    interface Mutation {
        UValue apply(UOp op, List<UValue> args, Candidate perfect) throws Throwable;
    }

    /** A perfect port with exactly one named defect in it. */
    static final class MutantPort implements Candidate {

        private final String name;
        private final Candidate perfect;
        private final Mutation mutation;
        private final Set<String> unsupported;

        MutantPort(String name, Candidate perfect, Mutation mutation) {
            this(name, perfect, mutation, Set.of());
        }

        MutantPort(String name, Candidate perfect, Mutation mutation, Set<String> unsupported) {
            this.name = name;
            this.perfect = perfect;
            this.mutation = mutation;
            this.unsupported = unsupported;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UValue invoke(UOp op, List<UValue> args) throws Throwable {
            return mutation.apply(op, args, perfect);
        }

        @Override
        public boolean supports(UOp op) {
            return !unsupported.contains(op.key()) && perfect.supports(op);
        }

        @Override
        public String unsupportedReason(UOp op) {
            return unsupported.contains(op.key())
                    ? name + " does not declare " + op.key()
                    : perfect.unsupportedReason(op);
        }

        @Override
        public void close() {
            // The delegate is shared across probes and is closed in @AfterAll, not here.
        }
    }

    /** A named probe: one mutation, the operations it is aimed at, and what it stands for. */
    static final class Probe {
        final String id;
        final String description;
        final Set<String> targets;
        final Mutation mutation;
        final Set<String> unsupported;

        Probe(String id, String description, Set<String> targets, Mutation mutation) {
            this(id, description, targets, mutation, Set.of());
        }

        Probe(String id, String description, Set<String> targets, Mutation mutation,
              Set<String> unsupported) {
            this.id = id;
            this.description = description;
            this.targets = targets;
            this.mutation = mutation;
            this.unsupported = unsupported;
        }
    }

    private static final Set<String> ADDITIVE = Set.of(
            "URealValue.add(value)", "URealValue.minus(value)",
            "UIntegerValue.add(value)", "UIntegerValue.minus(value)");

    private static final Set<String> STRING_INDEXED = Set.of(
            "UStringValue.at(int)", "UStringValue.uAt(int)", "UStringValue.uSubstring(int,int)");

    private static final Map<String, String> ORDER_SWAP = Map.of(
            "URealValue.lt(value)", "le",
            "URealValue.gt(value)", "ge",
            "UIntegerValue.lt(value)", "le",
            "UIntegerValue.gt(value)", "ge",
            "UStringValue.lt(value)", "le",
            "UStringValue.gt(value)", "ge");

    private static final Set<String> ROUNDED = Set.of(
            "URealValue.divideBy(value)", "URealValue.mult(value)", "URealValue.sqrt()",
            "URealValue.sin()", "URealValue.cos()", "URealValue.tan()", "URealValue.inverse()");

    private static final Set<String> UEQUALS = Set.of(
            "URealValue.uEquals(value)", "UIntegerValue.uEquals(value)",
            "UStringValue.uEquals(value)", "UBooleanValue.uEquals(value)");

    private static final Set<String> DIVIDING = Set.of(
            "URealValue.divideBy(value)", "UIntegerValue.divideBy(value)",
            "UIntegerValue.divideByR(value)", "UIntegerValue.mod(value)",
            "URealValue.inverse()", "UIntegerValue.inverse()");

    static List<Probe> probes() {
        return List.of(
                new Probe("P0-perfect",
                        "the control: a perfect port, no mutation at all",
                        Set.of(),
                        (op, args, perfect) -> perfect.invoke(op, args)),

                new Probe("P1-off-by-one-index",
                        "a 0-based port of a 1-based string index: at/uAt/uSubstring shift by one",
                        STRING_INDEXED,
                        (op, args, perfect) -> {
                            if (!STRING_INDEXED.contains(op.key())) {
                                return perfect.invoke(op, args);
                            }
                            List<UValue> shifted = new ArrayList<>(args);
                            shifted.set(1, UValue.integer(args.get(1).asInt() + 1));
                            return perfect.invoke(op, shifted);
                        }),

                new Probe("P2-linear-uncertainty",
                        "uncertainties combined with + where the historical uses sqrt(ua^2+ub^2)",
                        ADDITIVE,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            return ADDITIVE.contains(op.key())
                                    ? withUncertainty(produced,
                                            uncertaintyOf(args.get(0)) + uncertaintyOf(args.get(1)))
                                    : produced;
                        }),

                new Probe("P3-hypot-uncertainty",
                        "Math.hypot(ua,ub) instead of sqrt(ua*ua+ub*ub) -- algebraically the same "
                                + "rule, a different function",
                        ADDITIVE,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            return ADDITIVE.contains(op.key())
                                    ? withUncertainty(produced, Math.hypot(uncertaintyOf(args.get(0)),
                                            uncertaintyOf(args.get(1))))
                                    : produced;
                        }),

                new Probe("P4-le-for-lt",
                        "an order comparison written <= where the historical writes < (and >= for >)",
                        ORDER_SWAP.keySet(),
                        (op, args, perfect) -> {
                            String swapped = ORDER_SWAP.get(op.key());
                            return swapped == null
                                    ? perfect.invoke(op, args)
                                    : perfect.invoke(UOp.binary(op.receiverType(), swapped), args);
                        }),

                new Probe("P5-round-10dp",
                        "results rounded to ten decimal places -- the classic 'it looked the same "
                                + "when I printed it' port",
                        ROUNDED,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            if (!ROUNDED.contains(op.key()) || produced.kind() != UValue.Kind.UREAL) {
                                return produced;
                            }
                            return UValue.uReal(round10(produced.asDouble()),
                                    round10(produced.uncertainty()));
                        }),

                new Probe("P6-equals-ignores-uncertainty",
                        "uEquals compares the values and returns certainty 1.0, ignoring the "
                                + "uncertainty component entirely",
                        UEQUALS,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            if (!UEQUALS.contains(op.key())
                                    || produced.kind() != UValue.Kind.UBOOLEAN) {
                                return produced;
                            }
                            return UValue.uBoolean(coreEquals(args.get(0), args.get(1)), 1.0);
                        }),

                new Probe("P7-undefined-on-zero-divisor",
                        "division by zero answers UndefinedValue where the historical throws",
                        DIVIDING,
                        (op, args, perfect) -> {
                            if (DIVIDING.contains(op.key())) {
                                UValue divisor = op.params().isEmpty() ? args.get(0) : args.get(1);
                                if (isNumeric(divisor) && divisor.asDouble() == 0.0) {
                                    return UValue.opaque("org.tzi.use.uml.ocl.value.UndefinedValue",
                                            "UndefinedValue{}");
                                }
                            }
                            return perfect.invoke(op, args);
                        }),

                new Probe("P8-hides-behind-harness-error",
                        "P2's defect, plus an adapter that raises HarnessMarshallingException on "
                                + "exactly the rows where it would have shown (defect D-17)",
                        ADDITIVE,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            if (!ADDITIVE.contains(op.key())) {
                                return produced;
                            }
                            UValue mutated = withUncertainty(produced,
                                    uncertaintyOf(args.get(0)) + uncertaintyOf(args.get(1)));
                            if (!mutated.canonical().equals(produced.canonical())) {
                                // Precisely the exception Candidate's Javadoc instructs an adapter
                                // author to throw when it "could not drive the operation".
                                throw new HarnessMarshallingException(name(op)
                                        + " cannot marshal " + args.get(1).canonical());
                            }
                            return mutated;
                        }),

                new Probe("P9-hides-behind-unsupported",
                        "P2's defect, plus supports() answering false for the operations that carry "
                                + "it",
                        ADDITIVE,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            return ADDITIVE.contains(op.key())
                                    ? withUncertainty(produced,
                                            uncertaintyOf(args.get(0)) + uncertaintyOf(args.get(1)))
                                    : produced;
                        },
                        ADDITIVE),

                new Probe("P10-narrow-input-window",
                        "P2's defect, restricted to receivers whose value is exactly "
                                + NARROW_WINDOW + " -- a real arithmetic bug on an input no shipped "
                                + "corpus contains",
                        ADDITIVE,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            if (!ADDITIVE.contains(op.key()) || !isNumeric(args.get(0))
                                    || args.get(0).asDouble() != NARROW_WINDOW) {
                                return produced;
                            }
                            return withUncertainty(produced,
                                    uncertaintyOf(args.get(0)) + uncertaintyOf(args.get(1)));
                        }),

                new Probe("P11-negative-zero-collapse",
                        "a port that normalises -0.0 to 0.0 -- invisible to every printf and to "
                                + "Double.equals, visible only to an exact comparison",
                        SIGN_SENSITIVE,
                        (op, args, perfect) -> {
                            UValue produced = perfect.invoke(op, args);
                            if (!SIGN_SENSITIVE.contains(op.key())
                                    || produced.kind() != UValue.Kind.UREAL) {
                                return produced;
                            }
                            return UValue.uReal(unNegateZero(produced.asDouble()),
                                    unNegateZero(produced.uncertainty()));
                        }));
    }

    /**
     * A receiver value no shipped corpus contains. {@link InputGenerator#uRealBoundaries()} holds
     * 0, ±0, ±1, ±0.5, 2, ±100, MIN_VALUE, MAX_VALUE, NaN and both infinities, and the random draws
     * are rounded to six decimal places in [-100, 100], so hitting this exactly has probability
     * 1 in 2·10^8 per draw and does not happen at the recorded seed. See {@code P10}.
     */
    private static final double NARROW_WINDOW = 42.0;

    private static final Set<String> SIGN_SENSITIVE = Set.of(
            "URealValue.neg()", "URealValue.floor()", "URealValue.round()", "URealValue.mult(value)");

    private static double unNegateZero(double x) {
        return x == 0.0 ? 0.0 : x;
    }

    private static String name(UOp op) {
        return op.key();
    }

    /** Replaces the uncertainty of a UREAL/UINTEGER result, leaving anything else alone. */
    private static UValue withUncertainty(UValue produced, double uncertainty) {
        switch (produced.kind()) {
            case UREAL:    return UValue.uReal(produced.asDouble(), uncertainty);
            case UINTEGER: return UValue.uInteger(produced.asInt(), uncertainty);
            default:       return produced;
        }
    }

    /** The uncertainty an argument carries; a plain REAL/INTEGER is promoted to 0, as valueOf does. */
    private static double uncertaintyOf(UValue value) {
        switch (value.kind()) {
            case UREAL:
            case UINTEGER: return value.uncertainty();
            case REAL:
            case INTEGER:  return 0.0;
            default:       return Double.NaN;
        }
    }

    private static boolean isNumeric(UValue value) {
        switch (value.kind()) {
            case UREAL:
            case UINTEGER:
            case REAL:
            case INTEGER: return true;
            default:      return false;
        }
    }

    /** Equality of the payload alone, with the uncertainty / confidence / probability discarded. */
    private static boolean coreEquals(UValue a, UValue b) {
        if (isNumeric(a) && isNumeric(b)) {
            return a.asDouble() == b.asDouble();
        }
        if ((a.kind() == UValue.Kind.USTRING || a.kind() == UValue.Kind.STRING)
                && (b.kind() == UValue.Kind.USTRING || b.kind() == UValue.Kind.STRING)) {
            return a.asString().equals(b.asString());
        }
        if ((a.kind() == UValue.Kind.UBOOLEAN || a.kind() == UValue.Kind.BOOLEAN)
                && (b.kind() == UValue.Kind.UBOOLEAN || b.kind() == UValue.Kind.BOOLEAN)) {
            return a.asBoolean() == b.asBoolean();
        }
        return false;
    }

    private static double round10(double x) {
        double scaled = x * 1e10;
        if (!Double.isFinite(scaled) || Math.abs(scaled) > 9e15) {
            return x;
        }
        return Math.round(scaled) / 1e10;
    }

    // ------------------------------------------------------------------ measuring one probe

    /** What one probe did to the whole inventory. */
    static final class ProbeResult {
        final Probe probe;
        final Map<String, Long> verdicts = new TreeMap<>();
        /** operation key -> its verdict tally, so a probe can be diffed against the control. */
        final Map<String, Map<DiffVerdict, Integer>> perOperation = new TreeMap<>();
        final Map<String, String> statements = new TreeMap<>();
        final Set<String> stagePasses = new TreeSet<>();
        /**
         * Operations {@link DifferentialSweep.Result#isClean()} answers true for — i.e. what a stage
         * that ignored {@code requireStagePass} and used the older predicate would report as
         * passing. Measured beside {@link #stagePasses} so the two can be compared directly.
         */
        final Set<String> cleanOperations = new TreeSet<>();
        /** First few rows on which the mutant was seen to diverge, for the evidence file. */
        final List<String> divergenceSamples = new ArrayList<>();
        /** Why the stage gate refused, per operation; empty value means it passed. */
        final Map<String, List<String>> refusals = new TreeMap<>();
        /**
         * One entry per distinct (operation, both classes, both messages) a {@code BOTH_THREW} row
         * carried. Against a <em>perfect</em> port this is the number of {@link AcceptedThrowPairs}
         * entries a human would have to author before the gate's second clause could ever be met.
         */
        final Set<String> throwPairKeys = new TreeSet<>();
        long rows;
        long measured;
        long agreed;

        ProbeResult(Probe probe) {
            this.probe = probe;
        }

        Set<String> divergingOperations() {
            Set<String> out = new TreeSet<>();
            perOperation.forEach((key, tally) -> {
                if (tally.getOrDefault(DiffVerdict.DIFFER, 0)
                        + tally.getOrDefault(DiffVerdict.MIXED, 0) > 0) {
                    out.add(key);
                }
            });
            return out;
        }

        long count(DiffVerdict verdict) {
            return verdicts.getOrDefault(verdict.name(), 0L);
        }
    }

    /** How many diverging rows to keep verbatim, per probe. */
    private static final int SAMPLE_LIMIT = 6;

    private static ProbeResult measure(Probe probe) {
        return measure(probe, tuplesByOp);
    }

    private static ProbeResult measure(Probe probe, Map<String, List<List<UValue>>> tuples) {
        ProbeResult out = new ProbeResult(probe);
        try (Candidate mutant = new MutantPort(probe.id, perfectPort, probe.mutation,
                probe.unsupported)) {
            DifferentialSweep sweep = new DifferentialSweep(reference, mutant, generator.seed());
            for (UOp op : operations) {
                DifferentialSweep.Result result = sweep.run(op, tuples.get(op.key()));
                out.rows += result.rowCount();
                out.measured += result.measurementCount();
                out.agreed += result.agreementCount();
                Map<DiffVerdict, Integer> tally = new LinkedHashMap<>();
                for (DiffVerdict v : DiffVerdict.values()) {
                    if (result.count(v) > 0) {
                        tally.put(v, result.count(v));
                        out.verdicts.merge(v.name(), (long) result.count(v), Long::sum);
                    }
                }
                out.perOperation.put(op.key(), tally);
                if (probe.targets.contains(op.key())) {
                    out.statements.put(op.key(),
                            result.stageStatement(AcceptedDegenerateOperations.none()));
                }
                List<String> failures =
                        result.stageGateFailures(1, AcceptedDegenerateOperations.none());
                out.refusals.put(op.key(), failures);
                if (failures.isEmpty()) {
                    out.stagePasses.add(op.key());
                }
                if (result.isClean()) {
                    out.cleanOperations.add(op.key());
                }
                for (DiffRow row : result.rows()) {
                    if ((row.verdict() == DiffVerdict.DIFFER || row.verdict() == DiffVerdict.MIXED)
                            && out.divergenceSamples.size() < SAMPLE_LIMIT) {
                        out.divergenceSamples.add(row.toTsv());
                    }
                    if (row.verdict() == DiffVerdict.BOTH_THREW) {
                        out.throwPairKeys.add(op.key() + " || " + row.note());
                    }
                }
            }
        }
        return out;
    }

    /**
     * Why a <em>perfect</em> port fails the stage gate on the operations it fails it on. This is the
     * number S4 has to plan around: the gate is not satisfiable by fidelity alone.
     */
    private static Map<String, Integer> refusalCensus(ProbeResult r) {
        Map<String, Integer> census = new TreeMap<>();
        r.refusals.forEach((key, failures) -> {
            String bucket;
            if (failures.isEmpty()) {
                bucket = "0 PASS";
            } else if (failures.size() > 1) {
                bucket = "3 refused on more than one clause";
            } else if (failures.get(0).startsWith("measured ")) {
                bucket = "1 refused: measurement floor";
            } else if (failures.get(0).contains("did not agree")) {
                bucket = "2 refused: rows disagreed";
            } else {
                bucket = "4 refused: not discriminating (D-15)";
            }
            census.merge(bucket, 1, Integer::sum);
        });
        return census;
    }

    // ------------------------------------------------------------------ the measurement

    /**
     * Drives every probe over the whole 285-operation inventory and prints, for each, what the
     * harness saw. The assertions at the end are written against numbers that were <em>measured
     * first</em>; the printed block is the evidence and is meant to be read, not skimmed.
     */
    @Test
    @DisplayName("a subtle infidelity in a port is detected, and the ones that are not are named")
    void subtleInfidelitiesAreDetectedOrNamed() {
        ProbeResult control = measure(probes().get(0));

        System.out.println("=== detection power: control (a perfect port) =====================");
        System.out.println("seed                 " + generator.seed());
        System.out.println("operations           " + operations.size() + "  (stage-shaped domains)");
        System.out.println("rows                 " + control.rows);
        System.out.println("measured rows        " + control.measured);
        System.out.println("agreement rows       " + control.agreed);
        System.out.println("verdict tally        " + control.verdicts);
        System.out.println("diverging operations " + control.divergingOperations().size()
                + "   <- MUST be 0, or nothing below is attributable to a planted defect");
        System.out.println("stage passes         " + control.stagePasses.size() + " of "
                + operations.size() + "  (isStagePass(1, none()))");
        System.out.println("why a PERFECT port is refused elsewhere:");
        refusalCensus(control).forEach((bucket, n) ->
                System.out.println("    " + bucket + "   " + n));
        System.out.println("distinct throw-pairs a PERFECT port produces  " + control.throwPairKeys.size()
                + "   <- AcceptedThrowPairs entries a human would have to author, one per "
                + "(operation, both classes, both messages), before clause 2 could ever be met "
                + "on the operations that throw");
        control.throwPairKeys.stream().limit(4)
                .forEach(k -> System.out.println("    e.g. " + k));
        System.out.println("===================================================================");

        assertEquals(0L, control.count(DiffVerdict.DIFFER),
                "a second, independent HistoricalOracle must reproduce the first exactly, or every "
                        + "number below is measuring my wrapper instead of the planted defect");
        assertEquals(0L, control.count(DiffVerdict.MIXED),
                "the control must not diverge on the throw/return boundary either");
        assertEquals(Set.of(), control.divergingOperations());

        List<ProbeResult> results = new ArrayList<>();
        for (Probe probe : probes().subList(1, probes().size())) {
            ProbeResult probeResult = measure(probe);
            results.add(probeResult);
            report(probeResult, control);
        }

        // ---- the assertions, each one a measured fact ----------------------------------------

        Map<String, ProbeResult> byId = new LinkedHashMap<>();
        results.forEach(r -> byId.put(r.probe.id, r));

        // Every probe whose defect the harness can see must (a) diverge, and (b) refuse a stage
        // pass on the operations that carry the defect.
        Set<String> unseen = new TreeSet<>();
        for (String detected : List.of("P1-off-by-one-index", "P2-linear-uncertainty",
                "P3-hypot-uncertainty", "P4-le-for-lt", "P5-round-10dp",
                "P6-equals-ignores-uncertainty", "P7-undefined-on-zero-divisor",
                "P11-negative-zero-collapse")) {
            ProbeResult r = byId.get(detected);
            assertFalse(r.divergingOperations().isEmpty(),
                    detected + " planted a defect the harness did not see on any operation");
            for (String target : r.probe.targets) {
                if (r.divergingOperations().contains(target)) {
                    assertFalse(r.stagePasses.contains(target),
                            detected + " reached a STAGE PASS on " + target + ", an operation it is "
                                    + "deliberately wrong on: " + r.statements.get(target));
                } else {
                    unseen.add(detected + " / " + target
                            + (r.stagePasses.contains(target) ? "  [STAGE PASS]" : ""));
                }
            }
        }

        // ---- THE CATALOGUE OF WHAT THIS HARNESS CANNOT SEE -------------------------------------
        //
        // Every (defect, operation) pair a probe planted and the sweep did not report. Asserted as
        // an exact set, so the list cannot grow -- or shrink -- without a reader being told.
        System.out.println("=== planted defects the harness did NOT see =======================");
        unseen.forEach(s -> System.out.println("  ??? " + s));
        System.out.println("===================================================================");
        assertEquals(Set.of(
                        // The defect is real -- URealValue.round() would answer 0.0 where the
                        // historical answers -0.0 -- but no receiver in the shipped uReal corpus
                        // makes round() produce a negative zero, so there is no row on which the
                        // two implementations can be told apart. Detection of the very same defect
                        // on floor(), neg() and mult(value) is asserted above; this is the same
                        // finding as P10 in miniature, and its cause is the corpus, not the sweep.
                        "P11-negative-zero-collapse / URealValue.round()  [STAGE PASS]"),
                unseen,
                "the set of planted infidelities this instrument cannot see is a headline number "
                        + "of the round-5 review and must not change silently");

        // The two concealment probes: the defect is the same as P2's, and the question is whether an
        // adapter can bury it. Neither may reach a stage pass on the operations it is wrong about.
        for (String concealing : List.of("P8-hides-behind-harness-error",
                "P9-hides-behind-unsupported")) {
            ProbeResult r = byId.get(concealing);
            for (String target : ADDITIVE) {
                assertFalse(r.stagePasses.contains(target),
                        concealing + " buried a real infidelity and still reached a STAGE PASS on "
                                + target + ": " + r.statements.get(target));
            }
        }

        // ---- what the OLD predicate would have caught, on the same defects --------------------
        //
        // isClean() is documented as not a pass predicate, and nothing mechanically stops a stage
        // using it (the porter says so; the round-5 static review files it as R5-4). That is a real
        // hole for DEGENERATE operations. It is worth knowing how big a hole it is for genuine
        // infidelities, which is a different question and is answered here: on every probe, the set
        // of operations isClean() stops calling clean is a subset of the set the sweep diverged on,
        // and it is empty exactly where the operation was not clean to begin with.
        System.out.println("=== isClean() against requireStagePass, on the same defects =======");
        System.out.println("  probe                        detected  isClean lost  gate lost  "
                + "divergence with NO change in the pass bit");
        Set<String> silent = new TreeSet<>();
        for (ProbeResult r : results) {
            Set<String> lostClean = new TreeSet<>(control.cleanOperations);
            lostClean.removeAll(r.cleanOperations);
            Set<String> lostGate = new TreeSet<>(control.stagePasses);
            lostGate.removeAll(r.stagePasses);
            Set<String> alreadyFailing = new TreeSet<>(r.divergingOperations());
            alreadyFailing.removeAll(control.stagePasses);
            alreadyFailing.forEach(op -> silent.add(r.probe.id + " / " + op));
            System.out.printf("  %-28s %6d %13d %10d %10d%n", r.probe.id,
                    r.divergingOperations().size(), lostClean.size(), lostGate.size(),
                    alreadyFailing.size());
            assertTrue(r.probe.targets.containsAll(lostClean),
                    r.probe.id + ": isClean() stopped being true for an operation the probe does "
                            + "not target: " + lostClean);
        }
        // The rows are there and the counts change; the BOOLEAN does not, because a perfect port
        // already fails the gate on these operations (see the control's refusal census). A stage
        // that reads only pass/fail is blind to every entry in this list.
        System.out.println("  operations where a real infidelity leaves the pass bit unchanged, "
                + "because a PERFECT port already fails the gate there:");
        silent.forEach(s -> System.out.println("    !!! " + s));
        System.out.println("===================================================================");

        // Collateral: a probe aimed at one operation must not silently change any other. If this
        // ever fails, the mutation is broader than its description and every count above is
        // misattributed.
        for (ProbeResult r : results) {
            for (String diverged : r.divergingOperations()) {
                assertTrue(r.probe.targets.contains(diverged),
                        r.probe.id + " diverged on " + diverged + ", which it does not target");
            }
        }

        // ---- THE BLIND SPOT, asserted as such -------------------------------------------------
        //
        // P10 is a genuine arithmetic defect -- the same wrong uncertainty rule as P2 -- confined to
        // a receiver value no shipped corpus contains. The harness sees nothing, and the operations
        // that carry the defect are reported as a full stage pass. This is not a bug in the sweep:
        // a differential oracle can only compare what it was asked to compare. It is the boundary
        // of what an S4 fidelity claim from this instrument means, and it is pinned here so that
        // the boundary cannot move without someone noticing.
        ProbeResult blind = byId.get("P10-narrow-input-window");
        assertEquals(Set.of(), blind.divergingOperations(),
                "if this now detects something, the corpora have grown to reach " + NARROW_WINDOW
                        + " and the report's blind-spot section is stale -- a good problem, but the "
                        + "documentation has to catch up");
        assertEquals(control.stagePasses, blind.stagePasses,
                "a port with a real arithmetic defect on an unreached input is stage-pass-identical "
                        + "to a perfect one; that is the claim this test exists to make explicit");
        assertTrue(blind.stagePasses.contains("URealValue.add(value)"),
                "and the operation carrying the defect is one of them");
    }

    private static void report(ProbeResult r, ProbeResult control) {
        System.out.println("=== detection power: " + r.probe.id + " ============================");
        System.out.println("defect               " + r.probe.description);
        System.out.println("aimed at             " + new TreeSet<>(r.probe.targets));
        System.out.println("rows                 " + r.rows + "   (control " + control.rows + ")");
        System.out.println("measured rows        " + r.measured + "   (control " + control.measured + ")");
        System.out.println("agreement rows       " + r.agreed + "   (control " + control.agreed + ")");
        System.out.println("verdict tally        " + r.verdicts);
        Set<String> diverging = r.divergingOperations();
        System.out.println("DETECTED on          " + diverging.size() + " operation(s): " + diverging);
        System.out.println("stage passes         " + r.stagePasses.size() + "   (control "
                + control.stagePasses.size() + ")");
        Set<String> lostClean = new TreeSet<>(control.cleanOperations);
        lostClean.removeAll(r.cleanOperations);
        System.out.println("isClean() operations " + r.cleanOperations.size() + "   (control "
                + control.cleanOperations.size() + ")   the older predicate loses " + lostClean.size()
                + ": " + lostClean);
        for (String target : new TreeSet<>(r.probe.targets)) {
            Map<DiffVerdict, Integer> mine = r.perOperation.get(target);
            Map<DiffVerdict, Integer> theirs = control.perOperation.get(target);
            System.out.println("  target " + target);
            System.out.println("    control  " + theirs);
            System.out.println("    mutant   " + mine);
            System.out.println("    statement " + r.statements.get(target));
            System.out.println("    stage pass? " + r.stagePasses.contains(target)
                    + "   (control " + control.stagePasses.contains(target) + ")");
            r.refusals.getOrDefault(target, List.of())
                    .forEach(f -> System.out.println("    refused: " + f));
        }
        if (!r.divergenceSamples.isEmpty()) {
            System.out.println("  first " + r.divergenceSamples.size() + " diverging row(s):");
            System.out.println("  " + DiffRow.TSV_HEADER);
            r.divergenceSamples.forEach(s -> System.out.println("  " + s));
        }
        System.out.println("===================================================================");
    }

    // ------------------------------------------------------------------ the metric, by hand

    /**
     * <strong>{@code distinctReferenceValues()} recomputed by a different route, and printed so a
     * human can count it.</strong>
     *
     * <p>{@link DifferentialSweep.Result#referenceValues()} reads the {@code historical} column of
     * the rows the sweep classified as measurements. This test never looks at a row: it drives the
     * oracle directly over the same domain, collects the canonical forms itself, and requires the
     * two sets to be equal — and it prints the whole set, because a number a reviewer cannot check
     * by eye is a number a reviewer has to take on trust.
     *
     * <p>{@code URealValue.neg()} is chosen because its domain is one corpus of 24 receivers, small
     * enough to read, and its codomain is neither trivially constant nor unbounded.
     */
    @Test
    @DisplayName("distinct reference values, recomputed without looking at a single row")
    void theMetricRecomputedByHand() {
        UOp neg = UOp.unary("URealValue", "neg");
        List<UValue> receivers = UnwrittenPortInvariantTest.receiverCorpus("URealValue", corpora);
        List<List<UValue>> tuples = tuplesByOp.get(neg.key());

        java.util.SortedSet<String> byHand = new TreeSet<>();
        List<String> trace = new ArrayList<>();
        for (List<UValue> tuple : tuples) {
            try {
                UValue produced = reference.invoke(neg, tuple);
                if (produced.carriesAnObservation()) {
                    byHand.add(produced.canonical());
                    trace.add(tuple.get(0).canonical() + "  ->  " + produced.canonical());
                }
            } catch (Throwable t) {
                trace.add(tuple.get(0).canonical() + "  ->  threw " + t.getClass().getSimpleName());
            }
        }

        DifferentialSweep.Result result;
        try (Candidate mutant = new MutantPort("identity", perfectPort,
                (op, args, perfect) -> perfect.invoke(op, args))) {
            result = new DifferentialSweep(reference, mutant, generator.seed()).run(neg, tuples);
        }

        System.out.println("=== the metric, recomputed by hand ================================");
        System.out.println("operation            " + neg.key());
        System.out.println("receivers            " + receivers.size() + "  (the URealValue corpus)");
        System.out.println("rows                 " + result.rowCount());
        trace.forEach(line -> System.out.println("    " + line));
        System.out.println("by hand              " + byHand.size() + "  " + byHand);
        System.out.println("Result.referenceValues() " + result.distinctReferenceValues()
                + "  " + result.referenceValues());
        System.out.println("summary              " + result.summary());
        System.out.println("===================================================================");

        assertEquals(byHand, new TreeSet<>(result.referenceValues()),
                "the published metric must be the reference's own answers over the measured rows");
        assertEquals(byHand.size(), result.distinctReferenceValues());
        assertEquals(receivers.size(), result.rowCount(),
                "and the domain must be the receiver corpus, one row each");
    }

    // ------------------------------------------------------------------ corpus sensitivity

    /**
     * <strong>How much of the detection power is owed to which corpus entries.</strong>
     *
     * <p>Detection is a joint fact about the two implementations <em>and</em> the inputs, and the
     * only honest way to say how much of it the corpus is carrying is to take some of the corpus
     * away and re-measure. Here the non-finite boundary values — NaN and the two infinities, in the
     * value position and in the uncertainty position — are removed and the same probes re-run.
     *
     * <p>{@code P3} is the reason this test exists. {@code Math.hypot(ua,ub)} and
     * {@code sqrt(ua*ua+ub*ub)} are the same formula; they differ on almost no ordinary input, and
     * whether this harness can tell a port that uses one from a port that uses the other depends
     * entirely on a handful of values in {@link InputGenerator#uRealBoundaries()}.
     */
    @Test
    @DisplayName("detection is a property of the corpus too: what removing the non-finite "
            + "boundaries costs")
    void detectionDependsOnTheCorpus() {
        Map<String, List<UValue>> finite = withoutNonFinite(corpora);
        Map<String, List<List<UValue>>> finiteTuples = new LinkedHashMap<>();
        for (UOp op : operations) {
            finiteTuples.put(op.key(),
                    UnwrittenPortInvariantTest.tuples(
                            UnwrittenPortInvariantTest.stageDomains(op, finite)));
        }

        System.out.println("=== corpus sensitivity ============================================");
        System.out.println("full corpora         " + describe(corpora));
        System.out.println("finite-only corpora  " + describe(finite));

        Map<String, long[]> table = new LinkedHashMap<>();
        for (Probe probe : probes()) {
            ProbeResult full = measure(probe, tuplesByOp);
            ProbeResult reduced = measure(probe, finiteTuples);
            table.put(probe.id, new long[] {
                    full.count(DiffVerdict.DIFFER) + full.count(DiffVerdict.MIXED),
                    reduced.count(DiffVerdict.DIFFER) + reduced.count(DiffVerdict.MIXED),
                    full.divergingOperations().size(),
                    reduced.divergingOperations().size() });
        }
        System.out.println("probe                          detecting rows   ops detected");
        System.out.println("                               full  finite     full  finite");
        table.forEach((id, n) -> System.out.printf("  %-28s %5d %6d      %4d %6d%n",
                id, n[0], n[1], n[2], n[3]));
        System.out.println("===================================================================");

        // Measured, and NOT what I predicted. I expected hypot-vs-sqrt to be visible only through
        // the NaN / Infinity boundaries and to vanish without them. It does not: 20 of its 24
        // detecting rows survive, because the two functions differ by one unit in the last place on
        // ordinary finite inputs -- e.g. 0.3144000993956586 against 0.31440009939565855 -- and
        // UValue.canonical() compares Double.toString output exactly. The exactness the harness
        // documents is doing the work here, not the boundary corpus.
        long[] hypot = table.get("P3-hypot-uncertainty");
        assertTrue(hypot[0] > 0, "P3 must be detectable over the shipped corpora");
        assertTrue(hypot[1] > 0,
                "P3 must survive the loss of the non-finite boundaries: its detection rests on "
                        + "exact double comparison, not on NaN. Got " + hypot[1]);
        assertTrue(hypot[0] > hypot[1],
                "and the non-finite boundaries must still be contributing something: "
                        + hypot[0] + " vs " + hypot[1]);

        // No probe may gain detection from a SMALLER corpus; a detection count that goes up when
        // inputs are removed means the count is measuring something other than the defect.
        table.forEach((id, n) -> assertTrue(n[1] <= n[0],
                id + " detected MORE on the reduced corpus (" + n[1] + " > " + n[0] + ")"));
    }

    private static Map<String, List<UValue>> withoutNonFinite(Map<String, List<UValue>> in) {
        Map<String, List<UValue>> out = new LinkedHashMap<>();
        in.forEach((name, values) -> {
            List<UValue> kept = new ArrayList<>();
            for (UValue value : values) {
                if (isFinite(value)) {
                    kept.add(value);
                }
            }
            out.put(name, kept);
        });
        return out;
    }

    private static boolean isFinite(UValue value) {
        switch (value.kind()) {
            case UREAL:    return Double.isFinite(value.asDouble())
                                  && Double.isFinite(value.uncertainty());
            case UINTEGER: return Double.isFinite(value.uncertainty());
            case UBOOLEAN: return Double.isFinite(value.probability());
            case USTRING:  return Double.isFinite(value.confidence());
            case REAL:     return Double.isFinite(value.asDouble());
            default:       return true;
        }
    }

    private static String describe(Map<String, List<UValue>> corpora) {
        StringBuilder sb = new StringBuilder();
        corpora.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ", ").append(k).append('=')
                .append(v.size()));
        return sb.toString();
    }

    // ------------------------------------------------------------------ the reachability blind spot

    /**
     * <strong>What the inventory cannot name.</strong> {@link UnwrittenPortInvariantTest#reachableOperations}
     * enumerates public instance methods whose every parameter is expressible as a
     * {@link UOp.ParamKind} — {@code Value}, {@code int}, {@code double}, {@code float}. Every other
     * public instance method on the eight marshallable receivers is <em>not in the 285 at all</em>:
     * no row, no verdict, no {@code UNSUPPORTED} marker, nothing in any report.
     *
     * <p>This is not a defect in the sweep; it is the boundary of the inventory, and it is measured
     * here so that "285 operations" is never read as "the ported surface". A port whose
     * {@code equals(Object)} ignores the uncertainty component is invisible to this instrument, and
     * so is a port with a broken {@code indexOf(StringValue)}.
     */
    @Test
    @DisplayName("the inventory's own boundary: which public operations are not nameable at all")
    void operationsTheInventoryCannotName() {
        Class<?> valueClass = reference.historicalClass("Value");
        Map<String, List<String>> unnameable = new TreeMap<>();
        Set<String> reachable = new LinkedHashSet<>();
        operations.forEach(op -> reachable.add(op.key()));
        int nameable = 0;
        int total = 0;

        for (String receiver : HistoricalOracle.marshallableReceiverTypes()) {
            for (Method m : reference.historicalClass(receiver).getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() == Object.class) {
                    continue;
                }
                total++;
                if (expressible(valueClass, m)) {
                    nameable++;
                } else {
                    unnameable.computeIfAbsent(receiver, k -> new ArrayList<>())
                            .add(m.getName() + Arrays.toString(m.getParameterTypes()));
                }
            }
        }

        System.out.println("=== the inventory boundary ========================================");
        System.out.println("public instance methods on the 8 marshallable receivers  " + total);
        System.out.println("expressible as a UOp (before de-duplication)             " + nameable);
        System.out.println("NOT nameable, therefore absent from every report         "
                + (total - nameable));
        unnameable.forEach((receiver, methods) -> {
            System.out.println("  " + receiver);
            new TreeSet<>(methods).forEach(m -> System.out.println("      " + m));
        });
        System.out.println("distinct UOp keys in the inventory                       "
                + reachable.size());
        System.out.println("===================================================================");

        assertTrue(total - nameable > 0,
                "if every public method were nameable this test would have nothing to warn about, "
                        + "and the warning in its Javadoc would be stale");
        assertFalse(reachable.contains("URealValue.equals(value)"),
                "equals takes Object, not Value: it cannot be in the inventory. If this ever "
                        + "becomes true the blind spot has closed and the report should say so.");
        assertFalse(reachable.contains("UStringValue.indexOf(value)"),
                "indexOf takes StringValue, not Value: it cannot be in the inventory either.");
    }

    private static boolean expressible(Class<?> valueClass, Method m) {
        for (Class<?> type : m.getParameterTypes()) {
            if (type != valueClass && type != int.class && type != double.class
                    && type != float.class) {
                return false;
            }
        }
        return true;
    }
}
