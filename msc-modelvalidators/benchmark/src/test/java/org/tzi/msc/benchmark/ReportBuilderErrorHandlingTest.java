package org.tzi.msc.benchmark;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Regression test for BUG C: before this fix, {@link ReportBuilder} had ZERO catch blocks
 * anywhere in the file, so a missing/malformed {@code results.json}, a missing {@code
 * report-template.html} resource, or a truncated JSON from a benchmark run killed mid-write (a
 * failure mode {@code BenchmarkRunner}'s own class javadoc explicitly anticipates -- see its
 * "write outputJson incrementally ... so a forced kill loses at most the one cell" discussion) all
 * surfaced as a raw uncaught stack trace instead of a diagnostic, contradicting this project's own
 * stated discipline elsewhere ({@code SmtValidateCmd}'s javadoc: "every failure mode degrades to a
 * printed error line, not an exception escaping").
 *
 * <p>Every case here drives the REAL {@link ReportBuilder#run} (not a mock of it) against real
 * files on disk -- a genuinely missing file, a genuinely malformed/truncated JSON payload, and a
 * genuinely wrong classpath resource path -- and asserts two things for each: (1) {@code run}
 * returns {@code false} rather than throwing (a thrown exception here would fail the test with
 * that raw exception, exactly reproducing the historical symptom), and (2) stderr carries a
 * located, readable diagnostic rather than a raw stack trace (no {@code "\tat "} frame line, the
 * unmistakable signature of an uncaught-exception dump).
 */
public class ReportBuilderErrorHandlingTest {

	private static String captureStderr(Runnable action) {
		PrintStream original = System.err;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
		try {
			action.run();
		} finally {
			System.setErr(original);
		}
		return captured.toString(StandardCharsets.UTF_8);
	}

	private static void assertNoRawStackTrace(String stderr) {
		assertFalse("a raw Java stack trace frame ('\\tat ...') must never reach stderr -- the whole"
				+ " point of BUG C's fix is that this is replaced by one located diagnostic line. Got:\n"
				+ stderr, stderr.contains("\tat "));
	}

	@Test
	public void missingManifestFilePrintsLocatedDiagnosticInsteadOfStackTrace() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-error-test").toFile();
		File manifestFile = new File(tmpDir, "does-not-exist-manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[]");
		}

		boolean[] ok = new boolean[1];
		String stderr = captureStderr(() -> ok[0] = ReportBuilder
				.run(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() }));

		assertFalse("run() must report failure, not throw, for a missing manifest file", ok[0]);
		assertNoRawStackTrace(stderr);
		assertTrue("diagnostic must be located at the manifest-reading stage: " + stderr,
				stderr.contains("reading manifest"));
		assertTrue("diagnostic must name the actual missing file: " + stderr,
				stderr.contains(manifestFile.getPath()));
		assertFalse("report.html must not be written on a failed run", outputFile.exists());
	}

	@Test
	public void malformedResultsJsonPrintsLocatedDiagnosticInsteadOfStackTrace() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-error-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write("{\"examples\":[]}");
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("this is not json at all {{{");
		}

		boolean[] ok = new boolean[1];
		String stderr = captureStderr(() -> ok[0] = ReportBuilder
				.run(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() }));

		assertFalse("run() must report failure, not throw, for malformed results JSON", ok[0]);
		assertNoRawStackTrace(stderr);
		assertTrue("diagnostic must be located at the results-reading stage: " + stderr,
				stderr.contains("reading results"));
	}

	/**
	 * Reproduces exactly the failure mode {@code BenchmarkRunner}'s own class javadoc anticipates:
	 * a run killed mid-write, leaving a results.json cut off partway through a JSON value.
	 */
	@Test
	public void truncatedResultsJsonPrintsLocatedDiagnosticInsteadOfStackTrace() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-error-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write("{\"examples\":[]}");
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			// A forced kill mid-write of writeResults()'s pretty-printed JSON array would plausibly cut
			// off exactly like this: an opening bracket and one partial object, no closing.
			w.write("[\n  {\n    \"exampleId\": \"Library\",\n    \"solver\": \"DefaultSAT4J\"");
		}

		boolean[] ok = new boolean[1];
		String stderr = captureStderr(() -> ok[0] = ReportBuilder
				.run(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() }));

		assertFalse("run() must report failure, not throw, for truncated results JSON", ok[0]);
		assertNoRawStackTrace(stderr);
		assertTrue("diagnostic must be located at the results-reading stage: " + stderr,
				stderr.contains("reading results"));
	}

	/**
	 * A results.json truncated all the way down to empty (rather than cut off mid-token) is a
	 * distinct case worth its own assertion: Gson's {@code fromJson} returns {@code null} for empty
	 * input rather than throwing at all, so this exercises {@code run}'s own explicit null-check
	 * (not Gson's parser) -- without it, the null would propagate silently into
	 * {@code ParityTable.compute} and NPE far from this actual cause.
	 */
	@Test
	public void emptyManifestFilePrintsLocatedDiagnosticInsteadOfStackTrace() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-error-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write(""); // truncated to nothing
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[]");
		}

		boolean[] ok = new boolean[1];
		String stderr = captureStderr(() -> ok[0] = ReportBuilder
				.run(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() }));

		assertFalse("run() must report failure, not throw or NPE, for an empty/truncated-to-nothing"
				+ " manifest file", ok[0]);
		assertNoRawStackTrace(stderr);
		assertTrue("diagnostic must mention truncation/emptiness, not a bare NPE: " + stderr,
				stderr.toLowerCase().contains("truncated") || stderr.toLowerCase().contains("empty"));
	}

	/**
	 * Drives the real missing-resource path end-to-end (through the actual {@code getResourceAsStream}
	 * null-check in {@code run}, not a synthetic unit check) with a deliberately wrong classpath path
	 * -- see {@code run(String[], String)}'s own javadoc for why the path is overridable.
	 */
	@Test
	public void missingTemplateResourcePrintsLocatedDiagnosticInsteadOfNpe() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-error-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write("{\"examples\":[]}");
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[]");
		}

		boolean[] ok = new boolean[1];
		String stderr = captureStderr(() -> ok[0] = ReportBuilder.run(
				new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() },
				"/definitely-does-not-exist-template.html"));

		assertFalse("run() must report failure, not NPE, when the template resource is missing", ok[0]);
		assertNoRawStackTrace(stderr);
		assertTrue("diagnostic must be located at the template-loading stage and name the missing"
				+ " resource: " + stderr,
				stderr.contains("loading") && stderr.contains("definitely-does-not-exist-template.html"));
		assertFalse("report.html must not be written on a failed run", outputFile.exists());
	}

	/** Control case: a fully valid run must still succeed and write real output. */
	@Test
	public void validInputsStillSucceedNormally() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-error-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write("{\"examples\":[]}");
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[]");
		}

		boolean ok = ReportBuilder
				.run(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() });

		assertTrue("a fully valid run must still succeed", ok);
		assertTrue("report.html must exist after a successful run", outputFile.isFile());
	}
}
