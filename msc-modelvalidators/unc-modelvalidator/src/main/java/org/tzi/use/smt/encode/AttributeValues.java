package org.tzi.use.smt.encode;

import java.util.List;

/**
 * SMT symbols for one attribute on one concrete class's object slots.
 *
 * <p>A crisp attribute uses only {@link #valueNames}. A PAIRED U-typed attribute ({@link
 * AttributeType#UREAL} or {@link AttributeType#UINTEGER}) uses the value list for its
 * representative component and a same-sized parallel {@link #uncertaintyNames} list. A {@link
 * AttributeType#USTRING} attribute uses the value list for its SPELLING INDEX and a same-sized
 * parallel {@link #confidenceNames} list.
 *
 * <p>The confidence gets a list of its own rather than borrowing {@link #uncertaintyNames} because
 * it is not an uncertainty: {@code UStringValue}'s second constructor parameter is called {@code
 * uncertainty} but is stored straight into {@code UString.sConf} and read back through {@code
 * confidence()}, and {@code UString.calculateConf} multiplies those confidences directly. Reusing
 * the uncertainty field would carry that misnomer into this encoder, where the two quantities move
 * in OPPOSITE directions -- a larger sigma is a worse measurement, a larger confidence a better
 * one.
 *
 * <p>Keeping the components of one slot in one record prevents reconstruction and threshold
 * translation from accidentally looking up components belonging to different slots.
 */
public record AttributeValues(
    String className,
    String attributeName,
    AttributeType type,
    List<String> valueNames,
    List<String> uncertaintyNames,
    List<String> confidenceNames) {
  public AttributeValues {
    valueNames = List.copyOf(valueNames);
    uncertaintyNames = List.copyOf(uncertaintyNames);
    confidenceNames = List.copyOf(confidenceNames);
    if (type.isPairedUType() && valueNames.size() != uncertaintyNames.size()) {
      throw new IllegalArgumentException(type + " value/uncertainty symbol counts must match");
    }
    if (!type.isPairedUType() && !uncertaintyNames.isEmpty()) {
      throw new IllegalArgumentException(
          "only representative/uncertainty U-typed attributes may have uncertainty symbols");
    }
    if (type == AttributeType.USTRING && valueNames.size() != confidenceNames.size()) {
      throw new IllegalArgumentException("USTRING spelling/confidence symbol counts must match");
    }
    if (type != AttributeType.USTRING && !confidenceNames.isEmpty()) {
      throw new IllegalArgumentException("only UString attributes may have confidence symbols");
    }
  }

  /** The crisp and single-component form: no second symbol per slot. */
  public AttributeValues(
      String className, String attributeName, AttributeType type, List<String> valueNames) {
    this(className, attributeName, type, valueNames, List.of(), List.of());
  }
}
