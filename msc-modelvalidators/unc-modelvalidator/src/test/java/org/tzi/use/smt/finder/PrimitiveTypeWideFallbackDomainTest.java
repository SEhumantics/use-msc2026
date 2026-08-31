package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReadException;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.encode.FragmentBoundary;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * The PRIMITIVE-TYPE-WIDE fallback attribute domain: {@code Integer_min}/{@code Integer_max} and
 * {@code Real_min}/{@code Real_max} bound every attribute of that type for which the configuration
 * declares no per-attribute domain of its own.
 *
 * <p>This is the incumbent's own semantics, not an invention here. {@code
 * AttributeConfigurator.upperBound} (kk-modelvalidator, lines 117-182) branches on whether the
 * attribute has {@code domainValues}: with none it takes the TYPE's bound and, for Integer, filters
 * it to the type's configured range ({@code useMinMaxBounds}, lines 129-164); with some it uses
 * those values alone and never consults the type range at all. That branch IS the precedence rule,
 * and {@link #anExplicitPerAttributeDomainWinsOverTheTypeWideOne} and {@link
 * #explicitPerAttributeBoundsAreNotIntersectedWithTheTypeWideRange} pin both halves of it.
 *
 * <p>The String half is deliberately NOT implemented, and two tests hold it closed from both ends
 * -- {@link #theStringTypeWideKeysAreRefusedRatherThanReinterpretedAsADomain} at the configuration
 * level and {@link #aStringAttributeIsNeverGivenAFallbackDomainByTheIntegerRange} at the encoding
 * level. {@code StringConfigurator} reads only {@code ranges.get(0).getUpper()} and uses it as a
 * COUNT of string atoms, padding the universe with GENERATED placeholder spellings ({@code
 * type.name() + "_string" + i}, lines 38-44, 57-60 and 77-87), and never reads {@code String_min}
 * at all. That is a cardinality, not a domain of values, and this encoder has no generated-spelling
 * notion to match it with.
 */
public class PrimitiveTypeWideFallbackDomainTest {

  @Test
  public void anIntegerAttributeWithNoPerAttributeDomainIsBoundedByTheTypeWideRange()
      throws Exception {
    ModelFinderResult result = find("TypeWideFallback", null);

    assertTrue("Integer_min/Integer_max must give salary a domain", result.satisfiable());
    assertTrue(
        "the USE evaluator must independently confirm the reconstructed witness",
        result.allActiveInvariantsHold());
    assertEquals(1, result.verdicts().size());
    for (InvariantVerdict verdict : result.verdicts()) {
      assertTrue(verdict.invariantName() + " must be re-evaluated TRUE", verdict.holds());
    }

    MSystemState state = result.system().state();
    for (MObject employee : state.objectsOfClass(result.system().model().getClass("Employee"))) {
      int salary = integer(employee, state, "salary");
      assertTrue("salary must respect Integer_min = 0, was " + salary, salary >= 0);
      assertTrue("salary must respect Integer_max = 3, was " + salary, salary <= 3);
      assertTrue("PositiveSalary must actually hold, was " + salary, salary > 0);
    }
  }

  @Test
  public void theTypeWideRangeConstrainsRatherThanMerelyRegisteringASymbol() throws Exception {
    ModelFinderResult result = find("TypeWideFallback", "belowThreshold");

    assertFalse(
        "[-3,0] holds no value satisfying salary > 0, so this must be UNSAT", result.satisfiable());
  }

  @Test
  public void anExplicitPerAttributeDomainWinsOverTheTypeWideOne() throws Exception {
    ModelFinderResult result = find("TypeWideFallback", "enumeratedWins");

    assertTrue("Employee_salary = Set{40} must be satisfiable on its own", result.satisfiable());
    assertTrue(result.allActiveInvariantsHold());

    MSystemState state = result.system().state();
    MObject employee =
        state.objectsOfClass(result.system().model().getClass("Employee")).iterator().next();
    assertEquals(
        "the explicit enumerated domain must be used whole, not clamped into the type-wide range",
        40,
        integer(employee, state, "salary"));
  }

  @Test
  public void explicitPerAttributeBoundsAreNotIntersectedWithTheTypeWideRange() throws Exception {
    ModelFinderResult result = find("TypeWideFallback", "boundsWin");

    assertTrue(
        "explicit [50,50] bounds must win outright; intersecting them with the type-wide [0,3]"
            + " would empty the domain and make this UNSAT",
        result.satisfiable());
    assertTrue(result.allActiveInvariantsHold());

    MSystemState state = result.system().state();
    MObject employee =
        state.objectsOfClass(result.system().model().getClass("Employee")).iterator().next();
    assertEquals(50, integer(employee, state, "salary"));
  }

  /**
   * INVERTED 2026-08-31 (the String-universe port): the old pin demanded a refusal for
   * String_min/String_max; the port now consumes them with the incumbent's own semantics --
   * String_max is a count of string atoms, the universe padded with generated
   * "String_string<i>" spellings -- so an unconfigured String attribute becomes encodable
   * instead of refused. StringFallbackUniverseTest pins the ported behavior end to end; this
   * fixture-level test pins that the same key set that used to refuse now solves.
   */
  @Test
  public void theStringTypeWideKeysPadTheUniverseForUnconfiguredStringAttributes()
      throws Exception {
    ModelFinderResult result = find("TypeWideFallbackString", null);
    assertTrue(
        "String_max = 4 must give the unconfigured attribute a padded candidate universe,"
            + " making the trivial invariant solvable",
        result.satisfiable());
    assertTrue(result.allActiveInvariantsHold());
  }

  /**
   * The fail-closed half stated at the ENCODING level rather than the configuration one: with no
   * String key at all there is nothing to misread, and an unconfigured String attribute still gets
   * no symbol and still refuses every invariant that reads it. Without this, dropping the String
   * exclusion from {@link SmtModelFinder} could go unnoticed.
   */
  @Test
  public void aStringAttributeIsNeverGivenAFallbackDomainByTheIntegerRange() throws Exception {
    try {
      find("TypeWideFallbackStringUnconfigured", null);
      fail("a String attribute has no primitive-type-wide fallback and must keep failing closed");
    } catch (SmtTranslationException expected) {
      assertTrue(
          "the refusal must stay an encoding-scope refusal, got: " + expected.getMessage(),
          expected.getMessage().contains(FragmentBoundary.ENCODING_SCOPE.name()));
      assertTrue(
          "the refusal must name the unconfigured attribute, got: " + expected.getMessage(),
          expected.getMessage().contains("Person.name"));
    }
  }

  private static ModelFinderResult find(String resource, String section) throws Exception {
    MModel model = compile(resourcePath(resource + ".use"));
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw =
        ConfigurationReader.read(resourcePath(resource + ".properties"), section);
    AnalysisConfiguration config =
        ConfigurationReader.normalize(raw, vocabulary).requireSupported();
    return SmtModelFinder.find(model, config);
  }

  private static int integer(MObject object, MSystemState state, String attribute) {
    Object value = object.state(state).attributeValue(attribute);
    assertTrue(
        attribute + " must reconstruct as a USE IntegerValue, was " + value,
        value instanceof IntegerValue);
    return ((IntegerValue) value).value();
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
        Objects.requireNonNull(PrimitiveTypeWideFallbackDomainTest.class.getResource("/" + name))
            .toURI());
  }
}
