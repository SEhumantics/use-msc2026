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
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for OCL {@code enum} types: {@code #literal} expressions
 * ({@link ExpConstEnum}) and the DECLARATION-bounded attribute domain ({@code
 * SmtModelFinder#registerDeclarationBoundedAttributes}) that lets an enum-typed attribute (and,
 * bundled in the same fallback, a Boolean-typed one) register a symbol with NO {@code .properties}
 * configuration at all -- exactly the shape {@code CivilStatus.properties} relies on ("civstat/
 * gender/alive are enum/Boolean-typed and already tightly bounded by declaration...not narrowed
 * further"), which failed translation with "no attribute values registered" before this feature.
 */
public class EnumTranslationTest {

  @Test
  public void enumLiteralEqualityAgainstAnAttributeIsResolvedAsADomainIndexAndUnconditionallyDefined()
      throws Exception {
    TranslatedExpression translated = translateInvariant("statusIsOpen");
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("(= Ticket_0_status 0)", translated.value().toSmtLib());
  }

  @Test
  public void enumLiteralInequalityNegatesTheEqualityComparison() throws Exception {
    TranslatedExpression translated = translateInvariant("statusIsNotClosed");
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("(not (= Ticket_0_status 1))", translated.value().toSmtLib());
  }

  @Test
  public void isDefinedOverAnEnumAttributeIsUnconditionallyDefinedAndTrue() throws Exception {
    // Same TOTAL-function shape IsDefinedTranslationTest confirms for Integer: isDefined's own
    // definedness never depends on the operand, and an attribute access is unconditionally
    // defined within an already-bound object slot.
    TranslatedExpression translated = translateInvariant("statusIsDefined");
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("true", translated.value().toSmtLib());
  }

  @Test
  public void freeStandingEnumLiteralOutsideAnAttributeComparisonIsRefused() throws Exception {
    MModel model = compileFixture();
    MClassInvariant invariant = findInvariant(model, "freeStanding");
    SmtTranslationException thrown =
        assertThrows(
            SmtTranslationException.class,
            () ->
                ExpressionTranslator.translate(
                    invariant.bodyExpression(), aContext(), TranslationMode.UNCERTAIN));
    assertEquals(FragmentBoundary.TIER_2, thrown.boundary());
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("free-standing enum literal"));
  }

  @Test
  public void twoContradictoryEnumLiteralEqualitiesAreGenuinelyUnsatisfiable() throws Exception {
    // Confirms the domain indices are actually DISTINCT (a real constraint, not a vacuous one
    // that would let the solver satisfy both equalities by collapsing every index together).
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Ticket_min = 1
            Ticket_max = 1
            Ticket_statusIsOpen = active
            Ticket_statusIsClosedContradiction = active
            Ticket_statusIsNotClosed = inactive
            Ticket_statusIsDefined = inactive
            Ticket_flaggedIsDefined = inactive
            Ticket_freeStanding = inactive
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);
    assertTrue("open and closed at once must be unsatisfiable", !result.satisfiable());
  }

  @Test
  public void fullRoundTripWithNoExplicitDomainConfigConfirmedByUseEvaluator() throws Exception {
    // The load-bearing case: NEITHER Ticket_status (enum) NOR Ticket_flagged (Boolean) is given
    // any .properties entry at all -- both must register purely from their declared type via
    // registerDeclarationBoundedAttributes, exactly as CivilStatus.properties expects of
    // civstat/gender/alive.
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Ticket_min = 1
            Ticket_max = 1
            Ticket_statusIsOpen = active
            Ticket_flaggedIsDefined = active
            Ticket_statusIsNotClosed = inactive
            Ticket_statusIsDefined = inactive
            Ticket_statusIsClosedContradiction = inactive
            Ticket_freeStanding = inactive
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    // ModelFinderResult.allActiveInvariantsHold() checks EVERY invariant declared in the model,
    // not merely the requested/active ones (a real trap documented in OneTranslationTest) -- this
    // fixture's own contradiction invariant genuinely evaluates FALSE, so the two invariants under
    // test are checked by name instead.
    assertTrue("expected SAT", result.satisfiable());
    assertTrue(
        "the enum-literal comparison must hold, confirmed by USE's own evaluator",
        verdictFor(result, "Ticket::statusIsOpen").holds());
    assertTrue(
        "the Boolean isDefined check must hold, confirmed by USE's own evaluator",
        verdictFor(result, "Ticket::flaggedIsDefined").holds());
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      org.tzi.use.smt.finder.ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
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
        ObjectSlotEncoder.encode(script, List.of(new ClassScope("Ticket", 1, 1))).get("Ticket");
    AttributeDomain statusDomain =
        new AttributeDomain("Ticket", "status", null, List.of("open", "closed"), null, null);
    AttributeValues status =
        AttributeEncoder.encode(script, objects, "status", AttributeType.ENUM, statusDomain);
    return new TranslationContext(
        Map.of("t", new VariableBinding("Ticket", 0)),
        Map.of("Ticket.status", status),
        Map.of("Ticket.status", statusDomain),
        Map.of("Ticket", objects),
        Map.of());
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("enum", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model EnumScope
        enum Status {open, closed, archived}
        class Ticket
        attributes
          status : Status
          flagged : Boolean
        end
        constraints
        context t : Ticket inv statusIsOpen:
          t.status = #open
        context t : Ticket inv statusIsNotClosed:
          t.status <> #closed
        context t : Ticket inv statusIsDefined:
          t.status.isDefined
        context t : Ticket inv flaggedIsDefined:
          t.flagged.isDefined
        context t : Ticket inv statusIsClosedContradiction:
          t.status = #closed
        context t : Ticket inv freeStanding:
          #open.isDefined
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "EnumScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("EnumScope fixture model did not compile");
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
