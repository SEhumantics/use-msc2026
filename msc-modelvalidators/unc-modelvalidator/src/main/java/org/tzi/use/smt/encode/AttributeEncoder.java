package org.tzi.use.smt.encode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.tzi.use.smt.config.AttributeDomain;
import org.tzi.use.smt.solver.Smt;
import org.tzi.use.smt.solver.SmtScript;
import org.tzi.use.smt.solver.SmtSort;
import org.tzi.use.smt.solver.SmtTerm;

/**
 * Encodes always-declared attribute values whose domain constraints are guarded by owner existence.
 */
public final class AttributeEncoder {
  private AttributeEncoder() {}

  public static AttributeValues encode(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeType type,
      AttributeDomain domain) {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String name = owner.className() + "_" + i + "_" + attributeName;
      script.declareConst(name, sort(type));
      names.add(name);
      SmtTerm exists = Smt.sym(owner.existsNames().get(i)), value = Smt.sym(name);
      switch (type) {
        case STRING -> guardString(script, exists, value, domain, owner.className(), attributeName);
        case INTEGER ->
            guardInteger(script, exists, value, domain, owner.className(), attributeName);
        case REAL -> guardReal(script, exists, value, domain, owner.className(), attributeName);
        case UREAL ->
            throw new IllegalArgumentException(
                "UReal attribute '"
                    + owner.className()
                    + "."
                    + attributeName
                    + "' requires paired value/uncertainty domains");
        case BOOLEAN -> {}
      }
    }
    return new AttributeValues(owner.className(), attributeName, type, names, List.of());
  }

  /**
   * Encodes the nominal and uncertainty components of one UReal as a same-slot SMT pair -- the
   * single-scenario form, whose emitted declaration and assertion ORDER is deliberately left
   * byte-identical to every pre-4.6 run so an existing witness cannot shift underneath the
   * benchmark corpus. The two halves are also available separately, for the multi-scenario UNIFORM
   * encoding that has to share one and copy the other.
   */
  public static AttributeValues encodeUReal(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeDomain valueDomain,
      AttributeDomain uncertaintyDomain) {
    requireComponent(valueDomain, owner.className(), attributeName, "value");
    requireComponent(uncertaintyDomain, owner.className(), attributeName, "uncertainty");
    List<String> valueNames = new ArrayList<>();
    List<String> uncertaintyNames = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String stem = owner.className() + "_" + i + "_" + attributeName;
      String valueName = stem + "_value";
      String uncertaintyName = stem + "_uncertainty";
      script.declareConst(valueName, SmtSort.REAL);
      script.declareConst(uncertaintyName, SmtSort.REAL);
      valueNames.add(valueName);
      uncertaintyNames.add(uncertaintyName);
      SmtTerm exists = Smt.sym(owner.existsNames().get(i));
      guardReal(
          script,
          exists,
          Smt.sym(valueName),
          valueDomain,
          owner.className(),
          attributeName + ".value");
      guardReal(
          script,
          exists,
          Smt.sym(uncertaintyName),
          uncertaintyDomain,
          owner.className(),
          attributeName + ".uncertainty");
      script.assertThat(
          Smt.app(
              "=>", exists, Smt.app(">=", Smt.sym(uncertaintyName), Smt.realLit(BigDecimal.ZERO))));
    }
    return new AttributeValues(
        owner.className(), attributeName, AttributeType.UREAL, valueNames, uncertaintyNames);
  }

  /**
   * The REPRESENTATIVE half of a UReal attribute -- the part that belongs to the snapshot {@code S}
   * and is therefore declared exactly ONCE even when several scenario copies are encoded into the
   * same script. Sharing these symbols across scenario obligations is precisely what makes UNIFORM
   * stronger than COVER; giving each scenario its own copy would silently turn one into the other.
   */
  public static List<String> encodeURealRepresentatives(
      SmtScript script, ObjectSlots owner, String attributeName, AttributeDomain valueDomain) {
    requireComponent(valueDomain, owner.className(), attributeName, "value");
    List<String> valueNames = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String valueName = owner.className() + "_" + i + "_" + attributeName + "_value";
      script.declareConst(valueName, SmtSort.REAL);
      valueNames.add(valueName);
      guardReal(
          script,
          Smt.sym(owner.existsNames().get(i)),
          Smt.sym(valueName),
          valueDomain,
          owner.className(),
          attributeName + ".value");
    }
    return valueNames;
  }

  /**
   * The MEASUREMENT-QUALITY half -- the part that belongs to the scenario {@code s}.
   *
   * @param scenarioSuffix distinguishes one scenario copy's uncertainty symbols from another's
   *     inside a single script (UNIFORM). Empty for the ordinary single-copy encoding, which keeps
   *     the emitted symbol names byte-identical to every pre-4.6 run.
   * @param pinned one configured value per slot, fixing this scenario's measurement quality, or
   *     null to leave the component free within its configured domain -- which is what EXISTS
   *     wants, since {@code exists s exists S} lets the solver choose the scenario too.
   */
  public static List<String> encodeURealUncertainties(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeDomain uncertaintyDomain,
      String scenarioSuffix,
      List<BigDecimal> pinned) {
    requireComponent(uncertaintyDomain, owner.className(), attributeName, "uncertainty");
    if (pinned != null && pinned.size() != owner.capacity()) {
      throw new IllegalArgumentException(
          "a scenario must fix the measurement quality of every candidate slot of "
              + owner.className()
              + "."
              + attributeName);
    }
    List<String> uncertaintyNames = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String uncertaintyName =
          owner.className() + "_" + i + "_" + attributeName + "_uncertainty" + scenarioSuffix;
      script.declareConst(uncertaintyName, SmtSort.REAL);
      uncertaintyNames.add(uncertaintyName);
      SmtTerm exists = Smt.sym(owner.existsNames().get(i));
      SmtTerm symbol = Smt.sym(uncertaintyName);
      if (pinned == null) {
        guardReal(
            script,
            exists,
            symbol,
            uncertaintyDomain,
            owner.className(),
            attributeName + ".uncertainty");
      } else {
        // A scenario fixes the quality of every POTENTIALLY live slot, so the pin is
        // unconditional; a dead slot's pinned component is simply never read back.
        script.assertThat(Smt.eq(symbol, Smt.realLit(pinned.get(i))));
      }
      script.assertThat(Smt.app("=>", exists, Smt.app(">=", symbol, Smt.realLit(BigDecimal.ZERO))));
    }
    return uncertaintyNames;
  }

  private static SmtSort sort(AttributeType type) {
    return switch (type) {
      case STRING, INTEGER -> SmtSort.INT;
      case REAL, UREAL -> SmtSort.REAL;
      case BOOLEAN -> SmtSort.BOOL;
    };
  }

  private static void guardString(
      SmtScript s, SmtTerm exists, SmtTerm value, AttributeDomain d, String cls, String attr) {
    if (d.enumeratedValues().isEmpty())
      throw new IllegalArgumentException(
          "string attribute '" + cls + "." + attr + "' has no configured candidate values");
    List<SmtTerm> options = new ArrayList<>();
    for (int i = 0; i < d.enumeratedValues().size(); i++)
      options.add(Smt.eq(value, Smt.intLit(BigInteger.valueOf(i))));
    s.assertThat(Smt.app("=>", exists, Smt.or(options)));
  }

  private static void guardInteger(
      SmtScript s, SmtTerm exists, SmtTerm value, AttributeDomain d, String cls, String attr) {
    if (!d.enumeratedValues().isEmpty()) {
      List<SmtTerm> options = new ArrayList<>();
      for (String candidate : d.enumeratedValues())
        options.add(Smt.eq(value, Smt.intLit(parseInteger(candidate, cls, attr))));
      s.assertThat(Smt.app("=>", exists, Smt.or(options)));
      return;
    }
    guardRange(s, exists, value, d, true);
  }

  private static void guardReal(
      SmtScript s, SmtTerm exists, SmtTerm value, AttributeDomain d, String cls, String attr) {
    if (!d.enumeratedValues().isEmpty()) {
      List<SmtTerm> options = new ArrayList<>();
      for (String candidate : d.enumeratedValues()) {
        options.add(Smt.eq(value, Smt.realLit(parseDecimal(candidate, cls, attr))));
      }
      s.assertThat(Smt.app("=>", exists, Smt.or(options)));
    }
    guardRange(s, exists, value, d, false);
  }

  private static void guardRange(
      SmtScript s, SmtTerm exists, SmtTerm value, AttributeDomain d, boolean integer) {
    List<SmtTerm> bounds = new ArrayList<>();
    if (d.lowerBound() != null) bounds.add(Smt.app(">=", value, literal(d.lowerBound(), integer)));
    if (d.upperBound() != null) bounds.add(Smt.app("<=", value, literal(d.upperBound(), integer)));
    if (!bounds.isEmpty()) s.assertThat(Smt.app("=>", exists, Smt.and(bounds)));
  }

  private static SmtTerm literal(BigDecimal d, boolean integer) {
    return integer ? Smt.intLit(d.toBigIntegerExact()) : Smt.realLit(d);
  }

  private static BigInteger parseInteger(String candidate, String cls, String attr) {
    try {
      return new BigInteger(candidate);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "invalid integer candidate '" + candidate + "' for attribute '" + cls + "." + attr + "'",
          e);
    }
  }

  private static BigDecimal parseDecimal(String candidate, String cls, String attr) {
    try {
      return new BigDecimal(candidate);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "invalid real candidate '" + candidate + "' for attribute '" + cls + "." + attr + "'", e);
    }
  }

  private static void requireComponent(
      AttributeDomain domain, String className, String attributeName, String component) {
    if (!attributeName.equals(domain.attributeName()) || !component.equals(domain.component())) {
      throw new IllegalArgumentException(
          "expected " + className + "." + attributeName + "_" + component + " domain");
    }
  }
}
