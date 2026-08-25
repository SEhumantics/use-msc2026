package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MClassInvariant;

/**
 * Assembles one class invariant's full SATISFY contribution: its own context variable is an
 * implicit universal quantifier over every EXISTING instance of its context class, exactly like
 * Task 3.4b's ForAll -- existence-guarded per-slot conjunction, not a single fixed binding. Every
 * translation test through Task 3.4c deliberately bound the context variable to one fixed slot to
 * isolate the construct under test; this is the first place that changes.
 */
public final class InvariantAssembler {
  private InvariantAssembler() {}

  public static SmtTerm assemble(MClassInvariant invariant, TranslationContext baseContext) {
    if (!invariant.hasVar()) {
      throw new SmtTranslationException(
          "invariant '"
              + invariant.name()
              + "' has no named context variable (implicit self) - not yet supported");
    }
    String contextVar = invariant.var();
    String contextClass = invariant.cls().name();
    ObjectSlots slots = baseContext.slotsFor(contextClass);

    List<SmtTerm> conjuncts = new ArrayList<>();
    for (int i = 0; i < slots.capacity(); i++) {
      TranslationContext extended =
          baseContext.withBinding(contextVar, new VariableBinding(contextClass, i));
      SmtTerm body = ExpressionTranslator.translate(invariant.bodyExpression(), extended);
      conjuncts.add(Smt.app("=>", Smt.sym(slots.existsNames().get(i)), body));
    }
    return Smt.and(conjuncts);
  }
}
