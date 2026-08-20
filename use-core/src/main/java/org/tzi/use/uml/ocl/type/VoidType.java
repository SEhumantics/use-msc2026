package org.tzi.use.uml.ocl.type;

import java.util.Set;

public class VoidType extends TypeImpl {

	@Override
	public Set<Type> allSupertypes() {
		throw new UnsupportedOperationException("Call to allSupertypes is invalid on OclVoid");
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof VoidType;
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	public boolean conformsTo(Type t) {
		return true;
	}

	@Override
	public boolean isKindOfNumber(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfInteger(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfUnlimitedNatural(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfReal(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfString(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfBoolean(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfEnum(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	// ------------------------------------------------------------------ the uncertain types
	//
	// PORTING OMISSION, restored 2026-08-20. These five exist in the fork
	// (FORK/src/main/org/tzi/use/uml/ocl/type/VoidType.java:38, 58, 123, 128, 133) and were dropped
	// when this file was carried across. They are NOT a B7 correction: they move the port TOWARDS
	// the fork, not away from it, so no IntendedDepartures entry adjudicates them.
	//
	// Without them OclVoid answers false to isKindOfUReal(INCLUDE_VOID) and its four siblings, while
	// answering true for every crisp type above -- so `Undefined` could be passed where an Integer
	// was expected and refused where a UInteger was. Every operation in
	// StandardOperationsU*.java guards its matches() with isKindOfU*(INCLUDE_VOID), so the omission
	// silently narrowed the whole uncertain operation surface against undefined operands.

	@Override
	public boolean isKindOfUReal(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfUInteger(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfUBoolean(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfUString(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfSBoolean(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfCollection(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfSet(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfSequence(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfOrderedSet(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfBag(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfClass(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfDataType(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfOclAny(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfTupleType(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfClassifier(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isKindOfAssociation(VoidHandling h) {
		return h == VoidHandling.INCLUDE_VOID;
	}

	@Override
	public boolean isTypeOfVoidType() {
		return true;
	}

	@Override
	public boolean isVoidOrElementTypeIsVoid() {
		return true;
	}

	@Override
    public StringBuilder toString(StringBuilder sb) {
		return sb.append("OclVoid");
	}
}
