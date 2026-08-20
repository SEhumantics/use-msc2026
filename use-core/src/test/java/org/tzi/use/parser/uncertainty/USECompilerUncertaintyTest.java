package org.tzi.use.parser.uncertainty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tzi.use.config.Options;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ported from USE-Uncertainty (github.com/atenearesearchgroup/uncertainty @ 74acd0d),
 * src/test/org/tzi/use/parser/uncertainty/USECompilerUncertaintyTest.java. Replays the fork's
 * 1427-entry historical corpus ({@code UBooleanExpression.in}, {@code UCollectionOperations.in},
 * {@code UIntegerExpression.in}, {@code URealExpression.in}) through the port's own OCL compiler
 * and evaluator, asserting each entry's expected printed result exactly.
 *
 * <p>Modernized from the fork's JUnit 3 {@code TestCase} to JUnit 5, with these deliberate
 * departures from the original, none of which changes what is asserted:
 * <ul>
 * <li>corpus resolution is by classpath resource ({@link Class#getResource}) rather than a
 * {@code user.dir}-relative filesystem path, so the test runs the same way under Maven/Surefire
 * regardless of the working directory the build was launched from (CF-8);</li>
 * <li>the corpus files are read as {@link StandardCharsets#UTF_8} explicitly rather than the
 * platform default charset (CF-9);</li>
 * <li>{@link Options#explicitVariableDeclarations} is saved before and restored after the test in
 * {@code @BeforeEach}/{@code @AfterEach} rather than being set once and left mutated for every
 * later test in the same JVM (M-45);</li>
 * <li>{@code ExpressionTest} is a small private static final class with an explicit
 * {@code toString()} (used in assertion failure messages), not a Java record — a record's
 * generated {@code toString()} would print the field names as well as the values, changing every
 * failure message's shape for no semantic reason (M-48b);</li>
 * <li>the compiler-error path splits the captured stderr on {@code \r?\n} and takes the last
 * non-blank line, rather than the fork's {@code split("\n(\r\n)")} — that pattern is not a
 * line-separator alternation (parentheses are a capturing group, not alternation; {@code |} is
 * missing), so on any platform it does not actually split multi-line output, and the fork's own
 * {@code errArray[errArray.length - 1]} silently degenerates to the whole captured string (M-49b);</li>
 * <li>{@code IOException} while opening a corpus file is wrapped in a {@code RuntimeException}
 * that preserves the original exception as its cause, rather than discarding it (M-51).</li>
 * </ul>
 */
class USECompilerUncertaintyTest {

    private static final boolean VERBOSE = true;

    private boolean savedExplicitVariableDeclarations;

    @BeforeEach
    void saveOptions() {
        savedExplicitVariableDeclarations = Options.explicitVariableDeclarations;
        Options.explicitVariableDeclarations = false;
    }

    @AfterEach
    void restoreOptions() {
        Options.explicitVariableDeclarations = savedExplicitVariableDeclarations;
    }

    private static final class ExpressionTest {
        String expression;
        String expected;

        @Override
        public String toString() {
            return expression + "\n\t-> " + expected;
        }
    }

    private static final class StringOutputStream extends OutputStream {
        private StringBuilder fBuffer = new StringBuilder();

        @Override
        public void write(int b) {
            fBuffer.append((char) b);
        }

        void reset() {
            fBuffer = new StringBuilder();
        }

        @Override
        public String toString() {
            return fBuffer.toString();
        }
    }

    /**
     * The fork discovers this corpus by listing every {@code .in} file in the test's own source
     * directory (a plain {@code File.listFiles}). That approach does not survive the module system:
     * under a named module (this project's {@code use.core}), Surefire runs tests with test classes
     * and resources patched into the module via {@code --patch-module}, and a relative directory
     * listing through {@link Class#getResource(String) getResource(".")} resolves to {@code null} in
     * that configuration — confirmed by running exactly that call here before this fix. The corpus is
     * a small, fixed set of four files, not something that grows between runs, so CF-8 is addressed by
     * naming them explicitly and resolving each by classpath resource lookup (which does work under
     * the patched module, and works identically were this ever packaged into a jar), rather than by
     * a directory listing.
     */
    private static final String[] CORPUS_FILES = {
            "UBooleanExpression.in",
            "UCollectionOperations.in",
            "UIntegerExpression.in",
            "URealExpression.in",
    };

    @Test
    void testUncertaintyExpression() throws IOException {

        MModel model = new ModelFactory().createModel("Test");

        StringOutputStream sos = new StringOutputStream();
        PrintWriter pw = new PrintWriter(sos);

        System.out.println("-----------------------------------------------------------------");
        System.out.println("It's going to be executed " + CORPUS_FILES.length + " test files.");

        for (String testFileName : CORPUS_FILES) {
            System.out.println("-----------------------------------------------------------------");
            System.out.println("File : " + testFileName);

            InputStream resource = getClass().getResourceAsStream(testFileName);
            assertTrue(resource != null, "corpus resource " + testFileName + " not found on the classpath");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {

                ExpressionTest expTest = readExpressionLine(in);
                while (expTest != null) {

                    if (VERBOSE) {
                        System.out.println("\tExpression : \n" +
                                "\t\t" + expTest.expression + "\n" +
                                "\t\t-> " + expTest.expected);
                        System.out.println();
                    }

                    Value result = executeExpression(model, pw, expTest, testFileName);

                    if (result == null) {
                        String errMessage = lastNonBlank(sos.toString().split("\r?\n"));
                        assertEquals(expTest.expected, errMessage, "evaluate : " + expTest);
                        sos.reset();
                    } else {
                        assertEquals(expTest.expected, result.toStringWithType(), "evaluate : " + expTest.expression);
                    }

                    expTest = readExpressionLine(in);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Couldn't open file " + testFileName, ex);
            }
        }
    }

    private static String lastNonBlank(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isEmpty()) {
                return lines[i];
            }
        }
        return "";
    }

    private ExpressionTest readExpressionLine(BufferedReader in) throws IOException {
        ExpressionTest expTest = new ExpressionTest();
        String line;
        StringBuilder expressionBuilder = new StringBuilder();

        line = in.readLine();
        while (line != null && (expTest.expression == null || expTest.expected == null)) {
            line = line.trim();

            if (line.length() != 0 && !line.startsWith("#")) {

                if (expTest.expression == null) {

                    if (line.startsWith("->"))
                        throw new RuntimeException("missing expression");

                    if (!line.endsWith("\\")) {
                        expressionBuilder.append(line);
                        expTest.expression = expressionBuilder.toString().replace("\t", " ");
                    } else {
                        // The corpus's line-continuation marker is a literal DOUBLE backslash
                        // ("\\", verified with `cat -A`), not a single one — length()-2 strips
                        // both, matching the fork's own reader exactly
                        // (USE-Uncertainty USECompilerUncertaintyTest.java:128).
                        expressionBuilder.append(line, 0, line.length() - 2).append('\n');
                    }

                } else {

                    if (!line.startsWith("->"))
                        throw new RuntimeException("missing expected result line");

                    expTest.expected = line.substring(3);
                }
            }

            if (expTest.expected == null)
                line = in.readLine();
        }

        if (expTest.expected == null || expTest.expression == null)
            expTest = null; // End of file

        return expTest;
    }

    private Value executeExpression(MModel model, PrintWriter pwErr, ExpressionTest expressionTest, String testPath) {
        InputStream stream = new ByteArrayInputStream(expressionTest.expression.getBytes(StandardCharsets.UTF_8));
        Value result = null;
        Expression expr =
                OCLCompiler.compileExpression(
                        model,
                        stream,
                        testPath,
                        pwErr,
                        new VarBindings()
                );

        if (expr != null) {
            MSystemState systemState = new MSystem(model).state();
            result = new Evaluator().eval(expr, systemState);
        }

        return result;
    }
}
