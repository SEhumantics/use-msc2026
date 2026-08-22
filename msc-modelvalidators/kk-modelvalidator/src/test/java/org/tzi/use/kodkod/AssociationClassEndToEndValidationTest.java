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
 * Regression-protects {@code examples/06-AssociationClass}, exercising a real, previously-untested
 * plugin feature: {@code IAssociationClass}/{@code AssociationClass} in the plugin's own source had no
 * coverage at any level -- not one example, not one unit test -- anywhere in this plugin's history
 * before this example was built and this test automated it (see {@code examples/README.md}).
 */
public class AssociationClassEndToEndValidationTest {

	private static MModel mModel;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File file = new File("src/test/resources/associationclass/CompanyEmployment.use");
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, "CompanyEmployment.use",
					new PrintWriter(System.err), new ModelFactory());
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
	public void solvesAndReconstructsAssociationClassLinks() throws Exception {
		Session session = new Session();
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader("src/test/resources/associationclass/CompanyEmployment.properties")) {
			ini.read(reader);
		}
		String section = ini.getSections().iterator().next();
		Configuration config = ini.getSection(section);

		PrintWriter warnings = new PrintWriter(System.err, true);
		PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config, warnings);
		model.accept(configVisitor);
		assertFalse("model configuration reported errors", configVisitor.containErrors());

		new UseKodkodModelValidator(session).validate(model);

		MSystemState state = mSystem.state();

		// The [small] section pins 2 Person, 2 Company, 2 Employment -- an association-class instance
		// must be reconstructed as BOTH a class instance (counted here) and an association link
		// (checked via the association-link count below) simultaneously.
		assertEquals("Person count", 2, state.objectsOfClass(mModel.getClass("Person")).size());
		assertEquals("Company count", 2, state.objectsOfClass(mModel.getClass("Company")).size());
		assertEquals("Employment (association class) instance count", 2,
				state.objectsOfClass(mModel.getClass("Employment")).size());
		assertEquals("Employment association link count", 2,
				state.linksOfAssociation(mModel.getAssociation("Employment")).size());

		int checked = 0;
		for (MClassInvariant inv : mModel.classInvariants(true)) {
			EvalContext ctx = new EvalContext(state, state, mSystem.varBindings(), null, "");
			Value result = inv.expandedExpression().eval(ctx);
			assertTrue(inv.qualifiedName() + " must evaluate to a defined Boolean", result instanceof BooleanValue);
			assertTrue(inv.qualifiedName() + " must hold in the reconstructed solution",
					((BooleanValue) result).isTrue());
			checked++;
		}
		assertEquals("expected all 5 CompanyEmployment invariants to be checked", 5, checked);
	}
}
