package org.tzi.use.uncertainty.differential;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anti-self-comparison test. Everything else in this harness is worthless if this fails.
 *
 * <h2>What is actually being proved, and why it is not vacuous</h2>
 * The failure mode is: the harness resolves {@code org.tzi.use.uml.ocl.value.URealValue} to the
 * <em>ported</em> class instead of the historical one, and so compares the port against itself.
 * At S1 no port exists, so testing on {@code URealValue} alone would prove nothing — the app loader
 * has no class of that name to be confused with.
 *
 * <p>So these tests use {@code org.tzi.use.uml.ocl.value.RealValue}, which exists <em>right now</em>
 * in both {@code use-core/src/main/java} and in the historical {@code use.jar}. That is a genuine
 * same-fully-qualified-name collision, available today, and it is the exact situation the U-types
 * will be in from S4 onwards.
 *
 * <p>{@link #naiveLoaderWouldSelfCompare()} is the negative control: it builds the loader the
 * obvious way and demonstrates that it does resolve to the application's class, i.e. that the hazard
 * is real and that these assertions would catch it.
 *
 * <p>JUnit 5 Jupiter. There is no junit-vintage-engine in this reactor, so a JUnit 3 or JUnit 4
 * test here would compile, report nothing, and never run.
 */
@DisplayName("HistoricalOracle class-loader isolation")
class HistoricalOracleIsolationTest {

    /** Present in both the current product sources and the historical use.jar. */
    private static final String COLLIDING_CLASS = "org.tzi.use.uml.ocl.value.RealValue";

    private static HistoricalOracle oracle;

    @BeforeAll
    static void openOracle() {
        oracle = HistoricalOracle.open();
    }

    @AfterAll
    static void closeOracle() {
        if (oracle != null) {
            oracle.close();
        }
    }

    @Test
    @DisplayName("the isolated loader is parent-last and the app loader is not in its parent chain")
    void loaderIsParentLast() {
        assertTrue(oracle.loader() instanceof IsolatedJarClassLoader,
                "the oracle must use the parent-last loader; a plain URLClassLoader is not sufficient "
                        + "in this JPMS reactor, whatever its parent");
        assertSame(ClassLoader.getPlatformClassLoader(), oracle.loader().getParent(),
                "the parent supplies java.* only");
        assertTrue(IsolatedJarClassLoader.isIsolated("org.tzi.use.uml.ocl.value.URealValue"));
        assertTrue(IsolatedJarClassLoader.isIsolated("uDataTypes.UReal"));
        assertFalse(IsolatedJarClassLoader.isIsolated("java.lang.String"),
                "JDK classes must still be shared with the parent, or the historical code cannot run");

        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();
        for (ClassLoader l = oracle.loader(); l != null; l = l.getParent()) {
            assertNotSame(app, l, "the application class loader must not appear in the oracle's parent chain");
        }
    }

    /**
     * Measured during S1 and the reason {@link IsolatedJarClassLoader} exists at all.
     *
     * <p>Because {@code use-core} carries a {@code module-info.java}, Maven resolves {@code use.core}
     * into the boot layer, and {@code jdk.internal.loader.BuiltinClassLoader} maps every package in
     * the boot-layer module graph to its defining loader <em>before</em> falling back to its parent.
     * The platform class loader therefore resolves {@code org.tzi.use.*} to the application's
     * classes. The "obvious" fix of parenting on the platform loader is a trap here.
     *
     * <p>If this test ever starts failing it means the reactor stopped running tests on the module
     * path. That is worth knowing, so the test asserts the hazard rather than ignoring it.
     */
    @Test
    @DisplayName("hazard: under JPMS the platform loader still resolves application classes")
    void platformLoaderIsNotACleanParentUnderJpms() throws Exception {
        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();
        Class<?> applicationClass = Class.forName(COLLIDING_CLASS, false, app);

        Class<?> viaPlatform;
        try {
            viaPlatform = Class.forName(COLLIDING_CLASS, false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException e) {
            // Classpath mode: the platform loader is clean. Isolation is then trivially satisfied,
            // and the parent-last loader remains correct, so there is nothing to assert.
            return;
        }
        assertSame(applicationClass, viaPlatform,
                "expected the platform loader to delegate to the application loader via the boot-layer "
                        + "module graph; if it no longer does, revisit the IsolatedJarClassLoader rationale");
    }

    @Test
    @DisplayName("a same-named class exists on both sides and they are distinct Class objects")
    void sameNameDistinctClasses() throws Exception {
        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();

        Class<?> applicationClass = assertDoesNotThrow(
                () -> Class.forName(COLLIDING_CLASS, false, app),
                "precondition: the current product must provide " + COLLIDING_CLASS
                        + ", otherwise this test proves nothing");
        Class<?> historicalClass = oracle.historicalClass("RealValue");

        assertEquals(applicationClass.getName(), historicalClass.getName(),
                "precondition: the two classes must genuinely share a fully-qualified name");
        assertNotSame(applicationClass, historicalClass,
                "SELF-COMPARISON: the oracle returned the application's class, so a differential run "
                        + "would compare the port against itself");
        assertNotSame(applicationClass.getClassLoader(), historicalClass.getClassLoader(),
                "the two classes must come from different loaders");
        assertSame(oracle.loader(), historicalClass.getClassLoader(),
                "the historical class must be defined by the oracle's own loader");
    }

    @Test
    @DisplayName("instances of the two same-named classes are mutually incompatible")
    void instancesDoNotCrossTheBoundary() throws Exception {
        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();
        Class<?> applicationClass = Class.forName(COLLIDING_CLASS, false, app);
        Class<?> historicalClass = oracle.historicalClass("RealValue");

        Object historicalInstance = historicalClass.getConstructor(double.class).newInstance(1.25d);

        assertFalse(applicationClass.isInstance(historicalInstance),
                "a historical instance must not be assignable to the application class; if it is, "
                        + "the two 'sides' are one and the same class");
        assertTrue(historicalClass.isInstance(historicalInstance),
                "sanity: the historical instance is an instance of the historical class");
    }

    @Test
    @DisplayName("the U-types come from the jars and are absent from the application loader at S1")
    void uTypesResolveOnlyThroughTheOracle() {
        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();
        for (String simple : new String[] { "URealValue", "UIntegerValue", "UBooleanValue", "UStringValue" }) {
            Class<?> historical = oracle.historicalClass(simple);
            assertSame(oracle.loader(), historical.getClassLoader(),
                    simple + " must be defined by the isolated loader");
            // At S1 there is no port, so this must not resolve. When S4 lands, this assertion is
            // expected to be inverted; sameNameDistinctClasses() is the assertion that survives.
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("org.tzi.use.uml.ocl.value." + simple, false, app),
                    simple + " unexpectedly resolves on the application loader. If the port has "
                            + "landed, this test must be updated to assert distinctness instead of absence "
                            + "-- do NOT delete it.");
        }
    }

    /**
     * Negative controls. Both of the loaders a reasonable person would write DO self-compare here;
     * the harness's loader does not. Without these three assertions side by side, "the isolation
     * test passes" would be an empty statement.
     */
    @Test
    @DisplayName("negative control: both naive loaders self-compare, the harness's does not")
    void naiveLoadersWouldSelfCompare() throws Exception {
        ClassLoader app = HistoricalOracleIsolationTest.class.getClassLoader();
        Class<?> applicationClass = Class.forName(COLLIDING_CLASS, false, app);

        URL[] urls = {
                oracle.useJarPath().toUri().toURL(),
                oracle.uncertaintyJarPath().toUri().toURL() };

        // (1) default parent = system class loader, upward delegation wins.
        try (URLClassLoader naive = new URLClassLoader(urls)) {
            assertSame(applicationClass, Class.forName(COLLIDING_CLASS, false, naive),
                    "negative control 1 failed: the naive loader no longer demonstrates the hazard");
        }

        // (2) platform parent -- the textbook fix, defeated by the boot-layer module graph.
        try (URLClassLoader platformParented =
                     new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Class<?> viaPlatform;
            try {
                viaPlatform = Class.forName(COLLIDING_CLASS, false, platformParented);
            } catch (ClassNotFoundException e) {
                viaPlatform = null; // classpath mode; nothing to demonstrate
            }
            if (viaPlatform != null) {
                assertSame(applicationClass, viaPlatform,
                        "negative control 2: expected the platform-parented loader to leak the "
                                + "application class through the boot-layer module graph");
            }
        }

        // (3) the harness's loader.
        Class<?> viaOracle = oracle.historicalClass("RealValue");
        assertNotSame(applicationClass, viaOracle,
                "SELF-COMPARISON: the oracle resolved to the application's class");
        assertSame(oracle.loader(), viaOracle.getClassLoader());
    }

    @Test
    @DisplayName("the loaded jars are the recorded ones")
    void jarsAreTheRecordedOnes() {
        Path useJar = oracle.useJarPath();
        Path uncertaintyJar = oracle.uncertaintyJarPath();
        assertTrue(Files.isReadable(useJar), useJar + " must be readable");
        assertTrue(Files.isReadable(uncertaintyJar), uncertaintyJar + " must be readable");
        assertEquals(HistoricalOracle.USE_JAR_SHA256, HistoricalOracle.sha256(useJar));
        assertEquals(HistoricalOracle.UNCERTAINTY_JAR_SHA256, HistoricalOracle.sha256(uncertaintyJar));
    }

    @Test
    @DisplayName("a missing jar fails loudly and names the path, it never skips")
    void missingJarFailsLoudly() {
        String previous = System.getProperty(HistoricalOracle.JAR_DIR_PROPERTY);
        System.setProperty(HistoricalOracle.JAR_DIR_PROPERTY, "/nonexistent/historical/jars");
        try {
            HistoricalOracle.HistoricalOracleUnavailableException e = assertThrows(
                    HistoricalOracle.HistoricalOracleUnavailableException.class,
                    HistoricalOracle::open);
            assertTrue(e.getMessage().contains("/nonexistent/historical/jars"),
                    "the failure must name the path that was tried; got: " + e.getMessage());
            assertTrue(e.getMessage().contains(HistoricalOracle.USE_JAR),
                    "the failure must name the missing jar; got: " + e.getMessage());
        } finally {
            if (previous == null) {
                System.clearProperty(HistoricalOracle.JAR_DIR_PROPERTY);
            } else {
                System.setProperty(HistoricalOracle.JAR_DIR_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("a jar whose digest does not match is rejected")
    void wrongDigestFailsLoudly() throws Exception {
        Path dir = Files.createTempDirectory("historical-oracle-tamper-");
        try {
            // Right names, wrong bytes.
            Files.write(dir.resolve(HistoricalOracle.USE_JAR), new byte[] { 'P', 'K', 3, 4, 0 });
            Files.write(dir.resolve(HistoricalOracle.UNCERTAINTY_JAR), new byte[] { 'P', 'K', 3, 4, 0 });

            String previous = System.getProperty(HistoricalOracle.JAR_DIR_PROPERTY);
            System.setProperty(HistoricalOracle.JAR_DIR_PROPERTY, dir.toString());
            try {
                HistoricalOracle.HistoricalOracleUnavailableException e = assertThrows(
                        HistoricalOracle.HistoricalOracleUnavailableException.class,
                        HistoricalOracle::open);
                assertTrue(e.getMessage().contains("does not match the recorded digest"),
                        "expected a digest complaint, got: " + e.getMessage());
                assertTrue(e.getMessage().contains(HistoricalOracle.USE_JAR_SHA256),
                        "the failure must state the expected digest; got: " + e.getMessage());
            } finally {
                if (previous == null) {
                    System.clearProperty(HistoricalOracle.JAR_DIR_PROPERTY);
                } else {
                    System.setProperty(HistoricalOracle.JAR_DIR_PROPERTY, previous);
                }
            }
        } finally {
            Files.deleteIfExists(dir.resolve(HistoricalOracle.USE_JAR));
            Files.deleteIfExists(dir.resolve(HistoricalOracle.UNCERTAINTY_JAR));
            Files.deleteIfExists(dir);
        }
    }

    // ------------------------------------------------------------------ B1 relocation carve-out

    /**
     * The vendored uncertainty datatypes were relocated out of package {@code uDataTypes} into
     * {@code org.tzi.use.uncertainty.datatypes} (B1). That is a strict subtree of
     * {@code org.tzi.use.}, which {@link IsolatedJarClassLoader} isolates — so without a carve-out
     * the loader would claim those names, not find them in the jars, and throw rather than delegate.
     *
     * <p>The carve-out is only safe while the historical jars contain nothing under that prefix.
     * That is checked here against the jars themselves, so the safety argument cannot rot.
     */
    @Test
    @DisplayName("the org.tzi.use.uncertainty carve-out is safe: neither jar contains that subtree")
    void carveOutSubtreeIsAbsentFromBothHistoricalJars() throws Exception {
        for (Path jar : new Path[] { oracle.useJarPath(), oracle.uncertaintyJarPath() }) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                java.util.List<String> offenders = zip.stream()
                        .map(java.util.zip.ZipEntry::getName)
                        .filter(n -> n.startsWith("org/tzi/use/uncertainty/"))
                        .toList();
                assertTrue(offenders.isEmpty(),
                        jar.getFileName() + " contains " + offenders.size() + " entry/entries under "
                                + "org/tzi/use/uncertainty/, so the carve-out in "
                                + "IsolatedJarClassLoader would silently stop isolating them: "
                                + offenders);
            }
        }
    }

    @Test
    @DisplayName("the carve-out overrides the org.tzi.use. prefix, and only for that subtree")
    void carveOutAppliesOnlyToOurOwnSubtree() {
        assertFalse(IsolatedJarClassLoader.isIsolated(
                        "org.tzi.use.uncertainty.datatypes.UReal"),
                "the relocated vendored datatypes must be delegated, not isolated");
        assertFalse(IsolatedJarClassLoader.isIsolated(
                        "org.tzi.use.uncertainty.differential.UValue"),
                "the harness's own classes must be delegated, not isolated");

        assertTrue(IsolatedJarClassLoader.isIsolated("org.tzi.use.uml.ocl.value.URealValue"),
                "the product classes the harness compares against must still be isolated");
        assertTrue(IsolatedJarClassLoader.isIsolated("org.tzi.use.uml.ocl.type.TypeFactory"));
        assertTrue(IsolatedJarClassLoader.isIsolated("uDataTypes.UReal"),
                "the ORIGINAL package is what the historical jar carries, and stays isolated");
        assertTrue(IsolatedJarClassLoader.isIsolated("uDataTypes.SBoolean"));
    }
}
