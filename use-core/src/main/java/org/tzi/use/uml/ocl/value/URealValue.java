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
     * B7 / ledger F-3 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork hashed the <em>unrounded</em> {@code value()} and {@code uncertainty()} while
     * {@link #equals(Object)} three lines below compares them <em>rounded to ten decimals</em> (fork
     * {@code src/main/org/tzi/use/uml/ocl/value/URealValue.java:56-64} against {@code :67-91}). Two
     * values that {@code equals} calls equal could therefore land in different buckets, which is the
     * {@code hashCode}/{@code equals} contract violated in the direction that actually loses data:
     * {@code HashSet} never consults {@code equals} across buckets, so the set silently holds both.
     *
     * <p>This body rounds with the same {@code MathUtil.round(x, 10)} the {@code equals} arm uses, in
     * the same order, so the two are textually parallel and a future edit to one is visibly an edit
     * to the other. Note the guard tests the <em>rounded</em> uncertainty: {@code equals} treats
     * {@code 1e-15} as {@code 0}, so a hash that distinguished them would reintroduce the same defect
     * at a smaller scale.
     *
     * <p><strong>Declared consequence.</strong> {@code SET} — membership only, and only for pairs
     * differing beyond the tenth decimal. No corpus element has more than ten decimals, so no
     * recorded expectation moves. Print order is unaffected: it comes from {@code Collections.sort}
     * via {@link #compareTo(Value)}, not from the hash.
     *
     * <p>Decided by the user on 2026-08-17 (B7). Designed in
     * {@code docs/port2/b7-fix-plan.md} section 2 F-3, and bundled with M-10 per section 7.1
     * bundle B — M-10 creates new equal pairs across {@code UIntegerValue}/{@code URealValue}, and
     * new equal pairs that hash apart are a worse defect than the one being fixed.
     */
    @Override
    public int hashCode() {
        //return uReal.hashCode();
        int hash = Double.hashCode(MathUtil.round(value(), 10));
        double roundedUncertainty = MathUtil.round(uncertainty(), 10);

        if (roundedUncertainty != 0)
            hash = hash * 7 + Double.hashCode(roundedUncertainty);

        return hash;
    }

    /**
     * B7 / ledger F-4 and M-10 — <strong>behaviour deliberately changed from the fork</strong>, in
     * two places.
     *
     * <p><strong>F-4.</strong> The {@code IntegerValue} and {@code RealValue} arms compared with raw
     * {@code ==} and no rounding (fork
     * {@code src/main/org/tzi/use/uml/ocl/value/URealValue.java:84,87}), three lines below a
     * {@code URealValue} arm that rounds both operands to ten decimals for exactly the reason the
     * comment there gives. Same class, same method, same question, two different answers. Both
     * cross-type arms now round, matching the arm above.
     *
     * <p><strong>M-10.</strong> There was no {@code UIntegerValue} arm at all. That mattered beyond
     * this class, because {@link UIntegerValue#equals(Object)} <em>delegates here</em> for a
     * {@code URealValue} argument ({@code UIntegerValue.java:84-86}) — into an arm list that has no
     * case for it, so it fell through to {@code false}. Cross-type {@code UInteger(2,5) = UReal(2,5)}
     * answered {@code false} in both directions while every component was equal. The new arm lifts
     * through {@link UIntegerValue#toUReal()} and then applies the same rounded comparison, so the
     * two types agree by construction rather than by two hand-written bodies that must be kept in
     * step.
     *
     * <p><strong>Declared consequence.</strong> {@code VALUE} and {@code SET}, and F-4's direction is
     * one-way: rounding can only <em>add</em> equalities, never remove one, so {@code false} may
     * become {@code true} and {@code true} can never become {@code false}.
     *
     * <p><strong>Declared residual.</strong> {@code RealValue.equals} still has no
     * {@code URealValue} arm and still uses {@code FloatUtil.equals} with an epsilon of 1e-8, so the
     * relation remains asymmetric across the Real/UReal boundary. {@code RealValue} is edited only to
     * add {@code valueOf} (E26) and is not in scope here. See
     * {@code docs/port2/b7-fix-plan.md} section 7.2 item 1.
     *
     * <p>Decided by the user on 2026-08-17 (B7). Designed in
     * {@code docs/port2/b7-fix-plan.md} section 2 F-4 and M-10; M-10 is bundled with F-3 above per
     * section 7.1 bundle B.
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
     * B7 / ledger M-3 and bundle A — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork's fourth arm was an unreachable duplicate:
     * <pre>
     *   else if (o instanceof URealValue) {          // shadowed by the first arm
     *       URealValue uReal = (URealValue) o;
     *       res = uReal.compareTo(new UIntegerValue((int) uReal.value(), uReal.uncertainty()));
     *   }
     * </pre>
     * (fork {@code src/main/org/tzi/use/uml/ocl/value/URealValue.java:95-110}). Its <em>body</em>
     * builds a {@code UIntegerValue}, so what it was reaching for is plain: the guard says
     * {@code URealValue} where it meant {@code UIntegerValue}, and the first arm swallows every input
     * that could ever reach it. The arm is dead code, and the case it was meant to handle has no
     * implementation.
     *
     * <p>That absence is what makes M-9 unfixable on its own. {@link UIntegerValue#compareTo(Value)}
     * delegates here for a {@code URealValue} argument; with no {@code UIntegerValue} arm the
     * delegation falls through to the {@code res = 0} initialiser, so the composite is a constant
     * {@code 0} and negating it — the whole of M-9's one-character fix — changes nothing. The two
     * land together or the ledger row is discharged falsely; see
     * {@code docs/port2/b7-fix-plan.md} section 7.1 bundle A.
     *
     * <p>The replacement arm lifts through {@link UIntegerValue#toUReal()}, the same route
     * {@link #valueOf(Value)} and {@link #equals(Object)} take, rather than casting the receiver down
     * to an integer as the dead body did — that would have compared {@code UReal(2.7, u)} as
     * {@code 2}.
     *
     * <p><strong>Declared consequence.</strong> {@code SET} (order).
     *
     * <p>Decided by the user on 2026-08-17 (B7).
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
     * TODO: better description
     *
     * @param other Value to compare.
     * @return
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
     * This method ensure that the value is kind of UReal and return this value (casted)
     * @param value
     * @return An UReal value.
     */

    /**
     * B7 / ledger M-6 — <strong>DECIDED NOT TO CHANGE, and that decision is the fix.</strong>
     *
     * <p>The recommendation this row considered was narrowing {@code RuntimeException} to
     * {@code IllegalArgumentException}, on the ordinary grounds that a bare {@code RuntimeException}
     * is the least informative exception type available. It was not taken.
     *
     * <p><strong>Why not.</strong> {@code ExpQueryUncertaintyTest.java:179,200} catches
     * {@code RuntimeException} — a subclass would still satisfy that {@code catch}, so those two
     * sites are safe either way. But {@code ExpConstSBoolean.java:57} and
     * {@code ASTSBooleanLiteral.java:35} both {@code catch (Exception ex)} and swallow it, silently
     * converting whatever escapes into {@code Undefined} or a discarded error — and the full
     * downstream {@code catch} set reachable from this method <strong>could not be enumerated</strong>.
     * A narrower type is {@code ERR}-shaped risk with no offsetting benefit: nothing in this codebase
     * discriminates {@code RuntimeException} from {@code IllegalArgumentException}, so narrowing
     * could only ever change behaviour by accident, never on purpose.
     *
     * <p>Decided by the user on 2026-08-17 (B7); {@code docs/port2/b7-fix-plan.md} section 2 M-6.
     *
     * @param value the value to coerce
     * @return {@code value} narrowed to {@code UReal}
     * @throws RuntimeException if {@code value} is not a kind of {@code UReal}. Deliberately the
     *         broad type: see the note above.
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
