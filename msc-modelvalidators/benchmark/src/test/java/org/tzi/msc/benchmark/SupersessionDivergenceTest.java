package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.junit.BeforeClass;
import org.junit.Test;
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
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

import com.google.gson.Gson;

import kodkod.engine.Solution;

/**
 * Study B (spec S9, RQ2) supersession evidence for the two cases where the incumbent returns a
 * WRONG answer and the SMT finder returns the right one:
 *
 * <ul>
 * <li><b>IntegerBitwidth-DailyCap</b> -- an integer model whose correct answer requires a Kodkod
 * bitwidth far beyond any practical setting. Every value in the configured search domain
 * satisfies the invariant, so a witness cannot be missing for want of a candidate; the incumbent
 * still reports UNSATISFIABLE at the corpus-wide bitwidth of 8.</li>
 * <li><b>RealGrid-UnitInterval</b> -- a real-valued model whose witness must fall between the
 * incumbent's 0.5 grid points, inside the incumbent's OWN default real range.</li>
 * </ul>
 *
 * <p>Every claim here is checked three ways, because a supersession case that does not actually
 * supersede is worse than none: (1) the incumbent is run on the SAME unmodified corpus files at the
 * SAME manifest bitwidth and its real outcome is pinned, not assumed; (2) the SMT finder's witness
 * is re-checked by plain arithmetic in this test, so ground truth does not depend on either tool;
 * (3) the USE evaluator's own verdicts over the reconstructed snapshot are asserted.
 *
 * <p>A fourth check guards the one place these configurations are not read identically by both
 * backends. The SMT finder needs a per-attribute domain ({@code Class_attr_min}/{@code _max}) to
 * register an attribute at all; the incumbent has no such key -- for it, {@code Class_attr_min} is
 * {@code PropertyEntry.attributeDefValuesMin}, the number of DEFINED VALUES the attribute may take.
 * {@link #kodkodOutcomeIsUnchangedWhenTheKeysItMisreadsAreRemoved} re-runs the incumbent with those
 * keys stripped out entirely and asserts the outcome is identical, so neither divergence can be
 * blamed on a key the incumbent reads differently.
 */
public class SupersessionDivergenceTest {

	private static final String INTEGER_CASE = "IntegerBitwidth-DailyCap";
	private static final String REAL_CASE = "RealGrid-UnitInterval";

	private static ExampleManifest manifest;

	@BeforeClass
	public static void loadRealManifest() throws Exception {
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;
		try (FileReader r = new FileReader(new File("src/main/resources/manifest.json"))) {
			manifest = new Gson().fromJson(r, ExampleManifest.class);
		}
	}

	@Test
	public void bothSupersessionCasesAreRecordedInTheCorpusAsSatisfiable() {
		for (String id : List.of(INTEGER_CASE, REAL_CASE)) {
			ExampleEntry ex = entry(id);
			assertEquals(id + ": Study B rows carry the corpus-wide bitwidth", 8, ex.bitwidth);
			assertNotNull(id + ": missing expected oracle", ex.expected);
			assertEquals(id + ": ground truth is a satisfying instance", "sat", ex.expected.classification);
			assertEquals(id + ": ground truth is a satisfying instance", "SATISFIABLE", ex.expected.outcome);
		}
	}

	/**
	 * The incumbent refutes a model every value in the configured domain satisfies. Its integer
	 * literal {@code 100000000} does not fit the bitwidth of 8 the whole corpus runs at, and nothing
	 * in its configuration checking looks at literals INSIDE an invariant -- only at the configured
	 * Integer min/max, which here are 1 and 100 and fit comfortably. So no warning is produced at
	 * all.
	 */
	@Test
	public void kodkodRefutesTheIntegerModelEveryCandidateValueSatisfies() throws Exception {
		ExampleEntry ex = entry(INTEGER_CASE);
		assertEquals(Solution.Outcome.UNSATISFIABLE, kodkodOutcome(ex, false));
	}

