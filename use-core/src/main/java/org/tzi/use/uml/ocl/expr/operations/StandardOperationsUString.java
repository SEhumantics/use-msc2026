

/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/expr/operations/StandardOperationsUString.java.
 *
 * Semantics unchanged. The only edit is the import of the vendored uncertainty datatypes,
 * relocated from `uDataTypes` to org.tzi.use.uncertainty.datatypes (B1).
 */
package org.tzi.use.uml.ocl.expr.operations;

import com.google.common.collect.Multimap;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.ExpInvalidException;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;

public class StandardOperationsUString {

    public static void registerTypeOperations(Multimap<String, OpGeneric> opmap) {
        OpGeneric.registerOperation(new Op_uString_value(), opmap);
        OpGeneric.registerOperation(new Op_uString_confidence(), opmap);
        OpGeneric.registerOperation(new Op_uString_setValue(), opmap);
        OpGeneric.registerOperation(new Op_uString_setConfidence(), opmap);
        OpGeneric.registerOperation(new Op_uString_at(), opmap);
        OpGeneric.registerOperation(new Op_uString_character(), opmap);
        OpGeneric.registerOperation(new Op_uString_uConcat(), opmap);
        OpGeneric.registerOperation(new Op_uString_size(), opmap);
        OpGeneric.registerOperation(new Op_uString_indexOf(), opmap);
        OpGeneric.registerOperation(new Op_uString_substring(), opmap);
        OpGeneric.registerOperation(new Op_uString_toLowerCase(), opmap);
        OpGeneric.registerOperation(new Op_uString_toUpperCase(), opmap);
        OpGeneric.registerOperation(new Op_uString_toBoolean(), opmap);
        OpGeneric.registerOperation(new Op_uString_toInteger(), opmap);
        OpGeneric.registerOperation(new Op_uString_toReal(), opmap);
        OpGeneric.registerOperation(new Op_uString_toUBoolean(), opmap);
        OpGeneric.registerOperation(new Op_uString_toString(), opmap);
        OpGeneric.registerOperation(new Op_uString_less(), opmap);
        OpGeneric.registerOperation(new Op_uString_less_or_equal(), opmap);
        OpGeneric.registerOperation(new Op_uString_greater(), opmap);
        OpGeneric.registerOperation(new Op_uString_greater_or_equal(), opmap);
    }
}

// --------------------------------------------------------
//
// UString operations.
//
// --------------------------------------------------------

// value : UString -> String
final class Op_uString_value extends OpGeneric {

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
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUString() ?
                TypeFactory.mkString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustring = UStringValue.valueOf(args[0]);
        return new StringValue(ustring.value());
    }
}


// confidence : UString -> Real
final class Op_uString_confidence extends OpGeneric {

    @Override
    public String name() {
        return "confidence";
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
        return params.length == 1 && params[0].isTypeOfUString() ?
                TypeFactory.mkReal() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustring = UStringValue.valueOf(args[0]);
        return new RealValue(ustring.confidence());
    }
}


// setValue : UString x String -> UString
final class Op_uString_setValue extends OpGeneric {

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
        return params.length == 2 && params[0].isTypeOfUString() && params[1].isTypeOfString() ?
                TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustring = UStringValue.valueOf(args[0]);
        StringValue string = (StringValue) args[1];
        return new UStringValue(string.value(), ustring.confidence());
    }
}


// setConfidence : UString x Real -> UString
final class Op_uString_setConfidence extends OpGeneric {

    @Override
    public String name() {
        return "setConfidence";
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
        return params.length == 2 && params[0].isTypeOfUString() && params[1].isKindOfReal(Type.VoidHandling.EXCLUDE_VOID) ?
                TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustring = UStringValue.valueOf(args[0]);
        double confidence = 0;

        if (args[1].isInteger())
            confidence = ((IntegerValue) args[1]).value();
        else
            confidence = ((RealValue) args[1]).value();

        return new UStringValue(ustring.value(), confidence);
    }
}

// at : UString x Integer -> String
final class Op_uString_at extends OpGeneric {

    @Override
    public String name() {
        return "at";
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
        return params.length == 2 && params[0].isTypeOfUString() && params[1].isTypeOfInteger() ?
                TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustring = UStringValue.valueOf(args[0]);
        IntegerValue index = (IntegerValue) args[1];
        return ustring.uAt(index.value());
    }
}


// character : UString -> List<string>
final class Op_uString_character extends OpGeneric {

    @Override
    public String name() {
        return "character";
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
        return params.length == 1 && params[0].isTypeOfUString() ?
                TypeFactory.mkSequence(TypeFactory.mkUString()) : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustring = UStringValue.valueOf(args[0]);
        return ustring.uCharacters();
    }
}


