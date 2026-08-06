package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.TypeFactory;

/** A real representative value with non-negative absolute uncertainty. */
public final class URealValue extends UncertainValue {
    private final double value;
    private final double uncertainty;
    public URealValue(double value, double uncertainty) {
        super(TypeFactory.mkUReal());
        if (!Double.isFinite(value) || !Double.isFinite(uncertainty))
            throw new IllegalArgumentException("UReal value and uncertainty must be finite");
        // Historical uDataTypes.UReal normalizes uncertainty through setU(),
        // so negative literal/setUncertainty arguments are accepted.
        this.value = value; this.uncertainty = Math.abs(uncertainty);
    }
    public double value() { return value; }
    public double uncertainty() { return uncertainty; }
    @Override public boolean isUReal() { return true; }
    public URealValue add(URealValue o) { return new URealValue(value + o.value, Math.hypot(uncertainty, o.uncertainty)); }
    public URealValue subtract(URealValue o) { return new URealValue(value - o.value, this == o ? 0 : Math.hypot(uncertainty, o.uncertainty)); }
    public URealValue multiply(URealValue o) {
        return new URealValue(value * o.value, Math.sqrt(o.value*o.value*uncertainty*uncertainty + value*value*o.uncertainty*o.uncertainty));
    }
    public URealValue divide(URealValue o) {
        if (this == o) return new URealValue(1, 0);
        if (o.uncertainty == 0) return new URealValue(value/o.value, Math.abs(uncertainty/o.value));
        if (uncertainty == 0) return new URealValue(value/o.value, o.uncertainty/(o.value*o.value));
        double c=(uncertainty*uncertainty)/Math.abs(o.value);
        double d=(value*value*o.uncertainty*o.uncertainty)/(o.value*o.value*o.value*o.value);
        return new URealValue(value/o.value, Math.sqrt(c+d));
    }
    public URealValue negate() { return new URealValue(-value, uncertainty); }
    public URealValue abs() { return new URealValue(Math.abs(value), uncertainty); }
    /** Historical min/max select the opinion whose probabilistic comparison wins. */
    public URealValue min(URealValue o) { return o.lessThan(this).toBoolean().value() ? new URealValue(o.value, o.uncertainty) : new URealValue(value, uncertainty); }
    public URealValue max(URealValue o) { return o.greaterThan(this).toBoolean().value() ? new URealValue(o.value, o.uncertainty) : new URealValue(value, uncertainty); }
    public URealValue inverse() { return new URealValue(1,0).divide(this); }
    public URealValue power(double exponent) { double v=Math.pow(value,exponent); double u=Math.abs(exponent*uncertainty*Math.pow(value,exponent-1)); if(!Double.isFinite(v)||!Double.isFinite(u)) throw new ArithmeticException("invalid power"); return new URealValue(v,u); }
    public URealValue sqrt() { if(value==0 && uncertainty==0) return new URealValue(0,0); if(value<0) throw new ArithmeticException("sqrt domain"); return new URealValue(Math.sqrt(value), uncertainty/(2*Math.sqrt(value))); }
    public URealValue floorValue() { return new URealValue(Math.floor(value),uncertainty); }
    public URealValue roundValue() { return new URealValue(Math.round(value),uncertainty); }
    public URealValue sin() { return new URealValue(Math.sin(value),Math.abs(Math.cos(value))*uncertainty); }
    public URealValue cos() { return new URealValue(Math.cos(value),Math.abs(Math.sin(value))*uncertainty); }
    public URealValue tan() { return sin().divide(cos()); }
    public URealValue asin() { return new URealValue(Math.asin(value),Math.abs(value)==1 ? uncertainty : uncertainty/Math.sqrt(1-value*value)); }
    public URealValue acos() { return new URealValue(Math.acos(value),Math.abs(value)==1 ? uncertainty : uncertainty/Math.sqrt(1-value*value)); }
    public URealValue atan() { return new URealValue(Math.atan(value),uncertainty/(1+value*value)); }
    public RealValue toReal() { return new RealValue(value); }
    public IntegerValue toInteger() { return IntegerValue.valueOf((int)Math.floor(value)); }
    public UIntegerValue toUInteger() { int i=(int)Math.floor(value); return new UIntegerValue(i,Math.hypot(uncertainty,value-i)); }
    public UBooleanValue lessThan(URealValue o) { return UBooleanValue.probability(true, calculate(o).lt); }
    public UBooleanValue greaterThan(URealValue o) { return UBooleanValue.probability(true, calculate(o).gt); }
    private static final class Comparison {
        double lt, eq, gt;
        Comparison(double lt,double eq,double gt){this.lt=lt;this.eq=eq;this.gt=gt;}
        Comparison swapped(){return new Comparison(gt,eq,lt);}
    }
    /** Historical Gaussian comparison algorithm used by UReal/UInteger. */
    private Comparison calculate(URealValue other) {
        double m1,m2,s1,s2; boolean swap=false;
        if (value<=other.value) { m1=value; m2=other.value; s1=uncertainty; s2=other.uncertainty; }
        else { m1=other.value; m2=value; s1=other.uncertainty; s2=uncertainty; swap=true; }
        Comparison r;
        if (s1==0 && s2==0) {
            r=m1==m2?new Comparison(0,1,0):m1<m2?new Comparison(1,0,0):new Comparison(0,0,1);
            return swap?r.swapped():r;
        }
        if (s1==0) { r=new Comparison(1-cndf(m1,m2,s2),0,cndf(m1,m2,s2)); return swap?r.swapped():r; }
        if (s2==0) { r=new Comparison(cndf(m2,m1,s1),0,1-cndf(m2,m1,s1)); return swap?r.swapped():r; }
        if (s1==s2) {
            double crossing=(m1+m2)/2;
            r=new Comparison(cndf(crossing,m1,s1)-cndf(crossing,m2,s2),0,0);
            r.eq=1-r.lt;
            return swap?r.swapped():r;
        }
        double rad=(m1-m2)*(m1-m2)-2*(s1*s1-s2*s2)*Math.log(s2/s1);
        if (rad<0 || !Double.isFinite(rad)) {
            double p=cndf((m2-m1)/Math.hypot(s1,s2));
            r=new Comparison(p,0,1-p);
            return swap?r.swapped():r;
        }
        double root=s1*s2*Math.sqrt(rad);
        double crossing1=-(-m2*s1*s1+m1*s2*s2+root)/(s1*s1-s2*s2);
        double crossing2=(m2*s1*s1-m1*s2*s2+root)/(s1*s1-s2*s2);
        double c1=Math.min(crossing1,crossing2), c2=Math.max(crossing1,crossing2);
        if (s1<s2) {
            r=new Comparison(1-cndf(c2,m2,s2)-(1-cndf(c2,m1,s1)),0, cndf(c1,m2,s2)-cndf(c1,m1,s1));
            r.eq=1-r.lt-r.gt;
        } else {
            r=new Comparison(cndf(c1,m1,s1)-cndf(c1,m2,s2),0,1-cndf(c2,m1,s1)-(1-cndf(c2,m2,s2)));
            r.eq=1-r.lt-r.gt;
        }
        return swap?r.swapped():r;
    }
    private static double cndf(double x) {
        int neg=x<0?1:0; if(neg==1)x=-x; double k=1/(1+0.2316419*x);
        double y=((((1.330274429*k-1.821255978)*k+1.781477937)*k-0.356563782)*k+0.319381530)*k;
        y=1-0.398942280401*Math.exp(-0.5*x*x)*y; return neg==0?y:1-y;
    }
    private static double cndf(double x,double mean,double sigma){return cndf((x-mean)/sigma);}
    @Override public UBooleanValue uEquals(Value other) {
        if (other instanceof IntegerValue i) return uEquals(new URealValue(i.value(), 0));
        if (other instanceof RealValue r) return uEquals(new URealValue(r.value(), 0));
        if (other instanceof UIntegerValue i) return uEquals(i.toUReal());
        if (!(other instanceof URealValue o)) return UBooleanValue.FALSE;
        return UBooleanValue.probability(true, calculate(o).eq);
    }
    @Override public boolean equals(Object o) {
        if(o instanceof URealValue x) return round(value,10)==round(x.value,10)&&round(uncertainty,10)==round(x.uncertainty,10);
        if(o instanceof IntegerValue x) return value==x.value()&&uncertainty==0;
        if(o instanceof RealValue x) return value==x.value()&&uncertainty==0;
        if(o instanceof UIntegerValue x) return equals(x.toUReal());
        return false;
    }
    @Override public int hashCode() { return java.util.Objects.hash(round(value,10), round(uncertainty,10)); }
    @Override public int compareTo(Value o) {
        if (o instanceof UndefinedValue) return 1;
        if (o instanceof URealValue || o instanceof UIntegerValue || o instanceof IntegerValue || o instanceof RealValue) {
            URealValue other = o instanceof URealValue x ? x : o instanceof UIntegerValue x ? x.toUReal() : o instanceof IntegerValue x ? new URealValue(x.value(),0) : new URealValue(((RealValue)o).value(),0);
            if (uEquals(other).toBoolean().value()) return 0;
            return lessThan(other).toBoolean().value() ? -1 : 1;
        }
        return 0;
    }
    @Override public StringBuilder toString(StringBuilder b) { return b.append("UReal(").append(value==0?0:round(value,10)).append(", ").append(round(uncertainty,10)).append(')'); }
    private static double round(double value,int places){double scale=Math.pow(10,places);return Math.round(value*scale)/scale;}
}
