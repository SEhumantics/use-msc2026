package org.tzi.use.kodkod;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.PrintWriter;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.junit.Test;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.config.Options;
import org.tzi.use.config.Options.WarningType;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;

/**
 * Regression test for the exact bug documented in docs/experiments/rq4-cost/raw-results-repeats5.json:
 * 10 rows (RealOps/RealOps-UNSAT x 5 Kodkod solvers) all have {@code outcome=ERROR, error=None}
 * because {@link KodkodModelValidator#validate} used to catch any exception {@link KodkodSolver#solve}
 * threw, log it (unreliably, per this multi-plugin reactor's log4j config -- see
 * {@link KodkodModelValidator#solution()}'s own javadoc), and simply return, with no channel for a
 * caller to recover what actually went wrong. RealOps.use/.properties (copied verbatim from
 * msc-modelvalidators/benchmark/examples/RealOps, the exact fixture behind those 10 rows) is a real,
 * reproducible trigger: Kodkod's bounds construction has no atom for the non-integer Real value 3.5 in
 * its universe, so {@code KodkodSolver.solve} throws {@code IllegalArgumentException: No such atom in
 * the universe: Real_3.5} -- confirmed by calling {@code KodkodSolver.solve} directly, unmediated by
 * {@code validate}'s own catch block, before this fix existed.
 */
public class KodkodModelValidatorErrorReportingTest {

	private static void invalidatePluginModelFactoryCache() throws Exception {
		java.lang.reflect.Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
		reTransform.setAccessible(true);
		reTransform.set(PluginModelFactory.INSTANCE, true);
	}

	private static IModel loadRealOpsModel() throws Exception {
		MModel mModel;
		File file = new File("src/test/resources/realops/RealOps.use");
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, file.getName(), new PrintWriter(System.err),
					new ModelFactory());
		}
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader("src/test/resources/realops/RealOps.properties")) {
			ini.read(reader);
		}
		Configuration config = ini.getSection("main");

		PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config,
				new PrintWriter(System.err, true));
		model.accept(configVisitor);
		assertTrue("RealOps.properties [main] must configure cleanly (this test is about the SOLVE-time"
				+ " exception, not a configuration error)", !configVisitor.containErrors());
		return model;
	}

	/**
	 * MAIN CASE: before this fix, {@code validate} swallowed the "No such atom in the universe:
	 * Real_3.5" exception entirely -- {@code solution()} stayed null and nothing else recorded it. This
	 * asserts the new channel actually carries the real message through.
	 */
	@Test
	public void solveExceptionMessageSurvivesValidate() throws Exception {
		IModel model = loadRealOpsModel();
		// validate() never reaches ObjectDiagramCreator on this failing path (it throws inside
		// createBounds, well before a Solution exists), so the Session needs no MSystem wired up.
		UseKodkodModelValidator validator = new UseKodkodModelValidator(new Session());
		validator.validate(model);

		assertNull("RealOps must still fail to solve (this is a real, uncharacterized Kodkod Real-domain"
				+ " limitation, not something this fix changes)", validator.solution());
		assertNotNull("the caught solve() exception must now be recoverable via validationError()",
				validator.validationError());
		assertTrue("expected the real IllegalArgumentException message to survive, not be replaced or"
				+ " swallowed; got: " + validator.validationError().getMessage(),
				validator.validationError().getMessage() != null
						&& validator.validationError().getMessage().contains("No such atom in the universe"));
		assertTrue("expected the exact class of the underlying exception to survive too",
				validator.validationError() instanceof IllegalArgumentException);
	}

	/**
	 * validationError() must reset on every validate() call, mirroring solution()'s own per-call
	 * semantics -- otherwise a stale error from an earlier failed validate() on the same validator
	 * instance could wrongly appear to explain a LATER, successful call.
	 */
	@Test
	public void validationErrorIsNullAfterASuccessfulValidate() throws Exception {
		File file = new File("src/test/resources/library/Library.use");
		MModel mModel;
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, "Library.use", new PrintWriter(System.err),
					new ModelFactory());
		}
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader("src/test/resources/library/Library.properties")) {
			ini.read(reader);
		}
		String section = ini.getSections().isEmpty() ? null : ini.getSections().iterator().next();
		Configuration config = ini.getSection(section);
		PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config,
				new PrintWriter(System.err, true));
		model.accept(configVisitor);
		assertTrue(!configVisitor.containErrors());

		Session session = new Session();
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);
		UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
		validator.validate(model);

		assertNotNull("Library.use is a known-solvable fixture (see EndToEndValidationTest)",
				validator.solution());
		assertNull("a successful validate() must not leave a stale validationError()",
				validator.validationError());

		invalidatePluginModelFactoryCache();
	}
}
