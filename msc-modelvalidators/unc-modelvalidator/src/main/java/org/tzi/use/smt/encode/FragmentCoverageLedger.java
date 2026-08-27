package org.tzi.use.smt.encode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tzi.use.smt.config.TranslationMode;

/**
 * What the translation supports, recorded separately for every (invariant, translation mode) pair,
 * and -- since Milestone 4.7 -- classified by the {@link FragmentBoundary} each refusal hit, so the
 * ledger can be READ AS EVIDENCE rather than as a list of sentences.
 *
 * <p>Two fail-closed gates run before any solver call, and they check different things:
 *
 * <ul>
 *   <li>{@link #requireAllSupported()} -- nothing the query needs was refused.
 *   <li>{@link #requireAccountedFor(Map)} -- nothing the query needs was MISSED. A pair with no
 *       entry at all is worse than a refused one: a refusal is visible, an omission is not.
 * </ul>
 */
public record FragmentCoverageLedger(List<InvariantCoverage> entries) {
  public FragmentCoverageLedger {
    entries = List.copyOf(entries);
  }

  public boolean allSupported() {
    return entries.stream().allMatch(InvariantCoverage::supported);
  }

  /** Every refused entry, in ledger order. */
  public List<InvariantCoverage> unsupported() {
    return entries.stream().filter(entry -> !entry.supported()).toList();
  }

  /** Every refused entry that hit one particular boundary. */
  public List<InvariantCoverage> entriesFor(FragmentBoundary boundary) {
    return entries.stream().filter(entry -> entry.boundary() == boundary).toList();
  }

  /** Which boundaries this ledger's refusals hit at all, in ledger order. */
  public Set<FragmentBoundary> boundariesHit() {
    Set<FragmentBoundary> hit = new LinkedHashSet<>();
    unsupported().forEach(entry -> hit.add(entry.boundary()));
    return hit;
  }

  /** The refusals grouped by boundary -- the tier/U-type breakdown a report wants. */
  public Map<FragmentBoundary, List<InvariantCoverage>> byBoundary() {
    Map<FragmentBoundary, List<InvariantCoverage>> grouped = new EnumMap<>(FragmentBoundary.class);
    for (InvariantCoverage entry : unsupported()) {
      grouped.computeIfAbsent(entry.boundary(), ignored -> new ArrayList<>()).add(entry);
    }
    return Map.copyOf(grouped);
  }

  /**
   * Fails closed, naming every unsupported invariant with its mode, its located message (which
   * still names the construct, exactly as Milestones 4.3-4.6 left it) and, since 4.7, the boundary
   * it hit.
   */
  public void requireAllSupported() {
    List<String> unsupported =
        unsupported().stream()
            .map(
                entry ->
                    entry.invariantName()
                        + " ["
                        + entry.mode()
                        + "] {"
                        + entry.boundary().name()
                        + ": "
                        + entry.boundary().citation()
                        + "} ("
                        + entry.reason()
                        + ")")
            .toList();
    if (!unsupported.isEmpty()) {
      throw new SmtTranslationException(
          "unsupported invariant(s), refusing to proceed: " + String.join(", ", unsupported));
    }
  }

  /**
   * Fails closed unless every (invariant, mode) pair the query requires -- whether it named the
   * invariant directly or reached it through an aggregate -- has an entry in this ledger.
   *
   * <p>{@code requireAllSupported} cannot see this: an omitted pair produces no entry, so it is
   * vacuously "all supported". This is the check that makes "every active invariant referenced
   * directly or by an aggregate is accounted for before solving" a property of the code rather than
   * a property of how carefully the caller assembled its requirement map.
   *
   * @param requirements the qualified invariant name to translation modes map produced by {@code
   *     QueryRequirements.requiredClassifications}
   */
  public void requireAccountedFor(Map<String, Set<TranslationMode>> requirements) {
    List<String> missing = new ArrayList<>();
    requirements.forEach(
        (invariantName, modes) ->
            modes.stream()
                .filter(mode -> !hasEntry(invariantName, mode))
                .forEach(mode -> missing.add(invariantName + " [" + mode + "]")));
    if (!missing.isEmpty()) {
      throw new SmtTranslationException(
          "the fragment ledger does not account for every invariant/mode pair this query requires,"
              + " refusing to solve: "
              + String.join(", ", missing));
    }
  }

  private boolean hasEntry(String invariantName, TranslationMode mode) {
    return entries.stream()
        .anyMatch(entry -> entry.invariantName().equals(invariantName) && entry.mode() == mode);
  }
}