	@Test
	public void smtFindsTheIntegerWitnessAndUseConfirmsIt() throws Exception {
		ExampleEntry ex = entry(INTEGER_CASE);
		ModelFinderResult result = smtResult(ex);
		assertTrue(INTEGER_CASE + ": expected SAT", result.satisfiable());
		assertTrue(INTEGER_CASE + ": USE must confirm the witness", result.allActiveInvariantsHold());
		assertVerdictsAllTrue(result);

		MModel model = compile(ex);
		MSystemState state = result.system().state();
		MObject transfer = only(state.objectsOfClass(model.getClass("Transfer")), "Transfer");
		int amount = ((org.tzi.use.uml.ocl.value.IntegerValue) transfer.state(state)
				.attributeValue("amountCents")).value();
		// Ground truth, restated in arithmetic that depends on neither backend.
		assertTrue("amountCents must be strictly positive, was " + amount, amount > 0);
		assertTrue("amountCents must be under the cap, was " + amount, amount < 100000000);
	}

	/**
	 * The incumbent refutes a model satisfied by three values inside ITS OWN default real range. It
	 * enumerates reals on a fixed 0.5 grid rather than solving over them, and the open interval
	 * (0, 1) contains exactly one grid point, so it has no way to place three strictly increasing
	 * values there. Independently of that, its expression transformer has no support for real values
	 * at all, which is why the refutation lands as TRIVIALLY_UNSATISFIABLE -- constant-false at
	 * translation time -- rather than after a search.
	 */
	@Test
	public void kodkodRefutesTheRealModelItsOwnGridCannotWitness() throws Exception {
		ExampleEntry ex = entry(REAL_CASE);
		assertEquals(Solution.Outcome.TRIVIALLY_UNSATISFIABLE, kodkodOutcome(ex, false));
	}

	@Test
	public void smtFindsTheOffGridRealWitnessAndUseConfirmsIt() throws Exception {
		ExampleEntry ex = entry(REAL_CASE);
		ModelFinderResult result = smtResult(ex);
		assertTrue(REAL_CASE + ": expected SAT", result.satisfiable());
		assertTrue(REAL_CASE + ": USE must confirm the witness", result.allActiveInvariantsHold());
		assertVerdictsAllTrue(result);

		MModel model = compile(ex);
		MSystemState state = result.system().state();
		MObject calibration = only(state.objectsOfClass(model.getClass("Calibration")), "Calibration");
		double low = real(calibration, state, "lowCut");
		double mid = real(calibration, state, "midCut");
		double high = real(calibration, state, "highCut");

		// Ground truth, restated in arithmetic that depends on neither backend.
		assertTrue("0 < lowCut, was " + low, low > 0.0);
		assertTrue("lowCut < midCut, was " + low + " / " + mid, low < mid);
		assertTrue("midCut < highCut, was " + mid + " / " + high, mid < high);
		assertTrue("highCut < 1, was " + high, high < 1.0);

		// Inside the incumbent's own default real range [-2, 2] ...
		assertTrue("witness must sit inside the incumbent's own configured range", low >= -2.0 && high <= 2.0);
		// ... but necessarily between its grid points: (0, 1) holds exactly one multiple of 0.5 and
		// three distinct values are required, so at least two must be off that grid.
		int offGrid = (offHalfGrid(low) ? 1 : 0) + (offHalfGrid(mid) ? 1 : 0) + (offHalfGrid(high) ? 1 : 0);
		assertTrue("at least two cuts must be off the incumbent's 0.5 grid, off-grid count = " + offGrid,
				offGrid >= 2);
	}

	@Test
	public void kodkodOutcomeIsUnchangedWhenTheKeysItMisreadsAreRemoved() throws Exception {
		assertEquals("stripping the per-attribute domain keys must not change the integer refutation",
				kodkodOutcome(entry(INTEGER_CASE), false), kodkodOutcome(entry(INTEGER_CASE), true));
		assertEquals("stripping the per-attribute domain keys must not change the real refutation",
				kodkodOutcome(entry(REAL_CASE), false), kodkodOutcome(entry(REAL_CASE), true));
	}

	private static void assertVerdictsAllTrue(ModelFinderResult result) {
		assertFalse("no verdicts were taken at all", result.verdicts().isEmpty());
		for (InvariantVerdict verdict : result.verdicts()) {
			assertTrue(verdict.invariantName() + " must be re-evaluated TRUE, was " + verdict.outcome(),
					verdict.holds());
		}
	}

