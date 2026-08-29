package org.tzi.use.smt.encode;

import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * The SOURCE of a U-type let binding whose consumer enumerates configured candidates: the bare
 * attribute access or single-valued navigation a {@code UString}/{@code UBoolean}/{@code
 * UReal}/{@code UInteger} let initialized from, carried on the translator's {@code LocalBinding}
 * so {@link UBooleanProbability}'s case enumeration can read the same configured candidate lists
 * and the same source symbols it would read for the attribute itself -- the SMT let makes the
 * alias and the source interchangeable, and the alias key keeps the read-once aliasing rule per
 * let variable.
 *
 * <p>A bare-attribute initializer contributes exactly ONE slot ({@code guard = true}); a
 * navigated initializer contributes one slot PER destination slot of the end view, each with its
 * own selected symbols and its link term as the guard. {@code firstDomain} is the UBoolean
 * probability domain or the UString spelling domain; {@code secondDomain} is the UString
 * confidence domain, null for UBoolean and for the paired families.
 */
public record LetBindingSource(String aliasKey, List<LetSlot> slots) {

  public LetBindingSource {
    slots = List.copyOf(slots);
  }

  /** One candidate-enumerable position: its symbols, its guard, and its configured domains. */
  public record LetSlot(
      SmtTerm firstSymbol,
      SmtTerm secondSymbol,
      SmtTerm guard,
      AttributeDomain firstDomain,
      AttributeDomain secondDomain) {}
}
