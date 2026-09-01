package org.tzi.use.smt.encode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.config.ScenarioProfile;

/**
 * BUG A: {@link ScenarioSpace#enumerate} used to build {@code Sigma_K}'s full cross product with no
 * size cap at all, unlike every sibling combinatorial expansion in this codebase ({@link
 * UBooleanProbability#MAX_CASES} and {@code ExpressionTranslator}'s several 256-combination
 * checks). That matters more here than anywhere else those caps apply: COVER solves one script PER
 * enumerated scenario, and UNIFORM encodes every one of them into a single joint script, so an
 * uncapped cross product is a script-size blowup rather than merely a large term inside one
 * operator.
 */
public class ScenarioSpaceTest {

  @Test
  public void enumeratesTheFullSlotCrossProductUnderTheCap() {
    // Two candidates over a capacity-2 attribute: the axis is the SLOT, so this is 2^2 = 4
    // scenarios, not 2.
    AttributeDomain uncertainty =
        new AttributeDomain(
            "UnidentifiedObject", "speed", "uncertainty", List.of("0.02", "0.06"), null, null);
    List<ScenarioSpace.UncertainAttribute> attributes =
        List.of(new ScenarioSpace.UncertainAttribute("UnidentifiedObject", "speed", 2, uncertainty));

    List<Scenario> scenarios = ScenarioSpace.enumerate(attributes, ScenarioProfile.COVER);

    assertEquals(4, scenarios.size());
  }

  @Test
  public void refusesRatherThanEnumeratingBeyondThe256CombinationCap() {
    // One U-typed attribute, capacity 3, 7 configured candidates per slot: 7^3 = 343 scenarios,
    // over the 256-combination convention -- crossing the cap from a SINGLE attribute, exactly as
    // the class comment warns a small, legitimate-looking configuration can.
    List<String> sevenCandidates = List.of("0.01", "0.02", "0.03", "0.04", "0.05", "0.06", "0.07");
    AttributeDomain uncertainty =
        new AttributeDomain(
            "UnidentifiedObject", "speed", "uncertainty", sevenCandidates, null, null);
    List<ScenarioSpace.UncertainAttribute> attributes =
        List.of(new ScenarioSpace.UncertainAttribute("UnidentifiedObject", "speed", 3, uncertainty));

    SmtTranslationException refused =
        assertThrows(
            SmtTranslationException.class,
            () -> ScenarioSpace.enumerate(attributes, ScenarioProfile.UNIFORM));

    assertEquals(FragmentBoundary.UTYPE_CORE, refused.boundary());
    assertTrue(refused.getMessage(), refused.getMessage().contains("343"));
    assertTrue(refused.getMessage(), refused.getMessage().contains("256"));
  }

  @Test
  public void exactlyAtTheCapStillEnumeratesRatherThanRefusing() {
    // 2^8 = 256 scenarios: AT the cap, not over it, so this must still build the full list --
    // the boundary condition the ">" (not ">=") in the fix has to get right.
    AttributeDomain uncertainty =
        new AttributeDomain(
            "UnidentifiedObject", "speed", "uncertainty", List.of("0.02", "0.06"), null, null);
    List<ScenarioSpace.UncertainAttribute> attributes =
        List.of(new ScenarioSpace.UncertainAttribute("UnidentifiedObject", "speed", 8, uncertainty));

    List<Scenario> scenarios = ScenarioSpace.enumerate(attributes, ScenarioProfile.COVER);

    assertEquals(256, scenarios.size());
  }
}
