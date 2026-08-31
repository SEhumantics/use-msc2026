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
import org.tzi.use.uml.mm.MAssociation;
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
      if (navigation.getObjectExpression() instanceof ExpNavigation) {
        // CHAINED navigation-as-a-value (a.b.c.level): the general-case read the
        // ocl.navigation-regular-assoc row names -- every hop is a single-valued
        // navigation and the attribute sits at the terminal hop.
        result = chainedAttributeValue(navigation, e.attr());
        return;
      }
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
    if (e.objExp() instanceof ExpAsType cast) {
      if (cast.getSourceExpr() instanceof ExpVariable srcVar
          && !localBindings.containsKey(srcVar.getVarname())) {
        result = castReceiverAttribute(cast, srcVar.getVarname(), e.attr());
        return;
      }
      if (cast.getSourceExpr() instanceof ExpNavigation nav
          && !nav.getDestination().isCollection()
          && nav.getObjectExpression() instanceof ExpVariable navSource
          && !localBindings.containsKey(navSource.getVarname())) {
        result = navigatedCastAttribute(cast, nav, navSource.getVarname(), e.attr());
        return;
      }
    }
    VariableBinding b = context.binding(variableNameOf(e.objExp()));
    AttributeValues v = context.attributeValues(b.className(), e.attr().name());
    if (v.type() == AttributeType.SET_INTEGER) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "bare read of the collection-typed attribute '"
              + e.attr().name()
              + "': consume it through a supported collection operation (->includes,"
              + " ->size, ->isEmpty, ->forAll, ...)");
    }
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
    if (destination.association() instanceof MAssociationClass assocClass) {
      // Two shapes land here: the CLASSIFIER-to-end navigation (e.employer -- destination is an
      // END of the association class) and an end-to-CLASSIFIER attribute read (p.own.attr --
      // destination IS the classifier). Only the former has an end view to fold: use the same
      // folded view the pointer mechanism's guards were built over. Hand-built contexts without
      // registered views keep the single-class slotsFor behavior.
      boolean destinationIsTheClassifier =
          destination.cls().name().equals(assocClass.name());
      ObjectSlots assocClassSlots =
          destinationIsTheClassifier
              ? context.slotsFor(destination.cls().name())
              : assocClassEndViewOrNull(assocClass, destination);
      if (assocClassSlots == null) {
        assocClassSlots = context.slotsFor(destination.cls().name());
      }
      AttributeValues assocClassValues =
          context.attributeValues(assocClassSlots.className(), attribute.name());
      guardEndAgainstUncertainAttribute(assocClassSlots, attribute, assocClassValues);
      return associationClassNavigatedAttribute(
          source, destination, assocClassSlots, assocClassValues, attribute);
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
   * The CHAINED single-valued navigation read ({@code a.b.c.level}, two or more hops): the
   * value is built hop by hop, exactly as USE's sequential navigation evaluates -- hop j+1's
   * link term is selected under hop j's link, and the terminal hop selects the attribute value
   * per slot (the same per-slot pattern {@code selectLinkedValue} builds for one hop). The
   * result is defined iff EVERY hop along the chain is linked: a broken chain makes the read
   * undefined, which is the sequential-evaluation semantics, not an approximation. Each hop's
   * destination resolves redefinition against the PREVIOUS hop's concrete slot binding (slot
   * classes are known per candidate), so chains over folded or redefining ends stay correct.
   */
  private TranslatedExpression chainedAttributeValue(
      ExpNavigation outerNavigation, MAttribute attribute) {
    List<MNavigableElement> hops = new ArrayList<>();
    Expression cursor = outerNavigation;
    while (cursor instanceof ExpNavigation nav) {
      hops.add(nav.getDestination());
      cursor = nav.getObjectExpression();
    }
    java.util.Collections.reverse(hops);
    if (!(cursor instanceof ExpVariable rootVar)
        || localBindings.containsKey(rootVar.getVarname())) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "chained navigation whose source is not a context variable is not yet supported");
    }
    VariableBinding root = context.binding(rootVar.getVarname());
    return chainRead(root, hops, attribute, 0);
  }

  /**
   * forAll/exists over a SET-typed attribute's pool: only pool elements that are actually
   * MEMBERS are in the set, so forAll quantifies (member => body) and exists quantifies
   * (member AND body) over each pool element, with the iterator bound to that element through
   * the same per-element SMT let the constant-content quantifier uses.
   */
  private TranslatedExpression setAttrQuantifier(
      SetAttrView setAttr, String iterator, Expression bodyExpr, boolean forAll) {
    List<SmtTerm> definedTerms = new ArrayList<>();
    List<SmtTerm> trueTerms = new ArrayList<>();
    for (int j = 0; j < setAttr.poolSize(); j++) {
      String stem = "|set-attr-" + iterator + "-" + j + "|";
      LocalBinding binding =
          new LocalBinding(stem + "-defined|", stem + "-value|", false, null);
      Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
      extended.put(iterator, binding);
      TranslatedExpression body =
          translate(bodyExpr, context, mode, positivePolarity, Map.copyOf(extended));
      List<SmtTerm.Binding> bindings =
          List.of(
              new SmtTerm.Binding(binding.definedSymbol(), Smt.intLit(setAttr.pool().get(j))),
              new SmtTerm.Binding(binding.valueSymbol(), Smt.intLit(setAttr.pool().get(j))));
      SmtTerm member = setAttr.member(j);
      if (forAll) {
        definedTerms.add(Smt.app("=>", member, Smt.let(bindings, body.defined())));
        trueTerms.add(Smt.app("=>", member, Smt.let(bindings, body.value())));
      } else {
        trueTerms.add(Smt.and(List.of(member, Smt.let(bindings, body.trueTerm()))));
      }
    }
    if (forAll) {
      return new TranslatedExpression(Smt.and(definedTerms), Smt.and(trueTerms));
    }
    SmtTerm any = trueTerms.isEmpty() ? Smt.bool(false) : Smt.or(trueTerms);
    return new TranslatedExpression(Smt.bool(true), any);
  }

  /** The recursive hop-by-hop selection behind {@link #chainedAttributeValue}. */
  private TranslatedExpression chainRead(
      VariableBinding source, List<MNavigableElement> hops, MAttribute attribute, int index) {
    MNavigableElement hop = hops.get(index);
    MNavigableElement destination = resolveRedefinedDestination(hop, source);
    if (destination.isCollection()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "chained navigation with a collection-valued intermediate hop is not supported"
              + " (USE desugars it into a collect, a separate feature)");
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    boolean terminal = index == hops.size() - 1;
    List<SmtTerm> linkTerms = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      linkTerms.add(linkTerm(links, destination, source, k));
    }
    // The VALUE nests as ite selections (exactly the selectLinkedValue shape -- a boolean
    // and() over an Int symbol is a sort error the dumped script exposed), the DEFINEDNESS
    // as the OR of per-slot path definedness: a broken chain anywhere leaves the read
    // undefined, which is the sequential-evaluation semantics.
    if (terminal) {
      AttributeValues terminalValues =
          context.attributeValues(destSlots.className(), attribute.name());
      guardEndAgainstUncertainAttribute(destSlots, attribute, terminalValues);
      return new TranslatedExpression(
          Smt.or(linkTerms),
          selectLinkedValue(links, destination, source, destSlots, attribute, terminalValues));
    }
    if (destSlots.capacity() == 0) {
      // No intermediate slot can exist, so no chain passes through this hop: constant-undefined
      // with a never-consulted sort-correct placeholder.
      return new TranslatedExpression(Smt.bool(false), crispPlaceholder(attribute.type()));
    }
    List<TranslatedExpression> branches = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      branches.add(
          chainRead(destSlots.concreteBindings().get(k), hops, attribute, index + 1));
    }
    // DEFINED: some slot is linked AND the rest of the chain from it is defined.
    List<SmtTerm> pathDefined = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      pathDefined.add(Smt.and(List.of(linkTerms.get(k), branches.get(k).defined())));
    }
    SmtTerm defined = Smt.or(pathDefined);
    // VALUE: nested ite over the slots, deepest fallback last; the value is only consulted
    // under `defined`, so the fallback's content is never observable.
    SmtTerm value = branches.get(branches.size() - 1).value();
    for (int k = destSlots.capacity() - 2; k >= 0; k--) {
      value = Smt.ite(linkTerms.get(k), branches.get(k).value(), value);
    }
    return new TranslatedExpression(defined, value);
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
      AttributeValues v, MAttribute attribute) {
    AttributeValues pointer = associationClassPointer(source.className(), destination);
    int capacity = destSlots.capacity();
    if (capacity == 0) {
      return defined(placeholderOfSort(v.type()));
    }
    // Slot k may belong to a CONFIGURED SUBCLASS of the declared end class (the folded view):
    // read the value from that slot's concrete class's registration, exactly what
    // selectLinkedValue does for the ordinary grid path. Identity slots keep the declared
    // class's own values, so an unfolded view is byte-identical to the previous encoding.
    SmtTerm value = valueSymbolForEndSlot(destSlots, v, attribute, capacity - 1);
    for (int k = capacity - 2; k >= 0; k--) {
      SmtTerm pointsHere =
          Smt.eq(Smt.sym(pointer.valueNames().get(source.slotIndex())), Smt.intLit(BigInteger.valueOf(k)));
      value = Smt.ite(pointsHere, valueSymbolForEndSlot(destSlots, v, attribute, k), value);
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
          case "size" -> {
            if (a.length == 1 && a[0].type().isTypeOfString()) {
              yield stringSize(a[0]);
            }
            yield collectionSize(a[0]);
          }
          case "div" -> integerDivision(a);
          // USE's Op_number_div is REAL division on Integers (evalRealResult, confirmed
          // against the use-core bytecode): the result is Real-sorted, so the dividend is
          // lifted with to_real and the division happens in the Reals. A numeral divisor is
          // linear; a finite-domain variable divisor case-splits like div/mod.
          case "/" -> realDivision(a);
          // Numeric string conversion over a configured-candidate string: each parseable
          // candidate contributes its parsed value; USE's evaluators (Integer.parseInt /
          // Double.parseDouble) yield UNDEFINED on NumberFormatException (use-core bytecode),
          // so unparseable candidates are excluded -- a total-equality comparison never
          // matches them, and an all-unparseable domain is the constant-false expression.
          case "indexOf" -> {
            if (a.length == 2 && a[0].type().isTypeOfString() && stringCandidates(a[1]) != null) {
              yield stringIndexOf(a[0], a[1]);
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator 'indexOf' over a non-String receiver or a non-literal needle is not"
                    + " supported in this slice");
          }
          case "toInteger", "toReal" -> {
            if (a.length == 1 && a[0].type().isTypeOfString()) {
              yield stringNumericConversion(a[0], "toInteger".equals(e.opname()));
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator '"
                    + e.opname()
                    + "' over a non-String or wrong-arity operand is not supported in this"
                    + " slice");
          }
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
          case "at" -> {
            // COLLECTION at(i) over a constant-content ordered literal/let-bound collection
            // (Sequence/OrderedSet; Bag and Set are unordered and USE's type checker never
            // produces the expression). The STRING at is handled by the virtual-string
            // machinery inside comparisons and never reaches this case with a String
            // receiver; a non-collection receiver here keeps its refusal.
            if (a.length == 2
                && constantCollectionContent(a[0]) != null
                && a[0].type().isTypeOfSequence()
                || (a.length == 2
                    && constantCollectionContent(a[0]) != null
                    && a[0].type().isTypeOfOrderedSet())) {
              yield collectionAt(a[0], a[1]);
            }
            throw unsupported(
                FragmentBoundary.TIER_3,
                "at over a non-string, non-ordered-collection receiver is not supported in"
                    + " this slice");
          }
          case "sum" -> {
            if (a.length == 1) {
              yield collectionSum(a[0]);
            }
            yield new TranslatedExpression(Smt.bool(false), Smt.bool(false));
          }
          case "first", "last" -> {
            if (a.length == 1) {
              yield collectionEnd(a[0], "first".equals(e.opname()));
            }
            yield new TranslatedExpression(Smt.bool(false), Smt.bool(false));
          }
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
            if (a.length == 1
                && (a[0].type().isTypeOfInteger() || a[0].type().isTypeOfReal())) {
              // Crisp Real abs joins the Integer case: USE's Op_real_abs is Math.abs over
              // doubles with a Real result -- total, no rounding, so the same linear ite
              // shape applies with a Real-sorted zero. UReal keeps its refusal (the
              // uncertainty composition is a different, unsolved problem).
              TranslatedExpression operand = argResult(a[0]);
              boolean real = a[0].type().isTypeOfReal();
              yield new TranslatedExpression(
                  operand.defined(),
                  Smt.ite(
                      Smt.app(
                          ">=",
                          operand.value(),
                          real ? Smt.realLit(BigDecimal.ZERO) : Smt.intLit(BigInteger.ZERO)),
                      operand.value(),
                      Smt.app("-", operand.value())));
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator 'abs' over a non-number operand (UReal uncertainty composition is"
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
            // The Real widening, mirroring arithmetic()'s +/- widening exactly: USE's
            // ArithOperation.matches returns Real when EITHER side is Real (via
            // getLeastCommonSupertype) and Op_number_min/max then compute Math.min/max over
            // doubles -- so a mixed Integer/Real pair widens to a Real result, the Int-sorted
            // side lifts with to_real, and the ite is Real-sorted end to end. Still linear.
            if (a.length == 2
                && (a[0].type().isTypeOfInteger() || a[0].type().isTypeOfReal())
                && (a[1].type().isTypeOfInteger() || a[1].type().isTypeOfReal())
                && (a[0].type().isTypeOfReal() || a[1].type().isTypeOfReal())) {
              TranslatedExpression left = argResult(a[0]);
              TranslatedExpression right = argResult(a[1]);
              SmtTerm leftValue =
                  a[0].type().isTypeOfInteger() ? Smt.app("to_real", left.value()) : left.value();
              SmtTerm rightValue =
                  a[1].type().isTypeOfInteger() ? Smt.app("to_real", right.value()) : right.value();
              SmtTerm comparison =
                  "min".equals(e.opname())
                      ? Smt.app("<=", leftValue, rightValue)
                      : Smt.app(">=", leftValue, rightValue);
              yield new TranslatedExpression(
                  Smt.and(List.of(left.defined(), right.defined())),
                  Smt.ite(comparison, leftValue, rightValue));
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator '"
                    + e.opname()
                    + "' over non-number (UReal uncertainty composition is not in this"
                    + " slice) or wrong-arity operands is not supported");
          }
          // mod with a CONSTANT nonzero divisor: Java % semantics (sign follows the
          // dividend), which SMT-LIB `rem` matches exactly. The divisor must be a
          // compile-time constant: rem by a numeral is linear in the pinned QF_LIA logic,
          // while rem by a variable symbol would need the deferred undefinedness/variable
          // treatment. A zero divisor is genuinely undefined for every value -> the
          // assertion is false (the invariant is violated everywhere).
          case "mod" -> {
            if (a.length == 2 && a[1] instanceof ExpConstInteger divisor) {
              if (divisor.value() == 0) {
                // Mod by zero: the value is undefined for every input, so the assertion is
                // undefined (USE confirms) -> the scenario is unsatisfiable.
                yield new TranslatedExpression(Smt.bool(false), Smt.bool(false));
              }
              requireCrispInteger(a[0], "mod");
              TranslatedExpression dividend = argResult(a[0]);
              yield defined(
                  truncModTerm(dividend.value(), divisor.value()));
            }
            if (a.length == 2) {
              yield symbolicTruncatingDivision(a, true);
            }
            yield new TranslatedExpression(Smt.bool(false), Smt.bool(false));
          }
          // toString() on a crisp Integer: USE's Op_number_toString is Math.toString(int),
          // and the decimal representation is injective, so the encoding is the operand's
          // own value term -- the identity on the value, definedness included. A comparison
          // against a String literal resolves through {@link #resolve(ExpConstString,
          // Expression)} parsing the literal back to the Integer; every OTHER consumer is
          // guarded to refuse (see isIntegerToString's call sites): a String/Enum-encoded
          // operand would compare a domain INDEX against this VALUE, and an ordered
          // comparison would read lexicographic decimal order as Integer order
          // ('9' < '10' lexicographically).
          case "toString" -> {
            if (a.length == 1 && a[0].type().isTypeOfInteger()) {
              yield argResult(a[0]);
            }
            throw unsupported(
                FragmentBoundary.TIER_2,
                "operator 'toString' over a non-Integer operand is not supported in this"
                    + " slice");
          }
          case "round" -> {
            if (a.length == 1 && a[0].type().isTypeOfInteger()) {
              yield argResult(a[0]);
            }
            // Real.round() is USE's Op_real_round = Java Math.round(double) = floor(r + 0.5)
            // (StandardOperationsNumber) -- LINEAR in the pinned QF_LIRA logic once SMT-LIB's
            // own floor-valued to_int is used, so the old blanket refusal was not forced. The
            // encoding matches Java exactly, negative halves included: Math.round(-2.5) = -2.
            if (a.length == 1 && a[0].type().isTypeOfReal()) {
              TranslatedExpression operand = argResult(a[0]);
              yield defined(
                  Smt.app("to_int", Smt.app("+", operand.value(), Smt.realLit(new java.math.BigDecimal("0.5")))));
            }
            throw unsupported(
                boundaryOfOperator(e.opname()),
                "operator 'round' over a non-Integer/non-Real operand (UReal rounding composes"
                    + " uncertainty and is not in this slice)");
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
    // A toString() side makes the operand's Integer VALUE flow into a comparison whose
    // semantics over its String result would be lexicographic -- and lexicographic decimal
    // order is genuinely a different order ('9' < '10'), not an encoding artifact, so this
    // is refused rather than approximated.
    if (isIntegerToString(left) || isIntegerToString(right)) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "ordered comparison against toString() of an Integer: lexicographic decimal string"
              + " order is not Integer order");
    }
    TranslatedExpression l = argResult(left);
    TranslatedExpression r = argResult(right);
    SmtTerm leftValue = l.value();
    SmtTerm rightValue = r.value();
    if (left.type().isTypeOfInteger() && right.type().isTypeOfReal()) {
      leftValue = Smt.app("to_real", leftValue);
    } else if (left.type().isTypeOfReal() && right.type().isTypeOfInteger()) {
      rightValue = Smt.app("to_real", rightValue);
    }
    return new TranslatedExpression(
        Smt.and(List.of(l.defined(), r.defined())), Smt.app(operator, leftValue, rightValue));
  }

  /**
   * The value term of {@code e}, lifted with {@code to_real} when e is Integer-typed -- the
   * mixed-sort guard's building block: Integer-typed terms are not sort-compatible with Real
   * terms under strict SMT-LIB, numerals aside.
   */
  private SmtTerm maybeToReal(Expression e) {
    SmtTerm value = argResult(e).value();
    return e.type().isTypeOfInteger() ? Smt.app("to_real", value) : value;
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
   * through any invariant the real front end will actually compile. A genuinely crisp {@code
   * Real} operand IS reachable this way ({@code self.i + self.r = self.r2} compiles and widens to
   * {@code Real} per the same {@code ArithOperation.matches}, and {@code self.i * self.r = self.r2}
   * widens the same way under {@code *}); it is now SUPPORTED: the operation widens to Real (the
   * same widening USE's own evaluator performs via evalRealResult), the Int-sorted side lifts
   * with {@code to_real} exactly like {@link #realDivision}'s dividend, and the Real-sorted
   * result composes with the existing mixed-sort guards in {@link #comparison} and
   * {@link #orderedComparison}.
   */
  private TranslatedExpression arithmetic(String opname, Expression[] a) {
    if (a.length == 2) {
      requireCrispNumeric(a[0], opname);
      requireCrispNumeric(a[1], opname);
      if ("*".equals(opname)) {
        requireLinearProduct(a[0], a[1]);
      }
      TranslatedExpression l = argResult(a[0]);
      TranslatedExpression r = argResult(a[1]);
      // A REAL-typed operand widens the whole operation to Real (USE's
      // ArithOperation.matches widens (Integer|Real) op (Integer|Real) to Real, and
      // Op_number_plus/-/* compute via evalRealResult then): the Int-sorted side lifts with
      // to_real, mirroring realDivision's dividend lift, and the result term is Real-sorted.
      boolean real = a[0].type().isTypeOfReal() || a[1].type().isTypeOfReal();
      SmtTerm leftValue =
          real && a[0].type().isTypeOfInteger() ? Smt.app("to_real", l.value()) : l.value();
      SmtTerm rightValue =
          real && a[1].type().isTypeOfInteger() ? Smt.app("to_real", r.value()) : r.value();
      return new TranslatedExpression(
          Smt.and(List.of(l.defined(), r.defined())),
          Smt.app(opname, leftValue, rightValue));
    }
    if (a.length == 1 && "-".equals(opname)) {
      requireCrispNumeric(a[0], opname);
      TranslatedExpression operand = argResult(a[0]);
      // SMT-LIB's 1-argument negation form is sort-generic: it negates a Real term exactly
      // like an Int term.
      return new TranslatedExpression(operand.defined(), Smt.app("-", operand.value()));
    }
    if (a.length == 1 && "+".equals(opname)) {
      requireCrispNumeric(a[0], opname);
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
/**
   * mod / div with a VARIABLE divisor whose value is provably one of finitely many literals: a
   * crisp Integer attribute's configured domain. The candidates case-split the divisor exactly
   * the way {@link #integerDivision}'s population-count split does -- each branch is a
   * numeral-divisor rem/div, linear in the pinned QF_LIA logic, where the split over a symbolic
   * divisor itself would be nonlinear and refused. A domain containing 0 keeps the established
   * zero semantics: the value is undefined under that branch, so the emitted definedness
   * excludes it (a zero-only domain therefore yields the constant-false expression, and an
   * invariant mentioning the operation is violated whenever the solver picks 0 -- fail-closed,
   * matching the constant-zero-divisor treatment). The configured domain is the PROOF of the
   * candidate set: AttributeEncoder's domain guard pins the divisor symbol to exactly these
   * values, so the ite chain's impossible fallback is never consulted.
   */
/**
   * {@code x.n / d} over crisp Integer operands: USE's Op_number_div is REAL division on
   * Integers (evalRealResult, confirmed against the use-core bytecode), so the result is
   * Real-sorted -- the dividend lifts with {@code to_real} and the division happens in the
   * Reals, where a numeral divisor is linear in the pinned QF_LIA logic. Variable divisors use
   * the finite-domain case-split {@link #divisionByFiniteDomain} shares: each branch divides by
   * a configured candidate literal. Zero keeps the established semantics (undefined, excluded
   * from the definedness; a zero-only domain is the constant-false expression). Comparisons
   * against Integer-typed operands lift them -- see the mixed-sort guard in
   * {@link #comparison} and {@link #orderedComparison}.
   */
  private TranslatedExpression realDivision(Expression[] arguments) {
    if (arguments.length != 2) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '/' over non-Integer or wrong-arity operands: only crisp Integer / crisp"
              + " Integer (USE's real division) is supported");
    }
    // USE's Op_number_div matches (Number, Number) and answers REAL division; both operands
    // are lifted to the Reals. Dividend: crisp Integer (lifted) or crisp Real attribute.
    boolean dividendInt = arguments[0].type().isTypeOfInteger();
    boolean dividendReal = arguments[0].type().isTypeOfReal();
    if (!dividendInt && !dividendReal) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '/' over a non-numeric dividend: only crisp Integer and crisp Real"
              + " dividends are supported");
    }
    TranslatedExpression dividend = argResult(arguments[0]);
    SmtTerm lifted =
        dividendInt ? Smt.app("to_real", dividend.value()) : dividend.value();
    if (arguments[1] instanceof ExpConstInteger constantDivisor) {
      if (constantDivisor.value() == 0) {
        return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
      }
      return new TranslatedExpression(
          dividend.defined(),
          Smt.app("/", lifted, Smt.realLit(BigDecimal.valueOf(constantDivisor.value()))));
    }
    if (arguments[1] instanceof ExpConstReal constantRealDivisor) {
      BigDecimal c = BigDecimal.valueOf(constantRealDivisor.value());
      if (c.signum() == 0) {
        return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
      }
      return new TranslatedExpression(
          dividend.defined(), Smt.app("/", lifted, Smt.realLit(c)));
    }
    // Variable divisor with a finite configured candidate domain (Integer or Real attribute):
    // the candidate enumeration keeps every branch a division by a nonzero numeral, linear in
    // the pinned logic. Candidates parse exactly as configured; a zero candidate is excluded
    // from the definedness (division by zero is undefined, never a fallback value).
    if (!(arguments[1] instanceof ExpAttrOp divisorAttr)
        || !(divisorAttr.objExp() instanceof ExpVariable divisorSource)
        || localBindings.containsKey(divisorSource.getVarname())) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '/' with a divisor that is neither a compile-time constant nor a"
              + " configured numeric attribute");
    }
    VariableBinding b = context.binding(divisorSource.getVarname());
    AttributeValues divisorValues =
        context.attributeValues(b.className(), divisorAttr.attr().name());
    guardAgainstUncertainAttribute(divisorValues);
    AttributeDomain divisorDomain =
        context.attributeDomain(b.className(), divisorAttr.attr().name());
    boolean divisorInt = arguments[1].type().isTypeOfInteger();
    List<BigDecimal> divisorCandidates = new ArrayList<>();
    for (String candidate : divisorDomain.enumeratedValues()) {
      BigDecimal parsed;
      try {
        parsed = divisorInt
            ? BigDecimal.valueOf(Integer.parseInt(candidate.trim()))
            : new BigDecimal(candidate.trim());
      } catch (NumberFormatException e) {
        throw unsupported(
            FragmentBoundary.TIER_2,
            "operator '/' by a divisor whose configured domain entry '"
                + candidate
                + "' is not a numeric literal");
      }
      divisorCandidates.add(parsed);
    }
    if (divisorCandidates.isEmpty()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '/' by a divisor with an empty configured domain");
    }
    TranslatedExpression divisor = argResult(arguments[1]);
    List<BigDecimal> nonzero =
        divisorCandidates.stream().filter(c -> c.signum() != 0).toList();
    if (nonzero.isEmpty()) {
      return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
    }
    int last = nonzero.size() - 1;
    SmtTerm value = Smt.app("/", lifted, Smt.realLit(nonzero.get(last)));
    for (int i = last - 1; i >= 0; i--) {
      SmtTerm gate =
          divisorInt
              ? Smt.eq(divisor.value(), Smt.intLit(nonzero.get(i).toBigInteger()))
              : Smt.eq(divisor.value(), Smt.realLit(nonzero.get(i)));
      value = Smt.ite(gate, Smt.app("/", lifted, Smt.realLit(nonzero.get(i))), value);
    }
    SmtTerm defined =
        Smt.and(
            List.of(
                dividend.defined(),
                divisor.defined(),
                Smt.not(Smt.eq(divisor.value(), Smt.realLit(BigDecimal.ZERO)))));
    return new TranslatedExpression(defined, value);
  }

  /**
   * The configured candidate literals of a crisp Integer attribute divisor (deduplicated, in
   * configuration order), or null when {@code expression} is not a bare attribute access on a
   * context variable. Non-Integer domain entries and empty domains refuse -- the candidate set
   * is the PROOF the ite case-split is exhaustive.
   */
  private java.util.LinkedHashSet<BigInteger> finiteDomainCandidates(
      Expression expression, String opname) {
    if (!(expression instanceof ExpAttrOp attr)
        || !(attr.objExp() instanceof ExpVariable receiver)
        || localBindings.containsKey(receiver.getVarname())) {
      return null;
    }
    VariableBinding b = context.binding(receiver.getVarname());
    AttributeValues values = context.attributeValues(b.className(), attr.attr().name());
    guardAgainstUncertainAttribute(values);
    AttributeDomain domain = context.attributeDomain(b.className(), attr.attr().name());
    java.util.LinkedHashSet<BigInteger> candidates = new java.util.LinkedHashSet<>();
    for (String candidate : domain.enumeratedValues()) {
      try {
        candidates.add(new BigInteger(candidate.trim()));
      } catch (NumberFormatException e) {
        throw unsupported(
            FragmentBoundary.TIER_2,
            "operator '"
                + opname
                + "' by a divisor whose configured domain entry '"
                + candidate
                + "' is not an Integer literal");
      }
    }
    if (candidates.isEmpty()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '" + opname + "' by a divisor with an empty configured domain");
    }
    return candidates;
  }

  private TranslatedExpression divisionByFiniteDomain(Expression[] arguments, String opname) {
    requireCrispInteger(arguments[0], opname);
    requireCrispInteger(arguments[1], opname);
    java.util.LinkedHashSet<BigInteger> candidates =
        finiteDomainCandidates(arguments[1], opname);
    if (candidates == null) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '"
              + opname
              + "' with a divisor that is neither a compile-time Integer literal nor a crisp"
              + " Integer attribute with a finite configured domain");
    }
    TranslatedExpression dividend = argResult(arguments[0]);
    TranslatedExpression divisor = argResult(arguments[1]);
    java.util.List<BigInteger> nonzero =
        candidates.stream().filter(c -> c.signum() != 0).toList();
    if (nonzero.isEmpty()) {
      return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
    }
    int last = nonzero.size() - 1;
    SmtTerm value =
        opname.equals("mod")
            ? truncModTerm(dividend.value(), nonzero.get(last).intValue())
            : truncDivTerm(dividend.value(), nonzero.get(last).intValue());
    for (int i = last - 1; i >= 0; i--) {
      BigInteger candidate = nonzero.get(i);
      SmtTerm branch =
          opname.equals("mod")
              ? truncModTerm(dividend.value(), candidate.intValue())
              : truncDivTerm(dividend.value(), candidate.intValue());
      value = Smt.ite(Smt.eq(divisor.value(), Smt.intLit(candidate)), branch, value);
    }
    SmtTerm defined =
        Smt.and(List.of(dividend.defined(), divisor.defined()));
    if (nonzero.size() < candidates.size()) {
      defined =
          Smt.and(
              List.of(
                  defined,
                  Smt.not(Smt.eq(divisor.value(), Smt.intLit(BigInteger.ZERO)))));
    }
    return new TranslatedExpression(defined, value);
  }

  private TranslatedExpression integerDivision(Expression[] arguments) {
    // CONSTANT nonzero divisor: Java truncation toward zero. (x - rem(x,d)) is exactly
    // divisible by d, so SMT-LIB div of it is the exact truncated quotient regardless of
    // signs; total (defined = the dividend's definedness).
    if (arguments.length == 2 && arguments[1] instanceof ExpConstInteger constantDivisor) {
      requireCrispInteger(arguments[0], "div");
      if (constantDivisor.value() == 0) {
        // Mod/div by zero is undefined for every value -> the invariant can never hold.
        return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
      }
      TranslatedExpression numerator = argResult(arguments[0]);
      SmtTerm quotient = truncDivTerm(numerator.value(), constantDivisor.value());
      return new TranslatedExpression(numerator.defined(), quotient);
    }
    if (arguments.length != 2
        || !(arguments[1] instanceof ExpStdOp size)
        || !"size".equals(size.opname())
        || size.args().length != 1
        || !(size.args()[0] instanceof ExpSelect select)) {
      // Every other non-constant divisor takes the direct Euclidean encoding (Z3 accepts
      // symbolic div under the pinned logic; see symbolicTruncatingDivision).
      return symbolicTruncatingDivision(arguments, false);
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
   * mod / div with an ARBITRARY divisor: any Int-sorted divisor term works, because the pinned
   * Z3 5.1.0 accepts {@code (mod a d)}/{@code (div a d)} with a variable divisor under QF_LIRA
   * -- re-verified against the real binary per sign case; the long-documented "symbolic divisor
   * is nonlinear" claim was too coarse (Z3's own classification of nonlinearity covers variable
   * x variable multiplication, not Euclidean division). The binary's pair is EUCLIDEAN -- the
   * remainder is always non-negative and takes the DIVISOR's sign ({@code mod(-7,2) = 1},
   * {@code mod(7,-2) = 1}, {@code mod(-7,-2) = 1}) -- while USE/Java truncate toward zero with a
   * dividend-signed remainder. The conversion is linear: the quotient gains a {+1,-1} correction
   * exactly when the Euclidean remainder is nonzero and the operand signs differ (d&gt;0: +1,
   * d&lt;0: -1), and the remainder becomes {@code mod_e - |d|} under the same condition; zero
   * divisors exclude the candidate from the definedness (a total-equality comparison can never
   * match, and a mod-by-0 is never rescued by a sibling value). Division by a zero divisor is
   * likewise undefined.
   */
  private TranslatedExpression symbolicTruncatingDivision(Expression[] arguments, boolean mod) {
    requireCrispInteger(arguments[0], mod ? "mod" : "div");
    requireCrispInteger(arguments[1], mod ? "mod" : "div");
    TranslatedExpression dividend = argResult(arguments[0]);
    TranslatedExpression divisor = argResult(arguments[1]);
    SmtTerm a = dividend.value();
    SmtTerm d = divisor.value();
    SmtTerm zero = Smt.intLit(BigInteger.ZERO);
    SmtTerm modE = Smt.app("mod", a, d);
    SmtTerm divE = Smt.app("div", a, d);
    // Sign-correction condition: the Euclidean remainder is nonzero AND the operand signs
    // differ -- exactly the cases where Euclidean and truncated quotients differ by one.
    SmtTerm cond =
        Smt.and(
            List.of(
                Smt.not(Smt.eq(modE, zero)),
                Smt.or(
                    List.of(
                        Smt.and(List.of(Smt.app("<", a, zero), Smt.app(">", d, zero))),
                        Smt.and(List.of(Smt.app("<", a, zero), Smt.app("<", d, zero)))))));
    SmtTerm absD = Smt.ite(Smt.app(">=", d, zero), d, Smt.app("-", d));
    SmtTerm quotient = Smt.ite(cond, Smt.app("+", divE, Smt.ite(Smt.app(">", d, zero), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.valueOf(-1)))), divE);
    SmtTerm remainder = Smt.ite(cond, Smt.app("-", modE, absD), modE);
    SmtTerm defined =
        Smt.and(
            List.of(dividend.defined(), divisor.defined(), Smt.not(Smt.eq(d, zero))));
    return new TranslatedExpression(defined, mod ? remainder : quotient);
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
    if (!isNumericLiteral(left) && !isNumericLiteral(right)) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '*' between two non-constant numeric operands: multiplying two non-constant"
              + " terms is nonlinear arithmetic, which this project's pinned QF_LIRA solver logic"
              + " does not accept (confirmed against the real Z3 binary) -- only a product with at"
              + " least one compile-time numeric literal operand is supported");
    }
  }

  private static boolean isNumericLiteral(Expression e) {
    if (e instanceof ExpConstInteger || e instanceof ExpConstReal) {
      return true;
    }
    return e instanceof ExpStdOp op
        && "-".equals(op.opname())
        && op.args().length == 1
        && isNumericLiteral(op.args()[0]);
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
   * {@link #requireCrispInteger}'s numeric superset for {@code +}/{@code -}/{@code *}: a crisp
   * Real operand widens the operation to Real (USE's own ArithOperation widening), and the
   * translator lifts the Int-sorted side. U-typed operands stay refused -- the compiler itself
   * rejects every invariant shape that could carry a U-typed arithmetic result back to Boolean,
   * and {@code guardAgainstUncertainAttribute} re-guards bare attribute operands.
   */
  private void requireCrispNumeric(Expression e, String opname) {
    if (e instanceof ExpAttrOp attr
        && attr.objExp() instanceof ExpVariable source
        && !localBindings.containsKey(source.getVarname())) {
      guardAgainstUncertainAttribute(
          context.attributeValues(context.binding(source.getVarname()).className(),
              attr.attr().name()));
    }
    if (!e.type().isTypeOfInteger() && !e.type().isTypeOfReal()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "operator '"
              + opname
              + "' over a non-numeric operand of type "
              + e.type()
              + ": only plain crisp Integer/Real arithmetic is supported");
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
    if (comparisonArgs.length != 2) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UReal threshold whose left operand is not an attribute access");
    }
    // Two supported left-operand shapes: the attribute access (the original form) and a
    // U-type LET variable, whose LocalBinding carries the same (representative, uncertainty)
    // pair an attribute's symbols carry -- {@link #uTypeLet} bound both components, so the
    // threshold reads them from the binding and everything downstream is shared.
    ExpAttrOp attribute = comparisonArgs[0] instanceof ExpAttrOp a ? a : null;
    ExpVariable letVariable = null;
    ExpStdOp sizeOperand = null;
    ExpAttrOp usAttr = null;
    boolean sizeNavigated = false;
    if (attribute == null
        && comparisonArgs[0] instanceof ExpStdOp candidate
        && "size".equals(candidate.opname())
        && candidate.args().length == 1
        && candidate.args()[0] instanceof ExpAttrOp sizeAttr
        && sizeAttr.type().isTypeOfUString()) {
      usAttr = sizeAttr;
      if (sizeAttr.objExp() instanceof ExpVariable bareVar
          && !localBindings.containsKey(bareVar.getVarname())) {
        sizeOperand = candidate;
      } else if (sizeAttr.objExp() instanceof ExpNavigation sizeNav
          && !sizeNav.getDestination().isCollection()
          && sizeNav.getObjectExpression() instanceof ExpVariable navVar
          && !localBindings.containsKey(navVar.getVarname())) {
        // NAVIGATED (folded) UString size: the representative is the per-slot spelling length
        // over the end view -- folded views included, each slot's lengths from its concrete
        // class's configured spellings -- and the uncertainty the slot's confidence symbol.
        sizeOperand = candidate;
        sizeNavigated = true;
      }
    }
    if (attribute == null && letVariable == null) {
      if (comparisonArgs[0] instanceof ExpVariable v && localBindings.containsKey(v.getVarname())) {
        letVariable = v;
      } else if (sizeOperand == null) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "UReal threshold whose left operand is not an attribute access or a U-type let"
                + " variable");
      }
    }
    if (attribute != null) {
      if (!attribute.type().isTypeOfUReal() && !attribute.type().isTypeOfUInteger()) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE, "toBooleanC comparison over a non-U-typed attribute");
      }
    }
    if (attribute != null
        && comparisonArgs[1] instanceof ExpAttrOp rightAttribute
        && isUncertainNumericType(attribute.type())
        && isUncertainNumericType(rightAttribute.type())
        && List.of("<", ">", "<=", ">=").contains(comparison.opname())) {
      return uTypeUncertainVsUncertain(comparison, attribute, rightAttribute, projectionArgs[1]);
    }

    BigDecimal literal =
        decimalLiteral(
            comparisonArgs[1],
            "comparison threshold",
            FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN);
    BigDecimal confidence =
        decimalLiteral(projectionArgs[1], "confidence threshold", FragmentBoundary.UTYPE_CORE);

    SmtTerm declared = null;
    boolean integerRepresentative = false;
    SmtTerm uncertainty = null;
    SmtTerm operandLinkGuard = null;
    if (attribute != null) {
      VariableBinding binding = context.binding(variableNameOf(attribute.objExp()));
      AttributeValues values = context.attributeValues(binding.className(), attribute.attr().name());
      if (!values.type().isPairedUType()) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "U-type threshold without paired value/uncertainty SMT terms");
      }
      declared = Smt.sym(values.valueNames().get(binding.slotIndex()));
      integerRepresentative = values.type() == AttributeType.UINTEGER;
      uncertainty = Smt.sym(values.uncertaintyNames().get(binding.slotIndex()));
    } else if (letVariable != null) {
      LocalBinding local = localBindings.get(letVariable.getVarname());
      if (local.uncertaintySymbol() == null) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "U-type threshold over a let variable without a paired uncertainty binding");
      }
      declared = Smt.sym(local.valueSymbol());
      integerRepresentative = letVariable.type().isTypeOfUInteger();
      uncertainty = Smt.sym(local.uncertaintySymbol());
    } else if (sizeOperand != null && !sizeNavigated) {
      // BARE UString.size(): the representative is the SPELLING LENGTH -- a constant per
      // configured spelling candidate, selected by the spelling symbol's ite chain -- and the
      // uncertainty is the confidence symbol (UStringValue.uSize() carries the confidence
      // through).
      VariableBinding ub =
          context.binding(((ExpVariable) usAttr.objExp()).getVarname());
      AttributeValues uvalues = context.attributeValues(ub.className(), usAttr.attr().name());
      AttributeDomain spellings =
          context.attributeDomain(ub.className(), usAttr.attr().name(), "value");
      List<String> spellingList = spellings.enumeratedValues();
      if (spellingList.isEmpty()) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "size over a UString attribute with an empty configured spelling domain");
      }
      SmtTerm spellingSym = Smt.sym(uvalues.valueNames().get(ub.slotIndex()));
      SmtTerm rep = Smt.intLit(BigInteger.valueOf(spellingList.get(spellingList.size() - 1).length()));
      for (int i = spellingList.size() - 2; i >= 0; i--) {
        rep =
            Smt.ite(
                Smt.eq(spellingSym, Smt.intLit(BigInteger.valueOf(i))),
                Smt.intLit(BigInteger.valueOf(spellingList.get(i).length())),
                rep);
      }
      declared = rep;
      integerRepresentative = true;
      uncertainty = Smt.sym(uvalues.confidenceNames().get(ub.slotIndex()));
    } else {
      // NAVIGATED (folded) UString.size(): the representative is the per-slot spelling length
      // over the end view -- each slot's lengths from its concrete class's configured
      // spellings -- nested under the slot link guards, and the uncertainty the per-slot
      // confidence symbol under the same guards. The fallback is the last slot's block, never
      // consulted while unlinked (the definedness is false there and the body is violated).
      VariableBinding navSource =
          context.binding(
              ((ExpVariable) ((ExpNavigation) usAttr.objExp()).getObjectExpression())
                  .getVarname());
      MNavigableElement destination =
          resolveRedefinedDestination(
              ((ExpNavigation) usAttr.objExp()).getDestination(), navSource);
      if (destination.association() instanceof MAssociationClass) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "navigated UString size over an association class is not yet supported");
      }
      AssociationLinks links = context.linksFor(destination.association().name());
      ObjectSlots destSlots = destinationEndView(links, destination);
      List<SmtTerm> slotBlocks = new ArrayList<>();
      List<SmtTerm> slotGuards = new ArrayList<>();
      List<SmtTerm> slotUnc = new ArrayList<>();
      for (int k = 0; k < destSlots.capacity(); k++) {
        VariableBinding concrete = destSlots.concreteBindings().get(k);
        String concreteClass = concrete.className();
        AttributeValues vals = context.attributeValues(concreteClass, usAttr.attr().name());
        AttributeDomain classSpellings =
            context.attributeDomain(concreteClass, usAttr.attr().name(), "value");
        context.attributeDomain(concreteClass, usAttr.attr().name(), "confidence");
        List<String> classSpellingList = classSpellings.enumeratedValues();
        if (classSpellingList.isEmpty()) {
          throw unsupported(
              FragmentBoundary.UTYPE_CORE,
              "size over a UString attribute whose concrete class "
                  + concreteClass
                  + " has an empty configured spelling domain");
        }
        SmtTerm classSym = Smt.sym(vals.valueNames().get(concrete.slotIndex()));
        SmtTerm classUnc = Smt.sym(vals.confidenceNames().get(concrete.slotIndex()));
        SmtTerm block =
            Smt.intLit(
                BigInteger.valueOf(
                    classSpellingList.get(classSpellingList.size() - 1).length()));
        for (int i = classSpellingList.size() - 2; i >= 0; i--) {
          block =
              Smt.ite(
                  Smt.eq(classSym, Smt.intLit(BigInteger.valueOf(i))),
                  Smt.intLit(BigInteger.valueOf(classSpellingList.get(i).length())),
                  block);
        }
        SmtTerm link = linkTerm(links, destination, navSource, k);
        slotBlocks.add(block);
        slotGuards.add(link);
        slotUnc.add(classUnc);
      }
      declared = slotBlocks.get(slotBlocks.size() - 1);
      uncertainty = slotUnc.get(slotUnc.size() - 1);
      for (int k = slotBlocks.size() - 2; k >= 0; k--) {
        declared = Smt.ite(slotGuards.get(k), slotBlocks.get(k), declared);
        uncertainty = Smt.ite(slotGuards.get(k), slotUnc.get(k), uncertainty);
      }
      integerRepresentative = true;
      operandLinkGuard = Smt.or(slotGuards);
    }
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
    SmtTerm representative = integerRepresentative ? Smt.app("to_real", declared) : declared;
    SmtTerm zero = Smt.realLit(BigDecimal.ZERO);
    SmtTerm exact = Smt.app(comparison.opname(), representative, Smt.realLit(literal));
    if (mode == TranslationMode.NOMINAL) {
      return defined(operandLinkGuard == null ? exact : Smt.and(List.of(operandLinkGuard, exact)));
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
    SmtTerm thresholdFormula =
        Smt.or(
            List.of(
                Smt.and(List.of(Smt.eq(uncertainty, zero), exact)),
                Smt.and(List.of(Smt.app(">", uncertainty, zero), uncertain))));
    return defined(
        operandLinkGuard == null
            ? thresholdFormula
            : Smt.and(List.of(operandLinkGuard, thresholdFormula)));
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
  /**
   * Compile-time SET equality of two constant contents: same kind, same distinct elements
   * (order-insensitive, duplicate-collapsed).
   */
  private static boolean sameElements(SetContent a, SetContent b) {
    if ((a.integers() == null) != (b.integers() == null)) {
      return false;
    }
    if (a.integers() != null) {
      return new java.util.TreeSet<>(a.integers()).equals(new java.util.TreeSet<>(b.integers()));
    }
    if ((a.strings() == null) != (b.strings() == null)) {
      return false;
    }
    if (a.strings() != null) {
      return new java.util.TreeSet<>(a.strings()).equals(new java.util.TreeSet<>(b.strings()));
    }
    if ((a.reals() == null) != (b.reals() == null)) {
      return false;
    }
    return new java.util.TreeSet<>(a.reals()).equals(new java.util.TreeSet<>(b.reals()));
  }

  /** UReal or UInteger: the two families whose ordered comparison widens to the UReal evaluator. */
  private static boolean isUncertainNumericType(org.tzi.use.uml.ocl.type.Type type) {
    return type.isTypeOfUReal() || type.isTypeOfUInteger();
  }

  /**
   * The dispatcher for EVERY uncertain-vs-uncertain ordered comparison of two UReal attribute
   * accesses. Two encodings, in descending order of generality of their INPUT domains:
   *
   * <ol>
   *   <li>Proven-equal singleton uncertainties with a strict comparator: the verified symbolic
   *       boundary ({@link #uTypeSymmetricThreshold}) -- it accepts UNENUMERATED value domains
   *       because its boundary is a formula over the value symbols, and is kept byte-identical
   *       for the shape it was verified on.</li>
   *   <li>Finite enumerated (value, uncertainty) domains on both sides: the pairwise enumeration
   *       ({@link #uTypePairEnumeration}) -- each candidate pair's probability is a
   *       compile-time double obtained from USE'S OWN evaluator classes, so the pair filter is
   *       bit-exact against USE no matter how idiosyncratic the crossing-point model is.</li>
   * </ol>
   * Anything else (range-bound uncertainties, over-cap pair counts) refuses with a located
   * message rather than approximating.
   */
  private TranslatedExpression uTypeUncertainVsUncertain(
      ExpStdOp comparison, ExpAttrOp leftAttribute, ExpAttrOp rightAttribute, Expression confidenceArg) {
    if (List.of("<", ">").contains(comparison.opname())) {
      VariableBinding leftBinding = context.binding(variableNameOf(leftAttribute.objExp()));
      AttributeValues leftValues =
          context.attributeValues(leftBinding.className(), leftAttribute.attr().name());
      VariableBinding rightBinding = context.binding(variableNameOf(rightAttribute.objExp()));
      AttributeValues rightValues =
          context.attributeValues(rightBinding.className(), rightAttribute.attr().name());
      if (leftValues.type() == AttributeType.UREAL && rightValues.type() == AttributeType.UREAL) {
        BigDecimal leftUncertainty =
            singletonValue(
                context.attributeDomain(leftBinding.className(), leftAttribute.attr().name(), "uncertainty"));
        BigDecimal rightUncertainty =
            singletonValue(
                context.attributeDomain(rightBinding.className(), rightAttribute.attr().name(), "uncertainty"));
        if (leftUncertainty != null
            && rightUncertainty != null
            && leftUncertainty.compareTo(rightUncertainty) == 0) {
          return uTypeSymmetricThreshold(comparison, leftAttribute, rightAttribute, confidenceArg);
        }
      }
    }
    return uTypePairEnumeration(comparison, leftAttribute, rightAttribute, confidenceArg);
  }

  /** The pair-count cap, the project's established 256-combination convention. */
  private static final int MAX_U_TYPE_PAIRS = 256;

  /**
   * The GENERAL uncertain-vs-uncertain ordered comparison as a per-candidate-pair enumeration.
   * Both operands' value and uncertainty domains must be finite enumerations; for each of the
   * at-most-{@value #MAX_U_TYPE_PAIRS} candidate 4-tuples, the comparison probability is a
   * COMPILE-TIME double obtained by calling USE'S OWN evaluator ({@code URealValue.lt/gt/le/ge}
   * over {@code UReal} values built from the exact configured candidate strings) -- bit-exact
   * against USE by construction, crossing-point model included. A pair is admitted iff its
   * probability clears theta (the same {@code >=} {@code Op_uBoolean_toBooleanC} applies), and
   * its guard pins all four selections so a witness can only carry a combination the
   * probability actually holds for. The comparison of two defined UReals is total and
   * {@code toBooleanC} is defined for theta in [0, 1] (confirmed against
   * {@code Op_uBoolean_toBooleanC#eval}), so the result's definedness is the operands'.
   */
  private TranslatedExpression uTypePairEnumeration(
      ExpStdOp comparison, ExpAttrOp leftAttribute, ExpAttrOp rightAttribute, Expression confidenceArg) {
    BigDecimal confidence =
        decimalLiteral(confidenceArg, "confidence threshold", FragmentBoundary.UTYPE_CORE);
    if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
      // Op_uBoolean_toBooleanC: a confidence outside [0, 1] makes the projection UNDEFINED.
      return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
    }
    VariableBinding leftBinding = context.binding(variableNameOf(leftAttribute.objExp()));
    AttributeValues leftValues =
        context.attributeValues(leftBinding.className(), leftAttribute.attr().name());
    VariableBinding rightBinding = context.binding(variableNameOf(rightAttribute.objExp()));
    AttributeValues rightValues =
        context.attributeValues(rightBinding.className(), rightAttribute.attr().name());
    // UInteger participates through its own USE widening (UIntegerValue.lt is
    // toUReal().lt, the same crossing-point evaluator), so the pair enumeration is identical:
    // only the REPRESENTATIVE symbol's sort differs (Int, not Real), which the guard literals
    // below honour.
    boolean leftIsInt = leftValues.type() == AttributeType.UINTEGER;
    boolean rightIsInt = rightValues.type() == AttributeType.UINTEGER;
    if (leftValues.type() != AttributeType.UREAL && leftValues.type() != AttributeType.UINTEGER
        || rightValues.type() != AttributeType.UREAL
            && rightValues.type() != AttributeType.UINTEGER) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN,
          "uncertain-vs-uncertain comparison outside the UReal/UInteger shape");
    }
    List<BigDecimal> leftValueCandidates =
        enumeratedDecimals(context.attributeDomain(leftBinding.className(), leftAttribute.attr().name(), "value"));
    List<BigDecimal> leftUncertaintyCandidates =
        enumeratedDecimals(context.attributeDomain(leftBinding.className(), leftAttribute.attr().name(), "uncertainty"));
    List<BigDecimal> rightValueCandidates =
        enumeratedDecimals(context.attributeDomain(rightBinding.className(), rightAttribute.attr().name(), "value"));
    List<BigDecimal> rightUncertaintyCandidates =
        enumeratedDecimals(context.attributeDomain(rightBinding.className(), rightAttribute.attr().name(), "uncertainty"));
    if (leftValueCandidates == null || leftUncertaintyCandidates == null
        || rightValueCandidates == null || rightUncertaintyCandidates == null
        || (long) leftValueCandidates.size() * leftUncertaintyCandidates.size()
            * rightValueCandidates.size() * rightUncertaintyCandidates.size()
            > MAX_U_TYPE_PAIRS) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNCERTAIN_VERSUS_UNCERTAIN,
          "uncertain-vs-uncertain comparison needs finite enumerated value and uncertainty"
              + " domains on both sides (at most "
              + MAX_U_TYPE_PAIRS
              + " candidate pairs); a range-bound domain or an over-cap cross product is"
              + " outside this slice rather than approximated");
    }
    SmtTerm leftValueSym = Smt.sym(leftValues.valueNames().get(leftBinding.slotIndex()));
    SmtTerm leftUncSym = Smt.sym(leftValues.uncertaintyNames().get(leftBinding.slotIndex()));
    SmtTerm rightValueSym = Smt.sym(rightValues.valueNames().get(rightBinding.slotIndex()));
    SmtTerm rightUncSym = Smt.sym(rightValues.uncertaintyNames().get(rightBinding.slotIndex()));
    if (mode == TranslationMode.NOMINAL) {
      // The nominal-erasure oracle evaluates the crisp comparison of the representatives;
      // the erasure rule removes the confidence projection entirely. A mixed
      // UInteger/UReal pair lifts the Int-sorted side (the same widening the UNCERTAIN
      // pairs below encode).
      SmtTerm nominalLeft = leftIsInt && !rightIsInt ? Smt.app("to_real", leftValueSym) : leftValueSym;
      SmtTerm nominalRight = rightIsInt && !leftIsInt ? Smt.app("to_real", rightValueSym) : rightValueSym;
      return defined(Smt.app(comparison.opname(), nominalLeft, nominalRight));
    }
    double theta = confidence.doubleValue();
    List<SmtTerm> satisfying = new ArrayList<>();
    for (BigDecimal v1 : leftValueCandidates) {
      for (BigDecimal u1 : leftUncertaintyCandidates) {
        for (BigDecimal v2 : rightValueCandidates) {
          for (BigDecimal u2 : rightUncertaintyCandidates) {
            double probability =
                useRealComparisonProbability(comparison.opname(), v1, u1, v2, u2);
            if (probability >= theta) {
              satisfying.add(
                  Smt.and(
                      List.of(
                          Smt.eq(leftValueSym, repLiteral(v1, leftIsInt)),
                          Smt.eq(leftUncSym, repLiteral(u1, leftIsInt)),
                          Smt.eq(rightValueSym, repLiteral(v2, rightIsInt)),
                          Smt.eq(rightUncSym, repLiteral(u2, rightIsInt)))));
            }
          }
        }
      }
    }
    return defined(Smt.or(satisfying));
  }

  /** The selection literal for a representative candidate: Int-sorted for UInteger, Real otherwise. */
  private static SmtTerm repLiteral(BigDecimal candidate, boolean integerSorted) {
    return integerSorted
        ? Smt.intLit(candidate.toBigIntegerExact())
        : Smt.realLit(candidate);
  }

  /**
   * The comparison probability of one compile-time candidate pair, obtained from USE'S OWN
   * evaluator ({@code URealValue.lt/gt/le/ge} over {@code UReal}s built through the same String
   * constructor the configuration parse feeds) -- bit-exact against USE by construction,
   * including the crossing-point model's every degenerate branch.
   */
  private static double useRealComparisonProbability(
      String opname, BigDecimal v1, BigDecimal u1, BigDecimal v2, BigDecimal u2) {
    org.tzi.use.uml.ocl.value.URealValue left =
        new org.tzi.use.uml.ocl.value.URealValue(
            new org.tzi.use.uncertainty.datatypes.UReal(v1.toPlainString(), u1.toPlainString()));
    org.tzi.use.uml.ocl.value.URealValue right =
        new org.tzi.use.uml.ocl.value.URealValue(
            new org.tzi.use.uncertainty.datatypes.UReal(v2.toPlainString(), u2.toPlainString()));
    return switch (opname) {
      case "<" -> left.lt(right).probability();
      case ">" -> left.gt(right).probability();
      case "<=" -> left.le(right).probability();
      case ">=" -> left.ge(right).probability();
      default -> throw new IllegalStateException("not an ordered comparison: " + opname);
    };
  }

  /** The parsed candidates of a finite enumerated domain, or null when it is not one. */
  private static List<BigDecimal> enumeratedDecimals(AttributeDomain domain) {
    if (domain == null || domain.enumeratedValues().isEmpty()) {
      return null;
    }
    List<BigDecimal> values = new ArrayList<>();
    for (String candidate : domain.enumeratedValues()) {
      try {
        values.add(new BigDecimal(candidate.trim()));
      } catch (NumberFormatException notNumeric) {
        return null;
      }
    }
    return values;
  }

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
    // NAVIGATED FOLDED-USTRING SIZE EQUALITY: `(x.gauge.tag.size() = n).toBooleanC(conf)`.
    // The size of a UString over a (folded) end view enumerates per destination slot and per
    // that slot's concrete class's configured spelling lengths, each guarded by the slot's link
    // term; the equality against the literal keeps the matching cases. The projection over the
    // crisp equality UBoolean is the equality itself (a crisp true/false UBoolean has p = 1/0,
    // so any in-range confidence threshold yields the same answer).
    if (operand instanceof ExpStdOp eqOp
        && ("=".equals(eqOp.opname()) || "<>".equals(eqOp.opname()))
        && eqOp.args().length == 2
        && isNavigatedUStringSize(eqOp.args()[0])
        && eqOp.args()[1] instanceof ExpConstInteger demanded) {
      return navigatedUStringSizeEquality(
          eqOp, (ExpStdOp) eqOp.args()[0], demanded.value(), "=".equals(eqOp.opname()));
    }
    if (operand instanceof ExpStdOp eqOp
        && ("=".equals(eqOp.opname()) || "<>".equals(eqOp.opname()))
        && eqOp.args().length == 2
        && isNavigatedUStringSize(eqOp.args()[1])
        && eqOp.args()[0] instanceof ExpConstInteger demanded) {
      return navigatedUStringSizeEquality(
          eqOp, (ExpStdOp) eqOp.args()[1], demanded.value(), "=".equals(eqOp.opname()));
    }
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
    // U-type let variables resolve through their LetBindingSource -- the bare attribute the
    // let initialized from plus its configured candidate domains -- so the case enumeration
    // below treats the alias exactly like the attribute.
    java.util.function.Function<Expression, LetBindingSource> letResolver =
        expr -> {
          if (expr instanceof ExpVariable v) {
            LocalBinding local = localBindings.get(v.getVarname());
            return local != null ? local.letSource() : null;
          }
          return null;
        };
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
            UBooleanProbability.select(
                UBooleanProbability.lowerNominal(operand, context, letResolver), 0.5));
      }
      if (!(operand instanceof ExpAttrOp) && letResolver.apply(operand) == null) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "nominal erasure of the composed UBoolean expression '"
                + operand
                + "': the proposal's erasure table defines E_B only for a STORED UBoolean"
                + " probability (p >= 0.5) and for a comparison erased to its crisp form, and"
                + " NominalErasureEvaluator refuses the rest");
      }
      return defined(
          UBooleanProbability.select(
              UBooleanProbability.lower(operand, context, letResolver), 0.5));
    }
    return defined(
        UBooleanProbability.select(
            UBooleanProbability.lower(operand, context, letResolver), confidence.doubleValue()));
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
    // Cast identity: {@code v.oclAsType(T) = w} (either side cast, over bare context
    // variables). A defined cast denotes the SAME object as its source, so the identity is
    // the compile-time binding equality; the result's definedness is both sides' --
    // slot-exists, plus the cast's own conformance gate on the cast side.
    {
      VariableBinding lb = castOrVariableBinding(l);
      VariableBinding rb = castOrVariableBinding(r);
      if (lb != null && rb != null) {
        return new TranslatedExpression(
            Smt.and(List.of(castOrVariableDefinedness(l, lb), castOrVariableDefinedness(r, rb))),
            Smt.bool(lb.equals(rb)));
      }
    }
    if ((l instanceof ExpVariable cl && localBindings.containsKey(cl.getVarname()))
        || (r instanceof ExpVariable cr && localBindings.containsKey(cr.getVarname()))) {
      TranslatedExpression local = contentAwareEquality(l, r);
      if (local != null) return local;
    }
    // STANDALONE SET EQUALITY: both sides constant-content SET-kind collections -> compile-time
    // set equality (order-insensitive, duplicate-collapsed per Set semantics). Deliberately
    // SetType ONLY: Bag/Sequence content keeps duplicates, which sameElements' deduplicated
    // comparison would silently drop -- Bag{1,1,2} = Bag{1,2,2} must REFUSE (fail-closed), not
    // compare equal. OrderedSet is not a SetType subtype in USE's lattice and is likewise
    // refused here (it could be admitted later -- it collapses duplicates -- but refused is the
    // conservative reading).
    // NOTE: isTypeOfCollection() is FALSE for Set/Bag/Sequence in USE's lattice (it names the
    // abstract Collection type exactly -- the same trap the let dispatch documented).
    if (l.type() instanceof org.tzi.use.uml.ocl.type.SetType
        && r.type() instanceof org.tzi.use.uml.ocl.type.SetType) {
      SetContent lc = constantCollectionContent(l);
      SetContent rc = constantCollectionContent(r);
      if (lc != null && rc != null) {
        return defined(Smt.bool(sameElements(lc, rc)));
      }
    }
    if (isVirtualStringOp(l) || isVirtualStringOp(r)) {
      return virtualStringComparison(l, r);
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
    // A Real-typed side (a `/` result, a Real literal or Real attribute) compared against an
    // Integer-typed side is a SORT mix in the emitted text: lift the Integer side with
    // to_real so the script stays sort-correct under strict SMT-LIB (the UInteger to_real
    // portability precedent), instead of relying on a solver's automatic coercion.
    boolean integerLeftRealRight =
        l.type().isTypeOfInteger() && r.type().isTypeOfReal();
    boolean realLeftIntegerRight =
        l.type().isTypeOfReal() && r.type().isTypeOfInteger();
    if (integerLeftRealRight || realLeftIntegerRight) {
      SmtTerm valuesEqual =
          Smt.eq(maybeToReal(l), maybeToReal(r));
      return useEquality(argResult(l), argResult(r), valuesEqual);
    }

    // toString()'s identity encoding yields the Integer VALUE, so any comparison against a
    // String/Enum-ENCODED operand (a domain INDEX) would silently equate unrelated things --
    // e.g. `x.s = x.a.toString()` with s's domain {'7','42'} and a = 2 would hold because the
    // literal at index 2... does not even exist. Only value-encoded operands (Integer-typed)
    // and String literals (resolved by parsing, above) are sound here.
    Expression toStringSide =
        isIntegerToString(l) ? l : isIntegerToString(r) ? r : null;
    if (toStringSide != null) {
      Expression other = toStringSide == l ? r : l;
      if (other instanceof ExpConstEnum
          || other.type().isTypeOfString()
          || other.type().isTypeOfEnum()) {
        throw unsupported(
            FragmentBoundary.TIER_2,
            "toString() over an Integer compared against a String- or Enum-encoded operand:"
                + " the value-identity encoding only supports String literals and Integer"
                + " operands");
      }
    }
    return useEquality(argResult(l), argResult(r));
  }

  /**
   * Exactly the shape visitStdOp's toString case encodes as the operand's own Integer value:
   * a unary {@code toString()} over a crisp Integer. The predicate the guards on every
   * non-literal consumer of that encoding dispatch on.
   */
  private static boolean isIntegerToString(Expression e) {
    return e instanceof ExpStdOp op
        && "toString".equals(op.opname())
        && op.args().length == 1
        && op.args()[0].type().isTypeOfInteger();
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
    if (e instanceof ExpAsType cast
        && cast.getSourceExpr() instanceof ExpVariable srcVar
        && !localBindings.containsKey(srcVar.getVarname())) {
      // A cast is defined iff its object exists AND the runtime class conforms to the target
      // (ExpAsType#eval); per variant the conformance is a compile-time fact.
      VariableBinding b = context.binding(srcVar.getVarname());
      SmtTerm exists = Smt.sym(context.slotsFor(b.className()).existsNames().get(b.slotIndex()));
      if (bindingConformsTo(b, (org.tzi.use.uml.mm.MClassifier) cast.type())) {
        return exists;
      }
      return Smt.bool(false);
    }
    return argResult(e).defined();
  }

  /**
   * The context binding {@code e} denotes when it is a bare context variable or a cast of
   * one ({@code v}, {@code v.oclAsType(T)}), or null for anything else -- the identity
   * comparison's own resolution, shared by both sides.
   */
  private VariableBinding castOrVariableBinding(Expression e) {
    Expression source = e;
    if (e instanceof ExpAsType cast) {
      if (!(cast.getSourceExpr() instanceof ExpVariable variable)
          || localBindings.containsKey(variable.getVarname())) {
        return null;
      }
      source = variable;
    }
    if (source instanceof ExpVariable variable && !localBindings.containsKey(variable.getVarname())) {
      return context.binding(variable.getVarname());
    }
    return null;
  }

  /**
   * The definedness of one identity-comparison side already resolved by {@link
   * #castOrVariableBinding}: a bare variable's slot-exists symbol; a cast additionally gated
   * by its compile-time conformance ({@code ExpAsType#eval}'s undefined-on-nonconformance).
   */
  private SmtTerm castOrVariableDefinedness(Expression e, VariableBinding binding) {
    SmtTerm exists =
        Smt.sym(context.slotsFor(binding.className()).existsNames().get(binding.slotIndex()));
    if (e instanceof ExpAsType cast
        && bindingConformsTo(binding, (org.tzi.use.uml.mm.MClassifier) cast.type())) {
      return exists;
    }
    if (e instanceof ExpAsType) {
      return Smt.bool(false);
    }
    return exists;
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
    // The pointer indexes the FOLDED end view, so the source's own-class slot index is the
    // wrong identity to compare against when the view folds subclasses: resolve the source
    // binding's index WITHIN the folded view (its entry is there by construction -- the source
    // binds one of the view's slots). Contexts without registered views keep the single-class
    // own-index comparison.
    int sourceFoldedIndex = foldedIndexOfSourceInEndView(associationClass, sourceEnd, source);
    List<SmtTerm> matches = new ArrayList<>();
    for (int k = 0; k < associationClassSlots.capacity(); k++) {
      SmtTerm exists = Smt.sym(associationClassSlots.existsNames().get(k));
      SmtTerm pointsToSource =
          Smt.eq(
              Smt.sym(pointer.valueNames().get(k)),
              Smt.intLit(BigInteger.valueOf(sourceFoldedIndex)));
      matches.add(Smt.and(List.of(exists, pointsToSource)));
    }
    return Smt.or(matches);
  }

  /**
   * The source slot's index within the association class's FOLDED view of {@code sourceEnd}, or
   * {@code source.slotIndex()} when no folded view was registered (the single-class fallback).
   * Fails closed when a view IS registered but the source binding is not one of its slots --
   * that would mean the source class is not part of the end's population at all, and comparing
   * against any index would silently fabricate a link identity.
   */
  private int foldedIndexOfSourceInEndView(
      MAssociationClass associationClass, MNavigableElement sourceEnd, VariableBinding source) {
    List<ObjectSlots> views = context.assocClassEndViews(associationClass.name());
    if (views == null) {
      return source.slotIndex();
    }
    boolean sourceIsEnd0 = sourceEnd.equals(sourceEnd.association().associationEnds().get(0));
    ObjectSlots view = sourceIsEnd0 ? views.get(0) : views.get(1);
    int index = view.concreteBindings().indexOf(source);
    if (index < 0) {
      throw unsupported(
          FragmentBoundary.ENCODING_SCOPE,
          "association-class pointer lookup: source class "
              + source.className()
              + " is not part of the folded end view of "
              + view.className());
    }
    return index;
  }

  /**
   * The registered folded end view for one end of an association class, or null when none was
   * registered (hand-built translation contexts keep the single-class behavior).
   */
  private ObjectSlots assocClassEndViewOrNull(MAssociationClass assocClass, MNavigableElement end) {
    List<ObjectSlots> views = context.assocClassEndViews(assocClass.name());
    if (views == null) {
      return null;
    }
    boolean endIsEnd0 = end.equals(end.association().associationEnds().get(0));
    return endIsEnd0 ? views.get(0) : views.get(1);
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
    // `x.a.toString() = '42'`: the operand's value term IS the Integer (see visitStdOp's
    // toString case), and the decimal representation is injective, so the literal resolves
    // by parsing back to that Integer -- the comparison degenerates to `a = 42`. A literal
    // that is not a decimal integer can never be any Integer's decimal string; refusing it
    // (rather than emitting a never-equal term) keeps the slice's promise exact, since no
    // domain-independent out-of-range sentinel exists for an arbitrary configured domain.
    if (other instanceof ExpStdOp op
        && "toString".equals(op.opname())
        && op.args().length == 1
        && op.args()[0].type().isTypeOfInteger()) {
      BigInteger parsed;
      try {
        parsed = new BigInteger(literal.value());
      } catch (NumberFormatException e) {
        throw unsupported(
            FragmentBoundary.TIER_2,
            "string literal '"
                + literal.value()
                + "' that is not a decimal integer compared against toString() of an Integer");
      }
      return Smt.intLit(parsed);
    }
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
    // Crisp Real literals: exact SMT-LIB decimals. USE evaluates Reals as doubles, so a
    // literal a witness is compared against must be exactly representable in both -- halves
    // and quarters are, arbitrary decimals are not (the UReal threshold slices already
    // document that gap and enclose it; plain equality here simply trusts the decimal).
    result = defined(Smt.realLit(BigDecimal.valueOf(e.value())));
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
    SetContent rangeContentE =
        (variableCount == 1) ? constantCollectionContent(e.getRangeExpression()) : null;
    if (rangeContentE != null) {
      result = setQuantifierOver(rangeContentE.integers(), rangeContentE.strings(),
          e.getVariableDeclarations().varDecl(0).name(), e.getQueryExpression(), false);
      return;
    }
    SetAttrView existsSetAttr =
        (variableCount == 1) ? setAttrRead(e.getRangeExpression()) : null;
    if (existsSetAttr != null) {
      result = setAttrQuantifier(existsSetAttr,
          e.getVariableDeclarations().varDecl(0).name(), e.getQueryExpression(), false);
      return;
    }
    if (variableCount == 1) {
      SetContent letSet = localCollection(e.getRangeExpression());
      if (letSet != null) {
        result = setQuantifierOver(letSet.integers(), letSet.strings(),
            e.getVariableDeclarations().varDecl(0).name(), e.getQueryExpression(), false);
        return;
      }
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
    SetContent rangeContent =
        (variableCount == 1) ? constantCollectionContent(e.getRangeExpression()) : null;
    if (rangeContent != null) {
      result = setQuantifierOver(rangeContent.integers(), rangeContent.strings(),
          e.getVariableDeclarations().varDecl(0).name(), e.getQueryExpression(), true);
      return;
    }
    SetAttrView forAllSetAttr =
        (variableCount == 1) ? setAttrRead(e.getRangeExpression()) : null;
    if (forAllSetAttr != null) {
      result = setAttrQuantifier(forAllSetAttr,
          e.getVariableDeclarations().varDecl(0).name(), e.getQueryExpression(), true);
      return;
    }
    if (variableCount == 1) {
      SetContent letSet = localCollection(e.getRangeExpression());
      if (letSet != null) {
        result = setQuantifierOver(letSet.integers(), letSet.strings(),
            e.getVariableDeclarations().varDecl(0).name(), e.getQueryExpression(), true);
        return;
      }
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
      ExpCollectionLiteral set, String iterator, Expression bodyExpr, boolean forAll) {
    // Elements: Integer constants and constant-bounds ranges bind the loop variable to their
    // literal VALUE; String constants bind it to a singleton CONTENT candidate (stringOrEnum
    // LocalBinding whose candidate list is the literal), so body comparisons resolve by
    // content. Mixing kinds in one literal is refused.
    SetContent content = collectionLiteralContent(set);
    return setQuantifierOver(content.integers(), content.strings(), iterator, bodyExpr, forAll);
  }

  /**
   * The per-element quantifier emission shared by the direct set-literal ranges and the
   * let-bound set variables: each Integer element (or String content candidate) binds the loop
   * variable through a per-element SMT let, exactly as {@code setLiteralQuantifier} has always
   * done. {@code intValues} carries the distinct Integer elements, {@code stringValues} the
   * distinct String contents; exactly one is non-null.
   */
  private TranslatedExpression setQuantifierOver(
      List<BigInteger> intValues,
      List<String> stringValues,
      String iterator,
      Expression bodyExpr,
      boolean forAll) {
    // Callers carrying only one element kind pass null for the other (the SetContent record's
    // convention); the loops below want plain empty lists.
    if (intValues == null) intValues = List.of();
    if (stringValues == null) stringValues = List.of();
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

  /**
   * A single constant-bounds range literal ({@code Set{lo..hi}}) may expand to at most this
   * many elements. The expansion is compile-time, so this caps translation-time blowup (and
   * the term size handed to the solver), not a semantic boundary -- a model wanting more
   * elements than the cap must write them out or narrow the bounds.
   */
  private static final int MAX_RANGE_EXPANSION = 4096;

  /**
   * Appends the Integer value(s) of one set-literal ELEMENT to {@code out} -- an Integer
   * constant contributes its value; a constant-bounds range {@code lo..hi} contributes every
   * integer in the closed interval (an inverted interval {@code hi < lo} contributes none).
   * Values already present are skipped, so the caller's list stays the literal's DISTINCT
   * element set (Set semantics). Non-constant bounds refuse: QF_LIA has no scalar
   * quantifier-variable binding for a range end, and no real corpus shape needs one.
   */
  private static void appendIntegerElementValues(Expression element, List<BigInteger> out) {
    appendIntegerElementValues(element, out, true);
  }

  /**
   * The duplicate policy: Set semantics collapse ({@code dedupe}), Bag/Sequence semantics keep
   * every occurrence ({@code !dedupe}) so size() counts them.
   */
  private static void appendIntegerElementValues(
      Expression element, List<BigInteger> out, boolean dedupe) {
    if (element instanceof ExpConstInteger constant) {
      BigInteger value = BigInteger.valueOf(constant.value());
      if (!dedupe || !out.contains(value)) {
        out.add(value);
      }
      return;
    }
    if (element instanceof ExpRange range) {
      Expression lo = range.getStart();
      Expression hi = range.getEnd();
      if (!(lo instanceof ExpConstInteger loInt)
          || hi == null
          || !(hi instanceof ExpConstInteger hiInt)) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "range literal with non-constant bounds ("
                + lo + ".." + hi + ") is not yet supported");
      }
      long span = (long) hiInt.value() - (long) loInt.value() + 1;
      if (span <= 0) {
        return;
      }
      if (span > MAX_RANGE_EXPANSION) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "range literal " + loInt.value() + ".." + hiInt.value() + " expands to "
                + span + " elements, above the " + MAX_RANGE_EXPANSION
                + "-element expansion cap; narrow the bounds");
      }
      for (long cur = loInt.value(); cur <= hiInt.value(); cur++) {
        BigInteger v = BigInteger.valueOf(cur);
        if (!dedupe || !out.contains(v)) {
          out.add(v);
        }
      }
      return;
    }
    throw unsupported(
        FragmentBoundary.TIER_3,
        "Set literal with a non-constant or non-Integer/non-String element ("
            + element
            + ")");
  }

  /**
   * {@code Set{c1,...,cn}->one(k | body)} over an Integer-constant set literal: EXACTLY ONE
   * element satisfies the body. Each literal binds the loop variable as a singleton-content
   * candidate (per setLiteralQuantifier), the body translates once per distinct element, and
   * the element-truth indicators sum to one. Sound for duplicated literals (collapsed before
   * this point).
   */
  private TranslatedExpression oneOverIntegerSetLiteral(ExpOne e, ExpSetLiteral set) {
    String loopVariable = e.getVariableDeclarations().varDecl(0).name();
    List<BigInteger> values = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      appendIntegerElementValues(element, values);
    }
    SmtTerm zero = Smt.intLit(BigInteger.ZERO);
    SmtTerm oneCount = Smt.intLit(BigInteger.ONE);
    List<SmtTerm> definedTerms = new ArrayList<>();
    SmtTerm count = zero;
    for (BigInteger element : values) {
      String stem = "|ocl-set-" + loopVariable + "-" + element;
      LocalBinding binding =
          new LocalBinding(stem + "-defined|", stem + "-value|", true, List.of(element.toString()));
      Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
      extended.put(loopVariable, binding);
      TranslatedExpression body =
          translate(
              e.getQueryExpression(), context, mode, positivePolarity, Map.copyOf(extended));
      List<SmtTerm.Binding> bindings =
          List.of(
              new SmtTerm.Binding(binding.definedSymbol(), Smt.bool(true)),
              new SmtTerm.Binding(binding.valueSymbol(), Smt.intLit(element)));
      definedTerms.add(Smt.let(bindings, body.defined()));
      SmtTerm truth = Smt.let(bindings, body.trueTerm());
      count = Smt.app("+", count, Smt.ite(truth, oneCount, zero));
    }
    SmtTerm allDefined = Smt.and(definedTerms);
    return new TranslatedExpression(
        Smt.or(List.of(Smt.eq(count, oneCount), Smt.not(allDefined))),
        Smt.eq(count, oneCount));
  }

  /**
   * Java-truncation division of x by a constant nonzero divisor d: the quotient's sign
   * follows the product of the operand signs, magnitude = |x| / |d| truncated. x's sign
   * selects the positive or negated positive quotient (the divisor's own sign cancels
   * because the magnitude is the same either way).
   */
  private static SmtTerm truncDivTerm(SmtTerm x, int d) {
    int absD = Math.abs(d);
    SmtTerm absDLit = Smt.intLit(BigInteger.valueOf(absD));
    SmtTerm posDiv = Smt.app("div", x, absDLit);
    SmtTerm negDiv = Smt.app("-", Smt.app("div", Smt.app("-", x), absDLit));
    return Smt.ite(
        Smt.app(">=", x, Smt.intLit(BigInteger.ZERO)),
        posDiv,
        negDiv);
  }

  /**
   * Java-truncation remainder of x by a constant nonzero divisor d: sign follows the
   * dividend, magnitude = |x| mod |d|. Both ite branches use the absD magnitude so a
   * negative divisor works identically (magnitude unaffected by the divisor's sign).
   */
  private static SmtTerm truncModTerm(SmtTerm x, int d) {
    int absD = Math.abs(d);
    SmtTerm posMod = Smt.app("mod", x, Smt.intLit(BigInteger.valueOf(absD)));
    SmtTerm negMod = Smt.app("-", Smt.app("mod", Smt.app("-", x), Smt.intLit(BigInteger.valueOf(absD))));
    return Smt.ite(
        Smt.app(">=", x, Smt.intLit(BigInteger.ZERO)),
        posMod,
        negMod);
  }

  /**
   * {@code Set{'a','b'}->one(n | body)} over a String-constant set literal: exactly one
   * literal element satisfies the body. Each literal binds the loop variable as a
   * singleton-content candidate via the per-element SMT let, and the element-truth
   * indicators sum under the let with the constraint count = 1.
   */
  private TranslatedExpression oneOverStringSetLiteral(ExpOne e, ExpSetLiteral set) {
    String loopVariable = e.getVariableDeclarations().varDecl(0).name();
    List<String> contents = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      if (element instanceof ExpConstString cs) {
        if (!contents.contains(cs.value())) {
          contents.add(cs.value());
        }
      } else {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "one() over a set literal with a non-String-constant element");
      }
    }
    SmtTerm zero = Smt.intLit(BigInteger.ZERO);
    SmtTerm one = Smt.intLit(BigInteger.ONE);
    List<SmtTerm> definedTerms = new ArrayList<>();
    SmtTerm count = zero;
    for (String content : contents) {
      String stem = "|ocl-set-" + loopVariable + "-" + content;
      LocalBinding binding =
          new LocalBinding(stem + "-defined|", stem + "-value|", true, List.of(content));
      Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
      extended.put(loopVariable, binding);
      TranslatedExpression body =
          translate(e.getQueryExpression(), context, mode, positivePolarity, Map.copyOf(extended));
      List<SmtTerm.Binding> bindings =
          List.of(
              new SmtTerm.Binding(binding.definedSymbol(), Smt.bool(true)),
              new SmtTerm.Binding(binding.valueSymbol(), Smt.intLit(BigInteger.ZERO)));
      definedTerms.add(Smt.let(bindings, body.defined()));
      SmtTerm truth = Smt.let(bindings, body.trueTerm());
      count = Smt.app("+", count, Smt.ite(truth, one, zero));
    }
    SmtTerm allDefined = Smt.and(definedTerms);
    return new TranslatedExpression(
        Smt.or(List.of(Smt.eq(count, one), Smt.not(allDefined))),
        Smt.eq(count, one));
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
    // NON-CLASS targets (Integer/Real/String/UInteger/...): USE's ExpIsTypeOf compares the
    // value's runtime type EXACTLY (equals) and ExpIsKindOf uses conformsTo -- both are total
    // over the value's DECLARED type, which for a crisp scalar attribute is the compile-time
    // declared attribute type. Delegate to USE's own Type.equals/conformsTo rather than a
    // hand-maintained conformance matrix: the lattice facts are surprising (Integer
    // conformsTo Real AND UInteger/UReal via isKindOfNumber; Real does NOT conformTo
    // Integer), and delegating keeps the encoding bit-exact against the evaluator by
    // construction.
    if (!targetType.isTypeOfClass()) {
      if (sourceExpr instanceof ExpAttrOp attr
          && attr.objExp() instanceof ExpVariable v
          && !localBindings.containsKey(v.getVarname())) {
        org.tzi.use.uml.ocl.type.Type runtime = attr.type();
        if (runtime.isTypeOfUReal() || runtime.isTypeOfUInteger()
            || runtime.isTypeOfUBoolean() || runtime.isTypeOfUString()) {
          throw unsupported(
              FragmentBoundary.UTYPE_CORE,
              "type test of an uncertain attribute (" + runtime + "): the runtime type of a"
                  + " projected uncertain value is not modeled in this slice");
        }
        boolean result = includeSubtypes
            ? runtime.conformsTo(targetType)
            : runtime.equals(targetType);
        // Type tests are TOTAL (an undefined source tests FALSE, never undefined).
        return defined(Smt.bool(result));
      }
      if (sourceExpr instanceof ExpVariable v && !localBindings.containsKey(v.getVarname())) {
        // A context OBJECT variable: its runtime type is its class, which neither equals nor
        // conforms to a basic type -- compile-time false, both flavors.
        boolean result = includeSubtypes
            ? v.type().conformsTo(targetType)
            : v.type().equals(targetType);
        return defined(Smt.bool(result));
      }
      throw unsupported(
          FragmentBoundary.TIER_2,
          "isTypeOf/isKindOf against a non-class target type " + targetType
              + " with a source that is neither a scalar attribute nor a variable");
    }
    org.tzi.use.uml.mm.MClassifier targetClass = (org.tzi.use.uml.mm.MClassifier) targetType;
    // A cast source ({@code v.oclAsType(Truck).oclIsKindOf(Truck)}): the type test stays
    // TOTAL -- ExpIsKindOf/ExpIsTypeOf eval read the runtime type off the cast's RESULT, and
    // an undefined cast's type test is FALSE, never undefined. Per translation variant the
    // source's concrete class is fixed, so "cast defined AND runtime class matches" is a
    // compile-time fact.
    if (sourceExpr instanceof ExpAsType cast
        && cast.getSourceExpr() instanceof ExpVariable srcVar
        && !localBindings.containsKey(srcVar.getVarname())) {
      VariableBinding source = context.binding(srcVar.getVarname());
      boolean castDefined = bindingConformsTo(source, (org.tzi.use.uml.mm.MClassifier) cast.type());
      boolean runtimeMatches =
          source.className().equals(targetClass.name())
              || (includeSubtypes
                  && targetClass.allChildren().stream()
                      .anyMatch(child -> child.name().equals(source.className())));
      return defined(Smt.bool(castDefined && runtimeMatches));
    }
    // NAVIGATED source ({@code x.part.oclIsKindOf(B)}): the navigation's destination end view
    // folds the configured subclasses, and each destination slot's concrete class is a
    // translation-time fact -- so the test is the disjunction over slots of (link term AND
    // that slot's concrete class match). Type tests stay TOTAL: an unlinked navigation has no
    // matching slot and contributes FALSE, never undefinedness (ExpIsKindOf#eval's
    // undefined-tests-FALSE rule).
    if (sourceExpr instanceof ExpNavigation navigation
        && !navigation.getDestination().isCollection()
        && navigation.getObjectExpression() instanceof ExpVariable navSource
        && !localBindings.containsKey(navSource.getVarname())) {
      VariableBinding source = context.binding(navSource.getVarname());
      MNavigableElement destination =
          resolveRedefinedDestination(navigation.getDestination(), source);
      AssociationLinks links = context.linksFor(destination.association().name());
      ObjectSlots destSlots = destinationEndView(links, destination);
      List<SmtTerm> matchingSlots = new ArrayList<>();
      for (int k = 0; k < destSlots.capacity(); k++) {
        VariableBinding concrete = destSlots.concreteBindings().get(k);
        boolean matches =
            concrete.className().equals(targetClass.name())
                || (includeSubtypes
                    && targetClass.allChildren().stream()
                        .anyMatch(child -> child.name().equals(concrete.className())));
        if (matches) {
          matchingSlots.add(linkTerm(links, destination, source, k));
        }
      }
      return defined(Smt.or(matchingSlots));
    }
    VariableBinding binding = context.binding(variableNameOf(sourceExpr));
    boolean matches =
        binding.className().equals(targetClass.name())
            || (includeSubtypes
                && targetClass.allChildren().stream()
                    .anyMatch(child -> child.name().equals(binding.className())));
    return defined(Smt.bool(matches));
  }

  /**
   * The compile-time conformance of a binding's CONCRETE class to a target classifier: the
   * same class, or a (transitive) descendant -- exactly the runtime check {@code ExpAsType#eval}
   * performs ({@code obj.cls().conformsTo(targetType)}) before it hands the object back.
   */
  private static boolean bindingConformsTo(
      VariableBinding binding, org.tzi.use.uml.mm.MClassifier target) {
    return binding.className().equals(target.name())
        || target.allChildren().stream().anyMatch(child -> child.name().equals(binding.className()));
  }

  /**
   * Attribute access through a cast of a bare context variable ({@code
   * v.oclAsType(Truck).payloadCapacity}) -- the guarded-downcast idiom superclass-context
   * invariants use to reach subclass attributes. Per translation variant the variable's
   * concrete class is FIXED, so {@code ExpAsType#eval}'s runtime conformance check is a
   * compile-time fact: conforming, the cast is defined (given the slot exists -- the
   * quantifier guards already establish that) and the attribute is the plain per-slot read
   * (inherited attributes are registered per concrete subclass); non-conforming, the cast is
   * UNDEFINED and this variant is the constant-undefined expression -- the target attribute's
   * registration is never even looked up (it may not exist on this class, and inventing a
   * value would be worse than refusing).
   */
  private TranslatedExpression castReceiverAttribute(
      ExpAsType cast, String sourceVariableName, MAttribute attribute) {
    VariableBinding b = context.binding(sourceVariableName);
    if (!bindingConformsTo(b, (org.tzi.use.uml.mm.MClassifier) cast.type())) {
      return new TranslatedExpression(Smt.bool(false), crispPlaceholder(attribute.type()));
    }
    AttributeValues v = context.attributeValues(b.className(), attribute.name());
    guardAgainstUncertainAttribute(v);
    return defined(Smt.sym(v.valueNames().get(b.slotIndex())));
  }

  /**
   * Attribute access through a cast of a SINGLE-VALUED NAVIGATION ({@code
   * x.vehicle.oclAsType(Truck).payload}): the guarded downcast one hop out. Per destination
   * slot, the concrete class is a translation-time fact, so ExpAsType#eval's conformance check
   * filters slots: a conforming slot contributes its link-guarded attribute symbol; a
   * non-conforming slot contributes NOTHING (the cast is undefined through it), and its
   * attribute is never looked up. Definedness is the OR of the contributing slots' links -- an
   * unlinked navigation or a linked non-conforming slot leaves the read undefined, exactly the
   * sequential-evaluation semantics.
   */
  private TranslatedExpression navigatedCastAttribute(
      ExpAsType cast, ExpNavigation navigation, String sourceVariableName, MAttribute attribute) {
    VariableBinding source = context.binding(sourceVariableName);
    MNavigableElement destination =
        resolveRedefinedDestination(navigation.getDestination(), source);
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    org.tzi.use.uml.mm.MClassifier castTarget =
        (org.tzi.use.uml.mm.MClassifier) cast.type();
    // The VALUE nests as ite selections (the selectLinkedValue shape -- and() over an Int
    // symbol is a sort error); the DEFINEDNESS is the OR of the contributing slots' links.
    List<SmtTerm> contributingLinks = new ArrayList<>();
    List<SmtTerm> contributingValues = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      VariableBinding concrete = destSlots.concreteBindings().get(k);
      if (!bindingConformsTo(concrete, castTarget)) {
        continue;
      }
      AttributeValues v = context.attributeValues(concrete.className(), attribute.name());
      guardAgainstUncertainAttribute(v);
      SmtTerm link = linkTerm(links, destination, source, k);
      contributingLinks.add(link);
      contributingValues.add(Smt.sym(v.valueNames().get(concrete.slotIndex())));
    }
    if (contributingLinks.isEmpty()) {
      // No configured slot can ever conform: the cast is undefined for every instance.
      return new TranslatedExpression(Smt.bool(false), crispPlaceholder(attribute.type()));
    }
    SmtTerm value = contributingValues.get(contributingValues.size() - 1);
    for (int i = contributingLinks.size() - 2; i >= 0; i--) {
      value = Smt.ite(contributingLinks.get(i), contributingValues.get(i), value);
    }
    return new TranslatedExpression(Smt.or(contributingLinks), value);
  }

  /** A well-sorted, never-consulted filler for a use-core attribute {@link Type}. */
  private static SmtTerm crispPlaceholder(org.tzi.use.uml.ocl.type.Type type) {
    return type.isTypeOfString()
        ? Smt.intLit(BigInteger.ZERO)
        : type.isTypeOfReal()
            ? Smt.realLit(BigDecimal.ZERO)
            : type.isTypeOfBoolean()
                ? Smt.bool(false)
                : Smt.intLit(BigInteger.ZERO);
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
    if (range instanceof ExpNavigation navigation && isCollectionValuedNavigation(navigation)) {
      if (navigation.getObjectExpression() instanceof ExpVariable sourceVar) {
        VariableBinding source = context.binding(sourceVar.getVarname());
        MNavigableElement destination =
            resolveRedefinedDestination(navigation.getDestination(), source);
        // UNION-DERIVED NAVIGATION: a union-declared end's content is USE-core-derived --
        // the live union of every `subsets`-declaring association's links -- so the population
        // is the union of the subsetting populations, never an own grid (which would let the
        // solver choose content freely, reproducing the incumbent's documented defect).
        if (destination.isUnion()) {
          return unionNavigationPopulation(destination, source);
        }
        NaryAssociationLinks naryLinks = context.naryLinks(destination.association().name());
        if (naryLinks != null) {
          return naryNavigationPopulation(naryLinks, destination, source, construct);
        }
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
   * True when {@code navigation} denotes a collection: the usual single-valued/collection-valued
   * role-multiplicity test, OR the n-ary case -- an end of an N-ARY association (arity &ge; 3)
   * projects a collection even when the end's own multiplicity is single-valued (the navigation
   * ranges over the remaining tuple positions), so the static TYPE is the arbiter there.
   *
   * <p>CORRECTED 2026-08-31 against USE's own {@code MAssociationEnd.getType}: an n-ary end
   * navigation is typed SET, not Bag (the Bag/Sequence branches require qualifiers; both the
   * unqualified collection-multiplicity branch and the n-ary single-valued branch call
   * {@code TypeFactory.mkSet}). The earlier "n-ary navigations are Bags" note -- inherited from
   * a probe comment -- was a misreading; the witness checker caught it the same turn (an SMT
   * bag-count of duplicate tuples failed USE's independent re-evaluation, which counted the
   * distinct Set). The deduplicated per-slot population is therefore EXACT, and every
   * count-based construct is sound over it.
   */
  private static boolean isCollectionValuedNavigation(ExpNavigation navigation) {
    return navigation.getDestination().isCollection()
        || navigation.type().isKindOfCollection(
            org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID);
  }

  /**
   * The population consumers an N-ARY navigation may feed. CORRECTED 2026-08-31: USE types an
   * n-ary end navigation as a SET ({@code MAssociationEnd.getType} -- Bag/Sequence need
   * qualifiers, everything unqualified lands on {@code mkSet}), so the deduplicated per-slot
   * population is EXACT and every construct the binary navigation path serves is sound here
   * too: forAll/exists/isEmpty/notEmpty/size()/isUnique/one()/includesAll. Only constructs
   * outside that set (e.g. closure's fixed-point re-navigation, which has its own machinery)
   * still refuse. The original allowlist was four constructs wide because the population was
   * (wrongly) assumed to be a Bag whose duplicates the per-slot deduplication would miscount;
   * USE's own evaluator never counted duplicates, and the witness checker caught the bag-count
   * attempt failing re-evaluation before it could ship.
   */
  private static final java.util.Set<String> NARY_NAVIGATION_CONSTRUCTS =
      java.util.Set.of("forAll", "exists", "isEmpty", "notEmpty", "size()", "isUnique", "one",
          "includesAll");

  /**
   * The population one end of an N-ARY association (arity &ge; 3) projects from a fixed source
   * object at another end: per destination slot, the member guard is the disjunction of the link
   * terms over the cross product of the REMAINING N-2 positions' slots -- "reachable via SOME
   * tuple through the source", the n-ary generalization of the binary navigation population.
   * Source and destination ends are resolved structurally (the destination by its declared-end
   * identity, the source by slot-binding lookup); a REFLEXIVE n-ary association (the same class
   * at two ends) is refused rather than guessed at.
   */
  /**
   * The population of a UNION-declared end's navigation. USE core derives a union role's
   * content LIVE as the union of every {@code subsets}-declaring association's links
   * (Subsets.properties's own research: the role's own stored content and bound are never
   * consulted), so the encoding derives it too: the population is the per-slot union of each
   * subsetting association's destination end view, contributed only where the navigating
   * object's concrete class sits in that association's source end view -- the subclass
   * reprojection (a C source sees D partners through cd, an E source sees F partners through
   * ef; an A-typed binding would contribute via every subsetting association it conforms to).
   */
  private List<PopulationMember> unionNavigationPopulation(
      MNavigableElement unionEnd, VariableBinding source) {
    List<PopulationMember> population = new ArrayList<>();
    for (MAssociationEnd subsettingEnd : unionEnd.getSubsettingEnds()) {
      MAssociation subsettingAssociation = subsettingEnd.association();
      if (subsettingAssociation.associationEnds().size() != 2) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "union end '"
                + unionEnd.nameAsRolename()
                + "' is subsetted by the non-binary association "
                + subsettingAssociation.name()
                + ", which this slice does not model");
      }
      AssociationLinks links = context.linksFor(subsettingAssociation.name());
      if (links == null) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "union end '"
                + unionEnd.nameAsRolename()
                + "' is subsetted by association "
                + subsettingAssociation.name()
                + ", whose own link grid is not registered");
      }
      // The subclass reprojection: an association whose source end view does not contain the
      // navigating object contributes nothing (an E object derives nothing from C-D links).
      if (links.aEnd().indexOf(source) < 0 && links.bEnd().indexOf(source) < 0) {
        continue;
      }
      ObjectSlots destSlots = destinationEndView(links, subsettingEnd);
      for (int k = 0; k < destSlots.capacity(); k++) {
        population.add(
            new PopulationMember(
                destSlots.concreteBindings().get(k), linkTerm(links, subsettingEnd, source, k)));
      }
    }
    if (population.isEmpty()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "union end '"
              + unionEnd.nameAsRolename()
              + "' has no `subsets`-declaring association to derive its content from");
    }
    return population;
  }

  private List<PopulationMember> naryNavigationPopulation(
      NaryAssociationLinks links,
      MNavigableElement destination,
      VariableBinding source,
      String construct) {
    if (!NARY_NAVIGATION_CONSTRUCTS.contains(construct)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          construct
              + " over an n-ary association navigation: the navigation is typed Set (USE's own"
              + " MAssociationEnd.getType), and the supported consumers are forAll/exists/"
              + "isEmpty/notEmpty/size()/isUnique/one()/includesAll over that deduplicated"
              + " population -- this construct needs machinery the n-ary path does not have");
    }
    int arity = links.arity();
    int destEnd = -1;
    List<MAssociationEnd> declaredEnds = destination.association().associationEnds();
    for (int e = 0; e < arity; e++) {
      if (declaredEnds.get(e).equals(destination)) {
        destEnd = e;
        break;
      }
    }
    if (destEnd < 0) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "navigation destination is not a declared end of n-ary association "
              + links.associationName());
    }
    int sourceEnd = -1;
    for (int e = 0; e < arity; e++) {
      if (e != destEnd && links.endView(e).indexOf(source) >= 0) {
        sourceEnd = e;
        break;
      }
    }
    if (sourceEnd < 0) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "association "
              + links.associationName()
              + " does not connect class "
              + source.className());
    }
    List<Integer> existentialPositions = new ArrayList<>();
    for (int e = 0; e < arity; e++) {
      if (e != sourceEnd && e != destEnd) {
        existentialPositions.add(e);
      }
    }
    int sourceIndex = links.endView(sourceEnd).indexOf(source);
    ObjectSlots destView = links.endView(destEnd);
    List<PopulationMember> population = new ArrayList<>();
    for (int d = 0; d < destView.capacity(); d++) {
      int[] indices = new int[arity];
      indices[sourceEnd] = sourceIndex;
      indices[destEnd] = d;
      List<SmtTerm> options = new ArrayList<>();
      crossProductTerms(links, existentialPositions, 0, indices, options);
      population.add(new PopulationMember(destView.concreteBindings().get(d), Smt.or(options)));
    }
    return population;
  }

  /** Recursively enumerates the existential positions' cross product, one link term per tuple. */
  private static void crossProductTerms(
      NaryAssociationLinks links,
      List<Integer> positions,
      int position,
      int[] indices,
      List<SmtTerm> options) {
    if (position == positions.size()) {
      options.add(Smt.sym(links.linkName(indices.clone())));
      return;
    }
    int e = positions.get(position);
    for (int slot = 0; slot < links.endView(e).capacity(); slot++) {
      indices[e] = slot;
      crossProductTerms(links, positions, position + 1, indices, options);
    }
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
/**
   * {@code String.size()} over a configured-candidate string. The configured spellings ARE the
   * content space (the canonical string table this encoding works from), so the size is a
   * candidate enumeration: an ite chain over the attribute's configured candidates, each branch
   * the compile-time length of that spelling -- linear in the pinned QF_LIA logic, no string
   * theory. A String let binding carries its own candidate list
   * ({@code LocalBinding#enumeratedValues}), so size composes through lets; a literal's length
   * is a compile-time constant. USE's {@code StringSize.eval} is {@code value.length()}.
   */
/**
   * A VIRTUAL STRING: a concat/substring expression whose result strings are computable at
   * translation time from the source's configured candidates. The configured spellings are the
   * content space, so the operation expands per candidate -- each candidate's result is the
   * compile-time Java concat/substring of that spelling -- and an equality against a literal
   * keeps only the candidates whose result matches, as a guarded disjunction over the source
   * symbol's configured index. USE semantics confirmed against the use-core bytecode:
   * substring(start, end) is 1-based INCLUSIVE (java substring(start-1, end)) and an
   * out-of-range indices yield the EMPTY STRING (the evaluator's exception handler), never
   * undefined.
   */
  private boolean isVirtualStringOp(Expression e) {
    if (!(e instanceof ExpStdOp op)) {
      return false;
    }
    switch (op.opname()) {
      case "toUpper", "toLower" -> {
        return op.args().length == 1 && stringCandidates(op.args()[0]) != null;
      }
      case "at" -> {
        return op.args().length == 2
            && op.args()[1] instanceof ExpConstInteger
            && stringCandidates(op.args()[0]) != null;
      }
      case "concat" -> {
        return op.args().length == 2
            && stringCandidates(op.args()[0]) != null
            && stringCandidates(op.args()[1]) != null;
      }
      case "substring" -> {
        return op.args().length == 3
            && op.args()[1] instanceof ExpConstInteger
            && op.args()[2] instanceof ExpConstInteger
            && stringCandidates(op.args()[0]) != null;
      }
      default -> {
        return false;
      }
    }
  }

  /** One candidate of an enumerable string source: its guard and its spelling. */
  private static final class StringCandidateCase {
    final String spelling;
    final SmtTerm guard;

    StringCandidateCase(String spelling, SmtTerm guard) {
      this.spelling = spelling;
      this.guard = guard;
    }
  }

  private static final class EnumerableString {
    final List<StringCandidateCase> candidates = new ArrayList<>();
    SmtTerm defined = Smt.bool(true);
  }

  /**
   * The configured candidates of an enumerable string source: a literal (its own content),
   * a bare String attribute on a context variable (its configured domain), or a String let
   * variable (its recorded candidate list). Null for anything else.
   */
  private EnumerableString stringCandidates(Expression e) {
    EnumerableString result = new EnumerableString();
    if (e instanceof ExpConstString literal) {
      result.candidates.add(new StringCandidateCase(literal.value(), Smt.bool(true)));
      return result;
    }
    if (e instanceof ExpAttrOp attr
        && attr.objExp() instanceof ExpVariable source
        && !localBindings.containsKey(source.getVarname())) {
      VariableBinding b = context.binding(source.getVarname());
      AttributeValues values = context.attributeValues(b.className(), attr.attr().name());
      guardAgainstUncertainAttribute(values);
      AttributeDomain domain = context.attributeDomain(b.className(), attr.attr().name());
      SmtTerm symbol = Smt.sym(values.valueNames().get(b.slotIndex()));
      for (int i = 0; i < domain.enumeratedValues().size(); i++) {
        result.candidates.add(
            new StringCandidateCase(
                domain.enumeratedValues().get(i), Smt.eq(symbol, Smt.intLit(BigInteger.valueOf(i)))));
      }
      return result;
    }
    if (e instanceof ExpVariable v) {
      LocalBinding local = localBindings.get(v.getVarname());
      if (local != null && local.stringOrEnum() && local.enumeratedValues() != null) {
        SmtTerm symbol = Smt.sym(local.valueSymbol());
        for (int i = 0; i < local.enumeratedValues().size(); i++) {
          result.candidates.add(
              new StringCandidateCase(
                  local.enumeratedValues().get(i),
                  Smt.eq(symbol, Smt.intLit(BigInteger.valueOf(i)))));
        }
        result.defined = Smt.sym(local.definedSymbol());
        return result;
      }
    }
    // RECURSIVE COMPOSITION: a virtual-string operation whose source(s) resolve is itself a
    // candidate source -- each of its expanded results is a candidate guarded by the choices
    // that produced it. This is what lets concat/substring/at/case operations CHAIN
    // (x.s.concat(x.t).concat('!'), size of a composed result, ...).
    if (isVirtualStringOp(e)) {
      EnumerableString expanded = expandVirtualString((ExpStdOp) e);
      result.candidates.addAll(expanded.candidates);
      result.defined = expanded.defined;
      return result;
    }
    return null;
  }

  /** Expands the virtual string: one compile-time result spelling per source candidate. */
  private EnumerableString expandVirtualString(ExpStdOp op) {
    EnumerableString source = stringCandidates(op.args()[0]);
    EnumerableString result = new EnumerableString();
    result.defined = source.defined;
    if ("at".equals(op.opname())) {
      int position = ((ExpConstInteger) op.args()[1]).value();
      for (StringCandidateCase candidate : source.candidates) {
        // USE's Op_string_at: i < 1 or i > length is UNDEFINED -- the candidate is excluded
        // from the comparison entirely (never matched), unlike substring's empty string.
        if (position >= 1 && position <= candidate.spelling.length()) {
          result.candidates.add(
              new StringCandidateCase(
                  String.valueOf(candidate.spelling.charAt(position - 1)), candidate.guard));
        }
      }
      return result;
    }
    if ("toUpper".equals(op.opname()) || "toLower".equals(op.opname())) {
      // Locale.ROOT: USE calls the default-locale String.toUpperCase(); the pinned test
      // fixtures are ASCII, where the two agree, and ROOT keeps the encoding reproducible.
      boolean upper = "toUpper".equals(op.opname());
      for (StringCandidateCase candidate : source.candidates) {
        String converted =
            upper
                ? candidate.spelling.toUpperCase(java.util.Locale.ROOT)
                : candidate.spelling.toLowerCase(java.util.Locale.ROOT);
        result.candidates.add(new StringCandidateCase(converted, candidate.guard));
      }
      return result;
    }
    if ("concat".equals(op.opname())) {
      // The second operand may be a literal (a single guard-true candidate) or another
      // enumerable source (a configured-candidate attribute or String let variable): the
      // expansion crosses both candidate lists, each pair guarded by the conjunction of its
      // two guards, capped by the same expansion bound as the other enumerations.
      EnumerableString second = stringCandidates(op.args()[1]);
      if (source.candidates.size() * second.candidates.size() > 256) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "concat over "
                + source.candidates.size()
                + " x "
                + second.candidates.size()
                + " configured candidate pairs exceeds the 256-combination expansion cap");
      }
      for (StringCandidateCase left : source.candidates) {
        for (StringCandidateCase right : second.candidates) {
          result.candidates.add(
              new StringCandidateCase(
                  left.spelling + right.spelling, Smt.and(List.of(left.guard, right.guard))));
        }
      }
      return result;
    }
    int from = ((ExpConstInteger) op.args()[1]).value();
    int to = ((ExpConstInteger) op.args()[2]).value();
    for (StringCandidateCase candidate : source.candidates) {
      // USE's own handler: out-of-range indices yield the empty string, not undefined.
      String piece =
          from >= 1 && to >= from && to <= candidate.spelling.length()
              ? candidate.spelling.substring(from - 1, to)
              : "";
      result.candidates.add(new StringCandidateCase(piece, candidate.guard));
    }
    return result;
  }

  /**
   * An equality/inequality with a virtual-string side: expand the virtual side per candidate,
   * compare each result against the literal at compile time, and keep the matching candidates'
   * guards as the disjunction. The result is a defined Boolean (USE's total equality makes an
   * undefined operand side merely unequal, never undefined-equal).
   */
  private TranslatedExpression virtualStringComparison(Expression l, Expression r) {
    Expression virtual = isVirtualStringOp(l) ? l : r;
    Expression other = virtual == l ? r : l;
    EnumerableString expanded = expandVirtualString((ExpStdOp) virtual);
    EnumerableString comparands = stringCandidates(other);
    if (comparands == null) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "concat/substring compared against anything other than a string literal or a"
              + " configured-candidate string (attribute or let variable) is not supported in"
              + " this slice");
    }
    // Cross-domain content cases: per (result, comparand) pair the compile-time Java equality
    // decides the match, and each match contributes the CONJUNCTION of both sides' guards --
    // the contentAwareEquality pattern extended to computed results. The result is a defined
    // Boolean (USE's total equality makes a non-matching pair merely false).
    List<SmtTerm> admitted = new ArrayList<>();
    for (StringCandidateCase result : expanded.candidates) {
      for (StringCandidateCase comparand : comparands.candidates) {
        if (result.spelling.equals(comparand.spelling)) {
          admitted.add(Smt.and(List.of(result.guard, comparand.guard)));
        }
      }
    }
    SmtTerm value = admitted.isEmpty() ? Smt.bool(false) : Smt.or(admitted);
    return new TranslatedExpression(
        Smt.bool(true), Smt.and(List.of(expanded.defined, comparands.defined, value)));
  }

