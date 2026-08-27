package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
	 * The three figures the gate demands be stated plainly, pinned against a real run rather than
	 * asserted in prose: Kodkod reaches a real verdict on 33 parity-population rows, this project on
	 * 16, and the intersection where parity is claimable at all is 6.
	 */
	@Test
	public void theHonestDenominatorOverTheRealCorpusRun() {
		assertEquals("corpus finding rows", 45, table.summary.corpusRows);
		assertEquals("Study B rows are declared divergences, not parity evidence", 2,
				table.summary.declaredDivergenceRows);
		assertEquals("parity population", 43, table.summary.parityPopulation);
		assertEquals("Kodkod real verdicts", 33, table.summary.kodkodRealVerdicts);
		assertEquals("SMT real verdicts", 16, table.summary.smtRealVerdicts);
		assertEquals("intersection -- the only honest parity denominator", 6, table.summary.intersection);
		assertEquals("agreements", 6, table.summary.agreements);
		assertEquals("undeclared disagreements", 0, table.summary.disagreements);
	}

	/** The exact six rows the intersection consists of, named, so it cannot silently change shape. */
	@Test
	public void theIntersectionIsTheSixNamedRows() {
		List<String> inIntersection = new ArrayList<>();
		for (ParityTable.Row row : table.rows) {
			if (row.inIntersection) {
				inIntersection.add(row.exampleId);
			}
		}

		assertEquals(List.of("Library", "Inheritance", "MultipleInheritance", "Library-UNSAT", "Inheritance-UNSAT",
				"MultipleInheritance-UNSAT"), inIntersection);
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

		assertEquals("45 corpus rows, one table row each -- nothing collapsed", 45, table.rows.size());
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
		assertEquals(2, table.studyB.size());
		for (ParityTable.StudyBRow row : table.studyB) {
			assertTrue(row.exampleId, notBlank(row.kodkodOutcome));
			assertTrue(row.exampleId, notBlank(row.kodkodReason));
			assertTrue(row.exampleId, notBlank(row.smtOutcome));
			assertTrue(row.exampleId, notBlank(row.groundTruth));
			assertTrue(row.exampleId, notBlank(row.divergenceClass));
		}
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

		assertTrue("Kodkod denominator", md.contains("33"));
		assertTrue("SMT denominator", md.contains("16"));
		assertTrue("intersection denominator", md.contains("6 of 45"));
		for (ParityTable.Row row : table.rows) {
			assertTrue("missing row " + row.exampleId, md.contains("| " + row.exampleId + " |"));
		}
		assertTrue("Study A heading", md.contains("Study A"));
		assertTrue("Study B heading", md.contains("Study B"));
		assertFalse("a trivial Kodkod outcome must never be printed as agreement",
				md.contains("TRIVIALLY_SATISFIABLE | yes"));
	}

	private static boolean notBlank(String s) {
		return s != null && !s.trim().isEmpty();
	}

	private static ParityTable.Row rowFor(String exampleId) {
		return table.rows.stream().filter(r -> r.exampleId.equals(exampleId)).findFirst()
				.orElseThrow(() -> new AssertionError("no parity row for " + exampleId));
	}
}
