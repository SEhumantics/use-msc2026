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
 * End-to-end regression for an OBJECT-TYPED LET whose initializer is a CAST ({@code
 * let t : Truck = x.vehicle.oclAsType(Truck) in ...}) -- the bind-the-downcast-once idiom.
 * The audit finding this slice closes: casts through navigation/any object lets already
 * worked, operation calls through object lets already worked, but the let INITIALIZER itself
 * refused unless it was a navigation, an aliased variable, or an any() -- so the one shape
 * every guarded-downcast context wants was the missing one.
 *
 * <p>Semantics: {@code ExpAsType#eval} is strict, so the binding's definedness is the link
 * AND the destination slot's compile-time conformance to the cast target. Per destination
 * slot the concrete class is fixed, so a linked CAR slot contributes NOTHING (the cast is
 * undefined there) and must not disturb a conforming slot's read.
 */
public class CastInitializerLetTest {

  private static final String MODEL =
      """
      model CastLet
      class X
      end
      abstract class Vehicle
      end
      class Car < Vehicle
      end
      class Truck < Vehicle
      attributes
        payload : Integer
      operations
        doubled() : Integer = self.payload * 2
      end
      association Fleet between
        X[0..1] role owner
        Vehicle[0..1] role vehicle
      end
      constraints
      context x : X inv castLetAttrRead:
        let t : Truck = x.vehicle.oclAsType(Truck) in t.payload > 0
      context x : X inv castLetTwice:
        let t : Truck = x.vehicle.oclAsType(Truck) in t.payload + t.payload = 10
      context x : X inv castLetOperation:
        let t : Truck = x.vehicle.oclAsType(Truck) in t.doubled() > 4
      """;

  /** General: a linked Truck with payload 5 satisfies the cast-let attribute read. */
  @Test
  public void linkedTruckSatisfiesTheCastLetRead() throws Exception {
    ModelFinderResult match = find("X::castLetAttrRead", "truck", List.of("5"));
    assertTrue(match.satisfiable());
    assertTrue(verdictFor(match, "X::castLetAttrRead").holds());
  }

  /**
   * The asymmetric discriminator: a linked CAR makes the cast UNDEFINED, and the strict let
   * propagates that -- the invariant is violated, not merely false. This is the test that
   * dies if the conformance filter is dropped (a Car slot would bind with payload-free
   * undefined-ness treated as a Truck).
   */
  @Test
  public void linkedCarRefutesThroughLetDefinedness() throws Exception {
    ModelFinderResult miss = find("X::castLetAttrRead", "car", List.of("5"));
    assertFalse("the linked slot is a Car: the cast-let binding is undefined",
        miss.satisfiable());
  }

  /** The value genuinely flows through the binding: two reads sum to twice the payload. */
  @Test
  public void twoReadsThroughTheSameBindingSum() throws Exception {
    ModelFinderResult match = find("X::castLetTwice", "truck", List.of("5"));
    assertTrue("payload 5 read twice sums to 10", match.satisfiable());
    assertTrue(verdictFor(match, "X::castLetTwice").holds());

    ModelFinderResult wrong = find("X::castLetTwice", "truck", List.of("6"));
    assertFalse("payload 6 read twice sums to 12, not 10", wrong.satisfiable());
  }

  /** The binding composes with query-operation inlining: the bound object's op is called. */
  @Test
  public void operationCallThroughTheCastLet() throws Exception {
    ModelFinderResult match = find("X::castLetOperation", "truck", List.of("5"));
    assertTrue("doubled() = 10 > 4 through the cast-let binding", match.satisfiable());
    assertTrue(verdictFor(match, "X::castLetOperation").holds());

    ModelFinderResult wrong = find("X::castLetOperation", "truck", List.of("2"));
    assertFalse("doubled() = 4 is not > 4", wrong.satisfiable());
  }

  /**
   * BOTH concrete kinds populated, only the Truck linked: the non-conforming Car slot must
   * contribute nothing -- the read still succeeds via the Truck slot alone.
   */
  @Test
  public void nonConformingSlotDoesNotDisturbTheConformingRead() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Car", 1, 1, List.of("car1")),
                new ClassScope("Truck", 1, 1, List.of("trk1"))),
            List.of(new AssociationScope("Fleet", 1, 1, List.of(List.of("x1", "trk1")))),
            List.of(new AttributeDomain("Truck", "payload", null, List.of("7"), null, null)),
            Set.of("X::castLetAttrRead"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("the Car slot is unlinked and non-conforming; the Truck slot carries payload 7",
        match.satisfiable());
    assertTrue(verdictFor(match, "X::castLetAttrRead").holds());
  }

  private static ModelFinderResult find(String invariant, String vehicleKind, List<String> payload)
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
            List.of(new AssociationScope("Fleet", 1, 1,
                List.of(List.of("x1", truck ? "trk1" : "car1")))),
            List.of(new AttributeDomain("Truck", "payload", null, payload, null, null)),
            Set.of(invariant),
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
    MModel model = USECompiler.compileSpecification(MODEL, "CastLet", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
