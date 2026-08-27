package org.tzi.use.smt.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One finite measurement scenario {@code s in Sigma_K}.
 *
 * <p>The split between a snapshot and a scenario is fixed by the proposal ("Scope and satisfaction
 * contract"), not chosen here:
 *
 * <blockquote>
 * "s in Sigma_K is one finite measurement scenario. By default it selects one configured
 * uncertainty or confidence for every potentially live U-type attribute slot. A singleton choice
 * expresses shared fixed quality [...] Keeping representatives in S and measurement quality in s
 * makes the three scenario questions below unambiguous."
 * </blockquote>
 *
 * <p>So a snapshot {@code S} owns the objects, the links and the REPRESENTATIVE attribute values,
 * and a scenario owns the {@code _uncertainty}/{@code _confidence} components. That maps exactly
 * onto the {@link AttributeDomain#component()} plumbing: a UReal attribute's {@code _value} domain
 * belongs to {@code S} and its {@code _uncertainty} domain generates {@code Sigma_K}.
 *
 * <p>Only {@code uncertainty} occurs in this slice because {@code UReal} is the only U-type the
 * translation supports; {@code confidence} components join the same cross product unchanged when
 * their types land.
 */
public record Scenario(int index, List<Binding> bindings) {
  public Scenario {
    bindings = List.copyOf(bindings);
  }

  /** One slot's configured measurement quality: {@code Class_i.attribute_component = value}. */
  public record Binding(
      String className, String attributeName, int slotIndex, String component, BigDecimal value) {
    public Binding {
      if (value == null) {
        throw new IllegalArgumentException("a scenario binding needs a configured value");
      }
    }

    /** The SMT symbol this binding pins, in the unsuffixed (single-scenario) encoding. */
    public String symbolStem() {
      return className + "_" + slotIndex + "_" + attributeName + "_" + component;
    }

    @Override
    public String toString() {
      return symbolStem() + "=" + value.toPlainString();
    }
  }

  /** A stable, human-readable identity for reports and failure messages. */
  public String label() {
    return bindings.isEmpty()
        ? "s" + index + "{crisp}"
        : "s"
            + index
            + "{"
            + bindings.stream().map(Binding::toString).collect(Collectors.joining(", "))
            + "}";
  }

  @Override
  public String toString() {
    return label();
  }
}
