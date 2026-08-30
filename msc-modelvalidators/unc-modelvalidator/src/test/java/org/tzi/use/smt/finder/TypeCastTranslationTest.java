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
 * End-to-end regression for {@code oclAsType} against a bare context variable, the classic
 * guarded-downcast idiom real OCL uses on superclass contexts: {@code v.oclIsKindOf(Truck)
 * implies v.oclAsType(Truck).payloadCapacity > 0}.
 *
 * <p>Semantics pinned against {@code ExpAsType#eval} (use-core), not assumed: the cast of an
 * object is defined iff the object EXISTS and its RUNTIME class conformsTo the target type --
 * otherwise undefined. Per translation variant the context variable's concrete class is fixed,
 * so the conformance check is a compile-time fact: for a conforming variant the cast is the
 * plain variable read; for a non-conforming one the whole expression is constant-undefined.
 * Type tests stay TOTAL over a cast ({@code ExpIsKindOf#eval}: an undefined source tests
 * FALSE, never undefined), so {@code v.oclAsType(Truck).oclIsKindOf(Truck)} is true exactly
 * for Truck slots.
 */
public class TypeCastTranslationTest {

  private static final String MODEL =
      """
      model Cast
      abstract class Vehicle
      attributes
        licensePlate : String
      end
      class Car < Vehicle
      attributes
        numDoors : Integer
      end
      class Truck < Vehicle
      attributes
        payloadCapacity : Integer
      end
      constraints
      context v : Vehicle inv TruckPayloadPositiveWhenTruck:
        v.oclIsKindOf(Truck) implies v.oclAsType(Truck).payloadCapacity > 0
      context v : Vehicle inv CarDoorsNonNegWhenCar:
        v.oclIsKindOf(Car) implies v.oclAsType(Car).numDoors >= 0
      context v : Vehicle inv CastIsKindOfTarget:
        v.oclIsKindOf(Truck) implies v.oclAsType(Truck).oclIsKindOf(Truck)
      context v : Vehicle inv CastIdentityHolds:
        v.oclIsKindOf(Truck) implies v.oclAsType(Truck) = v
      """;

  /** Both guarded casts hold over a fully positive domain. */
  @Test
  public void guardedDowncastHoldsOverPositiveDomains() throws Exception {
    ModelFinderResult match = find(MODEL, "TruckPayloadPositiveWhenTruck", List.of("3"), List.of("2"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "Vehicle::TruckPayloadPositiveWhenTruck").holds());
  }

  /** The payload guard genuinely discriminates: a non-positive-only payload domain refutes. */
  @Test
  public void guardedDowncastRefutesWhenTheCastAttributeViolates() throws Exception {
    ModelFinderResult miss =
        find(MODEL, "TruckPayloadPositiveWhenTruck", List.of("0", "-1"), List.of("2"));
    assertFalse("the forced Truck's payload is 0 or -1, so the guarded cast must be violated",
        miss.satisfiable());
  }

  /** The car-side cast is an independent discriminator: negative-only door domains refute. */
  @Test
  public void carSideDowncastRefutesOnNegativeDoors() throws Exception {
    ModelFinderResult miss = find(MODEL, "CarDoorsNonNegWhenCar", List.of("3"), List.of("-2"));
    assertFalse("the forced Car's numDoors is -2, so the guarded car cast must be violated",
        miss.satisfiable());
  }

  /** A successful cast tests as kindOf its own target (total type test over the cast). */
  @Test
  public void castThenKindOfTargetHoldsExactlyForTargetSlots() throws Exception {
    ModelFinderResult match = find(MODEL, "CastIsKindOfTarget", List.of("3"), List.of("2"));
    assertTrue(match.satisfiable());
  }

  /** Cast identity: a successful cast denotes the same object as its source. */
  @Test
  public void castIdentityHolds() throws Exception {
    ModelFinderResult match = find(MODEL, "CastIdentityHolds", List.of("3"), List.of("2"));
    assertTrue(match.satisfiable());
  }

  /**
   * UNGUARDED downcast: the invariant is undefined (hence violated) for every non-Truck slot,
   * so with both a Car and a Truck forced to exist the model is refuted -- the conformance
   * gate is load-bearing, not decorative. A wrong implementation that resolved the attribute
   * without the gate cannot even complete (no payloadCapacity registration on Car); a wrong
   * implementation that skipped the cast definedness would report SAT.
   */
  @Test
  public void unguardedDowncastRefutesWhenAnotherSlotCannotCast() throws Exception {
    String spec =
        """
        model CastUnguarded
        abstract class Vehicle
        attributes
          licensePlate : String
        end
        class Car < Vehicle
        attributes
          numDoors : Integer
        end
        class Truck < Vehicle
        attributes
          payloadCapacity : Integer
        end
        constraints
        context v : Vehicle inv UnguardedTruckPayload:
          v.oclAsType(Truck).payloadCapacity > 0
        """;
    MModel model = compile(spec);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Vehicle", 0, 0), new ClassScope("Car", 1, 1),
                new ClassScope("Truck", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Vehicle", "licensePlate", null, List.of("'A'", "'B'"), null, null),
                new AttributeDomain("Car", "numDoors", null, List.of("2"), null, null),
                new AttributeDomain("Truck", "payloadCapacity", null, List.of("5"), null, null)),
            Set.of("Vehicle::UnguardedTruckPayload"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult result = SmtModelFinder.find(model, config);
    assertFalse("the Car slot's cast is undefined -> the invariant must be violated",
        result.satisfiable());
  }

  /**
   * UNGUARDED cast identity: for the Car slot the cast is UNDEFINED, so the comparison is
   * undefined and the invariant is violated -- UNSAT. Dropping the cast's conformance gate
   * from the identity path would make the comparison crisply true for every slot and wrongly
   * SAT, so this pins the gate where a mutation is verdict-detectable.
   */
  @Test
  public void unguardedCastIdentityRefutesWhenAnotherSlotCannotCast() throws Exception {
    String spec =
        """
        model CastUnguardedIdentity
        abstract class Vehicle
        attributes
          licensePlate : String
        end
        class Car < Vehicle
        attributes
          numDoors : Integer
        end
        class Truck < Vehicle
        attributes
          payloadCapacity : Integer
        end
        constraints
        context v : Vehicle inv UnguardedCastIdentity:
          v.oclAsType(Truck) = v
        """;
    MModel model = compile(spec);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", 1, 1),
                new ClassScope("Truck", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Vehicle", "licensePlate", null, List.of("'A'", "'B'"), null, null),
                new AttributeDomain("Car", "numDoors", null, List.of("2"), null, null),
                new AttributeDomain("Truck", "payloadCapacity", null, List.of("5"), null, null)),
            Set.of("Vehicle::UnguardedCastIdentity"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult result = SmtModelFinder.find(model, config);
    assertFalse("the Car slot's cast is undefined -> the identity must be violated",
        result.satisfiable());
  }

  private static ModelFinderResult find(
      String spec, String invariantName, List<String> truckPayloadDomain, List<String> carDoorsDomain)
      throws Exception {
    MModel model = compile(spec);
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", 1, 1),
                new ClassScope("Truck", 1, 1)),
            List.of(),
            List.of(
                new AttributeDomain("Vehicle", "licensePlate", null, List.of("'A'", "'B'"), null, null),
                new AttributeDomain("Car", "numDoors", null, carDoorsDomain, null, null),
                new AttributeDomain("Truck", "payloadCapacity", null, truckPayloadDomain, null, null)),
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

  private static MModel compile(String spec) {
    ModelFactory factory = new ModelFactory();
    PrintWriter err = new PrintWriter(System.err);
    MModel model = USECompiler.compileSpecification(spec, "Cast", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile");
    }
    return model;
  }
}
