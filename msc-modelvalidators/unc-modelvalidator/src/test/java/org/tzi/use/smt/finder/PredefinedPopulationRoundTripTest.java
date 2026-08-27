package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.encode.FragmentBoundary;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * End-to-end proof that predefined objects and predefined links survive configuration reading,
 * encoding, solving AND reconstruction -- i.e. that the witness really does contain the exact link
 * set the {@code .properties} file wrote down, between the exact objects it named.
 *
 * <p>The fixture is deliberately DIRECTION-SENSITIVE. {@code Holds = Set{(s1,c1),(s1,c2)}} loads
 * all the links onto ONE shelf, so a translation that transposed the tuple ends -- or that read the
 * grid's axes positionally instead of resolving them against the model's own declared association
 * ends, the failure mode Task 3.6 already found once -- cannot reproduce it by accident.
 */
public class PredefinedPopulationRoundTripTest {

  private static final String MODEL =
      """
      model Warehouse

      class Shelf
      attributes
        tag : String
      end

      class Crate
      attributes
        code : String
      end

      association Holds between
        Shelf[0..*] role shelf
        Crate[0..*] role crate
      end

      constraints

      context s : Shelf inv tagged:
        s.tag <> 'unset'
      """;

  @Test
  public void predefinedObjectsAndLinksReachTheReconstructedWitnessUnchanged() throws Exception {
    MModel model = compile(MODEL, "Warehouse");
    AnalysisConfiguration config =
        readConfig(
            model,
            """
            Shelf_min = 2
            Shelf_max = 2
            Shelf = Set{s1,s2}
            Shelf_tag = Set{'left','right'}
            Crate_min = 2
            Crate_max = 2
            Crate = Set{c1,c2}
            Crate_code = Set{'X','Y'}
            Holds = Set{(s1,c1),(s1,c2)}
            Holds_min = 2
            Holds_max = 2
            Shelf_tagged = active
            """);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT", result.satisfiable());
    MSystemState state = result.system().state();
    assertEquals(2, state.objectsOfClass(model.getClass("Shelf")).size());
    assertEquals(2, state.objectsOfClass(model.getClass("Crate")).size());

    MAssociation holds = model.getAssociation("Holds");
    List<String> links = new ArrayList<>();
    for (MLink link : state.linksOfAssociation(holds).links()) {
      List<String> ends = new ArrayList<>();
      for (MObject object : link.linkedObjects()) {
        ends.add(object.name());
      }
      links.add(String.join("->", ends));
    }
    links.sort(String::compareTo);

    assertEquals(
        "the witness must carry exactly the two configured links, in the configured direction",
        List.of("s1->c1", "s1->c2"),
        links);
  }

  /**
   * The real {@code Subsets} corpus fixture, which predefined links made reachable for the first
   * time -- and which must NOT reach a verdict.
   *
   * <p>{@code ab}'s ends are {@code union}, so its extent is the union of {@code cd} and {@code
   * ef}; the configuration asks for {@code ab_min = ab_max = 2} while giving {@code A} and {@code
   * B} zero objects of their own. An independent 2-D grid over those zero slots cannot hold two
   * links, so without this refusal the encoding answered UNSATISFIABLE where the incumbent answers
   * SATISFIABLE. A wrong verdict is worse than a refusal, so this pins the refusal.
   */
  @Test
  public void theUnionAssociationOfTheRealSubsetsFixtureIsRefusedNotAnswered() throws Exception {
    Path directory = examples().resolve("Subsets");
    MModel model = compile(Files.readString(directory.resolve("Subsets.use")), "SimpleSubset");
    AnalysisConfiguration config =
        ConfigurationReader.normalize(
                ConfigurationReader.read(directory.resolve("Subsets.properties"), "demo"),
                ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    SmtTranslationException thrown =
        assertThrows(SmtTranslationException.class, () -> SmtModelFinder.find(model, config));

    assertTrue(
        "the refusal must name the union end: " + thrown.getMessage(),
        thrown.getMessage().contains("union"));
    assertEquals(FragmentBoundary.TIER_3, thrown.boundary());
  }

  private static Path examples() {
    Path directory = Path.of("../benchmark/examples");
    return Files.isDirectory(directory)
        ? directory
        : Path.of("msc-modelvalidators/benchmark/examples");
  }

  private static AnalysisConfiguration readConfig(MModel model, String body) throws Exception {
    Path file = Files.createTempFile("warehouse", ".properties");
    file.toFile().deleteOnExit();
    Files.writeString(file, body);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, ConfigurationVocabulary.fromModel(model))
        .requireSupported();
  }

  private static MModel compile(String source, String name) {
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, name, err, new ModelFactory());
    err.flush();
    return model;
  }
}
