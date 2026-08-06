package org.tzi.use.uml.ocl.type;
import java.util.*;
public final class UIntegerType extends UncertainType {
    UIntegerType() { super("UInteger"); }
    @Override public boolean isTypeOfUInteger() { return true; }
    @Override public boolean isKindOfUInteger(VoidHandling h) { return true; }
    @Override public boolean isKindOfUReal(VoidHandling h) { return true; }
    @Override public boolean isKindOfNumber(VoidHandling h) { return true; }
    @Override public boolean conformsTo(Type t) { return t.isTypeOfUInteger() || t.isTypeOfUReal() || t.isTypeOfOclAny(); }
    @Override public Set<Type> allSupertypes() { return new HashSet<>(Arrays.asList(this, TypeFactory.mkUReal(), TypeFactory.mkOclAny())); }
}
