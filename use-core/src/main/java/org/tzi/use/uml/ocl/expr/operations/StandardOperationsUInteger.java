

/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.java.
 *
 * Semantics unchanged. The only edit is the import of the vendored uncertainty datatypes,
 * relocated from `uDataTypes` to org.tzi.use.uncertainty.datatypes (B1).
 */
package org.tzi.use.uml.ocl.expr.operations;

import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;

public class StandardOperationsUInteger {

    public static void registerTypeOperations(Multimap<String, OpGeneric> opmap) {
        // operations on UInteger
        OpGeneric.registerOperation(new Op_uInteger_value(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_setUncertainty(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_uncertainty(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_setValue(), opmap);
        OpGeneric.registerOperation("toInteger", new Op_uInteger_value(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_toUReal(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_toReal(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_abs(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_div(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_mod(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_sqrt(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_power(), opmap);
        OpGeneric.registerOperation(new Op_uInteger_neg(), opmap);
    }
}

// --------------------------------------------------------
//
// UInteger operations.
//
// --------------------------------------------------------

// value : UInteger -> Integer
final class Op_uInteger_value extends OpGeneric {

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

    /**
     * Declares the static result type of {@code value}/{@code toInteger} as {@link
     * org.tzi.use.uml.ocl.type.IntegerType Integer}, matching what {@link #eval} actually returns.
     *
     * @implNote The fork declared {@code TypeFactory.mkUInteger()} here while {@code eval} returns an
     *     {@code IntegerValue} (the sibling {@code Op_ureal_value} correctly declares {@code
     *     mkReal()} for the analogous case). Not cosmetic: {@code ExpStdOp.create} stores this
     *     method's return as the expression's <em>static</em> type, so the fork mistyped every
     *     <em>enclosing</em> expression that consumes {@code x.value()}. The printed suffix is
     *     unaffected either way &mdash; {@code Value.toStringWithType} prints the runtime type, not
     *     this one &mdash; so no corpus expectation changes.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-37 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUInteger() ?
                TypeFactory.mkInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];

        return IntegerValue.valueOf(uInteger.value());
    }
}

// setUncertainty : UInteger x (Real + Integer) -> UInteger
final class Op_uInteger_setUncertainty extends OpGeneric {

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
    public Type matches(Type[] params) {
        return params.length == 2 && params[0].isTypeOfUInteger() &&
                params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID) ?
                TypeFactory.mkUInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];
        UIntegerValue result = null;
        double uncertainty;

        if (args[1].isInteger())
            uncertainty = ((IntegerValue) args[1]).value();
        else
            uncertainty = ((RealValue) args[1]).value();

        result = new UIntegerValue(uInteger.value(), uncertainty);

        return result;
    }
}


// uncertainty : UInteger -> Real
final class Op_uInteger_uncertainty extends OpGeneric {

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
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUInteger() ?
                TypeFactory.mkReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];

        return new RealValue(uInteger.uncertainty());
    }
}

// setValue : UInteger x Integer -> UInteger
final class Op_uInteger_setValue extends OpGeneric {

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
    public Type matches(Type[] params) {
        return params.length == 2 && params[0].isTypeOfUInteger() &&
                params[1].isTypeOfInteger() ?
                TypeFactory.mkUInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];
        IntegerValue newValue = (IntegerValue) args[1];

        UIntegerValue result = new UIntegerValue(newValue.value(), uInteger.uncertainty());

        return result;
    }
}
// toUReal : UInteger -> UReal
final class Op_uInteger_toUReal extends OpGeneric {

    @Override
    public String name() {
        return "toUReal";
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
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUInteger() ?
                TypeFactory.mkUReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];

        return new URealValue(uInteger.value(), uInteger.uncertainty());
    }
}

// toReal : UInteger -> Real
final class Op_uInteger_toReal extends OpGeneric {

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
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUInteger() ?
                TypeFactory.mkReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];

        return new RealValue(uInteger.value());
    }
}

// --------------------------------------------------------

/* abs : UInteger -> UInteger */
final class Op_uInteger_abs extends OpGeneric {
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
        return (params.length == 1 && params[0].isTypeOfUInteger()) ? TypeFactory
                .mkUInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        return ((UIntegerValue) args[0]).abs();
    }
}

// div : UInteger x UInteger -> UInteger
// div : UInteger x Integer  -> UInteger
// div : Integer  x UInteger -> UInteger
final class Op_uInteger_div extends OpGeneric {

    @Override
    public String name() {
        return "div";
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
    public Type matches(Type[] params) {
        Type result = null;

        if (params.length == 2 && params[0].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID))
            // Some of them must be UInteger
            if (params[1].isTypeOfUInteger() || params[0].isTypeOfUInteger())
                result = TypeFactory.mkUInteger();

        return result;
    }

    /**
     * Integer-truncating quotient of the two operands' value components, with the uncertainty
     * component rebuilt by a four-branch rule depending on which side, if either, is exact.
     *
     * @implNote Delegates to {@code UIntegerValue.divideBy}, i.e. the vendored
     *     {@code uDataTypes.UInteger.divideBy}. That method selects among:
     *     <ol>
     *       <li><b>Reference identity</b> ({@code r == this}) &rarr; {@code (1, 0.0)}. Reachable
     *           only when the evaluator hands the identical {@code UIntegerValue} instance to
     *           both operands of one call; two separately evaluated equal constants are distinct
     *           objects and take the next branch instead.</li>
     *       <li><b>Exact divisor</b> ({@code r.getU() == 0.0}) &rarr; {@code x' = this.x / r.x}
     *           (int division, truncates toward zero), {@code u' = this.u / r.x}.</li>
     *       <li><b>Exact dividend</b> ({@code this.getU() == 0.0}) &rarr;
     *           {@code x' = this.x / r.x} (int division), {@code u' = r.u / (r.x * r.x)}.</li>
     *       <li><b>Both operands uncertain</b> &rarr; {@code x'} is the int-truncated quotient
     *           widened to {@code double} afterward (still truncating, not a real-valued
     *           division), {@code u' = sqrt(|this.u^2 / r.x| + this.x^2 * r.u^2 / r.x^4)}.</li>
     *     </ol>
     *     A zero divisor makes the int division inside branches 2-4 raise
     *     {@code ArithmeticException}, which {@code ExpStdOp} converts to OCL {@code Undefined};
     *     no explicit zero-divisor guard is needed here because of that upstream conversion. The
     *     stored uncertainty is normalised to its absolute value at construction, so every branch
     *     above yields a non-negative {@code u'} regardless of operand sign.
     * @see "docs/port2/specification.md &sect;2.3 UInteger div/mod branch table &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        return UIntegerValue.valueOf(args[0]).divideBy(args[1]);
    }

}

// mod : UInteger x UInteger -> UInteger
// mod : UInteger x Integer  -> UInteger
// mod : Integer  x UInteger -> UInteger
final class Op_uInteger_mod extends OpGeneric {

    @Override
    public String name() {
        return "mod";
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
    public Type matches(Type[] params) {
        Type result = null;

        if (params.length == 2 && params[0].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUInteger(Type.VoidHandling.EXCLUDE_VOID))
            // Some of them must be UInteger
            if (params[1].isTypeOfUInteger() || params[0].isTypeOfUInteger())
                result = TypeFactory.mkUInteger();

        return result;
    }

    /**
     * Java {@code %} remainder of the two operands' value components (so the result takes the
     * sign of the dividend, not the mathematical modulus), with the uncertainty component built
     * by the same three-way rule {@link Op_uInteger_div} uses.
     *
     * @implNote Delegates to {@code UIntegerValue.mod}, i.e. the vendored
     *     {@code uDataTypes.UInteger.mod}. Its branch structure mirrors {@code divideBy}
     *     exactly &mdash; reference-identity &rarr; {@code (0, 0.0)}; exact divisor
     *     &rarr; {@code (this.x % r.x, this.u / r.x)}; exact dividend
     *     &rarr; {@code (this.x % r.x, r.u / (r.x * r.x))}; both uncertain
     *     &rarr; value {@code (int) Math.floor(this.x % r.x)} with uncertainty
     *     {@code sqrt(|this.u^2 / r.x| + this.x^2 * r.u^2 / r.x^4)} &mdash; and that last formula
     *     is {@code div}'s quadrature formula reused verbatim, not a remainder-specific
     *     derivation; whether that reuse is mathematically intended is not stated anywhere in the
     *     source. As with {@code div}, a zero divisor raises {@code ArithmeticException} from the
     *     {@code %} operator itself, which {@code ExpStdOp} converts to {@code Undefined}, so no
     *     guard is needed here.
     * @see "docs/port2/specification.md &sect;2.3 UInteger div/mod branch table &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        return UIntegerValue.valueOf(args[0]).mod(args[1]);
    }

}

// --------------------------------------------------------

/* sqrt : UInteger -> UInteger */
final class Op_uInteger_sqrt extends OpGeneric {
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
        return (params.length == 1 && params[0].isTypeOfUInteger()) ? TypeFactory
                .mkUInteger() : null;
    }

    /**
     * Floor of the real square root of the value component, with the uncertainty set to the
     * quadrature sum of the linearly propagated spread {@code u/(2*sqrt(x))} and the fractional
     * residue the floor discards.
     *
     * @implNote The two guards below look redundant &mdash; both mention
     *     {@code result.uncertainty()} &mdash; but they are not, and must both be kept verbatim.
     *     {@code result.value()} is an {@code int}, so {@code Double.isNaN(result.value())} (the
     *     first disjunct of the first guard) is always false. Its <em>second</em> disjunct,
     *     {@code Double.isInfinite(result.uncertainty())}, is the one that matters: it is the
     *     only check that fires for a zero receiver value with positive uncertainty, e.g.
     *     {@code UInteger(0, u).sqrt()} with {@code u > 0}, where the linear propagation term
     *     {@code u / (2 * sqrt(x))} divides by zero and yields {@code +Infinity} (not NaN). The
     *     second guard below only ever catches a NaN uncertainty, produced when the receiver's
     *     value is negative and {@code Math.sqrt(x)} is itself NaN. Rewriting the first guard to
     *     test {@code result.value()} in both disjuncts &mdash; the "obviously dead, obviously
     *     symmetric with {@link Op_uInteger_power}'s guard" reading &mdash; would delete the only
     *     check that runs before an infinite-uncertainty result could otherwise escape as
     *     {@code UInteger(0, Infinity)} instead of {@code Undefined}.
     * @see "docs/port2/specification.md &sect;2.3 UInteger sqrt guard &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue uInteger = (UIntegerValue) args[0];
        UIntegerValue result = uInteger.sqrt();

        if (Double.isNaN(result.value()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* power : UInteger x Integer -> UInteger */
/* power : UInteger x Real -> UInteger */
final class Op_uInteger_power extends OpGeneric {
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

        if (params.length == 2 && ( params[0].isTypeOfUInteger() ) &&
                (params[1].isTypeOfInteger() || params[1].isTypeOfReal()))
            expected = TypeFactory.mkUInteger();

        return expected;
    }

    /**
     * Floor of {@code x^s} (the exponent narrowed to {@code float} and treated as exact), with
     * the uncertainty set to the quadrature sum of the linearly propagated spread
     * {@code |s * u * x^(s-1)|} and the fractional residue the floor discards.
     *
     * @implNote Unlike {@link Op_uInteger_sqrt}'s structurally similar first guard, this one is
     *     entirely dead code, not partially dead. Both disjuncts test {@code result.value()},
     *     an {@code int}, so {@code Double.isNaN(result.value())} and
     *     {@code Double.isInfinite(result.value())} are both always false. Every {@code Undefined}
     *     outcome this operation produces &mdash; e.g. {@code UInteger(0, 0).power(0)}, where
     *     {@code s * u * x^(s-1) = 0 * 0 * Infinity = NaN} &mdash; is caught by the second guard,
     *     which tests {@code result.uncertainty()}. Kept verbatim for oracle parity and symmetry
     *     with {@code sqrt}'s guard shape; removing the first guard here would not change
     *     behaviour (contrast {@code sqrt}, where the analogous-looking removal would).
     * @see "docs/port2/specification.md &sect;2.3 UInteger sqrt guard &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UIntegerValue result = ((UIntegerValue) args[0]).power(args[1]);

        if (Double.isNaN(result.value()) || Double.isInfinite(result.value()))
            throw new ArithmeticException();

        if (Double.isNaN(result.uncertainty()) || Double.isInfinite(result.uncertainty()))
            throw new ArithmeticException();

        return result;
    }
}

// --------------------------------------------------------

/* neg : UInteger -> UInteger */
final class Op_uInteger_neg extends OpGeneric {
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
        return (params.length == 1 && params[0].isTypeOfUInteger()) ? TypeFactory
                .mkUInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        return ((UIntegerValue) args[0]).neg();
    }
}