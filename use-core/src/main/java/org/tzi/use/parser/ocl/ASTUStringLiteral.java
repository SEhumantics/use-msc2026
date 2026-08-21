package org.tzi.use.parser.ocl;

import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.uml.ocl.expr.ExpConstUString;
import org.tzi.use.uml.ocl.expr.ExpInvalidException;
import org.tzi.use.uml.ocl.expr.Expression;

import java.util.Set;

public class ASTUStringLiteral extends ASTExpression {

    private ASTExpression eValue;
    private ASTExpression eConf;

    public ASTUStringLiteral(ASTExpression eValue, ASTExpression eConf) {
        this.eValue = eValue;
        this.eConf = eConf;
    }

    @Override
    public Expression gen(Context ctx) throws SemanticException {
        ExpConstUString result;

        try {
            result = new ExpConstUString(eValue.gen(ctx), eConf.gen(ctx));
        }
        catch (ExpInvalidException ex) {
            throw new SemanticException(ex.getMessage());
        }

        return result;
    }

    @Override
    public void getFreeVariables(Set<String> freeVars) {
        eValue.getFreeVariables(freeVars);
        eConf.getFreeVariables(freeVars);
    }

    /**
     * Returns readable OCL source text for this node, e.g. {@code UString(x, 0.8)}.
     *
     * @implNote The fork had no {@code toString()} override for this node at all, unlike the other
     *     four uncertain AST literals ({@code ASTURealLiteral}, {@code ASTUIntegerLiteral}, {@code
     *     ASTUBooleanLiteral}, {@code ASTSBooleanLiteral}), so it fell through to {@code
     *     Object.toString()}'s identity hash — including inside every {@code SemanticException}
     *     message that interpolates this node. Added here, matching {@link
     *     ASTURealLiteral#toString()}.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-33 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public String toString() {
        return "UString(" + eValue.toString() + ", " + eConf.toString() + ")";
    }
}
