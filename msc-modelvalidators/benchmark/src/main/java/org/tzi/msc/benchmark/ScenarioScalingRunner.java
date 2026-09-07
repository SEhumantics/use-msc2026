package org.tzi.msc.benchmark;

import com.google.gson.GsonBuilder;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.ClassScope;
import org.tzi.use.smt.config.ConfigurationVocabulary;
import org.tzi.use.smt.config.QueryParser;
import org.tzi.use.smt.finder.ModelFinderResult;
import org.tzi.use.smt.finder.ResultClassification;
import org.tzi.use.smt.finder.SmtModelFinder;
import org.tzi.use.smt.solver.SolveInstrumentation;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Measures how the cost of each scenario policy grows with the number of exposed uncertainty
 * coordinates.
 *
 * <p>The model is generated, not curated, precisely so that ONE variable moves: {@code n} UReal
 * attributes on a single class, each carrying one positive confidence threshold, each given the
 * same two candidate deviations. The configured scenario space is therefore exactly {@code 2^n},
 * and {@code ScenarioSpace.MAX_SCENARIOS} (256) caps the experiment at {@code n = 8}.
 *
 * <p>Every representative is chosen to clear the WIDER deviation, so every scenario is satisfiable
 * and no policy exits early. That is the honest worst case for COVER, which must solve and then
 * independently re-validate all {@code 2^n} of them.
 *
 * <p>What the three policies actually do differs in kind, which is why solver calls and script size
 * are recorded separately rather than inferred from wall-clock:
 *
 * <ul>
 *   <li>EXISTS emits ONE script with the deviation left symbolic;
 *   <li>COVER emits ONE SCRIPT PER SCENARIO, each the size of a single-scenario problem;
 *   <li>UNIFORM emits ONE script carrying every scenario at once.
 * </ul>
 *
 * <p>Usage: {@code ScenarioScalingRunner <output.json> [maxCoordinates] [repeats] [warmups]}.
 * Defaults: 8 coordinates, 5 measured repeats, 1 warm-up. Exits 0 on success, 1 on a usage error.
 */
public final class ScenarioScalingRunner {

  private static final String[] PROFILES = {"exists satisfy", "cover satisfy", "uniform satisfy"};
  private static final Duration PER_RUN_TIMEOUT = Duration.ofSeconds(120);

