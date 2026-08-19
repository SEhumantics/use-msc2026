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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The uncertain type lattice, locked down.
 *
 * <p>This is a <em>new</em> test file, not an edit of an upstream one. It exists because
 * {@code TypeTest#testSupertype} had to be modified under waiver W-01
 * ({@code docs/port2/upstream-test-waivers.md}), and a modified oracle is a weaker oracle unless
 * something independent pins the behaviour the modification was made for.
 *
 * <p>What is asserted here is the <em>purpose</em> of the lattice change rather than its shape:
 * that a mixed crisp/uncertain collection literal acquires the uncertain element type. The fork's
 * measured behaviour is {@code Set{UReal(2,0.5), 1, 2.5} : Set(UReal)}, and the component that
 * decides that element type is {@link UniqueLeastCommonSupertypeDeterminator}, which reads
 * {@code allSupertypes()}.
 *
 * <p>The controls matter as much as the cases: if the crisp answers ever move, the change stopped
 * being conservative and this file should fail.
 */
public class UncertainTypeLatticeTest {

    private static Type ulcs(Type... types) {
        Set<Type> s = new LinkedHashSet<>();
        for (Type t : types) {
            s.add(t);
        }
        return new UniqueLeastCommonSupertypeDeterminator().calculateFor(s);
    }

    /** The fork's worked example: the element type of {@code Set{UReal(2,0.5), 1, 2.5}}. */
    @Test
    public void mixedUncertainLiteralTakesTheUncertainElementType() {
        assertEquals(TypeFactory.mkUReal(),
                ulcs(TypeFactory.mkUReal(), TypeFactory.mkInteger(), TypeFactory.mkReal()),
                "element type of Set{UReal(2,0.5), 1, 2.5}");
    }

    @Test
    public void eachUncertainTypeAbsorbsItsCrispCounterpart() {
        assertEquals(TypeFactory.mkUReal(), ulcs(TypeFactory.mkUReal(), TypeFactory.mkInteger()));
        assertEquals(TypeFactory.mkUReal(), ulcs(TypeFactory.mkUReal(), TypeFactory.mkReal()));
        assertEquals(TypeFactory.mkUInteger(), ulcs(TypeFactory.mkUInteger(), TypeFactory.mkInteger()));
        assertEquals(TypeFactory.mkUBoolean(), ulcs(TypeFactory.mkUBoolean(), TypeFactory.mkBoolean()));
        assertEquals(TypeFactory.mkUString(), ulcs(TypeFactory.mkUString(), TypeFactory.mkString()));
        assertEquals(TypeFactory.mkSBoolean(),
                ulcs(TypeFactory.mkSBoolean(), TypeFactory.mkUBoolean(), TypeFactory.mkBoolean()));
    }

    /** Conformance is one-directional. A UReal is not a Real. */
    @Test
    public void conformanceRunsCrispToUncertainOnly() {
        assertTrue(TypeFactory.mkReal().conformsTo(TypeFactory.mkUReal()));
        assertTrue(TypeFactory.mkInteger().conformsTo(TypeFactory.mkUReal()));
        assertTrue(TypeFactory.mkInteger().conformsTo(TypeFactory.mkUInteger()));
        assertTrue(TypeFactory.mkBoolean().conformsTo(TypeFactory.mkUBoolean()));
        assertTrue(TypeFactory.mkString().conformsTo(TypeFactory.mkUString()));
        assertTrue(TypeFactory.mkUInteger().conformsTo(TypeFactory.mkUReal()));
        assertTrue(TypeFactory.mkUBoolean().conformsTo(TypeFactory.mkSBoolean()));

        assertFalse(TypeFactory.mkUReal().conformsTo(TypeFactory.mkReal()));
        assertFalse(TypeFactory.mkUInteger().conformsTo(TypeFactory.mkInteger()));
        assertFalse(TypeFactory.mkUBoolean().conformsTo(TypeFactory.mkBoolean()));
        assertFalse(TypeFactory.mkUString().conformsTo(TypeFactory.mkString()));
    }

    /**
     * Controls. These are plain-USE answers and must not move; the whole justification of waiver
     * W-01 is that the change touches {@code allSupertypes()} and nothing else.
     */
    @Test
    public void crispAnswersAreUnchanged() {
        assertEquals(TypeFactory.mkReal(), ulcs(TypeFactory.mkInteger(), TypeFactory.mkReal()));
        assertEquals(TypeFactory.mkOclAny(), ulcs(TypeFactory.mkInteger(), TypeFactory.mkString()));
        assertTrue(TypeFactory.mkInteger().conformsTo(TypeFactory.mkReal()));
        assertFalse(TypeFactory.mkReal().conformsTo(TypeFactory.mkInteger()));
        assertFalse(TypeFactory.mkInteger().conformsTo(TypeFactory.mkString()));
    }

    /** The abstract tags carry no behaviour and must not be conformance targets of their own. */
    @Test
    public void theFiveLeavesAreTaggedAsUncertain() {
        Type[] leaves = { TypeFactory.mkUReal(), TypeFactory.mkUInteger(), TypeFactory.mkUBoolean(),
                          TypeFactory.mkUString(), TypeFactory.mkSBoolean() };
        for (Type t : leaves) {
            assertTrue(t instanceof UncertainType, t + " must be tagged UncertainType");
            assertTrue(t.conformsTo(t), t + " must conform to itself");
            assertTrue(t.conformsTo(TypeFactory.mkOclAny()), t + " must conform to OclAny");
        }
        assertTrue(TypeFactory.mkUBoolean() instanceof UncertainBooleanType);
        assertTrue(TypeFactory.mkSBoolean() instanceof UncertainBooleanType);
        Type uReal = TypeFactory.mkUReal();
        assertFalse(uReal instanceof UncertainBooleanType);
    }
}
