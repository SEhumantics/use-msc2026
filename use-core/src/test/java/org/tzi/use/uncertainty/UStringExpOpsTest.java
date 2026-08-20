package org.tzi.use.uncertainty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.parser.ocl.OCLCompiler;

/**
 * All 20 {@code UString} operations ({@code StandardOperationsUString.java}), exercised through the
 * real grammar rather than direct Java calls to {@code Op_uString_*}/{@code UStringValue}. Not a B7
 * ledger row: found untested by an independent audit at S9 (this stage), and confirmed against the
 * fork's own test suite, which has no {@code UStringValueTest}/{@code UStringExpOpsTest} at all —
 * unlike {@code UBoolean}/{@code UReal}/{@code UInteger}, none of which are missing a test class.
 * {@code specification.md} §6.5 records the same gap on the corpus side: no {@code .in} fixture
 * contains a {@code UString} token, so this file is UString's only OCL-level evidence.
 *
 * <p>Two operations required a fix before they could be given a meaningful test at all —
 * {@code indexOf} and {@code toString} both declared a static type of {@code UString} in their
 * {@code matches()} while their {@code eval()} always returned an {@code IntegerValue}/
 * {@code StringValue}. Fixed the same way as the already-closed ledger row M-37 (a declared-vs-
 * actual return type correction, TYPE only). See the fix-site comments in
 * {@code StandardOperationsUString.java}.
 */
@DisplayName("UString: all 20 operations")
class UStringExpOpsTest {

    private static Value run(String expr) {
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
        if (e == null) {
            throw new IllegalStateException(expr + " did not compile: " + err);
        }
        return new Evaluator().eval(e, null, null, new VarBindings(), null, "");
    }

    private static Expression compile(String expr) {
        StringWriter err = new StringWriter();
        Expression e = OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
        if (e == null) {
            throw new IllegalStateException(expr + " did not compile: " + err);
        }
        return e;
    }

    @Nested
    @DisplayName("accessors: value, confidence")
    class Accessors {

        @Test
        @DisplayName("value returns the bare crisp String")
        void value() {
            assertEquals("'hello'", run("UString('hello', 0.9).value()").toString());
        }

        @Test
        @DisplayName("confidence returns the crisp Real")
        void confidence() {
            assertEquals("0.9", run("UString('hello', 0.9).confidence()").toString());
        }
    }

    @Nested
    @DisplayName("mutators: setValue, setConfidence")
    class Mutators {

        @Test
        @DisplayName("setValue replaces the string, keeps the confidence")
        void setValue() {
            assertEquals("UString('bye', 0.9)",
                    run("UString('hello', 0.9).setValue('bye')").toString());
        }

        @Test
        @DisplayName("setConfidence replaces the confidence, keeps the string")
        void setConfidence() {
            assertEquals("UString('hello', 0.3)",
                    run("UString('hello', 0.9).setConfidence(0.3)").toString());
        }
    }

    @Nested
    @DisplayName("character access: at, character, size")
    class CharacterAccess {

        @Test
        @DisplayName("at returns the character at an index, wrapped as UString")
        void at() {
            Value v = run("UString('hello', 0.9).at(1)");
            assertEquals("UString", v.type().toString());
        }

        @Test
        @DisplayName("character returns a Sequence of single-character UStrings")
        void character() {
            Value v = run("UString('ab', 0.9).character()");
            assertEquals("Sequence(UString)", v.type().toString());
        }

        @Test
        @DisplayName("size returns a UInteger")
        void size() {
            Value v = run("UString('hello', 0.9).size()");
            assertEquals("UInteger", v.type().toString());
        }
    }

    @Nested
    @DisplayName("concatenation, indexOf, substring")
    class StringOps {

        @Test
        @DisplayName("uConcat (+) joins two UStrings")
        void uConcat() {
            Value v = run("UString('ab', 0.9) + UString('cd', 0.8)");
            assertEquals("UString", v.type().toString());
        }

        @Test
        @DisplayName("indexOf: fixed declared type (M-37-class), now statically Integer")
        void indexOf() {
            Expression e = compile("UString('hello', 0.9).indexOf('l')");
            assertEquals("Integer", e.type().toString(),
                    "before the fix this was UString despite eval() always returning an Integer");
            assertEquals("2", run("UString('hello', 0.9).indexOf('l')").toString());
        }

        @Test
        @DisplayName("indexOf's corrected static type lets its result feed a crisp Integer context")
        void indexOfResultUsableAsInteger() {
            // Would not compile before the fix: '+' has no UString/Integer overload.
            assertEquals("3", run("UString('hello', 0.9).indexOf('l') + 1").toString());
        }

        @Test
        @DisplayName("substring extracts the given range (1-indexed lower, exclusive-upper on the raw string)")
        void substring() {
            assertEquals("UString('hel', 0.9)",
                    run("UString('hello', 0.9).substring(1,3)").toString());
        }
    }