/**
   * {@code toInteger()}/{@code toReal()} over a configured-candidate string: each parseable
   * candidate contributes its parsed value to the ite chain; UNPARSEABLE candidates are
   * excluded from the chain AND from the conversion's usability -- USE's evaluators yield
   * UndefinedValue on NumberFormatException (use-core bytecode), and total equality treats
   * undefined as unequal, so the comparison must never be able to read a parseable
   * fallback value while an unparseable candidate is selected (the same soundness shape as
   * the zero-divisor exclusion). An all-unparseable domain is the constant-false expression.
   */
  private TranslatedExpression stringNumericConversion(Expression receiver, boolean toInteger) {
    EnumerableString source = stringCandidates(receiver);
    if (source == null) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "numeric conversion over anything other than a configured-candidate string"
              + " attribute, a String let variable, or a string literal");
    }
    List<StringCandidateCase> parseable = new ArrayList<>();
    for (StringCandidateCase candidate : source.candidates) {
      boolean parses;
      if (toInteger) {
        try {
          Integer.parseInt(candidate.spelling.trim());
          parses = true;
        } catch (NumberFormatException e) {
          parses = false;
        }
      } else {
        try {
          Double.parseDouble(candidate.spelling.trim());
          parses = true;
        } catch (NumberFormatException e) {
          parses = false;
        }
      }
      if (parses) {
        parseable.add(candidate);
      }
    }
    if (parseable.isEmpty()) {
      return new TranslatedExpression(Smt.bool(false), Smt.bool(false));
    }
    List<SmtTerm> unparseableGates = new ArrayList<>();
    List<SmtTerm> parseableGates = new ArrayList<>();
    java.util.IdentityHashMap<StringCandidateCase, SmtTerm> gateByCase =
        new java.util.IdentityHashMap<>();
    for (StringCandidateCase candidate : source.candidates) {
      if (parseable.contains(candidate)) {
        parseableGates.add(candidate.guard);
        gateByCase.put(candidate, candidate.guard);
      } else {
        unparseableGates.add(candidate.guard);
      }
    }
    int last = parseable.size() - 1;
    SmtTerm value = valueOfParsed(parseable.get(last), toInteger);
    for (int i = last - 1; i >= 0; i--) {
      value =
          Smt.ite(
              gateByCase.get(parseable.get(i)), valueOfParsed(parseable.get(i), toInteger), value);
    }
    // Definedness excludes the unparseable selections: selecting one means the conversion is
    // undefined, so the comparison can never match (the value chain's fallback is never
    // consulted once the definedness is false).
    SmtTerm defined = source.defined;
    if (!unparseableGates.isEmpty()) {
      defined =
          Smt.and(List.of(defined, Smt.not(Smt.or(unparseableGates))));
    }
    return new TranslatedExpression(defined, value);
  }

  private SmtTerm valueOfParsed(StringCandidateCase candidate, boolean toInteger) {
    if (toInteger) {
      return Smt.intLit(BigInteger.valueOf(Integer.parseInt(candidate.spelling.trim())));
    }
    return Smt.realLit(java.math.BigDecimal.valueOf(Double.parseDouble(candidate.spelling.trim())));
  }

