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
 * <p>Semantics CORRECTED 2026-08-31 against USE's own {@code MAssociationEnd.getType}: an n-ary
 * end navigation is typed SET, not Bag (the Bag/Sequence branches require qualifiers; both the
 * unqualified collection-multiplicity branch and the n-ary single-valued branch call mkSet), so
 * duplicate tuples count ONCE and the deduplicated per-slot population is EXACT. Every construct
 * the binary navigation path serves is therefore sound over the n-ary population:
 * forAll/exists/isEmpty/notEmpty/size()/isUnique/one()/includesAll. (The first bag-count attempt
 * at this slice died in USE's independent witness re-evaluation -- USE counted 1 distinct part
 * where an SMT fiber-sum over duplicate tuples claimed 2 -- which is what established the Set
 * reading.) Only constructs outside that set (closure's fixed-point machinery) still refuse.
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
      context s : Supplier inv partSizeIsOne:
        s.part->size() = 1
      context s : Supplier inv partSizeIsTwo:
        s.part->size() = 2
      context s : Supplier inv partSizeIsZero:
        s.part->size() = 0
      context s : Supplier inv partSkusUnique:
        s.part->isUnique(p | p.sku)
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

  /** General: the forced tuple makes {@code s.part->size()} exactly 1 (USE-confirmed). */
  @Test
  public void forcedTupleMakesTheProjectedSizeOne() throws Exception {
    ModelFinderResult match = find(SCOPES, List.of(new AssociationScope("Supplies", 1, 1,
        List.of(List.of("sup", "p1", "j1")))), Set.of("Supplier::partSizeIsOne"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Supplier::partSizeIsOne").holds());
  }

  /**
   * THE SET-SEMANTICS DISCRIMINATOR: two tuples through the SAME part ((sup,p1,j1),
   * (sup,p1,j2)) still give {@code ->size()} exactly 1 -- duplicate tuples count ONCE, per
   * USE's own Set typing of the navigation. A bag-style count would answer 2 and fail USE's
   * independent re-evaluation (as the first, wrong, attempt at this slice did).
   */
  @Test
  public void duplicateTuplesCountOnceUnderSetSemantics() throws Exception {
    ModelFinderResult match = find(
        List.of(
            new ClassScope("Supplier", 1, 1, List.of("sup")),
            new ClassScope("Part", 1, 1, List.of("p1")),
            new ClassScope("Project", 2, 2)),
        List.of(new AssociationScope("Supplies", 2, 2)),
        Set.of("Supplier::partSizeIsOne"));
    assertTrue("two tuples through the same part: one DISTINCT part, so size 1",
        match.satisfiable());
    assertTrue(verdictFor(match, "Supplier::partSizeIsOne").holds());
  }

  /** The mirror polarity of the discriminator: demanding 2 distinct parts refutes. */
  @Test
  public void demandingTwoDistinctPartsWhereOnlyOneExistsRefutes() throws Exception {
    ModelFinderResult miss = find(
        List.of(
            new ClassScope("Supplier", 1, 1, List.of("sup")),
            new ClassScope("Part", 1, 1, List.of("p1")),
            new ClassScope("Project", 2, 2)),
        List.of(new AssociationScope("Supplies", 2, 2)),
        Set.of("Supplier::partSizeIsTwo"));
    assertFalse("both tuples reach the same part: the size-2 demand is impossible",
        miss.satisfiable());
  }

  /** Genuine element count: two DISTINCT parts project to size exactly 2. */
  @Test
  public void twoDistinctPartsMakeTheSizeTwo() throws Exception {
    ModelFinderResult match = find(
        List.of(
            new ClassScope("Supplier", 1, 1, List.of("sup")),
            new ClassScope("Part", 2, 2, List.of("p1", "p2")),
            new ClassScope("Project", 1, 1, List.of("j1"))),
        List.of(new AssociationScope("Supplies", 2, 2)),
        Set.of("Supplier::partSizeIsTwo"));
    assertTrue("both tuples reach DIFFERENT parts: size 2", match.satisfiable());
    assertTrue(verdictFor(match, "Supplier::partSizeIsTwo").holds());
  }

  /** The empty grid's projected size is exactly 0 -- and 0 is satisfiable, not vacuous. */
  @Test
  public void sizeZeroOnAnEmptyGrid() throws Exception {
    ModelFinderResult match = find(SCOPES,
        List.of(new AssociationScope("Supplies", 0, 0)),
        Set.of("Supplier::partSizeIsZero"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Supplier::partSizeIsZero").holds());
  }

  /**
   * isUnique over the projected navigation: two forced parts with distinct configured skus
   * satisfy it (USE-confirmed).
   */
  @Test
  public void isUniqueOverDistinctSkusHolds() throws Exception {
    MModel model = compile(MODEL);
    List<AttributeDomain> domains = List.of(
        new AttributeDomain("Supplier", "name", null, List.of("'ACME'"), null, null),
        new AttributeDomain("Part", "sku", null, List.of("'p1'", "'p2'"), null, null),
        new AttributeDomain("Project", "title", null, List.of("'apollo'"), null, null));
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Supplier", 1, 1, List.of("sup")),
                new ClassScope("Part", 2, 2, List.of("p1", "p2")),
                new ClassScope("Project", 1, 1, List.of("j1"))),
            List.of(new AssociationScope("Supplies", 2, 2)),
            domains,
            Set.of("Supplier::partSkusUnique"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Supplier::partSkusUnique").holds());
  }

  /**
   * isUnique's enforced-UNSAT sibling: with only ONE configured sku spelling, two forced parts
   * must both carry it, so the skus cannot be unique.
   */
  @Test
  public void isUniqueWithASingleSkuSpellingRefutes() throws Exception {
    MModel model = compile(MODEL);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Supplier", 1, 1, List.of("sup")),
                new ClassScope("Part", 2, 2, List.of("p1", "p2")),
                new ClassScope("Project", 1, 1, List.of("j1"))),
            List.of(new AssociationScope("Supplies", 2, 2)),
            DOMAINS,
            Set.of("Supplier::partSkusUnique"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse("both parts must carry sku 'p1': not unique", miss.satisfiable());
  }

  /**
   * THE FIBER-DEGREE SOUNDNESS DISCRIMINATOR (2026-09-02 adversarial audit). A ternary
   * association where two tuples share the SAME (A,B) pair but land in two DIFFERENT C slots:
   * {@code Tern_0=(a0,b0,c0)}, {@code Tern_1=(a0,b0,c1)}. C's declared multiplicity is
   * {@code 0..1}, and UML/OCL n-ary semantics bind that per FIXED (A,B) combination -- here there
   * is only ONE such combination, {@code (a0,b0)}, and it has TWO C's linked to it, so this MUST
   * be UNSAT. The bug this pins: summing each C slot's tuple-booleans across every (A,B)
   * combination instead of per-combination made both {@code c0} and {@code c1} individually look
   * like "1 out of an unconstrained aggregate", so the old encoder wrongly reported SAT -- A and B
   * are both {@code 0..*} (unbounded) specifically so neither end's degree constraint can produce
   * an UNSAT unrelated to the discriminator; only C's {@code 0..1} can.
   */
  private static final String TERNARY_MODEL =
      """
      model Tern
      class A
      attributes
        name : String
      end
      class B
      attributes
        name : String
      end
      class C
      attributes
        name : String
      end
      association Tern between
        A[0..*] role a
        B[0..*] role b
        C[0..1] role c
      end
      constraints
      """;

  @Test
  public void twoTuplesSharingTheSameOtherEndsPairViolateTheThirdEndsFiberDegree()
      throws Exception {
    MModel model = compile(TERNARY_MODEL);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("A", 2, 2, List.of("a0", "a1")),
                new ClassScope("B", 1, 1, List.of("b0")),
                new ClassScope("C", 2, 2, List.of("c0", "c1"))),
            List.of(
                new AssociationScope(
                    "Tern",
                    2,
                    2,
                    List.of(List.of("a0", "b0", "c0"), List.of("a0", "b0", "c1")))),
            List.of(
                new AttributeDomain("A", "name", null, List.of("'a'"), null, null),
                new AttributeDomain("B", "name", null, List.of("'b'"), null, null),
                new AttributeDomain("C", "name", null, List.of("'c'"), null, null)),
            Set.of(),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse(
        "(a0,b0) has two C's linked to it, violating C's declared 0..1 -- must be UNSAT",
        miss.satisfiable());
  }

  /**
   * THE MIRROR REGRESSION: this project's own history (the seventy-seventh turn) hit exactly this
   * bug from the OTHER side while first authoring {@code NaryAssociationTest} -- pinning Supplier
   * to {@code [1]} with two links through distinct (Part,Project) combinations wrongly came back
   * UNSAT (the old aggregate summed both links into one Supplier-slot total of 2, over the
   * declared max of 1), so the fixture was loosened to {@code [0..2]} to route around it rather
   * than fix the encoder. With the fiber-degree fix this is genuinely SAT: each combination --
   * {@code (Part=p1,Project=j1)} and {@code (Part=p1,Project=j2)} -- individually sees exactly
   * ONE Supplier, which is exactly what {@code Supplier[1]} requires.
   */
  private static final String SUPPLIER_MULTIPLICITY_ONE_MODEL =
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
        Supplier[1] role supplier
        Part[0..2] role part
        Project[0..2] role project
      end
      constraints
      context s : Supplier inv navigatedPartPresent:
        s.part->notEmpty()
      context j : Project inv navigatedPartPresentFromProject:
        j.part->notEmpty()
      """;

  @Test
  public void pinnedSupplierMultiplicityStillAllowsTwoLinksThroughDistinctOtherEndsCombinations()
      throws Exception {
    MModel model = compile(SUPPLIER_MULTIPLICITY_ONE_MODEL);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Supplier", 1, 1, List.of("sup")),
                new ClassScope("Part", 1, 1, List.of("p1")),
                new ClassScope("Project", 2, 2)),
            List.of(new AssociationScope("Supplies", 2, 2)),
            DOMAINS,
            BOTH_INVARIANTS,
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue(
        "Supplier pinned to exactly 1 must still allow two links, one per distinct"
            + " (Part,Project) combination -- the false-UNSAT this project hit once before the"
            + " fiber-degree fix",
        match.satisfiable());
    assertTrue(verdictFor(match, "Project::navigatedPartPresentFromProject").holds());
  }

  private static final Set<String> BOTH_INVARIANTS =
      Set.of("Supplier::navigatedPartPresent", "Project::navigatedPartPresentFromProject");

  private static ModelFinderResult find(
      List<ClassScope> scopes, List<AssociationScope> associationScopes) throws Exception {
    return find(scopes, associationScopes, BOTH_INVARIANTS);
  }

  private static ModelFinderResult find(
      List<ClassScope> scopes,
      List<AssociationScope> associationScopes,
      Set<String> invariantNames)
      throws Exception {
    MModel model = compile(MODEL);
    return SmtModelFinder.find(model, config(model, scopes, associationScopes, invariantNames));
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
