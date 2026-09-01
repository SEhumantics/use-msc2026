package org.tzi.use.smt.finder;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * BUG B, end-to-end through the REAL {@code SmtModelFinder.find()} pipeline -- compiled OCL source,
 * {@code AnalysisConfiguration}, {@code ExpressionTranslator} -- not just {@code
 * URealThresholdBoundary.enclose()} called directly in isolation (that unit-level coverage lives in
 * {@code URealThresholdBoundaryTest}).
 *
 * <p>{@code toBooleanC(0.9999999999999999)} is a syntactically valid confidence: it is a genuine
 * {@code BigDecimal} strictly less than one, so it clears {@code URealThresholdBoundary}'s open-
 * interval guard. But the live evaluator's CNDF approximation the boundary bisects against never
 * actually returns a probability that high anywhere in its {@code [-8, 8]} search window --
 * confirmed live in {@code URealThresholdBoundaryTest}: {@code UReal(8,1).gt(UReal(0)).getC()} tops
 * out at {@code 0.9999999999999993}, strictly below the target. Before the fix this invariant
 * translated to a bogus boundary pinned at the search edge with no diagnostic; it must now come back
 * as a located refusal that survives the full pipeline, not just the isolated helper.
 */
public class UnreachableConfidenceRefusalTest {

  private static final String FAST = "Measured::Fast";

  @Test
  public void existsIsRefusedForAConfidenceTheLiveEvaluatorCanNeverReach() throws Exception {
    MModel model = compile();

    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Measured", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Measured", "speed", "value", List.of("0.35"), null, null),
                new AttributeDomain(
                    "Measured", "speed", "uncertainty", List.of("0.02"), null, null)),
            Set.of(FAST),
            new QueryExpr.Profiled(ScenarioProfile.EXISTS, new QueryExpr.Satisfy()),
            Duration.ofSeconds(30),
            1);

    // SmtModelFinder classifies each invariant's translation failure through
    // FragmentCoverageLedger, which re-raises the aggregate, boundary-LESS form of
    // SmtTranslationException (see that class's own javadoc: the aggregate exists precisely
    // because its individual causes are already classified in the ledger entries it reports) --
    // so the located UTYPE_CORE classification and the unreachable value both surface in the
    // aggregate's message rather than on refused.boundary() directly.
    SmtTranslationException refused =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));

    assertTrue(
        "the located per-invariant cause must still be UTYPE_CORE",
        refused.getMessage().contains("UTYPE_CORE"));
    assertTrue(refused.getMessage(), refused.getMessage().contains("0.9999999999999999"));
    assertTrue(
        refused.getMessage(),
        refused.getMessage().contains("not reachable by the live evaluator"));
  }

  private static MModel compile() {
    String source =
        """
        model Measured
        class Measured
        attributes
          speed : UReal
        end
        constraints
        context self : Measured inv Fast:
          (self.speed > 0.30).toBooleanC(0.9999999999999999)
        """;
    MModel model =
        USECompiler.compileSpecification(
            source, "Measured", new PrintWriter(System.err), new ModelFactory());
    if (model == null) {
      throw new AssertionError("generated model did not compile:\n" + source);
    }
    return model;
  }
}
