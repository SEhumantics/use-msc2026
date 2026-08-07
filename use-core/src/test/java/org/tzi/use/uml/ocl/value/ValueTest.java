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

package org.tzi.use.uml.ocl.value;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.testing.EqualsTester;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.TypeFactory;

import java.util.Arrays;

/**
 * Test Value classes.
 *
 * @author  Mark Richters
 */

public class ValueTest {

    @Test
    public void testEnum() {
        String[] literals = { "a", "b", "c" };
        EnumType enm = TypeFactory.mkEnum("E", Arrays.asList(literals));
        assertEquals( "b", new EnumValue(enm, "b").value(),"EnumValue.value");
        try {
            new EnumValue(enm, "d");
            fail("Illegal EnumValue");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testInt() {
        assertEquals( 42, IntegerValue.valueOf(42).value(),"IntegerValue.value");
        assertEquals(
                     TypeFactory.mkInteger(),
                     IntegerValue.valueOf(42).type(),"IntegerValue.type");
        assertTrue(
                   IntegerValue.valueOf(42).equals(IntegerValue.valueOf(42)),"IntegerValue.equals");
    }

    @Test
    public void testReal() {
        assertEquals( 1.2, new RealValue(1.2).value(), 0,"RealValue.value");
        assertEquals( TypeFactory.mkReal(), new RealValue(1.2).type(),"RealValue.type");
        assertTrue( new RealValue(1.2).equals(new RealValue(1.2)),"RealValue.equals");
    }

    @Test
    public void testBoolean() {
        assertTrue( BooleanValue.TRUE.value(),"BooleanValue.value");
        assertFalse( BooleanValue.FALSE.value(),"BooleanValue.value");
        assertEquals( BooleanValue.TRUE, BooleanValue.get(true),"BooleanValue.value");
        assertEquals( BooleanValue.FALSE, BooleanValue.get(false),"BooleanValue.value");
        assertEquals(
                     TypeFactory.mkBoolean(),
                     BooleanValue.TRUE.type(),"BooleanValue.type");
        assertEquals(
                     TypeFactory.mkBoolean(),
                     BooleanValue.FALSE.type(),"BooleanValue.type");
        assertTrue( BooleanValue.FALSE.equals(BooleanValue.FALSE),"BooleanValue.equals");
        assertTrue( BooleanValue.FALSE.equals(BooleanValue.FALSE),"BooleanValue.equals");
    }

    @Test
    public void testString() {
        assertEquals( "foo", new StringValue("foo").value(),"StringValue.value");
        assertEquals(
                     TypeFactory.mkString(),
                     new StringValue("foo").type(),"StringValue.type");
        assertTrue(
                   new StringValue("bar").equals(new StringValue("bar")),"StringValue.equals");
    }

    @Test
    public void testSet() {
        SetValue intSet = new SetValue(TypeFactory.mkInteger());
        assertEquals( 0, intSet.size(),"SetValue.size");
        intSet.add(IntegerValue.valueOf(3));
        assertEquals( 1, intSet.size(),"SetValue.insert");
        intSet.add(IntegerValue.valueOf(3));
        assertEquals( 1, intSet.size(),"SetValue/no duplicates");
        try {
            intSet.add(new StringValue("foo"));
            // fail("SetValue/matching element type");
            // currently this is allowed..
        } catch (IllegalArgumentException e) {
            // expected
        }
        SetValue set1 = new SetValue(TypeFactory.mkInteger());
        set1.add(IntegerValue.valueOf(1));
        set1.add(IntegerValue.valueOf(2));
        set1.add(IntegerValue.valueOf(3));
        SetValue set2 = new SetValue(TypeFactory.mkInteger());
        set2.add(IntegerValue.valueOf(2));
        set2.add(IntegerValue.valueOf(3));
        assertFalse( set1.equals(set2),"SetValue.equals");
        set2.add(IntegerValue.valueOf(1));
        assertTrue( set1.equals(set2),"SetValue.equals");
        assertEquals( "Set{1,2,3}", set1.toString(),"SetValue.toString");
    }

    @Test
    public void testBag() {
        BagValue intBag = new BagValue(TypeFactory.mkInteger());
        assertEquals( 0, intBag.size(),"BagValue.size");
        intBag.add(IntegerValue.valueOf(3));
        assertEquals( 1, intBag.size(),"BagValue.insert");
        intBag.add(IntegerValue.valueOf(3));
        assertEquals( 2, intBag.size(),"BagValue/duplicates");
        try {
            intBag.add(new StringValue("foo"));
            //            fail("BagValue/matching element type");
        } catch (IllegalArgumentException e) {
            // expected
        }
        BagValue bag1 = new BagValue(TypeFactory.mkInteger());
        bag1.add(IntegerValue.valueOf(1));
        bag1.add(IntegerValue.valueOf(2));
        bag1.add(IntegerValue.valueOf(3));
        BagValue bag2 = new BagValue(TypeFactory.mkInteger());
        bag2.add(IntegerValue.valueOf(2));
        bag2.add(IntegerValue.valueOf(3));
        assertFalse( bag1.equals(bag2),"BagValue.equals");
        bag2.add(IntegerValue.valueOf(1));
        assertTrue( bag1.equals(bag2),"BagValue.equals");
        bag2.add(IntegerValue.valueOf(1));
        assertEquals( "Bag{1,1,2,3}", bag2.toString(),"BagValue.toString");
    }

    @Test
    public void testSequence() {
        SequenceValue intSeq = new SequenceValue(TypeFactory.mkInteger());
        assertEquals( 0, intSeq.size(),"SequenceValue.size");
        intSeq.add(IntegerValue.valueOf(3));
        assertEquals( 1, intSeq.size(),"SequenceValue.insert");
        intSeq.add(IntegerValue.valueOf(3));
        assertEquals( 2, intSeq.size(),"SequenceValue/duplicates");
        try {
            intSeq.add(new StringValue("foo"));
            //            fail("SequenceValue/matching element type");
        } catch (IllegalArgumentException e) {
            // expected
        }
        SequenceValue seq1 = new SequenceValue(TypeFactory.mkInteger());
        seq1.add(IntegerValue.valueOf(1));
        seq1.add(IntegerValue.valueOf(2));
        seq1.add(IntegerValue.valueOf(3));
        SequenceValue seq2 = new SequenceValue(TypeFactory.mkInteger());
        seq2.add(IntegerValue.valueOf(1));
        seq2.add(IntegerValue.valueOf(2));
        assertFalse( seq1.equals(seq2),"SequenceValue.equals");
        seq2.add(IntegerValue.valueOf(3));
        assertTrue( seq1.equals(seq2),"SequenceValue.equals");
        seq2.add(IntegerValue.valueOf(1));
        assertEquals( "Sequence{1,2,3,1}", seq2.toString(),"SequenceValue.toString");
    }

    @Test
    public void testSetEquals() {
        SetValue intSet1 = new SetValue(TypeFactory.mkInteger());
        intSet1.add(IntegerValue.valueOf(1));
        SetValue intSet2 = new SetValue(TypeFactory.mkInteger());
        intSet2.add(IntegerValue.valueOf(1));
        SetValue intSet3 = new SetValue(TypeFactory.mkInteger());
        intSet3.add(IntegerValue.valueOf(2));
        SetValue intSet4 = new SetValue(TypeFactory.mkInteger()) { /*subclass*/ };
        intSet4.add(IntegerValue.valueOf(1));
        
        new EqualsTester()
                .addEqualityGroup(intSet1, intSet2)
                .addEqualityGroup(intSet3)
                .addEqualityGroup(intSet4)
                .testEquals();
    }
    
    @Test
    public void testSequenceEquals() {
        SequenceValue intSequence1 = new SequenceValue(TypeFactory.mkInteger());
        intSequence1.add(IntegerValue.valueOf(1));
        SequenceValue intSequence2 = new SequenceValue(TypeFactory.mkInteger());
        intSequence2.add(IntegerValue.valueOf(1));
        SequenceValue intSequence3 = new SequenceValue(TypeFactory.mkInteger());
        intSequence3.add(IntegerValue.valueOf(2));
        SequenceValue intSequence4 = new SequenceValue(TypeFactory.mkInteger()) { /*subclass*/ };
        intSequence4.add(IntegerValue.valueOf(1));
        
        new EqualsTester()
                .addEqualityGroup(intSequence1, intSequence2)
                .addEqualityGroup(intSequence3)
                .addEqualityGroup(intSequence4)
                .testEquals();
    }


    @Test
    public void testBagEquals() {
        BagValue intBag1 = new BagValue(TypeFactory.mkInteger());
        intBag1.add(IntegerValue.valueOf(1));
        BagValue intBag2 = new BagValue(TypeFactory.mkInteger());
        intBag2.add(IntegerValue.valueOf(1));
        BagValue intBag3 = new BagValue(TypeFactory.mkInteger());
        intBag3.add(IntegerValue.valueOf(2));
        BagValue intBag4 = new BagValue(TypeFactory.mkInteger()) { /*subclass*/ };
        intBag4.add(IntegerValue.valueOf(1));
        
        new EqualsTester()
                .addEqualityGroup(intBag1, intBag2)
                .addEqualityGroup(intBag3)
                .addEqualityGroup(intBag4)
                .testEquals();
    }
    
    
}
