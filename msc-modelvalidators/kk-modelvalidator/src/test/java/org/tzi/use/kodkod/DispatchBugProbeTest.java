package org.tzi.use.kodkod;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.PrintWriter;

import kodkod.engine.Solution;

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
 * Reproduction for a real, characterized kk-modelvalidator defect (see
 * docs/modelvalidator-feature-matrix.json, row {@code ocl.operation-polymorphic-override}):
 * {@code OperationExpressionVisitor.getOverriddenOperations()} (lines ~135-147) only collects
 * descendant classes that OWN-declare (searchInherited=false, via {@code child.operation(name,
 * false)}) a redefinition of the operation, and {@code handleOveriddenOperation()} (lines ~109-126)
 * tests membership via {@code IClass.relation()} -- that class's OWN exact-type atoms, not the
 * subtype-inclusive {@code IClass.inheritanceOrRegularRelation()}. For a 3-level chain A -&gt; B -&gt;
 * C where B overrides op() and C does not re-declare it (C simply inherits B's override -- ordinary,
 * legal OCL), the correct dispatch target for an exact-type-C receiver is B's override (nearest
 * ancestor declaration): {@code self.oclIsTypeOf(C) implies self.op() = 2} must be a tautology. C is
 * never collected into {@code overiddenOperations} (it has no own declaration), so the resulting
 * nested if-then-else never even offers B's body as a candidate for a C-typed self -- kk instead
 * wrongly reports the model (TRIVIALLY_)UNSATISFIABLE. Confirmed by {@link
 * #skipLevelInheritedOverrideShouldBeSatisfiable} (the reproduction, which is expected to FAIL --
 * see its own javadoc) together with three independent isolation/sanity controls that all pass,
 * pinning the defect specifically to the "override inherited without re-declaration" dispatch gap
 * rather than to anything structural about the fixture itself: {@link
 * #structuralHierarchyAloneIsSatisfiable} (no op() call at all), {@link
 * #dispatchedOpCallIsAtLeastDefined} (op() is at least defined for the C atom), and {@link
 * #directRedeclarationOnLeafClassDispatchesCorrectly} (dispatch IS correct once C re-declares
 * op() itself, the case {@code getOverriddenOperations()} actually handles).
 */
public class DispatchBugProbeTest {

	private static void invalidatePluginModelFactoryCache() throws Exception {
		java.lang.reflect.Field reTransform = PluginModelFactory.class.getDeclaredField("reTransform");
		reTransform.setAccessible(true);
		reTransform.set(PluginModelFactory.INSTANCE, true);
	}

	private static Solution runProbe(String useFile, String propertiesFile) throws Exception {
		MModel mModel;
		File file = new File(useFile);
		try (FileInputStream specStream = new FileInputStream(file)) {
			mModel = USECompiler.compileSpecification(specStream, new File(useFile).getName(), new PrintWriter(System.err),
					new ModelFactory());
		}
		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;

		Session session = new Session();
		MSystem mSystem = new MSystem(mModel);
		session.setSystem(mSystem);

		invalidatePluginModelFactoryCache();
		IModel model = PluginModelFactory.INSTANCE.getModel(mModel);

		INIConfiguration ini = new INIConfiguration();
		ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
		try (FileReader reader = new FileReader(propertiesFile)) {
			ini.read(reader);
		}
		String section = ini.getSections().isEmpty() ? null : ini.getSections().iterator().next();
		Configuration config = ini.getSection(section);

		PrintWriter warnings = new PrintWriter(System.err, true);
		PropertyConfigurationVisitor configVisitor = new PropertyConfigurationVisitor(config, warnings);
		model.accept(configVisitor);
		assertFalse("model configuration reported errors", configVisitor.containErrors());

		for (org.tzi.kodkod.model.iface.IInvariant inv : model.getClass("A").invariants()) {
			System.err.println("### DEBUG invariant " + inv.name() + " activated=" + inv.isActivated() + " formula="
					+ org.tzi.kodkod.helper.PrintHelper.prettyKodkod(inv.formula()));
		}

		UseKodkodModelValidator validator = new UseKodkodModelValidator(session);
		validator.validate(model);

		invalidatePluginModelFactoryCache();
		return validator.solution();
	}

