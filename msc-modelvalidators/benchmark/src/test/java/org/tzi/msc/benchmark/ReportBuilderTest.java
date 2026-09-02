package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Exercises {@link ReportBuilder} end-to-end against small fixture JSON files (not the real
 * manifest/results -- this is testing the rendering mechanism itself, not any particular example's
 * data) and asserts the two things that matter for "safe report rendering" / "dashboard data
 * integrity": no leftover placeholder tokens, and every embedded JSON data block actually parses as
 * JSON -- including when a field contains characters ("</script>", quotes, backslashes) that would
 * break a naive raw-JS-literal substitution.
 */
public class ReportBuilderTest {

	@Test
	public void rendersValidJsonDataBlocksAndLeavesNoPlaceholders() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File soilResultsFile = new File(tmpDir, "soil-results.json");
		File runMetadataFile = new File(tmpDir, "run-metadata.json");
		File featureMatrixFile = new File(tmpDir, "feature-matrix.json");
		File outputFile = new File(tmpDir, "report.html");

		// A citation/question field deliberately containing characters that are dangerous for a naive
		// raw-JS-literal substitution: a script-closing sequence, a quote, and a backslash.
		String adversarialManifest = "{\"examples\":[{\"id\":\"X\",\"directory\":\"x\",\"useFile\":\"x.use\","
				+ "\"category\":\"expressiveness\",\"mode\":\"finding\",\"hasValidationTests\":false,"
				+ "\"provenanceType\":\"authored-for-thesis\",\"features\":[],"
				+ "\"question\":\"</script><script>alert(1)</script> and a \\\"quote\\\" and a \\\\backslash\"}]}";
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write(adversarialManifest);
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[{\"exampleId\":\"X\",\"solver\":\"test\",\"outcome\":\"SATISFIABLE\"}]");
		}
		try (FileWriter w = new FileWriter(soilResultsFile, StandardCharsets.UTF_8)) {
			w.write("[{\"exampleId\":\"X\",\"note\":\"</script><script>alert(2)</script>\"}]");
		}
		try (FileWriter w = new FileWriter(runMetadataFile, StandardCharsets.UTF_8)) {
			w.write("{\"host\":\"test\\\\host\"}");
		}
		try (FileWriter w = new FileWriter(featureMatrixFile, StandardCharsets.UTF_8)) {
			w.write("{\"plugins\":[{\"id\":\"test\",\"evidence\":\"</script><script>alert(3)</script>\"}]}");
		}

