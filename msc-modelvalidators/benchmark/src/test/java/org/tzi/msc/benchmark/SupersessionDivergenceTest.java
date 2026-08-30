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
import org.tzi.kodkod.model.iface.IClass;
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
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.URealValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MLink;
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
	private static final String UREAL_BELOW_CASE = "URealThreshold-Below";
	private static final String UREAL_ERASURE_CASE = "URealThreshold-NominalErasure";
	private static final String SELF_CYCLE_CASE = "AggregationComposition-SelfCycle";
	private static final String TRANSLATION_GAP_CASE = "Redefines-TranslationGap";
	private static final List<String> FALSE_ACCEPT_CASES = List.of(UREAL_BELOW_CASE, UREAL_ERASURE_CASE);

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
		assertObservedKodkodOutcome(entry(INTEGER_CASE), Solution.Outcome.UNSATISFIABLE);
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
		assertObservedKodkodOutcome(entry(REAL_CASE), Solution.Outcome.TRIVIALLY_UNSATISFIABLE);
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

	// ---------------------------------------------------------------- the false ACCEPTS

	/**
	 * The other divergence DIRECTION, and the dangerous one. The two cases above are false
	 * REJECTIONS: the incumbent refutes a model that has a witness, which a user notices, because the
	 * tool visibly declines to produce a snapshot. The two rows below are false ACCEPTANCES: the
	 * incumbent reports a satisfying instance for a model that has none, which a user does NOT
	 * notice, because a verification tool saying "fine" is exactly what a correct model looks like.
	 * Everything here is checked the same three ways as the false rejections: the incumbent is run on
	 * the SAME unmodified corpus files, its mechanism is inspected rather than inferred, and ground
	 * truth is established by the USE evaluator alone -- neither model finder is consulted for it.
	 */
	@Test
	public void bothFalseAcceptCasesAreRecordedInTheCorpusAsUnsatisfiable() {
		for (String id : FALSE_ACCEPT_CASES) {
			ExampleEntry ex = entry(id);
			assertEquals(id + ": Study B rows carry the corpus-wide bitwidth", 8, ex.bitwidth);
			assertNotNull(id + ": missing expected oracle", ex.expected);
			assertEquals(id + ": ground truth is a refutation", "unsat", ex.expected.classification);
			assertEquals(id + ": ground truth is a refutation", "UNSATISFIABLE", ex.expected.outcome);
			assertNotNull(id + ": a Study B row must carry its supersession columns", ex.supersession);
			assertEquals(id + ": this is the wrong-acceptance direction", "false-sat",
					ex.supersession.divergenceClass);
		}
	}

	@Test
	public void kodkodAcceptsTheURealModelWhoseOnlyCandidateFailsTheInvariant() throws Exception {
		assertObservedKodkodOutcome(entry(UREAL_BELOW_CASE), Solution.Outcome.TRIVIALLY_SATISFIABLE);
	}

	@Test
	public void kodkodAcceptsTheNominalErasureModelItsOwnConfigurationCannotExpress() throws Exception {
		assertObservedKodkodOutcome(entry(UREAL_ERASURE_CASE), Solution.Outcome.TRIVIALLY_SATISFIABLE);
	}

	/**
	 * The MECHANISM behind both acceptances, inspected rather than inferred. {@code
	 * TypeConverter.convert} has no {@code UReal} arm, so it falls through to {@code LOG.error} and
	 * returns null; the attribute is therefore never created on the incumbent's model, the invariant
	 * that reads it fails to transform with "Cannot find attribute speed", {@code
	 * InvariantTransformator.transformAndAdd} CATCHES that and only logs it, and what reaches the
	 * solver is a class with no attributes and no invariants -- constant true. This asserts the end
	 * state directly: nothing survives to be searched.
	 */
	@Test
	public void theURealAttributeAndItsInvariantAreBothGoneBeforeAnySearch() throws Exception {
		IModel transformed = transformedKodkodModel(entry(UREAL_BELOW_CASE));

		IClass unidentified = null;
		for (IClass c : transformed.classes()) {
			if ("UnidentifiedObject".equals(c.name())) {
				unidentified = c;
			}
		}
		assertNotNull("the class itself does survive; only what constrains it does not", unidentified);
		assertTrue("the UReal attribute must be absent -- TypeConverter returned null for it, was "
				+ unidentified.attributes(), unidentified.attributes().isEmpty());
		assertTrue("the invariant reading it must have been dropped, was " + unidentified.invariants(),
				unidentified.invariants().isEmpty());
	}

	/**
	 * And it is dropped SILENTLY as far as anything a caller can see: the incumbent's configuration
	 * check reports neither an error nor a single warning, so nothing in the corpus record
	 * distinguishes this run from one where the invariant was honoured. (It does write a log4j ERROR
	 * line; that never reaches the outcome, the object diagram, or the benchmark's own results file,
	 * whose {@code error} column is null for these rows.)
	 */
	@Test
	public void theIncumbentEmitsNoErrorAndNoWarningWhileDroppingTheInvariant() throws Exception {
		for (String id : FALSE_ACCEPT_CASES) {
			String warnings = configurationWarnings(entry(id));
			assertTrue(id + ": the incumbent must ACCEPT this configuration", warnings != null);
			assertTrue(id + ": dropping the invariant produced a warning, so it is not silent after all: "
					+ warnings, warnings.isBlank());
		}
	}

	/**
	 * Ground truth, established by the USE evaluator alone. Both configurations pin the object count
	 * to exactly one and the attribute to exactly ONE candidate value, so the entire search space is
	 * a single snapshot; USE evaluates the invariant FALSE on it; therefore no satisfying instance
	 * exists and UNSATISFIABLE is the correct answer. No model finder is involved in this argument.
	 */
	// ------------------------------------------------------------------
	// The 2026-08-30 deliberate Study B extension: two mechanism-distinct
	// rows, each re-pinned here empirically against the real incumbent.
	// ------------------------------------------------------------------

	/**
	 * {@code AggregationComposition-SelfCycle}: with {@code aggregationcyclefreeness = off}, a
	 * 2-tuple cycle forced through the SINGLE PrimaryContains composition is still refused by the
	 * incumbent -- its single-association composition acyclicity is an UNCONDITIONAL constraint
	 * that ignores the toggle (the corpus model's own header documents this; this assertion
	 * re-runs the incumbent on the same files so the recorded manifest outcome cannot rot).
	 */
	@Test
	public void kodkodRefutesTheSelfCycleEvenWithTheToggleOff() throws Exception {
		assertObservedKodkodOutcome(entry(SELF_CYCLE_CASE), Solution.Outcome.TRIVIALLY_UNSATISFIABLE);
	}

	/**
	 * The toggle-honoring backend accepts the forced cycle, and the reconstructed snapshot
	 * genuinely contains it -- USE's own checkStructure() cycle warning firing on reconstruction
	 * is exactly what the OFF toggle tells the plugin not to treat as an error.
	 */
	@Test
	public void smtHonorsTheToggleAndReconstructsTheForcedCycle() throws Exception {
		ExampleEntry ex = entry(SELF_CYCLE_CASE);
		ModelFinderResult result = smtResult(ex);
		assertTrue(SELF_CYCLE_CASE + ": expected SAT", result.satisfiable());
		MModel model = compile(ex);
		MSystemState state = result.system().state();
		int primaryContainsLinks = 0;
		for (MLink link : state.allLinks()) {
			if (link.association().name().equals("PrimaryContains")) {
				primaryContainsLinks++;
				assertEquals("a PrimaryContains link joins two Folder slots", 2,
						link.linkEnds().size());
			}
		}
		assertEquals("the forced 2-tuple self-cycle must reconstruct as two links", 2,
				primaryContainsLinks);
	}

	/**
	 * {@code Redefines-TranslationGap}: the incumbent's translator has no redefines handling, so
	 * {@code c.b} is encoded through AB's relation (empty for a CD-only-linked C), the forAll is
	 * vacuously true, and the incumbent ACCEPTS the configuration whose own semantics violate the
	 * invariant. The recorded manifest outcome is re-pinned against the real incumbent here.
	 */
	@Test
	public void kodkodAcceptsTheTranslationGapItsMissingRedefinesHandlingMakesVacuous() throws Exception {
		assertObservedKodkodOutcome(entry(TRANSLATION_GAP_CASE), Solution.Outcome.SATISFIABLE);
	}

	/** The redefines-aware backend navigates CD, sees tagD = 'NOT-child-d', and refutes. */
	@Test
	public void smtRefutesTheTranslationGapConfiguration() throws Exception {
		ExampleEntry ex = entry(TRANSLATION_GAP_CASE);
		ModelFinderResult result = smtResult(ex);
		assertFalse(TRANSLATION_GAP_CASE + ": expected UNSAT -- C::b redefines A::b to navigate"
				+ " CD, so tagD = 'NOT-child-d' violates the forAll", result.satisfiable());
	}

	@Test
	public void useEvaluatorRefutesTheSingleCandidateEachConfigurationAllows() throws Exception {
		for (String id : FALSE_ACCEPT_CASES) {
			ExampleEntry ex = entry(id);
			Configuration config = section(ex);
			assertEquals(id + ": the search space must be exactly one object", "1",
					config.getString("UnidentifiedObject_min"));
			assertEquals(id + ": the search space must be exactly one object", "1",
					config.getString("UnidentifiedObject_max"));
			List<Double> values = singletonSet(config.getString("UnidentifiedObject_speed_value"));
			List<Double> uncertainties = singletonSet(config.getString("UnidentifiedObject_speed_uncertainty"));
			assertEquals(id + ": exactly one candidate value, or the argument below does not close", 1,
					values.size());
			assertEquals(id + ": exactly one candidate uncertainty", 1, uncertainties.size());

			MModel model = compile(ex);
			MClass cls = model.getClass("UnidentifiedObject");
			MAttribute speed = cls.attribute("speed", true);
			MSystemState state = new MSystem(model).state();
			state.createObject(cls, "o1").state(state)
					.setAttributeValue(speed, new URealValue(values.get(0), uncertainties.get(0)));

			MClassInvariant invariant = model.classInvariants().iterator().next();
			Value verdict = new Evaluator().eval(invariant.expandedExpression(), state);
			assertTrue(id + ": the invariant must evaluate to a definite Boolean, was " + verdict,
					verdict instanceof BooleanValue);
			assertFalse(id + ": USE must REFUTE the only candidate, or the corpus's UNSAT oracle is wrong "
					+ "and this whole row must be withdrawn", ((BooleanValue) verdict).value());
		}
	}

	@Test
	public void smtRefutesBothFalseAcceptCases() throws Exception {
		for (String id : FALSE_ACCEPT_CASES) {
			ModelFinderResult result = smtResult(entry(id));
			assertFalse(id + ": expected UNSAT", result.satisfiable());
		}
	}

	@Test
	public void kodkodOutcomeIsUnchangedWhenTheKeysItMisreadsAreRemoved() throws Exception {
		assertEquals("stripping the per-attribute domain keys must not change the integer refutation",
				kodkodOutcome(entry(INTEGER_CASE), false), kodkodOutcome(entry(INTEGER_CASE), true));
		assertEquals("stripping the per-attribute domain keys must not change the real refutation",
				kodkodOutcome(entry(REAL_CASE), false), kodkodOutcome(entry(REAL_CASE), true));
	}

	/**
	 * Pins the incumbent's outcome twice over. Once against the literal written here, so a reader of
	 * this test sees the claim without opening the manifest; and once against the row's OWN recorded
	 * {@code supersession.kodkodOutcome} column, so that column is executable rather than decorative.
	 * Adversarial check that motivated the second assertion: mislabelling the real case's column as
	 * SATISFIABLE was caught by ManifestSchemaTest's internal-consistency rule but NOT by this class,
	 * which is exactly the gap a Study B table generated from those columns would inherit.
	 */
	private static void assertObservedKodkodOutcome(ExampleEntry ex, Solution.Outcome documented) throws Exception {
		Solution.Outcome observed = kodkodOutcome(ex, false);
		assertEquals(ex.id + ": incumbent outcome", documented, observed);
		assertNotNull(ex.id + ": a Study B row must carry its supersession columns", ex.supersession);
		assertEquals(ex.id + ": the manifest's recorded kodkodOutcome must be what the incumbent ACTUALLY does",
				observed.name(), ex.supersession.kodkodOutcome);
	}

	private static void assertVerdictsAllTrue(ModelFinderResult result) {
		assertFalse("no verdicts were taken at all", result.verdicts().isEmpty());
		for (InvariantVerdict verdict : result.verdicts()) {
			assertTrue(verdict.invariantName() + " must be re-evaluated TRUE, was " + verdict.outcome(),
					verdict.holds());
		}
	}

	/** The incumbent's model AFTER transformation -- where a dropped invariant is visible as absence. */
	private static IModel transformedKodkodModel(ExampleEntry ex) throws Exception {
		invalidatePluginModelFactoryCache();
		return PluginModelFactory.INSTANCE.getModel(compile(ex));
	}

	/** Everything the incumbent's configuration check wrote for this row; "" when it wrote nothing. */
	private static String configurationWarnings(ExampleEntry ex) throws Exception {
		IModel kodkodModel = transformedKodkodModel(ex);
		StringWriter warnings = new StringWriter();
		PrintWriter warningsOut = new PrintWriter(warnings);
		PropertyConfigurationVisitor visitor = new PropertyConfigurationVisitor(section(ex), warningsOut);
		kodkodModel.accept(visitor);
		warningsOut.flush();
		assertFalse(ex.id + ": the incumbent must ACCEPT this configuration. Warnings were: " + warnings,
				visitor.containErrors());
		invalidatePluginModelFactoryCache();
		return warnings.toString();
	}

	private static Configuration section(ExampleEntry ex) throws Exception {
		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader(propertiesFile(ex))) {
			ini.read(reader);
		}
		String section = ex.section != null ? ex.section
				: (ini.getSections().isEmpty() ? null : ini.getSections().iterator().next());
		return ini.getSection(section);
	}

	/** Parses a {@code Set{...}} configuration literal into its numbers, in order. */
	private static List<Double> singletonSet(String literal) {
		assertNotNull("missing configuration value", literal);
		String inner = literal.trim().replaceFirst("^Set\\s*\\{", "").replaceFirst("\\}$", "");
		List<Double> values = new ArrayList<>();
		for (String part : inner.split(",")) {
			if (!part.isBlank()) {
				values.add(Double.parseDouble(part.trim()));
			}
		}
		return values;
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
