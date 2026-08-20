package org.tzi.use.uncertainty.datatypes;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
            // Two distinct failure modes deliberately kept apart, rather than wrapped in a single
            // assertDoesNotThrow: a missing clone() (NoSuchMethodException) and a non-public clone()
            // (assertTrue below) should each report their own clear message, not have one blur into
            // "unexpected exception thrown" with the real cause demoted to a suppressed exception.
            Method clone;
            try {
                clone = type.getMethod("clone");
            } catch (NoSuchMethodException e) {
                fail(type.getSimpleName() + " implements Cloneable but declares no public clone() "
                        + "at all — external code cannot call Object.clone() despite the declared "
                        + "contract.", e);
                return;
            }
            assertTrue(Modifier.isPublic(clone.getModifiers()),
                    type.getSimpleName() + " implements Cloneable but clone() is not public — "
                            + "external code cannot call Object.clone() despite the declared contract.");
        }
    }
}
