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
    Map<String, List<String>> objectNamesByClass = new LinkedHashMap<>();
    for (String name : vocabulary.classNames().stream().sorted().toList()) {
      int min = bound(entries, name + "_min", 1);
      int max = bound(entries, name + "_max", 1);
      if (vocabulary.isAbstractClass(name)) {
        int[] forced = abstractClassBounds(entries, name, min, max);
        min = forced[0];
        max = forced[1];
      }
      List<String> objectNames =
          entries.containsKey(name) ? objectNames(name, entries.get(name)) : List.of();
      if (max != -1 && objectNames.size() > max) {
        throw new ConfigurationReadException(
            "class '"
                + name
                + "' predefines "
                + objectNames.size()
                + " object name(s) but its maximum is "
                + max
                + "; the incumbent silently truncates the list to the bound"
                + " (ClassConfigurator.generateObjectsTuple), which would quietly shrink a"
                + " configured population, so it is refused here instead");
      }
      objectNamesByClass.put(name, objectNames);
      classes.add(new ClassScope(name, min, max, objectNames));
    }
    List<AssociationScope> associations = new ArrayList<>();
    for (String name : vocabulary.associationNames().stream().sorted().toList()) {
      associations.add(
          associationScope(
              name,
              entries.containsKey(name) ? linkTuples(name, entries.get(name)) : List.of(),
              bound(entries, name + "_min", 1),
              bound(entries, name + "_max", 1)));
    }
    requireLinkEndsArePredefined(associations, objectNamesByClass);
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
      BigDecimal minSize = decimal(entries, attribute + "_minSize");
      BigDecimal maxSize = decimal(entries, attribute + "_maxSize");
      if (minSize != null) {
        min = minSize;
      }
      if (maxSize != null) {
        max = maxSize;
      }
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
    List<String> negated = new ArrayList<>();
    for (String invariant : vocabulary.invariantNames()) {
      String status = one(entries, invariant);
      if (status == null || "active".equalsIgnoreCase(status)) {
        active.add(invariant.replaceFirst("_", "::"));
      } else if ("negate".equalsIgnoreCase(status)) {
        // A negated invariant is still active (QueryExpr.Counterexample's own target must be
        // active -- see QueryCompiler.desugar's requireActive), but the query is switched below
        // to ask for a witness where it is FALSE while every other active invariant holds,
        // exactly the incumbent's InvariantIndepChecker-style single-invariant negation --
        // reusing the counterexample query machinery already built and tested for
        // SmtModelFinder.independenceSweep rather than inventing a second one.
        String qualified = invariant.replaceFirst("_", "::");
        active.add(qualified);
        negated.add(qualified);
      } else if (!"inactive".equalsIgnoreCase(status)) {
        throw new ConfigurationReadException(
            "invalid invariant state for '"
                + invariant
                + "': expected active, inactive, or negate but was '"
                + status
                + "'");
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
    // forbiddensharing is deliberately NOT a deferredKeyDiagnostic (which would still block
    // requireSupported()): its own cross-inheritance violation (a subtype object composed via two
    // DIFFERENT compositions through two DIFFERENT supertype-typed ends, e.g. FileSystem.use's
    // MediaFile shared between FolderHasFile's File-typed end and ArchiveHasMedia's MediaFile-typed
    // end) is structurally UNREACHABLE under this encoder as it stands: an association end's
    // candidate population is drawn ONLY from its own declared class's slots (confirmed directly --
    // AssociationLinkEncoder never widens to descendant slots the way PolymorphicRange does for
    // allInstances()/select), so a MediaFile object can never even be a candidate for
    // FolderHasFile's File-typed end in the first place. Silently accepting the key (already in
    // recognisedKeys(), so it needs no further handling here) is therefore sound, not a weakening
    // of a REACHABLE constraint -- unlike bitwidth/satsolver below, which are ALSO deferred
    // diagnostics (not silent) because THEY are genuinely retained-but-unsupported rather than
    // provably inert. (The SAME-CLASS sharing case FileSystem.use's own header comment separately
    // documents as unconditionally enforced by Kodkod regardless of this toggle remains genuinely
    // unaddressed here -- no shipped scenario needs it, but a future one that did would need real
    // cross-association enforcement this does not provide.)
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
    // Collection-typed attribute SIZE bounds (the incumbent's attributeColSizeMin/Max keys):
    // parsed into the domain's lower/upper, which the SET_INTEGER encoder reads as the
    // per-object set cardinality bounds. Defaults 0/unbounded (attributesColSizeMin/Max).
    AnalysisConfiguration configuration =
        new AnalysisConfiguration(
            classes,
            associations,
            domains,
            active,
            negatedInvariantQuery(entries, vocabulary, negated, diagnostics),
            duration(entries),
            modelLimit(entries),
            aggregationCycleFreedomRequired(entries));
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

  /**
   * The PREDEFINED OBJECT NAMES of one bare {@code ClassName} key.
   *
   * <p>Identical in shape to {@link #setValues} but deliberately NOT routed through it: these are
   * object identities, not an attribute domain, so the 2026-08-27 quote-stripping rule must not
   * touch them. The incumbent draws the same line in the same place -- {@code
   * PropertyConfigurationVisitor.visitClass} (lines 100-106) only {@code trim()}s each element,
   * while {@code adjustElement} (line 616-618) strips quotes for String-typed attribute domains
   * alone. {@code GraphColoring.properties} depends on exactly that distinction: {@code Region =
   * Set&#123;r0,...&#125;} are unquoted object names and {@code Region_name =
   * Set&#123;'R0',...&#125;} are quoted String candidates, in the same section.
   */
  private static List<String> objectNames(String key, List<String> values) {
    List<String> names = setBody(key, values);
    Set<String> seen = new LinkedHashSet<>();
    for (String name : names) {
      if (name.isEmpty()) {
        throw new ConfigurationReadException("empty object name in '" + key + "'");
      }
      if (!seen.add(name)) {
        throw new ConfigurationReadException(
            "duplicate object name '" + name + "' in '" + key + "'");
      }
    }
    return names;
  }

  /**
   * The PREDEFINED LINKS of one bare {@code AssociationName} key, each tuple in the association's
   * DECLARED end order.
   *
   * <p>Mirrors {@code PropertyConfigurationVisitor.readComplexElements} (lines 438-476): strip the
   * {@code Set&#123;...&#125;} wrapper, split on {@code )}, take everything after the {@code (},
   * and split the remainder on commas, trimming each element. Tuple ARITY is checked against the
   * association by the link encoders (which know the model), not here: this reader accepts any
   * N-tuple with N &ge; 2 non-empty ends (the n-ary slice's ternary tuples parse exactly like the
   * incumbent's; the incumbent's own three-element ASSOCIATION-CLASS form prepends the link
   * object the same way an ordinary third end is spelled, and an arity mismatch is a located
   * encoder refusal, not a silent binary read).
   */
  private static List<List<String>> linkTuples(String key, List<String> values) {
    String body = String.join(",", setBody(key, values));
    if (body.isEmpty()) {
      return List.of();
    }
    List<List<String>> tuples = new ArrayList<>();
    for (String part : body.split("\\)", -1)) {
      int open = part.indexOf('(');
      if (open < 0) {
        if (!part.replace(",", "").trim().isEmpty()) {
          throw new ConfigurationReadException(
              "invalid link tuple list for '" + key + "': expected Set{(a,b),(c,d)}");
        }
        continue;
      }
      List<String> ends =
          List.of(part.substring(open + 1).split(",", -1)).stream().map(String::trim).toList();
      if (ends.size() < 2 || ends.stream().anyMatch(String::isEmpty)) {
        throw new ConfigurationReadException(
            "unsupported link tuple '("
                + String.join(",", ends)
                + ")' for '"
                + key
                + "': link tuples need at least two non-empty ends");
      }
      tuples.add(ends);
    }
    return tuples;
  }

  /** The comma-separated, trimmed elements inside a {@code Set{...}} literal, quotes untouched. */
  private static List<String> setBody(String key, List<String> values) {
    String value = String.join(",", values).trim();
    if (!value.startsWith("Set{") || !value.endsWith("}")) {
      throw new ConfigurationReadException(
          "invalid enumerated set for '" + key + "': expected Set{...}");
    }
    String body = value.substring(4, value.length() - 1).trim();
    return body.isEmpty()
        ? List.of()
        : List.of(body.split(",", -1)).stream().map(String::trim).toList();
  }

  /**
   * Forces an abstract class's own direct-instance bound to 0/0 -- refusing rather than silently
   * overriding when the user explicitly configured a nonzero {@code ClassName_min}/{@code
   * ClassName_max} for it.
   *
   * <p>UML abstract classes categorically cannot have direct instances; nothing before this
   * checked that anywhere in unc-modelvalidator (docs/modelvalidator-feature-matrix.json, feature
   * {@code class.abstract}) -- an unconfigured abstract class defaulted to the ordinary min=1/
   * max=1 a concrete class gets, and USE's own core object-creation API (not this reader) was
   * left to reject the resulting witness at reconstruction time with an uncaught {@code
   * MSystemException}. Kodkod's own {@code ClassConfigurator.generateObjectsTuple}
   * (kk-modelvalidator, lines 23-34) handles this by forcing an abstract class's own relation to
   * an empty {@code TupleSet} UNCONDITIONALLY -- it overrides whatever bound was configured,
   * silently.
   *
   * <p>This reader does not follow that override precedent. It follows this codebase's OWN
   * precedent instead -- {@link #associationScope}, directly above -- which refuses rather than
   * silently resolves a genuine contradiction between what the user configured and what the model
   * structurally demands (there, k forced link tuples versus an explicit association bound; here,
   * abstractness versus an explicit class bound). Only a bound the user left UNCONFIGURED is
   * defaulted to 0/0 silently -- exactly like every other unconfigured bound in this method
   * already defaults, so a scenario that never mentions the abstract class's bounds at all (the
   * ordinary case) is unaffected. A bound the user explicitly wrote down to something other than
   * 0 is a config/model contradiction, refused with a located, descriptive error instead of
   * guessed at -- per this project's standing bias against silently doing something other than
   * what was asked.
   */
  private static int[] abstractClassBounds(
      Map<String, List<String>> entries, String name, int min, int max) {
    if (entries.containsKey(name + "_min") && min != 0) {
      throw new ConfigurationReadException(
          "class '"
              + name
              + "' is declared abstract and can never have a direct instance of its own, but '"
              + name
              + "_min' is explicitly configured to "
              + min
              + "; leave '"
              + name
              + "_min' unconfigured (or set it to 0) to let it default to 0");
    }
    if (entries.containsKey(name + "_max") && max != 0) {
      throw new ConfigurationReadException(
          "class '"
              + name
              + "' is declared abstract and can never have a direct instance of its own, but '"
              + name
              + "_max' is explicitly configured to "
              + max
              + "; leave '"
              + name
              + "_max' unconfigured (or set it to 0) to let it default to 0");
    }
    return new int[] {0, 0};
  }

  /**
   * Derives one association's link bounds exactly as {@code AssociationConfigurator} does, and
   * refuses the one shape it cannot express consistently.
   *
   * <p>{@code PropertyConfigurationVisitor.setAssociationConfigurator} (lines 356-363) calls {@code
   * setSpecificValues(tuples)} -- which sets min=max=k (lines 162-165) -- and only THEN {@code
   * setLimits(Association_min, Association_max)}, whose error values are {@code
   * DefaultConfigurationValues.linksPerAssocMin/Max} (1/1). {@code setLimits} (lines 173-192) is
   * reproduced verbatim below, INCLUDING the detail that its {@code else} arm assigns the read
   * MINIMUM to {@code max}. With no tuples at all ({@code k == 0}) nothing here fires and the plain
   * bounds are returned unchanged, which is what keeps every scenario that predefines no links
   * reading byte-identically to before.
   *
   * <p>The refusal covers {@code GraphColoring.properties}: 134 forced {@code Adjacent} tuples with
   * no {@code Adjacent_min}/{@code Adjacent_max} yield min=134 and max=1, an unsatisfiable
   * link-count constraint that Kodkod only clears because at {@code bitwidth := 8} both the count
   * and the constant 134 wrap to -122. (That, and not "the Kodkod encoding needs headroom", is the
   * real cause of the bitwidth table documented in that file's own header -- at bitwidth 4/5/6 the
   * same wrap lands on 6 and the run is reported UNSATISFIABLE.) Reproducing the arithmetic
   * accident is impossible on unbounded SMT integers and reproducing the literal bounds would emit
   * a confident UNSAT the model does not support, so the contradiction is refused instead.
   */
  private static AssociationScope associationScope(
      String name, List<List<String>> links, int readMin, int readMax) {
    int k = links.size();
    if (k == 0) {
      return new AssociationScope(name, readMin, readMax, links);
    }
    int min = k;
    int max = k;
    if (readMin >= k) {
      min = readMin;
    }
    if (readMax >= k && readMax >= readMin) {
      max = readMax;
    } else if (readMax <= readMin) {
      max = readMax == -1 ? -1 : readMin;
    }
    if (max != -1 && max < min) {
      throw new ConfigurationReadException(
          "association '"
              + name
              + "' predefines "
              + k
              + " forced link(s) but its derived bounds are min="
              + min
              + ", max="
              + max
              + "; the incumbent's AssociationConfigurator.setLimits drives the maximum down to the"
              + " READ minimum, which only Kodkod's fixed-bitwidth wraparound makes satisfiable, so"
              + " the contradiction is refused rather than reported as UNSAT");
    }
    return new AssociationScope(name, min, max, links);
  }

  /**
   * Every predefined link end must name a predefined object. Which CLASS each tuple position
   * belongs to needs the model's association ends, which this reader does not have, so the exact
   * per-end check lives in {@code PredefinedLinkEncoder}; what is checkable here -- and what
   * catches a plain typo -- is that the name was predefined by some class at all.
   *
   * <p>Not reproduced: the incumbent additionally accepts generated {@code classname<n>} spellings
   * for the slots between the name list and {@code Class_min} ({@code
   * PropertyConfigurationVisitor.checkComplexElement}, lines 478-495). No corpus row uses that
   * form, so it stays outside this slice rather than being reproduced untested.
   */
  private static void requireLinkEndsArePredefined(
      List<AssociationScope> associations, Map<String, List<String>> objectNamesByClass) {
    Set<String> predefined = new LinkedHashSet<>();
    objectNamesByClass.values().forEach(predefined::addAll);
    for (AssociationScope scope : associations) {
      for (List<String> tuple : scope.links()) {
        for (String end : tuple) {
          if (!predefined.contains(end)) {
            throw new ConfigurationReadException(
                "predefined link of '"
                    + scope.associationName()
                    + "' names '"
                    + end
                    + "', which no class predefines as an object");
          }
        }
      }
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
      keys.add(name);
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

  /**
   * Deliberately defaults to {@code false} (not the incumbent's own on-by-default convention) --
   * see {@link AnalysisConfiguration#requireAggregationCycleFreedom()}'s own javadoc for why:
   * every existing corpus scenario with a composition/aggregation association never mentions this
   * key, so matching the incumbent's default would add a brand new constraint nobody asked for.
   */
  private static boolean aggregationCycleFreedomRequired(Map<String, List<String>> entries) {
    String status = one(entries, "aggregationcyclefreeness");
    if (status == null) {
      return false;
    }
    if ("on".equalsIgnoreCase(status)) {
      return true;
    }
    if ("off".equalsIgnoreCase(status)) {
      return false;
    }
    throw new ConfigurationReadException(
        "invalid aggregationcyclefreeness value: expected on or off but was '" + status + "'");
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

  /**
   * A {@code status = negate} invariant asks for a witness where THAT ONE invariant is false
   * while every other active invariant still holds -- exactly {@link QueryExpr.Counterexample}'s
   * own meaning, so this reuses that query rather than inventing a second representation of the
   * same idea. Refused (retained as a diagnostic, same fail-closed policy as every other
   * not-yet-understood key) rather than guessed at in the two cases where "the" target is
   * genuinely ambiguous: more than one invariant negated in the same section (a counterexample
   * query has exactly one target), or an explicit {@code query} key ALSO configured alongside a
   * negated invariant (which of the two should win is not this reader's call to make).
   */
  private static QueryExpr negatedInvariantQuery(
      Map<String, List<String>> entries,
      ConfigurationVocabulary vocabulary,
      List<String> negated,
      List<ConfigurationDiagnostic> diagnostics) {
    if (negated.isEmpty()) {
      return query(entries, vocabulary);
    }
    if (negated.size() > 1) {
      diagnostics.add(
          new ConfigurationDiagnostic(
              String.join(", ", negated),
              "a counterexample query has exactly one target, so which of these "
                  + negated.size()
                  + " negated invariants is meant is ambiguous"));
      return QueryExpr.SATISFY;
    }
    if (entries.containsKey("query")) {
      diagnostics.add(
          new ConfigurationDiagnostic(
              negated.get(0),
              "an explicit 'query' key is also configured in this section; combining it with a"
                  + " negated invariant is ambiguous, so this is refused rather than guessing"
                  + " which one should win"));
      return QueryExpr.SATISFY;
    }
    return new QueryExpr.Profiled(ScenarioProfile.EXISTS, new QueryExpr.Counterexample(negated.get(0)));
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
