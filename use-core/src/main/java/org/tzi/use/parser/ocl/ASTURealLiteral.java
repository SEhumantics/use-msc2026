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
     * Type-checks the value and uncertainty sub-expressions and builds an {@link ExpConstUReal}.
     *
     * @implNote Each child is generated exactly once, into a local, and that local is reused for
     *     both the type check and the construction. The fork called {@code eValue.gen(ctx)} and
     *     {@code eUncertainty.gen(ctx)} twice each — once here, again at construction — building two
     *     distinct {@link Expression} graphs per operand and installing the second one, even though
     *     {@code gen} is not documented pure and mutates {@code ctx} as a side effect for
     *     sub-expressions carrying variable declarations. Do not re-inline the calls at their second
     *     use site: that reintroduces the double mutation and installs the wrong graph.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-32 &mdash; deviation ledger (decided 2026-08-17)"
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
