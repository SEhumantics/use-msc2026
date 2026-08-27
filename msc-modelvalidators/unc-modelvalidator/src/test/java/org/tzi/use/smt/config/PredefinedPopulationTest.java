package org.tzi.use.smt.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * The bare {@code ClassName} and {@code AssociationName} keys of the shared {@code .properties}
 * format: PREDEFINED OBJECTS and PREDEFINED LINKS.
 *
 * <p>These are the incumbent's keys, so kk-modelvalidator defines what they mean and this reader
 * reproduces it rather than inventing a reading. The three facts pinned here are all established
 * from that source:
 *
 * <ul>
 *   <li>A bare class-name key names OBJECT IDENTITIES, not a population size. {@code
 *       PropertyConfigurationVisitor.setClassConfigurator} (lines 328-333) calls {@code
 *       setSpecificValues} -- which sets min=max=|names| ({@code ClassConfigurator} lines 49-52) --
 *       and then UNCONDITIONALLY overwrites both with {@code Class_min}/{@code Class_max}, whose
 *       error values are {@code DefaultConfigurationValues.objectsPerClassMin/Max} = 1/1. So the
 *       name list never changes how many objects exist; it only labels them, and {@code
 *       ClassConfigurator.generateObjectsTuple} (lines 20-36) pads any remaining slots with
 *       generated names.
 *   <li>A bare association-name key names LINK TUPLES that are FORCED, and it DOES raise the link
 *       count. {@code AssociationConfigurator.lowerBound} (lines 39-67) puts every listed tuple in
 *       the relation's LOWER bound, and {@code setSpecificValues} (lines 162-165) sets min=max=k
 *       before {@code setLimits} can only RAISE the minimum ({@code if (min >= specificValues
 *       .size())}, line 174).
 *   <li>Nothing anywhere infers symmetry: {@code lowerBound} adds exactly the tuples that are
 *       written down, which is why {@code GraphColoring.properties} spells out both {@code (r0,r4)}
 *       and {@code (r4,r0)}.
 * </ul>
 */
public class PredefinedPopulationTest {

  private static final ConfigurationVocabulary GENEALOGY =
      ConfigurationVocabulary.of(
          Set.of("Person"),
          Set.of("Parenthood"),
          Set.of("Person_fName"),
          Set.of("Person_fName"),
          Set.of("Person_nameUnique"));

