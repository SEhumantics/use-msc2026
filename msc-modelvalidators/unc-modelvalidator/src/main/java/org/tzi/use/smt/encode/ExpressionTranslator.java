package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.ocl.expr.*;

/** Translates the verified leaf-level Library OCL fragment and fails closed on everything else. */
public final class ExpressionTranslator implements ExpressionVisitor {
  private static final BigInteger UNDEFINED_STRING_SENTINEL = BigInteger.valueOf(-1);
  private final TranslationContext context;
  private final boolean positivePolarity;
  private SmtTerm result;

  private ExpressionTranslator(TranslationContext c, boolean positivePolarity) {
    context = c;
    this.positivePolarity = positivePolarity;
  }

  public static SmtTerm translate(Expression e, TranslationContext c) {
    return translate(e, c, true);
  }

  private static SmtTerm translate(Expression e, TranslationContext c, boolean positivePolarity) {
    ExpressionTranslator t = new ExpressionTranslator(c, positivePolarity);
    e.processWithVisitor(t);
    return t.result;
  }

  @Override
  public void visitConstInteger(ExpConstInteger e) {
    result = Smt.intLit(BigInteger.valueOf(e.value()));
  }

  @Override
  public void visitConstString(ExpConstString e) {
    throw unsupported(
        "free-standing string literal ('" + e.value() + "') outside an attribute comparison");
  }

  @Override
  public void visitConstBoolean(ExpConstBoolean e) {
    throw unsupported("Boolean literal");
  }

  @Override
  public void visitUndefined(ExpUndefined e) {
    if (!e.type().isTypeOfString())
      throw unsupported("oclUndefined of a non-String type (" + e.type() + ")");
    result = Smt.intLit(UNDEFINED_STRING_SENTINEL);
  }

  @Override
  public void visitVariable(ExpVariable e) {
    throw unsupported(
        "bare variable reference '" + e.getVarname() + "' outside an attribute access");
  }

  @Override
  public void visitAttrOp(ExpAttrOp e) {
    VariableBinding b = context.binding(variableNameOf(e.objExp()));
    AttributeValues v = context.attributeValues(b.className(), e.attr().name());
    if (v.type() == AttributeType.UREAL) {
      throw unsupported("bare UReal attribute access outside a supported threshold comparison");
    }
    result = Smt.sym(v.valueNames().get(b.slotIndex()));
  }

  @Override
  public void visitStdOp(ExpStdOp e) {
    Expression[] a = e.args();
    if ("toBooleanC".equals(e.opname())) {
      result = uRealThreshold(e);
      return;
    }
    result =
        switch (e.opname()) {
          case "and" -> Smt.and(List.of(arg(a[0]), arg(a[1])));
          case "or" -> Smt.or(List.of(arg(a[0]), arg(a[1])));
          case "not" -> Smt.not(arg(a[0], !positivePolarity));
          case "implies" ->
              Smt.app("=>", arg(a[0], !positivePolarity), arg(a[1], positivePolarity));
          case "=" -> comparison(a[0], a[1]);
          case "<>" -> Smt.not(comparison(a[0], a[1]));
          case ">=" -> Smt.app(">=", arg(a[0]), arg(a[1]));
          case "<=" -> Smt.app("<=", arg(a[0]), arg(a[1]));
          case ">" -> Smt.app(">", arg(a[0]), arg(a[1]));
          case "<" -> Smt.app("<", arg(a[0]), arg(a[1]));
          default -> throw unsupported("operator '" + e.opname() + "'");
        };
  }

