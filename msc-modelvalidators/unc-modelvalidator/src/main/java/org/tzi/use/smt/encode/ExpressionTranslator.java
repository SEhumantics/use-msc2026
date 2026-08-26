package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.ocl.expr.*;

/** Translates the verified leaf-level Library OCL fragment and fails closed on everything else. */
public final class ExpressionTranslator implements ExpressionVisitor {
  private static final BigInteger UNDEFINED_STRING_SENTINEL = BigInteger.valueOf(-1);
  private final TranslationContext context;
  private final TranslationMode mode;
  private final boolean positivePolarity;
  private TranslatedExpression result;

  private ExpressionTranslator(
      TranslationContext c, TranslationMode mode, boolean positivePolarity) {
    context = c;
    this.mode = mode;
    this.positivePolarity = positivePolarity;
  }

  public static SmtTerm translate(Expression e, TranslationContext c) {
    return translate(e, c, TranslationMode.UNCERTAIN).value();
  }

  public static TranslatedExpression translate(
      Expression e, TranslationContext c, TranslationMode mode) {
    return translate(e, c, mode, true);
  }

  private static TranslatedExpression translate(
      Expression e, TranslationContext c, TranslationMode mode, boolean positivePolarity) {
    ExpressionTranslator t = new ExpressionTranslator(c, mode, positivePolarity);
    e.processWithVisitor(t);
    return t.result;
  }

  private static TranslatedExpression defined(SmtTerm value) {
    return new TranslatedExpression(Smt.bool(true), value);
  }

  @Override
  public void visitConstInteger(ExpConstInteger e) {
    result = defined(Smt.intLit(BigInteger.valueOf(e.value())));
  }

  @Override
  public void visitConstString(ExpConstString e) {
    throw unsupported(
        "free-standing string literal ('" + e.value() + "') outside an attribute comparison");
  }

  @Override
  public void visitConstBoolean(ExpConstBoolean e) {
    result = defined(Smt.bool(e.value()));
  }

