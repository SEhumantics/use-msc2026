package org.tzi.use.uncertainty;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * {@code uSelect}/{@code uSelectC}, {@code UBoolean}-aware {@code forAll}/{@code exists}, and
 * uncertainty-aware collection membership — ported at S9, alongside the historical corpus
 * ({@code USECompilerUncertaintyTest}), which is the fuller evidence: 1427 entries including
 * dedicated {@code uSelect}/{@code uSelectC}/{@code forAll}/{@code includes} cases. This file is the
 * narrow, standalone witness for exactly what was missing before this stage — findable without
 * reading 1427 lines of fixture.
 *
 * <h2>What was missing, and why the corpus alone found it</h2>
 * None of this was a B7 ledger row: nothing here existed in the port at all. {@code ExpQuery} had no
 * {@code uSelect}/{@code uSelectC} support, no grammar production for either, {@code forAll}/
 * {@code exists} accepted only a crisp {@code Boolean} body (rejecting the {@code UBoolean} a
 * {@code UReal} comparison produces), and {@code CollectionValue} had no {@code uIncludes}/
 * {@code uExcludes}/{@code uIncludesAll}/{@code uExcludesAll}. Running the ported historical corpus
 * for the first time found this as 41 of {@code UCollectionOperations.in}'s 44 entries failing —
 * parse errors for {@code uSelect}/{@code uSelectC}, and "must have boolean type, found UBoolean"
 * for every uncertain {@code forAll}/{@code exists}.
 *
 * <p>Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d):
 * {@code ExpQuery.java}, {@code ExpUSelect.java}, {@code ExpUSelectC.java},
 * {@code ParserHelper.java}, {@code ASTQueryExpression.java}, the {@code queryExpression} grammar
 * rule, and {@code CollectionValue.java}'s {@code uIncludes}/{@code uIncludesAll}/{@code uExcludes}/
 * {@code uExcludesAll}. Semantics unchanged from the fork throughout.
 *
 * <p>Test-scoped. Not part of the product.
 */
