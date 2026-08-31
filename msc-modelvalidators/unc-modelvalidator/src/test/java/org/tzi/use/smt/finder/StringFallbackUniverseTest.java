package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for the {@code String_max} universe port
 * ({@code config.primitive-type-wide-value-domain}'s String half): the incumbent's
 * {@code StringConfigurator} reads {@code String_max} as a COUNT of string atoms and pads the
 * string universe with generated placeholder spellings ({@code "String_string" + i},
 * i from the model-wide specific count upward), so a String attribute WITHOUT an explicit
 * domain is free over that padded universe instead of being unencodable. This tool now ports
 * exactly that: with {@code String_max = k}, an unconfigured String attribute's candidate
 * space is the model's explicit spellings (capped at k) plus generated placeholders up to k
 * total; an attribute WITH an explicit domain keeps it (kk's per-attribute domain restricts on
 * top of the shared universe). {@code String_min} is accepted-and-ignored when {@code
 * String_max} is present, mirroring kk, which never reads it; alone it still refuses.
 */
public class StringFallbackUniverseTest {

  private static final String MODEL =
      """
      model StringUniverse
      class X
      attributes
        s : String
        t : String
      end
      constraints
      context x : X inv sPicksPlaceholder:
        x.s = 'String_string3'
      context x : X inv sCannotTakeForeignSpelling:
        x.s = 'zorblug'
      context x : X inv tKeepsItsExplicitDomain:
        x.t = 'alice'
      context x : X inv tCannotTakePlaceholders:
        x.t = 'String_string3'
      """;

  /**
   * The reader applies USE's own default-active invariant rule (the same lesson the RangeBound
   * corpus authoring learned): an invariant not mentioned in the section is ACTIVE, so each
   * scenario must name its one active invariant and explicitly deactivate the other three.
   */
  private static String properties(String activeInvariant) {
    StringBuilder sb = new StringBuilder("[main]\n\nX_min = 1\nX_max = 1\n\nString_max = 5\n\nX_t = Set{'alice'}\n\n");
    for (String inv : List.of("X_sPicksPlaceholder", "X_sCannotTakeForeignSpelling",
        "X_tKeepsItsExplicitDomain", "X_tCannotTakePlaceholders")) {
      sb.append(inv).append(" = ").append(inv.equals(activeInvariant) ? "active" : "inactive").append("\n");
    }
    return sb.toString();
  }

  /** The shared universe makes the placeholder spelling a genuine value of s. */
  @Test
  public void unconfiguredStringAttributeTakesAPlaceholderValue() throws Exception {
    ModelFinderResult match = find(Set.of("X::sPicksPlaceholder"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::sPicksPlaceholder").holds());
  }

  /** The universe is bounded: a spelling outside it is impossible, not merely false. */
  @Test
  public void unconfiguredStringAttributeCannotLeaveTheUniverse() throws Exception {
    ModelFinderResult miss = find(Set.of("X::sCannotTakeForeignSpelling"));
    assertFalse("'zorblug' is not in the padded universe of 5 atoms", miss.satisfiable());
  }

  /** An explicit per-attribute domain restricts on top of the shared universe. */
  @Test
  public void explicitDomainWinsOverTheUniverse() throws Exception {
    ModelFinderResult match = find(Set.of("X::tKeepsItsExplicitDomain"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::tKeepsItsExplicitDomain").holds());

    ModelFinderResult miss = find(Set.of("X::tCannotTakePlaceholders"));
    assertFalse("t's explicit domain is {alice}: placeholders are not candidates",
        miss.satisfiable());
  }

  /** Reader-level pin: the synthesized candidate list is exactly the padded universe. */
  @Test
  public void readerSynthesizesExactlyFiveCandidates() throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    Path file = Files.createTempFile("string-universe", ".properties");
    Files.writeString(file, properties("X_sPicksPlaceholder"));
    RawConfiguration raw = ConfigurationReader.read(file, "main");
    AnalysisConfiguration config = ConfigurationReader.normalize(raw, vocabulary).requireSupported();
    AttributeDomain s = config.attributeDomains().stream()
        .filter(d -> d.className().equals("X") && d.attributeName().equals("s"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no synthesized domain for the unconfigured s"));
    assertEquals(List.of("alice", "String_string2", "String_string3", "String_string4",
        "String_string5"), s.enumeratedValues());
  }

  private static ModelFinderResult find(Set<String> activeInvariant) throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    Path file = Files.createTempFile("string-universe", ".properties");
    Files.writeString(file, properties(activeInvariant.iterator().next().replace("::", "_")));
    RawConfiguration raw = ConfigurationReader.read(file, "main");
    AnalysisConfiguration config = ConfigurationReader.normalize(raw, vocabulary).requireSupported();
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
    MModel model = USECompiler.compileSpecification(MODEL, "StringUniverse", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
