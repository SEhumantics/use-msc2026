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
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for the CHAINED navigation-as-a-value: {@code a.b.c.level} reads an
 * attribute two single-valued hops from the context object -- the shape
 * {@code ocl.navigation-regular-assoc}'s general case names as "chained navigation-as-a-value".
 * The value is conditioned on the FULL chain being linked: every hop's link term joins the
 * selection, and a broken chain makes the read undefined (hence the invariant violated), exactly
 * as USE's sequential navigation evaluates.
 */
public class ChainedNavigationTest {

  private static final String MODEL =
      """
      model Chain
      class A
      end
      class B
      end
      class C
      attributes
        level : Integer
      end
      association OwnsB between
        A[0..1] role a
        B[0..1] role b
      end
      association OwnsC between
        B[0..1] role b2
        C[0..1] role c
      end
      constraints
      context a : A inv chainedLevelPositive:
        a.b.c.level > 0
      """;

  private static final List<ClassScope> SCOPES =
      List.of(
          new ClassScope("A", 1, 1, List.of("a1")),
          new ClassScope("B", 1, 1, List.of("b1")),
          new ClassScope("C", 1, 1, List.of("c1")));

  /** Both links forced end-to-end: the chain is fully linked. */
  private static final List<org.tzi.use.smt.config.AssociationScope> FULL_CHAIN =
      List.of(
          new org.tzi.use.smt.config.AssociationScope("OwnsB", 1, 1,
              List.of(List.of("a1", "b1"))),
          new org.tzi.use.smt.config.AssociationScope("OwnsC", 1, 1,
              List.of(List.of("b1", "c1"))));

  /** Only the first hop: the chain is broken at B->C, so the read is undefined. */
  private static final List<org.tzi.use.smt.config.AssociationScope> HALF_CHAIN =
      List.of(
          new org.tzi.use.smt.config.AssociationScope("OwnsB", 1, 1,
              List.of(List.of("a1", "b1"))),
          new org.tzi.use.smt.config.AssociationScope("OwnsC", 0, 0));

  /** The fully linked chain with a positive level satisfies the two-hop read. */
  @Test
  public void fullyLinkedChainReadsTheAttribute() throws Exception {
    ModelFinderResult match = find(FULL_CHAIN, List.of("5"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "A::chainedLevelPositive").holds());
  }

  /** A broken chain makes the read undefined, hence the invariant violated. */
  @Test
  public void brokenChainRefutesTheRead() throws Exception {
    ModelFinderResult miss = find(HALF_CHAIN, List.of("5"));
    assertFalse("the chain is broken at B->C, so the read is undefined and must violate",
        miss.satisfiable());
  }

  /** The level value genuinely flows: a non-positive domain refutes the fully linked chain. */
  @Test
  public void chainValueGenuinelyFlows() throws Exception {
    ModelFinderResult miss = find(FULL_CHAIN, List.of("0", "-5"));
    assertFalse("level is 0 or negative, so the chained read must be violated", miss.satisfiable());
  }

  private static ModelFinderResult find(
      List<org.tzi.use.smt.config.AssociationScope> associationScopes, List<String> levelDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            SCOPES,
            associationScopes,
            List.of(new AttributeDomain("C", "level", null, levelDomain, null, null)),
            Set.of("A::chainedLevelPositive"),
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
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "Chain", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
