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
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for navigation over a UNION-declared association end -- the full
 * {@code subsets} containment semantics. USE core derives a union role's content LIVE as the
 * union of every {@code subsets}-declaring association's links (established empirically by
 * Subsets.properties's own research: the role's own stored content and bound are never
 * consulted), so the encoding derives it too: {@code c.b} is the per-slot union of cd's and
 * ef's populations, each contributing only where the navigating object's concrete class sits
 * in that association's source end view (the subclass reprojection: a C source sees D partners
 * through cd, an E source sees F partners through ef).
 */
public class UnionNavigationTest {

  private static final String MODEL =
      """
      model UnionNav
      class A
      end
      class B
      end
      class C < A
      end
      class D < B
      end
      class E < A
      end
      class F < B
      end
      association ab between
        A[*] role a union
        B[*] role b union
      end
      association cd between
        C[*] role c subsets a
        D[*] role d subsets b
      end
      association ef between
        E[*] role e subsets a
        F[*] role f subsets b
      end
      constraints
      context c : C inv cSeesDThroughUnion:
        c.b->notEmpty()
      context c : C inv cUnionEmpty:
        c.b->isEmpty()
      context e : E inv eSeesFThroughUnion:
        e.b->notEmpty()
      context e : E inv eSeesNothingWhenOnlyCdLinked:
        e.b->isEmpty()
      context c : C inv cUnionSizeOne:
        c.b->size() = 1
      """;

  /** General: cd's forced link (c1,d1) is visible through the union role from c1. */
  @Test
  public void cSeesItsDPartnerThroughTheUnionRole() throws Exception {
    ModelFinderResult match = find("C::cSeesDThroughUnion", true, true);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "C::cSeesDThroughUnion").holds());
  }

  /** The empty polarity: no subsetting links at all, so the derived union is empty. */
  @Test
  public void unionIsEmptyWhenNoSubsettingAssociationHasLinks() throws Exception {
    ModelFinderResult match = find("C::cUnionEmpty", false, false);
    assertTrue("cd and ef carry no links: the derived union is empty", match.satisfiable());
    assertTrue(verdictFor(match, "C::cUnionEmpty").holds());
  }

  /** The count through the union role: exactly one derived partner. */
  @Test
  public void unionSizeCountsTheDerivedPartner() throws Exception {
    ModelFinderResult match = find("C::cUnionSizeOne", true, false);
    assertTrue("cd linked, ef not: the derived union holds exactly d1",
        match.satisfiable());
    assertTrue(verdictFor(match, "C::cUnionSizeOne").holds());
  }

  /**
   * THE SUBCLASS-REPROJECTION DISCRIMINATOR: an E source must see ef's links, NOT cd's -- the
   * subsetting association whose source end view does not contain the navigating object
   * contributes nothing. This is the test that dies if the union population ignores which
   * subsetting association the source can actually participate in.
   */
  @Test
  public void eSourceDoesNotSeeCdLinksThroughTheUnionRole() throws Exception {
    ModelFinderResult match = find("E::eSeesNothingWhenOnlyCdLinked", true, false);
    assertTrue("only cd (C-D) is linked; an E object derives nothing", match.satisfiable());
    assertTrue(verdictFor(match, "E::eSeesNothingWhenOnlyCdLinked").holds());
  }

  /** And the mirror: an E source DOES see its own ef partner through the union role. */
  @Test
  public void eSeesItsFPartnerThroughTheUnionRole() throws Exception {
    ModelFinderResult match = find("E::eSeesFThroughUnion", false, true);
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "E::eSeesFThroughUnion").holds());
  }

  /**
   * The genuinely-UNSAT polarity: an invariant that demands a partner through the union role
   * while no subsetting association is linked at all.
   */
  @Test
  public void demandingAPartnerWithNoSubsettingLinksRefutes() throws Exception {
    ModelFinderResult miss = find("C::cSeesDThroughUnion", false, false);
    assertFalse("cd and ef are empty: c.b is empty, so notEmpty refutes", miss.satisfiable());
  }

  private static ModelFinderResult find(String invariant, boolean cdLinked, boolean efLinked)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("A", 0, 0),
                new ClassScope("B", 0, 0),
                new ClassScope("C", 1, 1, List.of("c1")),
                new ClassScope("D", 1, 1, List.of("d1")),
                new ClassScope("E", 1, 1, List.of("e1")),
                new ClassScope("F", 1, 1, List.of("f1"))),
            List.of(
                new AssociationScope(
                    "cd", cdLinked ? 1 : 0, cdLinked ? 1 : 0,
                    cdLinked ? List.of(List.of("c1", "d1")) : List.of()),
                new AssociationScope(
                    "ef", efLinked ? 1 : 0, efLinked ? 1 : 0,
                    efLinked ? List.of(List.of("e1", "f1")) : List.of())),
            List.of(),
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
    MModel model = USECompiler.compileSpecification(MODEL, "UnionNav", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
