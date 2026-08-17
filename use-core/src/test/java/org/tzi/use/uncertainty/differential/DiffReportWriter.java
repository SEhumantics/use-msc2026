package org.tzi.use.uncertainty.differential;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a sweep to a stable, machine-readable TSV under {@code target/differential/}, and compares
 * it against a committed golden under {@code docs/port2/differential/}.
 *
 * <p>File shape:
 * <pre>
 * # harness            differential-sweep/1
 * # seed               20260817
 * # reference          historical
 * # subject            stub-ureal
 * # sha256.use.jar     80ac...
 * # sha256.atenea...   53b2...
 * # operations         URealValue.add(value)
 * # rows               484
 * # verdict.AGREE      484
 * index &lt;tab&gt; operation &lt;tab&gt; inputs &lt;tab&gt; historical &lt;tab&gt; ported &lt;tab&gt; verdict &lt;tab&gt; note
 * ...
 * </pre>
 *
 * <p>Every header line begins with {@code #}, so a consumer can skip the preamble with a single
 * predicate. Nothing time-dependent is written: two runs with the same seed and the same jars
 * produce byte-identical files, which is what makes a diff of two reports meaningful.
 *
 * <p>Test-scoped. Not part of the product.
 */
public final class DiffReportWriter {

    /**
     * Where a run writes its reports: build output, not a tracked directory.
     *
     * <p>This used to be {@link #GOLDEN_DIR}, so every {@code mvn test} overwrote two files that are
     * in {@code git ls-files} and nothing ever compared the new content to the old. A report that is
     * regenerated on every run and never compared is a log, and calling it a baseline is the part
     * that misleads: {@code git status} being clean afterwards proved only that the run reproduced
     * itself, and a report that changed silently changed the "baseline" with it.
     */
    public static final String REPORT_DIR = "target/differential";

    /**
     * Where the committed goldens live, relative to the repository root. Written only by an explicit
     * refresh (see {@link #assertMatchesGolden(Path, String)}), never by an ordinary run.
     */
    public static final String GOLDEN_DIR = "docs/port2/differential";

    /**
     * Set to {@code true} to overwrite the golden from the freshly generated report instead of
     * comparing against it. Intended for the one command a reviewer runs when a golden legitimately
     * changes, so that the change lands in a diff they have to read.
     */
    public static final String GOLDEN_REFRESH_PROPERTY = "use.differential.golden.refresh";

    private static final String FORMAT_VERSION = "differential-sweep/1";

    private DiffReportWriter() {
    }

    /**
     * Writes one sweep result. Returns the path written.
     *
     * @param fileName bare file name, e.g. {@code s1-smoke-ureal-add.tsv}
     */
    public static Path write(String fileName, DifferentialSweep.Result result,
                             Map<String, String> jarDigests) {
        return writeAll(fileName, java.util.Collections.singletonList(result), jarDigests);
    }

    /**
     * Writes several sweeps into one report, in the order given.
     *
     * @throws IllegalArgumentException if the report would contain no data rows. The guard is on the
     *         total row count, not on {@code results.isEmpty()}: the old check tested the number of
     *         {@link DifferentialSweep.Result} objects, which is not the property its own error
     *         message claimed to enforce. {@link DifferentialSweep} produces a zero-row Result
     *         whenever any input domain is empty — an easy accident, since
     *         {@code buildTuples} yields nothing for an empty domain and reports it as a clean sweep
     *         with no disagreements.
     */
    public static Path writeAll(String fileName, List<DifferentialSweep.Result> results,
                                Map<String, String> jarDigests) {
        int rowTotal = 0;
        for (DifferentialSweep.Result r : results) {
            rowTotal += r.rowCount();
        }
        if (rowTotal == 0) {
            throw new IllegalArgumentException("refusing to write an empty differential report '"
                    + fileName + "': " + results.size() + " sweep result(s) contributing 0 rows in "
                    + "total. A report with no rows would read as agreement. The usual cause is an "
                    + "empty input domain, which makes the cartesian product empty.");
        }
        Path target = reportDir().resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create report directory " + target.getParent(), e);
        }

        long seed = results.get(0).seed();
        String reference = results.get(0).referenceName();
        String subject = results.get(0).subjectName();
        int totalRows = 0;
        List<String> operations = new ArrayList<>();
        Map<DiffVerdict, Integer> tally = new LinkedHashMap<>();
        for (DifferentialSweep.Result r : results) {
            totalRows += r.rowCount();
            operations.add(r.op().key());
            for (DiffVerdict v : DiffVerdict.values()) {
                int c = r.count(v);
                if (c > 0) {
                    tally.merge(v, c, Integer::sum);
                }
            }
        }

        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            header(out, "harness", FORMAT_VERSION);
            header(out, "seed", Long.toString(seed));
            header(out, "reference", reference);
            header(out, "subject", subject);
            for (Map.Entry<String, String> e : jarDigests.entrySet()) {
                header(out, "sha256." + e.getKey(), e.getValue());
            }
            header(out, "operations", String.join(",", operations));
            header(out, "rows", Integer.toString(totalRows));
            for (Map.Entry<DiffVerdict, Integer> e : tally.entrySet()) {
                header(out, "verdict." + e.getKey().name(), Integer.toString(e.getValue()));
            }
            out.write(DiffRow.TSV_HEADER);
            out.write('\n');
            for (DifferentialSweep.Result r : results) {
                for (DiffRow row : r.rows()) {
                    out.write(row.toTsv());
                    out.write('\n');
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write differential report " + target, e);
        }
        return target;
    }

    private static void header(BufferedWriter out, String key, String value) throws IOException {
        out.write("# " + key + "\t" + value + "\n");
    }

    /**
     * Where this run's reports go: {@code target/differential} under the module being tested. Tests
     * run with the working directory set to the module ({@code use-core}), so this is
     * {@code use-core/target/differential} and it is wiped by {@code mvn clean} like any other build
     * output.
     */
    public static Path reportDir() {
        return Paths.get("").toAbsolutePath().resolve(REPORT_DIR);
    }

    /**
     * Locates the committed goldens, {@code docs/port2/differential}. Tests run with the working
     * directory set to the module ({@code use-core}), so the repository root is normally the parent;
     * a run from the repository root is also supported.
     */
    public static Path goldenDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("docs/port2"))) {
            return cwd.resolve(GOLDEN_DIR);
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("docs/port2"))) {
            return parent.resolve(GOLDEN_DIR);
        }
        return (parent == null ? cwd : parent).resolve(GOLDEN_DIR);
    }

    /**
     * Compares a freshly written report, byte for byte, against the committed golden of the same
     * name, and throws {@link AssertionError} on any difference.
     *
     * <h2>Why golden comparison rather than target-only plus a refresh convention</h2>
     * Both options stop {@code mvn test} from silently rewriting tracked files. Only this one
     * enforces the property the report is supposed to carry. A target-only report with a manual
     * refresh step leaves nothing checking that this run reproduces the committed evidence, so a
     * behavioural regression would show up as a file nobody diffed; here it fails the build and
     * prints the first differing line. It also subsumes the determinism check: byte-identical to the
     * golden implies byte-identical between runs. The cost is one escape hatch,
     * {@code -D{@value #GOLDEN_REFRESH_PROPERTY}=true}, which copies the new report over the golden
     * so the change arrives as a reviewable diff rather than as an unnoticed overwrite.
     *
     * @param written   the report just produced, normally under {@link #reportDir()}
     * @param fileName  bare golden file name under {@link #goldenDir()}
     * @return the golden path that was compared against (or refreshed)
     */
    public static Path assertMatchesGolden(Path written, String fileName) {
        Path golden = goldenDir().resolve(fileName);
        List<String> actual = readLines(written);
        if (Boolean.getBoolean(GOLDEN_REFRESH_PROPERTY)) {
            try {
                Files.createDirectories(golden.getParent());
                Files.copy(written, golden, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot refresh golden " + golden, e);
            }
            return golden;
        }
        if (!Files.isReadable(golden)) {
            throw new AssertionError("no committed golden at " + golden + " to compare " + written
                    + " against. Create it with -D" + GOLDEN_REFRESH_PROPERTY + "=true and commit it, "
                    + "so that later runs have something to regress against.");
        }
        List<String> expected = readLines(golden);
        int limit = Math.min(expected.size(), actual.size());
        for (int i = 0; i < limit; i++) {
            if (!expected.get(i).equals(actual.get(i))) {
                throw new AssertionError("differential report " + written + " diverges from the "
                        + "committed golden " + golden + " at line " + (i + 1)
                        + "\n  golden: " + expected.get(i)
                        + "\n  actual: " + actual.get(i)
                        + "\nIf the new output is correct, review the diff and re-record with -D"
                        + GOLDEN_REFRESH_PROPERTY + "=true.");
            }
        }
        if (expected.size() != actual.size()) {
            throw new AssertionError("differential report " + written + " has " + actual.size()
                    + " lines but the committed golden " + golden + " has " + expected.size()
                    + "; the first " + limit + " agree. Re-record with -D" + GOLDEN_REFRESH_PROPERTY
                    + "=true once the change is understood.");
        }
        return golden;
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read differential report " + path, e);
        }
    }
}
