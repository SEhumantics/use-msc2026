package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.uml.ocl.expr.ExpAttrOp;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uncertainty.datatypes.UBoolean;

/**
 * Lowers a {@code UBoolean} expression to a finite, mutually exclusive, exhaustive set of CASES,
 * each pairing a purely propositional SMT guard with a probability that is a Java {@code double}
 * CONSTANT.
 *
 * <p><b>Why this shape, and why it is the only shape that can be emitted.</b> {@code
 * archive2/robust_utype_model_finding_proposal.md} fixes the UBoolean rules as {@code not(p) =
 * 1-p}, {@code p1 and p2 = p1*p2}, {@code p1 or p2 = 1-(1-p1)(1-p2)} and {@code implies =
 * 1-p1(1-p2)}. Three of those four are NONLINEAR: they multiply two probabilities. This project
 * pins {@code QF_LIRA} -- LINEAR integer/real arithmetic -- and widening that logic to make a
 * conjunction translate would change solver behaviour across the whole project, not just this
 * feature.
 *
 * <p>The proposal states its own mitigation: "Version 1 makes base confidence values constants or
 * finite configured choices to control nonlinear search." This class IS that mitigation. Every base
 * probability is one of finitely many configured candidates, so the composition is enumerated at
 * TRANSLATION time and each combination's probability is an ordinary Java number. What reaches the
 * solver is only {@code (= <Real symbol> <rational literal>)} atoms combined with {@code and} /
 * {@code or} / {@code not} -- <b>no arithmetic operator is emitted at all</b>, so the emitted
 * script is trivially inside {@code QF_LIRA}. Anything that would require multiplying two
 * SOLVER-CHOSEN probabilities fails closed instead; see {@link #refuseNonFinite}.
 *
 * <p><b>The arithmetic is USE's, not a reimplementation of it.</b> Each combination calls the real
 * {@code org.tzi.use.uncertainty.datatypes.UBoolean}, and reproduces the SHORT-CIRCUITS of {@code
 * StandardOperationsUBoolean} as well as the formulas -- {@code Op_uBoolean_and} returns the
 * zero-probability operand itself rather than computing a product, and {@code Op_uBoolean_implies}
 * returns {@code UBooleanValue.TRUE} outright when its antecedent has probability zero. Those
 * branches are not redundant at double precision: {@code 1 + p - 1*p} is {@code 1.0000000000000002}
 * for {@code p = 0.1}, not {@code 1}. Reproducing the evaluator's control flow, not merely its
 * algebra, is what makes the encoded value bit-identical to the evaluator's.
 */
public final class UBooleanProbability {

  /**
   * The expansion cap. Beyond it the finite enumeration stops being a mitigation for nonlinear
   * search and starts being a different scaling problem, so it fails closed rather than emitting a
   * script whose size is a surprise.
   */
  static final int MAX_CASES = 256;

  private UBooleanProbability() {}

  /**
   * One combination of configured choices: the guard that identifies it and the probability USE's
   * own arithmetic gives it.
   */
  public record Case(SmtTerm guard, double probability) {}

  /**
   * Lowers {@code expression} and returns its cases.
   *
   * @throws SmtTranslationException, always with a located boundary, for any shape outside the
   *     supported fragment.
   */
  public static List<Case> lower(Expression expression, TranslationContext context) {
    return lower(expression, context, new LinkedHashSet<>());
  }

  /**
   * {@code toBooleanC(p, theta) <=> p >= theta}, applied case by case. The comparison is the
   * evaluator's own: {@code Op_uBoolean_toBooleanC} compares {@code left.probability() >=
   * confidence} on doubles, and so does this.
   *
   * <p>The result is a disjunction of the guards of the cases that clear {@code threshold}. Because
   * the guards are exhaustive under the owning slot's existence guard and mutually exclusive,
   * exactly one of them holds in any model, so the disjunction says precisely "the combination that
   * was chosen clears the threshold".
   */
  public static SmtTerm select(List<Case> cases, double threshold) {
    List<SmtTerm> admitted = new ArrayList<>();
    for (Case candidate : cases) {
      if (candidate.probability() >= threshold) {
        admitted.add(candidate.guard());
      }
    }
    if (admitted.isEmpty()) {
      return Smt.bool(false);
    }
    if (admitted.size() == cases.size()) {
      // Every configured combination clears the threshold, so the answer does not depend on which
      // one the solver picks. Stated as `true` rather than as an exhaustive disjunction, which
      // would only be equivalent under the existence guard.
      return Smt.bool(true);
    }
    return Smt.or(admitted);
  }

