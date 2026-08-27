package org.tzi.msc.benchmark;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * Renders {@code report-template.html} (a static shell with two placeholder tokens) by
 * substituting the manifest and benchmark-result JSON verbatim into it. Kept deliberately dumb
 * (string substitution, not a templating engine) so the template stays a plain, readable HTML
 * file anyone can edit directly -- all the actual rendering logic lives in that file's own
 * &lt;script&gt;, not here.
 *
 * Usage: {@code java -cp <classpath> org.tzi.msc.benchmark.ReportBuilder <manifest.json>
 * <results.json> <output.html> [soil-validation-results.json] [run-metadata.json]
 * [feature-matrix.json]}
 */
public class ReportBuilder {

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("usage: ReportBuilder <manifest.json> <results.json> <output.html> "
					+ "[soil-validation-results.json] [run-metadata.json] [feature-matrix.json]");
			System.exit(1);
		}
		File manifestFile = new File(args[0]);
		File resultsFile = new File(args[1]);
		File outputFile = new File(args[2]);
		File soilResultsFile = args.length >= 4 ? new File(args[3]) : null;
		File runMetadataFile = args.length >= 5 ? new File(args[4]) : null;
		File featureMatrixFile = args.length >= 6 ? new File(args[5]) : null;

		Gson gson = new Gson();
		ExampleManifest manifest;
		try (FileReader r = new FileReader(manifestFile)) {
			manifest = gson.fromJson(r, ExampleManifest.class);
		}
		Type resultListType = new TypeToken<List<SolverResult>>() {
		}.getType();
		List<SolverResult> results;
		try (FileReader r = new FileReader(resultsFile)) {
			results = gson.fromJson(r, resultListType);
		}
		Type soilResultListType = new TypeToken<List<SoilValidationResult>>() {
		}.getType();
		List<SoilValidationResult> soilResults = List.of();
		if (soilResultsFile != null && soilResultsFile.isFile()) {
			try (FileReader r = new FileReader(soilResultsFile)) {
				soilResults = gson.fromJson(r, soilResultListType);
			}
		}
		JsonElement runMetadata = new JsonObject(); // schema-free: whatever the run script chose to record
		if (runMetadataFile != null && runMetadataFile.isFile()) {
			try (FileReader r = new FileReader(runMetadataFile)) {
				runMetadata = gson.fromJson(r, JsonElement.class);
			}
		}
		// schema-free like runMetadata: the report's own JS reads whatever shape is there, so a schema
		// change to the matrix never requires touching this builder
		JsonElement featureMatrix = new JsonObject();
		if (featureMatrixFile != null && featureMatrixFile.isFile()) {
			try (FileReader r = new FileReader(featureMatrixFile)) {
				featureMatrix = gson.fromJson(r, JsonElement.class);
			}
		}

		String template;
		try (InputStreamReader r = new InputStreamReader(
				ReportBuilder.class.getResourceAsStream("/report-template.html"), StandardCharsets.UTF_8)) {
			StringBuilder sb = new StringBuilder();
			char[] buf = new char[8192];
			int n;
			while ((n = r.read(buf)) != -1) {
				sb.append(buf, 0, n);
			}
			template = sb.toString();
		}

		// Computed HERE, from the very manifest and results this report is about, and embedded as the
		// finished table -- see ParityTable's class javadoc. The report's own JS renders it and does
		// not re-derive it: a second implementation of the agreement rule, in a language with no test
		// suite behind it, is exactly how a parity number quietly grows.
		ParityTable.Table parity = ParityTable.compute(manifest.examples, results);

		String manifestJson = gson.toJson(manifest.examples);
		String resultsJson = gson.toJson(results);
		String soilResultsJson = gson.toJson(soilResults);
		String runMetadataJson = gson.toJson(runMetadata);
		String featureMatrixJson = gson.toJson(featureMatrix);
		// serializeNulls: Row.agree is deliberately null outside the parity intersection ("there is no
		// agreement question here"), and Row.reconstructed/useChecked are null where no such claim was
		// made. Dropping those keys would let the page's `=== null` checks see `undefined` instead and
		// lose the distinction between "not applicable" and "no".
		String parityJson = new GsonBuilder().serializeNulls().create().toJson(parity);

		// String.replace(CharSequence, CharSequence) is a literal substitution (not regex), so the
		// JSON payload -- which can contain '$'/backslash characters in citation text -- is inserted
		// verbatim, with no regex-replacement escaping needed or wanted.
		String rendered = template
				.replace("__MANIFEST_JSON_PLACEHOLDER__", manifestJson)
				.replace("__RESULTS_JSON_PLACEHOLDER__", resultsJson)
				.replace("__SOIL_RESULTS_JSON_PLACEHOLDER__", soilResultsJson)
				.replace("__RUN_METADATA_JSON_PLACEHOLDER__", runMetadataJson)
				.replace("__FEATURE_MATRIX_JSON_PLACEHOLDER__", featureMatrixJson)
				.replace("__PARITY_JSON_PLACEHOLDER__", parityJson);

		try (FileWriter w = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
			w.write(rendered);
		}
		System.err.println("Wrote " + outputFile + " (" + Files.size(outputFile.toPath()) + " bytes)");
	}
}
