package org.tzi.use.smt.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** A selected INI section before it is interpreted against a USE model. */
public record RawConfiguration(Path source, String section, Map<String, List<String>> entries) {
  public RawConfiguration {
    entries = Map.copyOf(entries);
  }
}
