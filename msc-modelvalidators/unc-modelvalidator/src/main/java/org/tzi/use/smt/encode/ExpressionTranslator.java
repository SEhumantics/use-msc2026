package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MNavigableElement;
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
        FragmentBoundary.TIER_2,
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
        FragmentBoundary.TIER_1,
        "bare variable reference '" + e.getVarname() + "' outside an attribute access");
  }

  @Override
  public void visitAttrOp(ExpAttrOp e) {
    // The receiver is either a bare context variable (the ordinary case, below) or exactly one
    // single-valued navigation hop -- e.g. Sudoku's `self.row.index` (a plain ExpNavigation) or
    // AssociationClass's `e.employer.budget` (an ExpNavigationClassifierSource, OCL's OTHER
    // navigation-shaped node, used when the navigation starts at an association/association-class
    // instance rather than an ordinary object). Both expose the same (object expression,
    // destination) shape, so both route through the same navigatedAttribute helper; anything
    // beyond that -- a second hop, a collection destination -- fails closed inside it rather than
    // being handled here.
    if (e.objExp() instanceof ExpNavigation navigation) {
      result =
          navigatedAttribute(
              navigation.getObjectExpression(), navigation.getDestination(), e.attr());
      return;
    }
    if (e.objExp() instanceof ExpNavigationClassifierSource navigation) {
      result =
          navigatedAttribute(
              navigation.getObjectExpression(), navigation.getDestination(), e.attr());
      return;
    }
    VariableBinding b = context.binding(variableNameOf(e.objExp()));
    AttributeValues v = context.attributeValues(b.className(), e.attr().name());
    guardAgainstUncertainAttribute(v);
    result = defined(Smt.sym(v.valueNames().get(b.slotIndex())));
  }

  /**
   * Attribute access at the far end of exactly one single-valued navigation hop -- {@code
   * <var>.<role>.<attr>}, e.g. Sudoku's {@code self.row.index} or AssociationClass's {@code
   * e.employer.budget}. There is no standalone {@link SmtTerm} for the intermediate navigated
   * object (see {@link #definednessOf}), so the attribute's value is built directly: for each
   * destination slot k, "if the source links there ({@link #linkTerm}), the value is that slot's
   * attribute symbol" ({@link TranslationContext#attributeValues}) -- the same per-slot disjunction
   * {@link #singleValuedNavigationDefined} and {@link #navigationEquals} already build for "is
   * there a link at all" and "do two navigations share a target", reused rather than reinvented.
   *
   * <p>Deliberately ONE hop: {@code objectExpression} must itself be a bare variable, not another
   * navigation (a chained {@code self.a.b.c} is refused rather than silently generalized into a
   * recursive evaluator -- neither real corpus invariant needs more than one hop), and {@code
   * destination} must be single-valued (a collection-valued destination needs {@code collect}
   * semantics; unreachable through USE's own OCL front end today, since {@code x.attr} on a
   * collection-typed x is desugared into {@code x->collect($e|$e.attr)} before an {@link ExpAttrOp}
   * is ever built -- see {@code ASTOperationExpression} cases {@code SRC_COLLECTION_TYPE + DOT} --
   * but guarded here anyway in case that ever changes).
   */
  private TranslatedExpression navigatedAttribute(
      Expression objectExpression, MNavigableElement destination, MAttribute attribute) {
    if (destination.isCollection()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "attribute access after a collection-valued navigation is not yet supported");
    }
    if (!(objectExpression instanceof ExpVariable sourceVar)) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "attribute access after more than one navigation hop is not yet supported");
    }
    String destClass = destination.cls().name();
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = context.slotsFor(destClass);
    VariableBinding source = context.binding(sourceVar.getVarname());
    AttributeValues v = context.attributeValues(destClass, attribute.name());
    guardAgainstUncertainAttribute(v);

    List<SmtTerm> targets = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      targets.add(linkTerm(links, source, k));
    }
    return new TranslatedExpression(
        Smt.or(targets), selectLinkedValue(links, source, destSlots, v));
  }

  /**
   * The value at whichever destination slot {@code source} links to, as a nested {@code ite} chain
   * over {@link #linkTerm} -- the same primitive {@link #singleValuedNavigationDefined} and {@link
   * #navigationEquals} use, applied here to SELECT a value rather than just test existence. Exactly
   * one linked slot is possible per Task 3.2's degree constraint on the navigated association, so
   * which of the (mutually exclusive, in a well-formed instance) conditions is "the" true one does
   * not matter to the chain's correctness.
   *
   * <p>An empty destination population (capacity 0) has no slot to select and is therefore
   * correctly undefined -- {@link #navigatedAttribute}'s own {@code Smt.or(targets)} over zero
   * targets is already {@code false} -- but the VALUE half still has to be a well-sorted term
   * regardless (SMT-LIB sort-checks every emitted term, guard or not), hence the type-derived
   * placeholder.
   */
  private SmtTerm selectLinkedValue(
      AssociationLinks links, VariableBinding source, ObjectSlots destSlots, AttributeValues v) {
    int capacity = destSlots.capacity();
    if (capacity == 0) {
      return placeholderOfSort(v.type());
    }
    SmtTerm value = Smt.sym(v.valueNames().get(capacity - 1));
    for (int k = capacity - 2; k >= 0; k--) {
      value = Smt.ite(linkTerm(links, source, k), Smt.sym(v.valueNames().get(k)), value);
    }
    return value;
  }

  /**
   * A well-sorted, never-actually-selected filler for {@link #selectLinkedValue}'s capacity-0
   * corner. Only crisp sorts reach here: {@link #guardAgainstUncertainAttribute} already refused
   * every uncertain {@link AttributeType} before this is called.
   */
  private static SmtTerm placeholderOfSort(AttributeType type) {
    return switch (type) {
      case REAL -> Smt.realLit(BigDecimal.ZERO);
      case BOOLEAN -> Smt.bool(false);
      case STRING, INTEGER -> Smt.intLit(BigInteger.ZERO);
      default ->
          throw new IllegalStateException(
              "uncertain attribute type reached a crisp placeholder: " + type);
    };
  }

  /**
   * The bare-U-type refusal shared by a direct attribute access ({@code x.attr}) and one reached
   * through a single navigation hop ({@code x.role.attr}) -- the reason is identical either way: a
   * bare uncertain value outside a supported projection, regardless of how the source object was
   * reached.
   */
  private static void guardAgainstUncertainAttribute(AttributeValues v) {
    if (v.type().isUncertain()) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "bare "
              + switch (v.type()) {
                case UREAL -> "UReal";
                case UINTEGER -> "UInteger";
                case USTRING -> "UString";
                default -> "UBoolean";
              }
              + " attribute access outside a supported "
              + switch (v.type()) {
                case UBOOLEAN -> "toBooleanC projection";
                // A UString slot shares its SMT Int sort with a crisp String attribute's index, so
                // falling through here would decode as a plain String and silently drop the
                // confidence. It is refused rather than allowed to look like it worked.
                case USTRING -> "equality projection";
                default -> "threshold comparison";
              });
    }
  }

  @Override
  public void visitStdOp(ExpStdOp e) {
    Expression[] a = e.args();
    if ("toBooleanC".equals(e.opname())) {
      result = uTypeThreshold(e);
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
          case "size" -> collectionSize(a[0]);
          default ->
              throw unsupported(boundaryOfOperator(e.opname()), "operator '" + e.opname() + "'");
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
   * Translates exactly {@code (object.uTypedAttr op crispLiteral).toBooleanC(confidence)}, for
   * either U-type family.
   *
   * <p>For nonzero uncertainty this is a linear boundary over the representative and uncertainty
   * terms. Zero uncertainty follows USE's ordinary strict/non-strict comparator instead. General
   * uncertain comparisons and nonconstant confidence remain deliberately unsupported.
   *
   * <p><b>Why {@code UInteger} needs no second boundary.</b> USE's own {@code UInteger.gt} is
   * literally {@code toUReal().gt(number.toUReal())}, and OCL's {@code Op_number_greater} widens
   * through {@code URealValue.valueOf} before comparing at all, so the uncertain reading of a
   * UInteger comparison IS its UReal widening -- the same normal-CDF threshold, bisected once in
   * {@link URealThresholdBoundary}. The only difference reaching the solver is that the
   * representative is an Int, so the emitted inequality lifts it with {@code to_real} and the
   * solver's integer theory then rounds the real-valued boundary to the least admissible integer by
   * itself. Deriving a separate integer boundary here would be duplicating verified arithmetic and
   * inviting the two copies to drift.
   */
  private TranslatedExpression uTypeThreshold(ExpStdOp projection) {
    Expression[] projectionArgs = projection.args();
    if (projectionArgs.length != 2) {
      throw unsupported(FragmentBoundary.UTYPE_CORE, "toBooleanC with a non-binary argument list");
    }
    // Three disjoint families reach this operation, and the OPERAND decides which. A numeric
    // ordered comparison is UReal/UInteger's evaluator-derived normal-CDF threshold; a UString
    // comparison and anything else UBoolean-typed lower through UBooleanProbability, whose
    // probability is carried or computed directly rather than derived from a normal CDF.
    //
    // The UString exclusion is load-bearing, not tidiness: USE types `<`/`<=`/`>`/`>=` over two
    // UStrings as a UBoolean, so without it an ORDERED UString comparison would take the numeric
    // route and be refused as "a non-U-typed attribute" -- a wrong reason for a real refusal. It
    // belongs to the unrestricted-string boundary, which is where UBooleanProbability puts it.
    if (!(projectionArgs[0] instanceof ExpStdOp comparison)
        || !List.of(">", ">=", "<", "<=").contains(comparison.opname())
        || mentionsUString(comparison)) {
      return uBooleanThreshold(projectionArgs[0], projectionArgs[1]);
    }
    Expression[] comparisonArgs = comparison.args();
    if (comparisonArgs.length != 2 || !(comparisonArgs[0] instanceof ExpAttrOp attribute)) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UReal threshold whose left operand is not an attribute access");
    }
    if (!attribute.type().isTypeOfUReal() && !attribute.type().isTypeOfUInteger()) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE, "toBooleanC comparison over a non-U-typed attribute");
    }

    BigDecimal literal =
        decimalLiteral(
            comparisonArgs[1],
            "comparison threshold",
            FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN);
    BigDecimal confidence =
        decimalLiteral(projectionArgs[1], "confidence threshold", FragmentBoundary.UTYPE_CORE);

    VariableBinding binding = context.binding(variableNameOf(attribute.objExp()));
    AttributeValues values = context.attributeValues(binding.className(), attribute.attr().name());
    if (!values.type().isPairedUType()) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "U-type threshold without paired value/uncertainty SMT terms");
    }
    SmtTerm declared = Smt.sym(values.valueNames().get(binding.slotIndex()));
    // The representative is an Int for UInteger and a Real for UReal, while the boundary is always
    // a Real -- so the arithmetic comparison lifts it. The DECLARED symbol stays an Int, which is
    // precisely what leaves the rounding to the solver's integer theory.
    //
    // The lift is a PORTABILITY measure, not a correctness one, and was measured rather than
    // assumed: deleting it leaves every UInteger test green, because Z3 silently coerces an Int
    // into mixed Int/Real arithmetic. SMT-LIB 2.6 does not oblige a solver to, and this project's
    // standing constraint is that the solver stays swappable over portable SMT-LIB text, so the
    // explicit to_real stays. What IS load-bearing is the Int SORT of the declared symbol:
    // declaring it Real instead makes the free-range fixture come back with the boundary itself,
    // 891857137/134217728 = 6.644853480160236, rather than 7.
    SmtTerm representative =
        values.type() == AttributeType.UINTEGER ? Smt.app("to_real", declared) : declared;
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

  /**
   * Translates {@code <UBoolean expression>.toBooleanC(theta)}, the third U-type family's only
   * projection.
   *
   * <p>All of the real work -- and the whole reason this family is not a two-line addition -- lives
   * in {@link UBooleanProbability}: the source's {@code and}/{@code or}/{@code implies} rules are
   * PRODUCTS of probabilities, which {@code QF_LIRA} cannot express over two solver variables, so
   * the composition is enumerated over the finitely many configured choices at translation time and
   * the solver is only ever asked WHICH choice was taken. See that class for the argument that the
   * emitted script stays inside the pinned logic.
   *
   * <p>Definedness is a constant {@code true}: every operand is a stored attribute whose existence
   * guard already forces it to carry one of its configured probabilities, and {@code toBooleanC}
   * itself is total for a confidence inside {@code [0,1]}. A confidence outside {@code [0,1]} makes
   * USE's own {@code Op_uBoolean_toBooleanC} yield {@code UndefinedValue}; rather than encode a
   * whole invariant as undefined on a constant the modeller almost certainly mistyped, that fails
   * closed.
   */
  private TranslatedExpression uBooleanThreshold(Expression operand, Expression confidenceArg) {
    if (!operand.type().isTypeOfUBoolean()) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "toBooleanC over the non-UBoolean operand '" + operand + "'");
    }
    BigDecimal confidence =
        decimalLiteral(confidenceArg, "confidence threshold", FragmentBoundary.UTYPE_CORE);
    if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UBoolean confidence threshold outside [0,1] (USE yields UndefinedValue there), got "
              + confidence);
    }
    if (mode == TranslationMode.NOMINAL) {
      // The independent oracle's erasure is E(b.toBooleanC(theta)) = E_B(b), and
      // NominalErasureEvaluator defines E_B for exactly two shapes: "a STORED UBoolean probability
      // uses the p >= 0.5 rule", and a COMPARISON, which "becomes its CRISP comparison" -- for a
      // UString that means erasing each operand to its representative spelling and comparing those.
      // The SMT nominal arm must refuse exactly where the oracle refuses, or a FRAGILE verdict
      // could rest on a nominal reading nothing can independently confirm.
      if (isUStringEquality(operand)) {
        // lowerNominal, not lower: erasure discards the confidence outright rather than reading
        // p >= 0.5 off the confidence rule, and at c < 0.5 with a matching spelling the two
        // genuinely disagree.
        return defined(
            UBooleanProbability.select(UBooleanProbability.lowerNominal(operand, context), 0.5));
      }
      if (!(operand instanceof ExpAttrOp)) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "nominal erasure of the composed UBoolean expression '"
                + operand
                + "': the proposal's erasure table defines E_B only for a STORED UBoolean"
                + " probability (p >= 0.5) and for a comparison erased to its crisp form, and"
                + " NominalErasureEvaluator refuses the rest");
      }
      return defined(UBooleanProbability.select(UBooleanProbability.lower(operand, context), 0.5));
    }
    return defined(
        UBooleanProbability.select(
            UBooleanProbability.lower(operand, context), confidence.doubleValue()));
  }

  /** True when either operand of a binary operation is UString-typed. */
  private static boolean mentionsUString(ExpStdOp operation) {
    Expression[] args = operation.args();
    return args.length == 2
        && (args[0].type().isTypeOfUString() || args[1].type().isTypeOfUString());
  }

  /**
   * True for {@code <UString> = ...} / {@code <UString> <> ...}, the one UBoolean-typed shape whose
   * nominal erasure the independent oracle DOES define -- as a crisp comparison of the erased
   * spellings, not as {@code p >= 0.5} over a confidence.
   */
  private static boolean isUStringEquality(Expression operand) {
    if (!(operand instanceof ExpStdOp op) || op.args().length != 2) {
      return false;
    }
    if (!List.of("=", "<>").contains(op.opname())) {
      return false;
    }
    return op.args()[0].type().isTypeOfUString() || op.args()[1].type().isTypeOfUString();
  }

  /**
   * @param boundary a non-literal COMPARISON operand is 7.2's excluded general
   *     uncertain-versus-uncertain comparison -- the core supports "comparison against one exact
   *     operand" and nothing wider -- while a non-literal CONFIDENCE operand is still inside the
   *     {@code toBooleanC} core, just not in the fixed-threshold shape this slice encodes.
   */
  private static BigDecimal decimalLiteral(
      Expression expression, String role, FragmentBoundary boundary) {
    if (expression instanceof ExpConstReal real) {
      return BigDecimal.valueOf(real.value());
    }
    if (expression instanceof ExpConstInteger integer) {
      return BigDecimal.valueOf(integer.value());
    }
    throw unsupported(boundary, role + " is not a crisp numeric literal");
  }

  /**
   * 7.1 puts "integer arithmetic and comparisons" in Tier 2 and the collection iterators in Tier 3;
   * an operator in neither list is out of the required first fragment. Classifying by name keeps
   * one unimplemented arithmetic operator from being reported as the same kind of gap as, say,
   * {@code sortedBy}.
   */
  private static FragmentBoundary boundaryOfOperator(String opname) {
    if (List.of("+", "-", "*", "/", "div", "mod", "abs", "max", "min").contains(opname)) {
      return FragmentBoundary.TIER_2;
    }
    if (List.of(
            "isEmpty",
            "notEmpty",
            "includes",
            "excludes",
            "includesAll",
            "excludesAll",
            "union",
            "intersection",
            "including",
            "excluding",
            "asSet",
            "asBag",
            "asSequence",
            "sum",
            "count",
            "flatten")
        .contains(opname)) {
      return FragmentBoundary.TIER_3;
    }
    if (List.of("oclIsUndefined", "oclIsInvalid", "oclAsType", "oclIsKindOf", "oclIsTypeOf")
        .contains(opname)) {
      return FragmentBoundary.TIER_2;
    }
    if (List.of("toBooleanC", "confidence", "probability", "uncertainty").contains(opname)) {
      return FragmentBoundary.UTYPE_CORE;
    }
    if (List.of("exp", "log", "sqrt", "sin", "cos", "tan", "power", "floor", "round")
        .contains(opname)) {
      return FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL;
    }
    return FragmentBoundary.BEYOND_FIRST_FRAGMENT;
  }

  /**
   * USE's own {@code =} is TOTAL, not strict. {@code Op_equal} declares {@code kind() == SPECIAL},
   * so {@link ExpStdOp#eval} hands it undefined arguments instead of short-circuiting to undefined
   * the way it does for an {@code OPERATION}, and {@code Op_equal.evalBooleanResult} then returns
   * {@code BooleanValue.get(args[1].isUndefined())} when the left operand is undefined and an
   * ordinary {@code equals} otherwise. The result is therefore always DEFINED, and true exactly
   * when both operands are undefined or both are defined and equal.
   *
   * <p>Confirmed by executing the real evaluator, not inferred from the source: over one {@code A}
   * with {@code s}/{@code n} unset and no {@code b} link, {@code x.s = oclUndefined(String)} and
   * {@code x.n = oclUndefined(Integer)} both evaluate to a DEFINED {@code true}, {@code x.b <>
   * oclUndefined(B)} to a DEFINED {@code false}, and {@code oclUndefined(Integer) =
   * oclUndefined(String)} to {@code true} -- while {@code x.n > 0} stays undefined, because the
   * ordered comparators really are strict ({@link #orderedComparison}).
   *
   * <p>This replaces a short-circuit that returned a CONSTANT for any {@code oclUndefined} operand
   * without ever translating the other side. That erased the other operand's definedness (an
   * unlinked navigation's genuine, USE-confirmed violation was reported as unsatisfiable) and
   * bypassed the fail-closed refusal an untranslatable operand must produce.
   */
  private TranslatedExpression comparison(Expression l, Expression r) {
    if (l instanceof ExpUndefined || r instanceof ExpUndefined) {
      if (l instanceof ExpUndefined && r instanceof ExpUndefined) {
        return defined(Smt.bool(true));
      }
      return defined(Smt.not(definednessOf(l instanceof ExpUndefined ? r : l)));
    }
    if (l instanceof ExpVariable lv && r instanceof ExpVariable rv)
      return defined(
          Smt.bool(context.binding(lv.getVarname()).equals(context.binding(rv.getVarname()))));
    if (l instanceof ExpConstString s) {
      TranslatedExpression other = argResult(r);
      return useEquality(defined(resolve(s, r)), other);
    }
    if (r instanceof ExpConstString s) {
      TranslatedExpression other = argResult(l);
      return useEquality(other, defined(resolve(s, l)));
    }
    if (l instanceof ExpNavigation ln
        && r instanceof ExpNavigation rn
        && !ln.getDestination().isCollection()
        && !rn.getDestination().isCollection()) return navigationEquals(ln, rn);
    return useEquality(argResult(l), argResult(r));
  }

  /**
   * USE's total equality rule over two already-translated operands: always defined, true when both
   * are undefined or both are defined and equal. The all-defined case -- every comparison in the
   * crisp Library fragment, where an attribute always carries a value from its configured domain --
   * is emitted in its simplified form so the SMT text is unchanged for it.
   */
  private static TranslatedExpression useEquality(
      TranslatedExpression left, TranslatedExpression right) {
    return useEquality(left, right, Smt.eq(left.value(), right.value()));
  }

  /**
   * The same rule where "the values are equal" is not a term-level {@code =} over two standalone
   * SMT values -- single-valued navigation equality, which is a shared-target disjunction.
   */
  private static TranslatedExpression useEquality(
      TranslatedExpression left, TranslatedExpression right, SmtTerm valuesEqual) {
    if (isTrue(left.defined()) && isTrue(right.defined())) {
      return defined(valuesEqual);
    }
    return defined(
        Smt.or(
            List.of(
                Smt.and(List.of(Smt.not(left.defined()), Smt.not(right.defined()))),
                Smt.and(List.of(left.defined(), right.defined(), valuesEqual)))));
  }

  private static boolean isTrue(SmtTerm term) {
    return term instanceof SmtTerm.Atom atom && "true".equals(atom.symbol());
  }

  /**
   * The definedness of one operand of an equality, without requiring it to have a standalone SMT
   * value. Single-valued navigation is exactly that case: it names a linked object rather than a
   * value, so {@link #visitNavigation} cannot translate it at all -- but "is there a link?" is
   * precisely what an {@code oclUndefined} comparison asks, and the link grid already answers it (a
   * link implies both endpoints exist; see {@code AssociationLinkEncoder}). Every other shape goes
   * through the ordinary translator, so an operand outside the supported fragment still fails
   * closed with its own located reason.
   */
  private SmtTerm definednessOf(Expression e) {
    if (e instanceof ExpNavigation navigation && !navigation.getDestination().isCollection()) {
      return singleValuedNavigationDefined(navigation);
    }
    return argResult(e).defined();
  }

  /** True exactly when the source slot links to some target slot of the navigated association. */
  private SmtTerm singleValuedNavigationDefined(ExpNavigation navigation) {
    AssociationLinks links = context.linksFor(navigation.getDestination().association().name());
    ObjectSlots destinationSlots = context.slotsFor(navigation.getDestination().cls().name());
    VariableBinding source = context.binding(variableNameOf(navigation.getObjectExpression()));
    List<SmtTerm> targets = new ArrayList<>();
    for (int k = 0; k < destinationSlots.capacity(); k++) {
      targets.add(linkTerm(links, source, k));
    }
    return Smt.or(targets);
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
    return useEquality(
        new TranslatedExpression(Smt.or(leftTargets), Smt.bool(true)),
        new TranslatedExpression(Smt.or(rightTargets), Smt.bool(true)),
        Smt.or(sharedTarget));
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
        FragmentBoundary.TIER_3,
        "association " + links.associationName() + " does not connect class " + source.className());
  }

  private SmtTerm resolve(ExpConstString literal, Expression other) {
    if (!(other instanceof ExpAttrOp a))
      throw unsupported(
          FragmentBoundary.TIER_2, "string literal compared against a non-attribute expression");
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
        FragmentBoundary.TIER_2,
        "attribute access on a non-variable receiver is not yet supported");
  }

  /**
   * Every refusal names BOTH the construct (wording unchanged, so no located message built by
   * Milestones 4.3-4.6 regresses) and the supported-fragment boundary it hit. The boundary is a
   * required argument rather than a defaulted one on purpose: a construct added later cannot be
   * refused without someone deciding, at the call site, which tier or U-type rule excludes it.
   */
  private static SmtTranslationException unsupported(FragmentBoundary boundary, String c) {
    return new SmtTranslationException(
        boundary, "unsupported OCL construct in this translation slice: " + c);
  }

  @Override
  public void visitAllInstances(ExpAllInstances e) {
    throw unsupported(
        FragmentBoundary.TIER_2, "allInstances outside a forAll range is not yet supported");
  }

  @Override
  public void visitAny(ExpAny e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "any");
  }

  @Override
  public void visitAsType(ExpAsType e) {
    throw unsupported(FragmentBoundary.TIER_2, "asType");
  }

  @Override
  public void visitBagLiteral(ExpBagLiteral e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "Bag literal");
  }

  @Override
  public void visitCollect(ExpCollect e) {
    throw unsupported(FragmentBoundary.TIER_3, "collect");
  }

  @Override
  public void visitCollectNested(ExpCollectNested e) {
    throw unsupported(FragmentBoundary.TIER_3, "collectNested");
  }

  @Override
  public void visitConstEnum(ExpConstEnum e) {
    throw unsupported(FragmentBoundary.TIER_3, "enum literal");
  }

  @Override
  public void visitConstReal(ExpConstReal e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "Real literal");
  }

  @Override
  public void visitConstUBoolean(ExpConstUBoolean e) {
    throw unsupported(FragmentBoundary.UTYPE_CORE, "UBoolean literal");
  }

  @Override
  public void visitConstSBoolean(ExpConstSBoolean e) {
    throw unsupported(FragmentBoundary.UTYPE_SBOOLEAN, "SBoolean literal");
  }

  @Override
  public void visitConstUInteger(ExpConstUInteger e) {
    throw unsupported(FragmentBoundary.UTYPE_CORE, "UInteger literal");
  }

  @Override
  public void visitConstUReal(ExpConstUReal e) {
    throw unsupported(FragmentBoundary.UTYPE_CORE, "UReal literal");
  }

  @Override
  public void visitConstUString(ExpConstUString e) {
    throw unsupported(FragmentBoundary.UTYPE_CORE, "UString literal");
  }

  @Override
  public void visitEmptyCollection(ExpEmptyCollection e) {
    throw unsupported(FragmentBoundary.TIER_3, "empty collection");
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
      throw unsupported(FragmentBoundary.TIER_2, "exists with a variable count other than two");
    if (!(e.getRangeExpression() instanceof ExpNavigation range))
      throw unsupported(
          FragmentBoundary.TIER_2, "exists over a range other than a collection-valued navigation");
    if (!range.getDestination().isCollection())
      throw unsupported(FragmentBoundary.TIER_2, "exists over a single-valued navigation");
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
      throw unsupported(FragmentBoundary.TIER_1, "forAll with more than one loop variable");
    if (!(e.getRangeExpression() instanceof ExpAllInstances all))
      throw unsupported(FragmentBoundary.TIER_1, "forAll over a range other than X.allInstances");
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
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "if");
  }

  @Override
  public void visitIsKindOf(ExpIsKindOf e) {
    throw unsupported(FragmentBoundary.TIER_2, "isKindOf");
  }

  @Override
  public void visitIsTypeOf(ExpIsTypeOf e) {
    throw unsupported(FragmentBoundary.TIER_2, "isTypeOf");
  }

  /**
   * {@code X.allInstances()->isUnique(body)} (Shape 1: {@code Column::columnIndexUnique}, {@code
   * Row::rowIndexUnique}) and {@code self.<single-hop, collection-valued association
   * end>->isUnique(body)} (Shape 2: {@code Row::uniqueValuesRow}, {@code
   * Column::uniqueValuesColumn}, {@code Square::uniqueValuesSquare}) -- the two population sources
   * confirmed to be the ONLY remaining refusal blocking Sudoku/Sudoku-UNSAT. Both reduce to the
   * same abstraction, a finite existence/link-guarded population plus a pairwise-distinctness
   * requirement, built once by {@link #isUniqueOver} and fed from {@link #populationOf}.
   *
   * <p>Real USE isUnique semantics, established by executing the real evaluator (not inferred, not
   * read off the OCL spec): the result is ALWAYS a defined Boolean for these two source shapes --
   * never undefined regardless of how many population members have an undefined body -- and
   * duplicate detection is USE's own TOTAL equality rule, where two undefined body values collide
   * with EACH OTHER (never with a defined value). {@code Set{}->isUnique(i | Undefined)} on a
   * two-element undefined-body population is {@code false} (a genuine collision), while a SINGLE
   * undefined body among otherwise-distinct members is {@code true} (nothing to collide with). That
   * is exactly {@link #useEquality}, already used for {@code =}/{@code <>}, reused here rather than
   * re-derived.
   *
   * <p>The implicit loop variable binds exactly like {@link #visitForAll}'s: USE's parser always
   * hands {@link ExpIsUnique} exactly one {@code VarDecl} (explicit or internally generated for the
   * unnamed-argument form Sudoku's real invariants use, e.g. {@code isUnique(value)}), never zero
   * or more than one -- the size guard below documents that invariant rather than being reachable
   * through the real parser.
   */
  @Override
  public void visitIsUnique(ExpIsUnique e) {
    if (e.getVariableDeclarations().size() != 1) {
      throw unsupported(FragmentBoundary.TIER_3, "isUnique with a variable count other than one");
    }
    String loopVariable = e.getVariableDeclarations().varDecl(0).name();
    result =
        isUniqueOver(
            populationOf(e.getRangeExpression(), "isUnique"), loopVariable, e.getQueryExpression());
  }

  /**
   * The finite, existence/link-guarded population BOTH {@code isUnique} and {@link #collectionSize}
   * range over, for exactly the two supported source shapes -- anything else (a filtered/derived
   * collection such as {@code ->select(...)->isUnique(...)}, a chained multi-hop navigation, a set
   * literal, ...) fails closed here, before a single body translation (or, for {@code size()}, the
   * summation) is even attempted.
   *
   * @param construct the calling construct's own name, spliced into both refusal messages so a
   *     {@code size()} refusal reads as a {@code size()} refusal and not a leftover {@code
   *     isUnique} one -- {@link #collectionSize} additionally pre-filters to the {@code
   *     ExpNavigation} shape before ever reaching here, so only the "more than one hop" message is
   *     reachable through it in practice; the second message stays parameterized too rather than
   *     silently keeping its original wording as a trap for the next caller.
   */
  private List<PopulationMember> populationOf(Expression range, String construct) {
    if (range instanceof ExpAllInstances all) {
      List<PopulationMember> population = new ArrayList<>();
      for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
        population.add(new PopulationMember(slot.binding(), Smt.sym(slot.existsName())));
      }
      return population;
    }
    if (range instanceof ExpNavigation navigation && navigation.getDestination().isCollection()) {
      if (!(navigation.getObjectExpression() instanceof ExpVariable sourceVar)) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            construct
                + " over a collection-valued navigation with more than one hop is not yet"
                + " supported");
      }
      String destClass = navigation.getDestination().cls().name();
      AssociationLinks links = context.linksFor(navigation.getDestination().association().name());
      ObjectSlots destSlots = context.slotsFor(destClass);
      VariableBinding source = context.binding(sourceVar.getVarname());
      List<PopulationMember> population = new ArrayList<>();
      for (int k = 0; k < destSlots.capacity(); k++) {
        population.add(
            new PopulationMember(new VariableBinding(destClass, k), linkTerm(links, source, k)));
      }
      return population;
    }
    throw unsupported(
        FragmentBoundary.TIER_3,
        construct
            + " over a range other than X.allInstances() or a single-hop collection-valued"
            + " association end is not yet supported");
  }

  /**
   * One candidate population member shared by {@code isUnique} and {@link #collectionSize}: its
   * loop-variable binding, plus the SMT term guarding whether it is actually present -- an
   * existence flag for {@link #populationOf}'s allInstances branch, a {@link #linkTerm} for its
   * association-end branch.
   */
  private record PopulationMember(VariableBinding binding, SmtTerm memberGuard) {}

  /**
   * Shared "population -> pairwise distinctness" core for BOTH supported {@code isUnique} source
   * shapes: {@code isUnique} holds iff no two DISTINCT, actually-present population members have
   * {@code useEquality}-equal body values. Pairs are unordered ({@code i < j} only) since {@code
   * useEquality} is symmetric -- encoding both {@code (i,j)} and {@code (j,i)} would double the
   * script size for no semantic gain. A population of size 0 or 1 has no pair at all, so {@link
   * Smt#and} over an empty list correctly yields {@code true} (vacuously unique), matching the real
   * evaluator's own empty/singleton-range behaviour.
   */
  private TranslatedExpression isUniqueOver(
      List<PopulationMember> population, String loopVariable, Expression body) {
    List<TranslatedExpression> bodies = new ArrayList<>(population.size());
    for (PopulationMember member : population) {
      TranslationContext extended = context.withBinding(loopVariable, member.binding());
      bodies.add(translate(body, extended, mode, positivePolarity));
    }
    List<SmtTerm> distinctPairs = new ArrayList<>();
    for (int i = 0; i < population.size(); i++) {
      for (int j = i + 1; j < population.size(); j++) {
        SmtTerm bothMembers =
            Smt.and(List.of(population.get(i).memberGuard(), population.get(j).memberGuard()));
        TranslatedExpression sameValue = useEquality(bodies.get(i), bodies.get(j));
        distinctPairs.add(Smt.app("=>", bothMembers, Smt.not(sameValue.value())));
      }
    }
    return defined(Smt.and(distinctPairs));
  }

  /**
   * {@code self.<one-hop, collection-valued association end>->size()} -- the sole {@code size()}
   * source shape evidenced by the real corpus (e.g. {@code CollectionSemantics::hasThreeSongs},
   * {@code self.songs->size() = 3}). Resolves its population through the SAME {@link #populationOf}
   * association-end branch {@code isUnique}'s Shape 2 already uses -- reused directly, not
   * re-derived -- then sums each member's {@link PopulationMember#memberGuard()} indicator into an
   * SMT Integer via {@link #sizeTerm}. The result is an ordinary, always-defined {@link
   * TranslatedExpression}, so it composes with the generic {@link #comparison}/{@link
   * #orderedComparison} machinery for free: {@code self.songs->size() = 3} needs no special-casing
   * on the comparison side, it is just an equality between two already-translated operands.
   *
   * <p>Deliberately narrower than {@link #populationOf} as a whole: only its {@code ExpNavigation}
   * branch is a supported {@code size()} source, checked HERE before {@link #populationOf} is even
   * consulted. {@code X.allInstances()->size()} is a different shape, not evidenced by the real
   * corpus and out of this slice's scope, so it is refused with a {@code size()}-specific message
   * rather than silently falling into {@link #populationOf}'s allInstances branch. The same refusal
   * covers a filtered/derived collection ({@code ->select(...)->size()}, not an {@link
   * ExpNavigation}), a single-valued 0..1 navigation coerced to a set via OCL's own {@code ->op}
   * "uniform syntax" rule ({@link ExpObjAsSet}, not an {@link ExpNavigation} either -- e.g. {@code
   * AssociationClass}'s {@code p.employer->size()}, {@code employer} being a 0..1 end), and {@code
   * size()} on a String -- which never reaches this method with an {@link ExpNavigation} receiver
   * at all, since a String operand is never navigation-shaped.
   */
  private TranslatedExpression collectionSize(Expression receiver) {
    if (!(receiver instanceof ExpNavigation navigation)
        || !navigation.getDestination().isCollection()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "size() over anything other than a single-hop, collection-valued association navigation"
              + " is not yet supported");
    }
    return defined(sizeTerm(populationOf(navigation, "size()")));
  }

  /**
   * A population's cardinality as an SMT Integer term: one per-member indicator (1 if {@link
   * PopulationMember#memberGuard()} holds, 0 otherwise), folded with {@code "+"} -- the SAME
   * ite-per-slot/"+" pattern {@link AssociationLinkEncoder}'s own aggregate degree count already
   * uses over the identical link booleans, applied here to build a VALUE rather than a constraint.
   * An empty population is 0, matching {@link Smt#and}/{@link Smt#or}'s own empty-list convention
   * elsewhere in this class.
   */
  private static SmtTerm sizeTerm(List<PopulationMember> population) {
    if (population.isEmpty()) {
      return Smt.intLit(BigInteger.ZERO);
    }
    SmtTerm total = indicator(population.get(0));
    for (int i = 1; i < population.size(); i++) {
      total = Smt.app("+", total, indicator(population.get(i)));
    }
    return total;
  }

  private static SmtTerm indicator(PopulationMember member) {
    return Smt.ite(member.memberGuard(), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO));
  }

  @Override
  public void visitIterate(ExpIterate e) {
    throw unsupported(FragmentBoundary.TIER_3, "iterate");
  }

  @Override
  public void visitLet(ExpLet e) {
    throw unsupported(FragmentBoundary.TIER_3, "let");
  }

  @Override
  public void visitNavigation(ExpNavigation e) {
    throw unsupported(FragmentBoundary.TIER_2, "navigation");
  }

  @Override
  public void visitObjAsSet(ExpObjAsSet e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "objAsSet");
  }

  @Override
  public void visitInstanceOp(ExpInstanceOp e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "instance operation");
  }

  @Override
  public void visitObjRef(ExpObjRef e) {
    throw unsupported(FragmentBoundary.TIER_2, "object reference");
  }

  @Override
  public void visitOne(ExpOne e) {
    throw unsupported(FragmentBoundary.TIER_3, "one");
  }

  @Override
  public void visitOrderedSetLiteral(ExpOrderedSetLiteral e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "OrderedSet literal");
  }

  @Override
  public void visitQuery(ExpQuery e) {
    throw unsupported(FragmentBoundary.TIER_3, "query");
  }

  @Override
  public void visitReject(ExpReject e) {
    throw unsupported(FragmentBoundary.TIER_3, "reject");
  }

  @Override
  public void visitWithValue(ExpressionWithValue e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "withValue");
  }

  @Override
  public void visitSelect(ExpSelect e) {
    throw unsupported(FragmentBoundary.TIER_3, "select");
  }

  @Override
  public void visitSequenceLiteral(ExpSequenceLiteral e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "Sequence literal");
  }

  @Override
  public void visitSetLiteral(ExpSetLiteral e) {
    throw unsupported(FragmentBoundary.TIER_3, "Set literal");
  }

  @Override
  public void visitSortedBy(ExpSortedBy e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "sortedBy");
  }

  @Override
  public void visitTupleLiteral(ExpTupleLiteral e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "Tuple literal");
  }

  @Override
  public void visitTupleSelectOp(ExpTupleSelectOp e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "tuple select");
  }

  @Override
  public void visitClosure(ExpClosure e) {
    throw unsupported(FragmentBoundary.TIER_3, "closure");
  }

  @Override
  public void visitOclInState(ExpOclInState e) {
    throw unsupported(FragmentBoundary.UTYPE_BEHAVIOURAL_OCL, "oclInState");
  }

  @Override
  public void visitVarDeclList(VarDeclList e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "VarDeclList");
  }

  @Override
  public void visitVarDecl(VarDecl e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "VarDecl");
  }

  @Override
  public void visitObjectByUseId(ExpObjectByUseId e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "objectByUseId");
  }

  @Override
  public void visitConstUnlimitedNatural(ExpConstUnlimitedNatural e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "UnlimitedNatural literal");
  }

  @Override
  public void visitSelectByKind(ExpSelectByKind e) {
    throw unsupported(FragmentBoundary.TIER_3, "selectByKind");
  }

  @Override
  public void visitExpSelectByType(ExpSelectByType e) {
    throw unsupported(FragmentBoundary.TIER_3, "selectByType");
  }

  @Override
  public void visitRange(ExpRange e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "range");
  }

  @Override
  public void visitNavigationClassifierSource(ExpNavigationClassifierSource e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "navigationClassifierSource");
  }

  @Override
  public void visitUSelectC(ExpUSelectC e) {
    throw unsupported(FragmentBoundary.UTYPE_VALUE_COLLECTION, "USelectC");
  }

  @Override
  public void visitUSelect(ExpUSelect e) {
    throw unsupported(FragmentBoundary.UTYPE_VALUE_COLLECTION, "USelect");
  }
}
