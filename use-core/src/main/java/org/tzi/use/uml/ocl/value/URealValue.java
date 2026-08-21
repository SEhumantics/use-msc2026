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
 * src/main/org/tzi/use/uml/ocl/value/URealValue.java.
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
 *   F-3   hashCode() hashed unrounded values that equals() compares rounded to 10 dp
 *   F-4   the IntegerValue and RealValue arms of equals() used raw == with no rounding
 *   M-10  equals() had no UIntegerValue arm, though UIntegerValue.equals delegates HERE
 *   M-3   compareTo()'s fourth arm was an unreachable duplicate of its first; the UIntegerValue
 *         case it was reaching for is now implemented (bundle A, with M-9)
 *
 * See docs/port2/stage-09.md sec. 3 and docs/port2/b7-fix-plan.md.
 */
package org.tzi.use.uml.ocl.value;

import org.tzi.use.uncertainty.datatypes.UBoolean;
import org.tzi.use.uncertainty.datatypes.UReal;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.util.MathUtil;

/**
 * URealValue is a wrapper of the real UReal witch is in the library of atenearesearchgroup.
 *
 * @author Víctor Manuel Ortiz Guardeño
 */

public class URealValue extends UncertainValue {

    private UReal uReal;

    public URealValue(double value, double uncertainty) {
        super(TypeFactory.mkUReal());
        uReal = new UReal(value, uncertainty);
    }

    public URealValue(UReal uReal) {
        super(TypeFactory.mkUReal());
        this.uReal = uReal;
    }

    public double value() {
        return uReal.getX();
    }

    public double uncertainty() {
        return uReal.getU();
    }

    @Override
    public boolean isUReal() {
        return true;
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
        // Sometimes Java set a negative zero to a double. This produces
        // a "-0.00", and for fix this, have to wrote the next line.
        double valueCorrected = value() == 0 ? 0 : value();
        sb.append(type())
                .append("(")
                .append(MathUtil.round(valueCorrected, 10))
                .append(", ")
                .append(MathUtil.round(uncertainty(), 10))
                .append(")");
        return sb;
    }

    /**
     * Returns a hash code consistent with the rounded comparison in {@link #equals(Object)}.
     *
     * @implNote The upstream fork hashed the unrounded value while {@code equals} compared it rounded
     *     to 10 decimals, violating the hashCode/equals contract. This rounds with the same {@code
     *     MathUtil.round(x, 10)} the {@code equals} arm uses, so the two stay in sync by construction.
     * @see "docs/port2/b7-fix-plan.md &sect;2 F-3 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public int hashCode() {
        int hash = Double.hashCode(MathUtil.round(value(), 10));
        double roundedUncertainty = MathUtil.round(uncertainty(), 10);

        if (roundedUncertainty != 0)
            hash = hash * 7 + Double.hashCode(roundedUncertainty);

        return hash;
    }

    /**
     * Returns whether this value equals {@code obj}, comparing numeric components rounded to 10
     * decimals and treating {@link UIntegerValue} as convertible via {@link UIntegerValue#toUReal()}.
     *
     * @implNote The fork compared the {@code IntegerValue}/{@code RealValue} arms unrounded
     *     (inconsistent with the {@code URealValue} arm three lines below) and had no {@code
     *     UIntegerValue} arm at all, even though {@link UIntegerValue#equals(Object)} delegates here
     *     — both fixed, so equality is now rounded and reflexive across types. Residual: {@link
     *     RealValue#equals(Object)} still has no {@code URealValue} arm and uses a different epsilon,
     *     so the Real/UReal relation remains asymmetric (tracked separately, not fixed here).
     * @see "docs/port2/b7-fix-plan.md &sect;2 F-4, M-10 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public boolean equals(Object obj) {
        boolean eq = false;

        if (obj instanceof Value) {

            if (obj instanceof URealValue) {
                // Avoiding the double precision, I have to round the values
                double thisValue = value(), otherValue = ((URealValue) obj).value();
                double thisUncertainty = uncertainty(), otherUncertainty = ((URealValue)obj).uncertainty();

                thisValue = MathUtil.round(thisValue, 10);
                otherValue = MathUtil.round(otherValue, 10);
                thisUncertainty = MathUtil.round(thisUncertainty, 10);
                otherUncertainty = MathUtil.round(otherUncertainty, 10);

                eq = thisValue == otherValue && thisUncertainty == otherUncertainty;
            }
            else if (obj instanceof UIntegerValue)
                // M-10. Lifted rather than compared field by field, so this arm cannot drift away
                // from the URealValue arm above it.
                eq = equals(((UIntegerValue) obj).toUReal());
            else if (obj instanceof IntegerValue)
                eq = MathUtil.round(value(), 10) == ((IntegerValue) obj).value()
                        && MathUtil.round(uncertainty(), 10) == 0;
            else if (obj instanceof RealValue)
                eq = MathUtil.round(value(), 10) == MathUtil.round(((RealValue) obj).value(), 10)
                        && MathUtil.round(uncertainty(), 10) == 0;

        }

        return eq;
    }

    /**
     * Compares this value to {@code o}, ordering across {@link RealValue}, {@link IntegerValue}, and
     * {@link UIntegerValue} by lifting the other operand to {@link UReal}.
     *
     * @implNote The fork's fourth arm was dead code — an unreachable duplicate of the first arm,
     *     guarding on {@code URealValue} where its body's own logic meant {@code UIntegerValue} — so
     *     no {@code UIntegerValue} case actually existed. Replaced with a real arm via {@link
     *     UIntegerValue#toUReal()}, the same route {@link #valueOf(Value)} and {@link #equals(Object)}
     *     take.
     * @see "docs/port2/b7-fix-plan.md &sect;7.1 bundle A, item M-3 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public int compareTo(Value o) {
        int res = 0;

        if (o instanceof URealValue)
            res = uReal.compareTo(((URealValue) o).uReal);
        else if (o instanceof RealValue)
            res = uReal.compareTo(new UReal(((RealValue) o).value()));
        else if (o instanceof IntegerValue)
            res = uReal.compareTo(new UReal(((IntegerValue) o).value()));
        else if (o instanceof UIntegerValue)
            res = uReal.compareTo(((UIntegerValue) o).toUReal().uReal);

        return res;
    }

    public static URealValue valueOf(Value value) {
        URealValue ur1;

        if (value.isReal())
            ur1 = new URealValue(((RealValue) value).value(), 0);
        else if (value.isInteger())
            ur1 = new URealValue(((IntegerValue) value).value(), 0);
        else if (value.isUInteger())
            ur1 = ((UIntegerValue) value).toUReal();
        else if (value.isUReal())
            ur1 = (URealValue) value;
        else
            ur1 = null;

        return ur1;
    }

    /**
     * Returns the uncertain degree to which this value equals {@code other}, as an {@link
     * UncertainBooleanValue} rather than a crisp {@code boolean}.
     *
     * @param other Value to compare.
     * @return {@code UBoolean(false, 1)} if {@code other} does not narrow to {@link URealValue}, else
     *     the {@link UBoolean} degree of equality delegated to the wrapped {@link UReal}.
     */

