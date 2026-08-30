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
 * End-to-end regression for {@code prim.string-operations}' virtual-string COMPOSITION: a string
 * operation's RESULT feeding another operation -- {@code x.s.concat(x.t).concat('!') = 'abc!'}
 * and {@code x.s.concat(x.t).concat('!').size() = 5}. The candidate resolver is now RECURSIVE:
 * each virtual operation expands its source's candidates (composing the inner guards), so
 * arbitrary chains of concat/substring/at/case operations enumerate exactly the composed result
 * strings, guarded by the conjunction of the choices that produced them.
 *
 * <p>Fixture: s = {'ab'}, t = {'c','de'} -- the inner concat yields 'abc' or 'abde' depending on
 * t, so the outer demands separate the two compositions. Before this slice the inner concat
 * refused ("operator 'concat'") the moment it appeared as another operation's source.
 */
public class VirtualStringCompositionTest {

  private static final String MODEL =
      """
      model VirtualComposition
      class X
      attributes
        s : String
        t : String
      end
      constraints
      context x : X inv ComposedConcatSizeFive:
        x.s.concat(x.t).concat('!').size() = 5
      context x : X inv ComposedConcatEquals:
        x.s.concat(x.t).concat('!') = 'abc!'
      context x : X inv ComposedConcatEqualsDe:
        x.s.concat(x.t).concat('!') = 'abde!'
      context x : X inv ComposedSizeNine:
        x.s.concat(x.t).concat('!').size() = 9
      """;

  /** The outer demand size 5 forces t = 'de' ('abde!' is 5 long). */
  @Test
  public void theOuterSizeForcesTheInnerChoice() throws Exception {
    ModelFinderResult result = find("ComposedConcatSizeFive");

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ComposedConcatSizeFive").holds());
  }

  /** The equality demand forces the OTHER composition: 'abc!' (t = 'c'). */
  @Test
  public void theEqualityForcesTheOtherComposition() throws Exception {
    ModelFinderResult result = find("ComposedConcatEquals");

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ComposedConcatEquals").holds());
  }

  /** 'abde!' through the same chain: both compositions are reachable and distinct. */
  @Test
  public void theOtherEqualityForcesTheOtherComposition() throws Exception {
    ModelFinderResult result = find("ComposedConcatEqualsDe");

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::ComposedConcatEqualsDe").holds());
  }

  /** No composition is 9 long: genuinely unsatisfiable. */
  @Test
  public void anUnreachableComposedSizeIsUnsatisfiable() throws Exception {
    ModelFinderResult miss = find("ComposedSizeNine");

    assertFalse("'abc!' is 4 and 'abde!' is 5, never 9", miss.satisfiable());
  }

  private static ModelFinderResult find(String invariantName) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "s", null, List.of("ab"), null, null),
                new AttributeDomain("X", "t", null, List.of("c", "de"), null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "VirtualComposition", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
