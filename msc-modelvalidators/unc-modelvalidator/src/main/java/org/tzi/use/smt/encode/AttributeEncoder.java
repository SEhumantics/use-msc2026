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
        case STRING, ENUM ->
            guardString(script, exists, value, domain, owner.className(), attributeName);
        case INTEGER ->
            guardInteger(script, exists, value, domain, owner.className(), attributeName);
        case REAL -> guardReal(script, exists, value, domain, owner.className(), attributeName);
        case BOOLEAN -> guardBoolean(script, exists, value, domain, owner.className(), attributeName);
        case UREAL, UINTEGER ->
            throw new IllegalArgumentException(
                type
                    + " attribute '"
                    + owner.className()
                    + "."
                    + attributeName
                    + "' requires paired value/uncertainty domains");
        case UBOOLEAN ->
            throw new IllegalArgumentException(
                type
                    + " attribute '"
                    + owner.className()
                    + "."
                    + attributeName
                    + "' requires a probability domain");
        case USTRING ->
            throw new IllegalArgumentException(
                type
                    + " attribute '"
                    + owner.className()
                    + "."
                    + attributeName
                    + "' requires paired value/confidence domains");
      }
    }
    return new AttributeValues(owner.className(), attributeName, type, names);
  }

  /**
   * Encodes the representative and uncertainty components of one U-typed attribute as a same-slot
   * SMT pair -- the single-scenario form, whose emitted declaration and assertion ORDER is
   * deliberately left byte-identical to every pre-4.6 run so an existing witness cannot shift
   * underneath the benchmark corpus. The two halves are also available separately, for the
   * multi-scenario UNIFORM encoding that has to share one and copy the other.
   *
   * <p>{@code UREAL} and {@code UINTEGER} share this method whole. The only difference between the
   * families is the representative's sort and the domain guard that goes with it; the uncertainty
   * is a non-negative Real either way, which is exactly why the threshold boundary is shared too
   * (USE's own {@code UInteger.gt} is defined as {@code toUReal().gt(...)}).
   */
  public static AttributeValues encodeUType(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeType type,
      AttributeDomain valueDomain,
      AttributeDomain uncertaintyDomain) {
    requireUType(type);
    requireComponent(valueDomain, owner.className(), attributeName, "value");
    requireComponent(uncertaintyDomain, owner.className(), attributeName, "uncertainty");
    List<String> valueNames = new ArrayList<>();
    List<String> uncertaintyNames = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String stem = owner.className() + "_" + i + "_" + attributeName;
      String valueName = stem + "_value";
      String uncertaintyName = stem + "_uncertainty";
      script.declareConst(valueName, representativeSort(type));
      script.declareConst(uncertaintyName, SmtSort.REAL);
      valueNames.add(valueName);
      uncertaintyNames.add(uncertaintyName);
      SmtTerm exists = Smt.sym(owner.existsNames().get(i));
      guardRepresentative(
          script,
          exists,
          Smt.sym(valueName),
          type,
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
        owner.className(), attributeName, type, valueNames, uncertaintyNames, List.of());
  }

  /**
   * The REPRESENTATIVE half of a U-typed attribute -- the part that belongs to the snapshot {@code
   * S} and is therefore declared exactly ONCE even when several scenario copies are encoded into
   * the same script. Sharing these symbols across scenario obligations is precisely what makes
   * UNIFORM stronger than COVER; giving each scenario its own copy would silently turn one into the
   * other.
   */
  public static List<String> encodeUTypeRepresentatives(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeType type,
      AttributeDomain valueDomain) {
    requireUType(type);
    requireComponent(valueDomain, owner.className(), attributeName, "value");
    List<String> valueNames = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String valueName = owner.className() + "_" + i + "_" + attributeName + "_value";
      script.declareConst(valueName, representativeSort(type));
      valueNames.add(valueName);
      guardRepresentative(
          script,
          Smt.sym(owner.existsNames().get(i)),
          Smt.sym(valueName),
          type,
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
  public static List<String> encodeUTypeUncertainties(
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

  /**
   * Encodes one {@code UBoolean} attribute as a SINGLE SMT Real per object slot: its canonical
   * truth probability.
   *
   * <p>This is the whole representation, and it is the proposal's, not a simplification of it:
   * "{@code UBoolean} is canonicalized to one probability of truth. {@code UBoolean(false,0.9)}
   * becomes probability (0.1)", and its solver-representation table says "Real probability p, with
   * 0 &lt;= p &lt;= 1 [...] <b>no independent carried Boolean</b>". Declaring the constructor's
   * first argument as a free solver Boolean would be a mistranslation, not an extra degree of
   * freedom -- the proposal spells that out at its {@code UBoolean(false,0.90)} worked example.
   *
   * <p>The {@code [0,1]} guard is asserted here rather than left to the configured domain because
   * it is a property of the TYPE: {@code UBooleanValue}'s own constructor rejects a probability
   * outside that interval, so a solver assignment outside it could not be reconstructed at all.
   */
  public static AttributeValues encodeUBoolean(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeDomain probabilityDomain) {
    requireComponent(probabilityDomain, owner.className(), attributeName, "probability");
    List<String> names = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String name = owner.className() + "_" + i + "_" + attributeName + "_probability";
      script.declareConst(name, SmtSort.REAL);
      names.add(name);
      SmtTerm exists = Smt.sym(owner.existsNames().get(i));
      SmtTerm probability = Smt.sym(name);
      guardReal(
          script,
          exists,
          probability,
          probabilityDomain,
          owner.className(),
          attributeName + ".probability");
      script.assertThat(
          Smt.app(
              "=>",
              exists,
              Smt.and(
                  List.of(
                      Smt.app(">=", probability, Smt.realLit(BigDecimal.ZERO)),
                      Smt.app("<=", probability, Smt.realLit(BigDecimal.ONE))))));
    }
    return new AttributeValues(owner.className(), attributeName, AttributeType.UBOOLEAN, names);
  }

  /**
   * Encodes one {@code UString} attribute as a same-slot SMT pair: an Int SPELLING INDEX into the
   * configured candidate spellings, and a Real CONFIDENCE.
   *
   * <p>The spelling index is the proposal's own default encoding, not a shortcut around native
   * strings: "in the default bounded encoding, configured spellings become a finite Z3 enumeration
   * and equality decides the spelling relation", with native Z3 String terms explicitly left as an
   * optional experiment. It is also EXACTLY the representation crisp {@code String} attributes have
   * always used here ({@link #guardString}), which is why this method reuses that guard rather than
   * inventing a second one -- one enumeration mechanism, two attribute types.
   *
   * <p>The second component is a CONFIDENCE, and the naming is deliberate rather than inherited:
   * {@code UStringValue(String str, double uncertainty)} names its parameter {@code uncertainty}
   * but stores it in {@code UString.sConf}, returns it from {@code confidence()}, and multiplies it
   * as a confidence in {@code UString.calculateConf} ({@code this.sConf * u.sConf}). The configured
   * key is therefore {@code _confidence}, matching what the value MEANS rather than what one
   * constructor parameter is spelled.
   *
   * <p>The {@code [0,1]} guard is asserted here rather than left to the configured domain because
   * it is a property of the TYPE: {@code UString}'s own constructor throws {@code
   * IllegalArgumentException("Invalid parameters")} outside that interval, so a solver assignment
   * outside it could not be reconstructed at all. Both guards are comparisons, never arithmetic, so
   * nothing this method emits can leave {@code QF_LIRA}.
   */
  public static AttributeValues encodeUString(
      SmtScript script,
      ObjectSlots owner,
      String attributeName,
      AttributeDomain spellingDomain,
      AttributeDomain confidenceDomain) {
    requireComponent(spellingDomain, owner.className(), attributeName, "value");
    requireComponent(confidenceDomain, owner.className(), attributeName, "confidence");
    if (spellingDomain.enumeratedValues().isEmpty()) {
      // 7.2's excluded "unrestricted strings", hit at CONFIGURATION level rather than inside one
      // invariant: the proposal's default bounded encoding is defined only where "configured
      // spellings become a finite Z3 enumeration", and without one there is nothing to enumerate.
      // This is deliberately not a ledger row -- the ledger records what one invariant asked for,
      // and this is a property of the attribute's configuration, so it fails the run outright
      // instead of quietly encoding a string the solver could choose freely.
      throw new SmtTranslationException(
          FragmentBoundary.UTYPE_UNRESTRICTED_STRING,
          "UString attribute '"
              + owner.className()
              + "."
              + attributeName
              + "' has no finite configured spelling domain; the source's default bounded encoding"
              + " turns configured spellings into a finite enumeration, and an unrestricted string"
              + " is outside the supported fragment rather than something to approximate");
    }
    List<String> spellingNames = new ArrayList<>();
    List<String> confidenceNames = new ArrayList<>();
    for (int i = 0; i < owner.capacity(); i++) {
      String stem = owner.className() + "_" + i + "_" + attributeName;
      String spellingName = stem + "_value";
      String confidenceName = stem + "_confidence";
      script.declareConst(spellingName, SmtSort.INT);
      script.declareConst(confidenceName, SmtSort.REAL);
      spellingNames.add(spellingName);
      confidenceNames.add(confidenceName);
      SmtTerm exists = Smt.sym(owner.existsNames().get(i));
      guardString(
          script,
          exists,
          Smt.sym(spellingName),
          spellingDomain,
          owner.className(),
          attributeName + ".value");
      SmtTerm confidence = Smt.sym(confidenceName);
      guardReal(
          script,
          exists,
          confidence,
          confidenceDomain,
          owner.className(),
          attributeName + ".confidence");
      script.assertThat(
          Smt.app(
              "=>",
              exists,
              Smt.and(
                  List.of(
                      Smt.app(">=", confidence, Smt.realLit(BigDecimal.ZERO)),
                      Smt.app("<=", confidence, Smt.realLit(BigDecimal.ONE))))));
    }
    return new AttributeValues(
        owner.className(),
        attributeName,
        AttributeType.USTRING,
        spellingNames,
        List.of(),
        confidenceNames);
  }

  private static SmtSort sort(AttributeType type) {
    return switch (type) {
      case STRING, ENUM, INTEGER, UINTEGER, USTRING -> SmtSort.INT;
      case REAL, UREAL, UBOOLEAN -> SmtSort.REAL;
      case BOOLEAN -> SmtSort.BOOL;
    };
  }

  /**
   * The sort of a U-type's REPRESENTATIVE half. This is the whole of what separates the two
   * families: {@code UReal(mu, sigma)} puts {@code mu} on Real, {@code UInteger(n, sigma)} puts
   * {@code n} on Int -- and it is the Int sort, not any second boundary computation, that makes the
   * solver's own integer theory round a real-valued threshold up to the least admissible integer.
   * The uncertainty half is a Real in both families.
   */
  private static SmtSort representativeSort(AttributeType type) {
    return type == AttributeType.UINTEGER ? SmtSort.INT : SmtSort.REAL;
  }

  private static void guardRepresentative(
      SmtScript script,
      SmtTerm exists,
      SmtTerm value,
      AttributeType type,
      AttributeDomain domain,
      String className,
      String attributeName) {
    if (type == AttributeType.UINTEGER) {
      guardInteger(script, exists, value, domain, className, attributeName);
    } else {
      guardReal(script, exists, value, domain, className, attributeName);
    }
  }

  private static void requireUType(AttributeType type) {
    if (!type.isPairedUType()) {
      throw new IllegalArgumentException(
          "paired representative/uncertainty encoding is only defined for U-types, got " + type);
    }
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

  /**
   * A Boolean attribute's enumerated domain pins the symbol to its literal candidates
   * ({@code Set{true, false}} = free, a single candidate = pinned). The guard was MISSING
   * entirely: the switch had no BOOLEAN arm, so a configured Boolean domain was silently
   * ignored and the solver chose the value freely -- found by the Kleene-strictness audit,
   * whose forced-false operand came back true.
   */
  private static void guardBoolean(
      SmtScript s, SmtTerm exists, SmtTerm value, AttributeDomain d, String cls, String attr) {
    if (d.enumeratedValues().isEmpty()) {
      return;
    }
    List<SmtTerm> options = new ArrayList<>();
    for (String candidate : d.enumeratedValues()) {
      String normalized = candidate.trim();
      if (normalized.equalsIgnoreCase("true")) {
        options.add(Smt.eq(value, Smt.bool(true)));
      } else if (normalized.equalsIgnoreCase("false")) {
        options.add(Smt.eq(value, Smt.bool(false)));
      } else {
        throw new IllegalArgumentException(
            "boolean attribute '"
                + cls
                + "."
                + attr
                + "' has a non-literal configured candidate '"
                + candidate
                + "'");
      }
    }
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
