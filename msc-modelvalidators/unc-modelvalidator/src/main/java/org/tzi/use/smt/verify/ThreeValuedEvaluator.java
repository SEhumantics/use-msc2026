package org.tzi.use.smt.verify;

import java.util.List;
import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.ExpExists;
import org.tzi.use.uml.ocl.expr.ExpForAll;
import org.tzi.use.uml.ocl.expr.ExpIf;
import org.tzi.use.uml.ocl.expr.ExpLet;
import org.tzi.use.uml.ocl.expr.ExpObjOp;
import org.tzi.use.uml.ocl.expr.ExpOne;
import org.tzi.use.uml.ocl.expr.ExpQuery;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.VarDeclList;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.InstanceValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MInstance;

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
 * <p>{@code isDefined()}/{@code isUndefined()} (the latter also reachable as {@code
 * oclIsUndefined()}, {@code Op_isUndefined}'s own registered alias -- {@code op.opname()} always
 * reports the canonical {@code "isUndefined"} regardless of which spelling parsed it) are walked
 * ONLY when their single argument is Boolean-typed, the same guard as {@code =}/{@code <>}: the
 * result is ALWAYS defined ({@code Op_isDefined}/{@code Op_isUndefined} are both kind {@code
 * SPECIAL} and never propagate their argument's undefinedness as their OWN, they report it as a
 * value, exactly matching {@code ExpressionTranslator}'s {@code case "isDefined" ->
 * defined(definednessOf(a[0]))} / {@code case "isUndefined" -> defined(Smt.not(definednessOf(a[0])))}),
 * but a Boolean-typed argument that is itself a nested quantifier must be read through {@link
 * #eval} first or {@code ExpStdOp#eval}'s own raw {@code fArgs[i].eval(ctx)} -- called on every
 * argument BEFORE {@code Op_isDefined}/{@code Op_isUndefined} ever run -- collapses it the same
 * way.
 *
 * <p>{@code range->one(v | body)} is walked as {@link #one}: an undefined RANGE stays undefined
 * (matching {@code ExpOne#eval}'s own already-correct handling), but each population element's
 * BODY is read through {@link #eval} instead of raw {@code Expression.eval}, so a nested
 * quantifier reachable only through an enclosing connective in the body -- not just a body that
 * IS bare a {@code forAll}/{@code exists} -- is read correctly. The per-element MATCH rule stays
 * {@code ExpOne#evalAux}'s own documented one ("undefined query values default to false"),
 * independently confirmed by {@code ExpressionTranslator.visitOne}'s identical total encoding:
 * {@code one} is never itself undefined, an undefined body element simply does not count toward
 * the "found == 1" tally.
 *
 * <p>{@code let <var> : <type> = <varExpr> in <inExpr>} is walked as {@link #let}: the SAME one
 * binding {@code ExpLet#eval} pushes and pops, but a Boolean-typed {@code varExpr} is ALSO read
 * through {@link #eval} (converted back to a {@code Value} before binding) rather than only the
 * {@code inExpr} body -- otherwise a {@code let} nested INSIDE another {@code let}'s Boolean
 * initializer (confirmed live in this project's own benchmark corpus, Genealogy's {@code let B =
 * let P = Person.allInstances() in ... P->one(...) or ... in (A implies B) and (B implies A)})
 * would still raw-evaluate that inner initializer via {@code ExpLet#eval}, undoing the fix the
 * moment the outer variable is referenced. A non-Boolean {@code varExpr} keeps raw {@code
 * Expression.eval}, unchanged -- it cannot itself be read as an {@link InvariantOutcome}.
 *
 * <p>{@code if <cond> then <a> else <b> endif} is walked as {@link #ifThenElse}, matching {@code
 * ExpIf#eval}'s ACTUAL code rather than its docstring (the two disagree; {@code
 * ExpressionTranslator.visitIf} documents and confirms the same discrepancy independently): an
 * undefined condition makes the WHOLE if-expression undefined without ever touching either
 * branch, it does NOT fall through to the else branch. The condition and whichever branch is
 * selected are all read through {@link #eval} instead of raw {@code Expression.eval}, so a nested
 * quantifier in any of the three positions is properly three-valued.
 *
 * <p>{@code self.op()} for a zero-or-more-argument query operation is walked as {@link
 * #objectOperationCall}, matching {@code ExpObjOp#eval}'s value-computing logic exactly: the same
 * undefined-receiver and destroyed-object guards, the same {@code @pre} context swap, the same
 * dynamic dispatch through the RECEIVER's own class ({@code self.cls().operation(...)}, so a
 * redefined override's body is read rather than the statically-resolved one the AST node
 * carries), and the same parameter-binding order (arguments evaluated before any binding is
 * pushed, so a parameter name cannot shadow a value a later argument still needs). Only the
 * operation's body is read through {@link #eval} instead of raw {@code
 * operation.expression().eval(ctx)}; its (non-Boolean, arbitrarily-typed) arguments keep raw
 * {@code Expression.eval}, carrying no risk of this collapse themselves. Boundary, stated rather
 * than hidden: {@code MSystem.enterQueryOperation}/{@code exitQueryOperation}'s call-stack
 * bookkeeping and {@code pre:}/{@code post:} contract assertions are NOT replicated -- a wholly
 * separate concern from the definedness-collapse bug this class exists to fix, unreachable from
 * every invariant in this project's model corpus (confirmed by the regression coverage this
 * change ships with). A query operation whose OWN contract is violated in the state under test
 * reads as an ordinary three-valued outcome here instead of throwing, unlike {@code
 * ExpObjOp#eval}.
 *
 * <p>Boundary, stated rather than hidden: the collection-filtering constructs USE also collapses
 * undefined inside ({@code select}/{@code reject}, {@code any}, {@code uSelect}) are NOT walked
 * here. None of them is in the supported SMT translation fragment -- {@code ExpressionTranslator}
 * fails closed on every one of them -- so no witness this oracle checks can contain one in a
 * translated invariant. ({@code one} USED to belong on this list too; it does not any more --
 * {@code ExpressionTranslator.visitOne} genuinely translates it, so it is walked above instead.)
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
    if (expression instanceof ExpOne oneExpr) {
      return one(oneExpr, ctx);
    }
    if (expression instanceof ExpLet letExpr) {
      return let(letExpr, ctx);
    }
    if (expression instanceof ExpIf ifExpr) {
      return ifThenElse(ifExpr, ctx);
    }
    if (expression instanceof ExpObjOp call) {
      return objectOperationCall(call, ctx);
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
        case "isDefined":
          if (args[0].type().isTypeOfBoolean()) {
            return definite(eval(args[0], ctx) != InvariantOutcome.UNDEFINED);
          }
          break;
        case "isUndefined":
          if (args[0].type().isTypeOfBoolean()) {
            return definite(eval(args[0], ctx) == InvariantOutcome.UNDEFINED);
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

  /**
   * {@code range->one(v | body)}. See the class doc for the full rationale; in short, an undefined
   * range stays undefined (matching {@code ExpOne#eval}'s own already-correct handling), and each
   * population element's body is read through {@link #eval} before applying {@code
   * ExpOne#evalAux}'s own "undefined defaults to false" match rule.
   */
  private static InvariantOutcome one(ExpOne oneExpr, EvalContext ctx) {
    Value range = oneExpr.getRangeExpression().eval(ctx);
    if (range.isUndefined()) {
      return InvariantOutcome.UNDEFINED;
    }
    if (!(range instanceof CollectionValue collection)) {
      throw new IllegalStateException("`one` range did not evaluate to a collection: " + range);
    }
    int found =
        countMatches(
            collection, oneExpr.getVariableDeclarations(), 0, oneExpr.getQueryExpression(), ctx);
    return definite(found == 1);
  }

  /**
   * The same multi-variable permutation recursion {@link #overElements} uses for {@code
   * forAll}/{@code exists}, counting matches instead of short-circuiting on a decisive value: an
   * undefined body outcome does not count as a match (mirrors {@code ExpOne#evalAux}'s {@code if
   * (queryVal.isUndefined()) queryVal = BooleanValue.FALSE;}), a true outcome does. Stops early
   * once more than one match is found -- "found == 1" is already decided false, and unlike {@code
   * ExpOne#evalAux} this oracle never drives USE's eval-tree debug view, so there is no reason to
   * keep counting past that point.
   */
  private static int countMatches(
      CollectionValue range, VarDeclList variables, int nesting, Expression body, EvalContext ctx) {
    boolean bound = !variables.isEmpty();
    int found = 0;
    for (Value element : range) {
      if (bound) {
        ctx.pushVarBinding(variables.varDecl(nesting).name(), element);
      }
      try {
        if (bound && nesting < variables.size() - 1) {
          found += countMatches(range, variables, nesting + 1, body, ctx);
        } else if (eval(body, ctx) == InvariantOutcome.TRUE) {
          found++;
        }
      } finally {
        if (bound) {
          // See overElements: EvalContext.popVarBinding() is package-private in use-core.
          ctx.varBindings().pop();
        }
      }
      if (found > 1) {
        break;
      }
    }
    return found;
  }

  /**
   * {@code let <var> : <type> = <varExpr> in <inExpr>}. See the class doc for the full rationale;
   * in short, the SAME one binding {@code ExpLet#eval} pushes and pops, but a Boolean-typed {@code
   * varExpr} is ALSO read through {@link #eval} (converted back to a {@code Value}) rather than
   * only the {@code inExpr} body, so a nested {@code let} bound to a Boolean initializer stays
   * fixed too.
   */
  private static InvariantOutcome let(ExpLet letExpr, EvalContext ctx) {
    Expression varExpr = letExpr.getVarExpression();
    Value boundValue =
        varExpr.type().isTypeOfBoolean() ? valueOf(eval(varExpr, ctx)) : varExpr.eval(ctx);
    ctx.pushVarBinding(letExpr.getVarname(), boundValue);
    try {
      return eval(letExpr.getInExpression(), ctx);
    } finally {
      // See overElements: EvalContext.popVarBinding() is package-private in use-core.
      ctx.varBindings().pop();
    }
  }

  private static Value valueOf(InvariantOutcome outcome) {
    return switch (outcome) {
      case TRUE -> BooleanValue.TRUE;
      case FALSE -> BooleanValue.FALSE;
      case UNDEFINED -> UndefinedValue.instance;
    };
  }

  /**
   * {@code if <cond> then <a> else <b> endif}. See the class doc for the full rationale; in short,
   * this matches {@code ExpIf#eval}'s ACTUAL code (an undefined condition makes the whole
   * expression undefined without evaluating either branch) rather than its docstring, and reads
   * the condition and whichever branch is selected all through {@link #eval}.
   */
  private static InvariantOutcome ifThenElse(ExpIf ifExpr, EvalContext ctx) {
    return switch (eval(ifExpr.getCondition(), ctx)) {
      case TRUE -> eval(ifExpr.getThenExpression(), ctx);
      case FALSE -> eval(ifExpr.getElseExpression(), ctx);
      case UNDEFINED -> InvariantOutcome.UNDEFINED;
    };
  }

  /**
   * {@code self.op(...)}. See the class doc for the full rationale and the stated call-stack/
   * contract-checking boundary; in short, this matches {@code ExpObjOp#eval}'s value-computing
   * logic (undefined-receiver/destroyed-object guards, the {@code @pre} context swap, dynamic
   * dispatch through the receiver's own class, and argument-before-binding evaluation order)
   * exactly, but reads the resolved operation's body through {@link #eval} instead of raw {@code
   * operation.expression().eval(ctx)}.
   */
  private static InvariantOutcome objectOperationCall(ExpObjOp call, EvalContext ctx) {
    EvalContext callCtx =
        call.isPre()
            ? new EvalContext(ctx.preState(), ctx.preState(), ctx.varBindings(), ctx)
            : ctx;
    Expression[] args = call.getArguments();
    Value selfValue = args[0].eval(callCtx);
    if (selfValue.isUndefined() || !(selfValue instanceof InstanceValue instanceValue)) {
      return InvariantOutcome.UNDEFINED;
    }
    MInstance self = instanceValue.value();
    if (selfValue instanceof ObjectValue
        && ((call.isPre() && self.state(callCtx.preState()) == null)
            || (!call.isPre() && self.state(callCtx.postState()) == null))) {
      return InvariantOutcome.UNDEFINED;
    }
    MOperation operation = self.cls().operation(call.getOperation().name(), true);
    if (!operation.isCallableFromOCL()) {
      throw new IllegalStateException("Cannot call operation " + operation);
    }
    List<String> parameterNames = operation.paramNames();
    Value[] arguments = new Value[parameterNames.size()];
    for (int i = 1; i < args.length; i++) {
      arguments[i - 1] = args[i].eval(callCtx);
    }
    callCtx.pushVarBinding("self", selfValue);
    for (int i = 0; i < parameterNames.size(); i++) {
      callCtx.pushVarBinding(parameterNames.get(i), arguments[i]);
    }
    try {
      return operation.hasExpression()
          ? eval(operation.expression(), callCtx)
          : InvariantOutcome.UNDEFINED;
    } finally {
      // See overElements: EvalContext.popVarBinding() is package-private in use-core.
      for (int i = 0; i < parameterNames.size() + 1; i++) {
        callCtx.varBindings().pop();
      }
    }
  }

  private static InvariantOutcome definite(boolean value) {
    return value ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
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
