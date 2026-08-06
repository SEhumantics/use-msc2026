package org.tzi.use.uml.ocl.value;

import java.util.*;
import org.tzi.use.uml.ocl.type.TypeFactory;

/**
 * Native binary subjective opinion.  Values retain full double precision;
 * the historical 0.001 mass tolerance is accepted but never silently normalised.
 */
public final class SBooleanValue extends UncertainValue {
    public static final double MASS_TOLERANCE = 1e-3;
    private final double belief, disbelief, uncertainty, baseRate, relativeWeight;
    public static final SBooleanValue TRUE = new SBooleanValue(1,0,0,1);
    public static final SBooleanValue FALSE = new SBooleanValue(0,1,0,1);
    public SBooleanValue(double belief, double disbelief, double uncertainty, double baseRate) {
        this(belief, disbelief, uncertainty, baseRate, 1);
    }
    public SBooleanValue(double belief,double disbelief,double uncertainty,double baseRate,double relativeWeight){
        super(TypeFactory.mkSBoolean());
        checkUnit(belief,"belief"); checkUnit(disbelief,"disbelief"); checkUnit(uncertainty,"uncertainty"); checkUnit(baseRate,"base rate");
        if (Math.abs(belief+disbelief+uncertainty-1) > MASS_TOLERANCE) throw new IllegalArgumentException("belief + disbelief + uncertainty must equal 1 within " + MASS_TOLERANCE);
        if(!Double.isFinite(relativeWeight)||relativeWeight<0)throw new IllegalArgumentException("relative weight must be non-negative");
        this.belief=belief; this.disbelief=disbelief; this.uncertainty=uncertainty; this.baseRate=baseRate;
        this.relativeWeight=relativeWeight;
    }
    private static void checkUnit(double x, String name) { if (!Double.isFinite(x) || x < 0 || x > 1) throw new IllegalArgumentException(name+" must be in [0,1]"); }
    public double belief() { return belief; } public double disbelief() { return disbelief; }
    public double uncertainty() { return uncertainty; } public double baseRate() { return baseRate; }
    public double projection() { return belief + baseRate*uncertainty; }
    public double certainty() { return 1-uncertainty; }
    public double relativeWeight() { return isDogmatic() ? relativeWeight : 0; }
    @Override public boolean isSBoolean() { return true; }
    public static SBooleanValue dogmatic(double probability, double baseRate) { return new SBooleanValue(probability,1-probability,0,baseRate); }
    public static SBooleanValue vacuous(double baseRate) { return new SBooleanValue(0,0,1,baseRate); }
    public UBooleanValue toUBoolean() { return UBooleanValue.probability(projection()); }
    public SBooleanValue not() { return new SBooleanValue(disbelief, belief, uncertainty, 1-baseRate, relativeWeight); }
    public SBooleanValue and(SBooleanValue o) {
        if (this == o) return this;
        double a=baseRate*o.baseRate;
        double b=belief*o.belief+(a==1?0:((1-baseRate)*o.baseRate*belief*o.uncertainty+baseRate*(1-o.baseRate)*uncertainty*o.belief)/(1-a));
        double d=disbelief+o.disbelief-disbelief*o.disbelief;
        return new SBooleanValue(b,d,1-b-d,a,relativeWeight()+o.relativeWeight());
    }
    public SBooleanValue or(SBooleanValue o) {
        if (this == o) return this;
        double a=baseRate+o.baseRate-baseRate*o.baseRate;
        double b=belief+o.belief-belief*o.belief;
        double d=disbelief*o.disbelief+(baseRate+o.baseRate==baseRate*o.baseRate?0:(baseRate*(1-o.baseRate)*disbelief*o.uncertainty+o.baseRate*(1-baseRate)*uncertainty*o.disbelief)/a);
        return new SBooleanValue(b,d,1-b-d,a,relativeWeight()+o.relativeWeight());
    }
    public SBooleanValue xor(SBooleanValue o) {
        double b=Math.abs(belief-o.belief), u=uncertainty*o.uncertainty;
        return new SBooleanValue(b,1-b-u,u,Math.abs(baseRate-o.baseRate),relativeWeight()+o.relativeWeight());
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
        if (baseRate==0) return belief==0 ? new SBooleanValue(0,0,1,baseRate,relativeWeight) : new SBooleanValue(p,0,1-p,baseRate,relativeWeight);
        if (baseRate==1) return new SBooleanValue(0,1-p,p,baseRate,relativeWeight);
        double u=Math.min(p/baseRate,(1-p)/(1-baseRate));
        return new SBooleanValue(p-baseRate*u,1-p-(1-baseRate)*u,u,baseRate,relativeWeight);
    }
    public SBooleanValue uncertainOpinion() { return uncertaintyMaximized(); }
    public SBooleanValue min(SBooleanValue o) { return projection() <= o.projection() ? this : o; }
    public SBooleanValue max(SBooleanValue o) { return projection() >= o.projection() ? this : o; }
    /** Historical probability-sensitive trust discounting. */
    public SBooleanValue discount(SBooleanValue trust) {
        if (trust == null) throw new IllegalArgumentException("discount requires a trust opinion");
        double p=trust.projection();
        return new SBooleanValue(belief*p, disbelief*p, 1-(belief+disbelief)*p, baseRate);
    }
    public SBooleanValue discount(Collection<SBooleanValue> trusts) {
        if (trusts == null || trusts.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("discount requires non-null trusts");
        double p=1; for (SBooleanValue t: trusts) p*=t.projection();
        return new SBooleanValue(p*belief,p*disbelief,1-p*(belief+disbelief),baseRate);
    }
    public SBooleanValue applyOn(UBooleanValue value) {
        // Historical UBoolean stores a truth flag and confidence separately;
        // its confidence becomes the SBoolean base rate.
        double c=value.confidence();
        if (baseRate==0) return new SBooleanValue(belief+disbelief*c, 1-belief-disbelief*c-uncertainty, uncertainty,c);
        double b=Math.min(c*belief/baseRate,1-uncertainty);
        return new SBooleanValue(b,1-b-uncertainty,uncertainty,c);
    }
    /** Historical subjective-logic deduction of Y from X and two conditional opinions. */
    public SBooleanValue deduceY(SBooleanValue yGivenX, SBooleanValue yGivenNotX) {
        double px=projection();
        double a=(yGivenX.uncertainty+yGivenNotX.uncertainty<2)
            ? (baseRate*yGivenX.belief+(1-baseRate)*yGivenNotX.belief)/(1-baseRate*yGivenX.uncertainty-(1-baseRate)*yGivenNotX.uncertainty)
            : yGivenX.baseRate;
        double pyxhat=yGivenX.belief*baseRate+yGivenNotX.belief*(1-baseRate)+a*(yGivenX.uncertainty*baseRate+yGivenNotX.uncertainty*(1-baseRate));
        double bIy=belief*yGivenX.belief+disbelief*yGivenNotX.belief+uncertainty*(yGivenX.belief*baseRate+yGivenNotX.belief*(1-baseRate));
        double dIy=belief*yGivenX.disbelief+disbelief*yGivenNotX.disbelief+uncertainty*(yGivenX.disbelief*baseRate+yGivenNotX.disbelief*(1-baseRate));
        double uIy=belief*yGivenX.uncertainty+disbelief*yGivenNotX.uncertainty+uncertainty*(yGivenX.uncertainty*baseRate+yGivenNotX.uncertainty*(1-baseRate));
        double k=0;
        if(yGivenX.belief>yGivenNotX.belief&&yGivenX.disbelief<=yGivenNotX.disbelief){
            if(pyxhat<=yGivenNotX.belief+a*(1-yGivenNotX.belief-yGivenX.disbelief)){
                if(px<=baseRate)k=baseRate*uncertainty*(bIy-yGivenNotX.belief)/((belief+baseRate*uncertainty)*a);
                else k=baseRate*uncertainty*(dIy-yGivenX.disbelief)*(yGivenX.belief-yGivenNotX.belief)/((disbelief+(1-baseRate)*uncertainty)*a*(yGivenNotX.disbelief-yGivenX.disbelief));
            } else {
                if(px<=baseRate)k=(1-baseRate)*uncertainty*(bIy-yGivenNotX.belief)*(yGivenNotX.disbelief-yGivenX.disbelief)/((belief+baseRate*uncertainty)*(1-a)*(yGivenX.belief-yGivenNotX.belief));
                else k=(1-baseRate)*uncertainty*(dIy-yGivenX.disbelief)/((disbelief+(1-baseRate)*uncertainty)*(1-a));
            }
        } else if(yGivenX.belief<=yGivenNotX.belief&&yGivenX.disbelief>yGivenNotX.disbelief){
            if(pyxhat<=yGivenX.belief+a*(1-yGivenX.belief-yGivenNotX.disbelief)){
                if(px<=baseRate)k=(1-baseRate)*uncertainty*(dIy-yGivenNotX.disbelief)*(yGivenNotX.belief-yGivenX.belief)/((belief+baseRate*uncertainty)*a*(yGivenX.disbelief-yGivenNotX.disbelief));
                else k=(1-baseRate)*uncertainty*(bIy-yGivenX.disbelief)/((disbelief+(1-baseRate)*uncertainty)*a);
            } else {
                if(px<=baseRate)k=baseRate*uncertainty*(dIy-yGivenNotX.belief)/((belief+baseRate*uncertainty)*(1-a));
                else k=baseRate*uncertainty*(bIy-yGivenX.belief)*(yGivenX.disbelief-yGivenNotX.disbelief)/((disbelief+(1-baseRate)*uncertainty)*(1-a)*(yGivenNotX.belief-yGivenX.belief));
            }
        }
        return new SBooleanValue(bIy-a*k,dIy-(1-a)*k,uIy+k,a);
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
        if (os.size() == 1) return os.get(0);
        if (!dogs.isEmpty()) return dogmaticFusion(dogs, os.get(0).baseRate);
        double product=1,a=os.get(0).baseRate; for(var o:os){product*=o.uncertainty;} double den=0,b=0,d=0; for(var o:os){double w=product/o.uncertainty;den+=w;b+=w*o.belief;d+=w*o.disbelief;} den-=(os.size()-1)*product; return new SBooleanValue(b/den,d/den,product/den,a,0);
    }
    public static SBooleanValue epistemicCumulativeBeliefFusion(Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions,"epistemic cumulative fusion"); List<SBooleanValue> os=List.copyOf(opinions);
        List<SBooleanValue> dogs=os.stream().filter(SBooleanValue::isDogmatic).toList();
        if (os.size() == 1) return os.get(0);
        if (!dogs.isEmpty()) return dogmaticFusion(dogs, os.get(0).baseRate).uncertaintyMaximized();
        double product=1,b=0,d=0,den=0,a=os.get(0).baseRate; for(var o:os)product*=o.uncertainty;
        for(var o:os){double w=product/o.uncertainty;den+=w;b+=w*o.belief;d+=w*o.disbelief;}
        den-=(os.size()-1)*product;
        return new SBooleanValue(b/den,d/den,product/den,a,0).uncertaintyMaximized();
    }
    public static SBooleanValue beliefConstraintFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions,"belief constraint fusion"); SBooleanValue result=null;
        for (SBooleanValue next: opinions) result=result==null?next:beliefConstraintBinary(result,next);
        return result;
    }
    private static SBooleanValue beliefConstraintBinary(SBooleanValue x,SBooleanValue y) {
        double harmony=x.belief*y.uncertainty+x.uncertainty*y.belief+x.belief*y.belief;
        double conflict=x.belief*y.disbelief+x.disbelief*y.belief;
        if (conflict==1) throw new IllegalArgumentException("belief constraint fusion: total conflict");
        double b=harmony/(1-conflict), u=x.uncertainty*y.uncertainty/(1-conflict);
        double a=(x.uncertainty+y.uncertainty==2)?(x.baseRate+y.baseRate)/2:
            (x.baseRate*(1-x.uncertainty)+y.baseRate*(1-y.uncertainty))/(2-x.uncertainty-y.uncertainty);
        return new SBooleanValue(b,1-b-u,u,a);
    }
    public static SBooleanValue weightedBeliefFusion(Collection<SBooleanValue> opinions) {
        requireNonEmpty(opinions,"weighted fusion"); List<SBooleanValue> os=List.copyOf(opinions);
        if (os.size() == 1) return os.get(0);
        List<SBooleanValue> dogmatic=os.stream().filter(SBooleanValue::isDogmatic).toList();
        if (!dogmatic.isEmpty()) return dogmaticFusion(dogmatic, os.get(0).baseRate);
        if (os.stream().allMatch(SBooleanValue::isVacuous)) return vacuous(os.stream().mapToDouble(SBooleanValue::baseRate).average().orElse(.5));
        double product=1,sumU=0,b=0,d=0,a=0; for(var o:os){product*=o.uncertainty;sumU+=o.uncertainty;}
        double denominator=0; for(var o:os){double w=product/o.uncertainty;denominator+=w;b+=w*o.belief*o.certainty();d+=w*o.disbelief*o.certainty();a+=o.baseRate*o.certainty();}
        double u=(os.size()-sumU)*product/ (denominator-os.size()*product);
        return new SBooleanValue(b/(denominator-os.size()*product),d/(denominator-os.size()*product),u,a/(os.size()-sumU));
    }
    private static SBooleanValue dogmaticFusion(List<SBooleanValue> opinions, double baseRate) {
        double total=opinions.stream().mapToDouble(SBooleanValue::relativeWeight).sum();
        if (total == 0) throw new IllegalArgumentException("dogmatic fusion requires positive relative weight");
        double b=opinions.stream().mapToDouble(o->o.belief*o.relativeWeight()).sum()/total;
        double d=opinions.stream().mapToDouble(o->o.disbelief*o.relativeWeight()).sum()/total;
        return new SBooleanValue(b,d,0,baseRate,total);
    }
    public static SBooleanValue consensusAndCompromiseFusion(Collection<SBooleanValue> opinions) {
        requireTwo(opinions,"consensus and compromise fusion"); List<SBooleanValue> os=List.copyOf(opinions);
        double base=os.get(0).baseRate; for(var o:os)if(o.baseRate!=base)throw new IllegalArgumentException("CCF requires equal base rates");
        double cb=os.stream().mapToDouble(SBooleanValue::belief).min().orElse(0), cd=os.stream().mapToDouble(SBooleanValue::disbelief).min().orElse(0), product=1;
        for(var o:os)product*=o.uncertainty;
        double rb=0,rd=0,rx=0; List<Double> br=new ArrayList<>(),dr=new ArrayList<>(),ur=new ArrayList<>();
        for(var o:os){br.add(Math.max(o.belief-cb,0));dr.add(Math.max(o.disbelief-cd,0));ur.add(o.uncertainty);double w=o.uncertainty==0?0:product/o.uncertainty;rb+=br.get(br.size()-1)*w;rd+=dr.get(dr.size()-1)*w;}
        for(List<Domain> p:domainOptions(os.size())){
            Domain intersection=Domain.DOMAIN, union=Domain.NIL;
            for(Domain x:p){intersection=intersection.intersect(x);union=union.union(x);}
            if(intersection==Domain.TRUE) rb+=productFor(p,br,dr,true);
            else if(intersection==Domain.FALSE) rd+=productFor(p,br,dr,false);
            if(intersection==Domain.NIL) {
                if(union==Domain.DOMAIN) rx+=productFor(p,br,dr,false);
                else if(union==Domain.TRUE) rb+=productFor(p,br,dr,false);
                else if(union==Domain.FALSE) rd+=productFor(p,br,dr,false);
            }
        }
        double compromiseMass=rb+rd+rx, norm=compromiseMass==0?1:(1-cb-cd-product)/compromiseMass;
        double b=cb+norm*rb,d=cd+norm*rd;return new SBooleanValue(b,d,1-b-d,base);
    }
    private enum Domain { NIL, TRUE, FALSE, DOMAIN;
        Domain intersect(Domain x){if(this==NIL||x==NIL)return NIL;if(this==DOMAIN)return x;if(x==DOMAIN)return this;return this==x?this:NIL;}
        Domain union(Domain x){if(this==DOMAIN||x==DOMAIN)return DOMAIN;if(this==NIL)return x;if(x==NIL)return this;return this==x?this:DOMAIN;}
    }
    private static double productFor(List<Domain> p,List<Double> br,List<Double> dr,boolean intersectionMass){
        double prod=1; for(int i=0;i<p.size();i++){switch(p.get(i)){case TRUE->prod*=br.get(i);case FALSE->prod*=dr.get(i);case NIL,DOMAIN-> { return 0; }}}
        return prod;
    }
    private static List<List<Domain>> domainOptions(int n){List<List<Domain>> r=new ArrayList<>();if(n==0){r.add(new ArrayList<>());return r;}for(var p:domainOptions(n-1))for(var d:Domain.values()){List<Domain> q=new ArrayList<>(p);q.add(d);r.add(q);}return r;}
    private static void requireNonEmpty(Collection<SBooleanValue> os,String n){ if(os==null||os.isEmpty()||os.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException(n+" requires non-null opinions"); }
    private static void requireTwo(Collection<SBooleanValue> os,String n){ requireNonEmpty(os,n); if(os.size()<2)throw new IllegalArgumentException(n+" requires at least two opinions"); }
    @Override public UBooleanValue uEquals(Value other) { return other instanceof SBooleanValue o ? equivalent(o).toUBoolean() : UBooleanValue.FALSE; }
    @Override public UBooleanValue uDistinct(Value other) { return other instanceof SBooleanValue o ? xor(o).toUBoolean() : UBooleanValue.TRUE; }
    @Override public boolean equals(Object o) { return o instanceof SBooleanValue x && Double.compare(belief,x.belief)==0&&Double.compare(disbelief,x.disbelief)==0&&Double.compare(uncertainty,x.uncertainty)==0&&Double.compare(baseRate,x.baseRate)==0; }
    @Override public int hashCode() { return Objects.hash(belief,disbelief,uncertainty,baseRate); }
    /** Stable representation order only; it is not a subjective-logic preference relation. */
    @Override public int compareTo(Value o) { if(o instanceof UndefinedValue)return 1; if(o instanceof SBooleanValue x){int c=Double.compare(projection(),x.projection());if(c!=0)return c;c=Double.compare(belief,x.belief);if(c!=0)return c;c=Double.compare(disbelief,x.disbelief);if(c!=0)return c;return Double.compare(baseRate,x.baseRate);} return toString().compareTo(o.toString()); }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("SBoolean(").append(round(belief,3)).append(", ").append(round(disbelief,3)).append(", ").append(round(uncertainty,3)).append(", ").append(round(baseRate,3)).append(')'); }
    private static double round(double value,int places){double scale=Math.pow(10,places);return Math.round(value*scale)/scale;}
}
