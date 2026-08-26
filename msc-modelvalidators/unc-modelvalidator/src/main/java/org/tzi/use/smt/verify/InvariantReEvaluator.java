package org.tzi.use.smt.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.VarDeclList;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Independently re-checks a reconstructed state with USE's own OCL evaluator -- the ground truth
 * our SMT translation is checked against, not assumed to agree with. A solved-but-unchecked witness
 * is not evidence the translation was correct; this mirrors the standard {@code
 * kk-modelvalidator}'s own {@code EndToEndValidationTest} already holds itself to.
 *
 * <p>The verdict is three-valued, and USE's own {@code Expression.eval} cannot produce that: {@code
 * ExpQuery.evalForAll0}/{@code evalExists0} rewrite an undefined element to {@code
 * BooleanValue.FALSE} at EVERY quantifier level, so an undefined invariant and a genuinely violated
 * one both come back as {@code BooleanValue.FALSE}. Driving the evaluator over the invariant's BODY
 * once per instance fixes only the OUTERMOST collapse; a nested {@code forAll}/{@code exists} in
 * the body still collapses. {@link ThreeValuedEvaluator} therefore reads the body at full depth,
 * and this class supplies the invariant's own implicit context quantifier around it, with the same
 * three-valued rule {@link org.tzi.use.smt.encode.InvariantAssembler#classify} encodes into SMT:
 *
 * <ul>
 *   <li>ordinary {@code inv} -- universal: a defined-false instance makes the whole invariant
 *       defined-false, otherwise any undefined instance makes it undefined, otherwise it is true.
 *       With no instances it is vacuously true, exactly as {@code forAll} over an empty range is.
 *   <li>{@code existential inv} -- existential: a defined-true instance makes it defined-true,
 *       otherwise any undefined instance makes it undefined, otherwise it is false. With no
 *       instances it is FALSE, exactly as {@code exists} over an empty range is.
 * </ul>
 *
 * <p>A multi-variable context ({@code context p1, p2 : P inv ...}) is the same rule over the
 * cartesian product of the instance range, which is how USE itself expands it. It used to fall back
 * to {@code expandedExpression().eval(...)} and so invented a definite FALSE for a genuinely
 * undefined invariant.
 */
public final class InvariantReEvaluator {
  private InvariantReEvaluator() {}

  /** Every invariant of the model, read U-AWARE -- the reading every pre-4.5 caller wants. */
  public static List<InvariantVerdict> reevaluate(MModel model, MSystem system) {
    return reevaluate(model, system, TranslationMode.UNCERTAIN, null);
  }

  /**
   * The same implicit context quantifier in either translation mode, over an optional subset of the
   * model's invariants.
   *
   * <p>The context quantifier itself is mode-independent on purpose: {@code
   * InvariantAssembler.classify} encodes ONE quantifier structure and selects the mode only inside
   * the body, so an oracle that quantified differently per mode would disagree with the solver for
   * a reason that has nothing to do with erasure. Only the body reading differs -- {@link
   * ThreeValuedEvaluator} for {@code UNCERTAIN}, {@link NominalErasureEvaluator} for {@code
   * NOMINAL}.
   *
   * @param onlyThese the qualified invariant names to classify, or {@code null} for all of them.
   *     NOMINAL mode is deliberately asked for by name: an invariant with no type-directed erasure
   *     rule is UNSUPPORTED, and refusing one the query never mentioned would be a fabricated
   *     failure.
   */
  public static List<InvariantVerdict> reevaluate(
      MModel model, MSystem system, TranslationMode mode, Set<String> onlyThese) {
    List<InvariantVerdict> verdicts = new ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      if (onlyThese != null && !onlyThese.contains(invariant.qualifiedName())) {
        continue;
      }
      verdicts.add(
          new InvariantVerdict(invariant.qualifiedName(), classify(invariant, system, mode)));
    }
    return verdicts;
  }

  private static InvariantOutcome classify(
      MClassInvariant invariant, MSystem system, TranslationMode mode) {
    MSystemState state = system.state();
    if (!(invariant.cls() instanceof MClass contextClass)) {
      throw new IllegalStateException(
          "invariant '" + invariant.qualifiedName() + "' has a non-class context");
    }
    List<MObject> instances = new ArrayList<>(state.objectsOfClassAndSubClasses(contextClass));
    EvalContext ctx = new EvalContext(state, state, system.varBindings(), null, "");
    return overInstances(invariant, contextVariablesOf(invariant), 0, instances, ctx, mode);
  }

  /**
   * The invariant's context variable names.
   *
   * <p>{@code hasVar()} is false only when no context variable was written, and never for an
   * invariant parsed from a {@code .use} file at all: {@code ASTInvariantClause.gen} supplies the
   * pseudo-variable {@code "self"} in that case, so {@code hasVar()} is always true there. The
   * implicit-self spelling is therefore handled by name rather than treated as an untranslatable
   * shape needing a separate, weaker fallback.
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

  private static InvariantOutcome overInstances(
      MClassInvariant invariant,
      List<String> variables,
      int nesting,
      List<MObject> instances,
      EvalContext ctx,
      TranslationMode mode) {
    boolean existential = invariant.isExistential();
    InvariantOutcome decisive = existential ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
    boolean anyUndefined = false;
    for (MObject instance : instances) {
      ctx.pushVarBinding(variables.get(nesting), new ObjectValue(instance.cls(), instance));
      InvariantOutcome outcome;
      try {
        outcome =
            nesting < variables.size() - 1
                ? overInstances(invariant, variables, nesting + 1, instances, ctx, mode)
                : mode == TranslationMode.NOMINAL
                    ? NominalErasureEvaluator.eval(invariant.bodyExpression(), ctx)
                    : ThreeValuedEvaluator.eval(invariant.bodyExpression(), ctx);
      } finally {
        // See ThreeValuedEvaluator: popVarBinding() is package-private in use-core.
        ctx.varBindings().pop();
      }
      if (outcome == decisive) {
        return decisive;
      }
      anyUndefined |= outcome == InvariantOutcome.UNDEFINED;
    }
    if (anyUndefined) {
      return InvariantOutcome.UNDEFINED;
    }
    return existential ? InvariantOutcome.FALSE : InvariantOutcome.TRUE;
  }
}
