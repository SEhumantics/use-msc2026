package org.tzi.use.kodkod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

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
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Exercises the full pipeline the unit-level `transform/ocl` suite never touches: translate a real
 * multi-class, multi-invariant model to Kodkod, solve it, and reconstruct the solution as live USE
 * objects. The `transform/ocl` tests only check that individual OCL expressions translate to the
 * expected Kodkod formula text; none of them call {@link KodkodModelValidator#validate} or inspect
 * a resulting {@link MSystem}.
 *
 * <p>The fixture is the plugin's own original example, {@code test2/t002.use} /
 * {@code t002.properties} from the pinned reference clone (classic Library/User/Copy/Book model:
 * associations, multiplicities, key-uniqueness invariants, format-check invariants, and a
 * no-double-borrowing invariant) — not a new toy model. Manually confirmed once via a live
 * {@code mv -validate} shell session (see docs/kk-modelvalidator-port.md); this test makes that
 * check automated and repeatable rather than a one-off transcript.
 */
public class EndToEndValidationTest {

	private static MModel mModel;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File file = new File("src/test/resources/library/Library.use");
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, "Library.use", new PrintWriter(System.err),
					new ModelFactory());
		}
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		invalidatePluginModelFactoryCache();
	}

	/**
	 * {@code PluginModelFactory.INSTANCE} (an enum singleton) caches a single transformed
	 * {@link IModel} behind a private {@code reTransform} gate that only clears via a Session/
	 * EventBus wiring this test has no other reason to set up. Every other test class in this
	 * module shares one fixture ({@code testModel.use}), so a stale cache is invisible to them; this
	 * test loads a distinct model (the Library example), so without resetting the gate, whichever
	 * test class happens to run adjacent to this one in the same JVM fork would silently transform
	 * against the wrong model. Reset via reflection rather than reaching into the plugin's
	 * production wiring for what is purely a test-isolation concern.
	 */
	private static void invalidatePluginModelFactoryCache() throws Exception {
		java.lang.reflect.Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
		reTransform.setAccessible(true);
		reTransform.set(PluginModelFactory.INSTANCE, true);
	}

	@Test
	public void solvesAndReconstructsTheLibraryExample() throws Exception {
		Session session = new Session();
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		// see ConfigurablePlugin.readConfiguration (main source) for why this is needed: config2 no
		// longer splits comma-delimited "Set{a,b,c}" list values by default like config1 did.
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader("src/test/resources/library/Library.properties")) {
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

		// The candidate value sets in Library.properties are each exactly 3 elements
		// (User_name/Copy_signature/Book_title), with min/max scope 3 for every class, so a
		// satisfying solution must use every candidate exactly once — this is what makes the
		// expected object count and attribute values pinned rather than merely "some solution".
		assertEquals("User count", 3, state.objectsOfClass(mModel.getClass("User")).size());
		assertEquals("Copy count", 3, state.objectsOfClass(mModel.getClass("Copy")).size());
		assertEquals("Book count", 3, state.objectsOfClass(mModel.getClass("Book")).size());

		// StringValue.toString() renders quoted, matching USE's OCL-printed representation
		// (e.g. 'Ada'), the same form seen interactively via `? User.allInstances()->collect(name)`.
		Set<String> userNames = attributeValues(state, mModel.getClass("User"), "name");
		assertEquals(new HashSet<>(java.util.Arrays.asList("'Ada'", "'Bob'", "'Cyd'")), userNames);

		Set<String> bookTitles = attributeValues(state, mModel.getClass("Book"), "title");
		assertEquals(new HashSet<>(java.util.Arrays.asList("'DBforDummies'", "'IntrotoAI'", "'PrincsofNW'")),
				bookTitles);

		// Independent post-validation: re-evaluate every active invariant against the reconstructed
		// state and require it to be defined-true, exactly the same standard the thesis proposal
		// holds the eventual Z3-ModelValidator to (output/robust_utype_model_finding_proposal.md
		// §7, "Query-witness soundness"). A solved-but-uninvariant-checked witness is not evidence.
		for (MClassInvariant inv : mModel.classInvariants(true)) {
			EvalContext ctx = new EvalContext(state, state, mSystem.varBindings(), null, "");
			Value result = inv.expandedExpression().eval(ctx);
			assertTrue(inv.qualifiedName() + " must evaluate to a defined Boolean", result instanceof BooleanValue);
			assertTrue(inv.qualifiedName() + " must hold in the reconstructed solution",
					((BooleanValue) result).isTrue());
		}
	}

	private static Set<String> attributeValues(MSystemState state, org.tzi.use.uml.mm.MClass cls, String attrName) {
		Set<String> values = new HashSet<>();
		for (MObject obj : state.objectsOfClass(cls)) {
			Value v = obj.state(state).attributeValue(attrName);
			values.add(v.toString());
		}
		return values;
	}
}
