package org.tzi.use.smt.encode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;

/** Encodes a guarded binary link grid with cross-wired end multiplicities. */
public final class AssociationLinkEncoder {
  private AssociationLinkEncoder() {}

  public static AssociationLinks encode(
      SmtScript script,
      String associationName,
      ObjectSlots aEnd,
      Multiplicity linksPerB,
      ObjectSlots bEnd,
      Multiplicity linksPerA,
      AssociationScope aggregate) {
    String[][] names = new String[aEnd.capacity()][bEnd.capacity()];
    List<SmtTerm> all = new ArrayList<>();
    for (int i = 0; i < aEnd.capacity(); i++)
      for (int j = 0; j < bEnd.capacity(); j++) {
        String n = associationName + "_" + i + "_" + j;
        script.declareConst(n, SmtSort.BOOL);
        names[i][j] = n;
        SmtTerm link = Smt.sym(n);
        all.add(link);
        script.assertThat(Smt.app("=>", link, Smt.sym(aEnd.existsNames().get(i))));
        script.assertThat(Smt.app("=>", link, Smt.sym(bEnd.existsNames().get(j))));
      }
    for (int j = 0; j < bEnd.capacity(); j++) {
      List<SmtTerm> col = new ArrayList<>();
      for (int i = 0; i < aEnd.capacity(); i++) col.add(Smt.sym(names[i][j]));
      degree(script, col, linksPerB);
    }
    for (int i = 0; i < aEnd.capacity(); i++) {
      List<SmtTerm> row = new ArrayList<>();
      for (int j = 0; j < bEnd.capacity(); j++) row.add(Smt.sym(names[i][j]));
      degree(script, row, linksPerA);
    }
    degree(script, all, new Multiplicity(aggregate.min(), aggregate.max()));
    return new AssociationLinks(associationName, aEnd, bEnd, names);
  }

  private static void degree(SmtScript s, List<SmtTerm> terms, Multiplicity m) {
    SmtTerm count = sum(terms);
    if (m.lower() > 0)
      s.assertThat(Smt.app(">=", count, Smt.intLit(BigInteger.valueOf(m.lower()))));
    if (!m.isUnbounded())
      s.assertThat(Smt.app("<=", count, Smt.intLit(BigInteger.valueOf(m.upper()))));
  }

  private static SmtTerm sum(List<SmtTerm> terms) {
    if (terms.isEmpty()) return Smt.intLit(BigInteger.ZERO);
    SmtTerm total =
        Smt.ite(terms.getFirst(), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO));
    for (int i = 1; i < terms.size(); i++)
      total =
          Smt.app(
              "+",
              total,
              Smt.ite(terms.get(i), Smt.intLit(BigInteger.ONE), Smt.intLit(BigInteger.ZERO)));
    return total;
  }
}
