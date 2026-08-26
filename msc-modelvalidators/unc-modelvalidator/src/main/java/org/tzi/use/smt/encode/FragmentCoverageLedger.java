package org.tzi.use.smt.encode;

import java.util.List;

public record FragmentCoverageLedger(List<InvariantCoverage> entries) {
  public FragmentCoverageLedger {
    entries = List.copyOf(entries);
  }

  public boolean allSupported() {
    return entries.stream().allMatch(InvariantCoverage::supported);
  }

  /** Fails closed, naming every unsupported invariant, if any exist. */
  public void requireAllSupported() {
    List<String> unsupported =
        entries.stream()
            .filter(e -> !e.supported())
            .map(e -> e.invariantName() + " [" + e.mode() + "] (" + e.reason() + ")")
            .toList();
    if (!unsupported.isEmpty()) {
      throw new SmtTranslationException(
          "unsupported invariant(s), refusing to proceed: " + String.join(", ", unsupported));
    }
  }
}
