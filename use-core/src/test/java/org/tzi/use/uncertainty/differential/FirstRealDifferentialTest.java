package org.tzi.use.uncertainty.differential;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The first differential evidence in this project taken against the <em>real port</em> rather than a
 * stub, and the standing regression guard for it.
 *
 * <p>It asserts the only thing that may be asserted from a differential run: that no operation
 * <em>diverged</em>. It deliberately does NOT assert an agreement count, because an agreement count
 * is not a fidelity claim on its own — an operation whose codomain is a single point agrees for free
 * (defect D-15). {@code distinctRef} is therefore printed for every operation, and the three
 * genuinely degenerate ones are named in {@link #DEGENERATE} rather than quietly counted.
 *
 * <p>{@code HARNESS_ERROR} rows are not failures either: they are inputs one or both sides refused
 * to construct — an invalid probability, an opinion outside the 0.001 simplex tolerance — and the
 * operation under comparison was never entered. They are not measurements and are not agreement.
 */
public class FirstRealDifferentialTest {

    /** Operations measured to have a single-point codomain: their agreement is free, not evidence. */
    private static final java.util.Set<String> DEGENERATE = java.util.Set.of(
            "UStringValue.toBoolean()", "UStringValue.toInteger()", "UStringValue.toReal()");

    private static final java.util.List<String> diverged = new java.util.ArrayList<>();
    private static final java.util.List<String> unexpectedlyDegenerate = new java.util.ArrayList<>();

    private static void sweep(String receiver, String method, List<UValue> recv, List<UValue> arg) {
        try (HistoricalOracle oracle = HistoricalOracle.open();
             PortedCandidate ported = PortedCandidate.open()) {
            UOp op = arg == null ? UOp.unary(receiver, method) : UOp.binary(receiver, method);
            if (!oracle.supports(op) || !ported.supports(op)) {
                System.out.printf("%-28s SKIPPED ref=%s ported=%s%n", op.key(),
                        oracle.supports(op), ported.supports(op));
                return;
            }
            DifferentialSweep.Result r = arg == null
                    ? new DifferentialSweep(oracle, ported, 1L).sweepUnary(op, recv)
                    : new DifferentialSweep(oracle, ported, 1L).sweepBinary(op, recv, arg);
            Map<DiffVerdict, Integer> tally = new EnumMap<>(DiffVerdict.class);
            for (DiffRow row : r.rows()) {
                tally.merge(row.verdict(), 1, Integer::sum);
            }
            int distinct = r.distinctReferenceValues();
            System.out.printf("%-28s %4d rows  distinctRef=%-4d %s%s%n", op.key(), r.rows().size(),
                    distinct, tally, distinct <= 1 ? "   <== DEGENERATE, agreement is free" : "");
            int bad = tally.getOrDefault(DiffVerdict.DIFFER, 0) + tally.getOrDefault(DiffVerdict.MIXED, 0);
            if (bad > 0) {
                diverged.add(op.key() + " (" + bad + " rows)");
            }
            if (distinct <= 1 && !DEGENERATE.contains(op.key())) {
                unexpectedlyDegenerate.add(op.key());
            }
            for (DiffRow row : r.rows()) {
                if (row.verdict() == DiffVerdict.DIFFER || row.verdict() == DiffVerdict.MIXED) {
                    System.out.println("     " + row.verdict() + "  in=" + row.inputs()
                            + "\n         ref   =" + row.historical()
                            + "\n         ported=" + row.ported());
                    break;
                }
            }
        }
    }

    @Test
    @DisplayName("first real differential: the ported U-types against the historical jar")
    void firstRealSweep() {
        InputGenerator gen = new InputGenerator(20260817L);
        List<UValue> ureal = gen.uRealCorpus(6);
        List<UValue> uint = gen.uIntegerCorpus(6);
        List<UValue> ubool = gen.uBooleanCorpus(6);
        List<UValue> ustr = gen.uStringCorpus(6);
        List<UValue> sbool = gen.sBooleanCorpus(4);

        System.out.println("================ FIRST REAL DIFFERENTIAL ================");
        for (String m : new String[] { "add", "minus", "mult", "divideBy", "min", "max" }) {
            sweep("URealValue", m, ureal, ureal);
        }
        for (String m : new String[] { "neg", "abs", "floor", "round", "sqrt", "inverse",
                                       "toReal", "toInteger", "toUInteger" }) {
            sweep("URealValue", m, ureal, null);
        }
        for (String m : new String[] { "lt", "gt", "le", "ge" }) {
            sweep("URealValue", m, ureal, ureal);
        }
        for (String m : new String[] { "add", "minus", "mult" }) {
            sweep("UIntegerValue", m, uint, uint);
        }
        for (String m : new String[] { "and", "or", "not" }) {
            sweep("UBooleanValue", m, ubool, m.equals("not") ? null : ubool);
        }
        for (String m : new String[] { "uConcat", "lt", "gt", "le", "ge" }) {
            sweep("UStringValue", m, ustr, ustr);
        }
        for (String m : new String[] { "toBoolean", "toInteger", "toReal", "uToString",
                                       "toUBoolean", "uCharacters" }) {
            sweep("UStringValue", m, ustr, null);
        }
        sweep("SBooleanValue", "and", sbool, sbool);
        sweep("SBooleanValue", "not", sbool, null);
        System.out.println("=========================================================");

        assertTrue(diverged.isEmpty(),
                "the ported U-types diverged from the historical reference on: " + diverged);
        assertTrue(unexpectedlyDegenerate.isEmpty(),
                "these operations collapsed to a single reference value, so their agreement is free "
                        + "and is not evidence -- either widen the corpus or add them to DEGENERATE "
                        + "with a written reason: " + unexpectedlyDegenerate);
    }
}
