/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/expr/ExpUSelectC.java. Semantics unchanged.
 */
package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.value.Value;

/**
 * OCL {@code uSelectC} expression: {@code collection->uSelectC(e | body, confidence)}, as
 * {@link ExpUSelect} but with an explicit confidence threshold instead of the implicit 0.5.
 */
public class ExpUSelectC extends ExpQuery {

    public ExpUSelectC(VarDecl elemVarDecl,
                       Expression rangeExp,
                       Expression queryExp,
                       Expression uncertaintyExp)
        throws ExpInvalidException
    {
        super(rangeExp.type(),
                elemVarDecl != null ? new VarDeclList(elemVarDecl) : new VarDeclList(true),
                rangeExp, queryExp, uncertaintyExp);

        assertKindOfUBoolean();
    }

    @Override
    public Value eval(EvalContext ctx) {
        ctx.enter(this);
        Value res = evalUSelect(ctx);
        ctx.exit(this, res);
        return res;
    }

    @Override
    public String name() {
        return "uSelectC";
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitUSelectC(this);
    }
}
