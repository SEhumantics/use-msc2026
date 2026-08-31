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
 * End-to-end regression for general String SYNTHESIS from an Integer:
 * {@code x.code.toString() = x.name} -- per candidate pair the decimal spelling is
 * compile-time Java (String.valueOf of the Integer candidate), so the comparison holds iff
 * the String attribute chose the candidate spelling the Integer candidate synthesizes.
 * The String LITERAL form (toString() = '42') was already handled by parsing; this pins the
 * String-ATTRIBUTE form that the value-identity encoding previously refused.
 */
public class ToStringSynthesisTest {

  private static final String MODEL =
      """
      model TsSynth
      class X
      attributes
        code : Integer
        name : String
      end
      constraints
      context x : X inv codeMatchesName:
        x.code.toString() = x.name
      """;

  /** code in {5,6}, name in {5,7}: the pair (5,"5") satisfies the synthesis. */
  @Test
  public void synthesisSelectsTheMatchingSpellingPair() throws Exception {
    ModelFinderResult match = find();
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::codeMatchesName").holds());
  }

  /** Mirror polarity: name cannot spell any code candidate -> genuinely unsatisfiable. */
  @Test
  public void synthesisRefutesWhenNoSpellingPairMatches() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1, List.of("x1"))),
            List.of(),
            List.of(
                new AttributeDomain("X", "code", null, List.of("5", "6"), null, null),
                new AttributeDomain("X", "name", null, List.of("7"), null, null)),
            Set.of("X::codeMatchesName"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult miss = SmtModelFinder.find(model, config);
    assertFalse("neither '5' nor '6' is spellable as '7': genuinely unsatisfiable",
        miss.satisfiable());
  }

    private static ModelFinderResult find() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1, List.of("x1"))),
            List.of(),
            List.of(
                new AttributeDomain("X", "code", null, List.of("5", "6"), null, null),
                new AttributeDomain("X", "name", null, List.of("5", "7"), null, null)),
            Set.of("X::codeMatchesName"),
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
    MModel model = USECompiler.compileSpecification(MODEL, "TsSynth", err, factory);
    err.flush();
    if (model == null) throw new AssertionError("did not compile:\n" + buffer);
    return model;
  }
}
