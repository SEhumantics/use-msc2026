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
 * Regression-protects {@code examples/07-Inheritance}: a class hierarchy (generalization) with a
 * superclass-level invariant and subclass-specific invariants -- a structural OCL/UML feature the
 * {@code transform/ocl} unit suite never touches, and which had no example anywhere in this plugin
 * before this one (see {@code examples/README.md}).
 */
public class InheritanceEndToEndValidationTest {

	private static MModel mModel;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File file = new File("src/test/resources/inheritance/Vehicle.use");
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, "Vehicle.use", new PrintWriter(System.err),
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
	public void solvesAndReconstructsThePolymorphicVehicleHierarchy() throws Exception {
		Session session = new Session();
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader("src/test/resources/inheritance/Vehicle.properties")) {
			ini.read(reader);
		}
		String section = ini.getSections().isEmpty() ? null : ini.getSections().iterator().next();
		Configuration config = ini.getSection(section);

		PrintWriter warnings = new PrintWriter(System.err, true);
		PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config, warnings);
		model.accept(configVisitor);
		assertFalse("model configuration reported errors", configVisitor.containErrors());

		new UseKodkodModelValidator(session).validate(model);

		MSystemState state = mSystem.state();

		// Vehicle is abstract (Vehicle_min/max = 0): no direct Vehicle instances, exactly one Car and
		// one Truck. Confirms the abstract superclass itself never gets a phantom instance while its
		// concrete subclasses do.
		assertEquals("direct Vehicle instance count", 0, state.objectsOfClass(mModel.getClass("Vehicle")).size());
		assertEquals("Car count", 1, state.objectsOfClass(mModel.getClass("Car")).size());
		assertEquals("Truck count", 1, state.objectsOfClass(mModel.getClass("Truck")).size());

		// Polymorphic collection: objectsOfClass(Vehicle, true) includes subclass instances -- this is
		// exactly the USE-core mechanism the plugin's own reconstructed state must interoperate with
		// correctly for oclIsTypeOf/oclAsType/superclass-collection navigation to work at all.
		assertEquals("polymorphic Vehicle.allInstances() count", 2,
				state.objectsOfClassAndSubClasses(mModel.getClass("Vehicle")).size());

		// A superclass-level invariant (Vehicle::PositiveWheels) and two subclass-specific invariants
		// (Car::ReasonableDoors, Truck::PositivePayload) all re-checked against the reconstructed state.
		int checked = 0;
		for (MClassInvariant inv : mModel.classInvariants(true)) {
			EvalContext ctx = new EvalContext(state, state, mSystem.varBindings(), null, "");
			Value result = inv.expandedExpression().eval(ctx);
			assertTrue(inv.qualifiedName() + " must evaluate to a defined Boolean", result instanceof BooleanValue);
			assertTrue(inv.qualifiedName() + " must hold in the reconstructed solution",
					((BooleanValue) result).isTrue());
			checked++;
		}
		assertEquals("expected all 3 Vehicle-hierarchy invariants to be checked", 3, checked);
	}
}
