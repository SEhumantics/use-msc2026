package org.tzi.use.smt.verify;

import org.tzi.use.uml.ocl.expr.ExpAllInstances;
import org.tzi.use.uml.ocl.expr.ExpAny;
import org.tzi.use.uml.ocl.expr.ExpAsType;
import org.tzi.use.uml.ocl.expr.ExpAttrOp;
import org.tzi.use.uml.ocl.expr.ExpBagLiteral;
import org.tzi.use.uml.ocl.expr.ExpClosure;
import org.tzi.use.uml.ocl.expr.ExpCollect;
import org.tzi.use.uml.ocl.expr.ExpCollectNested;
import org.tzi.use.uml.ocl.expr.ExpCollectionLiteral;
import org.tzi.use.uml.ocl.expr.ExpConstBoolean;
import org.tzi.use.uml.ocl.expr.ExpConstEnum;
import org.tzi.use.uml.ocl.expr.ExpConstInteger;
import org.tzi.use.uml.ocl.expr.ExpConstReal;
import org.tzi.use.uml.ocl.expr.ExpConstSBoolean;
import org.tzi.use.uml.ocl.expr.ExpConstString;
import org.tzi.use.uml.ocl.expr.ExpConstUBoolean;
import org.tzi.use.uml.ocl.expr.ExpConstUInteger;
import org.tzi.use.uml.ocl.expr.ExpConstUReal;
import org.tzi.use.uml.ocl.expr.ExpConstUString;
import org.tzi.use.uml.ocl.expr.ExpConstUnlimitedNatural;
import org.tzi.use.uml.ocl.expr.ExpEmptyCollection;
import org.tzi.use.uml.ocl.expr.ExpExists;
import org.tzi.use.uml.ocl.expr.ExpForAll;
import org.tzi.use.uml.ocl.expr.ExpIf;
import org.tzi.use.uml.ocl.expr.ExpInstanceOp;
import org.tzi.use.uml.ocl.expr.ExpIsKindOf;
import org.tzi.use.uml.ocl.expr.ExpIsTypeOf;
import org.tzi.use.uml.ocl.expr.ExpIsUnique;
import org.tzi.use.uml.ocl.expr.ExpIterate;
import org.tzi.use.uml.ocl.expr.ExpLet;
import org.tzi.use.uml.ocl.expr.ExpNavigation;
import org.tzi.use.uml.ocl.expr.ExpNavigationClassifierSource;
import org.tzi.use.uml.ocl.expr.ExpObjAsSet;
import org.tzi.use.uml.ocl.expr.ExpObjRef;
import org.tzi.use.uml.ocl.expr.ExpObjectByUseId;
import org.tzi.use.uml.ocl.expr.ExpOclInState;
import org.tzi.use.uml.ocl.expr.ExpOne;
import org.tzi.use.uml.ocl.expr.ExpOrderedSetLiteral;
import org.tzi.use.uml.ocl.expr.ExpQuery;
import org.tzi.use.uml.ocl.expr.ExpRange;
import org.tzi.use.uml.ocl.expr.ExpReject;
import org.tzi.use.uml.ocl.expr.ExpSelect;
import org.tzi.use.uml.ocl.expr.ExpSelectByKind;
import org.tzi.use.uml.ocl.expr.ExpSelectByType;
import org.tzi.use.uml.ocl.expr.ExpSequenceLiteral;
import org.tzi.use.uml.ocl.expr.ExpSetLiteral;
import org.tzi.use.uml.ocl.expr.ExpSortedBy;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.ExpTupleLiteral;
import org.tzi.use.uml.ocl.expr.ExpTupleSelectOp;
import org.tzi.use.uml.ocl.expr.ExpUSelect;
import org.tzi.use.uml.ocl.expr.ExpUSelectC;
import org.tzi.use.uml.ocl.expr.ExpUndefined;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.ExpressionVisitor;
import org.tzi.use.uml.ocl.expr.ExpressionWithValue;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.tzi.use.uml.ocl.expr.VarDeclList;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;

