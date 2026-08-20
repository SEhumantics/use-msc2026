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
     * B7 / ledger M-33 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork's whole file had no {@code toString()} override, unlike the other four uncertain
     * AST literals — {@code ASTURealLiteral}, {@code ASTUIntegerLiteral}, {@code ASTUBooleanLiteral}
     * and {@code ASTSBooleanLiteral} all have one. Without it, this node fell through to
     * {@code Object.toString()}: an identity hash such as
     * {@code org.tzi.use.parser.ocl.ASTUStringLiteral@1a2b3c4d} in place of readable OCL source text.
     *
     * <p>Added here, matching {@link ASTURealLiteral#toString()}'s form.
     *
     * <p><strong>Declared consequence.</strong> {@code TEXT} — the text of every
     * {@code SemanticException} that interpolates this node via {@code getStringRep()} or similar.
     * <strong>No corpus entry mentions {@code UString}</strong> ({@code specification.md} section
     * 6.5), so no recorded expectation moves.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-33.
     */
    @Override
    public String toString() {
        return "UString(" + eValue.toString() + ", " + eConf.toString() + ")";
    }
}
