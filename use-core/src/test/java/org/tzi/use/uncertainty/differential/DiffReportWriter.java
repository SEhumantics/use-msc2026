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
 * Writes a sweep to a stable, machine-readable TSV under {@code docs/port2/differential/}.
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

    /** Report root, relative to the repository root. */
    public static final String REPORT_DIR = "docs/port2/differential";

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

    /** Writes several sweeps into one report, in the order given. */
    public static Path writeAll(String fileName, List<DifferentialSweep.Result> results,
                                Map<String, String> jarDigests) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("refusing to write an empty differential report: "
                    + "a report with no rows would read as agreement");
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
     * Locates {@code docs/port2/differential}. Tests run with the working directory set to the
     * module ({@code use-core}), so the repository root is normally the parent; a run from the
     * repository root is also supported.
     */
    public static Path reportDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("docs/port2"))) {
            return cwd.resolve(REPORT_DIR);
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("docs/port2"))) {
            return parent.resolve(REPORT_DIR);
        }
        // Neither worked: create under the parent anyway rather than scattering reports into cwd,
        // and let the caller see the resulting absolute path.
        return (parent == null ? cwd : parent).resolve(REPORT_DIR);
    }
}