  /**
   * A bare class-name key and a bare association-name key must both be UNDERSTOOD, not retained as
   * "not yet understood" diagnostics. This is the single fact that keeps eight corpus rows from
   * ever reaching translation.
   */
  @Test
  public void predefinedObjectsAndLinksAreUnderstoodRatherThanRefused() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 4
            Person_max = 4
            Person = Set{gp,pa,ch,gc}
            Person_fName = Set{'Vito','Sonny'}
            Parenthood = Set{(gp,pa),(pa,ch),(ch,gc)}
            Parenthood_min = 3
            Parenthood_max = 3
            Person_nameUnique = active
            """);

    ConfigurationReader.NormalizedConfiguration normalized =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY);

    assertEquals(
        "no key of this configuration is beyond the reader",
        List.of(),
        normalized.diagnostics().stream()
            .map(ConfigurationDiagnostic::key)
            .sorted()
            .collect(Collectors.toList()));
  }

  /**
   * The number of predefined link tuples is a LOWER BOUND on the link count that a smaller
   * configured {@code _min} cannot lower -- {@code AssociationConfigurator.setLimits} line 174 only
   * assigns the read minimum when it is at least the number of specific values, so {@code
   * Parenthood_min = 0} against three forced tuples still reads as three.
   */
  @Test
  public void predefinedLinkCountRaisesTheLinkMinimumOverASmallerConfiguredBound()
      throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 4
            Person_max = 4
            Person = Set{gp,pa,ch,gc}
            Parenthood = Set{(gp,pa),(pa,ch),(ch,gc)}
            Parenthood_min = 0
            Parenthood_max = -1
            """);

    AnalysisConfiguration configuration =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
            .configuration();

    AssociationScope parenthood =
        configuration.associationScopes().stream()
            .filter(scope -> scope.associationName().equals("Parenthood"))
            .findFirst()
            .orElseThrow();
    assertEquals("three forced tuples must raise the configured minimum of 0", 3, parenthood.min());
    assertEquals("an unbounded maximum stays unbounded", -1, parenthood.max());
    assertEquals(
        "the tuples are kept in the association's declared end order",
        List.of(List.of("gp", "pa"), List.of("pa", "ch"), List.of("ch", "gc")),
        parenthood.links());
  }

  /**
   * A bare class-name key labels slots; it never resizes the population. {@code Person_min}/{@code
   * Person_max} alone decide how many objects exist, exactly as in the incumbent.
   */
  @Test
  public void predefinedObjectNamesLabelSlotsWithoutResizingThePopulation() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 2
            Person_max = 4
            Person = Set{gp,pa}
            Person_fName = Set{'Vito'}
            """);

    AnalysisConfiguration configuration =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
            .configuration();

    assertTrue(
        "the bounds are the configured ones, not the name count: " + configuration.classScopes(),
        configuration.classScopes().contains(new ClassScope("Person", 2, 4, List.of("gp", "pa"))));
  }

  /**
   * Object names are NOT quote-stripped; String attribute domains in the same section still are.
   * {@code GraphColoring.properties} depends on exactly this split -- {@code Region =
   * Set&#123;r0,...&#125;} names objects while {@code Region_name = Set&#123;'R0',...&#125;}
   * enumerates String candidates.
   */
  @Test
  public void objectNamesKeepTheirTextWhileStringDomainsAreStillUnquoted() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 1
            Person_max = 1
            Person = Set{p0}
            Person_fName = Set{'P0'}
            """);

    AnalysisConfiguration configuration =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
            .configuration();

    assertEquals(
        List.of("p0"),
        configuration.classScopes().stream()
            .filter(scope -> scope.className().equals("Person"))
            .findFirst()
            .orElseThrow()
            .objectNames());
    assertEquals(
        List.of("P0"),
        configuration.attributeDomains().stream()
            .filter(domain -> domain.attributeName().equals("fName"))
            .findFirst()
            .orElseThrow()
            .enumeratedValues());
  }

  /**
   * An association with NO predefined tuples must keep reading exactly as it did before predefined
   * links existed. This is the additivity guarantee for the 50 corpus rows that do not use the
   * feature.
   */
  @Test
  public void anAssociationWithoutPredefinedTuplesKeepsItsPlainBounds() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 0
            Person_max = 6
            Parenthood_min = 0
            Parenthood_max = -1
            """);

    AnalysisConfiguration configuration =
        ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
            .configuration();

    assertTrue(
        "unchanged: " + configuration.associationScopes(),
        configuration.associationScopes().contains(new AssociationScope("Parenthood", 0, -1)));
    assertTrue(
        "unchanged: " + configuration.classScopes(),
        configuration.classScopes().contains(new ClassScope("Person", 0, 6)));
  }

  /**
   * {@code GraphColoring.properties} is the one corpus file whose predefined links the incumbent
   * cannot express consistently: 134 forced {@code Adjacent} tuples with no {@code Adjacent_min}/
   * {@code Adjacent_max}, so {@code AssociationConfigurator.setLimits(1, 1)} leaves min=134 but
   * drives max down to the READ minimum, 1 ({@code else if (max <= min) ... this.max = min;}, lines
   * 180-185). Kodkod only ever "satisfies" that because at {@code bitwidth := 8} both the count and
   * the constant 134 wrap to -122 -- which is exactly the otherwise-unexplained bitwidth table
   * documented at the top of that same file. On exact SMT arithmetic there is no such accident, so
   * this reader must REFUSE the contradiction rather than emit a spurious UNSAT, and must say why
   * instead of hiding behind the generic unsupported-key message.
   */
  @Test
  public void contradictoryPredefinedLinkBoundsFailClosedWithTheirOwnReason() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 3
            Person_max = 3
            Person = Set{a,b,c}
            Parenthood = Set{(a,b),(b,a),(b,c)}
            """);

    ConfigurationReadException thrown =
        assertThrows(
            ConfigurationReadException.class,
            () ->
                ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
                    .requireSupported());

    assertFalse(
        "must not be the generic unsupported-key refusal: " + thrown.getMessage(),
        thrown.getMessage().contains("unsupported configuration key(s)"));
    assertTrue(
        "the refusal must name the forced-tuple count that cannot fit: " + thrown.getMessage(),
        thrown.getMessage().contains("3") && thrown.getMessage().contains("Parenthood"));
  }

  /**
   * A link end naming an object the class does not predefine is refused rather than guessed at. The
   * incumbent additionally accepts generated {@code classname&lt;n&gt;} spellings for slots between
   * the name list and {@code Class_min} ({@code PropertyConfigurationVisitor.checkComplexElement},
   * lines 478-495); no corpus row uses that form, so it stays outside this slice instead of being
   * reproduced untested.
   */
  @Test
  public void aLinkEndOutsideThePredefinedObjectsFailsClosed() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 2
            Person_max = 2
            Person = Set{gp,pa}
            Parenthood = Set{(gp,nobody)}
            """);

    ConfigurationReadException thrown =
        assertThrows(
            ConfigurationReadException.class,
            () ->
                ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
                    .requireSupported());

    assertTrue(
        "the refusal must name the unresolvable end: " + thrown.getMessage(),
        thrown.getMessage().contains("nobody"));
  }

  /**
   * More predefined names than the class's own maximum would silently drop objects (the incumbent
   * truncates at {@code bound} in {@code ClassConfigurator.generateObjectsTuple}, line 25).
   * Silently shrinking a configured population is exactly the kind of quiet approximation this
   * translation refuses to make.
   */
  @Test
  public void morePredefinedObjectsThanTheClassMaximumFailsClosed() throws Exception {
    Path file =
        temporaryConfiguration(
            """
            Person_min = 1
            Person_max = 1
            Person = Set{gp,pa,ch}
            """);

    ConfigurationReadException thrown =
        assertThrows(
            ConfigurationReadException.class,
            () ->
                ConfigurationReader.normalize(ConfigurationReader.read(file, null), GENEALOGY)
                    .requireSupported());

    assertTrue(
        "the refusal must name the class and both counts: " + thrown.getMessage(),
        thrown.getMessage().contains("Person") && thrown.getMessage().contains("3"));
  }

  private static Path temporaryConfiguration(String body) throws Exception {
    Path file = Files.createTempFile("predefined", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    return file;
  }
}
