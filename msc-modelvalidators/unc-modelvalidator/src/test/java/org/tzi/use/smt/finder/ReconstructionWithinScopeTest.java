package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * The proposal's proof obligation "reconstruction producing a snapshot within configured scopes",
 * made executable and generic.
 *
 * <p>Every existing round-trip test asserts specific values it expects to see. That catches a wrong
 * witness; it does not catch a witness that is merely OUT OF BOUNDS in some way nobody thought to
 * assert. The check here is the other direction: take whatever snapshot was delivered and hold ALL
 * of it against the configuration it came from -- object counts per class scope, link counts per
 * association scope, and every attribute value against its configured domain.
 */
public class ReconstructionWithinScopeTest {

  /** The real corpus configuration, not a micro-fixture. */
  @Test
  public void everyLibraryWitnessRespectsItsConfiguredScopesAndDomains() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration config = libraryConfiguration(model);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(result.satisfiable());
    assertFalse(result.witnesses().isEmpty());
    for (ScenarioReport witness : result.witnesses()) {
      assertEquals(
          "Library witness out of configured scope",
          List.of(),
          violations(model, config, witness.system().state()));
    }
  }

  /** The UReal fixture, whose value and uncertainty components carry separate domains. */
  @Test
  public void everyURealWitnessRespectsItsConfiguredComponentDomains() throws Exception {
    MModel model = compile(resourcePath("ReliablyFast.use"));
    AnalysisConfiguration config =
        ConfigurationReader.normalize(
                ConfigurationReader.read(resourcePath("ReliablyFast.properties"), "above"),
                ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(result.satisfiable());
    for (ScenarioReport witness : result.witnesses()) {
      assertEquals(
          "UReal witness out of configured scope",
          List.of(),
          violations(model, config, witness.system().state()));
    }
  }

  /**
   * The check itself has to be able to fail, or the two tests above prove only that it runs. The
   * same delivered Library snapshot is held against a DELIBERATELY narrowed configuration, and
   * every kind of violation the checker looks for must be reported.
   */
  @Test
  public void aWitnessOutsideItsConfiguredScopeWouldBeCaught() throws Exception {
    MModel model = compileLibrary();
    AnalysisConfiguration real = libraryConfiguration(model);
    ModelFinderResult result = SmtModelFinder.find(model, real);
    assertTrue(result.satisfiable());
    MSystemState state = result.system().state();

    AnalysisConfiguration impossible =
        new AnalysisConfiguration(
            real.classScopes().stream()
                .map(scope -> new ClassScope(scope.className(), 0, 0))
                .toList(),
            real.associationScopes().stream()
                .map(scope -> new AssociationScope(scope.associationName(), 0, 0))
                .toList(),
            real.attributeDomains().stream()
                .map(
                    domain ->
                        new AttributeDomain(
                            domain.className(),
                            domain.attributeName(),
                            domain.component(),
                            domain.enumeratedValues().isEmpty()
                                ? List.<String>of()
                                : List.of("no-such-value"),
                            domain.lowerBound() == null ? null : BigDecimal.valueOf(-999999),
                            domain.lowerBound() == null ? null : BigDecimal.valueOf(-999998)))
                .toList(),
            real.activeInvariants(),
            real.query(),
            real.timeout(),
            real.modelLimit());

    List<String> violations = violations(model, impossible, state);
    assertFalse("the scope checker must be able to fail", violations.isEmpty());
    assertTrue(
        "class-count violations must be reported: " + violations,
        violations.stream().anyMatch(v -> v.contains("object count")));
    assertTrue(
        "link-count violations must be reported: " + violations,
        violations.stream().anyMatch(v -> v.contains("link count")));
    assertTrue(
        "attribute-domain violations must be reported: " + violations,
        violations.stream().anyMatch(v -> v.contains("outside its configured domain")));
  }

  /**
   * Everything the configuration constrains, checked against the delivered snapshot. Returns one
   * message per violation so a failure names all of them, not just the first.
   */
  private static List<String> violations(
      MModel model, AnalysisConfiguration config, MSystemState state) {
    List<String> violations = new ArrayList<>();

    for (ClassScope scope : config.classScopes()) {
      MClass cls = model.getClass(scope.className());
      int count = state.objectsOfClass(cls).size();
      if (count < scope.min() || (scope.max() >= 0 && count > scope.max())) {
        violations.add(
            "object count "
                + count
                + " for "
                + scope.className()
                + " outside ["
                + scope.min()
                + ", "
                + scope.max()
                + "]");
      }
    }

    for (AssociationScope scope : config.associationScopes()) {
      MAssociation association = model.getAssociation(scope.associationName());
      int count = state.linksOfAssociation(association).size();
      if (count < scope.min() || (scope.max() >= 0 && count > scope.max())) {
        violations.add(
            "link count "
                + count
                + " for "
                + scope.associationName()
                + " outside ["
                + scope.min()
                + ", "
                + scope.max()
                + "]");
      }
    }

    for (AttributeDomain domain : config.attributeDomains()) {
      MClass cls = model.getClass(domain.className());
      if (cls == null) {
        continue;
      }
      MAttribute attribute = cls.attribute(domain.attributeName(), true);
      if (attribute == null) {
        continue;
      }
      for (MObject object : state.objectsOfClass(cls)) {
        Value value = object.state(state).attributeValue(attribute);
        if (value == null || value.isUndefined()) {
          continue;
        }
        String rendered = render(value, domain.component());
        if (rendered == null) {
          continue;
        }
        if (!withinDomain(rendered, domain)) {
          violations.add(
              domain.className()
                  + "."
                  + domain.attributeName()
                  + (domain.component() == null ? "" : "_" + domain.component())
                  + " = "
                  + rendered
                  + " outside its configured domain");
        }
      }
    }
    return violations;
  }

  /** The component of a reconstructed value the given domain row actually constrains. */
  private static String render(Value value, String component) {
    if (value instanceof URealValue ureal) {
      if ("uncertainty".equals(component)) {
        return BigDecimal.valueOf(ureal.uncertainty()).stripTrailingZeros().toPlainString();
      }
      if ("value".equals(component)) {
        return BigDecimal.valueOf(ureal.value()).stripTrailingZeros().toPlainString();
      }
      return null;
    }
    if (component != null) {
      // A component row over a non-UReal value constrains nothing this checker can read.
      return null;
    }
    if (value instanceof StringValue string) {
      return string.value();
    }
    if (value instanceof RealValue real) {
      return BigDecimal.valueOf(real.value()).stripTrailingZeros().toPlainString();
    }
    return value.toString();
  }

  private static boolean withinDomain(String rendered, AttributeDomain domain) {
    if (!domain.enumeratedValues().isEmpty()) {
      for (String candidate : domain.enumeratedValues()) {
        if (candidate.equals(rendered) || numericallyEqual(candidate, rendered)) {
          return true;
        }
      }
      return false;
    }
    if (domain.lowerBound() == null && domain.upperBound() == null) {
      return true;
    }
    BigDecimal numeric;
    try {
      numeric = new BigDecimal(rendered);
    } catch (NumberFormatException e) {
      return true;
    }
    if (domain.lowerBound() != null && numeric.compareTo(domain.lowerBound()) < 0) {
      return false;
    }
    return domain.upperBound() == null || numeric.compareTo(domain.upperBound()) <= 0;
  }

  private static boolean numericallyEqual(String candidate, String rendered) {
    try {
      return new BigDecimal(candidate).compareTo(new BigDecimal(rendered)) == 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static AnalysisConfiguration libraryConfiguration(MModel model) throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.properties");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.properties");
    }
    return ConfigurationReader.normalize(
            ConfigurationReader.read(file, null), ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compileLibrary() throws Exception {
    Path file = Path.of("../benchmark/examples/Library/Library.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Library/Library.use");
    }
    return compile(file);
  }

  private static MModel compile(Path file) throws Exception {
    PrintWriter err = new PrintWriter(System.err);
    MModel model =
        USECompiler.compileSpecification(
            Files.readString(file), file.getFileName().toString(), err, new ModelFactory());
    err.flush();
    if (model == null) {
      throw new AssertionError("model did not compile: " + file);
    }
    return model;
  }

  private static Path resourcePath(String name) throws URISyntaxException {
    return Path.of(
        Objects.requireNonNull(ReconstructionWithinScopeTest.class.getResource("/" + name))
            .toURI());
  }
}
