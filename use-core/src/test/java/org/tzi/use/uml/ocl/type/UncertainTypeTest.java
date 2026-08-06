package org.tzi.use.uml.ocl.type;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UncertainTypeTest {
    @Test void allHistoricalBuiltinNamesResolve() {
        assertSame(TypeFactory.mkUReal(),TypeFactory.mkSimpleType("UReal"));
        assertSame(TypeFactory.mkUInteger(),TypeFactory.mkSimpleType("UInteger"));
        assertSame(TypeFactory.mkUString(),TypeFactory.mkSimpleType("UString"));
        assertSame(TypeFactory.mkUBoolean(),TypeFactory.mkSimpleType("UBoolean"));
        assertSame(TypeFactory.mkSBoolean(),TypeFactory.mkSimpleType("SBoolean"));
    }
    @Test void certainTypesPromoteToHistoricalUncertainSupertypes() {
        assertTrue(TypeFactory.mkInteger().conformsTo(TypeFactory.mkUInteger()));
        assertTrue(TypeFactory.mkReal().conformsTo(TypeFactory.mkUReal()));
        assertTrue(TypeFactory.mkBoolean().conformsTo(TypeFactory.mkUBoolean()));
        assertTrue(TypeFactory.mkBoolean().conformsTo(TypeFactory.mkSBoolean()));
    }
}
