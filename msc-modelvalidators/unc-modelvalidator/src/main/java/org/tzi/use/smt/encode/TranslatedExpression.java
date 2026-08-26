package org.tzi.use.smt.encode;

import java.util.List;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;

/** An expression's explicit definedness and its value when defined. */
public record TranslatedExpression(SmtTerm defined, SmtTerm value) {
  public TranslatedExpression {
    if (defined == null || value == null) {
      throw new IllegalArgumentException("definedness and value terms are required");
    }
  }

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
