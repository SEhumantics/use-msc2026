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
 * End-to-end regression for the FIRST supported use of a {@code Set{...}} literal: an
 * Integer-constant set literal ({@code Set{2,4}}) as the direct range of {@code forAll}/{@code
 * exists}. The set literal used to be an unconditional refusal; this slice gives it a genuine
 * finite-population reading -- each literal element is one quantifier candidate, bound to its
 * constant value through a per-element SMT {@code let}, exactly the reading OCL's own
 * {@code forAll}/{@code exists} over an explicit enumeration has.
 *
 * <p>Deliberately narrow (matching the {@code select()}/{@code one()} slice precedent): only
 * Integer-CONSTANT elements are supported (a non-constant element like {@code Set{2..x}} or a
 * String element needs machinery this slice does not add), only a single loop variable, and only
 * as a quantifier range -- a set literal standing alone, or consumed by {@code size()}/{@code
 * includes}, is still refused.
 */
public class SetLiteralQuantifierTest {

  private static final String MODEL =
      """
      model SetLiteralQuantifier
      class X
      attributes
        i : Integer
        s : String
      end
      constraints
      context x : X inv SetExistsMatches:
        Set{2,4}->exists(k | k = x.i)
      context x : X inv SetForAllHolds:
        Set{2,4}->forAll(k | k <= x.i)
      context x : X inv SetSizeTwo:
        Set{2,4,2}->size() = 2
      context x : X inv SetSizeThree:
        Set{2,4,2}->size() = 3
      context x : X inv SetIncludesTwo:
        Set{2,4}->includes(x.i)
      context x : X inv SetExcludesThree:
        Set{2,4}->excludes(x.i)
      context x : X inv StringSetIncludes:
        Set{'alice','carol'}->includes(x.s)
      context x : X inv StringSetExcludes:
        Set{'alice','carol'}->excludes(x.s)
      context x : X inv StringSetSize:
        Set{'alice','carol'}->size() = 2
      context x : X inv OneMatchesI:
        Set{2,4}->one(k | k = x.i)
      context x : X inv OneMatchesNine:
        Set{2,4}->one(k | k = 9)
      """;

  /**
   * exists over {2,4}: satisfiable exactly when i can be one of the literal elements, and USE's
   * own re-evaluation confirms the witness's i really is one of them.
   */
  @Test
  public void existsOverASetLiteralMatchesAnElementOfTheLiteral() throws Exception {
    ModelFinderResult match = find("SetExistsMatches", List.of("4"), List.of("alice"));
    assertTrue("i can be 4, a member of the literal", match.satisfiable());
    assertTrue(verdictFor(match, "X::SetExistsMatches").holds());

    ModelFinderResult miss = find("SetExistsMatches", List.of("3"), List.of("alice"));
    assertFalse("i can only be 3, which is not in {2,4}", miss.satisfiable());
  }

