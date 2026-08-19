/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2004 Mark Richters, University of Bremen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.tzi.use.uml.ocl.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Guards the two fixes applied to {@link TupleType}'s supertype cost after the uncertain lattice
 * raised {@code allSupertypes()} from {@code 3ⁿ+1} to {@code 5ⁿ+1} entries.
 *
 * <p>Measured before the fixes, on this build: arity 8 produced 390,626 supertypes in ~31 s, and
 * {@code getLeastCommonSupertype} reached that enumeration whenever a tuple was the argument.
 *
 * <p>These are cost regressions with a correctness half. The correctness half is asserted first and
 * is the point; the timing bounds carry a wide margin (seconds against a fault that took tens of
 * seconds) so they detect the regression without being flaky.
 */
public class TupleTypeSupertypeCostTest {

    private static TupleType tupleOfIntegers(int arity) {
        TupleType.Part[] parts = new TupleType.Part[arity];
        for (int i = 0; i < arity; i++) {
            parts[i] = new TupleType.Part(i, "p" + i, TypeFactory.mkInteger());
        }
        return TypeFactory.mkTuple(parts);
    }

    /**
     * A tuple's supertype set contains only tuples, itself and OclAny; a simple type's never
     * contains a tuple. So the intersection is exactly {OclAny} and the answer is OclAny — without
     * materialising the exponential set.
     */
    @Test
    public void leastCommonSupertypeOfSimpleTypeAndTupleIsOclAnyAndIsNotExponential() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int arity = 1; arity <= 9; arity++) {
                TupleType t = tupleOfIntegers(arity);
                assertEquals(TypeFactory.mkOclAny(),
                        TypeFactory.mkInteger().getLeastCommonSupertype(t),
                        "Integer.getLeastCommonSupertype(Tuple/" + arity + ")");
                assertEquals(TypeFactory.mkOclAny(),
                        TypeFactory.mkUReal().getLeastCommonSupertype(t),
                        "UReal.getLeastCommonSupertype(Tuple/" + arity + ")");
                assertEquals(TypeFactory.mkOclAny(),
                        TypeFactory.mkString().getLeastCommonSupertype(t),
                        "String.getLeastCommonSupertype(Tuple/" + arity + ")");
            }
        });
    }

    /** Tuple-to-tuple still goes through TupleType's own part-wise algorithm, unchanged. */
    @Test
    public void tupleToTupleStillComputesPartWise() {
        TupleType.Part[] a = { new TupleType.Part(0, "x", TypeFactory.mkInteger()) };
        TupleType.Part[] b = { new TupleType.Part(0, "x", TypeFactory.mkReal()) };
        assertEquals("Tuple(x:Real)",
                TypeFactory.mkTuple(a).getLeastCommonSupertype(TypeFactory.mkTuple(b)).toString());

        TupleType.Part[] c = { new TupleType.Part(0, "y", TypeFactory.mkBoolean()) };
        assertEquals(TypeFactory.mkOclAny(),
                TypeFactory.mkTuple(a).getLeastCommonSupertype(TypeFactory.mkTuple(c)),
                "differing part names have no common tuple supertype");
    }

    /** allSupertypes() is memoised: same instance back, and cheap on every call after the first. */
    @Test
    public void allSupertypesIsMemoised() {
        TupleType t = tupleOfIntegers(5);
        assertEquals(3126, t.allSupertypes().size());
        assertSame(t.allSupertypes(), t.allSupertypes(), "second call must return the cached set");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 100_000; i++) {
                t.allSupertypes();
            }
        });
    }

    /** The cache can only be safe if the type is immutable, so that is asserted, not assumed. */
    @Test
    public void tupleTypeIsStructurallyImmutable() {
        TupleType t = tupleOfIntegers(2);
        assertThrows(UnsupportedOperationException.class, () -> t.getParts().clear(),
                "getParts() must hand out an unmodifiable view");
        assertThrows(UnsupportedOperationException.class,
                () -> t.allSupertypes().clear(),
                "allSupertypes() must hand out an unmodifiable view");
    }
}
