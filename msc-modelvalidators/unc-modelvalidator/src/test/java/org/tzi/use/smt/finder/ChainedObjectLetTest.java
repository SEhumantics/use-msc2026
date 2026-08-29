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
 * End-to-end regression for {@code ocl.let}'s CHAINED OBJECT lets: {@code let b2 : B = b1 in ...}
 * where {@code b1} is itself an object binding (an any-let or a navigation let). The chained
 * binding is pure IDENTITY aliasing -- {@code b2}'s binding IS {@code b1}'s slot binding -- so
 * every read through {@code b2} reads the same slot's symbols, and the chain's definedness is
 * the initializer's: a no-match any (or an unlinked navigation) anywhere in the chain makes the
 * whole let undefined, so the invariant fails.
 *
 * <p>Before this slice an object let whose initializer was another let variable refused
 * ("initializer is not T.allInstances()-&gt;any(predicate)").
 */
public class ChainedObjectLetTest {

  private static final String MODEL =
      """
      model ChainedObjectLet
      class X
      attributes
        s : String
      end
      class B
      attributes
        t : String
      end
      association R between
        X [0..1] role x
        B [0..1] role b
      end
      constraints
      context x : X inv AnyChainSelectsTheMatch:
        let b1 : B = B.allInstances()->any(p | p.t = 'x') in
        let b2 : B = b1 in
        b2.t = 'x'
      context x : X inv NavLetChainCarriesTheLink:
        let b1 : B = x.b in
        let b2 : B = b1 in
        b2.t = x.s
      context x : X inv NavLetChainUndefinedWithoutLink:
        let b1 : B = x.b in
        let b2 : B = b1 in
        b2.t <> 'y'
      context x : X inv AnyChainUndefinedWithoutMatch:
        let b1 : B = B.allInstances()->any(p | p.t = 'zzz') in
        let b2 : B = b1 in
        b2.t = 'x'
      """;

  /** The any selects the matching B and the alias reads it. */
  @Test
  public void anAnyChainSelectsTheMatchAndTheAliasReadsIt() throws Exception {
    ModelFinderResult result = find("AnyChainSelectsTheMatch", 1, 1);

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::AnyChainSelectsTheMatch").holds());
  }

  /** A navigation-initialized chain carries the linked object's state to the alias. */
  @Test
  public void aNavigationChainCarriesTheLinkedObjectsState() throws Exception {
    ModelFinderResult result = find("NavLetChainCarriesTheLink", 1, 1);

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::NavLetChainCarriesTheLink").holds());
  }

  /** Undefinedness propagates: with no link, the chain is undefined and the invariant fails. */
  @Test
  public void aChainOverAnUnlinkedNavigationIsUndefined() throws Exception {
    ModelFinderResult result = find("NavLetChainUndefinedWithoutLink", 0, 1);

    assertFalse(
        "no link means b1 (and therefore b2) is undefined, so the invariant fails",
        result.satisfiable());
  }

  /** A no-match any makes the chain undefined: the alias must not read some other slot. */
  @Test
  public void aChainOverANoMatchAnyIsUndefined() throws Exception {
    ModelFinderResult result = find("AnyChainUndefinedWithoutMatch", 1, 1);

    assertFalse(
        "no B matches 'zzz', so b1/b2 are undefined; the alias must not fall through to"
            + " another slot",
        result.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, int links, int bSlots)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1), new ClassScope("B", bSlots, bSlots)),
            List.of(new AssociationScope("R", 0, links)),
            List.of(
                new AttributeDomain("X", "s", null, List.of("x"), null, null),
                new AttributeDomain("B", "t", null, List.of("x", "y"), null, null)),
            Set.of("X::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "ChainedObjectLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
