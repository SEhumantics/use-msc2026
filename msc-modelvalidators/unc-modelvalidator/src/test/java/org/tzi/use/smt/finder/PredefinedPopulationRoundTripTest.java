package org.tzi.use.smt.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
   * time.
   *
   * <p>{@code ab}'s ends are {@code union}, so its own extent is entirely DERIVED from {@code cd}
   * and {@code ef} -- it is not an independently choosable association at all, per UML/OCL
   * semantics and per this fixture's own extensively-documented finding that even Kodkod's own
   * bound on a union association has zero effect on its actual OCL-visible content. {@code
   * SmtModelFinder} therefore skips {@code ab} entirely rather than giving it an independent 2-D
   * grid over {@code A}/{@code B}'s own (deliberately zero-capacity) slots -- which is exactly what
   * an earlier, narrower version of this test pinned as a REFUSAL, reasoning that such a grid could
   * never hold the configured {@code ab_min = ab_max = 2}. That reasoning no longer applies: {@code
   * ab} is never given an independent grid to fail in the first place. {@code Subsets.use} has no
   * OCL invariant that navigates the union role at all, so nothing needs {@code ab}'s derived
   * content to be actually computed for this fixture -- the fixture's own predefined {@code
   * cd}/{@code ef} links are what this test confirms survive reconstruction unchanged, matching the
   * manifest's own expected SATISFIABLE classification for this scenario.
   */
  @Test
  public void theUnionAssociationOfTheRealSubsetsFixtureIsSkippedNotGivenAnIndependentGrid()
      throws Exception {
    Path directory = examples().resolve("Subsets");
    MModel model = compile(Files.readString(directory.resolve("Subsets.use")), "SimpleSubset");
    AnalysisConfiguration config =
        ConfigurationReader.normalize(
                ConfigurationReader.read(directory.resolve("Subsets.properties"), "demo"),
                ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT, matching the manifest's own expected classification", result.satisfiable());
    MSystemState state = result.system().state();
    assertEquals(0, state.objectsOfClass(model.getClass("A")).size());
    assertEquals(0, state.objectsOfClass(model.getClass("B")).size());
    assertEquals(1, state.objectsOfClass(model.getClass("C")).size());
    assertEquals(1, state.objectsOfClass(model.getClass("D")).size());
    assertEquals(1, state.objectsOfClass(model.getClass("E")).size());
    assertEquals(1, state.objectsOfClass(model.getClass("F")).size());

    assertEquals(
        "cd's own predefined link must survive reconstruction unchanged",
        List.of("c1->d1"),
        linkedObjectNames(state, model.getAssociation("cd")));
    assertEquals(
        "ef's own predefined link must survive reconstruction unchanged",
        List.of("e1->f1"),
        linkedObjectNames(state, model.getAssociation("ef")));
  }

  /**
   * The paired UNSAT scenario ({@code Subsets-UNSAT} in the manifest, section {@code cdOverflow}):
   * with only 2 {@code C} and 2 {@code D} objects, {@code cd} (a perfectly ordinary association --
   * only {@code ab}, the union end, needed this pass's fix) can hold at most 2*2 = 4 distinct
   * (c,d) tuples, but {@code cd_min = cd_max = 5} demands one more than that structural ceiling --
   * a genuine pigeonhole contradiction with nothing to do with {@code union}/{@code subsets}
   * semantics at all (confirmed directly against the properties file's own header comment for this
   * section, authored specifically to isolate this from the union-translation gap that used to
   * block the WHOLE scenario before this pass).
   */
  @Test
  public void theCdOverflowSectionOfTheRealSubsetsFixtureIsGenuinelyUnsatisfiable()
      throws Exception {
    Path directory = examples().resolve("Subsets");
    MModel model = compile(Files.readString(directory.resolve("Subsets.use")), "SimpleSubset");
    AnalysisConfiguration config =
        ConfigurationReader.normalize(
                ConfigurationReader.read(directory.resolve("Subsets.properties"), "cdOverflow"),
                ConfigurationVocabulary.fromModel(model))
            .requireSupported();

    assertFalse(
        "cd cannot hold 5 distinct tuples over only 2 C's and 2 D's, independent of ab/ef",
        SmtModelFinder.find(model, config).satisfiable());
  }

  private static List<String> linkedObjectNames(MSystemState state, MAssociation association) {
    List<String> links = new ArrayList<>();
    for (MLink link : state.linksOfAssociation(association).links()) {
      List<String> ends = new ArrayList<>();
      for (MObject object : link.linkedObjects()) {
        ends.add(object.name());
      }
      links.add(String.join("->", ends));
    }
    links.sort(String::compareTo);
    return links;
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
