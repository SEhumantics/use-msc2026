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
 * # operations              URealValue.add(value)
 * # rows                    484
 * # rows.measured           484
 * # rows.agreement          484
 * # rows.disagreement       0
 * # rows.throwClassMismatch 0
 * # verdict.AGREE           484
 * # op.URealValue.add(value).rows                     484
 * # op.URealValue.add(value).measured                 484
 * # op.URealValue.add(value).agreement                484
 * # op.URealValue.add(value).disagreement             0
 * # op.URealValue.add(value).distinctReferenceValues  231
 * # op.URealValue.add(value).discriminating           true
 * # accepted.degenerateOperations                     0
 * index &lt;tab&gt; operation &lt;tab&gt; inputs &lt;tab&gt; historical &lt;tab&gt; ported &lt;tab&gt; verdict &lt;tab&gt; note
 * ...
 * </pre>
 *
 * <p>Every header line begins with {@code #}, so a consumer can skip the preamble with a single
 * predicate. Nothing time-dependent is written: two runs with the same seed and the same jars
 * produce byte-identical files, which is what makes a diff of two reports meaningful.
 *
 * <h2>The {@code # rows.*} block is a sum; the {@code # op.*} block is not</h2>
 * Every {@code # rows.*} and {@code # verdict.*} line is a total over all the results in the file,
 * and {@code # operations} is a comma-joined list with no counts attached. A report over 40
 * operations in which 39 measured nothing and one measured a single row therefore has a header
 * indistinguishable in shape from a fully-measured one. The {@code # op.<key>.*} block exists so
 * that a number can be attributed to an operation without re-deriving it from the data rows, and
 * above all so that {@code distinctReferenceValues} — <em>how many different answers the reference
 * gave</em> — is impossible to read the agreement count without.
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
     * <h2>Two guards, and why the second one had to be added</h2>
     * The first rejects a report with no data rows. The guard is on the total row count, not on
     * {@code results.isEmpty()}: the old check tested the number of
     * {@link DifferentialSweep.Result} objects, which is not the property its own error message
     * claimed to enforce. {@link DifferentialSweep} produces a zero-row Result whenever any input
     * domain is empty — an easy accident, since {@code buildTuples} yields nothing for an empty
     * domain and reports it as a clean sweep with no disagreements.
     *
     * <p>The second rejects a report with no <em>measurements</em>, and it exists because the first
     * one turned out to be the same category of error it replaced. Rows are not comparisons. A sweep
     * of the six reachable {@code setTypeToRuntimeType()} operations against an empty-bodied subject
     * wrote a perfectly well-formed 75-row report whose own header said
     * {@code # rows.agreement 72 / # rows.disagreement 3} over <em>zero</em> comparisons; the guard
     * counted 75 and let it through. The property a report has to carry is "this file contains
     * comparisons", and row count is not that property.
     *
     * @throws IllegalArgumentException if the report would contain no data rows, or no row in which
     *         two observed values were compared
     */
    public static Path writeAll(String fileName, List<DifferentialSweep.Result> results,
                                Map<String, String> jarDigests) {
        return writeAll(fileName, results, jarDigests, AcceptedDegenerateOperations.none());
    }

    /**
     * As above, recording which degenerate-operation sign-offs were in force.
     *
     * <p>The allowlist is written into the header whether or not it matched anything, so that a run
     * with a sign-off in force is never byte-indistinguishable from a run without one. (That is the
     * shape of the still-open D-14 complaint against {@link AcceptedThrowPairs}, fixed here for the
     * mechanism introduced alongside it rather than repeated.)
     */
    public static Path writeAll(String fileName, List<DifferentialSweep.Result> results,
                                Map<String, String> jarDigests,
                                AcceptedDegenerateOperations acknowledged) {
        java.util.Objects.requireNonNull(acknowledged,
                "acknowledged (use AcceptedDegenerateOperations.none())");
        int rowTotal = 0;
        int measuredTotal = 0;
        for (DifferentialSweep.Result r : results) {
            rowTotal += r.rowCount();
            measuredTotal += r.measurementCount();
        }
        if (rowTotal == 0) {
            throw new IllegalArgumentException("refusing to write an empty differential report '"
                    + fileName + "': " + results.size() + " sweep result(s) contributing 0 rows in "
                    + "total. A report with no rows would read as agreement. The usual cause is an "
                    + "empty input domain, which makes the cartesian product empty.");
        }
        if (measuredTotal == 0) {
            StringBuilder tallies = new StringBuilder();
            for (DifferentialSweep.Result r : results) {
                tallies.append("\n    ").append(r.summary());
            }
            throw new IllegalArgumentException("refusing to write a differential report '" + fileName
                    + "' that contains no measurements: " + rowTotal + " row(s) across "
                    + results.size() + " sweep result(s), and not one of them compared two observed "
                    + "values. Every number this file would carry would describe an absence, and a "
                    + "reader would see '# rows " + rowTotal + "' and a green-looking verdict tally. "
                    + "The usual causes are a subject that throws on every row, a receiver type the "
                    + "harness cannot marshal, and an operation that returns void." + tallies);
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
        int agreementRows = 0;
        int measuredRows = 0;
        int throwClassMismatches = 0;
        List<String> operations = new ArrayList<>();
        Map<DiffVerdict, Integer> tally = new LinkedHashMap<>();
        for (DifferentialSweep.Result r : results) {
            totalRows += r.rowCount();
            agreementRows += r.agreementCount();
            measuredRows += r.measurementCount();
            throwClassMismatches += r.throwClassMismatchCount();
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
            // Stated outright rather than left for the reader to derive from the verdict names,
            // because deriving it wrongly is this harness's recurring defect: a reader who saw
            // "verdict.AGREE_THROWN 169" scored a sweep green in which nothing was ever compared.
            //
            // rows.measured is first among the three because it is the one that says how much of
            // this file is evidence. A report can have rows.agreement == rows and rows.measured == 0
            // -- that is precisely what an empty-bodied port over void operations produced -- and a
            // reader who has only the agreement count is being told a true number about a
            // population that does not exist.
            header(out, "rows.measured", Integer.toString(measuredRows));
            header(out, "rows.agreement", Integer.toString(agreementRows));
            header(out, "rows.disagreement", Integer.toString(totalRows - agreementRows));
            // A port that fails on the right rows with the wrong exception class changes no other
            // aggregate in this header. See DifferentialSweep.Result#throwClassMismatchCount().
            header(out, "rows.throwClassMismatch", Integer.toString(throwClassMismatches));
            for (Map.Entry<DiffVerdict, Integer> e : tally.entrySet()) {
                header(out, "verdict." + e.getKey().name(), Integer.toString(e.getValue()));
            }
            // ---------------------------------------------------------- per operation
            //
            // Everything above this point is a SUM over all results. A 40-operation report in which
            // 39 measured nothing and one measured a single row produces a header indistinguishable
            // in shape from a fully-measured one (defect D-21) -- the D-10 lesson ("444 of 471471 is
            // noise in an aggregate; per operation it was 144 of 144") applied to the invariant test
            // and not to the artefact a human reads. The block below is per operation, and it
            // carries the one number that says whether an agreement figure means anything:
            //
            //   op.<key>.distinctReferenceValues -- how many different answers the reference gave
            //                                       across the measured rows. 1 means this sweep
            //                                       could not have failed; its agreement figure was
            //                                       decided before either implementation ran.
            //   op.<key>.discriminating          -- that number against DISCRIMINATING_MINIMUM,
            //                                       stated outright rather than left to be derived,
            //                                       because deriving it wrongly is this harness's
            //                                       recurring defect.
            //   op.<key>.soleReferenceValue      -- present only when there is exactly one, so the
            //                                       reader can see WHAT the constant was without
            //                                       reading 20 000 data rows.
            //   op.<key>.degenerate.acknowledged -- the written sign-off, verbatim, when one is in
            //                                       force for that operation and that value.
            for (DifferentialSweep.Result r : results) {
                String key = r.op().key();
                header(out, "op." + key + ".rows", Integer.toString(r.rowCount()));
                header(out, "op." + key + ".measured", Integer.toString(r.measurementCount()));
                header(out, "op." + key + ".agreement", Integer.toString(r.agreementCount()));
                header(out, "op." + key + ".disagreement",
                        Integer.toString(r.disagreements().size()));
                header(out, "op." + key + ".distinctReferenceValues",
                        Integer.toString(r.distinctReferenceValues()));
                header(out, "op." + key + ".discriminating", Boolean.toString(r.isDiscriminating()));
                String sole = r.soleReferenceValue();
                if (sole != null) {
                    header(out, "op." + key + ".soleReferenceValue", sole);
                    String rationale = acknowledged.rationaleFor(key, sole);
                    if (rationale != null) {
                        header(out, "op." + key + ".degenerate.acknowledged", rationale);
                    }
                }
            }
            // Stated even when empty, so that "no sign-off was in force" is an assertion this file
            // makes rather than an absence the reader has to infer.
            header(out, "accepted.degenerateOperations", Integer.toString(acknowledged.size()));
            for (String entry : acknowledged.describe()) {
                header(out, "accepted.degenerateOperation", entry);
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

    /**
     * One header line, {@code # key<tab>value}. The value is scrubbed of tabs and newlines: most
     * header values are numbers, but the degenerate-operation rationales are prose a human typed,
     * and a tab in one of them would silently turn a two-column header into a three-column one.
     */
    private static void header(BufferedWriter out, String key, String value) throws IOException {
        out.write("# " + key + "\t"
                + value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ') + "\n");
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
     * <h2>It really is bytes</h2>
     * This used to compare {@code Files.readAllLines} to {@code Files.readAllLines} while its own
     * first sentence said "byte for byte". That comparison is blind to line terminators and to a
     * missing final newline — {@code "a\nb\n"} and {@code "a\nb"} both read back as {@code [a, b]} —
     * so two files that differ in bytes compared equal, in a method whose product is evidence. The
     * comparison below is {@link java.util.Arrays#equals(byte[], byte[])} on the file contents; the
     * line-by-line walk exists only to build a readable message once a difference is known to be
     * there, and reports a whitespace-only difference as such rather than printing two lines that
     * look identical.
     *
     * @param written   the report just produced, normally under {@link #reportDir()}
     * @param fileName  bare golden file name under {@link #goldenDir()}
     * @return the golden path that was compared against (or refreshed)
     */
    public static Path assertMatchesGolden(Path written, String fileName) {
        Path golden = goldenDir().resolve(fileName);
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
        if (java.util.Arrays.equals(readBytes(written), readBytes(golden))) {
            return golden;
        }

        // Known different. Everything from here on only phrases the failure.
        List<String> actual = readLines(written);
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
        throw new AssertionError("differential report " + written + " differs from the committed "
                + "golden " + golden + " in bytes but not in any line: the files disagree only about "
                + "line terminators or a trailing newline. A line-based comparison would have called "
                + "these two files equal. Re-record with -D" + GOLDEN_REFRESH_PROPERTY + "=true once "
                + "the change is understood.");
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read differential report " + path, e);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read differential report " + path, e);
        }
    }
}
