package org.tzi.use.parser.ocl;

import java.util.List;
import java.util.Set;

import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.uml.ocl.expr.ExpConstUncertain;
import org.tzi.use.uml.ocl.expr.ExpInvalidException;
import org.tzi.use.uml.ocl.expr.Expression;

/**
 * AST node for the five uncertainty literals.
 *
 * <p>The historical fork had one node class per kind -- ASTURealLiteral,
 * ASTUIntegerLiteral, ASTUStringLiteral, ASTUBooleanLiteral and
 * ASTSBooleanLiteral. They differed only in which expression they built, so they
 * are one node with a kind here, matching the single
 * {@link ExpConstUncertain} they generate.
 */
public final class ASTUncertainLiteral extends ASTExpression {

    private final ExpConstUncertain.Kind kind;
    private final List<ASTExpression> parts;

    public ASTUncertainLiteral(ExpConstUncertain.Kind kind, ASTExpression... parts) {
        this.kind = kind;
        this.parts = List.of(parts);
    }

    @Override
    public Expression gen(Context ctx) throws SemanticException {
        Expression[] generated = new Expression[parts.size()];
        for (int i = 0; i < generated.length; i++) {
            generated[i] = parts.get(i).gen(ctx);
        }
        try {
            return new ExpConstUncertain(kind, generated);
        } catch (ExpInvalidException e) {
            throw new SemanticException(e.getMessage());
        }
    }

    @Override
    public void getFreeVariables(Set<String> vars) {
        for (ASTExpression part : parts) {
            part.getFreeVariables(vars);
        }
    }
}
