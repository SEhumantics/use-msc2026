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
 * End-to-end regression for {@code ocl.operation-polymorphic-override}: when a subclass
 * REDEFINES a query operation declared on an ancestor, a call on a super-typed receiver must
 * dispatch to the receiver's concrete class's body. The incumbent does this by runtime-type
 * dispatch (nested if-receiver-in-subclass); this encoding resolves the same dispatch at
 * TRANSLATION time -- every candidate slot carries its concrete class, and the most specific
 * redefinition for that class (via {@code MClass.operation(name, searchInherited=true)}, the
 * model's own vtable lookup) is used for that slot's call.
 *
 * <p>Before this fix the call always used the statically-declared body: with Truck redefining
 * {@code Axles()} = 6 and Car = 2, demanding {@code Axles() = 6} over the selected Trucks was
 * REFUTED (the translator read Vehicle's body, 2) -- a live divergence from the incumbent,
 * which reports SATISFIABLE on the byte-identical model.
 */
public class PolymorphicOperationDispatchTest {

  private static final String MODEL =
      """
      model PolyOpDispatch
      abstract class Vehicle
      attributes
        wheels : Integer
      operations
        Axles(): Integer = 0
      end
      class Car < Vehicle
      operations
        Axles(): Integer = 2
      end
      class Truck < Vehicle
      operations
        Axles(): Integer = 6
      end
      constraints
      context v : Vehicle inv TrucksHaveSixAxles:
        Vehicle.allInstances()->select(w | w.oclIsTypeOf(Truck))->forAll(t | t.Axles() = 6)
      context v : Vehicle inv CarsHaveTwoAxles:
        Vehicle.allInstances()->select(w | w.oclIsTypeOf(Car))->forAll(t | t.Axles() = 2)
      context v : Vehicle inv TrucksHaveTwoAxles:
        Vehicle.allInstances()->select(w | w.oclIsTypeOf(Truck))->forAll(t | t.Axles() = 2)
      """;

  /** The dispatch discriminator: Truck slots must read Truck's redefinition (6). */
  @Test
  public void aTruckSlotDispatchesToTheTruckRedefinition() throws Exception {
    ModelFinderResult result = find("TrucksHaveSixAxles");

    assertTrue(
        "the Truck slot's Axles() must dispatch to Truck's redefinition (the incumbent"
            + " reports SATISFIABLE on the byte-identical model)",
        result.satisfiable());
    assertTrue(verdictFor(result, "Vehicle::TrucksHaveSixAxles").holds());
  }

  /** The polarity control: Car slots dispatch to Car's redefinition (2). */
  @Test
  public void aCarSlotDispatchesToTheCarRedefinition() throws Exception {
    ModelFinderResult result = find("CarsHaveTwoAxles");

    assertTrue("the Car slot's Axles() must dispatch to Car's redefinition", result.satisfiable());
    assertTrue(verdictFor(result, "Vehicle::CarsHaveTwoAxles").holds());
  }

  /**
   * The cross-demand negative control: demanding Car's value (2) over selected Trucks is
   * genuinely unsatisfiable -- the Truck slots dispatch to 6, and pre-fix (reading the static
   * body) the demand would have been vacuously satisfiable in the wrong way... precisely: it
   * proves the dispatch reads the CONCRETE class's body, since the demand fails only under
   * the correct 6-dispatch.
   */
  @Test
  public void demandingACarsValueOverTrucksIsGenuinelyUnsatisfiable() throws Exception {
    ModelFinderResult result = find("TrucksHaveTwoAxles");

    assertFalse(
        "the Truck's redefinition gives 6, so demanding Axles() = 2 over Trucks is genuinely"
            + " unsatisfiable",
        result.satisfiable());
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
    MModel model = USECompiler.compileSpecification(MODEL, "PolyOpDispatch", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
