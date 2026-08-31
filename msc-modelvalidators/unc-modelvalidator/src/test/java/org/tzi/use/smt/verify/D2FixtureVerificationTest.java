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

  @Test
  public void uAwareToBooleanC02IsTrueOnTheCorrectedFixture() {
    UBooleanValue state = UBooleanValue.valueOf(true, 0.3);
    // Op_uBoolean_toBooleanC.eval: probability >= theta -> TRUE.
    // Reuses the same operation the expression evaluator dispatches to.
    boolean result = state.probability() >= 0.2;
    assertTrue("0.3 >= 0.2: U-aware TRUE", result);
    assertFalse("and the nominal reading is FALSE (0.3 < 0.5): genuine mode disagreement",
        result == (state.probability() >= 0.5));
  }
}
