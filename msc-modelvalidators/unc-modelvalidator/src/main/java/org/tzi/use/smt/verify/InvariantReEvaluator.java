package org.tzi.use.smt.verify;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Independently re-checks a reconstructed state with USE's own OCL evaluator -- the ground truth
 * our SMT translation (Tasks 3.1-3.5) is checked against, not assumed to agree with. A
 * solved-but-unchecked witness is not evidence the translation was correct; mirrors the standard
 * {@code kk-modelvalidator}'s own {@code EndToEndValidationTest} already holds itself to.
 *
 * <p>Phase 4.3 makes the verdict three-valued. Evaluating {@link
 * MClassInvariant#expandedExpression()} cannot do that: USE's own {@code forAll} maps an undefined
 * body element to {@code false} ({@code ExpQuery.evalForAll0}), so an undefined invariant and a
 * genuinely violated one both come back as {@code BooleanValue.FALSE}. This evaluator therefore
 * drives USE's evaluator over the invariant's BODY once per instance -- where undefinedness is
 * still visible -- and recombines the per-instance results with the same three-valued rule {@link
 * org.tzi.use.smt.encode.InvariantAssembler#classify} encodes into SMT: a defined-false instance
 * makes the whole invariant defined-false, otherwise any undefined instance makes it undefined,
 * otherwise it is true. Vacuously (no instances) it is true, exactly as {@code forAll} over an
 * empty range is.
 */
public final class InvariantReEvaluator {
  private InvariantReEvaluator() {}

  public static List<InvariantVerdict> reevaluate(MModel model, MSystem system) {
    MSystemState state = system.state();
    List<InvariantVerdict> verdicts = new ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      verdicts.add(new InvariantVerdict(invariant.qualifiedName(), classify(invariant, system)));
    }
    return verdicts;
  }

  private static InvariantOutcome classify(MClassInvariant invariant, MSystem system) {
    MSystemState state = system.state();
    if (!invariant.hasVar() || invariant.isExistential() || invariant.vars().size() != 1) {
      // Shapes the SMT side does not translate either (InvariantAssembler.classify requires a
      // single named context variable): fall back to USE's own expansion rather than invent a
      // three-valued reading of something we cannot cross-check.
      EvalContext ctx = new EvalContext(state, state, system.varBindings(), null, "");
      return outcomeOf(invariant, invariant.expandedExpression().eval(ctx));
    }
    if (!(invariant.cls() instanceof MClass contextClass)) {
      throw new IllegalStateException(
          "invariant '" + invariant.qualifiedName() + "' has a non-class context");
    }
    boolean anyUndefined = false;
    for (MObject object : state.objectsOfClassAndSubClasses(contextClass)) {
      EvalContext ctx = new EvalContext(state, state, system.varBindings(), null, "");
      ctx.pushVarBinding(invariant.var(), new ObjectValue(object.cls(), object));
      InvariantOutcome perInstance = outcomeOf(invariant, invariant.bodyExpression().eval(ctx));
      if (perInstance == InvariantOutcome.FALSE) {
        return InvariantOutcome.FALSE;
      }
      anyUndefined |= perInstance == InvariantOutcome.UNDEFINED;
    }
    return anyUndefined ? InvariantOutcome.UNDEFINED : InvariantOutcome.TRUE;
  }

  private static InvariantOutcome outcomeOf(MClassInvariant invariant, Value result) {
    if (result.isUndefined()) {
      return InvariantOutcome.UNDEFINED;
    }
    if (!(result instanceof BooleanValue bool)) {
      throw new IllegalStateException(
          "invariant '"
              + invariant.qualifiedName()
              + "' evaluated to a non-Boolean result: "
              + result);
    }
    return bool.isTrue() ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
  }
}
