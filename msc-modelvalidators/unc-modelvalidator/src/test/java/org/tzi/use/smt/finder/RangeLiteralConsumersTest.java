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
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for Integer range literals ({@code Set{a..b}}) consumed by everything
 * OTHER than forAll/exists (those already have {@link RangeLiteralQuantifierTest}): size(),
 * includes/excludes, one(), and isEmpty. Each consumer must see the range's EXPANDED,
 * duplicate-collapsed element set -- the same values a hand-written Set{2,3,4} literal would
 * produce -- so a range with constant bounds is behaviorally indistinguishable from its
 * enumeration.
 *
 * <p>Non-constant bounds ({@code Set{1..x.i}}) stay refused with a located message: QF_LIA has
 * no scalar quantifier-variable binding for the range's end, and no real corpus shape needs one.
 */
public class RangeLiteralConsumersTest {

  private static final String MODEL =
      """
      model RangeLitConsumers
      class X
      attributes
        i : Integer
        n : Integer
      end
      constraints
      context x : X inv RangeIncludesMember:
        Set{2..4}->includes(x.i)
      context x : X inv RangeExcludesOutside:
        Set{2..4}->excludes(x.i)
      context x : X inv RangeSizeMatches:
        Set{2,1..3,9}->size() = x.n
      context x : X inv RangeOneExactly:
        Set{2..4}->one(k | k >= 4)
      context x : X inv EmptyRangeIsEmpty:
        Set{5..4}->isEmpty()
      context x : X inv EmptyRangeSizeZero:
        Set{5..4}->size() = 0
      """;

  /** includes over {2..4}: satisfiable exactly when i can be one of {2, 3, 4}. */
  @Test
  public void rangeIncludesMatchesAnElementOfTheRange() throws Exception {
    ModelFinderResult match = find(MODEL, "RangeIncludesMember", List.of("3"), List.of("1"));
    assertTrue("i can be 3, a member of {2..4}", match.satisfiable());
    assertTrue(verdictFor(match, "X::RangeIncludesMember").holds());

    ModelFinderResult miss = find(MODEL, "RangeIncludesMember", List.of("5"), List.of("1"));
    assertFalse("i can only be 5, which is not in {2..4}", miss.satisfiable());
  }

  /** excludes is the exact negation of includes over the same expanded range. */
  @Test
  public void rangeExcludesIsTheNegationOfIncludes() throws Exception {
    ModelFinderResult holds = find(MODEL, "RangeExcludesOutside", List.of("5"), List.of("1"));
    assertTrue("i = 5 is outside {2..4}", holds.satisfiable());
    assertTrue(verdictFor(holds, "X::RangeExcludesOutside").holds());

    ModelFinderResult fails = find(MODEL, "RangeExcludesOutside", List.of("3"), List.of("1"));
    assertFalse("i = 3 is inside {2..4}, so excludes must be violated", fails.satisfiable());
  }

  /**
   * size() over a MIXED literal (constant + range + constant): {2,1..3,9} collapses to
   * {1,2,3,9} -- the duplicate 2 (constant vs. range member) counts ONCE -- so the distinct
   * size is 4, not 5.
   */
  @Test
  public void rangeSizeCollapsesDuplicatesAcrossConstantsAndRanges() throws Exception {
    ModelFinderResult match = find(MODEL, "RangeSizeMatches", List.of("1"), List.of("4"));
    assertTrue("n = 4 is the collapsed distinct size of {2,1..3,9}", match.satisfiable());
    assertTrue(verdictFor(match, "X::RangeSizeMatches").holds());

    ModelFinderResult miss = find(MODEL, "RangeSizeMatches", List.of("1"), List.of("5"));
    assertFalse("n = 5 would only be right if the duplicate 2 counted twice", miss.satisfiable());
  }

  /** one() over {2..4}: exactly one element (4) satisfies k >= 4. */
  @Test
  public void rangeOneCountsExactlyOneSatisfyingElement() throws Exception {
    ModelFinderResult match = find(MODEL, "RangeOneExactly", List.of("1"), List.of("1"));
    assertTrue("only k = 4 of {2..4} satisfies k >= 4", match.satisfiable());
    assertTrue(verdictFor(match, "X::RangeOneExactly").holds());
  }