// + : UString x UString -> UString
final class Op_uString_uConcat extends OpGeneric {

    @Override
    public String name() {
        return "+";
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
     * @implNote Guards on {@code params.length} before indexing {@code params[1]}: {@code "+"} is
     *     also registered as OCL's unary-plus operator ({@code Op_number_unaryplus} and siblings),
     *     so this {@code matches()} is tried against every unary {@code +} expression too (e.g.
     *     {@code +'a'}, {@code +true}) with a 1-element {@code params} array. The original
     *     unconditional {@code params[1]} access threw {@code ArrayIndexOutOfBoundsException} for
     *     any such expression instead of returning {@code null} (no match), crashing OCL
     *     type-checking wherever unary {@code +} was used on a non-numeric operand.
     */
    @Override
    public Type matches(Type[] params) {
        if (params.length != 2)
            return null;

        boolean someOfThemIsUString = params[0].isTypeOfUString() || params[1].isTypeOfUString();

        return params[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID) && someOfThemIsUString ?
                TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        return ustringA.uConcat(args[1]);
    }
}


// indexOf : UString x String -> Integer
final class Op_uString_indexOf extends OpGeneric {

    @Override
    public String name() {
        return "indexOf";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    /*
     * Found while adding test coverage at S9 (not a B7 ledger row): matches() declared mkUString()
     * while eval() below always returns an IntegerValue (delegating to UStringValue.indexOf(String),
     * uml/ocl/value/UStringValue.java:329-331, which wraps IntegerValue.valueOf(...)). Byte-identical
     * to the fork, so pre-existing there too. Same defect class as the already-fixed ledger row M-37
     * (UInteger.value()/toInteger() declared mkUInteger() while returning IntegerValue) — fixed the
     * same way, by correcting the declared static type to match the real runtime type. TYPE only: no
     * corpus entry uses indexOf (specification.md 6.5), so no recorded expectation moves; an enclosing
     * expression that consumes indexOf(...)'s result as an Integer (e.g. `x.indexOf('a') + 1`) now
     * type-checks, where it previously failed at compile time expecting a UString operand.
     */
    @Override
    public Type matches(Type[] params) {
        return params.length == 2 && params[0].isTypeOfUString() &&
                params[1].isTypeOfString() ? TypeFactory.mkInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        return ustringA.indexOf((StringValue) args[1]);
    }
}


// substring : UString x Integer x Integer -> UString
final class Op_uString_substring extends OpGeneric {

    @Override
    public String name() {
        return "substring";
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
        return params.length == 3 && params[0].isTypeOfUString() &&
                params[1].isTypeOfInteger() && params[2].isTypeOfInteger()
                ? TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        IntegerValue first, end;
        UStringValue result = null;

        first = (IntegerValue) args[1];
        end = (IntegerValue) args[2];

        try {
            result = ustringA.uSubstring(first.value(), end.value());
        }
        catch (Exception ex) {
            // In case of error, the result will be "" because its the same
            // behavior as String.substring
            result = new UStringValue("", 1);
        }


        return result;
    }
}


// toLowerCase : UString -> UString
final class Op_uString_toLowerCase extends OpGeneric {

    @Override
    public String name() {
        return "toLowerCase";
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
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        return  ustringA.uToLowerCase();
    }
}


// toUpperCase : UString -> UString
final class Op_uString_toUpperCase extends OpGeneric {

    @Override
    public String name() {
        return "toUpperCase";
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
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkUString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        return  ustringA.uToUpperCase();
    }
}


// size : UString -> UInteger
final class Op_uString_size extends OpGeneric {

    @Override
    public String name() {
        return "size";
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
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkUInteger() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        return ustringA.uSize();
    }
}


// uToString : UString -> String
final class Op_uString_toString extends OpGeneric {

    @Override
    public String name() {
        return "toString";
    }

    @Override
    public int kind() {
        return OPERATION;
    }

    @Override
    public boolean isInfixOrPrefix() {
        return false;
    }

    /*
     * Found while adding test coverage at S9 (not a B7 ledger row): matches() declared mkUString()
     * while eval() below always returns a StringValue (delegating to UStringValue.uToString(),
     * uml/ocl/value/UStringValue.java:285-287, which wraps StringValue). This class's own leading
     * comment already said "-> String", agreeing with eval(); matches() was the outlier. Same defect
     * class as the already-fixed ledger row M-37 — see Op_uString_indexOf's comment above for the
     * full account. TYPE only, same reasoning: no corpus entry uses toString() on a UString.
     */
    @Override
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkString() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);
        return ustringA.uToString();
    }
}


