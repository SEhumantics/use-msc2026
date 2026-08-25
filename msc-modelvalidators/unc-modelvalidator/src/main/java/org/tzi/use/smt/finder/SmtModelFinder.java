package org.tzi.use.smt.finder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.api.UseApiException;
import org.tzi.use.smt.config.AnalysisConfiguration;
import org.tzi.use.smt.config.AssociationScope;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.encode.AssociationLinkEncoder;
import org.tzi.use.smt.encode.AssociationLinks;
import org.tzi.use.smt.encode.AttributeEncoder;
import org.tzi.use.smt.encode.AttributeType;
import org.tzi.use.smt.encode.AttributeValues;
import org.tzi.use.smt.encode.FragmentChecker;
import org.tzi.use.smt.encode.Multiplicity;
import org.tzi.use.smt.encode.ObjectSlotEncoder;
import org.tzi.use.smt.encode.ObjectSlots;
import org.tzi.use.smt.encode.TranslationContext;
import org.tzi.use.smt.reconstruct.SystemStateReconstructor;
import org.tzi.use.smt.solver.SmtModelParser;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtTerm;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.smt.solver.SolverBinary;
import org.tzi.use.smt.solver.SolverOutcome;
import org.tzi.use.smt.solver.SolverProcess;
import org.tzi.use.smt.solver.SolverResult;
import org.tzi.use.smt.verify.InvariantReEvaluator;
import org.tzi.use.smt.verify.InvariantVerdict;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
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

  public static ModelFinderResult find(MModel model, AnalysisConfiguration config)
      throws UseApiException {
    SmtScript script = new SmtScript("QF_LIA");

    Map<String, ObjectSlots> slotsByClass = ObjectSlotEncoder.encode(script, config.classScopes());

    Map<String, AttributeValues> attributeValuesByKey = new LinkedHashMap<>();
    Map<String, AttributeDomain> attributeDomainByKey = new LinkedHashMap<>();
    for (AttributeDomain domain : config.attributeDomains()) {
      if (domain.component() != null || domain.className().isEmpty()) {
        // U-type components (Phase 5) and primitive-type-wide fallback domains: out of scope.
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

    List<MClassInvariant> enforcedInvariants = new ArrayList<>();
    for (MClassInvariant invariant : model.classInvariants(true)) {
      if (config.activeInvariants().contains(invariant.qualifiedName())) {
        enforcedInvariants.add(invariant);
      }
    }

    FragmentChecker.Result checked = FragmentChecker.check(enforcedInvariants, context);
    checked.ledger().requireAllSupported();
    for (SmtTerm term : checked.assembled().values()) {
      script.assertThat(term);
    }

    SolverResult result =
        new SolverProcess(SolverBinary.resolve(), config.timeout()).run(script.toSmtLib());
    if (result.outcome() != SolverOutcome.SAT) {
      return new ModelFinderResult(false, checked.ledger(), List.of(), null);
    }

    Map<String, SmtValue> modelValues = SmtModelParser.parse(result.modelText());
    MSystem system = SystemStateReconstructor.reconstruct(model, context, modelValues);
    List<InvariantVerdict> verdicts = InvariantReEvaluator.reevaluate(model, system);
    return new ModelFinderResult(true, checked.ledger(), verdicts, system);
  }

  private static AttributeType attributeTypeOf(Type type) {
    if (type.isTypeOfString()) {
      return AttributeType.STRING;
    }
    if (type.isTypeOfInteger()) {
      return AttributeType.INTEGER;
    }
    if (type.isTypeOfReal()) {
      return AttributeType.REAL;
    }
    if (type.isTypeOfBoolean()) {
      return AttributeType.BOOLEAN;
    }
    throw new IllegalArgumentException("unsupported attribute type for encoding: " + type);
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