		ReportBuilder.main(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath(),
				soilResultsFile.getPath(), runMetadataFile.getPath(), featureMatrixFile.getPath() });

		String html = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);

		assertFalse("leftover manifest placeholder", html.contains("__MANIFEST_JSON_PLACEHOLDER__"));
		assertFalse("leftover results placeholder", html.contains("__RESULTS_JSON_PLACEHOLDER__"));
		assertFalse("leftover soil-results placeholder", html.contains("__SOIL_RESULTS_JSON_PLACEHOLDER__"));
		assertFalse("leftover run-metadata placeholder", html.contains("__RUN_METADATA_JSON_PLACEHOLDER__"));
		assertFalse("leftover feature-matrix placeholder", html.contains("__FEATURE_MATRIX_JSON_PLACEHOLDER__"));
		assertFalse("leftover parity placeholder", html.contains("__PARITY_JSON_PLACEHOLDER__"));

		// The adversarial payload's literal, unescaped "</script>" must not survive into the rendered
		// output at all -- checked against the FULL html, not a substring already cut at the first
		// "</script>" (extracting the data block via a "stop at </script>" regex would trivially "pass"
		// this check even if escaping had failed, since the malicious close tag would just become the
		// regex's own stopping point instead of being flagged).
		assertFalse("the exact unescaped malicious payload must not appear in the rendered output",
				html.contains("<script>alert(1)</script>") || html.contains("<script>alert(2)</script>")
						|| html.contains("<script>alert(3)</script>"));

		// Every data block must remain valid JSON after its independent deserialization/re-serialization
		// path. Gson parsing is a reasonable proxy for the browser's JSON.parse calls in the template.
		String manifestBlock = extractScriptBlock(html, "manifest-json");
		assertTrue("manifest data block must parse back to a JSON array",
				com.google.gson.JsonParser.parseString(manifestBlock).isJsonArray());
		assertTrue("results data block must parse back to a JSON array",
				com.google.gson.JsonParser.parseString(extractScriptBlock(html, "results-json")).isJsonArray());
		assertTrue("SOIL results data block must parse back to a JSON array",
				com.google.gson.JsonParser.parseString(extractScriptBlock(html, "soil-results-json")).isJsonArray());
		assertTrue("run metadata data block must parse back to a JSON object",
				com.google.gson.JsonParser.parseString(extractScriptBlock(html, "run-metadata-json")).isJsonObject());
		assertTrue("feature matrix data block must parse back to a JSON object",
				com.google.gson.JsonParser.parseString(extractScriptBlock(html, "feature-matrix-json")).isJsonObject());
		assertTrue("parity data block must parse back to a JSON object",
				com.google.gson.JsonParser.parseString(extractScriptBlock(html, "parity-json")).isJsonObject());
	}

	/**
	 * The parity table is the RQ1 evidence, and it is only evidence if it is COMPUTED from the run
	 * embedded in the same file. Rendering the report must therefore embed the computed table, not a
	 * copy of the raw results for the page to re-derive parity from in JavaScript -- a second,
	 * untested implementation of the one calculation that must not overstate parity.
	 */
	@Test
	public void embedsTheComputedParityTableRatherThanLeavingTheReportToReDeriveIt() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-parity-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File outputFile = new File(tmpDir, "report.html");

		// One row where Kodkod's yes is vacuous and ours is searched: the case that must never be
		// rendered as agreement.
		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write("{\"examples\":[{\"id\":\"Vacuous\",\"directory\":\"v\",\"useFile\":\"v.use\","
					+ "\"category\":\"expressiveness\",\"mode\":\"finding\",\"hasValidationTests\":false,"
					+ "\"provenanceType\":\"authored-for-thesis\",\"features\":[\"f1\"]}]}");
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[{\"exampleId\":\"Vacuous\",\"solver\":\"DefaultSAT4J\","
					+ "\"outcome\":\"TRIVIALLY_SATISFIABLE\"},"
					+ "{\"exampleId\":\"Vacuous\",\"solver\":\"LightSAT4J\","
					+ "\"outcome\":\"TRIVIALLY_SATISFIABLE\"},"
					+ "{\"exampleId\":\"Vacuous\",\"solver\":\"MiniSat\","
					+ "\"outcome\":\"TRIVIALLY_SATISFIABLE\"},"
					+ "{\"exampleId\":\"Vacuous\",\"solver\":\"MiniSatProver\","
					+ "\"outcome\":\"TRIVIALLY_SATISFIABLE\"},"
					+ "{\"exampleId\":\"Vacuous\",\"solver\":\"Lingeling\","
					+ "\"outcome\":\"TRIVIALLY_SATISFIABLE\"},"
					+ "{\"exampleId\":\"Vacuous\",\"solver\":\"SMT-Z3\",\"outcome\":\"SATISFIABLE\","
					+ "\"reconstructed\":true,\"useChecked\":true}]");
		}

		ReportBuilder.main(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() });

		String html = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
		com.google.gson.JsonObject parity =
				com.google.gson.JsonParser.parseString(extractScriptBlock(html, "parity-json")).getAsJsonObject();
		com.google.gson.JsonObject summary = parity.getAsJsonObject("summary");

		assertEquals("one corpus row", 1, summary.get("corpusRows").getAsInt());
		assertEquals("a vacuous Kodkod yes is not a real verdict", 0, summary.get("kodkodRealVerdicts").getAsInt());
		assertEquals("our verdict was searched", 1, summary.get("smtRealVerdicts").getAsInt());
		assertEquals("nothing is claimable on this row", 0, summary.get("intersection").getAsInt());
		assertEquals("and nothing may be reported as agreement", 0, summary.get("agreements").getAsInt());
	}

	/**
	 * A run killed by run-benchmark.sh's outer {@code timeout} produces the same file set as a
	 * complete one -- same manifest, same report.html, just fewer rows in results.json, which nobody
	 * reading the HTML would notice. run-benchmark.sh therefore rewrites {@code benchmarkStatus} in
	 * run-metadata.json AFTER the run, and the report footer renders an INCOMPLETE banner from it.
	 * That only works if ReportBuilder passes the field through, so this pins it: the marker written
	 * by the script must survive verbatim into the rendered page's run-metadata block, and the
	 * template must actually read it.
	 */
	@Test
	public void carriesTheKilledRunMarkerFromRunMetadataIntoTheReport() throws Exception {
		File tmpDir = Files.createTempDirectory("report-builder-timeout-test").toFile();
		File manifestFile = new File(tmpDir, "manifest.json");
		File resultsFile = new File(tmpDir, "results.json");
		File runMetadataFile = new File(tmpDir, "run-metadata.json");
		File outputFile = new File(tmpDir, "report.html");

		try (FileWriter w = new FileWriter(manifestFile, StandardCharsets.UTF_8)) {
			w.write("{\"examples\":[{\"id\":\"X\",\"directory\":\"x\",\"useFile\":\"x.use\","
					+ "\"category\":\"expressiveness\",\"mode\":\"finding\",\"hasValidationTests\":false,"
					+ "\"provenanceType\":\"authored-for-thesis\",\"features\":[]}]}");
		}
		try (FileWriter w = new FileWriter(resultsFile, StandardCharsets.UTF_8)) {
			w.write("[{\"exampleId\":\"X\",\"solver\":\"test\",\"outcome\":\"SATISFIABLE\"}]");
		}
		try (FileWriter w = new FileWriter(runMetadataFile, StandardCharsets.UTF_8)) {
			w.write("{\"benchmarkStatus\":\"timed-out\",\"benchmarkExitCode\":124}");
		}

		ReportBuilder.main(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath(),
				"", runMetadataFile.getPath() });

		String html = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
		com.google.gson.JsonObject metadata = com.google.gson.JsonParser
				.parseString(extractScriptBlock(html, "run-metadata-json")).getAsJsonObject();
		assertEquals("timed-out", metadata.get("benchmarkStatus").getAsString());
		assertEquals(124, metadata.get("benchmarkExitCode").getAsInt());
		assertTrue("the footer must actually consume benchmarkStatus, not just carry it",
				html.contains("RUN_METADATA.benchmarkStatus"));
	}

	/**
	 * The killed-run banner only works if two files agree on one vocabulary: run-benchmark.sh writes
	 * the {@code benchmarkStatus} strings, report-template.html decides which of them mean "this run
	 * did not finish". Nothing else couples them -- renaming a status on either side would silently
	 * restore exactly the F6 symptom (a killed run rendering as a complete one), because the report
	 * would simply never match. This test is that coupling: every status the script can write, other
	 * than the one success value, must appear in the template's banner condition.
	 *
	 * <p>Deliberately a source-text check. The banner is rendered by the page's own JavaScript, which
	 * no test in this module executes; asserting the rendered HTML contains the banner text would
	 * pass unconditionally, since that text is part of the template either way.
	 */
	@Test
	public void everyUnfinishedRunStatusTheScriptWritesIsOneTheReportFooterFlags() throws Exception {
		String script = Files.readString(repoFile("msc-modelvalidators/benchmark/scripts/run-benchmark.sh"),
				StandardCharsets.UTF_8);
		String template = Files.readString(
				repoFile("msc-modelvalidators/benchmark/src/main/resources/report-template.html"),
				StandardCharsets.UTF_8);

		Matcher calls = Pattern.compile("(?m)^\\s*write_run_metadata\\s+([a-z-]+)\\s").matcher(script);
		java.util.Set<String> written = new java.util.TreeSet<>();
		while (calls.find()) {
			written.add(calls.group(1));
		}
		assertTrue("the script must actually call write_run_metadata", written.size() >= 2);
		assertTrue("a completed run must be one of the statuses", written.contains("completed"));

		for (String status : written) {
			if (status.equals("completed")) {
				continue;
			}
			assertTrue("report-template.html's footer does not flag benchmarkStatus=" + status
					+ ", so a run in that state would render as a complete one",
					template.contains("RUN_STATUS === '" + status + "'"));
		}
	}

	/** Resolves a repo-relative path by walking up from the module directory Surefire runs in. */
	private static java.nio.file.Path repoFile(String repoRelative) {
		java.nio.file.Path base = java.nio.file.Paths.get("").toAbsolutePath();
		for (int depth = 0; depth <= 8 && base != null; depth++) {
			java.nio.file.Path candidate = base.resolve(repoRelative);
			if (java.nio.file.Files.isRegularFile(candidate)) {
				return candidate;
			}
			base = base.getParent();
		}
		throw new AssertionError("could not locate " + repoRelative + " from "
				+ java.nio.file.Paths.get("").toAbsolutePath());
	}

	private static String extractScriptBlock(String html, String id) {
		Pattern p = Pattern.compile(
				"<script type=\"application/json\" id=\"" + id + "\">(.*?)</script>", Pattern.DOTALL);
		Matcher m = p.matcher(html);
		if (!m.find()) {
			throw new AssertionError("no <script id=\"" + id + "\"> block found in rendered output");
		}
		return m.group(1);
	}
}
