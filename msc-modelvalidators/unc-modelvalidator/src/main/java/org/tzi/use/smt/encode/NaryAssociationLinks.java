package org.tzi.use.smt.encode;

import java.util.List;

/**
 * The link-boolean grid for one N-ary association (arity &ge; 3), one Bool per TUPLE over the
 * end views in the association's DECLARED end order, flattened row-major. The binary
 * {@link AssociationLinks} grid stays untouched for every existing path; n-ary links flow only
 * through the n-ary consumers (population-based navigation, predefined n-ary links, n-ary
 * reconstruction), each of which decodes tuple indices with {@link #linkName(int...)}.
 *
 * <p>End views are the same folded slot views the binary encoder uses (declared class plus
 * configured subclasses, SmtModelFinder's endSlotsView order), so a polymorphic context or
 * navigation slot resolves against its concrete class exactly as on the binary paths.
 */
public record NaryAssociationLinks(
    String associationName, List<ObjectSlots> endViews, int[] capacities, String[] linkNames) {

  public NaryAssociationLinks {
    endViews = List.copyOf(endViews);
    capacities = capacities.clone();
  }

  /** The arity: how many ends this association declares. */
  public int arity() {
    return endViews.size();
  }

  /** The total number of tuples in the grid (product of the per-end capacities). */
  public int tupleCount() {
    int total = 1;
    for (int capacity : capacities) {
      total *= capacity;
    }
    return total;
  }

  /**
   * The Bool constant name of the tuple selecting {@code indices} -- one index per end, in
   * declared end order, each within its end view's capacity. Row-major strides, the inverse of
   * the enumeration the encoder and the reconstructor iterate.
   */
  public String linkName(int... indices) {
    if (indices.length != arity()) {
      throw new IllegalArgumentException(
          "association "
              + associationName
              + " has "
              + arity()
              + " ends; got "
              + indices.length
              + " indices");
    }
    int flat = 0;
    for (int e = 0; e < indices.length; e++) {
      if (indices[e] < 0 || indices[e] >= capacities[e]) {
        throw new IllegalArgumentException(
            "index " + indices[e] + " out of range for end " + e + " of " + associationName);
      }
      flat = flat * capacities[e] + indices[e];
    }
    return linkNames[flat];
  }

  /** The end view at declared position {@code position}. */
  public ObjectSlots endView(int position) {
    return endViews.get(position);
  }

  /**
   * The index of {@code binding} within the end view at declared position {@code position}, or
   * -1 when the binding is not a member of that view (an unrelated class).
   */
  public int indexOf(int position, VariableBinding binding) {
    return endViews.get(position).indexOf(binding);
  }
}
