package org.tzi.use.smt.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.LegacyListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;

/** Reads the incumbent's INI-flavoured {@code .properties} configuration format. */
public final class ConfigurationReader {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
  private static final int DEFAULT_MODEL_LIMIT = 1;
  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("(?m)--[^\\r\\n]*");

  /**
   * {@code DefaultConfigurationValues.integerMin/stringMin/realMin} from kk-modelvalidator, used to
   * complete a one-sided type-wide range exactly as {@code
   * PropertyConfigurationVisitor.visitConfigurableType} lines 203-253 do.
   */
  private static final Map<String, BigDecimal> DEFAULT_TYPE_WIDE_MIN =
      Map.of(
          "Integer", BigDecimal.valueOf(-10),
          "String", BigDecimal.ZERO,
          "Real", BigDecimal.valueOf(-2));

  /**
   * {@code DefaultConfigurationValues.integerMax/stringMax/realMax}; see {@link
   * #DEFAULT_TYPE_WIDE_MIN}.
   */
  private static final Map<String, BigDecimal> DEFAULT_TYPE_WIDE_MAX =
      Map.of(
          "Integer", BigDecimal.valueOf(10),
          "String", BigDecimal.valueOf(10),
          "Real", BigDecimal.valueOf(2));

  private ConfigurationReader() {}

  /**
   * Reads one section exactly as the incumbent does: an unnamed section is selected with {@code
   * null}; named sections must exist. USE-style line and block comments are discarded.
   */
  public static RawConfiguration read(Path file, String section) {
    if (file == null || !Files.isRegularFile(file)) {
      throw new ConfigurationReadException("configuration file does not exist: " + file);
    }
    try {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      INIConfiguration ini = new INIConfiguration();
      ini.setListDelimiterHandler(new LegacyListDelimiterHandler(','));
      try (Reader reader = new StringReader(stripUseComments(text))) {
        ini.read(reader);
      }
      if (section != null && !ini.getSections().contains(section)) {
        throw new ConfigurationReadException("section '" + section + "' does not exist in " + file);
      }
      String selectedSection = section;
      var selected = ini.getSection(selectedSection);
      if (section == null && !selected.getKeys().hasNext() && !ini.getSections().isEmpty()) {
        selectedSection = ini.getSections().iterator().next();
        selected = ini.getSection(selectedSection);
      }
      Map<String, List<String>> entries = new LinkedHashMap<>();
      var keys = selected.getKeys();
      while (keys.hasNext()) {
        String key = keys.next();
        List<String> values = selected.getList(key).stream().map(Object::toString).toList();
        entries.put(key, values);
      }
      if (entries.isEmpty()) {
        throw new ConfigurationReadException(
            "section '" + displaySection(selectedSection) + "' is empty in " + file);
      }
      return new RawConfiguration(file.toAbsolutePath().normalize(), selectedSection, entries);
    } catch (IOException | ConfigurationException ex) {
      throw new ConfigurationReadException(
          "cannot read configuration " + file + ": " + ex.getMessage(), ex);
    }
  }

