package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * benchmark/examples/AssociationClass/CompanyEmployment.use} -- an OCL association class
 * ({@code Employment}, between {@code Person [0..*] role employee} and {@code Company [0..1]
 * role employer}), which has its own object identity AND is simultaneously a binary association,
 * evidenced by the manifest's own question: "Can [the finder] reconstruct a link object that is
 * simultaneously a class instance and an association link?".
 *
 * <p>Exercises every real invariant, hence every association-class-specific translation path
 * added this session: direct attribute access on the classifier itself ({@code PositiveSalary},
 * {@code StartDateNotNegative}, {@code e.salary}/{@code e.startDate}), classifier-to-end
 * navigation plus attribute access through it ({@code SalaryBelowEmployerBudget}, {@code
 * e.employer.budget}, an {@link org.tzi.use.uml.ocl.expr.ExpNavigationClassifierSource}),
 * classifier-to-end definedness ({@code EmployeeAndEmployerAlwaysLinked}, {@code e.employee <>
 * null and e.employer <> null}), and end-to-classifier-to-other-end navigation size ({@code
 * AtMostOneEmployer}, {@code p.employer->size() <= 1}, an {@link
 * org.tzi.use.uml.ocl.expr.ExpObjAsSet} over an ordinary {@link
 * org.tzi.use.uml.ocl.expr.ExpNavigation} whose destination's association is the classifier) --
 * plus reconstruction of the association-class instance itself via {@code createLinkObjectEx}
 * rather than the ordinary {@code createObjectEx} USE refuses for a link-object class.
 */
public class AssociationClassCorpusRoundTripTest {

  @Test
  public void theRealAssociationClassScenarioReachesSatWithEveryActiveInvariantConfirmedByUseEvaluator()
      throws Exception {
    MModel model = compileCompanyEmployment();
    AnalysisConfiguration config = readSection("small");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    for (String invariantName :
        List.of(
            "Employment::PositiveSalary",
            "Employment::StartDateNotNegative",
            "Employment::SalaryBelowEmployerBudget",
            "Employment::EmployeeAndEmployerAlwaysLinked",
            "Person::AtMostOneEmployer")) {
      assertTrue(
          invariantName + " is genuinely active in CompanyEmployment.properties[small] and must"
              + " hold, confirmed by USE's own evaluator against the reconstructed system state"
              + " (a real MLinkObject, created via createLinkObjectEx)",
          verdictFor(result, invariantName).holds());
    }
  }

  /**
   * The paired UNSAT scenario ({@code AssociationClass-UNSAT} in the manifest, section {@code
   * linkcap}): 3 Persons, but {@code Employment_min = 4} demands a 4th association-class
   * instance -- structurally impossible, since the {@code Company [0..1] role employer} end
   * multiplicity caps each Person at at most one Employment link (exactly the constraint {@link
   * org.tzi.use.smt.encode.AssociationClassPointerEncoder}'s degree-by-pointer-value bound
   * enforces on the encoding side). A genuine soundness check of that bound, not merely a label
   * match against Kodkod: if the degree constraint or its cross-wiring (end0's bound is end1's
   * OWN declared multiplicity, and vice versa) were wrong, this would most likely come back SAT
   * instead.
   */
  @Test
  public void theRealAssociationClassUnsatScenarioIsGenuinelyUnsatisfiable() throws Exception {
    MModel model = compileCompanyEmployment();
    AnalysisConfiguration config = readSection("linkcap");

    assertFalse(
        "a 4th Employment instance must be genuinely unreachable once every Person is already"
            + " capped at one Employment link by the Company[0..1] employer end, not an artifact"
            + " of a mistranslated or mis-cross-wired degree bound",
        SmtModelFinder.find(model, config).satisfiable());
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static AnalysisConfiguration readSection(String section) throws Exception {
    Path propertiesFile = examplePath("AssociationClass/CompanyEmployment.properties");
    RawConfiguration raw = ConfigurationReader.read(propertiesFile, section);
    return ConfigurationReader.normalize(
            raw, ConfigurationVocabulary.fromModel(compileCompanyEmployment()))
        .requireSupported();
  }

  private static MModel compileCompanyEmployment() throws Exception {
    Path file = examplePath("AssociationClass/CompanyEmployment.use");
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "CompanyEmploymentWorld", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("CompanyEmployment.use did not compile");
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
