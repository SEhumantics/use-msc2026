package org.tzi.use.smt.verify;

import org.tzi.use.smt.config.InvariantOutcome;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.ExpAllInstances;
import org.tzi.use.uml.ocl.expr.ExpAttrOp;
import org.tzi.use.uml.ocl.expr.ExpConstBoolean;
import org.tzi.use.uml.ocl.expr.ExpConstEnum;
import org.tzi.use.uml.ocl.expr.ExpConstInteger;
import org.tzi.use.uml.ocl.expr.ExpConstReal;
import org.tzi.use.uml.ocl.expr.ExpConstString;
import org.tzi.use.uml.ocl.expr.ExpExists;
import org.tzi.use.uml.ocl.expr.ExpForAll;
import org.tzi.use.uml.ocl.expr.ExpIsKindOf;
import org.tzi.use.uml.ocl.expr.ExpIsTypeOf;
import org.tzi.use.uml.ocl.expr.ExpNavigation;
import org.tzi.use.uml.ocl.expr.ExpObjAsSet;
import org.tzi.use.uml.ocl.expr.ExpObjRef;
import org.tzi.use.uml.ocl.expr.ExpQuery;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.ExpUndefined;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.VarDeclList;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UBooleanValue;
import org.tzi.use.uml.ocl.value.UIntegerValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.UStringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;

/**
 * The independent NOMINAL-ERASURE oracle: reads an invariant body over a reconstructed snapshot as
 * one of the three mutually exclusive outcomes {@code T_N}/{@code F_N}/{@code X_N}, with every
 * uncertainty discarded first. This is the second half of the pair {@link QueryWitnessChecker}
 * needs before a {@code fragile(j)} witness may be delivered; {@link ThreeValuedEvaluator} is the
 * U-aware half.
 *
 * <p>Erasure is defined over EXPRESSIONS, not only over stored values ({@code
 * archive2/robust_utype_model_finding_proposal.md}, "What FRAGILE means"). The stored-value table
 * is:
 *
 * <pre>
 *   UReal(mu, sigma)             -&gt; mu
 *   UInteger(n, sigma)           -&gt; n
 *   UString(s, c)                -&gt; the representative spelling s
 *   UBoolean with probability p  -&gt; true iff p &gt;= 0.5
 * </pre>
 *
 * <p>and the confidence projection is REMOVED after erasing its UBoolean operand:
 *
 * <pre>
 *   E( b.toBooleanC(theta) ) = E_B(b)
 * </pre>
 *
 * <p>where {@code E_B} turns a UBoolean expression into its nominal Boolean: <b>numeric and string
 * comparisons become their CRISP comparisons</b>, while a STORED UBoolean probability uses the
 * {@code p >= 0.5} rule. Those two rules are not interchangeable, and conflating them is the
 * plausible-looking wrong implementation this class exists to avoid: evaluating the UN-erased
 * {@code self.speed > 0.30} and applying {@code p >= 0.5} to the resulting {@code UBooleanValue} is
 * NOT the spec's rule. It is dangerously close to correct, because for {@code UReal(mu,sigma) > c}
 * the probability is monotone in {@code mu}; the readings part company exactly at the tie {@code mu
 * == c}, measured against USE's own evaluator at {@code sigma = 0.02}:
 *
 * <pre>
 *   speed &gt;  0.30   crisp FALSE   USE p = 0.4999999994746490   (shortcut also FALSE)
 *   speed &gt;= 0.30   crisp TRUE    USE p = 0.4999999994746490   (shortcut FALSE -- wrong)
 *   speed &lt;  0.30   crisp FALSE   USE p = 0.5000000005253510   (shortcut TRUE  -- wrong)
 *   speed &lt;= 0.30   crisp TRUE    USE p = 0.5000000005253510   (shortcut also TRUE)
 * </pre>
 *
 * <p>(USE's normal-CDF approximation is why the {@code >} tie does NOT separate them, contrary to
 * the exact-arithmetic expectation that {@code Phi(0) == 0.5}; the {@code >=} and {@code <} ties
 * do.)
 *
 * <p><b>Fail closed.</b> "If no type-directed erasure rule exists, the invariant is ineligible for
 * FRAGILE and the tool returns UNSUPPORTED rather than guessing." Every shape without a rule throws
 * {@link NominalErasureUnsupportedException} rather than approximating one, and an expression whose
 * crispness cannot be established is treated as uncertain -- so the refusal is the default, not the
 * exception.
 *
 * <p>A crisp expression erases to itself, so anything provably free of U-typed subexpressions is
 * handed to {@link ThreeValuedEvaluator} unchanged. That is not a shortcut: it is {@code
 * THESIS_SMT_MODEL_FINDER_PLAN.md} §5.1's degenerate case, and it mirrors {@code
 * ExpressionTranslator}, whose NOMINAL and UNCERTAIN encodings differ in exactly one place ({@code
 * uRealThreshold}).
 */
final class NominalErasureEvaluator {
  private NominalErasureEvaluator() {}

