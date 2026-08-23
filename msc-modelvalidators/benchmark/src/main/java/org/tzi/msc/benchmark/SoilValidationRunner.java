package org.tzi.msc.benchmark;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Actually executes every example's standalone SOIL validation fixtures ({@code valid-instance.cmd}
 * / {@code invalid-instance.cmd} -- a hand-built instance opened via {@code open *.soil} and checked
 * via {@code check -v}, no solver involved at all) instead of trusting the manifest's
 * {@code hasValidationTests} flag as an unverified claim. This is what {@link BenchmarkRunner}
 * deliberately does NOT do (it only runs {@code -validate}/Kodkod search); the two are kept in
 * separate classes/artifacts because they exercise genuinely different things -- one drives Kodkod
 * search in-process for nanosecond-precision timing, the other drives USE's own shell (which lives
 * in {@code use-gui}, not {@code use-core}/{@code kk-modelvalidator}) via the exact real
 * {@code use-gui.jar -nogui <model> <cmd>} subprocess invocation documented in every fixture's own
 * header comment and in {@code benchmark/examples/run-example.sh} -- reusing that real,
 * already-manually-confirmed mechanism rather than driving USE's {@code Shell} singleton in-process
 * (a REPL-oriented class wrapping stdin/stdout that was never designed to run 20+ times in one JVM).
 *
 * Usage: {@code java -cp <classpath> org.tzi.msc.benchmark.SoilValidationRunner <examples-dir>
 * <use-gui.jar> <output-json> [timeoutSeconds]}
 */
public class SoilValidationRunner {

