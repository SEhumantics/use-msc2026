package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

public class InvariantClassificationTest {

  @Test
  public void definedFalseAndUndefinedAreDistinctReifiedOutcomesInBothModes() throws Exception {
    MModel model =
        compile(
            """
            model Outcomes
            class Sample
            end
            constraints
            context self : Sample inv TrueInv: true
            context self : Sample inv FalseInv: false
            context self : Sample inv UndefinedInv: oclUndefined(Boolean)
            """,
            "Outcomes");

    for (TranslationMode mode : TranslationMode.values()) {
      SmtScript script = new SmtScript("QF_LIA");
      ObjectSlots slots =
          ObjectSlotEncoder.encode(script, List.of(new ClassScope("Sample", 1, 1))).get("Sample");
      TranslationContext context =
          new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of("Sample", slots), Map.of());
      InvariantClassification falseClassification =
          InvariantAssembler.reify(script, invariant(model, "FalseInv"), context, mode);
      InvariantClassification undefinedClassification =
          InvariantAssembler.reify(script, invariant(model, "UndefinedInv"), context, mode);
      InvariantClassification trueClassification =
          InvariantAssembler.reify(script, invariant(model, "TrueInv"), context, mode);

      assertTrue(script.declaredNames().contains(falseClassification.definedName()));
      assertTrue(script.declaredNames().contains(falseClassification.valueName()));
      script.assertThat(Smt.sym("Sample_0_exists"));
      script.assertThat(falseClassification.falseTerm());
      script.assertThat(undefinedClassification.undefinedTerm());
      script.assertThat(trueClassification.trueTerm());
      assertEquals(mode.toString(), SolverOutcome.SAT, solve(script));

      assertThreeOutcomesAreMutuallyExclusive(falseClassification);
      assertThreeOutcomesAreMutuallyExclusive(undefinedClassification);
      assertThreeOutcomesAreMutuallyExclusive(trueClassification);
    }
  }

  @Test
  public void nominalErasureAccepts031WhileUncertainThresholdRejectsIt() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));
    MClassInvariant invariant = invariant(model, "ReliablyFast");
    SmtScript script = new SmtScript("QF_LIRA");
    ObjectSlots slots =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("UnidentifiedObject", 1, 1)))
            .get("UnidentifiedObject");
    AttributeDomain valueDomain =
        new AttributeDomain("UnidentifiedObject", "speed", "value", List.of("0.31"), null, null);
    AttributeDomain uncertaintyDomain =
        new AttributeDomain(
            "UnidentifiedObject", "speed", "uncertainty", List.of("0.02"), null, null);
    AttributeValues values =
        AttributeEncoder.encodeUType(
            script, slots, "speed", AttributeType.UREAL, valueDomain, uncertaintyDomain);
    TranslationContext context =
        new TranslationContext(
            Map.of(),
            Map.of("UnidentifiedObject.speed", values),
            Map.of(
                "UnidentifiedObject.speed.value",
                valueDomain,
                "UnidentifiedObject.speed.uncertainty",
                uncertaintyDomain),
            Map.of("UnidentifiedObject", slots),
            Map.of());

    InvariantClassification nominal =
        InvariantAssembler.reify(script, invariant, context, TranslationMode.NOMINAL);
    InvariantClassification uncertain =
        InvariantAssembler.reify(script, invariant, context, TranslationMode.UNCERTAIN);
    script.assertThat(Smt.sym("UnidentifiedObject_0_exists"));
    script.assertThat(nominal.trueTerm());
    script.assertThat(uncertain.falseTerm());

    assertEquals(SolverOutcome.SAT, solve(script));
    assertFalse(nominal.definedName().equals(uncertain.definedName()));
    assertFalse(nominal.valueName().equals(uncertain.valueName()));
  }

  @Test
  public void booleanOperatorsUseUseStyleDominanceInsteadOfStrictUndefinedPropagation()
      throws Exception {
    MModel model =
        compile(
            """
            model BooleanDefinedness
            class Sample
            end
            constraints
            context self : Sample inv FalseDominatesAnd: false and oclUndefined(Boolean)
            context self : Sample inv AndUndefined: true and oclUndefined(Boolean)
            context self : Sample inv TrueDominatesOr: true or oclUndefined(Boolean)
            context self : Sample inv OrUndefined: false or oclUndefined(Boolean)
            context self : Sample inv TrueDominatesImplies: oclUndefined(Boolean) implies true
            context self : Sample inv ImpliesUndefined: oclUndefined(Boolean) implies false
            """,
            "BooleanDefinedness");

    for (TranslationMode mode : TranslationMode.values()) {
      SmtScript script = new SmtScript("QF_LIA");
      ObjectSlots slots =
          ObjectSlotEncoder.encode(script, List.of(new ClassScope("Sample", 1, 1))).get("Sample");
      TranslationContext context =
          new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of("Sample", slots), Map.of());
      script.assertThat(Smt.sym("Sample_0_exists"));
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "FalseDominatesAnd"), context, mode)
              .falseTerm());
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "AndUndefined"), context, mode)
              .undefinedTerm());
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "TrueDominatesOr"), context, mode)
              .trueTerm());
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "OrUndefined"), context, mode)
              .undefinedTerm());
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "TrueDominatesImplies"), context, mode)
              .trueTerm());
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "ImpliesUndefined"), context, mode)
              .undefinedTerm());

      assertEquals(mode.toString(), SolverOutcome.SAT, solve(script));
    }
  }

  /**
   * The counterpart to {@link
   * #booleanOperatorsUseUseStyleDominanceInsteadOfStrictUndefinedPropagation} for arithmetic and
   * ordered comparison: unlike {@code and}/{@code or}/{@code implies}, which use USE's own
   * dominance rules (a defined-false/-true operand can settle the result without the other operand
   * ever needing to be defined), {@code +}/unary {@code -} and {@code >}/{@code <}/{@code >=}/
   * {@code <=} are unconditionally STRICT: {@link ExpressionTranslator#arithmetic} and {@link
   * ExpressionTranslator#orderedComparison} both compose definedness as a plain {@code AND} of
   * every operand's own definedness, with no dominance shortcut. Confirmed directly against the
   * translator's own source, but until now nothing drove either shape with a genuinely undefined
   * operand -- every prior arithmetic/comparison test used only defined Integer operands.
   */
  @Test
  public void arithmeticAndComparisonOperatorsStrictlyPropagateUndefinedOperands()
      throws Exception {
    MModel model =
        compile(
            """
            model IntegerDefinedness
            class Sample
            end
            constraints
            context self : Sample inv BinaryPlusUndefined: (1 + oclUndefined(Integer)) > 0
            context self : Sample inv UnaryMinusUndefined: -oclUndefined(Integer) > 0
            context self : Sample inv OrderedComparisonUndefined: 1 > oclUndefined(Integer)
            """,
            "IntegerDefinedness");

    for (TranslationMode mode : TranslationMode.values()) {
      SmtScript script = new SmtScript("QF_LIA");
      ObjectSlots slots =
          ObjectSlotEncoder.encode(script, List.of(new ClassScope("Sample", 1, 1))).get("Sample");
      TranslationContext context =
          new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of("Sample", slots), Map.of());
      script.assertThat(Smt.sym("Sample_0_exists"));
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "BinaryPlusUndefined"), context, mode)
              .undefinedTerm());
      script.assertThat(
          InvariantAssembler.reify(script, invariant(model, "UnaryMinusUndefined"), context, mode)
              .undefinedTerm());
      script.assertThat(
          InvariantAssembler.reify(
                  script, invariant(model, "OrderedComparisonUndefined"), context, mode)
              .undefinedTerm());

      assertEquals(mode.toString(), SolverOutcome.SAT, solve(script));
    }
  }

  private static void assertThreeOutcomesAreMutuallyExclusive(
      InvariantClassification classification) {
    List<org.tzi.use.smt.solver.SmtTerm> outcomes =
        List.of(
            classification.trueTerm(), classification.falseTerm(), classification.undefinedTerm());
    for (int first = 0; first < outcomes.size(); first++) {
      for (int second = first + 1; second < outcomes.size(); second++) {
        SmtScript script = new SmtScript("QF_LIA");
        script.declareConst(classification.definedName(), SmtSort.BOOL);
        script.declareConst(classification.valueName(), SmtSort.BOOL);
        script.assertThat(outcomes.get(first));
        script.assertThat(outcomes.get(second));
        assertEquals(SolverOutcome.UNSAT, solve(script));
      }
    }
  }

  private static SolverOutcome solve(SmtScript script) {
    return new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30))
        .run(script.toSmtLib())
        .outcome();
  }

  private static MClassInvariant invariant(MModel model, String name) {
    return model.classInvariants().stream()
        .filter(candidate -> candidate.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static MModel compile(String source, String name) {
    MModel model =
        USECompiler.compileSpecification(
            source, name, new PrintWriter(System.err), new ModelFactory());
    if (model == null) throw new AssertionError("model did not compile: " + name);
    return model;
  }

  private static MModel compile(Path file) throws Exception {
    return compile(Files.readString(file), file.getFileName().toString());
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(InvariantClassificationTest.class.getResource("/" + name)).toURI());
  }
}
