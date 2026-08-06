package org.tzi.use.uml.ocl.value;

import java.util.*;
import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * Native binary subjective opinion.  Values retain full double precision;
 * the historical 0.001 mass tolerance is accepted but never silently normalised.
 */
public final class SBooleanValue extends UncertainValue {
    public static final double MASS_TOLERANCE = 1e-3;
    private final double belief, disbelief, uncertainty, baseRate;
    public static final SBooleanValue TRUE = new SBooleanValue(1,0,0,1);
    public static final SBooleanValue FALSE = new SBooleanValue(0,1,0,1);
    public SBooleanValue(double belief, double disbelief, double uncertainty, double baseRate) {
        super(TypeFactory.mkSBoolean());
        checkUnit(belief,"belief"); checkUnit(disbelief,"disbelief"); checkUnit(uncertainty,"uncertainty"); checkUnit(baseRate,"base rate");
        if (Math.abs(belief+disbelief+uncertainty-1) > MASS_TOLERANCE) throw new IllegalArgumentException("belief + disbelief + uncertainty must equal 1 within " + MASS_TOLERANCE);
        this.belief=belief; this.disbelief=disbelief; this.uncertainty=uncertainty; this.baseRate=baseRate;
    }
    private static void checkUnit(double x, String name) { if (!Double.isFinite(x) || x < 0 || x > 1) throw new IllegalArgumentException(name+" must be in [0,1]"); }
    public double belief() { return belief; } public double disbelief() { return disbelief; }
    public double uncertainty() { return uncertainty; } public double baseRate() { return baseRate; }
    public double projection() { return belief + baseRate*uncertainty; }
    public double certainty() { return 1-uncertainty; }
    @Override public boolean isSBoolean() { return true; }
    public static SBooleanValue dogmatic(double probability, double baseRate) { return new SBooleanValue(probability,1-probability,0,baseRate); }
    public static SBooleanValue vacuous(double baseRate) { return new SBooleanValue(0,0,1,baseRate); }
    public UBooleanValue toUBoolean() { return UBooleanValue.probability(projection()); }
    public SBooleanValue not() { return new SBooleanValue(disbelief, belief, uncertainty, 1-baseRate); }
    public SBooleanValue and(SBooleanValue o) {
        return new SBooleanValue(belief*o.belief, disbelief+o.disbelief-disbelief*o.disbelief,
            belief*o.uncertainty+uncertainty*o.belief+uncertainty*o.uncertainty, baseRate*o.baseRate);
    }
    public SBooleanValue or(SBooleanValue o) {
        return new SBooleanValue(belief+o.belief-belief*o.belief, disbelief*o.disbelief,
            disbelief*o.uncertainty+uncertainty*o.disbelief+uncertainty*o.uncertainty, baseRate+o.baseRate-baseRate*o.baseRate);
    }
    public SBooleanValue xor(SBooleanValue o) {
        double b=Math.abs(belief-o.belief), u=uncertainty*o.uncertainty;
        return new SBooleanValue(b,1-b-u,u,Math.abs(baseRate-o.baseRate));
    }
    public SBooleanValue equivalent(SBooleanValue o) { return xor(o).not(); }
    public SBooleanValue implies(SBooleanValue o) { return not().or(o); }
    public double projectiveDistance(SBooleanValue o) { return Math.abs(projection()-o.projection()); }
    public double conjunctiveCertainty(SBooleanValue o) { return certainty()*o.certainty(); }
    public double degreeOfConflict(SBooleanValue o) { return projectiveDistance(o)*conjunctiveCertainty(o); }
    public boolean isAbsolute() { return belief==1 || disbelief==1; }
    public boolean isVacuous() { return uncertainty==1; }
    public boolean isDogmatic() { return uncertainty==0; }
    public boolean isMaximizedUncertainty() { return belief==0 || disbelief==0; }
    public boolean isCertain(double threshold) { return !isUncertain(threshold); }
    public boolean isUncertain(double threshold) { checkUnit(threshold,"threshold"); return certainty() < threshold; }
    public SBooleanValue uncertaintyMaximized() {
        double p=projection();
        if (baseRate==0) return new SBooleanValue(p,1-p,0,baseRate);
        if (baseRate==1) return new SBooleanValue(0,1-p,p,baseRate);
        double u=Math.min(p/baseRate,(1-p)/(1-baseRate));
        return new SBooleanValue(p-baseRate*u,1-p-(1-baseRate)*u,u,baseRate);
    }
    public SBooleanValue uncertainOpinion() { return uncertaintyMaximized(); }
    public SBooleanValue min(SBooleanValue o) { return projection() <= o.projection() ? this : o; }
    public SBooleanValue max(SBooleanValue o) { return projection() >= o.projection() ? this : o; }
    /** Historical probability-sensitive trust discounting. */
    public SBooleanValue discount(SBooleanValue trust) {
        double p=trust.projection();
        return new SBooleanValue(belief*p, disbelief*p, 1-(belief+disbelief)*p, baseRate);
    }
    public SBooleanValue applyOn(UBooleanValue value) {
        double c=value.probability();
        if (baseRate==0) return new SBooleanValue(belief+disbelief*c, 1-belief-disbelief*c-uncertainty, uncertainty,c);
        double b=Math.min(c*belief/baseRate,1-uncertainty);
        return new SBooleanValue(b,1-b-uncertainty,uncertainty,c);
    }
    public static SBooleanValue minimumBeliefFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions,"minimum fusion"); return opinions.stream().min(Comparator.comparingDouble(SBooleanValue::projection)).orElseThrow();
    }
    public static SBooleanValue majorityBeliefFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions,"majority fusion"); int positive=0,negative=0; for(var o:opinions) { if(o.projection()>o.baseRate) positive++; else if(o.projection()<o.baseRate) negative++; }
        return positive>negative ? dogmatic(1,.5) : negative>positive ? dogmatic(0,.5) : vacuous(.5);
    }
    public static SBooleanValue averageBeliefFusion(Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions,"average fusion"); List<SBooleanValue> os=List.copyOf(opinions); List<SBooleanValue> dogs=os.stream().filter(SBooleanValue::isDogmatic).toList();
        List<SBooleanValue> use=dogs.isEmpty()?os:dogs; double b=0,a=0; if(!dogs.isEmpty()) { for(var o:use){b+=o.belief;a+=o.baseRate;} return new SBooleanValue(b/use.size(),1-b/use.size(),0,a/use.size()); }
        double product=1; for(var o:use) product*=o.uncertainty; double denominator=0,numerator=0; for(var o:use){ double w=product/o.uncertainty; denominator+=w; numerator+=o.belief*w; a+=o.baseRate; }
        double u=use.size()*product/denominator; double rb=numerator/denominator; return new SBooleanValue(rb,1-rb-u,u,a/use.size());
    }
    public static SBooleanValue aleatoryCumulativeBeliefFusion(Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions,"cumulative fusion"); List<SBooleanValue> os=List.copyOf(opinions); List<SBooleanValue> dogs=os.stream().filter(SBooleanValue::isDogmatic).toList();
        if(!dogs.isEmpty()) return averageBeliefFusion(dogs); double product=1,a=0; for(var o:os){product*=o.uncertainty;a+=o.baseRate;} double den=0,b=0,d=0; for(var o:os){double w=product/o.uncertainty;den+=w;b+=w*o.belief;d+=w*o.disbelief;} den-=(os.size()-1)*product; return new SBooleanValue(b/den,d/den,product/den,a/os.size());
    }
    public static SBooleanValue epistemicCumulativeBeliefFusion(Collection<SBooleanValue> opinions) { return aleatoryCumulativeBeliefFusion(opinions).uncertaintyMaximized(); }
    public static SBooleanValue weightedBeliefFusion(Collection<SBooleanValue> opinions) { return averageBeliefFusion(opinions); }
    public static SBooleanValue beliefConstraintFusion(Collection<SBooleanValue> opinions) { return aleatoryCumulativeBeliefFusion(opinions); }
    public static SBooleanValue consensusAndCompromiseFusion(Collection<SBooleanValue> opinions) { return averageBeliefFusion(opinions); }
    private static void requireNonEmpty(Collection<SBooleanValue> os,String n){ if(os==null||os.isEmpty()||os.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException(n+" requires non-null opinions"); }
    private static void requireTwo(Collection<SBooleanValue> os,String n){ requireNonEmpty(os,n); if(os.size()<2)throw new IllegalArgumentException(n+" requires at least two opinions"); }
    @Override public UBooleanValue uEquals(Value other) { return other instanceof SBooleanValue o ? UBooleanValue.probability(1-projectiveDistance(o)) : UBooleanValue.FALSE; }
    @Override public boolean equals(Object o) { return o instanceof SBooleanValue x && Double.compare(belief,x.belief)==0&&Double.compare(disbelief,x.disbelief)==0&&Double.compare(uncertainty,x.uncertainty)==0&&Double.compare(baseRate,x.baseRate)==0; }
    @Override public int hashCode() { return Objects.hash(belief,disbelief,uncertainty,baseRate); }
    /** Stable representation order only; it is not a subjective-logic preference relation. */
    @Override public int compareTo(Value o) { if(o instanceof UndefinedValue)return 1; if(o instanceof SBooleanValue x){int c=Double.compare(projection(),x.projection());if(c!=0)return c;c=Double.compare(belief,x.belief);if(c!=0)return c;c=Double.compare(disbelief,x.disbelief);if(c!=0)return c;return Double.compare(baseRate,x.baseRate);} return toString().compareTo(o.toString()); }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("SBoolean(").append(belief).append(", ").append(disbelief).append(", ").append(uncertainty).append(", ").append(baseRate).append(')'); }
}