  /** Interprets a raw section using model names, retaining unknown keys as diagnostics. */
  public static NormalizedConfiguration normalize(
      RawConfiguration raw, ConfigurationVocabulary vocabulary) {
    Map<String, List<String>> entries = raw.entries();
    List<ClassScope> classes = new ArrayList<>();
    for (String name : vocabulary.classNames().stream().sorted().toList()) {
      classes.add(
          new ClassScope(name, bound(entries, name + "_min", 1), bound(entries, name + "_max", 1)));
    }
    List<AssociationScope> associations = new ArrayList<>();
    for (String name : vocabulary.associationNames().stream().sorted().toList()) {
      associations.add(
          new AssociationScope(
              name, bound(entries, name + "_min", 1), bound(entries, name + "_max", 1)));
    }
    validateScopes(
        classes.stream().map(s -> new Scope(s.className(), s.min(), s.max())).toList(), "class");
    validateScopes(
        associations.stream().map(s -> new Scope(s.associationName(), s.min(), s.max())).toList(),
        "association");

    List<AttributeDomain> domains = new ArrayList<>();
    for (String attribute : vocabulary.attributeNames().stream().sorted().toList()) {
      boolean stringTyped = vocabulary.isStringAttribute(attribute);
      List<String> values =
          entries.containsKey(attribute)
              ? setValues(attribute, entries.get(attribute), stringTyped)
              : List.of();
      BigDecimal min = decimal(entries, attribute + "_min");
      BigDecimal max = decimal(entries, attribute + "_max");
      if (min != null || max != null || !values.isEmpty()) {
        String[] ownerAndName = splitAttribute(attribute);
        domains.add(new AttributeDomain(ownerAndName[0], ownerAndName[1], null, values, min, max));
      }
      for (String component : List.of("value", "uncertainty", "probability", "confidence")) {
        String componentKey = attribute + "_" + component;
        if (entries.containsKey(componentKey)) {
          String[] ownerAndName = splitAttribute(attribute);
          domains.add(
              new AttributeDomain(
                  ownerAndName[0],
                  ownerAndName[1],
                  component,
                  setValues(componentKey, entries.get(componentKey), stringTyped),
                  null,
                  null));
        }
      }
    }
    addPrimitiveDomain(domains, entries, "Integer");
    addPrimitiveDomain(domains, entries, "Real");
    addPrimitiveDomain(domains, entries, "String");

    Set<String> active = new LinkedHashSet<>();
    List<ConfigurationDiagnostic> diagnostics = new ArrayList<>();
    for (String invariant : vocabulary.invariantNames()) {
      String status = one(entries, invariant);
      if (status == null || "active".equalsIgnoreCase(status)) {
        active.add(invariant.replaceFirst("_", "::"));
      } else if (!"inactive".equalsIgnoreCase(status) && !"negate".equalsIgnoreCase(status)) {
        throw new ConfigurationReadException(
            "invalid invariant state for '"
                + invariant
                + "': expected active, inactive, or negate but was '"
                + status
                + "'");
      } else if ("negate".equalsIgnoreCase(status)) {
        diagnostics.add(
            new ConfigurationDiagnostic(
                invariant,
                "negated invariants are retained but cannot be submitted before query support is"
                    + " added"));
      }
    }

    Set<String> recognised = recognisedKeys(vocabulary);
    diagnostics.addAll(
        entries.keySet().stream()
            .filter(key -> !recognised.contains(key))
            .map(
                key ->
                    new ConfigurationDiagnostic(
                        key, "not yet understood; retained without weakening the configuration"))
            .toList());
    deferredKeyDiagnostic(
        entries,
        diagnostics,
        "aggregationcyclefreeness",
        "aggregation-cycle encoding is scheduled for Phase 3");
    deferredKeyDiagnostic(
        entries,
        diagnostics,
        "forbiddensharing",
        "forbidden-sharing encoding is scheduled for Phase 3");
    deferredKeyDiagnostic(
        entries,
        diagnostics,
        "bitwidth",
        "accepted for Kodkod compatibility and ignored by the unbounded SMT integer encoding");
    deferredKeyDiagnostic(
        entries,
        diagnostics,
        "satsolver",
        "accepted for Kodkod compatibility and ignored; the pinned SMT solver is configured"
            + " separately");
    deferredKeyDiagnostic(
        entries,
        diagnostics,
        "Real_step",
        "accepted for Kodkod compatibility and ignored because SMT Reals are not discretised");
    for (String key : List.of("String_min", "String_max")) {
      deferredKeyDiagnostic(
          entries,
          diagnostics,
          key,
          "not a String value bound: the incumbent's StringConfigurator reads it as a COUNT of"
              + " string atoms and pads the universe with generated placeholder spellings"
              + " (\"String_string\" + i), which this encoding has no counterpart for, so it is"
              + " refused rather than reinterpreted as a domain");
    }
    for (String association : vocabulary.associationNames()) {
      deferredKeyDiagnostic(
          entries,
          diagnostics,
          association,
          "explicit association tuples are retained but link reconstruction is scheduled for Phase"
              + " 3");
    }
    for (String attribute : vocabulary.attributeNames()) {
      deferredKeyDiagnostic(
          entries,
          diagnostics,
          attribute + "_minSize",
          "collection-valued attribute bounds are retained but unsupported before collection"
              + " encoding");
      deferredKeyDiagnostic(
          entries,
          diagnostics,
          attribute + "_maxSize",
          "collection-valued attribute bounds are retained but unsupported before collection"
              + " encoding");
    }
    AnalysisConfiguration configuration =
        new AnalysisConfiguration(
            classes,
            associations,
            domains,
            active,
            query(entries, vocabulary),
            duration(entries),
            modelLimit(entries));
    return new NormalizedConfiguration(configuration, diagnostics);
  }