/**
 * "Can a U-typed value reach this expression at all?" -- a WHOLE-TREE structural scan, answered by
 * looking at the static type of every node in the subtree rather than by pattern-matching a
 * handful of node classes.
 *
 * <p>This is the question {@link NominalErasureEvaluator} asks before every dispatch, and it used
 * to be answered by an {@code instanceof} chain ending in a whitelist: any {@code Expression}
 * subclass the chain did not name answered "yes, uncertain", including provably crisp ones. That
 * default was not a conservative safety margin, it was a systematic REFUSAL -- an expression
 * declared uncertain is routed into the erasure dispatch, which has no rule for a crisp {@code
 * let}, {@code if} or collection literal and therefore throws {@link
 * NominalErasureUnsupportedException}, contradicting the evaluator's own "a crisp expression
 * erases to itself". {@code Set{1,2}->includes(1)} was refused with "operator includes over an
 * uncertain operand" although nothing in it is uncertain.
 *
 * <p>Inverting a fail-closed default is only safe if the "no" really is proved, so the scan is
 * exhaustive by CONSTRUCTION rather than by inspection: implementing {@link ExpressionVisitor} in
 * full means the compiler refuses to build this class if use-core ever grows an expression node
 * this scan does not consider. Every node is checked through {@link #check}, which tests the
 * node's own static type first and only then descends into that node's children, so the answer is
 * "some subexpression is genuinely U-typed" and nothing weaker.
 *
 * <p>{@link #isUncertain} keeps the two fail-closed cases that are real: a {@code null} type is
 * unknown, not crisp, and a collection type is uncertain exactly when its element type is.
 *
 * <p>TWO shapes still answer "uncertain" without being U-typed, and both are deliberate rather
 * than forgotten:
 *
 * <ul>
 *   <li>an INSTANCE OPERATION CALL ({@link ExpInstanceOp}, i.e. {@code self.op(...)} and a
 *       constructor call). Its arguments are scanned, but the body that actually runs is NOT the
 *       one the AST node names: {@code ThreeValuedEvaluator.objectOperationCall} dispatches
 *       through the RECEIVER's own class, so a crisp base body can be overridden by a U-typed one
 *       this scan would never see. Scanning the statically-resolved body would therefore prove
 *       nothing, and proving nothing is exactly when this scan must fail closed. Behaviour is
 *       unchanged from before this class existed.
 *   <li>a {@link VarDecl} whose declared type is uncertain, checked explicitly rather than left to
 *       the range expression's element type, so an {@code iterate} accumulator declared U-typed is
 *       caught even where its initializer is not.
 * </ul>
 */
final class UncertaintyScan implements ExpressionVisitor {

  private boolean found;

  private UncertaintyScan() {}

  /** Whether any U-typed value can reach {@code expression}. */
  static boolean reaches(Expression expression) {
    UncertaintyScan scan = new UncertaintyScan();
    scan.check(expression);
    return scan.found;
  }

  /**
   * Whether a TYPE is uncertain. A {@code null} type is unknown and so counts as uncertain; a
   * collection is uncertain exactly when its element type is.
   */
  static boolean isUncertain(Type type) {
    if (type == null) {
      return true;
    }
    if (type.isTypeOfUReal()
        || type.isTypeOfUInteger()
        || type.isTypeOfUBoolean()
        || type.isTypeOfUString()
        || type.isTypeOfSBoolean()) {
      return true;
    }
    return type instanceof CollectionType collection && isUncertain(collection.elemType());
  }

  /** The single per-node step: own type first, then children. */
  private void check(Expression expression) {
    if (found) {
      return;
    }
    if (expression == null || isUncertain(expression.type())) {
      found = true;
      return;
    }
    expression.processWithVisitor(this);
  }

  private void checkQuery(ExpQuery query) {
    query.getVariableDeclarations().processWithVisitor(this);
    check(query.getRangeExpression());
    check(query.getQueryExpression());
    if (query.getUncertaintyExpression() != null) {
      // uSelectC's explicit confidence argument: a child like any other.
      check(query.getUncertaintyExpression());
    }
  }

  private void checkCollectionLiteral(ExpCollectionLiteral literal) {
    for (Expression element : literal.getElemExpr()) {
      check(element);
    }
  }

  // --------------------------------------------------------------------------- leaves

  @Override
  public void visitAllInstances(ExpAllInstances exp) {}

  @Override
  public void visitConstBoolean(ExpConstBoolean exp) {}

  @Override
  public void visitConstEnum(ExpConstEnum exp) {}

  @Override
  public void visitConstInteger(ExpConstInteger exp) {}

  @Override
  public void visitConstReal(ExpConstReal exp) {}

  @Override
  public void visitConstString(ExpConstString exp) {}

  @Override
  public void visitConstUnlimitedNatural(ExpConstUnlimitedNatural exp) {}

  @Override
  public void visitEmptyCollection(ExpEmptyCollection exp) {}

  @Override
  public void visitUndefined(ExpUndefined exp) {}

  @Override
  public void visitVariable(ExpVariable exp) {}

  @Override
  public void visitObjRef(ExpObjRef exp) {
    // A literal object reference. Deliberately NOT delegating the way
    // AbstractCoverageVisitor.visitObjRef does -- that call re-enters itself and never terminates.
  }

  @Override
  public void visitWithValue(ExpressionWithValue exp) {
    // The wrapped value's own type IS this expression's type, already tested in check().
  }

  // The uncertain constants are leaves whose TYPE is what makes them uncertain; check() has
  // already answered for them before any of these can run.

  @Override
  public void visitConstUBoolean(ExpConstUBoolean exp) {}

  @Override
  public void visitConstSBoolean(ExpConstSBoolean exp) {}

  @Override
  public void visitConstUInteger(ExpConstUInteger exp) {}

  @Override
  public void visitConstUReal(ExpConstUReal exp) {}

  @Override
  public void visitConstUString(ExpConstUString exp) {}

  // ------------------------------------------------------------------- one-child nodes

  @Override
  public void visitAsType(ExpAsType exp) {
    check(exp.getSourceExpr());
  }

  @Override
  public void visitAttrOp(ExpAttrOp exp) {
    check(exp.objExp());
  }

