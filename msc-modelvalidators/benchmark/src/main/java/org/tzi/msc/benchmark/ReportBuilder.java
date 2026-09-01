package org.tzi.msc.benchmark;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
 * <p><b>Failure handling.</b> {@link #run} tracks which stage (reading which file, or which
 * later step) is in progress and {@link #main} catches anything that escapes it, printing a
 * single located {@code [ReportBuilder] error while <stage>: ...} diagnostic instead of letting a
 * raw stack trace reach the console -- the same discipline {@code SmtValidateCmd}'s own javadoc
 * states elsewhere in this project ("every failure mode degrades to a printed error line, not an
 * exception escaping"). This module's own {@code manifest.json}/{@code results.json} can be
 * missing (not yet produced), malformed (hand-edited or from an incompatible tool version), or
 * truncated (a benchmark run killed mid-write -- {@code BenchmarkRunner}'s own javadoc documents
 * exactly that failure mode for its output), and {@code report-template.html} is a classpath
 * resource that a stripped/incomplete jar can lack; all three are ordinary operator mistakes, not
 * programming errors, so a diagnostic beats a stack trace. {@code run} returns {@code false}
 * (never throwing itself) precisely so a test can drive every failure mode directly without
 * risking the {@link System#exit} call {@link #main} performs on failure -- the same reason
 * {@code BenchmarkRunner#finalizeResult}/{@code #recordReconstruction} were pulled out as their
 * own testable units.
 *
 * Usage: {@code java -cp <classpath> org.tzi.msc.benchmark.ReportBuilder <manifest.json>
 * <results.json> <output.html> [soil-validation-results.json] [run-metadata.json]
 * [feature-matrix.json]}
 */
public class ReportBuilder {

	public static void main(String[] args) {
		if (!run(args)) {
			System.exit(1);
		}
	}

	static boolean run(String[] args) {
		return run(args, "/report-template.html");
	}

	/**
	 * Does the actual work of {@link #main}, but returns {@code false} instead of ever letting an
	 * exception escape or calling {@link System#exit} -- see the class javadoc for why. {@code
	 * stage} names whatever step was in progress when a failure is caught, so the printed
	 * diagnostic is LOCATED (which file, or which later step) rather than a bare exception name.
	 *
	 * @param templateResourcePath classpath location of the report template; always
	 *     {@code "/report-template.html"} in real use ({@link #run(String[])} and {@link #main}
	 *     both fix it), overridable only so a test can drive the real missing-resource failure
	 *     mode end-to-end with a deliberately wrong path instead of needing the actual packaged
	 *     resource removed from the classpath.
	 */
	static boolean run(String[] args, String templateResourcePath) {
		String stage = "startup";
		try {
			if (args.length < 3) {
				System.err.println("usage: ReportBuilder <manifest.json> <results.json> <output.html> "
						+ "[soil-validation-results.json] [run-metadata.json] [feature-matrix.json]");
				return false;
			}
			File manifestFile = new File(args[0]);
			File resultsFile = new File(args[1]);
			File outputFile = new File(args[2]);
			File soilResultsFile = args.length >= 4 ? new File(args[3]) : null;
			File runMetadataFile = args.length >= 5 ? new File(args[4]) : null;
			File featureMatrixFile = args.length >= 6 ? new File(args[5]) : null;

			Gson gson = new Gson();

			stage = "reading manifest [" + manifestFile + "]";
			ExampleManifest manifest;
			try (FileReader r = new FileReader(manifestFile)) {
				manifest = gson.fromJson(r, ExampleManifest.class);
			}
			if (manifest == null || manifest.examples == null) {
				// Gson.fromJson silently returns null on empty input rather than throwing -- a file
				// truncated to nothing (or to a bare "{" with no closing brace on some JSON libraries,
				// though Gson itself throws JsonSyntaxException for that shape, caught by the generic
				// handler below) would otherwise NPE much later, far from this actual cause.
				throw new IOException("empty or truncated JSON -- no top-level \"examples\" array was parsed");
			}

			stage = "reading results [" + resultsFile + "]";
			Type resultListType = new TypeToken<List<SolverResult>>() {
			}.getType();
			List<SolverResult> results;
			try (FileReader r = new FileReader(resultsFile)) {
				results = gson.fromJson(r, resultListType);
			}
			if (results == null) {
				throw new IOException("empty or truncated JSON -- no top-level array was parsed");
			}

			Type soilResultListType = new TypeToken<List<SoilValidationResult>>() {
			}.getType();
			List<SoilValidationResult> soilResults = List.of();
			if (soilResultsFile != null && soilResultsFile.isFile()) {
				stage = "reading SOIL results [" + soilResultsFile + "]";
				try (FileReader r = new FileReader(soilResultsFile)) {
					soilResults = gson.fromJson(r, soilResultListType);
				}
				if (soilResults == null) {
					soilResults = List.of();
				}
			}
			JsonElement runMetadata = new JsonObject(); // schema-free: whatever the run script chose to record
			if (runMetadataFile != null && runMetadataFile.isFile()) {
				stage = "reading run metadata [" + runMetadataFile + "]";
				try (FileReader r = new FileReader(runMetadataFile)) {
					runMetadata = gson.fromJson(r, JsonElement.class);
				}
				if (runMetadata == null) {
					runMetadata = new JsonObject();
				}
			}
			// schema-free like runMetadata: the report's own JS reads whatever shape is there, so a schema
			// change to the matrix never requires touching this builder
			JsonElement featureMatrix = new JsonObject();
			if (featureMatrixFile != null && featureMatrixFile.isFile()) {
				stage = "reading feature matrix [" + featureMatrixFile + "]";
				try (FileReader r = new FileReader(featureMatrixFile)) {
					featureMatrix = gson.fromJson(r, JsonElement.class);
				}
				if (featureMatrix == null) {
					featureMatrix = new JsonObject();
				}
			}

			stage = "loading " + templateResourcePath + " resource";
			String template;
			try (InputStream resourceStream = ReportBuilder.class.getResourceAsStream(templateResourcePath)) {
				if (resourceStream == null) {
					// getResourceAsStream returns null (not an exception) when the resource is missing --
					// e.g. a stripped/incomplete jar -- which would otherwise NPE inside
					// InputStreamReader's own constructor with no hint of the real cause.
					throw new IOException("resource " + templateResourcePath + " not found on the classpath");
				}
				InputStreamReader r = new InputStreamReader(resourceStream, StandardCharsets.UTF_8);
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
			stage = "computing parity table";
			ParityTable.Table parity = ParityTable.compute(manifest.examples, results);

			stage = "rendering report";
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

			stage = "writing output [" + outputFile + "]";
			try (FileWriter w = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
				w.write(rendered);
			}
			System.err.println("Wrote " + outputFile + " (" + Files.size(outputFile.toPath()) + " bytes)");
			return true;
		} catch (Exception e) {
			System.err.println("[ReportBuilder] error while " + stage + ": " + e.getClass().getSimpleName()
					+ (e.getMessage() != null && !e.getMessage().isBlank() ? ": " + e.getMessage() : ""));
			return false;
		}
	}
}