    @Nested
    @DisplayName("case conversion")
    class CaseConversion {

        @Test
        @DisplayName("toLowerCase")
        void toLowerCase() {
            assertEquals("UString('hello', 0.9)",
                    run("UString('HELLO', 0.9).toLowerCase()").toString());
        }

        @Test
        @DisplayName("toUpperCase")
        void toUpperCase() {
            assertEquals("UString('HELLO', 0.9)",
                    run("UString('hello', 0.9).toUpperCase()").toString());
        }
    }

    @Nested
    @DisplayName("conversions: toString, toInteger, toReal, toBoolean, toUBoolean")
    class Conversions {

        @Test
        @DisplayName("toString: fixed declared type (M-37-class), now statically String")
        void toStringOp() {
            Expression e = compile("UString('hello', 0.9).toString()");
            assertEquals("String", e.type().toString(),
                    "before the fix this was UString despite eval() always returning a String");
            assertEquals("'hello'", run("UString('hello', 0.9).toString()").toString());
        }

        /**
         * Also the regression test for the null-instead-of-Undefined crash fixed alongside the two
         * M-37-class type fixes above (see StandardOperationsUString.java's Op_uString_toInteger
         * comment): {@code Integer.parseInt}/{@code Double.parseDouble} DO throw on a non-numeric
         * string, unlike {@code toBoolean}/{@code toUBoolean} below, so toInteger/toReal are the two
         * operations whose failure path was actually reachable and previously crashed.
         */
        @Test
        @DisplayName("toInteger succeeds on a numeric string, fails (Undefined, not a crash) otherwise")
        void toInteger() {
            assertEquals("3", run("UString('3', 0.9).toInteger()").toString());
            assertTrue(run("UString('hello', 0.9).toInteger()").isUndefined());
            // Before the fix this NPE'd inside ExpStdOp.eval instead of evaluating to Undefined.
            assertTrue(run("1 + UString('hello', 0.9).toInteger()").isUndefined());
        }

        @Test
        @DisplayName("toReal succeeds on a numeric string, fails (Undefined, not a crash) otherwise")
        void toReal() {
            assertEquals("3.5", run("UString('3.5', 0.9).toReal()").toString());
            assertTrue(run("UString('hello', 0.9).toReal()").isUndefined());
        }

        /**
         * Unlike toInteger/toReal, {@code UString.toBoolean()} delegates to
         * {@code Boolean.parseBoolean}, which never throws: any string that is not (case-
         * insensitively) {@code "true"} simply parses as {@code false}. There is no failure path to
         * observe here.
         */
        @Test
        @DisplayName("toBoolean: true only for (case-insensitive) 'true', false for every other string")
        void toBoolean() {
            assertEquals("true", run("UString('true', 0.9).toBoolean()").toString());
            assertEquals("true", run("UString('TRUE', 0.9).toBoolean()").toString());
            assertEquals("false", run("UString('hello', 0.9).toBoolean()").toString());
        }

        /**
         * {@code UString.uToUBoolean()} also never throws: it measures string similarity to "TRUE"
         * and "FALSE" and falls back to {@code UBoolean(true, 0.5)} (maximum uncertainty) when
         * neither is a good match, rather than failing.
         */
        @Test
        @DisplayName("toUBoolean: never fails, falls back to maximum uncertainty for an unrelated string")
        void toUBoolean() {
            assertEquals("UBoolean(true, 0.9)", run("UString('true', 0.9).toUBoolean()").toString());
            assertEquals("UBoolean(true, 0.5)", run("UString('hello', 0.9).toUBoolean()").toString());
        }
    }

    @Nested
    @DisplayName("ordering: <, <=, >, >=")
    class Ordering {

        @Test
        @DisplayName("< / <= / > / >= all answer UBoolean; the confidence is the product of both operands' (0.9*0.9=0.81)")
        void ordering() {
            // UBoolean is always canonicalised to a "true" state with an attached probability (see
            // M-3, docs/port2/stage-09.md sec 4.3m) -- UBoolean(true, 0.19) below means "true with
            // probability 0.19", i.e. mostly false, not a literal false.
            Value lt = run("UString('abc', 0.9) < UString('abd', 0.9)");
            Value gt = run("UString('abc', 0.9) > UString('abd', 0.9)");
            Value le = run("UString('abc', 0.9) <= UString('abc', 0.9)");
            Value ge = run("UString('abc', 0.9) >= UString('abc', 0.9)");
            assertEquals("UBoolean", lt.type().toString());
            assertEquals("UBoolean(true, 0.81)", lt.toString());
            assertEquals("UBoolean(true, 0.19)", gt.toString());
            assertEquals("UBoolean(true, 0.81)", le.toString());
            assertEquals("UBoolean(true, 0.81)", ge.toString());
        }
    }
}
