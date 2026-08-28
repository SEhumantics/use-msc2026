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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Parser-backed regression coverage for {@code isDefined}/{@code isUndefined} applied to a
 * SINGLE-VALUED association navigation (e.g. {@code wife.isUndefined} in CivilStatus's
 * {@code femaleHasNoWife}), the shape {@code visitStdOp}'s "isDefined"/"isUndefined" cases used to
 * mistranslate: they called the generic {@code argResult(a[0]).defined()} path, which routes
 * through {@link ExpressionTranslator#visitNavigation} and fails closed ("unsupported OCL
 * construct...: navigation") because a single-valued navigation has no standalone SMT value (it
 * names a linked object, not a term) -- exactly the reason {@link ExpressionTranslator} already
 * has a purpose-built {@code definednessOf(Expression)} helper (used inside {@code comparison()}'s
 * {@code oclUndefined}-operand handling), which the two cases now route through instead.
 */
public class NavigationDefinednessTranslationTest {

  @Test
  public void isDefinedOverASingleValuedNavigationIsTheLinkBooleanItself() throws Exception {
    TranslatedExpression translated = translateInvariant("petIsDefined");
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("Ownership_0_0", translated.value().toSmtLib());
  }

  @Test
  public void isUndefinedOverASingleValuedNavigationNegatesTheLinkBoolean() throws Exception {
    TranslatedExpression translated = translateInvariant("petIsUndefined");
    assertEquals("true", translated.defined().toSmtLib());
    assertEquals("(not Ownership_0_0)", translated.value().toSmtLib());
  }

  @Test
  public void aForcedLinkMakesTheNavigationDefinedConfirmedByUseEvaluator() throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Owner_min = 1
            Owner_max = 1
            Pet_min = 1
            Pet_max = 1
            Ownership_min = 1
            Ownership_max = 1
            Owner_petIsDefined = active
            Owner_petIsUndefined = inactive
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    // ModelFinderResult.allActiveInvariantsHold() checks EVERY invariant declared in the model,
    // not merely the requested/active ones (see EnumTranslationTest) -- this fixture's own
    // petIsUndefined invariant genuinely evaluates FALSE here (the link IS defined), so the
    // invariant under test is checked by name instead.
    assertTrue("exactly one Owner, one Pet, one forced link: expected SAT", result.satisfiable());
    assertTrue(
        "the only possible link connects the only Owner to the only Pet, so it must be defined,"
            + " confirmed by USE's own evaluator",
        verdictFor(result, "Owner::petIsDefined").holds());
  }

  @Test
  public void aForcedAbsenceOfLinksMakesTheNavigationUndefinedConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Owner_min = 1
            Owner_max = 1
            Pet_min = 1
            Pet_max = 1
            Ownership_min = 0
            Ownership_max = 0
            Owner_petIsDefined = inactive
            Owner_petIsUndefined = active
            """);
    org.tzi.use.smt.finder.ModelFinderResult result =
        org.tzi.use.smt.finder.SmtModelFinder.find(model, config);

    assertTrue("exactly one Owner, one Pet, zero links allowed: expected SAT", result.satisfiable());
    assertTrue(
        "with no link possible at all, the navigation must be undefined, confirmed by USE's own"
            + " evaluator",
        verdictFor(result, "Owner::petIsUndefined").holds());
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
        invariant.bodyExpression(), aContext(model), TranslationMode.UNCERTAIN);
  }

  private static TranslationContext aContext(MModel model) {
    SmtScript script = new SmtScript("QF_LIA");
    Map<String, ObjectSlots> slots =
        ObjectSlotEncoder.encode(
            script, List.of(new ClassScope("Owner", 1, 1), new ClassScope("Pet", 1, 1)));
    MAssociation association = model.getAssociation("Ownership");
    List<MAssociationEnd> ends = association.associationEnds();
    AssociationLinks links =
        AssociationLinkEncoder.encode(
            script,
            "Ownership",
            slots.get(ends.get(0).cls().name()),
            new Multiplicity(0, 1),
            slots.get(ends.get(1).cls().name()),
            new Multiplicity(0, 1),
            new AssociationScope("Ownership", 0, 1));
    return new TranslationContext(
        Map.of("o", new VariableBinding("Owner", 0)),
        Map.of(),
        Map.of(),
        slots,
        Map.of("Ownership", links));
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("navdefined", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model NavDefinedScope
        class Owner end
        class Pet end
        association Ownership between
          Owner[0..1] role theOwner
          Pet[0..1] role thePet
        end
        constraints
        context o : Owner inv petIsDefined:
          o.thePet.isDefined
        context o : Owner inv petIsUndefined:
          o.thePet.isUndefined
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "NavDefinedScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("NavDefinedScope fixture model did not compile");
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
