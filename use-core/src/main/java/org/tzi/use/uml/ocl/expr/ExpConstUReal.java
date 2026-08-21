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
     * Constructs a {@code UReal} literal from an already-typed value and uncertainty expression,
     * without validating either.
     *
     * @implNote Deliberately left unvalidated, unlike its four sibling constructors ({@link
     *     ExpConstUInteger}, {@link ExpConstUBoolean}, {@link ExpConstUString}, {@link
     *     ExpConstSBoolean}), which all validate their own arguments — the type check lives instead
     *     in {@link org.tzi.use.parser.ocl.ASTURealLiteral#gen}. Do not move it here: this
     *     constructor is called directly, with unvalidated arguments, at 300+ sites in the ported
     *     test suite; turning the check into a constructor-thrown exception would break every one of
     *     them and would move this type's corpus error messages out of the {@code SemanticException}
     *     path they are expected on.
     * @param eValue the numeric value, expected to be {@code Integer} or {@code Real} — validated
     *               by the caller, not here
     * @param eUncertainty the uncertainty, likewise expected to be {@code Integer} or {@code Real}
     *                     and likewise validated by the caller
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-31 &mdash; deviation ledger (decided 2026-08-17)"
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
