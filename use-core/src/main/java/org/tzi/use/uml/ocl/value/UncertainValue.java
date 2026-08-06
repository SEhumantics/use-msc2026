package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.Type;

/** Base class for values whose equality is itself uncertain. */
public abstract class UncertainValue extends Value {
    protected UncertainValue(Type type) { super(type); }
    public abstract UBooleanValue uEquals(Value other);
    public UBooleanValue uDistinct(Value other) { return uEquals(other).not(); }
}
