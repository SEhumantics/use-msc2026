package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.mm.MNavigableElement;
import org.tzi.use.uml.ocl.expr.*;

/** Translates the verified leaf-level Library OCL fragment and fails closed on everything else. */
public final class ExpressionTranslator implements ExpressionVisitor {
  private static final BigInteger UNDEFINED_STRING_SENTINEL = BigInteger.valueOf(-1);
  private TranslationContext context;
  private final TranslationMode mode;
  private final boolean positivePolarity;
  private final Map<String, LocalBinding> localBindings;
  private final Set<MOperation> operationsInProgress;
  private TranslatedExpression result;

  private ExpressionTranslator(
      TranslationContext c,
      TranslationMode mode,
      boolean positivePolarity,
      Map<String, LocalBinding> localBindings,
      Set<MOperation> operationsInProgress) {
    context = c;
    this.mode = mode;
    this.positivePolarity = positivePolarity;
    this.localBindings = localBindings;
    this.operationsInProgress =
        operationsInProgress == null ? Set.of() : operationsInProgress;
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
    return translate(e, c, mode, positivePolarity, Map.of(), null);
  }

  private TranslatedExpression translate(
      Expression e,
      TranslationContext c,
      TranslationMode mode,
      boolean positivePolarity,
      Map<String, LocalBinding> localBindings) {
    return translate(e, c, mode, positivePolarity, localBindings, operationsInProgress);
  }

