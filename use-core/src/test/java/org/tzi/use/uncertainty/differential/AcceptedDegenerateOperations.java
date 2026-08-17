package org.tzi.use.uncertainty.differential;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only way a <em>degenerate</em> operation can ever pass the stage gate: an explicit,
 * hand-authored list of operations whose reference side answers the same thing on every input the
 * corpora can supply, each signed off by a human who wrote down why that is acceptable.
 *
 * <h2>What this is for</h2>
 * {@link DifferentialSweep.Result#isStagePass(int, AcceptedDegenerateOperations)} refuses to call a
 * sweep a pass when the reference produced fewer than
 * {@link DifferentialSweep.Result#DISCRIMINATING_MINIMUM} (two) distinct values across the measured
 * rows. That is defect D-15: <strong>120 of the 285 reachable operations produce exactly one distinct
 * reference value over the shipped corpora</strong>, so a subject consisting of 120 hardcoded
 * literals — no arithmetic, no branching, never reading its receiver or its arguments — is scored
 * {@code AGREE} on every measured row of all 120, {@code isClean() == true} on 76 of them, and its
 * report header reads {@code # rows.disagreement 0}. Every one of those rows is individually
 * correct. The false statement is at sweep level, which is the level a stage reads.
 *
 * <h2>Why an allowlist and not an exclusion</h2>
 * Those operations are legitimately part of the ported surface — {@code isDefined()},
 * {@code isUReal()}, {@code type()}, {@code getRuntimeType()} and the type predicates are exactly
 * what an adapter writes first. Deleting them from the inventory would hide the row instead of
 * classifying it, which is the mistake round 1 made with {@code void} operations. Down-ranking them
 * silently would be worse. So they stay, they are measured, they are labelled, and the label has to
 * be signed off by name before any of them can read as a pass.
 *
 * <h2>Why the key includes the value</h2>
 * An entry is keyed on the operation <em>and</em> the single canonical value the reference gave.
 * A sign-off therefore lapses automatically the moment that value changes — a widened corpus, a
 * different jar, a different seed — rather than silently continuing to bless an operation whose
 * behaviour is no longer the one that was reviewed. This mirrors {@link AcceptedThrowPairs}, which
 * keys on both messages verbatim for the same reason, and it is the same deliberate friction: an
 * entry covers one concrete degeneracy and no others. A blanket "accept all type predicates" cannot
 * be expressed by this API. That is the point.
 *
 * <p>The {@code rationale} is mandatory, non-blank, and is copied into the stage statement and into
 * the report header of any sweep that used it, so the weakness travels with the number instead of
 * living in a document the reader of the number may never open.
 *
 * <p>The default is {@link #none()}, and it is never supplied implicitly: every call site has to
 * name it.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class AcceptedDegenerateOperations {

    /**
     * Separator between the two discriminators of a map key. ASCII US (unit separator, 0x1F),
     * written as an escape: a raw NUL in a character literal makes the source file binary to git and
     * defeats the human review this class's only safeguard rests on. See {@link AcceptedThrowPairs}
     * for the measurement that lesson came from.
     */
    private static final char SEP = '\u001F';

    private static final AcceptedDegenerateOperations NONE =
            new AcceptedDegenerateOperations(new LinkedHashMap<>());

    /** key -> rationale. */
    private final Map<String, String> accepted;

    private AcceptedDegenerateOperations(Map<String, String> accepted) {
        this.accepted = Collections.unmodifiableMap(new LinkedHashMap<>(accepted));
    }

    /** The empty allowlist: no degenerate operation may read as a pass. This is the default. */
    public static AcceptedDegenerateOperations none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return accepted.isEmpty();
    }

    public int size() {
        return accepted.size();
    }

    /** The signed-off entries, one per line, for a report header or a stage document. */
    public List<String> describe() {
        List<String> out = new ArrayList<>(accepted.size());
        for (Map.Entry<String, String> e : accepted.entrySet()) {
            out.add(e.getKey().replace(SEP, '|') + " -> " + e.getValue());
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The written rationale for accepting this operation as genuinely constant at exactly this
     * value, or {@code null} if it was never signed off — in which case the stage gate refuses.
     *
     * @param operationKey        {@link UOp#key()}
     * @param soleReferenceValue  the one canonical value the reference produced, from
     *                            {@link DifferentialSweep.Result#soleReferenceValue()}
     */
    public String rationaleFor(String operationKey, String soleReferenceValue) {
        if (accepted.isEmpty() || operationKey == null || soleReferenceValue == null) {
            return null;
        }
        return accepted.get(key(operationKey, soleReferenceValue));
    }

    private static String key(String operationKey, String soleReferenceValue) {
        return operationKey + SEP + soleReferenceValue;
    }

    @Override
    public String toString() {
        return "AcceptedDegenerateOperations[" + accepted.size() + " signed off]";
    }

    /** Builds an allowlist. Every argument is mandatory and none may be blank. */
    public static final class Builder {

        private final Map<String, String> accepted = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Signs off one genuinely-constant operation.
         *
         * @param operationKey       {@link UOp#key()}, e.g. {@code URealValue.isUReal()}
         * @param soleReferenceValue the one canonical value the reference gives, verbatim, e.g.
         *                           {@code BOOLEAN(true)}. If the reference ever gives a different
         *                           one this entry stops matching and the gate refuses again.
         * @param rationale          why a constant answer is the correct and complete specification
         *                           of this operation, and what a reader should therefore <em>not</em>
         *                           conclude from its agreement figure. Copied into the evidence.
         */
        public Builder accept(String operationKey, String soleReferenceValue, String rationale) {
            require(operationKey, "operationKey");
            require(soleReferenceValue, "soleReferenceValue");
            require(rationale, "rationale");
            String key = key(operationKey, soleReferenceValue);
            String previous = accepted.put(key, rationale);
            if (previous != null && !previous.equals(rationale)) {
                throw new IllegalArgumentException("the same degenerate operation is signed off twice "
                        + "with different rationales: " + key.replace(SEP, '|'));
            }
            return this;
        }

        private static void require(String value, String what) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(what + " must not be blank: an acknowledged "
                        + "degenerate operation has to name what was reviewed and why, or it is "
                        + "exactly the blanket exemption this class exists to prevent");
            }
        }

        public AcceptedDegenerateOperations build() {
            return accepted.isEmpty() ? NONE : new AcceptedDegenerateOperations(accepted);
        }
    }
}
