package org.tzi.use.smt.verify;

import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.ExpExists;
import org.tzi.use.uml.ocl.expr.ExpForAll;
import org.tzi.use.uml.ocl.expr.ExpQuery;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.VarDeclList;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * Reads a Boolean OCL expression as one of the three mutually exclusive outcomes
 * THESIS_SMT_MODEL_FINDER_PLAN s5.1 fixes, at EVERY quantifier depth.
 *
 * <p>USE's own evaluator cannot answer this question, and not only at the top level: {@code
 * ExpQuery.evalForAll0} and {@code evalExists0} both rewrite an undefined body element to {@code
 * BooleanValue.FALSE} ({@code if (queryVal.isUndefined()) queryVal = BooleanValue.FALSE;}) before
 * combining it. Handing a whole invariant body to {@code Expression.eval} therefore turns an
 * UNDEFINED nested {@code forAll}/{@code exists} into a bogus DEFINED-FALSE -- which is exactly
 * what {@link QueryWitnessChecker} would accept as a valid counterexample attribution. Refusing
 * such bodies is not an option either: all three of Library's key invariants have {@code forAll}
 * bodies, so the milestone's own parity evidence runs through this path.
 *
 * <p>So the quantifiers and the Boolean connectives are walked here, and everything else is
 * delegated to USE unchanged. The connective rules are USE's own, verified by executing the real
 * evaluator over all 3x3 operand combinations of {@code and}, {@code or} and {@code implies} plus
 * all three of {@code not}: they are exactly strong Kleene logic ({@code false and undef = false},
 * {@code true or undef = true}, {@code false implies undef = true}, {@code undef implies false =
 * undef}). The quantifier rules are the same three-valued rule {@link
 * org.tzi.use.smt.encode.InvariantAssembler#classify} and {@code
 * ExpressionTranslator.visitForAll}/{@code visitExists} encode into SMT, so the oracle and the
 * encoder agree by construction rather than by coincidence: for {@code forAll}, a defined-false
 * element makes the whole quantifier defined-false, otherwise any undefined element makes it
 * undefined, otherwise it is true (vacuously true over an empty range); for {@code exists}, a
 * defined-true element makes it defined-true, otherwise any undefined element makes it undefined,
 * otherwise it is false (and OCL's {@code exists} over an empty range is FALSE, not vacuously
 * true).
 *
 * <p>{@code xor} is walked too, matching {@code ExpressionTranslator.booleanXor}'s rule: unlike
 * {@code and}/{@code or} it has no ABSORBING value, so it is undefined if EITHER operand is
 * undefined, otherwise the ordinary boolean xor of the two definite values. Without its own case
 * here, an {@code xor} node used to fall to {@code expression.eval(ctx)} -- USE's raw evaluator --
 * whose {@code Op_boolean_xor.evalWithArgs} calls {@code args[i].eval(ctx)} directly on each
 * operand, hitting the exact same {@code evalForAll0}/{@code evalExists0} collapse for a nested
 * quantifier operand that motivated this class in the first place.
 *
 * <p>{@code =}/{@code <>} are walked ONLY when BOTH operands are Boolean-typed, matching {@code
 * ExpressionTranslator}'s {@code useEquality} rule (in turn USE's own {@code Op_equal.eval}, kind
 * {@code SPECIAL}): the comparison is ALWAYS defined, true exactly when both operands are
 * undefined or both are defined and equal. A Boolean-typed operand can itself be a nested
 * quantifier or connective, so the same raw-{@code eval} collapse above is reachable through
 * {@code =}/{@code <>} too; non-Boolean operands (Integer, String, object identity, and so on)
 * carry no such risk and keep falling through to {@code expression.eval(ctx)} unchanged --
 * {@code Op_equal.eval}'s own undefined handling for those types is not reimplemented here.
 *
 * <p>Boundary, stated rather than hidden: the collection-filtering constructs USE also collapses
 * undefined inside ({@code select}/{@code reject}, {@code any}, {@code one}, {@code uSelect}) are
 * NOT walked here. None of them is in the supported SMT translation fragment -- {@code
 * ExpressionTranslator} fails closed on every one of them -- so no witness this oracle checks can
 * contain one in a translated invariant.
 */
final class ThreeValuedEvaluator {
  private ThreeValuedEvaluator() {}

  static InvariantOutcome eval(Expression expression, EvalContext ctx) {
    if (expression instanceof ExpForAll forAll) {
      return quantify(forAll, false, ctx);
    }
    if (expression instanceof ExpExists exists) {
      return quantify(exists, true, ctx);
    }
    if (expression instanceof ExpStdOp op) {
      Expression[] args = op.args();
      switch (op.opname()) {
        case "and":
          return and(eval(args[0], ctx), eval(args[1], ctx));
        case "or":
          return or(eval(args[0], ctx), eval(args[1], ctx));
        case "not":
          return not(eval(args[0], ctx));
        case "implies":
          return or(not(eval(args[0], ctx)), eval(args[1], ctx));
        case "xor":
          return xor(eval(args[0], ctx), eval(args[1], ctx));
        case "=":
          if (args[0].type().isTypeOfBoolean() && args[1].type().isTypeOfBoolean()) {
            return equalsOutcome(eval(args[0], ctx), eval(args[1], ctx));
          }
          break;
        case "<>":
          if (args[0].type().isTypeOfBoolean() && args[1].type().isTypeOfBoolean()) {
            return not(equalsOutcome(eval(args[0], ctx), eval(args[1], ctx)));
          }
          break;
        default:
          break;
      }
    }
    return outcomeOf(expression, expression.eval(ctx));
  }

