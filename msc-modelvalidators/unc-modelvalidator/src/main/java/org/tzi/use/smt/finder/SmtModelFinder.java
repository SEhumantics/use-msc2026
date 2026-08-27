package org.tzi.use.smt.finder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.api.UseApiException;
import org.tzi.use.main.Session;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.config.QueryExpr;
import org.tzi.use.smt.config.QueryRequirements;
import org.tzi.use.smt.config.Scenario;
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.encode.AssociationLinkEncoder;
import org.tzi.use.smt.encode.AssociationLinks;
import org.tzi.use.smt.encode.AttributeEncoder;
import org.tzi.use.smt.encode.AttributeType;
import org.tzi.use.smt.encode.AttributeValues;
import org.tzi.use.smt.encode.FragmentBoundary;
import org.tzi.use.smt.encode.FragmentChecker;
import org.tzi.use.smt.encode.FragmentCoverageLedger;
import org.tzi.use.smt.encode.Multiplicity;
import org.tzi.use.smt.encode.ObjectSlotEncoder;
import org.tzi.use.smt.encode.ObjectSlots;
import org.tzi.use.smt.encode.PredefinedLinkEncoder;
import org.tzi.use.smt.encode.QueryCompiler;
import org.tzi.use.smt.encode.ScenarioSpace;
import org.tzi.use.smt.encode.SmtTranslationException;
import org.tzi.use.smt.encode.TranslationContext;
import org.tzi.use.smt.reconstruct.SmtValueDecoder;
import org.tzi.use.smt.reconstruct.SystemStateReconstructor;
import org.tzi.use.smt.solver.SmtModelParser;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;
import org.tzi.use.smt.verify.InvariantReEvaluator;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.smt.verify.QueryWitnessChecker;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MClassifier;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MMultiplicity;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.sys.MSystem;

/**
 * The end-to-end SMT model finder: given a compiled model and a normalized configuration (Tasks
 * 3.1-3.7's building blocks driven mechanically over every class/attribute/association/invariant
 * the configuration names, instead of a test hand-picking exactly what it needs), encodes, solves,
 * and -- on SAT -- reconstructs and independently re-verifies one instance. Every prior task
 * exercised these pieces individually against a hand-built subset of Library; this is the first
 * place they are driven generically from a real configuration, the shape production use (and Task
 * 3.8b's differential test) needs.
 */
public final class SmtModelFinder {
  private SmtModelFinder() {}

  /**
   * One solve's outcome. A single script may carry SEVERAL scenario copies (that is what UNIFORM
   * is), so the reconstruction inputs are a LIST: one context and one compiled core per scenario,
   * over shared snapshot symbols.
   */
  private record Solved(
      SolverOutcome outcome,
      FragmentCoverageLedger ledger,
      List<Copy> copies,
      Map<String, SmtValue> modelValues) {}

  /**
   * One scenario's share of a solve. {@code scenario} is null exactly for the free-uncertainty
   * EXISTS encoding, where the solver picks the measurement quality itself and the scenario is
   * decoded back out of the assignment afterwards.
   */
  private record Copy(Scenario scenario, QueryExpr core, TranslationContext context) {}

  /**
   * Headless convenience: reconstructs (on SAT) into a throwaway {@link Session}/system. Every
   * unc-modelvalidator test and Task 3.8b's differential test use this form.
   */
  public static ModelFinderResult find(MModel model, AnalysisConfiguration config)
      throws UseApiException {
    return run(null, model, config, null);
  }

  /**
   * The incumbent invariant-independence check, reproduced over the query algebra: activate the
   * configured invariant set, then solve one targeted {@code counterexample(j)} obligation per
   * active invariant {@code j} -- exactly what {@code kk-modelvalidator}'s {@code
   * InvariantIndepChecker} does by negating one invariant at a time. An entry is satisfiable
   * precisely when {@code j} is independent of the others, and its witness is already attributed to
   * {@code j} alone by the same oracle every other query result goes through. All solves share one
   * persistent solver process, since the sweep is by construction many solves of one model.
   */
  public static Map<String, ModelFinderResult> independenceSweep(
      MModel model, AnalysisConfiguration config) throws UseApiException {
    QueryExpr requested = config.query();
    if (requested instanceof QueryExpr.Profiled profiled) {
      // The parser already refuses `cover invariant-independence`; this catches a caller that
      // built the AST directly. A sweep is many solves, so a profile over it would have to mean
      // a profile over each entry -- a shape version 1 does not define.
      if (profiled.profile() != ScenarioProfile.EXISTS) {
        throw new IllegalArgumentException(
            "invariant-independence is a sweep of one solve per active invariant and cannot"
                + " carry the scenario profile "
                + profiled.profile());
      }
      requested = profiled.expression();
    }
    if (!(requested instanceof QueryExpr.InvariantIndependence)) {
      throw new IllegalArgumentException(
          "independenceSweep requires the invariant-independence query, got: " + config.query());
    }
    Map<String, ModelFinderResult> sweep = new LinkedHashMap<>();
    try (SolverProcess shared =
        SolverProcess.persistent(SolverBinary.resolve(), config.timeout())) {
      for (MClassInvariant invariant : model.classInvariants(true)) {
        String name = invariant.qualifiedName();
        if (!config.activeInvariants().contains(name)) {
          continue;
        }
        AnalysisConfiguration targeted =
            new AnalysisConfiguration(
                config.classScopes(),
                config.associationScopes(),
                config.attributeDomains(),
                config.activeInvariants(),
                new QueryExpr.Profiled(ScenarioProfile.EXISTS, new QueryExpr.Counterexample(name)),
                config.timeout(),
                config.modelLimit());
        sweep.put(name, find(model, targeted, shared));
      }
    }
    return sweep;
  }

  /**
   * Headless, reusing an externally-managed {@link SolverProcess} across many calls instead of
   * spawning a fresh solver process per call -- the form a caller doing many solves in one run
   * (like {@code BenchmarkRunner}) should use; see {@link SolverProcess#persistent} for why. The
   * caller owns the given {@code SolverProcess}'s lifecycle (construct it once via {@link
   * SolverProcess#persistent}, {@link SolverProcess#close} it when the whole run is done); this
   * method neither constructs nor closes one itself.
   */
  public static ModelFinderResult find(
      MModel model, AnalysisConfiguration config, SolverProcess solverProcess)
      throws UseApiException {
    return run(null, model, config, solverProcess);
  }

