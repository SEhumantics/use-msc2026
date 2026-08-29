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
 * End-to-end regression for isEmpty()/notEmpty() over String set literals.
 * A non-empty String set literal always has elements: isEmpty is false and notEmpty is true,
 * regardless of the caller attribute's value.
 */
public class StringSetEmptinessTest {

  private static final String MODEL =
      """
      model StringSetEmptiness
      class X
      attributes
        s : String
      end
      constraints
      context x : X inv StringSetNotEmpty:
        Set{'alice','carol'}->notEmpty()
      context x : X inv StringSetIsEmpty:
        Set{'alice','carol'}->isEmpty()
      """;

  /** A non-empty String set literal is not empty: notEmpty holds, isEmpty does not. */
  @Test
  public void nonEmptyStringSetLiteralsAreNotEmpty() throws Exception {
    ModelFinderResult notEmpty = find("StringSetNotEmpty", List.of("alice", "carol"));
    assertTrue("Set{'alice','carol'} is non-empty, so notEmpty() holds",
        notEmpty.satisfiable());
    assertTrue(verdictFor(notEmpty, "X::StringSetNotEmpty").holds());

    ModelFinderResult empty = find("StringSetIsEmpty", List.of("alice", "carol"));
    assertFalse("Set{'alice','carol'} is non-empty, so isEmpty() cannot hold",
        empty.satisfiable());
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringSetEmptiness", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