  /**
   * The PRIMITIVE-TYPE-WIDE domain, recorded with an empty {@code className()} so it can never be
   * confused with a per-attribute one. Two details are the incumbent's, not this reader's choice.
   *
   * <p>First, the unset sentinel differs from the per-attribute keys'. {@code
   * PropertyConfigurationVisitor.visitConfigurableType} reads these with {@code readSize(name,
   * Integer.MIN_VALUE, /* allowNegative *&#47; true)} (lines 200-201, 243-244, 249-250), so a
   * configured {@code -1} is an ordinary negative bound here. The per-attribute keys are read with
   * {@code DefaultConfigurationValues.attributesPerClassMin} (= -1) as the error value and {@code
   * allowNegative} false (line 346), which is why {@link #decimal} treats {@code -1} as "unset"
   * there and must not do so here -- widening a configured domain silently is exactly the defect
   * that conflation would introduce.
   *
   * <p>Second, a range is created as soon as EITHER side is configured, and the missing side is
   * completed from {@code DefaultConfigurationValues} rather than left open (lines 260-266). With
   * neither side configured the incumbent sets no range at all, so neither does this.
   */
  private static void addPrimitiveDomain(
      List<AttributeDomain> domains, Map<String, List<String>> entries, String typeName) {
    BigDecimal min = typeWideDecimal(entries, typeName + "_min");
    BigDecimal max = typeWideDecimal(entries, typeName + "_max");
    if (min == null && max == null) {
      return;
    }
    domains.add(
        new AttributeDomain(
            "",
            typeName,
            null,
            List.of(),
            min != null ? min : DEFAULT_TYPE_WIDE_MIN.get(typeName),
            max != null ? max : DEFAULT_TYPE_WIDE_MAX.get(typeName)));
  }

  private static Set<String> recognisedKeys(ConfigurationVocabulary vocabulary) {
    Set<String> keys =
        new LinkedHashSet<>(
            Set.of(
                "aggregationcyclefreeness",
                "forbiddensharing",
                "timeout",
                "modelLimit",
                "query",
                "bitwidth",
                "satsolver",
                "Real_step"));
    for (String name : vocabulary.classNames()) {
      keys.add(name + "_min");
      keys.add(name + "_max");
    }
    for (String name : vocabulary.associationNames()) {
      keys.add(name + "_min");
      keys.add(name + "_max");
      keys.add(name);
    }
    for (String name : vocabulary.attributeNames()) {
      keys.add(name);
      keys.add(name + "_min");
      keys.add(name + "_max");
      keys.add(name + "_minSize");
      keys.add(name + "_maxSize");
      for (String component : List.of("value", "uncertainty", "probability", "confidence")) {
        keys.add(name + "_" + component);
      }
    }
    keys.addAll(
        Set.of("Integer_min", "Integer_max", "Real_min", "Real_max", "String_min", "String_max"));
    keys.addAll(vocabulary.invariantNames());
    return keys;
  }

  private static void deferredKeyDiagnostic(
      Map<String, List<String>> entries,
      List<ConfigurationDiagnostic> diagnostics,
      String key,
      String message) {
    if (entries.containsKey(key)) {
      diagnostics.add(new ConfigurationDiagnostic(key, message));
    }
  }

  private static int bound(Map<String, List<String>> entries, String key, int defaultValue) {
    String text = one(entries, key);
    if (text == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      throw new ConfigurationReadException(
          "invalid integer bound for '" + key + "': '" + text + "'");
    }
  }

  private static void validateScopes(List<Scope> scopes, String kind) {
    for (Scope scope : scopes) {
      if (scope.min < 0 || scope.max < -1 || (scope.max != -1 && scope.max < scope.min)) {
        throw new ConfigurationReadException(
            "invalid "
                + kind
                + " bounds for '"
                + scope.name
                + "': min="
                + scope.min
                + ", max="
                + scope.max);
      }
    }
  }

