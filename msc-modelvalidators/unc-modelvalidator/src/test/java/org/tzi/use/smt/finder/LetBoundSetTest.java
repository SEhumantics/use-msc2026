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
 * End-to-end regression for LET-BOUND SET literals ({@code let s : Set(Integer) = Set{1,2,3} in
 * ...}) -- the first-class-collections step of the {@code collection.set-literal} row: the
 * literal is the collection's representation, so binding it to a variable and consuming it
 * through the membership/predicate/size consumers needs no solver-side collection value, only
 * the constant element set carried on the binding.
 *
 * <p>Duplicates collapse and constant-bounds ranges expand exactly as in the direct-literal
 * consumers ({@code Set{1..3}} is {1,2,3}; {@code Set{2,1..3}} is {1,2,3}).
 */
public class LetBoundSetTest {

  private static final String MODEL =
      """
      model LetSet
      class X
      attributes
        i : Integer
        n : Integer
        tag : String
      end
      constraints
      context x : X inv LetSetSize:
        (let s : Set(Integer) = Set{1,2,3} in s->size()) = x.n
      context x : X inv LetSetIncludes:
        (let s : Set(Integer) = Set{1,2,3} in s->includes(x.i))
      context x : X inv LetRangeIncludes:
        (let s : Set(Integer) = Set{1..3} in s->includes(x.i))
      context x : X inv LetSetForAll:
        (let s : Set(Integer) = Set{1,2,3} in s->forAll(k | k < x.i))
      context x : X inv LetStringSetIncludes:
        (let s : Set(String) = Set{'a','b'} in s->includes(x.tag))
      context x : X inv LetSetNotEmpty:
        (let s : Set(Integer) = Set{1,2,3} in s->notEmpty())
      """;

  /** size() of the let-bound literal is its distinct element count. */
  @Test
  public void letBoundSetSizeIsTheDistinctElementCount() throws Exception {
    ModelFinderResult match = find("LetSetSize", List.of("9"), List.of("3"), List.of("a"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::LetSetSize").holds());

    ModelFinderResult miss = find("LetSetSize", List.of("9"), List.of("4"), List.of("a"));
    assertFalse("Set{1,2,3} has 3 elements, not 4", miss.satisfiable());
  }

  /** includes over the let-bound set: satisfiable exactly for a member. */
  @Test
  public void letBoundSetIncludesMembersOnly() throws Exception {
    ModelFinderResult match = find("LetSetIncludes", List.of("2"), List.of("3"), List.of("a"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::LetSetIncludes").holds());

    ModelFinderResult miss = find("LetSetIncludes", List.of("9"), List.of("3"), List.of("a"));
    assertFalse("9 is not a member", miss.satisfiable());
  }

  /** A constant-bounds range initializer behaves as its enumeration. */
  @Test
  public void letBoundRangeSetIncludesMembersOnly() throws Exception {
    ModelFinderResult match = find("LetRangeIncludes", List.of("3"), List.of("3"), List.of("a"));
    assertTrue(match.satisfiable());

    ModelFinderResult miss = find("LetRangeIncludes", List.of("4"), List.of("3"), List.of("a"));
    assertFalse("4 is outside {1..3}", miss.satisfiable());
  }

  /** forAll over the let-bound set requires every element below i. */
  @Test
  public void letBoundSetForAllRequiresEveryElementBelow() throws Exception {
    ModelFinderResult holds = find("LetSetForAll", List.of("9"), List.of("3"), List.of("a"));
    assertTrue(holds.satisfiable());
    assertTrue(verdictFor(holds, "X::LetSetForAll").holds());

    ModelFinderResult fails = find("LetSetForAll", List.of("2"), List.of("3"), List.of("a"));
    assertFalse("element 3 is not < 2", fails.satisfiable());
  }

  /** String-element sets: membership by CONTENT against the literal's spellings. */
  @Test
  public void letBoundStringSetIncludesByContent() throws Exception {
    ModelFinderResult match = find("LetStringSetIncludes", List.of("1"), List.of("3"), List.of("a"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::LetStringSetIncludes").holds());

    ModelFinderResult miss = find("LetStringSetIncludes", List.of("1"), List.of("3"), List.of("zz"));
    assertFalse("'zz' is not in {'a','b'}", miss.satisfiable());
  }

  /** A non-empty let-bound set is never empty. */
  @Test
  public void letBoundSetNotEmpty() throws Exception {
    ModelFinderResult holds = find("LetSetNotEmpty", List.of("1"), List.of("3"), List.of("a"));
    assertTrue(holds.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> nDomain, List<String> tagDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "n", null, nDomain, null, null),
                new AttributeDomain("X", "tag", null, tagDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "LetSet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
