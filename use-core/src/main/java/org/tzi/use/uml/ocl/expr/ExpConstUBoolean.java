package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

public class ExpConstUBoolean extends Expression {
    private Expression eValue;
    private Expression eProbability;

    public ExpConstUBoolean(Expression eValue, Expression eProbability)
    throws ExpInvalidException
    {
        super(TypeFactory.mkUBoolean());

        if (!eValue.type().isTypeOfBoolean())
            throw new ExpInvalidException("Value must be Boolean");

        if (!(eProbability.type().isTypeOfInteger() || eProbability.type().isTypeOfReal()))
            throw new ExpInvalidException("Probability must be a Integer or Real");

        this.eValue = eValue;
        this.eProbability = eProbability;
    }

    public String value() {
        return eValue.toString();
    }

    public String probability() {
        return eProbability.toString();
    }

    /**
     * Evaluates the value and probability sub-expressions and builds a {@link UBooleanValue}.
     *
     * @implNote Both operands are now checked for undefined before use, matching {@code
     *     ExpConstUInteger} and {@code ExpConstUReal}. The fork only checked {@code
     *     probability.isUndefined()}: an undefined {@code value} slipped through because {@code
     *     value.toString()} on {@code UndefinedValue} is the literal text {@code "Undefined"}, {@code
     *     Boolean.valueOf("Undefined")} silently returns {@code false} (no exception — it only
     *     recognises {@code "true"}), and {@code UBooleanValue.valueOf(false, p)} normalises that to
     *     {@code (true, 1-p)} — a defined {@code UBoolean} manufactured from an operand that was
     *     never there. Unaffected by, and not to be confused with, the separate M-28 decision on the
     *     {@code Boolean.valueOf(value.toString())} round-trip a few lines below.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-29 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public Value eval(EvalContext ctx) {
        Value res = null;
        Value value, probability;

        ctx.enter(this);
        value = eValue.eval(ctx);
        probability = eProbability.eval(ctx);

        if (value.isUndefined() || probability.isUndefined())
            res = UndefinedValue.instance;
        else try {
            // implNote: deliberately NOT rewritten to direct accessors ((BooleanValue) value).value()
            // / ((RealValue) probability).value(). The String round-trip carries two behaviours at
            // once: Double.valueOf(anIntegerValue.toString()) yields 1.0 where a direct accessor
            // would yield the int 1, and a malformed string's NumberFormatException here is exactly
            // what the catch below converts to Undefined -- a direct accessor has no string to fail
            // to parse, so it would silently delete that error path.
            // See docs/port2/b7-fix-plan.md section 2 M-28 -- deviation ledger (decided 2026-08-17).
            res = UBooleanValue.valueOf(Boolean.valueOf(value.toString()), Double.valueOf(probability.toString()));
        }
        catch (RuntimeException ex) {
            res = UndefinedValue.instance;
        }

        ctx.exit(this, res);

        return res;
    }

    @Override
    protected boolean childExpressionRequiresPreState() {
        return false;
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
        sb.append("UBoolean(")
                .append(eValue.toString())
                .append(",")
                .append(eProbability.toString())
                .append(")");
        return sb;
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitConstUBoolean(this);
    }

}
