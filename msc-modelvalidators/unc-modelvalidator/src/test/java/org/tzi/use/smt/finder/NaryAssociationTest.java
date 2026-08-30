package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for N-ARY associations (arity 3), the assoc.nary slice: a real N-tuple
 * link grid, membership-based population consumers over projected navigations, and predefined
 * tuple bounds. The kk-side behavior this parity-slots against is empirically pinned in
 * NaryAssociationProbeTest (benchmark module): the real Kodkod pipeline solves exactly this
 * model shape, honoring the forced 3-tuple.
 *
 * <p>USE types an n-ary end navigation ({@code s.part}) as a BAG over the projected tuples, so
 * the supported consumers are the membership/predicate ones (forAll/exists/isEmpty/notEmpty);
 * count-based consumers (size()/isUnique/one()) refuse with a located message because this
 * encoder's population is per-slot deduplicated and would answer the Set question where the
 * Bag question is asked.
 */
public class NaryAssociationTest {

  private static final String MODEL =
      """
      model Nary
      class Supplier
      attributes
        name : String
      end
      class Part
      attributes
        sku : String
      end
      class Project
      attributes
        title : String
      end
      association Supplies between
        Supplier[0..2] role supplier
        Part[0..2] role part
        Project[0..2] role project
      end
      constraints
      context s : Supplier inv navigatedPartPresent:
        s.part->notEmpty()
      context j : Project inv navigatedPartPresentFromProject:
        j.part->notEmpty()
      """;

  private static final List<ClassScope> SCOPES =
      List.of(
          new ClassScope("Supplier", 1, 1, List.of("sup")),
          new ClassScope("Part", 1, 1, List.of("p1")),
          new ClassScope("Project", 1, 1, List.of("j1")));

  private static final List<AttributeDomain> DOMAINS =
      List.of(
          new AttributeDomain("Supplier", "name", null, List.of("'ACME'"), null, null),
          new AttributeDomain("Part", "sku", null, List.of("'p1'"), null, null),
          new AttributeDomain("Project", "title", null, List.of("'apollo'"), null, null));

  /** The forced 3-tuple supplies the whole population; both projected navigations see it. */
  @Test
  public void forcedTernaryTupleFeedsBothProjectedNavigations() throws Exception {
    ModelFinderResult match = find(SCOPES, List.of(new AssociationScope("Supplies", 1, 1,
        List.of(List.of("sup", "p1", "j1")))));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Supplier::navigatedPartPresent").holds());
    assertTrue(verdictFor(match, "Project::navigatedPartPresentFromProject").holds());
  }

  /** Zero allowed links violates notEmpty on both ends -- the grid is genuinely consulted. */
  @Test
  public void zeroLinkBoundRefutesTheNotEmptyInvariants() throws Exception {
    ModelFinderResult miss = find(SCOPES, List.of(new AssociationScope("Supplies", 0, 0)));
    assertFalse("no links may exist, so both notEmpty projections must be violated",
        miss.satisfiable());
  }

  /**
   * A placed tuple satisfies only the ends it actually touches: with two Project slots but a
   * single allowed link, whichever project receives the link leaves the other's projection
   * empty -- UNSAT. This is the projection guard being load-bearing, not the mere presence of
   * links.
   */
  @Test
  public void singleLinkWithTwoProjectsLeavesOneProjectionEmpty() throws Exception {
    ModelFinderResult miss = find(
        List.of(
            new ClassScope("Supplier", 1, 1, List.of("sup")),
            new ClassScope("Part", 1, 1, List.of("p1")),
            new ClassScope("Project", 2, 2)),
        List.of(new AssociationScope("Supplies", 1, 1)));
    assertFalse("one link cannot feed both project slots", miss.satisfiable());
  }

  /** Two links (one per project) satisfy both projections again. */
  @Test
  public void twoLinksFeedBothProjectSlots() throws Exception {
    ModelFinderResult match = find(
        List.of(
            new ClassScope("Supplier", 1, 1, List.of("sup")),
            new ClassScope("Part", 1, 1, List.of("p1")),
            new ClassScope("Project", 2, 2)),
        List.of(new AssociationScope("Supplies", 2, 2)));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Project::navigatedPartPresentFromProject").holds());
  }

  /** Count-based consumers refuse with the located bag-semantics message. */
  @Test
  public void sizeOverAnNaryNavigationRefuses() throws Exception {
    String spec = MODEL.replace(
        "s.part->notEmpty()",
        "s.part->size() > 0");
    MModel model = compile(spec);
    AnalysisConfiguration config = config(model, SCOPES, List.of(new AssociationScope("Supplies", 1, 1,
        List.of(List.of("sup", "p1", "j1")))), BOTH_INVARIANTS);
    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("n-ary association navigation"));
  }

  private static final Set<String> BOTH_INVARIANTS =
      Set.of("Supplier::navigatedPartPresent", "Project::navigatedPartPresentFromProject");

  private static ModelFinderResult find(
      List<ClassScope> scopes, List<AssociationScope> associationScopes) throws Exception {
    MModel model = compile(MODEL);
    return SmtModelFinder.find(model, config(model, scopes, associationScopes, BOTH_INVARIANTS));
  }

  private static AnalysisConfiguration config(
      MModel model,
      List<ClassScope> scopes,
      List<AssociationScope> associationScopes,
      Set<String> invariantNames) {
    return new AnalysisConfiguration(
        scopes,
        associationScopes,
        DOMAINS,
        invariantNames,
        QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
        Duration.ofSeconds(30),
        1);
  }

  private static org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static MModel compile(String spec) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(spec, "Nary", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
