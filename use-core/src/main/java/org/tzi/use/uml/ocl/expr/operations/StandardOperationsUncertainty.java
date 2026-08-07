package org.tzi.use.uml.ocl.expr.operations;

import com.google.common.collect.Multimap;
import java.util.*;
import java.util.function.Function;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.ocl.value.*;

/** Native registrations for the uncertainty built-ins. */
public final class StandardOperationsUncertainty {
    private StandardOperationsUncertainty() { }
    public static void registerTypeOperations(Multimap<String, OpGeneric> map) {
        // UBoolean algebra and accessors
        // Canonical form: the carried Boolean of a UBoolean is always true.
        unary(map,"value", Type::isTypeOfUBoolean, TypeFactory.mkBoolean(), a -> BooleanValue.get(((UBooleanValue)a[0]).value()));
        unary(map,"confidence", Type::isTypeOfUBoolean, TypeFactory.mkReal(), a -> new RealValue(((UBooleanValue)a[0]).confidence()));
        // The historical toString operation renders the more likely side,
        // unlike the canonical rendering of the value itself.
        unary(map,"toString", Type::isTypeOfUBoolean, TypeFactory.mkString(), a -> {
            double p=((UBooleanValue)a[0]).probability();
            return new StringValue(p<.5 ? "UBoolean(false, "+(1-p)+")" : "UBoolean(true, "+p+")");
        });
        binary(map,"setValue", (t,h)->t.isTypeOfUBoolean(), (t,h)->t.isTypeOfBoolean(), TypeFactory.mkUBoolean(), a -> ((UBooleanValue)a[0]).withValue(((BooleanValue)a[1]).value()), false);
        binary(map,"setConfidence", (t,h)->t.isTypeOfUBoolean(), Type::isKindOfReal, TypeFactory.mkUBoolean(), a -> ((UBooleanValue)a[0]).withConfidence(real(a[1])), false);
        unary(map,"toBoolean", Type::isTypeOfUBoolean, TypeFactory.mkBoolean(), a -> ((UBooleanValue)a[0]).toBoolean());
        binary(map,"toBooleanC", (t,h)->t.isTypeOfUBoolean(), Type::isKindOfReal, TypeFactory.mkBoolean(), a -> { double c=real(a[1]); return c<0||c>1?UndefinedValue.instance:ub(a[0]).toBooleanC(c); }, false);
        ternary(map,"equalsC", (t,h)->t.isTypeOfUBoolean(), (t,h)->t.isKindOfUBoolean(h), Type::isKindOfReal, TypeFactory.mkBoolean(), a -> { double c=real(a[2]); return c<0||c>1?UndefinedValue.instance:ub(a[0]).equalsC(ub(a[1]),c); });
        unary(map,"not", Type::isTypeOfUBoolean, TypeFactory.mkUBoolean(), a -> ((UBooleanValue)a[0]).not());
        uLogical(map,"and", UBooleanValue::and, 0);
        uLogical(map,"or", UBooleanValue::or, 1);
        uLogical(map,"xor", UBooleanValue::xor, -1);
        // equivalent is the one historical logical operator that also applies to
        // two plain Booleans, in which case the result is a plain Boolean.
        map.put("equivalent", new BooleanOperation(){
            public String name(){return "equivalent";}
            public boolean isInfixOrPrefix(){return false;}
            public Type matches(Type[] x){
                if (x.length!=2 || !x[0].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID)
                        || !x[1].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID)) return null;
                return x[0].isTypeOfBoolean() && x[1].isTypeOfBoolean()
                    ? TypeFactory.mkBoolean() : TypeFactory.mkUBoolean();
            }
            public Value evalWithArgs(EvalContext c,org.tzi.use.uml.ocl.expr.Expression[] args){
                Value left=args[0].eval(c), right=args[1].eval(c);
                if (!left.isDefined() || !right.isDefined()) return UndefinedValue.instance;
                UBooleanValue result=ub(left).equivalent(ub(right));
                return left.isBoolean() && right.isBoolean() ? result.toBoolean() : result;
            }
        });
        // implies absorbs from either side: a false antecedent makes it certainly
        // true, and a certainly true consequent does the same
        map.put("implies", new BooleanOperation(){
            public String name(){return "implies";}
            public boolean isInfixOrPrefix(){return true;}
            public Type matches(Type[] x){
                return x.length==2
                    && x[0].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID)
                    && x[1].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID)
                    && (x[0].isTypeOfUBoolean()||x[1].isTypeOfUBoolean())
                    ? TypeFactory.mkUBoolean() : null;
            }
            public Value evalWithArgs(EvalContext c,org.tzi.use.uml.ocl.expr.Expression[] args){
                Value left=args[0].eval(c);
                if (left.isDefined()) {
                    UBooleanValue l=ub(left);
                    if (l.probability()==0) return UBooleanValue.TRUE;
                    Value right=args[1].eval(c);
                    return right.isDefined() ? l.implies(ub(right)) : UndefinedValue.instance;
                }
                Value right=args[1].eval(c);
                if (right.isDefined()) {
                    UBooleanValue r=ub(right);
                    if (r.probability()==1) return r;
                }
                return UndefinedValue.instance;
            }
        });

        // Uncertain numeric accessors
        unary(map,"value", Type::isTypeOfUReal, TypeFactory.mkReal(), a -> ((URealValue)a[0]).toReal());
        unary(map,"uncertainty", Type::isTypeOfUReal, TypeFactory.mkReal(), a -> new RealValue(((URealValue)a[0]).uncertainty()));
        unary(map,"toReal", Type::isTypeOfUReal, TypeFactory.mkReal(), a -> ((URealValue)a[0]).toReal());
        unary(map,"toInteger", Type::isTypeOfUReal, TypeFactory.mkInteger(), a -> ((URealValue)a[0]).toInteger());
        unary(map,"toUInteger", Type::isTypeOfUReal, TypeFactory.mkUInteger(), a -> ((URealValue)a[0]).toUInteger());
        unary(map,"abs", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).abs());
        unary(map,"neg", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).negate());
        prefix(map,"-", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).negate());
        unary(map,"inv", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).inverse());
        unary(map,"sqrt", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).sqrt());
        unary(map,"sin", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).sin());
        unary(map,"cos", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).cos());
        unary(map,"tan", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).tan());
        unary(map,"asin", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).asin());
        unary(map,"acos", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).acos());
        unary(map,"atan", Type::isTypeOfUReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).atan());
        binary(map,"power", (t,h)->t.isTypeOfUReal(), Type::isKindOfReal, TypeFactory.mkUReal(), a -> ((URealValue)a[0]).power(real(a[1])), false);
        binary(map,"setValue", (t,h)->t.isTypeOfUReal(), (t,h)->t.isKindOfReal(h), TypeFactory.mkUReal(), a -> new URealValue(real(a[1]),((URealValue)a[0]).uncertainty()), false);
        binary(map,"setUncertainty", (t,h)->t.isTypeOfUReal(), (t,h)->t.isKindOfReal(h), TypeFactory.mkUReal(), a -> new URealValue(((URealValue)a[0]).value(),real(a[1])), false);
        uRealBinary(map,"+", (x,y)->x.add(y));
        uRealBinary(map,"-", (x,y)->x.subtract(y));
        uRealBinary(map,"*", (x,y)->x.multiply(y));
        uRealBinary(map,"/", (x,y)->x.divide(y));
        uRealCompare(map,"<", (x,y)->x.lessThan(y)); uRealCompare(map,">", (x,y)->x.greaterThan(y));
        uRealCompare(map,"<=", (x,y)->x.lessThan(y).or(x.uEquals(y))); uRealCompare(map,">=", (x,y)->x.greaterThan(y).or(x.uEquals(y)));
        uRealMinMax(map,"min", URealValue::min); uRealMinMax(map,"max", URealValue::max);
        unary(map,"toString", Type::isTypeOfUReal, TypeFactory.mkString(), a -> new StringValue(a[0].toString()));

        unary(map,"value", Type::isTypeOfUInteger, TypeFactory.mkInteger(), a -> ((UIntegerValue)a[0]).toInteger());
        unary(map,"uncertainty", Type::isTypeOfUInteger, TypeFactory.mkReal(), a -> new RealValue(((UIntegerValue)a[0]).uncertainty()));
        unary(map,"toUReal", Type::isTypeOfUInteger, TypeFactory.mkUReal(), a -> ((UIntegerValue)a[0]).toUReal());
        unary(map,"toReal", Type::isTypeOfUInteger, TypeFactory.mkReal(), a -> ((UIntegerValue)a[0]).toReal());
        unary(map,"toInteger", Type::isTypeOfUInteger, TypeFactory.mkInteger(), a -> ((UIntegerValue)a[0]).toInteger());
        binary(map,"setValue", (t,h)->t.isTypeOfUInteger(), (t,h)->t.isTypeOfInteger(), TypeFactory.mkUInteger(), a -> new UIntegerValue(((IntegerValue)a[1]).value(),((UIntegerValue)a[0]).uncertainty()), false);
        binary(map,"setUncertainty", (t,h)->t.isTypeOfUInteger(), (t,h)->t.isKindOfReal(h), TypeFactory.mkUInteger(), a -> new UIntegerValue(((UIntegerValue)a[0]).value(),real(a[1])), false);
        unary(map,"abs", Type::isTypeOfUInteger, TypeFactory.mkUInteger(), a -> new UIntegerValue(Math.abs(((UIntegerValue)a[0]).value()),((UIntegerValue)a[0]).uncertainty()));
        unary(map,"neg", Type::isTypeOfUInteger, TypeFactory.mkUInteger(), a -> new UIntegerValue(-((UIntegerValue)a[0]).value(),((UIntegerValue)a[0]).uncertainty()));
        prefix(map,"-", Type::isTypeOfUInteger, TypeFactory.mkUInteger(), a -> new UIntegerValue(-((UIntegerValue)a[0]).value(),((UIntegerValue)a[0]).uncertainty()));
        unary(map,"sqrt", Type::isTypeOfUInteger, TypeFactory.mkUInteger(), a -> ((UIntegerValue)a[0]).sqrt());
        binary(map,"power", (t,h)->t.isTypeOfUInteger(), Type::isKindOfReal, TypeFactory.mkUInteger(), a -> ((UIntegerValue)a[0]).power(real(a[1])), false);
        unary(map,"toString", Type::isTypeOfUInteger, TypeFactory.mkString(), a -> new StringValue(a[0].toString()));
        uIntegerIntegralBinary(map,"div", UIntegerValue::div);
        uIntegerIntegralBinary(map,"mod", UIntegerValue::mod);
        uIntegerBinary(map,"+", TypeFactory.mkUInteger(), UIntegerValue::add);
        uIntegerBinary(map,"-", TypeFactory.mkUInteger(), UIntegerValue::subtract);
        uIntegerBinary(map,"*", TypeFactory.mkUInteger(), UIntegerValue::multiply);
        uIntegerBinary(map,"/", TypeFactory.mkUReal(), UIntegerValue::divide);
        uIntegerCompare(map,"<", (x,y)->x.toUReal().lessThan(y.toUReal()));
        uIntegerCompare(map,">", (x,y)->x.toUReal().greaterThan(y.toUReal()));
        uIntegerCompare(map,"<=", (x,y)->x.toUReal().lessThan(y.toUReal()).or(x.uEquals(y)));
        uIntegerCompare(map,">=", (x,y)->x.toUReal().greaterThan(y.toUReal()).or(x.uEquals(y)));
        uIntegerMinMax(map,"min",(x,y)->x.toUReal().lessThan(y.toUReal()).toBoolean().value()?x:y);
        uIntegerMinMax(map,"max",(x,y)->x.toUReal().greaterThan(y.toUReal()).toBoolean().value()?x:y);

        // UString public surface
        unary(map,"value", Type::isTypeOfUString, TypeFactory.mkString(), a -> ((UStringValue)a[0]).toStringValue());
        unary(map,"confidence", Type::isTypeOfUString, TypeFactory.mkReal(), a -> new RealValue(((UStringValue)a[0]).confidence()));
        // Historical UString toString yields the underlying string.
        unary(map,"toString", Type::isTypeOfUString, TypeFactory.mkString(), a -> ((UStringValue)a[0]).toStringValue());
        binary(map,"setValue", (t,h)->t.isTypeOfUString(), (t,h)->t.isTypeOfString(), TypeFactory.mkUString(), a -> new UStringValue(((StringValue)a[1]).value(),((UStringValue)a[0]).confidence()), false);
        binary(map,"setConfidence", (t,h)->t.isTypeOfUString(), Type::isKindOfReal, TypeFactory.mkUString(), a -> new UStringValue(((UStringValue)a[0]).value(),real(a[1])), false);
        unary(map,"size", Type::isTypeOfUString, TypeFactory.mkUInteger(), a -> ((UStringValue)a[0]).size());
        binary(map,"at", (t,h)->t.isTypeOfUString(), (t,h)->t.isTypeOfInteger(), TypeFactory.mkUString(), a -> ((UStringValue)a[0]).character(((IntegerValue)a[1]).value()), false);
        unary(map,"character", Type::isTypeOfUString, TypeFactory.mkSequence(TypeFactory.mkUString()), a -> ((UStringValue)a[0]).characters());
        binary(map,"indexOf", (t,h)->t.isTypeOfUString(), (t,h)->t.isTypeOfString(), TypeFactory.mkInteger(), a -> ((UStringValue)a[0]).indexOf(((StringValue)a[1]).value()), false);
        ternary(map,"substring", (t,h)->t.isTypeOfUString(), (t,h)->t.isTypeOfInteger(), (t,h)->t.isTypeOfInteger(), TypeFactory.mkUString(), a -> ((UStringValue)a[0]).substring(((IntegerValue)a[1]).value(),((IntegerValue)a[2]).value()));
        unary(map,"toLowerCase", Type::isTypeOfUString, TypeFactory.mkUString(), a -> ((UStringValue)a[0]).lower());
        unary(map,"toUpperCase", Type::isTypeOfUString, TypeFactory.mkUString(), a -> ((UStringValue)a[0]).upper());
        unary(map,"toInteger", Type::isTypeOfUString, TypeFactory.mkInteger(), a -> ((UStringValue)a[0]).toInteger());
        unary(map,"toReal", Type::isTypeOfUString, TypeFactory.mkReal(), a -> ((UStringValue)a[0]).toReal());
        unary(map,"toBoolean", Type::isTypeOfUString, TypeFactory.mkBoolean(), a -> ((UStringValue)a[0]).toBoolean());
        unary(map,"toUBoolean", Type::isTypeOfUString, TypeFactory.mkUBoolean(), a -> ((UStringValue)a[0]).toUBoolean());
        uStringCompare(map,"<",(x,y)->x.lessThan(y)); uStringCompare(map,"<=",(x,y)->x.lessOrEqual(y));
        uStringCompare(map,">",(x,y)->x.greaterThan(y)); uStringCompare(map,">=",(x,y)->x.greaterOrEqual(y));
        map.put("+",new Base("+",TypeFactory.mkUString(),a->us(a[0]).concat(us(a[1])),true){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfUString()||x[1].isTypeOfUString())?r:null;}});

        registerSBoolean(map);
    }
    private static void registerSBoolean(Multimap<String,OpGeneric> map) {
        unary(map,"belief", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).belief()));
        unary(map,"disbelief", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).disbelief()));
        unary(map,"uncertainty", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).uncertainty()));
        unary(map,"baseRate", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).baseRate()));
        unary(map,"projection", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).projection()));
        unary(map,"certainty", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).certainty()));
        unary(map,"toString", Type::isTypeOfSBoolean, TypeFactory.mkString(), a->new StringValue(a[0].toString()));
        unary(map,"toUBoolean", Type::isTypeOfSBoolean, TypeFactory.mkUBoolean(), a->sb(a[0]).toUBoolean());
        unary(map,"not", Type::isTypeOfSBoolean, TypeFactory.mkSBoolean(), a->sb(a[0]).not());
        unary(map,"getRelativeWeight", Type::isTypeOfSBoolean, TypeFactory.mkReal(), a->new RealValue(sb(a[0]).relativeWeight()));
        binary(map,"isCertain", (t,h)->t.isTypeOfSBoolean(), Type::isKindOfReal, TypeFactory.mkBoolean(), a->BooleanValue.get(sb(a[0]).isCertain(real(a[1]))), false);
        binary(map,"isUncertain", (t,h)->t.isTypeOfSBoolean(), Type::isKindOfReal, TypeFactory.mkBoolean(), a->BooleanValue.get(sb(a[0]).isUncertain(real(a[1]))), false);
        unary(map,"uncertaintyMaximized", Type::isTypeOfSBoolean, TypeFactory.mkSBoolean(), a->sb(a[0]).uncertaintyMaximized());
        unary(map,"uncertainOpinion", Type::isTypeOfSBoolean, TypeFactory.mkSBoolean(), a->sb(a[0]).uncertainOpinion());
        unary(map,"isAbsolute", Type::isTypeOfSBoolean, TypeFactory.mkBoolean(), a->BooleanValue.get(sb(a[0]).isAbsolute()));
        unary(map,"isVacuous", Type::isTypeOfSBoolean, TypeFactory.mkBoolean(), a->BooleanValue.get(sb(a[0]).isVacuous()));
        unary(map,"isDogmatic", Type::isTypeOfSBoolean, TypeFactory.mkBoolean(), a->BooleanValue.get(sb(a[0]).isDogmatic()));
        unary(map,"isMaximizedUncertainty", Type::isTypeOfSBoolean, TypeFactory.mkBoolean(), a->BooleanValue.get(sb(a[0]).isMaximizedUncertainty()));
        sbinary(map,"and",(x,y)->x.and(y)); sbinary(map,"or",(x,y)->x.or(y)); sbinary(map,"xor",(x,y)->x.xor(y)); sbinary(map,"equivalent",(x,y)->x.equivalent(y)); sbinary(map,"implies",(x,y)->x.implies(y));
        binary(map,"projectiveDistance",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),TypeFactory.mkReal(),a->new RealValue(sb(a[0]).projectiveDistance(sb(a[1]))),false);
        binary(map,"conjunctiveCertainty",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),TypeFactory.mkReal(),a->new RealValue(sb(a[0]).conjunctiveCertainty(sb(a[1]))),false);
        binary(map,"degreeOfConflict",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),TypeFactory.mkReal(),a->new RealValue(sb(a[0]).degreeOfConflict(sb(a[1]))),false);
        binary(map,"min",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),TypeFactory.mkSBoolean(),a->sb(a[0]).min(sb(a[1])),false);
        binary(map,"max",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),TypeFactory.mkSBoolean(),a->sb(a[0]).max(sb(a[1])),false);
        binary(map,"applyOn",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfUBoolean(),TypeFactory.mkSBoolean(),a->sb(a[0]).applyOn((UBooleanValue)a[1]),false);
        ternary(map,"deduceY",(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isTypeOfSBoolean(),TypeFactory.mkSBoolean(),a->sb(a[0]).deduceY(sb(a[1]),sb(a[2])));
        fusion(map,"minimumBeliefFusion",SBooleanValue::minimumBeliefFusion); fusion(map,"majorityBeliefFusion",SBooleanValue::majorityBeliefFusion);
        fusion(map,"beliefConstraintFusion",SBooleanValue::beliefConstraintFusion); fusion(map,"averageBeliefFusion",SBooleanValue::averageBeliefFusion);
        fusion(map,"aleatoryCumulativeBeliefFusion",SBooleanValue::aleatoryCumulativeBeliefFusion); fusion(map,"epistemicCumulativeBeliefFusion",SBooleanValue::epistemicCumulativeBeliefFusion);
        fusion(map,"weightedBeliefFusion",SBooleanValue::weightedBeliefFusion); fusion(map,"consensusAndCompromiseFusion",SBooleanValue::consensusAndCompromiseFusion);
        fusion(map,"discount", opinions -> { SBooleanValue first=opinions.iterator().next(); return first.discount(new ArrayList<>(opinions).subList(1,opinions.size())); });
    }
    private static void fusion(Multimap<String,OpGeneric> m,String n,Function<Collection<SBooleanValue>,SBooleanValue> f){ binary(m,n,(t,h)->t.isTypeOfSBoolean(),(t,h)->t.isKindOfCollection(h),TypeFactory.mkSBoolean(),a->{List<SBooleanValue>x=new ArrayList<>();x.add(sb(a[0]));for(Value v:(CollectionValue)a[1])x.add(sb(v));return f.apply(x);},false); }
    private interface TypeTest { boolean test(Type t, Type.VoidHandling h); }
    private static void unary(Multimap<String,OpGeneric> m,String n,java.util.function.Predicate<Type> p,Type r,Function<Value[],Value> f){ m.put(n,new Base(n,r,f,false){public Type matches(Type[] x){return x.length==1&&p.test(x[0])?r:null;}}); }
    private static void prefix(Multimap<String,OpGeneric> m,String n,java.util.function.Predicate<Type> p,Type r,Function<Value[],Value> f){ m.put(n,new Base(n,r,f,true){public Type matches(Type[] x){return x.length==1&&p.test(x[0])?r:null;}}); }
    private static void binary(Multimap<String,OpGeneric> m,String n,TypeTest p,TypeTest q,Type r,Function<Value[],Value> f,boolean infix){m.put(n,new Base(n,r,f,infix){public Type matches(Type[] x){return x.length==2&&p.test(x[0],Type.VoidHandling.EXCLUDE_VOID)&&q.test(x[1],Type.VoidHandling.EXCLUDE_VOID)?r:null;}});}
    private static void ternary(Multimap<String,OpGeneric> m,String n,TypeTest p,TypeTest q,TypeTest rtest,Type r,Function<Value[],Value> f){m.put(n,new Base(n,r,f,false){public Type matches(Type[] x){return x.length==3&&p.test(x[0],Type.VoidHandling.EXCLUDE_VOID)&&q.test(x[1],Type.VoidHandling.EXCLUDE_VOID)&&rtest.test(x[2],Type.VoidHandling.EXCLUDE_VOID)?r:null;}});}
    /** Historical numeric dispatch: an uncertain operand whose least common
     *  supertype with the other operand is not UInteger is handled as UReal,
     *  which is what makes mixed pairs such as UInteger + Real yield UReal. */
    private static boolean uncertainRealOperands(Type[] x) {
        if (x.length != 2) return false;
        if (!x[0].isKindOfNumber(Type.VoidHandling.EXCLUDE_VOID) || !x[1].isKindOfNumber(Type.VoidHandling.EXCLUDE_VOID)) return false;
        if (!(x[0] instanceof org.tzi.use.uml.ocl.type.UncertainType || x[1] instanceof org.tzi.use.uml.ocl.type.UncertainType)) return false;
        Type common = x[0].getLeastCommonSupertype(x[1]);
        return common != null && !common.isTypeOfUInteger();
    }
    private static class Base extends OpGeneric { final String n;final Type r;final Function<Value[],Value> f;final boolean infix;Base(String n,Type r,Function<Value[],Value>f,boolean i){this.n=n;this.r=r;this.f=f;this.infix=i;} public String name(){return n;}public int kind(){return OPERATION;}public boolean isInfixOrPrefix(){return infix;}public Type matches(Type[]x){return null;}/** Historical uncertainty operations compute with plain doubles and report a
     *  non-finite outcome as undefined; here the value constructors reject the
     *  non-finite result instead, so that rejection is what maps to undefined. */
    public Value eval(EvalContext c,Value[]a,Type t){ try { return f.apply(a); } catch (ArithmeticException|IllegalArgumentException ex) { return UndefinedValue.instance; } } }
    private interface UBin { UBooleanValue apply(UBooleanValue x,UBooleanValue y); } private interface SBin { SBooleanValue apply(SBooleanValue x,SBooleanValue y); } private interface Cmp { UBooleanValue apply(URealValue x,URealValue y); } private interface UIntegerBin { Value apply(UIntegerValue x,UIntegerValue y); } private interface UIntegerIntegral { UIntegerValue apply(UIntegerValue x,UIntegerValue y); } private interface UIntegerCmp { UBooleanValue apply(UIntegerValue x,UIntegerValue y); }
    private static void uIntegerIntegralBinary(Multimap<String,OpGeneric>m,String n,UIntegerIntegral f){m.put(n,new Base(n,TypeFactory.mkUInteger(),a->f.apply(ui(a[0]),ui(a[1])),true){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfUInteger()||x[1].isTypeOfUInteger())?r:null;}});}
    private static void uIntegerMinMax(Multimap<String,OpGeneric>m,String n,java.util.function.BiFunction<UIntegerValue,UIntegerValue,UIntegerValue> f){m.put(n,new Base(n,TypeFactory.mkUInteger(),a->f.apply(ui(a[0]),ui(a[1])),false){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfUInteger()||x[1].isTypeOfUInteger())?r:null;}});}
    private interface UStringCmp { UBooleanValue apply(UStringValue x,UStringValue y); }
    private static void uRealMinMax(Multimap<String,OpGeneric>m,String n,java.util.function.BiFunction<URealValue,URealValue,URealValue> f){m.put(n,new Base(n,TypeFactory.mkUReal(),a->f.apply(ur(a[0]),ur(a[1])),false){public Type matches(Type[] x){return uncertainRealOperands(x)?r:null;}});}
    /**
     * Registers a historical uncertain logical operator. Undefined operands are
     * absorbed the way the historical implementation absorbs them: {@code and}
     * still yields its zero-probability operand and {@code or} its certain one,
     * so an undefined operand only makes the result undefined when it could
     * still have influenced it. {@code absorbing} is that short-circuit
     * probability, or -1 for the operators that simply propagate undefined.
     */
    private static void uLogical(Multimap<String,OpGeneric>m,String n,UBin f,int absorbing){
        m.put(n,new BooleanOperation(){
            public String name(){return n;}
            public boolean isInfixOrPrefix(){return true;}
            public Type matches(Type[] x){
                return x.length==2
                    && x[0].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID)
                    && x[1].isKindOfUBoolean(Type.VoidHandling.INCLUDE_VOID)
                    && (x[0].isTypeOfUBoolean()||x[1].isTypeOfUBoolean())
                    ? TypeFactory.mkUBoolean() : null;
            }
            public Value evalWithArgs(EvalContext c,org.tzi.use.uml.ocl.expr.Expression[] args){
                Value left=args[0].eval(c), right=args[1].eval(c);
                UBooleanValue l=left.isDefined()?ub(left):null;
                UBooleanValue r=right.isDefined()?ub(right):null;
                if (absorbing>=0) {
                    if (l!=null && l.probability()==absorbing) return l;
                    if (r!=null && r.probability()==absorbing) return r;
                }
                return l==null||r==null ? UndefinedValue.instance : f.apply(l,r);
            }
        });
    } private static void sbinary(Multimap<String,OpGeneric>m,String n,SBin f){m.put(n,new Base(n,TypeFactory.mkSBoolean(),a->f.apply(sb(a[0]),sb(a[1])),true){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfSBoolean(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfSBoolean(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfSBoolean()||x[1].isTypeOfSBoolean())?r:null;}});} private static void uRealBinary(Multimap<String,OpGeneric>m,String n,java.util.function.BiFunction<URealValue,URealValue,URealValue> f){m.put(n,new Base(n,TypeFactory.mkUReal(),a->f.apply(ur(a[0]),ur(a[1])),true){public Type matches(Type[] x){return uncertainRealOperands(x)?r:null;}});} private static void uRealCompare(Multimap<String,OpGeneric>m,String n,Cmp f){m.put(n,new Base(n,TypeFactory.mkUBoolean(),a->f.apply(ur(a[0]),ur(a[1])),true){public Type matches(Type[] x){return uncertainRealOperands(x)?r:null;}});} private static void uIntegerBinary(Multimap<String,OpGeneric>m,String n,Type r,UIntegerBin f){m.put(n,new Base(n,r,a->f.apply(ui(a[0]),ui(a[1])),true){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfUInteger()||x[1].isTypeOfUInteger())?r:null;}});} private static void uIntegerCompare(Multimap<String,OpGeneric>m,String n,UIntegerCmp f){m.put(n,new Base(n,TypeFactory.mkUBoolean(),a->f.apply(ui(a[0]),ui(a[1])),true){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfUInteger()||x[1].isTypeOfUInteger())?r:null;}});} private static void uStringCompare(Multimap<String,OpGeneric>m,String n,UStringCmp f){m.put(n,new Base(n,TypeFactory.mkUBoolean(),a->f.apply(us(a[0]),us(a[1])),true){public Type matches(Type[] x){return x.length==2&&x[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)&&x[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)&&(x[0].isTypeOfUString()||x[1].isTypeOfUString())?r:null;}});}
    private static double real(Value v){return v instanceof IntegerValue i?i.value():((RealValue)v).value();} private static URealValue ur(Value v){if(v instanceof URealValue x)return x;if(v instanceof UIntegerValue x)return x.toUReal();if(v instanceof IntegerValue x)return new URealValue(x.value(),0);return new URealValue(((RealValue)v).value(),0);} private static UIntegerValue ui(Value v){return v instanceof UIntegerValue x?x:new UIntegerValue(((IntegerValue)v).value(),0);} private static UBooleanValue ub(Value v){return v instanceof UBooleanValue x?x:UBooleanValue.valueOf(((BooleanValue)v).value());}private static UStringValue us(Value v){return v instanceof UStringValue x?x:new UStringValue(((StringValue)v).value(),1);}private static SBooleanValue sb(Value v){if(v instanceof SBooleanValue x)return x;if(v instanceof UBooleanValue x)return SBooleanValue.dogmatic(x.confidence(),x.confidence());return ((BooleanValue)v).value()?SBooleanValue.TRUE:SBooleanValue.FALSE;}
}
