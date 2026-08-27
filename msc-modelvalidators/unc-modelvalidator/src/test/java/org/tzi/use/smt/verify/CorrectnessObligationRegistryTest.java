package org.tzi.use.smt.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Milestone 4.7a, Part 2. The proposal's 7 "Correctness strategy" states seven claims and a list of
 * proof obligations in prose; several were already tested by Milestones 4.3-4.6, but nothing said
 * WHICH. {@link CorrectnessObligation} is that mapping, and this test is what stops it rotting: it
 * resolves every declared discharging test by reflection, so renaming or deleting one breaks the
 * build rather than silently un-covering a claim.
 */
public class CorrectnessObligationRegistryTest {

  /**
   * Every declared discharge names a real, {@code @Test}-annotated method. This is the whole point
   * of a registry over a comment: a comment cannot be wrong loudly.
   */
  @Test
  public void everyDeclaredDischargeResolvesToARealAnnotatedTestMethod() throws Exception {
    List<String> broken = new ArrayList<>();
    for (CorrectnessObligation obligation : CorrectnessObligation.values()) {
      assertFalse(obligation + " states no claim", obligation.claim().isBlank());
      assertFalse(obligation + " discharges nothing", obligation.discharges().isEmpty());
      for (String discharge : obligation.discharges()) {
        int hash = discharge.indexOf('#');
        assertTrue(discharge + " is not Class#method", hash > 0);
        String className = discharge.substring(0, hash);
        String methodName = discharge.substring(hash + 1);
        Class<?> testClass;
        try {
          testClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
          broken.add(discharge + " (no such class)");
          continue;
        }
        Method method;
        try {
          method = testClass.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException e) {
          broken.add(discharge + " (no such method)");
          continue;
        }
        if (method.getAnnotation(org.junit.Test.class) == null) {
          broken.add(discharge + " (not annotated @Test)");
        }
      }
    }
    assertEquals("unresolvable obligation discharges: " + broken, List.of(), broken);
  }

  /**
   * The definition of done names the areas that must be covered. Enumerating them here means a
   * future edit that quietly DROPS an obligation fails, rather than leaving a shorter list that
   * still looks complete.
   */
  @Test
  public void everyObligationTheDefinitionOfDoneNamesIsPresent() {
    Set<CorrectnessObligation> required =
        EnumSet.of(
            CorrectnessObligation.TYPE_AND_DEFINEDNESS_PRESERVATION,
            CorrectnessObligation.EXPRESSION_AGREEMENT,
            CorrectnessObligation.QUERY_WITNESS_SOUNDNESS,
            CorrectnessObligation.COUNTEREXAMPLE_ATTRIBUTION,
            CorrectnessObligation.FRAGILE_IMPLICATION_AND_ATTRIBUTION,
            CorrectnessObligation.SCENARIO_PROFILE_AND_SHARING,
            CorrectnessObligation.ALIVE_SLOT_QUANTIFIER_RANGE,
            CorrectnessObligation.RECONSTRUCTION_WITHIN_SCOPE,
            CorrectnessObligation.QUALIFIED_BOUNDED_COMPLETENESS);
    assertTrue(
        "the definition of done's obligations must all be registered",
        EnumSet.allOf(CorrectnessObligation.class).containsAll(required));
  }

  /**
   * The three obligations the milestone brief says are already tested must point AT those existing
   * tests, not at new duplicates of them.
   */
  @Test
  public void theAlreadyTestedObligationsAreWiredInRatherThanDuplicated() {
    assertTrue(
        CorrectnessObligation.FRAGILE_IMPLICATION_AND_ATTRIBUTION
            .discharges()
            .contains(
                "org.tzi.use.smt.finder.FragileQueryTest#everyFragileWitnessIsAlsoACounterexample"));
    assertTrue(
        CorrectnessObligation.SCENARIO_PROFILE_AND_SHARING.discharges().stream()
            .anyMatch(d -> d.startsWith("org.tzi.use.smt.finder.ScenarioProfileTest#")));
    assertTrue(
        CorrectnessObligation.QUERY_WITNESS_SOUNDNESS.discharges().stream()
            .anyMatch(d -> d.startsWith("org.tzi.use.smt.verify.QueryWitnessCheckerTest#")));
  }
}