  /** forAll over {2,4}: every literal element must satisfy the body. */
  @Test
  public void forAllOverASetLiteralRequiresEveryElementToSatisfyTheBody() throws Exception {
    ModelFinderResult holds = find("SetForAllHolds", List.of("9"), List.of("alice"));
    assertTrue("i = 9 satisfies k <= 9 for both k = 2 and k = 4", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::SetForAllHolds").holds());

    ModelFinderResult fails = find("SetForAllHolds", List.of("3"), List.of("alice"));
    assertFalse("i = 3 violates k = 2's conjunct, so the forAll is genuinely unsatisfiable",
        fails.satisfiable());
  }

  /**
   * size() over a set literal is the DISTINCT element count (Set semantics collapse the
   * duplicated 2): {2,4,2} has size 2, never 3.
   */
  @Test
  public void setSizeIsTheDistinctElementCount() throws Exception {
    ModelFinderResult two = find("SetSizeTwo", List.of("9"), List.of("alice"));
    assertTrue("the duplicated 2 collapses, so the size is 2", two.satisfiable());
    assertTrue(verdictFor(two, "X::SetSizeTwo").holds());

    ModelFinderResult three = find("SetSizeThree", List.of("9"), List.of("alice"));
    assertFalse("{2,4,2} cannot have size 3", three.satisfiable());
  }

  /** includes/excludes over a set literal track the caller attribute's membership. */
  @Test
  public void includesAndExcludesOverASetLiteralTrackMembership() throws Exception {
    ModelFinderResult included = find("SetIncludesTwo", List.of("2"), List.of("alice"));
    assertTrue("i = 2 is a member of {2,4}", included.satisfiable());
    assertTrue(verdictFor(included, "X::SetIncludesTwo").holds());

    ModelFinderResult notMember = find("SetIncludesTwo", List.of("3"), List.of("alice"));
    assertFalse("i = 3 is not a member of {2,4}", notMember.satisfiable());

    ModelFinderResult excluded = find("SetExcludesThree", List.of("3"), List.of("alice"));
    assertTrue("i = 3 is not a member of {2,4}, so excludes holds", excluded.satisfiable());
    assertTrue(verdictFor(excluded, "X::SetExcludesThree").holds());

    ModelFinderResult memberExcluded = find("SetExcludesThree", List.of("2"), List.of("alice"));
    assertFalse("i = 2 IS a member of {2,4}, so excludes cannot hold",
        memberExcluded.satisfiable());
  }

  /**
   * String-constant set literals: includes/excludes/size resolve by CONTENT against the
   * element attribute's own domain (sound: membership is against a single domain, so no
   * cross-domain index comparison arises).
   */
  @Test
  public void stringSetLiteralsTrackMembershipByContent() throws Exception {
    ModelFinderResult included = findS("StringSetIncludes", List.of("alice"));
    assertTrue("s = 'alice' is a member of {'alice','carol'}", included.satisfiable());
    assertTrue(verdictFor(included, "X::StringSetIncludes").holds());

    ModelFinderResult notMember = findS("StringSetIncludes", List.of("bob"));
    assertFalse("s = 'bob' is not a member", notMember.satisfiable());

    ModelFinderResult excluded = findS("StringSetExcludes", List.of("alice"));
    assertFalse("s = 'alice' IS a member, so excludes cannot hold", excluded.satisfiable());

    ModelFinderResult excludedHold = findS("StringSetExcludes", List.of("bob"));
    assertTrue("s = 'bob' is not a member, so excludes holds", excludedHold.satisfiable());
    assertTrue(verdictFor(excludedHold, "X::StringSetExcludes").holds());
  }

  /** size() over a String set literal is the distinct element count. */
  @Test
  public void stringSetSizeIsTheDistinctElementCount() throws Exception {
    ModelFinderResult two = findS("StringSetSize", List.of("alice"));
    assertTrue(two.satisfiable());
    assertTrue(verdictFor(two, "X::StringSetSize").holds());
  }

  /**
   * one() over an Integer-constant set literal: exactly one element satisfies the body.
   * With i pinned to 2, exactly one member matches (SAT); with i free over {2,4}, both match
   * (UNSAT); a constant no element can equal is also UNSAT.
   */
  @Test
  public void oneOverASetLiteralRequiresExactlyOneMatchingElement() throws Exception {
    ModelFinderResult pinned = find("OneMatchesI", List.of("2"), List.of("alice"));
    assertTrue("only i = 2 matches, so exactly one element satisfies the body",
        pinned.satisfiable());
    assertTrue(verdictFor(pinned, "X::OneMatchesI").holds());

    ModelFinderResult either = find("OneMatchesI", List.of("2", "4"), List.of("alice"));
    assertTrue("i can be 2 or 4; either way exactly one element of {2,4} matches",
        either.satisfiable());
    assertTrue(verdictFor(either, "X::OneMatchesI").holds());

    ModelFinderResult none = find("OneMatchesNine", List.of("2", "4"), List.of("alice"));
    assertFalse("no element equals 9, so zero match -- not exactly one",
        none.satisfiable());
  }

  private static ModelFinderResult findS(String invariantName, List<String> sDomain)
      throws Exception {
    return find(invariantName, List.of("9"), sDomain);
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> sDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "s", null, sDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "SetLiteralQuantifier", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
