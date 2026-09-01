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
 * End-to-end regression: {@code ExpressionTranslator.navigationReceiverOperation} ({@code
 * x.b.op()}, a single-hop navigated receiver) used to crash with a raw {@link
 * IndexOutOfBoundsException} when the navigated end's class has ZERO configured capacity
 * ({@code gatedValues} stays empty, and {@code gatedValues.get(gatedValues.size() - 1)} is called
 * regardless) -- not an exotic configuration, this is the DEFAULT scope USE gives every abstract
 * class, and a legitimate scope for any concrete class deliberately excluded from a scenario.
 * Every sibling ite-chain-building method already guards this shape ({@code selectLinkedValue},
 * {@code associationClassNavigatedAttribute}, {@code chainRead}, and this method's own sibling
 * {@code deepNavigationReceiverOperation} via {@code population.isEmpty()}); the fix adds the
 * identical guard here.
 */
public class ZeroCapacityNavigatedReceiverOperationTest {

  private static final String MODEL =
      """
      model NavReceiverOpZeroCap
      class A
      end
      class B
      attributes
        base : Integer
      operations
        doubled() : Integer = self.base * 2
      end
      association R between
        A [0..1] role a
        B [0..1] role b
      end
      constraints
      context x : A inv NavDoubledIsFour:
        x.b.doubled() = 4
      """;

  /**
   * B's class scope is pinned to [0,0]: {@code x.b} can never link, so the call is undefined
   * and the demand {@code x.b.doubled() = 4} is violated -- a clean, located refusal to
   * satisfy, not a raw {@link IndexOutOfBoundsException} escaping the solve.
   */
  @Test
  public void navigatedReceiverOperationOverZeroCapacityDestinationClassIsCleanlyUndefined()
      throws Exception {
    ModelFinderResult result = find(0, 0, List.of("2"));
    assertFalse(
        "B has zero configured capacity, so x.b is never linked and x.b.doubled() is"
            + " undefined -- the equality demand must be refuted, not crash",
        result.satisfiable());
  }

  /** Control: with B actually populated and linked, the same call is defined and correct. */
  @Test
  public void navigatedReceiverOperationHoldsWhenTheDestinationIsPopulated() throws Exception {
    ModelFinderResult match = find(1, 1, List.of("2"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "A::NavDoubledIsFour").holds());
  }

  private static ModelFinderResult find(int bMin, int bMax, List<String> baseDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("A", 1, 1), new ClassScope("B", bMin, bMax)),
            List.of(new AssociationScope("R", 0, bMax)),
            List.of(new AttributeDomain("B", "base", null, baseDomain, null, null)),
            Set.of("A::NavDoubledIsFour"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile() {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "NavReceiverOpZeroCap", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
