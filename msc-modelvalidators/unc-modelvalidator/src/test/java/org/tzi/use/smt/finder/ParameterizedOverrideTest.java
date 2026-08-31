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
 * End-to-end regression for a PARAMETERIZED operation that is also OVERRIDDEN -- the
 * composition the ocl.operation-polymorphic-override row named as its remaining gap.
 * {@code Truck.capacity(p)} redefines {@code Vehicle.capacity(p)} with a different body, so
 * the demanded result can only be produced when BOTH halves compose: per-slot dispatch to the
 * concrete class's body AND the parameter bound through the per-call SMT let from the
 * caller-side argument.
 */
public class ParameterizedOverrideTest {

  private static final String MODEL =
      """
      model ParamOverride
      class X
      end
      class Vehicle
      operations
        capacity(p : Integer) : Integer = p + 1
      end
      class Truck < Vehicle
      attributes
        payload : Integer
      operations
        capacity(p : Integer) : Integer = p + payload
      end
      association Fleet between
        X[0..1] role owner
        Vehicle[0..1] role vehicle
      end
      constraints
      context x : X inv navParamOverrideTruckBody:
        x.vehicle.capacity(3) = 23
      context x : X inv navParamOverrideStaticBody:
        x.vehicle.capacity(3) = 4
      context x : X inv navArgFromAttribute:
        x.vehicle.capacity(x.vehicle.oclAsType(Truck).payload) = 40
      context v : Vehicle inv bareParamOverrideTruckBody:
        v.capacity(3) = 23
      """;

  /**
   * A linked Truck with payload 20: capacity(3) is 23 ONLY through Truck's overriding body
   * (3 + 20). The statically declared Vehicle body would compute 4. USE's own evaluator
   * dispatches by runtime type, so the USE-confirmed verdict is the oracle.
   */
  @Test
  public void navigationReceiverResolvesTheOverridingBodyWithTheParameter() throws Exception {
    ModelFinderResult match = find("X::navParamOverrideTruckBody", List.of("20"));
    assertTrue("capacity(3) over a Truck with payload 20 is 23", match.satisfiable());
    assertTrue(verdictFor(match, "X::navParamOverrideTruckBody").holds());
  }

  /** The mirror polarity: the STATIC body's result is genuinely not producible. */
  @Test
  public void theStaticBodysResultRefutesThroughTheOverride() throws Exception {
    ModelFinderResult miss = find("X::navParamOverrideStaticBody", List.of("20"));
    assertFalse("capacity(3) is 23 through the override; 4 is not producible",
        miss.satisfiable());
  }

  /** The argument may itself read the receiver's attributes (caller-side evaluation). */
  @Test
  public void argumentReferencingTheReceiverFlowsThrough() throws Exception {
    ModelFinderResult match = find("X::navArgFromAttribute", List.of("20"));
    assertTrue("capacity(payload) = payload + payload = 40", match.satisfiable());
    assertTrue(verdictFor(match, "X::navArgFromAttribute").holds());
  }

  /**
   * A bare polymorphic variable bound to a Truck slot: the dispatch keys on the slot's
   * CONCRETE class, so the override still wins over the declared Vehicle body.
   */
  @Test
  public void bareVariableReceiverDispatchesToTheConcreteOverride() throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 0, 0),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Truck", 1, 1, List.of("t1"))),
            List.of(),
            List.of(new AttributeDomain("Truck", "payload", null, List.of("20"), null, null)),
            Set.of("Vehicle::bareParamOverrideTruckBody"),
            QueryParser.parse("satisfy", ConfigurationVocabulary.fromModel(model)),
            Duration.ofSeconds(30),
            1);
    ModelFinderResult match = SmtModelFinder.find(model, config);
    assertTrue("v.capacity(3) over a Truck slot is 23", match.satisfiable());
    assertTrue(verdictFor(match, "Vehicle::bareParamOverrideTruckBody").holds());
  }

  private static ModelFinderResult find(String invariant, List<String> payload)
      throws Exception {
    MModel model = compile();
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(
                new ClassScope("X", 1, 1, List.of("x1")),
                new ClassScope("Vehicle", 0, 0),
                new ClassScope("Truck", 1, 1, List.of("trk1"))),
            List.of(new AssociationScope("Fleet", 1, 1, List.of(List.of("x1", "trk1")))),
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
    MModel model = USECompiler.compileSpecification(MODEL, "ParamOverride", err, factory);
    err.flush();
    if (model == null) {
      throw new AssertionError("fixture model did not compile:\n" + buffer);
    }
    return model;
  }
}
