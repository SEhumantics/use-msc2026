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
 * End-to-end regression for {@code ->flatten()} over NESTED collection literals
 * ({@code collection.flatten}): the nesting is resolved at translation time -- the leaf constants
 * of the inner literals become the outer collection's content, under the OUTER literal's
 * duplicate policy (Set/OrderedSet collapse, Bag keeps). All the constant-content consumers
 * read it: size, sum, includes, forAll.
 */
public class FlattenLiteralTest {

  private static final String MODEL =
      """
      model FlattenLit
      class X
      attributes
        n : Integer
        i : Integer
      end
      constraints
      context x : X inv FlattenSize:
        Set{Set{1,2},Set{3}}->flatten()->size() = x.n
      context x : X inv BagFlattenKeepsDuplicates:
        Bag{Bag{1,1},Bag{2}}->flatten()->size() = x.n
      context x : X inv FlattenSum:
        Set{Set{1,2},Set{3}}->flatten()->sum() = 6
      context x : X inv FlattenIncludes:
        Set{Set{1,2},Set{3}}->flatten()->includes(x.i)
      context x : X inv FlattenForAll:
        Set{Set{1,2},Set{3}}->flatten()->forAll(k | k <= x.i)
      """;

  /** Set-of-Sets flattens to {1,2,3}: size 3. */
  @Test
  public void setOfSetsFlattensToTheLeafSize() throws Exception {
    ModelFinderResult match = find("FlattenSize", List.of("3"), List.of("3"), List.of("9"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::FlattenSize").holds());

    ModelFinderResult miss = find("FlattenSize", List.of("2"), List.of("3"), List.of("9"));
    assertFalse("the leaves are {1,2,3}: size 3, not 2", miss.satisfiable());
  }

  /** Bag-of-Bags keeps the duplicate: flatten is {1,1,2}, size 3. */
  @Test
  public void bagOfBagsKeepsDuplicates() throws Exception {
    ModelFinderResult match = find("BagFlattenKeepsDuplicates", List.of("3"), List.of("3"), List.of("9"));
    assertTrue(match.satisfiable());
  }

  /** The flattened leaves sum to 6. */
  @Test
  public void flattenedLeavesSum() throws Exception {
    ModelFinderResult match = find("FlattenSum", List.of("9"), List.of("3"), List.of("9"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::FlattenSum").holds());
  }

  /** Membership over the flattened leaves. */
  @Test
  public void flattenIncludesMembersOnly() throws Exception {
    ModelFinderResult match = find("FlattenIncludes", List.of("9"), List.of("3"), List.of("3"));
    assertTrue(match.satisfiable());

    ModelFinderResult miss = find("FlattenIncludes", List.of("9"), List.of("3"), List.of("9"));
    assertFalse("9 is not among the leaves", miss.satisfiable());
  }

  /** forAll over the flattened leaves. */
  @Test
  public void flattenForAllRequiresEveryLeaf() throws Exception {
    ModelFinderResult holds = find("FlattenForAll", List.of("9"), List.of("3"), List.of("9"));
    assertTrue(holds.satisfiable());
    assertTrue(verdictFor(holds, "X::FlattenForAll").holds());

    ModelFinderResult fails = find("FlattenForAll", List.of("9"), List.of("3"), List.of("2"));
    assertFalse("leaf 3 is not <= 2", fails.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> nDomain, List<String> sumUnused, List<String> iDomain)
      throws Exception {
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
    MModel model = USECompiler.compileSpecification(MODEL, "FlattenLit", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + err);
    }
    model.classInvariants(true).forEach(inv -> {
      if (inv.name().equals("FlattenSize")) {
        org.tzi.use.uml.ocl.expr.Expression body = inv.bodyExpression();
        System.out.println("### AST: " + body.getClass().getSimpleName() + " -> " + body);
        walk(body, 1);
      }
    });
    return model;
  }

  private static void walk(org.tzi.use.uml.ocl.expr.Expression e, int depth) {
    if (depth > 5 || e == null) {
      return;
    }
    System.out.println("###   " + "  ".repeat(depth) + e.getClass().getSimpleName() + " : " + e.type());
    if (e instanceof org.tzi.use.uml.ocl.expr.ExpStdOp op) {
      for (org.tzi.use.uml.ocl.expr.Expression a : op.args()) {
        walk(a, depth + 1);
      }
    }
  }
}
