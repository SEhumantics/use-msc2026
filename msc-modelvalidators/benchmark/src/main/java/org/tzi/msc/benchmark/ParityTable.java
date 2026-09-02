package org.tzi.msc.benchmark;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Study A (spec S9, RQ1) -- the parity table -- computed from a real {@link BenchmarkRunner} run
 * rather than transcribed next to one.
 *
 * <p><b>Why this is generated.</b> A parity claim is the central RQ1 claim, and a hand-written
 * parity table drifts from the run silently and in the flattering direction. Everything here is a
 * pure function of the manifest and one results file, so the only way to change the table is to
 * change the corpus or re-run the benchmark.
 *
 * <p><b>The one rule everything else follows from.</b> Parity is claimable ONLY on the intersection
 * where both backends reach a real, searched verdict. Kodkod's {@code Solution.Outcome} has four
 * values, and only two of them are that: {@code TRIVIALLY_SATISFIABLE} and {@code
 * TRIVIALLY_UNSATISFIABLE} are returned when the translated formula was already constant before any
 * search happened -- which, for this corpus, is what the incumbent produces when it silently drops
 * every invariant it cannot represent. Counting a TRIVIALLY_SATISFIABLE against our SATISFIABLE as
 * agreement would raise the parity number using precisely the incumbent defect this project exists
 * to expose. {@link #isRealVerdict} is therefore an exact-equality test against exactly two names,
 * never a {@code contains("SATISFIABLE")} substring test -- which would match all four, the same
 * trap {@link BenchmarkRunner#isWitnessCapturingOutcome} already documents.
 *
 * <p><b>Disagreements are not summarised away</b> (spec S9). There is one output row per corpus
 * row, each carrying its own classification; nothing is collapsed into a count.
 *
 * <p>Usage: {@code java -cp <classpath> org.tzi.msc.benchmark.ParityTable <manifest.json>
 * <results.json> [out.md] [out.json]} -- with no output paths it writes the Markdown rendering to
 * stdout, so a report can embed captured output instead of retyping numbers.
 */
public final class ParityTable {

	/** The solver name {@link BenchmarkRunner} writes for this project's backend. */
	public static final String SMT_SOLVER = BenchmarkRunner.SMT_SOLVER_NAME;

	/**
	 * The only two Kodkod outcomes that are a real, searched verdict. See the class javadoc: the two
	 * TRIVIALLY_* outcomes are deliberately absent, and this is exact equality, not a substring test.
	 */
	private static final Set<String> REAL_VERDICTS = Set.of("SATISFIABLE", "UNSATISFIABLE");

	/** Printed in the Kodkod column when the five SAT backends did not all reach the same outcome. */
	static final String MIXED = "MIXED";

	/**
	 * Printed in the Kodkod column when FEWER than all five SAT backends in {@link
	 * BenchmarkRunner#SOLVERS} have a result cell for this row at all -- a partial or truncated
	 * {@code results.json} (a benchmark run killed mid-write; {@link ReportBuilder}'s own javadoc
	 * documents exactly this failure mode), not a complete run. This is checked BEFORE the
	 * single-outcome/{@link #MIXED} distinction in {@link #agreedKodkodOutcome}: a subset of cells
	 * that happen to agree with each other (or with the SMT result) is a coincidence over partial
	 * data, never the full 5-way corroboration that a bare outcome name in this column claims. Never
	 * silently folded into a real verdict -- see the class javadoc's "silent drift in the flattering
	 * direction" warning, which this constant exists to close off.
	 */
	static final String PARTIAL = "PARTIAL";

	private ParityTable() {
	}

	/** How one corpus row's pair of outcomes is classified. Exactly one applies to each row. */
	public enum ParityClass {
		/** Both backends reached a real verdict and it was the same one. The only parity evidence. */
		AGREE,
		/**
		 * A divergence the corpus DECLARES in advance, with the incumbent's mechanism and an
		 * independently establishable ground truth recorded alongside it (a Study B supersession row).
		 * Deliberate divergence is not a defect on either side and is not parity evidence either, so
		 * these rows leave the parity population entirely rather than scoring for or against it.
		 */
		DECLARED_DIVERGENCE,
		/** Both reached a real verdict, they differ, and the recorded ground truth backs ours. */
		KODKOD_DEFECT,
		/** Both reached a real verdict, they differ, and the recorded ground truth backs the incumbent. */
		SMT_DEFECT,
		/**
		 * Both reached a real verdict and they differ, with no recorded ground truth to settle it.
		 * Deliberately its own class: silently folding an unexplained disagreement into one of the
		 * comfortable ones is the failure mode this whole file is written against.
		 */
		UNCLASSIFIED_DISAGREEMENT,
		/**
		 * We reached a real verdict and the incumbent did not -- a TRIVIALLY_* outcome (its
		 * translation collapsed before search) or an error. NOT agreement, whatever the two strings
		 * look like.
		 */
		KODKOD_NO_REAL_VERDICT,
		/**
		 * The incumbent reached a real verdict and we did not: our translation refused the model,
		 * fail-closed, because it falls outside the OCL fragment currently supported. Not a wrong
		 * answer, and equally not agreement -- it is an absence of an answer, and the parity table
		 * shows it as one.
		 */
		SMT_NO_VERDICT,
		/** Neither backend reached a real verdict. Nothing is claimable in either direction. */
		NEITHER_VERDICT
	}

	/**
	 * WHICH WAY a declared divergence points. {@link ParityClass#DECLARED_DIVERGENCE} says only "the
	 * corpus expected these two backends to differ here"; it does not say whether the incumbent
	 * wrongly refused a model that has a witness or wrongly blessed one that has none. Those are not
	 * the same finding, and for a verification tool they are not even the same severity, so the Study
	 * B table names the direction instead of leaving a reader to infer it from two outcome strings.
	 */
	public enum DivergenceDirection {
		/**
		 * The incumbent ACCEPTED a model that has no satisfying instance -- it reported that a valid
		 * instance exists, and produced one, where the ground truth is a refutation. The dangerous
		 * direction: a verification tool answering "fine" is indistinguishable, to its user, from a
		 * model that really is fine, so nothing prompts anyone to look again.
		 */
		FALSE_ACCEPT,
		/**
		 * The incumbent REFUTED a model that does have a witness. Wrong, and costly, but self-
		 * announcing: the user is told there is no instance and gets no snapshot, which is a visible
		 * failure rather than a silent one.
		 */
		FALSE_REJECT,
		/**
		 * The incumbent gave no wrong ANSWER -- it could not state the question (silent-drop,
		 * cannot-configure) or it failed (error). Kept separate on purpose: promoting one of these to
		 * a direction would upgrade a weak claim into a strong one, which is the specific dishonesty
		 * {@code ExampleEntry.Supersession#divergenceClass} exists to prevent.
		 */
		NO_ANSWER
	}

	/**
	 * Maps a corpus divergence class onto its direction. Only the two STRONG classes have one; the
	 * mapping is exhaustive over the vocabulary {@code ManifestSchemaTest} enforces, and anything
	 * outside it falls to {@link DivergenceDirection#NO_ANSWER} rather than being guessed at.
	 */
	static DivergenceDirection divergenceDirection(String divergenceClass) {
		if ("false-sat".equals(divergenceClass)) {
			return DivergenceDirection.FALSE_ACCEPT;
		}
		if ("false-unsat".equals(divergenceClass)) {
			return DivergenceDirection.FALSE_REJECT;
		}
		return DivergenceDirection.NO_ANSWER;
	}

	/** One Study A row -- the spec's seven columns plus the bookkeeping that justifies them. */
	public static final class Row {
		public String exampleId;
		public List<String> features;
		public int featureCount;
		/** The outcome all five Kodkod SAT backends agreed on, {@link #MIXED}, or "ABSENT". */
		public String kodkodResult;
		public String smtResult;
		public boolean kodkodRealVerdict;
		public boolean smtRealVerdict;
		/** True only for rows in the parity population where BOTH backends reached a real verdict. */
		public boolean inIntersection;
		/**
		 * Null outside the intersection, and that is the point: "false" would read as a disagreement
		 * about an answer when in fact one side never produced one.
		 */
		public Boolean agree;
		/** Whether OUR backend reconstructed a snapshot for this row; null when it made no claim. */
		public Boolean reconstructed;
		/** Whether OUR backend's independent USE re-evaluation passed; null when none was performed. */
		public Boolean useChecked;
		public String parityClass;
		/** Null unless this row is a declared Study B divergence; see {@link DivergenceDirection}. */
		public DivergenceDirection divergenceDirection;
		public String note;
	}

	/** One Study B row -- the spec's six columns, read straight out of the corpus manifest. */
	public static final class StudyBRow {
		public String exampleId;
		public String kodkodOutcome;
		public String kodkodReason;
		public String smtOutcome;
		public String groundTruth;
		public String divergenceClass;
		/** Derived from {@link #divergenceClass}, so the table cannot label one and imply the other. */
		public DivergenceDirection divergenceDirection;
	}

	/** The denominators, which the gate requires be stated plainly rather than implied. */
	public static final class Summary {
		/** Every finding-mode corpus row the run covered. */
		public int corpusRows;
		/** Rows the corpus declares as deliberate divergence (Study B); excluded from parity. */
		public int declaredDivergenceRows;
		/** Declared divergences where the incumbent wrongly ACCEPTED -- the dangerous direction. */
		public int falseAcceptRows;
		/** Declared divergences where the incumbent wrongly REFUTED. */
		public int falseRejectRows;
		/** {@link #corpusRows} minus {@link #declaredDivergenceRows}. */
		public int parityPopulation;
		/** Parity-population rows where the incumbent reached a real, searched verdict. */
		public int kodkodRealVerdicts;
		/** Parity-population rows where this project reached a real verdict. */
		public int smtRealVerdicts;
		/** Rows where BOTH did -- the only honest parity denominator. */
		public int intersection;
		public int agreements;
		/** Undeclared disagreements inside the intersection. */
		public int disagreements;
		public int unclassifiedDisagreements;
		public int smtNoVerdict;
		public int kodkodNoRealVerdict;
		public int neitherVerdict;
	}

	/** Everything a report needs: the summary, every retained row, and the Study B table. */
	public static final class Table {
		public Summary summary = new Summary();
		public List<Row> rows = new ArrayList<>();
		public List<StudyBRow> studyB = new ArrayList<>();
	}

	/** See the class javadoc: exact equality against exactly two names, never a substring test. */
	static boolean isRealVerdict(String outcome) {
		return outcome != null && REAL_VERDICTS.contains(outcome);
	}

	/**
	 * Classifies one corpus row. Pure, and package-visible so it can be tested directly on the pairs
	 * that matter rather than only through a whole run.
	 *
	 * @param kodkodOutcome the incumbent's outcome for this row, or {@link #MIXED}/null
	 * @param smtOutcome this project's outcome for this row
	 * @param declaredDivergence whether the corpus declares this row a deliberate divergence
	 * @param groundTruthOutcome the outcome recorded as this row's oracle; null when none is recorded
	 */
	static ParityClass classify(String kodkodOutcome, String smtOutcome, boolean declaredDivergence,
			String groundTruthOutcome) {
		// Checked first, and on purpose: a declared divergence is a row the corpus already explains,
		// with the incumbent's mechanism and a tool-independent ground truth recorded alongside. It is
		// neither a defect nor parity evidence, so it never reaches the defect classification below.
		if (declaredDivergence) {
			return ParityClass.DECLARED_DIVERGENCE;
		}
		boolean kodkodReal = isRealVerdict(kodkodOutcome);
		boolean smtReal = isRealVerdict(smtOutcome);
		if (kodkodReal && smtReal) {
			if (kodkodOutcome.equals(smtOutcome)) {
				return ParityClass.AGREE;
			}
			if (groundTruthOutcome == null) {
				return ParityClass.UNCLASSIFIED_DISAGREEMENT;
			}
			if (groundTruthOutcome.equals(smtOutcome)) {
				return ParityClass.KODKOD_DEFECT;
			}
			if (groundTruthOutcome.equals(kodkodOutcome)) {
				return ParityClass.SMT_DEFECT;
			}
			// The oracle agrees with neither side: it settles nothing, so nothing is settled.
			return ParityClass.UNCLASSIFIED_DISAGREEMENT;
		}
		if (!kodkodReal && !smtReal) {
			return ParityClass.NEITHER_VERDICT;
		}
		return kodkodReal ? ParityClass.SMT_NO_VERDICT : ParityClass.KODKOD_NO_REAL_VERDICT;
	}

	/**
	 * Builds the whole table from a manifest and one run's results. Rows keep manifest order, so the
	 * table reads in the same order as every other view of this corpus.
	 */
	public static Table compute(List<ExampleEntry> examples, List<SolverResult> results) {
		Map<String, Map<String, SolverResult>> byExample = new LinkedHashMap<>();
		for (SolverResult r : results) {
			byExample.computeIfAbsent(r.exampleId, k -> new LinkedHashMap<>()).put(r.solver, r);
		}

		Table table = new Table();
		for (ExampleEntry ex : examples) {
			Map<String, SolverResult> cells = byExample.get(ex.id);
			if (cells == null) {
				continue; // validating-only example: it never reaches a solver at all
			}
			SolverResult smt = cells.get(SMT_SOLVER);
			Row row = new Row();
			row.exampleId = ex.id;
			row.features = ex.features == null ? List.of() : ex.features;
			row.featureCount = row.features.size();
			row.kodkodResult = agreedKodkodOutcome(cells);
			row.smtResult = smt == null ? "ABSENT" : smt.outcome;
			row.reconstructed = smt == null ? null : smt.reconstructed;
			row.useChecked = smt == null ? null : smt.useChecked;
			row.kodkodRealVerdict = isRealVerdict(row.kodkodResult);
			row.smtRealVerdict = isRealVerdict(row.smtResult);

			boolean declared = ex.supersession != null;
			String groundTruth = ex.expected == null ? null : ex.expected.outcome;
			ParityClass parityClass = classify(row.kodkodResult, row.smtResult, declared, groundTruth);
			row.parityClass = parityClass.name();
			row.divergenceDirection = declared ? divergenceDirection(ex.supersession.divergenceClass) : null;
			row.inIntersection = !declared && row.kodkodRealVerdict && row.smtRealVerdict;
			row.agree = row.inIntersection ? Boolean.valueOf(parityClass == ParityClass.AGREE) : null;
			row.note = noteFor(parityClass, row);
			table.rows.add(row);

			table.summary.corpusRows++;
			if (declared) {
				table.summary.declaredDivergenceRows++;
				if (row.divergenceDirection == DivergenceDirection.FALSE_ACCEPT) {
					table.summary.falseAcceptRows++;
				} else if (row.divergenceDirection == DivergenceDirection.FALSE_REJECT) {
					table.summary.falseRejectRows++;
				}
			} else {
				table.summary.parityPopulation++;
				if (row.kodkodRealVerdict) {
					table.summary.kodkodRealVerdicts++;
				}
				if (row.smtRealVerdict) {
					table.summary.smtRealVerdicts++;
				}
				if (row.inIntersection) {
					table.summary.intersection++;
				}
			}
			switch (parityClass) {
				case AGREE -> table.summary.agreements++;
				case KODKOD_DEFECT, SMT_DEFECT -> table.summary.disagreements++;
				case UNCLASSIFIED_DISAGREEMENT -> {
					table.summary.disagreements++;
					table.summary.unclassifiedDisagreements++;
				}
				case SMT_NO_VERDICT -> table.summary.smtNoVerdict++;
				case KODKOD_NO_REAL_VERDICT -> table.summary.kodkodNoRealVerdict++;
				case NEITHER_VERDICT -> table.summary.neitherVerdict++;
				case DECLARED_DIVERGENCE -> {
					// counted as declaredDivergenceRows above; deliberately scores neither way
				}
			}

			if (declared) {
				StudyBRow b = new StudyBRow();
				b.exampleId = ex.id;
				b.kodkodOutcome = ex.supersession.kodkodOutcome;
				b.kodkodReason = ex.supersession.kodkodReason;
				b.smtOutcome = ex.supersession.smtOutcome;
				b.groundTruth = ex.supersession.groundTruth;
				b.divergenceClass = ex.supersession.divergenceClass;
				b.divergenceDirection = row.divergenceDirection;
				table.studyB.add(b);
			}
		}
		return table;
	}

	/**
	 * The incumbent's outcome for a row, which is only well defined when all five of its SAT backends
	 * are PRESENT and agree. They are five different SAT solvers behind one translation, so a split
	 * is a finding in its own right, not something to average -- {@link #MIXED} is never a real
	 * verdict, so a split row leaves the intersection instead of being resolved by majority.
	 *
	 * <p>Presence is checked FIRST, before agreement: a real, documented failure mode
	 * ({@link ReportBuilder}'s javadoc names a benchmark run killed mid-write) can leave a row with
	 * only some of the five cells written. If those happen to agree with each other -- the most
	 * dangerous case, since a lone surviving cell trivially "agrees with itself" -- that is NOT the
	 * 5-way corroboration a bare outcome name in this column claims, so it must never reach the
	 * {@code outcomes.size() == 1} shortcut below. {@link #PARTIAL} is returned instead, which is
	 * never in {@link #REAL_VERDICTS} and so can never be silently counted as agreement.
	 */
	private static String agreedKodkodOutcome(Map<String, SolverResult> cells) {
		Set<String> outcomes = new LinkedHashSet<>();
		List<String> missing = new ArrayList<>();
		for (String solver : BenchmarkRunner.SOLVERS) {
			SolverResult r = cells.get(solver);
			if (r != null) {
				outcomes.add(r.outcome);
			} else {
				missing.add(solver);
			}
		}
		if (outcomes.isEmpty()) {
			return "ABSENT";
		}
		if (!missing.isEmpty()) {
			int present = BenchmarkRunner.SOLVERS.length - missing.size();
			return PARTIAL + " (" + present + "/" + BenchmarkRunner.SOLVERS.length + " present, missing "
					+ String.join(", ", missing) + ")";
		}
		return outcomes.size() == 1 ? outcomes.iterator().next() : MIXED + outcomes;
	}

	/** Whether an {@link #agreedKodkodOutcome} value is the {@link #PARTIAL} state, not a real verdict. */
	private static boolean isPartialKodkodResult(String kodkodResult) {
		return kodkodResult != null && kodkodResult.startsWith(PARTIAL);
	}

	private static String noteFor(ParityClass parityClass, Row row) {
		return switch (parityClass) {
			case AGREE -> "both backends searched and returned " + row.smtResult;
			case DECLARED_DIVERGENCE -> "declared Study B supersession row ("
					+ (row.divergenceDirection == DivergenceDirection.FALSE_ACCEPT
							? "the incumbent wrongly ACCEPTED a model with no instance"
							: row.divergenceDirection == DivergenceDirection.FALSE_REJECT
									? "the incumbent wrongly REFUTED a model that has one"
									: "no wrong answer, only an unstatable question")
					+ "); see the Study B table";
			case KODKOD_DEFECT -> "undeclared disagreement, ground truth backs the SMT backend";
			case SMT_DEFECT -> "undeclared disagreement, ground truth backs the incumbent";
			case UNCLASSIFIED_DISAGREEMENT -> "undeclared disagreement with no ground truth to settle it";
			case SMT_NO_VERDICT -> "SMT translation refused this model (fail-closed, outside the "
					+ "supported OCL fragment); no verdict, so no parity claim";
			case KODKOD_NO_REAL_VERDICT -> isPartialKodkodResult(row.kodkodResult)
					? "Kodkod's results for this row are INCOMPLETE (" + row.kodkodResult + ") -- a "
							+ "partial/truncated results.json, never full 5-way corroboration; not agreement"
					: "Kodkod returned " + row.kodkodResult + ", a verdict reached "
							+ "without search; not agreement";
			case NEITHER_VERDICT -> isPartialKodkodResult(row.kodkodResult)
					? "Kodkod's results for this row are INCOMPLETE (" + row.kodkodResult + ") and the SMT "
							+ "backend reached no verdict either; nothing is claimable"
					: "neither backend reached a real verdict";
		};
	}

	// ------------------------------------------------------------------ rendering

	/**
	 * Renders the table as GitHub-flavoured Markdown, so an evidence document can embed captured
	 * output instead of retyping figures that would then be free to drift.
	 */
	public static String toMarkdown(Table t) {
		Summary s = t.summary;
		StringBuilder sb = new StringBuilder();
		sb.append("### Study A -- Parity (RQ1)\n\n");
		sb.append("Generated by `ParityTable` from one `BenchmarkRunner` run over the corpus.\n\n");
		sb.append("**The honest denominator.** Parity is claimable only where BOTH backends reach a real, ")
				.append("searched verdict.\n\n");
		sb.append("| Population | Rows |\n|---|---:|\n");
		sb.append("| Corpus finding rows | ").append(s.corpusRows).append(" |\n");
		sb.append("| Declared Study B divergences (excluded from parity) | ")
				.append(s.declaredDivergenceRows).append(" |\n");
		sb.append("| Parity population | ").append(s.parityPopulation).append(" |\n");
		sb.append("| Kodkod reaches a real verdict | ").append(s.kodkodRealVerdicts).append(" |\n");
		sb.append("| SMT reaches a real verdict | ").append(s.smtRealVerdicts).append(" |\n");
		sb.append("| **Intersection -- the parity denominator** | **").append(s.intersection).append("** |\n");
		sb.append("| Agreements within the intersection | ").append(s.agreements).append(" |\n");
		sb.append("| Undeclared disagreements within the intersection | ").append(s.disagreements)
				.append(" |\n\n");
		sb.append("Parity is therefore claimed on ").append(s.intersection).append(" of ").append(s.corpusRows)
				.append(" corpus rows (").append(s.intersection).append(" of the ").append(s.parityPopulation)
				.append("-row parity population), with ").append(s.agreements).append("/").append(s.intersection)
				.append(" agreement. A Kodkod `TRIVIALLY_SATISFIABLE` is NOT counted as agreement: it is a")
				.append(" verdict reached without search, after the incumbent silently dropped what it could")
				.append(" not represent.\n\n");

		sb.append("| Example | Features | Kodkod result | SMT result | Agree | Reconstructed | USE-checked |")
				.append(" Classification |\n");
		sb.append("|---|---:|---|---|---|---|---|---|\n");
		for (Row r : t.rows) {
			sb.append("| ").append(r.exampleId).append(" | ").append(r.featureCount).append(" | ")
					.append(r.kodkodResult).append(" | ").append(r.smtResult).append(" | ")
					.append(agreeCell(r)).append(" | ").append(tri(r.reconstructed)).append(" | ")
					.append(tri(r.useChecked)).append(" | ").append(r.parityClass).append(" |\n");
		}
		sb.append("\n`Reconstructed` and `USE-checked` describe THIS project's snapshot. The incumbent")
				.append(" performs no independent re-evaluation of its own witnesses at all, which is why no")
				.append(" Kodkod row can fill that column.\n\n");

		sb.append("#### Every non-agreeing row, retained and classified\n\n");
		sb.append("Spec S9: \"Disagreements are not summarised away.\" One entry each, never a count.\n\n");
		sb.append("| Example | Kodkod | SMT | Classification | Why |\n|---|---|---|---|---|\n");
		for (Row r : t.rows) {
			if (!ParityClass.AGREE.name().equals(r.parityClass)) {
				sb.append("| ").append(r.exampleId).append(" | ").append(r.kodkodResult).append(" | ")
						.append(r.smtResult).append(" | ").append(r.parityClass).append(" | ")
						.append(r.note).append(" |\n");
			}
		}

		sb.append("\n### Study B -- Supersession (RQ2)\n\n");
		sb.append("The corpus declares ").append(s.declaredDivergenceRows).append(" supersession rows, in TWO")
				.append(" directions, which are not the same finding and are not the same severity.\n\n");
		sb.append("- **").append(s.falseRejectRows).append(" `FALSE_REJECT`** -- the incumbent refutes a model")
				.append(" that does have a witness. Wrong, and costly, but self-announcing: the user is told")
				.append(" there is no instance and gets no snapshot.\n");
		sb.append("- **").append(s.falseAcceptRows).append(" `FALSE_ACCEPT`** -- the incumbent reports a")
				.append(" satisfying instance for a model that has none, and hands over a snapshot. For a")
				.append(" verification tool this is the more dangerous error: a false ACCEPT is")
				.append(" indistinguishable, to its user, from a model that really is correct, so nothing")
				.append(" prompts anyone to look again.\n\n");
		sb.append("| Case | Kodkod outcome | Kodkod reason | SMT outcome | Ground truth | Divergence class |")
				.append(" Divergence direction |\n");
		sb.append("|---|---|---|---|---|---|---|\n");
		for (StudyBRow b : t.studyB) {
			sb.append("| ").append(b.exampleId).append(" | ").append(b.kodkodOutcome).append(" | ")
					.append(oneLine(b.kodkodReason)).append(" | ").append(b.smtOutcome).append(" | ")
					.append(oneLine(b.groundTruth)).append(" | ").append(b.divergenceClass).append(" | ")
					.append(b.divergenceDirection).append(" |\n");
		}
		return sb.toString();
	}

	private static String agreeCell(Row r) {
		if (r.agree == null) {
			return "n/a";
		}
		return r.agree ? "yes" : "**NO**";
	}

	private static String tri(Boolean b) {
		return b == null ? "n/a" : (b ? "yes" : "no");
	}

	/** Markdown table cells cannot contain newlines or bare pipes. */
	private static String oneLine(String s) {
		return s == null ? "" : s.replaceAll("\\s+", " ").replace("|", "\\|").trim();
	}

	// ------------------------------------------------------------------ entry point

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: ParityTable <manifest.json> <results.json> [out.md] [out.json]");
			System.exit(1);
		}
		Gson gson = new Gson();
		ExampleManifest manifest;
		try (FileReader r = new FileReader(new File(args[0]), StandardCharsets.UTF_8)) {
			manifest = gson.fromJson(r, ExampleManifest.class);
		}
		Type listType = new TypeToken<List<SolverResult>>() {
		}.getType();
		List<SolverResult> results;
		try (FileReader r = new FileReader(new File(args[1]), StandardCharsets.UTF_8)) {
			results = gson.fromJson(r, listType);
		}
		Table table = compute(manifest.examples, results);
		String markdown = toMarkdown(table);
		if (args.length >= 3) {
			try (PrintWriter w = new PrintWriter(new File(args[2]), StandardCharsets.UTF_8)) {
				w.write(markdown);
			}
			System.err.println("Wrote " + args[2]);
		} else {
			System.out.print(markdown);
		}
		if (args.length >= 4) {
			try (PrintWriter w = new PrintWriter(new File(args[3]), StandardCharsets.UTF_8)) {
				w.write(new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(table));
			}
			System.err.println("Wrote " + args[3]);
		}
	}
}
