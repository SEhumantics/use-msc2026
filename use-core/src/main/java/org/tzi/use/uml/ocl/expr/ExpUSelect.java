package org.tzi.use.uml.ocl.expr;
import org.tzi.use.uml.ocl.value.Value;
public final class ExpUSelect extends ExpQuery {
    public ExpUSelect(VarDecl d,Expression range,Expression query)throws ExpInvalidException{super(range.type(),d==null?new VarDeclList(true):new VarDeclList(d),range,query);if(!query.type().isKindOfUBoolean(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID))throw new ExpInvalidException("uSelect requires Boolean or UBoolean predicate");}
    public String name(){return "uSelect";} public Value eval(EvalContext c){c.enter(this);Value v=evalUSelect(c,.5);c.exit(this,v);return v;} public void processWithVisitor(ExpressionVisitor v){v.visitUSelect(this);}
}
