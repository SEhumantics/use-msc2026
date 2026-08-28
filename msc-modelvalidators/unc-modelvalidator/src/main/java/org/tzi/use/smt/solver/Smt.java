package org.tzi.use.smt.solver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Factory methods for {@link SmtTerm}. */
public final class Smt {

  private Smt() {}

  public static SmtTerm sym(String name) {
    return new SmtTerm.Atom(name);
  }

  public static SmtTerm bool(boolean value) {
    return new SmtTerm.Atom(Boolean.toString(value));
  }

  public static SmtTerm intLit(BigInteger value) {
    if (value.signum() < 0) {
      return app("-", new SmtTerm.Atom(value.negate().toString()));
    }
    return new SmtTerm.Atom(value.toString());
  }

  /** A real literal in the SMT-LIB form, including unary minus for negatives. */
  public static SmtTerm realLit(BigDecimal value) {
    if (value.signum() < 0) {
      return app("-", new SmtTerm.Atom(withDecimalPoint(value.negate())));
    }
    return new SmtTerm.Atom(withDecimalPoint(value));
  }

  private static String withDecimalPoint(BigDecimal value) {
    String plain = value.toPlainString();
    return plain.contains(".") ? plain : plain + ".0";
  }

  public static SmtTerm app(String op, SmtTerm... args) {
    return new SmtTerm.App(op, List.of(args));
  }

  public static SmtTerm and(List<SmtTerm> conjuncts) {
    return nary("and", conjuncts, bool(true));
  }

  public static SmtTerm or(List<SmtTerm> disjuncts) {
    return nary("or", disjuncts, bool(false));
  }

  private static SmtTerm nary(String op, List<SmtTerm> args, SmtTerm unit) {
    if (args.isEmpty()) {
      return unit;
    }
    if (args.size() == 1) {
      return args.get(0);
    }
    return new SmtTerm.App(op, new ArrayList<>(args));
  }

  public static SmtTerm not(SmtTerm term) {
    return app("not", term);
  }

  public static SmtTerm eq(SmtTerm left, SmtTerm right) {
    return app("=", left, right);
  }

  public static SmtTerm ite(SmtTerm condition, SmtTerm thenTerm, SmtTerm elseTerm) {
    return app("ite", condition, thenTerm, elseTerm);
  }

  public static SmtTerm let(List<SmtTerm.Binding> bindings, SmtTerm body) {
    return new SmtTerm.Let(bindings, body);
  }
}
