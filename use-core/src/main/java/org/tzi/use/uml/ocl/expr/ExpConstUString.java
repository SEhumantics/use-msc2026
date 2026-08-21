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
     * Evaluates the value and confidence sub-expressions and builds a {@link UStringValue}.
     *
     * @implNote The body is wrapped in {@code try}/{@code catch (Exception)}, matching {@code
     *     ExpConstSBoolean.eval}. The fork had no guard here at all: {@code eValue} and {@code eConf}
     *     are statically typed by the constructor's guards, but a statically well-typed OCL
     *     expression can still evaluate to {@code UndefinedValue} at runtime (an undefined
     *     navigation, an {@code if}-branch, a failed {@code oclAsType}), and the fork's unguarded
     *     {@code (StringValue)} cast and {@code Double.valueOf(confidence.toString())} then escaped
     *     as an uncaught {@code ClassCastException}/{@code NumberFormatException} instead of yielding
     *     {@code Undefined} like every sibling uncertain literal does.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-30 &mdash; deviation ledger (decided 2026-08-17)"
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
