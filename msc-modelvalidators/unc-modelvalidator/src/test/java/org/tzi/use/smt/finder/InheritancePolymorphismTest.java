package org.tzi.use.smt.finder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Proves a superclass-level invariant is actually checked against SUBCLASS instances, not just
 * direct instances of the literal declaring class -- the gap discovered by hand-running the real
 * {@code examples/Inheritance}/{@code examples/MultipleInheritance} benchmark scenarios: {@code
 * Inheritance}'s {@code PositiveWheels} (declared on the abstract superclass {@code Vehicle}, which
 * has zero direct instances of its own) silently checked nothing at all before this, and z3's SAT
 * witness left {@code wheels} undefined on the reconstructed Car/Truck objects -- caught by {@link
 * org.tzi.use.smt.verify.InvariantReEvaluator}'s independent re-check, but only after the whole
 * scenario had already been mis-reported UNSATISFIABLE (Kodkod's own five backends agree it is
 * genuinely SATISFIABLE). {@code MultipleInheritance}'s {@code LevelsDiffer}/{@code
 * DepthMatchesTag} (declared on the diamond's leaf class {@code D}, inheriting attributes along
 * three distinct superclass paths) failed closed with an honest ERROR for the same underlying
 * reason -- an inherited attribute was never given its own values on the subclass's object slots.
 */
public class InheritancePolymorphismTest {

  @Test
  public void vehicleDefaultSectionIsSatisfiableWithPositiveWheelsHoldingOnBothSubclasses()
      throws Exception {
    MModel model = compile("Inheritance", "Vehicle");
    AnalysisConfiguration config = readConfig(model, "Inheritance", "Vehicle", null);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT (Kodkod's own five backends agree)", result.satisfiable());
    assertTrue(
        "PositiveWheels must actually hold on the reconstructed Car/Truck instances, not just on"
            + " zero direct Vehicle instances",
        result.allActiveInvariantsHold());

    MSystemState state = result.system().state();
    MObject car = state.objectsOfClass(model.getClass("Car")).iterator().next();
    MObject truck = state.objectsOfClass(model.getClass("Truck")).iterator().next();
    assertTrue(
        "Car.wheels (inherited from Vehicle) must be assigned and positive",
        ((IntegerValue) car.state(state).attributeValue("wheels")).value() > 0);
    assertTrue(
        "Truck.wheels (inherited from Vehicle) must be assigned and positive",
        ((IntegerValue) truck.state(state).attributeValue("wheels")).value() > 0);
  }

  @Test
  public void vehicleNonPositiveWheelsSectionIsGenuinelyUnsatisfiable() throws Exception {
    MModel model = compile("Inheritance", "Vehicle");
    AnalysisConfiguration config =
        readConfig(model, "Inheritance", "Vehicle", "nonpositivewheels");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "every Vehicle_wheels candidate is non-positive, so PositiveWheels is structurally"
            + " unsatisfiable for the required Car/Truck population -- Kodkod's own five backends"
            + " agree",
        result.allActiveInvariantsHold());
  }

  @Test
  public void multipleInheritanceDefaultSectionIsSatisfiableAcrossTheDiamond() throws Exception {
    MModel model = compile("MultipleInheritance", "MultipleInheritance");
    AnalysisConfiguration config =
        readConfig(model, "MultipleInheritance", "MultipleInheritance", null);

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue("expected SAT (Kodkod's own five backends agree)", result.satisfiable());
    assertTrue(
        "NameNotEmpty/LevelsDiffer/DepthMatchesTag must all hold once every inherited attribute"
            + " (levelB via B, levelC via C, tag via E, name via A) is actually assigned on D's own"
            + " reconstructed instance",
        result.allActiveInvariantsHold());
  }

  @Test
  public void multipleInheritanceCollisionSectionIsGenuinelyUnsatisfiable() throws Exception {
    MModel model = compile("MultipleInheritance", "MultipleInheritance");
    AnalysisConfiguration config =
        readConfig(model, "MultipleInheritance", "MultipleInheritance", "collision");

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertFalse(
        "B_levelB and C_levelC are both collapsed to the identical singleton {5}, so D::LevelsDiffer"
            + " (d.levelB <> d.levelC) is structurally unsatisfiable -- Kodkod's own five backends"
            + " agree",
        result.allActiveInvariantsHold());
  }

  private static AnalysisConfiguration readConfig(
      MModel model, String directory, String baseName, String section) throws Exception {
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(propertiesFile(directory, baseName), section);
    return ConfigurationReader.normalize(raw, vocabulary).requireSupported();
  }

  private static MModel compile(String directory, String baseName) throws Exception {
    String source = Files.readString(useFile(directory, baseName));
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, baseName, err, factory);
    err.flush();
    return model;
  }

  private static Path useFile(String directory, String baseName) {
    return exampleFile(directory, baseName + ".use");
  }

  private static Path propertiesFile(String directory, String baseName) {
    return exampleFile(directory, baseName + ".properties");
  }

  private static Path exampleFile(String directory, String fileName) {
    Path file = Path.of("../benchmark/examples/" + directory + "/" + fileName);
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/" + directory + "/" + fileName);
    }
    return file;
  }
}
