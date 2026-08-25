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
      List<String> values =
          entries.containsKey(attribute) ? setValues(attribute, entries.get(attribute)) : List.of();
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
                  setValues(componentKey, entries.get(componentKey)),
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
    deferredKeyDiagnostic(entries, diagnostics, "query", "query parsing is scheduled for Phase 4");
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
            QueryExpr.SATISFY,
            duration(entries),
            modelLimit(entries));
    return new NormalizedConfiguration(configuration, diagnostics);
  }

  private static void addPrimitiveDomain(
      List<AttributeDomain> domains, Map<String, List<String>> entries, String typeName) {
    BigDecimal min = decimal(entries, typeName + "_min");
    BigDecimal max = decimal(entries, typeName + "_max");
    if (min != null || max != null) {
      domains.add(new AttributeDomain("", typeName, null, List.of(), min, max));
    }
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

  private static List<String> setValues(String key, List<String> values) {
    String value = String.join(",", values).trim();
    if (!value.startsWith("Set{") || !value.endsWith("}")) {
      throw new ConfigurationReadException(
          "invalid enumerated domain for '" + key + "': expected Set{...}");
    }
    String body = value.substring(4, value.length() - 1).trim();
    return body.isEmpty()
        ? List.of()
        : List.of(body.split(",", -1)).stream().map(String::trim).toList();
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
