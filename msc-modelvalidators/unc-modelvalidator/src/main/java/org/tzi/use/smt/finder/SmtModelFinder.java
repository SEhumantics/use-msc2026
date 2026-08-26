package org.tzi.use.smt.finder;

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
import org.tzi.use.smt.config.ScenarioProfile;
import org.tzi.use.smt.config.TranslationMode;
import org.tzi.use.smt.encode.AssociationLinkEncoder;
import org.tzi.use.smt.encode.AssociationLinks;
import org.tzi.use.smt.encode.AttributeEncoder;
import org.tzi.use.smt.encode.AttributeType;
import org.tzi.use.smt.encode.AttributeValues;
import org.tzi.use.smt.encode.FragmentChecker;
import org.tzi.use.smt.encode.FragmentCoverageLedger;
import org.tzi.use.smt.encode.Multiplicity;
import org.tzi.use.smt.encode.ObjectSlotEncoder;
import org.tzi.use.smt.encode.ObjectSlots;
import org.tzi.use.smt.encode.QueryCompiler;
import org.tzi.use.smt.encode.TranslationContext;
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

  /** Solving is session-independent; only reconstruction (on SAT) needs a target session. */
  private record Solved(
      boolean satisfiable,
      FragmentCoverageLedger ledger,
      QueryCompiler.Obligation obligation,
      TranslationContext context,
      Map<String, SmtValue> modelValues) {}

  /**
   * Headless convenience: reconstructs (on SAT) into a throwaway {@link Session}/system. Every
   * unc-modelvalidator test and Task 3.8b's differential test use this form.
   */
  public static ModelFinderResult find(MModel model, AnalysisConfiguration config)
      throws UseApiException {
    Solved solved = solve(model, config, null);
    if (!solved.satisfiable()) {
      return new ModelFinderResult(false, solved.ledger(), List.of(), null);
    }
    MSystem system =
        SystemStateReconstructor.reconstruct(model, solved.context(), solved.modelValues());
    return finish(model, solved, system);
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
      if (profiled.profile() != ScenarioProfile.EXISTS) {
        throw new IllegalArgumentException(
            "scenario profile "
                + profiled.profile()
                + " has no executable oracle yet; it starts at Milestone 4.6");
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
    Solved solved = solve(model, config, solverProcess);
    if (!solved.satisfiable()) {
      return new ModelFinderResult(false, solved.ledger(), List.of(), null);
    }
    MSystem system =
        SystemStateReconstructor.reconstruct(model, solved.context(), solved.modelValues());
    return finish(model, solved, system);
  }

  /**
   * Reconstructs (on SAT) into the given {@link Session}'s own system instead of a throwaway one --
   * the form a live GUI plugin action must use, so the result becomes visible as "the current
   * session" rather than a system nothing is looking at.
   */
  public static ModelFinderResult find(Session session, MModel model, AnalysisConfiguration config)
      throws UseApiException {
    Solved solved = solve(model, config, null);
    if (!solved.satisfiable()) {
      return new ModelFinderResult(false, solved.ledger(), List.of(), null);
    }
    MSystem system =
        SystemStateReconstructor.reconstruct(
            session, model, solved.context(), solved.modelValues());
    return finish(model, solved, system);
  }

  private static ModelFinderResult finish(MModel model, Solved solved, MSystem system) {
    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, system);
    QueryWitnessChecker.requireExpectedOutcomes(solved.obligation().expectedOutcomes(), verdicts);
    return new ModelFinderResult(true, solved.ledger(), verdicts, system);
  }

  private static Solved solve(
      MModel model, AnalysisConfiguration config, SolverProcess externalSolverProcess) {
    SmtScript script = new SmtScript("QF_LIRA");

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
      if (!attribute.type().isTypeOfUReal()) {
        throw new IllegalArgumentException(
            "component domains are only supported for UReal attributes, got "
                + className
                + "."
                + attributeName
                + " : "
                + attribute.type());
      }
      if (!components.keySet().equals(Set.of("value", "uncertainty"))) {
        throw new IllegalArgumentException(
            "UReal attribute '"
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
      registerURealAttribute(
          script,
          owner,
          attributeName,
          valueDomain,
          uncertaintyDomain,
          attributeValuesByKey,
          attributeDomainByKey);

      for (MClassifier descendant : cls.allChildren()) {
        String descendantKey = descendant.name() + "." + attributeName;
        if (explicitlyDeclaredAttributes.contains(descendantKey)) {
          continue;
        }
        ObjectSlots descendantOwner = slotsByClass.get(descendant.name());
        if (descendantOwner == null) {
          continue;
        }
        registerURealAttribute(
            script,
            descendantOwner,
            attributeName,
            valueDomain,
            uncertaintyDomain,
            attributeValuesByKey,
            attributeDomainByKey);
      }
    }

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
      linksByAssociation.put(scope.associationName(), links);
    }

    TranslationContext context =
        new TranslationContext(
            Map.of(), attributeValuesByKey, attributeDomainByKey, slotsByClass, linksByAssociation);

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

    FragmentChecker.ReifiedResult checked =
        FragmentChecker.checkAndReify(requestedInvariants, requirements, context, script);
    checked.ledger().requireAllSupported();
    QueryCompiler.Obligation obligation =
        QueryCompiler.compile(config.query(), config.activeInvariants(), checked.classifications());
    script.assertThat(obligation.constraint());

    SolverProcess solverProcess =
        externalSolverProcess != null
            ? externalSolverProcess
            : new SolverProcess(SolverBinary.resolve(), config.timeout());
    SolverResult result = solverProcess.run(script.toSmtLib());
    if (result.outcome() != SolverOutcome.SAT) {
      return new Solved(false, checked.ledger(), obligation, null, null);
    }

    Map<String, SmtValue> modelValues = SmtModelParser.parse(result.modelText());
    return new Solved(true, checked.ledger(), obligation, context, modelValues);
  }

  private static AttributeType attributeTypeOf(Type type) {
    if (type.isTypeOfString()) {
      return AttributeType.STRING;
    }
    if (type.isTypeOfInteger()) {
      return AttributeType.INTEGER;
    }
    if (type.isTypeOfUReal()) {
      return AttributeType.UREAL;
    }
    if (type.isTypeOfReal()) {
      return AttributeType.REAL;
    }
    if (type.isTypeOfBoolean()) {
      return AttributeType.BOOLEAN;
    }
    throw new IllegalArgumentException("unsupported attribute type for encoding: " + type);
  }

  private static void registerURealAttribute(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeDomain valueDomain,
      AttributeDomain uncertaintyDomain,
      Map<String, AttributeValues> attributeValuesByKey,
      Map<String, AttributeDomain> attributeDomainByKey) {
    AttributeValues values =
        AttributeEncoder.encodeUReal(script, owner, attributeName, valueDomain, uncertaintyDomain);
    String key = owner.className() + "." + attributeName;
    attributeValuesByKey.put(key, values);
    attributeDomainByKey.put(key, valueDomain);
    attributeDomainByKey.put(key + ".value", valueDomain);
    attributeDomainByKey.put(key + ".uncertainty", uncertaintyDomain);
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
