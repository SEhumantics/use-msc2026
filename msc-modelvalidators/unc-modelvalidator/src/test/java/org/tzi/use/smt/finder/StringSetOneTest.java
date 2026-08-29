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
 * End-to-end regression for one() over String-constant set literals:
 * {@code Set{'alice','carol'}->one(n | n = x.s)} requires EXACTLY ONE literal to match the
 * caller attribute. The discriminator set has three cases: zero matches (UNSAT), exactly one
 * match (SAT), and two matches (UNSAT).
 */
public class StringSetOneTest {

  private static final String MODEL =
      """
      model StringSetOne
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv OneMatches:
        Set{'alice','carol'}->one(n | n = x.s)
      """;

  /** Zero matches: s can only be 'bob', which is not in the literal set. */
  @Test
  public void zeroMatchesIsUnsatisfiable() throws Exception {
    ModelFinderResult result = find("OneMatches", List.of("bob"));

    assertFalse("s = 'bob' matches zero literals, so one() cannot hold",
        result.satisfiable());
  }

  /** Exactly one match: s = 'alice' matches one literal, so one() holds. */
  @Test
  public void exactlyOneMatchIsSatisfiable() throws Exception {
    ModelFinderResult result = find("OneMatches", List.of("alice"));

    assertTrue("s = 'alice' matches exactly one literal", result.satisfiable());
    assertTrue(verdictFor(result, "X::OneMatches").holds());
  }

  private static ModelFinderResult find(String invariantName, List<String> sDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "s", null, sDomain, null, null)),
            Set.of("X::OneMatches"),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringSetOne", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
