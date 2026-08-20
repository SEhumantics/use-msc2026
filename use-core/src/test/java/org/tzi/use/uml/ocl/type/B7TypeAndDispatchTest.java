package org.tzi.use.uml.ocl.type;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;

/**
 * B7 rows at the type and dispatch layers, plus one porting omission found alongside them.
 *
 * <p>Everything here is invisible to the differential harness by construction: it drives methods on
 * <em>value</em> classes, and none of these are that. {@code harness-contract.md} §C3 states the
 * limit outright — "the differential harness cannot see the type layer" — so these rows were always
 * going to need purpose-built evidence, and B7's "fix, documenting each" means a fix with no test is
 * a claim.
 *
 * <h2>Why this file lives in {@code org.tzi.use.uml.ocl.type}</h2>
 * Because it has to, and that is M-22's evidence. The first draft sat in
 * {@code org.tzi.use.uncertainty} and did not compile: five errors, one per uncertain type, all
 * reading <em>"{@code URealType()} is not public ... cannot be accessed from outside package"</em>.
 * A reflective modifier check is asserted below as well, but the compiler's refusal is the stronger
 * statement, and it is recorded here because a reader of the reflective assertion would otherwise
 * have no way to know it had ever been tested against a real call site.
 *
 * <p>Test-scoped. Not part of the product.
 */
@DisplayName("B7 at the type and dispatch layers")
class B7TypeAndDispatchTest {

    @Nested
    @DisplayName("M-21: a directly-constructed type was missing from its own supertype set")
    class M21 {

        /**
         * The five uncertain types, constructed <em>directly</em> rather than taken from
         * {@link TypeFactory}. This is the population the row is about: for a factory singleton
         * {@code this} and {@code mkX()} are the same object, so the fork's inconsistency was
         * invisible. {@code TypeTest} constructs them directly at {@code :380-403}, and so does this.
         */
        private List<Type> directlyConstructed() {
            return List.of(new URealType(), new UIntegerType(), new UBooleanType(),
                    new UStringType(), new SBooleanType());
        }

        @Test
        @DisplayName("every uncertain type is among its own supertypes")
        void selfIsASupertype() {
            for (Type t : directlyConstructed()) {
                assertTrue(t.allSupertypes().contains(t),
                        t + " constructed directly is not in its own allSupertypes(): "
                        + t.allSupertypes());
            }
        }

        @Test
        @DisplayName("and therefore conforms to itself")
        void conformsToItself() {
            for (Type t : directlyConstructed()) {
                assertTrue(t.conformsTo(t), t + " does not conform to itself");
            }
        }

        @Test
        @DisplayName("the factory singletons are unaffected")
        void singletonsUnchanged() {
            assertAll(
                    () -> assertTrue(TypeFactory.mkUReal().allSupertypes()
                            .contains(TypeFactory.mkUReal())),
                    () -> assertTrue(TypeFactory.mkUInteger().allSupertypes()
                            .contains(TypeFactory.mkUReal()),
                            "UInteger still conforms to UReal, which is the lattice edge that matters"),
                    () -> assertTrue(TypeFactory.mkUBoolean().allSupertypes()
                            .contains(TypeFactory.mkSBoolean()),
                            "and UBoolean still conforms to SBoolean"));
        }
    }

    @Nested
    @DisplayName("M-22: every uncertain type's constructor is package-private")
    class M22 {

