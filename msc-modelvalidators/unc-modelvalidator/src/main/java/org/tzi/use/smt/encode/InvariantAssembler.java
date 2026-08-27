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
 * Assembles one class invariant's full SATISFY contribution. Its context variable is an implicit
 * quantifier over every EXISTING instance of its context class -- including every subclass's
 * instances, exactly like {@code X.allInstances()} itself (see {@link PolymorphicRange}) -- so the
 * result is an existence-guarded per-slot combination, not a single fixed binding. Every
 * translation test through Task 3.4c deliberately bound the context variable to one fixed slot to
 * isolate the construct under test; this is the first place that changes.
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
    String contextVar = contextVariableOf(invariant);
    List<SmtTerm> valueConjuncts = new ArrayList<>();
    List<SmtTerm> definedConjuncts = new ArrayList<>();
    List<SmtTerm> witnessCandidates = new ArrayList<>();
    boolean existential = invariant.isExistential();
    for (PolymorphicRange.Slot slot : PolymorphicRange.slotsOf(invariant.cls(), baseContext)) {
      TranslationContext extended = baseContext.withBinding(contextVar, slot.binding());
      TranslatedExpression body =
          ExpressionTranslator.translate(invariant.bodyExpression(), extended, mode);
      SmtTerm exists = Smt.sym(slot.existsName());
      definedConjuncts.add(Smt.app("=>", exists, body.defined()));
      if (existential) {
        // One EXISTING instance with a defined-true body is enough, and is what makes the whole
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
   * The invariant's single context variable.
   *
   * <p>{@code hasVar()} is always true for an invariant parsed from a {@code .use} file -- {@code
   * ASTInvariantClause.gen} supplies the pseudo-variable {@code "self"} whenever no context
   * variable is written -- so the implicit-self case is not an unsupported shape at all, only a
   * differently-spelled one, and it is handled rather than refused. A multi-variable context is a
   * genuinely different quantifier structure this translation slice does not encode, and fails
   * closed by name instead of by accident.
   */
  private static String contextVariableOf(MClassInvariant invariant) {
    if (!invariant.hasVar()) {
      return "self";
    }
    if (invariant.vars().size() != 1) {
      throw new SmtTranslationException(
          "invariant '"
              + invariant.qualifiedName()
              + "' has "
              + invariant.vars().size()
              + " context variables ("
              + invariant.var()
              + "); only a single context variable is supported in this translation slice");
    }
    return invariant.var();
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
