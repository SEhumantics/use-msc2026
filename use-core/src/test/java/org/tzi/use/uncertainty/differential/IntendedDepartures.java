package org.tzi.use.uncertainty.differential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only way a divergence from the historical oracle can read as anything but a porting error: an
 * explicit, hand-authored, <em>pre-registered</em> statement that this operation, on this exact input
 * pair, was <strong>deliberately</strong> changed — naming the ledger row that decided it, which way
 * the answer moves, and why.
 *
 * <h2>What this is for</h2>
 * The user's decision B7 (2026-08-17, binding) is <strong>fix</strong> the historical defects rather
 * than reproduce them bug-for-bug. The recommendation on file was the opposite; it was not taken, and
 * {@code docs/port2/b7-fix-plan.md} section 0 records that so it is not re-litigated. The consequence
 * is stated there in section 0.1 and is the reason this class exists:
 *
 * <blockquote>
 * The port will deliberately not be bit-faithful to the fork. On the affected operations the
 * differential harness <em>will</em> report {@link DiffVerdict#DIFFER} by design. Therefore every
 * such divergence must be pre-registered <em>before</em> the sweep that produces it runs. A
 * divergence that is <em>discovered</em> is indistinguishable from a porting error.
 * </blockquote>
 *
 * That last sentence is the whole argument. {@link DifferentialSweep} measures <em>difference</em>,
 * and has no access to <em>intent</em>. Given a red row it cannot tell "we corrected
 * {@code UStringValue.equals}, which was constant {@code false}" from "the port dropped a
 * conjunct" — both are one operation, two values, not equal. The only thing that separates them is
 * whether a human wrote down, in advance, which pair would move and in which direction.
 *
 * <h2>Why the two existing allowlists do not cover this</h2>
 * {@link AcceptedThrowPairs} adjudicates <em>throw against throw</em>; it keys on both throwable
 * classes and both messages and never looks at a returned value. {@link AcceptedDegenerateOperations}
 * adjudicates <em>a reference that could not have answered otherwise</em>; it keys on the operation
 * and the single value the reference always gave. Neither can express the proposition here, which is
 * "both sides returned a value, the values differ, and the difference is the correction we decided to
 * make". Under the one gate a stage may use, such a row is a disagreement, and refusing it is
 * <em>correct</em> until something says otherwise in writing.
 *
 * <h2>The six rules, and the door each one closes</h2>
 * <ol>
 *   <li><strong>Both canonical forms are in the key, verbatim and type-bearing</strong> — the same
 *       {@link UValue#canonical()} string the verdict compares, {@code @Class} suffix included. A
 *       declaration therefore <em>lapses automatically</em> the moment either side's answer changes:
 *       a widened corpus, a different jar, a different seed, or a porting regression layered on top
 *       of the correction. This is {@link AcceptedDegenerateOperations}'s "why the key includes the
 *       value" argument applied to two columns instead of one.</li>
 *   <li><strong>{@code ledgerRowId} is mandatory and must name a row</strong> of
 *       {@code b7-fix-plan.md} section 2. A departure with no ledger row is not a decision, it is a
 *       surprise.</li>
 *   <li><strong>{@code rationale} is mandatory and non-blank</strong>, and travels into the note of
 *       every row it adjudicates and into the report header, so the weakness arrives with the
 *       number instead of living in a document the reader of the number may never open.</li>
 *   <li><strong>No blanket form is expressible.</strong> There is no {@code declareOperation(String)},
 *       no wildcard, no predicate. "Accept {@code DIFFER} on {@code UStringValue.equals}" cannot be
 *       written with this API. That is the point, and it is why {@code declareBounded} is capped.</li>
 *   <li><strong>A contradicted declaration does not apply.</strong> {@link Direction} is mandatory and
 *       is checked against the observed pair. If the pair does not move the way the stage predicted,
 *       the row stays {@link DiffVerdict#DIFFER}. <em>This is what converts "discovered" into
 *       "pre-registered":</em> the stage must state, before the run, not merely <em>that</em> an
 *       operation will differ but <em>which way</em>.</li>
 *   <li><strong>The count is in the header</strong>, unconditionally, including when it is zero —
 *       {@code # rows.intendedDeparture}, {@code # intendedDeparture.<ledgerRowId>}. A reader of the
 *       agreement figure cannot avoid seeing how much of the run was adjudicated rather than
 *       measured-and-matched.</li>
 * </ol>
 *
 * <h2>The clause that is easy to leave out, and is the whole point</h2>
 * A pre-registration mechanism that only ever <em>permits</em> differences lets an unfixed defect
 * through: a stage declares the departure, forgets to write the fix, and the sweep is green because
 * the port still agrees with the defective reference. So the gate also <em>requires</em> them.
 * {@link DifferentialSweep.Result#unusedDeclarations(IntendedDepartures)} is non-empty exactly when a
 * declaration was written and never fired, and that is a stage-gate failure with the same standing as
 * a residual {@code DIFFER}. Either the fix did not land, or the prediction was wrong. Both are
 * failures.
 *
 * <p>The default is {@link #none()} and it is never supplied implicitly: every call site names it.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class IntendedDepartures {

    /**
     * Separator between key discriminators. ASCII US (unit separator, 0x1F), written as an escape:
     * a raw control character in a literal makes the source binary to git and defeats the human
     * review this class's only safeguard rests on. See {@link AcceptedThrowPairs} for the
     * measurement that lesson came from.
     */
    private static final char SEP = '\u001F';

    /**
     * Upper bound on the number of <em>distinct</em> pairs a {@code Builder.declarePopulation} entry
     * may name. It is a bound on what a human is asked to read, which is the only bound that means
     * anything: the row count a correction moves is a fact about the corpus, but the number of
     * distinct answers it moves them to is a fact about the correction.
     *
     * <p>Measured on this repo at the moment the five B7 value-layer fixes landed: the departing
     * populations were 72, 112, 343, 343 and 432 rows, over 2 to 6 distinct pairs each. A form
     * capped on rows would have rejected all five; a form capped on distinct pairs asks a reviewer
     * to read at most six lines per correction and lapses if a seventh ever appears.
     */
    public static final int BOUNDED_CAP = 64;

    private static final IntendedDepartures NONE =
            new IntendedDepartures(new LinkedHashMap<>(), new LinkedHashMap<>());

    /** Which way a correction moves the answer, checked against what was actually observed. */
    public enum Direction {

        /**
         * The reference gave {@code BOOLEAN(false)} and the subject gives {@code BOOLEAN(true)}: the
         * correction <em>adds</em> equalities and can never remove one. This is F-4's shape — rounding
         * the cross-type arms of {@code URealValue.equals} can only make more things equal — and
         * M-8's and M-11's.
         */
        SUBJECT_IS_WIDER {
            @Override
            boolean holdsFor(String referenceContent, String subjectContent) {
                return "BOOLEAN(false)".equals(referenceContent)
                        && "BOOLEAN(true)".equals(subjectContent);
            }

            @Override
            String describeExpectation() {
                return "reference BOOLEAN(false) -> subject BOOLEAN(true)";
            }
        },

        /** The converse: the correction <em>removes</em> an equality the fork asserted. */
        SUBJECT_IS_NARROWER {
            @Override
            boolean holdsFor(String referenceContent, String subjectContent) {
                return "BOOLEAN(true)".equals(referenceContent)
                        && "BOOLEAN(false)".equals(subjectContent);
            }

            @Override
            String describeExpectation() {
                return "reference BOOLEAN(true) -> subject BOOLEAN(false)";
            }
        },

        /**
         * The reference returned {@code INTEGER(0)} — "these compare equal" — and the subject returns
         * a non-zero ordering. This is exactly M-9 ({@code UIntegerValue.compareTo} delegating without
         * negating, over a delegate with no matching arm, so the composite is a constant {@code 0})
         * and M-18 ({@code SBooleanValue.compareTo} whose entire body is {@code return 0;}).
         *
         * <p>Note it does <strong>not</strong> hold for {@code INTEGER(0) -> INTEGER(0)}: a fix that
         * left the tie in place is not this, and a declaration claiming it stays {@code DIFFER}.
         */
        SUBJECT_ORDERS_WHERE_REFERENCE_TIED {
            @Override
            boolean holdsFor(String referenceContent, String subjectContent) {
                return "INTEGER(0)".equals(referenceContent)
                        && subjectContent != null
                        && subjectContent.startsWith("INTEGER(")
                        && !"INTEGER(0)".equals(subjectContent);
            }

            @Override
            String describeExpectation() {
                return "reference INTEGER(0) -> subject INTEGER(non-zero)";
            }
        },

        /**
         * The general case: the reference is simply wrong and the subject is right, in a shape that
         * has no order to it — a changed hash, a changed rounding, a changed sort position.
         *
         * <p><strong>Stated plainly, because pretending otherwise would be the mistake this whole
         * class exists to prevent: this direction adds no constraint of its own.</strong> Its teeth
         * come entirely from rule 1 — the two exact canonical forms are in the key, so the
         * declaration lapses if either side's answer moves by one bit — and, for
         * {@code declareBounded}, from the digest and the exact count. Use one of the three shaped
         * directions whenever the departure has a shape; reach for this one when it genuinely does
         * not, and expect a reviewer to ask why.
         */
        REFERENCE_WAS_WRONG {
            @Override
            boolean holdsFor(String referenceContent, String subjectContent) {
                return true;
            }

            @Override
            String describeExpectation() {
                return "no shape constraint; the exact pair is the whole statement";
            }
        };

        /** Whether the observed pair moves the way this direction predicted. Content, not canonical. */
        abstract boolean holdsFor(String referenceContent, String subjectContent);

        /** Human-readable form of the prediction, for the failure message when it is contradicted. */
        abstract String describeExpectation();
    }

    /** One pre-registered departure. Immutable. */
    public static final class Declaration {

        private final String id;
        private final String operationKey;
        private final String ledgerRowId;
        private final Direction direction;
        private final String rationale;
        /** {@code null} for the per-pair form; the declared distinct pairs for the population form. */
        private final java.util.Set<String> distinctPairs;
        /** {@code -1} for the per-pair form. */
        private final int exactCount;
        /** {@code -1} unless this is a wide-codomain declaration; see {@code declareWideCodomain}. */
        private final int exactDistinctPairs;

        private Declaration(String id, String operationKey, String ledgerRowId, Direction direction,
                            String rationale, java.util.Set<String> distinctPairs, int exactCount) {
            this(id, operationKey, ledgerRowId, direction, rationale, distinctPairs, exactCount, -1);
        }

        private Declaration(String id, String operationKey, String ledgerRowId, Direction direction,
                            String rationale, java.util.Set<String> distinctPairs, int exactCount,
                            int exactDistinctPairs) {
            this.exactDistinctPairs = exactDistinctPairs;
            this.id = id;
            this.operationKey = operationKey;
            this.ledgerRowId = ledgerRowId;
            this.direction = direction;
            this.rationale = rationale;
            this.distinctPairs = distinctPairs == null ? null
                    : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(distinctPairs));
            this.exactCount = exactCount;
        }

        /** Stable identity of this declaration, used to report which ones never fired. */
        public String id() {
            return id;
        }

        public String operationKey() {
            return operationKey;
        }

        public String ledgerRowId() {
            return ledgerRowId;
        }

        public Direction direction() {
            return direction;
        }

        public String rationale() {
            return rationale;
        }

        /** Whether this entry names a whole population rather than a single input pair. */
        public boolean isPopulation() {
            return distinctPairs != null;
        }

        /** The exact number of rows this population declaration expects; {@code -1} per-pair. */
        public int exactCount() {
            return exactCount;
        }

        /** The distinct {@code reference\tsubject} pairs named, or {@code null} per-pair. */
        public java.util.Set<String> distinctPairs() {
            return distinctPairs;
        }

        /** Whether this entry pins two counts and a sample rather than the whole distinct set. */
        public boolean isWideCodomain() {
            return exactDistinctPairs >= 0;
        }

        /** The exact number of distinct pairs a wide-codomain declaration expects; {@code -1} else. */
        public int exactDistinctPairs() {
            return exactDistinctPairs;
        }

        /** The note this declaration puts on every row it adjudicates. */
        String note() {
            return "intended departure " + ledgerRowId + " (" + direction + ": "
                    + direction.describeExpectation() + "): " + rationale;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    /** per-pair key -&gt; declaration. */
    private final Map<String, Declaration> perPair;
    /** operationKey + SEP + ledgerRowId -&gt; declaration. */
    private final Map<String, Declaration> bounded;

    private IntendedDepartures(Map<String, Declaration> perPair, Map<String, Declaration> bounded) {
        this.perPair = Collections.unmodifiableMap(new LinkedHashMap<>(perPair));
        this.bounded = Collections.unmodifiableMap(new LinkedHashMap<>(bounded));
    }

    /** No divergence is intended. Every {@code DIFFER} is a porting error. This is the default. */
    public static IntendedDepartures none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return perPair.isEmpty() && bounded.isEmpty();
    }

    public int size() {
        return perPair.size() + bounded.size();
    }

    /** Every declaration, in declaration order, per-pair first. */
    public List<Declaration> declarations() {
        List<Declaration> out = new ArrayList<>(size());
        out.addAll(perPair.values());
        out.addAll(bounded.values());
        return Collections.unmodifiableList(out);
    }

    /** Every declaration written against {@code operationKey}. */
    public List<Declaration> declarationsFor(String operationKey) {
        List<Declaration> out = new ArrayList<>();
        for (Declaration d : declarations()) {
            if (d.operationKey().equals(operationKey)) {
                out.add(d);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The signed-off entries, one per line, for a report header or a stage document. */
    public List<String> describe() {
        List<String> out = new ArrayList<>(size());
        for (Declaration d : declarations()) {
            out.add(d.id() + " [" + d.ledgerRowId() + ", " + d.direction() + "] -> " + d.rationale());
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The per-pair declaration adjudicating this exact observed pair, or {@code null}. Returns
     * {@code null} when a declaration exists for the pair but {@code Direction.holdsFor} is
     * contradicted by it — rule 5: a prediction that did not come true does not adjudicate anything.
     *
     * @param operationKey       {@link UOp#key()}
     * @param referenceCanonical the historical side's {@link UValue#canonical()}, verbatim
     * @param subjectCanonical   the ported side's {@link UValue#canonical()}, verbatim
     */
    public Declaration adjudicate(String operationKey, String referenceCanonical,
                                  String subjectCanonical) {
        if (perPair.isEmpty() || operationKey == null
                || referenceCanonical == null || subjectCanonical == null) {
            return null;
        }
        Declaration d = perPair.get(pairKey(operationKey, referenceCanonical, subjectCanonical));
        if (d == null) {
            return null;
        }
        return d.direction.holdsFor(contentOf(referenceCanonical), contentOf(subjectCanonical))
                ? d : null;
    }

    /**
     * The population declaration adjudicating the whole residual departing set of one operation, or
     * {@code null}.
     *
     * <p>Applied as a post-pass rather than per row, because a claim about a population cannot be
     * evaluated until the population is complete. Fires only when <em>all three</em> hold: the row
     * count matches exactly, the distinct pairs observed are exactly the distinct pairs declared —
     * neither a subset nor a superset — and the direction holds for every one of them.
     *
     * <p>That is a strictly stronger statement than the per-pair form scaled up, and it is the
     * reason this form is not a blanket. One extra row, one missing row, one new pair, one pair that
     * stopped appearing, or one pair moving the wrong way, and it does not fire.
     *
     * @param operationKey {@link UOp#key()}
     * @param pairs        the residual departing pairs, as {@code reference + '\t' + subject}, with
     *                     duplicates — the count is part of the claim
     */
    public Declaration adjudicatePopulation(String operationKey, List<String> pairs) {
        if (bounded.isEmpty() || operationKey == null || pairs == null || pairs.isEmpty()) {
            return null;
        }
        java.util.Set<String> observed = new java.util.LinkedHashSet<>(pairs);
        for (Declaration d : bounded.values()) {
            if (!d.operationKey().equals(operationKey) || d.exactCount() != pairs.size()) {
                continue;
            }
            if (d.isWideCodomain()) {
                // Two counts and a sample. Weaker than set equality, and named so.
                if (d.exactDistinctPairs() != observed.size()
                        || !observed.containsAll(d.distinctPairs())) {
                    continue;
                }
            } else if (!d.distinctPairs().equals(observed)) {
                continue;
            }
            boolean allHold = true;
            for (String pair : observed) {
                int tab = pair.indexOf('\t');
                if (tab < 0 || !d.direction.holdsFor(contentOf(pair.substring(0, tab)),
                        contentOf(pair.substring(tab + 1)))) {
                    allHold = false;
                    break;
                }
            }
            if (allHold) {
                return d;
            }
        }
        return null;
    }

    /**
     * {@code UREAL(2.0,0.5)@URealValue} to {@code UREAL(2.0,0.5)}.
     *
     * <p>Splits at the last {@code '@'} only when what follows is a Java simple name, so a quoted
     * {@code STRING("a@b")} carrying no type suffix is not mistaken for a suffixed form. Direction
     * predicates run on content because the correction they describe is about the value, and a fix
     * that also changed the Java class would be a different finding (D-18) that must not be smuggled
     * through a direction check.
     */
    static String contentOf(String canonical) {
        if (canonical == null) {
            return null;
        }
        int at = canonical.lastIndexOf('@');
        if (at < 0 || at == canonical.length() - 1) {
            return canonical;
        }
        for (int i = at + 1; i < canonical.length(); i++) {
            char c = canonical.charAt(i);
            boolean ok = i == at + 1
                    ? Character.isJavaIdentifierStart(c)
                    : Character.isJavaIdentifierPart(c);
            if (!ok) {
                return canonical;
            }
        }
        return canonical.substring(0, at);
    }

    private static String pairKey(String operationKey, String referenceCanonical,
                                  String subjectCanonical) {
        return operationKey + SEP + referenceCanonical + SEP + subjectCanonical;
    }

    @Override
    public String toString() {
        return "IntendedDepartures[" + perPair.size() + " per-pair, " + bounded.size() + " bounded]";
    }

    /** Builds a pre-registration list. Every argument is mandatory and none may be blank. */
    public static final class Builder {

        private final Map<String, Declaration> perPair = new LinkedHashMap<>();
        private final Map<String, Declaration> bounded = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Pre-registers one departure, on one operation, at one exact input pair.
         *
         * @param operationKey       {@link UOp#key()}, e.g. {@code UStringValue.equals(value)}
         * @param ledgerRowId        the row of {@code b7-fix-plan.md} section 2 that decided this,
         *                           e.g. {@code M-11}
         * @param referenceCanonical the historical side's {@link UValue#canonical()}, verbatim and
         *                           type-bearing, e.g. {@code BOOLEAN(false)@Boolean}
         * @param subjectCanonical   the ported side's, likewise
         * @param direction          which way the correction moves the answer; checked against the
         *                           observed pair, and the row stays {@code DIFFER} if contradicted
         * @param rationale          what the fork does, what the port does instead, why the port is
         *                           right, and where it was decided. Copied into the evidence.
         */
        public Builder declare(String operationKey, String ledgerRowId, String referenceCanonical,
                               String subjectCanonical, Direction direction, String rationale) {
            require(operationKey, "operationKey");
            requireLedgerRow(ledgerRowId);
            require(referenceCanonical, "referenceCanonical");
            require(subjectCanonical, "subjectCanonical");
            require(rationale, "rationale");
            if (direction == null) {
                throw new IllegalArgumentException("direction must not be null: a departure whose "
                        + "direction was not predicted was discovered, not pre-registered");
            }
            if (referenceCanonical.equals(subjectCanonical)) {
                throw new IllegalArgumentException("referenceCanonical and subjectCanonical are "
                        + "identical (" + referenceCanonical + "): that pair is an AGREE and can "
                        + "never be adjudicated as a departure");
            }
            String key = pairKey(operationKey, referenceCanonical, subjectCanonical);
            String id = operationKey + " " + ledgerRowId + " " + referenceCanonical + "->"
                    + subjectCanonical;
            Declaration previous = perPair.put(key, new Declaration(id, operationKey, ledgerRowId,
                    direction, rationale, null, -1));
            if (previous != null && !previous.rationale().equals(rationale)) {
                throw new IllegalArgumentException("the same departure is pre-registered twice with "
                        + "different rationales: " + key.replace(SEP, '|'));
            }
            return this;
        }

        /**
         * Pre-registers a whole departing population, for the case the per-pair form cannot carry:
         * a correction that moves hundreds of rows onto a handful of answers.
         *
         * <p><strong>This is not a blanket, and it is not a digest either.</strong> An earlier draft
         * of this method took a {@code sha256} of the departing lines, on the theory that a digest
         * preserves the lapse property at one entry instead of many. It does — but a digest is a
         * thing a reviewer cannot read, and the review is the entire safeguard. Measured against the
         * five B7 value-layer corrections, the populations were 72 to 432 rows over 2 to 6 distinct
         * pairs; the pairs are short, they are the whole content of the claim, and they fit on the
         * screen. So the pairs are written out and the digest is gone.
         *
         * <p>The declaration fires only if all three hold: the residual row count is exactly
         * {@code exactRowCount}, the distinct pairs observed are <em>exactly</em> the set named here,
         * and {@code direction} holds for every one of them. A new pair, a vanished pair, or one row
         * more or fewer, and it lapses.
         *
         * @param exactRowCount how many rows will depart; an equality, not a maximum. This is the
         *                      part that lapses when the corpus or the seed moves.
         * @param distinctPairs every distinct {@code reference + '\t' + subject} the departure
         *                      produces, each written out in full and type-bearing. At most
         *                      {@link #BOUNDED_CAP}.
         */
        public Builder declarePopulation(String operationKey, String ledgerRowId, int exactRowCount,
                                         List<String> distinctPairs, Direction direction,
                                         String rationale) {
            require(operationKey, "operationKey");
            requireLedgerRow(ledgerRowId);
            require(rationale, "rationale");
            if (direction == null) {
                throw new IllegalArgumentException("direction must not be null");
            }
            if (distinctPairs == null || distinctPairs.isEmpty()) {
                throw new IllegalArgumentException("distinctPairs must name at least one pair: a "
                        + "population declaration that names no values is the blanket exemption this "
                        + "class exists to prevent");
            }
            java.util.Set<String> pairs = new java.util.LinkedHashSet<>(distinctPairs);
            if (pairs.size() != distinctPairs.size()) {
                throw new IllegalArgumentException("distinctPairs contains duplicates; it is a set of "
                        + "distinct answers, and the row multiplicity is carried by exactRowCount");
            }
            if (pairs.size() > BOUNDED_CAP) {
                throw new IllegalArgumentException("distinctPairs names " + pairs.size() + " pairs, "
                        + "above the cap of " + BOUNDED_CAP + ". A correction that moves rows onto "
                        + "that many different answers is not one correction, and nobody will read "
                        + "the list. Split it, or narrow the domain, or say in the stage document why "
                        + "this shape is the intended outcome.");
            }
            for (String pair : pairs) {
                if (pair == null || pair.indexOf('\t') < 0) {
                    throw new IllegalArgumentException("each pair must be reference + TAB + subject, "
                            + "got: " + pair);
                }
            }
            if (exactRowCount < pairs.size()) {
                throw new IllegalArgumentException("exactRowCount " + exactRowCount + " is below the "
                        + pairs.size() + " distinct pairs named: every declared pair has to occur on "
                        + "at least one row, or it was never observed and does not belong here");
            }
            String key = operationKey + SEP + ledgerRowId;
            String id = operationKey + " " + ledgerRowId + " x" + exactRowCount + " over "
                    + pairs.size() + " distinct pair(s)";
            Declaration previous = bounded.put(key, new Declaration(id, operationKey, ledgerRowId,
                    direction, rationale, pairs, exactRowCount));
            if (previous != null) {
                throw new IllegalArgumentException("two population declarations for the same "
                        + "operation and ledger row: " + key.replace(SEP, '|'));
            }
            return this;
        }

        /**
         * Pre-registers a departure whose <em>codomain is wide</em>: a correction that moves hundreds
         * of rows onto hundreds of different answers, so no enumeration of the answers is
         * reviewable.
         *
         * <p><strong>This is the weakest of the three forms and it is named so nobody reaches for it
         * by accident.</strong> {@code declare} pins one pair. {@code declarePopulation} pins the
         * whole distinct set. This one pins only two counts and a sample, and a reviewer should ask
         * why it was needed before accepting it.
         *
         * <p>It exists because one real correction has this shape and the alternative was worse.
         * M-12 fixes {@code UStringValue.compareTo}, where the fork compared a bare string against the
         * <em>wrapper rendering</em> {@code UString('x', 1.0)} of the argument; the port compares bare
         * against bare. {@code String.compareTo} returns a character difference, so over the shipped
         * corpora the correction moved <strong>432 rows onto 210 distinct pairs</strong>. Raising
         * {@link #BOUNDED_CAP} to 210 would have converted this class into the blanket exemption it
         * exists to prevent — the list would exist and nobody would read it — and excluding the
         * operation outright would have been weaker still, since an exclusion cannot notice that the
         * fix never landed.
         *
         * <p><strong>What it still pins.</strong> Both counts are equalities, and together they are a
         * fingerprint: a port that regressed on this operation would have to reproduce 432 rows over
         * exactly 210 distinct answers to slip past. Every pair in {@code mustInclude} has to be
         * observed, and {@code direction} has to hold for every one of them. And it is still a
         * <em>requirement</em>, not merely a permission: a fix that did not land produces zero
         * departing rows, the declaration does not fire, and clause 4 of the stage gate fails.
         *
         * <p><strong>What it does not pin.</strong> The 200-odd pairs outside {@code mustInclude} are
         * counted and not read. That is a declared weakness of this entry and belongs in the stage
         * document beside any figure taken from it.
         *
         * @param exactRowCount        rows that depart; an equality
         * @param exactDistinctPairs   distinct {@code reference\tsubject} pairs among them; an equality
         * @param mustInclude          pairs a reviewer has read and that must all be observed
         */
        public Builder declareWideCodomain(String operationKey, String ledgerRowId, int exactRowCount,
                                           int exactDistinctPairs, List<String> mustInclude,
                                           Direction direction, String rationale) {
            require(operationKey, "operationKey");
            requireLedgerRow(ledgerRowId);
            require(rationale, "rationale");
            if (direction == null) {
                throw new IllegalArgumentException("direction must not be null");
            }
            if (mustInclude == null || mustInclude.isEmpty()) {
                throw new IllegalArgumentException("mustInclude must name at least one pair a human "
                        + "has actually read; a declaration that pins only two integers is a "
                        + "fingerprint with nothing behind it");
            }
            java.util.Set<String> sample = new java.util.LinkedHashSet<>(mustInclude);
            if (sample.size() != mustInclude.size()) {
                throw new IllegalArgumentException("mustInclude contains duplicates");
            }
            if (sample.size() > BOUNDED_CAP) {
                throw new IllegalArgumentException("mustInclude names " + sample.size()
                        + " pairs, above the cap of " + BOUNDED_CAP);
            }
            for (String pair : sample) {
                if (pair == null || pair.indexOf('\t') < 0) {
                    throw new IllegalArgumentException("each pair must be reference + TAB + subject, "
                            + "got: " + pair);
                }
            }
            if (exactDistinctPairs <= BOUNDED_CAP) {
                throw new IllegalArgumentException("exactDistinctPairs " + exactDistinctPairs
                        + " is within the cap of " + BOUNDED_CAP + ", so this departure CAN be written "
                        + "out in full with declarePopulation. Use that: naming every answer is "
                        + "strictly stronger than naming two counts and a sample, and this form exists "
                        + "only for the corrections where the full list would not be read.");
            }
            if (exactDistinctPairs > exactRowCount) {
                throw new IllegalArgumentException("exactDistinctPairs " + exactDistinctPairs
                        + " exceeds exactRowCount " + exactRowCount);
            }
            if (sample.size() > exactDistinctPairs) {
                throw new IllegalArgumentException("mustInclude names more pairs than the declaration "
                        + "says exist");
            }
            String key = operationKey + SEP + ledgerRowId;
            String id = operationKey + " " + ledgerRowId + " x" + exactRowCount + " over "
                    + exactDistinctPairs + " distinct pair(s), WIDE CODOMAIN, " + sample.size()
                    + " read";
            Declaration previous = bounded.put(key, new Declaration(id, operationKey, ledgerRowId,
                    direction, rationale, sample, exactRowCount, exactDistinctPairs));
            if (previous != null) {
                throw new IllegalArgumentException("two population declarations for the same "
                        + "operation and ledger row: " + key.replace(SEP, '|'));
            }
            return this;
        }

        private static void requireLedgerRow(String ledgerRowId) {
            require(ledgerRowId, "ledgerRowId");
            if (!ledgerRowId.matches("^(CF|M|F)-[0-9]+[a-z]?$")) {
                throw new IllegalArgumentException("ledgerRowId " + ledgerRowId + " does not name a "
                        + "row of b7-fix-plan.md section 2 (expected CF-n, M-n or F-n): a departure "
                        + "with no ledger row is not a decision, it is a surprise");
            }
        }

        private static void require(String value, String what) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(what + " must not be blank: a pre-registered "
                        + "departure has to name what changes and why, or it is exactly the blanket "
                        + "exemption this class exists to prevent");
            }
        }

        public IntendedDepartures build() {
            return perPair.isEmpty() && bounded.isEmpty()
                    ? NONE : new IntendedDepartures(perPair, bounded);
        }
    }
}
