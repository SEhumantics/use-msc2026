package org.tzi.use.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

/**
 * Replays the historical USE-Uncertainty compiler corpus
 * ({@code USECompilerUncertaintyTest} together with its {@code .in} files)
 * against the ported implementation. Every entry is an OCL expression followed
 * by the expected {@code toStringWithType()} rendering, so the corpus exercises
 * grammar, parser, evaluator and operation lookup end to end.
 *
 * <p>Entries listed in {@code known-divergences.txt} are not yet reproduced.
 * The list is an exact contract: an unlisted divergence is a regression, and a
 * listed entry that now agrees must be removed from the list.
 */
class UncertaintyCompilerCorpusTest {

    private static final String RESOURCE_DIR = "org/tzi/use/parser/uncertainty/";

    private static final String[] CORPUS = {
        "URealExpression.in",
        "UIntegerExpression.in",
        "UBooleanExpression.in",
        "UCollectionOperations.in",
    };

    private record Entry(String expression, String expected, String id) { }

    @Test
    void historicalCorpusEvaluatesUnchanged() throws IOException {
        Options.explicitVariableDeclarations = false;
        MModel model = new ModelFactory().createModel("Test");

        Set<String> known = readKnownDivergences();
        List<String> regressions = new ArrayList<>();
        Set<String> stillDiverging = new LinkedHashSet<>();
        int checked = 0;

        for (String file : CORPUS) {
            for (Entry entry : readCorpus(file)) {
                checked++;
                String actual = evaluate(model, entry.expression());
                if (normalize(entry.expected()).equals(actual)) continue;
                stillDiverging.add(entry.id());
                if (!known.contains(entry.id())) {
                    regressions.add(entry.id() + "  " + entry.expression()
                        + "\n    expected: " + normalize(entry.expected())
                        + "\n    actual  : " + actual);
                }
            }
        }

        assertTrue(checked > 1400, "corpus should be fully loaded, got " + checked + " entries");
        assertTrue(regressions.isEmpty(),
            regressions.size() + " historical corpus entries diverge without being recorded as known:\n"
                + String.join("\n", regressions));

        Set<String> stale = new TreeSet<>(known);
        stale.removeAll(stillDiverging);
        assertTrue(stale.isEmpty(),
            "known-divergences.txt lists entries that now agree with the historical corpus; "
                + "remove them:\n" + String.join("\n", stale));
    }

    /**
     * Differences that belong to the modern USE baseline rather than to the
     * uncertainty port: current USE renders the undefined value as {@code null}
     * where the historical branch rendered {@code Undefined}.
     */
    private static String normalize(String expected) {
        return expected.equals("Undefined : OclVoid") ? "null : OclVoid" : expected;
    }

    /**
     * Mirrors the historical harness: a successfully compiled expression is
     * evaluated and rendered with its type, while a compile failure is reported
     * through the captured error text.
     */
    private String evaluate(MModel model, String expression) {
        StringWriter err = new StringWriter();
        Expression expr = OCLCompiler.compileExpression(model,
            new ByteArrayInputStream(expression.getBytes(StandardCharsets.UTF_8)),
            "corpus", new PrintWriter(err), new VarBindings());
        if (expr == null) return lastLine(err.toString());
        try {
            MSystemState state = new MSystem(model).state();
            Value value = new Evaluator().eval(expr, state);
            return value.toStringWithType();
        } catch (RuntimeException ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private static String lastLine(String text) {
        String[] lines = text.replace("\r", "").split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) return lines[i];
        }
        return "";
    }

    private Set<String> readKnownDivergences() throws IOException {
        Set<String> known = new LinkedHashSet<>();
        try (BufferedReader reader = open("known-divergences.txt")) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                known.add(line.split("\\s", 2)[0]);
            }
        }
        return known;
    }

    private List<Entry> readCorpus(String name) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = open(name)) {
            StringBuilder expression = null;
            int startLine = 0;
            int lineNr = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNr++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (expression == null) {
                    expression = new StringBuilder();
                    startLine = lineNr;
                }
                if (line.startsWith("->")) {
                    entries.add(new Entry(expression.toString().trim(),
                        line.substring(3), name + ":" + startLine));
                    expression = null;
                } else if (line.endsWith("\\")) {
                    // The corpus continues a line with a doubled backslash, and
                    // the historical reader drops both characters.
                    expression.append(line, 0, line.length() - 2).append(' ');
                } else {
                    expression.append(line).append(' ');
                }
            }
        }
        return entries;
    }

    private BufferedReader open(String name) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_DIR + name);
        assertTrue(in != null, "missing corpus resource " + name);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
