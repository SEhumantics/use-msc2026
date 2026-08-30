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
 * End-to-end regression for the SET OPERATIONS over constant-content collections
 * ({@code ->excluding}, {@code ->including}, {@code ->union}, {@code ->intersection},
 * {@code infix -} (USE's difference spelling), {@code ->symmetricDifference}) and for STANDALONE SET-LITERAL EQUALITY
 * ({@code Set{1,2} = Set{2,1}}) -- the remaining shapes of the {@code collection.set-literal}
 * generalization. Every operand is constant-content, so each operation folds to the constant
 * result set (order-insensitive, duplicate-collapsed per Set semantics) and all the
 * constant-content consumers read it.
 */
public class SetOperationsTest {

  private static final String MODEL =
      """
      model SetOps
      class X
      attributes
        n : Integer
        i : Integer
      end
      constraints
      context x : X inv ExcludingRemoves:
        Set{1,2,3}->excluding(2)->size() = x.n
      context x : X inv IncludingAdds:
        Set{1,2}->including(3)->includes(x.i)
      context x : X inv UnionDeduplicates:
        Set{1,2}->union(Set{2,3})->size() = x.n
      context x : X inv IntersectionKeepsCommon:
        Set{1,2,3}->intersection(Set{2,3,4})->includes(x.i)
      context x : X inv MinusRemovesRight:
        (Set{1,2,3} - Set{3})->includes(x.i)
      context x : X inv SymmetricDifferenceSize:
        Set{1,2}->symmetricDifference(Set{2,3})->size() = x.n
      context x : X inv SetLiteralEqualityOrderInsensitive:
        Set{1,2} = Set{2,1}
      context x : X inv SetLiteralEqualityContent:
        Set{1,2} = Set{1,3}
      """;

  /** excluding removes the element: {1,2,3} minus {2} has size 2. */
  @Test
  public void excludingRemovesTheElement() throws Exception {
    ModelFinderResult match = find("ExcludingRemoves", List.of("2"), List.of("1"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::ExcludingRemoves").holds());

    ModelFinderResult miss = find("ExcludingRemoves", List.of("3"), List.of("1"));
    assertFalse("excluding 2 leaves 2 elements, not 3", miss.satisfiable());
  }

  /** including adds the element, which is then a member. */
  @Test
  public void includingAddsTheElement() throws Exception {
    ModelFinderResult match = find("IncludingAdds", List.of("1"), List.of("3"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::IncludingAdds").holds());

    ModelFinderResult miss = find("IncludingAdds", List.of("1"), List.of("9"));
    assertFalse("9 is not in {1,2,3}", miss.satisfiable());
  }

  /** Set union collapses the shared element: {1,2} + {2,3} has size 3. */
  @Test
  public void unionCollapsesSharedElements() throws Exception {
    ModelFinderResult match = find("UnionDeduplicates", List.of("3"), List.of("1"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::UnionDeduplicates").holds());

    ModelFinderResult miss = find("UnionDeduplicates", List.of("4"), List.of("1"));
    assertFalse("the union collapses 2: size 3, not 4", miss.satisfiable());
  }

  /** Intersection keeps exactly the common elements. */
  @Test
  public void intersectionKeepsOnlyCommonElements() throws Exception {
    ModelFinderResult match = find("IntersectionKeepsCommon", List.of("1"), List.of("2"));
    assertTrue(match.satisfiable());

    ModelFinderResult miss = find("IntersectionKeepsCommon", List.of("1"), List.of("1"));
    assertFalse("1 is not in {2,3}", miss.satisfiable());
  }

  /** minus removes the right operand's elements from the left. */
  @Test
  public void minusRemovesTheRightOperandsElements() throws Exception {
    ModelFinderResult match = find("MinusRemovesRight", List.of("1"), List.of("1"));
    assertTrue(match.satisfiable());

    ModelFinderResult miss = find("MinusRemovesRight", List.of("1"), List.of("3"));
    assertFalse("3 was removed by minus", miss.satisfiable());
  }

  /** symmetricDifference {1,2} ^ {2,3} = {1,3}: size 2. */
  @Test
  public void symmetricDifferenceHasTheInExactlyOneSize() throws Exception {
    ModelFinderResult match = find("SymmetricDifferenceSize", List.of("2"), List.of("2"));
    assertTrue(match.satisfiable());
  }

  /** Set equality is order-insensitive: {1,2} = {2,1} holds. */
  @Test
  public void setEqualityIsOrderInsensitive() throws Exception {
    ModelFinderResult match = find("SetLiteralEqualityOrderInsensitive", List.of("1"), List.of("1"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::SetLiteralEqualityOrderInsensitive").holds());
  }

  /** Set equality is content-based: {1,2} = {1,3} is false. */
  @Test
  public void setEqualityRefutesDifferentContent() throws Exception {
    ModelFinderResult miss = find("SetLiteralEqualityContent", List.of("1"), List.of("1"));
    assertFalse("{1,2} and {1,3} differ", miss.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> nDomain, List<String> iDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "n", null, nDomain, null, null),
                new AttributeDomain("X", "i", null, iDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "SetOps", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    return model;
  }
}
