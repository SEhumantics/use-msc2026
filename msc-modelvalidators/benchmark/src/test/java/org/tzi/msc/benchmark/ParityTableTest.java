package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.tzi.msc.benchmark.ParityTable.ParityClass;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Study A (spec S9, RQ1) is a parity claim, and a parity claim is only worth as much as the
 * arithmetic behind it. Everything here exists to stop that arithmetic flattering the project.
 *
 * <p>The single most dangerous mistake this class guards is counting a Kodkod {@code
 * TRIVIALLY_SATISFIABLE} as agreement with an SMT {@code SATISFIABLE}. Kodkod reports
 * TRIVIALLY_SATISFIABLE when its translation of the model collapsed to constant true -- i.e. when
 * every invariant it could not represent was silently dropped and nothing was left to search. Both
 * outcomes then contain the word "SATISFIABLE" and both look like a yes. Counting them as a match
 * would inflate parity using precisely the incumbent defect this project exists to expose, so
 * {@link #kodkodsTriviallySatisfiableIsNeverAgreement} pins that it never can.
 *
 * <p>The second guard is the denominator itself. Parity is claimable only where BOTH backends reach
 * a real, searched verdict, and {@link #theHonestDenominatorOverTheRealCorpusRun} pins all three
 * figures against the checked-in results of an actual run, so the table cannot quietly drift into
 * claiming more coverage than the run supports.
 */
public class ParityTableTest {

	private static List<ExampleEntry> examples;
	private static List<SolverResult> results;
	private static ParityTable.Table table;

	@BeforeClass
	public static void loadRealCorpusAndRun() throws Exception {
		try (FileReader r = new FileReader(new File("src/main/resources/manifest.json"))) {
			examples = new Gson().fromJson(r, ExampleManifest.class).examples;
		}
		Type listType = new TypeToken<List<SolverResult>>() {
		}.getType();
		try (FileReader r = new FileReader(new File("src/main/resources/latest-results.json"))) {
			results = new Gson().fromJson(r, listType);
		}
		table = ParityTable.compute(examples, results);
	}

	// ------------------------------------------------------- classification, in isolation

	@Test
	public void kodkodsTriviallySatisfiableIsNeverAgreement() {
		ParityClass c = ParityTable.classify("TRIVIALLY_SATISFIABLE", "SATISFIABLE", false, "SATISFIABLE");

		assertNotEquals("a vacuous Kodkod yes must never be counted as agreement", ParityClass.AGREE, c);
		assertEquals(ParityClass.KODKOD_NO_REAL_VERDICT, c);
	}

	/**
	 * The mirror-image trap: "TRIVIALLY_UNSATISFIABLE" and "UNSATISFIABLE" also differ only by a
	 * prefix, and a substring test would match them. A trivial refutation is produced without search
	 * too, so it is no more a real verdict than a trivial satisfaction.
	 */
	@Test
	public void kodkodsTriviallyUnsatisfiableIsNeverAgreement() {
		ParityClass c = ParityTable.classify("TRIVIALLY_UNSATISFIABLE", "UNSATISFIABLE", false, "UNSATISFIABLE");

		assertNotEquals(ParityClass.AGREE, c);
		assertEquals(ParityClass.KODKOD_NO_REAL_VERDICT, c);
	}

	@Test
	public void twoRealVerdictsThatMatchAgree() {
		assertEquals(ParityClass.AGREE, ParityTable.classify("SATISFIABLE", "SATISFIABLE", false, "SATISFIABLE"));
		assertEquals(ParityClass.AGREE,
				ParityTable.classify("UNSATISFIABLE", "UNSATISFIABLE", false, "UNSATISFIABLE"));
	}

	@Test
	public void aDeclaredSupersessionRowIsADeclaredDivergenceNotADefect() {
		ParityClass c = ParityTable.classify("UNSATISFIABLE", "SATISFIABLE", true, "SATISFIABLE");

		assertEquals(ParityClass.DECLARED_DIVERGENCE, c);
	}

	@Test
	public void anUndeclaredDisagreementGroundTruthBacksUsIsAKodkodDefect() {
		assertEquals(ParityClass.KODKOD_DEFECT,
				ParityTable.classify("UNSATISFIABLE", "SATISFIABLE", false, "SATISFIABLE"));
	}

	@Test
	public void anUndeclaredDisagreementGroundTruthBacksTheIncumbentIsAnSmtDefect() {
		assertEquals(ParityClass.SMT_DEFECT,
				ParityTable.classify("UNSATISFIABLE", "SATISFIABLE", false, "UNSATISFIABLE"));
	}

	/**
	 * With no recorded ground truth, a real disagreement cannot be blamed on either side -- and must
	 * not silently become one of the comfortable classes. It stays visible as unclassified, which is
	 * a demand for human attention, not a verdict.
	 */
	@Test
	public void aDisagreementWithNoGroundTruthStaysUnclassified() {
		assertEquals(ParityClass.UNCLASSIFIED_DISAGREEMENT,
				ParityTable.classify("UNSATISFIABLE", "SATISFIABLE", false, null));
	}

	@Test
	public void anSmtRefusalIsNotAgreementAndNotADefect() {
		assertEquals(ParityClass.SMT_NO_VERDICT, ParityTable.classify("SATISFIABLE", "ERROR", false, "SATISFIABLE"));
	}

	@Test
	public void neitherBackendReachingAVerdictIsItsOwnClass() {
		assertEquals(ParityClass.NEITHER_VERDICT,
				ParityTable.classify("TRIVIALLY_SATISFIABLE", "ERROR", false, "SATISFIABLE"));
	}

	// ------------------------------------------------------- over the real corpus run

	/**
	 * The figures the gate demands be stated plainly, pinned against a real run rather than asserted
	 * in prose.
	 *
	 * <p><b>Provenance of the pinned numbers.</b> Regenerated 2026-08-31 from the full 80-row corpus
	 * run at commit {@code 0c3b5917} (RangeBound/-UNSAT added; their Kodkod cells are
	 * TRIVIALLY_UNSATISFIABLE -- the incumbent's defined-count reading of <Attr>_min/_max,
	 * documented in the scenario itself) (the first Study A snapshot to cover the whole corpus -- the
	 * previous pin, 45 rows / intersection 6, predated every scenario added since the August-27 gate).
	 * 2026-08-31, later the same day: the disagreement was RESOLVED BY PROMOTION. DerivedAttr-UNSAT
	 * became the SEVENTH declared Study B divergence (the incumbent's derived attributes never
	 * constrain their stored value, so a derivation-vs-domain contradiction is invisible to it;
	 * fresh pipeline re-check confirmed its recorded outcome). It leaves the parity population by
	 * the standing rule, so the intersection is 41 with agreement on EVERY member and zero
	 * disagreements -- the figures below are pinned to that state.
	 */
	@Test
	public void theHonestDenominatorOverTheRealCorpusRun() {
		assertEquals("corpus finding rows", 80, table.summary.corpusRows);
		assertEquals("Study B rows are declared divergences, not parity evidence", 7,
				table.summary.declaredDivergenceRows);
		assertEquals("parity population", 73, table.summary.parityPopulation);
		assertEquals("Kodkod real verdicts", 43, table.summary.kodkodRealVerdicts);
		assertEquals("SMT real verdicts", 71, table.summary.smtRealVerdicts);
		assertEquals("intersection -- the only honest parity denominator", 41, table.summary.intersection);
		assertEquals("agreements", 41, table.summary.agreements);
		assertEquals("undeclared disagreements", 0, table.summary.disagreements);
		assertEquals("unclassified disagreements", 0, table.summary.unclassifiedDisagreements);
	}

	/**
	 * The intersection's SHAPE is pinned: 41 members, the six original members still in it
	 * (continuity of the parity claim), the Set-attribute corpus rows in it too, ZERO disagreeing
	 * members -- the one disagreement the earlier 78-row snapshot had is now the seventh Study B row and
	 * has left the population by the standing rule.
	 */
	@Test
	public void theIntersectionIsFortyOneAgreeingRows() {
		List<String> inIntersection = new ArrayList<>();
		List<String> disagreeing = new ArrayList<>();
		for (ParityTable.Row row : table.rows) {
			if (row.inIntersection) {
				inIntersection.add(row.exampleId);
				if (Boolean.FALSE.equals(row.agree)) {
					disagreeing.add(row.exampleId);
				}
			}
		}

		assertEquals(41, inIntersection.size());
		for (String original : List.of("Library", "Inheritance", "MultipleInheritance", "Library-UNSAT",
				"Inheritance-UNSAT", "MultipleInheritance-UNSAT")) {
			assertTrue("the original parity rows must still be in the intersection: " + original,
					inIntersection.contains(original));
		}
		assertTrue("the Set(Integer)-attribute rows must be parity evidence now",
				inIntersection.containsAll(List.of("SetAttr", "SetAttr-UNSAT")));
		assertTrue("the promoted Study B row must have LEFT the parity population",
				!inIntersection.contains("DerivedAttr-UNSAT"));
		assertTrue("every intersection member must now agree", disagreeing.isEmpty());
	}

	/**
	 * "Disagreements are not summarised away" (spec S9). Every corpus row that is not a plain
	 * agreement is retained individually, with its own classification -- a count is not evidence.
	 */
	@Test
	public void everyNonAgreeingRowIsRetainedIndividuallyWithAClassification() {
		int nonAgreeing = 0;
		for (ParityTable.Row row : table.rows) {
			assertTrue(row.exampleId + ": every row carries a classification",
					row.parityClass != null && !row.parityClass.isEmpty());
			if (!ParityClass.AGREE.name().equals(row.parityClass)) {
				nonAgreeing++;
			}
		}

		assertEquals("80 corpus rows, one table row each -- nothing collapsed", 80, table.rows.size());
		assertEquals("39 rows are not plain agreements and each is retained", 39, nonAgreeing);
	}

	/**
	 * A row outside the intersection has no agreement question to answer, and must say so rather than
	 * answering "false" -- false would read as a disagreement about an answer, when in fact one side
	 * never gave one.
	 */
	@Test
	public void rowsOutsideTheIntersectionHaveNoAgreementVerdictAtAll() {
		for (ParityTable.Row row : table.rows) {
			if (!row.inIntersection) {
				assertNull(row.exampleId + ": agreement is undefined outside the intersection", row.agree);
			} else {
				assertTrue(row.exampleId + ": rows inside the intersection must answer it", row.agree != null);
			}
		}
	}

	/** The incumbent re-checks nothing, so no Kodkod row may claim a USE re-evaluation. */
	@Test
	public void noKodkodRowClaimsAUseReEvaluation() {
		for (SolverResult r : results) {
			if (!ParityTable.SMT_SOLVER.equals(r.solver)) {
				assertNull(r.exampleId + "/" + r.solver + ": the incumbent performs no USE re-evaluation",
						r.useChecked);
			}
		}
	}

	// ------------------------------------------------------- Study B, and the rendered output

	@Test
	public void studyBRowsComeFromTheCorpusWithAllSixColumnsFilled() {
		assertEquals("spec S9's four cases plus the 2026-08-30 and 2026-08-31 promotions", 7,
			table.studyB.size());
		for (ParityTable.StudyBRow row : table.studyB) {
			assertTrue(row.exampleId, notBlank(row.kodkodOutcome));
			assertTrue(row.exampleId, notBlank(row.kodkodReason));
			assertTrue(row.exampleId, notBlank(row.smtOutcome));
			assertTrue(row.exampleId, notBlank(row.groundTruth));
			assertTrue(row.exampleId, notBlank(row.divergenceClass));
			assertNotNull(row.exampleId + ": every Study B row must state which way it diverges",
					row.divergenceDirection);
		}
	}

	// ------------------------------------------------- the two divergence DIRECTIONS

	/**
	 * A refutation of a model that has a witness and an acceptance of a model that has none are both
	 * "the incumbent is wrong", and flattening them into one class would lose the finding that
	 * matters most: for a verification tool the false ACCEPT is the dangerous one, because it reports
	 * the model is fine when it is not. These are the unit-level pins; {@link
	 * #theRealCorpusCarriesBothDirectionsOnTheNamedRows} is the same claim over the real run.
	 */
	@Test
	public void aWrongRefutationAndAWrongAcceptanceAreDifferentDirections() {
		assertEquals(ParityTable.DivergenceDirection.FALSE_REJECT, ParityTable.divergenceDirection("false-unsat"));
		assertEquals(ParityTable.DivergenceDirection.FALSE_ACCEPT, ParityTable.divergenceDirection("false-sat"));
		assertNotEquals("a wrong refutation and a wrong acceptance must never share a direction",
				ParityTable.divergenceDirection("false-unsat"), ParityTable.divergenceDirection("false-sat"));
	}

	/**
	 * The weaker vocabulary entries are "the incumbent could not state the question", which is not a
	 * wrong answer in either direction. Mapping one of them onto FALSE_ACCEPT or FALSE_REJECT would
	 * upgrade a weak claim into a strong one, the exact dishonesty {@code ExampleEntry.Supersession}
	 * documents the divergence-class field as existing to prevent.
	 */
	@Test
	public void theWeakerDivergenceClassesClaimNoDirectionAtAll() {
		for (String weak : List.of("silent-drop", "cannot-configure", "error")) {
			assertEquals(weak + " is not a wrong ANSWER, so it has no direction",
					ParityTable.DivergenceDirection.NO_ANSWER, ParityTable.divergenceDirection(weak));
		}
	}

	@Test
	public void theRealCorpusCarriesBothDirectionsOnTheNamedRows() {
		List<String> falseRejects = new ArrayList<>();
		List<String> falseAccepts = new ArrayList<>();
		for (ParityTable.StudyBRow row : table.studyB) {
			if (row.divergenceDirection == ParityTable.DivergenceDirection.FALSE_REJECT) {
				falseRejects.add(row.exampleId);
			} else if (row.divergenceDirection == ParityTable.DivergenceDirection.FALSE_ACCEPT) {
				falseAccepts.add(row.exampleId);
			}
		}

		assertEquals("the incumbent wrongly REFUTES these", List.of("IntegerBitwidth-DailyCap",
				"RealGrid-UnitInterval", "AggregationComposition-SelfCycle"), falseRejects);
		assertEquals("the incumbent wrongly ACCEPTS these", List.of("URealThreshold-Below",
				"URealThreshold-NominalErasure", "DerivedAttr-UNSAT", "Redefines-TranslationGap"), falseAccepts);
		assertEquals("false rejects, counted in the summary", 3, table.summary.falseRejectRows);
		assertEquals("false accepts, counted in the summary", 4, table.summary.falseAcceptRows);
	}

	/**
	 * A false ACCEPT is an acceptance: the incumbent's outcome must be one of Kodkod's two
	 * SATISFIABLE names while the row's own ground truth refutes. Checked here, over the real corpus,
	 * rather than trusted from the label.
	 */
	@Test
	public void everyFalseAcceptRowActuallyAcceptsWhatItsGroundTruthRefutes() {
		int checked = 0;
		for (ParityTable.StudyBRow row : table.studyB) {
			if (row.divergenceDirection != ParityTable.DivergenceDirection.FALSE_ACCEPT) {
				continue;
			}
			checked++;
			assertTrue(row.exampleId + ": the incumbent must have accepted, was " + row.kodkodOutcome,
					row.kodkodOutcome.endsWith("SATISFIABLE") && !row.kodkodOutcome.contains("UNSAT"));
			assertEquals(row.exampleId + ": our answer must be the refutation", "UNSATISFIABLE", row.smtOutcome);
			assertTrue(row.exampleId + ": ground truth must state the refutation",
					row.groundTruth.startsWith("UNSATISFIABLE"));
		}
		assertEquals(4, checked);
	}

	/**
	 * The Study B rows record the incumbent's outcome as a manifest claim. The run has to actually
	 * produce it, or the table is prose with a table's authority.
	 */
	@Test
	public void studyBRecordedKodkodOutcomesMatchTheObservedRun() {
		for (ParityTable.StudyBRow row : table.studyB) {
			ParityTable.Row observed = rowFor(row.exampleId);
			assertEquals(row.exampleId + ": recorded Kodkod outcome must be the observed one",
					row.kodkodOutcome, observed.kodkodResult);
			assertEquals(row.exampleId + ": recorded SMT outcome must be the observed one",
					row.smtOutcome, observed.smtResult);
		}
	}

	@Test
	public void markdownStatesAllThreeDenominatorFiguresAndOneLinePerCorpusRow() {
		String md = ParityTable.toMarkdown(table);

		assertTrue("Kodkod denominator", md.contains("43"));
		assertTrue("SMT denominator", md.contains("71"));
		assertTrue("intersection denominator", md.contains("41 of 80"));
		for (ParityTable.Row row : table.rows) {
			assertTrue("missing row " + row.exampleId, md.contains("| " + row.exampleId + " |"));
		}
		assertTrue("Study A heading", md.contains("Study A"));
		assertTrue("Study B heading", md.contains("Study B"));
		assertFalse("a trivial Kodkod outcome must never be printed as agreement",
				md.contains("TRIVIALLY_SATISFIABLE | yes"));
	}

	/**
	 * The rendered Study B table is what a reader of the thesis actually sees, so the direction has
	 * to survive rendering -- a distinction that exists only in the JSON is not evidence in a table.
	 */
	@Test
	public void theRenderedStudyBTableShowsBothDirectionsAndNamesTheDangerousOne() {
		String md = ParityTable.toMarkdown(table);
		String studyB = md.substring(md.indexOf("### Study B"));

		assertTrue("the Study B table needs a direction column", studyB.contains("Divergence direction"));
		assertTrue("the table must say why a false accept is the worse error for a verification tool",
				studyB.contains("false ACCEPT"));
		// Asserted on the CELLS, not on the section: an earlier version of this test looked only for
		// the two words anywhere in the section, and the adversarial run that flattened both classes
		// onto FALSE_REJECT still passed it, because the prose above the table names both directions
		// regardless of what the rows say. A distinction that survives only in the surrounding prose
		// is not a table column.
		for (ParityTable.StudyBRow b : table.studyB) {
			assertTrue(b.exampleId + ": the direction must be rendered in the row itself",
					studyB.contains("| " + b.divergenceClass + " | " + b.divergenceDirection + " |"));
		}
		assertTrue("a false-unsat row must render as FALSE_REJECT",
				studyB.contains("| false-unsat | FALSE_REJECT |"));
		assertTrue("a false-sat row must render as FALSE_ACCEPT",
				studyB.contains("| false-sat | FALSE_ACCEPT |"));
		for (String id : List.of("IntegerBitwidth-DailyCap", "RealGrid-UnitInterval", "URealThreshold-Below",
				"URealThreshold-NominalErasure", "AggregationComposition-SelfCycle", "Redefines-TranslationGap",
				"DerivedAttr-UNSAT")) {
			assertTrue("missing Study B row " + id, studyB.contains("| " + id + " |"));
		}
	}

	private static boolean notBlank(String s) {
		return s != null && !s.trim().isEmpty();
	}

	private static ParityTable.Row rowFor(String exampleId) {
		return table.rows.stream().filter(r -> r.exampleId.equals(exampleId)).findFirst()
				.orElseThrow(() -> new AssertionError("no parity row for " + exampleId));
	}
}
