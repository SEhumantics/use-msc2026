package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.SBooleanValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * Construction of the five uncertainty value literals.
 *
 * <p>The historical fork had one expression class per kind -- ExpConstUReal,
 * ExpConstUInteger, ExpConstUString, ExpConstUBoolean and ExpConstSBoolean --
 * which differed only in their argument checks and the value they built. They
 * are one class with a kind here.
 *
 * <p>The arguments are expressions, not constants, so only their types can be
 * checked while compiling; a coordinate that turns out to be out of range is a
 * runtime concern and yields an undefined value.
 */
public final class ExpConstUncertain extends Expression {

    public enum Kind { UREAL, UINTEGER, USTRING, UBOOLEAN, SBOOLEAN }

    private final Kind kind;
    private final Expression[] parts;

    public ExpConstUncertain(Kind kind, Expression... parts) throws ExpInvalidException {
        super(switch (kind) {
            case UREAL -> TypeFactory.mkUReal();
            case UINTEGER -> TypeFactory.mkUInteger();
            case USTRING -> TypeFactory.mkUString();
            case UBOOLEAN -> TypeFactory.mkUBoolean();
            case SBOOLEAN -> TypeFactory.mkSBoolean();
        });

        int arity = kind == Kind.SBOOLEAN ? 4 : 2;
        if (parts.length != arity) {
            throw new ExpInvalidException(kind + " requires " + arity + " arguments");
        }
        this.kind = kind;
        this.parts = parts.clone();
        checkArgumentTypes();
    }

    /**
     * Historical literals reject arguments of the wrong type while compiling, with
     * these diagnostics. The fourth SBoolean coordinate is the base rate; the
     * historical message calls it "Agent", which is kept so that the diagnostics
     * stay recognisable even though the name is misleading.
     */
    private void checkArgumentTypes() throws ExpInvalidException {
        switch (kind) {
            case UREAL -> {
                if (!isIntegerOrReal(parts[0])) {
                    throw new ExpInvalidException("Value must be Integer or Real");
                }
                if (!isIntegerOrReal(parts[1])) {
                    throw new ExpInvalidException("Uncertainty must be Integer or Real");
                }
            }
            case UINTEGER -> {
                if (!parts[0].type().isTypeOfInteger() && !parts[0].type().isTypeOfVoidType()) {
                    throw new ExpInvalidException("Value must be Integer");
                }
                if (!isIntegerOrReal(parts[1]) && !parts[1].type().isTypeOfVoidType()) {
                    throw new ExpInvalidException("Uncertainty must be Integer or Real");
                }
            }
            case UBOOLEAN -> {
                if (!parts[0].type().isTypeOfBoolean()) {
                    throw new ExpInvalidException("Value must be Boolean");
                }
                if (!isIntegerOrReal(parts[1])) {
                    throw new ExpInvalidException("Probability must be a Integer or Real");
                }
            }
            case USTRING -> {
                if (!parts[1].type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)) {
                    throw new ExpInvalidException("UString : confidance need to be kind of Real");
                }
                if (!parts[0].type().isTypeOfString()) {
                    throw new ExpInvalidException("UString : value must be type of String");
                }
            }
            case SBOOLEAN -> {
                String[] names = { "Belief", "Disbelief", "Uncertainty", "Agent" };
                for (int i = 0; i < parts.length; i++) {
                    if (!parts[i].type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)) {
                        throw new ExpInvalidException(names[i] + "  must be a kind of Real");
                    }
                }
            }
        }
    }

    private static boolean isIntegerOrReal(Expression e) {
        return e.type().isTypeOfInteger() || e.type().isTypeOfReal();
    }

    @Override
    public Value eval(EvalContext ctx) {
        ctx.enter(this);

        Value[] args = new Value[parts.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = parts[i].eval(ctx);
            if (args[i].isUndefined()) {
                ctx.exit(this, UndefinedValue.instance);
                return UndefinedValue.instance;
            }
        }

        try {
            Value res = switch (kind) {
                case UREAL -> new URealValue(number(args[0]), number(args[1]));
                case UINTEGER -> new UIntegerValue(integer(args[0]), number(args[1]));
                case USTRING -> new UStringValue(string(args[0]), number(args[1]));
                case UBOOLEAN -> UBooleanValue.probability(bool(args[0]), number(args[1]));
                case SBOOLEAN -> new SBooleanValue(number(args[0]), number(args[1]),
                        number(args[2]), number(args[3]));
            };
            ctx.exit(this, res);
            return res;
        } catch (IllegalArgumentException ex) {
            // An out-of-range coordinate or an invalid mass: not a value.
            ctx.exit(this, UndefinedValue.instance);
            return UndefinedValue.instance;
        }
    }

    private static double number(Value v) {
        if (v instanceof IntegerValue i) return i.value();
        if (v instanceof RealValue r) return r.value();
        throw new IllegalArgumentException("numeric argument required");
    }

    private static int integer(Value v) {
        if (v instanceof IntegerValue i) return i.value();
        throw new IllegalArgumentException("integer argument required");
    }

    private static String string(Value v) {
        if (v instanceof StringValue s) return s.value();
        throw new IllegalArgumentException("string argument required");
    }

    private static boolean bool(Value v) {
        if (v instanceof BooleanValue b) return b.value();
        throw new IllegalArgumentException("boolean argument required");
    }

    @Override
    public StringBuilder toString(StringBuilder b) {
        b.append(switch (kind) {
            case UREAL -> "UReal";
            case UINTEGER -> "UInteger";
            case USTRING -> "UString";
            case UBOOLEAN -> "UBoolean";
            case SBOOLEAN -> "SBoolean";
        }).append('(');
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(", ");
            b.append(parts[i]);
        }
        return b.append(')');
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitUncertainConstant(this);
    }

    @Override
    protected boolean childExpressionRequiresPreState() {
        for (Expression e : parts) {
            if (e.requiresPreState()) return true;
        }
        return false;
    }
}
