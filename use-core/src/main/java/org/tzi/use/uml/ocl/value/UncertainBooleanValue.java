package org.tzi.use.uml.ocl.value;

import org.tzi.use.uml.ocl.type.Type;

/**
 * An uncertain truth value. Uncertain equality yields one of these, so the kind
 * of the operands decides the kind of the result: comparing subjective opinions
 * yields a subjective opinion, everything else yields a UBoolean.
 */
public abstract class UncertainBooleanValue extends UncertainValue {
    protected UncertainBooleanValue(Type type) { super(type); }
    public abstract UncertainBooleanValue not();
    /** Projection onto a plain uncertain Boolean, for probability-based clients. */
    public abstract UBooleanValue toUBoolean();
}
