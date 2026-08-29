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
import org.tzi.use.uml.ocl.expr.ExpConstString;
import org.tzi.use.uml.ocl.expr.ExpConstUString;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uncertainty.datatypes.UBoolean;
import org.tzi.use.uncertainty.datatypes.UString;

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
 * <p><b>{@code UString} equality lowers here too, and for the same reason.</b> The source's
 * UString-to-UString rule is "let {@code b = c_s * c_r}; the evaluator returns {@code b} when the
 * representative spellings match and {@code 1 - b} otherwise" -- another PRODUCT of two quantities
 * the solver would otherwise choose. Its result type in USE really is a {@code UBoolean} ({@code
 * Op_equal.matches} returns {@code TypeFactory.mkUBoolean()} whenever either operand is an
 * uncertain type), so a UString equality is not a special case bolted on beside this lowering: it
 * IS one of the UBoolean expressions this lowering exists to handle, and it composes with {@code
 * not}/{@code and}/{@code or}/{@code implies} through the very same cases. The spellings and
 * confidences are finite configured choices, so each case's probability is computed at translation
 * time by USE's own {@code UString} and, again, no arithmetic operator is emitted.
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
    return lower(expression, context, new LinkedHashSet<>(), false, x -> null);
  }

  /**
   * Lowering with U-type LET bindings: {@code resolver} maps an expression that IS a let
   * variable (an {@code ExpVariable}) to its {@link LetBindingSource} -- the bare attribute the
   * let initialized from plus its configured candidate domains -- and null for anything else.
   * A resolved let variable enumerates cases over the SAME configured candidates and guards as
   * the attribute itself (the SMT let makes the alias and the source interchangeable); the
   * alias key keeps the read-once rule per let variable.
   */
  public static List<Case> lower(
      Expression expression,
      TranslationContext context,
      java.util.function.Function<Expression, LetBindingSource> letResolver) {
    return lower(expression, context, new LinkedHashSet<>(), false, letResolver);
  }

  /**
   * The NOMINAL-erasure lowering: identical in shape, but every case's probability is the
   * independent oracle's erased Boolean rather than the source's confidence rule.
   *
   * <p>Only {@code UString} behaves differently between the two modes, and it must. {@code
   * NominalErasureEvaluator} erases {@code UString(s,c)} to the representative spelling {@code s}
   * and then compares CRISPLY, discarding the confidence outright -- so a matching spelling erases
   * to {@code true} at any confidence, including one the uncertainty-aware reading rejects. Reading
   * {@code p >= 0.5} off the confidence-derived probability instead would silently disagree with
   * the oracle: at {@code c = 0.3} with a matching spelling it answers {@code false} where the
   * erasure answers {@code true}. A stored UBoolean's probability, by contrast, IS its own erasure
   * input ({@code p >= 0.5}), so nothing changes for that family.
   */
  public static List<Case> lowerNominal(Expression expression, TranslationContext context) {
    return lower(expression, context, new LinkedHashSet<>(), true, x -> null);
  }

  /** {@link #lowerNominal} with U-type let bindings; see {@link #lower}. */
  public static List<Case> lowerNominal(
      Expression expression,
      TranslationContext context,
      java.util.function.Function<Expression, LetBindingSource> letResolver) {
    return lower(expression, context, new LinkedHashSet<>(), true, letResolver);
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
      Expression expression,
      TranslationContext context,
      Set<String> alreadyRead,
      boolean nominal,
      java.util.function.Function<Expression, LetBindingSource> letResolver) {
    if (expression instanceof ExpVariable v) {
      LetBindingSource source = letResolver.apply(expression);
      if (source != null) {
        return letVariableCases(v, source, context, alreadyRead);
      }
    }
    if (expression instanceof ExpAttrOp attribute) {
      return storedProbability(attribute, context, alreadyRead);
    }
    if (expression instanceof ExpStdOp operation) {
      return operation(operation, context, alreadyRead, nominal, letResolver);
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

/**
   * A let-bound UBOOLEAN variable's cases: the stored probability IS the value, so the cases
   * enumerate the source's configured probability candidates with the guard on the source's own
   * probability symbol -- the SMT let binds the alias to that symbol, so reading the source
   * directly is interchangeable with reading the alias. Nominal erasure needs no special case:
   * a stored probability's erasure is the same p >= 0.5 rule in both modes.
   */
  private static List<Case> letVariableCases(
      ExpVariable variable,
      LetBindingSource source,
      TranslationContext context,
      Set<String> alreadyRead) {
    if (source.secondDomain() != null) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UBoolean composition over the let variable '"
              + variable.getVarname()
              + "' whose source is a UString: a spelling/confidence pair is not a probability");
    }
    if (!alreadyRead.add(source.aliasKey())) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UBoolean composition reads the let variable '"
              + variable.getVarname()
              + "' more than once; the source rules assume INDEPENDENT operands, and USE's own"
              + " UBoolean.and returns p rather than p*p for two reads of one value");
    }
    SmtTerm symbol = Smt.sym(source.values().valueNames().get(source.source().slotIndex()));
    List<Case> cases = new ArrayList<>();
    for (String candidate : source.firstDomain().enumeratedValues()) {
      BigDecimal probability =
          parse(candidate, source.values().className(), source.aliasKey());
      cases.add(new Case(Smt.eq(symbol, Smt.realLit(probability)), probability.doubleValue()));
    }
    return cases;
  }

  /**
   * A let-bound USTRING variable's candidates: the source's configured spellings crossed with
   * its configured confidences (spelling-only under nominal erasure), guarded on the source's
   * own symbols -- the SMT let makes that interchangeable with reading the alias.
   */
  private static Side letStringSide(
      ExpVariable variable,
      LetBindingSource source,
      TranslationContext context,
      Set<String> alreadyRead,
      boolean nominal) {
    if (!alreadyRead.add(source.aliasKey())) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UString comparison reads the let variable '"
              + variable.getVarname()
              + "' more than once; USE's own UString.uEquals returns certainty rather than"
              + " c_s * c_r for two references to one value, and the source's rules assume"
              + " INDEPENDENT operands");
    }
    SmtTerm spellingSymbol =
        Smt.sym(source.values().valueNames().get(source.source().slotIndex()));
    List<Candidate> candidates = new ArrayList<>();
    if (nominal) {
      List<String> spellings = source.firstDomain().enumeratedValues();
      for (int i = 0; i < spellings.size(); i++) {
        candidates.add(
            new Candidate(
                spellings.get(i),
                Double.NaN,
                List.of(Smt.eq(spellingSymbol, Smt.intLit(java.math.BigInteger.valueOf(i))))));
      }
      return new Side(candidates);
    }
    SmtTerm confidenceSymbol =
        Smt.sym(source.values().confidenceNames().get(source.source().slotIndex()));
    for (int i = 0; i < source.firstDomain().enumeratedValues().size(); i++) {
      for (String candidate : source.secondDomain().enumeratedValues()) {
        BigDecimal confidence = parse(candidate, source.values().className(), source.aliasKey());
        candidates.add(
            new Candidate(
                source.firstDomain().enumeratedValues().get(i),
                confidence.doubleValue(),
                List.of(
                    Smt.eq(spellingSymbol, Smt.intLit(java.math.BigInteger.valueOf(i))),
                    Smt.eq(confidenceSymbol, Smt.realLit(confidence)))));
      }
    }
    return new Side(candidates);
  }

  private static List<Case> operation(
      ExpStdOp operation,
      TranslationContext context,
      Set<String> alreadyRead,
      boolean nominal,
      java.util.function.Function<Expression, LetBindingSource> letResolver) {
    Expression[] args = operation.args();
    String name = operation.opname();
    if (isUStringComparison(args)) {
      return uStringComparison(operation, context, alreadyRead, nominal, letResolver);
    }
    if ("not".equals(name) && args.length == 1) {
      List<Case> operand = lower(args[0], context, alreadyRead, nominal, letResolver);
      List<Case> negated = new ArrayList<>(operand.size());
      for (Case candidate : operand) {
        negated.add(new Case(candidate.guard(), complement(candidate.probability())));
      }
      return negated;
    }
    if (List.of("and", "or", "implies").contains(name) && args.length == 2) {
      List<Case> left = lower(args[0], context, alreadyRead, nominal, letResolver);
      List<Case> right = lower(args[1], context, alreadyRead, nominal, letResolver);
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

  /**
   * True when either operand of a binary operation is {@code UString}-typed, which is what routes
   * an expression into {@link #uStringComparison}. Deliberately typed on the OPERANDS, not the
   * operator: every UString shape USE lets into an invariant arrives as some binary operation over
   * a UString-typed operand, and routing on the operand is what makes the unsupported ones fail
   * closed HERE, with a UString-specific reason, instead of falling through to the
   * numeric-comparison refusal and being misreported as a nonlinearity.
   */
  private static boolean isUStringComparison(Expression[] args) {
    return args.length == 2
        && (args[0].type().isTypeOfUString() || args[1].type().isTypeOfUString());
  }

  /**
   * Lowers {@code <UString> = <UString|exact string>} and its negation to cases.
   *
   * <p>The rules are the source's, computed by USE's own {@code UString.uEquals} rather than
   * reimplemented. Against an exact string the other operand is lifted to confidence {@code 1.0},
   * which is exactly what {@code UStringValue.valueOf(StringValue)} does, so the exact-string rule
   * ({@code c_s} on a match, {@code 1 - c_s} otherwise) is the {@code c_r = 1} special case of the
   * two-UString rule ({@code b = c_s c_r} on a match, {@code 1 - b} otherwise) rather than a second
   * rule. The {@code 1 - x} half is taken from the evaluator too: {@code uEquals} builds {@code new
   * UBoolean(false, b)} and the constructor's {@code setNormalForm} is what turns it into {@code 1
   * - b}, at double precision -- {@code 1 - 0.7} is {@code 0.30000000000000004}, and that is the
   * number the encoding must carry, not {@code 0.3}.
   *
   * <p>{@code <>} is supported because USE DERIVES it: {@code UncertainValue.uDistinct} is
   * literally {@code uEquals(other).not()}, and {@code not(p) = 1 - p} is one of the four rules the
   * proposal gives. Nothing is assumed that the source does not state.
   */
  private static List<Case> uStringComparison(
      ExpStdOp operation,
      TranslationContext context,
      Set<String> alreadyRead,
      boolean nominal,
      java.util.function.Function<Expression, LetBindingSource> letResolver) {
    String name = operation.opname();
    if (!"=".equals(name) && !"<>".equals(name)) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNRESTRICTED_STRING,
          "UString operation '"
              + name
              + "' in '"
              + operation
              + "': the source scopes UString to equality and inequality against an exact string"
              + " and between two configured UStrings, and puts unrestricted string-operation"
              + " chains outside version 1");
    }
    Expression[] args = operation.args();
    Side left = side(args[0], context, alreadyRead, nominal, letResolver);
    Side right = side(args[1], context, alreadyRead, nominal, letResolver);
    long combinations = (long) left.candidates().size() * right.candidates().size();
    if (combinations > MAX_CASES) {
      throw unsupported(
          FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL,
          "UString '"
              + name
              + "' over "
              + left.candidates().size()
              + " x "
              + right.candidates().size()
              + " configured choices exceeds the "
              + MAX_CASES
              + "-combination expansion cap; the finite enumeration is what keeps the c_s * c_r"
              + " rule inside QF_LIRA, so it is refused rather than emitted symbolically");
    }
    List<Case> cases = new ArrayList<>();
    for (Candidate l : left.candidates()) {
      for (Candidate r : right.candidates()) {
        double probability = uStringProbability(name, l, r, nominal);
        List<SmtTerm> guards = new ArrayList<>();
        guards.addAll(l.guards());
        guards.addAll(r.guards());
        cases.add(new Case(guards.isEmpty() ? Smt.bool(true) : Smt.and(guards), probability));
      }
    }
    return cases;
  }

  /**
   * The equality probability of one combination.
   *
   * <p>In {@code UNCERTAIN} mode this is USE's own {@code UString} arithmetic. In {@code NOMINAL}
   * mode it is the independent oracle's erasure instead -- {@code NominalErasureEvaluator} reduces
   * {@code UString(s,c)} to the spelling {@code s} and compares crisply -- expressed as a {@code
   * 1.0}/{@code 0.0} probability so that {@link #select} at {@code 0.5} reproduces it exactly. The
   * spelling relation is the same one USE uses ({@code UString.uEquals} decides {@code
   * this.string.compareTo(u.string) == 0}), so the two paths differ only in whether the confidence
   * is consulted at all.
   */
  private static double uStringProbability(
      String operator, Candidate left, Candidate right, boolean nominal) {
    if (nominal) {
      boolean same = left.spelling().equals(right.spelling());
      boolean holds = "<>".equals(operator) != same;
      return holds ? 1.0 : 0.0;
    }
    UBoolean equality =
        new UString(left.spelling(), left.confidence())
            .uEquals(new UString(right.spelling(), right.confidence()));
    return "<>".equals(operator) ? equality.not().getC() : equality.getC();
  }

  /**
   * One configured (spelling, confidence) choice of one operand, with the guards that select it.
   */
  private record Candidate(String spelling, double confidence, List<SmtTerm> guards) {}

  /** All the choices one operand of a UString comparison can take. */
  private record Side(List<Candidate> candidates) {}

  /**
   * The choices one operand offers.
   *
   * <p>An exact string literal offers exactly one, with confidence {@code 1.0} and no guard: it is
   * not something the solver chooses. A stored UString attribute offers the cross product of its
   * configured spellings and confidences -- except under {@code NOMINAL}, where the confidence is
   * not read at all, so only the spellings are enumerated and no confidence symbol appears in the
   * emitted guard. Anything else fails closed.
   */
  private static Side side(
      Expression expression,
      TranslationContext context,
      Set<String> alreadyRead,
      boolean nominal,
      java.util.function.Function<Expression, LetBindingSource> letResolver) {
    if (expression instanceof ExpVariable v) {
      LetBindingSource source = letResolver.apply(expression);
      if (source != null) {
        return letStringSide(v, source, context, alreadyRead, nominal);
      }
    }
    if (expression instanceof ExpConstString literal) {
      // UStringValue.valueOf(StringValue) constructs new UStringValue(value, 1), so an exact string
      // enters the rule as a certain UString. This is not a convention chosen here.
      return new Side(List.of(new Candidate(literal.value(), 1.0, List.of())));
    }
    if (expression instanceof ExpConstUString) {
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UString literal operand '"
              + expression
              + "': this slice encodes stored UString attributes against configured spellings, and"
              + " a literal U-value is refused here exactly as it is everywhere else");
    }
    if (!(expression instanceof ExpAttrOp attribute)) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNRESTRICTED_STRING,
          "UString operand '"
              + expression
              + "' ("
              + expression.getClass().getSimpleName()
              + ") is neither a stored UString attribute nor an exact string literal; the source"
              + " puts unrestricted string-operation chains outside version 1");
    }
    VariableBinding binding = context.binding(variableNameOf(attribute.objExp()));
    String attributeName = attribute.attr().name();
    AttributeValues values = context.attributeValues(binding.className(), attributeName);
    if (values.type() != AttributeType.USTRING) {
      throw unsupported(
          FragmentBoundary.ENCODING_SCOPE,
          "no UString spelling/confidence symbols registered for "
              + binding.className()
              + "."
              + attributeName);
    }
    String slot = binding.className() + "#" + binding.slotIndex() + "." + attributeName;
    if (!alreadyRead.add(slot)) {
      // Measured, not assumed: UString.uEquals is `double conf = (this == u) ? 1.0 :
      // calculateConf(u)`, so two reads of one slot answer with CERTAINTY rather than with c * c --
      // 1.0 against 0.49 at c = 0.7. The source's rules are written for independent operands, so an
      // aliased comparison is outside them and is refused rather than encoded to either reading.
      throw unsupported(
          FragmentBoundary.UTYPE_CORE,
          "UString comparison reads "
              + slot
              + " more than once; USE's own UString.uEquals returns certainty rather than c_s * c_r"
              + " for two references to one value, and the source's rules assume INDEPENDENT"
              + " operands");
    }
    AttributeDomain spellings =
        context.attributeDomain(binding.className(), attributeName, "value");
    if (spellings.enumeratedValues().isEmpty()) {
      throw unsupported(
          FragmentBoundary.UTYPE_UNRESTRICTED_STRING,
          "UString attribute '"
              + binding.className()
              + "."
              + attributeName
              + "' has no finite configured spelling domain; the source's default bounded encoding"
              + " turns configured spellings into a finite enumeration, and an unrestricted string"
              + " is outside the supported fragment");
    }
    SmtTerm spellingSymbol = Smt.sym(values.valueNames().get(binding.slotIndex()));
    List<Candidate> candidates = new ArrayList<>();
    if (nominal) {
      for (int i = 0; i < spellings.enumeratedValues().size(); i++) {
        candidates.add(
            new Candidate(
                spellings.enumeratedValues().get(i),
                Double.NaN,
                List.of(Smt.eq(spellingSymbol, Smt.intLit(java.math.BigInteger.valueOf(i))))));
      }
      return new Side(candidates);
    }
    AttributeDomain confidences =
        context.attributeDomain(binding.className(), attributeName, "confidence");
    if (confidences.enumeratedValues().isEmpty()) {
      throw unsupported(
          FragmentBoundary.UTYPE_NONLINEAR_OR_TRANSCENDENTAL,
          "UString attribute '"
              + binding.className()
              + "."
              + attributeName
              + "' has no finite configured confidence domain; the source's c_s * c_r rule is a"
              + " product of confidences, and only a finite configured choice lets it be evaluated"
              + " at translation time instead of emitted as nonlinear SMT");
    }
    SmtTerm confidenceSymbol = Smt.sym(values.confidenceNames().get(binding.slotIndex()));
    for (int i = 0; i < spellings.enumeratedValues().size(); i++) {
      for (String candidate : confidences.enumeratedValues()) {
        BigDecimal confidence = parse(candidate, binding.className(), attributeName);
        candidates.add(
            new Candidate(
                spellings.enumeratedValues().get(i),
                confidence.doubleValue(),
                List.of(
                    Smt.eq(spellingSymbol, Smt.intLit(java.math.BigInteger.valueOf(i))),
                    Smt.eq(confidenceSymbol, Smt.realLit(confidence)))));
      }
    }
    return new Side(candidates);
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
          "probability/confidence candidate '"
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
