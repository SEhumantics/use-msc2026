package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;

/** Parser-level construction of the five uncertainty value literals. */
public final class ExpConstUncertain extends Expression {
    public enum Kind { UREAL, UINTEGER, USTRING, UBOOLEAN, SBOOLEAN }
    private final Kind kind; private final Expression[] parts;
    public ExpConstUncertain(Kind kind, Expression... parts) throws ExpInvalidException {
        super(switch(kind) { case UREAL -> TypeFactory.mkUReal(); case UINTEGER -> TypeFactory.mkUInteger(); case USTRING -> TypeFactory.mkUString(); case UBOOLEAN -> TypeFactory.mkUBoolean(); case SBOOLEAN -> TypeFactory.mkSBoolean(); });
        int arity=kind==Kind.SBOOLEAN?4:2; if(parts.length!=arity)throw new ExpInvalidException(kind+" requires "+arity+" arguments"); this.kind=kind;this.parts=parts.clone();
        checkArgumentTypes();
    }
    /**
     * Historical literals reject arguments of the wrong type while compiling,
     * with these diagnostics. An argument of the right type that turns out to
     * be undefined is a runtime concern and still yields an undefined value.
     */
    private void checkArgumentTypes() throws ExpInvalidException {
        switch (kind) {
        case UREAL -> {
            if (!isIntegerOrReal(parts[0])) throw new ExpInvalidException("Value must be Integer or Real");
            if (!isIntegerOrReal(parts[1])) throw new ExpInvalidException("Uncertainty must be Integer or Real");
        }
        case UINTEGER -> {
            if (!parts[0].type().isTypeOfInteger() && !parts[0].type().isTypeOfVoidType())
                throw new ExpInvalidException("Value must be Integer");
            if (!isIntegerOrReal(parts[1]) && !parts[1].type().isTypeOfVoidType())
                throw new ExpInvalidException("Uncertainty must be Integer or Real");
        }
        case UBOOLEAN -> {
            if (!parts[0].type().isTypeOfBoolean()) throw new ExpInvalidException("Value must be Boolean");
            if (!isIntegerOrReal(parts[1])) throw new ExpInvalidException("Probability must be a Integer or Real");
        }
        case USTRING -> {
            if (!parts[1].type().isKindOfReal(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID))
                throw new ExpInvalidException("UString : confidance need to be kind of Real");
            if (!parts[0].type().isTypeOfString())
                throw new ExpInvalidException("UString : value must be type of String");
        }
        case SBOOLEAN -> {
            String[] names = {"Belief", "Disbelief", "Uncertainty", "Agent"};
            for (int i = 0; i < parts.length; i++)
                if (!parts[i].type().isKindOfReal(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID))
                    throw new ExpInvalidException(names[i] + "  must be a kind of Real");
        }
        }
    }
    private static boolean isIntegerOrReal(Expression e) {
        return e.type().isTypeOfInteger() || e.type().isTypeOfReal();
    }
    @Override public Value eval(EvalContext ctx) {
        ctx.enter(this); Value[] v=new Value[parts.length]; for(int i=0;i<v.length;i++){v[i]=parts[i].eval(ctx);if(v[i].isUndefined()){ctx.exit(this,UndefinedValue.instance);return UndefinedValue.instance;}}
        try { Value r=switch(kind) {
            case UREAL -> new URealValue(number(v[0]),number(v[1]));
            case UINTEGER -> new UIntegerValue(integer(v[0]),number(v[1]));
            case USTRING -> new UStringValue(string(v[0]),number(v[1]));
            case UBOOLEAN -> UBooleanValue.probability(bool(v[0]),number(v[1]));
            case SBOOLEAN -> new SBooleanValue(number(v[0]),number(v[1]),number(v[2]),number(v[3]));
        };ctx.exit(this,r);return r;
        } catch(IllegalArgumentException ex) {ctx.exit(this,UndefinedValue.instance);return UndefinedValue.instance;}
    }
    private static double number(Value v){if(v instanceof IntegerValue i)return i.value();if(v instanceof RealValue r)return r.value();throw new IllegalArgumentException("numeric argument required");}
    private static int integer(Value v){if(v instanceof IntegerValue i)return i.value();throw new IllegalArgumentException("integer argument required");}
    private static String string(Value v){if(v instanceof StringValue s)return s.value();throw new IllegalArgumentException("string argument required");}
    private static boolean bool(Value v){if(v instanceof BooleanValue b)return b.value();throw new IllegalArgumentException("boolean argument required");}
    @Override public StringBuilder toString(StringBuilder b){b.append(switch(kind){case UREAL->"UReal";case UINTEGER->"UInteger";case USTRING->"UString";case UBOOLEAN->"UBoolean";case SBOOLEAN->"SBoolean";}).append('(');for(int i=0;i<parts.length;i++){if(i>0)b.append(", ");b.append(parts[i]);}return b.append(')');}
    @Override public void processWithVisitor(ExpressionVisitor visitor){visitor.visitUncertainConstant(this);}
    @Override protected boolean childExpressionRequiresPreState(){for(Expression e:parts)if(e.requiresPreState())return true;return false;}
}
