package org.tzi.use.uml.ocl.expr;
import org.tzi.use.uml.ocl.value.*;
public final class ExpUSelectC extends ExpQuery {
    private final Expression threshold;
    public ExpUSelectC(VarDecl d,Expression range,Expression query,Expression threshold)throws ExpInvalidException{super(range.type(),d==null?new VarDeclList(true):new VarDeclList(d),range,query);this.threshold=threshold;if(!query.type().isKindOfUBoolean(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)||!threshold.type().isKindOfReal(org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID))throw new ExpInvalidException("uSelectC requires uncertain Boolean predicate and numeric threshold");}
    public String name(){return "uSelectC";} public Value eval(EvalContext c){c.enter(this);Value t=threshold.eval(c);if(t.isUndefined()){c.exit(this,UndefinedValue.instance);return UndefinedValue.instance;}double x=t instanceof IntegerValue i?i.value():((RealValue)t).value();if(x<0||x>1){c.exit(this,UndefinedValue.instance);return UndefinedValue.instance;}Value v=evalUSelect(c,x);c.exit(this,v);return v;} public void processWithVisitor(ExpressionVisitor v){v.visitUSelectC(this);}
}