/**
   * {@code indexOf(sub)} over a configured-candidate string with a literal needle. USE's
   * Op_string_indexOf (use-core bytecode) is the 1-based position -- java indexOf + 1 -- with
   * two quirks read off the same bytecode: an EMPTY receiver yields 0, and an EMPTY needle over
   * a non-empty receiver yields 1. Every candidate is defined (the operation always answers an
   * Int), so the ite chain needs no exclusion.
   */
  private TranslatedExpression stringIndexOf(Expression receiver, Expression needleExpr) {
    EnumerableString source = stringCandidates(receiver);
    if (source == null) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "indexOf over anything other than a configured-candidate string attribute, a String"
              + " let variable, or a string literal");
    }
    EnumerableString needles = stringCandidates(needleExpr);
    if (needles == null) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "indexOf with a needle that is neither a string literal nor a configured-candidate"
              + " string (attribute or let variable)");
    }
    // Per (receiver, needle) candidate pair the 1-based answer is compile-time; the ite chain
    // selects the pair both guards point at. The cap mirrors UBooleanProbability's expansion
    // cap: the finite enumeration is what keeps the answer exact without string theory.
    List<SmtTerm> pairGuards = new ArrayList<>();
    List<Integer> pairValues = new ArrayList<>();
    for (StringCandidateCase receiverCase : source.candidates) {
      for (StringCandidateCase needleCase : needles.candidates) {
        pairGuards.add(Smt.and(List.of(receiverCase.guard, needleCase.guard)));
        pairValues.add(indexOfResult(receiverCase.spelling, needleCase.spelling));
      }
    }
    if (pairGuards.size() > 256) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "indexOf over "
              + source.candidates.size()
              + " x "
              + needles.candidates.size()
              + " configured candidate pairs exceeds the 256-combination expansion cap");
    }
    int last = pairGuards.size() - 1;
    SmtTerm value = Smt.intLit(BigInteger.valueOf(pairValues.get(last)));
    for (int i = last - 1; i >= 0; i--) {
      value = Smt.ite(pairGuards.get(i), Smt.intLit(BigInteger.valueOf(pairValues.get(i))), value);
    }
    return new TranslatedExpression(
        Smt.and(List.of(source.defined, needles.defined)), value);
  }

  /** Op_string_indexOf's exact answer table, per the use-core bytecode. */
  private static int indexOfResult(String receiver, String needle) {
    if (receiver.isEmpty()) {
      return 0;
    }
    if (needle.isEmpty()) {
      return 1;
    }
    return receiver.indexOf(needle) + 1;
  }

  /**
   * One resolved U-type arithmetic operand: its representative term, whether that representative
   * is Int-sorted (UInteger family), its configured uncertainty candidates, and the SMT symbol
   * those candidates are guarded on. A crisp numeric literal carries an EMPTY candidate list and
   * no symbol -- it contributes zero uncertainty. Null signals the operand is unsupported for
   * arithmetic.
   */
  private record UArithOperand(
      SmtTerm representative, boolean integer, List<Double> sigmas, SmtTerm sigmaSymbol) {}

  /**
   * Resolves a U-type arithmetic operand: a crisp numeric literal, or a bare paired-U attribute
   * access on a context variable with a NON-EMPTY configured uncertainty domain (any candidate
   * count -- the composed quadrature enumerates the candidate PAIRS, each pair's quadrature a
   * compile-time constant guarded on both uncertainty symbols, so a symbolic-sigma nonlinearity
   * is never emitted).
   */
  private UArithOperand uArithOperand(Expression e) {
    if (e instanceof ExpConstInteger ci) {
      return new UArithOperand(
          Smt.intLit(BigInteger.valueOf(ci.value())), true, List.of(), null);
    }
    if (e instanceof ExpConstReal cr) {
      return new UArithOperand(
          Smt.realLit(BigDecimal.valueOf(cr.value())), false, List.of(), null);
    }
    if (e instanceof ExpAttrOp attr
        && attr.objExp() instanceof ExpVariable source
        && !localBindings.containsKey(source.getVarname())) {
      VariableBinding b = context.binding(source.getVarname());
      AttributeValues values = context.attributeValues(b.className(), attr.attr().name());
      if (!values.type().isPairedUType()) {
        return null;
      }
      AttributeDomain domain =
          context.attributeDomain(b.className(), attr.attr().name(), "uncertainty");
      if (domain.enumeratedValues().isEmpty()) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "U-type arithmetic over "
                + b.className()
                + "."
                + attr.attr().name()
                + ": the uncertainty domain has no configured candidates to enumerate");
      }
      List<Double> sigmas = new ArrayList<>();
      for (String candidate : domain.enumeratedValues()) {
        sigmas.add(Double.parseDouble(candidate));
      }
      return new UArithOperand(
          Smt.sym(values.valueNames().get(b.slotIndex())),
          attr.type().isTypeOfUInteger(),
          List.copyOf(sigmas),
          Smt.sym(values.uncertaintyNames().get(b.slotIndex())));
    }
    return null;
  }

  /**
   * The COMPOSED uncertainty term of a U-type +/-: the quadrature
   * {@code sqrt(sigma1^2 + sigma2^2)} USE computes, enumerated over the operands' configured
   * candidate PAIRS -- each pair's quadrature is a compile-time constant guarded by BOTH
   * uncertainty symbols (an ite chain over the pair guards, in the stable candidate order), so
   * the emitted term stays linear in the pinned logic no matter how many candidates either side
   * configures. Two crisp operands compose to the constant zero. More candidate pairs than the
   * 256-combination convention refuses rather than emitting a quadratic expansion.
   */
  private SmtTerm composedUArithUncertainty(UArithOperand left, UArithOperand right, String letVar) {
    if (left.sigmas().isEmpty() && right.sigmas().isEmpty()) {
      return Smt.realLit(BigDecimal.ZERO);
    }
    List<Double> leftSigmas = left.sigmas().isEmpty() ? List.of(0.0) : left.sigmas();
    List<Double> rightSigmas = right.sigmas().isEmpty() ? List.of(0.0) : right.sigmas();
    if ((long) leftSigmas.size() * rightSigmas.size() > 256) {
      throw unsupported(
          FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL,
          "let-bound U-type variable '"
              + letVar
              + "': composing "
              + left.sigmas().size()
              + " x "
              + right.sigmas().size()
              + " configured uncertainty candidates exceeds the 256-combination expansion cap;"
              + " the per-pair enumeration is what keeps the composed quadrature linear");
    }
    List<SmtTerm> pairGuards = new ArrayList<>();
    List<Double> pairValues = new ArrayList<>();
    for (double sigmaL : leftSigmas) {
      for (double sigmaR : rightSigmas) {
        List<SmtTerm> guards = new ArrayList<>();
        if (!left.sigmas().isEmpty()) {
          guards.add(Smt.eq(left.sigmaSymbol(), Smt.realLit(BigDecimal.valueOf(sigmaL))));
        }
        if (!right.sigmas().isEmpty()) {
          guards.add(Smt.eq(right.sigmaSymbol(), Smt.realLit(BigDecimal.valueOf(sigmaR))));
        }
        pairGuards.add(guards.isEmpty() ? Smt.bool(true) : Smt.and(guards));
        pairValues.add(Math.sqrt(sigmaL * sigmaL + sigmaR * sigmaR));
      }
    }
    int last = pairValues.size() - 1;
    SmtTerm composed = Smt.realLit(BigDecimal.valueOf(pairValues.get(last)));
    for (int i = last - 1; i >= 0; i--) {
      composed = Smt.ite(pairGuards.get(i), Smt.realLit(BigDecimal.valueOf(pairValues.get(i))), composed);
    }
    return composed;
  }