  /** True for the UBoolean shapes {@link #lower} can handle, used to route the translator. */
  public static boolean isSupportedShape(Expression expression) {
    return expression instanceof ExpAttrOp || expression instanceof ExpStdOp;
  }

  private static List<Case> lower(
      Expression expression, TranslationContext context, Set<String> alreadyRead) {
    if (expression instanceof ExpAttrOp attribute) {
      return storedProbability(attribute, context, alreadyRead);
    }
    if (expression instanceof ExpStdOp operation) {
      return operation(operation, context, alreadyRead);
    }
    throw unsupported(
        FragmentBoundary.UTYPE_CORE,
        "UBoolean expression '"
            + expression
            + "' ("
            + expression.getClass().getSimpleName()
            + ") is not a stored probability or a supported connective");
  }

  /**
   * A stored UBoolean attribute's probability, split into one case per configured candidate.
   *
   * <p>The same slot may not be read twice anywhere in one projected expression. That is a
   * deliberate, conservative refusal and it was MEASURED rather than reasoned about: {@code
   * UBoolean.and} carries a reference-identity fast path ({@code if (this==b) return new
   * UBoolean(this.b & b.b, this.c)}) which returns {@code p}, not {@code p*p}, whenever both
   * operands evaluate to the same object -- which is exactly what two reads of one attribute do.
   * Run against the real evaluator with {@code p = 0.5}, {@code (self.f and
   * self.f).toBooleanC(0.26)} answers {@code true}, while {@code 0.5*0.5 = 0.25} would answer
   * {@code false}. The proposal's rules are stated for INDEPENDENT operands ("The source assumes
   * independence"), so an aliased conjunction is outside them and is refused rather than encoded to
   * either reading.
   */
  private static List<Case> storedProbability(
      ExpAttrOp attribute, TranslationContext context, Set<String> alreadyRead) {
    if (!attribute.type().isTypeOfUBoolean()) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UBoolean composition over the non-UBoolean operand '" + attribute + "'");
    }
    VariableBinding binding = context.binding(variableNameOf(attribute.objExp()));
    String attributeName = attribute.attr().name();
    AttributeValues values = context.attributeValues(binding.className(), attributeName);
    if (values.type() != AttributeType.UBOOLEAN) {
      throw unsupported(
          FragmentBoundary.ENCODING_SCOPE,
          "no UBoolean probability symbol registered for "
              + binding.className()
              + "."
              + attributeName);
    }
    String slot = binding.className() + "#" + binding.slotIndex() + "." + attributeName;
    if (!alreadyRead.add(slot)) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UBoolean composition reads "
              + slot
              + " more than once; the source rules assume INDEPENDENT operands, and USE's own"
              + " UBoolean.and returns p rather than p*p for two reads of one value");
    }
    AttributeDomain domain =
        context.attributeDomain(binding.className(), attributeName, "probability");
    if (domain.enumeratedValues().isEmpty()) {
      throw refuseNonFinite(binding.className() + "." + attributeName);
    }
    SmtTerm symbol = Smt.sym(values.valueNames().get(binding.slotIndex()));
    List<Case> cases = new ArrayList<>();
    for (String candidate : domain.enumeratedValues()) {
      BigDecimal probability = parse(candidate, binding.className(), attributeName);
      cases.add(new Case(Smt.eq(symbol, Smt.realLit(probability)), probability.doubleValue()));
    }
    return cases;
  }

  private static List<Case> operation(
      ExpStdOp operation, TranslationContext context, Set<String> alreadyRead) {
    Expression[] args = operation.args();
    String name = operation.opname();
    if ("not".equals(name) && args.length == 1) {
      List<Case> operand = lower(args[0], context, alreadyRead);
      List<Case> negated = new ArrayList<>(operand.size());
      for (Case candidate : operand) {
        negated.add(new Case(candidate.guard(), complement(candidate.probability())));
      }
      return negated;
    }
    if (List.of("and", "or", "implies").contains(name) && args.length == 2) {
      List<Case> left = lower(args[0], context, alreadyRead);
      List<Case> right = lower(args[1], context, alreadyRead);
      if ((long) left.size() * right.size() > MAX_CASES) {
        throw unsupported(
            FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL,
            "UBoolean '"
                + name
                + "' over "
                + left.size()
                + " x "
                + right.size()
                + " configured choices exceeds the "
                + MAX_CASES
                + "-combination expansion cap; the finite enumeration is what keeps the product"
                + " rule inside QF_LIRA, so it is refused rather than emitted symbolically");
      }
      List<Case> combined = new ArrayList<>(left.size() * right.size());
      for (Case l : left) {
        for (Case r : right) {
          combined.add(
              new Case(
                  Smt.and(List.of(l.guard(), r.guard())),
                  compose(name, l.probability(), r.probability())));
        }
      }
      return combined;
    }
    if (List.of("xor", "equivalent").contains(name)) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UBoolean '"
              + name
              + "' is not in the source's rule set and is not derivable from it: USE computes xor"
              + " as abs(p1 - p2) (UBoolean.java xor), not as p1(1-p2) + p2(1-p1), and equivalent"
              + " as xor().not()");
    }
    if (List.of(">", ">=", "<", "<=", "=", "<>").contains(name)) {
      throw unsupported(
          FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL,
          "UBoolean composition over the comparison '"
              + operation
              + "', whose probability is a normal-CDF function of a solver-chosen representative"
              + " rather than a finite configured choice; multiplying it by another probability"
              + " would need a product of two solver variables, outside QF_LIRA");
    }
    throw unsupported(FragmentBoundary.UTYPE_CORE, "UBoolean operator '" + name + "'");
  }

  /**
   * {@code not(p) = 1-p}, computed through USE's own canonicalisation rather than by subtracting.
   * {@code UBoolean.not()} builds {@code (!b, c)} and the constructor's {@code setNormalForm}
   * rewrites it to {@code (true, 1-c)}, which is where the rule actually lives.
   */
  private static double complement(double probability) {
    return new UBoolean(true, probability).not().getC();
  }

  /** The connective's probability, reproducing {@code StandardOperationsUBoolean} exactly. */
  static double compose(String operator, double left, double right) {
    switch (operator) {
      case "and":
        // Op_uBoolean_and returns the zero-probability OPERAND, never a computed product.
        if (left == 0.0) {
          return left;
        }
        if (right == 0.0) {
          return right;
        }
        return new UBoolean(true, left).and(new UBoolean(true, right)).getC();
      case "or":
        // Op_uBoolean_or returns the certainly-true left operand without consulting the right.
        if (left == 1.0) {
          return left;
        }
        return new UBoolean(true, left).or(new UBoolean(true, right)).getC();
      case "implies":
        // Op_uBoolean_implies returns UBooleanValue.TRUE outright for a zero-probability
        // antecedent.
        if (left == 0.0) {
          return 1.0;
        }
        return new UBoolean(true, left).implies(new UBoolean(true, right)).getC();
      default:
        throw new IllegalArgumentException("not a supported UBoolean connective: " + operator);
    }
  }

  private static SmtTranslationException refuseNonFinite(String attribute) {
    return unsupported(
        FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL,
        "UBoolean attribute '"
            + attribute
            + "' has no finite configured probability domain; the source's and/or/implies rules are"
            + " products of probabilities, and only a finite configured choice lets them be"
            + " evaluated at translation time instead of emitted as nonlinear SMT");
  }

  private static BigDecimal parse(String candidate, String className, String attributeName) {
    BigDecimal probability;
    try {
      probability = new BigDecimal(candidate);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "invalid probability candidate '"
              + candidate
              + "' for attribute '"
              + className
              + "."
              + attributeName
              + "'",
          e);
    }
    if (probability.signum() < 0 || probability.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException(
          "UBoolean probability candidate '"
              + candidate
              + "' for attribute '"
              + className
              + "."
              + attributeName
              + "' is outside [0,1]");
    }
    return probability;
  }

  /**
   * Same wording {@code ExpressionTranslator.unsupported} uses, so a UBoolean refusal reads
   * identically to every other refusal in the ledger.
   */
  private static SmtTranslationException unsupported(FragmentBoundary boundary, String construct) {
    return new SmtTranslationException(
        boundary, "unsupported OCL construct in this translation slice: " + construct);
  }

  private static String variableNameOf(Expression expression) {
    if (expression instanceof ExpVariable variable) {
      return variable.getVarname();
    }
    throw unsupported(
        FragmentBoundary.TIER_2,
        "attribute access on a non-variable receiver '" + expression + "'");
  }
}