  /**
   * Translates exactly {@code (object.urealAttr op crispLiteral).toBooleanC(confidence)}.
   *
   * <p>For nonzero uncertainty this is a linear boundary over the representative and uncertainty
   * terms. Zero uncertainty follows USE's ordinary strict/non-strict comparator instead. General
   * uncertain comparisons and nonconstant confidence remain deliberately unsupported.
   */
  private SmtTerm uRealThreshold(ExpStdOp projection) {
    Expression[] projectionArgs = projection.args();
    if (projectionArgs.length != 2 || !(projectionArgs[0] instanceof ExpStdOp comparison)) {
      throw unsupported("toBooleanC outside a direct comparison");
    }
    if (!List.of(">", ">=", "<", "<=").contains(comparison.opname())) {
      throw unsupported("toBooleanC over comparison operator '" + comparison.opname() + "'");
    }
    Expression[] comparisonArgs = comparison.args();
    if (comparisonArgs.length != 2 || !(comparisonArgs[0] instanceof ExpAttrOp attribute)) {
      throw unsupported("UReal threshold whose left operand is not an attribute access");
    }
    if (!attribute.type().isTypeOfUReal()) {
      throw unsupported("toBooleanC comparison over a non-UReal attribute");
    }

    BigDecimal literal = decimalLiteral(comparisonArgs[1], "comparison threshold");
    BigDecimal confidence = decimalLiteral(projectionArgs[1], "confidence threshold");
    URealThresholdBoundary.Enclosure enclosure = URealThresholdBoundary.enclose(confidence);
    BigDecimal standardizedBoundary = positivePolarity ? enclosure.upper() : enclosure.lower();

    VariableBinding binding = context.binding(variableNameOf(attribute.objExp()));
    AttributeValues values = context.attributeValues(binding.className(), attribute.attr().name());
    if (values.type() != AttributeType.UREAL) {
      throw unsupported("UReal threshold without paired value/uncertainty SMT terms");
    }
    SmtTerm representative = Smt.sym(values.valueNames().get(binding.slotIndex()));
    SmtTerm uncertainty = Smt.sym(values.uncertaintyNames().get(binding.slotIndex()));
    SmtTerm zero = Smt.realLit(BigDecimal.ZERO);
    SmtTerm exact = Smt.app(comparison.opname(), representative, Smt.realLit(literal));
    SmtTerm offset = Smt.app("*", uncertainty, Smt.realLit(standardizedBoundary));
    SmtTerm uncertainBoundary =
        comparison.opname().startsWith(">")
            ? Smt.app("+", Smt.realLit(literal), offset)
            : Smt.app("-", Smt.realLit(literal), offset);
    SmtTerm uncertain =
        Smt.app(
            comparison.opname().startsWith(">") ? ">=" : "<=", representative, uncertainBoundary);
    return Smt.or(
        List.of(
            Smt.and(List.of(Smt.eq(uncertainty, zero), exact)),
            Smt.and(List.of(Smt.app(">", uncertainty, zero), uncertain))));
  }

  private static BigDecimal decimalLiteral(Expression expression, String role) {
    if (expression instanceof ExpConstReal real) {
      return BigDecimal.valueOf(real.value());
    }
    if (expression instanceof ExpConstInteger integer) {
      return BigDecimal.valueOf(integer.value());
    }
    throw unsupported(role + " is not a crisp numeric literal");
  }

  private SmtTerm comparison(Expression l, Expression r) {
    if (l instanceof ExpVariable lv && r instanceof ExpVariable rv)
      return Smt.bool(context.binding(lv.getVarname()).equals(context.binding(rv.getVarname())));
    if (l instanceof ExpConstString s) return Smt.eq(resolve(s, r), arg(r));
    if (r instanceof ExpConstString s) return Smt.eq(arg(l), resolve(s, l));
    if (l instanceof ExpNavigation ln
        && r instanceof ExpNavigation rn
        && !ln.getDestination().isCollection()
        && !rn.getDestination().isCollection()) return navigationEquals(ln, rn);
    return Smt.eq(arg(l), arg(r));
  }

