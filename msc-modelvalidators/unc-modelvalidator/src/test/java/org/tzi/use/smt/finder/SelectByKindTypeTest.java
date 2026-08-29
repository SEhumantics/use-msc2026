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
 * End-to-end regression for {@code ->selectByKind(T)} / {@code ->selectByType(T)} over {@code
 * X.allInstances()} -- both operations used to be unconditional refusals.
 *
 * <p>Semantics mirror USE's own evaluators exactly ({@code ExpSelectByKind#includeElement} uses
 * {@code conformsTo}, {@code ExpSelectByType} uses exact runtime-type equality): kind-of keeps
 * slots of T AND every subclass, type-of keeps only slots whose concrete class IS T. In this
 * encoding that is a Java-side filter over {@link PolymorphicRange} slots -- each slot already
 * carries its own concrete class name -- so every population consumer (size(), forAll, ...)
 * inherits the operations uniformly.
 *
 * <p>The abstract {@code Vehicle} is load-bearing for the discriminating power: with Vehicle
 * scoped 0..0 (no direct instances) and exactly one Car and one Truck configured, every
 * population size below is forced by the filter and CANNOT be explained away by solver freedom.
 */
public class SelectByKindTypeTest {

  private static final String MODEL =
      """
      model SelectByKindScope
      abstract class Vehicle
      attributes
        wheels : Integer
      end
      class Car < Vehicle
      end
      class Truck < Vehicle
      end
      constraints
      context v : Vehicle inv KindCarIsOne:
        Vehicle.allInstances()->selectByKind(Car)->size() = 1
      context v : Vehicle inv KindVehicleIsTwo:
        Vehicle.allInstances()->selectByKind(Vehicle)->size() = 2
      context v : Vehicle inv KindVehicleIsOne:
        Vehicle.allInstances()->selectByKind(Vehicle)->size() = 1
      context v : Vehicle inv TypeVehicleIsZero:
        Vehicle.allInstances()->selectByType(Vehicle)->size() = 0
      context v : Vehicle inv TypeCarIsOne:
        Vehicle.allInstances()->selectByType(Car)->size() = 1
      context v : Vehicle inv TypeVehicleIsOne:
        Vehicle.allInstances()->selectByType(Vehicle)->size() = 1
      context v : Vehicle inv TrucksAllWheels99:
        Vehicle.allInstances()->selectByKind(Truck)->forAll(t | t.wheels = 99)
      context v : Vehicle inv TrucksAllWheels4:
        Vehicle.allInstances()->selectByKind(Truck)->forAll(t | t.wheels = 4)
      """;

  /** kind-of(Car) keeps the Car slot and excludes the sibling Truck: exactly 1 member. */
  @Test
  public void kindFilterExcludesSiblings() throws Exception {
    ModelFinderResult result = find("KindCarIsOne");

    assertTrue(
        "one Car and one Truck are configured, so kind-of(Car) keeps exactly the Car",
        result.satisfiable());
    assertTrue(verdictFor(result, "Vehicle::KindCarIsOne").holds());
  }

  /**
   * kind-of(Vehicle) keeps every subclass slot too (conformsTo): both the Car and the Truck, and
   * NOT the abstract Vehicle itself (no direct instances can exist). The = 1 control proves the
   * count is real, not vacuous.
   */
  @Test
  public void kindFilterIncludesSubclassesAndExcludesNothingConcrete() throws Exception {
    ModelFinderResult two = find("KindVehicleIsTwo");
    assertTrue("kind-of(Vehicle) keeps both the Car and the Truck slots", two.satisfiable());
    assertTrue(verdictFor(two, "Vehicle::KindVehicleIsTwo").holds());

    ModelFinderResult one = find("KindVehicleIsOne");
    assertFalse("both concrete slots exist, so a count of 1 is genuinely unsatisfiable",
        one.satisfiable());
  }

  /**
   * type-of is EXACT: the abstract Vehicle's own population is empty even though Car and Truck
   * slots exist (= 0 holds, = 1 is unsatisfiable), while type-of(Car) still counts exactly the
   * one Car.
   */
  @Test
  public void typeFilterIsExactNotKindOf() throws Exception {
    ModelFinderResult zero = find("TypeVehicleIsZero");
    assertTrue("the abstract Vehicle has no direct-instance slots", zero.satisfiable());
    assertTrue(verdictFor(zero, "Vehicle::TypeVehicleIsZero").holds());

    ModelFinderResult oneCar = find("TypeCarIsOne");
    assertTrue("type-of(Car) keeps exactly the Car slot", oneCar.satisfiable());

    ModelFinderResult oneVehicle = find("TypeVehicleIsOne");
    assertFalse("exact-type Vehicle excludes both subclass slots, so 1 is unsatisfiable",
        oneVehicle.satisfiable());
  }

  /**
   * A forAll over a kind-filtered population genuinely constrains its MEMBERS: demanding
   * wheels = 99 over kind-of(Truck) is unsatisfiable exactly because the Truck slot IS in the
   * population (a wrongly-empty filter would make the forAll vacuously true), while wheels = 4
     * is satisfiable and USE-confirmed.
   */
  @Test
  public void forAllOverAKindFilteredPopulationConstrainsItsMembers() throws Exception {
    ModelFinderResult impossible = find("TrucksAllWheels99");
    assertFalse(
        "the Truck slot is a population member, so demanding wheels = 99 with a {4}-only"
            + " domain is genuinely unsatisfiable",
        impossible.satisfiable());

    ModelFinderResult satisfiable = find("TrucksAllWheels4");
    assertTrue(satisfiable.satisfiable());
    assertTrue(verdictFor(satisfiable, "Vehicle::TrucksAllWheels4").holds());
  }

  private static ModelFinderResult find(String invariantName) throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", 1, 1),
                new ClassScope("Truck", 1, 1)),
            List.of(),
            List.of(new AttributeDomain("Vehicle", "wheels", null, List.of("4"), null, null)),
            Set.of("Vehicle::" + invariantName),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
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
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(MODEL, "SelectByKindScope", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