/** True for exactly the navigated-UString-size shape the equality branch supports. */
  private boolean isNavigatedUStringSize(Expression e) {
    if (!(e instanceof ExpStdOp size)
        || !"size".equals(size.opname())
        || size.args().length != 1
        || !(size.args()[0] instanceof ExpAttrOp attr)) {
      return false;
    }
    if (!(attr.objExp() instanceof ExpNavigation navigation)
        || navigation.getDestination().isCollection()
        || !(navigation.getObjectExpression() instanceof ExpVariable source)
        || localBindings.containsKey(source.getVarname())) {
      return false;
    }
    return attr.type().isTypeOfUString();
  }

  /**
   * {@code (x.gauge.tag.size() = n).toBooleanC(conf)} over a (folded) end view: the UString
   * size enumerates per destination slot the configured spelling lengths of the slot's CONCRETE
   * class, each guarded by the slot's link term; the equality keeps the cases whose length
   * matches ({@code <>} keeps the complement). Unlinked, the navigation is undefined and the
   * whole body is false -- USE's crisp equality over an undefined operand side.
   */
  private TranslatedExpression navigatedUStringSizeEquality(
      ExpStdOp eqOp, ExpStdOp sizeExpr, int demanded, boolean wantEq) {
    ExpAttrOp attr = (ExpAttrOp) sizeExpr.args()[0];
    ExpNavigation navigation = (ExpNavigation) attr.objExp();
    VariableBinding navSource =
        context.binding(((ExpVariable) navigation.getObjectExpression()).getVarname());
    MNavigableElement destination =
        resolveRedefinedDestination(navigation.getDestination(), navSource);
    if (destination.association() instanceof MAssociationClass) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "navigated UString size over an association class is not yet supported");
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    List<SmtTerm> admitted = new ArrayList<>();
    List<SmtTerm> all = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      VariableBinding concrete = destSlots.concreteBindings().get(k);
      String concreteClass = concrete.className();
      AttributeValues vals = context.attributeValues(concreteClass, attr.attr().name());
      AttributeDomain spellings =
          context.attributeDomain(concreteClass, attr.attr().name(), "value");
      SmtTerm tagSym = Smt.sym(vals.valueNames().get(concrete.slotIndex()));
      SmtTerm link = linkTerm(links, destination, navSource, k);
      for (int i = 0; i < spellings.enumeratedValues().size(); i++) {
        SmtTerm caseGuard =
            Smt.and(List.of(link, Smt.eq(tagSym, Smt.intLit(BigInteger.valueOf(i)))));
        all.add(caseGuard);
        if (spellings.enumeratedValues().get(i).length() == demanded) {
          admitted.add(caseGuard);
        }
      }
    }
    SmtTerm value =
        wantEq
            ? (admitted.isEmpty() ? Smt.bool(false) : Smt.or(admitted))
            : Smt.not(Smt.or(admitted.isEmpty() ? List.of(Smt.bool(false)) : admitted));
    return defined(value);
  }

  private TranslatedExpression stringSize(Expression receiver) {
    // One resolver for every supported source shape (literal, configured-candidate attribute,
    // String let variable, and -- since the composition slice -- any virtual string): the
    // candidates carry their own guards, so the size chain is guard -> length per candidate.
    EnumerableString source = stringCandidates(receiver);
    if (source == null || source.candidates.isEmpty()) {
      throw unsupported(
          FragmentBoundary.TIER_2,
          "size() over anything other than a configured-candidate string source (literal, crisp"
              + " String attribute, String let variable, or a composed virtual string)");
    }
    List<StringCandidateCase> cases = source.candidates;
    int last = cases.size() - 1;
    SmtTerm length = Smt.intLit(BigInteger.valueOf(cases.get(last).spelling.length()));
    for (int i = last - 1; i >= 0; i--) {
      length =
          Smt.ite(
              cases.get(i).guard,
              Smt.intLit(BigInteger.valueOf(cases.get(i).spelling.length())),
              length);
    }
    return new TranslatedExpression(source.defined, length);
  }

  /** The ite chain spelling-to-length over the configured candidates, last-candidate fallback. */
  private static SmtTerm lengthChain(SmtTerm symbol, List<String> candidates) {
    SmtTerm length =
        Smt.intLit(BigInteger.valueOf(candidates.get(candidates.size() - 1).length()));
    for (int i = candidates.size() - 2; i >= 0; i--) {
      length =
          Smt.ite(
              Smt.eq(symbol, Smt.intLit(BigInteger.valueOf(i))),
              Smt.intLit(BigInteger.valueOf(candidates.get(i).length())),
              length);
    }
    return length;
  }