	private static ExampleEntry entry(String id) {
		for (ExampleEntry ex : manifest.examples) {
			if (id.equals(ex.id)) {
				return ex;
			}
		}
		throw new AssertionError("manifest has no Study B supersession row with id " + id);
	}

	private static Solution.Outcome kodkodOutcome(ExampleEntry ex, boolean stripPerAttributeDomainKeys)
			throws Exception {
		Session session = new Session();
		MModel mModel = compile(ex);
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);

		invalidatePluginModelFactoryCache();
		IModel kodkodModel = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader(propertiesFile(ex))) {
			ini.read(reader);
		}
		String section = ex.section != null ? ex.section
				: (ini.getSections().isEmpty() ? null : ini.getSections().iterator().next());
		Configuration config = ini.getSection(section);
		if (stripPerAttributeDomainKeys) {
			List<String> doomed = new ArrayList<>();
			for (Iterator<String> keys = config.getKeys(); keys.hasNext();) {
				String key = keys.next();
				if ((key.endsWith("_min") || key.endsWith("_max")) && key.chars().filter(c -> c == '_').count() >= 2) {
					doomed.add(key);
				}
			}
			assertFalse("nothing was stripped -- this guard would be vacuous", doomed.isEmpty());
			doomed.forEach(config::clearProperty);
		}

		StringWriter warnings = new StringWriter();
		PrintWriter warningsOut = new PrintWriter(warnings);
		PropertyConfigurationVisitor visitor = new PropertyConfigurationVisitor(config, warningsOut);
		kodkodModel.accept(visitor);
		warningsOut.flush();
		assertFalse(ex.id + ": the incumbent must ACCEPT this configuration, so the divergence cannot be "
				+ "dismissed as a rejected configuration. Warnings were: " + warnings, visitor.containErrors());

		int previousBitwidth = KodkodModelValidatorConfiguration.getInstance().bitwidth();
		try {
			KodkodModelValidatorConfiguration.getInstance().setBitwidth(ex.bitwidth);
			UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
			validator.validate(kodkodModel);
			assertNotNull(ex.id + ": the incumbent produced no solution at all", validator.solution());
			return validator.solution().outcome();
		} finally {
			KodkodModelValidatorConfiguration.getInstance().setBitwidth(previousBitwidth);
			invalidatePluginModelFactoryCache();
		}
	}

	private static ModelFinderResult smtResult(ExampleEntry ex) throws Exception {
		MModel model = compile(ex);
		ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
		RawConfiguration raw = ConfigurationReader.read(propertiesFile(ex).toPath(), ex.section);
		AnalysisConfiguration config = ConfigurationReader.normalize(raw, vocabulary).requireSupported();
		return SmtModelFinder.find(model, config);
	}

	private static boolean offHalfGrid(double value) {
		return Math.abs(value * 2.0 - Math.rint(value * 2.0)) > 1.0e-9;
	}

	private static double real(MObject object, MSystemState state, String attribute) {
		Object value = object.state(state).attributeValue(attribute);
		assertTrue(attribute + " must reconstruct as a USE RealValue, was " + value.getClass().getName(),
				value instanceof RealValue);
		return ((RealValue) value).value();
	}

	private static MObject only(java.util.Set<MObject> objects, String className) {
		assertEquals("expected exactly one " + className + " in the reconstructed snapshot", 1, objects.size());
		return objects.iterator().next();
	}

	private static File exampleDirectory(ExampleEntry ex) {
		return new File(new File("examples"), ex.directory);
	}

	private static File propertiesFile(ExampleEntry ex) {
		return new File(exampleDirectory(ex), ex.propertiesFile);
	}

	private static MModel compile(ExampleEntry ex) throws Exception {
		File useFile = new File(exampleDirectory(ex), ex.useFile);
		Path path = useFile.toPath();
		PrintWriter err = new PrintWriter(System.err);
		MModel model = USECompiler.compileSpecification(java.nio.file.Files.readString(path), useFile.getName(), err,
				new ModelFactory());
		err.flush();
		assertNotNull(ex.id + ": model did not compile: " + useFile, model);
		return model;
	}

	private static void invalidatePluginModelFactoryCache() throws Exception {
		Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
		reTransform.setAccessible(true);
		reTransform.set(PluginModelFactory.INSTANCE, true);
	}
}
