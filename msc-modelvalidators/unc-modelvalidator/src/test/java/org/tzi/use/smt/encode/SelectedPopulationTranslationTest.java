package org.tzi.use.smt.encode;

import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * {@link ExpressionTranslator#populationOf} and {@link
 * ExpressionTranslator#selectedAllInstancesPopulation} extended to two more range shapes -- found
 * chasing CompanyERSchema's remaining gaps. {@code Project::ProjectBudget_greater_PartCost}'s
 * {@code forAll} range is {@code X.allInstances()->select(pred)} (previously only {@code
 * populationOf}'s two ORIGINAL shapes -- bare {@code allInstances()} or a single-hop navigation --
 * were supported there; {@code select} was only reachable through {@code collectionSize}). {@code
 * Part::pname_primary_key} / {@code ProjectWork::fname_lname_pname_primary_key}'s shape is the
 * standard OCL primary-key idiom {@code X.allInstances()->excluding(self)->select(pred)} --
 * previously {@code selectedAllInstancesPopulation} only recognized a BARE {@code allInstances()}
 * source, not one wrapped in {@code excluding(v)}. The excluded slot is resolved STATICALLY (its
 * {@link VariableBinding} compared directly, not an SMT-level inequality), matching every other
 * same-object comparison in this translator.
 */
public class SelectedPopulationTranslationTest {

  @Test
  public void forAllOverASelectFilteredAllInstancesDiscriminatesOnAViolatingMember()
      throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration allBig =
        readConfig(
            model,
            """
            Part_min = 2
            Part_max = 2
            Part_pname = Set{'p'}
            Part_cost = Set{5, 8}
            Project_min = 1
            Project_max = 1
            Project_budget = Set{10}
            Project_ProjectAllPartsCheaperThanBudget = active
            Part_pname_unique = inactive
            """);
    ModelFinderResult okResult = SmtModelFinder.find(model, allBig);
    assertTrue("expected SAT", okResult.satisfiable());
    assertTrue(
        "every part's cost is under budget, forAll over select(pname='p') must hold, confirmed by"
            + " USE's own evaluator",
        verdictFor(okResult, "Project::ProjectAllPartsCheaperThanBudget").holds());

    AnalysisConfiguration oneTooExpensive =
        readConfig(
            model,
            """
            Part_min = 2
            Part_max = 2
            Part_pname = Set{'p'}
            Part_cost = Set{12}
            Project_min = 1
            Project_max = 1
            Project_budget = Set{10}
            Project_ProjectAllPartsCheaperThanBudget = inactive
            Part_pname_unique = inactive
            """);
    ModelFinderResult violatingResult = SmtModelFinder.find(model, oneTooExpensive);
    assertTrue("expected SAT", violatingResult.satisfiable());
    assertTrue(
        "every part's only possible cost is 12 > budget 10, forAll must genuinely be FALSE,"
            + " confirmed by USE's own evaluator, not vacuously true",
        !verdictFor(violatingResult, "Project::ProjectAllPartsCheaperThanBudget").holds());
  }

  @Test
  public void primaryKeyIdiomWithRealExcludingIsFalseOnADuplicateAndTrueOnDistinctValues()
      throws Exception {
    MModel model = compileFixture();
    AnalysisConfiguration duplicate =
        readConfig(
            model,
            """
            Part_min = 2
            Part_max = 2
            Part_pname = Set{'same'}
            Part_cost = Set{1}
            Project_min = 1
            Project_max = 1
            Project_budget = Set{100}
            Project_ProjectAllPartsCheaperThanBudget = inactive
            Part_pname_unique = inactive
            """);
    ModelFinderResult duplicateResult = SmtModelFinder.find(model, duplicate);
    assertTrue("expected SAT", duplicateResult.satisfiable());
    for (InvariantVerdict v : duplicateResult.verdicts()) {
      if (v.invariantName().equals("Part::pname_unique")) {
        assertTrue(
            "both parts share pname='same', so allInstances()->excluding(self)->"
                + "select(pname=self.pname)->isEmpty() must genuinely be FALSE for each,"
                + " confirmed by USE's own evaluator, not vacuously true",
            !v.holds());
      }
    }

    AnalysisConfiguration distinct =
        readConfig(
            model,
            """
            Part_min = 2
            Part_max = 2
            Part_pname = Set{'alpha', 'beta'}
            Part_cost = Set{1}
            Project_min = 1
            Project_max = 1
            Project_budget = Set{100}
            Project_ProjectAllPartsCheaperThanBudget = inactive
            Part_pname_unique = active
            """);
    ModelFinderResult distinctResult = SmtModelFinder.find(model, distinct);
    assertTrue("expected SAT with distinct pnames", distinctResult.satisfiable());
    for (InvariantVerdict v : distinctResult.verdicts()) {
      if (v.invariantName().equals("Part::pname_unique")) {
        assertTrue(
            "distinct pnames: pname_unique must hold for both, confirmed by USE's own evaluator",
            v.holds());
      }
    }
  }

  private static InvariantVerdict verdictFor(ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("selected-population", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileFixture() throws Exception {
    String source =
        """
        model SelectedPopulationScope
        class Part
        attributes
          pname : String
          cost : Integer
        end
        class Project
        attributes
          budget : Integer
        end
        constraints
        context p : Project inv ProjectAllPartsCheaperThanBudget:
          Part.allInstances()->select(pname = 'p')->forAll(part | part.cost < p.budget)
        context p1 : Part inv pname_unique:
          Part.allInstances()->excluding(p1)->select(p2 | p2.pname = p1.pname)->isEmpty()
        """;
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(source, "SelectedPopulationScope", err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("SelectedPopulationScope fixture model did not compile");
    }
    return model;
  }
}
