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
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * End-to-end regression for the association-class POINTER paths over polymorphic (folded) end
 * views -- the documented remaining restriction of {@code assoc.type-multiplicity-with-
 * inheritance}: an association class's index pointers, the classifier-to-end attribute read
 * ({@code o.vehicle.wheels}), and the end-side definedness lookup ({@code c.holder}) all kept
 * SINGLE-CLASS end views, so a superclass-typed end could not attach a link to a CONFIGURED
 * SUBCLASS instance even though every ordinary association's grid could.
 *
 * <p>After this slice the pointer's own range/existing-target/degree guards span the same folded
 * view {@code endSlotsView} builds for ordinary associations; the classifier-to-end value chain
 * reads each slot's value from the slot's CONCRETE class's registration; and the end-side
 * definedness compares the pointer against the source slot's index WITHIN the folded view (not
 * its own-class index).
 */
public class PolymorphicAssociationClassTest {

  private static final String MODEL =
      """
      model PolyAssocClass
      class Person
      attributes
        name : String
      end
      class Vehicle
      attributes
        wheels : Integer
      end
      class Car < Vehicle
      end
      associationclass Ownership between
        Person [0..1] role holder
        Vehicle [0..1] role vehicle
      end
      constraints
      context o : Ownership inv PointsAtVehicleWithTwoWheels:
        o.vehicle.wheels = 2
      context o : Ownership inv PointsAtCarWithFourWheels:
        o.vehicle.wheels = 4
      context o : Ownership inv PointsAtCarWithEighteenWheels:
        o.vehicle.wheels = 18
      context c : Car inv CarHasHolder:
        not c.holder.oclIsUndefined()
      """;

  /**
   * The control: with distinct per-concrete-class wheels domains (Vehicle {2}, Car {4}), the
   * pointer can attach to the plain Vehicle and read 2 -- the ordinary unfolded shape, green
   * before and after this slice.
   */
  @Test
  public void aPointerToTheDeclaredClassStillReadsItsOwnValue() throws Exception {
    ModelFinderResult result =
        find("Ownership::PointsAtVehicleWithTwoWheels", 1, 1, List.of("2"), List.of("4"));

    assertTrue(result.satisfiable());
    assertTrue(verdictFor(result, "Ownership::PointsAtVehicleWithTwoWheels").holds());
  }

  /**
   * The slice's first discriminator: demanding the CONCRETE class's value (4, which only the Car
   * carries) forces the pointer to attach to the configured subclass instance through the folded
   * end view -- impossible while the pointer machinery keeps a Vehicle-only view.
   */
  @Test
  public void aPointerReachesAConfiguredSubclassInstanceAndReadsItsConcreteValue()
      throws Exception {
    ModelFinderResult result =
        find("Ownership::PointsAtCarWithFourWheels", 1, 1, List.of("2"), List.of("4"));

    assertTrue(
        "the Ownership pointer must attach to the Car (folded end view) and read its wheels"
            + " from Car's own registration",
        result.satisfiable());
    assertTrue(verdictFor(result, "Ownership::PointsAtCarWithFourWheels").holds());
  }

  /** Neither concrete value is 18: genuinely unsatisfiable, never a misread slot. */
  @Test
  public void demandingAValueNoSlotCarriesIsUnsatisfiable() throws Exception {
    ModelFinderResult miss =
        find("Ownership::PointsAtCarWithEighteenWheels", 1, 1, List.of("2"), List.of("4"));

    assertFalse("Vehicle carries 2 and Car carries 4; 18 is unreachable", miss.satisfiable());
  }

  /**
   * The end-side index must be the source's index WITHIN THE FOLDED VIEW: with two Vehicle slots
   * configured ahead of the Car slot, the Car's folded index is 2 -- a pointer comparison against
   * the Car's own-class index (0) would match a VEHICLE slot instead, and USE's re-evaluation of
   * the reconstructed witness would find the Car unlinked.
   */
  @Test
  public void anEndSideLookupResolvesTheSourceWithinTheFoldedView() throws Exception {
    ModelFinderResult result = find("Car::CarHasHolder", 2, 1, List.of("2"), List.of("4"));

    assertTrue(
        "the Car (folded index 2, after two Vehicle slots) must be attachable as the pointer"
            + " target, and c.holder must resolve through the folded view",
        result.satisfiable());
    assertTrue(verdictFor(result, "Car::CarHasHolder").holds());
  }

  private static ModelFinderResult find(
      String invariantName,
      int vehicleSlots,
      int personSlots,
      List<String> vehicleWheels,
      List<String> carWheels)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Person", personSlots, personSlots),
                new ClassScope("Vehicle", vehicleSlots, vehicleSlots),
                new ClassScope("Car", 1, 1),
                new ClassScope("Ownership", 1, 1)),
            List.of(new AssociationScope("Ownership", 0, 1)),
            List.of(
                new AttributeDomain("Person", "name", null, List.of("alice"), null, null),
                new AttributeDomain("Vehicle", "wheels", null, vehicleWheels, null, null),
                new AttributeDomain("Car", "wheels", null, carWheels, null, null)),
            Set.of(invariantName),
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
    MModel model = USECompiler.compileSpecification(MODEL, "PolyAssocClass", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
