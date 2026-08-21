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
     * @return {@code value} narrowed to {@code UString}
     * @throws RuntimeException if {@code value} is not a kind of {@code UString}. Deliberately the
     *         broad type: see the note above.
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
     * B7 / ledger M-11 — <strong>behaviour deliberately changed from the fork.</strong>
     *
     * <p>The fork's body was:
     * <pre>
     *   eq = wrapper.getString().equals(ustring.wrapper) &amp;&amp;
     *           wrapper.getsConf() == wrapper.getsConf();
     * </pre>
     * (fork {@code src/main/org/tzi/use/uml/ocl/value/UStringValue.java:79-91}), and it carries two
     * independent defects in one expression:
     * <ol>
     *   <li>{@code wrapper.getString()} is a {@code java.lang.String} and {@code ustring.wrapper} is a
     *       {@code UString}, so {@code String.equals(Object)} is {@code false} for <em>every</em>
     *       argument;</li>
     *   <li>the second conjunct compares the receiver's confidence <strong>to itself</strong> — the
     *       argument is never read.</li>
     * </ol>
     * Net effect: {@code equals} is the constant {@code false}. {@code a.equals(a)} is {@code false},
     * reflexivity is broken, and no {@code UStringValue} can be found in any {@code HashSet},
     * {@code HashMap} or {@code SetValue} — even though {@link #hashCode()} delegates correctly, so
     * the two were never consistent.
     *
     * <p>The port delegates to {@code UString.equals}, which compares the string and the confidence
     * (vendored {@code org.tzi.use.uncertainty.datatypes.UString}, from
     * {@code uDataTypes/UString.java:111-119}). That is the only body under which this class's own
     * {@code hashCode} is contract-correct.
     *
     * <p><strong>Declared consequence.</strong> {@link #valueOf(Value)} lifts a {@link StringValue}
     * to confidence {@code 1.0}, so the fix makes {@code UString('x', 1.0) = 'x'} evaluate
     * <em>true</em> where the fork gave <em>false</em>. {@link StringValue#equals(Object)} has no
     * {@code UStringValue} arm and is not edited here, so the relation stays asymmetric across the
     * String/UString boundary. That asymmetry is a declared residual, not an oversight —
     * {@code docs/port2/b7-fix-plan.md} section 7.2 item 2.
     *
     * <p>Decided by the user on 2026-08-17 (B7: fix the historical defects rather than reproduce them
     * bug-for-bug). Designed in {@code docs/port2/b7-fix-plan.md} section 1 C1.
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
     * B7 / ledger M-12 — <strong>behaviour deliberately changed from the fork</strong>, in one of the
     * two places the ledger names.
     *
     * <p><strong>What changed.</strong> The last line was
     * {@code wrapper.getString().compareTo(valueOf(o).toString())} (fork
     * {@code src/main/org/tzi/use/uml/ocl/value/UStringValue.java:103}). The receiver contributes its
     * bare string; the argument — already known to be a {@link StringValue} by the guard above —
     * contributes {@code valueOf(o).toString()}, which is the <em>wrapper rendering</em>
     * {@code UString('x', 1.0)}. So {@code UString('x',1) . compareTo('x')} compared {@code "x"}
     * against {@code "UString('x', 1.0)"} and answered a large negative number. Every plain string
     * sorts after every {@code UString}, whatever the strings are. Both sides now contribute their
     * bare string.
     *
     * <p><strong>What deliberately did not change.</strong> The guard is
     * {@code !(o instanceof StringValue)}, so a {@code UStringValue} argument does <em>not</em> reach
     * the line below — it is diverted to the {@code toString().compareTo(...)} route on the line
     * above, comparing two full wrapper renderings. That is odd, but it is total, self-consistent,
     * and orders {@code UString} against {@code UString} by string and then by confidence, which is a
     * defensible order. Widening the guard is a separate decision with its own consequences and is
     * not taken here. See {@code docs/port2/b7-fix-plan.md} section 2 M-12.
     *
     * <p><strong>Declared consequence.</strong> {@code SET} (order) — the sort position of a
     * {@code UString} relative to a plain {@code String}. No {@code .in} corpus entry contains a
     * {@code UString} token, so no recorded expectation moves.
     *
     * <p>Decided by the user on 2026-08-17 (B7).
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