  /**
   * Reconstructs (on SAT) into the given {@link Session}'s own system instead of a throwaway one --
   * the form a live GUI plugin action must use, so the result becomes visible as "the current
   * session" rather than a system nothing is looking at.
   *
   * <p>A session holds ONE state, so when a profile delivers several snapshots only the FIRST
   * witnessed scenario is reconstructed into the session; the remaining scenarios are reconstructed
   * into throwaway systems and are still independently checked and reported. Nothing is skipped --
   * only what the GUI ends up displaying is chosen.
   */
  public static ModelFinderResult find(Session session, MModel model, AnalysisConfiguration config)
      throws UseApiException {
    return run(session, model, config, null);
  }

  /**
   * Applies the requested SCENARIO PROFILE, which is orthogonal to the witness predicate {@code
   * W_Q} the query names -- the reported mode is a pair such as {@code SATISFY/EXISTS} or {@code
   * FRAGILE/COVER}. Straight from the proposal's equations:
   *
   * <pre>
   *   EXISTS(Q)  = exists s exists S      : W_Q(S,s)
   *   COVER(Q)   = forall s exists S_s    : W_Q(S_s,s)     -- the snapshot may differ per scenario
   *   UNIFORM(Q) = exists S forall s      : W_Q(S,s)       -- ONE shared snapshot
   * </pre>
   *
   * Each is a genuinely different solve, and never each other's fallback:
   *
   * <ul>
   *   <li>EXISTS leaves the measurement quality FREE inside its configured domain, so the solver
   *       chooses {@code s} along with {@code S}. The chosen scenario is decoded back out of the
   *       assignment for the report.
   *   <li>COVER runs ONE INDEPENDENT SOLVE PER SCENARIO with that scenario's uncertainty pinned, so
   *       each scenario may answer with its own snapshot. Every scenario is solved even after one
   *       is refuted, because the definition of done requires the evidence to identify every
   *       scenario.
   *   <li>UNIFORM runs ONE SOLVE carrying every scenario at once: the object, link and
   *       REPRESENTATIVE symbols are declared once and shared, while each scenario gets its own
   *       pinned uncertainty symbols and its own reified {@code def}/{@code val} pair. Solving the
   *       first scenario and reporting UNIFORM would be EXISTS wearing a different label.
   * </ul>
   */
  private static ModelFinderResult run(
      Session session, MModel model, AnalysisConfiguration config, SolverProcess solverProcess)
      throws UseApiException {
    ScenarioProfile profile = profileOf(config.query());
    if (profile == ScenarioProfile.EXISTS) {
      Solved solved = solve(model, config, solverProcess, null);
      if (solved.outcome() != SolverOutcome.SAT) {
        return new ModelFinderResult(
            solved.ledger(),
            profile,
            aggregate(List.of(scenarioOutcomeOf(solved))),
            List.of(),
            BoundedCompletenessQualification.of(config, profile, null));
      }
      Copy copy = solved.copies().get(0);
      Scenario chosen = decodeScenario(copy.context(), solved.modelValues());
      ScenarioReport report = witness(session, model, solved, copy, chosen, true);
      return new ModelFinderResult(
          solved.ledger(),
          profile,
          ProfileOutcome.SATISFIED,
          List.of(report),
          BoundedCompletenessQualification.of(config, profile, null));
    }

    List<Scenario> space = scenarioSpace(model, config, profile);
    if (profile == ScenarioProfile.UNIFORM) {
      Solved solved = solve(model, config, solverProcess, space);
      if (solved.outcome() != SolverOutcome.SAT) {
        // The refutation is JOINT over the whole scenario set -- "no ONE snapshot works for all of
        // them" -- so it is not attributable to any single scenario, and every scenario is
        // reported with the same status rather than one being blamed.
        ScenarioOutcome each = scenarioOutcomeOf(solved);
        List<ScenarioReport> reports =
            space.stream().map(s -> ScenarioReport.unwitnessed(s, each)).toList();
        return new ModelFinderResult(
            solved.ledger(),
            profile,
            aggregate(List.of(each)),
            reports,
            BoundedCompletenessQualification.of(config, profile, space));
      }
      List<ScenarioReport> reports = new ArrayList<>();
      boolean first = true;
      for (Copy copy : solved.copies()) {
        // One reconstruction and one INDEPENDENT USE check per scenario, over the same shared
        // snapshot symbols. Checking a UNIFORM witness once is the silent degradation this
        // milestone exists to prevent.
        reports.add(witness(session, model, solved, copy, copy.scenario(), first));
        first = false;
      }
      return new ModelFinderResult(
          solved.ledger(),
          profile,
          ProfileOutcome.SATISFIED,
          reports,
          BoundedCompletenessQualification.of(config, profile, space));
    }

    List<ScenarioReport> reports = new ArrayList<>();
    List<ScenarioOutcome> outcomes = new ArrayList<>();
    FragmentCoverageLedger ledger = null;
    boolean firstWitness = true;
    for (Scenario scenario : space) {
      Solved solved = solve(model, config, solverProcess, List.of(scenario));
      ledger = solved.ledger();
      if (solved.outcome() != SolverOutcome.SAT) {
        ScenarioOutcome outcome = scenarioOutcomeOf(solved);
        outcomes.add(outcome);
        reports.add(ScenarioReport.unwitnessed(scenario, outcome));
        continue;
      }
      reports.add(witness(session, model, solved, solved.copies().get(0), scenario, firstWitness));
      firstWitness = false;
      outcomes.add(ScenarioOutcome.WITNESSED);
    }
    return new ModelFinderResult(
        ledger,
        profile,
        aggregate(outcomes),
        reports,
        BoundedCompletenessQualification.of(config, profile, space));
  }

