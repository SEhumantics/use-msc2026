package org.tzi.use.uml.ocl.expr.operations;

import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.type.SBooleanType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.type.UncertainType;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.UncertainValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.util.StringUtil;

import com.google.common.collect.Multimap;

public class StandardOperationsAny {
	public static void registerTypeOperations(Multimap<String, OpGeneric> opmap) {
		// generic operations on all types
		OpGeneric.registerOperation(new Op_equal(), opmap);
		OpGeneric.registerOperation(new Op_identical(), opmap);
		OpGeneric.registerOperation(new Op_notequal(), opmap);
		OpGeneric.registerOperation(new Op_isDefined(), opmap);
		OpGeneric op = new Op_isUndefined();
		OpGeneric.registerOperation(op, opmap);
		OpGeneric.registerOperation("oclIsUndefined", op, opmap);
	}
}

// --------------------------------------------------------
//
// Generic operations on all types.
//
// --------------------------------------------------------

/* = : T1 x T2 -> Boolean, with T2 <= T1 or T1 <= T2 */
final class Op_equal extends OpGeneric {
	public String name() {
		return "=";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return true;
	}

	public Type matches(Type params[]) {
		if (params.length != 2 || params[0].getLeastCommonSupertype(params[1]) == null)
			return null;
		Type uncertain = UncertainComparison.resultType(params);
		return uncertain != null ? uncertain : TypeFactory.mkBoolean();
	}

	@Override
	public String checkWarningUnrelatedTypes(Expression args[]) {
		Type lcst = args[0].type().getLeastCommonSupertype(args[1].type());

		if ((!(args[0].type().isTypeOfOclAny() || args[1].type().isTypeOfOclAny()) && lcst.isTypeOfOclAny()) ||
				(!(args[0].type().isTypeOfCollection() || args[1].type().isTypeOfCollection()) && lcst.isTypeOfCollection())) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) +
					 " can never evaluate to true because " + StringUtil.inQuotes(args[0].type()) +
					 " and " + StringUtil.inQuotes(args[1].type()) + " are unrelated.";
		}

		return null;
	}

	public Value eval(EvalContext ctx, Value[] args, Type resultType) {
		if (UncertainComparison.isUncertainComparison(resultType, args))
			return UncertainComparison.operand(args).uEquals(UncertainComparison.other(args));

		if (args[0].isUndefined())
			return BooleanValue.get(args[1].isUndefined());

		return BooleanValue.get(UncertainComparison.certainEquals(args));
	}
}

// --------------------------------------------------------

/**
 * {@code equals} is the historical certain counterpart of {@code =}: it keeps
 * comparing uncertain values by their representation and always yields Boolean.
 */
final class Op_identical extends OpGeneric {
	public String name() {
		return "equals";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return true;
	}

	public Type matches(Type params[]) {
		if (params.length == 2 && params[0].getLeastCommonSupertype(params[1]) != null)
			return TypeFactory.mkBoolean();
		else
			return null;
	}

	@Override
	public String checkWarningUnrelatedTypes(Expression args[]) {
		Type lcst = args[0].type().getLeastCommonSupertype(args[1].type());

		if ((!(args[0].type().isTypeOfOclAny() || args[1].type().isTypeOfOclAny()) && lcst.isTypeOfOclAny()) ||
				(!(args[0].type().isTypeOfCollection() || args[1].type().isTypeOfCollection()) && lcst.isTypeOfCollection())) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) +
					 " can never evaluate to true because " + StringUtil.inQuotes(args[0].type()) +
					 " and " + StringUtil.inQuotes(args[1].type()) + " are unrelated.";
		}

		return null;
	}

	public Value eval(EvalContext ctx, Value[] args, Type resultType) {
		if (args[0].isUndefined())
			return BooleanValue.get(args[1].isUndefined());

		return BooleanValue.get(UncertainComparison.certainEquals(args));
	}
}

// --------------------------------------------------------

/**
 * Shared decision procedure for {@code =} and {@code <>}: when an operand
 * carries uncertainty, the comparison itself is uncertain and its result type
 * follows the operands.
 */
final class UncertainComparison {

	private UncertainComparison() { }

	static Type resultType(Type[] params) {
		boolean uncertain = params[0] instanceof UncertainType || params[1] instanceof UncertainType
				|| params[0] instanceof SBooleanType || params[1] instanceof SBooleanType;
		if (!uncertain || params[0].isTypeOfVoidType() || params[1].isTypeOfVoidType())
			return null;
		return params[0] instanceof SBooleanType || params[1] instanceof SBooleanType
				? TypeFactory.mkSBoolean() : TypeFactory.mkUBoolean();
	}

