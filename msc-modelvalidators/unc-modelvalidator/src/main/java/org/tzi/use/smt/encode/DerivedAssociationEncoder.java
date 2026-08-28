package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.ExpAllInstances;
import org.tzi.use.uml.ocl.expr.ExpAny;
import org.tzi.use.uml.ocl.expr.VarDeclList;

/**
 * A derived association end whose declared derivation expression is EXACTLY {@code
 * T.allInstances()->any(x | predicate)} -- the "foreign-key lookup" idiom {@code
 * benchmark/examples/CompanyERSchema/CompanyER.use} builds its entire schema out of (all twelve
 * of its associations are declared this way, e.g. {@code FK_Component_Contained}'s {@code
 * containedPart derived = Part.allInstances()->any(p|p.pname=self.containedname)}) -- is NOT
 * arbitrary derivation, and does not need general OCL-expression-to-constraint evaluation to
 * support: it is exactly the same "finite disjunction over candidate slots, select the first
 * matching one in stable order" shape {@link ExpressionTranslator}'s own {@code objectAnyLet}
 * already implements and tests for {@code let x = T.allInstances()->any(pred) in body}. This
 * class generalizes that same selection logic from "one predicate translated once" to "one
 * predicate translated once per (source, candidate) pair, wired into an ordinary {@link
 * AssociationLinks} grid" so every existing reader (navigation, populationOf, reconstruction)
 * keeps working against it unmodified -- the grid's cells are simply ASSERTED equal to the
 * selection formula instead of left free for the solver to choose, rather than the association
 * being given no grid at all or refused outright.
 *
 * <p>Deliberately narrow, matching the rest of this association-scope loop's own established
 * scope: exactly one derive-parameter (the implicit {@code self}, USE's own short-notation
 * default for a binary association -- see {@code ASTAssociationEnd#genDerived}), exactly one
 * {@code any} iterator, and the {@code allInstances()} source class must be a single,
 * non-polymorphic {@link ObjectSlots} pool -- the SAME "no subclass-slot folding" limitation
 * {@code assoc.type-multiplicity-with-inheritance} already documents for ordinary associations,
 * not a new one introduced here. Any derivation expression outside this exact shape returns
 * empty, letting the caller fall back to the existing clean refusal rather than silently
 * approximating something this encoder cannot actually compute.
 */
public final class DerivedAssociationEncoder {
  private DerivedAssociationEncoder() {}

  /**
   * @param sourceEnd the OTHER (non-derived) end of the association -- {@code self}'s declared
   *     type, per USE's own short-notation derive-parameter default.
   * @param sourceSlots {@code sourceEnd}'s own {@link ObjectSlots} pool.
   * @param derivedEnd the derived end itself.
   * @param derivedSlots {@code derivedEnd}'s own {@link ObjectSlots} pool -- the {@code any}
   *     selection's candidate population.
   * @return the constrained grid, with {@code sourceSlots} as {@link AssociationLinks#aEnd} and
   *     {@code derivedSlots} as {@link AssociationLinks#bEnd} (regardless of which end is
   *     declared first in the model -- the caller repositions if the model declares them the
   *     other way around), or empty if the derive expression is not exactly {@code
   *     T.allInstances()->any(x | predicate)} over {@code derivedEnd}'s own class with a single
   *     iterator and the implicit single-parameter {@code self} short notation.
   */
  public static Optional<AssociationLinks> encodeAnyMatch(
      SmtScript script,
      String associationName,
      MAssociationEnd sourceEnd,
      ObjectSlots sourceSlots,
      MAssociationEnd derivedEnd,
      ObjectSlots derivedSlots,
      TranslationContext baseContext,
      TranslationMode mode) {
    Expression deriveExpression = derivedEnd.getDeriveExpression();
    if (!(deriveExpression instanceof ExpAny any)
        || !(any.getRangeExpression() instanceof ExpAllInstances all)) {
      return Optional.empty();
    }
    if (any.getVariableDeclarations().size() != 1) {
      return Optional.empty();
    }
    if (!all.getSourceType().name().equals(derivedEnd.cls().name())) {
      return Optional.empty();
    }
    VarDeclList deriveParameter = derivedEnd.getDeriveParamter();
    if (deriveParameter == null || deriveParameter.size() != 1) {
      return Optional.empty();
    }
    String selfName = deriveParameter.varDecl(0).name();
    String iteratorName = any.getVariableDeclarations().varDecl(0).name();
    Expression predicateExpression = any.getQueryExpression();

    String[][] names = new String[sourceSlots.capacity()][derivedSlots.capacity()];
    for (int i = 0; i < sourceSlots.capacity(); i++) {
      TranslationContext selfBound =
          baseContext.withBinding(selfName, new VariableBinding(sourceEnd.cls().name(), i));
      List<SmtTerm> priorMatches = new ArrayList<>();
      for (int j = 0; j < derivedSlots.capacity(); j++) {
        TranslationContext predicateContext =
            selfBound.withBinding(
                iteratorName, new VariableBinding(derivedEnd.cls().name(), j));
        TranslatedExpression predicate =
            ExpressionTranslator.translate(predicateExpression, predicateContext, mode);
        SmtTerm match =
            Smt.and(List.of(Smt.sym(derivedSlots.existsNames().get(j)), predicate.trueTerm()));
        SmtTerm selected = Smt.and(List.of(match, Smt.not(Smt.or(priorMatches))));
        priorMatches.add(match);

        String cellName = associationName + "_" + i + "_" + j;
        script.declareConst(cellName, SmtSort.BOOL);
        names[i][j] = cellName;
        script.assertThat(Smt.eq(Smt.sym(cellName), selected));
      }
    }
    return Optional.of(new AssociationLinks(associationName, sourceSlots, derivedSlots, names));
  }
}
