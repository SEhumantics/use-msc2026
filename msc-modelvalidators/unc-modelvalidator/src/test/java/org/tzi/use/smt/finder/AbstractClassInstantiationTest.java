package org.tzi.use.smt.finder;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReadException;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystemState;

/**
 * {@code unc-modelvalidator} never calls {@code MClassifier.isAbstract()} anywhere
 * (docs/modelvalidator-feature-matrix.json, feature {@code class.abstract}):
 * ConfigurationReader.normalize() builds every class's ClassScope identically via {@code
 * bound(entries, name + "_min"/"_max", 1)}, so an unconfigured abstract class defaults to
 * min=1/max=1 -- the exact same default an ordinary concrete class gets. Kodkod's own {@code
 * ClassConfigurator.generateObjectsTuple} (kk-modelvalidator, lines 23-34) forces an abstract
 * class's own relation to an empty {@code TupleSet} unconditionally; nothing in
 * unc-modelvalidator does anything analogous.
 *
 * <p>{@code examples/Inheritance/Vehicle.properties} only avoids exposing this because it
 * manually pins {@code Vehicle_min=0}/{@code Vehicle_max=0} -- its own header comment says so,
 * calling it out as a scenario-author workaround, not a plugin guarantee. This test reuses the
 * exact same real {@code Vehicle.use} model (abstract class {@code Vehicle}, concrete {@code
 * Car}/{@code Truck}) with that manual override removed -- {@code Vehicle_min}/{@code
 * Vehicle_max} simply left unconfigured, exactly the shape the corpus itself avoids -- and pins
 * that a direct {@code Vehicle} instance must never appear: UML abstract classes categorically
 * cannot have direct instances.
 */
public class AbstractClassInstantiationTest {

  @Test
  public void anAbstractClassLeftUnconfiguredGetsZeroDirectInstancesNotTheOrdinaryOneOneDefault()
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config = readConfig(model, withoutVehicleBoundsOverride());

    ModelFinderResult result = SmtModelFinder.find(model, config);

    assertTrue(
        "Car/Truck population is still forced 1/1 each, so this must stay satisfiable exactly as"
            + " examples/Inheritance/Vehicle.properties's default section already is",
        result.satisfiable());
    MSystemState state = result.system().state();
    assertTrue(
        "Vehicle is declared abstract (Vehicle.use line 10); it must have zero DIRECT instances"
            + " of its own even when its _min/_max are left completely unconfigured, exactly as"
            + " if the scenario author had never had to manually pin"
            + " Vehicle_min=0/Vehicle_max=0 -- a direct instance of an abstract class is a UML"
            + " soundness violation the incumbent (ClassConfigurator.generateObjectsTuple)"
            + " forbids unconditionally",
        state.objectsOfClass(model.getClass("Vehicle")).isEmpty());
  }

  /**
   * The other branch of the same decision: when the user EXPLICITLY configures a nonzero {@code
   * Vehicle_min}/{@code Vehicle_max} for the abstract {@code Vehicle} class -- an outright
   * config/model contradiction, not merely an unconfigured default -- this reader refuses it with
   * a located, descriptive {@link ConfigurationReadException} rather than silently overriding it
   * to 0/0 the way Kodkod's {@code ClassConfigurator.generateObjectsTuple} unconditionally does.
   * That choice follows this codebase's own established precedent for a genuinely analogous
   * config/model contradiction: {@code ConfigurationReader.associationScope} already refuses
   * (rather than silently resolves) a mismatch between predefined link tuples and an explicit
   * association bound (commits 4277d708/53944b23/b5820df5).
   */
  @Test
  public void anAbstractClassExplicitlyConfiguredNonzeroIsRefusedNotSilentlyOverridden()
      throws Exception {
    MModel model = compile();
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    Path file = Files.createTempFile("abstract-class-explicit-override", ".properties");
    Files.writeString(
        file,
        """
        Integer_min = 0
        Integer_max = 7

        Vehicle_min = 1
        Vehicle_max = 1

        Vehicle_licensePlate = Set{'CAR-001','TRK-100'}
        Vehicle_wheels = Set{4,6,8}

        Car_min = 1
        Car_max = 1

        Car_numDoors = Set{2,4,5}

        Truck_min = 1
        Truck_max = 1

        Truck_payloadCapacity = Set{2,5,10}

        Vehicle_PositiveWheels = active
        Car_ReasonableDoors = active
        Truck_PositivePayload = active
        """);
    file.toFile().deleteOnExit();
    RawConfiguration raw = ConfigurationReader.read(file, null);

    ConfigurationReadException thrown =
        assertThrows(
            ConfigurationReadException.class,
            () -> ConfigurationReader.normalize(raw, vocabulary).requireSupported());

    assertTrue(
        "the refusal must name the offending class so a user can locate the contradiction",
        thrown.getMessage().contains("Vehicle"));
    assertTrue(
        "the refusal must name the offending key",
        thrown.getMessage().contains("Vehicle_min"));
  }

  /**
   * Identical in intent to {@code examples/Inheritance/Vehicle.properties}'s default (unnamed)
   * section, except the manual {@code Vehicle_min = 0} / {@code Vehicle_max = 0} lines are
   * omitted entirely, so both bounds fall through to the reader's ordinary unconfigured-class
   * default of 1/1 -- the exact scenario the checked-in corpus file avoids ever exercising.
   */
  private static String withoutVehicleBoundsOverride() {
    return """
        Integer_min = 0
        Integer_max = 7

        Vehicle_licensePlate = Set{'CAR-001','TRK-100'}
        Vehicle_wheels = Set{4,6,8}

        Car_min = 1
        Car_max = 1

        Car_numDoors = Set{2,4,5}

        Truck_min = 1
        Truck_max = 1

        Truck_payloadCapacity = Set{2,5,10}

        Vehicle_PositiveWheels = active
        Car_ReasonableDoors = active
        Truck_PositivePayload = active
        """;
  }

  private static AnalysisConfiguration readConfig(MModel model, String propertiesContents)
      throws Exception {
    Path file = Files.createTempFile("abstract-class-instantiation", ".properties");
    Files.writeString(file, propertiesContents);
    file.toFile().deleteOnExit();
    ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
    RawConfiguration raw = ConfigurationReader.read(file, null);
    return ConfigurationReader.normalize(raw, vocabulary).requireSupported();
  }

  private static MModel compile() throws Exception {
    String source = Files.readString(useFile());
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(source, "Vehicle", err, factory);
    err.flush();
    return model;
  }

  private static Path useFile() {
    Path file = Path.of("../benchmark/examples/Inheritance/Vehicle.use");
    if (!Files.isRegularFile(file)) {
      file = Path.of("msc-modelvalidators/benchmark/examples/Inheritance/Vehicle.use");
    }
    return file;
  }
}
