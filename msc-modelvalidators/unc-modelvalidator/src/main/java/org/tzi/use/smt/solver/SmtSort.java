package org.tzi.use.smt.solver;

/** The SMT-LIB sorts this translation uses. */
public enum SmtSort {
  BOOL("Bool"),
  INT("Int"),
  REAL("Real");

  private final String smtName;

  SmtSort(String smtName) {
    this.smtName = smtName;
  }

  public String smtName() {
    return smtName;
  }
}