/**
   * A VIRTUAL SPLIT: {@code x.s.split(sep)} over a configured-candidate string with a literal
   * separator. USE's Op_string_split is java {@code String.split(sep)} -- a regex split into a
   * Sequence of strings, trailing empties removed -- and this encoding expands it per candidate
   * at translation time by calling the SAME java method on the SAME spelling, so agreement is
   * exact by construction (regex-special separators cancel).
   */
  private boolean isVirtualStringSplit(Expression e) {
    if (!(e instanceof ExpStdOp op) || !"split".equals(op.opname())) {
      return false;
    }
    // BOTH the source and the separator may be any enumerable string source (literal,
    // configured-candidate attribute, or String let variable); the expansion crosses their
    // candidate lists.
    return op.args().length == 2
        && stringCandidates(op.args()[0]) != null
        && stringCandidates(op.args()[1]) != null;
  }

  /** One candidate of an expanded split: its part list and its guard. */
  private record SplitCase(List<String> parts, SmtTerm guard) {}

  private List<SplitCase> expandVirtualSplit(ExpStdOp split) {
    EnumerableString source = stringCandidates(split.args()[0]);
    EnumerableString separators = stringCandidates(split.args()[1]);
    List<SplitCase> cases = new ArrayList<>();
    for (StringCandidateCase sourceCase : source.candidates) {
      for (StringCandidateCase separatorCase : separators.candidates) {
        // The same java String.split call USE's evaluator makes -- exact by construction.
        cases.add(
            new SplitCase(
                List.of(sourceCase.spelling.split(separatorCase.spelling)),
                Smt.and(List.of(sourceCase.guard, separatorCase.guard))));
      }
    }
    if (cases.size() > 256) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "split over "
              + source.candidates.size()
              + " x "
              + separators.candidates.size()
              + " configured candidate pairs exceeds the 256-combination expansion cap");
    }
    return cases;
  }

  /** The per-candidate part-count chain for {@code split(sep)->size()}. */
  private SmtTerm virtualSplitSizeChain(ExpStdOp split) {
    List<SplitCase> cases = expandVirtualSplit(split);
    int last = cases.size() - 1;
    SmtTerm value = Smt.intLit(BigInteger.valueOf(cases.get(last).parts().size()));
    for (int i = last - 1; i >= 0; i--) {
      value =
          Smt.ite(
              cases.get(i).guard(),
              Smt.intLit(BigInteger.valueOf(cases.get(i).parts().size())),
              value);
    }
    return value;
  }

  /** The per-candidate membership chain for {@code split(sep)->includes/excludes(lit)}. */
  private SmtTerm virtualSplitMembershipChain(
      ExpStdOp split, String needle, boolean wantIncludes) {
    List<SplitCase> cases = expandVirtualSplit(split);
    int last = cases.size() - 1;
    SmtTerm value =
        Smt.bool(cases.get(last).parts().contains(needle) == wantIncludes);
    for (int i = last - 1; i >= 0; i--) {
      value =
          Smt.ite(
              cases.get(i).guard(),
              Smt.bool(cases.get(i).parts().contains(needle) == wantIncludes),
              value);
    }
    return value;
  }

  private TranslatedExpression collectionSize(Expression receiver) {
    if (isVirtualStringSplit(receiver)) {
      return defined(virtualSplitSizeChain((ExpStdOp) receiver));
    }
    SetContent letSet = localCollection(receiver);
    if (letSet != null) {
      return defined(Smt.intLit(BigInteger.valueOf(letSet.size())));
    }
    if (receiver instanceof ExpNavigation navigation
        && isCollectionValuedNavigation(navigation)) {
      return defined(sizeTerm(populationOf(navigation, "size()")));
    }
    if (receiver instanceof ExpQuery query
        && (query instanceof ExpSelect || query instanceof ExpReject)
        && isSupportedSelectSource(query)) {
      return defined(sizeTerm(selectedAllInstancesPopulation(query)));
    }
    SetAttrView sizeSetAttr = setAttrRead(receiver);
    if (sizeSetAttr != null) {
      SmtTerm count = null;
      for (int j = 0; j < sizeSetAttr.poolSize(); j++) {
        SmtTerm bit = sizeSetAttr.member(j);
        SmtTerm one = Smt.intLit(BigInteger.ONE);
        count = count == null
            ? Smt.ite(bit, one, Smt.intLit(BigInteger.ZERO))
            : Smt.app("+", count, Smt.ite(bit, one, Smt.intLit(BigInteger.ZERO)));
      }
      return defined(count == null ? Smt.intLit(BigInteger.ZERO) : count);
    }
    SetContent literalContent = constantCollectionContent(receiver);
    if (literalContent != null) {
      // The element count is a compile-time constant: DISTINCT for Set/OrderedSet semantics,
      // duplicate-COUNTING for Bag/Sequence (collectionLiteralContent keeps the occurrence);
      // a ->flatten() node contributes its flattened leaves.
      return defined(Smt.intLit(BigInteger.valueOf(literalContent.size())));
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
  /**
   * {@code ->sum()} over a CONSTANT-CONTENT collection (a collection literal or a let-bound
   * one): the total is a compile-time constant of the element set -- duplicate-COUNTING for
   * Bag/Sequence (the multiset semantics the size() slice pinned), and USE's own total for the
   * empty collection (0). Integer elements sum exactly; Real elements sum as exact decimals.
   * Sums over navigations or other non-constant sources need per-member value aggregation and
   * stay refused. NOTE: the empty-collection literal parses to {@link ExpEmptyCollection}, not
   * ExpSetLiteral, so the empty case is a dispatch case of its own.
   */
  private TranslatedExpression collectionSum(Expression receiver) {
    if (receiver instanceof ExpEmptyCollection) {
      return defined(Smt.intLit(BigInteger.ZERO));
    }
    SetContent content = constantCollectionContent(receiver);
    if (content == null) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "sum over anything other than a constant-content collection literal or a let-bound"
              + " set/bag/sequence is not yet supported (navigations need per-member value"
              + " aggregation)");
    }
    if (content.reals() != null) {
      BigDecimal total = BigDecimal.ZERO;
      for (BigDecimal v : content.reals()) {
        total = total.add(v);
      }
      return defined(Smt.realLit(total));
    }
    if (content.integers() != null) {
      java.math.BigInteger total = java.math.BigInteger.ZERO;
      for (java.math.BigInteger v : content.integers()) {
        total = total.add(v);
      }
      return defined(Smt.intLit(total));
    }
    throw unsupported(
        FragmentBoundary.TIER_3, "sum over a String-valued collection is not an OCL operation");
  }

  /**
   * {@code ->first()}/{@code ->last()} over a constant-content ORDERED collection (Sequence,
   * OrderedSet -- Bag and Set are unordered and USE's own type checker never produces the
   * expression): the element is a compile-time constant; an empty ordered collection yields an
   * UNDEFINED element (the constant-undefined expression). Integer elements only; String
   * elements refuse (a String result needs the content-comparison consumers, not a value).
   */
  private TranslatedExpression collectionEnd(Expression receiver, boolean first) {
    SetContent content = constantCollectionContent(receiver);
    if (content == null || content.integers() == null) {
        throw unsupported(
          FragmentBoundary.TIER_3,
          (first ? "first" : "last")
              + " over anything other than an Integer-valued ordered collection literal or a"
              + " let-bound ordered collection is not yet supported");
    }
    if (content.integers().isEmpty()) {
      return new TranslatedExpression(Smt.bool(false), Smt.intLit(BigInteger.ZERO));
    }
    BigInteger element = first
        ? content.integers().get(0)
        : content.integers().get(content.integers().size() - 1);
    return defined(Smt.intLit(element));
  }

  /**
   * {@code ->at(i)} over a constant-content ORDERED collection (Sequence/OrderedSet). Semantics
   * per {@code Op_sequence_at} (use-core): 1-based; an out-of-range index yields UNDEFINED, so
   * the definedness requires the index within 1..size and the value is an ite chain over the
   * in-range positions. A constant index folds to the element (or the constant-undefined
   * expression when out of range).
   */
  private TranslatedExpression collectionAt(Expression receiver, Expression indexExpr) {
    SetContent content = constantCollectionContent(receiver);
    if (content == null || content.integers() == null) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "at over a non-Integer-valued collection content is not supported in this slice");
    }
    List<BigInteger> elements = content.integers();
    TranslatedExpression index = argResult(indexExpr);
    if (indexExpr instanceof ExpConstInteger constantIndex) {
      int position = constantIndex.value();
      if (position >= 1 && position <= elements.size()) {
        return defined(Smt.intLit(elements.get(position - 1)));
      }
      // Op_sequence_at: out of range is UNDEFINED for every index value.
      return new TranslatedExpression(Smt.bool(false), Smt.intLit(BigInteger.ZERO));
    }
    // Symbolic index: value = ite chain over the 1-based positions with an out-of-range
    // (constant-undefined) fallback; definedness = the index is one of the in-range positions.
    SmtTerm value = Smt.intLit(BigInteger.ZERO);
    for (int position = elements.size(); position >= 1; position--) {
      SmtTerm matches = Smt.eq(index.value(), Smt.intLit(BigInteger.valueOf(position)));
      value = Smt.ite(matches, Smt.intLit(elements.get(position - 1)), value);
    }
    SmtTerm defined =
        Smt.and(
            List.of(
                index.defined(),
                Smt.app(">=", index.value(), Smt.intLit(BigInteger.ONE)),
                Smt.app("<=", index.value(), Smt.intLit(BigInteger.valueOf(elements.size())))));
    return new TranslatedExpression(defined, value);
  }

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
    } else if (setAttrRead(receiver) != null) {
      // A SET-typed attribute is empty iff every pool membership is false.
      SetAttrView setAttr = setAttrRead(receiver);
      List<SmtTerm> someMember = new ArrayList<>();
      for (int j = 0; j < setAttr.poolSize(); j++) {
        someMember.add(setAttr.member(j));
      }
      SmtTerm any = Smt.or(someMember);
      return new TranslatedExpression(
          Smt.bool(true), wantEmpty ? Smt.not(any) : any);
    } else if (localCollection(receiver) != null) {
      boolean nonEmpty = localCollection(receiver).size() > 0;
      return new TranslatedExpression(
          Smt.bool(true), wantEmpty ? Smt.bool(!nonEmpty) : Smt.bool(nonEmpty));
    } else if (receiver instanceof ExpAllInstances
        || (receiver instanceof ExpNavigation navigation
            && isCollectionValuedNavigation(navigation))) {
      population = populationOf(receiver, construct);
    } else if (receiver instanceof ExpCollectionLiteral set && set.getElemExpr().length > 0) {
      // Emptiness from the EXPANDED distinct element count: a constant-bounds range with an
      // inverted interval (Set{5..4}) contributes no elements, so it really is empty -- the
      // pre-expansion shortcut ("a literal always has elements") answered isEmpty wrongly for
      // that shape.
      boolean nonEmpty = distinctLiteralElementCount(set) > 0;
      return new TranslatedExpression(
          Smt.bool(true), wantEmpty ? Smt.bool(!nonEmpty) : Smt.bool(nonEmpty));
    } else if (receiver instanceof ExpClosure closure) {
      // An OCL closure always INCLUDES its direct image (it is the least fixed point
      // containing the range relation's first hop), so the closure is empty exactly when its
      // RANGE is empty -- regardless of what the body's further hops traverse (a redefined
      // end, an n-ary end, or any heterogeneous continuation). Routing the emptiness decision
      // to the range reuses every existing range machinery, including the redirect-aware and
      // n-ary population paths, without reaching the closure fixed point at all.
      return collectionEmptiness(closure.getRangeExpression(), wantEmpty);
    } else if (constantCollectionContent(receiver) != null) {
      // Constant-content receivers routed through the shared extractor (collection-valued
      // operation results with constant literal bodies): the content is compile-time, so the
      // emptiness decision is too.
      SetContent opContent = constantCollectionContent(receiver);
      boolean nonEmpty = (opContent.integers() != null && !opContent.integers().isEmpty());
      return new TranslatedExpression(
          Smt.bool(true), wantEmpty ? Smt.bool(!nonEmpty) : Smt.bool(nonEmpty));
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
      // CAST INITIALIZER: `let t : T = x.b.oclAsType(T) in ...` -- the bind-the-downcast-once
      // idiom every guarded-downcast context wants. ExpAsType#eval is strict, so the binding's
      // per-slot definedness is the link AND the destination slot's compile-time conformance
      // to the cast target; a non-conforming slot contributes nothing.
      if (e.getVarExpression() instanceof ExpAsType cast
          && cast.getSourceExpr() instanceof ExpNavigation castNavigation
          && !castNavigation.getDestination().isCollection()
          && castNavigation.getObjectExpression() instanceof ExpVariable) {
        result =
            objectLetOverNavigation(
                e, castNavigation, (org.tzi.use.uml.mm.MClassifier) cast.type());
        return;
      }
      // CHAINED OBJECT LET: the initializer names another OBJECT binding (an any-let or a
      // navigation let variable) -- the alias IS that binding, so every read through the new
      // variable reads the same slot's symbols and the chain's definedness is the
      // initializer's. Pure binding aliasing; nothing new is enumerated.
      if (e.getVarExpression() instanceof ExpVariable aliased
          && !localBindings.containsKey(aliased.getVarname())) {
        VariableBinding object = context.binding(aliased.getVarname());
        result =
            translate(
                e.getInExpression(),
                context.withBinding(e.getVarname(), object),
                mode,
                positivePolarity,
                localBindings);
        return;
      }
      result = objectAnyLet(e);
      return;
    }
    // U-type families: the binding aliases the source attribute's symbol(s) and records the
    // source's configured candidate domains (LetBindingSource), so the confidence-threshold
    // consumers enumerate cases over the binding exactly as over the attribute.
    if (e.getVarType().isTypeOfUReal()
        || e.getVarType().isTypeOfUInteger()
        || e.getVarType().isTypeOfUBoolean()
        || e.getVarType().isTypeOfUString()) {
      result = uTypeLet(e);
      return;
    }
    // NOTE: SetType.isTypeOfCollection() is FALSE in USE's own type lattice (that predicate
    // names the abstract Collection type exactly); CollectionType is the right arbiter.
    if (e.getVarType() instanceof org.tzi.use.uml.ocl.type.CollectionType
        && e.getVarExpression() instanceof ExpCollectionLiteral set) {
      result = setLiteralLet(e, set);
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
              + ": only primitive Integer, Boolean, Real, String, and Enum let bindings, plus"
              + " U-typed bindings over a bare attribute-access initializer, are supported;"
              + " object- and collection-typed bindings require a finite"
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
   * {@code let u : UReal/UInteger = <bare attribute access> in body}: the paired U-type let.
   * The binding carries BOTH components -- its value symbol is let-bound to the initializer
   * attribute's REPRESENTATIVE and its uncertainty symbol to the attribute's UNCERTAINTY -- so
   * a confidence-threshold consumer reading the binding sees exactly the pair it would see
   * reading the attribute. A bare read is mandatory: {@code guardAgainstUncertainAttribute}
   * refuses every other U-typed expression shape, so no initializer outside the bare/navigated
   * attribute forms translates anyway; chained let variables are refused here (the source
   * binding would have to be threaded both components, which no consumer needs yet).
   */
  /**
   * {@code let s : Set(Integer) = Set{...} in body} (String likewise): the FIRST-CLASS
   * collection binding. The literal IS the collection's representation -- duplicates collapsed,
   * constant-bounds ranges expanded -- so the binding carries the constant element set
   * ({@link SetContent}) and no solver-side collection value is invented. Every element kind
   * the direct set-literal consumers accept (Integer constants, constant-bounds ranges, String
   * constants; no mixing) is accepted here through the same extraction.
   */
  /**
   * The constant content of ANY collection literal: Set literals collapse duplicates,
   * Bag/Sequence/OrderedSet literals keep every occurrence (a Bag's size counts them; an
   * OrderedSet's insertion-ordered uniqueness still collapses -- the element order the SMT
   * encoding never observes anyway). Integer constants and constant-bounds ranges take the
   * shared expansion; String constants bind by content; mixing refuses.
   */
  private static SetContent collectionLiteralContent(ExpCollectionLiteral lit) {
    return collectionLiteralContent(lit, false);
  }

  /**
   * The constant content of a collection literal. With {@code flattenNesting}, an element that
   * is ITSELF a collection literal contributes its own (recursively flattened) leaves -- the
   * {@code ->flatten()} resolution -- under the OUTER literal's duplicate policy; without it, a
   * nested collection element refuses (the direct consumers never see one).
   */
  private static SetContent collectionLiteralContent(ExpCollectionLiteral lit, boolean flattenNesting) {
    boolean dedupe = lit instanceof ExpSetLiteral || lit instanceof ExpOrderedSetLiteral;
    boolean sawString = false;
    boolean sawReal = false;
    boolean sawInt = false;
    List<BigDecimal> realValues = new ArrayList<>();
    List<BigInteger> intValues = new ArrayList<>();
    List<String> stringValues = new ArrayList<>();
    for (Expression element : lit.getElemExpr()) {
      if (element instanceof ExpConstString constant) {
        sawString = true;
        if (!dedupe || !stringValues.contains(constant.value())) {
          stringValues.add(constant.value());
        }
      } else if (element instanceof ExpConstReal real) {
        sawReal = true;
        BigDecimal v = BigDecimal.valueOf(real.value());
        if (!dedupe || !realValues.contains(v)) {
          realValues.add(v);
        }
      } else if (element instanceof ExpCollectionLiteral inner) {
        if (!flattenNesting) {
          throw unsupported(
              FragmentBoundary.TIER_3,
              "collection literal with a nested collection element ("
                  + element
                  + "); use ->flatten()");
        }
        SetContent innerContent = collectionLiteralContent(inner, true);
        sawString |= innerContent.strings() != null;
        sawReal |= innerContent.reals() != null;
        sawInt |= innerContent.integers() != null;
        List<?>[] innerLists = {
          innerContent.reals(), innerContent.integers(), innerContent.strings()
        };
        List<?>[] outerLists = {realValues, intValues, stringValues};
        for (int kind = 0; kind < 3; kind++) {
          if (innerLists[kind] == null) {
            continue;
          }
          for (Object v : innerLists[kind]) {
            boolean present =
                dedupe && ((List<Object>) outerLists[kind]).contains(v);
            if (!present) {
              ((List<Object>) outerLists[kind]).add(v);
            }
          }
        }
      } else {
        sawInt = true;
        appendIntegerElementValues(element, intValues, dedupe);
      }
    }
    if ((sawReal && sawString) || (sawReal && sawInt) || (sawInt && sawString)) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "collection literal mixes element kinds; not supported in this slice");
    }
    if (sawReal) {
      return new SetContent(realValues, null, null);
    }
    return sawString
        ? new SetContent(null, null, stringValues)
        : new SetContent(null, intValues, null);
  }

  private TranslatedExpression setLiteralLet(ExpLet e, ExpCollectionLiteral set) {
    SetContent content = collectionLiteralContent(set);
    String symbolStem = "|ocl-let-" + e.getVarname();
    LocalBinding binding =
        new LocalBinding(
            symbolStem + "-defined|", symbolStem + "-value|", false, null, null, null, content);
    Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
    extended.put(e.getVarname(), binding);
    // A literal is always defined, so the let's definedness is the body's own.
    return translate(e.getInExpression(), context, mode, positivePolarity, Map.copyOf(extended));
  }

  /**
   * The constant content behind a collection RECEIVER: a let-bound set/bag/sequence variable, a
   * collection literal, or {@code ->flatten()} over a (possibly nested) collection literal -- the
   * three shapes whose content is fully known at translation time. Null for anything else.
   */
  private SetContent constantCollectionContent(Expression receiver) {
    SetContent direct = localCollection(receiver);
    if (direct != null) {
      return direct;
    }
    if (receiver instanceof ExpCollectionLiteral lit) {
      boolean isSetKind =
          lit instanceof ExpSetLiteral
              || lit instanceof ExpBagLiteral
              || lit instanceof ExpSequenceLiteral
              || lit instanceof ExpOrderedSetLiteral;
      return isSetKind ? collectionLiteralContent(lit, false) : null;
    }
    // COLLECTION-VALUED OPERATION RESULTS (the Lists.use arc, first slice): an operation
    // whose result type is a collection and whose BODY is a collection literal over
    // compile-time-constant elements has a constant content, so every consumer that reads
    // constant content (size, membership, emptiness, quantifiers) serves it through this
    // same branch. The receiver does not influence a constant body's content; symbolic
    // elements and non-literal bodies return null (the caller's constant-content branches
    // fall through to their own located refusals).
    if (receiver instanceof ExpObjOp objOp) {
      MOperation operation = objOp.getOperation();
      if (!operationsInProgress.contains(operation)
          && operation.resultType() != null
          && operation.resultType().isKindOfCollection(
              org.tzi.use.uml.ocl.type.Type.VoidHandling.EXCLUDE_VOID)
          && operation.expression() instanceof ExpCollectionLiteral literal) {
        boolean allConstant = true;
        for (Expression element : literal.getElemExpr()) {
          if (!(element instanceof ExpConstInteger)) {
            allConstant = false;
            break;
          }
        }
        if (allConstant) {
          return collectionLiteralContent(literal, false);
        }
      }
      return null;
    }
    if (receiver instanceof ExpStdOp op
        && "flatten".equals(op.opname())
        && op.args().length == 1
        && op.args()[0] instanceof ExpCollectionLiteral lit) {
      // ->flatten() over a collection literal: the nesting resolves to the leaves, under the
      // OUTER literal's duplicate policy.
      return collectionLiteralContent(lit, true);
    }
    if (receiver instanceof ExpStdOp op && op.args().length == 2) {
      // SET ALGEBRA over constant-content collections: including/excluding take a constant
      // scalar element; union/intersection/symmetricDifference/infix-minus take a second
      // constant-content collection. Set semantics: duplicate-collapsed results. The
      // algebra is Integer-kind only in this slice (String/Real set algebra refuses via the
      // null fall-through to the caller's refusal).
      String name = op.opname();
      if (name.equals("including") || name.equals("excluding")) {
        SetContent left = constantCollectionContent(op.args()[0]);
        if (left != null && left.integers() != null
            && op.args()[1] instanceof ExpConstInteger element) {
          List<BigInteger> result = new ArrayList<>(left.integers());
          BigInteger v = BigInteger.valueOf(element.value());
          if (name.equals("including")) {
            if (!result.contains(v)) {
              result.add(v);
            }
          } else {
            result.removeIf(v::equals);
          }
          return new SetContent(null, result, null);
        }
        return null;
      }
      if (name.equals("union") || name.equals("intersection")
          || name.equals("symmetricDifference") || name.equals("-")) {
        SetContent left = constantCollectionContent(op.args()[0]);
        SetContent right = constantCollectionContent(op.args()[1]);
        if (left != null && right != null
            && left.integers() != null && right.integers() != null) {
          java.util.TreeSet<BigInteger> ls = new java.util.TreeSet<>(left.integers());
          java.util.TreeSet<BigInteger> rs = new java.util.TreeSet<>(right.integers());
          List<BigInteger> result =
              switch (name) {
                case "union" -> {
                  ls.addAll(rs);
                  yield new ArrayList<>(ls);
                }
                case "intersection" -> {
                  ls.retainAll(rs);
                  yield new ArrayList<>(ls);
                }
                case "symmetricDifference" -> {
                  java.util.TreeSet<BigInteger> copy = new java.util.TreeSet<>(ls);
                  ls.removeAll(rs);
                  rs.removeAll(copy);
                  ls.addAll(rs);
                  yield new ArrayList<>(ls);
                }
                default -> {
                  ls.removeAll(rs);
                  yield new ArrayList<>(ls);
                }
              };
          return new SetContent(null, result, null);
        }
      }
    }
    return null;
  }

  /**
   * A SET(Integer)-typed attribute read from a context variable: the pool (the configured
   * element candidates, as BigIntegers in enumerated order) and the slot's membership Bool
   * symbols (slot-major, matching the encoder's flattened declaration order).
   */
  private record SetAttrView(List<BigInteger> pool, List<String> members, int slot) {
    SmtTerm member(int poolIndex) {
      return Smt.sym(members().get(slot() * pool().size() + poolIndex));
    }
    int poolSize() {
      return pool.size();
    }
  }

  /**
   * The SET-typed attribute view behind {@code x.tags}, or null when {@code e} is not a
   * Set(Integer) attribute access on a context variable.
   */
  private SetAttrView setAttrRead(Expression e) {
    if (!(e instanceof ExpAttrOp attr)
        || !(attr.objExp() instanceof ExpVariable v)
        || localBindings.containsKey(v.getVarname())) {
      return null;
    }
    VariableBinding b;
    AttributeValues vals;
    try {
      b = context.binding(v.getVarname());
      vals = context.attributeValues(b.className(), attr.attr().name());
    } catch (SmtTranslationException unregistered) {
      return null;
    }
    if (vals.type() != AttributeType.SET_INTEGER) {
      return null;
    }
    AttributeDomain domain;
    try {
      domain = context.attributeDomain(b.className(), attr.attr().name());
    } catch (SmtTranslationException noDomain) {
      return null;
    }
    List<BigInteger> pool = new ArrayList<>();
    for (String candidate : domain.enumeratedValues()) {
      pool.add(new BigInteger(candidate.trim()));
    }
    return new SetAttrView(pool, vals.valueNames(), b.slotIndex());
  }

  /** The constant set content a variable binding carries, or null. */
  private SetContent localCollection(Expression e) {
    if (e instanceof ExpVariable v) {
      LocalBinding binding = localBindings.get(v.getVarname());
      return binding == null ? null : binding.collection();
    }
    return null;
  }

  private TranslatedExpression uTypeLet(ExpLet e) {
    // Three supported initializer shapes: a bare U-typed attribute access on a context variable
    // (one slot, guard true), a CHAINED U-type let variable (aliases its predecessor's symbols
    // and INHERITS its let source -- alias key included, so the read-once rule spans the chain),
    // and a single-valued NAVIGATED attribute access (one slot per destination slot of the end
    // view, symbols selected per slot with concrete-class dispatch, each guarded by the slot's
    // link term; unfolded views only -- folded ends carry per-concrete-class configured domains,
    // a slice of their own).
    Expression initializer = e.getVarExpression();
    String symbolStem = "|ocl-let-" + e.getVarname();
    String aliasKey = "let:" + e.getVarname();
    List<LetBindingSource.LetSlot> slots = null;
    String uncertaintySymbol = null;
    SmtTerm valueTerm;
    SmtTerm uncertaintyTerm = null;
    SmtTerm definedTerm = Smt.bool(true);
    LetBindingSource letSource = null;

    if (initializer instanceof ExpAttrOp attr
        && attr.objExp() instanceof ExpVariable source
        && !localBindings.containsKey(source.getVarname())) {
      // BARE ATTRIBUTE ACCESS: one slot, guard true, the attribute's own symbols.
      VariableBinding b = context.binding(source.getVarname());
      String attributeName = attr.attr().name();
      AttributeValues values = context.attributeValues(b.className(), attributeName);
      AttributeDomain firstDomain;
      AttributeDomain secondDomain = null;
      SmtTerm firstSymbol = Smt.sym(values.valueNames().get(b.slotIndex()));
      if (values.type().isPairedUType()) {
        firstDomain = context.attributeDomain(b.className(), attributeName, "value");
        secondDomain = context.attributeDomain(b.className(), attributeName, "uncertainty");
        uncertaintySymbol = symbolStem + "-uncertainty|";
        uncertaintyTerm = Smt.sym(values.uncertaintyNames().get(b.slotIndex()));
      } else if (values.type() == AttributeType.UBOOLEAN) {
        firstDomain = context.attributeDomain(b.className(), attributeName, "probability");
      } else if (values.type() == AttributeType.USTRING) {
        firstDomain = context.attributeDomain(b.className(), attributeName, "value");
        secondDomain = context.attributeDomain(b.className(), attributeName, "confidence");
        uncertaintySymbol = symbolStem + "-uncertainty|";
        uncertaintyTerm = Smt.sym(values.confidenceNames().get(b.slotIndex()));
      } else {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "U-type let over "
                + values.type()
                + " with no supported candidate-enumerable encoding");
      }
      slots =
          List.of(
              new LetBindingSource.LetSlot(firstSymbol, uncertaintyTerm, Smt.bool(true), firstDomain, secondDomain));
      valueTerm = firstSymbol;
    } else if (initializer instanceof ExpVariable prevVar
        && localBindings.containsKey(prevVar.getVarname())) {
      LocalBinding prev = localBindings.get(prevVar.getVarname());
      if (prev.letSource() == null && prev.uncertaintySymbol() == null) {
        throw unsupported(
            FragmentBoundary.UTYPE_CORE,
            "U-type let chained over '"
                + prevVar.getVarname()
                + "': the source binding carries no U-type encoding (only U-type lets chain)");
      }
      // Alias the predecessor's symbols; inherit its let source UNCHANGED -- the inherited
      // alias key is what makes the read-once rule treat the whole chain as one value.
      valueTerm = Smt.sym(prev.valueSymbol());
      if (prev.uncertaintySymbol() != null) {
        uncertaintySymbol = symbolStem + "-uncertainty|";
        uncertaintyTerm = Smt.sym(prev.uncertaintySymbol());
      }
      letSource = prev.letSource();
      // CHAINED with uncertainty: the alias binds the predecessor's uncertainty symbol too.
      uncertaintySymbol = prev.uncertaintySymbol();
      uncertaintyTerm = Smt.sym(prev.uncertaintySymbol());
    } else if (initializer instanceof ExpStdOp arith
        && ("+".equals(arith.opname()) || "-".equals(arith.opname()))
        && arith.args().length == 2
        && uArithOperand(arith.args()[0]) != null
        && uArithOperand(arith.args()[1]) != null) {
      // U-TYPE ARITHMETIC: +/- over crisp numeric literals and paired-U attribute accesses.
      // USE composes uncertainties by quadrature (sqrt(s1^2 + s2^2) on doubles); the composed
      // uncertainty ENUMERATES the operands' configured candidate pairs (each pair's quadrature
      // a compile-time constant guarded on both uncertainty symbols -- see
      // composedUArithUncertainty), the composed representative stays a linear sum/difference of
      // symbols, and the threshold boundary arithmetic stays linear. A mixed-family sum
      // (UInteger + UReal) lifts the Int-sorted representative: USE widens the UInteger operand
      // through UReal, so the composed family is UREAL.
      UArithOperand left = uArithOperand(arith.args()[0]);
      UArithOperand right = uArithOperand(arith.args()[1]);
      SmtTerm repL = left.representative();
      SmtTerm repR = right.representative();
      // MIXED FAMILY (UInteger + UReal): the Int-sorted part lifts with to_real.
      if (left.integer() != right.integer()) {
        if (left.integer()) {
          repL = Smt.app("to_real", repL);
        }
        if (right.integer()) {
          repR = Smt.app("to_real", repR);
        }
      }
      valueTerm =
          "+".equals(arith.opname())
              ? Smt.app("+", repL, repR)
              : Smt.app("-", repL, repR);
      uncertaintyTerm = composedUArithUncertainty(left, right, e.getVarname());
      uncertaintySymbol = symbolStem + "-uncertainty|";
    } else if (initializer instanceof ExpAttrOp navAttr
        && navAttr.objExp() instanceof ExpNavigation navigation
        && !navigation.getDestination().isCollection()
        && navigation.getObjectExpression() instanceof ExpVariable navSource
        && !localBindings.containsKey(navSource.getVarname())) {
      // NAVIGATED ATTRIBUTE ACCESS: one slot per destination slot of the end view.
      return navigatedUTypeLet(e, navAttr, navigation, navSource.getVarname(), symbolStem, aliasKey);
    } else {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "let-bound U-type variable '"
              + e.getVarname()
              + "': only a bare U-typed attribute access on a context variable, another U-type"
              + " let variable, or a single-valued navigated U-typed attribute access is"
              + " supported as the initializer");
    }

    if (letSource == null) {
      letSource = new LetBindingSource(aliasKey, slots == null ? List.of() : slots);
    }
    LocalBinding binding =
        new LocalBinding(
            symbolStem + "-defined|",
            symbolStem + "-value|",
            false,
            null,
            uncertaintySymbol,
            letSource);
    Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
    extended.put(e.getVarname(), binding);
    TranslatedExpression body =
        translate(e.getInExpression(), context, mode, positivePolarity, Map.copyOf(extended));
    java.util.List<SmtTerm.Binding> bindings = new java.util.ArrayList<>();
    bindings.add(new SmtTerm.Binding(binding.definedSymbol(), definedTerm));
    bindings.add(new SmtTerm.Binding(binding.valueSymbol(), valueTerm));
    if (uncertaintySymbol != null) {
      bindings.add(new SmtTerm.Binding(uncertaintySymbol, uncertaintyTerm));
    }
    return new TranslatedExpression(
        Smt.let(bindings, body.defined()), Smt.let(bindings, body.value()));
  }

  /**
   * The NAVIGATED U-type initializer: {@code let u : UReal = x.gauge.speed in body}. The
   * binding's definedness is the navigation itself (the or of the end view's link terms -- an
   * unlinked navigation is undefined), its value and uncertainty are the per-slot selections
   * over the end view (concrete-class dispatch, exactly what the ordinary navigated attribute
   * read selects for the value), and its let source records one slot per destination slot so the
   * candidate-enumerating consumers guard each configured choice under that slot's link term.
   * UNFOLDED end views only: a folded view's slots carry per-concrete-class configured domains,
   * and enumerating candidates over them is a slice of its own.
   */
  private TranslatedExpression navigatedUTypeLet(
      ExpLet e,
      ExpAttrOp attr,
      ExpNavigation navigation,
      String sourceName,
      String symbolStem,
      String aliasKey) {
    VariableBinding navSource = context.binding(sourceName);
    MNavigableElement destination =
        resolveRedefinedDestination(navigation.getDestination(), navSource);
    if (destination.association() instanceof MAssociationClass) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "U-type let whose initializer navigates an association class is not yet supported");
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destSlots = destinationEndView(links, destination);
    AttributeValues declaredValues =
        context.attributeValues(destSlots.className(), attr.attr().name());
    // NOTE: guardAgainstUncertainAttribute must NOT run here -- it refuses exactly the U-typed
    // attribute this slice exists to let over. Missing per-class registrations fail closed on
    // their own when the per-slot symbol helpers resolve them.
    boolean paired = declaredValues.type().isPairedUType();
    boolean uboolean = declaredValues.type() == AttributeType.UBOOLEAN;
    boolean ustring = declaredValues.type() == AttributeType.USTRING;
    if (!paired && !uboolean && !ustring) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "U-type let over "
              + declaredValues.type()
              + " with no supported candidate-enumerable encoding");
    }
    // Per-SLOT candidate domains: a FOLDED end view's slots belong to different concrete
    // classes, each with its own registered configured domains -- the enumerating consumers
    // read the slot's own domain, so the let preserves exactly the per-concrete-class content
    // spaces. An unfolded view's slots all reuse the declared class's domains, byte-identical
    // to the single-class behavior.
    String firstComponent = paired || ustring ? "value" : "probability";
    String secondComponent = ustring ? "confidence" : paired ? "uncertainty" : null;
    List<LetBindingSource.LetSlot> slots = new ArrayList<>();
    for (int k = 0; k < destSlots.capacity(); k++) {
      VariableBinding concrete = destSlots.concreteBindings().get(k);
      String concreteClass = concrete.className();
      AttributeDomain firstDomain =
          context.attributeDomain(concreteClass, attr.attr().name(), firstComponent);
      AttributeDomain secondDomain =
          secondComponent == null
              ? null
              : context.attributeDomain(concreteClass, attr.attr().name(), secondComponent);
      SmtTerm guard = linkTerm(links, destination, navSource, k);
      SmtTerm first = valueSymbolForEndSlot(destSlots, declaredValues, attr.attr(), k);
      SmtTerm second = null;
      if (paired) {
        second = uncertaintySymbolForEndSlot(destSlots, declaredValues, attr.attr(), k);
      } else if (ustring) {
        second = confidenceSymbolForEndSlot(destSlots, declaredValues, attr.attr(), k);
      }
      slots.add(new LetBindingSource.LetSlot(first, second, guard, firstDomain, secondDomain));
    }
    boolean hasUncertainty = paired;
    String uncertaintySymbol = hasUncertainty ? symbolStem + "-uncertainty|" : null;
    SmtTerm valueTerm = selectLinkedValue(links, destination, navSource, destSlots, attr.attr(), declaredValues);
    SmtTerm uncertaintyTerm =
        hasUncertainty
            ? selectLinkedUncertainty(links, destination, navSource, destSlots, attr.attr(), declaredValues)
            : null;
    SmtTerm defined = Smt.or(linkTargets(links, destination, navSource, destSlots.capacity()));

    LetBindingSource letSource = new LetBindingSource(aliasKey, slots);
    LocalBinding binding =
        new LocalBinding(
            symbolStem + "-defined|",
            symbolStem + "-value|",
            false,
            null,
            uncertaintySymbol,
            letSource);
    Map<String, LocalBinding> extended = new LinkedHashMap<>(localBindings);
    extended.put(e.getVarname(), binding);
    TranslatedExpression body =
        translate(e.getInExpression(), context, mode, positivePolarity, Map.copyOf(extended));
    java.util.List<SmtTerm.Binding> bindings = new ArrayList<>();
    bindings.add(new SmtTerm.Binding(binding.definedSymbol(), defined));
    bindings.add(new SmtTerm.Binding(binding.valueSymbol(), valueTerm));
    if (uncertaintySymbol != null) {
      bindings.add(new SmtTerm.Binding(uncertaintySymbol, uncertaintyTerm));
    }
    // USE's ExpLet.eval is STRICT: an undefined bound value makes the whole let undefined, so
    // the navigation's definedness gates the body's, not just the variable's reads.
    return new TranslatedExpression(
        Smt.let(bindings, Smt.and(List.of(defined, body.defined()))),
        Smt.let(bindings, body.value()));
  }

  /** The link terms of one end view, in slot order (the navigation's definedness disjuncts). */
  private List<SmtTerm> linkTargets(
      AssociationLinks links, MNavigableElement destination, VariableBinding source, int capacity) {
    List<SmtTerm> targets = new ArrayList<>();
    for (int k = 0; k < capacity; k++) {
      targets.add(linkTerm(links, destination, source, k));
    }
    return targets;
  }

  /**
   * The uncertainty symbol at one destination slot of a (possibly folded) end view -- the twin
   * of {@link #valueSymbolForEndSlot} for the paired families' second component.
   */
  private SmtTerm uncertaintySymbolForEndSlot(
      ObjectSlots destSlots, AttributeValues declaredValues, MAttribute attribute, int k) {
    VariableBinding concrete = destSlots.concreteBindings().get(k);
    if (concrete.className().equals(destSlots.className())) {
      return Smt.sym(declaredValues.uncertaintyNames().get(k));
    }
    return Smt.sym(
        context.attributeValues(concrete.className(), attribute.name())
            .uncertaintyNames()
            .get(concrete.slotIndex()));
  }

  /** The confidence symbol at one destination slot -- the UString twin of the above. */
  private SmtTerm confidenceSymbolForEndSlot(
      ObjectSlots destSlots, AttributeValues declaredValues, MAttribute attribute, int k) {
    VariableBinding concrete = destSlots.concreteBindings().get(k);
    if (concrete.className().equals(destSlots.className())) {
      return Smt.sym(declaredValues.confidenceNames().get(k));
    }
    return Smt.sym(
        context.attributeValues(concrete.className(), attribute.name())
            .confidenceNames()
            .get(concrete.slotIndex()));
  }

  /**
   * The selected UNCERTAINTY at whichever destination slot {@code source} links to -- the same
   * nested-ite shape {@link #selectLinkedValue} builds for the value, applied to the paired
   * families' uncertainty component.
   */
  private SmtTerm selectLinkedUncertainty(
      AssociationLinks links,
      MNavigableElement destination,
      VariableBinding source,
      ObjectSlots destSlots,
      MAttribute attribute,
      AttributeValues v) {
    int capacity = destSlots.capacity();
    if (capacity == 0) {
      return Smt.realLit(java.math.BigDecimal.ZERO);
    }
    SmtTerm value = uncertaintySymbolForEndSlot(destSlots, v, attribute, capacity - 1);
    for (int k = capacity - 2; k >= 0; k--) {
      value =
          Smt.ite(
              linkTerm(links, destination, source, k),
              uncertaintySymbolForEndSlot(destSlots, v, attribute, k),
              value);
    }
    return value;
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
      List<String> enumeratedValues, String uncertaintySymbol,
      LetBindingSource letSource, SetContent collection) {
    /** The scalar (non-U-typed) form: no uncertainty component, no let source. */
    private LocalBinding(
        String definedSymbol, String valueSymbol, boolean stringOrEnum,
        List<String> enumeratedValues) {
      this(definedSymbol, valueSymbol, stringOrEnum, enumeratedValues, null, null, null);
    }

    /** The paired UReal/UInteger form: uncertainty without an enumerated source. */
    private LocalBinding(
        String definedSymbol, String valueSymbol, boolean stringOrEnum,
        List<String> enumeratedValues, String uncertaintySymbol) {
      this(definedSymbol, valueSymbol, stringOrEnum, enumeratedValues, uncertaintySymbol, null, null);
    }

    /** The let-source form (scalar and inlined-operation parameters). */
    private LocalBinding(
        String definedSymbol, String valueSymbol, boolean stringOrEnum,
        List<String> enumeratedValues, String uncertaintySymbol,
        LetBindingSource letSource) {
      this(definedSymbol, valueSymbol, stringOrEnum, enumeratedValues, uncertaintySymbol,
          letSource, null);
    }
  }

  /**
   * The CONSTANT element set of a let-bound Set literal ({@code let s : Set(Integer) =
   * Set{1,2,3} in ...}): the literal IS the collection's representation, duplicates collapsed
   * and constant ranges expanded, exactly as for the direct set-literal consumers. Exactly one
   * list is non-null; a Set(String) binds by CONTENT (the strings), a Set(Integer) by value.
   */
  private record SetContent(
      List<BigDecimal> reals, List<BigInteger> integers, List<String> strings) {
    /** The element count: the active list's size (exactly one list is non-null). */
    int size() {
      if (reals != null) return reals.size();
      return integers != null ? integers.size() : strings.size();
    }
  }

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
    return objectLetOverNavigation(e, navigation, null);
  }

  /**
   * Object let over a single-valued navigation initializer, optionally through a cast ({@code
   * let t : T = x.b.oclAsType(T)}): per destination slot the let variable IS the linked
   * object, so the slot's guard is the link term -- AND, when a cast target is present, the
   * slot's compile-time conformance to that target ({@code ExpAsType#eval}'s runtime check,
   * fixed per slot). A non-conforming slot contributes NOTHING: the cast is undefined there,
   * and the slot's attribute registrations may not even exist on the wrong class.
   */
  private TranslatedExpression objectLetOverNavigation(
      ExpLet e, ExpNavigation navigation, org.tzi.use.uml.mm.MClassifier castTarget) {
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
      if (castTarget != null && !bindingConformsTo(slotBinding, castTarget)) {
        continue;
      }
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
    Set<MOperation> inProgress = new java.util.HashSet<>(operationsInProgress);
    Expression receiver = arguments[0];
    // Two supported receiver shapes. A BARE VARIABLE binds self to the receiver's own slot
    // binding (one dispatch, one body). A SINGLE-VALUED NAVIGATION from a context variable
    // (x.b.op()) has no single slot to bind: the call expands per destination slot exactly the
    // way {@link #navigationObjectLet} expands a navigation-object let -- self bound to each
    // slot's CONCRETE binding (so polymorphic dispatch resolves per slot), each case gated by
    // that slot's link term. Both refusals below stay located: a let-bound scalar receiver and
    // every other receiver shape (deeper chains, collection-typed receivers) are out of slice.
    if (receiver instanceof ExpVariable targetVar
        && !localBindings.containsKey(targetVar.getVarname())) {
      VariableBinding selfBinding = context.binding(targetVar.getVarname());
      // Polymorphic dispatch: the receiver's CONCRETE class (a folded slot's own class, or the
      // static class for unfolded views) may REDEFINE the operation -- use its most specific
      // body, exactly the incumbent's runtime-type dispatch resolved at translation time via
      // the slot's concrete binding.
      MOperation dispatched = context.dispatchOperation(selfBinding.className(), operation.name());
      MOperation resolved = dispatched != null ? dispatched : operation;
      inProgress.add(resolved);
      result =
          inlineOperationBody(resolved, arguments, context.withBinding("self", selfBinding), inProgress);
      return;
    }
    if (receiver instanceof ExpVariable) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "operation call on a let-bound scalar receiver is not supported");
    }
    if (receiver instanceof ExpNavigation navigation
        && !navigation.getDestination().isCollection()
        && navigation.getObjectExpression() instanceof ExpVariable sourceVar
        && !localBindings.containsKey(sourceVar.getVarname())) {
      result =
          navigationReceiverOperation(operation, arguments, navigation, sourceVar.getVarname(), inProgress);
      return;
    }
    // DEEP RECEIVER CHAIN (x.b.c.op(), any number of single-valued hops): the chain's
    // population is built hop by hop by navigationHop -- per final-destination slot, the
    // reachability guard composes every hop's link term -- so the call expands exactly like
    // the single-hop path, with self bound to each member's CONCRETE binding (per-slot
    // dispatch) gated by the chain reachability instead of a single link term.
    if (receiver instanceof ExpNavigation navigation
        && !navigation.getDestination().isCollection()
        && navigation.getObjectExpression() instanceof ExpNavigation) {
      result = deepNavigationReceiverOperation(operation, arguments, navigation, inProgress);
      return;
    }
    throw unsupported(
        FragmentBoundary.TIER_2,
        "operation call on a receiver that is neither a bare variable, a single-valued"
            + " navigation, nor a multi-hop navigation chain from a context variable is not"
            + " yet supported");
  }

  /**
   * One inlined call body: constructs the per-call SMT lets for {@code operation}'s crisp
   * parameters from the CALLER-side arguments (arguments evaluate in the caller's scope, so
   * they translate under this translator's own context; only the BODY evaluates under
   * {@code selfContext}), then translates the already dispatch-resolved body. Shared by the
   * variable-receiver path (one call) and {@link #navigationReceiverOperation} (one call per
   * destination slot, each with that slot's dispatch-resolved operation).
   */
  private TranslatedExpression inlineOperationBody(
      MOperation operation,
      Expression[] arguments,
      TranslationContext selfContext,
      Set<MOperation> inProgress) {
    boolean parameterized = operation.paramList().size() > 0;
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
    if (parameterized) {
      return new TranslatedExpression(
          Smt.let(parameterBindings, body.defined()),
          Smt.let(parameterBindings, body.value()));
    }
    return body;
  }

  /**
   * {@code x.b.op(...)}: the navigated receiver names the LINKED object, not a slot, so the
   * inlined call expands per destination slot -- self bound to each slot's concrete binding
   * (dispatching the operation against that slot's concrete class, the same translation-time
   * polymorphic resolution the variable path gets from a folded slot), each case gated by the
   * slot's link term. Definedness and value are both {@code or}-combinations of the per-slot
   * cases exactly as {@link #navigationObjectLet} builds them for a let-bound navigation
   * initializer.
   */
  private TranslatedExpression deepNavigationReceiverOperation(
      MOperation operation,
      Expression[] arguments,
      ExpNavigation navigation,
      Set<MOperation> inProgress) {
    NavigationHop hop = navigationHop(navigation);
    List<PopulationMember> population = hop.population();
    if (population.isEmpty()) {
      // No destination slots configured: the receiver can never exist, so the call is
      // undefined and USE's total-equality rule rejects every demand on it.
      return new TranslatedExpression(Smt.bool(false), crispPlaceholder(operation.resultType()));
    }
    List<SmtTerm> definedCases = new ArrayList<>();
    List<SmtTerm> gatedValues = new ArrayList<>();
    for (PopulationMember member : population) {
      MOperation dispatched =
          context.dispatchOperation(member.binding().className(), operation.name());
      MOperation resolved = dispatched != null ? dispatched : operation;
      java.util.Set<MOperation> slotInProgress = new java.util.HashSet<>(inProgress);
      slotInProgress.add(resolved);
      TranslatedExpression body =
          inlineOperationBody(
              resolved, arguments, context.withBinding("self", member.binding()), slotInProgress);
      SmtTerm gate = Smt.and(List.of(member.memberGuard(), body.defined()));
      definedCases.add(gate);
      gatedValues.add(gate);
      gatedValues.add(body.value());
    }
    SmtTerm value = gatedValues.get(gatedValues.size() - 1);
    for (int k = gatedValues.size() - 2; k >= 0; k -= 2) {
      value = Smt.ite(gatedValues.get(k), gatedValues.get(k + 1), value);
    }
    return new TranslatedExpression(Smt.or(definedCases), value);
  }

  private TranslatedExpression navigationReceiverOperation(
      MOperation operation,
      Expression[] arguments,
      ExpNavigation navigation,
      String sourceName,
      Set<MOperation> inProgress) {
    VariableBinding source = context.binding(sourceName);
    MNavigableElement destination = resolveRedefinedDestination(navigation.getDestination(), source);
    if (destination.association() instanceof MAssociationClass) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "operation call on a receiver navigating an association class is not yet supported");
    }
    AssociationLinks links = context.linksFor(destination.association().name());
    ObjectSlots destinationSlots = destinationEndView(links, destination);
    List<SmtTerm> definedCases = new ArrayList<>();
    List<SmtTerm> gatedValues = new ArrayList<>();
    for (int k = 0; k < destinationSlots.capacity(); k++) {
      VariableBinding slotBinding = destinationSlots.concreteBindings().get(k);
      MOperation dispatched = context.dispatchOperation(slotBinding.className(), operation.name());
      MOperation resolved = dispatched != null ? dispatched : operation;
      java.util.Set<MOperation> slotInProgress = new java.util.HashSet<>(inProgress);
      slotInProgress.add(resolved);
      TranslatedExpression body =
          inlineOperationBody(
              resolved, arguments, context.withBinding("self", slotBinding), slotInProgress);
      SmtTerm link = linkTerm(links, destination, source, k);
      SmtTerm gate = Smt.and(List.of(link, body.defined()));
      definedCases.add(gate);
      gatedValues.add(gate);
      gatedValues.add(body.value());
    }
    // The VALUE cases must combine into a term of the BODY's own sort, not a boolean or():
    // the per-slot value terms are Ints (or Booleans) and the comparison consumer compares
    // them by sort. objectAnyLet's ite-chain is the established shape; the gate is each
    // slot's defined case, and at most one is true (a single-valued navigation's multiplicity
    // bounds pin at most one destination). The final fallback is the last slot's own value,
    // sort-consistent by construction and never consulted: it is read only when every gate
    // is false, i.e. when the definedness case is false and USE's total-equality rule never
    // looks at the value.
    SmtTerm value = gatedValues.get(gatedValues.size() - 1);
    for (int k = gatedValues.size() - 2; k >= 0; k -= 2) {
      value = Smt.ite(gatedValues.get(k), gatedValues.get(k + 1), value);
    }
    return new TranslatedExpression(Smt.or(definedCases), value);
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
    if (e.getRangeExpression() instanceof ExpSetLiteral set
        && set.getElemExpr().length > 0) {
      Expression first = set.getElemExpr()[0];
      if (first instanceof ExpConstInteger || first instanceof ExpRange) {
        result = oneOverIntegerSetLiteral(e, set);
        return;
      }
      if (first instanceof ExpConstString) {
        result = oneOverStringSetLiteral(e, set);
        return;
      }
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
    if (isVirtualStringSplit(collectionExpr) && elementExpr instanceof ExpConstString needle) {
      return defined(virtualSplitMembershipChain((ExpStdOp) collectionExpr, needle.value(), wantIncludes));
    }
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
    SetContent literalSet = constantCollectionContent(collectionExpr);
    if (literalSet != null) {
      TranslatedExpression element = argResult(elementExpr);
      List<SmtTerm> matches = new ArrayList<>();
      if (literalSet.strings() != null) {
        // String content: membership by CONTENT against the element's own registered domain
        // (single domain, so no cross-domain index comparison arises).
        ContentOperand elementContent = contentOperand(elementExpr);
        if (elementContent == null || elementContent.enumeratedValues() == null) {
          throw unsupported(
              FragmentBoundary.TIER_3,
              (wantIncludes ? "includes" : "excludes")
                  + " over a String-valued collection literal requires a String-typed element"
                  + " with a registered domain");
        }
        for (String constant : literalSet.strings()) {
          int idx = elementContent.enumeratedValues().indexOf(constant);
          if (idx >= 0) {
            matches.add(Smt.eq(elementContent.value(), Smt.intLit(BigInteger.valueOf(idx))));
          }
        }
      } else {
        // Integer membership: the element's value equals one of the DISTINCT literal constants
        // (total -- a literal is always defined). Membership is duplicate-insensitive, so
        // Bag/Sequence collapse is harmless HERE.
        for (BigInteger constant : literalSet.integers()) {
          matches.add(Smt.eq(element.value(), Smt.intLit(constant)));
        }
      }
      SmtTerm member = Smt.and(List.of(element.defined(), Smt.or(matches)));
      return defined(wantIncludes ? member : Smt.not(member));
    }
    SetAttrView setAttr = setAttrRead(collectionExpr);
    if (setAttr != null) {
      TranslatedExpression element = argResult(elementExpr);
      List<SmtTerm> matches = new ArrayList<>();
      for (int j = 0; j < setAttr.poolSize(); j++) {
        matches.add(
            Smt.and(
                List.of(
                    setAttr.member(j),
                    Smt.eq(element.value(), Smt.intLit(setAttr.pool().get(j))))));
      }
      SmtTerm member = Smt.and(List.of(element.defined(), Smt.or(matches)));
      return defined(wantIncludes ? member : Smt.not(member));
    }
    SetContent letSet = localCollection(collectionExpr);
    if (letSet != null) {
      TranslatedExpression element = argResult(elementExpr);
      List<SmtTerm> matches = new ArrayList<>();
      if (letSet.integers() != null) {
        for (BigInteger constant : letSet.integers()) {
          matches.add(Smt.eq(element.value(), Smt.intLit(constant)));
        }
      } else {
        ContentOperand elementContent = contentOperand(elementExpr);
        if (elementContent == null || elementContent.enumeratedValues() == null) {
          throw unsupported(
              FragmentBoundary.TIER_3,
              (wantIncludes ? "includes" : "excludes")
                  + " over a let-bound String set requires a String-typed element with a"
                  + " registered domain");
        }
        for (String constant : letSet.strings()) {
          int idx = elementContent.enumeratedValues().indexOf(constant);
          if (idx >= 0) {
            matches.add(Smt.eq(elementContent.value(), Smt.intLit(BigInteger.valueOf(idx))));
          }
        }
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
  private static List<String> distinctStringConstants(ExpCollectionLiteral set) {
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
   * collapsed per Set semantics). A constant-bounds range element contributes its whole
   * closed interval (see {@link #appendIntegerElementValues}); String or non-constant
   * elements refuse.
   */
  private static List<BigInteger> distinctIntegerConstants(ExpCollectionLiteral set) {
    List<BigInteger> values = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      if (element instanceof ExpConstString) {
        throw unsupported(
            FragmentBoundary.TIER_3,
            "Set literal with a non-constant or non-Integer element ("
                + element
                + "); only Integer constants are supported in this slice");
      }
      appendIntegerElementValues(element, values);
    }
    return values;
  }

  /**
   * The DISTINCT element count of a set literal mixing String constants with Integer
   * constants / constant-bounds ranges (the emptiness consumer only needs the count, and an
   * empty range contributes none -- {@code Set{5..4}} really is empty). Mixed Integer/String
   * content refuses, exactly as in every other set-literal consumer.
   */
  private static int distinctLiteralElementCount(ExpCollectionLiteral set) {
    List<BigInteger> ints = new ArrayList<>();
    List<String> strings = new ArrayList<>();
    for (Expression element : set.getElemExpr()) {
      if (element instanceof ExpConstString constant) {
        if (!strings.contains(constant.value())) {
          strings.add(constant.value());
        }
      } else {
        appendIntegerElementValues(element, ints);
      }
    }
    if (!ints.isEmpty() && !strings.isEmpty()) {
      throw unsupported(
          FragmentBoundary.TIER_3,
          "Set literal mixes Integer and String constants; not supported in this slice");
    }
    return ints.size() + strings.size();
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
