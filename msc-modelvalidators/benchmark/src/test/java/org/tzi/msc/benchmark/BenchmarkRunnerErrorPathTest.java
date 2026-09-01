package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;
import org.tzi.use.config.Options;
import org.tzi.use.config.Options.WarningType;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Regression test for BUG B: {@code runOne}'s early return on
 * {@code PropertyConfigurationVisitor.containErrors()} (fired when a config file references a
 * malformed/unknown model element -- see {@code TinyBadConfig.properties}' [bad] section) used to
 * {@code return result;} directly, skipping {@code finalizeResult}/{@code recordReconstruction}
 * entirely. That left {@link SolverResult#allWitnessDigests} as raw Java {@code null} instead of
 * the {@code emptyList()} every other ERROR cell gets -- a real null-vs-empty-list inconsistency
 * for a {@code List<String>} field with no guard downstream, and one whose actual observable
 * symptom is that {@code writeResults}' plain (non-serializeNulls) Gson OMITS the key entirely from
 * the written JSON on this one path, unlike every other ERROR row.
 *
 * <p>{@code runOne} is {@code private}, and {@code main()} always reads its example list from the
 * packaged {@code /manifest.json} classpath resource (not from test-controllable args), so there is
 * no way to drive this exact path through the public API with a small, isolated fixture -- this
 * test invokes the real, unmodified {@code runOne} via reflection instead of mocking anything,
 * against a real compiled {@code MModel} and a real {@link org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor}
 * run that genuinely fails, so every layer up to and including the bug's own return statement is
 * exercised for real.
 */
public class BenchmarkRunnerErrorPathTest {

	private static final File EX_DIR = new File("src/test/resources/badconfig");

	private static MModel compile(File useFile) throws Exception {
		try (FileInputStream specStream = new FileInputStream(useFile)) {
			return USECompiler.compileSpecification(specStream, useFile.getName(), new PrintWriter(System.err),
					new ModelFactory());
		}
	}

	private static ExampleEntry exampleEntry(String section) {
		ExampleEntry ex = new ExampleEntry();
		ex.id = "TinyBadConfig";
		ex.useFile = "TinyBadConfig.use";
		ex.propertiesFile = "TinyBadConfig.properties";
		ex.section = section;
		ex.bitwidth = 4;
		return ex;
	}

	/** Reflective call to the real, unmodified {@code BenchmarkRunner.runOne} -- see class javadoc. */
	private static SolverResult invokeRunOne(MModel mModel, ExampleEntry ex, String solver, int repeats,
			int warmups, boolean watchdogEnabled) throws Exception {
		Method m = BenchmarkRunner.class.getDeclaredMethod("runOne", MModel.class, File.class, ExampleEntry.class,
				String.class, int.class, int.class, boolean.class);
		m.setAccessible(true);
		try {
			return (SolverResult) m.invoke(null, mModel, EX_DIR, ex, solver, repeats, warmups, watchdogEnabled);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof Exception) {
				throw (Exception) e.getCause();
			}
			throw e;
		}
	}

	@Test
	public void containErrorsEarlyReturnNormalizesWitnessDigestsToEmptyList() throws Exception {
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;
		MModel mModel = compile(new File(EX_DIR, "TinyBadConfig.use"));

		SolverResult result = invokeRunOne(mModel, exampleEntry("bad"), "DefaultSAT4J", 1, 0, false);

		assertEquals("ERROR", result.outcome);
		assertNotNull("error field must explain the PropertyConfigurationVisitor failure", result.error);
		assertTrue("expected the real containErrors() message, got: " + result.error,
				result.error.contains("PropertyConfigurationVisitor"));

		// THE bug itself: before the fix, allWitnessDigests stayed raw Java null on this path.
		assertNotNull("allWitnessDigests must never be raw null on an ERROR cell -- BUG B", result.allWitnessDigests);
		assertTrue("allWitnessDigests must be empty on this early-return ERROR path",
				result.allWitnessDigests.isEmpty());
		assertNull("witnessDigest must stay null on ERROR", result.witnessDigest);
		// Symmetric with the normal end-of-loop ERROR path: no reconstruction/re-check claim either.
		assertNull(result.reconstructed);
		assertNull(result.useChecked);

		// Reproduces the actual downstream symptom, not just the in-memory field: writeResults() uses
		// a plain (non-serializeNulls) Gson, which OMITS a null field entirely but PRINTS an empty
		// list -- so a raw-null allWitnessDigests silently drops the key from results.json, while
		// the fixed emptyList() prints it explicitly, same as every other ERROR row.
		String json = new com.google.gson.Gson().toJson(result);
		assertTrue("serialized ERROR row must carry an explicit empty allWitnessDigests array, not"
				+ " silently omit the key: " + json, json.contains("\"allWitnessDigests\":[]"));
	}

	/**
	 * Control case, same fixture: the [good] section configures cleanly and must reach a real
	 * (non-ERROR) outcome, proving TinyBadConfig.use itself is not what makes [bad] fail -- only its
	 * deliberately-broken association line does.
	 */
	@Test
	public void cleanSectionSolvesNormallyAndIsUnaffectedByTheFix() throws Exception {
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;
		MModel mModel = compile(new File(EX_DIR, "TinyBadConfig.use"));

		SolverResult result = invokeRunOne(mModel, exampleEntry("good"), "DefaultSAT4J", 1, 0, false);

		assertNotEquals("control case: [good] must configure and solve cleanly", "ERROR", result.outcome);
		assertNotNull(result.allWitnessDigests);
	}
}