  private ScenarioScalingRunner() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println(
          "usage: ScenarioScalingRunner <output.json> [maxCoordinates] [repeats] [warmups]");
      System.exit(1);
      return;
    }
    Path out = Path.of(args[0]);
    int maxCoordinates = args.length > 1 ? Integer.parseInt(args[1]) : 8;
    int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 5;
    int warmups = args.length > 3 ? Integer.parseInt(args[3]) : 1;

    List<Row> rows = new ArrayList<>();
    for (int n = 1; n <= maxCoordinates; n++) {
      MModel model = compile(modelText(n));
      ConfigurationVocabulary vocabulary = ConfigurationVocabulary.fromModel(model);
      for (boolean persistent : new boolean[] {true, false}) {
        for (String profile : PROFILES) {
          Row row = measure(model, vocabulary, n, profile, repeats, warmups, persistent);
          rows.add(row);
          System.out.printf(
              "n=%d %-9s %-8s scenarios=%-4d calls=%-4d chars=%-8d solve=%.1fms witness=%.1fms"
                  + " encode=%.1fms total=%.1fms %s%n",
              row.coordinates, row.solverMode, row.policy, row.configuredScenarios, row.solverCalls,
              row.scriptCharacters, row.solverMs, row.witnessMs, row.encodingMs, row.totalMs,
              row.classification);
        }
      }
    }
    Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(rows));
    System.out.println("wrote " + rows.size() + " rows to " + out.toAbsolutePath());
  }

  /** One class, {@code n} UReal attributes, one positive confidence threshold on each. */
  static String modelText(int n) {
    StringBuilder text = new StringBuilder("model Scaling\nclass Sensor\nattributes\n");
    for (int i = 0; i < n; i++) {
      text.append("  a").append(i).append(" : UReal\n");
    }
    text.append("end\nconstraints\n");
    for (int i = 0; i < n; i++) {
      text.append("context s : Sensor inv t")
          .append(i)
          .append(": (s.a")
          .append(i)
          .append(" > 0.30).toBooleanC(0.95)\n");
    }
    return text.toString();
  }

  private static Row measure(
      MModel model,
      ConfigurationVocabulary vocabulary,
      int n,
      String profile,
      int repeats,
      int warmups,
      boolean persistent) {
    List<AttributeDomain> domains = new ArrayList<>();
    List<String> active = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      // 0.45 clears 0.30 + z(0.95) * 0.06 = 0.3987, so EVERY scenario is satisfiable.
      domains.add(new AttributeDomain("Sensor", "a" + i, "value", List.of("0.45"), null, null));
      domains.add(
          new AttributeDomain("Sensor", "a" + i, "uncertainty", List.of("0.02", "0.06"), null, null));
      active.add("Sensor::t" + i);
    }
    AnalysisConfiguration config =
        new AnalysisConfiguration(
            List.of(new ClassScope("Sensor", 1, 1, List.of("s1"))),
            List.of(),
            domains,
            Set.copyOf(active),
            QueryParser.parse(profile, vocabulary),
            PER_RUN_TIMEOUT,
            1);

    Row row = new Row();
    row.coordinates = n;
    row.configuredScenarios = 1 << n;
    row.policy = profile.split(" ")[0];
    row.repeats = repeats;
    row.warmups = warmups;
    // One-shot spawns a fresh solver process per (check-sat); persistent reuses one across the
    // whole run. COVER emits one script PER SCENARIO, so it is the policy whose measured cost this
    // choice can dominate -- recording both is what separates the policy's cost from the harness's.
    row.solverMode = persistent ? "persistent" : "one-shot";

    List<Double> totals = new ArrayList<>();
    List<Double> solverMs = new ArrayList<>();
    List<Double> witnessMs = new ArrayList<>();
    for (int r = 0; r < warmups + repeats; r++) {
      long[] saved = SolveInstrumentation.snapshot();
      long started = System.nanoTime();
      ModelFinderResult result;
      try (SolverProcess process =
          persistent
              ? SolverProcess.persistent(SolverBinary.resolve(), PER_RUN_TIMEOUT)
              : null) {
        result = SmtModelFinder.find(model, config, process);
      } catch (Exception e) {
        row.classification = ResultClassification.ofRefusal().name();
        row.note = e.getClass().getSimpleName();
        SolveInstrumentation.restore(saved);
        return row;
      }
      double total = (System.nanoTime() - started) / 1e6;
      double solver = SolveInstrumentation.solverNanos() / 1e6;
      double witness = SolveInstrumentation.witnessNanos() / 1e6;
      if (r >= warmups) {
        totals.add(total);
        solverMs.add(solver);
        witnessMs.add(witness);
        row.solverCalls = SolveInstrumentation.solverCalls();
        row.scriptCharacters = SolveInstrumentation.scriptCharacters();
        row.reportedScenarios = result.scenarios().size();
        row.quantileEnclosures = result.quantileEnclosures();
        row.classification = ResultClassification.of(result, config.activeInvariants()).name();
      }
      SolveInstrumentation.restore(saved);
    }
    row.totalMs = median(totals);
    row.solverMs = median(solverMs);
    row.witnessMs = median(witnessMs);
    // Everything that is neither solving nor witness reconstruction/validation: building the
    // scenario space, translating the invariants and rendering SMT-LIB.
    row.encodingMs = round(row.totalMs - row.solverMs - row.witnessMs);
    return row;
  }

  private static double median(List<Double> values) {
    List<Double> sorted = new ArrayList<>(values);
    sorted.sort(Double::compareTo);
    return round(sorted.get(sorted.size() / 2));
  }

  private static double round(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private static MModel compile(String text) {
    MModel model =
        USECompiler.compileSpecification(
            text, "Scaling", new PrintWriter(new StringWriter(), true), new ModelFactory());
    if (model == null) {
      throw new IllegalStateException("generated scaling model did not compile");
    }
    return model;
  }

  /** One measured (coordinates, policy) cell. Serialized straight to JSON. */
  static final class Row {
    int coordinates;
    int configuredScenarios;
    int reportedScenarios;
    String policy;
    int repeats;
    int warmups;
    long solverCalls;
    long scriptCharacters;
    int quantileEnclosures;
    double solverMs;
    double witnessMs;
    double encodingMs;
    double totalMs;
    String classification;
    String solverMode;
    String note;
  }
}
