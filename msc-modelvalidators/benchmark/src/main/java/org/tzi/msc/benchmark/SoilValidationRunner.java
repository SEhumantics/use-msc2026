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
	// Every command/compile/load failure the -nogui shell can report goes through use-core's
	// org.tzi.use.util.Log (Log.java:192-227), which has exactly three error formats:
	//   "Error: <msg>"            -- Log.error(String), used by every Shell.cmd* failure path; also
	//                                SoilCompiler's compile-failure prefix (SoilCompiler.java:268)
	//                                and Shell.java:1727's direct
	//                                Log.println("Error: File `<f>' could not be found!"), which is
	//                                what a missing `open <x>.soil' target actually prints
	//   "error in <class>: <msg>" -- Log.error(Object, String)
	//   "exception <class>: <msg>" -- Log.error(Exception) / Log.error(String, Exception)
	// All three are anchored at line start so that ordinary output which merely CONTAINS one of
	// these words is not mistaken for a USE error -- notably the JVM's own
	// "java.lang.UnsatisfiedLinkError: no natGNUReadline ..." readline warning, which every one of
	// these runs emits on a machine without GNU readline.
	private static final Pattern USE_ERROR_LINE = Pattern
			.compile("^(?:Error: .*|error in \\S+: .*|exception \\S+: .*)$", Pattern.MULTILINE);
	// `info state' prints "State: <name>" and then two Report tables (Shell.cmdInfoState,
	// Shell.java:851-926): objects per class first, links per association second. Each ends with a
	// dashed ruler followed by a "total : <n>" row. The FIRST ruler+total pair after the "State:"
	// header is therefore the object count -- the evidence that the fixture's hand-built instance
	// actually loaded.
	//
	// Two details keep a CLASS row from being read as the summary row (a model may legitimately
	// contain a class named "total", and the object table's header ruler sits directly above its
	// first class row): the row must follow a ruler, AND the number must be the last thing on the
	// line. A class row carries two numbers ("#objects" and "+ #objects in subclasses"), so it can
	// never satisfy the second condition; the summary row's third cell is empty, so it always does.
	//
	// The count is written by NumberFormat.getInstance(), whose grouping separator is
	// locale-dependent -- ',' and '.' (and the no-break spaces Java uses for e.g. fr) are accepted
	// and stripped in parseObjectTotal. A locale that groups with a plain ASCII space is
	// indistinguishable from this Report's column padding, so it does not match at all and the
	// fixture fails closed with "no `info state' object report found" rather than silently reading a
	// wrong count. (CHECK_SUMMARY above already assumes a '.' decimal separator, so such a locale
	// was never supported here in the first place.)
	private static final Pattern STATE_HEADER = Pattern.compile("^State: .*$", Pattern.MULTILINE);
	private static final Pattern REPORT_TOTAL_ROW = Pattern.compile(
			"^-{2,}[ \\t]*$\\R^total[ \\t]*:[ \\t]*([0-9][0-9.,\\u00A0\\u202F]*)[ \\t]*$", Pattern.MULTILINE);

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
					missing.errorLines = Collections.emptyList();
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
		boolean finished = waitForProcessAndCollectOutput(process, output, timeoutSeconds);
		if (!finished) {
			result.exitCode = -1;
			// Scanned even here: a fixture that hangs often prints the error that led to the hang
			// first, and the field must not be null in the emitted JSON either way.
			result.errorLines = collectErrorLines(ECHOED_SCRIPT_LINE.matcher(output.toString()).replaceAll(""));
			result.note = "timed out after " + timeoutSeconds + "s";
			return result;
		}
		result.exitCode = process.exitValue();
		applyParsedOutcome(result, output.toString(), kind, knownOutOfScopeInvariants);
		return result;
	}

	/**
	 * Drains merged child output concurrently while the caller's timeout is running. Reading a
	 * process stream synchronously before {@link Process#waitFor(long, TimeUnit)} makes the timeout
	 * ineffective: a child that hangs without closing stdout leaves {@code readLine()} blocked forever.
	 * Conversely, waiting before draining can deadlock a chatty child once its stdout pipe fills. A
	 * dedicated reader thread avoids both failure modes.
	 *
	 * <p>The process is forcibly terminated and reaped before joining the reader on timeout, which
	 * guarantees that the stream reaches EOF and the reader cannot keep this method blocked.
	 */
	static boolean waitForProcessAndCollectOutput(Process process, StringBuilder output, int timeoutSeconds)
			throws IOException, InterruptedException {
		IOException[] readFailure = new IOException[1];
		Thread outputReader = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append('\n');
				}
			} catch (IOException e) {
				readFailure[0] = e;
			}
		}, "soil-validation-output-reader");
		outputReader.start();

		boolean finished;
		try {
			finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
			if (!finished) {
				destroyAndReap(process);
			}
			outputReader.join();
		} catch (InterruptedException interrupted) {
			// An interrupted benchmark run must not strand a use-gui child (nor its non-daemon
			// output reader). Reap both before preserving the caller's interrupt semantics.
			destroyAndReap(process);
			joinUninterruptibly(outputReader);
			throw interrupted;
		}
		if (readFailure[0] != null) {
			throw readFailure[0];
		}
		return finished;
	}

	private static void destroyAndReap(Process process) {
		process.destroyForcibly();
		boolean interrupted = false;
		while (true) {
			try {
				process.waitFor();
				break;
			} catch (InterruptedException ignored) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private static void joinUninterruptibly(Thread thread) {
		boolean interrupted = false;
		while (true) {
			try {
				thread.join();
				break;
			} catch (InterruptedException ignored) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Parses a real {@code use-gui.jar -nogui} transcript and decides pass/fail, mutating {@code
	 * result} in place. Pulled out of {@link #runCmd} as a pure string-in function (no process/file
	 * I/O) specifically so a test can exercise the parsing and pass/fail decision directly against a
	 * captured transcript, without spawning a real subprocess. Package-private for that reason.
	 *
	 * <p>The invariant verdicts alone are NOT sufficient evidence that a fixture did its job. USE's
	 * {@code -nogui} shell does not abort on a failed {@code open}: it prints one {@code Error:} line,
	 * carries on with the empty state it already had, and then reports every invariant {@code OK} with
	 * {@code 0 failures} and exit code 0. A "valid-instance" fixture whose {@code open *.soil} target
	 * is missing or unparseable therefore passes vacuously if only the {@code checking invariant} /
	 * {@code checked N invariants, M failures.} lines are consulted -- proving nothing about the
	 * instance the fixture claims to validate. Three independent gates close that hole, and each is
	 * applied to BOTH kinds of fixture: a non-zero exit code, any USE error line, or a state that
	 * holds no objects at all fails the fixture outright, whatever the invariant verdicts said.
	 */
	static void applyParsedOutcome(SoilValidationResult result, String output, String kind,
			List<String> knownOutOfScopeInvariants) {
		String engineOutput = ECHOED_SCRIPT_LINE.matcher(output).replaceAll("");

		result.errorLines = collectErrorLines(engineOutput);
		result.numObjectsInState = parseObjectTotal(engineOutput);

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
				// still recorded in failedInvariants either way, just not treated as a regression. Do
				// not accept a summary failure whose corresponding detail line was not parsed: without
				// its invariant name we cannot establish that it is one of the documented exclusions.
				List<String> unexpected = new ArrayList<>(failedInvariants);
				unexpected.removeAll(knownOutOfScopeInvariants);
				if (result.numFailures != failedInvariants.size()) {
					result.passed = false;
					result.note = "USE summary reported " + result.numFailures + " failure(s), but only "
							+ failedInvariants.size() + " failed invariant detail line(s) could be parsed";
				} else {
					result.passed = unexpected.isEmpty();
					result.note = result.passed
							? (knownOutOfScopeInvariants.isEmpty() ? "as expected"
									: "as expected (excluding documented out-of-scope " + knownOutOfScopeInvariants + ")")
							: "unexpected failure(s) beyond the documented out-of-scope set: " + unexpected;
				}
			}
		} else {
			// Include the transcript tail: this branch fires when the run produced something other
			// than a completed `check -v', and "no summary line, exit=0" on its own is not enough to
			// tell a broken fixture from a truncated capture. (Observed once, unreproduced in 60
			// repeats of the same fixture -- without the tail there was nothing to diagnose.)
			result.passed = false;
			result.note = "could not find USE's 'checked N invariants..., M failures.' summary line in output"
					+ " (exit=" + result.exitCode + "); last output was: " + tail(engineOutput, 400);
		}

		applyEvidenceGates(result);
	}

	/**
	 * Overrides an otherwise-clean invariant verdict when the transcript shows the run itself was not
	 * sound. Runs last so that a fixture which already failed on its invariants keeps that (more
	 * specific) note appended rather than replaced.
	 */
	private static void applyEvidenceGates(SoilValidationResult result) {
		List<String> blockers = new ArrayList<>();
		if (result.exitCode != 0) {
			blockers.add("USE exited with code " + result.exitCode);
		}
		if (!result.errorLines.isEmpty()) {
			blockers.add("USE reported " + result.errorLines.size() + " error line(s): " + result.errorLines);
		}
		if (result.numObjectsInState == null) {
			blockers.add("no `info state' object report found in the transcript, so there is no evidence the"
					+ " fixture's instance loaded at all -- add `info state' to the .cmd file between its"
					+ " `open' and its `check -v'");
		} else if (result.numObjectsInState == 0) {
			blockers.add("`info state' reports 0 objects: the hand-built instance did not load, so every"
					+ " invariant verdict below was reached vacuously on an empty state");
		}
		if (blockers.isEmpty()) {
			return;
		}
		String previousNote = result.note;
		result.passed = false;
		result.note = String.join("; ", blockers)
				+ (previousNote == null || previousNote.isEmpty() ? "" : " [invariant verdict was: " + previousNote + "]");
	}

	/** Last {@code maxChars} characters of {@code text}, blank-line-collapsed, for a diagnostic note. */
	private static String tail(String text, int maxChars) {
		String collapsed = text.replaceAll("(?m)^\\s*$\\R", "").trim();
		if (collapsed.isEmpty()) {
			return "<no output>";
		}
		return collapsed.length() <= maxChars ? collapsed
				: "..." + collapsed.substring(collapsed.length() - maxChars);
	}

	/** All USE error lines in an echo-stripped transcript, in the order USE printed them. */
	private static List<String> collectErrorLines(String engineOutput) {
		List<String> errors = new ArrayList<>();
		Matcher m = USE_ERROR_LINE.matcher(engineOutput);
		while (m.find()) {
			errors.add(m.group().trim());
		}
		return errors;
	}

	/**
	 * Total object count from {@code info state}'s first (per-class) report, or {@code null} if the
	 * transcript contains no such report. Digits are extracted rather than parsed directly because
	 * {@code cmdInfoState} formats through {@link java.text.NumberFormat}, whose grouping separator is
	 * locale-dependent ({@code 1,024} / {@code 1.024} / {@code 1 024} all mean the same count).
	 */
	static Integer parseObjectTotal(String engineOutput) {
		Matcher state = STATE_HEADER.matcher(engineOutput);
		if (!state.find()) {
			return null;
		}
		Matcher total = REPORT_TOTAL_ROW.matcher(engineOutput);
		if (!total.find(state.end())) {
			return null;
		}
		String digits = total.group(1).replaceAll("\\D", "");
		if (digits.isEmpty() || digits.length() > 9) {
			return null;
		}
		return Integer.valueOf(digits);
	}
}
