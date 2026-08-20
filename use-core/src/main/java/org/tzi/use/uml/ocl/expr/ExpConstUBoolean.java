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
     * B7 / ledger M-29 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork's guard was {@code if (probability.isUndefined())}. An undefined {@code value}
     * was not checked at all: {@code value.toString()} on an {@code UndefinedValue} is the literal
     * text {@code "Undefined"}, {@code Boolean.valueOf("Undefined")} is {@code false} (no exception
     * — {@code Boolean.valueOf} accepts any string and only recognises {@code "true"}), and
     * {@code UBooleanValue.valueOf(false, p)} normalises that to {@code (true, 1-p)}. So
     * {@code UBoolean(Undefined, 0.8)} silently produced a <strong>defined</strong> {@code UBoolean}
     * — a value manufactured from an operand that was not there.
     *
     * <p>Both operands are now checked, matching {@code ExpConstUInteger} and {@code ExpConstUReal},
     * which already guard both of theirs.
     *
     * <p><strong>M-28 is unaffected and deliberately not revisited here</strong>: the
     * {@code Boolean.valueOf(value.toString())} round-trip stays, because it is also what converts a
     * malformed string into the {@code NumberFormatException} the surrounding {@code catch} turns
     * into {@code Undefined} — see the class comment on the parameter-shaped ledger row M-28.
     *
     * <p><strong>Declared consequence.</strong> {@code VALUE} — an undefined value operand now
     * yields {@code Undefined} instead of a fabricated {@code UBoolean}. <strong>Unreachable from the
     * corpus</strong>: {@code UBoolean(3 + 2, 1)} and {@code UBoolean(3 / 0, 1)} are both compile
     * errors at the constructor's own type guard above, so neither corpus attempt reaches {@code eval}.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-29.
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
            // B7 / ledger M-28 -- DECIDED NOT TO CHANGE, and that decision is the fix.
            //
            // The recommendation this row considered was replacing the round-trip through String
            // (Boolean.valueOf(value.toString()), Double.valueOf(probability.toString())) with
            // direct accessors: ((BooleanValue) value).value() and
            // ((RealValue) probability).value(). It was not taken.
            //
            // Why not: the round-trip is not an oversight, it is TWO behaviours in one expression.
            // First, Double.valueOf(anIntegerValue.toString()) yields 1.0 where a direct accessor
            // ((IntegerValue) probability).value() yields the int 1 -- a real value shift a rewrite
            // would introduce silently. Second, and load-bearing: the NumberFormatException a
            // malformed string raises here is exactly what the catch two lines below converts to
            // Undefined. A direct-accessor rewrite has no string to fail to parse, so it deletes
            // that error path along with the string conversion, changing which malformed inputs
            // become Undefined and which throw.
            //
            // Decided by the user on 2026-08-17 (B7); docs/port2/b7-fix-plan.md section 2 M-28.
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
