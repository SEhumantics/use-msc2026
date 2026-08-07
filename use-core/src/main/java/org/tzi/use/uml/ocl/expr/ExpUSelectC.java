package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * OCL <code>uSelectC</code> expression: {@link ExpUSelect} with an explicit
 * confidence, written as a second argument -- <code>c-&gt;uSelectC(e | p, 0.8)</code>.
 */
public final class ExpUSelectC extends ExpQuery {

    private final Expression confidence;

    public ExpUSelectC(VarDecl elemVarDecl, Expression rangeExp, Expression queryExp,
            Expression confidence) throws ExpInvalidException {
        super(rangeExp.type(),
                elemVarDecl == null ? new VarDeclList(true) : new VarDeclList(elemVarDecl),
                rangeExp, queryExp);
        this.confidence = confidence;

        if (!queryExp.type().isKindOfUBoolean(Type.VoidHandling.EXCLUDE_VOID)
                || !confidence.type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new ExpInvalidException(
                    "uSelectC requires uncertain Boolean predicate and numeric threshold");
        }
    }

    @Override
    public String name() {
        return "uSelectC";
    }

    /**
     * A confidence outside [0,1] raises rather than evaluating to undefined. The
     * historical implementation raises there too, and ExpQuery already reports
     * other evaluator-internal violations the same way.
     */
    @Override
    public Value eval(EvalContext ctx) {
        ctx.enter(this);

        Value threshold = confidence.eval(ctx);
        if (threshold.isUndefined()) {
            ctx.exit(this, UndefinedValue.instance);
            return UndefinedValue.instance;
        }

        double value = threshold instanceof IntegerValue i ? i.value()
                : ((RealValue) threshold).value();
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("uSelectC confidence must be between 0 and 1");
        }

        Value res = evalUSelect(ctx, value);
        ctx.exit(this, res);
        return res;
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitUSelectC(this);
    }
}
