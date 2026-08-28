package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * Asserts acyclicity of the whole-part graph formed by the UNION of every REFLEXIVE (both ends the
 * SAME class) composition/aggregation association targeting one class -- the {@code
 * aggregationcyclefreeness} configuration toggle's own meaning ("no cycles are allowed inside of
 * aggregations and compositions").
 *
 * <p>Combining associations into ONE graph is load-bearing, not a simplification: confirmed
 * directly against the real corpus (FileSystem.use's {@code PrimaryContains}/{@code AltContains},
 * both {@code Folder}-{@code Folder}), a cycle that only closes ACROSS two different associations
 * (one hop via each) needs their edges unioned before reachability is computed -- checking each
 * association's own link grid independently would miss it entirely, since neither association
 * alone contains a self-loop.
 *
 * <p>Edge direction: USE's own parser assigns a {@code composition}/{@code aggregation}
 * declaration's kind to the FIRST declared end only ({@code ASTAssociation.gen}'s own comment,
 * use-core: "kind of association determines kind of first association end"), and that marked end is
 * the WHOLE side. So for one association's grid cell {@code linkNames[whole][part]}, the "points to
 * its whole" edge this encoder needs is {@code part -> whole}.
 *
 * <p>Scoped to the reflexive (same-class) case only, matching the one shape the real corpus needs
 * -- combining associations whose ends are DIFFERENT classes into one heterogeneous graph is a
 * materially harder problem (different classes' slots are unrelated SMT identities under this
 * encoder), deliberately out of scope; {@code SmtModelFinder}'s own caller is responsible for
 * refusing rather than silently ignoring a cross-class composition/aggregation association with a
 * genuinely nonzero configured population.
 */
public final class CompositionCycleFreenessEncoder {
  private CompositionCycleFreenessEncoder() {}

  /**
   * Computed via the same "extend the reachable set by one more hop, N times" fixed-point
   * construction {@link ExpressionTranslator}'s own {@code closureReachability} uses for a single
   * association's single-seed reachability, generalized here to a full pairwise reachability
   * matrix (every candidate slot as its own seed) over the UNION of every given association's
   * edges, since acyclicity needs "can slot i reach slot i" for EVERY i, not just one fixed start.
   * Each hop's {@code capacity * capacity} candidate cells are bound to fresh, named SMT-LIB {@code
   * let} symbols, one {@code let} per hop nested inside the previous hop's body -- required for
   * correctness (SMT-LIB {@code let} bindings within ONE {@code let} are simultaneous) and to avoid
   * exponential term blowup, exactly as {@code closureReachability}'s own javadoc documents for the
   * single-seed case.
   */
  public static void assertAcyclic(
      SmtScript script, String stem, ObjectSlots slots, List<AssociationLinks> reflexiveCompositions) {
    int capacity = slots.capacity();
    if (capacity == 0 || reflexiveCompositions.isEmpty()) {
      return;
    }
    for (AssociationLinks links : reflexiveCompositions) {
      if (!links.aEnd().className().equals(slots.className())
          || !links.bEnd().className().equals(slots.className())) {
        throw new IllegalArgumentException(
            "CompositionCycleFreenessEncoder requires every given association to be reflexive over "
                + slots.className()
                + ", got "
                + links.associationName());
      }
    }

    List<List<SmtTerm.Binding>> hopBindings = new ArrayList<>();
    String[][] previous = new String[capacity][capacity];
    List<SmtTerm.Binding> firstHop = new ArrayList<>(capacity * capacity);
    for (int i = 0; i < capacity; i++) {
      for (int j = 0; j < capacity; j++) {
        String symbol = stem + "1-" + i + "-" + j + "|";
        previous[i][j] = symbol;
        firstHop.add(new SmtTerm.Binding(symbol, wholeEdge(reflexiveCompositions, i, j)));
      }
    }
    hopBindings.add(firstHop);
    for (int hop = 2; hop <= capacity; hop++) {
      String[][] current = new String[capacity][capacity];
      List<SmtTerm.Binding> bindings = new ArrayList<>(capacity * capacity);
      for (int i = 0; i < capacity; i++) {
        for (int j = 0; j < capacity; j++) {
          List<SmtTerm> viaAnyIntermediate = new ArrayList<>();
          for (int mid = 0; mid < capacity; mid++) {
            viaAnyIntermediate.add(
                Smt.and(
                    List.of(Smt.sym(previous[i][mid]), wholeEdge(reflexiveCompositions, mid, j))));
          }
          String name = stem + hop + "-" + i + "-" + j + "|";
          current[i][j] = name;
          bindings.add(
              new SmtTerm.Binding(
                  name, Smt.or(List.of(Smt.sym(previous[i][j]), Smt.or(viaAnyIntermediate)))));
        }
      }
      hopBindings.add(bindings);
      previous = current;
    }

    List<SmtTerm> noSelfCycle = new ArrayList<>(capacity);
    for (int i = 0; i < capacity; i++) {
      SmtTerm exists = Smt.sym(slots.existsNames().get(i));
      noSelfCycle.add(Smt.app("=>", exists, Smt.not(Smt.sym(previous[i][i]))));
    }
    SmtTerm assertion = Smt.and(noSelfCycle);
    for (int hop = hopBindings.size() - 1; hop >= 0; hop--) {
      assertion = Smt.let(hopBindings.get(hop), assertion);
    }
    script.assertThat(assertion);
  }

  /** The union, over every given association, of "slot {@code part}'s whole is slot {@code whole}". */
  private static SmtTerm wholeEdge(List<AssociationLinks> compositions, int part, int whole) {
    List<SmtTerm> options = new ArrayList<>();
    for (AssociationLinks links : compositions) {
      options.add(Smt.sym(links.linkNames()[whole][part]));
    }
    return Smt.or(options);
  }
}
