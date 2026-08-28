package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@code and}/{@code or}/{@code not}/{@code implies}/{@code xor} over crisp Boolean literals and
 * attributes -- previously untested as a standalone unit (only exercised incidentally inside other
 * fixtures' larger expressions), found while auditing the feature matrix's own {@code
 * prim.boolean-literals-ops} entry: its evidence claimed {@link ExpressionTranslator#visitConstBoolean}
 * throws unconditionally for every Boolean literal, which was already false (it translates cleanly);
 * {@code xor} being genuinely absent from {@link ExpressionTranslator#visitStdOp}'s switch was the one
 * accurate part of that entry, closed here. {@code xor(true, x) = not(x)} has no Kleene-logic
 * absorbing value the way {@code and}/{@code or} do (see {@link ExpressionTranslator#booleanXor}'s own
 * javadoc), so {@code xorOfAValueWithItselfIsUnsatisfiableForAnyBooleanValue} below proves the
 * translation is genuinely value-dependent rather than vacuously true.
 */
public class BooleanConnectiveTranslationTest {

  @Test
  public void trueAndFalseLiteralsTranslateDirectly() throws Exception {
    MModel model = compileFixture();
    TranslatedExpression trueLit =
        ExpressionTranslator.translate(
            findInvariant(model, "TrueLiteral").bodyExpression(), emptyContext(), TranslationMode.UNCERTAIN);
    TranslatedExpression falseLit =
        ExpressionTranslator.translate(
            findInvariant(model, "FalseLiteralNegated").bodyExpression(),
            emptyContext(),
            TranslationMode.UNCERTAIN);
    assertEquals("true", trueLit.defined().toSmtLib());
    assertEquals("true", trueLit.value().toSmtLib());
    assertEquals("true", falseLit.defined().toSmtLib());
    assertEquals("(not false)", falseLit.value().toSmtLib());
  }

  @Test
  public void andOrNotImpliesXorAllHoldOnAFullyDefinedTrueFalsePair() throws Exception {
    // PAndQ/PImpliesQ/QXorQ are legitimately FALSE for this p/q pair, so they're marked inactive
    // (not REQUIRED to hold, or the config itself becomes UNSAT) but still checked by name below --
    // result.verdicts() reports every declared invariant regardless of active status, the same
    // "checks everything, not just active" trap EnumTranslationTest's own fullRoundTrip test
    // documents and works around the same way (allActiveInvariantsHold() would be wrong here too).
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_p = Set{true}
            A_q = Set{false}
            A_PAndQ = inactive
            A_PImpliesQ = inactive
            A_QXorQ = inactive
            """);
    ModelFinderResult result = SmtModelFinder.find(model, config);
    assertTrue("expected SAT", result.satisfiable());
    assertTrue("p or q", verdictFor(result, "A::POrQ").holds());
    assertTrue("not q", verdictFor(result, "A::NotQ").holds());
    assertTrue("p xor q, confirmed by USE's own evaluator", verdictFor(result, "A::PXorQ").holds());
    assertTrue("true literal", verdictFor(result, "A::TrueLiteral").holds());
    assertTrue("not false literal", verdictFor(result, "A::FalseLiteralNegated").holds());
    org.junit.Assert.assertFalse("p and q must genuinely be false here", verdictFor(result, "A::PAndQ").holds());
    org.junit.Assert.assertFalse("p implies q must genuinely be false here",
        verdictFor(result, "A::PImpliesQ").holds());
    org.junit.Assert.assertFalse("q xor q is always false", verdictFor(result, "A::QXorQ").holds());
  }

  @Test
  public void xorIsFalseWhenBothOperandsAgree() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_p = Set{true}
            A_q = Set{true}
            A_NotQ = inactive
            A_PXorQ = inactive
            A_QXorQ = inactive
            """);
    ModelFinderResult result = SmtModelFinder.find(model, config);
    assertTrue("expected SAT", result.satisfiable());
    assertTrue("p and q", verdictFor(result, "A::PAndQ").holds());
    assertTrue("p implies q", verdictFor(result, "A::PImpliesQ").holds());
    org.junit.Assert.assertFalse("p xor q must be false when both operands agree, confirmed by USE's own evaluator",
        verdictFor(result, "A::PXorQ").holds());
    org.junit.Assert.assertFalse("q xor q is always false", verdictFor(result, "A::QXorQ").holds());
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  @Test
  public void xorOfAValueWithItselfIsUnsatisfiableForAnyBooleanValue() throws Exception {
    // self.q xor self.q can never hold for either Boolean value -- a genuine UNSAT, not merely an
    // untested case, proving the translation isn't vacuously true.
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            A_min = 1
            A_max = 1
            A_p = Set{true, false}
            A_q = Set{true, false}
            A_QXorQ = active
            A_PAndQ = inactive
            A_POrQ = inactive
            A_NotQ = inactive
            A_PImpliesQ = inactive
            A_PXorQ = inactive
            A_TrueLiteral = inactive
            A_FalseLiteralNegated = inactive
            """);
    assertTrue("q xor q is a contradiction for every candidate q, expected UNSAT",
        !SmtModelFinder.find(model, config).satisfiable());
  }

  private static TranslationContext emptyContext() {
    return new TranslationContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("boolean-connective", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model)).requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model BooleanConnectiveScope
        class A
        attributes
          p : Boolean
          q : Boolean
        end
        constraints
        context a : A inv PAndQ:
          a.p and a.q
        context a : A inv POrQ:
          a.p or a.q
        context a : A inv NotQ:
          not a.q
        context a : A inv PImpliesQ:
          a.p implies a.q
        context a : A inv PXorQ:
          a.p xor a.q
        context a : A inv QXorQ:
          a.q xor a.q
        context a : A inv TrueLiteral:
          true
        context a : A inv FalseLiteralNegated:
          not false
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "BooleanConnectiveScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("BooleanConnectiveScope fixture model did not compile");
    }
    return model;
  }

  private static MClassInvariant findInvariant(MModel model, String name) {
    for (MClassInvariant inv : model.classInvariants()) {
      if (inv.name().equals(name)) {
        return inv;
      }
    }
    throw new IllegalStateException("invariant not found: " + name);
  }
}
