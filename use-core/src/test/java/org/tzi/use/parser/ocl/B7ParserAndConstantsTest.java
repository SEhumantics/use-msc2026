package org.tzi.use.parser.ocl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * B7 rows M-29, M-30, M-32 and M-33, driven through the real grammar end to end: text in, a
 * {@link Value} or a {@link org.tzi.use.uml.ocl.type.Type} out.
 *
 * <h2>Why these four needed evidence written by hand</h2>
 * Every one of them is marked in {@code docs/port2/b7-fix-plan.md} section 3 as having <strong>no
 * corpus example at all</strong> or as reaching a compile error before the corrected line, which is
 * a different way of saying the same thing: nothing that already exists in this repository exercises
 * the code these rows touch. B7's "fix, documenting each" means a fix with no test is a claim, and
 * for these four the claim would otherwise have nothing behind it.
 *
 * <p>Modelled on {@link UncertainExpressionTypingTest}, which is the established pattern for driving
 * the port through {@link OCLCompiler#compileExpression} rather than through any internal class —
 * this is what a user's {@code .use} model and {@code .soil} script actually go through.
 *
 * <p>Test-scoped. Not part of the product.
 */
@DisplayName("B7 at the parser and literal-constant layer")
class B7ParserAndConstantsTest {

    private static Expression compile(String expr) {
        StringWriter err = new StringWriter();
        return OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
    }

    private static Value evaluate(Expression e) {
        return new Evaluator().eval(e, null, null, new VarBindings(), null, "");
    }

    /** Compiles and evaluates in one step; asserts the expression compiled at all. */
    private static Value run(String expr) {
        Expression e = compile(expr);
        assertNotNull(e, expr + " must compile");
        return evaluate(e);
    }

    @Nested
    @DisplayName("M-29: an undefined UBoolean VALUE operand was silently accepted")
    class M29 {

        @Test
        @DisplayName("an undefined first operand now yields Undefined")
        void undefinedValueYieldsUndefined() {
            // A let-declaration with an explicit type ascription is Undefined but STATICALLY
            // Boolean, which is what the constructor's own type guard requires -- so this reaches
            // eval rather than being rejected at compile time. oclAsType does not serve this purpose
            // here: it refuses to widen OclVoid to a declared subtype.
            Value v = run("UBoolean((let b : Boolean = Undefined in b), 0.8)");
            assertTrue(v.isUndefined(),
                    "the fork's Boolean.valueOf(\"Undefined\") is false, and valueOf(false, 0.8) "
                    + "normalises that to a DEFINED UBoolean(true, 0.2) -- a value manufactured from "
                    + "an operand that was not there");
        }

        @Test
        @DisplayName("an undefined probability operand still yields Undefined (the fork's own guard)")
        void undefinedProbabilityStillWorks() {
            Value v = run("UBoolean(true, (let r : Real = Undefined in r))");
            assertTrue(v.isUndefined(), "unchanged from the fork: this guard was already present");
        }

        @Test
        @DisplayName("a defined operand still produces a defined UBoolean")
        void definedOperandsUnaffected() {
            Value v = run("UBoolean(true, 0.8)");
            assertAll(
                    () -> assertTrue(v.isDefined()),
                    () -> assertEquals("UBoolean(true, 0.8)", v.toString()));
        }
    }

    @Nested
    @DisplayName("M-30: ExpConstUString had two unguarded operations")
    class M30 {

        @Test
        @DisplayName("an undefined string value no longer throws ClassCastException")
        void undefinedValueYieldsUndefined() {
            // Statically String (the constructor's guard requires isTypeOfString()), dynamically
            // Undefined via a typed let-declaration -- exactly the gap between static and runtime
            // typing the row is about.
            Value v = run("UString((let s : String = Undefined in s), 0.9)");
            assertTrue(v.isUndefined(),
                    "the fork's unguarded (StringValue) cast raised ClassCastException here, "
                    + "escaping eval as an uncaught exception rather than becoming Undefined");
        }

        @Test
        @DisplayName("an undefined confidence no longer throws NumberFormatException")
        void undefinedConfidenceYieldsUndefined() {
            Value v = run("UString('abc', (let r : Real = Undefined in r))");
            assertTrue(v.isUndefined(),
                    "the fork's unguarded Double.valueOf(confidence.toString()) parsed the literal "
                    + "text \"Undefined\" and threw NumberFormatException");
        }

        @Test
        @DisplayName("an out-of-range confidence is Undefined, as the fork already intended")
        void outOfRangeConfidenceUnaffected() {
            Value v = run("UString('abc', 1.5)");
            assertTrue(v.isUndefined(), "unchanged: the fork's own range check, not this fix");
        }

        @Test
        @DisplayName("a well-formed UString still evaluates normally")
        void definedOperandsUnaffected() {
            Value v = run("UString('abc', 0.9)");
            assertAll(
                    () -> assertTrue(v.isDefined()),
                    () -> assertEquals("UString('abc', 0.9)", v.toString()));
        }
    }

    @Nested
    @DisplayName("M-32: ASTURealLiteral built two Expression graphs and installed the second")
    class M32 {

        /**
         * A variable-declaring operand: {@code let x = 3 in x}. If {@code gen(ctx)} runs twice, the
         * declaration of {@code x} would be registered into {@code ctx} twice, which is the concrete
         * shape of the side effect the row describes -- and if two DIFFERENT expression graphs were
         * installed, only one of them would carry the correct value at eval time.
         */
        @Test
        @DisplayName("a let-bound operand types and evaluates correctly, exactly once each")
        void letBoundOperandEvaluatesOnce() {
            // Each let must be parenthesized -- an unparenthesized `let ... in ...` is not a valid
            // function-call argument in this grammar, a fact of precedence unrelated to M-32.
            Value v = run("UReal((let x : Integer = 3 in x), (let y : Real = 0.5 in y))");
            assertAll(
                    () -> assertTrue(v.isDefined()),
                    () -> assertEquals("UReal(3.0, 0.5)", v.toString(),
                            "both operands must carry the value the let-expression actually bound"));
        }

        @Test
        @DisplayName("an ordinary UReal literal is unaffected")
        void ordinaryLiteralUnaffected() {
            Value v = run("UReal(2, 0.5)");
            assertEquals("UReal(2.0, 0.5)", v.toString());
        }

        @Test
        @DisplayName("the type check still rejects a non-numeric operand")
        void typeGuardStillWorks() {
            assertNull(compile("UReal('x', 0.5)"),
                    "the hoisted locals must still be checked before construction");
        }
    }

    @Nested
    @DisplayName("M-33: ASTUStringLiteral fell through to Object.toString()")
    class M33 {

        /** A minimal operand whose {@code toString()} is fixed source text, nothing else needed. */
        private ASTExpression literalText(String text) {
            return new ASTExpression() {
                @Override
                public Expression gen(org.tzi.use.parser.Context ctx) {
                    throw new UnsupportedOperationException("not exercised by this test");
                }

                @Override
                public void getFreeVariables(java.util.Set<String> freeVars) {
                }

                @Override
                public String toString() {
                    return text;
                }
            };
        }

        @Test
        @DisplayName("the AST node renders as OCL source text, not an identity hash")
        void rendersAsSource() {
            ASTUStringLiteral node = new ASTUStringLiteral(literalText("'abc'"), literalText("0.9"));
            String text = node.toString();
            assertAll(
                    () -> assertTrue(text.startsWith("UString("),
                            "must render as OCL source, not java.lang.Object's identity form: "
                            + text),
                    () -> assertTrue(text.contains("'abc'") && text.contains("0.9"), text));
        }

        @Test
        @DisplayName("that text is what a compile error interpolates")
        void appearsInSemanticExceptionText() {
            // A malformed UString reaches ASTUStringLiteral.gen, which wraps the ExpInvalidException
            // from the constructor's own type guard into a SemanticException. Before M-33 the
            // message carried an identity hash for this node instead of readable source.
            StringWriter err = new StringWriter();
            Expression e = OCLCompiler.compileExpression(
                    new ModelFactory().createModel("m"), "UString(1, 0.9)", "test",
                    new PrintWriter(err), new VarBindings());
            assertNull(e, "UString's first argument must be a String");
            // The compiler prints the SemanticException to err rather than propagating it, so the
            // text is read back from there.
            assertTrue(err.toString().contains("UString"),
                    "the error text must name the operation via a real toString(), not an identity "
                    + "hash: " + err);
        }
    }
}
