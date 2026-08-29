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
 * End-to-end regression for {@code ocl.query-operation-inlining}'s non-variable-receiver slice:
 * a zero-argument (or crisp-parameter) query operation called on a SINGLE-VALUED NAVIGATION
 * receiver ({@code x.b.doubled()}) is inlined with {@code self} bound per destination slot --
 * the same per-slot expansion {@code navigationObjectLet} established for navigation-object
 * lets -- so the operation computes over the LINKED object's actual state, with polymorphic
 * dispatch per slot's concrete class.
 *
 * <p>Before this slice the call refused with "operation call on a receiver that is not a bare
 * variable is not yet supported". Receivers that are neither bare variables nor single-valued
 * navigations from a variable source (deeper chains, let-bound scalars, association-class
 * navigations) stay refused with located messages.
 */
public class NavigatedReceiverOperationTest {

  private static final String MODEL =
      """
      model NavReceiverOp
      class A
      end
      class B
      attributes
        base : Integer
      operations
        doubled() : Integer = self.base * 2
        plusd(f : Integer): Integer = self.base + f
      end
      association R between
        A [0..1] role a
        B [0..1] role b
      end
      constraints
      context x : A inv NavDoubledIsFour:
        x.b.doubled() = 4
      context x : A inv NavDoubledIsSeven:
        x.b.doubled() = 7
      context x : A inv NavPlusIsFive:
        x.b.plusd(3) = 5
      """;

  /**
   * SAT: the solver links x.b to the B and the inlined body computes doubled = 2 * 2 = 4 over
   * the LINKED object's state (an unlinked navigation is undefined, and USE's total equality
   * makes undefined = 4 false, so the link must genuinely exist).
   */
  @Test
  public void aNavigatedReceiverCallComputesOverTheLinkedObjectsState() throws Exception {
    ModelFinderResult match = find("NavDoubledIsFour", List.of("2"), 1);

    assertTrue(
        "x.b linked to B{base=2} gives doubled() = 4", match.satisfiable());
    assertTrue(verdictFor(match, "A::NavDoubledIsFour").holds());
  }

  /**
   * The discriminator: with base pinned to 2, every linked B yields doubled() = 4, so demanding
   * 7 is genuinely unsatisfiable -- proving the call reads the LINKED object, not a constant or
   * the source object's own state.
   */
  @Test
  public void demandingAnUnrelatedValueIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("NavDoubledIsSeven", List.of("2"), 1);

    assertFalse(
        "base = 2 gives doubled() = 4, never 7 (and unlinked is undefined, which total"
            + " equality also rejects against the defined literal)",
        miss.satisfiable());
  }

  /** The parameterized composition: a crisp parameter threads through the per-slot expansion. */
  @Test
  public void aParameterizedCallThreadsTheArgumentThroughTheExpansion() throws Exception {
    ModelFinderResult result = find("NavPlusIsFive", List.of("2"), 1);

    assertTrue("x.b linked to B{base=2} gives plusd(3) = 5", result.satisfiable());
    assertTrue(verdictFor(result, "A::NavPlusIsFive").holds());
  }

  /**
   * The receiver's definedness IS the link: with links forbidden, x.b is undefined for every
   * state, and USE's total equality makes undefined = 4 false -- so the scenario must be
   * unsatisfiable, never satisfiable by silently reading B's slot without a link.
   */
  @Test
  public void withLinksForbiddenTheCallIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result = find("NavDoubledIsFour", List.of("2"), 0);

    assertFalse(
        "an unlinked navigation is undefined and total equality rejects undefined = 4:"
            + " the encoding must not read the destination slot without a link",
        result.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> baseDomain, int maxLinks)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("A", 1, 1), new ClassScope("B", 1, 1)),
            List.of(new org.tzi.use.smt.config.AssociationScope("R", 0, maxLinks)),
            List.of(new AttributeDomain("B", "base", null, baseDomain, null, null)),
            Set.of("A::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "NavReceiverOp", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
