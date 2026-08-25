package org.tzi.use.smt;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Smoke test: the module compiles, JUnit runs, and use-core is genuinely on the classpath. If this
 * fails, nothing else in the module can be trusted.
 */
public class ModuleBuildsTest {

  @Test
  public void useCoreIsOnTheClasspath() {
    ModelFactory factory = new ModelFactory();
    assertNotNull(factory.createModel("Smoke"));
  }
}
