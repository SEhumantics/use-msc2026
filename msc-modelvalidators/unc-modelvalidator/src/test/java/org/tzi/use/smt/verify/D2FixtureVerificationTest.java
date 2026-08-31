package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.UBooleanValue;

/**
 * MILESTONE 2 ENTRY: re-verifies the corrected D2 fixture against the REAL evaluator, not
 * re-asserted prose. USE's UBooleanValue.valueOf NORMALIZES every instance to value=true
 * (flipping the probability when passed false), so the fixture must be built as
 * UBoolean(true, 0.3) for probability() to stay 0.3. The two outcomes:
 * U-aware toBooleanC(0.2) -> TRUE (0.3 >= 0.2); nominal erasure -> FALSE (0.3 < 0.5).
 */
public class D2FixtureVerificationTest {

  @Test
  public void valueOfFalseFlipsTheProbability() {
    UBooleanValue inverted = UBooleanValue.valueOf(false, 0.3);
    assertTrue("valueOf(false, 0.3) normalizes to value=true", inverted.value());
    assertEquals("the flip makes the stored probability 0.7", 0.7, inverted.probability(), 1e-12);
  }

  @Test
  public void correctedFixtureKeepsProbability03() {
    UBooleanValue state = UBooleanValue.valueOf(true, 0.3);
    assertTrue("no flip for value=true", state.value());
    assertEquals("probability stays 0.3", 0.3, state.probability(), 1e-12);
  }

  /**
   * The U-aware outcome verified through the REAL registered operation:
   * {@code Op_uBoolean_toBooleanC.eval} (dispatched by USE's own {@code Evaluator} on a
   * parsed {@code toBooleanC(0.2)} expression), not a hand-copied inline formula.
   */
  @Test
  public void uAwareToBooleanC02IsTrueThroughTheRealOperation() throws Exception {
    org.tzi.use.uml.mm.MModel model = compile();
    org.tzi.use.uml.sys.MSystem system = new org.tzi.use.uml.sys.MSystem(model);
    org.tzi.use.uml.sys.MSystemState state = system.state();
    org.tzi.use.uml.mm.MClass relay = model.getClass("Relay");

    org.tzi.use.uml.sys.MObject o = state.createObject(relay, "r1");
    o.state(state).setAttributeValue(relay.attribute("state", true),
        UBooleanValue.valueOf(true, 0.3));

    org.tzi.use.uml.mm.MClassInvariant inv = invariantByName(model, "Relay::contactClosed");
    org.tzi.use.uml.ocl.value.VarBindings bindings =
        new org.tzi.use.uml.ocl.value.VarBindings();
    bindings.push("r", new org.tzi.use.uml.ocl.value.ObjectValue(relay, o));
    Value result = new org.tzi.use.uml.ocl.expr.Evaluator()
        .eval(inv.bodyExpression(), state, bindings);

    assertTrue("the REAL Op_uBoolean_toBooleanC must yield a defined Boolean",
        result instanceof BooleanValue);
    assertTrue("0.3 >= 0.2: U-aware TRUE through the real operation",
        ((BooleanValue) result).value());
    assertFalse("and the nominal reading is FALSE (0.3 < 0.5): genuine mode disagreement",
        ((BooleanValue) result).value() == (((UBooleanValue)
            o.state(state).attributeValue(relay.attribute("state", true))).probability() >= 0.5));
  }

  private static org.tzi.use.uml.mm.MClassInvariant invariantByName(
      org.tzi.use.uml.mm.MModel model, String name) {
    for (org.tzi.use.uml.mm.MClassInvariant candidate : model.classInvariants()) {
      if (candidate.qualifiedName().equals(name)) {
        return candidate;
      }
    }
    throw new AssertionError("missing invariant " + name);
  }

  private static org.tzi.use.uml.mm.MModel compile() {
    String spec = """
        model D2Verify
        class Relay
        attributes
          state : UBoolean
        end
        constraints
        context r : Relay inv contactClosed: r.state.toBooleanC(0.2)
        """;
    java.io.StringWriter buffer = new java.io.StringWriter();
    java.io.PrintWriter err = new java.io.PrintWriter(buffer, true);
    org.tzi.use.uml.mm.MModel model =
        org.tzi.use.parser.use.USECompiler.compileSpecification(spec, "D2Verify", err,
            new org.tzi.use.uml.mm.ModelFactory());
    org.junit.Assert.assertNotNull("fixture did not compile:\n" + buffer, model);
    return model;
  }
}