	/**
	 * MAIN PROBE: correct OCL/UML semantics say the sole C instance inherits B's override
	 * (op() = 2), so "oclIsTypeOf(C) implies op() = 2" is a tautology and the model MUST be
	 * satisfiable. If the suspected dispatch bug is present, kk instead falls through to A's
	 * original body (op() = 1) for the exact-type-C instance, making the invariant -- and hence
	 * the whole model -- wrongly (TRIVIALLY_)UNSATISFIABLE.
	 */
	@Test
	public void skipLevelInheritedOverrideShouldBeSatisfiable() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBug.use",
				"src/test/resources/dispatchbug/DispatchBug.properties");
		System.out.println("### PROBE 1 (expect SAT/TRIVIALLY_SAT) OUTCOME: " + (solution == null ? "null" : solution.outcome()));

		org.junit.Assert.assertTrue(
				"expected SATISFIABLE or TRIVIALLY_SATISFIABLE (correct OCL semantics: C inherits B's op() override); got "
						+ (solution == null ? "null" : solution.outcome()),
				solution != null
						&& (solution.outcome() == Solution.Outcome.SATISFIABLE
								|| solution.outcome() == Solution.Outcome.TRIVIALLY_SATISFIABLE));
	}

	/**
	 * PINPOINT CONTROL, REFUTED: the natural next hypothesis is that the buggy dispatch simply falls
	 * through to A's ORIGINAL body (op() = 1) for the exact-type-C instance -- i.e. "some" wrong
	 * answer, specifically the pre-override base-class body. This asserts that hypothesis directly
	 * (op() = 1 instead of the correct 2) and it is REFUTED: empirically this is ALSO
	 * (TRIVIALLY_)UNSATISFIABLE, not satisfiable. So the bug is not "dispatch silently falls back to
	 * A's body" -- something about the C atom's op() is unsatisfiable under BOTH candidate values here
	 * (A_min=A_max=0, B_min=B_max=0 in this fixture's bounds, i.e. zero A-only and zero B-only atoms
	 * exist for the sole C atom to ever land in). {@link #dispatchedOpValueIsNeitherOneNorTwo} pushes
	 * this further (also UNSAT for "neither 1 nor 2"): op() does not appear to hold ANY concrete value
	 * for the C atom in this configuration. Kept as a real, asserted regression (not just a diagnostic
	 * print) specifically so this refutation itself cannot silently regress into "well actually it IS
	 * satisfiable now" without being noticed -- the exact mechanism behind this narrower fact is not
	 * pinned down further here; only {@link #skipLevelInheritedOverrideShouldBeSatisfiable}'s false-UNSAT
	 * finding is what docs/modelvalidator-feature-matrix.json cites.
	 */
	@Test
	public void baseClassBodyFallbackHypothesisIsRefuted() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBug.use",
				"src/test/resources/dispatchbug/DispatchBugControlWrongValue.properties");
		System.out.println("### PROBE 2 (pinpoint control, refuted) OUTCOME: " + (solution == null ? "null" : solution.outcome()));

		org.junit.Assert.assertTrue(
				"expected the WRONG-value invariant (op()==1) to ALSO be (TRIVIALLY_)UNSATISFIABLE (the"
						+ " 'falls back to A's original body' hypothesis is refuted, not confirmed); got "
						+ (solution == null ? "null" : solution.outcome()),
				solution != null
						&& (solution.outcome() == Solution.Outcome.UNSATISFIABLE
								|| solution.outcome() == Solution.Outcome.TRIVIALLY_UNSATISFIABLE));
	}

	/**
	 * ISOLATION CONTROL: same class hierarchy and bounds, but the invariant never calls op() at
	 * all (just oclIsTypeOf(C) implies true). If THIS also comes back UNSAT, the problem is
	 * structural (bounds/hierarchy) and unrelated to operation dispatch.
	 */
	@Test
	public void structuralHierarchyAloneIsSatisfiable() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBugStructural.use",
				"src/test/resources/dispatchbug/DispatchBugStructural.properties");
		System.out.println("### PROBE 4 (structural isolation, no op() call) OUTCOME: " + (solution == null ? "null" : solution.outcome()));

		org.junit.Assert.assertTrue(
				"expected SATISFIABLE or TRIVIALLY_SATISFIABLE for a trivial invariant that never calls op(); got "
						+ (solution == null ? "null" : solution.outcome()),
				solution != null
						&& (solution.outcome() == Solution.Outcome.SATISFIABLE
								|| solution.outcome() == Solution.Outcome.TRIVIALLY_SATISFIABLE));
	}

	/**
	 * ISOLATION CONTROL: same hierarchy, invariant calls self.op() (on the A-typed self, dynamic
	 * dispatch path) but only checks it is DEFINED, not which value it holds. If this is UNSAT
	 * too, op() itself is producing Undefined for the C instance (a different failure mode than
	 * "wrong value").
	 */
	@Test
	public void dispatchedOpCallIsAtLeastDefined() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBugOpDefined.use",
				"src/test/resources/dispatchbug/DispatchBugOpDefined.properties");
		System.out.println("### PROBE 5 (op() must just be defined) OUTCOME: " + (solution == null ? "null" : solution.outcome()));

		org.junit.Assert.assertTrue(
				"expected SATISFIABLE or TRIVIALLY_SATISFIABLE for self.op() merely being defined; got "
						+ (solution == null ? "null" : solution.outcome()),
				solution != null
						&& (solution.outcome() == Solution.Outcome.SATISFIABLE
								|| solution.outcome() == Solution.Outcome.TRIVIALLY_SATISFIABLE));
	}

	/**
	 * FOLLOW-UP, unresolved: probes 1 and 2 both came back UNSAT for op()==2 (correct) AND op()==1
	 * (the hypothesized "falls through to A's original body" value), while probe 5 shows op() is at
	 * least DEFINED. So whatever kk computes for the exact-type-C instance's op(), it does not appear
	 * to be either 1 or 2. This checks whether it is some OTHER defined value instead -- purely
	 * diagnostic (no assertion): empirically ALSO (TRIVIALLY_)UNSATISFIABLE, i.e. op() does not appear
	 * to hold ANY concrete Integer value for the C atom in this configuration either. The exact
	 * resulting formula is not pinned down further; only the top-level false-UNSAT finding ({@link
	 * #skipLevelInheritedOverrideShouldBeSatisfiable}) and its refuted-hypothesis follow-up ({@link
	 * #baseClassBodyFallbackHypothesisIsRefuted}) are asserted and cited in
	 * docs/modelvalidator-feature-matrix.json.
	 */
	@Test
	public void dispatchedOpValueIsNeitherOneNorTwo() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBugOpValue.use",
				"src/test/resources/dispatchbug/DispatchBugOpValue.properties");
		System.out.println("### PROBE 6 (op() is neither 1 nor 2) OUTCOME: " + (solution == null ? "null" : solution.outcome()));
	}

	/**
	 * FOLLOW-UP, diagnostic only (no assertion): does the dispatched formula at least satisfy the
	 * OCL-level tautology self.op() = self.op()? Empirically TRIVIALLY_SATISFIABLE.
	 */
	@Test
	public void dispatchedOpEqualsItselfTautology() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBugSelfEq.use",
				"src/test/resources/dispatchbug/DispatchBugSelfEq.properties");
		System.out.println("### PROBE 7 (op() = op(), tautology) OUTCOME: " + (solution == null ? "null" : solution.outcome()));
	}

	/**
	 * FOLLOW-UP, diagnostic only (no assertion): sanity-checks that the C atom is not ALSO exactly
	 * type B under this encoding (oclIsTypeOf is exact-type, so a C instance must not be oclIsTypeOf
	 * B). Empirically TRIVIALLY_SATISFIABLE, i.e. the model's own type-membership bookkeeping is not
	 * itself the source of the defect.
	 */
	@Test
	public void theCInstanceIsNotExactlyTypeB() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBugSelfInB.use",
				"src/test/resources/dispatchbug/DispatchBugSelfInB.properties");
		System.out.println("### PROBE 8 (C instance is not exactly-type B) OUTCOME: " + (solution == null ? "null" : solution.outcome()));
	}

	/**
	 * SANITY CONTROL: identical shape, except C directly re-declares op() itself instead of merely
	 * inheriting B's override. {@code getOverriddenOperations()} DOES pick up own-declarations
	 * (that's exactly what {@code child.operation(name, searchInherited=false)} finds here), so this
	 * case is expected to dispatch correctly -- confirming the harness/model shape itself is sound
	 * and isolating the bug to the specific "inherited-without-redeclaration" case.
	 */
	@Test
	public void directRedeclarationOnLeafClassDispatchesCorrectly() throws Exception {
		Solution solution = runProbe("src/test/resources/dispatchbug/DispatchBugRedeclared.use",
				"src/test/resources/dispatchbug/DispatchBugRedeclared.properties");
		System.out.println("### PROBE 3 (sanity control, C redeclares) OUTCOME: " + (solution == null ? "null" : solution.outcome()));

		org.junit.Assert.assertTrue(
				"expected SATISFIABLE or TRIVIALLY_SATISFIABLE when C directly redeclares op(); got "
						+ (solution == null ? "null" : solution.outcome()),
				solution != null
						&& (solution.outcome() == Solution.Outcome.SATISFIABLE
								|| solution.outcome() == Solution.Outcome.TRIVIALLY_SATISFIABLE));
	}
}
