package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.ocl.expr.VarDeclList;

/**
 * Assembles one class invariant's full SATISFY contribution. Its context variables are an implicit
 * quantifier over every EXISTING instance of its context class -- including every subclass's
 * instances, exactly like {@code X.allInstances()} itself (see {@link PolymorphicRange}) -- so the
 * result is an existence-guarded per-slot combination, not a single fixed binding. Every
 * translation test through Task 3.4c deliberately bound the context variable to one fixed slot to
 * isolate the construct under test; this is the first place that changes.
 *
 * <p>An invariant may declare SEVERAL context variables ({@code context e1, e2 : Employee inv
 * ...}), and USE expands that as the SAME quantifier over the CARTESIAN PRODUCT of the instance
 * range -- the diagonal included, which is why the corpus shapes all open with {@code e1 <> e2
 * implies ...}. Each combination is guarded by the conjunction of the existence flags of the slots
 * it binds, one per variable, so a combination touching a non-existing slot contributes nothing in
 * either direction. {@link org.tzi.use.smt.verify.InvariantReEvaluator} has expanded exactly this
 * product since Milestone 4.5, so every witness of a multi-variable invariant is still
 * independently re-checked by machinery that already understood the shape.
 *
 * <p>With one variable the product is the range itself and the guard conjunction collapses to the
 * bare existence flag ({@code Smt.and} of a singleton is that singleton), so the emitted script for
 * a one-variable invariant is byte-identical to the pre-generalisation encoder's -- asserted
 * verbatim by {@code
 * MultiVariableContextInvariantTest#theSingleContextVariableEmissionIsByteIdenticalToThePreGeneralisationEncoder}.
 *
 * <p>That quantifier is UNIVERSAL for an ordinary {@code inv} and EXISTENTIAL for USE's own {@code
 * existential inv} -- real USE syntax (USEBase.gpart:436), parsed by {@code
 * ASTExistentialInvariantClause} into an {@link MClassInvariant} whose {@link
 * MClassInvariant#isExistential()} is true, and expanded by USE itself as {@code
 * C.allInstances()->exists(...)} rather than {@code forAll}. Encoding one as the other is a silent
 * mistranslation, not a missing feature, so it is encoded rather than guessed at.
 */
public final class InvariantAssembler {
  private InvariantAssembler() {}

  public static SmtTerm assemble(MClassInvariant invariant, TranslationContext baseContext) {
    return classify(invariant, baseContext, TranslationMode.UNCERTAIN).trueTerm();
  }

  public static TranslatedExpression classify(
      MClassInvariant invariant, TranslationContext baseContext, TranslationMode mode) {
    List<String> contextVars = contextVariablesOf(invariant);
    List<SmtTerm> valueConjuncts = new ArrayList<>();
    List<SmtTerm> definedConjuncts = new ArrayList<>();
    List<SmtTerm> witnessCandidates = new ArrayList<>();
    boolean existential = invariant.isExistential();
    List<PolymorphicRange.Slot> range = PolymorphicRange.slotsOf(invariant.cls(), baseContext);
    for (List<PolymorphicRange.Slot> combination : cartesianProduct(range, contextVars.size())) {
      TranslationContext extended = baseContext;
      // One guard per VARIABLE, not one for the combination's first slot: a combination is live
      // only when every slot it binds exists. Repeated slots (the diagonal) contribute the same
      // flag once, which is what keeps the one-variable emission unchanged.
      LinkedHashSet<String> existsNames = new LinkedHashSet<>();
      for (int i = 0; i < contextVars.size(); i++) {
        PolymorphicRange.Slot slot = combination.get(i);
        extended = extended.withBinding(contextVars.get(i), slot.binding());
        existsNames.add(slot.existsName());
      }
      TranslatedExpression body =
          ExpressionTranslator.translate(invariant.bodyExpression(), extended, mode);
      SmtTerm exists = Smt.and(existsNames.stream().map(Smt::sym).toList());
      definedConjuncts.add(Smt.app("=>", exists, body.defined()));
      if (existential) {
        // One EXISTING combination with a defined-true body is enough, and is what makes the whole
        // invariant defined -- the exact dual of the universal case below, and the same rule
        // ExpressionTranslator.visitExists already encodes for an inner exists.
        witnessCandidates.add(Smt.and(List.of(exists, body.defined(), body.value())));
      } else {
        valueConjuncts.add(Smt.app("=>", exists, body.value()));
        witnessCandidates.add(Smt.and(List.of(exists, body.defined(), Smt.not(body.value()))));
      }
    }
    SmtTerm decisive = Smt.or(witnessCandidates);
    SmtTerm defined = Smt.or(List.of(decisive, Smt.and(definedConjuncts)));
    // OCL's exists over an empty range is FALSE (no witness), while forAll over an empty range is
    // vacuously TRUE -- which is exactly what the empty disjunction and the empty conjunction
    // already give, so the two branches need no separate empty-population special case.
    return new TranslatedExpression(defined, existential ? decisive : Smt.and(valueConjuncts));
  }

