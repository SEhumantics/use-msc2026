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
 * End-to-end regression for {@code toString()} on a crisp Integer attribute compared against
 * a String literal: {@code x.a.toString() = '42'} is equivalent to {@code x.a = 42} because
 * the decimal string representation is injective for Integers. The encoding resolves the
 * String literal to its Integer value and compares the attribute symbol directly.
 *
 * <p>Before this slice, {@code toString()} was an unconditional refusal under
 * {@code prim.integer-round-toString}. The Real/UReal remainder and general String synthesis
 * stay out of slice (they need the canonical string table or Real semantics).
 */
public class IntegerToStringTest {

  private static final String MODEL =
      """
      model IntegerToString
      class X
      attributes
        a : Integer
        b : Integer
      end
      constraints
      context x : X inv ToStringIs42:
        x.a.toString() = '42'
      context x : X inv ToStringNotSeven:
        x.a.toString() <> '7'
      """;

  /** toString() carries the attribute's VALUE: satisfiable exactly when a can be 42. */
  @Test
  public void toStringCarriesTheIntegerValue() throws Exception {
    ModelFinderResult match = find("ToStringIs42", List.of("42"), List.of("7"));
    assertTrue("a = 42 gives toString() = '42'", match.satisfiable());
    assertTrue(verdictFor(match, "X::ToStringIs42").holds());

    ModelFinderResult miss = find("ToStringIs42", List.of("7"), List.of("7"));
    assertFalse("a = 7 gives toString() = '7', not '42'", miss.satisfiable());
  }

  /** The negative polarity: toString() <> '7' is satisfiable when a can be 42. */
  @Test
  public void toStringInequalityCarriesTheIntegerValue() throws Exception {
    ModelFinderResult result = find("ToStringNotSeven", List.of("42"), List.of("7"));
    assertTrue("a = 42 gives toString() = '42' ≠ '7'", result.satisfiable());
    assertTrue(verdictFor(result, "X::ToStringNotSeven").holds());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> aDomain, List<String> bDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "a", null, aDomain, null, null),
                new AttributeDomain("X", "b", null, bDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "IntegerToString", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
