

/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUReal.java.
 *
 * Semantics unchanged. The only edit is the import of the vendored uncertainty datatypes,
 * relocated from `uDataTypes` to org.tzi.use.uncertainty.datatypes (B1).
 */
package org.tzi.use.uml.ocl.expr.operations;

import org.tzi.use.uncertainty.datatypes.UReal;
import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;

public class StandardOperationsUReal {


    public static void registerTypeOperations(Multimap<String, OpGeneric> opmap) {
        // operations of UReal
        OpGeneric.registerOperation(new Op_ureal_abs(), opmap);
        OpGeneric.registerOperation(new Op_ureal_sin(), opmap);
        OpGeneric.registerOperation(new Op_ureal_cos(), opmap);
        OpGeneric.registerOperation(new Op_ureal_tan(), opmap);
        OpGeneric.registerOperation(new Op_ureal_asin(), opmap);
        OpGeneric.registerOperation(new Op_ureal_acos(), opmap);
        OpGeneric.registerOperation(new Op_ureal_atan(), opmap);
        OpGeneric.registerOperation(new Op_ureal_uncertainty(), opmap);
        OpGeneric.registerOperation(new Op_ureal_setUncertainty(), opmap);
        OpGeneric.registerOperation(new Op_ureal_value(), opmap);
        OpGeneric.registerOperation(new Op_ureal_setValue(), opmap);
        OpGeneric.registerOperation(new Op_ureal_neg(), opmap);
        OpGeneric.registerOperation(new Op_ureal_power(), opmap);
        OpGeneric.registerOperation(new Op_ureal_sqrt(), opmap);
        OpGeneric.registerOperation(new Op_ureal_inv(), opmap);
        OpGeneric.registerOperation(new Op_ureal_toReal(), opmap);
        OpGeneric.registerOperation(new Op_ureal_toInteger(), opmap);
        OpGeneric.registerOperation(new Op_ureal_toUInteger(), opmap);
    }

}

// --------------------------------------------------------

/* abs : UReal -> UReal */
final class Op_ureal_abs extends OpGeneric {
    @Override
    public String name() {
        return "abs";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.abs();
    }
}



// --------------------------------------------------------

