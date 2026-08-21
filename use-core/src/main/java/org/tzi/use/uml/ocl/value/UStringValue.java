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
 * src/main/org/tzi/use/uml/ocl/value/UStringValue.java.
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
 *   M-11  equals() was the constant false: String.equals(UString) can never hold, and the second
 *         conjunct compared the receiver's confidence to itself
 *   M-12  compareTo() compared the receiver's bare string against the argument's WRAPPER
 *         rendering, so every plain String sorted after every UString
 *
 * See docs/port2/stage-09.md sec. 3 and docs/port2/b7-fix-plan.md.
 */
package org.tzi.use.uml.ocl.value;

import org.tzi.use.uncertainty.datatypes.UString;
import org.tzi.use.uml.ocl.type.TypeFactory;

import java.util.List;

public class UStringValue extends UncertainValue {

    private UString wrapper;

    public UStringValue(UString ustring) {
        super(TypeFactory.mkUString());
        this.wrapper = ustring;
    }

    public UStringValue(String str, double uncertainty) {
        this(new UString(str, uncertainty));
    }

    /**
     * Cast the argument to UString, returns null if cannot be casted.
     * @param value
     * @return
     */

    public static UStringValue valueOf(Value value) {
        UStringValue ustring = null;

        if (value instanceof StringValue)
            ustring = new UStringValue(((StringValue) value).value(), 1);
        else if (value instanceof UStringValue)
            ustring = (UStringValue) value;

        return ustring;
    }

    /**
     * Narrows {@code value} to {@link UStringValue}, delegating to {@link #valueOf(Value)}.
     *
     * @implNote Deliberately throws the broad {@code RuntimeException} rather than narrowing to
     *     {@code IllegalArgumentException}: some callers ({@code ExpConstSBoolean}, {@code
     *     ASTSBooleanLiteral}) catch {@code Exception} generically and the full downstream catch set
     *     could not be enumerated, so narrowing risks silently changing behavior for no benefit.
     * @param value the value to coerce
     * @return {@code value} narrowed to {@code UString}
     * @throws RuntimeException if {@code value} is not a kind of {@code UString} (deliberately the
     *     broad type, see {@code @implNote})
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-6 &mdash; deviation ledger (decided 2026-08-17)"
     */
    private UStringValue assertKindOfUString(Value value) {
        UStringValue ustring = valueOf(value);

        if (ustring == null)
            throw new RuntimeException("A value kind of UString expected");

        return ustring;
    }

    @Override
    public UncertainBooleanValue uEquals(Value other) {
        UStringValue ustring = valueOf(other);
        UBooleanValue result = null;

        if (ustring == null)
            result = UBooleanValue.FALSE;
        else
            result = UBooleanValue.valueOf(wrapper.uEquals(ustring.wrapper));

        return result;
    }

    @Override
    public StringBuilder toString(StringBuilder sb) {
        return sb.append("UString('")
                .append(wrapper.getString()).append("', ")
                .append(wrapper.getsConf()).append(")");
    }

    @Override
    public int hashCode() {
        return wrapper.hashCode();
    }