  /**
   * Translation with an OPERATION-IN-PROGRESS set for query-operation inlining: an operation
   * currently being inlined is a member, so a recursive (or mutually recursive) call is detected
   * at the nested level and refused there instead of looping forever.
   */
  private static TranslatedExpression translate(
      Expression e,
      TranslationContext c,
      TranslationMode mode,
      boolean positivePolarity,
      Map<String, LocalBinding> localBindings,
      Set<MOperation> operationsInProgress) {
    ExpressionTranslator t =
        new ExpressionTranslator(c, mode, positivePolarity, localBindings, operationsInProgress);
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
    LocalBinding local = localBindings.get(e.getVarname());
    if (local != null) {
      result =
          new TranslatedExpression(Smt.sym(local.definedSymbol()), Smt.sym(local.valueSymbol()));
      return;
    }
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
    VariableBinding source = context.binding(sourceVar.getVarname());
    destination = resolveRedefinedDestination(destination, source);
    if (destination.association() instanceof MAssociationClass) {
      ObjectSlots assocClassSlots = context.slotsFor(destination.cls().name());
      AttributeValues assocClassValues =
          context.attributeValues(assocClassSlots.className(), attribute.name());
      guardEndAgainstUncertainAttribute(assocClassSlots, attribute, assocClassValues);
      return associationClassNavigatedAttribute(source, destination, assocClassSlots, assocClassValues);
    }

    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    AttributeValues v = context.attributeValues(destSlots.className(), attribute.name());
    guardEndAgainstUncertainAttribute(destSlots, attribute, v);

    List<SmtTerm> targets = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      targets.add(linkTerm(links, destination, source, k));
    }
    return new TranslatedExpression(
        Smt.or(targets),
        selectLinkedValue(links, destination, source, destSlots, attribute, v));
  }

  /**
   * The destination end's own slot view for one association's grid: the declared class's slots
   * unless the grid is FOLDED over configured subclasses ({@code SmtModelFinder}'s end-view
   * construction), in which case the view spans the whole polymorphic population. Unfolded
   * associations return exactly {@code context.slotsFor(declared)} -- byte-identical to the
   * pre-folding behavior.
   */
  private ObjectSlots destinationEndView(AssociationLinks links, MNavigableElement destination) {
    String declared = destination.cls().name();
    if (links.aEnd().className().equals(declared)) {
      return links.aEnd();
    }
    if (links.bEnd().className().equals(declared)) {
      return links.bEnd();
    }
    return context.slotsFor(declared);
  }

  /**
   * The declared end class's values (the representative for placeholder sorts and for the
   * identity fast path), guarded -- plus, for a FOLDED view, every DISTINCT concrete class the
   * view spans, since grid slot k reads ITS class's values, and an uncertain one must be refused
   * before any formula is emitted.
   */
  private void guardEndAgainstUncertainAttribute(
      ObjectSlots destSlots, MAttribute attribute, AttributeValues declaredValues) {
    guardAgainstUncertainAttribute(declaredValues);
    for (VariableBinding concrete : destSlots.concreteBindings()) {
      if (!concrete.className().equals(destSlots.className())) {
        guardAgainstUncertainAttribute(
            context.attributeValues(concrete.className(), attribute.name()));
      }
    }
  }

  /**
   * The value symbol of the attribute at one destination slot of a (possibly folded) end view:
   * the declared class's own value for identity slots, the concrete class's registered values
   * otherwise (inherited attributes are registered per concrete subclass). {@code slotIndex} is
   * the slot's index WITHIN its concrete class, exactly how those values were encoded.
   */
  private SmtTerm valueSymbolForEndSlot(
      ObjectSlots destSlots, AttributeValues declaredValues, MAttribute attribute, int k) {
    VariableBinding concrete = destSlots.concreteBindings().get(k);
    if (concrete.className().equals(destSlots.className())) {
      return Smt.sym(declaredValues.valueNames().get(k));
    }
    AttributeValues concreteValues =
        context.attributeValues(concrete.className(), attribute.name());
    return Smt.sym(concreteValues.valueNames().get(concrete.slotIndex()));
  }

  /**
   * {@code e.employer.budget}-shaped: {@code e} is bound to an ASSOCIATION CLASS instance, not to
   * either end's class, so there is no {@link AssociationLinks} grid to consult at all -- the
   * source's own link identity IS its index-pointer attribute ({@link
   * AssociationClassPointerEncoder}), looked up the same way any other attribute is (zero special
   * {@link TranslationContext} plumbing). Unconditionally DEFINED: an association-class instance's
   * two ends are always bound BY CONSTRUCTION the moment the instance itself exists (confirmed
   * directly against {@code ExpNavigationClassifierSource#eval}, use-core -- "a link is always
   * connected to objects, i.e. obj cannot be null" -- and enforced on the encoding side by {@link
   * AssociationClassPointerEncoder}'s own existing-target guard), matching {@link #visitAttrOp}'s
   * own unconditional-defined convention for a direct attribute access exactly -- the OUTER
   * exists-guard {@code InvariantAssembler} already wraps every translated invariant with is what
   * handles "what if {@code e} itself does not exist", not this expression's own concern.
   */
  private TranslatedExpression associationClassNavigatedAttribute(
      VariableBinding source, MNavigableElement destination, ObjectSlots destSlots,
      AttributeValues v) {
    AttributeValues pointer = associationClassPointer(source.className(), destination);
    int capacity = destSlots.capacity();
    if (capacity == 0) {
      return defined(placeholderOfSort(v.type()));
    }
    SmtTerm value = Smt.sym(v.valueNames().get(capacity - 1));
    for (int k = capacity - 2; k >= 0; k--) {
      SmtTerm pointsHere =
          Smt.eq(Smt.sym(pointer.valueNames().get(source.slotIndex())), Smt.intLit(BigInteger.valueOf(k)));
      value = Smt.ite(pointsHere, Smt.sym(v.valueNames().get(k)), value);
    }
    return defined(value);
  }

  /**
   * Resolves which of the association class's two synthetic pointer attributes {@code end}
   * corresponds to, by declared end position -- the same reflexive-safe convention {@link
   * #linkTerm} already uses (compare against {@code associationEnds().get(0)} rather than class
   * name, so this stays correct even for a hypothetical reflexive association class, though the
   * real corpus does not have one).
   *
   * <p>{@code associationClassName} is taken explicitly rather than derived from a binding's own
   * {@code className()}: the two call sites disagree on whose binding is in scope. {@link
   * #associationClassNavigatedAttribute} is reached from the classifier's OWN instance ({@code
   * e.employer}, {@code source} bound to Employment), where {@code source.className()} happens to
   * equal the association class's name -- but {@link #associationClassEndNavigationDefined} is
   * reached from the OPPOSITE end's instance ({@code p.employer}, {@code source} bound to Person),
   * where it does not.
   */
  private AttributeValues associationClassPointer(String associationClassName, MNavigableElement end) {
    boolean isEnd0 = end.equals(end.association().associationEnds().get(0));
    String attributeName =
        isEnd0
            ? AssociationClassPointerEncoder.END0_POINTER_ATTRIBUTE
            : AssociationClassPointerEncoder.END1_POINTER_ATTRIBUTE;
    return context.attributeValues(associationClassName, attributeName);
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
      AssociationLinks links,
      MNavigableElement destination,
      VariableBinding source,
      ObjectSlots destSlots,
      MAttribute attribute,
      AttributeValues v) {
    int capacity = destSlots.capacity();
    if (capacity == 0) {
      return placeholderOfSort(v.type());
    }
    SmtTerm value = valueSymbolForEndSlot(destSlots, v, attribute, capacity - 1);
    for (int k = capacity - 2; k >= 0; k--) {
      value =
          Smt.ite(linkTerm(links, destination, source, k), valueSymbolForEndSlot(destSlots, v, attribute, k), value);
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
          case "xor" -> booleanXor(argResult(a[0]), argResult(a[1]));
          case "=" -> comparison(a[0], a[1]);
          case "<>" -> negate(comparison(a[0], a[1]));
          case ">=", "<=", ">", "<" -> orderedComparison(e.opname(), a[0], a[1]);
          case "size" -> collectionSize(a[0]);
          case "div" -> integerDivision(a);
          case "+", "-", "*" -> arithmetic(e.opname(), a);
          // Both total functions over any operand (confirmed directly against Op_isDefined/
          // Op_isUndefined, use-core: `!args[0].isUndefined()` / `args[0].isUndefined()`, kind()
          // SPECIAL) -- the operand's own definedness is exactly this translator's existing
          // TranslatedExpression#defined() for it, already computed by argResult; isDefined/
          // isUndefined never propagate that as their OWN definedness, they report it as a value.
          case "isDefined" -> defined(definednessOf(a[0]));
          case "isUndefined" -> defined(Smt.not(definednessOf(a[0])));
          case "excludes" -> membershipTest(a[0], a[1], false);
          case "includes" -> membershipTest(a[0], a[1], true);
          case "includesAll" -> collectionIncludesAll(a[0], a[1]);
          case "isEmpty" -> collectionEmptiness(a[0], true);
          case "notEmpty" -> collectionEmptiness(a[0], false);
          // USE's Op_real_round accepts any number, but on a crisp Integer it is the IDENTITY
          // (Math.round(intValue) is the same int -- confirmed against the use-core source, not
          // inferred), so the encoding is the operand's own value with its definedness. Real and
          // UReal round() carry genuinely different rounding semantics and stay refused.
          // abs / min / max over crisp Integers: TOTAL operations (no division-style
          // undefinedness), each encodable as a LINEAR ite term -- the reason they join the
          // slice while / and mod (whose zero-divisor case needs undefinedness semantics this
          // slice does not model) stay refused. USE's own evaluators confirm the semantics:
          // Op_integer_abs is Math.abs, Op_number_min/max the smaller/larger operand.
          case "abs" -> {
            if (a.length == 1 && a[0].type().isTypeOfInteger()) {
              TranslatedExpression operand = argResult(a[0]);
              yield new TranslatedExpression(
                  operand.defined(),
                  Smt.ite(
                      Smt.app(">=", operand.value(), Smt.intLit(BigInteger.ZERO)),
                      operand.value(),
                      Smt.app("-", operand.value())));
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator 'abs' over a non-Integer operand (Real/UReal rounding semantics are"
                    + " not in this slice)");
          }
          case "min", "max" -> {
            if (a.length == 2
                && a[0].type().isTypeOfInteger()
                && a[1].type().isTypeOfInteger()) {
              TranslatedExpression left = argResult(a[0]);
              TranslatedExpression right = argResult(a[1]);
              SmtTerm comparison =
                  "min".equals(e.opname())
                      ? Smt.app("<=", left.value(), right.value())
                      : Smt.app(">=", left.value(), right.value());
              yield new TranslatedExpression(
                  Smt.and(List.of(left.defined(), right.defined())),
                  Smt.ite(comparison, left.value(), right.value()));
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator '"
                    + e.opname()
                    + "' over non-Integer or wrong-arity operands is not supported in this"
                    + " slice");
          }
          case "round" -> {
            if (a.length == 1 && a[0].type().isTypeOfInteger()) {
              yield argResult(a[0]);
            }
            throw unsupported(
                boundaryOfOperator(e.opname()),
                "operator 'round' over a non-Integer operand (Real/UReal rounding semantics are"
                    + " not in this slice)");
          }
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

  /**
   * Unlike {@link #booleanAnd}/{@link #booleanOr}, {@code xor} has no ABSORBING value under Kleene
   * three-valued logic -- {@code xor(true, x)} is {@code not(x)}, so it genuinely depends on {@code
   * x} regardless of whether {@code x} turns out true or false, and the same holds symmetrically for
   * {@code xor(false, x)}. Neither operand being definitely true or definitely false can settle the
   * result the way it does for {@code and}/{@code or}, so definedness is the plain conjunctive rule
   * {@link #orderedComparison} already uses: defined exactly when BOTH operands are.
   */
  private static TranslatedExpression booleanXor(
      TranslatedExpression left, TranslatedExpression right) {
    return new TranslatedExpression(
        Smt.and(List.of(left.defined(), right.defined())),
        Smt.app("xor", left.value(), right.value()));
  }

  private TranslatedExpression orderedComparison(
      String operator, Expression left, Expression right) {
    TranslatedExpression l = argResult(left);
    TranslatedExpression r = argResult(right);
    return new TranslatedExpression(
        Smt.and(List.of(l.defined(), r.defined())), Smt.app(operator, l.value(), r.value()));
  }

  /**
   * Binary {@code +}/{@code -}/{@code *} over two plain crisp Integer operands -- the shape {@code
   * NQueens::noAttack} needs ({@code q1.row.idx+q1.col.idx}, {@code q1.row.idx-q1.col.idx}, each
   * over two already-supported {@link #navigatedAttribute} results) -- and unary {@code -}/{@code
   * +} over one plain crisp Integer operand. Unary {@code -} is confirmed real and reachable by
   * compiling the real {@code Employee.use} and inspecting the parsed AST directly: {@code
   * self.salary > -1} reaches this visitor as {@code ExpStdOp} opname {@code "-"} with {@code
   * a.length==1} wrapping {@code ExpConstInteger(1)} -- USE does NOT fold a literal unary minus
   * into a negative constant at parse time. Unary {@code +} is syntactically legal per USE's own
   * grammar ({@code ("not" | "-" | "+") unaryExpression}, {@code OCLBase.gpart:232}) and DOES reach
   * this method for a crisp Integer operand, confirmed the same way (compiling {@code a.i =
   * +a.j} reaches {@code ExpStdOp} opname {@code "+"}, {@code a.length==1}); it is translated as a
   * plain identity -- {@code +x} denotes the same value as {@code x} -- rather than as an emitted
   * SMT-LIB {@code (+ x)} application, since SMT-LIB's arithmetic theories declare {@code +} with
   * arity &gt;= 2 (unlike {@code -}, which SMT-LIB itself overloads with an explicit 1-argument
   * negation form, the form the unary {@code -} case below already relies on).
   *
   * <p>Binary {@code *} additionally requires {@link #requireLinearProduct}: unlike {@code +}/
   * {@code -}, which are linear for any two Integer operands, a product of two non-constant
   * operands is nonlinear arithmetic, which this project's pinned {@code QF_LIA} solver logic
   * rejects outright (see that method's docstring for the Z3-confirmed evidence) -- so {@code *} is
   * supported only when at least one operand is a compile-time Integer literal.
   *
   * <p>Each supported shape produces an ordinary, always-defined {@link TranslatedExpression}
   * wrapping an SMT Integer term, exactly {@link #orderedComparison}'s own {@code
   * argResult}/definedness-conjunction pattern -- so {@code +}/{@code -}/{@code *} compose with the
   * existing generic {@link #comparison}/{@link #orderedComparison} dispatch with ZERO
   * special-casing, the same design {@link #collectionSize} established one task ago.
   *
   * <p>{@code /}, {@code div}, {@code mod}, {@code abs}, {@code min}, and {@code max} remain
   * deliberately out of this method's scope and unconditionally refused via the final {@code
   * throw}: each carries real division/mod-by-zero undefinedness semantics (and, for {@code /}
   * specifically, OCL's Integer-may-widen-to-Real rule) that this always-defined shape does not
   * model and that need their own dedicated translation, not a silent extension of this one.
   *
   * <p>The {@link #requireCrispInteger} guard is defense-in-depth rather than a dead branch: a
   * {@code UInteger} operand genuinely DOES reach {@code +}/{@code -}/{@code *} through the real
   * parser (({@code StandardOperationsNumber.ArithOperation.matches} widens {@code UInteger op
   * Integer}/{@code UInteger} to {@code UInteger}, and {@code Op_number_unaryminus.matches} accepts
   * any {@code isKindOfNumber} operand including {@code UInteger} -- both confirmed by compiling
   * {@code f.u + 1 = f.u2}), but every USE invariant must itself be Boolean-typed, and the only way
   * to turn that UInteger-typed result back into one is (a) an ordinary {@code =}/{@code <>}, which
   * USE compiles as UBoolean and therefore REJECTS at compile time ("An invariant must be a boolean
   * expression", confirmed empirically for {@code f.u + 1 = f.u2}) unless wrapped in {@code
   * toBooleanC}, or (b) {@code toBooleanC} itself, whose own extraction in {@link #uTypeThreshold}
   * requires the compared operand to be a bare {@link ExpAttrOp} and refuses an arithmetic
   * sub-expression before ever calling this method -- so a UInteger operand cannot reach here
   * through any invariant the real front end will actually compile. A genuinely crisp {@code Real}
   * operand IS reachable this way ({@code self.i + self.r = self.r2} compiles and widens to {@code
   * Real} per the same {@code ArithOperation.matches}, and {@code self.i * self.r = self.r2} widens
   * the same way under {@code *}), and is the shape this guard actually refuses in practice.
   */
  private TranslatedExpression arithmetic(String opname, Expression[] a) {
    if (a.length == 2) {
      requireCrispInteger(a[0], opname);
      requireCrispInteger(a[1], opname);
      if ("*".equals(opname)) {
        requireLinearProduct(a[0], a[1]);
      }
      TranslatedExpression l = argResult(a[0]);
      TranslatedExpression r = argResult(a[1]);
      return new TranslatedExpression(
          Smt.and(List.of(l.defined(), r.defined())), Smt.app(opname, l.value(), r.value()));
    }
    if (a.length == 1 && "-".equals(opname)) {
      requireCrispInteger(a[0], opname);
      TranslatedExpression operand = argResult(a[0]);
      return new TranslatedExpression(operand.defined(), Smt.app("-", operand.value()));
    }
    if (a.length == 1 && "+".equals(opname)) {
      requireCrispInteger(a[0], opname);
      return argResult(a[0]);
    }
    throw unsupported(
        boundaryOfOperator(opname), "operator '" + opname + "' with " + a.length + " argument(s)");
  }

  /**
   * USE's Java-backed Integer {@code div} for the exact CompanyER denominator shape: a filtered
   * allInstances cardinality. That count is itself a non-constant SMT term (it depends on which
   * candidate slots satisfy the select predicate), and SMT-LIB {@code div} with a non-numeral
   * second argument is nonlinear arithmetic -- confirmed directly against the pinned Z3 5.1.0
   * binary: {@code (declare-const n Int) (declare-const d Int) (assert (= d 2)) (assert (= (div n
   * d) 3))} is accepted (numeral-shaped {@code d} after propagation is NOT enough; Z3 requires the
   * term itself to be a numeral), but the analogous term built from a non-constant count via {@code
   * sizeTerm} is rejected with {@code "logic does not support nonlinear arithmetic"}, exactly the
   * same restriction {@link #requireLinearProduct} already documents for {@code *}.
   *
   * <p>The fix is the same shape as {@code *}'s own literal-coefficient restriction, generalized:
   * the divisor here is not an arbitrary variable, it is {@code sizeTerm} of a population whose
   * Java-side candidate-slot count ({@code population.size()}) is known at translation time, so
   * the true count is provably one of finitely many literal values {@code 0..population.size()}.
   * Case-splitting over that exhaustive, closed range turns one nonlinear {@code div} into a chain
   * of linear ones, each against a compile-time numeral -- sound because {@code sizeTerm} sums
   * exactly {@code population.size()} zero/one indicators, so it cannot take any value outside that
   * range. Divisor 0 (an empty matching population) makes the result undefined, matching Java's own
   * {@code ArithmeticException}-to-{@code Undefined} conversion this project already relies on
   * elsewhere (see {@code Op_integer_idiv}/{@code Op_uInteger_div} in use-core).
   */
  private TranslatedExpression integerDivision(Expression[] arguments) {
    if (arguments.length != 2
        || !(arguments[1] instanceof ExpStdOp size)
        || !"size".equals(size.opname())
        || size.args().length != 1
        || !(size.args()[0] instanceof ExpSelect select)) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator 'div' outside the verified Integer / filtered-allInstances-size shape");
    }
    requireCrispInteger(arguments[0], "div");
    requireCrispInteger(arguments[1], "div");
    TranslatedExpression numerator = argResult(arguments[0]);
    List<PopulationMember> population = selectedAllInstancesPopulation(select);
    SmtTerm count = sizeTerm(population);
    SmtTerm zero = Smt.intLit(BigInteger.ZERO);
    SmtTerm quotient = zero;
    for (int k = population.size(); k >= 1; k--) {
      SmtTerm literalK = Smt.intLit(BigInteger.valueOf(k));
      SmtTerm positiveQuotient = Smt.app("div", numerator.value(), literalK);
      SmtTerm negativeQuotient =
          Smt.app("-", Smt.app("div", Smt.app("-", numerator.value()), literalK));
      SmtTerm divByK =
          Smt.ite(Smt.app(">=", numerator.value(), zero), positiveQuotient, negativeQuotient);
      quotient = Smt.ite(Smt.eq(count, literalK), divByK, quotient);
    }
    return new TranslatedExpression(
        Smt.and(List.of(numerator.defined(), Smt.not(Smt.eq(count, zero)))), quotient);
  }

  /**
   * {@code *} needs a guard {@code +}/{@code -} do not: this project's SMT script is pinned to
   * {@code QF_LIA} (linear integer arithmetic, {@code SmtModelFinder.java}), and Z3 enforces that
   * restriction syntactically -- {@code (* <non-numeral> <non-numeral>)} is rejected outright with
   * {@code "logic does not support nonlinear arithmetic"} even when both sides are transitively
   * pinned to a specific value by other assertions (confirmed by feeding the pinned Z3 5.1.0 binary
   * {@code (declare-const x Int) (declare-const y Int) (assert (= x 2)) (assert (= y 3)) (assert (=
   * (* x y) 6))} directly: the solver errors before ever reaching {@code check-sat}). {@code
   * (* <numeral-or-negated-numeral> <var>)} and {@code (* <var> <numeral-or-negated-numeral>)} are
   * both linear and confirmed accepted the same way. So a plain crisp Integer product is supported
   * exactly when at least one OCL-level operand is a literal (optionally unary-minus-wrapped, e.g.
   * {@code -2}, mirroring the negative-literal shape {@link #arithmetic} already documents for unary
   * {@code -}) -- {@code a.i * 2}, {@code 2 * a.j} -- and refused, rather than silently handed to
   * the solver to error out on, when BOTH operands are non-constant, e.g. {@code a.i * a.j}. This is
   * a genuinely narrower slice than {@code +}/{@code -} (which are linear for any two operands): it
   * is a property of the PINNED SOLVER LOGIC, not of OCL's own arithmetic semantics, and widening it
   * (moving to {@code QF_NIA}, or reformulating products differently) is out of this task's scope --
   * changing the project's pinned decidable fragment is exactly the kind of solver-plumbing decision
   * this task was told not to make.
   */
  private static void requireLinearProduct(Expression left, Expression right) {
    if (!isIntegerLiteral(left) && !isIntegerLiteral(right)) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '*' between two non-constant Integer operands: multiplying two non-constant"
              + " terms is nonlinear arithmetic, which this project's pinned QF_LIA solver logic"
              + " does not accept (confirmed against the real Z3 binary) -- only a product with at"
              + " least one compile-time Integer literal operand is supported");
    }
  }

  private static boolean isIntegerLiteral(Expression e) {
    if (e instanceof ExpConstInteger) {
      return true;
    }
    return e instanceof ExpStdOp op
        && "-".equals(op.opname())
        && op.args().length == 1
        && isIntegerLiteral(op.args()[0]);
  }

  private static void requireCrispInteger(Expression e, String opname) {
    if (!e.type().isTypeOfInteger()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '"
              + opname
              + "' over a non-Integer operand of type "
              + e.type()
              + ": only plain crisp Integer arithmetic is supported");
    }
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
    if (comparisonArgs[1] instanceof ExpAttrOp rightAttribute
        && attribute.type().isTypeOfUReal()
        && rightAttribute.type().isTypeOfUReal()
        && List.of("<", ">").contains(comparison.opname())) {
      return uTypeSymmetricThreshold(comparison, attribute, rightAttribute, projectionArgs[1]);
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
   * Translates {@code (leftAttr < rightAttr).toBooleanC(confidence)} / {@code (leftAttr >
   * rightAttr)...} between two UReal attributes -- the one narrow slice of "uncertain versus
   * uncertain" this translation supports, not the general case (see
   * FragmentBoundary#UTYPE_UNCERTAIN_VERSUS_UNCERTAIN for everything else, still refused).
   *
   * <p>USE's live evaluator ({@code UReal#calculate}, the equal-uncertainty branch) computes {@code
   * P(A<B)} via a crossing-point method, NOT the naive "difference of two independent Gaussians is
   * positive" formula a reasonable first guess would assume -- confirmed by direct probe against the
   * compiled evaluator, not read off the source alone: {@code P(A<B)} is exactly 0 whenever {@code
   * mean(A) > mean(B)}, however close the means are, not a small positive tail probability. That
   * reduces, uniformly across both branches (no case-split needed in the encoding -- verified
   * numerically, not assumed) to {@code P(A<B) >= confidence <=> meanB - meanA >=
   * 2*sigma*inverseCNDF((confidence+1)/2)}, and the mirror image for {@code >}. {@link
   * URealThresholdBoundary#encloseSymmetric} bisects that standardized constant the same way {@link
   * URealThresholdBoundary#enclose} already does for the single-sided case -- against the live
   * evaluator itself, not a hand-derived {@code erf} formula.
   *
   * <p>The one precondition that makes this SOUND rather than merely convenient: both attributes'
   * uncertainty must be a PROVEN SINGLETON -- their configured {@code uncertainty}-component {@link
   * AttributeDomain} forces exactly one value, at translation time, via real emitted bounds (not
   * assumed from config text). Uncertainty in this encoder is an ordinary free SMT symbol per
   * object slot (see {@link AttributeEncoder}), not necessarily a constant; asserting the two
   * symbols equal as a solver-level constraint instead of verifying this statically would silently
   * shrink the search space to a scenario the original OCL invariant never asked for. Anything short
   * of two proven-equal singleton domains stays refused under
   * {@code UTYPE_UNCERTAIN_VERSUS_UNCERTAIN} -- including UInteger operands (its widen-through-UReal
   * comparison story is a different, unverified derivation, deliberately not attempted here) and
   * {@code <=}/{@code >=} (the "eq" probability mass this crossing-point model assigns needs its own
   * derivation this task did not attempt).
   */
  private TranslatedExpression uTypeSymmetricThreshold(
      ExpStdOp comparison, ExpAttrOp leftAttribute, ExpAttrOp rightAttribute, Expression confidenceArg) {
    BigDecimal confidence =
        decimalLiteral(confidenceArg, "confidence threshold", FragmentBoundary.UTYPE_CORE);

    VariableBinding leftBinding = context.binding(variableNameOf(leftAttribute.objExp()));
    AttributeValues leftValues =
        context.attributeValues(leftBinding.className(), leftAttribute.attr().name());
    VariableBinding rightBinding = context.binding(variableNameOf(rightAttribute.objExp()));
    AttributeValues rightValues =
        context.attributeValues(rightBinding.className(), rightAttribute.attr().name());
    if (leftValues.type() != AttributeType.UREAL || rightValues.type() != AttributeType.UREAL) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN,
          "uncertain-vs-uncertain comparison outside the verified UReal/UReal shape");
    }

    BigDecimal leftUncertainty =
        singletonValue(
            context.attributeDomain(leftBinding.className(), leftAttribute.attr().name(), "uncertainty"));
    BigDecimal rightUncertainty =
        singletonValue(
            context.attributeDomain(
                rightBinding.className(), rightAttribute.attr().name(), "uncertainty"));
    if (leftUncertainty == null || rightUncertainty == null || leftUncertainty.compareTo(rightUncertainty) != 0) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN,
          "uncertain-vs-uncertain comparison whose two operands' uncertainty is not a proven-equal"
              + " configured constant (left="
              + (leftUncertainty == null ? "not a singleton domain" : leftUncertainty)
              + ", right="
              + (rightUncertainty == null ? "not a singleton domain" : rightUncertainty)
              + ") -- the general unequal-uncertainty case needs its own, unverified derivation");
    }

    SmtTerm leftValue = Smt.sym(leftValues.valueNames().get(leftBinding.slotIndex()));
    SmtTerm rightValue = Smt.sym(rightValues.valueNames().get(rightBinding.slotIndex()));
    boolean lessThan = "<".equals(comparison.opname());
    SmtTerm exact = Smt.app(comparison.opname(), leftValue, rightValue);
    if (mode == TranslationMode.NOMINAL) {
      return defined(exact);
    }
    if (leftUncertainty.signum() == 0) {
      // Both sides proven crisp: the crossing-point model degenerates to an ordinary strict
      // comparison (confirmed against calculate()'s own s1==0&&s2==0 branch), not the boundary
      // formula below, which divides conceptually by a zero sigma.
      return defined(exact);
    }
    URealThresholdBoundary.Enclosure enclosure = URealThresholdBoundary.encloseSymmetric(confidence);
    BigDecimal standardizedBoundary = positivePolarity ? enclosure.upper() : enclosure.lower();
    // encloseSymmetric bisects P(UReal(0,1) < UReal(midpoint,1)) directly against `confidence`
    // (not (confidence+1)/2), so `midpoint` at the boundary already equals 2*inverseCNDF((confidence
    // +1)/2) -- the factor of 2 from the derivation is already baked into this constant. Multiplying
    // sigma by 2 again here double-counted it (found live: emitted offset was ~4*sigma*inverseCNDF
    // instead of 2*sigma*inverseCNDF, confirmed against Z3 turning a should-be-SAT case UNSAT).
    SmtTerm offset = Smt.app("*", Smt.realLit(leftUncertainty), Smt.realLit(standardizedBoundary));
    SmtTerm difference =
        lessThan
            ? Smt.app("-", rightValue, leftValue)
            : Smt.app("-", leftValue, rightValue);
    return defined(Smt.app(">=", difference, offset));
  }

  /** The single configured value an {@link AttributeDomain} is proven to force, or null. */
  private static BigDecimal singletonValue(AttributeDomain domain) {
    if (domain.enumeratedValues().size() == 1) {
      try {
        return new BigDecimal(domain.enumeratedValues().get(0));
      } catch (NumberFormatException notNumeric) {
        return null;
      }
    }
    if (domain.lowerBound() != null
        && domain.upperBound() != null
        && domain.lowerBound().compareTo(domain.upperBound()) == 0) {
      return domain.lowerBound();
    }
    return null;
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
    if (l instanceof ExpVariable lv
        && r instanceof ExpVariable rv
        && !localBindings.containsKey(lv.getVarname())
        && !localBindings.containsKey(rv.getVarname()))
      return defined(
          Smt.bool(context.binding(lv.getVarname()).equals(context.binding(rv.getVarname()))));
    if ((l instanceof ExpVariable cl && localBindings.containsKey(cl.getVarname()))
        || (r instanceof ExpVariable cr && localBindings.containsKey(cr.getVarname()))) {
      TranslatedExpression local = contentAwareEquality(l, r);
      if (local != null) return local;
    }
    if (l instanceof ExpConstString s) {
      TranslatedExpression content = contentAwareEquality(l, r);
      if (content != null) return content;
      TranslatedExpression other = argResult(r);
      return useEquality(defined(resolve(s, r)), other);
    }
    if (r instanceof ExpConstString s) {
      TranslatedExpression content = contentAwareEquality(l, r);
      if (content != null) return content;
      TranslatedExpression other = argResult(l);
      return useEquality(other, defined(resolve(s, l)));
    }
    if (l instanceof ExpConstEnum en) {
      TranslatedExpression content = contentAwareEquality(l, r);
      if (content != null) return content;
      TranslatedExpression other = argResult(r);
      return useEquality(defined(resolve(en, r)), other);
    }
    if (r instanceof ExpConstEnum en) {
      TranslatedExpression content = contentAwareEquality(l, r);
      if (content != null) return content;
      TranslatedExpression other = argResult(l);
      return useEquality(other, defined(resolve(en, l)));
    }
    if (l instanceof ExpAttrOp la
        && la.objExp() instanceof ExpVariable lv
        && !localBindings.containsKey(lv.getVarname())
        && r instanceof ExpAttrOp ra
        && ra.objExp() instanceof ExpVariable rv
        && !localBindings.containsKey(rv.getVarname())) {
      TranslatedExpression crossDomain = crossDomainStringOrEnumEquality(la, lv, ra, rv);
      if (crossDomain != null) return crossDomain;
    }
    if (l instanceof ExpAttrOp la2
        && la2.objExp() instanceof ExpNavigation lnav
        && !lnav.getDestination().isCollection()
        && r instanceof ExpAttrOp ra2
        && ra2.objExp() instanceof ExpVariable rv2
        && !localBindings.containsKey(rv2.getVarname())) {
      TranslatedExpression crossDomain =
          crossDomainNavigatedAttributeEquality(lnav, la2.attr(), ra2, rv2);
      if (crossDomain != null) return crossDomain;
    }
    if (r instanceof ExpAttrOp ra3
        && ra3.objExp() instanceof ExpNavigation rnav
        && !rnav.getDestination().isCollection()
        && l instanceof ExpAttrOp la3
        && la3.objExp() instanceof ExpVariable lv3
        && !localBindings.containsKey(lv3.getVarname())) {
      TranslatedExpression crossDomain =
          crossDomainNavigatedAttributeEquality(rnav, ra3.attr(), la3, lv3);
      if (crossDomain != null) return crossDomain;
    }
    if (l instanceof ExpNavigation ln
        && r instanceof ExpNavigation rn
        && !ln.getDestination().isCollection()
        && !rn.getDestination().isCollection()) return navigationEquals(ln, rn);
    if (l instanceof ExpNavigation ln
        && !ln.getDestination().isCollection()
        && r instanceof ExpVariable rv
        && !localBindings.containsKey(rv.getVarname())) return navigationEqualsVariable(ln, rv);
    if (r instanceof ExpNavigation rn
        && !rn.getDestination().isCollection()
        && l instanceof ExpVariable lv
        && !localBindings.containsKey(lv.getVarname())) return navigationEqualsVariable(rn, lv);
    return useEquality(argResult(l), argResult(r));
  }

  /**
   * {@code a.attr1 = b.attr2}-shaped equality between two BARE attribute accesses, string/enum-typed
   * on at least one side. Returns {@code null} (defers to the caller's ordinary {@link #useEquality}
   * path) whenever either side is not String/Enum-typed -- Integer/Real/Boolean attribute values ARE
   * the literal value itself ({@link AttributeEncoder}'s {@code guardInteger}/{@code guardReal}
   * assert the SMT symbol equal to the actual configured number), so raw {@code Smt.eq} is already
   * correct for them.
   *
   * <p>String and Enum are different: {@code guardString} assigns each attribute's value an index
   * that is POSITIONAL WITHIN THAT ONE ATTRIBUTE'S OWN configured candidate list ({@code
   * AttributeDomain#enumeratedValues}), with no global identity tying the same literal to the same
   * integer across two independently-configured domains. The generic {@link #useEquality} path
   * (comparing {@code left.value() = right.value()} as raw SMT terms) therefore silently equates two
   * DIFFERENT literals whenever they happen to occupy the same position in their own attribute's
   * list -- e.g. both attributes' first configured candidate. Discovered via a {@code
   * WitnessAttributionException} on a derived-association {@code any()}-match predicate comparing a
   * String attribute of one class against a String attribute of another with disjoint, differently-
   * ordered domains (the solver reported a match the reconstructed witness's real string values did
   * not have), reproduced in isolation with a single class's two String attributes ({@code C.s1 =
   * C.s2}, domains {@code {'Zulu','Yankee'}} vs {@code {'Yankee','Zulu'}}).
   *
   * <p>The fix compares by CONTENT: a disjunction over every (i, j) index pair whose configured
   * literals actually match, each conjunct pinning both sides to that pair's index. When the two
   * domains are identical in content and order this is logically equivalent to the raw {@code i = j}
   * the old path emitted (no behavior change for same-domain corpus scenarios, e.g. comparing one
   * attribute across two objects of the same class); it only differs where the old path was unsound.
   *
   * <p>Deliberately narrow, matching this method's one proven shape: both operands must be a bare
   * {@code <var>.<attr>} access. {@link #crossDomainNavigatedAttributeEquality} covers the sibling
   * shape with one operand a single-hop navigated attribute ({@code a.role.attr}) -- both were
   * needed together: {@code DerivedAssociationEncoder}'s {@code any()}-match predicate is this
   * bare-vs-bare shape, but the invariant that CONSUMES its result ({@code g.widget.wname =
   * g.targetname} in the discovering scenario) is the navigated-vs-bare shape, and fixing only one
   * of the two made them disagree with EACH OTHER at the SMT level (the selection formula correctly
   * requiring content equality while the consuming comparison still compared raw indices),
   * regressing a previously-passing test with a manufactured UNSAT. A let-bound variable on either
   * side is not yet covered (still routes through the ordinary, unsound {@link #useEquality} path),
   * left as an explicitly open extension of this same finding rather than silently assumed safe.
   */
  private TranslatedExpression crossDomainStringOrEnumEquality(
      ExpAttrOp l, ExpVariable lv, ExpAttrOp r, ExpVariable rv) {
    VariableBinding lb = context.binding(lv.getVarname());
    VariableBinding rb = context.binding(rv.getVarname());
    AttributeValues lav = context.attributeValues(lb.className(), l.attr().name());
    AttributeValues rav = context.attributeValues(rb.className(), r.attr().name());
    if ((lav.type() != AttributeType.STRING && lav.type() != AttributeType.ENUM)
        || (rav.type() != AttributeType.STRING && rav.type() != AttributeType.ENUM)) {
      return null;
    }
    guardAgainstUncertainAttribute(lav);
    guardAgainstUncertainAttribute(rav);
    AttributeDomain ld = context.attributeDomain(lb.className(), l.attr().name());
    AttributeDomain rd = context.attributeDomain(rb.className(), r.attr().name());
    SmtTerm lValue = Smt.sym(lav.valueNames().get(lb.slotIndex()));
    SmtTerm rValue = Smt.sym(rav.valueNames().get(rb.slotIndex()));
    List<SmtTerm> matches = new ArrayList<>();
    for (int i = 0; i < ld.enumeratedValues().size(); i++) {
      for (int j = 0; j < rd.enumeratedValues().size(); j++) {
        if (ld.enumeratedValues().get(i).equals(rd.enumeratedValues().get(j))) {
          matches.add(
              Smt.and(
                  List.of(
                      Smt.eq(lValue, Smt.intLit(BigInteger.valueOf(i))),
                      Smt.eq(rValue, Smt.intLit(BigInteger.valueOf(j))))));
        }
      }
    }
    return defined(Smt.or(matches));
  }

  /**
   * The sibling of {@link #crossDomainStringOrEnumEquality} for {@code a.role.attr = b.attr2}
   * (single-hop navigated attribute compared to a bare attribute), String/Enum-typed on at least
   * one side. Same root cause, same fix shape: per-destination-slot, the true condition is "{@code
   * source} links to this slot AND that slot's configured literal actually equals the bare side's
   * configured literal" -- built as a content-correct disjunction over (destination-domain-index,
   * bare-domain-index) pairs, exactly {@link #crossDomainStringOrEnumEquality}'s per-pair matching
   * applied once per destination slot instead of once overall, then OR'd with that slot's {@link
   * #linkTerm}. The link disjunction ALSO becomes the equality's definedness half (an unlinked
   * navigation makes the whole comparison definitely-false against a defined bare attribute, via
   * {@link #useEquality}'s existing total-equality rule -- unchanged from before this fix).
   *
   * <p>One hop only, matching {@link #navigatedAttribute}'s own restriction: {@code
   * navigation.getObjectExpression()} must be a bare variable, not another navigation. Returns
   * {@code null} (defers to the caller's ordinary path) whenever that restriction fails, the
   * destination sits on an association class (a different, index-pointer-based mechanism {@link
   * #associationClassNavigatedAttribute} owns, not touched here), or either attribute is not
   * String/Enum-typed.
   */
  private TranslatedExpression crossDomainNavigatedAttributeEquality(
      ExpNavigation navigation, MAttribute navAttribute, ExpAttrOp bare, ExpVariable bareVar) {
    if (!(navigation.getObjectExpression() instanceof ExpVariable navSourceVar)) {
      return null;
    }
    VariableBinding source = context.binding(navSourceVar.getVarname());
    MNavigableElement destination = resolveRedefinedDestination(navigation.getDestination(), source);
    if (destination.association() instanceof MAssociationClass) {
      return null;
    }
    String destClass = destination.cls().name();
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    AttributeValues destAttrValues = context.attributeValues(destClass, navAttribute.name());
    VariableBinding bareBinding = context.binding(bareVar.getVarname());
    AttributeValues bareAttrValues =
        context.attributeValues(bareBinding.className(), bare.attr().name());
    if ((destAttrValues.type() != AttributeType.STRING && destAttrValues.type() != AttributeType.ENUM)
        || (bareAttrValues.type() != AttributeType.STRING
            && bareAttrValues.type() != AttributeType.ENUM)) {
      return null;
    }
    guardEndAgainstUncertainAttribute(destSlots, navAttribute, destAttrValues);
    guardAgainstUncertainAttribute(bareAttrValues);
    AttributeDomain destDomain = context.attributeDomain(destClass, navAttribute.name());
    AttributeDomain bareDomain =
        context.attributeDomain(bareBinding.className(), bare.attr().name());
    SmtTerm bareValue = Smt.sym(bareAttrValues.valueNames().get(bareBinding.slotIndex()));

    List<SmtTerm> targets = new ArrayList<>();
    List<SmtTerm> matches = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      SmtTerm link = linkTerm(links, destination, source, k);
      targets.add(link);
      SmtTerm destValue = valueSymbolForEndSlot(destSlots, destAttrValues, navAttribute, k);
      List<SmtTerm> contentMatches = new ArrayList<>();
      for (int di = 0; di < destDomain.enumeratedValues().size(); di++) {
        for (int bi = 0; bi < bareDomain.enumeratedValues().size(); bi++) {
          if (destDomain.enumeratedValues().get(di).equals(bareDomain.enumeratedValues().get(bi))) {
            contentMatches.add(
                Smt.and(
                    List.of(
                        Smt.eq(destValue, Smt.intLit(BigInteger.valueOf(di))),
                        Smt.eq(bareValue, Smt.intLit(BigInteger.valueOf(bi))))));
          }
        }
      }
      matches.add(Smt.and(List.of(link, Smt.or(contentMatches))));
    }
    TranslatedExpression navigatedSide = new TranslatedExpression(Smt.or(targets), Smt.bool(false));
    TranslatedExpression bareSide = defined(bareValue);
    return useEquality(navigatedSide, bareSide, Smt.or(matches));
  }

  /**
   * Content-aware equality between two operands that are both String/Enum-typed values this
   * translator can DESCRIBE: a let-bound variable (carrying its initializer's configured candidate
   * list, see {@link LocalBinding}), a bare or single-hop-navigated attribute access, or a literal
   * (its own singleton content). Returns {@code null} when either side is not such an operand,
   * deferring to the caller's ordinary path -- Integer/Real/Boolean values ARE their SMT symbol, so
   * raw equality is already correct for them.
   *
   * <p>This is the same positional-index soundness finding {@link #crossDomainStringOrEnumEquality}
   * fixed for the bare-vs-bare attribute shape, extended to the shapes that finding's turn
   * deliberately left open: a LET-BOUND String/Enum variable compared against anything (it used to
   * route through the raw, unsound {@link #useEquality} fallback and manufactured witnesses USE's
   * own re-evaluation denied -- {@code WitnessAttributionException} on swapped or disjoint
   * domains), and a free-standing literal against a non-bare-attribute operand (it used to fail
   * closed with "string literal compared against a non-attribute expression"). Bare-attribute
   * operands are also described here, superseding the old {@code resolve}-based emission with a
   * logically equivalent one; the old path remains as the fallback and still owns its refusal for
   * operands no descriptor can capture.
   */
  private TranslatedExpression contentAwareEquality(Expression l, Expression r) {
    ContentOperand lo = contentOperand(l);
    ContentOperand ro = contentOperand(r);
    if (lo == null || ro == null) return null;
    return useEquality(lo.translated(), ro.translated(), contentMatches(lo, ro));
  }

  /**
   * The {@link ContentOperand} describing {@code e}, or {@code null}. Every returned operand's
   * values are indices into {@code enumeratedValues()} (null only for a let rooted in
   * {@code oclUndefined}, whose value can never be consulted under {@link #useEquality}'s
   * both-defined rule -- {@link #visitLet} refuses every other domain-less initializer).
   */
  private ContentOperand contentOperand(Expression e) {
    if (e instanceof ExpVariable v) {
      LocalBinding local = localBindings.get(v.getVarname());
      if (local == null || !local.stringOrEnum()) {
        return null;
      }
      return new ContentOperand(
          Smt.sym(local.valueSymbol()),
          Smt.sym(local.definedSymbol()),
          local.enumeratedValues(),
          false);
    }
    if (e instanceof ExpConstString s) {
      return new ContentOperand(
          Smt.intLit(BigInteger.ZERO), Smt.bool(true), List.of(s.value()), true);
    }
    if (e instanceof ExpConstEnum en) {
      return new ContentOperand(
          Smt.intLit(BigInteger.ZERO), Smt.bool(true), List.of(en.value()), true);
    }
    if (e instanceof ExpAttrOp a) {
      AttributeValues vals;
      AttributeDomain domain;
      if (a.objExp() instanceof ExpVariable v && !localBindings.containsKey(v.getVarname())) {
        VariableBinding b = context.binding(v.getVarname());
        vals = context.attributeValues(b.className(), a.attr().name());
        domain = context.attributeDomain(b.className(), a.attr().name());
      } else if (a.objExp() instanceof ExpNavigation nav
          && !nav.getDestination().isCollection()
          && nav.getObjectExpression() instanceof ExpVariable sv
          && !localBindings.containsKey(sv.getVarname())) {
        VariableBinding source = context.binding(sv.getVarname());
        MNavigableElement destination =
            resolveRedefinedDestination(nav.getDestination(), source);
        String destClass = destination.cls().name();
        vals = context.attributeValues(destClass, a.attr().name());
        domain = context.attributeDomain(destClass, a.attr().name());
      } else {
        return null;
      }
      if (vals.type() != AttributeType.STRING && vals.type() != AttributeType.ENUM) {
        return null;
      }
      guardAgainstUncertainAttribute(vals);
      TranslatedExpression translated = argResult(a);
      return new ContentOperand(
          translated.value(), translated.defined(), domain.enumeratedValues(), false);
    }
    return null;
  }

  /**
   * "The two values are equal" as a disjunction over every (left-index, right-index) pair whose
   * configured literals actually match, each conjunct pinning both operands to that pair's index.
   * Logically equivalent to raw index equality whenever the two candidate lists are identical in
   * content and order; differing from it exactly where raw equality was unsound. A literal operand
   * carries its content at index 0 of its own singleton list, so it is pinned directly to the
   * matching index of the other side (or the match is simply false when the content is absent)
   * instead of contributing a trivial {@code (= 0 0)} conjunct.
   */
  private static SmtTerm contentMatches(ContentOperand lo, ContentOperand ro) {
    if (lo.enumeratedValues() == null || ro.enumeratedValues() == null) {
      // Only an oclUndefined-rooted let can lack its candidate list; its definedness is always
      // false, so useEquality never consults this term.
      return Smt.bool(false);
    }
    if (lo.literal() && ro.literal()) {
      return Smt.bool(lo.enumeratedValues().get(0).equals(ro.enumeratedValues().get(0)));
    }
    if (lo.literal() || ro.literal()) {
      ContentOperand literal = lo.literal() ? lo : ro;
      ContentOperand other = lo.literal() ? ro : lo;
      int idx = other.enumeratedValues().indexOf(literal.enumeratedValues().get(0));
      if (idx < 0) {
        return Smt.bool(false);
      }
      return Smt.eq(other.value(), Smt.intLit(BigInteger.valueOf(idx)));
    }
    List<SmtTerm> matches = new ArrayList<>();
    for (int i = 0; i < lo.enumeratedValues().size(); i++) {
      for (int j = 0; j < ro.enumeratedValues().size(); j++) {
        if (lo.enumeratedValues().get(i).equals(ro.enumeratedValues().get(j))) {
          matches.add(
              Smt.and(
                  List.of(
                      Smt.eq(lo.value(), Smt.intLit(BigInteger.valueOf(i))),
                      Smt.eq(ro.value(), Smt.intLit(BigInteger.valueOf(j))))));
        }
      }
    }
    return Smt.or(matches);
  }

  /**
   * One side of a {@link #contentAwareEquality} comparison: an SMT value term that is an index
   * into {@code enumeratedValues()} (in the same order as the configured candidate list), the
   * operand's definedness, and the candidate list itself. A literal is its own singleton list.
   */
  private record ContentOperand(
      SmtTerm value,
      SmtTerm defined,
      List<String> enumeratedValues,
      boolean literal) {
    TranslatedExpression translated() {
      return new TranslatedExpression(defined, value);
    }
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
   * link implies both endpoints exist; see {@code AssociationLinkEncoder}). A CONTEXT-BOUND object
   * variable (the invariant's own context, a quantifier iterator, or an object-any let binding --
   * all Java-side {@link VariableBinding}s, never {@link LocalBinding}s) is the sibling case: it
   * names a slot rather than a value, and its definedness is that slot's own exists symbol, which
   * {@code c = oclUndefined(C)} / {@code c <> oclUndefined(C)} / {@code c.isDefined()} /
   * {@code c.oclIsUndefined()} all reduce to. Scalar {@link LocalBinding}s (a let-bound Integer or
   * String, which DO have standalone value terms) keep the ordinary translator path. Every other
   * shape goes through the ordinary translator, so an operand outside the supported fragment still
   * fails closed with its own located reason.
   */
  private SmtTerm definednessOf(Expression e) {
    if (e instanceof ExpNavigation navigation && !navigation.getDestination().isCollection()) {
      return singleValuedNavigationDefined(navigation);
    }
    if (e instanceof ExpNavigationClassifierSource) {
      // An association-class instance's two ends are always bound the moment the instance
      // itself exists -- see associationClassNavigatedAttribute's own javadoc for the confirmed
      // use-core semantics this mirrors. No link search needed at all, unlike the ExpNavigation
      // branch above.
      return Smt.bool(true);
    }
    if (e instanceof ExpVariable v && !localBindings.containsKey(v.getVarname())) {
      VariableBinding b = context.binding(v.getVarname());
      return Smt.sym(context.slotsFor(b.className()).existsNames().get(b.slotIndex()));
    }
    return argResult(e).defined();
  }

  /**
   * True exactly when the source slot links to some target slot of the navigated association.
   * Redirect-aware for {@code redefines} via {@link #resolveRedefinedDestination}, the same
   * primitive {@link #populationOf} already uses -- a subclass-typed source navigating a
   * superclass-declared role that its own association redefines reads the REDEFINING grid, not
   * the redefined one, matching real UML redefinition semantics rather than failing closed with
   * "association ... does not connect class ..." the way this used to.
   */
  private SmtTerm singleValuedNavigationDefined(ExpNavigation navigation) {
    VariableBinding source = context.binding(variableNameOf(navigation.getObjectExpression()));
    MNavigableElement destination = resolveRedefinedDestination(navigation.getDestination(), source);
    if (destination.association() instanceof MAssociationClass associationClass) {
      return associationClassEndNavigationDefined(associationClass, destination, source);
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destinationSlots = destinationEndView(links, destination);
    List<SmtTerm> targets = new ArrayList<>();
    for (int k = 0; k < destinationSlots.capacity(); k++) {
      targets.add(linkTerm(links, destination, source, k));
    }
    return Smt.or(targets);
  }

  /**
   * {@code p.employer}-shaped: navigating from an ORDINARY end (Person) toward the association
   * class's OTHER end (Company), through the classifier itself. There is no {@link AssociationLinks}
   * grid to consult here either -- same reason as {@link #associationClassNavigatedAttribute} --
   * so existence is resolved the mirror way: does some EXISTING association-class slot have its
   * OWN pointer for the end {@code source} sits at (the end OPPOSITE {@code destination}) equal to
   * {@code source}'s own slot index. Multiplicity on that end (enforced by {@link
   * AssociationClassPointerEncoder}'s degree constraint) already guarantees at most one such slot;
   * this only needs to find it, matching {@link #navigationEquals}'s own "find, don't enforce
   * uniqueness" convention for the ordinary link-grid case.
   */
  private SmtTerm associationClassEndNavigationDefined(
      MAssociationClass associationClass, MNavigableElement destination, VariableBinding source) {
    MNavigableElement sourceEnd = oppositeEnd(destination);
    ObjectSlots associationClassSlots = context.slotsFor(associationClass.name());
    AttributeValues pointer = associationClassPointer(associationClass.name(), sourceEnd);
    List<SmtTerm> matches = new ArrayList<>();
    for (int k = 0; k < associationClassSlots.capacity(); k++) {
      SmtTerm exists = Smt.sym(associationClassSlots.existsNames().get(k));
      SmtTerm pointsToSource =
          Smt.eq(
              Smt.sym(pointer.valueNames().get(k)),
              Smt.intLit(BigInteger.valueOf(source.slotIndex())));
      matches.add(Smt.and(List.of(exists, pointsToSource)));
    }
    return Smt.or(matches);
  }

  private static MNavigableElement oppositeEnd(MNavigableElement end) {
    List<? extends MNavigableElement> ends = end.association().associationEnds();
    return end.equals(ends.get(0)) ? ends.get(1) : ends.get(0);
  }

  /**
   * {@code c.b}-shaped: navigating via {@code destination}'s role name (declared on the
   * SUPERCLASS-typed association, e.g. {@code AB}) from a source whose OWN declared class is a
   * narrower type that REDEFINES this role (e.g. {@code C < A}, {@code CD}'s {@code c redefines
   * a}, {@code d redefines b}). USE/UML redefinition semantics -- confirmed directly against the
   * real evaluator, not assumed -- mean the redefining association COMPLETELY REPLACES the
   * redefined one for instances of the narrower type, not merely adds to it: reading {@code
   * destination}'s own (redefined) association's grid for such a source would silently see it as
   * structurally disconnected (a {@code C}-typed source has no direct link registered in {@code
   * AB}'s grid at all, since {@code C} draws from its own, separate slot pool), reading back
   * "always empty" -- exactly the false-vacuous-truth defect {@code Redefines.use}'s own header
   * comment documents against Kodkod's translator. This redirect is load-bearing for soundness,
   * not an optimisation.
   *
   * <p>Resolved STATICALLY from {@code source}'s own declared class. That is complete, not merely
   * a common case: a {@link VariableBinding} carries exactly one fixed class for its whole
   * lifetime throughout this encoder (there is no "a variable declared {@code A} that happens to
   * hold a {@code C} at solve time" case to additionally handle) -- quantifying over a superclass
   * already iterates each concrete descendant through its OWN separately-bound slots ({@link
   * PolymorphicRange}), so by the time a bare variable is bound at all, its declared class already
   * IS the most specific one in play.
   */
  private static MNavigableElement resolveRedefinedDestination(
      MNavigableElement destination, VariableBinding source) {
    for (MAssociationEnd redefining : destination.getRedefiningEnds()) {
      if (oppositeEnd(redefining).cls().name().equals(source.className())) {
        return redefining;
      }
    }
    return destination;
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
    ObjectSlots destSlots = destinationEndView(links, left.getDestination());
    VariableBinding leftSource = context.binding(variableNameOf(left.getObjectExpression()));
    VariableBinding rightSource = context.binding(variableNameOf(right.getObjectExpression()));
    List<SmtTerm> sharedTarget = new ArrayList<>();
    List<SmtTerm> leftTargets = new ArrayList<>();
    List<SmtTerm> rightTargets = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      SmtTerm leftLink = linkTerm(links, left.getDestination(), leftSource, k);
      SmtTerm rightLink = linkTerm(links, left.getDestination(), rightSource, k);
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
   * {@code f.primaryParent <> f}-shaped: a single-valued navigation compared against a BARE
   * object variable rather than another navigation ({@link #navigationEquals}'s own shape). A bare
   * variable is always defined (it names an already-bound, live object), so USE's total-equality
   * rule ({@link #useEquality}) collapses to exactly "the navigation is defined AND its value is
   * this specific target slot" -- which {@link #linkTerm} already computes directly, with no need
   * to build and immediately discard a separate definedness term the way {@link #navigationEquals}
   * does for two navigations. Always defined, matching every other comparison's own convention.
   */
  private TranslatedExpression navigationEqualsVariable(
      ExpNavigation navigation, ExpVariable variable) {
    VariableBinding source = context.binding(variableNameOf(navigation.getObjectExpression()));
    MNavigableElement destination = resolveRedefinedDestination(navigation.getDestination(), source);
    VariableBinding target = context.binding(variable.getVarname());
    if (!target.className().equals(destination.cls().name())) {
      // Structurally a different class entirely: the navigation can never resolve to it.
      return defined(Smt.bool(false));
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    return defined(linkTerm(links, destination, source, target.slotIndex()));
  }

  /**
   * Resolves which side of {@code links} a source binding is on, and returns the SMT term for its
   * link to candidate slot {@code otherIndex}.
   *
   * <p>The ORDINARY case matches by class name, exactly as before this method learned about
   * {@code destination} at all: {@code AssociationLinks}' own {@code aEnd}/{@code bEnd} carry no
   * guaranteed relationship to the model's declared end order (deliberately -- {@link
   * PredefinedLinkEncoder}'s own class javadoc documents "the SMT grid's end order can differ from
   * {@code associationEnds()}", and several hand-built test fixtures construct {@code
   * AssociationLinks} with the ends reversed on purpose), so class-name matching is the only
   * association-agnostic way to resolve orientation when the two ends have DIFFERENT classes, and
   * it is kept unconditionally for that case.
   *
   * <p>Class name is genuinely AMBIGUOUS only for a REFLEXIVE association (both ends the same
   * class, e.g. CivilStatus's {@code Marriage}, {@code Person [0..1] role wife -- Person [0..1]
   * role husband}) -- the one case previously refused outright. There, {@code destination} (the
   * end actually being navigated TO) is resolved against the association's own DECLARED end order
   * instead: {@code aEnd}/{@code bEnd} are always built from {@code associationEnds().get(0)}/
   * {@code .get(1)} respectively, POSITIONALLY, in the one production caller that can even reach a
   * reflexive association ({@code SmtModelFinder.solve()} -- no test fixture built one before this
   * fix, so there is no reversed-reflexive precedent to preserve), so "{@code destination} is
   * declared end 0" and "{@code source} is on the {@code aEnd} axis" are the same fact: OCL
   * navigation always crosses from one end to the other.
   */
  private SmtTerm linkTerm(
      AssociationLinks links, MNavigableElement destination, VariableBinding source, int otherIndex) {
    // Orientation by SLOT-BINDING lookup, not by class name: the source binding is a member of
    // exactly one end's view (identity for a plain class view, so the index equals the slot
    // index and this reads exactly like the previous class-name dispatch), and for a FOLDED end
    // view -- or a polymorphic context binding like a Car slot bound by `context v : Vehicle` --
    // only the lookup finds it. An unrelated class lands in neither view and still fails closed.
    int sourceIndex = links.aEnd().indexOf(source);
    boolean sourceIsAEnd = sourceIndex >= 0;
    if (!sourceIsAEnd) {
      sourceIndex = links.bEnd().indexOf(source);
      if (sourceIndex < 0) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "association "
                + links.associationName()
                + " does not connect class "
                + source.className());
      }
    }
    // Class name is genuinely AMBIGUOUS only for a REFLEXIVE association (both ends the same
    // class, e.g. CivilStatus's Marriage): there, destination (the end actually being navigated
    // TO) is resolved against the association's own DECLARED end order.
    if (links.aEnd().className().equals(links.bEnd().className())) {
      boolean destinationIsAEnd =
          destination.equals(destination.association().associationEnds().get(0));
      return destinationIsAEnd
          ? Smt.sym(links.linkNames()[otherIndex][sourceIndex])
          : Smt.sym(links.linkNames()[sourceIndex][otherIndex]);
    }
    return sourceIsAEnd
        ? Smt.sym(links.linkNames()[sourceIndex][otherIndex])
        : Smt.sym(links.linkNames()[otherIndex][sourceIndex]);
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

  /**
   * Resolves an enum literal (e.g. {@code #single}) the same way {@link #resolve(ExpConstString,
   * Expression)} resolves a String literal: as an index into the COMPARED attribute's own
   * registered domain, never a domain-free absolute value -- an enum literal has no standalone SMT
   * encoding, only a meaning relative to whichever attribute's candidate ordering it is compared
   * against ({@link #visitConstEnum} refuses it outside that context for exactly this reason).
   */
  private SmtTerm resolve(ExpConstEnum literal, Expression other) {
    if (!(other instanceof ExpAttrOp a))
      throw unsupported(
          FragmentBoundary.TIER_2, "enum literal compared against a non-attribute expression");
    VariableBinding b = context.binding(variableNameOf(a.objExp()));
    AttributeDomain d = context.attributeDomain(b.className(), a.attr().name());
    int i = d.enumeratedValues().indexOf(literal.value());
    return Smt.intLit(i >= 0 ? BigInteger.valueOf(i) : UNDEFINED_STRING_SENTINEL);
  }

  private TranslatedExpression argResult(Expression e) {
    return argResult(e, positivePolarity);
  }

  private TranslatedExpression argResult(Expression e, boolean polarity) {
    return translate(e, context, mode, polarity, localBindings);
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
        FragmentBoundary.TIER_2,
        "allInstances as a bare/standalone value (outside the range/receiver of forAll, exists,"
            + " isUnique, size(), includesAll, isEmpty, notEmpty, one, closure, or as the source"
            + " of select()/reject()) is not yet supported");
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
    throw unsupported(
        FragmentBoundary.TIER_2,
        "free-standing enum literal ('" + e.value() + "') outside an attribute comparison");
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
    int variableCount = e.getVariableDeclarations().size();
    if (variableCount != 1 && variableCount != 2) {
      throw unsupported(FragmentBoundary.TIER_2, "exists with more than two loop variables");
    }
    if (e.getRangeExpression() instanceof ExpSetLiteral setLiteral && variableCount == 1) {
      result = setLiteralQuantifier(setLiteral, e.getVariableDeclarations().varDecl(0).name(),
          e.getQueryExpression(), false);
      return;
    }
    List<PopulationMember> population = populationOf(e.getRangeExpression(), "exists");
    List<SmtTerm> trueCandidates = new ArrayList<>();
    List<SmtTerm> definedCandidates = new ArrayList<>();
    for (List<PopulationMember> members : tuplesOf(population, variableCount)) {
      TranslationContext extended = context;
      List<SmtTerm> memberGuards = new ArrayList<>(variableCount);
      for (int i = 0; i < variableCount; i++) {
        String variableName = e.getVariableDeclarations().varDecl(i).name();
        extended = extended.withBinding(variableName, members.get(i).binding());
        memberGuards.add(members.get(i).memberGuard());
      }
      SmtTerm member = variableCount == 1 ? memberGuards.get(0) : Smt.and(memberGuards);
      TranslatedExpression body =
          translate(e.getQueryExpression(), extended, mode, positivePolarity, localBindings);
      trueCandidates.add(Smt.and(List.of(member, body.defined(), body.value())));
      definedCandidates.add(Smt.app("=>", member, body.defined()));
    }
    SmtTerm anyTrue = Smt.or(trueCandidates);
    result =
        new TranslatedExpression(Smt.or(List.of(anyTrue, Smt.and(definedCandidates))), anyTrue);
  }

  /**
   * {@code X.allInstances()->forAll(body)} or {@code source.role->forAll(body)} (including a
   * CHAINED, multi-hop association navigation -- see {@link #populationOf}'s {@link
   * #navigationHop}) -- reuses {@link #populationOf} for every range shape, the exact same
   * population-building code {@code isUnique}/{@link #collectionSize} already trust, rather than
   * re-deriving a second, navigation-specific loop here. Was {@code X.allInstances()}-only until
   * this change; the real corpus motivation is Genealogy's {@code p.child->forAll(c |
   * p.yearB+15<=c.yearB)}. Its sibling {@code gp.child.child->forAll(gc|...)} looks superficially
   * similar but is a DIFFERENT shape underneath -- {@code child} is collection-valued on BOTH
   * association ends, so USE's own parser desugars it into {@code
   * gp.child->collect($e|$e.child)->forAll(...)} (confirmed directly by inspecting the compiled
   * AST), an {@code ExpCollect} range {@link #populationOf} does not recognize -- still refused,
   * correctly (a {@code collect()}-based flatten, {@code ocl.collect}, is a separate, larger,
   * unattempted feature), not silently mistranslated.
   */
  @Override
  public void visitForAll(ExpForAll e) {
    int variableCount = e.getVariableDeclarations().size();
    if (variableCount != 1 && variableCount != 2) {
      throw unsupported(FragmentBoundary.TIER_1, "forAll with more than two loop variables");
    }
    if (e.getRangeExpression() instanceof ExpSetLiteral setLiteral && variableCount == 1) {
      result = setLiteralQuantifier(setLiteral, e.getVariableDeclarations().varDecl(0).name(),
          e.getQueryExpression(), true);
      return;
    }
    List<PopulationMember> population = populationOf(e.getRangeExpression(), "forAll");
    List<SmtTerm> valueConjuncts = new ArrayList<>();
    List<SmtTerm> definedConjuncts = new ArrayList<>();
    List<SmtTerm> falseCandidates = new ArrayList<>();
    for (List<PopulationMember> members : tuplesOf(population, variableCount)) {
      TranslationContext extended = context;
      List<SmtTerm> memberGuards = new ArrayList<>(variableCount);
      for (int i = 0; i < variableCount; i++) {
        String variableName = e.getVariableDeclarations().varDecl(i).name();
        extended = extended.withBinding(variableName, members.get(i).binding());
        memberGuards.add(members.get(i).memberGuard());
      }
      SmtTerm exists = variableCount == 1 ? memberGuards.get(0) : Smt.and(memberGuards);
      TranslatedExpression body =
          translate(e.getQueryExpression(), extended, mode, positivePolarity, localBindings);
      valueConjuncts.add(Smt.app("=>", exists, body.value()));
      definedConjuncts.add(Smt.app("=>", exists, body.defined()));
      falseCandidates.add(Smt.and(List.of(exists, body.defined(), Smt.not(body.value()))));
    }
    result =
        new TranslatedExpression(
            Smt.or(List.of(Smt.or(falseCandidates), Smt.and(definedConjuncts))),
            Smt.and(valueConjuncts));
  }

  /**
   * Every {@code variableCount}-length tuple drawn from {@code population}, EACH variable ranging
   * independently over the SAME population -- including a tuple that repeats one member across
   * positions (e.g. {@code h1==h2}), which OCL's own multi-variable {@code forAll}/{@code exists}
   * does not exclude either (confirmed directly against {@code ExpQuery.evalForAll0}/{@code
   * evalExists0}, use-core: {@code for (Value elemVal : rangeVal)} at every nesting level, no
   * equal-index skip -- a body that must exclude it, like ZebraPuzzle's {@code DistinctColor}
   * (h1&lt;&gt;h2 implies ...), does so with its own explicit inequality check, the same pattern
   * {@link #visitExists}'s own two-variable cross product already relies on). For
   * {@code variableCount==1} this degenerates to one singleton tuple per member, so the loop below
   * is a strict generalisation of the pre-existing single-variable case, not a parallel code path.
   */
  /**
   * {@code Set{c1,...,cn}->forAll(k | body)} / {@code ->exists(k | body)} over an
   * Integer-CONSTANT set literal: each literal element is one quantifier candidate, and the loop
   * variable is bound to that constant through a per-element SMT {@code let} (fresh
   * {@link LocalBinding} symbols per element, so the conjuncts cannot capture each other).
   *
   * <p>This is the narrowest honest reading of "collections as values" for this encoding: the
   * elements are compile-time integers, so no collection representation is invented -- the
   * literal IS its enumeration. Non-constant or non-Integer elements, more than one loop
   * variable, and any other consumption of a set literal stay refused. Empty literals cannot
   * reach here ({@code Set{}} parses to {@link ExpEmptyCollection}); duplicated elements
   * collapse ({@code Set} semantics), matching {@code SetValue}.
   */
  private TranslatedExpression setLiteralQuantifier(
      ExpSetLiteral set, String iterator, Expression bodyExpr, boolean forAll) {
    // Elements: Integer constants bind the loop variable to their literal VALUE; String
    // constants bind it to a singleton CONTENT candidate (stringOrEnum LocalBinding whose
    // candidate list is the literal), so body comparisons resolve by content. Mixing kinds in
    // one literal is refused.
    boolean sawInt = false;
    boolean sawString = false;
    List<BigInteger> intValues = new ArrayList<>();
    List<String> stringValues = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      if (element instanceof ExpConstInteger constant) {
        sawInt = true;
        BigInteger value = BigInteger.valueOf(constant.value());
        if (!intValues.contains(value)) {
          intValues.add(value);
        }
      } else if (element instanceof ExpConstString constant) {
        sawString = true;
        if (!stringValues.contains(constant.value())) {
          stringValues.add(constant.value());
        }
      } else {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "Set literal with a non-constant or non-Integer/non-String element ("
                + element
                + ")");
      }
    }
    if (sawInt && sawString) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "Set literal mixes Integer and String constants; not supported in this slice");
    }
    List<SmtTerm> definedTerms = new ArrayList<>();
    List<SmtTerm> valueTerms = new ArrayList<>();
    for (BigInteger element : intValues) {
      // No closing pipe in the stem: the -defined/-value suffixes complete the quoted symbol.
      String stem = "|ocl-set-" + iterator + "-" + element;
      LocalBinding binding =
          new LocalBinding(stem + "-defined|", stem + "-value|", false, null);
      Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
      extended.put(iterator, binding);
      TranslatedExpression body =
          translate(bodyExpr, context, mode, positivePolarity, Map.copyOf(extended));
      List<SmtTerm.Binding> bindings =
          List.of(
              new SmtTerm.Binding(binding.definedSymbol(), Smt.bool(true)),
              new SmtTerm.Binding(binding.valueSymbol(), Smt.intLit(element)));
      definedTerms.add(Smt.let(bindings, body.defined()));
      valueTerms.add(Smt.let(bindings, body.value()));
    }
    for (String content : stringValues) {
      // A String literal candidate carries its own CONTENT as the candidate list; its value
      // anchor is arbitrary (0) because every comparison resolves by content against the
      // other operand's domain.
      String stem = "|ocl-set-" + iterator + "-" + content;
      LocalBinding binding =
          new LocalBinding(stem + "-defined|", stem + "-value|", true, List.of(content));
      Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
      extended.put(iterator, binding);
      TranslatedExpression body =
          translate(bodyExpr, context, mode, positivePolarity, Map.copyOf(extended));
      List<SmtTerm.Binding> bindings =
          List.of(
              new SmtTerm.Binding(binding.definedSymbol(), Smt.bool(true)),
              new SmtTerm.Binding(binding.valueSymbol(), Smt.intLit(BigInteger.ZERO)));
      definedTerms.add(Smt.let(bindings, body.defined()));
      valueTerms.add(Smt.let(bindings, body.value()));
    }
    if (forAll) {
      // Vacuously defined and true over an empty literal -- and an empty literal cannot reach
      // here (Set{} parses to ExpEmptyCollection), so both lists are non-empty in practice.
      return new TranslatedExpression(Smt.and(definedTerms), Smt.and(valueTerms));
    }
    SmtTerm anyTrue = Smt.or(zipConjunct(definedTerms, valueTerms));
    return new TranslatedExpression(
        Smt.or(List.of(anyTrue, Smt.and(definedTerms))), anyTrue);
  }

  private static List<SmtTerm> zipConjunct(List<SmtTerm> defined, List<SmtTerm> value) {
    List<SmtTerm> result = new ArrayList<>();
    for (int i = 0; i < defined.size(); i++) {
      result.add(Smt.and(List.of(defined.get(i), value.get(i))));
    }
    return result;
  }

  private static List<List<PopulationMember>> tuplesOf(
      List<PopulationMember> population, int variableCount) {
    List<List<PopulationMember>> tuples = new ArrayList<>();
    if (variableCount == 1) {
      for (PopulationMember member : population) {
        tuples.add(List.of(member));
      }
      return tuples;
    }
    for (PopulationMember first : population) {
      for (PopulationMember second : population) {
        tuples.add(List.of(first, second));
      }
    }
    return tuples;
  }

  /**
   * Translates {@code if <cond> then <a> else <b> endif}. USE's own {@link ExpIf#eval} (use-core)
   * defaults the result to undefined and only evaluates a branch once the condition is confirmed
   * DEFINED -- an undefined condition makes the WHOLE if-expression undefined, it does not fall
   * through to either branch (that method's own docstring says otherwise; the actual code,
   * guarded by {@code if (condValue.isDefined())}, does not match its docstring, and this
   * translation follows the code, confirmed directly rather than trusted from the comment). The
   * emitted {@code defined} term is therefore conjoined with the condition's own definedness
   * directly, not merely selected as one branch's value.
   *
   * <p>Scoped to matching then/else types: OCL's own compiler would widen a mismatched Integer/
   * Real pair to a common Real type the same way {@code arithmetic()}'s {@code +}/{@code -} does,
   * but that widening is not attempted here -- refused explicitly rather than silently generalized,
   * consistent with every other narrowly-scoped operator in this translator.
   */
  @Override
  public void visitIf(ExpIf e) {
    if (!e.getThenExpression().type().equals(e.getElseExpression().type())) {
      throw unsupported(
          FragmentBoundary.BEYOND_FIRST_FRAGMENT,
          "if-then-else whose then/else branches have different types ("
              + e.getThenExpression().type()
              + " vs "
              + e.getElseExpression().type()
              + "): only matching-type branches are supported in this translation slice");
    }
    TranslatedExpression condition = argResult(e.getCondition());
    TranslatedExpression thenBranch = argResult(e.getThenExpression());
    TranslatedExpression elseBranch = argResult(e.getElseExpression());
    result =
        new TranslatedExpression(
            Smt.and(
                List.of(
                    condition.defined(),
                    Smt.ite(condition.value(), thenBranch.defined(), elseBranch.defined()))),
            Smt.ite(condition.value(), thenBranch.value(), elseBranch.value()));
  }

  @Override
  public void visitIsKindOf(ExpIsKindOf e) {
    result = isTypeCheck(e.getSourceExpr(), e.getTargetType(), true);
  }

  @Override
  public void visitIsTypeOf(ExpIsTypeOf e) {
    result = isTypeCheck(e.getSourceExpr(), e.getTargetType(), false);
  }

  /**
   * {@code oclIsTypeOf}/{@code oclIsKindOf} against a class target, resolved at TRANSLATION time
   * rather than emitted as a solver-side formula -- possible because every object reference this
   * translator can resolve at all goes through a bare quantifier/context {@link ExpVariable},
   * which {@link #context}'s {@link VariableBinding} already tags with its own CONCRETE class name
   * (see {@link PolymorphicRange}: each candidate slot in a polymorphic range keeps its own
   * concrete class, exactly so an inherited invariant's context variable resolves attributes
   * against the right subclass). So "which class is this object" is not something the solver needs
   * to be asked; it is already known, in Java, the moment the binding is resolved.
   *
   * <p>Confirmed directly against {@code ExpIsTypeOf#eval}/{@code ExpIsKindOf#eval} (use-core), not
   * assumed: both are TOTAL functions over their source's RUNTIME TYPE -- "the value may be
   * undefined, still the type test is valid!" (the evaluator's own comment) -- so unlike every
   * other operator in this translator, the result here is unconditionally defined; the source
   * expression's own definedness is never consulted at all.
   *
   * <p>Narrowly scoped, same as everywhere else that resolves an object reference in this class:
   * {@link #variableNameOf} throws for anything other than a bare variable, so a navigation chain
   * (e.g. {@code self.owner.oclIsTypeOf(X)}) is refused rather than attempted -- this project has
   * no general mechanism to resolve an arbitrary navigation's concrete dynamic type, only a bound
   * variable's.
   */
  private TranslatedExpression isTypeCheck(
      Expression sourceExpr, org.tzi.use.uml.ocl.type.Type targetType, boolean includeSubtypes) {
    if (!targetType.isTypeOfClass()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "isTypeOf/isKindOf against a non-class target type " + targetType);
    }
    org.tzi.use.uml.mm.MClassifier targetClass = (org.tzi.use.uml.mm.MClassifier) targetType;
    VariableBinding binding = context.binding(variableNameOf(sourceExpr));
    boolean matches =
        binding.className().equals(targetClass.name())
            || (includeSubtypes
                && targetClass.allChildren().stream()
                    .anyMatch(child -> child.name().equals(binding.className())));
    return defined(Smt.bool(matches));
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
   * The finite, existence/link-guarded population {@code isUnique}, {@link #collectionSize},
   * {@code forAll}/{@code exists}, and {@link #collectionIncludesAll} all range over, for
   * {@code X.allInstances()}, a {@code select()}-filtered range, or a CHAINED (possibly multi-hop)
   * association navigation ({@link #navigationHop}) -- anything else (a {@code collect()}-based
   * flatten, a set literal, ...) fails closed here, before a single body translation (or, for
   * {@code size()}, the summation) is even attempted.
   *
   * @param construct the calling construct's own name, spliced into the refusal message so a
   *     {@code size()} refusal reads as a {@code size()} refusal and not a leftover {@code
   *     isUnique} one, rather than silently keeping a fixed wording as a trap for the next caller.
   */
  private List<PopulationMember> populationOf(Expression range, String construct) {
    if (range instanceof ExpAllInstances all) {
      List<PopulationMember> population = new ArrayList<>();
      for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
        population.add(new PopulationMember(slot.binding(), Smt.sym(slot.existsName())));
      }
      return population;
    }
    // ExpSelectByType EXTENDS ExpSelectByKind, so the exact-type branch MUST be tested first or
    // the instanceof below would swallow selectByType expressions as kind-of.
    if (range instanceof ExpSelectByType selectByType
        && selectByType.getSourceExpression() instanceof ExpAllInstances all) {
      return typeFilteredAllInstancesPopulation(
          all, ((org.tzi.use.uml.ocl.type.CollectionType) selectByType.type()).elemType(), false, construct);
    }
    if (range instanceof ExpSelectByKind selectByKind
        && selectByKind.getSourceExpression() instanceof ExpAllInstances all) {
      return typeFilteredAllInstancesPopulation(
          all, ((org.tzi.use.uml.ocl.type.CollectionType) selectByKind.type()).elemType(), true, construct);
    }
    if (range instanceof ExpQuery query
        && (query instanceof ExpSelect || query instanceof ExpReject)
        && isSupportedSelectSource(query)) {
      // The same X.allInstances()->select(pred) shape collectionSize/collectionEmptiness already
      // reuse selectedAllInstancesPopulation for -- found while chasing CompanyERSchema's own
      // ProjectBudget_greater_PartCost, whose forAll range is exactly this shape. Generalizing it
      // into populationOf itself (rather than special-casing forAll alone) means every population-
      // consuming construct -- forAll, exists, isUnique, includesAll -- gets it uniformly.
      // reject() rides the same branch, sharing selectedAllInstancesPopulation's own reject/select
      // distinction, since every consuming construct needs it exactly as much as select() does.
      return selectedAllInstancesPopulation(query);
    }
    if (range instanceof ExpNavigation navigation && navigation.getDestination().isCollection()) {
      if (navigation.getObjectExpression() instanceof ExpVariable sourceVar) {
        VariableBinding source = context.binding(sourceVar.getVarname());
        MNavigableElement destination =
            resolveRedefinedDestination(navigation.getDestination(), source);
        AssociationLinks links = context.linksFor(destination.association().name());
        ObjectSlots destSlots = destinationEndView(links, destination);
        List<PopulationMember> population = new ArrayList<>();
        for (int k = 0; k < destSlots.capacity(); k++) {
          population.add(
              new PopulationMember(destSlots.concreteBindings().get(k), linkTerm(links, destination, source, k)));
        }
        return population;
      }
      if (navigation.getObjectExpression() instanceof ExpNavigation) {
        return navigationHop(navigation).population();
      }
    }
    throw unsupported(
        FragmentBoundary.TIER_3,
        construct
            + " over a range other than X.allInstances() or a collection-valued association"
            + " navigation is not yet supported");
  }

  /**
   * One resolved navigation hop: the population it reaches, tagged with that population's own
   * (uniform) class -- needed so a FURTHER outer hop can resolve redefinition off it, even when the
   * population itself is empty (a zero-capacity destination class has no member to read a class
   * name off of, so the class travels alongside the members rather than being inferred from one).
   */
  private record NavigationHop(String destClass, List<PopulationMember> population) {}

  /**
   * Resolves one navigation hop's own population, recursing on its source when that source is
   * ITSELF a navigation -- the general form behind {@link #populationOf}'s multi-hop case, e.g.
   * Demo.use's real {@code self.department.employee} ({@code Controls}: {@code Department[1]},
   * single-valued; {@code WorksIn}: {@code Employee[*]}, collection-valued -- chaining works
   * uniformly regardless of either hop's own multiplicity, since uniform structural link-membership
   * is exactly the same SMT shape either way: {@link #linkTerm} per candidate slot).
   *
   * <p>Base case (a bare variable source) intentionally mirrors, rather than reuses,
   * populationOf's own single-hop branch: that branch's output stays byte-identical (a bare {@code
   * linkTerm}, no wrapping {@code and}) for the pre-existing one-hop shape, while THIS method's own
   * base case feeds only the new chained path, where each candidate's guard is legitimately an
   * AND of "source itself reachable" with "source links to this candidate".
   */
  private NavigationHop navigationHop(ExpNavigation navigation) {
    VariableBinding sourceClassWitness;
    List<PopulationMember> sourcePopulation;
    if (navigation.getObjectExpression() instanceof ExpVariable sourceVar) {
      sourceClassWitness = context.binding(sourceVar.getVarname());
      sourcePopulation = null;
    } else if (navigation.getObjectExpression() instanceof ExpNavigation innerNavigation) {
      NavigationHop inner = navigationHop(innerNavigation);
      sourceClassWitness = new VariableBinding(inner.destClass(), 0);
      sourcePopulation = inner.population();
    } else {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "navigation over a source other than a variable or another navigation is not yet"
              + " supported");
    }
    MNavigableElement destination =
        resolveRedefinedDestination(navigation.getDestination(), sourceClassWitness);
    String destClass = destination.cls().name();
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    List<PopulationMember> population = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      SmtTerm reachable;
      if (sourcePopulation == null) {
        reachable = linkTerm(links, destination, sourceClassWitness, k);
      } else {
        List<SmtTerm> reachableVia = new ArrayList<>();
        for (PopulationMember sourceMember : sourcePopulation) {
          reachableVia.add(
              Smt.and(
                  List.of(
                      sourceMember.memberGuard(),
                      linkTerm(links, destination, sourceMember.binding(), k))));
        }
        reachable = Smt.or(reachableVia);
      }
      population.add(new PopulationMember(destSlots.concreteBindings().get(k), reachable));
    }
    return new NavigationHop(destClass, population);
  }

  /**
   * One candidate population member shared by {@code isUnique} and {@link #collectionSize}: its
   * loop-variable binding, plus the SMT term guarding whether it is actually present -- an
   * existence flag for {@link #populationOf}'s allInstances branch, a {@link #linkTerm} for its
   * association-end branch.
   */
  private record PopulationMember(VariableBinding binding, SmtTerm memberGuard) {}

  /**
   * The population of {@code X.allInstances()->selectByKind(T)} / {@code ->selectByType(T)}: the
   * ordinary polymorphic {@link PolymorphicRange} slots of X, filtered by each slot's CONCRETE
   * class. Semantics mirror USE's own evaluators exactly -- {@code ExpSelectByKind#includeElement}
   * accepts a runtime type that {@code conformsTo} T (T itself or any descendant, the same
   * matching {@link #isTypeCheck} performs with subtypes included), {@code ExpSelectByType} exact
   * runtime-type equality -- and since every slot carries its own concrete class name, the filter
   * is resolved entirely at translation time, the same way {@link #isTypeCheck} is. A filter that
   * keeps nothing is a genuinely empty population (every consumer's empty-population convention
   * applies), not a refusal. Only the {@code X.allInstances()} source is supported, matching
   * {@code select()}'s own restriction.
   */
  private List<PopulationMember> typeFilteredAllInstancesPopulation(
      ExpAllInstances all,
      org.tzi.use.uml.ocl.type.Type targetType,
      boolean includeSubtypes,
      String construct) {
    if (!targetType.isTypeOfClass()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "selectByKind/selectByType against a non-class target type " + targetType);
    }
    org.tzi.use.uml.mm.MClassifier targetClass = (org.tzi.use.uml.mm.MClassifier) targetType;
    List<PopulationMember> population = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
      String className = slot.binding().className();
      boolean matches =
          className.equals(targetClass.name())
              || (includeSubtypes
                  && targetClass.allChildren().stream()
                      .anyMatch(child -> child.name().equals(className)));
      if (matches) {
        population.add(new PopulationMember(slot.binding(), Smt.sym(slot.existsName())));
      }
    }
    return population;
  }

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
      bodies.add(translate(body, extended, mode, positivePolarity, localBindings));
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
   * rather than silently falling into {@link #populationOf}'s allInstances branch.
   *
   * <p>A single-valued 0..1 navigation coerced to a set via OCL's own {@code ->op} "uniform syntax"
   * rule ({@link ExpObjAsSet}, not an {@link ExpNavigation} either) IS supported for the ONE shape
   * evidenced by the real corpus -- {@code AssociationClass}'s {@code p.employer->size()}, {@code
   * employer} being a 0..1 end reached THROUGH the association class -- as a 0-or-1 indicator over
   * {@link #definednessOf} rather than a genuine population sum: there is at most one linked slot
   * by construction (the end's own declared multiplicity), so "how many" and "is there one at all"
   * coincide. Deliberately NOT generalized to an ORDINARY (non-association-class) single-valued
   * navigation coerced the same way -- nothing about the underlying {@link #definednessOf}/{@link
   * #singleValuedNavigationDefined} machinery would need to change to support it too, but it was
   * never evidenced by the real corpus and stays refused, matching the dedicated regression test
   * that locks this in. {@code size()} on a String
   * never reaches this method with an {@link ExpNavigation} receiver at all, since a String operand
   * is never navigation-shaped, and a filtered/derived collection whose OWN source is not {@code
   * X.allInstances()} (e.g. {@code self.assoc->select(...)->size()}) remains refused; only {@code
   * X.allInstances()->select(...)->size()} is the supported {@link ExpSelect} shape (see {@link
   * #selectedAllInstancesPopulation}), checked as part of this method's own branch condition so
   * every other {@link ExpSelect} source falls through to the shared message below instead of a
   * select-specific one.
   */
  private TranslatedExpression collectionSize(Expression receiver) {
    if (receiver instanceof ExpNavigation navigation
        && navigation.getDestination().isCollection()) {
      return defined(sizeTerm(populationOf(navigation, "size()")));
    }
    if (receiver instanceof ExpQuery query
        && (query instanceof ExpSelect || query instanceof ExpReject)
        && isSupportedSelectSource(query)) {
      return defined(sizeTerm(selectedAllInstancesPopulation(query)));
    }
    if (receiver instanceof ExpSetLiteral set
        && set.getElemExpr().length > 0
        && set.getElemExpr()[0] instanceof ExpConstString) {
      List<String> constants = distinctStringConstants(set);
      return defined(Smt.intLit(BigInteger.valueOf(constants.size())));
    }
    if (receiver instanceof ExpSetLiteral set) {
      // The distinct element count is a compile-time constant (Set semantics collapse
      // duplicates), matching the incumbent's set-literal cardinality.
      return defined(Smt.intLit(BigInteger.valueOf(distinctIntegerConstants(set).size())));
    }
    // ExpSelectByType EXTENDS ExpSelectByKind: test the exact-type subclass FIRST (see
    // populationOf's own note).
    if (receiver instanceof ExpSelectByType selectByType
        && selectByType.getSourceExpression() instanceof ExpAllInstances all) {
      return defined(
          sizeTerm(
              typeFilteredAllInstancesPopulation(
                  all, ((org.tzi.use.uml.ocl.type.CollectionType) selectByType.type()).elemType(), false, "size()")));
    }
    if (receiver instanceof ExpSelectByKind selectByKind
        && selectByKind.getSourceExpression() instanceof ExpAllInstances all) {
      return defined(
          sizeTerm(
              typeFilteredAllInstancesPopulation(
                  all, ((org.tzi.use.uml.ocl.type.CollectionType) selectByKind.type()).elemType(), true, "size()")));
    }
    if (receiver instanceof ExpObjAsSet objAsSet
        && objAsSet.getObjectExpression() instanceof ExpNavigation navigation
        && !navigation.getDestination().isCollection()
        && navigation.getDestination().association() instanceof MAssociationClass) {
      // Scoped to the association-class case specifically (AtMostOneEmployer's own shape) --
      // NOT generalized to an ORDINARY single-valued 0..1 navigation coerced to a set, which
      // SizeTranslationTest#singleValuedNavigationCoercedToASetIsNotConfusedAndFailsClosed
      // deliberately locks in as still refused (that shape was never in this slice's scope, and
      // widening it here would be an untested, unrequested expansion of what Appendix M asked
      // for).
      return defined(
          Smt.ite(
              definednessOf(navigation), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO)));
    }
    throw unsupported(
        FragmentBoundary.TIER_3,
        "size() over anything other than a chained, collection-valued association navigation"
            + " or select over X.allInstances() is not yet supported");
  }

  /**
   * {@code X->includesAll(Y)} for two (possibly chained, multi-hop) collection-valued navigations
   * reaching the SAME destination class -- confirmed as a real, recurring shape by sweeping
   * unc-modelvalidator against USE's own bundled example models (not our own curated benchmark
   * corpus): {@code self.department.employee->includesAll(self.employee)} (Demo.use, ex.use,
   * Project.use, all three the identical shape modulo the outer navigation hop). {@code
   * self.department.employee} is itself two hops -- originally a separate, unsupported limitation
   * these three didn't fully close on their own; closed once {@link #populationOf} gained the
   * general multi-hop form ({@link #navigationHop}), see {@code
   * IncludesAllTranslationTest#includesAllOverAMultiHopNavigationDiscriminatesOnARealCorpusShape}.
   *
   * <p>Reduces to {@link #populationOf} directly rather than a new membership primitive: both
   * navigations, reaching the SAME class, draw from that class's ONE shared {@link ObjectSlots}
   * pool, so their two populations are index-aligned by construction -- slot {@code k} in one
   * population and slot {@code k} in the other refer to the exact same candidate object. "every Y
   * member is also an X member" is then simply, per slot, "Y's own member guard implies X's own
   * member guard" -- the SAME "found, don't reinvent" reuse {@link #navigationEquals} and {@link
   * #collectionSize} already established for single-hop populations. An empty Y population makes
   * the whole conjunction vacuously true (an empty {@link List} yields {@code Smt.and([])} =
   * {@code true}, matching every other empty-population convention in this class), matching OCL's
   * own {@code includesAll} semantics over an empty argument.
   */
  private TranslatedExpression collectionIncludesAll(Expression collectionExpr, Expression otherExpr) {
    if (!(collectionExpr instanceof ExpNavigation collectionNav)
        || !collectionNav.getDestination().isCollection()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "includesAll over anything other than a (possibly chained) collection-valued"
              + " association navigation is not yet supported");
    }
    if (!(otherExpr instanceof ExpNavigation otherNav) || !otherNav.getDestination().isCollection()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "includesAll's argument, over anything other than a (possibly chained)"
              + " collection-valued association navigation, is not yet supported");
    }
    String collectionDestClass = collectionNav.getDestination().cls().name();
    String otherDestClass = otherNav.getDestination().cls().name();
    if (!collectionDestClass.equals(otherDestClass)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "includesAll between two navigations reaching different classes ("
              + collectionDestClass
              + ", "
              + otherDestClass
              + ") is not yet supported");
    }
    List<PopulationMember> collectionPopulation = populationOf(collectionNav, "includesAll");
    List<PopulationMember> otherPopulation = populationOf(otherNav, "includesAll");
    List<SmtTerm> everyOtherMemberIsAlsoAMember = new ArrayList<>(otherPopulation.size());
    for (int k = 0; k < otherPopulation.size(); k++) {
      everyOtherMemberIsAlsoAMember.add(
          Smt.app(
              "=>",
              otherPopulation.get(k).memberGuard(),
              collectionPopulation.get(k).memberGuard()));
    }
    return defined(Smt.and(everyOtherMemberIsAlsoAMember));
  }

  /**
   * {@code X->isEmpty()} / {@code X->notEmpty()}, found while chasing CompanyERSchema's own
   * remaining gaps (its {@code Project::pname_primary_key} and {@code ProjectWork::
   * fname_lname_pname_primary_key} invariants both end in this shape). Reduces to whichever
   * population primitive already matches the receiver -- {@link #populationOf} for a bare {@code
   * X.allInstances()} or a single-hop collection-valued navigation, {@link
   * #selectedAllInstancesPopulation} for {@code X.allInstances()->select(pred)} (the SAME two
   * primitives {@link #collectionSize} and {@link #collectionIncludesAll} already reuse) -- rather
   * than inventing a third way to enumerate a population. "Some member exists" is exactly the OR
   * of every candidate's own {@link PopulationMember#memberGuard()}; {@code isEmpty} negates it,
   * {@code notEmpty} doesn't, and an empty population OR's to {@code false} either way (matching
   * OCL's own {@code Set{}->isEmpty()} = true), so no separate empty-population special case is
   * needed.
   */
  private TranslatedExpression collectionEmptiness(Expression receiver, boolean wantEmpty) {
    String construct = wantEmpty ? "isEmpty" : "notEmpty";
    List<PopulationMember> population;
    if (receiver instanceof ExpQuery query
        && (query instanceof ExpSelect || query instanceof ExpReject)
        && isSupportedSelectSource(query)) {
      population = selectedAllInstancesPopulation(query);
    } else if (receiver instanceof ExpAllInstances
        || (receiver instanceof ExpNavigation navigation
            && navigation.getDestination().isCollection())) {
      population = populationOf(receiver, construct);
    } else {
      throw unsupported(
          FragmentBoundary.TIER_3,
          construct
              + " over anything other than X.allInstances(), a single-hop collection-valued"
              + " association navigation, or select over X.allInstances() is not yet supported");
    }
    SmtTerm someMemberExists =
        Smt.or(population.stream().map(PopulationMember::memberGuard).toList());
    return defined(wantEmpty ? Smt.not(someMemberExists) : someMemberExists);
  }

  /**
   * The selected, existence-guarded allInstances population needed by CompanyER's let body, and
   * (extended while chasing CompanyERSchema's own {@code pname_primary_key}-shaped invariants,
   * e.g. {@code Part.allInstances()->excluding(p1)->select(p2|p2.pname=p1.pname)}) by the standard
   * OCL primary-key idiom's own self-exclusion. Recognizes two range shapes: a bare {@code
   * X.allInstances()}, or {@code X.allInstances()->excluding(v)} where {@code v} is a variable
   * already bound in {@code context} (the invariant's own context variable, in every real shape
   * evidenced so far). The excluded slot is resolved STATICALLY, off {@code v}'s own {@link
   * VariableBinding} -- the same "resolved once, at translation time, off one fixed binding"
   * convention {@link #resolveRedefinedDestination} and every other same-object comparison in this
   * class already use -- so an excluded candidate's member guard becomes the Java-level constant
   * {@code false} rather than an SMT-level inequality, one fewer term for the solver to reason
   * about. Callers must already have confirmed {@code select.getRangeExpression()} is one of these
   * two shapes (see {@link #collectionSize}); the check below is defense-in-depth, not the primary
   * guard, so a future second caller cannot silently bypass it.
   */
  /**
   * True for exactly the two {@code select}/{@code reject} source shapes {@link
   * #selectedAllInstancesPopulation} knows how to enumerate -- a bare {@code X.allInstances()} or
   * {@code X.allInstances()->excluding(v)} -- so every caller that dispatches to it shares ONE
   * shape check rather than each re-deriving (and risking silently drifting from) its own.
   */
  private static boolean isSupportedSelectSource(ExpQuery query) {
    Expression range = query.getRangeExpression();
    if (range instanceof ExpAllInstances) {
      return true;
    }
    return range instanceof ExpStdOp excludingOp
        && "excluding".equals(excludingOp.opname())
        && excludingOp.args().length == 2
        && excludingOp.args()[0] instanceof ExpAllInstances
        && excludingOp.args()[1] instanceof ExpVariable;
  }

  /**
   * Shared population builder for BOTH {@code select(pred)} and {@code reject(pred)} -- {@code
   * X->reject(pred)} is exactly {@code X->select(not pred)}, and {@code ExpReject}/{@code
   * ExpSelect} share every accessor used here via their common {@link ExpQuery} base, so the two
   * constructs differ only in which of {@link TranslatedExpression#trueTerm} (select: genuinely,
   * definitely true) or {@link TranslatedExpression#falseTerm} (reject: genuinely, definitely
   * false -- NOT simply the negation of {@code trueTerm}, since an undefined predicate must stay
   * excluded from a reject() result too, matching OCL's Kleene-negation-preserves-undefinedness
   * rule) each population member's guard is built from.
   */
  private List<PopulationMember> selectedAllInstancesPopulation(ExpQuery query) {
    boolean reject = query instanceof ExpReject;
    String construct = reject ? "reject()" : "select()";
    ExpAllInstances all;
    VariableBinding excluded = null;
    if (query.getRangeExpression() instanceof ExpAllInstances direct) {
      all = direct;
    } else if (query.getRangeExpression() instanceof ExpStdOp excludingOp
        && "excluding".equals(excludingOp.opname())
        && excludingOp.args()[0] instanceof ExpAllInstances excludingSource
        && excludingOp.args()[1] instanceof ExpVariable excludedVar) {
      all = excludingSource;
      excluded = context.binding(excludedVar.getVarname());
    } else {
      throw unsupported(
          FragmentBoundary.TIER_3,
          construct
              + " over a source other than X.allInstances() or"
              + " X.allInstances()->excluding(v) is not yet supported");
    }
    if (query.getVariableDeclarations().size() != 1) {
      throw unsupported(FragmentBoundary.TIER_3, construct + " with a variable count other than one");
    }
    String iterator = query.getVariableDeclarations().varDecl(0).name();
    List<PopulationMember> population = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
      if (excluded != null && slot.binding().equals(excluded)) {
        population.add(new PopulationMember(slot.binding(), Smt.bool(false)));
        continue;
      }
      TranslatedExpression predicate =
          translate(
              query.getQueryExpression(),
              context.withBinding(iterator, slot.binding()),
              mode,
              positivePolarity,
              localBindings);
      SmtTerm matches = reject ? predicate.falseTerm() : predicate.trueTerm();
      population.add(
          new PopulationMember(
              slot.binding(), Smt.and(List.of(Smt.sym(slot.existsName()), matches))));
    }
    return population;
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

  /**
   * Translates a scalar {@code let} as two simultaneous native SMT-LIB bindings: one for the
   * bound expression's value and one for its explicit definedness. USE's {@link ExpLet#eval}
   * evaluates the variable expression, pushes that value even when it is undefined, and then
   * evaluates the body; carrying both terms into the local environment preserves exactly that
   * behavior instead of incorrectly short-circuiting an undefined bound expression.
   *
   * <p>The representation is deliberately limited to primitive sorts already represented by a
   * standalone {@link SmtTerm}. Objects in this encoder are Java-side {@link VariableBinding}s and
   * collections are finite guarded populations, neither a first-class SMT value, so accepting
   * either here would require inventing a representation. Those shapes fail before their bound or
   * body expression is visited, with a message that identifies the let variable and its type.
   *
   * <p>A String or Enum let additionally records its initializer's configured candidate list (its
   * value is an index POSITIONAL within that one list, so every comparison involving the variable
   * must be built by content -- see {@link #contentAwareEquality}). A literal initializer is its
   * own singleton candidate list (free-standing literals have no attribute to resolve against,
   * which is precisely why {@code visitConstString}/{@code visitConstEnum} refuse them outside a
   * comparison); an {@code oclUndefined} initializer records no list at all, which
   * {@link #contentMatches} treats as "this value can never be consulted". Every OTHER initializer
   * shape is refused here rather than admitted without a list, so a list-less local is PROVEN
   * never-defined and can safely take the ordinary comparison fallback.
   */
  @Override
  public void visitLet(ExpLet e) {
    if (e.getVarType().isTypeOfClass()) {
      if (e.getVarExpression() instanceof ExpNavigation navigation
          && !navigation.getDestination().isCollection()) {
        result = navigationObjectLet(e, navigation);
        return;
      }
      result = objectAnyLet(e);
      return;
    }
    boolean stringOrEnum =
        e.getVarType().isTypeOfString() || e.getVarType().isTypeOfEnum();
    if (!e.getVarType().isTypeOfInteger()
        && !e.getVarType().isTypeOfBoolean()
        && !e.getVarType().isTypeOfReal()
        && !stringOrEnum) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "let-bound variable '"
              + e.getVarname()
              + "' of type "
              + e.getVarType()
              + ": only primitive Integer, Boolean, Real, String, and Enum let bindings are"
              + " supported; object- and collection-typed bindings require a finite"
              + " object/collection representation that this translation slice does not have");
    }

    TranslatedExpression bound;
    List<String> enumeratedValues = null;
    if (stringOrEnum) {
      enumeratedValues = initializerEnumeratedValues(e);
      bound =
          e.getVarExpression() instanceof ExpConstString
                  || e.getVarExpression() instanceof ExpConstEnum
              ? defined(Smt.intLit(BigInteger.ZERO))
              : argResult(e.getVarExpression());
    } else {
      bound = argResult(e.getVarExpression());
    }
    String symbolStem = "|ocl-let-" + e.getVarname();
    LocalBinding binding =
        new LocalBinding(symbolStem + "-defined|", symbolStem + "-value|", stringOrEnum,
            enumeratedValues);
    Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
    extended.put(e.getVarname(), binding);
    TranslatedExpression body =
        translate(e.getInExpression(), context, mode, positivePolarity, Map.copyOf(extended));
    List<SmtTerm.Binding> bindings =
        List.of(
            new SmtTerm.Binding(binding.definedSymbol(), bound.defined()),
            new SmtTerm.Binding(binding.valueSymbol(), bound.value()));
    result =
        new TranslatedExpression(
            Smt.let(bindings, body.defined()), Smt.let(bindings, body.value()));
  }

  /**
   * A scalar {@code let}'s local environment entry. {@code stringOrEnum} marks a String/Enum-typed
   * variable, whose SMT value is an index positional within {@code enumeratedValues} -- the
   * initializer's configured candidate list (a literal initializer's own singleton list). {@code
   * enumeratedValues} is null exactly when the value can never be consulted: a non-String/Enum
   * let, or one rooted in {@code oclUndefined} ({@link #visitLet} refuses every other list-less
   * initializer shape, so null is always comparison-safe).
   */
  private record LocalBinding(
      String definedSymbol, String valueSymbol, boolean stringOrEnum,
      List<String> enumeratedValues) {}

  /**
   * The configured candidate list a String/Enum let's initializer draws its value indices from:
   * the attribute's own domain for a bare or single-hop-navigated attribute access, the source
   * let's list for a chained let variable, the literal's own content for a literal, and null for
   * an {@code oclUndefined}-rooted chain. Any other initializer shape is refused rather than
   * admitted without a list -- a String/Enum local without one could only fall back to raw index
   * comparison, the exact unsoundness {@link #contentAwareEquality} exists to prevent.
   */
  private List<String> initializerEnumeratedValues(ExpLet e) {
    return initializerEnumeratedValues(e.getVarExpression(), e.getVarname());
  }

  /**
   * The configured candidate list a String/Enum-typed VALUE BINDING draws its content indices
   * from, shared by scalar lets ({@link #visitLet}) and inlined operation parameters alike:
   * the attribute's own domain for a bare or single-hop-navigated attribute access, the source
   * binding's list for a chained variable, the literal's own singleton content, and null for
   * an {@code oclUndefined}-rooted chain. Any other initializer shape is refused rather than
   * admitted without a list -- a String/Enum local without one could only fall back to raw
   * index comparison, the exact unsoundness {@link #contentAwareEquality} exists to prevent.
   */
  private List<String> initializerEnumeratedValues(Expression init, String variableName) {
    if (init instanceof ExpConstString s) {
      return List.of(s.value());
    }
    if (init instanceof ExpConstEnum en) {
      return List.of(en.value());
    }
    if (init instanceof ExpUndefined) {
      return null;
    }
    if (init instanceof ExpVariable v && localBindings.containsKey(v.getVarname())) {
      return localBindings.get(v.getVarname()).enumeratedValues();
    }
    if (init instanceof ExpAttrOp a) {
      String className;
      if (a.objExp() instanceof ExpVariable v && !localBindings.containsKey(v.getVarname())) {
        className = context.binding(v.getVarname()).className();
      } else if (a.objExp() instanceof ExpNavigation nav
          && !nav.getDestination().isCollection()
          && nav.getObjectExpression() instanceof ExpVariable sv
          && !localBindings.containsKey(sv.getVarname())) {
        className =
            resolveRedefinedDestination(nav.getDestination(), context.binding(sv.getVarname()))
                .cls()
                .name();
      } else {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "value binding '"
                + variableName
                + "': its initializer's attribute receiver is not a bare context variable or a"
                + " single-hop navigation");
      }
      AttributeValues vals = context.attributeValues(className, a.attr().name());
      if (vals.type() != AttributeType.STRING && vals.type() != AttributeType.ENUM) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "value binding '"
                + variableName
                + "': initializer attribute "
                + className
                + "."
                + a.attr().name()
                + " is not String/Enum-typed");
      }
      guardAgainstUncertainAttribute(vals);
      return context.attributeDomain(className, a.attr().name()).enumeratedValues();
    }
    throw unsupported(
        FragmentBoundary.TIER_3,
        "value binding '"
            + variableName
            + "': its initializer is not a String/Enum attribute access, another let-bound"
            + " variable, a literal, or oclUndefined");
  }

  /**
   * Translates the finite object-selection shape {@code let x = T.allInstances()->any(p) in body}
   * without pretending objects are first-class SMT values. Each candidate slot gets the ordinary
   * Java-side {@link VariableBinding}; the predicate and body are translated once for that slot,
   * then candidate guards select the first matching existing slot in the same stable order as the
   * bounded population. {@link ExpAny#eval} treats an undefined predicate as false, hence each
   * match uses {@link TranslatedExpression#trueTerm()} rather than the raw value term.
   *
   * <p>The supported body is deliberately strict in the object binding: if {@code any} finds no
   * match, USE binds the let variable to undefined, and a strict attribute/arithmetic/comparison
   * body is therefore undefined too. Non-strict bodies such as {@code chosen = oclUndefined(T)}
   * could produce a defined result for that same no-match case; they are refused because this
   * encoder has no undefined-object binding to evaluate them against.
   */
  /**
   * {@code let b : B = x.b in body} -- an object let whose initializer is a SINGLE-VALUED
   * navigation. The bound variable names a slot of the destination end's (possibly folded)
   * view; b's definedness is that slot's link term, and the body translates once per
   * destination slot exactly as {@code objectAnyLet} translates per candidate slot. The result:
   * defined = OR over linked slots of (link AND body-defined); value = the ite chain selecting
   * the linked slot's body value. A destination that is collection-valued is refused
   * (collect semantics); an unlinked destination leaves b undefined, so the body is undefined
   * -- the total-equality/closure consumers already handle that via the definedness term.
   */
  private TranslatedExpression navigationObjectLet(ExpLet e, ExpNavigation navigation) {
    VariableBinding source = context.binding(variableNameOf(navigation.getObjectExpression()));
    MNavigableElement destination = resolveRedefinedDestination(navigation.getDestination(), source);
    if (destination.association() instanceof MAssociationClass) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "let-bound object variable '"
              + e.getVarname()
              + "' whose initializer navigates an association class is not yet supported");
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destinationSlots = destinationEndView(links, destination);

    List<SmtTerm> definedCases = new ArrayList<>();
    List<SmtTerm> valueCases = new ArrayList<>();
    String stem = "|ocl-let-" + e.getVarname() + "-";
    for (int k = 0; k < destinationSlots.capacity(); k++) {
      VariableBinding slotBinding = destinationSlots.concreteBindings().get(k);
      TranslationContext slotContext = context.withBinding(e.getVarname(), slotBinding);
      TranslatedExpression body =
          translate(
              e.getInExpression(), slotContext, mode, positivePolarity, localBindings);
      SmtTerm link = linkTerm(links, destination, source, k);
      // The let variable IS the linked object: its per-slot definedness is the link term
      // itself, and its body must hold under that same link.
      definedCases.add(Smt.and(List.of(link, body.defined())));
      valueCases.add(Smt.and(List.of(link, body.value())));
    }
    return new TranslatedExpression(Smt.or(definedCases), Smt.or(valueCases));
  }

  private TranslatedExpression objectAnyLet(ExpLet e) {
    if (!(e.getVarExpression() instanceof ExpAny any)
        || !(any.getRangeExpression() instanceof ExpAllInstances all)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "let-bound object variable '"
              + e.getVarname()
              + "' whose initializer is not T.allInstances()->any(predicate)");
    }
    if (any.getVariableDeclarations().size() != 1) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "let-bound object variable '"
              + e.getVarname()
              + "' whose any initializer does not declare exactly one iterator");
    }
    String iterator = any.getVariableDeclarations().varDecl(0).name();
    List<SmtTerm> priorMatches = new ArrayList<>();
    List<SmtTerm> selectors = new ArrayList<>();
    List<TranslatedExpression> bodies = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(all.getSourceType(), context)) {
      TranslationContext predicateContext = context.withBinding(iterator, slot.binding());
      TranslatedExpression predicate =
          translate(
              any.getQueryExpression(),
              predicateContext,
              mode,
              positivePolarity,
              localBindings);
      SmtTerm match = Smt.and(List.of(Smt.sym(slot.existsName()), predicate.trueTerm()));
      SmtTerm selected = Smt.and(List.of(match, Smt.not(Smt.or(priorMatches))));
      selectors.add(selected);
      priorMatches.add(match);
      bodies.add(
          translate(
              e.getInExpression(),
              context.withBinding(e.getVarname(), slot.binding()),
              mode,
              positivePolarity,
              localBindings));
    }

    List<SmtTerm> definedCases = new ArrayList<>(selectors.size());
    for (int i = 0; i < selectors.size(); i++) {
      definedCases.add(Smt.and(List.of(selectors.get(i), bodies.get(i).defined())));
    }
    SmtTerm value = Smt.bool(false);
    for (int i = selectors.size() - 1; i >= 0; i--) {
      value = Smt.ite(selectors.get(i), bodies.get(i).value(), value);
    }
    return new TranslatedExpression(Smt.or(definedCases), value);
  }

  @Override
  public void visitNavigation(ExpNavigation e) {
    throw unsupported(FragmentBoundary.TIER_2, "navigation");
  }

  @Override
  public void visitObjAsSet(ExpObjAsSet e) {
    throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "objAsSet");
  }

  /**
   * FIRST SLICE of query-operation inlining: {@code x.op()} where {@code op} is a zero-argument
   * query operation ({@code op(): T = <OCL body>}) whose receiver is a bare variable, and whose
   * body translates in the supported fragment. The call is INLINED -- the body is translated
   * with {@code self} bound to the receiver's own slot binding -- so the operation's value is
   * computed by the solver, never guessed. Nested operation calls inline recursively (the
   * body's own {@code y.op2()} is visited with the extended in-progress set); an operation that
   * re-enters itself, directly or transitively, is refused as recursive. Parameterized
   * operations, non-variable receivers, and non-OCL-bodied operations stay refused.
   */
  @Override
  public void visitInstanceOp(ExpInstanceOp e) {
    if (!(e instanceof ExpObjOp objOp)) {
      throw unsupported(FragmentBoundary.BEYOND_FIRST_FRAGMENT, "instance operation");
    }
    MOperation operation = objOp.getOperation();
    if (operationsInProgress.contains(operation)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "recursive operation call '"
              + operation.name()
              + "': a query operation must not call itself, directly or transitively");
    }
    if (!operation.isCallableFromOCL() || operation.expression() == null) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operation '"
              + operation.name()
              + "': it has no OCL expression body to inline");
    }
    Expression[] arguments = objOp.getArguments();
    if (arguments.length != 1 + operation.paramList().size()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operation '"
              + operation.name()
              + "' called with "
              + (arguments.length - 1)
              + " argument(s) but declares "
              + operation.paramList().size()
              + " parameter(s)");
    }
    if (!(arguments[0] instanceof ExpVariable targetVar)) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operation call on a receiver that is not a bare variable is not yet supported");
    }
    if (localBindings.containsKey(targetVar.getVarname())) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "operation call on a let-bound scalar receiver is not supported");
    }
    VariableBinding selfBinding = context.binding(targetVar.getVarname());
    // Polymorphic dispatch: the receiver's CONCRETE class (a folded slot's own class, or the
    // static class for unfolded views) may REDEFINE the operation -- use its most specific
    // body, exactly the incumbent's runtime-type dispatch resolved at translation time via
    // the slot's concrete binding.
    MOperation dispatched = context.dispatchOperation(selfBinding.className(), operation.name());
    if (dispatched != null) {
      operation = dispatched;
    }
    boolean parameterized = operation.paramList().size() > 0;
    TranslationContext selfContext = context.withBinding("self", selfBinding);
    Set<MOperation> inProgress = new java.util.HashSet<>(operationsInProgress);
    inProgress.add(operation);
    // Parameterized calls: each parameter is bound through a per-call SMT let to the
    // translated ARGUMENT's value and definedness -- constants carry their literal, attribute
    // arguments carry the attribute's own symbol, so the body computes over the caller's
    // actual state. Crisp parameters only: a String/Enum-typed parameter's value is a domain
    // INDEX, and binding one to a caller-side symbol without the canonical string table would
    // reintroduce the positional-index unsoundness the content-aware comparison slices fixed.
    List<SmtTerm.Binding> parameterBindings = new ArrayList<>();
    Map<String, LocalBinding> bodyLocalBindings = localBindings;
    if (parameterized) {
      for (int i = 0; i < operation.paramList().size(); i++) {
        String parameterName = operation.paramList().varDecl(i).name();
        org.tzi.use.uml.ocl.type.Type parameterType =
            operation.paramList().varDecl(i).type();
        boolean contentTyped = parameterType.isTypeOfString() || parameterType.isTypeOfEnum();
        Expression argument = arguments[i + 1];
        TranslatedExpression argumentResult;
        List<String> parameterValues = null;
        if (contentTyped) {
          parameterValues = initializerEnumeratedValues(argument, parameterName);
          argumentResult =
              argument instanceof ExpConstString || argument instanceof ExpConstEnum
                  ? defined(Smt.intLit(BigInteger.ZERO))
                  : argResult(argument);
        } else {
          argumentResult = argResult(argument);
        }
        String stem = "|ocl-param-" + operation.name() + "-" + parameterName;
        LocalBinding parameterBinding =
            new LocalBinding(
                stem + "-defined|",
                stem + "-value|",
                contentTyped,
                parameterValues);
        parameterBindings.add(
            new SmtTerm.Binding(parameterBinding.definedSymbol(), argumentResult.defined()));
        parameterBindings.add(
            new SmtTerm.Binding(parameterBinding.valueSymbol(), argumentResult.value()));
        Map<String, LocalBinding> extended = new LinkedHashMap<>(bodyLocalBindings);
        extended.put(parameterName, parameterBinding);
        bodyLocalBindings = Map.copyOf(extended);
      }
    }
    TranslatedExpression body =
        translate(
            operation.expression(),
            selfContext,
            mode,
            positivePolarity,
            bodyLocalBindings,
            inProgress);
    result = body;
    if (parameterized) {
      result =
          new TranslatedExpression(
              Smt.let(parameterBindings, body.defined()),
              Smt.let(parameterBindings, body.value()));
    }
  }

  @Override
  public void visitObjRef(ExpObjRef e) {
    throw unsupported(FragmentBoundary.TIER_2, "object reference");
  }

  /**
   * {@code X->one(body)}: true exactly when precisely one candidate both exists (or, for a
   * navigation/select-filtered range, is actually a member -- see {@link #populationOf}) and
   * satisfies {@code body}. Reuses {@link #populationOf} for every range shape {@code isUnique}/
   * {@link #collectionSize}/{@code forAll}/{@code exists} already trust -- was {@code
   * X.allInstances()}-only until this change, hand-rolling its own {@link PolymorphicRange#slotsOf}
   * loop instead of going through the shared population primitive, the exact same generalization
   * every OTHER quantifier construct in this translator already received. Aggregation: a running
   * count of matches, each an {@code ite} over the member's own guard AND {@code body}'s {@link
   * TranslatedExpression#trueTerm()} -- which already folds an undefined body into "does not
   * count", matching {@code ExpOne#eval}'s own "undefined query values default to false" (confirmed
   * directly from that method, not assumed). The result is unconditionally defined: {@code found ==
   * 1} is a crisp comparison over a crisp count built entirely from {@code ite}s, with no propagated
   * undefinedness of its own to carry -- the same total-result shape confirmed for {@code
   * X.allInstances()} elsewhere in this translator (it is never itself undefined).
   */
  @Override
  public void visitOne(ExpOne e) {
    if (e.getVariableDeclarations().size() != 1) {
      throw unsupported(FragmentBoundary.TIER_3, "one with more than one loop variable");
    }
    List<PopulationMember> population = populationOf(e.getRangeExpression(), "one");
    String loopVariable = e.getVariableDeclarations().varDecl(0).name();
    SmtTerm zero = Smt.intLit(BigInteger.ZERO);
    SmtTerm one = Smt.intLit(BigInteger.ONE);
    SmtTerm count = zero;
    for (PopulationMember member : population) {
      TranslationContext extended = context.withBinding(loopVariable, member.binding());
      TranslatedExpression body =
          translate(e.getQueryExpression(), extended, mode, positivePolarity, localBindings);
      SmtTerm matched = Smt.and(List.of(member.memberGuard(), body.trueTerm()));
      count = Smt.app("+", count, Smt.ite(matched, one, zero));
    }
    result = defined(Smt.eq(count, one));
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

  /**
   * {@code collection->excludes(x)}/{@code collection->includes(x)}, narrowly scoped to the ONE
   * shape this translation slice can represent without needing collections as first-class SMT
   * values at all: {@code role->closure(role)}, e.g. Genealogy's {@code
   * p.parent->closure(parent)->excludes(p)} and RecursiveTree's {@code
   * self.child->closure(child)->excludes(self)} -- both the "is this object its own ancestor via
   * repeated navigation of a single association end" acyclicity idiom. Anything else {@code
   * excludes}/{@code includes} could receive (a set literal, a filtered collection, a general
   * {@code closure()} whose body does not simply re-navigate the same end, ...) is refused at the
   * same {@code TIER_3} {@link #boundaryOfOperator} would already have classified it at.
   */
  private TranslatedExpression membershipTest(
      Expression collectionExpr, Expression elementExpr, boolean wantIncludes) {
    // The corpus's chained form desugars to a COLLECT of per-member closures:
    // `p.contained()->collect($e | $e.containedPlus())->excludes(p)`. p sits in that union
    // exactly when p sits on a containment cycle through itself (every opC(m) for m one hop
    // from p is subsumed by opC(p), and a cycle through p passes through a member), which is
    // precisely the DIRECT form p.containedPlus()->excludes(p) -- so the collect is rewritten
    // to the direct call and handled by the branch below.
    if (collectionExpr instanceof ExpCollect collect
        && collect.getRangeExpression() instanceof ExpObjOp rangeOp
        && rangeOp.getArguments().length == 1
        && rangeOp.getArguments()[0] instanceof ExpVariable memberVar
        && collect.getQueryExpression() instanceof ExpObjOp collectBodyOp
        && collectBodyOp.getArguments().length == 1
        && collectBodyOp.getArguments()[0] instanceof ExpVariable collectIteratorVar) {
      try {
        Expression direct =
            new ExpObjOp(collectBodyOp.getOperation(), new Expression[] {memberVar});
        return membershipTest(direct, elementExpr, wantIncludes);
      } catch (org.tzi.use.uml.ocl.expr.ExpInvalidException impossible) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "collect-of-operations rewrite failed: " + impossible.getMessage());
      }
    }
    // A zero-arg query operation whose body is a closure (CompanyERSchema's containedPlus():
    // `self.contained()->closure(p|p.contained())`) is inlined structurally: the operation's
    // body IS the closure, with the operation's receiver bound as `self` for the whole
    // reachability computation.
    if (collectionExpr instanceof ExpObjOp objOp
        && objOp.getOperation().expression() instanceof ExpClosure closure
        && objOp.getArguments().length == 1
        && objOp.getArguments()[0] instanceof ExpVariable receiverVar) {
      VariableBinding receiver = context.binding(receiverVar.getVarname());
      SmtTerm reachable = closureReachabilityWithReceiver(closure, elementExpr, receiver);
      return defined(wantIncludes ? reachable : Smt.not(reachable));
    }
    // String-constant set literal: membership by CONTENT against the element's own
    // registered domain (single domain, so no cross-domain index comparison arises).
    if (collectionExpr instanceof ExpSetLiteral set
        && set.getElemExpr().length > 0
        && set.getElemExpr()[0] instanceof ExpConstString) {
      List<String> constants = distinctStringConstants(set);
      ContentOperand element = contentOperand(elementExpr);
      if (element == null || element.enumeratedValues() == null) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            (wantIncludes ? "includes" : "excludes")
                + " over a String set literal requires a String-typed element with a"
                + " registered domain");
      }
      List<SmtTerm> stringMatches = new ArrayList<>();
      for (String constant : constants) {
        int idx = element.enumeratedValues().indexOf(constant);
        if (idx >= 0) {
          stringMatches.add(Smt.eq(element.value(), Smt.intLit(BigInteger.valueOf(idx))));
        }
      }
      SmtTerm stringMember = Smt.and(List.of(element.defined(), Smt.or(stringMatches)));
      return defined(wantIncludes ? stringMember : Smt.not(stringMember));
    }
    if (collectionExpr instanceof ExpSetLiteral set) {
      // Membership in an Integer-constant set literal: the element's value equals one of the
      // DISTINCT literal constants (total -- a literal is always defined).
      List<BigInteger> constants = distinctIntegerConstants(set);
      TranslatedExpression element = argResult(elementExpr);
      List<SmtTerm> matches = new ArrayList<>();
      for (BigInteger constant : constants) {
        matches.add(Smt.eq(element.value(), Smt.intLit(constant)));
      }
      SmtTerm member = Smt.and(List.of(element.defined(), Smt.or(matches)));
      return defined(wantIncludes ? member : Smt.not(member));
    }
    if (!(collectionExpr instanceof ExpClosure closure)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          (wantIncludes ? "includes" : "excludes")
              + " over anything other than closure(role) is not yet supported");
    }
    SmtTerm reachable = closureReachability(closure, elementExpr);
    return defined(wantIncludes ? reachable : Smt.not(reachable));
  }

  /**
   * The DISTINCT String constants of a set literal, in declaration order (duplicates
   * collapsed per Set semantics).
   */
  private static List<String> distinctStringConstants(ExpSetLiteral set) {
    List<String> values = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      if (!(element instanceof ExpConstString constant)) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "Set literal with a non-constant or non-String element ("
                + element
                + "); only String constants are supported in this slice");
      }
      if (!values.contains(constant.value())) {
        values.add(constant.value());
      }
    }
    return values;
  }

  /**
   * The DISTINCT Integer constants of a set literal, in declaration order (duplicates
   * collapsed per Set semantics).
   */
  private static List<BigInteger> distinctIntegerConstants(ExpSetLiteral set) {
    List<BigInteger> values = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      if (!(element instanceof ExpConstInteger constant)) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "Set literal with a non-constant or non-Integer element ("
                + element
                + "); only Integer constants are supported in this slice");
      }
      BigInteger value = BigInteger.valueOf(constant.value());
      if (!values.contains(value)) {
        values.add(value);
      }
    }
    return values;
  }

  /** Runs {@link #closureReachability} with {@code self} aliased to the operation's receiver. */
  private SmtTerm closureReachabilityWithReceiver(
      ExpClosure closure, Expression elementExpr, VariableBinding selfBinding) {
    TranslationContext outer = context;
    context = context.withBinding("self", selfBinding);
    try {
      return closureReachability(closure, elementExpr);
    } finally {
      context = outer;
    }
  }

  /**
   * Bounded, greater-or-equal-to-one-hop reachability of {@code elementExpr} from {@code
   * closure}'s own starting navigation, over the SAME association end {@code closure}'s body
   * re-navigates -- confirmed against the real semantics ({@code ExpClosure.evalClosureAux},
   * use-core) before this was written, not assumed: starting from the range expression's own
   * value (the DIRECT, one-hop set), repeatedly re-navigate the SAME role from each newly
   * reached object and union in whatever is newly reached, until nothing new is added. For a
   * bounded object universe that is exactly bounded graph reachability over the association's
   * own link-boolean grid ({@link AssociationLinks}, the SAME grid {@link #linkTerm} already
   * reads elsewhere) -- not a general "collections as first-class SMT values" question at all,
   * which is why this stays narrowly scoped to feeding {@link #membershipTest} rather than
   * becoming a general {@code visitClosure}.
   *
   * <p>Computed via the standard "extend the reachable set by one more hop, N times" fixed-point
   * construction (N = the destination class's own capacity; any node reachable at all is
   * reachable within N hops, since a simple path visits at most N nodes). Each hop's N candidate
   * cells are bound to FRESH, NAMED SMT-LIB {@code let} symbols via {@link Smt#let}, one {@code
   * let} per hop NESTED inside the previous hop's body -- required for correctness, not just
   * size: SMT-LIB {@code let} bindings within ONE {@code let} are SIMULTANEOUS ({@link
   * SmtTerm.Let}'s own class javadoc), so a later hop's formula can only see an earlier hop's
   * symbols if its binding sits inside that earlier hop's nested body, never flattened into one
   * binding list. The naming is load-bearing for a second reason too: without it, each hop would
   * re-embed every earlier hop's full formula inline, growing the emitted term EXPONENTIALLY in
   * hop count (hand-derived before choosing this construction, not discovered empirically) --
   * exactly the failure mode named-{@code let} sharing exists to avoid.
   */
  private SmtTerm closureReachability(ExpClosure closure, Expression elementExpr) {
    if (closure.getRangeExpression() instanceof ExpObjOp rangeOp) {
      return operationClosureReachability(closure, rangeOp, elementExpr);
    }
    if (!(closure.getRangeExpression() instanceof ExpNavigation rangeNav)
        || !rangeNav.getDestination().isCollection()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "closure() over a range other than a collection-valued navigation is not yet"
              + " supported");
    }
    String loopVariable = closure.getVariableDeclarations().varDecl(0).name();
    if (!(closure.getQueryExpression() instanceof ExpNavigation queryNav)
        || !(queryNav.getObjectExpression() instanceof ExpVariable queryVar)
        || !queryVar.getVarname().equals(loopVariable)
        || !queryNav.getDestination().equals(rangeNav.getDestination())) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "closure() whose body does not directly re-navigate the exact same association end is"
              + " not yet supported");
    }
    VariableBinding elementBinding = context.binding(variableNameOf(elementExpr));
    String destClass = rangeNav.getDestination().cls().name();
    if (!elementBinding.className().equals(destClass)) {
      // Structurally a different class entirely: can never be a member of this closure.
      return Smt.bool(false);
    }
    int capacity = context.slotsFor(destClass).capacity();
    if (capacity == 0) {
      return Smt.bool(false);
    }
    AssociationLinks links = context.linksFor(rangeNav.getDestination().association().name());
    List<PopulationMember> seed = populationOf(rangeNav, "closure");
    String stem =
        "|closure-"
            + rangeNav.getDestination().association().name()
            + "-"
            + rangeNav.getDestination().nameAsRolename()
            + "-";

    List<List<SmtTerm.Binding>> hopBindings = new ArrayList<>();
    String[] previousSymbols = new String[capacity];
    List<SmtTerm.Binding> firstHop = new ArrayList<>(capacity);
    for (int k = 0; k < capacity; k++) {
      String symbol = stem + "1-" + k + "|";
      previousSymbols[k] = symbol;
      firstHop.add(new SmtTerm.Binding(symbol, seed.get(k).memberGuard()));
    }
    hopBindings.add(firstHop);
    for (int hop = 2; hop <= capacity; hop++) {
      String[] currentSymbols = new String[capacity];
      List<SmtTerm.Binding> bindings = new ArrayList<>(capacity);
      for (int k = 0; k < capacity; k++) {
        List<SmtTerm> viaAnyIntermediate = new ArrayList<>();
        for (int m = 0; m < capacity; m++) {
          SmtTerm link =
              linkTerm(links, rangeNav.getDestination(), new VariableBinding(destClass, m), k);
          viaAnyIntermediate.add(Smt.and(List.of(Smt.sym(previousSymbols[m]), link)));
        }
        String name = stem + hop + "-" + k + "|";
        currentSymbols[k] = name;
        bindings.add(
            new SmtTerm.Binding(
                name, Smt.or(List.of(Smt.sym(previousSymbols[k]), Smt.or(viaAnyIntermediate)))));
      }
      hopBindings.add(bindings);
      previousSymbols = currentSymbols;
    }

    SmtTerm result = Smt.sym(previousSymbols[elementBinding.slotIndex()]);
    for (int i = hopBindings.size() - 1; i >= 0; i--) {
      result = Smt.let(hopBindings.get(i), result);
    }
    return result;
  }

  /**
   * The operation-call sibling of the navigation closure branch: {@code p.op()->excludes(p)}
   * where {@code op(): Set(T) = T.allInstances()->select(v | pred)} is a zero-argument query
   * operation and the closure body re-calls the SAME operation on its iterator
   * ({@code closure(i | i.op())}) -- exactly CompanyERSchema's {@code containedPlus()} shape.
   *
   * <p>Reachability is the bounded least fixed point of the operation's membership relation
   * Q(m, k) = "k is a member of op(m)", instantiated by translating the select's predicate with
   * the select iterator bound to k's slot and {@code self} bound to m's slot (n*n predicate
   * translations for n candidate slots, n fixed-point hops -- the same bounded-closure pattern
   * as the navigation branch, with the predicate in place of the per-pair link term). Seed:
   * one application from the operation's receiver ({@code self}, bound by the inliner to the
   * receiver's slot). The element is reachable iff it is in the fixed point.
   */
  private SmtTerm operationClosureReachability(
      ExpClosure closure, ExpObjOp rangeOp, Expression elementExpr) {
    MOperation operation = rangeOp.getOperation();
    Expression operationBody = operation.expression();
    if (operationBody == null
        || !(operationBody instanceof ExpSelect select)
        || !(select.getRangeExpression() instanceof ExpAllInstances all)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "closure() over the operation '"
              + operation.name()
              + "()' requires its body to be T.allInstances()->select(v | pred) with a"
              + " single loop variable");
    }
    if (closure.getVariableDeclarations().size() != 1
        || !(closure.getQueryExpression() instanceof ExpObjOp bodyCall)
        || bodyCall.getOperation() != operation
        || !(bodyCall.getArguments()[0] instanceof ExpVariable bodyReceiver)
        || !bodyReceiver.getVarname().equals(closure.getVariableDeclarations().varDecl(0).name())) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "closure() whose body does not re-call the same operation on its iterator is not yet"
              + " supported");
    }
    VariableBinding sourceBinding = context.binding(variableNameOf(rangeOp.getArguments()[0]));
    VariableBinding elementBinding = context.binding(variableNameOf(elementExpr));
    String selectIterator = select.getVariableDeclarations().varDecl(0).name();

    List<PolymorphicRange.Slot> domain =
        PolymorphicRange.slotsOf(all.getSourceType(), context);
    int capacity = domain.size();
    if (capacity == 0) {
      return Smt.bool(false);
    }
    int sourceIndex = domain.indexOf(new PolymorphicRange.Slot(sourceBinding, null));
    List<PolymorphicRange.Slot> slots = domain;
    if (sourceIndex < 0) {
      // The receiver may name a slot the folded domain lists under an equal-value binding;
      // match by class name and index conservatively.
      for (int i = 0; i < domain.size(); i++) {
        if (domain.get(i).binding().className().equals(sourceBinding.className())
            && domain.get(i).binding().slotIndex() == sourceBinding.slotIndex()) {
          sourceIndex = i;
          break;
        }
      }
      if (sourceIndex < 0) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "closure() over '"
                + operation.name()
                + "()': the receiver binding "
                + sourceBinding
                + " is not in the operation's declared domain population");
      }
    }
    int elementIndex = -1;
    for (int i = 0; i < slots.size(); i++) {
      if (slots.get(i).binding().className().equals(elementBinding.className())
          && slots.get(i).binding().slotIndex() == elementBinding.slotIndex()) {
        elementIndex = i;
        break;
      }
    }
    if (elementIndex < 0) {
      // Structurally a different class: never a member of this closure.
      return Smt.bool(false);
    }

    java.util.function.BiFunction<Integer, Integer, SmtTerm> memberTerm =
        (m, k) -> {
          TranslationContext pairContext =
              context
                  .withBinding("self", slots.get(m).binding())
                  .withBinding(selectIterator, slots.get(k).binding());
          return translate(predicateOf(select), pairContext, mode, positivePolarity, localBindings)
              .value();
        };

    // Fixed point: reach_1(k) = Q(source, k); reach_{h+1}(k) = reach_h(k) OR (any m: reach_h(m)
    // AND Q(m, k)). After `capacity` hops every multi-step derivation is covered.
    SmtTerm[] reach = new SmtTerm[capacity];
    for (int k = 0; k < capacity; k++) {
      reach[k] = memberTerm.apply(sourceIndex, k);
    }
    for (int hop = 2; hop <= capacity; hop++) {
      SmtTerm[] next = new SmtTerm[capacity];
      for (int k = 0; k < capacity; k++) {
        List<SmtTerm> viaAny = new ArrayList<>();
        for (int m = 0; m < capacity; m++) {
          viaAny.add(Smt.and(List.of(reach[m], memberTerm.apply(m, k))));
        }
        next[k] = Smt.or(List.of(reach[k], Smt.or(viaAny)));
      }
      reach = next;
    }
    return reach[elementIndex];
  }

  private static Expression predicateOf(ExpSelect select) {
    return select.getQueryExpression();
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
