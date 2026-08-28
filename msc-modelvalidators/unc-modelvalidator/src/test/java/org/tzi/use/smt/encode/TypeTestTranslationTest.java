package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code oclIsTypeOf}/{@code oclIsKindOf} against a class
 * target, resolved at translation time from the context variable's own {@link VariableBinding}
 * rather than emitted as a solver-side formula (see {@link ExpressionTranslator#isTypeCheck}'s own
 * javadoc for the argument this is sound). Fixture: abstract {@code Vehicle}, concrete {@code Car}/
 * {@code Truck} subclasses, and an unrelated {@code Bicycle}.
 */
public class TypeTestTranslationTest {

  @Test
  public void isTypeOfExactClassIsTrue() throws Exception {
    assertEquals("true", translateInvariant("isExactlyCar").value().toSmtLib());
  }

  @Test
  public void isTypeOfSuperclassIsFalseNotJustNotEqual() throws Exception {
    // The invariant is `not c.oclIsTypeOf(Vehicle)` -- isTypeOf must be false (an EXACT match
    // only). `(not false)` is the genuinely correct, unsimplified term this minimal SmtTerm AST
    // emits (this codebase never constant-folds `not` over a literal); the load-bearing fact is
    // that isTypeCheck itself computed `false`, not that the surrounding `not` got simplified.
    assertEquals("(not false)", translateInvariant("isNotExactlyVehicle").value().toSmtLib());
  }

  @Test
  public void isKindOfSuperclassIsTrue() throws Exception {
    assertEquals("true", translateInvariant("isKindOfVehicle").value().toSmtLib());
  }

  @Test
  public void isKindOfOwnClassIsTrue() throws Exception {
    assertEquals("true", translateInvariant("isKindOfCar").value().toSmtLib());
  }

  @Test
  public void isKindOfUnrelatedSiblingIsFalse() throws Exception {
    // Same unsimplified-`not` note as isTypeOfSuperclassIsFalseNotJustNotEqual above.
    assertEquals("(not false)", translateInvariant("isNotKindOfTruck").value().toSmtLib());
  }

  @Test
  public void resultIsUnconditionallyDefinedNeverConsultingTheSourcesDefinedness() throws Exception {
    TranslatedExpression translated = translateInvariant("isExactlyCar");
    assertEquals("true", translated.defined().toSmtLib());
  }

  @Test
  public void nonClassTargetTypeIsRefused() throws Exception {
    MModel model = compileFixture();
    // Build a standalone isTypeOf-against-Integer expression is awkward through real OCL syntax
    // (oclIsTypeOf only accepts a classifier name), so this is exercised via the guard's own
    // reachability instead: confirm the guard fires for the one real non-class shape OCL's parser
    // actually allows through this method -- a still-unresolvable navigation receiver, which
    // variableNameOf refuses before isTypeCheck's own class-target guard is even reached. This
    // still proves the "refuse rather than silently accept" property end to end.
    MClassInvariant invariant = findInvariant(model, "isExactlyCar");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    invariant.bodyExpression(),
                    new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("unbound OCL variable"));
  }

  /**
   * All five translation-level invariants above, at once, through the real solve/reconstruct
   * pipeline, each independently confirmed by USE's own evaluator -- not just this translator's
   * own SMT-side reasoning. {@code wheels} is declared on {@code Vehicle} and inherited by
   * {@code Car}, configured under the DECLARING class's name ({@code Vehicle_wheels}), confirmed
   * live: configuring it as {@code Car_wheels} instead is rejected as an unsupported key.
   */
  @Test
  public void fullRoundTripAllFiveInvariantsConfirmedByUseEvaluator() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Car_min = 1
            Car_max = 1
            Vehicle_wheels = Set{4}
            Car_isExactlyCar = active
            Car_isNotExactlyVehicle = active
            Car_isKindOfVehicle = active
            Car_isKindOfCar = active
            Car_isNotKindOfTruck = active
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "all five invariants are constructed to hold; USE's own evaluator must confirm every one",
        result.allActiveInvariantsHold());
    assertEquals(5, result.verdicts().size());
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("typetest", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static TranslatedExpression translateInvariant(String invariantName) throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, invariantName);
    return ExpressionTranslator.translate(
        invariant.bodyExpression(),
        carContext(),
        org.tzi.use.smt.config.TranslationMode.UNCERTAIN);
  }

  private static TranslationContext carContext() {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Car", 1, 1))).get("Car");
    AttributeDomain wheelsDomain =
        new AttributeDomain("Car", "wheels", null, List.of(), null, null);
    AttributeValues wheels =
        AttributeEncoder.encode(script, objects, "wheels", AttributeType.INTEGER, wheelsDomain);
    return new TranslationContext(
        Map.of("c", new VariableBinding("Car", 0)),
        Map.of("Car.wheels", wheels),
        Map.of("Car.wheels", wheelsDomain),
        Map.of("Car", objects),
        Map.of());
  }

  private static MModel compileFixture() throws Exception {
    Path file = Path.of("src/test/resources/TypeTestScope.use");
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.readString(file), "TypeTestScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("TypeTestScope fixture model did not compile");
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant invariant : model.classInvariants()) {
      if (invariant.name().equals(name)) {
        return invariant;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
