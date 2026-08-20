

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
     * B7 / ledger M-37 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork returned {@code TypeFactory.mkUInteger()} here (fork
     * {@code src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUInteger.java:54-64})
     * while {@link #eval} two methods below returns an {@code IntegerValue}. The comment above this
     * class has always said {@code value : UInteger -> Integer}; the sibling
     * {@code Op_ureal_value} correctly declares {@code mkReal()}.
     *
     * <p>This is not cosmetic. {@code ExpStdOp.create} stores what {@code matches} returns as the
     * expression's <strong>static</strong> type, so type-checking of any <em>enclosing</em>
     * expression was performed against {@code UInteger} while the value that actually arrived at
     * runtime was an {@code Integer}.
     *
     * <p><strong>Declared consequence.</strong> {@code TYPE} only. The printed type suffix does not
     * move: {@code Value.toStringWithType} uses {@code getRuntimeType()}, so the nine corpus entries
     * that exercise this operation already read {@code -> 3 : Integer}. That was checked before the
     * change rather than after.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-37.
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