  /**
   * Single-valued navigation has no standalone SmtTerm (it names a linked object, not a value), so
   * equality between two of them ("c1.book = c2.book") is resolved as its own shape: there exists a
   * target slot both sides link to. The target association's own multiplicity (e.g. BelongsTo's
   * Book end [1]) already guarantees at most one such slot per source, via Task 3.2's degree
   * constraint -- this only needs to find it, not enforce uniqueness itself.
   */
  private SmtTerm navigationEquals(ExpNavigation left, ExpNavigation right) {
    String destClass = left.getDestination().cls().name();
    AssociationLinks links = context.linksFor(left.getDestination().association().name());
    ObjectSlots destSlots = context.slotsFor(destClass);
    VariableBinding leftSource = context.binding(variableNameOf(left.getObjectExpression()));
    VariableBinding rightSource = context.binding(variableNameOf(right.getObjectExpression()));
    List<SmtTerm> sharedTarget = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      sharedTarget.add(
          Smt.and(List.of(linkTerm(links, leftSource, k), linkTerm(links, rightSource, k))));
    }
    return Smt.or(sharedTarget);
  }

  /**
   * Resolves which side of {@code links} a source binding is on, and returns the SMT term for its
   * link to candidate slot {@code otherIndex}. Fails closed if the source's class matches neither
   * end (a reflexive association, which Library does not have and this slice does not support).
   */
  private SmtTerm linkTerm(AssociationLinks links, VariableBinding source, int otherIndex) {
    if (links.aEnd().className().equals(source.className()))
      return Smt.sym(links.linkNames()[source.slotIndex()][otherIndex]);
    if (links.bEnd().className().equals(source.className()))
      return Smt.sym(links.linkNames()[otherIndex][source.slotIndex()]);
    throw unsupported(
        "association " + links.associationName() + " does not connect class " + source.className());
  }

  private SmtTerm resolve(ExpConstString literal, Expression other) {
    if (!(other instanceof ExpAttrOp a))
      throw unsupported("string literal compared against a non-attribute expression");
    VariableBinding b = context.binding(variableNameOf(a.objExp()));
    AttributeDomain d = context.attributeDomain(b.className(), a.attr().name());
    int i = d.enumeratedValues().indexOf(literal.value());
    return Smt.intLit(i >= 0 ? BigInteger.valueOf(i) : UNDEFINED_STRING_SENTINEL);
  }

  private SmtTerm arg(Expression e) {
    return arg(e, positivePolarity);
  }

  private SmtTerm arg(Expression e, boolean polarity) {
    return translate(e, context, polarity);
  }

  private static String variableNameOf(Expression e) {
    if (e instanceof ExpVariable v) return v.getVarname();
    throw new SmtTranslationException(
        "attribute access on a non-variable receiver is not yet supported");
  }

  private static SmtTranslationException unsupported(String c) {
    return new SmtTranslationException("unsupported OCL construct in this translation slice: " + c);
  }

  @Override
  public void visitAllInstances(ExpAllInstances e) {
    throw unsupported("allInstances outside a forAll range is not yet supported");
  }

  @Override
  public void visitAny(ExpAny e) {
    throw unsupported("any");
  }

  @Override
  public void visitAsType(ExpAsType e) {
    throw unsupported("asType");
  }

  @Override
  public void visitBagLiteral(ExpBagLiteral e) {
    throw unsupported("Bag literal");
  }

  @Override
  public void visitCollect(ExpCollect e) {
    throw unsupported("collect");
  }

  @Override
  public void visitCollectNested(ExpCollectNested e) {
    throw unsupported("collectNested");
  }

  @Override
  public void visitConstEnum(ExpConstEnum e) {
    throw unsupported("enum literal");
  }

  @Override
  public void visitConstReal(ExpConstReal e) {
    throw unsupported("Real literal");
  }

  @Override
  public void visitConstUBoolean(ExpConstUBoolean e) {
    throw unsupported("UBoolean literal");
  }

  @Override
  public void visitConstSBoolean(ExpConstSBoolean e) {
    throw unsupported("SBoolean literal");
  }

  @Override
  public void visitConstUInteger(ExpConstUInteger e) {
    throw unsupported("UInteger literal");
  }

  @Override
  public void visitConstUReal(ExpConstUReal e) {
    throw unsupported("UReal literal");
  }

  @Override
  public void visitConstUString(ExpConstUString e) {
    throw unsupported("UString literal");
  }

  @Override
  public void visitEmptyCollection(ExpEmptyCollection e) {
    throw unsupported("empty collection");
  }

  /**
   * Only "source.role->exists(v1, v2 | body)" with exactly two loop variables ranging over the SAME
   * collection-valued navigation is supported -- the shape noDoubleBorrowings needs. Unlike
   * ForAll's per-slot existence guard, this needs a full cross product: OCL's exists ranges over
   * ALL pairs, including v1==v2 (the body's own "&lt;&gt;" check, where present, excludes that case
   * -- it is not excluded here). Link membership is an SMT term, not a Java boolean, so every
   * candidate pair contributes one disjunct guarded by both link memberships, not a compile-time
   * skip.
   */
  @Override
  public void visitExists(ExpExists e) {
    if (e.getVariableDeclarations().size() != 2)
      throw unsupported("exists with a variable count other than two");
    if (!(e.getRangeExpression() instanceof ExpNavigation range))
      throw unsupported("exists over a range other than a collection-valued navigation");
    if (!range.getDestination().isCollection())
      throw unsupported("exists over a single-valued navigation");
    VariableBinding source = context.binding(variableNameOf(range.getObjectExpression()));
    String destClass = range.getDestination().cls().name();
    AssociationLinks links = context.linksFor(range.getDestination().association().name());
    ObjectSlots destSlots = context.slotsFor(destClass);
    String var1 = e.getVariableDeclarations().varDecl(0).name();
    String var2 = e.getVariableDeclarations().varDecl(1).name();

    List<SmtTerm> disjuncts = new ArrayList<>();
    for (int i = 0; i < destSlots.capacity(); i++) {
      SmtTerm link1 = linkTerm(links, source, i);
      for (int j = 0; j < destSlots.capacity(); j++) {
        SmtTerm link2 = linkTerm(links, source, j);
        TranslationContext extended =
            context
                .withBinding(var1, new VariableBinding(destClass, i))
                .withBinding(var2, new VariableBinding(destClass, j));
        SmtTerm body = translate(e.getQueryExpression(), extended, positivePolarity);
        disjuncts.add(Smt.and(List.of(link1, link2, body)));
      }
    }
    result = Smt.or(disjuncts);
  }

  @Override
  public void visitForAll(ExpForAll e) {
    if (e.getVariableDeclarations().size() != 1)
      throw unsupported("forAll with more than one loop variable");
    if (!(e.getRangeExpression() instanceof ExpAllInstances all))
      throw unsupported("forAll over a range other than X.allInstances");
    String loopVariable = e.getVariableDeclarations().varDecl(0).name();
    List<SmtTerm> conjuncts = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
      TranslationContext extended = context.withBinding(loopVariable, slot.binding());
      SmtTerm body = translate(e.getQueryExpression(), extended, positivePolarity);
      conjuncts.add(Smt.app("=>", Smt.sym(slot.existsName()), body));
    }
    result = Smt.and(conjuncts);
  }

  @Override
  public void visitIf(ExpIf e) {
    throw unsupported("if");
  }

  @Override
  public void visitIsKindOf(ExpIsKindOf e) {
    throw unsupported("isKindOf");
  }

  @Override
  public void visitIsTypeOf(ExpIsTypeOf e) {
    throw unsupported("isTypeOf");
  }

  @Override
  public void visitIsUnique(ExpIsUnique e) {
    throw unsupported("isUnique");
  }

  @Override
  public void visitIterate(ExpIterate e) {
    throw unsupported("iterate");
  }

  @Override
  public void visitLet(ExpLet e) {
    throw unsupported("let");
  }

  @Override
  public void visitNavigation(ExpNavigation e) {
    throw unsupported("navigation");
  }

  @Override
  public void visitObjAsSet(ExpObjAsSet e) {
    throw unsupported("objAsSet");
  }

  @Override
  public void visitInstanceOp(ExpInstanceOp e) {
    throw unsupported("instance operation");
  }

  @Override
  public void visitObjRef(ExpObjRef e) {
    throw unsupported("object reference");
  }

  @Override
  public void visitOne(ExpOne e) {
    throw unsupported("one");
  }

  @Override
  public void visitOrderedSetLiteral(ExpOrderedSetLiteral e) {
    throw unsupported("OrderedSet literal");
  }

  @Override
  public void visitQuery(ExpQuery e) {
    throw unsupported("query");
  }

  @Override
  public void visitReject(ExpReject e) {
    throw unsupported("reject");
  }

  @Override
  public void visitWithValue(ExpressionWithValue e) {
    throw unsupported("withValue");
  }

  @Override
  public void visitSelect(ExpSelect e) {
    throw unsupported("select");
  }

  @Override
  public void visitSequenceLiteral(ExpSequenceLiteral e) {
    throw unsupported("Sequence literal");
  }

  @Override
  public void visitSetLiteral(ExpSetLiteral e) {
    throw unsupported("Set literal");
  }

  @Override
  public void visitSortedBy(ExpSortedBy e) {
    throw unsupported("sortedBy");
  }

  @Override
  public void visitTupleLiteral(ExpTupleLiteral e) {
    throw unsupported("Tuple literal");
  }

  @Override
  public void visitTupleSelectOp(ExpTupleSelectOp e) {
    throw unsupported("tuple select");
  }

  @Override
  public void visitClosure(ExpClosure e) {
    throw unsupported("closure");
  }

  @Override
  public void visitOclInState(ExpOclInState e) {
    throw unsupported("oclInState");
  }

  @Override
  public void visitVarDeclList(VarDeclList e) {
    throw unsupported("VarDeclList");
  }

  @Override
  public void visitVarDecl(VarDecl e) {
    throw unsupported("VarDecl");
  }

  @Override
  public void visitObjectByUseId(ExpObjectByUseId e) {
    throw unsupported("objectByUseId");
  }

  @Override
  public void visitConstUnlimitedNatural(ExpConstUnlimitedNatural e) {
    throw unsupported("UnlimitedNatural literal");
  }

  @Override
  public void visitSelectByKind(ExpSelectByKind e) {
    throw unsupported("selectByKind");
  }

  @Override
  public void visitExpSelectByType(ExpSelectByType e) {
    throw unsupported("selectByType");
  }

  @Override
  public void visitRange(ExpRange e) {
    throw unsupported("range");
  }

  @Override
  public void visitNavigationClassifierSource(ExpNavigationClassifierSource e) {
    throw unsupported("navigationClassifierSource");
  }

  @Override
  public void visitUSelectC(ExpUSelectC e) {
    throw unsupported("USelectC");
  }

  @Override
  public void visitUSelect(ExpUSelect e) {
    throw unsupported("USelect");
  }
}
