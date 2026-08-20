package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fidelity claim, over the WHOLE enumerated operation census rather than a hand-picked list.
 *
 * <p>This replaces the coverage half of {@code FirstRealDifferentialTest}, which swept 46 operations
 * chosen by hand — and, worse, chosen by the same person who wrote the port, so it measured the
 * perimeter of the work rather than the surface of the language. An audit found that if all five
 * ported value classes were deleted, that test would print 46 {@code SKIPPED} lines and stay green.
 *
 * <p>Four properties are asserted here, and the first two exist because of that finding:
 * <ul>
 *   <li><b>A coverage floor.</b> The sweep must reach a minimum number of operations and rows. An
 *       operation the port stops supporting drops out of the numerator, so the floor is what turns
 *       a silent regression into a failure.</li>
 *   <li><b>A support floor.</b> The ported side must actually implement most of what the reference
 *       exposes. {@code UNSUPPORTED} is not a pass.</li>
 *   <li><b>Zero <em>unintended</em> divergence</b> over everything that was driven.</li>
 *   <li><b>Every pre-registered departure fired.</b> See below — this is the clause that catches a
 *       B7 fix that was designed, documented, and never written.</li>
 * </ul>
 *
 * <h2>Why this sweep is no longer expected to be all-green</h2>
 * The user's decision B7 (2026-08-17) is that the port <strong>fixes</strong> the historical defects
 * rather than reproducing them bug-for-bug. On the operations those fixes touch, agreeing with the
 * historical jars would mean the fix did not land. So divergence there is the intended outcome, and
 * the instrument has to be able to tell an intended one from a mistake — which it cannot do by
 * measurement, only by pre-registration. See {@link IntendedDepartures}.
 *
 * <p>The operation inventory comes from {@link UnwrittenPortInvariantTest#reachableOperations} —
 * reflection over the historical jars — and the input domains from the same helpers that test uses,
 * so the two sweeps cover identical ground and their numbers are comparable.
 */
@DisplayName("Ported fidelity over the full operation census")
public class PortedFidelitySweepTest {

    /** Enumerated from the jars; a floor, not a target. Raise it when the census grows. */
    private static final int MIN_OPERATIONS = 100;

    /** Rows on which BOTH sides produced a value. Agreement can only come from these. */
    private static final int MIN_MEASURED_ROWS = 5_000;

    /** Of the operations the reference exposes, the fraction the port must actually implement. */
    private static final double MIN_SUPPORTED_FRACTION = 0.75;

    /**
     * <strong>Every B7 correction that this instrument can see, pre-registered.</strong>
     *
     * <p>Read {@link IntendedDepartures} first; this method is the payload, not the mechanism.
     *
     * <h4>Which corrections are missing from this list, and why that is not an omission</h4>
     * B7 has 33 behaviour-changing rows. Only the ones below produce a row here, and the reason is a
     * property of the census rather than of the fixes: {@code UnwrittenPortInvariantTest.kindsOf}
     * admits a method only when every parameter is a {@code Value}, {@code int}, {@code double} or
     * {@code float}. {@code equals(Object)} takes an {@code Object}, so <strong>no {@code equals}
     * override is in the census at all</strong> — M-11, M-8, M-10 and F-4 are invisible here, and
     * their evidence is the purpose-built tests, not this sweep.
     *
     * <p>That corrects {@code docs/port2/b7-fix-plan.md} section 4.1, which lists all four as
     * "visible to the sweep (DIFFER expected)". They are not, and a stage quoting this file as
     * evidence for them would be quoting a population that does not exist.
     */
    private static IntendedDepartures b7Departures() {
        String tiedOrder =
                "B7 (user decision 2026-08-17). The fork's UIntegerValue.compareTo delegated to the "
                + "other operand WITHOUT negating the sign, into a URealValue.compareTo that has no "
                + "UIntegerValue arm at all -- its fourth arm is an unreachable duplicate of its "
                + "first, whose body nonetheless builds a UIntegerValue. So the composite answered a "
                + "constant 0: every UInteger compared EQUAL to every UReal, in both directions. The "
                + "port adds the missing arm (lifting through UIntegerValue.toUReal()) and negates "
                + "the delegation, so the two now order. The reference is wrong; the subject is "
                + "right. b7-fix-plan.md section 2 M-9 and section 7.1 bundle A.";

        return IntendedDepartures.builder()

                // ---- bundle A: M-9 and the missing arm it needs, seen from both sides ----
                //
                // Two distinct answers out of 343 rows, and that is the whole shape of the
                // correction: the reference could only ever say "equal", and the subject says
                // "before" or "after". SUBJECT_ORDERS_WHERE_REFERENCE_TIED refuses any row where the
                // reference did NOT say 0, and any row where the subject still says 0 -- so a
                // half-landed fix (M-9's negation without the new arm, which is 0 negated) cannot be
                // adjudicated by this entry.
                .declarePopulation("UIntegerValue.compareTo(value)", "M-9", 343,
                        List.of("INTEGER(0)@Integer\tINTEGER(-1)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(1)@Integer"),
                        IntendedDepartures.Direction.SUBJECT_ORDERS_WHERE_REFERENCE_TIED,
                        tiedOrder)

                .declarePopulation("URealValue.compareTo(value)", "M-9", 343,
                        List.of("INTEGER(0)@Integer\tINTEGER(-1)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(1)@Integer"),
                        IntendedDepartures.Direction.SUBJECT_ORDERS_WHERE_REFERENCE_TIED,
                        tiedOrder + " This is the same correction observed from the URealValue side, "
                        + "where the missing arm actually lives.")

                // ---- F-10: every certain UInteger hashed to zero ----
                //
                // Ten of the twelve pairs have reference INTEGER(0), which is F-10 itself:
                // hash *= 7 * Double.hashCode(uncertainty()) and Double.hashCode(0.0) == 0, so the
                // multiplication annihilated the hash for EVERY UInteger(n, 0). The remaining two
                // are the uncertain case, where the fork's multiplicative combination and the port's
                // additive one simply differ. A hash has no order, so the direction carries no shape
                // and the teeth are the twelve exact pairs and the row count.
                .declarePopulation("UIntegerValue.hashCode()", "F-10", 112,
                        List.of("INTEGER(0)@Integer\tINTEGER(-1042284544)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(-1074790400)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(-1105199105)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(-2097152)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(-8388608)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(1065877504)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(1072693248)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(2139095040)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(2145386496)@Integer",
                                "INTEGER(0)@Integer\tINTEGER(9699328)@Integer",
                                "INTEGER(1546682368)@Integer\tINTEGER(-430141328)@Integer",
                                "INTEGER(254322688)@Integer\tINTEGER(-1918063385)@Integer"),
                        IntendedDepartures.Direction.REFERENCE_WAS_WRONG,
                        "B7 (user decision 2026-08-17). Double.hashCode(0.0) is 0, and the fork's "
                        + "UIntegerValue.hashCode MULTIPLIES by it, so every UInteger(n, 0) -- the "
                        + "commonest value in the type -- hashed to 0 whatever n was. Ten of the "
                        + "twelve pairs below are that case, and their reference column says so. The "
                        + "port uses the additive, zero-guarded body URealValue.hashCode already had. "
                        + "The reference is wrong; the subject is right. b7-fix-plan.md section 1 C2.")

                // ---- F-3: hashCode hashed unrounded values that equals() compares rounded ----
                //
                // INTEGER(-2147483648) -> INTEGER(0) is Double.hashCode(-0.0) becoming
                // Double.hashCode(0.0), and INTEGER(1) -> INTEGER(0) is a subnormal rounding to zero.
                // Both are cases where equals() ALREADY said "equal" and the hash said "different
                // bucket", which is the contract violation in the direction that loses data.
                .declarePopulation("URealValue.hashCode()", "F-3", 72,
                        List.of("INTEGER(-1048576)@Integer\tINTEGER(404537700)@Integer",
                                "INTEGER(-2146435072)@Integer\tINTEGER(-1742945948)@Integer",
                                "INTEGER(-2147483648)@Integer\tINTEGER(0)@Integer",
                                "INTEGER(1)@Integer\tINTEGER(0)@Integer",
                                "INTEGER(1048576)@Integer\tINTEGER(404537700)@Integer",
                                "INTEGER(1065353216)@Integer\tINTEGER(1470939492)@Integer",
                                "INTEGER(1065877504)@Integer\tINTEGER(1072693248)@Integer",
                                "INTEGER(2146435072)@Integer\tINTEGER(-1742945948)@Integer",
                                "INTEGER(2146959360)@Integer\tINTEGER(0)@Integer"),
                        IntendedDepartures.Direction.REFERENCE_WAS_WRONG,
                        "B7 (user decision 2026-08-17). The fork hashed the UNROUNDED value and "
                        + "uncertainty while equals(), three lines below, compares them ROUNDED to "
                        + "ten decimals -- so two values equals() calls equal could land in different "
                        + "buckets, and a HashSet never consults equals across buckets. The port "
                        + "rounds inside hashCode with the same MathUtil.round(x, 10). Note "
                        + "INTEGER(-2147483648) -> INTEGER(0): that is Double.hashCode(-0.0) becoming "
                        + "Double.hashCode(0.0), a pair the fork's own equals already called equal. "
                        + "b7-fix-plan.md section 2 F-3.")

                // ---- M-12: the one correction with a wide codomain ----
                .declareWideCodomain("UStringValue.compareTo(value)", "M-12", 432, 210,
                        List.of("INTEGER(-16)@Integer\tINTEGER(0)@Integer",
                                "INTEGER(-17)@Integer\tINTEGER(-1)@Integer",
                                "INTEGER(-18)@Integer\tINTEGER(-2)@Integer",
                                "INTEGER(-19)@Integer\tINTEGER(-3)@Integer",
                                "INTEGER(12)@Integer\tINTEGER(0)@Integer",
                                "INTEGER(12)@Integer\tINTEGER(-1)@Integer"),
                        IntendedDepartures.Direction.REFERENCE_WAS_WRONG,
                        "B7 (user decision 2026-08-17). The fork compared the receiver's BARE string "
                        + "against the argument's WRAPPER RENDERING: "
                        + "wrapper.getString().compareTo(valueOf(o).toString()), where "
                        + "valueOf(aStringValue).toString() is the literal text \"UString('x', 1.0)\". "
                        + "So UString('x',1).compareTo('x') compared \"x\" against "
                        + "\"UString('x', 1.0)\" and every plain String sorted after every UString "
                        + "whatever the strings were. The port compares bare against bare. "
                        + "String.compareTo returns a character difference, which is why this "
                        + "correction has 210 distinct answers and is declared by counts and sample "
                        + "rather than by enumeration -- see declareWideCodomain, and note the "
                        + "sample: INTEGER(-16) -> INTEGER(0) is the empty UString against the empty "
                        + "String, where 16 is exactly the length of the \"UString('', \" prefix the "
                        + "fork was comparing against. b7-fix-plan.md section 2 M-12.")

                .build();
    }

    @Test
    @DisplayName("the ported U-types agree with the historical jars except where B7 says otherwise")
    void portedSweepOverFullCensus() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        Map<String, List<UValue>> corpora = UnwrittenPortInvariantTest.corpora(generator);
        IntendedDepartures preRegistered = b7Departures();

        Map<DiffVerdict, Integer> tally = new EnumMap<>(DiffVerdict.class);
        Map<String, Integer> divergingOps = new LinkedHashMap<>();
        /** operation -> "reference TAB subject" -> how many rows. The authoring aid, and the review. */
        Map<String, Map<String, Integer>> departingPairs = new TreeMap<>();
        List<String> unsupported = new ArrayList<>();
        List<String> unusedDeclarations = new ArrayList<>();
        int operations = 0;
        int supported = 0;
        long measuredRows = 0;
        long totalRows = 0;
        long intendedRows = 0;

        try (HistoricalOracle oracle = HistoricalOracle.open();
             PortedCandidate ported = PortedCandidate.open()) {

            List<UOp> census = UnwrittenPortInvariantTest.reachableOperations(oracle);
            operations = census.size();
            DifferentialSweep sweep = new DifferentialSweep(oracle, ported, generator.seed(),
                    AcceptedThrowPairs.none(), preRegistered);

            for (UOp op : census) {
                if (!ported.supports(op)) {
                    unsupported.add(op.key());
                    continue;
                }
                supported++;
                // ONE result per operation, not one per corpus.
                //
                // The obvious loop -- sweep each corpus separately -- produces eight results per
                // operation, each holding a slice of the rows. A population declaration keys on an
                // exact row count over the operation, so against eight slices it matches none of
                // them, and the whole pre-registration silently fails to fire while the fixes are
                // demonstrably in place. Measured: with per-corpus results, all four
                // declarePopulation entries went unused and 870 rows stayed DIFFER.
                List<List<UValue>> tuples = new ArrayList<>();
                for (List<UValue> corpus : corpora.values()) {
                    tuples.addAll(DifferentialSweep.tuplesOf(
                            UnwrittenPortInvariantTest.domains(op, corpora, corpus)));
                }
                DifferentialSweep.Result r = sweep.run(op, tuples);
                for (DiffRow row : r.rows()) {
                    totalRows++;
                    tally.merge(row.verdict(), 1, Integer::sum);
                    if (row.verdict().isMeasurement() || row.verdict() == DiffVerdict.MIXED) {
                        measuredRows++;
                    }
                    if (row.verdict() == DiffVerdict.INTENDED_DEPARTURE) {
                        intendedRows++;
                    }
                    if (row.verdict() == DiffVerdict.DIFFER || row.verdict() == DiffVerdict.MIXED) {
                        divergingOps.merge(op.key(), 1, Integer::sum);
                        departingPairs
                                .computeIfAbsent(op.key(), k -> new TreeMap<>())
                                .merge(row.historical() + "\t" + row.ported(), 1, Integer::sum);
                    }
                }
                // Scoped to this operation by construction: Result.unusedDeclarations() only
                // considers declarations written against op.key().
                for (IntendedDepartures.Declaration d : r.unusedDeclarations()) {
                    unusedDeclarations.add(d.id());
                }
            }
        }

        System.out.println("=========== PORTED FIDELITY, FULL CENSUS ===========");
        System.out.println("operations enumerated  " + operations);
        System.out.println("supported by the port  " + supported
                + "  (" + Math.round(100.0 * supported / Math.max(1, operations)) + "%)");
        System.out.println("rows                   " + totalRows + " total, " + measuredRows + " measured");
        System.out.println("verdicts               " + tally);
        System.out.println("pre-registered (B7)    " + preRegistered.size() + " declaration(s), "
                + intendedRows + " row(s) adjudicated");
        System.out.println("diverging operations   " + divergingOps.size() + " (unintended)");
        // Printed in the exact shape a declarePopulation() call needs, because the alternative is a
        // human transcribing doubles out of a stack trace.
        departingPairs.forEach((op, pairs) -> {
            System.out.println("   " + op + "  " + pairs.values().stream().mapToInt(Integer::intValue).sum()
                    + " row(s) over " + pairs.size() + " distinct pair(s)");
            pairs.forEach((pair, n) -> System.out.println("       x" + n + "  \"" + pair.replace("\t", "\\t") + "\""));
        });
        if (!unsupported.isEmpty()) {
            System.out.println("unsupported (" + unsupported.size() + "): "
                    + unsupported.subList(0, Math.min(25, unsupported.size())));
        }
        System.out.println("====================================================");

        assertTrue(operations >= MIN_OPERATIONS,
                "the census must enumerate at least " + MIN_OPERATIONS + " operations, got " + operations);
        assertTrue(supported >= MIN_SUPPORTED_FRACTION * operations,
                "the port must implement at least " + Math.round(MIN_SUPPORTED_FRACTION * 100)
                        + "% of the reference's operations; it supports " + supported + " of "
                        + operations + ". UNSUPPORTED is not a pass. Missing: " + unsupported);
        assertTrue(measuredRows >= MIN_MEASURED_ROWS,
                "at least " + MIN_MEASURED_ROWS + " rows must have been measured on BOTH sides, got "
                        + measuredRows + ". A sweep that drives nothing proves nothing.");
        assertEquals(0, divergingOps.size(),
                "the port diverged from the historical reference on " + divergingOps.keySet()
                        + " without a pre-registered departure covering it. Either that is a porting "
                        + "error, or it is a B7 correction nobody wrote down before running. The "
                        + "distinct pairs are printed above in declarePopulation() shape.");
        assertTrue(unusedDeclarations.isEmpty(),
                "pre-registered departures that never fired: " + unusedDeclarations
                        + ". Each is either a fix that did not land or a prediction that was wrong.");
        assertNull(tally.get(DiffVerdict.MIXED), "no operation may partially diverge");
    }
}
