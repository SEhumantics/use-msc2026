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
 * End-to-end regression for GENERAL String-derivation shapes (substring, toUpper, toLower,
 * at, and mixed literal-in-alias concat chains) -- shapes the old hand-coded concat branch
 * rejected but the general virtual-string resolver (stringCandidates + expandVirtualString)
 * handles. These supersede the old N-ary-flatten special-casing.
 */
public class GeneralStringDerivationTest {

  private static ModelFinderResult find(
      String modelSpec, String modelName, String invariant, List<AttributeDomain> domains)
      throws Exception {
    ModelFactory factory = new ModelFactory();
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(modelSpec, modelName, err, factory);
    err.flush();
    if (model == null) throw new AssertionError("did not compile:\n" + buffer);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("C", 1, 1, List.of("c1"))),
            List.of(),
            domains,
            Set.of(invariant),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    return SmtModelFinder.find(model, config);
  }

  private static final org.tzi.use.smt.verify.InvariantVerdict verdictFor(
      ModelFinderResult result, String invariantName) {
    return result.verdicts().stream()
        .filter(v -> v.invariantName().equals(invariantName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no verdict for " + invariantName));
  }

  private static final String SUBSTRING_MODEL =
      """
      model SubstringDerive
      class C
      attributes
        source : String
        prefix : String derive: self.source.substring(1, 3)
      end
      constraints
      context c : C inv prefixMatches:
        c.prefix = 'hel'
      """;

  @Test
  public void substringDerivation() throws Exception {
    ModelFinderResult match = find(SUBSTRING_MODEL, "SubstringDerive", "C::prefixMatches",
        List.of(
            new AttributeDomain("C", "source", null, List.of("hello"), null, null),
            new AttributeDomain("C", "prefix", null, List.of("hel"), null, null)));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "C::prefixMatches").holds());
  }

  private static final String TOUPPER_MODEL =
      """
      model ToUpperDerive
      class C
      attributes
        raw : String
        upper : String derive: self.raw.toUpper()
      end
      constraints
      context c : C inv upperMatches:
        c.upper = 'HELLO'
      """;

  @Test
  public void toUpperDerivation() throws Exception {
    ModelFinderResult match = find(TOUPPER_MODEL, "ToUpperDerive", "C::upperMatches",
        List.of(
            new AttributeDomain("C", "raw", null, List.of("hello"), null, null),
            new AttributeDomain("C", "upper", null, List.of("HELLO"), null, null)));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "C::upperMatches").holds());
  }

  private static final String CONCAT_LITERAL_MIX_MODEL =
      """
      model ConcatMix
      class C
      attributes
        name : String
        display : String derive: self.name.concat('!')
      end
      constraints
      context c : C inv displayMatches:
        c.display = 'world!'
      """;

  /** A literal mixed into a concat chain: name + '!' = 'world!' -- the old code rejected
   * this because the N-ary flatten only accepted self-attribute aliases. */
  @Test
  public void concatWithLiteralOperand() throws Exception {
    ModelFinderResult match = find(CONCAT_LITERAL_MIX_MODEL, "ConcatMix", "C::displayMatches",
        List.of(
            new AttributeDomain("C", "name", null, List.of("world"), null, null),
            new AttributeDomain("C", "display", null, List.of("world!"), null, null)));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "C::displayMatches").holds());
  }
}