  /**
   * Reconstructs one scenario's snapshot and holds it to the query core with the independent USE
   * oracle. Every profile funnels through here, so no profile can deliver an unchecked snapshot.
   */
  private static ScenarioReport witness(
      Session session,
      MModel model,
      Solved solved,
      Copy copy,
      Scenario scenario,
      boolean intoSession)
      throws UseApiException {
    MSystem system =
        session != null && intoSession
            ? SystemStateReconstructor.reconstruct(
                session, model, copy.context(), solved.modelValues())
            : SystemStateReconstructor.reconstruct(model, copy.context(), solved.modelValues());
    return new ScenarioReport(
        scenario, ScenarioOutcome.WITNESSED, system, checkedVerdicts(model, copy.core(), system));
  }

  /**
   * Holds the delivered witness to the query it claims, in EVERY mode that query mentions.
   *
   * <p>{@link InvariantReEvaluator#reevaluate(MModel, MSystem)} drives the real USE evaluator over
   * the reconstructed state, which is the U-AWARE reading. Milestone 4.5 adds the second mode:
   * {@code NominalErasureEvaluator} reads the same reconstructed snapshot with every uncertainty
   * erased, so a {@code nominal} atom is now independently checked rather than refused. Only the
   * invariants the query actually classifies in NOMINAL mode are asked for -- an invariant with no
   * type-directed erasure rule is UNSUPPORTED, and refusing one the query never mentioned would be
   * a fabricated failure.
   *
   * <p>{@link QueryWitnessChecker} still reads ONLY these independent verdicts, never the solver's
   * own {@code def}/{@code val} assignment, and an atom whose (invariant, mode) has no verdict is a
   * hard refusal there. That is what replaces the pre-4.5 gate: the check is no longer "is a
   * nominal oracle available at all" but "did the nominal oracle actually report on this atom".
   *
   * <p>Milestone 4.6 changes only how OFTEN this runs: once per delivered snapshot for COVER, and
   * once per SCENARIO for UNIFORM -- the same objects, links and representative values each time,
   * with that scenario's uncertainty substituted into the reconstructed U-values.
   */
  private static Map<TranslationMode, List<InvariantVerdict>> checkedVerdicts(
      MModel model, QueryExpr core, MSystem system) {
    Map<TranslationMode, List<InvariantVerdict>> observed = new LinkedHashMap<>();
    observed.put(TranslationMode.UNCERTAIN, InvariantReEvaluator.reevaluate(model, system));
    Set<String> nominalTargets = nominalTargetsOf(core);
    if (!nominalTargets.isEmpty()) {
      observed.put(
          TranslationMode.NOMINAL,
          InvariantReEvaluator.reevaluate(model, system, TranslationMode.NOMINAL, nominalTargets));
    }
    QueryWitnessChecker.requireQuerySatisfied(core, observed);
    return observed;
  }

  /**
   * The invariants a compiled core classifies in NOMINAL mode. The core is already desugared, so it
   * holds no aggregate or macro node and the active-invariant set it would otherwise expand over is
   * irrelevant here -- which is why the empty set is the right argument, not an oversight.
   */
  private static Set<String> nominalTargetsOf(QueryExpr core) {
    Set<String> targets = new java.util.LinkedHashSet<>();
    QueryRequirements.requiredClassifications(core, Set.of())
        .forEach(
            (name, modes) -> {
              if (modes.contains(TranslationMode.NOMINAL)) {
                targets.add(name);
              }
            });
    return targets;
  }

  private static ScenarioProfile profileOf(QueryExpr query) {
    return query instanceof QueryExpr.Profiled profiled
        ? profiled.profile()
        : ScenarioProfile.EXISTS;
  }

  private static ScenarioOutcome scenarioOutcomeOf(Solved solved) {
    return solved.outcome() == SolverOutcome.UNSAT
        ? ScenarioOutcome.REFUTED
        : ScenarioOutcome.UNRESOLVED;
  }

  /**
   * The proposal's own aggregation rule for a profile that quantifies over scenarios: "success
   * means every scenario has a checked witness; one qualified UNSAT scenario refutes coverage,
   * while any unresolved scenario makes the aggregate result UNKNOWN/PARTIAL." A refutation
   * outranks an unresolved scenario because one UNSAT already settles the universal claim.
   */
  private static ProfileOutcome aggregate(List<ScenarioOutcome> outcomes) {
    if (outcomes.contains(ScenarioOutcome.REFUTED)) {
      return ProfileOutcome.REFUTED;
    }
    return outcomes.contains(ScenarioOutcome.UNRESOLVED)
        ? ProfileOutcome.PARTIAL
        : ProfileOutcome.SATISFIED;
  }

  /** {@code Sigma_K} for the configuration, refused rather than sampled when it is not finite. */
  private static List<Scenario> scenarioSpace(
      MModel model, AnalysisConfiguration config, ScenarioProfile profile) {
    List<ScenarioSpace.UncertainAttribute> attributes = new ArrayList<>();
    for (Map.Entry<String, Map<String, AttributeDomain>> entry :
        componentDomains(config).entrySet()) {
      AttributeDomain uncertainty = entry.getValue().get("uncertainty");
      if (uncertainty == null) {
        continue;
      }
      for (String className : owningClasses(model, config, uncertainty)) {
        attributes.add(
            new ScenarioSpace.UncertainAttribute(
                className,
                uncertainty.attributeName(),
                capacityOf(config, className),
                uncertainty));
      }
    }
    return ScenarioSpace.enumerate(attributes, profile);
  }

  /** The component domains of every attribute the configuration gives components for. */
  private static Map<String, Map<String, AttributeDomain>> componentDomains(
      AnalysisConfiguration config) {
    Map<String, Map<String, AttributeDomain>> byAttribute = new LinkedHashMap<>();
    for (AttributeDomain domain : config.attributeDomains()) {
      if (domain.component() != null && !domain.className().isEmpty()) {
        byAttribute
            .computeIfAbsent(
                domain.className() + "." + domain.attributeName(), ignored -> new LinkedHashMap<>())
            .put(domain.component(), domain);
      }
    }
    return byAttribute;
  }