  /**
   * {@link #decimal} without its {@code -1}-means-unset rule; see {@link #addPrimitiveDomain} for
   * why the type-wide keys must not share it.
   */
  private static BigDecimal typeWideDecimal(Map<String, List<String>> entries, String key) {
    String text = one(entries, key);
    if (text == null) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ex) {
      throw new ConfigurationReadException(
          "invalid decimal bound for '" + key + "': '" + text + "'");
    }
  }

  private static BigDecimal decimal(Map<String, List<String>> entries, String key) {
    String text = one(entries, key);
    if (text == null || "-1".equals(text)) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ex) {
      throw new ConfigurationReadException(
          "invalid decimal bound for '" + key + "': '" + text + "'");
    }
  }

  private static List<String> setValues(String key, List<String> values, boolean stringTyped) {
    String value = String.join(",", values).trim();
    if (!value.startsWith("Set{") || !value.endsWith("}")) {
      throw new ConfigurationReadException(
          "invalid enumerated domain for '" + key + "': expected Set{...}");
    }
    String body = value.substring(4, value.length() - 1).trim();
    return body.isEmpty()
        ? List.of()
        : List.of(body.split(",", -1)).stream()
            .map(element -> adjustElement(element, stringTyped))
            .toList();
  }

  /**
   * The incumbent's {@code PropertyConfigurationVisitor.adjustElement}, which defines the semantics
   * of the shared {@code .properties} format and is therefore reproduced rather than reinvented:
   * trim, then -- if and only if the attribute's (element) type is String -- remove ALL {@code '}
   * characters, keeping the result even when it is empty.
   *
   * <p>Three details are deliberate, not accidental.
   *
   * <p>String only. Quotes are meaningful text in a numeric domain, where a quoted element is a
   * malformed configuration that must still be rejected loudly by {@code AttributeEncoder}'s number
   * parsing, not silently repaired into a number.
   *
   * <p>ALL quotes, not just surrounding ones. {@code replaceAll("'", "")} is what the incumbent
   * does, so {@code a'b} and {@code 'a'b'} denote the same candidate {@code ab} in both tools.
   * Narrowing this to a surrounding-quotes-only strip would be an improvement that breaks parity on
   * the one file format both validators read.
   *
   * <p>Empty results are kept. {@code Set&#123;''&#125;} is a ONE-element domain whose only
   * candidate is the empty string -- {@code benchmark/examples/Redefines/Redefines.properties}
   * depends on exactly that to make its invariant unsatisfiable, and {@code
   * AttributeEncoder.guardString} rejects an empty String domain outright, so dropping the element
   * would turn a documented UNSAT into a spurious error. The incumbent's OTHER quote-stripping
   * site, {@code readTypeValues}, does skip empty elements; that is the type-wide path, not the
   * attribute-domain path, and its behaviour is not copied here.
   *
   * <p>Not reproduced: {@code adjustElement} returns the {@code Undefined}/{@code Undefined_Set}
   * sentinel tokens before stripping. Those tokens carry no quote characters, so the branch is a
   * no-op for String attributes; this reader has no notion of them, and inventing one here would be
   * new behaviour rather than a defect fix.
   */
  private static String adjustElement(String element, boolean stringTyped) {
    String trimmed = element.trim();
    return stringTyped ? trimmed.replaceAll("'", "") : trimmed;
  }

  private static String[] splitAttribute(String attribute) {
    int split = attribute.indexOf('_');
    if (split < 1 || split == attribute.length() - 1) {
      throw new ConfigurationReadException(
          "attribute vocabulary entry must be Class_attribute: " + attribute);
    }
    return new String[] {attribute.substring(0, split), attribute.substring(split + 1)};
  }

  private static Duration duration(Map<String, List<String>> entries) {
    String value = one(entries, "timeout");
    if (value == null) {
      return DEFAULT_TIMEOUT;
    }
    try {
      return Duration.ofSeconds(Long.parseLong(value));
    } catch (NumberFormatException ex) {
      throw new ConfigurationReadException("invalid timeout (seconds): '" + value + "'");
    }
  }

  private static int modelLimit(Map<String, List<String>> entries) {
    return bound(entries, "modelLimit", DEFAULT_MODEL_LIMIT);
  }

  private static QueryExpr query(
      Map<String, List<String>> entries, ConfigurationVocabulary vocabulary) {
    List<String> values = entries.get("query");
    if (values == null) {
      return QueryExpr.SATISFY;
    }
    // The legacy comma delimiter also splits functional query atoms; restore their source text.
    return QueryParser.parse(String.join(",", values).trim(), vocabulary);
  }

  private static String one(Map<String, List<String>> entries, String key) {
    List<String> values = entries.get(key);
    if (values == null) {
      return null;
    }
    if (values.size() != 1) {
      throw new ConfigurationReadException(
          "configuration key '" + key + "' must have exactly one value");
    }
    return values.getFirst().trim();
  }

  private static String stripUseComments(String source) {
    return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll("")).replaceAll("");
  }

  private static String displaySection(String section) {
    return section == null ? "default" : section;
  }

  private record Scope(String name, int min, int max) {}

  /** The normalised configuration plus compatibility diagnostics for its unrecognised raw keys. */
  public record NormalizedConfiguration(
      AnalysisConfiguration configuration, List<ConfigurationDiagnostic> diagnostics) {
    public NormalizedConfiguration {
      diagnostics = List.copyOf(diagnostics);
    }

    /** Enforces the fail-closed policy at the point a configuration is submitted for solving. */
    public AnalysisConfiguration requireSupported() {
      if (!diagnostics.isEmpty()) {
        throw new ConfigurationReadException(
            "unsupported configuration key(s): "
                + diagnostics.stream()
                    .map(ConfigurationDiagnostic::key)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", ")));
      }
      return configuration;
    }
  }
}
