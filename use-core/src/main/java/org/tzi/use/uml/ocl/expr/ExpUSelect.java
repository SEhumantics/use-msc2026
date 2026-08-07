package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.Value;

/**
 * OCL <code>uSelect</code> expression: selects the elements whose predicate
 * holds with a probability of at least one half.
 *
 * <p>It is <code>select</code> for an uncertain predicate. A certain Boolean
 * predicate is allowed and is then decided on its truth alone, so
 * <code>uSelect</code> degenerates to <code>select</code>.
 */
public final class ExpUSelect extends ExpQuery {

    /** The confidence <code>uSelect</code> applies, where uSelectC takes one. */
    private static final double DEFAULT_CONFIDENCE = 0.5;

    public ExpUSelect(VarDecl elemVarDecl, Expression rangeExp, Expression queryExp)
            throws ExpInvalidException {
        super(rangeExp.type(),
                elemVarDecl == null ? new VarDeclList(true) : new VarDeclList(elemVarDecl),
                rangeExp, queryExp);

        if (!queryExp.type().isKindOfUBoolean(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new ExpInvalidException("uSelect requires Boolean or UBoolean predicate");
        }
    }

    @Override
    public String name() {
        return "uSelect";
    }

    @Override
    public Value eval(EvalContext ctx) {
        ctx.enter(this);
        Value res = evalUSelect(ctx, DEFAULT_CONFIDENCE);
        ctx.exit(this, res);
        return res;
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitUSelect(this);
    }
}
