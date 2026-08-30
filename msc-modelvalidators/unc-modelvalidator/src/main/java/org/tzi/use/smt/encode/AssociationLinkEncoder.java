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

  /** The single-range form every pre-multi-range call site uses. */
  public static AssociationLinks encode(
      SmtScript script,
      String associationName,
      ObjectSlots aEnd,
      Multiplicity linksPerB,
      ObjectSlots bEnd,
      Multiplicity linksPerA,
      AssociationScope aggregate) {
    return encode(
        script,
        associationName,
        aEnd,
        List.of(linksPerB),
        bEnd,
        List.of(linksPerA),
        aggregate);
  }

  /**
   * @param rangesPerB the bEnd's OWN declared multiplicity, as one {@link Multiplicity} per
   *     declared range ({@code 1,3..5} = two entries); bounds each bEnd slot's COLUMN sum.
   * @param rangesPerA the aEnd's own declared multiplicity; bounds each aEnd slot's ROW sum.
   */
  public static AssociationLinks encode(
      SmtScript script,
      String associationName,
      ObjectSlots aEnd,
      List<Multiplicity> rangesPerB,
      ObjectSlots bEnd,
      List<Multiplicity> rangesPerA,
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
      degree(script, col, Smt.sym(bEnd.existsNames().get(j)), rangesPerB);
    }
    for (int i = 0; i < aEnd.capacity(); i++) {
      List<SmtTerm> row = new ArrayList<>();
      for (int j = 0; j < bEnd.capacity(); j++) row.add(Smt.sym(names[i][j]));
      degree(script, row, Smt.sym(aEnd.existsNames().get(i)), rangesPerA);
    }
    // The association's own configured link-count bound is a bound on the link count ITSELF,
    // not a per-object UML multiplicity, so it stays unguarded.
    degree(script, all, new Multiplicity(aggregate.min(), aggregate.max()));
    return new AssociationLinks(associationName, aEnd, bEnd, names);
  }

  /**
   * One slot's degree constraint: an EXISTING slot's link count must land in one of the declared
   * ranges (the disjunction, so {@code 1,3..5} admits count 1 or 3..5 -- the incumbent ORs its
   * per-range formulas the same way).
   *
   * <p>UML multiplicities bind per EXISTING object: an instance is a set of objects and links, and
   * a non-existent object violates nothing -- which is also exactly the incumbent's semantics,
   * since a Kodkod relation only ever contains existing atoms. The constraint is therefore guarded
   * by the slot's own exists symbol. Asserting it UNCONDITIONALLY, as this encoder used to, forced
   * every slot of a {@code min < max} class into existence whenever the opposite end's lower bound
   * was positive (the slot's degree still had to reach the lower bound, but every link requires
   * both endpoints to exist) -- a confirmed false-refutation divergence from the incumbent:
   * on A[1]/B[0..1] with A pinned at 1 and B scoped 1..2, kk-modelvalidator reports SATISFIABLE
   * (the legal exactly-one-B instance) where the unguarded encoding reported UNSATISFIABLE.
   */
  static void degree(
      SmtScript s, List<SmtTerm> terms, SmtTerm exists, List<Multiplicity> ranges) {
    SmtTerm count = sum(terms);
    List<SmtTerm> allowed = new ArrayList<>();
    for (Multiplicity m : ranges) {
      List<SmtTerm> conjuncts = new ArrayList<>();
      if (m.lower() > 0) conjuncts.add(Smt.app(">=", count, Smt.intLit(BigInteger.valueOf(m.lower()))));
      if (!m.isUnbounded())
        conjuncts.add(Smt.app("<=", count, Smt.intLit(BigInteger.valueOf(m.upper()))));
      allowed.add(
          conjuncts.isEmpty()
              ? Smt.bool(true)
              : conjuncts.size() == 1 ? conjuncts.get(0) : Smt.and(conjuncts));
    }
    s.assertThat(Smt.app("=>", exists, Smt.or(allowed)));
  }

  static void degree(SmtScript s, List<SmtTerm> terms, Multiplicity m) {
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
