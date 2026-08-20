package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

public class ExpConstUString extends Expression {

    private Expression eValue;
    private Expression eConf;

    public ExpConstUString(Expression eValue, Expression eConf) throws ExpInvalidException {
        super(TypeFactory.mkUString());

        if (!eConf.type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID))
            throw new ExpInvalidException("UString : confidance need to be kind of Real");

        if (!eValue.type().isTypeOfString())
            throw new ExpInvalidException("UString : value must be type of String");

        this.eValue = eValue;
        this.eConf = eConf;
    }

    public Expression valueExpression() {
        return eValue;
    }

    public Expression confidenceExpression() {
        return eConf;
    }

    /**
     * B7 / ledger M-30 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>Two unguarded operations could escape {@code eval} as an uncaught exception (fork
     * {@code src/main/org/tzi/use/uml/ocl/expr/ExpConstUString.java:44,48}): the cast
     * {@code (StringValue) eValue.eval(ctx)}, and {@code Double.valueOf(confidence.toString())}.
     * Both {@code eValue} and {@code eConf} are statically typed by the constructor's guards above,
     * but a <em>statically</em> well-typed OCL expression can still evaluate to
     * {@code UndefinedValue} at runtime — an attribute read through an undefined navigation, an
     * {@code if}-branch, a failed {@code oclAsType}. When it did, the cast raised
     * {@code ClassCastException} and the parse raised {@code NumberFormatException}, and neither was
     * caught: the fork has no guard here at all, unlike every sibling uncertain literal.
     *
     * <p>The fix wraps the body in {@code try}/{@code catch (Exception)}, matching
     * {@code ExpConstSBoolean.eval} exactly — the model B7 names for this row.
     *
     * <p><strong>Declared consequence.</strong> {@code ERR} — an escaping exception becomes
     * {@code Undefined}, which is what every other uncertain literal constructor in this package
     * does with a malformed or undefined operand. {@code UString(...)} has no corpus example at all
     * ({@code specification.md} section 6.5), so this correction is unobserved by the historical
     * oracle and needed a purpose-built test.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-30.
     */
    @Override
    public Value eval(EvalContext ctx) {
        Value ustring;

        ctx.enter(this);
        Value value = eValue.eval(ctx);
        Value confidence = eConf.eval(ctx);

        try {
            double confidenceValue = Double.valueOf(confidence.toString());
            if (confidenceValue < 0 || confidenceValue > 1)
                ustring = UndefinedValue.instance;
            else
                ustring = new UStringValue(((StringValue) value).value(), confidenceValue);
        }
        catch (Exception ex) {
            ustring = UndefinedValue.instance;
        }

        ctx.exit(this, ustring);

        return ustring;
    }

    @Override
    protected boolean childExpressionRequiresPreState() {
        return false;
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
        return sb.append("UString(")
                .append(eValue.toString()).append(",")
                .append(eConf.toString()).append(")");
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitConstUString(this);
    }
}
