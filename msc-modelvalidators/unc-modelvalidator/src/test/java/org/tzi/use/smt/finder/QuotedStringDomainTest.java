package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * A quoted enumerated String domain -- the spelling the incumbent's own shared {@code .properties}
 * files use, including the flagship {@code Library.properties} -- must denote the same candidate
 * strings an OCL literal denotes.
 *
 * <p>String domains are encoded as INDICES into {@code AttributeDomain.enumeratedValues()}, so a
 * value stored as {@code 'ok'} rather than {@code ok} corrupts both directions at once: {@code
 * ExpressionTranslator.resolve} cannot find the OCL literal {@code 'ok'} (whose value is {@code
 * ok}) in the domain and falls back to the undefined-string sentinel, making {@code label = 'ok'}
 * unsatisfiable -- a false UNSAT; and {@code SmtValueDecoder} hands back the quote characters as
 * part of the reconstructed attribute value.
 *
 * <p>The incumbent fixes the semantics: {@code PropertyConfigurationVisitor.adjustElement} trims,
 * and, if and only if the attribute's (element) type is String, removes ALL {@code '} characters,
 * keeping the result even when it is empty.
 */
public class QuotedStringDomainTest {

  /**
   * The false-UNSAT half. {@code Tag_label = Set&#123;'ok','bad'&#125;} offers {@code ok}, so an
   * invariant demanding {@code self.label = 'ok'} is satisfiable and independently TRUE.
   */
  @Test
  public void aStringLiteralInvariantMatchesItsQuotedEnumeratedDomain() throws Exception {
    MModel model = compile(resourcePath("QuotedStringDomain.use"));
    ModelFinderResult result = SmtModelFinder.find(model, config(model, "QuotedStringDomain"));

    assertTrue(
        "'ok' is in the configured domain, so the invariant must be satisfiable",
        result.satisfiable());
    assertEquals(1, result.verdicts().size());
    InvariantVerdict verdict = result.verdicts().getFirst();
    assertEquals("Tag::LabelIsOk", verdict.invariantName());
    assertTrue(
        "USE must independently agree the witness satisfies label = 'ok'",
        result.allActiveInvariantsHold());
  }

  /** The reconstruction half: the decoded value is the string itself, never its source spelling. */
  @Test
  public void theReconstructedStringValueCarriesNoQuoteCharacters() throws Exception {
    MModel model = compile(resourcePath("QuotedStringDomain.use"));
    ModelFinderResult result = SmtModelFinder.find(model, config(model, "QuotedStringDomain"));

    assertTrue(result.satisfiable());
    MSystemState state = result.system().state();
    MObject tag = state.objectsOfClass(model.getClass("Tag")).iterator().next();
    Value label = tag.state(state).attributeValue("label");
    assertTrue(label instanceof StringValue);
    assertEquals("ok", ((StringValue) label).value());
    assertFalse(
        "the reconstructed object diagram must not report the domain's quote characters",
        ((StringValue) label).value().contains("'"));
  }

  /**
   * The empty case, which {@code benchmark/examples/Redefines/Redefines.properties} depends on:
   * {@code Set&#123;''&#125;} is a ONE-element domain whose single candidate is the empty string,
   * not an empty domain. {@code AttributeEncoder.guardString} throws on an empty domain, so getting
   * this wrong turns that fixture into a spurious error rather than the UNSAT it documents.
   */
  @Test
  public void aQuotedEmptyStringIsAOneElementDomainContainingTheEmptyString() throws Exception {
    MModel model = compile(resourcePath("EmptyStringDomain.use"));
    AnalysisConfiguration config = config(model, "EmptyStringDomain");

    assertEquals(List.of(""), domainOf(config, "B", "tagB").enumeratedValues());

    // Redefines' own documented intent, end to end: the entire candidate domain is the empty
    // string, so nothing can satisfy TagBNotEmpty. This must be a real UNSAT -- dropping the
    // empty element instead leaves guardString an empty String domain, which it rejects
    // outright, turning a documented UNSAT into a spurious error.
    assertFalse(SmtModelFinder.find(model, config).satisfiable());
  }

  /**
   * Parity guard against over-stripping: {@code adjustElement} touches quotes for String-typed
   * attributes ONLY. A QUOTED non-String domain therefore keeps its quote characters verbatim, and
   * the malformed configuration still fails loudly in {@code AttributeEncoder}'s number parsing
   * instead of being silently repaired into a number the file never wrote.
   *
   * <p>The fixture has to be quoted for this to discriminate anything: an unquoted numeric domain
   * is fixed by a quote strip as much as by no quote strip, so it cannot tell the two apart.
   */
  @Test
  public void aQuotedNonStringEnumeratedDomainKeepsItsQuotesAndIsRejected() throws Exception {
    MModel model = compile(resourcePath("QuotedNonStringDomain.use"));
    AnalysisConfiguration config = config(model, "QuotedNonStringDomain");

    assertEquals(List.of("'1'", "'2'"), domainOf(config, "Counter", "rank").enumeratedValues());
    assertThrows(
        "a quoted Integer domain is malformed and must not be silently repaired",
        SmtTranslationException.class,
        () -> SmtModelFinder.find(model, config));
  }

  /** The plain non-String case: an unquoted numeric domain is untouched. */
  @Test
  public void anUnquotedNonStringEnumeratedDomainIsUnchanged() throws Exception {
    MModel model = compile(resourcePath("QuotedStringDomain.use"));
    AnalysisConfiguration config = config(model, "QuotedStringDomain");

    assertEquals(List.of("1", "2"), domainOf(config, "Tag", "rank").enumeratedValues());
  }

  /**
   * The incumbent removes ALL quote characters, not just surrounding ones, so {@code 'O'Brien'}
   * denotes the candidate {@code OBrien} in both tools. Parity on the one file format both
   * validators read outranks the more defensible surrounding-quotes-only reading, and this pins
   * which of the two is implemented -- without a fixture carrying an interior quote, nothing
   * distinguishes them.
   */
  @Test
  public void everyQuoteCharacterIsRemoved() throws Exception {
    MModel model = compile(resourcePath("QuotedStringDomain.use"));
    AnalysisConfiguration config = config(model, "QuotedStringDomain");

    assertEquals(
        List.of("ok", "bad", "OBrien"), domainOf(config, "Tag", "label").enumeratedValues());
  }

  private static AttributeDomain domainOf(
      AnalysisConfiguration config, String className, String attributeName) {
    return config.attributeDomains().stream()
        .filter(
            d ->
                d.className().equals(className)
                    && d.attributeName().equals(attributeName)
                    && d.component() == null)
        .findFirst()
        .orElseThrow();
  }

  private static AnalysisConfiguration config(MModel model, String stem) throws Exception {
    RawConfiguration raw = ConfigurationReader.read(resourcePath(stem + ".properties"), null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(Path file) throws Exception {
    String source = Files.readString(file);
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            source, file.getFileName().toString(), err, new ModelFactory());
    err.flush();
    return model;
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(QuotedStringDomainTest.class.getResource("/" + name)).toURI());
  }
}