  @Override
  public void visitIsKindOf(ExpIsKindOf exp) {
    check(exp.getSourceExpr());
  }

  @Override
  public void visitIsTypeOf(ExpIsTypeOf exp) {
    check(exp.getSourceExpr());
  }

  @Override
  public void visitNavigation(ExpNavigation exp) {
    check(exp.getObjectExpression());
  }

  @Override
  public void visitNavigationClassifierSource(ExpNavigationClassifierSource exp) {
    check(exp.getObjectExpression());
  }

  @Override
  public void visitObjAsSet(ExpObjAsSet exp) {
    check(exp.getObjectExpression());
  }

  @Override
  public void visitOclInState(ExpOclInState exp) {
    check(exp.getSourceExpr());
  }

  @Override
  public void visitObjectByUseId(ExpObjectByUseId exp) {
    check(exp.getIdExpression());
  }

  @Override
  public void visitSelectByKind(ExpSelectByKind exp) {
    check(exp.getSourceExpression());
  }

  @Override
  public void visitExpSelectByType(ExpSelectByType exp) {
    check(exp.getSourceExpression());
  }

  @Override
  public void visitTupleSelectOp(ExpTupleSelectOp exp) {
    check(exp.getTupleExp());
  }

  // ---------------------------------------------------------------- many-child nodes

  @Override
  public void visitStdOp(ExpStdOp exp) {
    for (Expression argument : exp.args()) {
      check(argument);
    }
  }

  @Override
  public void visitIf(ExpIf exp) {
    check(exp.getCondition());
    check(exp.getThenExpression());
    check(exp.getElseExpression());
  }

  @Override
  public void visitLet(ExpLet exp) {
    check(exp.getVarExpression());
    check(exp.getInExpression());
  }

  @Override
  public void visitRange(ExpRange exp) {
    check(exp.getStart());
    check(exp.getEnd());
  }

  @Override
  public void visitTupleLiteral(ExpTupleLiteral exp) {
    for (ExpTupleLiteral.Part part : exp.getParts()) {
      check(part.getExpression());
    }
  }

  @Override
  public void visitBagLiteral(ExpBagLiteral exp) {
    checkCollectionLiteral(exp);
  }

  @Override
  public void visitOrderedSetLiteral(ExpOrderedSetLiteral exp) {
    checkCollectionLiteral(exp);
  }

  @Override
  public void visitSequenceLiteral(ExpSequenceLiteral exp) {
    checkCollectionLiteral(exp);
  }

  @Override
  public void visitSetLiteral(ExpSetLiteral exp) {
    checkCollectionLiteral(exp);
  }

  // ------------------------------------------------------------------------- queries

  @Override
  public void visitQuery(ExpQuery exp) {
    checkQuery(exp);
  }

  @Override
  public void visitAny(ExpAny exp) {
    checkQuery(exp);
  }

  @Override
  public void visitClosure(ExpClosure exp) {
    checkQuery(exp);
  }

  @Override
  public void visitCollect(ExpCollect exp) {
    checkQuery(exp);
  }

  @Override
  public void visitCollectNested(ExpCollectNested exp) {
    checkQuery(exp);
  }

  @Override
  public void visitExists(ExpExists exp) {
    checkQuery(exp);
  }

  @Override
  public void visitForAll(ExpForAll exp) {
    checkQuery(exp);
  }

  @Override
  public void visitIsUnique(ExpIsUnique exp) {
    checkQuery(exp);
  }

  @Override
  public void visitIterate(ExpIterate exp) {
    checkQuery(exp);
    if (exp.getAccuInitializer() != null) {
      exp.getAccuInitializer().getVarDecl().processWithVisitor(this);
      check(exp.getAccuInitializer().initExpr());
    }
  }

  @Override
  public void visitOne(ExpOne exp) {
    checkQuery(exp);
  }

  @Override
  public void visitReject(ExpReject exp) {
    checkQuery(exp);
  }

  @Override
  public void visitSelect(ExpSelect exp) {
    checkQuery(exp);
  }

  @Override
  public void visitSortedBy(ExpSortedBy exp) {
    checkQuery(exp);
  }

  @Override
  public void visitUSelect(ExpUSelect exp) {
    checkQuery(exp);
  }

  @Override
  public void visitUSelectC(ExpUSelectC exp) {
    checkQuery(exp);
  }

  // ---------------------------------------------------------------- declarations

  @Override
  public void visitVarDeclList(VarDeclList varDeclList) {
    for (int i = 0; i < varDeclList.size(); i++) {
      varDeclList.varDecl(i).processWithVisitor(this);
    }
  }

  @Override
  public void visitVarDecl(VarDecl varDecl) {
    if (isUncertain(varDecl.type())) {
      found = true;
    }
  }

  // ------------------------------------------------------------------- fail closed

  @Override
  public void visitInstanceOp(ExpInstanceOp exp) {
    // See the class doc: the body that runs is chosen by the RECEIVER's class, not by this node,
    // so no scan of this node can prove the call crisp. Refusing keeps the oracle honest and
    // preserves the behaviour this class replaced.
    found = true;
  }
}
