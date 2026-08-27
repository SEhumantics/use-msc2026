package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Correctness claim 7 (qualified bounded completeness), made explicit and machine-checkable.
 *
 * <p>This is deliberately NOT a second UNSAT oracle, and building one would be a category error.
 * The proposal already settles the question: "exact encodings are complete only for the configured
 * scopes and value domains. Conservative quantile intervals may omit assignments inside their
 * boundary band, so UNSAT is never presented as an unbounded or numerically exact theorem" -- and 7
 * closes by stating that a successful post-validation "cannot strengthen the deliberately qualified
 * meaning of UNSAT". So UNSAT is a QUALIFIED claim by design, and what is executable is that the
 * qualification travels with the result instead of living in a document nobody reads next to the
 * verdict.
 *
 * <p>The failure mode this prevents is concrete: a caller reading {@code satisfiable() == false}
 * and reporting "no such model exists". With the qualification attached, the only thing that answer
 * can be rendered as is "no such model exists WITHIN these scopes, these domains, this scenario
 * policy and this numerical policy".
 */
public class BoundedCompletenessQualificationTest {

  /**
   * Every result carries it -- especially a refutation. A qualification attached only to SAT
   * results would be attached to precisely the case that does not need it.
   */
  @Test
  public void everyResultIncludingARefutationCarriesItsQualification() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));

    ModelFinderResult satisfied = SmtModelFinder.find(model, configuration(model, "above"));
    assertTrue(satisfied.satisfiable());
    assertNotNull(satisfied.qualification());

    ModelFinderResult refuted = SmtModelFinder.find(model, configuration(model, "belowActive"));
    assertEquals(ProfileOutcome.REFUTED, refuted.outcome());
    assertNotNull(
        "an UNSAT with no qualification IS the unbounded claim this project refuses to make",
        refuted.qualification());
  }

  /** The qualification must repeat what actually bounded the search, not a generic sentence. */
  @Test
  public void theQualificationRepeatsTheConfiguredScopesDomainsAndScenarioPolicy()
      throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));
    AnalysisConfiguration config = configuration(model, "belowActive");

    BoundedCompletenessQualification qualification =
        SmtModelFinder.find(model, config).qualification();

    assertEquals(config.classScopes(), qualification.classScopes());
    assertEquals(config.associationScopes(), qualification.associationScopes());
    assertEquals(config.attributeDomains(), qualification.attributeDomains());
    assertEquals(ScenarioProfile.EXISTS, qualification.profile());
    assertFalse(qualification.numericalPolicy().isBlank());
  }

  /**
   * A COVER refutation quantifies over a whole finite scenario set, so the qualification has to
   * name that set: "refuted for every configured scenario" is a different claim from "refuted".
   */
  @Test
  public void aProfiledRefutationNamesEveryScenarioItWasQualifiedOver() throws Exception {
    MModel model = compile(resourcePath("ScenarioProfiles.use"));
    AnalysisConfiguration config =
        configuration(model, "ScenarioProfiles.properties", "existsOnlyCover");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    BoundedCompletenessQualification qualification = result.qualification();
    assertNotNull(qualification);
    assertEquals(result.scenarios().size(), qualification.scenarioLabels().size());
    assertTrue(
        "the scenario policy must be part of the qualification",
        qualification.statement().contains(qualification.profile().name()));
  }

  /**
   * The rendered statement qualifies rather than concludes. Asserted on its content, so a future
   * edit that turns it into "no model exists" fails here.
   */
  @Test
  public void theStatementQualifiesRefutationRatherThanClaimingATheorem() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));
    ModelFinderResult refuted = SmtModelFinder.find(model, configuration(model, "belowActive"));

    String statement = refuted.qualification().statement();

    assertTrue(statement, statement.contains("within the configured"));
    assertTrue(
        "the numerical policy is part of what qualifies a refutation",
        statement.contains(BoundedCompletenessQualification.NUMERICAL_POLICY));
    assertFalse(
        "a refutation is never an unbounded theorem",
        statement.toLowerCase(java.util.Locale.ROOT).contains("unbounded"));
    assertTrue(
        "the configured class scope must appear verbatim",
        statement.contains("UnidentifiedObject"));
  }

  private static AnalysisConfiguration configuration(MModel model, String section)
      throws URISyntaxException {
    return configuration(model, "ReliablyFast.properties", section);
  }

  private static AnalysisConfiguration configuration(MModel model, String resource, String section)
      throws URISyntaxException {
    return ConfigurationReader.normalize(
            ConfigurationReader.read(resourcePath(resource), section),
            ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(Path file) throws Exception {
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.readString(file), file.getFileName().toString(), err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("model did not compile: " + file);
    }
    return model;
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(BoundedCompletenessQualificationTest.class.getResource("/" + name))
            .toURI());
  }
}
