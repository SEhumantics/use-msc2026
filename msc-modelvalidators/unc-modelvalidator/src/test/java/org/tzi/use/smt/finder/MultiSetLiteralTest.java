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
 * End-to-end regression for Bag, Sequence, and OrderedSet literals
 * ({@code collection.bag-literal} / {@code -sequence-literal} / {@code -orderedset-literal}):
 * the duplicate-PRESERVING siblings of the Set literal. The distinguishing semantics is size --
 * {@code Bag{1,1,2}->size()} is 3 where the Set collapse would say 2 -- while the
 * membership/predicate consumers (includes, forAll, notEmpty) are duplicate-insensitive and
 * behave exactly like their Set counterparts. The incumbent silently collapses bags to sets
 * (CollectionConstructorGroup.bagLiteral logs an unsupported-collection warning and delegates to
 * setLiteral), so a correct size here EXCEEDS parity.
 */
public class MultiSetLiteralTest {

  private static final String MODEL =
      """
      model MultiSetLit
      class X
      attributes
        i : Integer
        n : Integer
      end
      constraints
      context x : X inv BagSizeCountsDuplicates:
        Bag{1,1,2}->size() = x.n
      context x : X inv SeqSizeCountsDuplicates:
        Sequence{1,1,2}->size() = x.n
      context x : X inv BagIncludes:
        Bag{1,1,2}->includes(x.i)
      context x : X inv BagForAll:
        Bag{1,1,2}->forAll(k | k <= x.i)
      context x : X inv BagNotEmpty:
        Bag{1,1,2}->notEmpty()
      context x : X inv OrderedSetSizeCollapses:
        OrderedSet{1,1,2}->size() = x.n
      """;

  /** THE discriminator: a Bag's size counts duplicates -- 3, not the Set collapse's 2. */
  @Test
  public void bagSizeCountsDuplicates() throws Exception {
    ModelFinderResult match = find("BagSizeCountsDuplicates", List.of("9"), List.of("3"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::BagSizeCountsDuplicates").holds());

    ModelFinderResult miss = find("BagSizeCountsDuplicates", List.of("9"), List.of("2"));
    assertFalse("the duplicate must count: Bag{1,1,2} has 3 elements, not 2", miss.satisfiable());
  }

  /** Sequence sizes count duplicates identically. */
  @Test
  public void sequenceSizeCountsDuplicates() throws Exception {
    ModelFinderResult match = find("SeqSizeCountsDuplicates", List.of("9"), List.of("3"));
    assertTrue(match.satisfiable());
  }

  /** Bag membership is the ordinary element test. */
  @Test
  public void bagIncludesMembersOnly() throws Exception {
    ModelFinderResult match = find("BagIncludes", List.of("1"), List.of("3"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::BagIncludes").holds());

    ModelFinderResult miss = find("BagIncludes", List.of("9"), List.of("3"));
    assertFalse("9 is not in the bag", miss.satisfiable());
  }

  /** forAll over the bag: every element (duplicates included) must satisfy. */
  @Test
  public void bagForAllRequiresEveryElement() throws Exception {
    ModelFinderResult holds = find("BagForAll", List.of("9"), List.of("3"));
    assertTrue(holds.satisfiable());
    assertTrue(verdictFor(holds, "X::BagForAll").holds());

    ModelFinderResult fails = find("BagForAll", List.of("1"), List.of("3"));
    assertFalse("element 2 is not <= 1", fails.satisfiable());
  }

  /** The bag is non-empty. */
  @Test
  public void bagNotEmpty() throws Exception {
    ModelFinderResult holds = find("BagNotEmpty", List.of("1"), List.of("3"));
    assertTrue(holds.satisfiable());
  }

  /**
   * OrderedSet semantics: the duplicate COLLAPSES (unordered, unique), so its size is 2 -- the
   * opposite discriminator to the Bag case.
   */
  @Test
  public void orderedSetSizeCollapsesDuplicates() throws Exception {
    ModelFinderResult match = find("OrderedSetSizeCollapses", List.of("9"), List.of("2"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::OrderedSetSizeCollapses").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> nDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "n", null, nDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "MultiSetLit", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
