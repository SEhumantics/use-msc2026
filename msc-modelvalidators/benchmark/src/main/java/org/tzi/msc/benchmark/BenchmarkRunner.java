package org.tzi.msc.benchmark;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.config.Options;
import org.tzi.use.config.Options.WarningType;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Runs every example in {@code manifest.json} in-process (the same solve/reconstruct API
 * EndToEndValidationTest uses), across every usable SAT solver backend, and emits a structured
 * JSON result file for {@link ReportBuilder} to render.
 *
 * <p><b>Hang safety, and why there is no in-process solve timeout.</b> A single
 * {@code validator.validate(model)} call has no timeout at all -- confirmed by directly researching
 * every layer of this chain: {@code kodkod.engine.config.Options} has no timeout field;
 * {@code kodkod.engine.satlab.SAT4J} never calls the underlying SAT4J {@code ISolver}'s own
 * {@code setTimeout}/{@code setTimeoutMs} (which exist but are simply never wired up here); and the
 * native backends (MiniSat/MiniSatProver/Lingeling) expose a JNI {@code native boolean solve(long)}
 * that takes no time budget and offers no cancellation path once called. A same-JVM
 * thread-interrupt-based timeout was deliberately rejected as UNSAFE, not just undesirable: SAT4J's
 * solve loop never checks {@code Thread.interrupted()} (confirmed: zero references to "interrupt"
 * anywhere in the SAT4J jar), and a thread blocked inside native {@code solve(long)} generally will
 * not honor interrupt at all. Worse, an abandoned-but-still-running worker thread would keep
 * executing while {@code KodkodModelValidatorConfiguration.getInstance()} (mutated by
 * {@code setSatFactory}/{@code setBitwidth} below) and {@code PluginModelFactory.INSTANCE}'s cache are
 * shared, mutable singletons -- letting the main thread move on to the next cell while a stuck
 * worker keeps running risks silently corrupting or misattributing results for every cell after the
 * hang, not just the hung one. A killed OS process cannot keep mutating anything, which is the one
 * mechanism that can actually guarantee both termination and no such race; that isolation is provided
 * at the process level instead (see {@code scripts/run-benchmark.sh}'s outer {@code timeout} wrapper),
 * not inside this class. What this class DOES do, safely, in-process: (1) write {@code outputJson}
 * incrementally after every completed cell, not just once at the end, so a forced kill from outside
 * loses at most the one cell that was actually stuck; (2) run a purely observational watchdog thread
 * per solve that only sleeps and logs a warning to stderr if a cell runs unusually long -- it never
 * touches solver state or attempts cancellation, so it carries none of the race risk above.
 *
 * <p><b>This whole safety layer is optional, on purpose.</b> The original kk-modelvalidator plugin has
 * none of it -- no timeout, no incremental output, no watchdog -- and this benchmark exists partly to
 * produce numbers comparable against that original, unmodified baseline behavior. The outer
 * {@code timeout} wrapper (see {@code scripts/run-benchmark.sh}) and the per-solve watchdog thread here
 * are both independently toggleable via the {@code watchdog} argument below (the incremental-write
 * behavior is always on regardless, since it changes no measured value at all -- it only changes how
 * often the same data hits disk, entirely outside every timed region). With the watchdog off, the timed
 * region around {@code validator.validate(model)} is byte-for-byte what it was before this safety layer
 * existed: no extra thread is created, no extra call happens inside the {@code nanoTime()} window.
 *
 * Usage: {@code java -Djava.library.path=<vendored-solvers/x64> -cp <classpath>
 * org.tzi.msc.benchmark.BenchmarkRunner <examples-dir> <output-json> [repeats] [warmups] [watchdog]}
 * ({@code watchdog} defaults to {@code true}; pass {@code false} for byte-for-byte original timing.)
 */
public class BenchmarkRunner {

	static final String[] SOLVERS = { "DefaultSAT4J", "LightSAT4J", "MiniSat", "MiniSatProver", "Lingeling" };

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: BenchmarkRunner <examples-dir> <output-json> [repeats] [warmups] [watchdog]");
			System.exit(1);
		}
		File examplesDir = new File(args[0]);
		File outputJson = new File(args[1]);
		int repeats = args.length >= 3 ? Integer.parseInt(args[2]) : 5;
		int warmups = args.length >= 4 ? Integer.parseInt(args[3]) : 0; // opt-in: unchanged behavior by default
		boolean watchdogEnabled = args.length >= 5 ? Boolean.parseBoolean(args[4]) : true;

		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;

		Gson manifestGson = new Gson();
		ExampleManifest manifest;
		try (InputStreamReader r = new InputStreamReader(
				BenchmarkRunner.class.getResourceAsStream("/manifest.json"), StandardCharsets.UTF_8)) {
			manifest = manifestGson.fromJson(r, ExampleManifest.class);
		}

		List<SolverResult> allResults = new ArrayList<>();
		for (ExampleEntry ex : manifest.examples) {
			if (ex.propertiesFile == null || !ex.mode.contains("finding")) {
				continue; // validating-only examples don't touch a SAT solver at all
			}
			System.err.println("=== " + ex.id + " ===");
			File exDir = new File(examplesDir, ex.directory);
			MModel mModel;
			try {
				mModel = compile(new File(exDir, ex.useFile));
			} catch (Exception e) {
				// One broken .use file must not abort every other example's results -- record one
				// ERROR cell per solver for this example (so it's still visible in the report) and
				// move on, instead of letting the exception propagate out of main().
				System.err.println("  COMPILE ERROR for " + ex.id + ": " + e);
				for (String solver : SOLVERS) {
					SolverResult result = new SolverResult();
					result.exampleId = ex.id;
					result.solver = solver;
					result.outcome = "ERROR";
					result.error = "compile(" + ex.useFile + ") failed: " + e.getClass().getSimpleName() + ": "
							+ e.getMessage();
					allResults.add(result);
				}
				writeResults(allResults, outputJson);
				continue;
			}
			int effectiveRepeats = ex.repeats != null ? ex.repeats : repeats;
			int effectiveWarmups = ex.warmups != null ? ex.warmups : warmups;

			for (String solver : SOLVERS) {
				SolverResult result;
				try {
					result = runOne(mModel, exDir, ex, solver, effectiveRepeats, effectiveWarmups, watchdogEnabled);
				} catch (Exception e) {
					// Defense in depth: runOne() already isolates failures per-repeat internally, but
					// nothing above should be able to take the whole run down if it does anyway.
					System.err.println("  UNCAUGHT ERROR for " + ex.id + "/" + solver + ": " + e);
					result = new SolverResult();
					result.exampleId = ex.id;
					result.solver = solver;
					result.outcome = "ERROR";
					result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
				}
				allResults.add(result);
				writeResults(allResults, outputJson);
				System.err.printf("  %-15s outcome=%-14s wall(median/min/max)=%.2f/%.2f/%.2fms%n",
						solver, result.outcome, result.medianWallMs, result.minWallMs, result.maxWallMs);
			}
		}

		writeResults(allResults, outputJson);
		System.err.println("Wrote " + allResults.size() + " results to " + outputJson);
	}

	/**
	 * Writes the full, growing result list after every completed cell (not just once at the end) --
	 * cheap at this scale (dozens of cells, a JSON payload well under a megabyte) and means an
	 * external {@code timeout} kill (see the class javadoc) loses at most the one cell that was
	 * actually in progress, not every cell measured before it.
	 */
	private static void writeResults(List<SolverResult> allResults, File outputJson) throws java.io.IOException {
		Gson outGson = new GsonBuilder().setPrettyPrinting().create();
		try (PrintWriter w = new PrintWriter(new FileWriter(outputJson, StandardCharsets.UTF_8))) {
			w.write(outGson.toJson(allResults));
		}
	}

	private static MModel compile(File useFile) throws Exception {
		try (FileInputStream specStream = new FileInputStream(useFile)) {
			return USECompiler.compileSpecification(specStream, useFile.getName(),
					new PrintWriter(System.err), new ModelFactory());
		}
	}

	private static SolverResult runOne(MModel mModel, File exDir, ExampleEntry ex, String solver, int repeats,
			int warmups, boolean watchdogEnabled) throws Exception {
		SolverResult result = new SolverResult();
		result.exampleId = ex.id;
		result.solver = solver;
		result.repeats = repeats;
		result.warmups = warmups;
		result.numSearches = 1; // opaque via the public API for internal retry cases; wall time still covers them
		List<Double> wallMs = new ArrayList<>();
		List<Long> kodkodSolveMs = new ArrayList<>();
		List<Long> kodkodTranslateMs = new ArrayList<>();
		List<String> digests = new ArrayList<>();
		String outcome = null;

		// The first `warmups` iterations exercise the exact same solve path (JVM/JIT warm-up matters
		// here: solve times range from single-digit milliseconds to tens of seconds, and a cold first
		// call is not representative) but are discarded -- only iterations at index >= warmups are
		// recorded into wallMs/kodkodSolveMs/kodkodTranslateMs/digests below.
		for (int i = 0; i < warmups + repeats; i++) {
			boolean isWarmup = i < warmups;
			try {
				Session session = new Session();
				MSystem mSystem = new MSystem(mModel);
				session.setSystem(mSystem);

				invalidatePluginModelFactoryCache();
				IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

				INIConfiguration ini = new INIConfiguration();
				ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
				try (FileReader reader = new FileReader(new File(exDir, ex.propertiesFile))) {
					ini.read(reader);
				}
				String section = ex.section != null ? ex.section
						: (ini.getSections().isEmpty() ? null : ini.getSections().iterator().next());
				Configuration config = ini.getSection(section);
				PropertyConfigurationVisitor visitor = new PropertyConfigurationVisitor(config,
						new PrintWriter(System.err, true));
				model.accept(visitor);
				if (visitor.containErrors()) {
					result.outcome = "ERROR";
					result.error = "PropertyConfigurationVisitor reported errors for section [" + section + "]";
					return result;
				}

				KodkodModelValidatorConfiguration.getInstance().setSatFactory(solver);
				KodkodModelValidatorConfiguration.getInstance().setBitwidth(ex.bitwidth);

				UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
				Thread watchdog = watchdogEnabled
						? startHangWatchdog(ex.id + "/" + solver + " " + (isWarmup ? "warmup" : "repeat") + " " + i)
						: null;
				long t0 = System.nanoTime();
				if (watchdog != null) {
					try {
						validator.validate(model);
					} finally {
						watchdog.interrupt();
					}
				} else {
					// watchdogEnabled=false: nothing but the original call happens in this timed region --
					// byte-for-byte the same as before this safety layer existed.
					validator.validate(model);
				}
				long t1 = System.nanoTime();
				if (!isWarmup) {
					wallMs.add((t1 - t0) / 1_000_000.0);
				}

				if (validator.solution() != null) {
					outcome = validator.solution().outcome().toString();
					if (!isWarmup) {
						kodkodSolveMs.add(validator.solution().stats().solvingTime());
						kodkodTranslateMs.add(validator.solution().stats().translationTime());
					}
					if (!isWarmup && isWitnessCapturingOutcome(outcome)) {
						MSystemState state = mSystem.state();
						digests.add(WitnessDigest.digest(mModel, state));
					}
				}
			} catch (Exception e) {
				// Isolate a single failing repeat: keep whatever wall-time/digest data earlier
				// repeats already produced, mark this cell as an ERROR rather than letting the
				// exception propagate out of runOne() and abort every remaining example/solver in
				// the manifest.
				System.err.println("  " + (isWarmup ? "warmup " : "repeat ") + i + " failed: " + e);
				result.outcome = "ERROR";
				result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
				break;
			} finally {
				invalidatePluginModelFactoryCache();
			}
		}

		if (result.outcome == null) {
			result.outcome = outcome == null ? "ERROR" : outcome;
		}
		result.medianWallMs = median(wallMs);
		result.minWallMs = wallMs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
		result.maxWallMs = wallMs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
		result.medianKodkodSolvingMs = (long) medianLong(kodkodSolveMs);
		result.medianKodkodTranslationMs = (long) medianLong(kodkodTranslateMs);
		result.allWitnessDigests = digests;
		result.witnessDigest = digests.isEmpty() ? null : digests.get(0);
		return result;
	}

	/**
	 * True for the two outcomes that mean Kodkod actually reconstructed a satisfying instance (so a
	 * witness digest may be captured), false otherwise. Kodkod's {@code Solution.Outcome} enum has
	 * exactly 4 values: SATISFIABLE, UNSATISFIABLE, TRIVIALLY_SATISFIABLE, TRIVIALLY_UNSATISFIABLE. A
	 * plain {@code outcome.contains("SATISFIABLE")} substring check matches ALL FOUR of them --
	 * "UNSATISFIABLE" and "TRIVIALLY_UNSATISFIABLE" both contain "SATISFIABLE" as a substring -- which
	 * is exactly the bug this method exists to not repeat: an earlier version of this check silently
	 * captured a "witness" from an empty/default system state on UNSAT results too, since no solution
	 * was ever reconstructed there. Package-private (not private) so a test can exercise this directly
	 * without a real Kodkod solve.
	 */
	static boolean isWitnessCapturingOutcome(String outcome) {
		return "SATISFIABLE".equals(outcome) || "TRIVIALLY_SATISFIABLE".equals(outcome);
	}

	private static final long WATCHDOG_FIRST_WARNING_SECONDS = 60;

	/**
	 * Starts a purely observational daemon thread that logs a warning to stderr if {@code label}'s
	 * solve is still running after an unusually long time (doubling the wait each time, so it doesn't
	 * spam during a merely slow, not hung, solve). It never touches solver state and never attempts to
	 * cancel anything -- see the class javadoc for why in-process cancellation isn't safe here. The
	 * caller must call {@code interrupt()} on the returned thread once the solve completes; that
	 * interrupt only ever breaks the watchdog's own {@code Thread.sleep}, never the solve itself.
	 */
	private static Thread startHangWatchdog(String label) {
		Thread watchdog = new Thread(() -> {
			long warnAfterSeconds = WATCHDOG_FIRST_WARNING_SECONDS;
			try {
				while (true) {
					Thread.sleep(warnAfterSeconds * 1000L);
					System.err.println("  WARNING: " + label + " has now been running for over "
							+ warnAfterSeconds + "s -- possibly hung. No safe in-process cancellation exists"
							+ " for this call (see BenchmarkRunner's class javadoc); if this repeats, kill this"
							+ " process (the outer `timeout` wrapper in scripts/run-benchmark.sh will do so"
							+ " automatically) and re-run without this example/solver.");
					warnAfterSeconds *= 2;
				}
			} catch (InterruptedException expected) {
				// Normal shutdown: the solve finished and the caller interrupted us to stop watching.
			}
		});
		watchdog.setDaemon(true);
		watchdog.setName("hang-watchdog-" + label);
		watchdog.start();
		return watchdog;
	}

	/**
	 * {@code PluginModelFactory.INSTANCE} caches a single transformed {@link IModel} behind a
	 * private {@code reTransform} gate (see EndToEndValidationTest for the same technique) --
	 * required here because this runner iterates many distinct models/configs in one JVM.
	 */
	private static void invalidatePluginModelFactoryCache() throws Exception {
		java.lang.reflect.Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
		reTransform.setAccessible(true);
		reTransform.set(PluginModelFactory.INSTANCE, true);
	}

	private static double median(List<Double> values) {
		if (values.isEmpty()) {
			return 0;
		}
		List<Double> sorted = new ArrayList<>(values);
		sorted.sort(Double::compareTo);
		int mid = sorted.size() / 2;
		return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
	}

	private static double medianLong(List<Long> values) {
		if (values.isEmpty()) {
			return 0;
		}
		List<Long> sorted = new ArrayList<>(values);
		sorted.sort(Long::compareTo);
		int mid = sorted.size() / 2;
		return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
	}
}
