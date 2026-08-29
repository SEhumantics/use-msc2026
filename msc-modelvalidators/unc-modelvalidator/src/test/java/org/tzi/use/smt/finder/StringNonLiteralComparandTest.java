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
 * End-to-end regression for {@code prim.string-operations}' non-literal comparands: a virtual
 * string (concat/substring result) compared against a STRING ATTRIBUTE with its own configured
 * domain, and {@code indexOf} with an enumerable needle. Per candidate pair the operation's
 * result and the comparand's spelling are compared at translation time (compile-time Java
 * equality), and the surviving pairs become the guarded conjunction/disjunction over BOTH
 * sources' configured indices -- the content-aware cross-domain pattern, extended from
 * comparisons to computed results.
 *
 * <p>Before this slice concat/substring/indexOf refused non-literal comparands ("compared
 * against anything other than a string literal is not supported in this slice").
 */
public class StringNonLiteralComparandTest {

  private static final String MODEL =
      """
      model StringComparands
      class X
      attributes
        s : String
        t : String
      end
      constraints
      context x : X inv ConcatEqualsOtherAttr:
        x.s.concat('b') = x.t
      context x : X inv ConcatForcedMismatch:
        x.s.concat('b') = x.t and x.t = 'b'
      context x : X inv SubstringEqualsOtherAttr:
        x.s.substring(2, 2) = x.t
      context x : X inv IndexOfEnumerableNeedle:
        x.s.indexOf(x.t) = 2
      """;

  /**
   * The cross-domain discriminator: s = 'a' yields 'ab', which equals t exactly when t is the
   * 'ab' candidate -- both guards must hold in the admitted case.
   */
  @Test
  public void concatComparesAgainstTheOtherAttributesCandidates() throws Exception {
    ModelFinderResult result =
        find("ConcatEqualsOtherAttr", List.of("a", "ab"), List.of("ab", "b"));

    assertTrue("s = 'a' yields 'ab', matching t's configured 'ab'", result.satisfiable());
    assertTrue(verdictFor(result, "X::ConcatEqualsOtherAttr").holds());
  }

  /**
   * No accidental cross-pair: forcing t = 'b' makes every result ('ab' or 'abb') unequal, so
   * the conjunction is genuinely unsatisfiable.
   */
  @Test
  public void aForcedMismatchingComparandIsUnsatisfiable() throws Exception {
    ModelFinderResult result =
        find("ConcatForcedMismatch", List.of("a", "ab"), List.of("ab", "b"));

    assertFalse("'ab'/'abb' never equal 'b'", result.satisfiable());
  }

  /**
   * substring against the other attribute: s = 'ab' yields substring(2,2) = 'b' (1-based
   * inclusive), matching t's configured 'b'.
   */
  @Test
  public void substringComparesAgainstTheOtherAttributesCandidates() throws Exception {
    ModelFinderResult result =
        find("SubstringEqualsOtherAttr", List.of("ab"), List.of("a", "b"));

    assertTrue("s = 'ab' yields substring(2,2) = 'b', matching t's 'b'", result.satisfiable());
    assertTrue(verdictFor(result, "X::SubstringEqualsOtherAttr").holds());
  }

  /** The needle may be enumerable too: indexOf over the cross product of candidates. */
  @Test
  public void indexOfWithAnEnumerableNeedleCrossesBothCandidateLists() throws Exception {
    ModelFinderResult match =
        find("IndexOfEnumerableNeedle", List.of("abc", "de"), List.of("b", "z"));

    assertTrue("s = 'abc' with t = 'b' gives 1-based position 2", match.satisfiable());
    assertTrue(verdictFor(match, "X::IndexOfEnumerableNeedle").holds());

    ModelFinderResult miss =
        find("IndexOfEnumerableNeedle", List.of("abc", "de"), List.of("z"));
    assertFalse("'z' is absent from both candidates: indexOf is 0, never 2", miss.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> sDomain, List<String> tDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "s", null, sDomain, null, null),
                new AttributeDomain("X", "t", null, tDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringComparands", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
