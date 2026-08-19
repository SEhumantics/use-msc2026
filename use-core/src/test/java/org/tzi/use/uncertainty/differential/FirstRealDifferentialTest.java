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

    /**
     * Operations whose agreement is free because their codomain is a single point (D-15).
     *
     * <p><strong>Empty, and it should stay that way.</strong> It held three entries when this test
     * was written — {@code UStringValue.toBoolean/toInteger/toReal} — but that turned out to be a
     * property of the CORPUS, not of the operations: {@code uStringBoundaries()} contained no string
     * that parsed as a boolean, an integer or a real, so {@code toBoolean()} could only ever answer
     * false and the other two could only ever throw. Ten parseable spellings were added and the
     * three now discriminate (2, 5 and 8 distinct reference values respectively; 2 is the whole
     * Boolean codomain, so that one is complete rather than merely improved).
     *
     * <p>Adding an entry here is an admission that an operation is being counted as agreeing without
     * evidence. Widen the corpus first; only exempt an operation whose codomain is genuinely a
     * single point, and say why in writing.
     */
    private static final java.util.Set<String> DEGENERATE = java.util.Set.of(
            // GENUINELY single-point, and the only entry that has earned its place.
            //
            // UBooleanValue.valueOf(boolean,double) canonicalises every opinion to "true with
            // probability p":
            //     if (!value) { value = true; probability = 1 - probability; }
            // (UBooleanValue.java:127-130). So no UBooleanValue reachable through the public
            // factory has value() == false, and value() has ONE inhabitant by construction. No
            // corpus can widen it; the information lives entirely in probability(), which measures
            // distinctRef=10 over the same corpus.
            //
            // This is the distinction the rest of this list is for: UString's three conversions
            // looked identical to this and were NOT structural -- they were a corpus that contained
            // no parseable string. Widening fixed them. Widening cannot fix this one.
            "UBooleanValue.value()");

    private static final java.util.List<String> diverged = new java.util.ArrayList<>();
    private static final java.util.List<String> unexpectedlyDegenerate = new java.util.ArrayList<>();
    private static final java.util.List<String> typeMismatches = new java.util.ArrayList<>();

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
            if (r.javaTypeMismatchCount() > 0) {
                typeMismatches.add(op.key() + " (" + r.javaTypeMismatchCount() + " rows, observed="
                        + r.subjectTypeObservedCount() + ", assumed=" + r.subjectTypeAssumedCount() + ")");
            }
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
        // Primitive-returning accessors. harness-contract.md sec.7 names these specifically: an
        // adapter that types its result from the factory instead of observing the returned object
        // measures a class mismatch on all of them, because reflection hands back a boxed
        // java.lang.Double / Integer, not a URealValue. 3,445 such rows across 182 of 285
        // operations, from a port with no defect in it. They are swept here so that the
        // zero-mismatch assertion below is exercised on the case it exists for.
        for (String m : new String[] { "value", "uncertainty" }) {
            sweep("URealValue", m, ureal, null);
            sweep("UIntegerValue", m, uint, null);
        }
        sweep("UBooleanValue", "value", ubool, null);
        sweep("UBooleanValue", "probability", ubool, null);
        sweep("UStringValue", "value", ustr, null);
        sweep("UStringValue", "confidence", ustr, null);
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
        System.out.println("java-type mismatches: " + (typeMismatches.isEmpty() ? "NONE" : typeMismatches));

        assertTrue(diverged.isEmpty(),
                "the ported U-types diverged from the historical reference on: " + diverged);

        // D-52, now assertable. Until S4 the ported side had no object to observe, so its class
        // token was the factory's ASSUMPTION and a type-only difference had to be counted rather
        // than scored. PortedCandidate observes the object it actually returned, so the token is
        // real on both sides and this becomes a clause rather than a report line.
        assertTrue(typeMismatches.isEmpty(),
                "the two sides named different Java classes for identical content. If this fires on "
                        + "the primitive accessors it means the adapter typed its result from the "
                        + "factory instead of calling UValue.observedFrom(returned) -- see "
                        + "harness-contract.md sec.7: " + typeMismatches);
        assertTrue(unexpectedlyDegenerate.isEmpty(),
                "these operations collapsed to a single reference value, so their agreement is free "
                        + "and is not evidence -- either widen the corpus or add them to DEGENERATE "
                        + "with a written reason: " + unexpectedlyDegenerate);
    }
}