  /** The nominal reading of a Boolean-typed expression. */
  static InvariantOutcome eval(Expression expression, EvalContext ctx) {
    if (!containsUncertainty(expression)) {
      // E(e) = e for a crisp expression: nominal and uncertain evaluation coincide.
      return ThreeValuedEvaluator.eval(expression, ctx);
    }
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
        case "toBooleanC":
          // E( b.toBooleanC(theta) ) = E_B(b). The projection is REMOVED, not re-applied to an
          // erased operand, so the confidence argument plays no part in the nominal reading at all.
          return erasedBoolean(args[0], ctx);
        case "=":
          return equality(args[0], args[1], ctx);
        case "<>":
          return not(equality(args[0], args[1], ctx));
        case ">":
        case ">=":
        case "<":
        case "<=":
          return ordered(op.opname(), args[0], args[1], ctx);
        default:
          throw unsupported("operator '" + op.opname() + "' over an uncertain operand");
      }
    }
    throw unsupported(
        "Boolean expression shape "
            + expression.getClass().getSimpleName()
            + " ('"
            + expression
            + "')");
  }

  /**
   * {@code E_B}: the nominal Boolean of a UBoolean-valued expression, once its confidence
   * projection has been stripped.
   */
  private static InvariantOutcome erasedBoolean(Expression expression, EvalContext ctx) {
    if (!containsUncertainty(expression)) {
      return ThreeValuedEvaluator.eval(expression, ctx);
    }
    if (expression instanceof ExpStdOp op) {
      Expression[] args = op.args();
      switch (op.opname()) {
        case "=":
          return equality(args[0], args[1], ctx);
        case "<>":
          return not(equality(args[0], args[1], ctx));
        case ">":
        case ">=":
        case "<":
        case "<=":
          return ordered(op.opname(), args[0], args[1], ctx);
        default:
          break;
      }
    }
    if (isStoredAccess(expression) && expression.type().isTypeOfUBoolean()) {
      // The one place the p >= 0.5 rule belongs: a STORED UBoolean probability.
      return outcomeOfValue(eraseStoredValue(expression.eval(ctx)));
    }
    throw unsupported(
        "no nominal-erasure rule for the UBoolean expression '"
            + expression
            + "' ("
            + expression.getClass().getSimpleName()
            + ")");
  }

  /**
   * A numeric or string comparison, erased to its CRISP comparison. The ordered comparators are
   * strict in USE, so an undefined operand makes the whole comparison undefined.
   */
  private static InvariantOutcome ordered(
      String operator, Expression left, Expression right, EvalContext ctx) {
    Value l = eraseValue(left, ctx);
    Value r = eraseValue(right, ctx);
    if (l.isUndefined() || r.isUndefined()) {
      return InvariantOutcome.UNDEFINED;
    }
    int comparison;
    if (l instanceof StringValue ls && r instanceof StringValue rs) {
      comparison = ls.value().compareTo(rs.value());
    } else {
      Double a = asNumber(l);
      Double b = asNumber(r);
      if (a == null || b == null) {
        throw unsupported(
            "crisp comparison '" + operator + "' over erased operands " + l + " and " + r);
      }
      comparison = Double.compare(a, b);
    }
    boolean holds =
        switch (operator) {
          case ">" -> comparison > 0;
          case ">=" -> comparison >= 0;
          case "<" -> comparison < 0;
          default -> comparison <= 0;
        };
    return holds ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
  }

  /**
   * USE's {@code =} is TOTAL, not strict ({@code Op_equal.kind() == SPECIAL}): it is always
   * defined, and true exactly when both operands are undefined or both are defined and equal. The
   * erased comparison keeps that rule rather than inventing a strict one.
   */
  private static InvariantOutcome equality(Expression left, Expression right, EvalContext ctx) {
    Value l = eraseValue(left, ctx);
    Value r = eraseValue(right, ctx);
    if (l.isUndefined() || r.isUndefined()) {
      return l.isUndefined() && r.isUndefined() ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
    }
    return l.equals(r) ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
  }

  /**
   * The erased value of a value-position expression. A crisp expression erases to itself; a STORED
   * U-value access erases through the table above. Anything else -- notably arithmetic over
   * U-values, whose representative this class will not assume is preserved by USE's uncertainty
   * propagation -- has no rule here and is refused.
   */
  private static Value eraseValue(Expression expression, EvalContext ctx) {
    if (!containsUncertainty(expression)) {
      return expression.eval(ctx);
    }
    if (isStoredAccess(expression)) {
      return eraseStoredValue(expression.eval(ctx));
    }
    throw unsupported(
        "no nominal-erasure rule for the value expression '"
            + expression
            + "' ("
            + expression.getClass().getSimpleName()
            + ")");
  }

  /**
   * A variable, attribute access, or single-valued navigation: the "stored value" the erasure table
   * is written over. Its own source must be crisp, so the access really is a stored one.
   */
  private static boolean isStoredAccess(Expression expression) {
    if (expression instanceof ExpVariable) {
      return true;
    }
    if (expression instanceof ExpAttrOp attribute) {
      return !containsUncertainty(attribute.objExp());
    }
    if (expression instanceof ExpNavigation navigation) {
      return !containsUncertainty(navigation.getObjectExpression());
    }
    return false;
  }

  /** The proposal's stored-value erasure table, and nothing else. */
  private static Value eraseStoredValue(Value value) {
    if (value.isUndefined()) {
      return UndefinedValue.instance;
    }
    if (value instanceof URealValue real) {
      return new RealValue(real.value());
    }
    if (value instanceof UIntegerValue integer) {
      return IntegerValue.valueOf(integer.value());
    }
    if (value instanceof UStringValue string) {
      return new StringValue(string.value());
    }
    if (value instanceof UBooleanValue bool) {
      // The exact tie p == 0.5 maps to true, matching USE's own toBoolean(); the proposal records
      // it as ambiguous and reported separately, not as a second semantics.
      return BooleanValue.get(bool.probability() >= 0.5);
    }
    if (value.type() != null && isUncertain(value.type())) {
      throw unsupported("no nominal-erasure rule for the U-value " + value);
    }
    return value;
  }

  private static InvariantOutcome quantify(ExpQuery query, boolean existential, EvalContext ctx) {
    Expression rangeExpression = query.getRangeExpression();
    if (containsUncertainty(rangeExpression)) {
      throw unsupported(
          "no nominal-erasure rule for the uncertain quantifier range '" + rangeExpression + "'");
    }
    Value range = rangeExpression.eval(ctx);
    if (range.isUndefined()) {
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
   * The same three-valued quantifier rule {@link ThreeValuedEvaluator} and {@code
   * InvariantAssembler.classify} use, over the erased body: for {@code forAll} a defined-false
   * element decides, otherwise any undefined element makes it undefined, otherwise true (vacuously
   * true over an empty range); {@code exists} is the dual, and is FALSE over an empty range.
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
          // See ThreeValuedEvaluator: EvalContext.popVarBinding() is package-private in use-core.
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
   * Whether any U-typed value can reach this expression. The answer defaults to {@code true} for a
   * shape this method does not recognise, so an unrecognised construct is routed to the erasure
   * rules -- which refuse it -- instead of being silently handed to the U-AWARE evaluator, which
   * would answer the wrong question without saying so.
   */
  private static boolean containsUncertainty(Expression expression) {
    if (isUncertain(expression.type())) {
      return true;
    }
    if (expression instanceof ExpStdOp op) {
      for (Expression argument : op.args()) {
        if (containsUncertainty(argument)) {
          return true;
        }
      }
      return false;
    }
    if (expression instanceof ExpAttrOp attribute) {
      return containsUncertainty(attribute.objExp());
    }
    if (expression instanceof ExpNavigation navigation) {
      return containsUncertainty(navigation.getObjectExpression());
    }
    if (expression instanceof ExpObjAsSet objAsSet) {
      return containsUncertainty(objAsSet.getObjectExpression());
    }
    if (expression instanceof ExpIsKindOf isKindOf) {
      return containsUncertainty(isKindOf.getSourceExpr());
    }
    if (expression instanceof ExpIsTypeOf isTypeOf) {
      return containsUncertainty(isTypeOf.getSourceExpr());
    }
    if (expression instanceof ExpQuery query) {
      return containsUncertainty(query.getRangeExpression())
          || containsUncertainty(query.getQueryExpression());
    }
    return !(expression instanceof ExpVariable
        || expression instanceof ExpAllInstances
        || expression instanceof ExpObjRef
        || expression instanceof ExpUndefined
        || expression instanceof ExpConstInteger
        || expression instanceof ExpConstReal
        || expression instanceof ExpConstString
        || expression instanceof ExpConstBoolean
        || expression instanceof ExpConstEnum);
  }

  private static boolean isUncertain(Type type) {
    if (type == null) {
      return true;
    }
    if (type.isTypeOfUReal()
        || type.isTypeOfUInteger()
        || type.isTypeOfUBoolean()
        || type.isTypeOfUString()
        || type.isTypeOfSBoolean()) {
      return true;
    }
    return type instanceof CollectionType collection && isUncertain(collection.elemType());
  }

  private static Double asNumber(Value value) {
    if (value instanceof RealValue real) {
      return real.value();
    }
    if (value instanceof IntegerValue integer) {
      return (double) integer.value();
    }
    return null;
  }

  private static InvariantOutcome outcomeOfValue(Value value) {
    if (value.isUndefined()) {
      return InvariantOutcome.UNDEFINED;
    }
    if (!(value instanceof BooleanValue bool)) {
      throw unsupported("erased Boolean position produced the non-Boolean value " + value);
    }
    return bool.isTrue() ? InvariantOutcome.TRUE : InvariantOutcome.FALSE;
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

  private static NominalErasureUnsupportedException unsupported(String reason) {
    return new NominalErasureUnsupportedException(reason);
  }
}
