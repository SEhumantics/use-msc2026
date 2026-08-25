package org.tzi.use.smt.verify;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Independently re-checks a reconstructed state with USE's own OCL evaluator -- the ground truth
 * our SMT translation (Tasks 3.1-3.5) is checked against, not assumed to agree with. A
 * solved-but-unchecked witness is not evidence the translation was correct; mirrors the standard
 * {@code kk-modelvalidator}'s own {@code EndToEndValidationTest} already holds itself to.
 */
public final class InvariantReEvaluator {
  private InvariantReEvaluator() {}

  public static List<InvariantVerdict> reevaluate(MModel model, MSystem system) {
    MSystemState state = system.state();
    List<InvariantVerdict> verdicts = new ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      EvalContext ctx = new EvalContext(state, state, system.varBindings(), null, "");
      Value result = invariant.expandedExpression().eval(ctx);
      if (!(result instanceof BooleanValue bool)) {
        throw new IllegalStateException(
            "invariant '"
                + invariant.qualifiedName()
                + "' evaluated to a non-Boolean result: "
                + result);
      }
      verdicts.add(new InvariantVerdict(invariant.qualifiedName(), bool.isTrue()));
    }
    return verdicts;
  }
}
