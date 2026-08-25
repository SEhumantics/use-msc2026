package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.mm.MClassInvariant;

/**
 * Assembles one class invariant's full SATISFY contribution: its own context variable is an
 * implicit universal quantifier over every EXISTING instance of its context class -- including
 * every subclass's instances, exactly like {@code X.allInstances()} itself (see {@link
 * PolymorphicRange}) -- existence-guarded per-slot conjunction, not a single fixed binding. Every
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
    List<SmtTerm> conjuncts = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(invariant.cls(), baseContext)) {
      TranslationContext extended = baseContext.withBinding(contextVar, slot.binding());
      SmtTerm body = ExpressionTranslator.translate(invariant.bodyExpression(), extended);
      conjuncts.add(Smt.app("=>", Smt.sym(slot.existsName()), body));
    }
    return Smt.and(conjuncts);
  }
}
