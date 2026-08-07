package org.tzi.use.uml.ocl.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * A native binary subjective opinion: belief, disbelief, uncertainty and a base
 * rate, with belief + disbelief + uncertainty = 1.
 *
 * <p>Values retain full double precision. The historical library rounded every
 * coordinate to six decimals on construction; that rounding is not reproduced,
 * so a derived opinion can differ from the historical one in the sixth decimal.
 * Only the comparisons that historically broke ties on the rounded value --
 * {@link #min}, {@link #max} and {@link #minimumBeliefFusion} -- round for that
 * purpose. The historical 0.001 mass tolerance is accepted but never silently
 * normalised away.
 */
public final class SBooleanValue extends UncertainBooleanValue {

    /** The historical tolerance on belief + disbelief + uncertainty = 1. */
    public static final double MASS_TOLERANCE = 1e-3;

    /**
     * A coordinate this far outside [0,1] is floating-point drift rather than an
     * invalid opinion, so it is accepted and clamped. The historical library got
     * the same effect implicitly by rounding every coordinate to six decimals
     * before checking it; without this, a result such as {@code 1-b-u} yielding
     * -2.8e-17 would turn a perfectly valid opinion into an undefined value. The
     * mass check stays exact: only the individual coordinates are clamped.
     */
    public static final double COORDINATE_TOLERANCE = 1e-9;

    public static final SBooleanValue TRUE = new SBooleanValue(1, 0, 0, 1);
    public static final SBooleanValue FALSE = new SBooleanValue(0, 1, 0, 1);

    private final double belief;
    private final double disbelief;
    private final double uncertainty;
    private final double baseRate;
    private final double relativeWeight;

    public SBooleanValue(double belief, double disbelief, double uncertainty, double baseRate) {
        this(belief, disbelief, uncertainty, baseRate, 1);
    }

    public SBooleanValue(double belief, double disbelief, double uncertainty,
            double baseRate, double relativeWeight) {
        super(TypeFactory.mkSBoolean());
        belief = clampUnit(belief, "belief");
        disbelief = clampUnit(disbelief, "disbelief");
        uncertainty = clampUnit(uncertainty, "uncertainty");
        baseRate = clampUnit(baseRate, "base rate");
        if (Math.abs(belief + disbelief + uncertainty - 1) > MASS_TOLERANCE) {
            throw new IllegalArgumentException(
                    "belief + disbelief + uncertainty must equal 1 within " + MASS_TOLERANCE);
        }
        if (!Double.isFinite(relativeWeight) || relativeWeight < 0) {
            throw new IllegalArgumentException("relative weight must be non-negative");
        }
        this.belief = belief;
        this.disbelief = disbelief;
        this.uncertainty = uncertainty;
        this.baseRate = baseRate;
        this.relativeWeight = relativeWeight;
    }

    private static double clampUnit(double x, String name) {
        if (!Double.isFinite(x) || x < -COORDINATE_TOLERANCE || x > 1 + COORDINATE_TOLERANCE) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
        return Math.min(1, Math.max(0, x));
    }

    private static void checkUnit(double x, String name) {
        clampUnit(x, name);
    }

    public static SBooleanValue dogmatic(double probability, double baseRate) {
        return new SBooleanValue(probability, 1 - probability, 0, baseRate);
    }

    public static SBooleanValue vacuous(double baseRate) {
        return new SBooleanValue(0, 0, 1, baseRate);
    }

    // ----------------------------------------------------------------- accessors

    public double belief() {
        return belief;
    }

    public double disbelief() {
        return disbelief;
    }

    public double uncertainty() {
        return uncertainty;
    }

    public double baseRate() {
        return baseRate;
    }

    /** The projected probability: belief plus the base rate's share of uncertainty. */
    public double projection() {
        return belief + baseRate * uncertainty;
    }

    public double certainty() {
        return 1 - uncertainty;
    }

    /** Historically zero unless the opinion is dogmatic; only fusion consumes it. */
    public double relativeWeight() {
        return isDogmatic() ? relativeWeight : 0;
    }

    @Override
    public boolean isSBoolean() {
        return true;
    }

    // ---------------------------------------------------------------- predicates

    public boolean isAbsolute() {
        return belief == 1 || disbelief == 1;
    }

    public boolean isVacuous() {
        return uncertainty == 1;
    }

    public boolean isDogmatic() {
        return uncertainty == 0;
    }

    public boolean isMaximizedUncertainty() {
        return belief == 0 || disbelief == 0;
    }

    public boolean isCertain(double threshold) {
        return !isUncertain(threshold);
    }

    public boolean isUncertain(double threshold) {
        checkUnit(threshold, "threshold");
        return certainty() < threshold;
    }

    // ------------------------------------------------------------------- algebra

    @Override
    public UBooleanValue toUBoolean() {
        return UBooleanValue.probability(projection());
    }

    @Override
    public SBooleanValue not() {
        return new SBooleanValue(disbelief, belief, uncertainty, 1 - baseRate, relativeWeight);
    }

    /** Conjunction of two independent opinions. */
    public SBooleanValue and(SBooleanValue o) {
        if (this == o) return this;
        double a = baseRate * o.baseRate;
        double b = belief * o.belief + (a == 1 ? 0
                : ((1 - baseRate) * o.baseRate * belief * o.uncertainty
                        + baseRate * (1 - o.baseRate) * uncertainty * o.belief) / (1 - a));
        double d = disbelief + o.disbelief - disbelief * o.disbelief;
        return new SBooleanValue(b, d, 1 - b - d, a, relativeWeight() + o.relativeWeight());
    }

    /** Disjunction of two independent opinions. */
    public SBooleanValue or(SBooleanValue o) {
        if (this == o) return this;
        double a = baseRate + o.baseRate - baseRate * o.baseRate;
        double b = belief + o.belief - belief * o.belief;
        double d = disbelief * o.disbelief
                + (baseRate + o.baseRate == baseRate * o.baseRate ? 0
                        : (baseRate * (1 - o.baseRate) * disbelief * o.uncertainty
                                + o.baseRate * (1 - baseRate) * uncertainty * o.disbelief) / a);
        return new SBooleanValue(b, d, 1 - b - d, a, relativeWeight() + o.relativeWeight());
    }

    public SBooleanValue xor(SBooleanValue o) {
        double b = Math.abs(belief - o.belief);
        double u = uncertainty * o.uncertainty;
        return new SBooleanValue(b, 1 - b - u, u, Math.abs(baseRate - o.baseRate),
                relativeWeight() + o.relativeWeight());
    }

    public SBooleanValue equivalent(SBooleanValue o) {
        return xor(o).not();
    }

    public SBooleanValue implies(SBooleanValue o) {
        return not().or(o);
    }

    // ------------------------------------------------------------------- metrics

    public double projectiveDistance(SBooleanValue o) {
        return Math.abs(projection() - o.projection());
    }

    public double conjunctiveCertainty(SBooleanValue o) {
        return certainty() * o.certainty();
    }

    public double degreeOfConflict(SBooleanValue o) {
        return projectiveDistance(o) * conjunctiveCertainty(o);
    }

    /** The equivalent opinion with as much of its mass as possible in uncertainty. */
    public SBooleanValue uncertaintyMaximized() {
        double p = projection();
        if (baseRate == 0) {
            return belief == 0 ? new SBooleanValue(0, 0, 1, baseRate, relativeWeight)
                    : new SBooleanValue(p, 0, 1 - p, baseRate, relativeWeight);
        }
        if (baseRate == 1) return new SBooleanValue(0, 1 - p, p, baseRate, relativeWeight);
        double u = Math.min(p / baseRate, (1 - p) / (1 - baseRate));
        return new SBooleanValue(p - baseRate * u, 1 - p - (1 - baseRate) * u, u,
                baseRate, relativeWeight);
    }

    public SBooleanValue uncertainOpinion() {
        return uncertaintyMaximized();
    }

    /**
     * Ordering by projection uses the historical six-decimal precision, so two
     * opinions whose projections differ only by float noise still tie and the
     * receiver wins, as it does historically. The projection accessor itself keeps
     * full precision.
     */
    private double comparableProjection() {
        return round(projection(), 6);
    }

    public SBooleanValue min(SBooleanValue o) {
        return comparableProjection() <= o.comparableProjection() ? this : o;
    }

    public SBooleanValue max(SBooleanValue o) {
        return comparableProjection() >= o.comparableProjection() ? this : o;
    }

    // ---------------------------------------------------------------- discounting

    /** Historical probability-sensitive trust discounting over one trust edge. */
    public SBooleanValue discount(SBooleanValue trust) {
        if (trust == null) throw new IllegalArgumentException("discount requires a trust opinion");
        double p = trust.projection();
        return new SBooleanValue(belief * p, disbelief * p,
                1 - (belief + disbelief) * p, baseRate);
    }

    /** The multi-edge form: the trust probabilities along the path multiply. */
    public SBooleanValue discount(Collection<SBooleanValue> trusts) {
        if (trusts == null || trusts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("discount requires non-null trusts");
        }
        double p = 1;
        for (SBooleanValue trust : trusts) {
            p *= trust.projection();
        }
        return new SBooleanValue(p * belief, p * disbelief,
                1 - p * (belief + disbelief), baseRate);
    }

    /**
     * Re-bases the opinion on the confidence of an uncertain Boolean. The
     * historical UBoolean stores a truth flag and a confidence separately, and its
     * confidence becomes the resulting base rate.
     */
    public SBooleanValue applyOn(UBooleanValue value) {
        double c = value.confidence();
        if (baseRate == 0) {
            return new SBooleanValue(belief + disbelief * c,
                    1 - belief - disbelief * c - uncertainty, uncertainty, c);
        }
        double b = Math.min(c * belief / baseRate, 1 - uncertainty);
        return new SBooleanValue(b, 1 - b - uncertainty, uncertainty, c);
    }

    // ----------------------------------------------------------------- deduction

    /** Historical subjective-logic deduction of Y from X and two conditionals. */
    public SBooleanValue deduceY(SBooleanValue yGivenX, SBooleanValue yGivenNotX) {
        double px = projection();
        double a = (yGivenX.uncertainty + yGivenNotX.uncertainty < 2)
                ? (baseRate * yGivenX.belief + (1 - baseRate) * yGivenNotX.belief)
                        / (1 - baseRate * yGivenX.uncertainty
                                - (1 - baseRate) * yGivenNotX.uncertainty)
                : yGivenX.baseRate;
        double pyxhat = yGivenX.belief * baseRate + yGivenNotX.belief * (1 - baseRate)
                + a * (yGivenX.uncertainty * baseRate + yGivenNotX.uncertainty * (1 - baseRate));

        // The unconstrained ("image") opinion, before the correction term k.
        double bIy = belief * yGivenX.belief + disbelief * yGivenNotX.belief
                + uncertainty * (yGivenX.belief * baseRate + yGivenNotX.belief * (1 - baseRate));
        double dIy = belief * yGivenX.disbelief + disbelief * yGivenNotX.disbelief
                + uncertainty * (yGivenX.disbelief * baseRate
                        + yGivenNotX.disbelief * (1 - baseRate));
        double uIy = belief * yGivenX.uncertainty + disbelief * yGivenNotX.uncertainty
                + uncertainty * (yGivenX.uncertainty * baseRate
                        + yGivenNotX.uncertainty * (1 - baseRate));

        // The eight historical cases are tested independently rather than as a
        // decision tree, because case III does not use one span: III.A.1 keeps the
        // case-II span while the other three use the mirrored one. Where the two
        // disagree no case fires and k stays 0, which a tree cannot express.
        boolean caseII = yGivenX.belief > yGivenNotX.belief
                && yGivenX.disbelief <= yGivenNotX.disbelief;
        boolean caseIII = yGivenX.belief <= yGivenNotX.belief
                && yGivenX.disbelief > yGivenNotX.disbelief;
        double spanII = yGivenNotX.belief
                + a * (1 - yGivenNotX.belief - yGivenX.disbelief);
        double spanIIIa1 = yGivenX.belief
                + a * (1 - yGivenNotX.belief - yGivenX.disbelief);
        double spanIII = yGivenX.belief
                + a * (1 - yGivenX.belief - yGivenNotX.disbelief);

        double k = 0;
        if (caseII && pyxhat <= spanII && px <= baseRate) {
            k = baseRate * uncertainty * (bIy - yGivenNotX.belief)
                    / ((belief + baseRate * uncertainty) * a);
        }
        if (caseII && pyxhat <= spanII && px > baseRate) {
            k = baseRate * uncertainty * (dIy - yGivenX.disbelief)
                    * (yGivenX.belief - yGivenNotX.belief)
                    / ((disbelief + (1 - baseRate) * uncertainty) * a
                            * (yGivenNotX.disbelief - yGivenX.disbelief));
        }
        if (caseII && pyxhat > spanII && px <= baseRate) {
            k = (1 - baseRate) * uncertainty * (bIy - yGivenNotX.belief)
                    * (yGivenNotX.disbelief - yGivenX.disbelief)
                    / ((belief + baseRate * uncertainty) * (1 - a)
                            * (yGivenX.belief - yGivenNotX.belief));
        }
        if (caseII && pyxhat > spanII && px > baseRate) {
            k = (1 - baseRate) * uncertainty * (dIy - yGivenX.disbelief)
                    / ((disbelief + (1 - baseRate) * uncertainty) * (1 - a));
        }
        if (caseIII && pyxhat <= spanIIIa1 && px <= baseRate) {
            k = (1 - baseRate) * uncertainty * (dIy - yGivenNotX.disbelief)
                    * (yGivenNotX.belief - yGivenX.belief)
                    / ((belief + baseRate * uncertainty) * a
                            * (yGivenX.disbelief - yGivenNotX.disbelief));
        }
        if (caseIII && pyxhat <= spanIII && px > baseRate) {
            k = (1 - baseRate) * uncertainty * (bIy - yGivenX.disbelief)
                    / ((disbelief + (1 - baseRate) * uncertainty) * a);
        }
        if (caseIII && pyxhat > spanIII && px <= baseRate) {
            k = baseRate * uncertainty * (dIy - yGivenNotX.belief)
                    / ((belief + baseRate * uncertainty) * (1 - a));
        }
        if (caseIII && pyxhat > spanIII && px > baseRate) {
            k = baseRate * uncertainty * (bIy - yGivenX.belief)
                    * (yGivenX.disbelief - yGivenNotX.disbelief)
                    / ((disbelief + (1 - baseRate) * uncertainty) * (1 - a)
                            * (yGivenNotX.belief - yGivenX.belief));
        }

        // The historical implementation writes these coordinates straight onto a
        // fresh opinion without passing the validating constructor, so a
        // degenerate branch that makes k NaN yields (0,0,0,a) -- masses summing to
        // zero. There is no way to represent that here, so it becomes undefined.
        return new SBooleanValue(bIy - a * k, dIy - (1 - a) * k, uIy + k, a);
    }

    // -------------------------------------------------------------------- fusion

    public static SBooleanValue minimumBeliefFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions, "minimum fusion");
        SBooleanValue result = null;
        for (SBooleanValue o : opinions) {
            result = result == null ? o : result.min(o);
        }
        return result;
    }

    public static SBooleanValue majorityBeliefFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions, "majority fusion");
        int positive = 0;
        int negative = 0;
        for (SBooleanValue o : opinions) {
            if (o.projection() > o.baseRate) positive++;
            else if (o.projection() < o.baseRate) negative++;
        }
        return positive > negative ? dogmatic(1, .5)
                : negative > positive ? dogmatic(0, .5) : vacuous(.5);
    }

    /**
     * Genuinely n-ary: the belief is the uncertainty-weighted mean over all
     * operands at once. Folding this pairwise gives a different answer, because
     * each fold re-weights an already-fused operand.
     */
    public static SBooleanValue averageBeliefFusion(Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions, "average fusion");
        List<SBooleanValue> all = List.copyOf(opinions);
        List<SBooleanValue> dogmatic = all.stream().filter(SBooleanValue::isDogmatic).toList();

        if (!dogmatic.isEmpty()) {
            double belief = 0;
            double baseRate = 0;
            for (SBooleanValue o : dogmatic) {
                belief += o.belief;
                baseRate += o.baseRate;
            }
            double b = belief / dogmatic.size();
            return new SBooleanValue(b, 1 - b, 0, baseRate / dogmatic.size());
        }

        double product = 1;
        for (SBooleanValue o : all) {
            product *= o.uncertainty;
        }
        double denominator = 0;
        double numerator = 0;
        double baseRate = 0;
        for (SBooleanValue o : all) {
            double weight = product / o.uncertainty;
            denominator += weight;
            numerator += o.belief * weight;
            baseRate += o.baseRate;
        }
        double u = all.size() * product / denominator;
        double b = numerator / denominator;
        return new SBooleanValue(b, 1 - b - u, u, baseRate / all.size());
    }

    /** Genuinely n-ary cumulative fusion of aleatory opinions. */
    public static SBooleanValue aleatoryCumulativeBeliefFusion(
            Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions, "cumulative fusion");
        List<SBooleanValue> all = List.copyOf(opinions);
        if (all.size() == 1) return all.get(0);

        List<SBooleanValue> dogmatic = all.stream().filter(SBooleanValue::isDogmatic).toList();
        if (!dogmatic.isEmpty()) return dogmaticFusion(dogmatic, all.get(0).baseRate);
        return cumulative(all);
    }

    /** The epistemic form is the aleatory one with its uncertainty maximized. */
    public static SBooleanValue epistemicCumulativeBeliefFusion(
            Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions, "epistemic cumulative fusion");
        List<SBooleanValue> all = List.copyOf(opinions);
        if (all.size() == 1) return all.get(0);

        List<SBooleanValue> dogmatic = all.stream().filter(SBooleanValue::isDogmatic).toList();
        if (!dogmatic.isEmpty()) {
            return dogmaticFusion(dogmatic, all.get(0).baseRate).uncertaintyMaximized();
        }
        return cumulative(all).uncertaintyMaximized();
    }

    /** Shared n-ary cumulative kernel of the two cumulative fusions. */
    private static SBooleanValue cumulative(List<SBooleanValue> opinions) {
        double product = 1;
        for (SBooleanValue o : opinions) {
            product *= o.uncertainty;
        }
        double denominator = 0;
        double belief = 0;
        double disbelief = 0;
        for (SBooleanValue o : opinions) {
            double weight = product / o.uncertainty;
            denominator += weight;
            belief += weight * o.belief;
            disbelief += weight * o.disbelief;
        }
        denominator -= (opinions.size() - 1) * product;
        return new SBooleanValue(belief / denominator, disbelief / denominator,
                product / denominator, opinions.get(0).baseRate, 0);
    }

    public static SBooleanValue beliefConstraintFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions, "belief constraint fusion");
        SBooleanValue result = null;
        for (SBooleanValue next : opinions) {
            result = result == null ? next : beliefConstraintBinary(result, next);
        }
        return result;
    }

    /** Belief constraint fusion is defined pairwise, so folding it is faithful. */
    private static SBooleanValue beliefConstraintBinary(SBooleanValue x, SBooleanValue y) {
        double harmony = x.belief * y.uncertainty + x.uncertainty * y.belief + x.belief * y.belief;
        double conflict = x.belief * y.disbelief + x.disbelief * y.belief;
        if (conflict == 1) {
            throw new IllegalArgumentException("belief constraint fusion: total conflict");
        }
        double b = harmony / (1 - conflict);
        double u = x.uncertainty * y.uncertainty / (1 - conflict);
        double a = (x.uncertainty + y.uncertainty == 2)
                ? (x.baseRate + y.baseRate) / 2
                : (x.baseRate * (1 - x.uncertainty) + y.baseRate * (1 - y.uncertainty))
                        / (2 - x.uncertainty - y.uncertainty);
        return new SBooleanValue(b, 1 - b - u, u, a);
    }

    /** Genuinely n-ary: each operand is weighted by its own certainty. */
    public static SBooleanValue weightedBeliefFusion(Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions, "weighted fusion");
        List<SBooleanValue> all = List.copyOf(opinions);
        if (all.size() == 1) return all.get(0);

        List<SBooleanValue> dogmatic = all.stream().filter(SBooleanValue::isDogmatic).toList();
        if (!dogmatic.isEmpty()) return dogmaticFusion(dogmatic, all.get(0).baseRate);
        if (all.stream().allMatch(SBooleanValue::isVacuous)) {
            return vacuous(all.stream().mapToDouble(SBooleanValue::baseRate).average().orElse(.5));
        }

        double product = 1;
        double uncertaintySum = 0;
        for (SBooleanValue o : all) {
            product *= o.uncertainty;
            uncertaintySum += o.uncertainty;
        }
        double denominator = 0;
        double belief = 0;
        double disbelief = 0;
        double baseRate = 0;
        for (SBooleanValue o : all) {
            double weight = product / o.uncertainty;
            denominator += weight;
            belief += weight * o.belief * o.certainty();
            disbelief += weight * o.disbelief * o.certainty();
            baseRate += o.baseRate * o.certainty();
        }
        double scale = denominator - all.size() * product;
        double u = (all.size() - uncertaintySum) * product / scale;
        return new SBooleanValue(belief / scale, disbelief / scale, u,
                baseRate / (all.size() - uncertaintySum));
    }

    /** Dogmatic operands carry no uncertainty to weight by, so relative weights decide. */
    private static SBooleanValue dogmaticFusion(List<SBooleanValue> opinions, double baseRate) {
        double total = opinions.stream().mapToDouble(SBooleanValue::relativeWeight).sum();
        if (total == 0) {
            throw new IllegalArgumentException("dogmatic fusion requires positive relative weight");
        }
        double belief = opinions.stream()
                .mapToDouble(o -> o.belief * o.relativeWeight()).sum() / total;
        double disbelief = opinions.stream()
                .mapToDouble(o -> o.disbelief * o.relativeWeight()).sum() / total;
        return new SBooleanValue(belief, disbelief, 0, baseRate, total);
    }

    /**
     * Consensus and compromise fusion. The consensus is the shared belief and
     * disbelief; what the operands do not share is redistributed by enumerating
     * every assignment of operands to the domain elements, which is why this is
     * genuinely n-ary and exponential in the operand count.
     */
    public static SBooleanValue consensusAndCompromiseFusion(
            Collection<SBooleanValue> opinions) {
        requireTwo(opinions, "consensus and compromise fusion");
        List<SBooleanValue> all = List.copyOf(opinions);
        double base = all.get(0).baseRate;
        for (SBooleanValue o : all) {
            if (o.baseRate != base) {
                throw new IllegalArgumentException("CCF requires equal base rates");
            }
        }

        double consensusBelief = all.stream().mapToDouble(SBooleanValue::belief).min().orElse(0);
        double consensusDisbelief =
                all.stream().mapToDouble(SBooleanValue::disbelief).min().orElse(0);
        double product = 1;
        for (SBooleanValue o : all) {
            product *= o.uncertainty;
        }

        // Residual belief and disbelief, beyond the consensus, per operand.
        List<Double> residualBelief = new ArrayList<>();
        List<Double> residualDisbelief = new ArrayList<>();
        double toBelief = 0;
        double toDisbelief = 0;
        double toUncertainty = 0;
        for (SBooleanValue o : all) {
            residualBelief.add(Math.max(o.belief - consensusBelief, 0));
            residualDisbelief.add(Math.max(o.disbelief - consensusDisbelief, 0));
            double weight = o.uncertainty == 0 ? 0 : product / o.uncertainty;
            toBelief += residualBelief.get(residualBelief.size() - 1) * weight;
            toDisbelief += residualDisbelief.get(residualDisbelief.size() - 1) * weight;
        }

        for (List<Domain> assignment : domainOptions(all.size())) {
            Domain intersection = Domain.DOMAIN;
            Domain union = Domain.NIL;
            for (Domain d : assignment) {
                intersection = intersection.intersect(d);
                union = union.union(d);
            }
            if (intersection == Domain.TRUE) {
                toBelief += productFor(assignment, residualBelief, residualDisbelief);
            } else if (intersection == Domain.FALSE) {
                toDisbelief += productFor(assignment, residualBelief, residualDisbelief);
            }
            if (intersection == Domain.NIL) {
                double mass = productFor(assignment, residualBelief, residualDisbelief);
                if (union == Domain.DOMAIN) toUncertainty += mass;
                else if (union == Domain.TRUE) toBelief += mass;
                else if (union == Domain.FALSE) toDisbelief += mass;
            }
        }

        double compromiseMass = toBelief + toDisbelief + toUncertainty;
        double normaliser = compromiseMass == 0 ? 1
                : (1 - consensusBelief - consensusDisbelief - product) / compromiseMass;
        double b = consensusBelief + normaliser * toBelief;
        double d = consensusDisbelief + normaliser * toDisbelief;
        return new SBooleanValue(b, d, 1 - b - d, base);
    }

    /** The subsets of the binary domain an operand's residual mass can fall on. */
    private enum Domain {
        NIL, TRUE, FALSE, DOMAIN;

        Domain intersect(Domain d) {
            if (this == NIL || d == NIL) return NIL;
            if (this == DOMAIN) return d;
            if (d == DOMAIN) return this;
            return this == d ? this : NIL;
        }

        Domain union(Domain d) {
            if (this == DOMAIN || d == DOMAIN) return DOMAIN;
            if (this == NIL) return d;
            if (d == NIL) return this;
            return this == d ? this : DOMAIN;
        }
    }

    /** The joint residual mass of one assignment, or zero if it is not a pure one. */
    private static double productFor(List<Domain> assignment,
            List<Double> residualBelief, List<Double> residualDisbelief) {
        double product = 1;
        for (int i = 0; i < assignment.size(); i++) {
            switch (assignment.get(i)) {
                case TRUE -> product *= residualBelief.get(i);
                case FALSE -> product *= residualDisbelief.get(i);
                case NIL, DOMAIN -> {
                    return 0;
                }
            }
        }
        return product;
    }

    private static List<List<Domain>> domainOptions(int n) {
        List<List<Domain>> result = new ArrayList<>();
        if (n == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        for (List<Domain> shorter : domainOptions(n - 1)) {
            for (Domain d : Domain.values()) {
                List<Domain> extended = new ArrayList<>(shorter);
                extended.add(d);
                result.add(extended);
            }
        }
        return result;
    }

    private static void requireNonEmpty(Collection<SBooleanValue> opinions, String what) {
        if (opinions == null || opinions.isEmpty()
                || opinions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(what + " requires non-null opinions");
        }
    }

    private static void requireTwo(Collection<SBooleanValue> opinions, String what) {
        requireNonEmpty(opinions, what);
        if (opinions.size() < 2) {
            throw new IllegalArgumentException(what + " requires at least two opinions");
        }
    }

    // ---------------------------------------------------------- equality, order

    /** Historical SBoolean equality stays in the subjective domain. */
    @Override
    public SBooleanValue uEquals(Value other) {
        return other instanceof SBooleanValue o ? equivalent(o) : FALSE;
    }

    @Override
    public SBooleanValue uDistinct(Value other) {
        return other instanceof SBooleanValue o ? xor(o) : TRUE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SBooleanValue x
                && Double.compare(belief, x.belief) == 0
                && Double.compare(disbelief, x.disbelief) == 0
                && Double.compare(uncertainty, x.uncertainty) == 0
                && Double.compare(baseRate, x.baseRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(belief, disbelief, uncertainty, baseRate);
    }

    /**
     * Subjective opinions have no defined total ordering, so every pair ties.
     * That is still a valid comparator -- it is transitive and antisymmetric --
     * so sorting a collection of opinions is well defined, it simply leaves them
     * in place. Set membership goes by {@link #equals}, not by this.
     */
    @Override
    public int compareTo(Value o) {
        return o instanceof UndefinedValue ? 1 : 0;
    }

    @Override
    public StringBuilder toString(StringBuilder b) {
        return b.append("SBoolean(").append(round(belief, 3))
                .append(", ").append(round(disbelief, 3))
                .append(", ").append(round(uncertainty, 3))
                .append(", ").append(round(baseRate, 3)).append(')');
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
