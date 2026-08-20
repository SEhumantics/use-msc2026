package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p>Three properties are asserted here, and the first two exist because of that finding:
 * <ul>
 *   <li><b>A coverage floor.</b> The sweep must reach a minimum number of operations and rows. An
 *       operation the port stops supporting drops out of the numerator, so the floor is what turns
 *       a silent regression into a failure.</li>
 *   <li><b>A support floor.</b> The ported side must actually implement most of what the reference
 *       exposes. {@code UNSUPPORTED} is not a pass.</li>
 *   <li><b>Zero divergence</b> over everything that was driven.</li>
 * </ul>
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

    @Test
    @DisplayName("the ported U-types agree with the historical jars across the whole census")
    void portedSweepOverFullCensus() {
        InputGenerator generator = new InputGenerator(InputGenerator.DEFAULT_SEED);
        Map<String, List<UValue>> corpora = UnwrittenPortInvariantTest.corpora(generator);

        Map<DiffVerdict, Integer> tally = new EnumMap<>(DiffVerdict.class);
        Map<String, Integer> divergingOps = new LinkedHashMap<>();
        List<String> unsupported = new ArrayList<>();
        List<String> sampleDivergences = new ArrayList<>();
        int operations = 0;
        int supported = 0;
        long measuredRows = 0;
        long totalRows = 0;

        try (HistoricalOracle oracle = HistoricalOracle.open();
             PortedCandidate ported = PortedCandidate.open()) {

            List<UOp> census = UnwrittenPortInvariantTest.reachableOperations(oracle);
            operations = census.size();
            DifferentialSweep sweep = new DifferentialSweep(oracle, ported, generator.seed());

            for (UOp op : census) {
                if (!ported.supports(op)) {
                    unsupported.add(op.key());
                    continue;
                }
                supported++;
                for (List<UValue> corpus : corpora.values()) {
                    DifferentialSweep.Result r =
                            sweep.sweep(op, UnwrittenPortInvariantTest.domains(op, corpora, corpus));
                    for (DiffRow row : r.rows()) {
                        totalRows++;
                        tally.merge(row.verdict(), 1, Integer::sum);
                        if (row.verdict() == DiffVerdict.AGREE
                                || row.verdict() == DiffVerdict.DIFFER
                                || row.verdict() == DiffVerdict.MIXED) {
                            measuredRows++;
                        }
                        if (row.verdict() == DiffVerdict.DIFFER || row.verdict() == DiffVerdict.MIXED) {
                            divergingOps.merge(op.key(), 1, Integer::sum);
                            boolean firstForThisOp = divergingOps.get(op.key()) == 1;
                            if (firstForThisOp && sampleDivergences.size() < 15) {
                                sampleDivergences.add(op.key() + "  in=" + row.inputs()
                                        + "  ref=" + row.historical() + "  ported=" + row.ported());
                            }
                        }
                    }
                }
            }
        }

        System.out.println("=========== PORTED FIDELITY, FULL CENSUS ===========");
        System.out.println("operations enumerated  " + operations);
        System.out.println("supported by the port  " + supported
                + "  (" + Math.round(100.0 * supported / Math.max(1, operations)) + "%)");
        System.out.println("rows                   " + totalRows + " total, " + measuredRows + " measured");
        System.out.println("verdicts               " + tally);
        System.out.println("diverging operations   " + divergingOps.size());
        sampleDivergences.forEach(d -> System.out.println("   " + d));
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
                "the port diverged from the historical reference on " + divergingOps
                        + "; samples: " + sampleDivergences);
        assertNull(tally.get(DiffVerdict.MIXED), "no operation may partially diverge");
    }
}
