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
 * End-to-end regression for {@code Integer.round()} -- USE's own {@code Op_real_round} defines
 * round on ANY number, and for a crisp Integer it is the IDENTITY ({@code Math.round(intValue)}),
 * so the encoding is simply the operand's own value: a total function whose definedness is the
 * operand's. {@code Real.round()}/{@code UReal.round()} carry genuinely different (rounding)
 * semantics and stay refused. The row's other half, {@code toString()}, needs an Integer-to-
 * String synthesis this encoding deliberately has no machinery for and remains refused.
 */
public class IntegerRoundTest {

  private static final String MODEL =
      """
      model IntegerRound
      class X
      attributes
        i : Integer
        j : Integer
      end
      constraints
      context x : X inv RoundEqualsFive:
        x.i.round() = 5
      context x : X inv RoundIdentity:
        x.i.round() = x.j
      """;

  /** round() carries the operand's VALUE: satisfiable exactly when i can be 5. */
  @Test
  public void roundCarriesTheIntegerValue() throws Exception {
    ModelFinderResult match = find("RoundEqualsFive", List.of("5"), List.of("7"));
    assertTrue("i can be 5, so i.round() = 5 holds", match.satisfiable());
    assertTrue(verdictFor(match, "X::RoundEqualsFive").holds());

    ModelFinderResult mismatch = find("RoundEqualsFive", List.of("3"), List.of("7"));
    assertFalse("i can only be 3, so i.round() = 5 is genuinely unsatisfiable",
        mismatch.satisfiable());
  }

  /** round() is the identity: i.round() = j is satisfiable exactly when i and j can be equal. */
  @Test
  public void roundOnAnIntegerIsTheIdentity() throws Exception {
    ModelFinderResult same = find("RoundIdentity", List.of("6"), List.of("6"));
    assertTrue("i and j share the value 6", same.satisfiable());
    assertTrue(verdictFor(same, "X::RoundIdentity").holds());

    ModelFinderResult disjoint = find("RoundIdentity", List.of("6"), List.of("9"));
    assertFalse("i and j share no value, so i.round() = j is genuinely unsatisfiable",
        disjoint.satisfiable());
  }

  private static ModelFinderResult find(
      String invariantName, List<String> iDomain, List<String> jDomain) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "i", null, iDomain, null, null),
                new AttributeDomain("X", "j", null, jDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "IntegerRound", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
