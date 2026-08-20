/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/expr/ExpUSelect.java. Semantics unchanged.
 */
package org.tzi.use.uml.ocl.expr;

import org.tzi.use.uml.ocl.value.Value;

/**
 * OCL {@code uSelect} expression: {@code collection->uSelect(e | body)} keeps every element whose
 * {@code body} is crisply {@code true}, or a {@code UBoolean} whose probability is at least 0.5 —
 * see {@link ExpQuery#evalUSelect} for the shared algorithm with {@link ExpUSelectC}, which takes an
 * explicit threshold instead of the 0.5 default.
 *
 * @author Víctor M. Ortiz
 */
public class ExpUSelect extends ExpQuery {

    /**
     * Constructs a uSelect expression.
     * @param elemVarDecl  Vars declared in expression (may be null)
     * @param rangeExp     Expression with a collection of values
     * @param queryExp     Query about the collection
     * @throws ExpInvalidException
     */
    public ExpUSelect(VarDecl elemVarDecl,
                      Expression rangeExp,
                      Expression queryExp)
            throws ExpInvalidException
    {
        super(rangeExp.type(),
                elemVarDecl != null ? new VarDeclList(elemVarDecl) : new VarDeclList(true),
                rangeExp, queryExp);

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
        return "uSelect";
    }

    @Override
    public void processWithVisitor(ExpressionVisitor visitor) {
        visitor.visitUSelect(this);
    }
}
