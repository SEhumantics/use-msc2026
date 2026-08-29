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
 * End-to-end regression for String-constant set literals as quantifier ranges -- the String
 * sibling of the Integer-constant quantifier slice. Each literal element binds the loop
 * variable as a singleton-content LocalBinding (stringOrEnum, candidate list = the literal),
 * and the body's comparisons against String-typed attributes resolve BY CONTENT through
 * contentAwareEquality -- never by raw index.
 *
 * <p>The discriminators use domains DISJOINT in content from the literal so that only genuine
 * content matching distinguishes SAT from UNSAT.
 */
public class StringSetLiteralQuantifierTest {

  private static final String MODEL =
      """
      model StringSetQuant
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv SetExistsMatches:
        Set{'alice','carol'}->exists(n | n = x.s)
      context x : X inv SetForAllRequiresMembers:
        Set{'alice','carol'}->forAll(n | n = x.s)
      """;

  /** exists over {'alice','carol'}: satisfiable exactly when s can be one of the literals. */
  @Test
  public void existsOverAStringSetLiteralMatchesAnElement() throws Exception {
    ModelFinderResult match = find("SetExistsMatches", List.of("alice"));
    assertTrue("s can be 'alice', a member of the literal", match.satisfiable());
    assertTrue(verdictFor(match, "X::SetExistsMatches").holds());

    ModelFinderResult miss = find("SetExistsMatches", List.of("bob"));
    assertFalse("s can only be 'bob', which is not in {'alice','carol'}", miss.satisfiable());
  }

  /**
   * forAll over {'alice','carol'} requires s to equal BOTH literals -- impossible for a single
   * String value, so genuinely unsatisfiable.
   */
  @Test
  public void forAllOverAMultiElementStringLiteralIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result = find("SetForAllRequiresMembers", List.of("alice"));

    assertFalse(
        "s would have to equal both 'alice' and 'carol' at once",
        result.satisfiable());
  }

  private static ModelFinderResult find(String invariantName, List<String> sDomain)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("X", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("X", "s", null, sDomain, null, null)),
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringSetQuant", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
