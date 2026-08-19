package org.tzi.use.parser.soil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tzi.use.TestSystem;
import org.tzi.use.uml.sys.soil.MStatement;
import org.tzi.use.util.soil.VariableEnvironment;

/**
 * Guards a defect that only became visible when the grammar grew.
 *
 * <p>{@code ASTIterationStatement} warned about iterating a non-ordered collection by comparing the
 * range's token against {@code SoilLexer.T__44} and {@code T__48}, with the comment "44 is Bag, 48 is
 * Set". Those constants are assigned by ANTLR in grammar order, so adding the five uncertain literals
 * renumbered them: {@code T__44}/{@code T__48} came to mean {@code Sequence}, and USE began warning
 * that {@code for x in Sequence{1..9}} iterates a non-ordered collection. It does not. Shell test
 * t086 caught it.
 *
 * <p>Nothing had ever exercised the warning — no shell test iterates a Bag or Set literal and none
 * asserts the message — which is exactly why hard-coded token numbers could rot unnoticed. This test
 * exists so that cannot happen again: it pins both directions, the false positive AND the true one.
 */
public class IterationWarningTokenRotTest {

    private static final String WARNING = "Iteration over a non-ordered collection";

    /** Compiles a soil statement and returns whatever it wrote to {@code System.out}. */
    private String compileCapturingStdout(String statement) throws Exception {
        TestSystem testSystem = new TestSystem();
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            StringWriter err = new StringWriter();
            MStatement s = SoilCompiler.compileStatement(
                    testSystem.getSystem().model(),
                    testSystem.getState(),
                    new VariableEnvironment(testSystem.getState()),
                    statement, "test", new PrintWriter(err), false);
            if (s == null) {
                throw new AssertionError("the statement did not compile: " + statement
                        + " -- " + err);
            }
        } finally {
            System.setOut(original);
        }
        return captured.toString("UTF-8");
    }

    @Test
    @DisplayName("a Sequence range must NOT warn -- this is the regression the grammar exposed")
    void sequenceRangeDoesNotWarn() throws Exception {
        String out = compileCapturingStdout("for x in Sequence{1..9} do end");
        assertFalse(out.contains(WARNING),
                "Sequence is ordered; warning about it is the T__44/T__48 token-number rot: " + out);
    }

    @Test
    @DisplayName("a Bag or Set range must STILL warn -- the fix must not have deleted the warning")
    void bagAndSetRangesStillWarn() throws Exception {
        assertTrue(compileCapturingStdout("for x in Bag{1,2} do end").contains(WARNING),
                "the warning is upstream 7.5.0 behaviour and must survive the fix");
        assertTrue(compileCapturingStdout("for x in Set{1,2} do end").contains(WARNING),
                "the warning is upstream 7.5.0 behaviour and must survive the fix");
    }
}
