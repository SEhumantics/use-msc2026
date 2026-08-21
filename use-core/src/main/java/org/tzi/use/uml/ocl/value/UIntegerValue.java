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
 * The import of the uncertainty datatypes is edited: they were vendored into
 * org.tzi.use.uncertainty.datatypes rather than the original package `uDataTypes` (B1); see
 * docs/port2/stage-03-scope.md sec. 5.
 *
 * SEMANTICS ARE NOT UNCHANGED. This header said "Semantics unchanged" until 2026-08-20, which
 * documented the reverse of a binding user decision: B7 (2026-08-17) is that the port FIXES the
 * fork's defects rather than reproducing them bug-for-bug. The rows corrected in this file are
 * below, each justified in full at its own site, and each is a deliberate divergence from the
 * historical oracle:
 *
 *   F-10  hashCode() multiplied by Double.hashCode(uncertainty()), so every UInteger(n, 0)
 *         hashed to 0
 *   M-9   compareTo() delegated to the other operand without negating the sign
 *
 * See docs/port2/stage-09.md sec. 3 and docs/port2/b7-fix-plan.md.
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

    /**
     * Returns a hash code additive over value and uncertainty, so a zero uncertainty contributes
     * nothing to the hash rather than annihilating it — the same shape {@link URealValue#hashCode()}
     * uses.
     *
     * @implNote The fork multiplied the uncertainty term in: {@code hash *= 7 *
     *     Double.hashCode(uncertainty())}. Since {@code Double.hashCode(0.0) == 0}, that multiplication
     *     zeroed the entire hash whenever uncertainty was {@code 0} — every certain {@code
     *     UInteger(n, 0)}, for every {@code n}, the commonest value in the type, landed in the same
     *     bucket. This copies {@link URealValue#hashCode()}'s additive, zero-guarded body instead. No
     *     OCL-observable effect: {@code HashSet} still consults {@code equals} after bucketing so set
     *     membership is unchanged, and print order comes from {@code compareTo} via {@code
     *     Collections.sort}, not from the hash.
     * @see "docs/port2/b7-fix-plan.md &sect;2 F-10 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public int hashCode() {
        //return uInteger.hashCode();
        // for collections purposes, the follow equality must hold :
        // 1 = 1.0 = UReal(1, 0) = UInteger(1, 0).
        int hash = Double.hashCode(value());

        if (uncertainty() != 0)
            hash = hash * 7 + Double.hashCode(uncertainty());

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

    /**
     * Compares this value to {@code o}, ordering across {@link UIntegerValue}, {@link RealValue},
     * {@link IntegerValue}, and {@link URealValue}.
     *
     * @implNote The {@code URealValue} arm delegated as {@code res = o.compareTo(this);} without
     *     negating the sign, so it claimed {@code a < b} and {@code b < a} simultaneously. Negating
     *     alone would not have fixed it: {@link URealValue#compareTo(Value)} had no {@code
     *     UIntegerValue} arm at the time, so the delegated call fell through to a constant {@code 0}
     *     and {@code -0 == 0}. Both problems are fixed together — the sign is negated here, and the
     *     matching {@code UIntegerValue} arm now exists in {@link URealValue#compareTo(Value)} — since
     *     fixing either alone would have been a no-op dressed as a fix.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-9 &mdash; deviation ledger (decided 2026-08-17)"
     */
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
            res = -o.compareTo(this);

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

    /**
     * Narrows {@code value} to {@link UIntegerValue}, delegating to {@link #valueOf(Value)}.
     *
     * @implNote Deliberately throws the broad {@code RuntimeException} rather than narrowing to
     *     {@code IllegalArgumentException}: some callers ({@code ExpConstSBoolean}, {@code
     *     ASTSBooleanLiteral}) catch {@code Exception} generically and the full downstream catch set
     *     could not be enumerated, so narrowing risks silently changing behavior for no benefit.
     * @param value the value to coerce
     * @return {@code value} narrowed to {@code UInteger}
     * @throws RuntimeException if {@code value} is not a kind of {@code UInteger} (deliberately the
     *     broad type, see {@code @implNote})
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-6 &mdash; deviation ledger (decided 2026-08-17)"
     */
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
