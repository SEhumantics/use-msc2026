package org.tzi.use.smt.reconstruct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.encode.AssociationClassPointerEncoder;
import org.tzi.use.smt.encode.AssociationLinks;
import org.tzi.use.smt.encode.AttributeType;
import org.tzi.use.smt.encode.AttributeValues;
import org.tzi.use.smt.encode.ObjectSlots;
import org.tzi.use.smt.encode.TranslationContext;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MLinkObject;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Rebuilds a real, live {@link MSystem} from a solved SMT assignment, going through the same
 * SOIL-backed {@link UseSystemApi} path {@code org.tzi.use.kodkod.solution.ObjectDiagramCreator}
 * uses for Kodkod -- so the state is built through USE's own supported, event-firing mutation API
 * (undo history, GUI redraw) rather than reaching into {@code MSystemState} directly.
 */
public final class SystemStateReconstructor {
  private SystemStateReconstructor() {}

  /**
   * Headless convenience: builds a throwaway {@link Session}/{@link MSystem} and reconstructs into
   * it. Every unc-modelvalidator test uses this form.
   */
  public static MSystem reconstruct(
      MModel model, TranslationContext context, Map<String, SmtValue> modelValues)
      throws UseApiException {
    Session session = new Session();
    session.setSystem(new MSystem(model));
    return reconstruct(session, model, context, modelValues);
  }

  /**
   * Reconstructs into the given {@link Session}'s own system instead of a throwaway one -- the form
   * a live GUI plugin action must use, so the result becomes "the current session" the rest of USE
   * (object diagram views, the class browser, undo history) already observes, rather than a system
   * nothing is looking at. Mirrors {@code UseKodkodModelValidator.createObjectDiagram}: {@link
   * Session#reset()} first (same {@link MSystem} instance, cleared to an empty state, firing the
   * {@code ChangeEvent} that redraws already-open views), then repopulate via {@link
   * UseSystemApi#create(Session)}. The session must already have a system attached whose model is
   * {@code model} (i.e. {@link Session#hasSystem()} is true) -- a plugin action always has one,
   * since USE cannot be driving invariants without a loaded specification.
   */
  public static MSystem reconstruct(
      Session session, MModel model, TranslationContext context, Map<String, SmtValue> modelValues)
      throws UseApiException {
    session.reset();
    UseSystemApi api = UseSystemApi.create(session);

    Map<String, MObject> objectsBySlot = new LinkedHashMap<>();
    createObjects(api, model, context, modelValues, objectsBySlot);
    createAssociationClassObjects(api, model, context, modelValues, objectsBySlot);
    assignAttributes(api, model, context, modelValues, objectsBySlot);
    createLinks(api, model, context, modelValues, objectsBySlot);
    return session.system();
  }

  private static void createObjects(
      UseSystemApi api,
      MModel model,
      TranslationContext context,
      Map<String, SmtValue> modelValues,
      Map<String, MObject> objectsBySlot)
      throws UseApiException {
    for (Map.Entry<String, ObjectSlots> entry : context.slotsByClass().entrySet()) {
      String className = entry.getKey();
      ObjectSlots slots = entry.getValue();
      MClass cls = model.getClass(className);
      if (cls instanceof MAssociationClass) {
        // An association class's own instances go through createAssociationClassObjects
        // instead, after every ordinary class's objects (including its own two ends) already
        // exist -- see that method's own javadoc for why createObjectEx cannot be used here.
        continue;
      }
      for (int i = 0; i < slots.capacity(); i++) {
        if (isTrue(modelValues, slots.existsNames().get(i))) {
          // The slot's own name: the configured identity when the class predefined one, and
          // otherwise the generated `ClassName + index` spelling this line has always produced,
          // so a scenario that predefines nothing reconstructs exactly as before. Naming the
          // object after the configured identity is the incumbent's behaviour too -- its
          // ObjectStrategy.createElement strips the `ClassName_` prefix off the Kodkod atom.
          MObject object = api.createObjectEx(cls, slots.objectNames().get(i));
          objectsBySlot.put(slotKey(className, i), object);
        }
      }
    }
  }