	/**
	 * The result type was fixed when the expression was checked, so an uncertain
	 * result may only be produced when that type asks for one. A statically
	 * certain expression that happens to hold an uncertain value stays certain.
	 */
	static boolean isUncertainComparison(Type resultType, Value[] args) {
		if (!(resultType instanceof UncertainType || resultType instanceof SBooleanType)) return false;
		return isUncertainComparison(args);
	}

	private static boolean isUncertainComparison(Value[] args) {
		return (args[0] instanceof UncertainValue || args[1] instanceof UncertainValue)
				&& !args[0].isUndefined() && !args[1].isUndefined();
	}

	static UncertainValue operand(Value[] args) {
		return (UncertainValue) (args[0] instanceof UncertainValue ? args[0] : args[1]);
	}

	static Value other(Value[] args) {
		return args[0] instanceof UncertainValue ? args[1] : args[0];
	}

	static boolean certainEquals(Value[] args) {
		if (args[1].type().conformsTo(args[0].type()))
			return args[0].equals(args[1]);
		if (args[0].type().conformsTo(args[1].type()))
			return args[1].equals(args[0]);
		return false;
	}
}

// --------------------------------------------------------

/* <> : T1 x T2 -> Boolean, with T2 <= T1 or T1 <= T2 */
final class Op_notequal extends OpGeneric {
	public String name() {
		return "<>";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return true;
	}

	public Type matches(Type params[]) {
		if (params.length != 2 || params[0].getLeastCommonSupertype(params[1]) == null)
			return null;
		Type uncertain = UncertainComparison.resultType(params);
		return uncertain != null ? uncertain : TypeFactory.mkBoolean();
	}

	public Value eval(EvalContext ctx, Value[] args, Type resultType) {
		if (UncertainComparison.isUncertainComparison(resultType, args))
			return UncertainComparison.operand(args).uDistinct(UncertainComparison.other(args));

		if (args[0].isUndefined())
			return BooleanValue.get(!args[1].isUndefined());

		boolean res = !args[0].equals(args[1]);
		return BooleanValue.get(res);
	}
	
	@Override
	public String checkWarningUnrelatedTypes(Expression args[]) {
		Type lcst = args[0].type().getLeastCommonSupertype(args[1].type());
		
		if ((!(args[0].type().isTypeOfOclAny() || args[1].type().isTypeOfOclAny()) && lcst.isTypeOfOclAny()) ||
				(!(args[0].type().isTypeOfCollection() || args[1].type().isTypeOfCollection()) && lcst.isTypeOfCollection())) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) + 
					 " can never evaluate to false because " + StringUtil.inQuotes(args[0].type()) + 
					 " and " + StringUtil.inQuotes(args[1].type()) + " are unrelated.";
		}
		
		return null;
	}
}

// --------------------------------------------------------

/* isDefined : T -> Boolean */
final class Op_isDefined extends OpGeneric {
	public String name() {
		return "isDefined";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return false;
	}

	public Type matches(Type params[]) {
		return (params.length == 1) ? TypeFactory.mkBoolean() : null;
	}

	public Value eval(EvalContext ctx, Value[] args, Type resultType) {
		boolean res = !args[0].isUndefined();
		return BooleanValue.get(res);
	}
	
	@Override
	public String checkWarningUnrelatedTypes(Expression args[]) {
		if (args[0].type().isTypeOfVoidType()) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) + 
					 " can never evaluate to true because " + StringUtil.inQuotes(args[0].type()) + 
					 " is always undefined";
		}
		
		return null;
	}
}

// --------------------------------------------------------

/* isUndefined : T -> Boolean */
final class Op_isUndefined extends OpGeneric {
	public String name() {
		return "isUndefined";
	}

	public int kind() {
		return SPECIAL;
	}

	public boolean isInfixOrPrefix() {
		return false;
	}

	public Type matches(Type params[]) {
		return (params.length == 1) ? TypeFactory.mkBoolean() : null;
	}

	public Value eval(EvalContext ctx, Value[] args, Type resultType) {
		boolean res = args[0].isUndefined();
		return BooleanValue.get(res);
	}
	
	@Override
	public String checkWarningUnrelatedTypes(Expression args[]) {
		if (args[0].type().isTypeOfVoidType()) {
			return "Expression " + StringUtil.inQuotes(this.stringRep(args, "")) + 
					 " can never evaluate to false because " + StringUtil.inQuotes(args[0].type()) + 
					 " is always undefined";
		}
		
		return null;
	}
}
