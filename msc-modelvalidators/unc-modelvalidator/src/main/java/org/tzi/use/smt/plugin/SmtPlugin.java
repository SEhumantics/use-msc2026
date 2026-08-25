package org.tzi.use.smt.plugin;

import org.tzi.use.runtime.impl.Plugin;

/**
 * Entry point USE's plugin runtime instantiates (via the jar manifest's Main-Class, matching {@code
 * org.tzi.use.kodkod.plugin.KodkodPlugin}'s own pattern) once this jar is registered under {@code
 * lib/plugins/}. {@link Plugin#doRun} is left as the no-op default -- there is no plugin-wide setup
 * needed beyond what {@link SmtValidatePropertyAction} itself does per action invocation.
 */
public class SmtPlugin extends Plugin {

  private static final String PLUGIN_ID = "Unc-ModelValidatorPlugin";

  @Override
  public String getName() {
    return PLUGIN_ID;
  }
}
