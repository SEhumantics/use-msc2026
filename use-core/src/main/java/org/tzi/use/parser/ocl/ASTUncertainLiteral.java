package org.tzi.use.parser.ocl;

import java.util.*;
import org.tzi.use.parser.Context;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.uml.ocl.expr.*;

/** Current-parser AST node for native uncertainty literals. */
public final class ASTUncertainLiteral extends ASTExpression {
    private final ExpConstUncertain.Kind kind; private final List<ASTExpression> parts;
    public ASTUncertainLiteral(ExpConstUncertain.Kind kind, ASTExpression... parts){this.kind=kind;this.parts=List.of(parts);}
    @Override public Expression gen(Context ctx) throws SemanticException { Expression[] es=new Expression[parts.size()];for(int i=0;i<es.length;i++)es[i]=parts.get(i).gen(ctx);try{return new ExpConstUncertain(kind,es);}catch(ExpInvalidException e){throw new SemanticException(e.getMessage());} }
    @Override public void getFreeVariables(Set<String> vars){for(ASTExpression p:parts)p.getFreeVariables(vars);}
}
