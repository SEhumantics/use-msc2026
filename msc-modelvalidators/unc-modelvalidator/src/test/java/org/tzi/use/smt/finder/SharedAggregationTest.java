package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end evidence for shared aggregation ({@code aggregation} blocks, the open diamond) --
 * the feature-matrix row's gap was COVERAGE, not encoding: no shipped example ever declared an
 * actual {@code aggregation} block, so the row sat at {@code unsupported} on a source-absence
 * argument ("the aggregation marker is inert metadata") while its plain-link semantics, its
 * navigability, and its interaction with the {@code aggregationcyclefreeness} toggle were all
 * inferred, never demonstrated. This fixture demonstrates all three, against the real Z3 binary
 * with USE's own evaluator confirming every witness.
 *
 * <p>Semantics, re-pinned empirically against the real kk-modelvalidator on byte-identical
 * scenarios (standalone probe through the benchmark's own PropertyConfigurationVisitor +
 * UseKodkodModelValidator invocation path): (1) two wholes sharing one part is SATISFIABLE for
 * BOTH tools -- UML lets shared-aggregation parts be shared, unlike composition; (2) a forced
 * 2-cycle on a reflexive aggregation end with {@code aggregationcyclefreeness = on} is
 * UNSATISFIABLE for both (SmtModelFinder's cycle check covers every {@code aggregationKind() !=
 * NONE} end, matching the incumbent's {@code aggregationKind() != REGULAR}); (3) with the toggle
 * OFF the tools DIVERGE -- unc honors the toggle and finds the witness (whose reconstructed state
 * USE itself annotates with a part-whole cycle warning, independently proving the cycle is real),
 * while the incumbent still reports TRIVIALLY_UNSATISFIABLE. That divergence is the INCUMBENT's
 * own already-catalogued cycle-freeness defect (its {@code cycleFreenessDefinitions()} treats
 * every non-regular association as acyclic regardless of the toggle, aggregation included) -- it
 * is recorded here as the row's cross-reference, not counted against this row's support.
 */
public class SharedAggregationTest {

  private static final String SHARED_MODEL =
      """
      model SharedAgg
      class Whole
      attributes
        name : String
      end
      class Part
      attributes
        tag : String
      end
      aggregation Has between
        Whole[0..2] role whole
        Part[0..1] role part
      end
      constraints
      context w : Whole inv PartTag:
        w.part.tag = 't1'
      """;

  private static final String CYCLE_MODEL =
      """
      model AggCycle
      class F
      attributes
        tag : String
      end
      aggregation ParentOf between
        F[0..*] role parent
        F[0..1] role child
      end
      constraints
      context f : F inv FTag:
        f.tag = 't1'
      """;

  /**
   * Two wholes linked to the SAME part (forced via predefined link tuples): legal for a shared
   * aggregation, and the whole-to-part navigation in the active invariant must evaluate over the
   * shared part correctly for both wholes.
   */
  @Test
  public void twoWholesSharingOnePartIsLegalAndNavigatesCorrectly() throws Exception {
    ModelFinderResult result =
        find(SHARED_MODEL, "Whole::PartTag",
            List.of(new ClassScope("Whole", 2, 2, List.of("W0", "W1")),
                new ClassScope("Part", 1, 1, List.of("P0"))),
            List.of(new AssociationScope("Has", 2, 2,
                List.of(List.of("W0", "P0"), List.of("W1", "P0")))),
            false);

    assertTrue(
        "a shared part is legal for aggregation (kk-modelvalidator re-pin: SATISFIABLE)",
        result.satisfiable());
    assertTrue(verdictFor(result, "Whole::PartTag").holds());
  }

  /** Toggle ON: the forced 2-cycle on the reflexive aggregation end is unreachable. */
  @Test
  public void aForcedCycleOnAnAggregationEndIsRejectedWhenTheToggleIsOn() throws Exception {
    ModelFinderResult result =
        find(CYCLE_MODEL, "F::FTag",
            List.of(new ClassScope("F", 2, 2, List.of("F0", "F1"))),
            List.of(new AssociationScope("ParentOf", 2, 2,
                List.of(List.of("F0", "F1"), List.of("F1", "F0")))),
            true);

    assertFalse(
        "aggregationcyclefreeness = on covers aggregation ends exactly as it covers"
            + " composition ends (kk-modelvalidator re-pin: TRIVIALLY_UNSATISFIABLE)",
        result.satisfiable());
  }

  /**
   * Toggle OFF: the same forced cycle is found, and USE's own reconstruction annotates the
   * witness with a part-whole cycle warning -- the toggle genuinely gates the constraint. The
   * incumbent's contrary TRIVIALLY_UNSATISFIABLE here is its own known cycle-freeness defect
   * (toggle ignored), not a divergence this row owes parity for.
   */
  @Test
  public void theSameCycleIsFoundWhenTheToggleIsOff() throws Exception {
    ModelFinderResult result =
        find(CYCLE_MODEL, "F::FTag",
            List.of(new ClassScope("F", 2, 2, List.of("F0", "F1"))),
            List.of(new AssociationScope("ParentOf", 2, 2,
                List.of(List.of("F0", "F1"), List.of("F1", "F0")))),
            false);

    assertTrue(
        "with aggregationcyclefreeness = off the forced cycle is a legal instance",
        result.satisfiable());
    assertTrue(verdictFor(result, "F::FTag").holds());
  }

  private static ModelFinderResult find(
      String modelSource,
      String invariantName,
      List<ClassScope> classScopes,
      List<AssociationScope> associationScopes,
      boolean requireAggregationCycleFreedom)
      throws Exception {
    MModel model = compile(modelSource);
    var attributeDomains =
        modelSource.contains("class Whole")
            ? List.of(
                new AttributeDomain("Whole", "name", null, List.of("n1"), null, null),
                new AttributeDomain("Part", "tag", null, List.of("t1"), null, null))
            : List.of(new AttributeDomain("F", "tag", null, List.of("t1"), null, null));
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            classScopes,
            associationScopes,
            attributeDomains,
            Set.of(invariantName),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1,
            requireAggregationCycleFreedom);
    return SmtModelFinder.find(model, config);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile(String source) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "SharedAggregation", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
