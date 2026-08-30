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
 * End-to-end regression for a CAST over a NAVIGATION ({@code x.vehicle.oclAsType(Truck)
 * .payload > 0}) -- the guarded-downcast idiom one hop out, composing the navigation general
 * case with the oclAsType slice. Semantics: {@code ExpAsType#eval} succeeds iff the linked
 * object exists AND its runtime class conformsTo the target; per destination slot the concrete
 * class is a translation-time fact, so a non-conforming linked slot contributes NOTHING
 * (undefined), which violates the enforcing invariant.
 */
public class NavigatedCastTest {

  private static final String MODEL =
      """
      model NavCast
      class X
      end
      abstract class Vehicle
      end
      class Car < Vehicle
      end
      class Truck < Vehicle
      attributes
        payload : Integer
      end
      association Fleet between
        X[0..1] role owner
        Vehicle[0..1] role vehicle
      end
      constraints
      context x : X inv truckPayloadPositive:
        x.vehicle.oclAsType(Truck).payload > 0
      """;

  /** General: a linked Truck with positive payload satisfies the cast read. */
  @Test
  public void linkedTruckWithPositivePayloadSatisfies() throws Exception {
    ModelFinderResult match = config("truck", List.of("5"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::truckPayloadPositive").holds());
  }

  /** Edge: the linked slot is a CAR -- the cast is undefined, so the invariant is violated. */
  @Test
  public void linkedCarRefutesTheCastRead() throws Exception {
    ModelFinderResult miss = config("car", List.of("5"));
    assertFalse("the linked slot is a Car: the cast to Truck is undefined",
        miss.satisfiable());
  }

  /** Edge: a linked Truck with a non-positive payload also refutes (value genuinely flows). */
  @Test
  public void negativePayloadRefutes() throws Exception {
    ModelFinderResult miss = config("truck", List.of("-4"));
    assertFalse("payload -4 is not > 0", miss.satisfiable());
  }

  /**
   * BOTH concrete kinds populated, only the Truck linked: the per-slot filter must skip the
   * Car slot entirely -- the read still succeeds via the Truck slot alone.
   */
  @Test
  public void carSlotDoesNotDisturbTheTruckRead() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", 1, 1, List.of("car1")),
                new ClassScope("Truck", 1, 1, List.of("trk1"))),
            List.of(new org.tzi.use.smt.config.AssociationScope("Fleet", 1, 1,
                List.of(List.of("x1", "trk1")))),
            List.of(new AttributeDomain("Truck", "payload", null, List.of("7"), null, null)),
            Set.of("X::truckPayloadPositive"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("the Car slot is unlinked; the Truck slot carries payload 7",
        match.satisfiable());
    assertTrue(verdictFor(match, "X::truckPayloadPositive").holds());
  }

  private static ModelFinderResult config(String vehicleKind, List<String> payloadDomain)
      throws Exception {
    MModel model = compile();
    boolean truck = vehicleKind.equals("truck");
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", truck ? 0 : 1, truck ? 0 : 1,
                    truck ? List.of() : List.of("car1")),
                new ClassScope("Truck", truck ? 1 : 0, truck ? 1 : 0,
                    truck ? List.of("trk1") : List.of())),
            List.of(new org.tzi.use.smt.config.AssociationScope("Fleet", 1, 1,
                List.of(List.of("x1", truck ? "trk1" : "car1")))),
            List.of(new AttributeDomain("Truck", "payload", null, payloadDomain, null, null)),
            Set.of("X::truckPayloadPositive"),
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
    java.io.StringWriter buffer = new java.io.StringWriter();
    PrintWriter err = new PrintWriter(buffer, true);
    MModel model = USECompiler.compileSpecification(MODEL, "NavCast", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