    /**
     * Returns whether this value equals {@code obj}, comparing the wrapped string and confidence via
     * {@code UString.equals} when {@code obj} narrows to {@link UStringValue}.
     *
     * @implNote The fork compared {@code wrapper.getString().equals(ustring.wrapper)} — a {@code
     *     java.lang.String} against a {@code UString}, always {@code false} — conjoined with {@code
     *     wrapper.getsConf() == wrapper.getsConf()}, which compared the receiver's confidence to
     *     itself and never read the argument. Net effect: {@code equals} was the constant {@code
     *     false}, so {@code a.equals(a)} was {@code false} and no {@code UStringValue} could be found
     *     in a {@code HashSet} even though {@link #hashCode()} delegated correctly. Now delegates to
     *     {@code UString.equals}, comparing string and confidence, the only body under which {@code
     *     hashCode} is contract-correct. Consequence: {@link #valueOf(Value)} lifts a {@link
     *     StringValue} to confidence {@code 1.0}, so {@code UString('x', 1.0) = 'x'} now evaluates
     *     true; {@link StringValue#equals(Object)} still has no {@code UStringValue} arm, so the
     *     relation stays asymmetric across that boundary (a declared residual, not an oversight).
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-11 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public boolean equals(Object obj) {
        boolean eq = false;

        if (obj instanceof Value) {
            UStringValue ustring = valueOf((Value) obj);

            if (ustring != null)
                eq = wrapper.equals(ustring.wrapper);

        }

        return eq;
    }

    /**
     * Compares this value to {@code o}: against a {@link StringValue} it compares the two bare
     * strings, and against anything else (including another {@link UStringValue}) it falls back to a
     * {@code toString()} comparison so the order stays total.
     *
     * @implNote The {@code StringValue} arm used to compare the receiver's bare string against the
     *     argument's <em>wrapper rendering</em>, {@code valueOf(o).toString()} (e.g. {@code
     *     "UString('x', 1.0)"} rather than {@code "x"}), so every plain string sorted after every
     *     {@code UString} regardless of content. Fixed to compare both sides' bare strings. The guard
     *     {@code !(o instanceof StringValue)} deliberately still diverts {@code UStringValue}
     *     arguments to the {@code toString()} route above — that is odd but total and self-consistent,
     *     ordering {@code UString} against {@code UString} by string then confidence; widening the
     *     guard is a separate, unmade decision with its own consequences.
     * @see "docs/port2/b7-fix-plan.md &sect;2 M-12 &mdash; deviation ledger (decided 2026-08-17)"
     */
    @Override
    public int compareTo(Value o) {
        if (o == this )
            return 0;
        if (o instanceof UndefinedValue )
            return +1;
        if (! (o instanceof StringValue) )
            return toString().compareTo(o.toString());

        return wrapper.getString().compareTo(((StringValue) o).value());
    }


    //----------------------------------------------- métodos wrappper------------------------------------------------

    public String value() {
        return wrapper.getString();
    }

    public double confidence() {
        return wrapper.getsConf();
    }

    public UBooleanValue ge(Value other) {
        UStringValue ustring = assertKindOfUString(other);
        return UBooleanValue.valueOf(wrapper.ge(ustring.wrapper));
    }

    public UBooleanValue lt(Value other) {
        UStringValue ustring = assertKindOfUString(other);
        return UBooleanValue.valueOf(wrapper.lt(ustring.wrapper));
    }

    public UBooleanValue gt(Value other) {
        UStringValue ustring = assertKindOfUString(other);
        return UBooleanValue.valueOf(wrapper.gt(ustring.wrapper));
    }

    public UBooleanValue le(Value other) {
        UStringValue ustring = assertKindOfUString(other);
        return UBooleanValue.valueOf(wrapper.le(ustring.wrapper));
    }

    public StringValue at(int index) {
        return new StringValue(wrapper.at(index));
    }

    public UStringValue uAt(int index) {
        return new UStringValue(wrapper.uAt(index));
    }

    public BooleanValue toBoolean() {
        return BooleanValue.get(wrapper.toBoolean());
    }

    public IntegerValue toInteger() {
        return IntegerValue.valueOf(wrapper.toInteger());
    }

    public RealValue toReal() {
        return new RealValue(wrapper.toReal());
    }

    public StringValue uToString() {
        return new StringValue(wrapper.uToString());
    }

    public UBooleanValue toUBoolean() {
        return UBooleanValue.valueOf(wrapper.uToUBoolean());
    }

    public SequenceValue uCharacters() {
        List<UString> sequence = wrapper.uCharacters();
        Value [] result = new Value[sequence.size()];

        for (int i = 0 ; i < sequence.size() ; i++)
            result[i] = new UStringValue(sequence.get(i));

        return new SequenceValue(TypeFactory.mkUString(), result);
    }

    public UStringValue uConcat(Value other) {
        UStringValue ustring = assertKindOfUString(other);
        return new UStringValue(wrapper.uConcat(ustring.wrapper));
    }

    public UBooleanValue uEqualsIgnoreCase(Value other) {
        UStringValue ustring = assertKindOfUString(other);
        return UBooleanValue.valueOf(wrapper.uEqualsIgnoreCase(ustring.wrapper));
    }

    public UIntegerValue uSize() {
        return new UIntegerValue(wrapper.uSize());
    }

    public UStringValue uSubstring(int lower, int upper) {
        return new UStringValue(wrapper.uSubstring(lower, upper));
    }

    public UStringValue uToLowerCase() {
        return new UStringValue(wrapper.uToLowerCase());
    }

    public UStringValue uToUpperCase() {
        return new UStringValue(wrapper.uToUpperCase());
    }

    public IntegerValue indexOf(StringValue string) {
        return IntegerValue.valueOf(wrapper.indexOf(string.value()));
    }


}