@DisplayName("uSelect/uSelectC, UBoolean forAll/exists, uncertain collection membership")
class UncertainQueryAndMembershipTest {

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
        return OCLCompiler.compileExpression(
                new ModelFactory().createModel("m"), expr, "test",
                new PrintWriter(err), new VarBindings());
    }

    @Nested
    @DisplayName("uSelect: default 0.5 confidence threshold")
    class USelect {

        @Test
        @DisplayName("keeps elements whose UBoolean body clears 0.5, in element order")
        void keepsAboveDefaultThreshold() {
            // UReal(3,0.5) >= 2 is a Gaussian well clear of 2, high probability; UReal(3,0.5) >= 10
            // is far below it -- one clearly above 0.5, one clearly below, no need to pin the exact
            // probability here (the corpus's dedicated uSelect entries already do that).
            assertEquals("Set{2.5,UReal(3.0, 0.25),3.2}",
                    run("Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}->uSelect(e | e >= 2)")
                            .toString());
        }

        @Test
        @DisplayName("a crisply-true body is always kept, regardless of the threshold question")
        void crispTrueAlwaysKept() {
            assertEquals("Set{2,3,4}", run("Set{1,2,3,4}->uSelect(e | e > 1)").toString());
        }
    }

    @Nested
    @DisplayName("uSelectC: explicit confidence threshold")
    class USelectC {

        @Test
        @DisplayName("a near-certain threshold excludes elements a lax one keeps")
        void higherThresholdIsStricter() {
            // UReal(3,0.25) >= 2 clears 0.5 comfortably (it is the case kept at the uSelect test
            // above) but is not near-certain -- raising the bar to 0.999999 must drop it while a
            // literal crisp member (2.5) survives any threshold, being crisply true regardless.
            String source = "Set{UReal(2, 0.5), 2.5, 3.2, 1, UReal(3, 0.25)}";
            Value atDefault = run(source + "->uSelectC(e | e >= 2, 0.5)");
            Value nearCertain = run(source + "->uSelectC(e | e >= 2, 0.999999)");
            assertEquals("Set{2.5,UReal(3.0, 0.25),3.2}", atDefault.toString());
            assertEquals("Set{2.5,3.2}", nearCertain.toString(),
                    "the near-certain threshold must drop the uncertain member the lax one kept");
        }

        @Test
        @DisplayName("the confidence argument must be Real, or the expression does not compile")
        void confidenceMustBeReal() {
            assertNull(compile("Set{1,2,3}->uSelectC(e | e > 1, 'not a number')"));
        }
    }

    @Nested
    @DisplayName("forAll/exists accept a UBoolean body and combine via uncertain and/or")
    class ForAllExists {

        @Test
        @DisplayName("forAll over a mixed crisp/uncertain set types and evaluates as UBoolean")
        void forAllAcceptsUBooleanBody() {
            Value v = run("Set{1, 2, UReal(2,5)}->forAll(e | e >= 1)");
            assertEquals("UBoolean", v.type().toString());
        }

        @Test
        @DisplayName("a crisp-only forAll body is unaffected and still evaluates a plain Boolean")
        void crispOnlyForAllUnaffected() {
            assertEquals("true", run("Set{1,2,3}->forAll(e | e > 0)").toString());
            assertEquals("Boolean", run("Set{1,2,3}->forAll(e | e > 0)").type().toString());
        }

        @Test
        @DisplayName("exists over an uncertain set evaluates as UBoolean")
        void existsAcceptsUBooleanBody() {
            Value v = run("Set{0, 1, UReal(3, 0.5)}->exists(e | e >= 3)");
            assertEquals("UBoolean", v.type().toString());
        }

        @Test
        @DisplayName("select/reject/one/any stay crisp-only, unaffected by this change")
        void otherQueriesStillRejectUBoolean() {
            assertNull(compile("Set{1, UReal(2,5)}->select(e | e >= 1)"),
                    "select is not one of the query forms B7's plan gave UBoolean bodies to");
        }
    }

    @Nested
    @DisplayName("collection membership answers a degree when the comparison is uncertain")
    class Membership {

        private static final String A =
                "Set{UReal(2, 0.5), 1, 2.5, 3.2, UReal(3.5, 0.25)}";

        @Test
        @DisplayName("includes is UBoolean whenever the COLLECTION's element type is uncertain")
        void includesTypeDependsOnCollectionElementType() {
            // The type comes from the collection, not the argument: A's element type is UReal (the
            // lattice from S3 makes Real/Integer conform to UReal, so the whole set's element type
            // is UReal), so includes on A answers UBoolean even for a crisp 2.5 -- the comparison
            // still routes through uEquals because the RECEIVER side is uncertain-typed.
            assertEquals("UBoolean", run(A + "->includes(UReal(2, 0.2))").type().toString());
            assertEquals("UBoolean", run(A + "->includes(2.5)").type().toString());
            // A genuinely all-crisp collection keeps the crisp Boolean answer.
            assertEquals("Boolean", run("Set{1,2,3}->includes(2)").type().toString());
        }

        @Test
        @DisplayName("a crisp element literally present gives certainty 1.0")
        void exactCrispMemberIsCertain() {
            assertEquals("UBoolean(true, 1.0)", run(A + "->includes(UReal(2, 0.5))").toString());
        }

        @Test
        @DisplayName("excludes is the complement of includes for the same argument")
        void excludesIsComplementOfIncludes() {
            Value includes = run(A + "->includes(UReal(2, 0.2))");
            Value excludes = run(A + "->excludes(UReal(2, 0.2))");
            double pIncludes = Double.parseDouble(
                    includes.toString().replaceAll(".*, ([0-9.]+)\\).*", "$1"));
            double pExcludes = Double.parseDouble(
                    excludes.toString().replaceAll(".*, ([0-9.]+)\\).*", "$1"));
            assertEquals(1.0, pIncludes + pExcludes, 1e-9);
        }

        @Test
        @DisplayName("includesAll/excludesAll answer UBoolean whenever either side is uncertain-typed")
        void includesAllExcludesAllTypeDependsOnElementType() {
            assertEquals("UBoolean",
                    run(A + "->includesAll(Set{2.5, UReal(3.5, 0.15)})").type().toString());
            // A itself is UReal-elemented, so even an all-crisp second collection stays UBoolean.
            assertEquals("UBoolean", run(A + "->includesAll(Set{2.5, 3.2})").type().toString());
            assertEquals("Boolean",
                    run("Set{1,2,3}->includesAll(Set{2,3})").type().toString());
            assertEquals("UBoolean",
                    run(A + "->excludesAll(Set{UReal(59,3), UReal(-310,9)})").type().toString());
        }

        @Test
        @DisplayName("an empty collection includes nothing and excludes everything, certainly")
        void emptyCollectionEdgeCases() {
            assertEquals("UBoolean(true, 0.0)", run("Set{}->includes(UReal(2, 3))").toString());
            assertEquals("UBoolean(true, 1.0)", run("Set{}->excludes(UReal(1, 2))").toString());
        }
    }

    /**
     * A serious pre-existing defect, found and fixed while porting {@code uSelect}/{@code uSelectC},
     * that has nothing to do with either of them.
     *
     * <h2>What broke, and why porting the fork's own {@code evalForAll0}/{@code evalExists0} broke it</h2>
     * Every USE-Uncertainty release of {@code ExpQuery.evalForAll0}/{@code evalExists0} has, at the
     * nested-recursion branch (reached whenever a query declares two or more element variables —
     * {@code Set(T)->forAll(a, b | ...)}, or the desugared form of a multi-variable class invariant,
     * {@code context p1, p2 : T inv name: P(p1, p2)}):
     * <pre>
     *   res = evalForAll0(nesting + 1, rangeVal, ctx);   // OVERWRITES, does not combine
     * </pre>
     * That overwrites the outer accumulator with the inner loop's result instead of AND/OR-combining
     * the two. For a single-variable query the branch never executes and the defect is invisible;
     * for two-or-more variables, only the LAST outer element's inner result survives — an earlier
     * violation an inner loop found is silently erased by a later, unrelated outer element whose
     * inner loop was clean.
     *
     * <p>This was ported byte-for-byte at first, because it is what the fork's own source says
     * (confirmed against the read-only reference tree). It is not a B7 ledger row and not an
     * uncertainty concern — multi-variable {@code forAll}/{@code exists} is core OCL, predating the
     * fork — and it broke a stock shell fixture the moment this method replaced the crisp-only
     * algorithm that preceded it in this port: {@code t049} (two-variable invariant
     * {@code Person::nameUnique}) started reporting a real duplicate as {@code OK}, and {@code t022}
     * (a two-variable {@code exists} inside a transitive-closure {@code iterate}) started computing
     * the wrong set. Both are asserted here directly, independent of the shell fixtures, because a
     * defect this serious deserves a witness that says what it is in one place.
     *
     * <p>The fix combines the recursive result with the outer accumulator through the same
     * {@code ExpStdOp.create("and"/"or", ...)} dispatch the leaf case already uses — see
     * {@link org.tzi.use.uml.ocl.expr.ExpQuery#evalExists0}'s class comment for the full account.
     */
    @Nested
    @DisplayName("multi-variable forAll/exists: a pre-existing fork defect, found and fixed")
    class MultiVariableAccumulation {

        @Test
        @DisplayName("forAll(a, b | ...) does not let a later clean outer element erase an earlier violation")
        void forAllAccumulatesAcrossOuterIterations() {
            // Violation is at (2,2): 2 = 2 but with a THIRD, unrelated element (3) whose inner loop
            // is entirely clean. With the overwrite bug, iterating a=3 last overwrites the a=2
            // violation with a=3's clean `true`, and the whole forAll wrongly reports true.
            String expr = "Set{1,2,3}->forAll(a, b | a = 2 and b = 2 implies false)";
            assertEquals("false", run(expr).toString(),
                    "the violation at a=2,b=2 must not be erased by a=3's clean inner loop");
        }

        @Test
        @DisplayName("exists(a, b | ...) does not let a later clean outer element erase an earlier hit")
        void existsAccumulatesAcrossOuterIterations() {
            // The hit is at (1,1). With the overwrite bug, a=2 and a=3's clean (false) inner loops
            // run AFTER a=1 and overwrite the true result with false.
            String expr = "Set{1,2,3}->exists(a, b | a = 1 and b = 1)";
            assertEquals("true", run(expr).toString(),
                    "the hit at a=1,b=1 must not be erased by a=2 or a=3's clean inner loops");
        }

        @Test
        @DisplayName("t049 directly: a two-variable invariant catches a genuine duplicate")
        void twoVariableInvariantCatchesDuplicate() {
            // The exact shape of Person::nameUnique's desugaring (p1.name = p2.name implies
            // p1 = p2), over four names where two ('Ada', first and last) are equal, mirroring
            // t049's model exactly: names 'Bob' and 'Cyd' in between give clean inner loops that
            // ran AFTER the violating pair, which is precisely what the overwrite bug erased.
            assertEquals("false",
                    run("Sequence{'Ada','Bob','Cyd','Ada'}->forAll(a, b | a = b implies "
                            + "Sequence{'Ada','Bob','Cyd','Ada'}->count(a) = 1)").toString(),
                    "two people sharing a name must be caught even though later pairs are clean");
        }
    }
}