    @Override
    public UncertainBooleanValue uEquals(Value other) {

        URealValue uRealOther = valueOf(other);
        UBoolean result = null;

        if (uRealOther == null)
            result = new UBoolean(false, 1);
        else
            result = uReal.uEquals(uRealOther.uReal);

        return UBooleanValue.valueOf(result);
    }

    /**
     * Narrows {@code value} to {@link URealValue}, delegating to {@link #valueOf(Value)}.
     *
     * @implNote Deliberately throws the broad {@code RuntimeException} rather than narrowing to
     *     {@code IllegalArgumentException}: some callers ({@code ExpConstSBoolean}, {@code
     *     ASTSBooleanLiteral}) catch {@code Exception} generically and the full downstream catch set
     *     could not be enumerated, so narrowing risks silently changing behavior for no benefit.
     * @param value the value to coerce
     * @return {@code value} narrowed to {@code UReal}
     * @throws RuntimeException if {@code value} is not a kind of {@code UReal} (deliberately the
     *     broad type, see {@code @implNote})
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-6 &mdash; deviation ledger (decided 2026-08-17)"
     */
    private URealValue assertKindOfUReal(Value value) {
        URealValue uReal = valueOf(value);

        if (uReal == null)
            throw new RuntimeException("A value kind of UReal expected");

        return uReal;
    }


    // ----------------------------------------------- Wrapped method --------------------------------------------------

    public URealValue add(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return new URealValue(uReal.add(castedOther.uReal));
    }

    public URealValue minus(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return new URealValue(uReal.minus(castedOther.uReal));
    }

    public URealValue divideBy(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return new URealValue(uReal.divideBy(castedOther.uReal));
    }

    public URealValue mult(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return new URealValue(uReal.mult(castedOther.uReal));
    }

    public URealValue min(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return new URealValue(uReal.min(castedOther.uReal));
    }

    public URealValue max(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return new URealValue(uReal.max(castedOther.uReal));
    }

    public URealValue sin() {
        return new URealValue(uReal.sin());
    }

    public URealValue cos() {
        return new URealValue(uReal.cos());
    }

    public URealValue tan() {
        return new URealValue(uReal.tan());
    }

    public URealValue asin() {
        return new URealValue(uReal.asin());
    }

    public URealValue acos() {
        return new URealValue(uReal.acos());
    }

    public URealValue atan() {
        return new URealValue(uReal.atan());
    }

    public URealValue inverse() {
        return new URealValue(uReal.inverse());
    }

    public URealValue floor() {
        return new URealValue(uReal.floor());
    }

    public URealValue round() {
        return new URealValue(uReal.round());
    }

    public URealValue abs() {
        return new URealValue(uReal.abs());
    }

    public URealValue neg() {
        return new URealValue(uReal.neg());
    }

    public URealValue sqrt() {
        return new URealValue(uReal.sqrt());
    }

    public URealValue power(float value) {
        return new URealValue(uReal.power(value));
    }

    public RealValue toReal() {
        return new RealValue(uReal.toReal());
    }

    public IntegerValue toInteger() {
        return IntegerValue.valueOf(uReal.toInteger());
    }

    public UIntegerValue toUInteger() {
        return new UIntegerValue((int) value(), uncertainty());
    }

    public UBooleanValue lt(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return UBooleanValue.valueOf(uReal.lt(castedOther.uReal));
    }

    public UBooleanValue gt(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return UBooleanValue.valueOf(uReal.gt(castedOther.uReal));
    }

    public UBooleanValue le(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return UBooleanValue.valueOf(uReal.le(castedOther.uReal));
    }

    public UBooleanValue ge(Value other) {
        URealValue castedOther = assertKindOfUReal(other);
        return UBooleanValue.valueOf(uReal.ge(castedOther.uReal));
    }
}
