package org.tzi.use.uncertainty.datatypes;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class that declares {@code implements Cloneable} without overriding {@code clone()} with
 * public visibility is lying about its own contract: {@code Object.clone()} is {@code protected},
 * so external callers cannot invoke it despite the class's own declaration saying they can.
 */
class DatatypeCloneableContractTest {

    private static final Class<?>[] VENDORED_DATATYPES = {
            UBoolean.class, UInteger.class, UReal.class, UString.class, SBoolean.class
    };

    @Test
    void everyCloneableVendoredTypeExposesAPublicClone() {
        for (Class<?> type : VENDORED_DATATYPES) {
            if (!Cloneable.class.isAssignableFrom(type)) {
                continue;
            }
            assertDoesNotThrow(() -> {
                Method clone = type.getMethod("clone");
                assertTrue(Modifier.isPublic(clone.getModifiers()),
                        type.getSimpleName() + " implements Cloneable but clone() is not public");
            }, type.getSimpleName() + " implements Cloneable but declares no public clone() — "
                    + "external code cannot call Object.clone() despite the declared contract.");
        }
    }
}