  @Override
  public void visitUndefined(ExpUndefined e) {
    SmtTerm placeholder =
        e.type().isTypeOfString()
            ? Smt.intLit(UNDEFINED_STRING_SENTINEL)
            : e.type().isTypeOfReal() || e.type().isTypeOfUReal()
                ? Smt.realLit(BigDecimal.ZERO)
                : e.type().isTypeOfBoolean() || e.type().isTypeOfUBoolean()
                    ? Smt.bool(false)
                    : Smt.intLit(BigInteger.ZERO);
    result = new TranslatedExpression(Smt.bool(false), placeholder);
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
    result = defined(Smt.sym(v.valueNames().get(b.slotIndex())));
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
          case "and" -> booleanAnd(argResult(a[0]), argResult(a[1]));
          case "or" -> booleanOr(argResult(a[0]), argResult(a[1]));
          case "not" -> {
            TranslatedExpression operand = argResult(a[0], !positivePolarity);
            yield new TranslatedExpression(operand.defined(), Smt.not(operand.value()));
          }
          case "implies" ->
              booleanOr(
                  negate(argResult(a[0], !positivePolarity)), argResult(a[1], positivePolarity));
          case "=" -> comparison(a[0], a[1]);
          case "<>" -> negate(comparison(a[0], a[1]));
          case ">=", "<=", ">", "<" -> orderedComparison(e.opname(), a[0], a[1]);
          default -> throw unsupported("operator '" + e.opname() + "'");
        };
  }

  private static TranslatedExpression booleanAnd(
      TranslatedExpression left, TranslatedExpression right) {
    SmtTerm leftFalse = Smt.and(List.of(left.defined(), Smt.not(left.value())));
    SmtTerm rightFalse = Smt.and(List.of(right.defined(), Smt.not(right.value())));
    SmtTerm bothDefined = Smt.and(List.of(left.defined(), right.defined()));
    return new TranslatedExpression(
        Smt.or(List.of(bothDefined, leftFalse, rightFalse)),
        Smt.and(List.of(left.value(), right.value())));
  }

  private static TranslatedExpression booleanOr(
      TranslatedExpression left, TranslatedExpression right) {
    SmtTerm leftTrue = Smt.and(List.of(left.defined(), left.value()));
    SmtTerm rightTrue = Smt.and(List.of(right.defined(), right.value()));
    SmtTerm bothDefined = Smt.and(List.of(left.defined(), right.defined()));
    return new TranslatedExpression(
        Smt.or(List.of(bothDefined, leftTrue, rightTrue)),
        Smt.or(List.of(left.value(), right.value())));
  }

  private static TranslatedExpression negate(TranslatedExpression expression) {
    return new TranslatedExpression(expression.defined(), Smt.not(expression.value()));
  }

  private TranslatedExpression orderedComparison(
      String operator, Expression left, Expression right) {
    TranslatedExpression l = argResult(left);
    TranslatedExpression r = argResult(right);
    return new TranslatedExpression(
        Smt.and(List.of(l.defined(), r.defined())), Smt.app(operator, l.value(), r.value()));
  }

  /**
   * Translates exactly {@code (object.urealAttr op crispLiteral).toBooleanC(confidence)}.
   *
   * <p>For nonzero uncertainty this is a linear boundary over the representative and uncertainty
   * terms. Zero uncertainty follows USE's ordinary strict/non-strict comparator instead. General
   * uncertain comparisons and nonconstant confidence remain deliberately unsupported.
   */
  private TranslatedExpression uRealThreshold(ExpStdOp projection) {
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

    VariableBinding binding = context.binding(variableNameOf(attribute.objExp()));
    AttributeValues values = context.attributeValues(binding.className(), attribute.attr().name());
    if (values.type() != AttributeType.UREAL) {
      throw unsupported("UReal threshold without paired value/uncertainty SMT terms");
    }
    SmtTerm representative = Smt.sym(values.valueNames().get(binding.slotIndex()));
    SmtTerm uncertainty = Smt.sym(values.uncertaintyNames().get(binding.slotIndex()));
    SmtTerm zero = Smt.realLit(BigDecimal.ZERO);
    SmtTerm exact = Smt.app(comparison.opname(), representative, Smt.realLit(literal));
    if (mode == TranslationMode.NOMINAL) {
      return defined(exact);
    }
    URealThresholdBoundary.Enclosure enclosure = URealThresholdBoundary.enclose(confidence);
    BigDecimal standardizedBoundary = positivePolarity ? enclosure.upper() : enclosure.lower();
    SmtTerm offset = Smt.app("*", uncertainty, Smt.realLit(standardizedBoundary));
    SmtTerm uncertainBoundary =
        comparison.opname().startsWith(">")
            ? Smt.app("+", Smt.realLit(literal), offset)
            : Smt.app("-", Smt.realLit(literal), offset);
    SmtTerm uncertain =
        Smt.app(
            comparison.opname().startsWith(">") ? ">=" : "<=", representative, uncertainBoundary);
    return defined(
        Smt.or(
            List.of(
                Smt.and(List.of(Smt.eq(uncertainty, zero), exact)),
                Smt.and(List.of(Smt.app(">", uncertainty, zero), uncertain)))));
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

  private TranslatedExpression comparison(Expression l, Expression r) {
    // USE equality is deliberately non-strict for the explicit oclUndefined literal: it is how
    // legacy invariants state that an attribute must be present. Invalid navigation remains a
    // different case below and propagates undefinedness.
    if (l instanceof ExpUndefined || r instanceof ExpUndefined) {
      return defined(Smt.bool(l instanceof ExpUndefined && r instanceof ExpUndefined));
    }
    if (l instanceof ExpVariable lv && r instanceof ExpVariable rv)
      return defined(
          Smt.bool(context.binding(lv.getVarname()).equals(context.binding(rv.getVarname()))));
    if (l instanceof ExpConstString s) {
      TranslatedExpression other = argResult(r);
      return new TranslatedExpression(other.defined(), Smt.eq(resolve(s, r), other.value()));
    }
    if (r instanceof ExpConstString s) {
      TranslatedExpression other = argResult(l);
      return new TranslatedExpression(other.defined(), Smt.eq(other.value(), resolve(s, l)));
    }
    if (l instanceof ExpNavigation ln
        && r instanceof ExpNavigation rn
        && !ln.getDestination().isCollection()
        && !rn.getDestination().isCollection()) return navigationEquals(ln, rn);
    TranslatedExpression left = argResult(l);
    TranslatedExpression right = argResult(r);
    return new TranslatedExpression(
        Smt.and(List.of(left.defined(), right.defined())), Smt.eq(left.value(), right.value()));
  }

  /**
   * Single-valued navigation has no standalone SmtTerm (it names a linked object, not a value), so
   * equality between two of them ("c1.book = c2.book") is resolved as its own shape: there exists a
   * target slot both sides link to. The target association's own multiplicity (e.g. BelongsTo's
   * Book end [1]) already guarantees at most one such slot per source, via Task 3.2's degree
   * constraint -- this only needs to find it, not enforce uniqueness itself.
   */
  private TranslatedExpression navigationEquals(ExpNavigation left, ExpNavigation right) {
    String destClass = left.getDestination().cls().name();
    AssociationLinks links = context.linksFor(left.getDestination().association().name());
    ObjectSlots destSlots = context.slotsFor(destClass);
    VariableBinding leftSource = context.binding(variableNameOf(left.getObjectExpression()));
    VariableBinding rightSource = context.binding(variableNameOf(right.getObjectExpression()));
    List<SmtTerm> sharedTarget = new ArrayList<>();
    List<SmtTerm> leftTargets = new ArrayList<>();
    List<SmtTerm> rightTargets = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      SmtTerm leftLink = linkTerm(links, leftSource, k);
      SmtTerm rightLink = linkTerm(links, rightSource, k);
      leftTargets.add(leftLink);
      rightTargets.add(rightLink);
      sharedTarget.add(Smt.and(List.of(leftLink, rightLink)));
    }
    return new TranslatedExpression(
        Smt.and(List.of(Smt.or(leftTargets), Smt.or(rightTargets))), Smt.or(sharedTarget));
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

  private TranslatedExpression argResult(Expression e) {
    return argResult(e, positivePolarity);
  }

  private TranslatedExpression argResult(Expression e, boolean polarity) {
    return translate(e, context, mode, polarity);
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

    List<SmtTerm> trueCandidates = new ArrayList<>();
    List<SmtTerm> definedCandidates = new ArrayList<>();
    for (int i = 0; i < destSlots.capacity(); i++) {
      SmtTerm link1 = linkTerm(links, source, i);
      for (int j = 0; j < destSlots.capacity(); j++) {
        SmtTerm link2 = linkTerm(links, source, j);
        TranslationContext extended =
            context
                .withBinding(var1, new VariableBinding(destClass, i))
                .withBinding(var2, new VariableBinding(destClass, j));
        SmtTerm member = Smt.and(List.of(link1, link2));
        TranslatedExpression body =
            translate(e.getQueryExpression(), extended, mode, positivePolarity);
        trueCandidates.add(Smt.and(List.of(member, body.defined(), body.value())));
        definedCandidates.add(Smt.app("=>", member, body.defined()));
      }
    }
    SmtTerm anyTrue = Smt.or(trueCandidates);
    result =
        new TranslatedExpression(Smt.or(List.of(anyTrue, Smt.and(definedCandidates))), anyTrue);
  }

  @Override
  public void visitForAll(ExpForAll e) {
    if (e.getVariableDeclarations().size() != 1)
      throw unsupported("forAll with more than one loop variable");
    if (!(e.getRangeExpression() instanceof ExpAllInstances all))
      throw unsupported("forAll over a range other than X.allInstances");
    String loopVariable = e.getVariableDeclarations().varDecl(0).name();
    List<SmtTerm> valueConjuncts = new ArrayList<>();
    List<SmtTerm> definedConjuncts = new ArrayList<>();
    List<SmtTerm> falseCandidates = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
      TranslationContext extended = context.withBinding(loopVariable, slot.binding());
      SmtTerm exists = Smt.sym(slot.existsName());
      TranslatedExpression body =
          translate(e.getQueryExpression(), extended, mode, positivePolarity);
      valueConjuncts.add(Smt.app("=>", exists, body.value()));
      definedConjuncts.add(Smt.app("=>", exists, body.defined()));
      falseCandidates.add(Smt.and(List.of(exists, body.defined(), Smt.not(body.value()))));
    }
    result =
        new TranslatedExpression(
            Smt.or(List.of(Smt.or(falseCandidates), Smt.and(definedConjuncts))),
            Smt.and(valueConjuncts));
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
