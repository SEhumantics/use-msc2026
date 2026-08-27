package org.tzi.use.smt.reconstruct;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.encode.AssociationLinks;
import org.tzi.use.smt.encode.AttributeType;
import org.tzi.use.smt.encode.AttributeValues;
import org.tzi.use.smt.encode.ObjectSlots;
import org.tzi.use.smt.encode.TranslationContext;
import org.tzi.use.smt.solver.SmtValue;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
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
      for (int i = 0; i < slots.capacity(); i++) {
        if (isTrue(modelValues, slots.existsNames().get(i))) {
          MObject object = api.createObjectEx(cls, className + i);
          objectsBySlot.put(slotKey(className, i), object);
        }
      }
    }
  }

  private static void assignAttributes(
      UseSystemApi api,
      MModel model,
      TranslationContext context,
      Map<String, SmtValue> modelValues,
      Map<String, MObject> objectsBySlot)
      throws UseApiException {
    for (AttributeValues values : context.attributes().values()) {
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
      if (links.aEnd().className().equals(links.bEnd().className())) {
        throw new UnsupportedOperationException(
            "reflexive association reconstruction not yet supported: " + links.associationName());
      }
      MAssociation association = model.getAssociation(links.associationName());
      int aPosition = endPosition(association, links.aEnd().className());
      int bPosition = endPosition(association, links.bEnd().className());
      for (int i = 0; i < links.aEnd().capacity(); i++) {
        for (int j = 0; j < links.bEnd().capacity(); j++) {
          if (isTrue(modelValues, links.linkNames()[i][j])) {
            MObject[] order = new MObject[2];
            order[aPosition] = objectsBySlot.get(slotKey(links.aEnd().className(), i));
            order[bPosition] = objectsBySlot.get(slotKey(links.bEnd().className(), j));
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
