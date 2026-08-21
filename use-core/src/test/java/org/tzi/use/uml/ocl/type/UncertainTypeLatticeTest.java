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

    /**
     * Not an uncertainty case -- {@code Integer} and {@code UnlimitedNatural} are both classic USE
     * types, and this is a plain-USE defect docs/port2/adaptation/01-types.md &sect;C-08 found: they
     * are mutually conformant (each {@code conformsTo} the other via the same "is a kind of number"
     * check), so {@link UniqueLeastCommonSupertypeDeterminator}'s greedy tie-break had no fixpoint
     * and returned whichever element the {@code HashSet}-backed iteration yielded last -- ordered by
     * {@code BasicType.hashCode()}, the JVM's lazily-assigned identity hash of the type's
     * {@code Class} object, so the answer could differ across JVM runs of the same binary (confirmed
     * in &sect;C-08's own experiment; not reproducible as a same-run flip, since a class's identity
     * hash is fixed for the rest of that process once requested). Lives here because this is where
     * {@code ulcs()} already exists, not because it's about uncertainty.
     *
     * <p>What this pins down is the fix's actual claim -- {@code calculateFor} is now a pure function
     * of the input types (iterates a {@code TreeSet} ordered by {@code toString()}, not identity
     * hash) and so returns the same answer on every call, every run, regardless of input order. It
     * does not test "same run, forward vs reversed insertion order" -- with a fixed class-identity
     * hash for the run's whole lifetime, the pre-fix code passes that check too, for the same reason
     * it fails across runs: the answer is stable WITHIN a run and unstable BETWEEN runs. Which of the
     * two mutually-conformant types wins is still an open call (decision B11b); this only asserts
     * that it is now the same call every time.
     */
    @Test
    public void mutuallyConformantPairResolvesToTheSameDeterministicAnswer() {
        Type forward = ulcs(TypeFactory.mkInteger(), TypeFactory.mkUnlimitedNatural());
        Type reversed = ulcs(TypeFactory.mkUnlimitedNatural(), TypeFactory.mkInteger());
        assertEquals(forward, reversed,
                "ULCS(Integer, UnlimitedNatural) must not depend on input iteration order");
        // Pinned outcome of the toString()-ordered tie-break, not a claim that this answer is the
        // "correct" one -- see the class doc above and decision B11b.
        assertEquals(TypeFactory.mkUnlimitedNatural(), forward);
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