	private static final Pattern CHECK_SUMMARY = Pattern
			.compile("checked (\\d+) invariants?(?: in [0-9.]+s)?, (\\d+) failures?\\.");
	private static final Pattern INVARIANT_LINE = Pattern
			.compile("checking invariant \\(\\d+\\) `([^']+)': (OK|FAILED)\\.");
	// -nogui batch mode echoes every line of the driving .cmd/.soil script back to stdout, prefixed
	// "<file>> " -- including comment lines. A .cmd file's own header comment documenting expected
	// output (e.g. "-- checking invariant (4) `Book::yearPlausible': FAILED.") therefore re-matches
	// INVARIANT_LINE/CHECK_SUMMARY a second time when scanned unfiltered, double-counting a failure
	// that only happened once. Strip echoed input lines before matching either pattern.
	private static final Pattern ECHOED_SCRIPT_LINE = Pattern.compile("^\\S+\\.(cmd|soil)>.*$", Pattern.MULTILINE);

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("usage: SoilValidationRunner <examples-dir> <use-gui.jar> <output-json> [timeoutSeconds]");
			System.exit(1);
		}
		File examplesDir = new File(args[0]);
		File useGuiJar = new File(args[1]);
		File outputJson = new File(args[2]);
		int timeoutSeconds = args.length >= 4 ? Integer.parseInt(args[3]) : 30;
		// Mirrors run-example.sh's own convention: harmless to pass even if the directory doesn't
		// exist, since valid-instance.cmd/invalid-instance.cmd never call -config/-validate and so
		// never touch a native solver in the first place.
		File solverLibDir = new File(useGuiJar.getParentFile(), "plugins/modelValidatorPlugin/x64");

		Gson manifestGson = new Gson();
		ExampleManifest manifest;
		try (InputStreamReader r = new InputStreamReader(
				SoilValidationRunner.class.getResourceAsStream("/manifest.json"), StandardCharsets.UTF_8)) {
			manifest = manifestGson.fromJson(r, ExampleManifest.class);
		}

		List<SoilValidationResult> allResults = new ArrayList<>();
		for (ExampleEntry ex : manifest.examples) {
			if (!ex.hasValidationTests) {
				continue; // no genuine standalone SOIL fixture claimed for this example
			}
			File exDir = new File(examplesDir, ex.directory);
			File modelFile = new File(exDir, ex.useFile);

			for (String kind : new String[] { "valid", "invalid" }) {
				File cmdFile = new File(exDir, kind + "-instance.cmd");
				if (!cmdFile.isFile()) {
					SoilValidationResult missing = new SoilValidationResult();
					missing.exampleId = ex.id;
					missing.cmdFile = cmdFile.getName();
					missing.kind = kind;
					missing.passed = false;
					missing.note = "expected fixture file not found: " + cmdFile;
					allResults.add(missing);
					System.err.println("  MISSING " + cmdFile);
					continue;
				}
				System.err.println("=== " + ex.id + " / " + cmdFile.getName() + " ===");
				List<String> outOfScope = "valid".equals(kind) && ex.soilKnownOutOfScopeInvariants != null
						? ex.soilKnownOutOfScopeInvariants
						: Collections.emptyList();
				SoilValidationResult result;
				try {
					result = runCmd(exDir, useGuiJar, solverLibDir, ex.id, modelFile, cmdFile, kind, timeoutSeconds,
							outOfScope);
				} catch (Exception e) {
					// Same error-isolation principle as BenchmarkRunner: one broken fixture/subprocess
					// must not abort every other example's validation.
					result = new SoilValidationResult();
					result.exampleId = ex.id;
					result.cmdFile = cmdFile.getName();
					result.kind = kind;
					result.passed = false;
					result.note = e.getClass().getSimpleName() + ": " + e.getMessage();
				}
				allResults.add(result);
				System.err.printf("  passed=%-5s checked=%s failures=%s note=%s%n",
						result.passed, result.numInvariantsChecked, result.numFailures, result.note);
			}
		}

		Gson outGson = new GsonBuilder().setPrettyPrinting().create();
		try (PrintWriter w = new PrintWriter(new FileWriter(outputJson, StandardCharsets.UTF_8))) {
			w.write(outGson.toJson(allResults));
		}
		long notAsExpected = allResults.stream().filter(r -> !r.passed).count();
		System.err.println("Wrote " + allResults.size() + " SOIL validation results to " + outputJson
				+ " (" + notAsExpected + " not as expected)");
	}

	private static SoilValidationResult runCmd(File exDir, File useGuiJar, File solverLibDir, String exampleId,
			File modelFile, File cmdFile, String kind, int timeoutSeconds, List<String> knownOutOfScopeInvariants)
			throws IOException, InterruptedException {
		SoilValidationResult result = new SoilValidationResult();
		result.exampleId = exampleId;
		result.cmdFile = cmdFile.getName();
		result.kind = kind;

		List<String> command = new ArrayList<>();
		command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		command.add("-Djava.library.path=" + solverLibDir.getAbsolutePath());
		command.add("-jar");
		command.add(useGuiJar.getAbsolutePath());
		command.add("-nogui");
		command.add(modelFile.getName());
		command.add(cmdFile.getName());

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(exDir);
		pb.redirectErrorStream(true);
		Process process = pb.start();

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append('\n');
			}
		}
		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			result.exitCode = -1;
			result.note = "timed out after " + timeoutSeconds + "s";
			return result;
		}
		result.exitCode = process.exitValue();
		applyParsedOutcome(result, output.toString(), kind, knownOutOfScopeInvariants);
		return result;
	}

	/**
	 * Parses a real {@code use-gui.jar -nogui} transcript and decides pass/fail, mutating {@code
	 * result} in place. Pulled out of {@link #runCmd} as a pure string-in function (no process/file
	 * I/O) specifically so a test can exercise the parsing and pass/fail decision directly against a
	 * captured transcript, without spawning a real subprocess. Package-private for that reason.
	 */
	static void applyParsedOutcome(SoilValidationResult result, String output, String kind,
			List<String> knownOutOfScopeInvariants) {
		String engineOutput = ECHOED_SCRIPT_LINE.matcher(output).replaceAll("");

		List<String> failedInvariants = new ArrayList<>();
		Matcher invM = INVARIANT_LINE.matcher(engineOutput);
		while (invM.find()) {
			if ("FAILED".equals(invM.group(2))) {
				failedInvariants.add(invM.group(1));
			}
		}
		result.failedInvariants = failedInvariants;

		Matcher m = CHECK_SUMMARY.matcher(engineOutput);
		if (m.find()) {
			result.numInvariantsChecked = Integer.parseInt(m.group(1));
			result.numFailures = Integer.parseInt(m.group(2));

			if ("invalid".equals(kind)) {
				// The point of this fixture is "the plugin correctly detects at least one problem" --
				// deliberately loose rather than asserting the exact invariant name, since that's
				// already spelled out in the fixture's own header comment for a human to cross-check
				// against failedInvariants below.
				result.passed = result.numFailures >= 1;
				result.note = result.passed ? "as expected" : "expected >=1 failure, got 0";
			} else {
				// "valid" kind: a documented, deliberately out-of-scope invariant (see
				// ExampleEntry.soilKnownOutOfScopeInvariants) is subtracted before judging -- it is
				// still recorded in failedInvariants either way, just not treated as a regression.
				List<String> unexpected = new ArrayList<>(failedInvariants);
				unexpected.removeAll(knownOutOfScopeInvariants);
				result.passed = unexpected.isEmpty();
				result.note = result.passed
						? (knownOutOfScopeInvariants.isEmpty() ? "as expected"
								: "as expected (excluding documented out-of-scope " + knownOutOfScopeInvariants + ")")
						: "unexpected failure(s) beyond the documented out-of-scope set: " + unexpected;
			}
		} else {
			result.passed = false;
			result.note = "could not find USE's 'checked N invariants..., M failures.' summary line in output"
					+ " (exit=" + result.exitCode + ")";
		}
	}
}
