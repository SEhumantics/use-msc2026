package org.tzi.msc.benchmark;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.ConfigurationReader;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.RawConfiguration;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.verify.InvariantVerdict;
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
	static final String SMT_SOLVER_NAME = "SMT-Z3";

	/** {@link #SOLVERS} plus the SMT backend, for the compile-error fallback path only. */
	private static List<String> allSolverNames() {
		List<String> names = new ArrayList<>(Arrays.asList(SOLVERS));
		names.add(SMT_SOLVER_NAME);
		return names;
	}

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
		validateIterationCounts(repeats, warmups, "command line");

		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;

		Gson manifestGson = new Gson();
		ExampleManifest manifest;
		try (InputStreamReader r = new InputStreamReader(
				BenchmarkRunner.class.getResourceAsStream("/manifest.json"), StandardCharsets.UTF_8)) {
			manifest = manifestGson.fromJson(r, ExampleManifest.class);
		}

		List<SolverResult> allResults = new ArrayList<>();
		// One persistent Z3 process, reused across every SMT-Z3 solve in this whole run instead of
		// spawning a fresh OS process per call (see SolverProcess's own class javadoc for why: a
		// fresh-process-per-call floor of ~13-20ms otherwise dominates every solve on small
		// scenarios). Kodkod's own five backends are unaffected -- runOne's own solver calls are
		// untouched.
		try (SolverProcess smtSolverProcess =
				SolverProcess.persistent(SolverBinary.resolve(), Duration.ofSeconds(30))) {
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
				for (String solver : allSolverNames()) {
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
			validateIterationCounts(effectiveRepeats, effectiveWarmups, "manifest entry " + ex.id);

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

			SolverResult smtResult;
			try {
				smtResult = runOneSmt(mModel, exDir, ex, effectiveRepeats, effectiveWarmups, smtSolverProcess);
			} catch (Exception e) {
				System.err.println("  UNCAUGHT ERROR for " + ex.id + "/" + SMT_SOLVER_NAME + ": " + e);
				smtResult = new SolverResult();
				smtResult.exampleId = ex.id;
				smtResult.solver = SMT_SOLVER_NAME;
				smtResult.outcome = "ERROR";
				smtResult.error = e.getClass().getSimpleName() + ": " + e.getMessage();
			}
			allResults.add(smtResult);
			writeResults(allResults, outputJson);
			System.err.printf("  %-15s outcome=%-14s wall(median/min/max)=%.2f/%.2f/%.2fms%n",
					SMT_SOLVER_NAME, smtResult.outcome, smtResult.medianWallMs, smtResult.minWallMs,
					smtResult.maxWallMs);
			}
		}

		writeResults(allResults, outputJson);
		System.err.println("Wrote " + allResults.size() + " results to " + outputJson);
	}

	/**
	 * A zero/negative repeat count would skip the solve loop and become a fabricated ERROR row with
	 * zero timings; a negative warmup count is equally nonsensical. Fail at the configuration boundary
	 * with the responsible source instead of silently emitting data that looks like a solver failure.
	 */
	static void validateIterationCounts(int repeats, int warmups, String source) {
		if (repeats < 1) {
			throw new IllegalArgumentException(source + ": repeats must be at least 1, got " + repeats);
		}
		if (warmups < 0) {
			throw new IllegalArgumentException(source + ": warmups must be non-negative, got " + warmups);
		}
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

	/**
	 * SMT-backend counterpart to {@link #runOne}, structured the same way (repeat loop, warmups
	 * discarded, one ERROR cell per failing repeat rather than aborting the run) but driving
	 * {@link SmtModelFinder} instead of Kodkod -- an entirely separate configuration/solve pipeline
	 * (see {@code KodkodSmtDifferentialTest}, which already proves both read the same {@code
	 * .properties} file), not a plug-in point on {@link #runOne}. Most non-Library examples are
	 * expected to report ERROR here: Phase 3's OCL translation coverage is real but partial (leaf
	 * expressions, forAll/allInstances, single navigation/exists -- see the master plan), and {@code
	 * ConfigurationReader.requireSupported()}/{@code FragmentChecker} fail closed rather than
	 * silently approximating anything outside that. That is the honest, intended result of running
	 * this against the full manifest, not a bug to hide.
	 */
	private static SolverResult runOneSmt(MModel mModel, File exDir, ExampleEntry ex, int repeats,
			int warmups, SolverProcess smtSolverProcess) {
		SolverResult result = new SolverResult();
		result.exampleId = ex.id;
		result.solver = SMT_SOLVER_NAME;
		result.repeats = repeats;
		result.warmups = warmups;
		result.numSearches = 1;
		List<Double> wallMs = new ArrayList<>();
		List<String> digests = new ArrayList<>();
		String outcome = null;
		Boolean reconstructed = null;
		Boolean useChecked = null;

		for (int i = 0; i < warmups + repeats; i++) {
			boolean isWarmup = i < warmups;
			try {
				ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(mModel);
				RawConfiguration raw = ConfigurationReader
						.read(new File(exDir, ex.propertiesFile).toPath(), ex.section);
				AnalysisConfiguration config = ConfigurationReader.normalize(raw, vocabulary).requireSupported();

				long t0 = System.nanoTime();
				ModelFinderResult finderResult = SmtModelFinder.find(mModel, config, smtSolverProcess);
				long t1 = System.nanoTime();
				if (!isWarmup) {
					wallMs.add((t1 - t0) / 1_000_000.0);
				}

				// ModelFinderResult#allActiveInvariantsHold() checks EVERY invariant declared in
				// the model, not merely the ones this scenario's own .properties section marked
				// active -- its own javadoc says "all hold" without the "active" qualifier its
				// name implies. An inactive invariant is never solver-enforced, so a found witness
				// is free to leave one false without that being a genuine UNSAT; using the blanket
				// check here mislabelled Genealogy/RecursiveTree as UNSATISFIABLE the moment they
				// first reached a real witness (both have an inactive invariant that happens not
				// to hold for it), even though Kodkod -- and this same witness's own ACTIVE
				// invariants -- agree it is SATISFIABLE. Filtering verdicts to config's own active
				// set reproduces the SAME independent-oracle check, scoped to what was actually
				// requested.
				boolean sat = finderResult.satisfiable()
						&& finderResult.verdicts().stream()
								.filter(v -> config.activeInvariants().contains(v.invariantName()))
								.allMatch(InvariantVerdict::holds);
				outcome = sat ? "SATISFIABLE" : "UNSATISFIABLE";
				// Two independent facts, read off two independent accessors -- see
				// SolverResult#reconstructed. system() is non-null exactly when a scenario was
				// witnessed (a snapshot exists at all); allActiveInvariantsHold() additionally
				// requires that the USE evaluator's own verdicts over that snapshot all hold.
				reconstructed = finderResult.system() != null;
				useChecked = sat;
				if (!isWarmup && sat) {
					digests.add(WitnessDigest.digest(mModel, finderResult.system().state()));
				}
			} catch (Exception e) {
				System.err.println("  " + (isWarmup ? "warmup " : "repeat ") + i + " failed: " + e);
				result.outcome = "ERROR";
				result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
				break;
			}
		}

		finalizeResult(result, outcome, wallMs, List.of(), List.of(), digests);
		recordReconstruction(result, reconstructed, useChecked);
		return result;
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
					// Route through the same finalize/record steps the normal end-of-loop path below
					// uses, rather than returning `result` as-is: without this, `allWitnessDigests`
					// stayed raw Java `null` (finalizeResult is the only place that normalizes it to
					// emptyList()) instead of the empty-list-on-ERROR contract every other ERROR cell
					// gets -- a real null-vs-empty-list inconsistency for a List<String> field with no
					// guard downstream. wallMs/kodkodSolveMs/kodkodTranslateMs/digests are still empty
					// here (this fires before any repeat ever reaches validate()), so this is a pure
					// normalization, not a change to any recorded timing or witness data.
					finalizeResult(result, null, wallMs, kodkodSolveMs, kodkodTranslateMs, digests);
					recordReconstruction(result, isWitnessCapturingOutcome(result.outcome), null);
					return result;
				}

				KodkodModelValidatorConfiguration.getInstance().setSatFactory(solver);
				KodkodModelValidatorConfiguration.getInstance().setBitwidth(ex.bitwidth);

				UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
				Thread watchdog = watchdogEnabled
						? startHangWatchdog(ex.id + "/" + solver + " " + (isWarmup ? "warmup" : "repeat") + " " + i)
						: null;
				long[] window = timeThenCleanup(() -> validator.validate(model),
						watchdog == null ? null : watchdog::interrupt);
				long t0 = window[0];
				long t1 = window[1];
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

		finalizeResult(result, outcome, wallMs, kodkodSolveMs, kodkodTranslateMs, digests);
		// The incumbent re-checks nothing: it reports its own solver's verdict and stops. So the
		// snapshot fact is available (it did or did not reconstruct one) but the USE re-evaluation
		// fact is not -- null, not false. Study A prints that as not-applicable, and that gap IS a
		// finding, not a hole in this runner.
		recordReconstruction(result, isWitnessCapturingOutcome(result.outcome), null);
		return result;
	}

	/**
	 * Populates {@code result}'s outcome/timing/witness fields from the accumulated per-repeat data.
	 * Pulled out of {@link #runOne} as a pure function (no session/model/solver access) specifically so
	 * a test can exercise the finalization logic directly, the same reason
	 * {@link SoilValidationRunner#applyParsedOutcome} was pulled out of its own caller. Package-private
	 * for that reason.
	 *
	 * <p>Regression note: a witness digest represents "the" answer for this cell -- only report one
	 * when the cell as a whole succeeded. If a later repeat failed after earlier repeats already
	 * populated {@code digests}, {@link #runOne}'s catch block deliberately keeps that partial data
	 * (see its own comment) rather than discarding it, but it must not be surfaced as
	 * witnessDigest/allWitnessDigests: that would contradict this field's own "null on
	 * UNSATISFIABLE/ERROR" contract (see {@link SolverResult#witnessDigest}) and the report template
	 * renders the witness column with no ERROR guard, unlike every other per-repeat column.
	 */
	static void finalizeResult(SolverResult result, String lastOutcome, List<Double> wallMs,
			List<Long> kodkodSolveMs, List<Long> kodkodTranslateMs, List<String> digests) {
		if (result.outcome == null) {
			result.outcome = lastOutcome == null ? "ERROR" : lastOutcome;
		}
		result.medianWallMs = median(wallMs);
		result.minWallMs = wallMs.stream().mapToDouble(Double::doubleValue).min().orElse(0);
		result.maxWallMs = wallMs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
		result.medianKodkodSolvingMs = (long) medianLong(kodkodSolveMs);
		result.medianKodkodTranslationMs = (long) medianLong(kodkodTranslateMs);
		if ("ERROR".equals(result.outcome)) {
			result.allWitnessDigests = java.util.Collections.emptyList();
			result.witnessDigest = null;
		} else {
			result.allWitnessDigests = digests;
			result.witnessDigest = digests.isEmpty() ? null : digests.get(0);
		}
	}

	/**
	 * Records this cell's {@link SolverResult#reconstructed}/{@link SolverResult#useChecked} facts,
	 * subject to the same discipline {@link #finalizeResult} applies to witness digests: an ERROR cell
	 * reached no verdict, so it makes no reconstruction or re-evaluation claim at all, whatever a
	 * partially-completed repeat managed to build before it threw. Package-private and pure so a test
	 * can exercise it without a solver.
	 *
	 * @param reconstructed whether a snapshot was actually built; null when the backend does not say
	 * @param useChecked whether an INDEPENDENT USE re-evaluation of that snapshot passed; null when no
	 *     such check was performed -- never false, which would claim it was performed and failed
	 */
	static void recordReconstruction(SolverResult result, Boolean reconstructed, Boolean useChecked) {
		if ("ERROR".equals(result.outcome)) {
			result.reconstructed = null;
			result.useChecked = null;
			return;
		}
		result.reconstructed = reconstructed;
		result.useChecked = useChecked;
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

	/**
	 * Times {@code work} into a strict {@code [t0,t1]} window -- {@code t1} is captured the INSTANT
	 * {@code work} returns normally -- then runs {@code cleanup} (if non-null) strictly AFTER {@code
	 * t1}, in a {@code finally} so it still runs even when {@code work} throws. Pulled out of
	 * {@link #runOne} as its own testable unit, the same reason {@link #finalizeResult} and
	 * {@link #recordReconstruction} were: {@code BenchmarkRunnerTest} exercises the before/after
	 * ordering directly with fake work/cleanup, deterministically, instead of relying on measuring a
	 * real solve's timing (which the actual overhead here -- tens of microseconds -- would be far too
	 * small and noisy to assert on reliably).
	 *
	 * <p>Regression note (BUG A): {@code runOne}'s watchdog cleanup ({@code watchdog.interrupt()})
	 * used to run inside the SAME {@code finally} block as the timed call itself, BEFORE {@code t1}
	 * was captured -- so every watchdog-enabled Kodkod call paid ~17-70us of {@code interrupt()}
	 * overhead that {@code runOneSmt}'s SMT path (which has no watchdog at all) never paid. That is a
	 * small but SYSTEMATIC (not random) timing asymmetry favoring whichever side lacks the watchdog,
	 * and since {@code watchdogEnabled} defaults to {@code true} and {@code run-benchmark.sh} defaults
	 * {@code safety=on}, it is very likely present in the committed RQ4 timing data. Calling {@code
	 * cleanup} only after this method has already captured {@code t1} is what keeps that asymmetry
	 * from recurring.
	 *
	 * @return {@code {t0, t1}}, both from {@link System#nanoTime()}
	 */
	static long[] timeThenCleanup(Runnable work, Runnable cleanup) {
		long t0 = System.nanoTime();
		long t1;
		try {
			work.run();
			t1 = System.nanoTime();
		} finally {
			if (cleanup != null) {
				cleanup.run();
			}
		}
		return new long[] { t0, t1 };
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