  /**
   * An association class's own instances cannot go through {@link #createObjects}'s ordinary
   * {@code createObjectEx} path -- USE's own SOIL evaluator refuses a bare {@code create} for a
   * link-object class ("Creation of a linkobject is not allowed with the command create. Use
   * 'create ... between ...' or 'insert' instead.", confirmed directly against the real
   * exception). {@link UseSystemApi#createLinkObjectEx} is the supported alternative, needing the
   * two connected end objects UP FRONT -- resolved here by decoding each existing slot's two
   * synthetic index-pointer attributes ({@link AssociationClassPointerEncoder}) back into the
   * ALREADY-CREATED end objects, which is why this runs after {@link #createObjects} (so both
   * ends exist) but before {@link #assignAttributes} (so the returned {@link MLinkObject} --
   * itself an {@link MObject} -- is present in {@code objectsBySlot} for the association class's
   * OWN attributes, e.g. {@code salary}/{@code startDate}, to be assigned onto next).
   *
   * <p>{@link AssociationClassPointerEncoder}'s own {@code requirePointsToAnExistingObject}
   * constraint guarantees every existing association-class slot's two pointers resolve to an
   * existing end-object slot, so {@code objectsBySlot.get(...)} below is never null for a
   * satisfiable model.
   */
  private static void createAssociationClassObjects(
      UseSystemApi api,
      MModel model,
      TranslationContext context,
      Map<String, SmtValue> modelValues,
      Map<String, MObject> objectsBySlot)
      throws UseApiException {
    for (Map.Entry<String, ObjectSlots> entry : context.slotsByClass().entrySet()) {
      String className = entry.getKey();
      if (!(model.getClass(className) instanceof MAssociationClass associationClass)) {
        continue;
      }
      ObjectSlots slots = entry.getValue();
      List<MAssociationEnd> ends = associationClass.associationEnds();
      AttributeValues end0Pointer =
          context.attributeValues(
              className, AssociationClassPointerEncoder.END0_POINTER_ATTRIBUTE);
      AttributeValues end1Pointer =
          context.attributeValues(
              className, AssociationClassPointerEncoder.END1_POINTER_ATTRIBUTE);
      for (int i = 0; i < slots.capacity(); i++) {
        if (!isTrue(modelValues, slots.existsNames().get(i))) {
          continue;
        }
        MObject[] connectedObjects = new MObject[2];
        // The pointer indexes the end's FOLDED view (declared class + configured subclasses),
        // so the grid index maps to its concrete slot key through the view -- the same lookup
        // the ordinary link-grid reconstruction already does via slotKeyAt. Without a registered
        // view (hand-built contexts), the declared class's own keys keep the old behavior.
        List<ObjectSlots> endViews = context.assocClassEndViews(className);
        connectedObjects[0] =
            objectsBySlot.get(
                resolvePointerKey(endViews, 0, ends.get(0).cls().name(),
                    decodeIndex(modelValues, end0Pointer.valueNames().get(i))));
        connectedObjects[1] =
            objectsBySlot.get(
                resolvePointerKey(endViews, 1, ends.get(1).cls().name(),
                    decodeIndex(modelValues, end1Pointer.valueNames().get(i))));
        MLinkObject linkObject =
            api.createLinkObjectEx(associationClass, slots.objectNames().get(i), connectedObjects);
        objectsBySlot.put(slotKey(className, i), linkObject);
      }
    }
  }

  /**
   * The objectsBySlot key for one end of an association-class instance: through the end's
   * FOLDED view when one is registered (grid index -> concrete class slot), otherwise the
   * declared class's own generated key.
   */
  private static String resolvePointerKey(
      List<ObjectSlots> endViews, int endPosition, String declaredClassName, int pointerIndex) {
    if (endViews != null && endPosition < endViews.size()) {
      return endViews.get(endPosition).slotKeyAt(pointerIndex);
    }
    return slotKey(declaredClassName, pointerIndex);
  }

  private static int decodeIndex(Map<String, SmtValue> modelValues, String symbol) {
    if (!(modelValues.get(symbol) instanceof SmtValue.Int value)) {
      throw new IllegalStateException("expected an Int model value for " + symbol);
    }
    return value.value().intValueExact();
  }

