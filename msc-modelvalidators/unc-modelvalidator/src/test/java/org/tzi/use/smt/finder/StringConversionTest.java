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
 * End-to-end regression for {@code prim.string-operations}' conversion slice: case conversion
 * ({@code toUpper()}/{@code toLower()} -- virtual strings, compared against literals) and
 * numeric conversion ({@code toInteger()}/{@code toReal()} -- an ite chain of the parsed
 * candidates). Semantics confirmed against the use-core bytecode: {@code toInteger} is
 * {@code Integer.parseInt} and {@code toReal} is {@code Double.parseDouble}, each yielding
 * UNDEFINED on {@code NumberFormatException} -- so an unparseable candidate is excluded (total
 * equality treats undefined as unequal), never silently parsed.
 */
public class StringConversionTest {

  private static final String MODEL =
      """
      model StringConversion
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv UpperMatchesLiteral:
        x.s.toUpper() = 'ABC'
      context x : X inv LowerMatchesLiteral:
        x.s.toLower() = 'abc'
      context x : X inv ToIntegerIsFive:
        x.s.toInteger() = 5
      context x : X inv ToIntegerUnparseable:
        x.s.toInteger() = 7
      context x : X inv ToRealIsPointFive:
        x.s.toReal() = 0.5
      context x : X inv ForcedUnparseableParses:
        x.s = 'abc' and x.s.toInteger() = 5
      """;

  /** toUpper over the mixed-case candidate matches the demanded literal. */
  @Test
  public void toUpperIsAVirtualStringComparedAgainstTheLiteral() throws Exception {
    ModelFinderResult result = find("UpperMatchesLiteral", List.of("AbC"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::UpperMatchesLiteral").holds());
  }

  /** toLower over the upper-case candidate matches the demanded literal. */
  @Test
  public void toLowerIsAVirtualStringComparedAgainstTheLiteral() throws Exception {
    ModelFinderResult result = find("LowerMatchesLiteral", List.of("ABC"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "X::LowerMatchesLiteral").holds());
  }

  /** The parsed candidate flows into the Integer comparison: '5' parses to 5. */
  @Test
  public void toIntegerParsesTheConfiguredCandidate() throws Exception {
    ModelFinderResult match = find("ToIntegerIsFive", List.of("5"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::ToIntegerIsFive").holds());

    ModelFinderResult miss = find("ToIntegerIsFive", List.of("6"));
    assertFalse("'6' parses to 6, never 5", miss.satisfiable());
  }

  /** The unparseable candidate yields UNDEFINED (the bytecode's exception handler). */
  @Test
  public void anUnparseableCandidateIsUndefinedNotParsed() throws Exception {
    ModelFinderResult result = find("ToIntegerUnparseable", List.of("abc"));

    assertFalse(
        "'abc' does not parse: toInteger is undefined, and total equality treats undefined"
            + " as unequal -- the candidate must not be silently parsed",
        result.satisfiable());
  }

  /** toReal via Double.parseDouble: '0.5' parses to the exact demanded Real. */
  @Test
  public void toRealParsesTheConfiguredCandidate() throws Exception {
    ModelFinderResult match = find("ToRealIsPointFive", List.of("0.5"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::ToRealIsPointFive").holds());

    ModelFinderResult miss = find("ToRealIsPointFive", List.of("0.7"));
    assertFalse("'0.7' parses to 0.7, never 0.5", miss.satisfiable());
  }

  /**
   * With the spelling forced to the unparseable candidate, the conversion is undefined and the
   * equality cannot hold -- even though the domain ALSO offers '5' whose parsed value would
   * equal 5. An encoding whose ite chain falls through to a parseable sibling's value would
   * wrongly report SAT here.
   */
  @Test
  public void aForcedUnparseableCandidateIsNotRescuedByASiblingValue() throws Exception {
    ModelFinderResult result = find("ForcedUnparseableParses", List.of("abc", "5"));

    assertFalse(
        "s forced to 'abc' makes toInteger undefined -- the chain must not leak the '5'"
            + " candidate's parsed value",
        result.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> domain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "s", null, domain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringConversion", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
