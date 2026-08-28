package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Reproduces the REAL corpus scenario, unmodified: {@code
 * benchmark/examples/CivilStatus/CivilStatus.use} loaded with the shared {@code
 * CivilStatus.properties} default section, exactly what the benchmark harness runs.
 *
 * <p>CivilStatus was ERROR-before-solving for unc-modelvalidator across three separate,
 * independently-diagnosed gaps, closed one at a time in the same session that added this test:
 * enum type support (Person.civstat/gender), isDefined/isUndefined over a single-valued
 * association navigation ({@code wife.isUndefined}/{@code husband.isUndefined}), and reflexive
 * association translation/reconstruction ({@code Marriage}: {@code Person [0..1] role wife --
 * Person [0..1] role husband}, both ends the same class). This is the first test to run the real,
 * unmodified corpus files end to end and confirm all four.
 */
public class CivilStatusCorpusRoundTripTest {

  @Test
  public void theRealCivilStatusScenarioReachesSatWithEveryActiveInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileCivilStatus();
    Path propertiesFile = examplePath("CivilStatus/CivilStatus.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, null);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    for (String invariantName :
        List.of(
            "Person::attributesDefined",
            "Person::nameIsUnique",
            "Person::femaleHasNoWife",
            "Person::maleHasNoHusband")) {
      assertTrue(
          invariantName + " is genuinely active in CivilStatus.properties and must hold,"
              + " confirmed by USE's own evaluator against the reconstructed system state",
          verdictFor(result, invariantName).holds());
    }
  }

  /**
   * The paired UNSAT scenario ({@code CivilStatus-UNSAT} in the manifest): 3 Persons, a forced
   * odd gender split makes 2 Marriage links structurally impossible under {@code
   * femaleHasNoWife}/{@code maleHasNoHusband}'s own matching argument (documented at length in
   * {@code CivilStatus.properties[everyoneMarried]}'s own comment). Both invariants navigate the
   * REFLEXIVE {@code Marriage} association via {@code isUndefined} -- exactly the two fixes this
   * session's navigation-{@code isDefined} and reflexive-association work landed together -- so
   * this is a genuine soundness check of both, not merely a label match against Kodkod.
   */
  @Test
  public void theRealCivilStatusUnsatScenarioIsGenuinelyUnsatisfiable() throws Exception {
    MModel model = compileCivilStatus();
    AnalysisConfiguration config = readSection("everyoneMarried");

    assertFalse(
        "the odd gender-split parity contradiction must be genuinely unsatisfiable, not an"
            + " artifact of a mistranslated femaleHasNoWife/maleHasNoHusband",
        SmtModelFinder.find(model, config).satisfiable());
  }

  /**
   * Isolates WHY {@code everyoneMarried} is unsatisfiable: the properties file's own comment
   * traces a matching argument that needs BOTH {@code femaleHasNoWife} AND {@code
   * maleHasNoHusband} together -- neither alone forces the contradiction. Confirmed directly
   * rather than trusted from the comment: this is exactly the kind of two-invariant
   * interdependency a subtly wrong orientation or definedness bug in either translation would be
   * very unlikely to reproduce by coincidence, so it is strong evidence both are sound, not just
   * that the final label happens to match Kodkod.
   */
  @Test
  public void bothGenderInvariantsAreRequiredTogetherForTheContradiction() throws Exception {
    MModel model = compileCivilStatus();
    AnalysisConfiguration base = readSection("everyoneMarried");

    assertTrue(
        "with neither gender invariant active, the same population/link bounds must be"
            + " satisfiable",
        SmtModelFinder.find(model, withActive(base, "Person::attributesDefined",
                "Person::nameIsUnique"))
            .satisfiable());
    assertTrue(
        "femaleHasNoWife alone must NOT be sufficient to force the contradiction",
        SmtModelFinder.find(
                model,
                withActive(
                    base,
                    "Person::attributesDefined",
                    "Person::nameIsUnique",
                    "Person::femaleHasNoWife"))
            .satisfiable());
    assertTrue(
        "maleHasNoHusband alone must NOT be sufficient to force the contradiction",
        SmtModelFinder.find(
                model,
                withActive(
                    base,
                    "Person::attributesDefined",
                    "Person::nameIsUnique",
                    "Person::maleHasNoHusband"))
            .satisfiable());
    assertFalse(
        "both together must force the contradiction (the actual everyoneMarried section)",
        SmtModelFinder.find(model, base).satisfiable());
  }

  private static AnalysisConfiguration withActive(AnalysisConfiguration base, String... active) {
    Set<String> activeInvariants = new HashSet<>(List.of(active));
    return new AnalysisConfiguration(
        base.classScopes(),
        base.associationScopes(),
        base.attributeDomains(),
        activeInvariants,
        base.query(),
        base.timeout(),
        base.modelLimit());
  }

  private static AnalysisConfiguration readSection(String section) throws Exception {
    Path propertiesFile = examplePath("CivilStatus/CivilStatus.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, section);
    return ConfigurationReader.normalize(
            raw, ConfigurationVocabulary.fromModel(compileCivilStatus()))
        .requireSupported();
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compileCivilStatus() throws Exception {
    Path file = examplePath("CivilStatus/CivilStatus.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "CivilStatusWorld", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("CivilStatus.use did not compile");
    }
    return model;
  }

  private static Path examplePath(String relative) {
    Path fromModule = Path.of("../benchmark/examples").resolve(relative);
    if (Files.isRegularFile(fromModule)) {
      return fromModule;
    }
    return Path.of("msc-modelvalidators/benchmark/examples").resolve(relative);
  }
}
