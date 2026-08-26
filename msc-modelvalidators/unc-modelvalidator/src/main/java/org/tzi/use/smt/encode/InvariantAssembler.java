package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
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
    return classify(invariant, baseContext, TranslationMode.UNCERTAIN).trueTerm();
  }

  public static TranslatedExpression classify(
      MClassInvariant invariant, TranslationContext baseContext, TranslationMode mode) {
    if (!invariant.hasVar()) {
      throw new SmtTranslationException(
          "invariant '"
              + invariant.name()
              + "' has no named context variable (implicit self) - not yet supported");
    }
    String contextVar = invariant.var();
    List<SmtTerm> valueConjuncts = new ArrayList<>();
    List<SmtTerm> definedConjuncts = new ArrayList<>();
    List<SmtTerm> falseCandidates = new ArrayList<>();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(invariant.cls(), baseContext)) {
      TranslationContext extended = baseContext.withBinding(contextVar, slot.binding());
      TranslatedExpression body =
          ExpressionTranslator.translate(invariant.bodyExpression(), extended, mode);
      SmtTerm exists = Smt.sym(slot.existsName());
      valueConjuncts.add(Smt.app("=>", exists, body.value()));
      definedConjuncts.add(Smt.app("=>", exists, body.defined()));
      falseCandidates.add(
          Smt.and(List.of(exists, body.defined(), Smt.not(body.value()))));
    }
    SmtTerm defined =
        Smt.or(List.of(Smt.or(falseCandidates), Smt.and(definedConjuncts)));
    return new TranslatedExpression(defined, Smt.and(valueConjuncts));
  }

  /** Declares and binds the named classification pair exactly once for query reuse. */
  public static InvariantClassification reify(
      SmtScript script,
      MClassInvariant invariant,
      TranslationContext baseContext,
      TranslationMode mode) {
    TranslatedExpression translated = classify(invariant, baseContext, mode);
    String stem =
        mode.name().toLowerCase()
            + "_"
            + invariant.qualifiedName().replaceAll("[^A-Za-z0-9_]", "_");
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
