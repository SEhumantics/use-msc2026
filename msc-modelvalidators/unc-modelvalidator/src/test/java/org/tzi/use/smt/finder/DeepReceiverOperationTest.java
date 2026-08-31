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
 * End-to-end regression for a QUERY OPERATION called on a DEEP RECEIVER CHAIN ({@code
 * x.b.c.tagged()} -- two single-valued hops, the ocl.query-operation-inlining row's
 * "deeper receiver chains" residual). The chain's reachability composes every hop's link
 * term (a broken hop makes the call undefined), the receiver's concrete binding drives
 * per-slot dispatch, and the value genuinely flows to the comparison.
 */
public class DeepReceiverOperationTest {

  private static final String MODEL =
      """
      model DeepRecv
      class X
      end
      class B
      attributes
        level : Integer
      end
      class C
      attributes
        tag : String
      operations
        tagged() : Boolean = self.tag = 't'
      end
      association XB between
        X[0..1] role owner
        B[0..1] role b
      end
      association BC between
        B[0..1] role b2
        C[0..1] role c
      end
      constraints
      context x : X inv deepCallTrue:
        x.b.c.tagged()
      context x : X inv deepCallFalse:
        not x.b.c.tagged()
      """;

  /** Fully linked chain with tag 't': the deep call is defined and true (USE-confirmed). */
  @Test
  public void fullyLinkedChainCallHolds() throws Exception {
    ModelFinderResult match = find("X::deepCallTrue", List.of("'t'"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::deepCallTrue").holds());
  }

  /** The value genuinely flows: tag 'u' makes the same call false, so the demand refutes. */
  @Test
  public void wrongTagRefutesTheTrueDemand() throws Exception {
    ModelFinderResult miss = find("X::deepCallTrue", List.of("'u'"));
    assertFalse("tag 'u': tagged() is false, so the true-demand is violated", miss.satisfiable());
  }

  /** The negated demand holds over the wrong tag and refutes over the right one. */
  @Test
  public void negatedDemandTakesTheOppositePolarity() throws Exception {
    ModelFinderResult wrong = find("X::deepCallFalse", List.of("'t'"));
    assertFalse("tag 't': tagged() is true, so the false-demand is violated",
        wrong.satisfiable());

    ModelFinderResult match = find("X::deepCallFalse", List.of("'u'"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::deepCallFalse").holds());
  }

  /** A broken first hop (X-B unlinked) makes the call undefined: the true-demand refutes. */
  @Test
  public void brokenFirstHopRefutes() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("B", 1, 1, List.of("b1")),
                new ClassScope("C", 1, 1, List.of("c1"))),
            List.of(
                new AssociationScope("XB", 0, 0),
                new AssociationScope("BC", 1, 1, List.of(List.of("b1", "c1")))),
            List.of(new AttributeDomain("C", "tag", null, List.of("t"), null, null)),
            Set.of("X::deepCallTrue"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse("the chain's first hop is unlinked: the call is undefined", miss.satisfiable());
  }

  private static ModelFinderResult find(String invariant, List<String> tag) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("B", 1, 1, List.of("b1")),
                new ClassScope("C", 1, 1, List.of("c1"))),
            List.of(
                new AssociationScope("XB", 1, 1, List.of(List.of("x1", "b1"))),
                new AssociationScope("BC", 1, 1, List.of(List.of("b1", "c1")))),
            List.of(new AttributeDomain("C", "tag", null, tag.stream().map(t -> t.substring(1, t.length() - 1)).toList(), null, null)),
            Set.of(invariant),
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
    MModel model = USECompiler.compileSpecification(MODEL, "DeepRecv", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
