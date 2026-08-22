package org.tzi.msc.benchmark;

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
			w.write("[]");
		}

		ReportBuilder.main(new String[] { manifestFile.getPath(), resultsFile.getPath(), outputFile.getPath() });

		String html = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);

		assertFalse("leftover manifest placeholder", html.contains("__MANIFEST_JSON_PLACEHOLDER__"));
		assertFalse("leftover results placeholder", html.contains("__RESULTS_JSON_PLACEHOLDER__"));
		assertFalse("leftover soil-results placeholder", html.contains("__SOIL_RESULTS_JSON_PLACEHOLDER__"));
		assertFalse("leftover run-metadata placeholder", html.contains("__RUN_METADATA_JSON_PLACEHOLDER__"));

		// The adversarial payload's literal, unescaped "</script>" must not survive into the rendered
		// output at all -- checked against the FULL html, not a substring already cut at the first
		// "</script>" (extracting the data block via a "stop at </script>" regex would trivially "pass"
		// this check even if escaping had failed, since the malicious close tag would just become the
		// regex's own stopping point instead of being flagged).
		assertFalse("the exact unescaped malicious payload must not appear in the rendered output",
				html.contains("<script>alert(1)</script>"));

		// And the data block, once extracted, must still be valid, parseable JSON (a Gson round-trip
		// is a reasonable proxy for "a browser's JSON.parse would accept this").
		String manifestBlock = extractScriptBlock(html, "manifest-json");
		com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(manifestBlock);
		assertTrue("manifest data block must parse back to a JSON array", parsed.isJsonArray());
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