  private static void assignAttributes(
      UseSystemApi api,
      MModel model,
      TranslationContext context,
      Map<String, SmtValue> modelValues,
      Map<String, MObject> objectsBySlot)
      throws UseApiException {
    for (AttributeValues values : context.attributes().values()) {
      if (AssociationClassPointerEncoder.END0_POINTER_ATTRIBUTE.equals(values.attributeName())
          || AssociationClassPointerEncoder.END1_POINTER_ATTRIBUTE.equals(values.attributeName())) {
        // Synthetic index-pointer bookkeeping, not a real USE attribute -- already consumed by
        // createAssociationClassObjects to resolve the link, never assigned via setAttributeValueEx.
        continue;
      }
      String className = values.className();
      MClass cls = model.getClass(className);
      MAttribute attribute = cls.attribute(values.attributeName(), true);
      AttributeDomain domain = context.attributeDomain(className, values.attributeName());
      ObjectSlots slots = context.slotsFor(className);
      for (int i = 0; i < slots.capacity(); i++) {
        MObject object = objectsBySlot.get(slotKey(className, i));
        if (object == null) {
          continue;
        }
        SmtValue raw = modelValues.get(values.valueNames().get(i));
        if (values.type() == AttributeType.UREAL) {
          SmtValue rawUncertainty = modelValues.get(values.uncertaintyNames().get(i));
          api.setAttributeValueEx(
              object, attribute, SmtValueDecoder.decodeUReal(raw, rawUncertainty));
        } else if (values.type() == AttributeType.UINTEGER) {
          SmtValue rawUncertainty = modelValues.get(values.uncertaintyNames().get(i));
          api.setAttributeValueEx(
              object, attribute, SmtValueDecoder.decodeUInteger(raw, rawUncertainty));
        } else if (values.type() == AttributeType.UBOOLEAN) {
          api.setAttributeValueEx(object, attribute, SmtValueDecoder.decodeUBoolean(raw));
        } else if (values.type() == AttributeType.USTRING) {
          SmtValue rawConfidence = modelValues.get(values.confidenceNames().get(i));
          api.setAttributeValueEx(
              object, attribute, SmtValueDecoder.decodeUString(raw, rawConfidence, domain));
        } else {
          api.setAttributeValueEx(
              object, attribute, SmtValueDecoder.decode(raw, attribute.type(), domain));
        }
      }
    }
  }

  private static void createLinks(
      UseSystemApi api,
      MModel model,
      TranslationContext context,
      Map<String, SmtValue> modelValues,
      Map<String, MObject> objectsBySlot)
      throws UseApiException {
    for (AssociationLinks links : context.linksByAssociation().values()) {
      MAssociation association = model.getAssociation(links.associationName());
      List<MAssociationEnd> declaredEnds = association.associationEnds();
      if (declaredEnds.get(0).isDerived() || declaredEnds.get(1).isDerived()) {
        // USE core's own MSystem#createLink unconditionally refuses ANY link creation for a
        // derived-end association (confirmed exception chain, see SmtModelFinder's association-
        // scope loop). This is not a limitation to work around: it is UNNECESSARY here in the
        // first place. DerivedLinkControllerDerivedEnd (use-core) recomputes a derived end's
        // content dynamically, on every navigation, by re-evaluating the association's own
        // derive expression against whatever the CURRENT reconstructed attribute state is -- it
        // never consults a materialized link at all. So a real MSystemState needs no link object
        // for this association for InvariantReEvaluator's re-check (or any other navigation) to
        // see the correct derived content; only the plain attribute reconstruction above (already
        // unconditional) needs to be correct, which it already is.
        continue;
      }
      boolean reflexive = links.aEnd().className().equals(links.bEnd().className());
      // A REFLEXIVE association (both ends the same class, e.g. CivilStatus's Marriage) cannot be
      // resolved by class name -- endPosition() would find the same declared position for both
      // aEnd and bEnd. There aEnd/bEnd are POSITIONALLY declared end 0/1 instead, the only
      // convention SmtModelFinder.solve() (the sole caller that can even build a reflexive
      // AssociationLinks -- no test fixture did before this fix) constructs one under, matching
      // ExpressionTranslator.linkTerm's own reflexive-orientation convention on the read side.
      int aPosition = reflexive ? 0 : endPosition(association, links.aEnd().className());
      int bPosition = reflexive ? 1 : endPosition(association, links.bEnd().className());
      for (int i = 0; i < links.aEnd().capacity(); i++) {
        for (int j = 0; j < links.bEnd().capacity(); j++) {
          if (isTrue(modelValues, links.linkNames()[i][j])) {
            MObject[] order = new MObject[2];
            // slotKeyAt resolves the slot's CONCRETE class and index: identical to
            // className#index for a plain class view, and correct for a FOLDED end view where
            // grid index i may stand for a subclass instance.
            order[aPosition] = objectsBySlot.get(links.aEnd().slotKeyAt(i));
            order[bPosition] = objectsBySlot.get(links.bEnd().slotKeyAt(j));
            api.createLinkEx(association, order);
          }
        }
      }
    }
  }

  private static int endPosition(MAssociation association, String className) {
    List<MAssociationEnd> ends = association.associationEnds();
    for (int i = 0; i < ends.size(); i++) {
      if (ends.get(i).cls().name().equals(className)) {
        return i;
      }
    }
    throw new IllegalArgumentException(
        "class " + className + " is not an end of association " + association.name());
  }

  private static String slotKey(String className, int index) {
    return className + "#" + index;
  }

  private static boolean isTrue(Map<String, SmtValue> modelValues, String symbol) {
    SmtValue value = modelValues.get(symbol);
    return value instanceof SmtValue.Bool bool && bool.value();
  }
}