  /**
   * Every concrete class whose slots actually carry this attribute -- the declaring class plus each
   * descendant with a configured scope of its own. This mirrors {@link #registerUTypeAttribute}'s
   * own inheritance walk exactly, because a scenario must bind precisely the slots that were
   * encoded: one axis too few leaves an uncertainty symbol free inside a universally quantified
   * profile, and one too many pins a symbol that does not exist.
   */
  private static List<String> owningClasses(
      MModel model, AnalysisConfiguration config, AttributeDomain domain) {
    Set<String> explicitlyDeclared = new HashSet<>();
    for (AttributeDomain other : config.attributeDomains()) {
      if (!other.className().isEmpty()) {
        explicitlyDeclared.add(other.className() + "." + other.attributeName());
      }
    }
    Set<String> scoped = new HashSet<>();
    config.classScopes().forEach(scope -> scoped.add(scope.className()));

    List<String> owners = new ArrayList<>();
    if (!scoped.contains(domain.className())) {
      throw new IllegalArgumentException(
          "attribute domain '"
              + domain.className()
              + "."
              + domain.attributeName()
              + "' names a class with no configured scope");
    }
    owners.add(domain.className());
    for (MClassifier descendant : model.getClass(domain.className()).allChildren()) {
      String key = descendant.name() + "." + domain.attributeName();
      if (!explicitlyDeclared.contains(key) && scoped.contains(descendant.name())) {
        owners.add(descendant.name());
      }
    }
    return owners;
  }

  /**
   * The declaring class plus every descendant that has a configured scope and does not redeclare
   * the attribute -- the same inheritance walk the paired U-types and the crisp attributes already
   * do, so a {@code UBoolean} or {@code UString} attribute is reachable on a subclass instance
   * exactly as they are. Shared by the two families that carry no scenario-quantifiable measurement
   * quality and are therefore registered once rather than per scenario copy.
   */
  private static List<String> nonScenarioUTypeOwners(
      MClass declaring, String attributeName, Set<String> explicitlyDeclaredAttributes) {
    List<String> owners = new ArrayList<>();
    owners.add(declaring.name());
    for (MClassifier descendant : declaring.allChildren()) {
      if (!explicitlyDeclaredAttributes.contains(descendant.name() + "." + attributeName)) {
        owners.add(descendant.name());
      }
    }
    return owners;
  }