  /** one() over {2..4} with a body THREE elements satisfy must be unsatisfiable. */
  @Test
  public void rangeOneIsUnsatisfiableWhenThreeElementsSatisfy() throws Exception {
    String spec =
        """
        model RangeLitOneThree
        class X
        attributes
          i : Integer
          n : Integer
        end
        constraints
        context x : X inv RangeOneExactly:
          Set{2..4}->one(k | k >= 2)
        """;
    ModelFinderResult result = find(spec, "RangeOneExactly", List.of("1"), List.of("1"));
    assertFalse("three of {2..4} satisfy k >= 2, so one() must fail", result.satisfiable());
  }

  /** An empty range ({@code Set{5..4}}) really is empty: isEmpty holds, size() is 0. */
  @Test
  public void emptyRangeIsEmptyAndHasSizeZero() throws Exception {
    ModelFinderResult empty = find(MODEL, "EmptyRangeIsEmpty", List.of("1"), List.of("1"));
    assertTrue("Set{5..4} has no elements", empty.satisfiable());
    assertTrue(verdictFor(empty, "X::EmptyRangeIsEmpty").holds());

    ModelFinderResult zero = find(MODEL, "EmptyRangeSizeZero", List.of("1"), List.of("1"));
    assertTrue("Set{5..4}->size() = 0", zero.satisfiable());
  }

  /** A DEGENERATE single-element range behaves as its one element. */
  @Test
  public void singleElementRangeBehavesAsItsElement() throws Exception {
    String spec =
        """
        model RangeLitSingle
        class X
        attributes
          i : Integer
          n : Integer
        end
        constraints
        context x : X inv RangeIncludesMember:
          Set{7..7}->includes(x.i)
        """;
    ModelFinderResult result = find(spec, "RangeIncludesMember", List.of("7"), List.of("1"));
    assertTrue("i = 7 is the single element of {7..7}", result.satisfiable());
  }

  /** Non-constant bounds are refused with a located message, not mistranslated. */
  @Test
  public void nonConstantBoundsAreRefused() throws Exception {
    String spec =
        """
        model RangeLitNonConst
        class X
        attributes
          i : Integer
          n : Integer
        end
        constraints
        context x : X inv RangeIncludesMember:
          Set{1..x.i}->includes(1)
        """;
    MModel model = compile(spec);
    AnalysisConfiguration config =
        config(model, "RangeIncludesMember", List.of("3"), List.of("1"));
    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("non-constant bounds"));
  }

  /** A pathologically wide range is refused at translation time instead of exploding. */
  @Test
  public void excessivelyWideRangeIsRefused() throws Exception {
    String spec =
        """
        model RangeLitWide
        class X
        attributes
          i : Integer
          n : Integer
        end
        constraints
        context x : X inv RangeIncludesMember:
          Set{1..5000}->includes(1)
        """;
    MModel model = compile(spec);
    AnalysisConfiguration config =
        config(model, "RangeIncludesMember", List.of("3"), List.of("1"));
    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("expands to"));
  }

  private static ModelFinderResult find(
      String spec, String invariantName, List<String> iDomain, List<String> nDomain)
      throws Exception {
    MModel model = compile(spec);
    AnalysisConfiguration config = config(model, invariantName, iDomain, nDomain);
    return SmtModelFinder.find(model, config);
  }

  private static AnalysisConfiguration config(
      MModel model, String invariantName, List<String> iDomain, List<String> nDomain) {
    return new AnalysisConfiguration(
        List.of(new ClassScope("X", 1, 1)),
        List.of(),
        List.of(
            new AttributeDomain("X", "i", null, iDomain, null, null),
            new AttributeDomain("X", "n", null, nDomain, null, null)),
        Set.of("X::" + invariantName),
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
    MModel model = USECompiler.compileSpecification(spec, "RangeLitConsumers", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