// toInteger : UString -> Integer
final class Op_uString_toInteger extends OpGeneric {

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
    public Type matches(Type[] params) {
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkInteger() : null;
    }

    /*
     * Found while adding test coverage at S9 (not a B7 ledger row): on a failed conversion (e.g.
     * "abc".toInteger()) this returned a bare Java null instead of UndefinedValue.instance. Unlike
     * ExpConstUString's M-30 (an unguarded exception that ESCAPED eval entirely), this exception was
     * already caught -- but the catch produced a Value-typed null, which ExpStdOp.eval (:271-317)
     * assigns straight through to its own result and returns unwrapped. Any enclosing expression that
     * evaluates an operand and then calls isUndefined() on it (ExpStdOp.java:291, the ordinary
     * strict-operand-undefined check every OPERATION-kind op goes through) dereferences that null and
     * crashes with a real NullPointerException -- reproduced with `1 + UString('abc', 1).toInteger()`.
     * Byte-identical to the fork, so pre-existing there too. Same class of defect as the already-fixed
     * M-30 (an unguarded/uncaught failure must become Undefined, not escape or propagate as null);
     * fixed the same way, by returning UndefinedValue.instance on failure instead of null.
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);

        try {
            return ustringA.toInteger();
        }
        catch (Exception ex) {
            return UndefinedValue.instance;
        }
    }
}


// toReal : UString -> Real
final class Op_uString_toReal extends OpGeneric {

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
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkReal() : null;
    }

    /*
     * Found while adding test coverage at S9: same null-instead-of-Undefined defect as
     * Op_uString_toInteger above -- see its comment for the full account. Fixed the same way.
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);

        try {
            return ustringA.toReal();
        }
        catch (Exception ex) {
            return UndefinedValue.instance;
        }
    }
}


// toBoolean : UString -> Boolean
final class Op_uString_toBoolean extends OpGeneric {

    @Override
    public String name() {
        return "toBoolean";
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
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkBoolean() : null;
    }

    /*
     * Found while adding test coverage at S9: same null-instead-of-Undefined defect as
     * Op_uString_toInteger above -- see its comment for the full account. Fixed the same way.
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);

        try {
            return ustringA.toBoolean();
        }
        catch (Exception ex) {
            return UndefinedValue.instance;
        }
    }
}


// toUBoolean : UString -> UBoolean
final class Op_uString_toUBoolean extends OpGeneric {

    @Override
    public String name() {
        return "toUBoolean";
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
        return params.length == 1 && params[0].isTypeOfUString()
                ? TypeFactory.mkUBoolean() : null;
    }

    /*
     * Found while adding test coverage at S9: same null-instead-of-Undefined defect as
     * Op_uString_toInteger above -- see its comment for the full account. Fixed the same way.
     */
    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue ustringA = UStringValue.valueOf(args[0]);

        try {
            return ustringA.toUBoolean();
        }
        catch (Exception ex) {
            return UndefinedValue.instance;
        }
    }
}


// < : UString x Value -> UBoolean
final class Op_uString_less extends OpGeneric {

    @Override
    public String name() {
        return "<";
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
        return params.length == 2 && params[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)
                ? TypeFactory.mkUBoolean() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue stringA = UStringValue.valueOf(args[0]);
        return stringA.lt(args[1]);
    }
}


// <= : UString x Value -> UBoolean
final class Op_uString_less_or_equal extends OpGeneric {

    @Override
    public String name() {
        return "<=";
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
        return params.length == 2 && params[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)
                ? TypeFactory.mkUBoolean() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue stringA = UStringValue.valueOf(args[0]);
        return stringA.le(args[1]);
    }
}


// > : UString x Value -> UBoolean
final class Op_uString_greater extends OpGeneric {

    @Override
    public String name() {
        return ">";
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
        return params.length == 2 && params[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)
                ? TypeFactory.mkUBoolean() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue stringA = UStringValue.valueOf(args[0]);
        return stringA.gt(args[1]);
    }
}


// > : UString x Value -> UBoolean
final class Op_uString_greater_or_equal extends OpGeneric {

    @Override
    public String name() {
        return ">=";
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
        return params.length == 2 && params[0].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID) &&
                params[1].isKindOfUString(Type.VoidHandling.EXCLUDE_VOID)
                ? TypeFactory.mkUBoolean() : null;
    }

    @Override
    public Value eval(EvalContext ctx, Value[] args, Type resultType) {
        UStringValue stringA = UStringValue.valueOf(args[0]);
        return stringA.ge(args[1]);
    }
}