  /**
   * Every combination of {@code arity} slots drawn from {@code range}, in lexicographic order with
   * the FIRST context variable varying slowest -- the same nesting order {@link
   * org.tzi.use.smt.verify.InvariantReEvaluator} recurses in, so the two sides enumerate the
   * product identically. At arity 1 this is the range itself, one slot per combination.
   */
  private static List<List<PolymorphicRange.Slot>> cartesianProduct(
      List<PolymorphicRange.Slot> range, int arity) {
    List<List<PolymorphicRange.Slot>> combinations = new ArrayList<>();
    combinations.add(List.of());
    for (int i = 0; i < arity; i++) {
      List<List<PolymorphicRange.Slot>> extended = new ArrayList<>();
      for (List<PolymorphicRange.Slot> prefix : combinations) {
        for (PolymorphicRange.Slot slot : range) {
          List<PolymorphicRange.Slot> longer = new ArrayList<>(prefix);
          longer.add(slot);
          extended.add(longer);
        }
      }
      combinations = extended;
    }
    return combinations;
  }

  /**
   * The invariant's context variable names, in declaration order.
   *
   * <p>{@code hasVar()} is always true for an invariant parsed from a {@code .use} file -- {@code
   * ASTInvariantClause.gen} supplies the pseudo-variable {@code "self"} whenever no context
   * variable is written -- so the implicit-self case is not an unsupported shape at all, only a
   * differently-spelled one, and it is handled rather than refused. A multi-variable context is a
   * genuinely different quantifier structure and is encoded as such (see the class javadoc), not
   * refused and not silently collapsed onto the first variable.
   */
  private static List<String> contextVariablesOf(MClassInvariant invariant) {
    if (!invariant.hasVar()) {
      return List.of("self");
    }
    VarDeclList declarations = invariant.vars();
    List<String> names = new ArrayList<>(declarations.size());
    for (int i = 0; i < declarations.size(); i++) {
      names.add(declarations.varDecl(i).name());
    }
    return names;
  }

  /** Declares and binds the named classification pair exactly once for query reuse. */
  public static InvariantClassification reify(
      SmtScript script,
      MClassInvariant invariant,
      TranslationContext baseContext,
      TranslationMode mode) {
    return reify(script, invariant, baseContext, mode, "");
  }

  /**
   * The same reification, in one named SCENARIO COPY.
   *
   * <p>UNIFORM asserts {@code W_Q(S,s)} for every configured {@code s} inside ONE solve, so the
   * per-invariant {@code def}/{@code val} symbols exist once per scenario -- they genuinely differ,
   * because the scenario's uncertainty symbols differ. The snapshot symbols they are computed FROM
   * (object existence, links, representative values) are shared, which is exactly what makes
   * UNIFORM stronger than COVER. The suffix is empty for the ordinary single-copy encoding, keeping
   * the emitted names byte-identical to every pre-4.6 run.
   */
  public static InvariantClassification reify(
      SmtScript script,
      MClassInvariant invariant,
      TranslationContext baseContext,
      TranslationMode mode,
      String scenarioSuffix) {
    TranslatedExpression translated = classify(invariant, baseContext, mode);
    String stem =
        mode.name().toLowerCase()
            + "_"
            + invariant.qualifiedName().replaceAll("[^A-Za-z0-9_]", "_")
            + scenarioSuffix;
    String definedName = "def_" + stem;
    String valueName = "val_" + stem;
    script.declareConst(definedName, SmtSort.BOOL);
    script.declareConst(valueName, SmtSort.BOOL);
    script.assertThat(Smt.eq(Smt.sym(definedName), translated.defined()));
    script.assertThat(Smt.eq(Smt.sym(valueName), translated.value()));
    return new InvariantClassification(
        invariant.qualifiedName(),
        mode,
        definedName,
        valueName,
        Smt.sym(definedName),
        Smt.sym(valueName));
  }
}
