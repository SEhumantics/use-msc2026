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
 * End-to-end regression for a String derivation that CONCATENATES two attribute aliases
 * ({@code full : String derive: self.first.concat(self.second)}) -- the attr.derived row's
 * "String derivations beyond the alias/literal forms" residual. Per candidate pair the
 * concatenation is compile-time Java over the configured spellings, so the derived attribute's
 * value is pinned to its own domain's index whose content equals the concatenation; a
 * concatenation absent from the derived domain is genuinely unsatisfiable.
 */
public class ConcatDerivedAttributeTest {

  private static final String MODEL =
      """
      model ConcatDerive
      class C
      attributes
        first : String
        second : String
        full : String derive: self.first.concat(self.second)
      end
      constraints
      context c : C inv fullIsConcat:
        c.full = 'ab'
      context c : C inv fullIsXb:
        c.first = 'x'
      """;

  /** General: first 'a' + second 'b' pins full to 'ab' (USE-confirmed). */
  @Test
  public void concatDerivationPinsTheDerivedValue() throws Exception {
    ModelFinderResult match = find("C::fullIsConcat",
        List.of("'a'"), List.of("'b'"), List.of("'ab'"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "C::fullIsConcat").holds());
  }

  /** A derived domain that cannot offer the concatenation is genuinely unsatisfiable. */
  @Test
  public void concatAbsentFromTheDerivedDomainRefutes() throws Exception {
    ModelFinderResult miss = find("C::fullIsConcat",
        List.of("'a'"), List.of("'b'"), List.of("'xy'"));
    assertFalse("'a'+'b' = 'ab' is not offerable by the domain {xy}", miss.satisfiable());
  }

  /** Pair selection: forcing first='x' must pin full to 'xb', not 'ab'. */
  @Test
  public void forcingTheFirstCandidateSelectsTheMatchingPair() throws Exception {
    ModelFinderResult xb = find("C::fullIsXb",
        List.of("'a'", "'x'"), List.of("'b'"), List.of("'ab'", "'xb'"));
    assertTrue("first 'x' + second 'b' pins full to 'xb'", xb.satisfiable());
    assertTrue(verdictFor(xb, "C::fullIsXb").holds());
  }

  private static ModelFinderResult find(
      String invariant, List<String> first, List<String> second, List<String> full)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("C", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("C", "first", null,
                    first.stream().map(v -> v.substring(1, v.length() - 1)).toList(), null, null),
                new AttributeDomain("C", "second", null,
                    second.stream().map(v -> v.substring(1, v.length() - 1)).toList(), null, null),
                new AttributeDomain("C", "full", null,
                    full.stream().map(v -> v.substring(1, v.length() - 1)).toList(), null, null)),
            Set.of(invariant),
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
    MModel model = USECompiler.compileSpecification(MODEL, "ConcatDerive", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