/* inv : UReal -> UReal */
final class Op_ureal_inv extends OpGeneric {
    @Override
    public String name() {
        return "inv";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = (URealValue) args[0];
        URealValue result = uRealValue.inverse();

        // make special values resulting in undefined
        if (Double.isInfinite(result.value()) || Double.isNaN(result.value()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* uncertainty : UReal -> Real */
final class Op_ureal_uncertainty extends OpGeneric {
    @Override
    public String name() {
        return "uncertainty";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return new RealValue(uRealValue.uncertainty());
    }
}

// --------------------------------------------------------

/* setUncertainty : UReal x (Integer + Real) -> UReal */
final class Op_ureal_setUncertainty extends OpGeneric {
    @Override
    public String name() {
        return "setUncertainty";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        Type expected = null;

        if (params.length == 2 && params[0].isTypeOfUReal() &&
                params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID))
            expected = TypeFactory.mkUReal();

        return expected;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        Value result = null;
        double newUncertainty;

        if (args[1].isUndefined())
            result = UndefinedValue.instance;
        else {

            if (args[1].isInteger())
                newUncertainty = ((IntegerValue) args[1]).value();
            else
                newUncertainty = ((RealValue) args[1]).value();

            result = new URealValue(uRealValue.value(), newUncertainty);
        }

        return result;
    }
}


// --------------------------------------------------------

/* - : UReal -> UReal */
final class Op_ureal_neg extends OpGeneric {
    @Override
    public String name() {
        return "neg";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.neg();
    }
}

// --------------------------------------------------------

/* value : UReal -> Real */
final class Op_ureal_value extends OpGeneric {
    @Override
    public String name() {
        return "value";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return new RealValue(uRealValue.value());
    }
}

// --------------------------------------------------------

/* setValue : UReal -> UReal */
final class Op_ureal_setValue extends OpGeneric {
    @Override
    public String name() {
        return "setValue";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 2 && params[0].isTypeOfUReal() &&
                params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)) ?
                TypeFactory.mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        double newValue = 0;
        Value result = null;

        if (!args[1].isUndefined()) {
            if (args[1].isInteger())
                newValue = ((IntegerValue) args[1]).value();
            else if (args[1].isReal())
                newValue = ((RealValue) args[1]).value();

            result = new URealValue(newValue, uRealValue.uncertainty());
        }
        else
            result = UndefinedValue.instance;

        return result;
    }
}

// --------------------------------------------------------

/* toReal : UReal -> Real */
final class Op_ureal_toReal extends OpGeneric {
    @Override
    public String name() {
        return "toReal";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.toReal();
    }
}

// --------------------------------------------------------

/* toInteger : UReal -> Integer */
final class Op_ureal_toInteger extends OpGeneric {
    @Override
    public String name() {
        return "toInteger";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.toInteger();
    }
}


// --------------------------------------------------------

/**
 * {@code toUInteger : UReal -> UInteger}. Delegates to {@link URealValue#toUInteger()}, which
 * truncates the value component toward zero and carries the uncertainty component over unchanged.
 *
 * @implNote {@code URealValue.toUInteger()} deliberately does <strong>not</strong> call the backing
 *     library's {@code UReal.toUInteger()}. The library instead floors the value and inflates the
 *     uncertainty by the discarded fractional residue ({@code u' = sqrt(u^2 + (x -
 *     floor(x))^2)}). The two disagree on every non-integral negative value: this operation gives
 *     {@code UReal(-5.3, 3.75).toUInteger() -> UInteger(-5, 3.75)} (truncation, uncertainty
 *     untouched), while the library's own method would give {@code UInteger(-6, ...)} (floor, with
 *     the uncertainty inflated). <strong>Do not "fix" this by routing through the library method —
 *     it would break the historical oracle</strong>: the golden {@code URealExpression.in} file and
 *     {@code URealExpOpsTest} both pin the truncating behavior above. Note also that {@link
 *     Op_ureal_toInteger} (op #9, {@code UReal -> Integer}) floors rather than truncates, so the two
 *     conversions disagree with <em>each other</em> on negative non-integral receivers — a preserved
 *     inconsistency, not an oversight to reconcile.
 * @see "docs/port2/specification.md &sect;2.2 UReal op #10 toUInteger &mdash; deviation ledger"
 */
final class Op_ureal_toUInteger extends OpGeneric {
    @Override
    public String name() {
        return "toUInteger";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.toUInteger();
    }
}

// --------------------------------------------------------

/* power : UReal x Integer -> UReal */
/* power : UReal x Real -> UReal */
/**
 * {@code power(s) : UReal x (Integer|Real) -> UReal}. Result value is {@code x^s}; result
 * uncertainty is the first-order propagation {@code |s * u * x^(s-1)|}. The exponent is narrowed
 * to {@code float} before reaching the backing library, regardless of whether the OCL argument was
 * {@code Integer} or {@code Real}.
 *
 * @implNote The {@code x == 0, s > 0} case looks like it collapses to a single rule from a few
 *     {@code s > 1} examples, but it does not: {@code u' = s * u * Math.pow(0, s - 1)} splits into
 *     three genuinely different sub-cases.
 *     <ul>
 *       <li>{@code s > 1}: {@code Math.pow(0, s - 1) == 0}, so {@code u' == 0} &rarr;
 *           {@code UReal(0.0, 0.0)}.
 *       <li>{@code s == 1}: {@code Math.pow(0, 0) == 1.0} in Java, so {@code u' == u} &rarr;
 *           {@code UReal(0.0, u)} — <strong>not</strong> {@code (0.0, 0.0)}. This sub-case is
 *           pinned by no historical oracle at all (neither the golden {@code .in} file nor
 *           {@code URealExpOpsTest} exercise {@code s == 1} at {@code x == 0}).
 *       <li>{@code 0 < s < 1}: {@code Math.pow(0, s - 1) == +Infinity}, so {@code u'} is
 *           {@code +Infinity} ({@code u > 0}) or {@code NaN} ({@code u == 0}); either way the
 *           second guard below fires and the result is {@code Undefined}, not {@code (0.0, 0.0)}.
 *     </ul>
 *     The {@code 0 < s < 1} sub-case is independently corroborated by the historical oracle:
 *     {@code UReal(0,5).power(1/2).equals(UReal(0,5).sqrt())} evaluates to {@code true} only
 *     because <em>both</em> sides evaluate to {@code Undefined}, not because the two sides agree
 *     on a defined value.
 * @see "docs/port2/specification.md &sect;2.2 UReal op #11 power, &quot;Extractor / refuter
 *     disagreements&quot; row 1 &mdash; deviation ledger"
 */
final class Op_ureal_power extends OpGeneric {
    @Override
    public String name() {
        return "power";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        Type expected = null;

        if (params.length == 2 && ( params[0].isTypeOfUReal() ) &&
                (params[1].isTypeOfInteger() || params[1].isTypeOfReal()))
            expected = TypeFactory.mkUReal();

        return expected;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        URealValue result = null;
        float exponent;

        if (args[1].isInteger())
            exponent = ((IntegerValue) args[1]).value();
        else // Real
            exponent = (float) ((RealValue) args[1]).value();

        result = uRealValue.power(exponent);

        if (Double.isNaN(result.value()) || Double.isInfinite(result.value()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}



// --------------------------------------------------------

/**
 * {@code sqrt : UReal -> UReal}. Result is {@code (sqrt(x), u / (2 * sqrt(x)))}, with an explicit
 * {@code x == 0 && u == 0 -> (0.0, 0.0)} special case handled inside the backing library.
 *
 * @implNote The two NaN/Infinite guards in {@link #eval} are not symmetric the way they look: the
 *     first tests {@code isNaN(result.value()) || isInfinite(result.uncertainty())} rather than
 *     {@code isInfinite(result.value())} — a copy/paste slip against the second guard, which
 *     repeats the uncertainty test verbatim. The value component's infinity is therefore never
 *     directly checked. This is harmless in practice, not just in the cases this file's tests
 *     happen to hit: for any finite receiver value, {@code sqrt(x)} is itself finite or NaN (NaN
 *     is already caught by the first guard's first disjunct), so an infinite <em>value</em> paired
 *     with a finite uncertainty cannot arise here — the missing check is a latent defect, not an
 *     observable one. Net behavior: {@code Undefined} iff the value is NaN ({@code x < 0}) or the
 *     uncertainty is NaN/Infinite ({@code x == 0} with {@code u > 0}, since {@code u / (2 * 0)} is
 *     {@code +Infinity}).
 * @see "docs/port2/specification.md &sect;2.2 UReal op #12 sqrt &mdash; deviation ledger"
 */
final class Op_ureal_sqrt extends OpGeneric {
    @Override
    public String name() {
        return "sqrt";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        URealValue result = uRealValue.sqrt();

        if (Double.isNaN(result.value()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* atan : UReal -> UReal */
final class Op_ureal_atan extends OpGeneric {
    @Override
    public String name() {
        return "atan";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        URealValue result = uRealValue.atan();

        if (Double.isNaN(result.value()) || Double.isInfinite(result.value()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* sin : UReal -> UReal */
final class Op_ureal_sin extends OpGeneric {
    @Override
    public String name() {
        return "sin";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.sin();
    }
}


/* cos : UReal -> UReal */
final class Op_ureal_cos extends OpGeneric {
    @Override
    public String name() {
        return "cos";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        return uRealValue.cos();
    }
}

// --------------------------------------------------------

/**
 * {@code tan : UReal -> UReal}. Delegates to {@code UReal.tan()}, which is implemented as
 * {@code this.sin().divideBy(this.cos())} rather than a closed-form derivative.
 *
 * @implNote This routing through {@code sin}/{@code cos}/{@code divideBy} is load-bearing, not
 *     incidental — a direct {@code u / cos(x)^2} port (the textbook first-order propagation for
 *     {@code tan}) silently disagrees with this operation's actual output. {@code divideBy}
 *     branches on which operand carries nonzero uncertainty, and in the "both operands uncertain"
 *     branch (the general case here, since both {@code sin(x)} and {@code cos(x)} inherit
 *     uncertainty from {@code x}) the formula it computes is
 *     {@code sqrt((u*cos(x))^2 / |cos(x)| + sin(x)^2 * (u*sin(x))^2 / cos(x)^4)} — not
 *     {@code u / cos(x)^2}. The first term's denominator carries {@code |cos(x)|} to the first
 *     power, not squared, which is an artifact of {@code divideBy}'s general-case formula rather
 *     than a textbook derivation. Concretely, {@code tan(0.5, 0.1)} yields {@code u =
 *     0.09831850394390179} via this routing, versus {@code 0.1685} for the textbook formula. Near
 *     the poles ({@code x} close to {@code pi/2 + k*pi}), {@code Math.tan} returns a
 *     finite-but-huge double rather than {@code Infinity}, so the guards below do not fire there
 *     either — {@code tan} near a pole returns an enormous but finite {@code UReal}, not
 *     {@code Undefined}.
 * @see "docs/port2/specification.md &sect;2.2 UReal op #16 tan &mdash; deviation ledger"
 */
final class Op_ureal_tan extends OpGeneric {
    @Override
    public String name() {
        return "tan";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        URealValue result = uRealValue.tan();

        // FIXME: refractorize after studing better the case.
        if (Double.isNaN(result.value()) || Double.isInfinite(result.value()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* asin : UReal -> UReal */
final class Op_ureal_asin extends OpGeneric {
    @Override
    public String name() {
        return "asin";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        URealValue result = uRealValue.asin();

        if (Double.isNaN(result.value()) || Double.isInfinite(result.value()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* acos : UReal -> UReal */
final class Op_ureal_acos extends OpGeneric {
    @Override
    public String name() {
        return "acos";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    @Override
    public Type matches(Type params[]) {
        return (params.length == 1 && params[0].isTypeOfUReal()) ? TypeFactory
                .mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        URealValue uRealValue = URealValue.valueOf(args[0]);
        URealValue result = uRealValue.acos();

        if (Double.isNaN(result.value()) || Double.isInfinite(result.value()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}
