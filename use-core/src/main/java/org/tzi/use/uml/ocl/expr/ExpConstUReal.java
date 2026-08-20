package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

public class ExpConstUReal extends Expression {
    private Expression eValue;
    private Expression eUncertainty;

    /**
     * B7 / ledger M-31 — <strong>DECIDED NOT TO CHANGE, and that decision is the fix.</strong>
     *
     * <p>This constructor performs no type validation of {@code eValue} or {@code eUncertainty} —
     * unlike its four sibling constructors ({@link ExpConstUInteger}, {@link ExpConstUBoolean},
     * {@link ExpConstUString}, {@link ExpConstSBoolean}), which all validate their own arguments.
     * Here the check lives instead in {@link org.tzi.use.parser.ocl.ASTURealLiteral#gen}. The
     * recommendation this row considered was moving the check into this constructor, for
     * consistency with the siblings. It was not taken.
     *
     * <p><strong>Why not.</strong> This constructor is called <strong>directly, with unvalidated
     * {@code ExpConstReal} arguments, at 300+ sites</strong> in the ported test suite (the pattern
     * {@code URealExpOpsTest.java:34,39,44,…} repeats throughout). Moving the check here means
     * making it a checked failure — either an unchecked {@code RuntimeException} that changes no
     * call site, which would duplicate rather than relocate the check, or the pattern every sibling
     * uses of throwing from the constructor, which is exactly what the 300+ direct-construction
     * sites do not expect and do not handle. It would also move the two corpus error messages this
     * type contributes out of the {@code SemanticException} path and into a constructor exception,
     * which the {@code .in} corpus's {@code URealExpression} entries do not expect either.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-31.
     *
     * @param eValue the numeric value, expected to be {@code Integer} or {@code Real} — validated
     *               by the caller, not here
     * @param eUncertainty the uncertainty, likewise expected to be {@code Integer} or {@code Real}
     *                     and likewise validated by the caller
     */
    public ExpConstUReal(Expression eValue, Expression eUncertainty) {
        super(TypeFactory.mkUReal());
        this.eValue = eValue;
        this.eUncertainty = eUncertainty;
    }

    public String value() {
        return eValue.toString();
    }

    public String uncertainty() {
        return eUncertainty.toString();
    }

    @Override
    public Value eval(EvalContext ctx) {
        Value res = null;
        Value value, uncertainty;

        ctx.enter(this);;
        value = eValue.eval(ctx);
        uncertainty = eUncertainty.eval(ctx);

        if (value.isUndefined() || uncertainty.isUndefined())
            res = UndefinedValue.instance;
        else
            res = new URealValue(
                    Double.valueOf(value.toString()),
                    Double.valueOf(uncertainty.toString())
            );

        ctx.exit(this, res);

        return res;
    }

    @Override
    protected boolean childExpressionRequiresPreState() {
        return false;
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
        sb.append("UReal(")
                .append(eValue.toString())
                .append(",")
                .append(eUncertainty.toString())
                .append(")");
        return sb;
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitConstUReal(this);
    }

}
