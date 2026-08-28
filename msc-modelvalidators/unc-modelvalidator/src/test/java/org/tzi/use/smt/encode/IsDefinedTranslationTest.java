package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
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
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code isDefined}/{@code isUndefined}. Both are TOTAL
 * functions over ANY operand -- confirmed directly against {@code Op_isDefined}/{@code
 * Op_isUndefined} (use-core): {@code !args[0].isUndefined()} / {@code args[0].isUndefined()} --
 * so this expression's OWN definedness is unconditionally true; the operand's definedness is
 * reported as this expression's VALUE, not propagated as its own definedness.
 */
public class IsDefinedTranslationTest {

  private static final String MODEL =
      """
      model IsDefinedScope
      class A
      attributes
        i : Integer
      end
      constraints
      context a : A inv definedIsDefined:
        a.i.isDefined
      context a : A inv definedIsNotUndefined:
        not a.i.isUndefined
      context a : A inv undefinedLiteralIsNotDefined:
        not oclUndefined(Integer).isDefined
      context a : A inv undefinedLiteralIsUndefined:
        oclUndefined(Integer).isUndefined
      """;

  @Test
  public void isDefinedOverADefinedAttributeIsUnconditionallyDefinedAndTrue() throws Exception {
    TranslatedExpression translated = translateInvariant("definedIsDefined");
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("true", translated.value().toSmtLib());
  }

  @Test
  public void isUndefinedOverADefinedAttributeIsFalse() throws Exception {
    // Body is `not a.i.isUndefined`. a.i's own definedness term is the unsimplified literal
    // `true` (never folded), so isUndefined's value is `(not true)`, and the outer `not` from the
    // invariant body wraps that again: `(not (not true))` -- two genuinely unsimplified negations
    // stacked, not a bug (SmtTerm never constant-folds `not` over a literal, confirmed elsewhere
    // in this codebase).
    TranslatedExpression translated = translateInvariant("definedIsNotUndefined");
    assertEquals("(not (not true))", translated.value().toSmtLib());
  }

  @Test
  public void isDefinedOverAnUndefinedLiteralIsDefinedFalseNotUndefined() throws Exception {
    // isDefined is TOTAL: applying it to oclUndefined(Integer) yields a DEFINED false, not an
    // undefined result -- the load-bearing property this feature exists to encode. Body is
    // `not oclUndefined(Integer).isDefined`; isDefined's own defined term is unconditionally
    // `true` (never depends on the operand's own definedness), and its value is `false`.
    TranslatedExpression translated = translateInvariant("undefinedLiteralIsNotDefined");
    assertEquals("(not false)", translated.value().toSmtLib());
    assertEquals("true", translated.defined().toSmtLib());
  }

  @Test
  public void isUndefinedOverAnUndefinedLiteralIsDefinedTrue() throws Exception {
    // oclUndefined(Integer)'s own definedness term is the literal `false` (visitUndefined), so
    // isUndefined's value is the unsimplified `(not false)` -- genuinely true, just not folded.
    TranslatedExpression translated = translateInvariant("undefinedLiteralIsUndefined");
    assertEquals("(not false)", translated.value().toSmtLib());
    assertEquals("true", translated.defined().toSmtLib());
  }

  @Test
  public void fullRoundTripAllFourInvariantsConfirmedByUseEvaluator() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_i = Set{7}
            A_definedIsDefined = active
            A_definedIsNotUndefined = active
            A_undefinedLiteralIsNotDefined = active
            A_undefinedLiteralIsUndefined = active
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "all four invariants are constructed to hold; USE's own evaluator must confirm every one",
        result.allActiveInvariantsHold());
    assertEquals(4, result.verdicts().size());
  }

  private static TranslatedExpression translateInvariant(String invariantName) throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, invariantName);
    return ExpressionTranslator.translate(
        invariant.bodyExpression(), aContext(), TranslationMode.UNCERTAIN);
  }

  private static TranslationContext aContext() {
    SmtScript script = new SmtScript("QF_LIA");
    ObjectSlots objects =
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("A", 1, 1))).get("A");
    AttributeDomain iDomain = new AttributeDomain("A", "i", null, List.of(), null, null);
    AttributeValues i = AttributeEncoder.encode(script, objects, "i", AttributeType.INTEGER, iDomain);
    return new TranslationContext(
        Map.of("a", new VariableBinding("A", 0)),
        Map.of("A.i", i),
        Map.of("A.i", iDomain),
        Map.of("A", objects),
        Map.of());
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("isdefined", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "IsDefinedScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("IsDefinedScope fixture model did not compile");
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
