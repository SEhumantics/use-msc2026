package org.tzi.use.parser.ocl;

import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.uml.ocl.expr.ExpConstUReal;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.Type;

import java.util.Set;

public class ASTURealLiteral extends ASTExpression {

    private ASTExpression eValue;
    private ASTExpression eUncertainty;

    public ASTURealLiteral(ASTExpression eValue, ASTExpression eUncertainty) {
        this.eUncertainty = eUncertainty;
        this.eValue = eValue;
    }

    /**
     * B7 / ledger M-32 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork called {@code eValue.gen(ctx)} and {@code eUncertainty.gen(ctx)} each
     * <strong>twice</strong> — once here for the type check, and again at construction — building
     * two distinct {@link Expression} graphs per operand and installing the <em>second</em> one
     * (fork {@code src/main/org/tzi/use/parser/ocl/ASTURealLiteral.java:23-24} and {@code :34}).
     * {@code ASTExpression.gen(Context)} is not documented pure: for a sub-expression carrying
     * variable declarations it registers into {@code ctx} as a side effect, so calling it twice
     * mutates the context twice and keeps only the later graph.
     *
     * <p>Both children are now generated exactly once and reused for the type check and the
     * construction, halving the number of {@code ctx} mutations and installing the graph the type
     * check actually inspected — the first one, not the second.
     *
     * <p><strong>Declared consequence.</strong> {@code TREE}, and possibly {@code VALUE} for an
     * operand carrying a variable declaration. Whether any shipped corpus entry observes this is
     * {@code UNVERIFIABLE} without a test written for exactly this shape — no existing entry passes
     * a variable-declaring expression as a {@code UReal} operand.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-32.
     */
    @Override
    public Expression gen(Context ctx) throws SemanticException {
        Expression value = eValue.gen(ctx);
        Expression uncertainty = eUncertainty.gen(ctx);
        Type valueType = value.type();
        Type uncertaintyType = uncertainty.type();

        // Only integer or real are allowed.
        if ( !( valueType.isTypeOfInteger() || valueType.isTypeOfReal()) )
            throw new SemanticException("Value must be Integer or Real");

        if ( !(uncertaintyType.isTypeOfReal() || uncertaintyType.isTypeOfInteger()) )
            throw new SemanticException("Uncertainty must be Integer or Real");


        return new ExpConstUReal(value, uncertainty);
    }

    @Override
    public void getFreeVariables(Set<String> freeVars) {
        eValue.getFreeVariables(freeVars);
        eUncertainty.getFreeVariables(freeVars);
    }

    @Override
    public String toString() {
        return "UReal(" + eValue.toString() + ", " + eUncertainty.toString() + ")";
    }
}