        @Test
        @DisplayName("none of the five is publicly constructible")
        void allPackagePrivate() {
            // The fork had three different visibilities across five sibling types: UIntegerType's
            // constructor was PUBLIC, URealType's PROTECTED, the other three package-private. The
            // port is uniform, and TypeFactory is the only intended route.
            //
            // Asserted reflectively rather than by reading the source, so the uniformity is a
            // property the suite holds rather than a thing someone once checked. See the class
            // comment for the stronger statement: this file could not be compiled outside this
            // package at all.
            for (Class<?> c : List.of(URealType.class, UIntegerType.class, UBooleanType.class,
                    UStringType.class, SBooleanType.class)) {
                for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                    int m = ctor.getModifiers();
                    assertTrue(!java.lang.reflect.Modifier.isPublic(m)
                                    && !java.lang.reflect.Modifier.isProtected(m),
                            c.getSimpleName() + " has a constructor wider than package-private; "
                            + "TypeFactory is the only intended route (b7-fix-plan.md section 2 M-22)");
                }
            }
        }
    }

    @Nested
    @DisplayName("porting omission: VoidType had no isKindOfU* overrides")
    class VoidTypeOverrides {

        @Test
        @DisplayName("OclVoid is a kind of every uncertain type under INCLUDE_VOID")
        void includeVoid() {
            Type v = TypeFactory.mkVoidType();
            assertAll(
                    () -> assertTrue(v.isKindOfUReal(VoidHandling.INCLUDE_VOID)),
                    () -> assertTrue(v.isKindOfUInteger(VoidHandling.INCLUDE_VOID)),
                    () -> assertTrue(v.isKindOfUBoolean(VoidHandling.INCLUDE_VOID)),
                    () -> assertTrue(v.isKindOfUString(VoidHandling.INCLUDE_VOID)),
                    () -> assertTrue(v.isKindOfSBoolean(VoidHandling.INCLUDE_VOID)));
        }

        @Test
        @DisplayName("and of none of them under EXCLUDE_VOID")
        void excludeVoid() {
            Type v = TypeFactory.mkVoidType();
            assertAll(
                    () -> assertFalse(v.isKindOfUReal(VoidHandling.EXCLUDE_VOID)),
                    () -> assertFalse(v.isKindOfUInteger(VoidHandling.EXCLUDE_VOID)),
                    () -> assertFalse(v.isKindOfUBoolean(VoidHandling.EXCLUDE_VOID)),
                    () -> assertFalse(v.isKindOfUString(VoidHandling.EXCLUDE_VOID)),
                    () -> assertFalse(v.isKindOfSBoolean(VoidHandling.EXCLUDE_VOID)));
        }

        @Test
        @DisplayName("which is exactly how it already behaved for the crisp types")
        void matchesTheCrispTypes() {
            // The point of the row: the omission made OclVoid answer differently for Integer and for
            // UInteger, so `Undefined` could be passed where an Integer was expected and refused
            // where a UInteger was.
            Type v = TypeFactory.mkVoidType();
            assertAll(
                    () -> assertEquals(v.isKindOfInteger(VoidHandling.INCLUDE_VOID),
                            v.isKindOfUInteger(VoidHandling.INCLUDE_VOID)),
                    () -> assertEquals(v.isKindOfReal(VoidHandling.INCLUDE_VOID),
                            v.isKindOfUReal(VoidHandling.INCLUDE_VOID)),
                    () -> assertEquals(v.isKindOfBoolean(VoidHandling.INCLUDE_VOID),
                            v.isKindOfUBoolean(VoidHandling.INCLUDE_VOID)),
                    () -> assertEquals(v.isKindOfString(VoidHandling.INCLUDE_VOID),
                            v.isKindOfUString(VoidHandling.INCLUDE_VOID)));
        }
    }

    @Nested
    @DisplayName("M-38: `or` on two undefined UBooleans threw NullPointerException")
    class M38 {

        /**
         * A UBoolean-typed expression that evaluates to {@code Undefined}.
         *
         * <p>This is the whole difficulty of witnessing M-38, and it is worth saying why.
         * {@code ExpressionWithValue(UndefinedValue.instance)} has <em>static type</em>
         * {@code OclVoid}, and {@code Op_boolean_or} is registered before {@code Op_uBoolean_or}
         * ({@code OpGeneric.java:90} against {@code :94}), matches {@code (OclVoid, OclVoid)} under
         * {@code INCLUDE_VOID}, and {@code ExpStdOp.create} stops at the first match. So the obvious
         * witness never reaches the code M-38 is about — which is exactly why the four corpus
         * entries that write {@code Undefined or Undefined} pass today and would pass with the NPE
         * still in place.
         *
         * <p>{@code ExpUndefined(Type)} carries a declared type, so a pair of these dispatches to
         * {@code Op_uBoolean_or} and evaluates to {@code Undefined} on both operands, which is the
         * branch that dereferenced null.
         */
        private org.tzi.use.uml.ocl.expr.Expression undefinedUBoolean() {
            return new org.tzi.use.uml.ocl.expr.ExpUndefined(TypeFactory.mkUBoolean());
        }

        @Test
        @DisplayName("it now yields Undefined instead of throwing")
        void orOnTwoUndefinedOperands() throws Exception {
            org.tzi.use.uml.ocl.expr.ExpStdOp or = org.tzi.use.uml.ocl.expr.ExpStdOp.create("or",
                    new org.tzi.use.uml.ocl.expr.Expression[] {
                            undefinedUBoolean(), undefinedUBoolean() });

            assertAll(
                    () -> assertSame(TypeFactory.mkUBoolean(), or.type(),
                            "it must be Op_uBoolean_or that matched, not Op_boolean_or -- if this "
                            + "says Boolean, the witness missed the code under test entirely"),
                    () -> assertTrue(evaluate(or).isUndefined(),
                            "the fork dereferenced the null that UBooleanValue.valueOf returns for "
                            + "an UndefinedValue, and threw NullPointerException out of eval"));
        }

        @Test
        @DisplayName("the sibling that already had the guard is unchanged")
        void andWasAlreadyCorrect() throws Exception {
            // Op_uBoolean_and guards this at :411-414. Asserted so the fix is visibly the ONE
            // missing guard rather than a change of policy about undefined operands.
            org.tzi.use.uml.ocl.expr.ExpStdOp and = org.tzi.use.uml.ocl.expr.ExpStdOp.create("and",
                    new org.tzi.use.uml.ocl.expr.Expression[] {
                            undefinedUBoolean(), undefinedUBoolean() });
            assertTrue(evaluate(and).isUndefined());
        }

        @Test
        @DisplayName("a defined operand still produces a defined result")
        void definedOperandsUnaffected() throws Exception {
            org.tzi.use.uml.ocl.expr.Expression t = new org.tzi.use.uml.ocl.expr.ExpressionWithValue(
                    org.tzi.use.uml.ocl.value.UBooleanValue.valueOf(true, 1.0));
            org.tzi.use.uml.ocl.expr.ExpStdOp or = org.tzi.use.uml.ocl.expr.ExpStdOp.create("or",
                    new org.tzi.use.uml.ocl.expr.Expression[] { t, undefinedUBoolean() });
            assertTrue(evaluate(or).isDefined(),
                    "UBoolean(true, 1) or Undefined short-circuits on the first operand");
        }

        private org.tzi.use.uml.ocl.value.Value evaluate(
                org.tzi.use.uml.ocl.expr.Expression e) {
            return e.eval(new org.tzi.use.uml.ocl.expr.EvalContext(
                    null, null, new org.tzi.use.uml.ocl.value.VarBindings(), null, ""));
        }
    }

    @Nested
    @DisplayName("M-37: UInteger.value() declared a static type its eval never returns")
    class M37 {

        @Test
        @DisplayName("value() and toInteger() are statically Integer, matching what eval returns")
        void staticTypeIsInteger() throws Exception {
            // ExpStdOp.create is the production path: the parser calls it, and it stores what
            // matches() returns as the expression's STATIC type.
            // ExpStdOp.create stores what matches() returns as the expression's STATIC type, so this
            // is what type-checking of any ENCLOSING expression is performed against. The fork
            // declared UInteger here while eval returned an IntegerValue.
            org.tzi.use.uml.ocl.value.Value ui =
                    new org.tzi.use.uml.ocl.value.UIntegerValue(3, 0.5);
            assertAll(
                    () -> assertSame(TypeFactory.mkInteger(), staticTypeOf("value", ui),
                            "value : UInteger -> Integer, as the comment above the class always said"),
                    () -> assertSame(TypeFactory.mkInteger(), staticTypeOf("toInteger", ui),
                            "toInteger is the same operation registered under a second name"));
        }

        @Test
        @DisplayName("the sibling it was inconsistent with is unchanged")
        void urealValueUnchanged() throws Exception {
            assertSame(TypeFactory.mkReal(),
                    staticTypeOf("value", new org.tzi.use.uml.ocl.value.URealValue(3.0, 0.5)),
                    "Op_ureal_value already declared mkReal(); it is the model M-37 follows");
        }

        /**
         * The static type OCL gives {@code name} applied to {@code receiver}, obtained through the
         * production dispatch path — {@code ExpStdOp.create}, which is what the parser calls and
         * which stores {@code matches()}'s answer as the expression's type. Reflecting into
         * {@code OpGeneric} would have tested the registry; this tests what a model sees.
         */
        private Type staticTypeOf(String name, org.tzi.use.uml.ocl.value.Value receiver)
                throws Exception {
            org.tzi.use.uml.ocl.expr.Expression arg =
                    new org.tzi.use.uml.ocl.expr.ExpressionWithValue(receiver);
            return org.tzi.use.uml.ocl.expr.ExpStdOp
                    .create(name, new org.tzi.use.uml.ocl.expr.Expression[] {arg})
                    .type();
        }
    }
}
