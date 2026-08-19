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

/*
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java.
 *
 * Semantics unchanged. The only edit is the import of the uncertainty datatypes, which were
 * vendored into org.tzi.use.uncertainty.datatypes rather than the original package `uDataTypes`
 * (B1); see docs/port2/stage-03-scope.md sec. 5.
 */
package org.tzi.use.uml.ocl.value;

import org.tzi.use.uncertainty.datatypes.UInteger;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.util.MathUtil;

public class UIntegerValue extends UncertainValue {

    private UInteger uInteger;

    public UIntegerValue(UInteger uInteger) {
        super(TypeFactory.mkUInteger());
        this.uInteger = uInteger;
    }

    public UIntegerValue(int value, double uncertainty) {
        this(new UInteger(value, uncertainty));
    }

    public int value() {
        return uInteger.getX();
    }

    public double uncertainty() {
        return uInteger.getU();
    }

    @Override
    public boolean isUInteger() {
        return true;
    }

    public UInteger getuInteger() {
        return uInteger;
    }

    @Override
    public UncertainBooleanValue uEquals(Value other) {
        // Comapre a UReal with this because UReal is supertype of this type.
        URealValue urValue = URealValue.valueOf(this);
        return urValue.uEquals(other);
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
        sb.append(type())
                .append("(")
                .append(value())
                .append(", ")
                .append(MathUtil.round(uncertainty(), 10))
                .append(")");
        return sb;
    }

    @Override
    public int hashCode() {
        //return uInteger.hashCode();
        // for collections purposes, the follow equality must hold :
        // 1 = 1.0 = UReal(1, 0) = UInteger(1, 0).
        int hash = Double.hashCode(value());
        hash *= 7 * Double.hashCode(uncertainty());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        boolean eq = false;

        if (obj instanceof Value) {

            if (obj instanceof UIntegerValue) {
                // Avoiding the double precision, I have to round the values
                double thisUncertainty = uncertainty(), otherUncertainty = ((UIntegerValue)obj).uncertainty();
                thisUncertainty = MathUtil.round(thisUncertainty, 10);
                otherUncertainty = MathUtil.round(otherUncertainty, 10);

                eq = value() == ((UIntegerValue) obj).value() && thisUncertainty == otherUncertainty;
            }
            else if (obj instanceof IntegerValue) {
                int objValue = ((IntegerValue) obj).value();
                eq = value() == objValue && uncertainty() == 0;
            }
            else if (obj instanceof URealValue) {
                eq = obj.equals(this);
            }

        }

        return eq;
    }

    @Override
    public int compareTo(Value o) {
        int res = 0;

        if (o instanceof UIntegerValue)
            res = uInteger.compareTo(((UIntegerValue) o).uInteger);
        else if (o instanceof RealValue)
            res = uInteger.compareTo(new UInteger((int) ((RealValue) o).value(), 0));
        else if (o instanceof IntegerValue)
            res = uInteger.compareTo(new UInteger(((IntegerValue) o).value()));
        else if (o instanceof URealValue)
            res = o.compareTo(this);

        return res;
    }

    public static UIntegerValue valueOf(Value v) {
        UIntegerValue result = null;

        if (v.isUInteger())
            result = (UIntegerValue) v;
        else if (v.isInteger())
            result = new UIntegerValue(((IntegerValue) v).value(), 0);

        return result;
    }

    private UIntegerValue assertKindOfUInteger(Value value) {
        UIntegerValue uInteger = valueOf(value);

        if (uInteger == null)
            throw new RuntimeException("A value kind of UInteger expected");

        return uInteger;
    }

    // ------------------------------------------------ wrapper methods ------------------------------------------------

    public UIntegerValue add(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return new UIntegerValue(uInteger.add(v.uInteger));
    }

    public UIntegerValue minus(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return new UIntegerValue(uInteger.minus(v.uInteger));
    }

    public UIntegerValue mult(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return new UIntegerValue(uInteger.mult(v.uInteger));
    }

    public UIntegerValue divideBy(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return new UIntegerValue(uInteger.divideBy(v.uInteger));
    }

    public UIntegerValue mod(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return new UIntegerValue(uInteger.mod(v.uInteger));
    }

    public URealValue divideByR(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return new URealValue(uInteger.divideByR(v.uInteger));
    }

    public UIntegerValue abs() {
        return new UIntegerValue(uInteger.abs());
    }

    public UIntegerValue inverse() {
        return new UIntegerValue(uInteger.inverse());
    }

    public UIntegerValue neg() {
        return new UIntegerValue(uInteger.neg());
    }

    public UIntegerValue sqrt() {
        return new UIntegerValue(uInteger.sqrt());
    }

    public UIntegerValue power(Value value) {
        float exponent;

        if (!value.type().isKindOfReal(Type.VoidHandling.EXCLUDE_VOID))
            throw new RuntimeException("UInteger.power() : expected Real or Integer exponent value");

        if (value.isInteger())
            exponent = (float) ((IntegerValue) value).value();
        else
            exponent = (float) ((RealValue) value).value();

        return new UIntegerValue(uInteger.power(exponent));
    }

    public IntegerValue toInteger() {
        return IntegerValue.valueOf(uInteger.toInteger());
    }

    public RealValue toReal() {
        return new RealValue(uInteger.toReal());
    }

    public URealValue toUReal() {
        return new URealValue(uInteger.toUReal());
    }

    public UBooleanValue lt(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return UBooleanValue.valueOf(uInteger.lt(v.uInteger));
    }

    public UBooleanValue gt(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return UBooleanValue.valueOf(uInteger.gt(v.uInteger));
    }

    public UBooleanValue le(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return UBooleanValue.valueOf(uInteger.le(v.uInteger));
    }

    public UBooleanValue ge(Value value) {
        UIntegerValue v = assertKindOfUInteger(value);
        return UBooleanValue.valueOf(uInteger.ge(v.uInteger));
    }

}