  private static InvariantOutcome quantify(ExpQuery query, boolean existential, EvalContext ctx) {
    Value range = query.getRangeExpression().eval(ctx);
    if (range.isUndefined()) {
      // USE's own evalExistsOrForAll returns undefined for an undefined range, before any element
      // is ever visited; that part it already gets right.
      return InvariantOutcome.UNDEFINED;
    }
    if (!(range instanceof CollectionValue collection)) {
      throw new IllegalStateException(
          "quantifier range did not evaluate to a collection: " + range);
    }
    return overElements(
        collection,
        query.getVariableDeclarations(),
        0,
        query.getQueryExpression(),
        existential,
        ctx);
  }

  /**
   * The permutation recursion USE's own {@code evalForAll0}/{@code evalExists0} use for a
   * multi-variable quantifier: every variable ranges over the SAME collection.
   */
  private static InvariantOutcome overElements(
      CollectionValue range,
      VarDeclList variables,
      int nesting,
      Expression body,
      boolean existential,
      EvalContext ctx) {
    InvariantOutcome decisive = existential ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
    boolean bound = !variables.isEmpty();
    boolean anyUndefined = false;
    for (Value element : range) {
      if (bound) {
        ctx.pushVarBinding(variables.varDecl(nesting).name(), element);
      }
      InvariantOutcome outcome;
      try {
        outcome =
            bound && nesting < variables.size() - 1
                ? overElements(range, variables, nesting + 1, body, existential, ctx)
                : eval(body, ctx);
      } finally {
        if (bound) {
          // EvalContext.popVarBinding() is package-private and use-core is not ours to
          // change; varBindings() hands back the very same VarBindings it would pop.
          ctx.varBindings().pop();
        }
      }
      if (outcome == decisive) {
        return decisive;
      }
      anyUndefined |= outcome == InvariantOutcome.UNDEFINED;
    }
    if (anyUndefined) {
      return InvariantOutcome.UNDEFINED;
    }
    return existential ? InvariantOutcome.FALSE : InvariantOutcome.TRUE;
  }

  private static InvariantOutcome and(InvariantOutcome left, InvariantOutcome right) {
    if (left == InvariantOutcome.FALSE || right == InvariantOutcome.FALSE) {
      return InvariantOutcome.FALSE;
    }
    return left == InvariantOutcome.UNDEFINED || right == InvariantOutcome.UNDEFINED
        ? InvariantOutcome.UNDEFINED
        : InvariantOutcome.TRUE;
  }

  private static InvariantOutcome or(InvariantOutcome left, InvariantOutcome right) {
    if (left == InvariantOutcome.TRUE || right == InvariantOutcome.TRUE) {
      return InvariantOutcome.TRUE;
    }
    return left == InvariantOutcome.UNDEFINED || right == InvariantOutcome.UNDEFINED
        ? InvariantOutcome.UNDEFINED
        : InvariantOutcome.FALSE;
  }

  private static InvariantOutcome not(InvariantOutcome operand) {
    return switch (operand) {
      case TRUE -> InvariantOutcome.FALSE;
      case FALSE -> InvariantOutcome.TRUE;
      case UNDEFINED -> InvariantOutcome.UNDEFINED;
    };
  }

  /**
   * {@code xor}, matching {@code ExpressionTranslator.booleanXor}'s documented rule exactly:
   * unlike {@code and}/{@code or} there is no ABSORBING value, so the result is undefined if
   * EITHER operand is undefined; otherwise it is the ordinary boolean xor of the two definite
   * values.
   */
  private static InvariantOutcome xor(InvariantOutcome left, InvariantOutcome right) {
    if (left == InvariantOutcome.UNDEFINED || right == InvariantOutcome.UNDEFINED) {
      return InvariantOutcome.UNDEFINED;
    }
    return left == right ? InvariantOutcome.FALSE : InvariantOutcome.TRUE;
  }

  /**
   * {@code =} between two Boolean-typed operands, matching {@code ExpressionTranslator}'s {@code
   * useEquality} rule (in turn USE's own {@code Op_equal.eval}, kind {@code SPECIAL}): ALWAYS
   * defined, true exactly when both operands are undefined or both are defined and equal. Unlike
   * {@code and}/{@code or}/{@code xor}, an undefined operand never makes the comparison itself
   * undefined -- it can only make it FALSE (a defined value never equals an undefined one).
   */
  private static InvariantOutcome equalsOutcome(InvariantOutcome left, InvariantOutcome right) {
    return left == right ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
  }

  private static InvariantOutcome outcomeOf(Expression expression, Value result) {
    if (result.isUndefined()) {
      return InvariantOutcome.UNDEFINED;
    }
    if (!(result instanceof BooleanValue bool)) {
      throw new IllegalStateException(
          "expression '" + expression + "' evaluated to a non-Boolean result: " + result);
    }
    return bool.isTrue() ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
  }
}
