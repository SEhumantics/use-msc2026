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
     * B7 / ledger F-10 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork's body was:
     * <pre>
     *   int hash = Double.hashCode(value());
     *   hash *= 7 * Double.hashCode(uncertainty());
     * </pre>
     * (fork {@code src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java:56-64}). {@code
     * Double.hashCode(0.0)} is {@code 0}, so the multiplication annihilates the whole hash: <em>every
     * certain</em> {@code UInteger(n, 0)} hashes to {@code 0}, for every {@code n}. That is the
     * single worst bucket distribution available, and it is reached by the commonest value in the
     * type.
     *
     * <p>The sibling {@link URealValue#hashCode()} already has the correct shape — additive, and
     * guarded so a zero uncertainty contributes nothing rather than destroying everything — and this
     * body is that one, applied here. The comment the fork left above the code
     * ("{@code 1 = 1.0 = UReal(1, 0) = UInteger(1, 0)}") states the intent the multiplicative form
     * cannot deliver; the additive form does.
     *
     * <p><strong>Declared consequence.</strong> {@code NONE} observable through OCL.
     * {@code HashSet} consults {@code equals} after bucketing, so set <em>contents</em> do not move,
     * and print order comes from {@code Collections.sort} via {@code compareTo}, not from the hash
     * ({@code SetValue.java:319-323}). What changes is the value of the public {@code hashCode()}
     * itself, and the bucket layout it implies.
     *
     * <p><strong>Declared residual.</strong> {@code IntegerValue.hashCode} and
     * {@code RealValue.hashCode} are not aligned with the uncertain classes, so this fix delivers
     * {@code UReal(1,0)} and {@code UInteger(1,0)} hashing alike but not {@code 1} hashing with them
     * — the fork's own comment above is still not fully honoured. {@code IntegerValue} is outside the
     * uncertainty surface and is not edited. See {@code docs/port2/b7-fix-plan.md} section 7.2 item 3.
     *
     * <p>Decided by the user on 2026-08-17 (B7). Designed in
     * {@code docs/port2/b7-fix-plan.md} section 1 C2.
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
     * B7 / ledger M-9 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The {@code URealValue} arm read {@code res = o.compareTo(this);} (fork
     * {@code src/main/org/tzi/use/uml/ocl/value/UIntegerValue.java:103-104}), delegating to the other
     * operand's comparator <strong>without negating the sign</strong>. A comparator that answers
     * {@code a.compareTo(b) == b.compareTo(a)} claims {@code a &lt; b} and {@code b &lt; a}
     * simultaneously.
     *
     * <p><strong>Why the obvious one-character fix is not the fix.</strong> Negating alone changes
     * nothing, because the value being negated is a constant {@code 0}:
     * {@link URealValue#compareTo(Value)} has arms for {@code URealValue}, {@code RealValue} and
     * {@code IntegerValue} and none for {@code UIntegerValue}, so a {@code UIntegerValue} argument
     * falls through every one of them and returns {@code 0} — and {@code -0 == 0}. M-9 applied on its
     * own would discharge the ledger row while leaving the behaviour exactly as it was, which is
     * worse than leaving it open. The matching {@code UIntegerValue} arm is therefore added to
     * {@link URealValue#compareTo(Value)} in the same commit; see
     * {@code docs/port2/b7-fix-plan.md} section 7.1 bundle A.
     *
     * <p><strong>Declared consequence.</strong> {@code SET} (order). Sort position of a
     * {@code UIntegerValue} relative to a {@code URealValue} in any printed collection.
     *
     * <p>Decided by the user on 2026-08-17 (B7). Designed in
     * {@code docs/port2/b7-fix-plan.md} section 2 M-9.
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
     * @return {@code value} narrowed to {@code UInteger}
     * @throws RuntimeException if {@code value} is not a kind of {@code UInteger}. Deliberately the
     *         broad type: see the note above.
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
