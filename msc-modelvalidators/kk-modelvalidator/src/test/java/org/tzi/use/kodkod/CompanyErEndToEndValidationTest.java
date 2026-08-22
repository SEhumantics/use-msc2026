package org.tzi.use.kodkod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.PrintWriter;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.config.Options;
import org.tzi.use.config.Options.WarningType;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Regression-protects {@code examples/03-CompanyERSchema}, previously verified only once, manually,
 * via an interactive {@code mv -validate}/{@code check -v} session (see {@code examples/README.md}).
 * This model exercises derived (FK-string-computed) associations and a 29-invariant PK/FK/business-rule
 * constraint block -- structural OCL/UML features the {@code transform/ocl} unit suite never touches.
 */
public class CompanyErEndToEndValidationTest {

	private static MModel mModel;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File file = new File("src/test/resources/companyer/CompanyER.use");
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, "CompanyER.use", new PrintWriter(System.err),
					new ModelFactory());
		}
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		invalidatePluginModelFactoryCache();
	}

	private static void invalidatePluginModelFactoryCache() throws Exception {
		java.lang.reflect.Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
		reTransform.setAccessible(true);
		reTransform.set(PluginModelFactory.INSTANCE, true);
	}

	@Test
	public void solvesAndReconstructsTheCompanyErSmallSection() throws Exception {
		Session session = new Session();
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader("src/test/resources/companyer/CompanyER.properties")) {
			ini.read(reader);
		}
		// CompanyER.properties declares [small] before [six]; the plugin's own -validate resolves to
		// the first section when none is named on the command line, so pick the same one here.
		String section = ini.getSections().iterator().next();
		Configuration config = ini.getSection(section);

		PrintWriter warnings = new PrintWriter(System.err, true);
		PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config, warnings);
		model.accept(configVisitor);
		assertFalse("model configuration reported errors", configVisitor.containErrors());

		new UseKodkodModelValidator(session).validate(model);

		MSystemState state = mSystem.state();

		// [small] pins every class to exactly 2 objects except Part (3, deliberately one more than
		// Component, per CompanyER.properties' own header comment about avoiding a forced self-loop).
		assertEquals("Employee count", 2, state.objectsOfClass(mModel.getClass("Employee")).size());
		assertEquals("Department count", 2, state.objectsOfClass(mModel.getClass("Department")).size());
		assertEquals("Part count", 3, state.objectsOfClass(mModel.getClass("Part")).size());
		assertEquals("Component count", 2, state.objectsOfClass(mModel.getClass("Component")).size());

		// All 29 invariants (primary keys, foreign keys, business rules) re-checked independently
		// against the reconstructed state -- this is the assertion that actually regression-protects
		// the example: a future change that breaks derived-association or PK/FK translation would fail
		// here, not just silently degrade a manually-run example nobody re-checks.
		int checked = 0;
		for (MClassInvariant inv : mModel.classInvariants(true)) {
			EvalContext ctx = new EvalContext(state, state, mSystem.varBindings(), null, "");
			Value result = inv.expandedExpression().eval(ctx);
			assertTrue(inv.qualifiedName() + " must evaluate to a defined Boolean", result instanceof BooleanValue);
			assertTrue(inv.qualifiedName() + " must hold in the reconstructed solution",
					((BooleanValue) result).isTrue());
			checked++;
		}
		assertEquals("expected all 29 CompanyER invariants to be checked", 29, checked);
	}
}
