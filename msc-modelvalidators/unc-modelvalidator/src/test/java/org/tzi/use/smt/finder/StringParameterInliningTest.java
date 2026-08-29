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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for STRING-typed parameters in query-operation inlining. A String
 * parameter's value is a domain INDEX positional within the argument's own configured candidate
 * list, so the parameter binding carries the argument's candidate list on its LocalBinding and
 * every comparison in the body resolves by CONTENT through the same machinery the
 * let-bound-String slice uses.
 *
 * <p>The discriminating shape: the caller's attribute and the body's comparison attribute have
 * DIFFERENT domains ({Zulu} vs {Other}), so a raw-index binding would compare unrelated
 * literals. The parameter must carry the caller's actual content across the call.
 */
public class StringParameterInliningTest {

  private static final String MODEL =
      """
      model ParamString
      class X
      attributes
        s : String
        t : String
      operations
        matches(n : String): Boolean = n = self.t
      end
      constraints
      context x : X inv MatchesT:
        x.matches(x.s)
      """;

  /** Caller and callee domains agree: the parameter carries 'Zulu' across and the body holds. */
  @Test
  public void aStringParameterCarriesTheCallerContent() throws Exception {
    ModelFinderResult match = find(List.of("Zulu"), List.of("Zulu"));

    assertTrue("a.s = 'Zulu' and b.t = 'Zulu', so matches(a.s) holds", match.satisfiable());
    assertTrue(verdictFor(match, "X::MatchesT").holds());
  }

  /** Disjoint content: the body's comparison is genuinely unsatisfiable. */
  @Test
  public void disjointContentMakesTheStringParameterComparisonUnsatisfiable() throws Exception {
    ModelFinderResult result = find(List.of("Zulu"), List.of("Other"));

    assertFalse(
        "a.s = 'Zulu' can never equal b.t = 'Other' -- the parameter must carry content, not"
            + " index position",
        result.satisfiable());
  }

  private static ModelFinderResult find(List<String> aDomain, List<String> bDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("X", "s", null, aDomain, null, null),
                new AttributeDomain("X", "t", null, bDomain, null, null)),
            Set.of("X::MatchesT"),
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
    MModel model = USECompiler.compileSpecification(MODEL, "ParamString", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