  private static int capacityOf(AnalysisConfiguration config, String className) {
    return config.classScopes().stream()
        .filter(scope -> scope.className().equals(className))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("no configured scope for class '" + className + "'"))
        .max();
  }

  /**
   * The scenario the solver itself chose, read back out of a free-uncertainty EXISTS assignment.
   *
   * <p>Only LIVE slots are reported: a dead slot's component is left unconstrained by the
   * existence-guarded domain encoding, so reporting it would be reporting noise. COVER and UNIFORM
   * instead pin every candidate slot, because liveness is not known before solving.
   */
  private static Scenario decodeScenario(
      TranslationContext context, Map<String, SmtValue> modelValues) {
    List<Scenario.Binding> bindings = new ArrayList<>();
    for (AttributeValues values : context.attributes().values()) {
      if (!values.type().isPairedUType()) {
        continue;
      }
      ObjectSlots slots = context.slotsFor(values.className());
      for (int i = 0; i < values.uncertaintyNames().size(); i++) {
        SmtValue alive = modelValues.get(slots.existsNames().get(i));
        if (!(alive instanceof SmtValue.Bool bool) || !bool.value()) {
          continue;
        }
        SmtValue uncertainty = modelValues.get(values.uncertaintyNames().get(i));
        if (uncertainty == null) {
          continue;
        }
        bindings.add(
            new Scenario.Binding(
                values.className(),
                values.attributeName(),
                i,
                "uncertainty",
                SmtValueDecoder.decodeReal(uncertainty)));
      }
    }
    return new Scenario(0, bindings);
  }

  /**
   * Encodes and solves ONE script.
   *
   * @param scenarios null for the free-uncertainty encoding EXISTS uses (byte-identical to every
   *     pre-4.6 run), or the scenario copies to carry in this one script. A singleton list is one
   *     COVER obligation; the full list is UNIFORM, whose copies deliberately SHARE the object,
   *     link and representative symbols and differ only in their pinned uncertainty symbols and the
   *     {@code def}/{@code val} pair computed from them.
   */
  /**
   * Refuses an association whose link extent is not its own to search.
   *
   * <p>{@link org.tzi.use.smt.encode.AssociationLinkEncoder} gives every association an INDEPENDENT
   * grid of link booleans, constrained only by its own two end multiplicities and its own
   * configured link count. That is sound exactly while the association's extent is a free variable.
   * A {@code union} end, a {@code subsets} end and a {@code redefines} end all break that: the
   * association's links are then determined by -- or constrained against -- ANOTHER association's
   * links, and an independent grid silently drops the relationship.
   *
   * <p>This gate exists because predefined links made those models reachable for the first time.
   * {@code benchmark/examples/Subsets} is the concrete case: {@code ab}'s ends are {@code union},
   * its extent is the union of {@code cd} and {@code ef}, and its configuration asks for {@code
   * ab_min = ab_max = 2} while giving {@code A} and {@code B} zero objects of their own. An
   * independent 0x0 grid cannot hold two links, so the encoding reported a confident UNSATISFIABLE
   * against the incumbent's SATISFIABLE -- a wrong verdict, not a refusal. Refusing is the honest
   * outcome until union/subsets/redefines are encoded (THESIS_SMT_MODEL_FINDER_PLAN.md 7.1 Tier 3).
   *
   * <p>DERIVED association ends are a DIFFERENT gap and are deliberately not covered here: no
   * corpus row reaches one without first being refused at the invariant level, so gating them would
   * move ledger attribution around without closing a reachable soundness hole. They remain recorded
   * as unsupported in the feature matrix ({@code assoc.derived-binary}).
   */
  private static void requireIndependentlySearchableExtent(
      MAssociation association, List<MAssociationEnd> ends) {
    for (MAssociationEnd end : ends) {
      String feature = null;
      if (end.isUnion()) {
        feature = "a 'union' end (" + end.name() + ")";
      } else if (!end.getSubsettedEnds().isEmpty() || !end.getSubsettingEnds().isEmpty()) {
        feature = "a 'subsets' relationship on end " + end.name();
      } else if (!end.getRedefinedEnds().isEmpty() || !end.getRedefiningEnds().isEmpty()) {
        feature = "a 'redefines' relationship on end " + end.name();
      }
      if (feature != null) {
        throw new SmtTranslationException(
            FragmentBoundary.TIER_3,
            "association '"
                + association.name()
                + "' declares "
                + feature
                + ", so its link extent is tied to another association's; this translation gives"
                + " every association an independent link grid and would silently drop that"
                + " relationship, so it is refused rather than approximated");
      }
    }
  }

  private static Solved solve(
      MModel model,
      AnalysisConfiguration config,
      SolverProcess externalSolverProcess,
      List<Scenario> scenarios) {
    SmtScript script = new SmtScript("QF_LIRA");
    List<Map<String, AttributeValues>> scenarioAttributes = new ArrayList<>();
    if (scenarios != null) {
      for (int i = 0; i < scenarios.size(); i++) {
        scenarioAttributes.add(new LinkedHashMap<>());
      }
    }

    Map<String, ObjectSlots> slotsByClass = ObjectSlotEncoder.encode(script, config.classScopes());

    Set<String> explicitlyDeclaredAttributes = new HashSet<>();
    for (AttributeDomain domain : config.attributeDomains()) {
      if (!domain.className().isEmpty()) {
        explicitlyDeclaredAttributes.add(domain.className() + "." + domain.attributeName());
      }
    }

    Map<String, Map<String, AttributeDomain>> componentDomainsByAttribute = new LinkedHashMap<>();
    for (AttributeDomain domain : config.attributeDomains()) {
      if (domain.component() != null && !domain.className().isEmpty()) {
        String key = domain.className() + "." + domain.attributeName();
        componentDomainsByAttribute
            .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
            .put(domain.component(), domain);
      }
    }

    Map<String, AttributeValues> attributeValuesByKey = new LinkedHashMap<>();
    Map<String, AttributeDomain> attributeDomainByKey = new LinkedHashMap<>();
    for (AttributeDomain domain : config.attributeDomains()) {
      if (domain.component() != null || domain.className().isEmpty()) {
        // UReal components are grouped below; primitive-wide fallback domains remain out of scope.
        continue;
      }
      ObjectSlots owner = slotsByClass.get(domain.className());
      if (owner == null) {
        throw new IllegalArgumentException(
            "attribute domain '"
                + domain.className()
                + "."
                + domain.attributeName()
                + "' names a class with no configured scope");
      }
      MClass cls = model.getClass(domain.className());
      MAttribute attribute = cls.attribute(domain.attributeName(), true);
      AttributeType type = attributeTypeOf(attribute.type());
      AttributeValues values =
          AttributeEncoder.encode(script, owner, domain.attributeName(), type, domain);
      String key = domain.className() + "." + domain.attributeName();
      attributeValuesByKey.put(key, values);
      attributeDomainByKey.put(key, domain);

      // An attribute declared on a superclass is also accessible -- with this SAME domain -- on
      // every subclass's own instances (Vehicle.wheels is reachable as both v.wheels for a direct
      // Vehicle and c.wheels for a Car), so each subclass with a configured scope of its own gets
      // its own independently-encoded values here too, keyed by its own concrete class name (see
      // PolymorphicRange, which is what later binds a loop/context variable to those slots). A
      // subclass that redeclares the same attribute name itself (a redefinition) keeps its own
      // explicit domain instead -- not yet a real scenario in this translation slice, but cheap to
      // not silently clobber.
      for (MClassifier descendant : cls.allChildren()) {
        String descendantKey = descendant.name() + "." + domain.attributeName();
        if (explicitlyDeclaredAttributes.contains(descendantKey)) {
          continue;
        }
        ObjectSlots descendantOwner = slotsByClass.get(descendant.name());
        if (descendantOwner == null) {
          continue;
        }
        AttributeValues descendantValues =
            AttributeEncoder.encode(script, descendantOwner, domain.attributeName(), type, domain);
        attributeValuesByKey.put(descendantKey, descendantValues);
        attributeDomainByKey.put(descendantKey, domain);
      }
    }

    for (Map.Entry<String, Map<String, AttributeDomain>> entry :
        componentDomainsByAttribute.entrySet()) {
      Map<String, AttributeDomain> components = entry.getValue();
      AttributeDomain representative = components.values().iterator().next();
      String className = representative.className();
      String attributeName = representative.attributeName();
      MClass cls = model.getClass(className);
      MAttribute attribute = cls.attribute(attributeName, true);
      if (!attribute.type().isTypeOfUReal()
          && !attribute.type().isTypeOfUInteger()
          && !attribute.type().isTypeOfUBoolean()
          && !attribute.type().isTypeOfUString()) {
        throw new IllegalArgumentException(
            "component domains are only supported for U-typed attributes (UReal, UInteger,"
                + " UBoolean, UString), got "
                + className
                + "."
                + attributeName
                + " : "
                + attribute.type());
      }
      AttributeType uType = attributeTypeOf(attribute.type());
      if (uType == AttributeType.USTRING) {
        // The fourth family carries a SPELLING and a CONFIDENCE. Like UBoolean and unlike the two
        // paired numeric families it has no measurement quality for a scenario profile to quantify
        // over -- a confidence is a property of the reading itself, not of the instrument -- so
        // both components belong to the snapshot S and are registered once, SHARED by every
        // scenario copy rather than duplicated per copy.
        if (!components.keySet().equals(Set.of("value", "confidence"))) {
          throw new IllegalArgumentException(
              "USTRING attribute '"
                  + className
                  + "."
                  + attributeName
                  + "' requires exactly value and confidence component domains, got "
                  + components.keySet());
        }
        AttributeDomain spellingDomain = components.get("value");
        AttributeDomain confidenceDomain = components.get("confidence");
        for (String owningClass :
            nonScenarioUTypeOwners(cls, attributeName, explicitlyDeclaredAttributes)) {
          ObjectSlots uStringOwner = slotsByClass.get(owningClass);
          if (uStringOwner == null) {
            if (owningClass.equals(className)) {
              throw new IllegalArgumentException(
                  "attribute domain '"
                      + className
                      + "."
                      + attributeName
                      + "' names a class with no configured scope");
            }
            continue;
          }
          String uStringKey = owningClass + "." + attributeName;
          attributeValuesByKey.put(
              uStringKey,
              AttributeEncoder.encodeUString(
                  script, uStringOwner, attributeName, spellingDomain, confidenceDomain));
          // The SPELLING domain is what reconstruction reads the index against, so it is the one
          // registered under the bare key SystemStateReconstructor looks up.
          attributeDomainByKey.put(uStringKey, spellingDomain);
          attributeDomainByKey.put(uStringKey + ".value", spellingDomain);
          attributeDomainByKey.put(uStringKey + ".confidence", confidenceDomain);
        }
        continue;
      }
      if (uType == AttributeType.UBOOLEAN) {
        // The third family is canonicalised to ONE probability, so it has a single component and
        // no measurement quality for a scenario profile to quantify over. Its probability belongs
        // to the snapshot S, which is why it is registered once into attributeValuesByKey and
        // therefore SHARED by every scenario copy rather than duplicated per copy.
        if (!components.keySet().equals(Set.of("probability"))) {
          throw new IllegalArgumentException(
              "UBOOLEAN attribute '"
                  + className
                  + "."
                  + attributeName
                  + "' requires exactly a probability component domain, got "
                  + components.keySet());
        }
        AttributeDomain probabilityDomain = components.get("probability");
        for (String owningClass :
            nonScenarioUTypeOwners(cls, attributeName, explicitlyDeclaredAttributes)) {
          ObjectSlots uBooleanOwner = slotsByClass.get(owningClass);
          if (uBooleanOwner == null) {
            if (owningClass.equals(className)) {
              throw new IllegalArgumentException(
                  "attribute domain '"
                      + className
                      + "."
                      + attributeName
                      + "' names a class with no configured scope");
            }
            continue;
          }
          String uBooleanKey = owningClass + "." + attributeName;
          attributeValuesByKey.put(
              uBooleanKey,
              AttributeEncoder.encodeUBoolean(
                  script, uBooleanOwner, attributeName, probabilityDomain));
          attributeDomainByKey.put(uBooleanKey, probabilityDomain);
          attributeDomainByKey.put(uBooleanKey + ".probability", probabilityDomain);
        }
        continue;
      }
      if (!components.keySet().equals(Set.of("value", "uncertainty"))) {
        throw new IllegalArgumentException(
            uType
                + " attribute '"
                + className
                + "."
                + attributeName
                + "' requires exactly value and uncertainty component domains, got "
                + components.keySet());
      }
      AttributeDomain valueDomain = components.get("value");
      AttributeDomain uncertaintyDomain = components.get("uncertainty");
      ObjectSlots owner = slotsByClass.get(className);
      if (owner == null) {
        throw new IllegalArgumentException(
            "attribute domain '"
                + className
                + "."
                + attributeName
                + "' names a class with no configured scope");
      }
      registerUTypeAttribute(
          script,
          owner,
          attributeName,
          uType,
          valueDomain,
          uncertaintyDomain,
          attributeValuesByKey,
          attributeDomainByKey,
          scenarios,
          scenarioAttributes);

      for (MClassifier descendant : cls.allChildren()) {
        String descendantKey = descendant.name() + "." + attributeName;
        if (explicitlyDeclaredAttributes.contains(descendantKey)) {
          continue;
        }
        ObjectSlots descendantOwner = slotsByClass.get(descendant.name());
        if (descendantOwner == null) {
          continue;
        }
        registerUTypeAttribute(
            script,
            descendantOwner,
            attributeName,
            uType,
            valueDomain,
            uncertaintyDomain,
            attributeValuesByKey,
            attributeDomainByKey,
            scenarios,
            scenarioAttributes);
      }
    }

    registerTypeWideFallbackAttributes(
        script, model, config, slotsByClass, attributeValuesByKey, attributeDomainByKey);

    Map<String, AssociationLinks> linksByAssociation = new LinkedHashMap<>();
    for (AssociationScope scope : config.associationScopes()) {
      MAssociation association = model.getAssociation(scope.associationName());
      List<MAssociationEnd> ends = association.associationEnds();
      if (ends.size() != 2) {
        throw new IllegalArgumentException(
            "association '"
                + scope.associationName()
                + "' does not have exactly two ends; not yet supported");
      }
      requireIndependentlySearchableExtent(association, ends);
      ObjectSlots aEnd = slotsByClass.get(ends.get(0).cls().name());
      ObjectSlots bEnd = slotsByClass.get(ends.get(1).cls().name());
      if (aEnd == null || bEnd == null) {
        throw new IllegalArgumentException(
            "association '"
                + scope.associationName()
                + "' references a class with no configured scope");
      }
      AssociationLinks links =
          AssociationLinkEncoder.encode(
              script,
              scope.associationName(),
              aEnd,
              toMultiplicity(ends.get(0).multiplicity()),
              bEnd,
              toMultiplicity(ends.get(1).multiplicity()),
              scope);
      // The tuples a bare `AssociationName` key configured are FORCED links, so they are asserted
      // straight after the grid they live in. The declared end classes travel with them: see
      // PredefinedLinkEncoder for why the grid's own axes must not be assumed to match them.
      PredefinedLinkEncoder.encode(
          script, scope, links, List.of(ends.get(0).cls().name(), ends.get(1).cls().name()));
      linksByAssociation.put(scope.associationName(), links);
    }

    Map<String, Set<TranslationMode>> requirements =
        QueryRequirements.requiredClassifications(config.query(), config.activeInvariants());
    List<MClassInvariant> requestedInvariants = new ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      if (requirements.containsKey(invariant.qualifiedName())) {
        requestedInvariants.add(invariant);
      }
    }
    Set<String> resolvedInvariantNames =
        requestedInvariants.stream()
            .map(MClassInvariant::qualifiedName)
            .collect(java.util.stream.Collectors.toSet());
    if (!resolvedInvariantNames.equals(requirements.keySet())) {
      Set<String> missing = new HashSet<>(requirements.keySet());
      missing.removeAll(resolvedInvariantNames);
      throw new IllegalArgumentException("query names invariant(s) absent from model: " + missing);
    }

    // ONE desugaring, reused by every scenario copy. That is what fixes a targeted
    // COUNTEREXAMPLE/FRAGILE invariant OUTSIDE the scenario quantifiers: each copy lowers the
    // identical atom, so two scenarios cannot end up diagnosing different invariants.
    QueryExpr core = QueryCompiler.desugared(config.query(), config.activeInvariants());
    QueryCompiler.requireTargetDeterminate(core, profileOf(config.query()));

    List<Copy> copies = new ArrayList<>();
    FragmentCoverageLedger ledger = null;
    int copyCount = scenarios == null ? 1 : scenarios.size();
    for (int i = 0; i < copyCount; i++) {
      Map<String, AttributeValues> attributes = new LinkedHashMap<>(attributeValuesByKey);
      if (scenarios != null) {
        attributes.putAll(scenarioAttributes.get(i));
      }
      TranslationContext context =
          new TranslationContext(
              Map.of(), attributes, attributeDomainByKey, slotsByClass, linksByAssociation);
      FragmentChecker.ReifiedResult checked =
          FragmentChecker.checkAndReify(
              requestedInvariants,
              requirements,
              context,
              script,
              scenarios == null ? "" : scenarioSuffix(scenarios.get(i)));
      // Two different fail-closed gates, in this order and both BEFORE any solver call: nothing
      // the query needs was MISSED, then nothing it needs was REFUSED. Only the second is visible
      // to `allSupported()` -- an omitted pair produces no entry at all and is vacuously "all
      // supported", which is exactly the silent hole Milestone 4.7's definition of done names.
      checked.ledger().requireAccountedFor(requirements);
      checked.ledger().requireAllSupported();
      if (ledger == null) {
        ledger = checked.ledger();
      }
      script.assertThat(QueryCompiler.lowerCore(core, checked.classifications()).constraint());
      copies.add(new Copy(scenarios == null ? null : scenarios.get(i), core, context));
    }

    SolverProcess solverProcess =
        externalSolverProcess != null
            ? externalSolverProcess
            : new SolverProcess(SolverBinary.resolve(), config.timeout());
    SolverResult result = solverProcess.run(script.toSmtLib());
    if (result.outcome() != SolverOutcome.SAT) {
      return new Solved(result.outcome(), ledger, copies, Map.of());
    }
    return new Solved(SolverOutcome.SAT, ledger, copies, SmtModelParser.parse(result.modelText()));
  }

  /**
   * Applies the PRIMITIVE-TYPE-WIDE fallback domain: {@code Integer_min}/{@code Integer_max} and
   * {@code Real_min}/{@code Real_max} bound every attribute of that type the configuration gives no
   * per-attribute domain of its own. This is the other half of the domain records the reader has
   * produced since Phase 2 (the U-type component half was consumed in Phase 5), and it is a
   * CONFIGURATION-defaults gap, not an OCL-fragment one: without it, an attribute bounded the way
   * the incumbent ordinarily bounds one gets no SMT symbol at all and every invariant reading it is
   * refused with {@code ENCODING_SCOPE}.
   *
   * <p>PRECEDENCE is the incumbent's, verified rather than assumed. {@code kk-modelvalidator}'s
   * {@code AttributeConfigurator.upperBound} branches on {@code domainValues.isEmpty()} (line 117):
   * with a per-attribute domain it uses those values ALONE (lines 168-181) and never consults the
   * type's range; only without one does it fall back to the type's own bound, filtered to the
   * type's configured range for Integer (lines 129-164). So the per-attribute domain WINS OUTRIGHT
   * and is never intersected with the type-wide range -- which is why this method skips every
   * attribute already registered above, rather than merging bounds into it.
   *
   * <p>Deliberately NOT applied, each still failing closed:
   *
   * <ul>
   *   <li><b>String.</b> {@code String_min}/{@code String_max} are not string bounds. {@code
   *       StringConfigurator} reads only {@code ranges.get(0).getUpper()} and uses it as a COUNT of
   *       string atoms, padding the universe with GENERATED placeholder spellings ({@code
   *       type.name() + "_string" + i}, lines 38-44, 57-60 and 77-87); {@code String_min} is never
   *       read at all. There is no counterpart to a generated spelling here, so the reader refuses
   *       those two keys outright instead of reinterpreting them as a domain.
   *   <li><b>Enumerations and Boolean.</b> Neither has a type-wide configuration key in the shared
   *       format, so there is nothing here to consume; an enum-typed attribute is not encodable at
   *       all yet ({@link #attributeTypeOf} refuses it) and keeps its {@code ENCODING_SCOPE}
   *       refusal.
   *   <li><b>U-types.</b> Their components are registered above from explicit {@code _value}/{@code
   *       _uncertainty}/{@code _probability}/{@code _confidence} keys; a type-wide primitive range
   *       says nothing about a measurement quality and must not be substituted for one.
   * </ul>
   *
   * <p>A class whose configured scope has capacity 0 yields no symbols and no assertions here, so
   * an attribute on it stays exactly as absent from the script as before.
   */
  private static void registerTypeWideFallbackAttributes(
      SmtScript script,
      MModel model,
      AnalysisConfiguration config,
      Map<String, ObjectSlots> slotsByClass,
      Map<String, AttributeValues> attributeValuesByKey,
      Map<String, AttributeDomain> attributeDomainByKey) {
    Map<String, AttributeDomain> typeWide = new LinkedHashMap<>();
    for (AttributeDomain domain : config.attributeDomains()) {
      if (domain.className().isEmpty() && domain.component() == null) {
        typeWide.put(domain.attributeName(), domain);
      }
    }
    if (typeWide.isEmpty()) {
      return;
    }
    for (Map.Entry<String, ObjectSlots> entry : slotsByClass.entrySet()) {
      String className = entry.getKey();
      MClass cls = model.getClass(className);
      if (cls == null) {
        continue;
      }
      List<MAttribute> attributes = new ArrayList<>(cls.allAttributes());
      attributes.sort(java.util.Comparator.comparing(MAttribute::name));
      for (MAttribute attribute : attributes) {
        String key = className + "." + attribute.name();
        if (attributeValuesByKey.containsKey(key)) {
          continue;
        }
        AttributeType type = fallbackTypeOf(attribute.type());
        if (type == null) {
          continue;
        }
        AttributeDomain source = typeWide.get(type == AttributeType.INTEGER ? "Integer" : "Real");
        if (source == null) {
          continue;
        }
        AttributeDomain fallback =
            new AttributeDomain(
                className,
                attribute.name(),
                null,
                List.of(),
                source.lowerBound(),
                source.upperBound());
        attributeValuesByKey.put(
            key,
            AttributeEncoder.encode(script, entry.getValue(), attribute.name(), type, fallback));
        attributeDomainByKey.put(key, fallback);
      }
    }
  }

  /**
   * The attribute types a primitive-type-wide range can bound, or null for every type that keeps
   * failing closed. Only plain {@code Integer} and {@code Real} qualify; see {@link
   * #registerTypeWideFallbackAttributes} for why String, enumerations, Boolean and the U-types do
   * not.
   */
  private static AttributeType fallbackTypeOf(Type type) {
    if (type.isTypeOfInteger()) {
      return AttributeType.INTEGER;
    }
    if (type.isTypeOfReal()) {
      return AttributeType.REAL;
    }
    return null;
  }

  private static AttributeType attributeTypeOf(Type type) {
    if (type.isTypeOfString()) {
      return AttributeType.STRING;
    }
    if (type.isTypeOfInteger()) {
      return AttributeType.INTEGER;
    }
    if (type.isTypeOfUInteger()) {
      return AttributeType.UINTEGER;
    }
    if (type.isTypeOfUReal()) {
      return AttributeType.UREAL;
    }
    if (type.isTypeOfUBoolean()) {
      return AttributeType.UBOOLEAN;
    }
    if (type.isTypeOfUString()) {
      return AttributeType.USTRING;
    }
    if (type.isTypeOfReal()) {
      return AttributeType.REAL;
    }
    if (type.isTypeOfBoolean()) {
      return AttributeType.BOOLEAN;
    }
    throw new IllegalArgumentException("unsupported attribute type for encoding: " + type);
  }

  /**
   * Registers one U-typed attribute's symbols, for either family.
   *
   * <p>Without scenario copies this is the pre-4.6 encoding, unchanged. With them the split is the
   * whole point of the milestone: the REPRESENTATIVE symbols are declared ONCE and shared by every
   * copy (they belong to the snapshot {@code S}), while each copy gets its own pinned uncertainty
   * symbols (they belong to the scenario {@code s}). Letting each copy allocate its own
   * representatives would quietly turn UNIFORM into COVER.
   *
   * <p>{@code UINTEGER} rides the same path as {@code UREAL} rather than a parallel one: the
   * scenario machinery quantifies over the UNCERTAINTY half, which is a Real in both families, so
   * only the representative's sort differs and {@code AttributeEncoder} already handles that.
   */
  private static void registerUTypeAttribute(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeType type,
      AttributeDomain valueDomain,
      AttributeDomain uncertaintyDomain,
      Map<String, AttributeValues> attributeValuesByKey,
      Map<String, AttributeDomain> attributeDomainByKey,
      List<Scenario> scenarios,
      List<Map<String, AttributeValues>> scenarioAttributes) {
    String key = owner.className() + "." + attributeName;
    attributeDomainByKey.put(key, valueDomain);
    attributeDomainByKey.put(key + ".value", valueDomain);
    attributeDomainByKey.put(key + ".uncertainty", uncertaintyDomain);
    if (scenarios == null) {
      attributeValuesByKey.put(
          key,
          AttributeEncoder.encodeUType(
              script, owner, attributeName, type, valueDomain, uncertaintyDomain));
      return;
    }
    List<String> representatives =
        AttributeEncoder.encodeUTypeRepresentatives(
            script, owner, attributeName, type, valueDomain);
    for (int i = 0; i < scenarios.size(); i++) {
      Scenario scenario = scenarios.get(i);
      List<String> uncertainties =
          AttributeEncoder.encodeUTypeUncertainties(
              script,
              owner,
              attributeName,
              uncertaintyDomain,
              scenarioSuffix(scenario),
              pinnedUncertainties(scenario, owner, attributeName));
      scenarioAttributes
          .get(i)
          .put(
              key,
              new AttributeValues(
                  owner.className(),
                  attributeName,
                  type,
                  representatives,
                  uncertainties,
                  List.of()));
    }
  }

  private static String scenarioSuffix(Scenario scenario) {
    return "_s" + scenario.index();
  }

  /** This scenario's configured measurement quality for every candidate slot, in slot order. */
  private static List<BigDecimal> pinnedUncertainties(
      Scenario scenario, ObjectSlots owner, String attributeName) {
    List<BigDecimal> pinned = new ArrayList<>();
    for (int slot = 0; slot < owner.capacity(); slot++) {
      BigDecimal value = null;
      for (Scenario.Binding binding : scenario.bindings()) {
        if (binding.className().equals(owner.className())
            && binding.attributeName().equals(attributeName)
            && binding.slotIndex() == slot) {
          value = binding.value();
          break;
        }
      }
      if (value == null) {
        throw new IllegalArgumentException(
            "scenario "
                + scenario.label()
                + " fixes no measurement quality for slot "
                + slot
                + " of "
                + owner.className()
                + "."
                + attributeName
                + "; a scenario must bind every potentially live U-type slot");
      }
      pinned.add(value);
    }
    return pinned;
  }

  private static Multiplicity toMultiplicity(MMultiplicity multiplicity) {
    if (multiplicity.getRanges().size() != 1) {
      throw new IllegalArgumentException(
          "multi-range association end multiplicities are not yet supported: " + multiplicity);
    }
    MMultiplicity.Range range = multiplicity.getRanges().get(0);
    return new Multiplicity(range.getLower(), range.getUpper());
  }
}
