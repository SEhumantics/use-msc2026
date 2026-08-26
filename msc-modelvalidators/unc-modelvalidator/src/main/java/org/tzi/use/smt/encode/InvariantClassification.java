package org.tzi.use.smt.encode;

import java.util.List;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;

/** Named SMT {@code def}/{@code val} symbols for one invariant in one translation mode. */
public record InvariantClassification(
    String invariantName,
    TranslationMode mode,
    String definedName,
    String valueName,
    SmtTerm defined,
    SmtTerm value) {

  public SmtTerm trueTerm() {
    return Smt.and(List.of(defined, value));
  }

  public SmtTerm falseTerm() {
    return Smt.and(List.of(defined, Smt.not(value)));
  }

  public SmtTerm undefinedTerm() {
    return Smt.not(defined);
  }
}
