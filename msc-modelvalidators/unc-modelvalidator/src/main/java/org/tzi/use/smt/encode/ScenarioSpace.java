package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.config.ScenarioProfile;

/**
 * Enumerates {@code Sigma_K}, the finite set of configured measurement scenarios.
 *
 * <p><b>The convention, stated rather than left implicit.</b> The proposal's default is that a
 * scenario "selects one configured uncertainty or confidence for every potentially live U-type
 * attribute slot", so {@code Sigma_K} is the CROSS PRODUCT over those slots of their configured
 * component values. Two consequences follow and are both deliberate:
 *
 * <ul>
 *   <li>The axis is the SLOT, not the attribute: a class with capacity 3 and one two-valued {@code
 *       _uncertainty} domain has 2^3 = 8 scenarios, because each candidate object may carry its own
 *       measurement quality. Configurations meant for COVER/UNIFORM should keep the candidate
 *       population small for exactly this reason.
 *   <li>A SINGLETON component domain contributes one factor of size one -- the proposal's "shared
 *       fixed quality" -- so it does not multiply the space at all. A model with no U-typed
 *       attributes therefore has exactly ONE (empty) scenario, and EXISTS, COVER and UNIFORM
 *       genuinely coincide on it. That is the equations' own answer for {@code |Sigma_K| = 1}, not
 *       a silent degradation.
 * </ul>
 *
 * <p>The refinement in which "explicit configuration may bind several slots to one named
 * measurement source" is NOT implemented; every slot gets an independent factor.
 *
 * <p>A component domain with no enumerated candidates (a bounded RANGE) makes {@code Sigma_K}
 * infinite, so COVER and UNIFORM -- which quantify universally over it -- fail closed instead of
 * sampling it.
 */
public final class ScenarioSpace {
  private ScenarioSpace() {}

  /** One U-typed attribute on one concrete class, with the slot capacity it was encoded at. */
  public record UncertainAttribute(
      String className, String attributeName, int capacity, AttributeDomain uncertaintyDomain) {}

  /**
   * The complete configured scenario set, in a stable order: the LAST axis varies fastest, so a
   * single-attribute single-slot fixture enumerates its component values in configuration order.
   */
  public static List<Scenario> enumerate(
      List<UncertainAttribute> attributes, ScenarioProfile profile) {
    List<List<Scenario.Binding>> axes = new ArrayList<>();
    for (UncertainAttribute attribute : attributes) {
      List<String> candidates = attribute.uncertaintyDomain().enumeratedValues();
      if (candidates.isEmpty()) {
        throw new IllegalArgumentException(
            "scenario profile "
                + profile
                + " quantifies over every configured measurement scenario, but '"
                + attribute.className()
                + "."
                + attribute.attributeName()
                + "_"
                + attribute.uncertaintyDomain().component()
                + "' is configured as a bounded range rather than a finite Set{...} of"
                + " candidates, so Sigma_K is not finite; refusing rather than sampling it");
      }
      for (int slot = 0; slot < attribute.capacity(); slot++) {
        List<Scenario.Binding> axis = new ArrayList<>();
        for (String candidate : candidates) {
          axis.add(
              new Scenario.Binding(
                  attribute.className(),
                  attribute.attributeName(),
                  slot,
                  attribute.uncertaintyDomain().component(),
                  parse(attribute, candidate)));
        }
        axes.add(axis);
      }
    }

    List<List<Scenario.Binding>> combinations = new ArrayList<>();
    combinations.add(List.of());
    for (List<Scenario.Binding> axis : axes) {
      List<List<Scenario.Binding>> extended = new ArrayList<>();
      for (List<Scenario.Binding> prefix : combinations) {
        for (Scenario.Binding choice : axis) {
          List<Scenario.Binding> next = new ArrayList<>(prefix);
          next.add(choice);
          extended.add(next);
        }
      }
      combinations = extended;
    }

    List<Scenario> scenarios = new ArrayList<>();
    for (int index = 0; index < combinations.size(); index++) {
      scenarios.add(new Scenario(index, combinations.get(index)));
    }
    return scenarios;
  }

  private static BigDecimal parse(UncertainAttribute attribute, String candidate) {
    try {
      return new BigDecimal(candidate.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "invalid "
              + attribute.uncertaintyDomain().component()
              + " candidate '"
              + candidate
              + "' for '"
              + attribute.className()
              + "."
              + attribute.attributeName()
              + "'",
          e);
    }
  }
}
