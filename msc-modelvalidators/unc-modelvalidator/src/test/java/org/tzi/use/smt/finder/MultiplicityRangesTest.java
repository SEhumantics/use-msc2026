package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
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
 * End-to-end regression for association-end multiplicity semantics, closing two findings at once:
 *
 * <p>(1) THE SCOPE-FORCING SOUNDNESS BUG. The degree constraints used to be asserted for every
 * candidate slot's row/column UNCONDITIONALLY -- including slots whose {@code exists} symbol is
 * false. UML multiplicity constraints bind per EXISTING object (an instance is a set of objects and
 * links; a non-existent object violates nothing), which is also what the incumbent's Kodkod
 * relations do: its relations only ever contain existing atoms. The unguarded encoding therefore
 * FORCED every slot of a {@code min < max} class to exist whenever the opposite end's multiplicity
 * had {@code lower > 0}: the non-existent slot's column sum still had to reach the lower bound, but
 * every link requires both endpoints to exist, so the solver had to drag the slot into existence.
 * Confirmed live against the real incumbent before the fix: on {@code DegreeScope} below
 * (A[1]/B[0..1], A 1..1, B 1..2, no other constraints) kk-modelvalidator reports SATISFIABLE while
 * unc-modelvalidator reported UNSATISFIABLE -- a genuine false refutation, since exactly one B
 * linked to the one A satisfies every declared multiplicity.
 *
 * <p>(2) MULTI-RANGE multiplicities ({@code 1,3..5}, comma-separated ranges) were hard-rejected in
 * {@code SmtModelFinder.toMultiplicity} with an uncaught {@code IllegalArgumentException}. The
 * incumbent ORs its per-range formulas; the encoding now asserts the disjunction of the ranges
 * (guarded by slot existence like everything else).
 */
public class MultiplicityRangesTest {

  /**
   * The false-refutation scenario, byte-for-byte the one re-pinned against the real incumbent:
   * {@code A[1] role a} (every B has exactly one A), {@code B[0..1] role b} (every A has at most
   * one B), A pinned at exactly 1 object, B scoped 1..2, no invariant beyond a trivial attribute
   * check. The only legal instance has exactly one B -- and it exists, so this must be
   * SATISFIABLE with a one-B witness, never a refutation.
   */
  @Test
  public void aNonexistentSlotIsNotBoundByTheOppositeEndsLowerMultiplicity() throws Exception {
    String model =
        """
        model DegreeScope
        class A
        attributes
          name : String
        end
        class B
        attributes
          tag : String
        end
        association R between
          A[1] role a
          B[0..1] role b
        end
        constraints
        context a : A inv AHasName:
          a.name = 'x'
        """;
    ModelFinderResult result =
        find(model, "AHasName",
            List.of(new ClassScope("A", 1, 1), new ClassScope("B", 1, 2)),
            List.of(new AssociationScope("R", 0, -1)));

    assertTrue(
        "the one-A/one-B instance is legal (kk-modelvalidator re-pin: SATISFIABLE); the old"
            + " encoding forced the second B slot to exist and then contradicted itself",
        result.satisfiable());
    assertTrue(verdictFor(result, "A::AHasName").holds());
    long bObjects = result.witnesses().get(0).system().state()
        .objectsOfClass(result.witnesses().get(0).system().model().getClass("B")).size();
    assertEquals("the witness must use the minimal legal B population", 1, bObjects);
  }

  /**
   * Multi-range end multiplicity {@code A[1,3]}: each B carries 1 or 3 links (columns sum over
   * the A slots, so A needs capacity >= 3 for the 3 to be reachable at all -- which is exactly
   * why the first draft of this scenario, 2 A's, was wrongly unsatisfiable). With A pinned at 3,
   * b-end unbounded, and the association's own link total pinned at exactly 4, the two B's must
   * split it as 1 + 3 -- satisfiable ONLY because the ranges are disjoined; neither single range
   * alone would allow it (all-1 gives 2, all-3 gives 6).
   */
  @Test
  public void multiRangeMultiplicitySatisfiesALinkTotalNoSingleRangeAllows() throws Exception {
    String model =
        """
        model MultiRangeSat
        class A
        attributes
          name : String
        end
        class B
        attributes
          tag : String
        end
        association R between
          A[1,3] role a
          B[0..*] role b
        end
        constraints
        context a : A inv AHasName:
          a.name = 'x'
        """;
    ModelFinderResult result =
        find(model, "AHasName",
            List.of(new ClassScope("A", 3, 3), new ClassScope("B", 2, 2)),
            List.of(new AssociationScope("R", 4, 4)));

    assertTrue(
        "4 links split as 1+3 across the two B objects satisfies A[1,3] per-B; the old"
            + " hard-reject refused to even translate this model",
        result.satisfiable());
    assertTrue(verdictFor(result, "A::AHasName").holds());
  }

  /** The polarity control: one B cannot absorb the forced 4-link total within {1, 3}. */
  @Test
  public void multiRangeMultiplicityIsGenuinelyUnsatisfiableWhenNoRangeFits() throws Exception {
    String model =
        """
        model MultiRangeUnsat
        class A
        attributes
          name : String
        end
        class B
        attributes
          tag : String
        end
        association R between
          A[1,3] role a
          B[0..*] role b
        end
        constraints
        context a : A inv AHasName:
          a.name = 'x'
        """;
    ModelFinderResult result =
        find(model, "AHasName",
            List.of(new ClassScope("A", 3, 3), new ClassScope("B", 1, 1)),
            List.of(new AssociationScope("R", 4, 4)));

    assertFalse(
        "one B can carry only 1 or 3 links while the association total is pinned at exactly 4:"
            + " genuinely unsatisfiable",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      String modelSource,
      String invariantName,
      List<ClassScope> classScopes,
      List<AssociationScope> associationScopes)
      throws Exception {
    MModel model = compile(modelSource);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            classScopes,
            associationScopes,
            List.of(
                new AttributeDomain("A", "name", null, List.of("x", "y"), null, null),
                new AttributeDomain("B", "tag", null, List.of("t1"), null, null)),
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

  private static MModel compile(String source) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "MultiplicityRanges", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
