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
 * End-to-end regression for the constant-content collection reductions: {@code ->sum()} over
 * Integer/Real collections and {@code ->first()}/{@code ->last()} over Sequence/OrderedSet
 * literals -- all constant-folded off the collection's constant element content (the same
 * representation the let-bound and direct literal consumers already share). The Bag sum counts
 * duplicates (the multiset semantics the size() slice pinned); an empty Integer collection sums
 * to 0 (USE's own total: Collection{}->sum() = 0).
 */
public class CollectionSumReductionsTest {

  private static final String MODEL =
      """
      model CollReduce
      class X
      attributes
        n : Integer
        r : Real
        i : Integer
      end
      constraints
      context x : X inv SetSum:
        Set{1,2,3}->sum() = x.n
      context x : X inv BagSumCountsDuplicates:
        Bag{1,1,2}->sum() = x.n
      context x : X inv RealSeqSum:
        Sequence{0.5,0.25}->sum() = x.r
      context x : X inv SeqFirst:
        Sequence{1,2,3}->first() = x.i
      context x : X inv OrderedSetLast:
        OrderedSet{1,2,3}->last() = x.i
      """;

  /** Set sum: 1+2+3 = 6. */
  @Test
  public void setSumIsTheElementTotal() throws Exception {
    ModelFinderResult match = find("SetSum", List.of("6"), List.of("0.0"), List.of("1"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::SetSum").holds());

    ModelFinderResult miss = find("SetSum", List.of("7"), List.of("0.0"), List.of("1"));
    assertFalse("1+2+3 = 6, not 7", miss.satisfiable());
  }

  /** THE multiset discriminator: the Bag sum counts the duplicate -- 1+1+2 = 4. */
  @Test
  public void bagSumCountsDuplicates() throws Exception {
    ModelFinderResult match = find("BagSumCountsDuplicates", List.of("4"), List.of("0.0"), List.of("1"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::BagSumCountsDuplicates").holds());

    ModelFinderResult miss = find("BagSumCountsDuplicates", List.of("3"), List.of("0.0"), List.of("1"));
    assertFalse("the duplicate must count in the sum: 1+1+2 = 4, not 3", miss.satisfiable());
  }

  /** Real sequence sums in the Reals: 0.5 + 0.25 = 0.75. */
  @Test
  public void realSequenceSumIsExact() throws Exception {
    ModelFinderResult match = find("RealSeqSum", List.of("1"), List.of("0.75"), List.of("1"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::RealSeqSum").holds());
  }

  /** first/last over ordered literal sequences. */
  @Test
  public void sequenceFirstAndOrderedSetLast() throws Exception {
    ModelFinderResult first = find("SeqFirst", List.of("1"), List.of("0.0"), List.of("1"));
    assertTrue(first.satisfiable());
    assertTrue(verdictFor(first, "X::SeqFirst").holds());

    ModelFinderResult last = find("OrderedSetLast", List.of("3"), List.of("0.0"), List.of("3"));
    assertTrue(last.satisfiable());
    assertTrue(verdictFor(last, "X::OrderedSetLast").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> nDomain, List<String> rDomain, List<String> iDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "n", null, nDomain, null, null),
                new AttributeDomain("X", "r", null, rDomain, null, null),
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
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "CollReduce", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
