package org.tzi.use.uncertainty.differential;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * A parent-last class loader for the historical jars.
 *
 * <h2>Why the obvious constructions do not work here</h2>
 * <ol>
 *   <li>{@code new URLClassLoader(urls)} inherits the system class loader as parent and delegates
 *       upwards first, so {@code org.tzi.use.*} resolves to the application's classes. Once a port
 *       exists this makes the harness compare the port against itself.</li>
 *   <li>{@code new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())} looks like the fix
 *       and is <em>not</em> one in this repository. {@code use-core} has a
 *       {@code module-info.java}, so Maven puts it on the module path and {@code use.core} is
 *       resolved into the boot layer. {@code jdk.internal.loader.BuiltinClassLoader#loadClassOrNull}
 *       consults a package-to-module map covering <em>every</em> boot-layer module before it
 *       considers its own parent, finds {@code org.tzi.use.uml.ocl.value} in module
 *       {@code use.core}, and delegates to the application class loader that defines it. The
 *       platform loader therefore happily returns application classes. This was measured, not
 *       assumed: with a platform-parented {@code URLClassLoader},
 *       {@code org.tzi.use.uml.ocl.value.Value} came back defined by
 *       {@code jdk.internal.loader.ClassLoaders$AppClassLoader}.</li>
 * </ol>
 *
 * <h2>What this loader does instead</h2>
 * For the two namespaces that collide — {@code org.tzi.use.} and {@code uDataTypes.} — it resolves
 * from its own URLs first and never delegates. Everything else (all of {@code java.*} and the JDK
 * modules) goes to the platform loader as usual, so the historical code still gets a working JDK.
 *
 * <p>The consequence worth stating plainly: a class loaded here can share a fully-qualified name
 * with an application class and remain a genuinely different {@code Class} object, which is the
 * whole premise of the differential harness.
 *
 * <p>Test-scoped. Not part of the product.
 */
final class IsolatedJarClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    /**
     * Package prefixes that must come from the jars and from nowhere else. {@code org.tzi.use.} is
     * the collision with the product; {@code uDataTypes.} is the historical uncertainty library,
     * isolated too so that a future dependency of the same name cannot shadow it.
     */
    private static final List<String> ISOLATED_PREFIXES =
            List.of("org.tzi.use.", "uDataTypes.");

    /**
     * Carved out of {@link #ISOLATED_PREFIXES}, and checked first.
     *
     * <p>{@code org.tzi.use.uncertainty.} is entirely <em>this project's own</em> code: the
     * differential harness itself, and — since B1 — the vendored uncertainty datatypes, which were
     * relocated out of package {@code uDataTypes} into
     * {@code org.tzi.use.uncertainty.datatypes}. It is a strict subtree of {@code org.tzi.use.},
     * so without this carve-out the loader would claim those names, fail to find them in the jars,
     * and — being parent-last with no fallback — throw {@link ClassNotFoundException} rather than
     * delegate.
     *
     * <p>The carve-out is safe because the subtree exists in <em>neither</em> historical jar, so
     * there is nothing there to isolate. That is not an assumption:
     * {@code HistoricalOracleIsolationTest} asserts it against the jars on every run.
     */
    private static final List<String> NON_ISOLATED_PREFIXES =
            List.of("org.tzi.use.uncertainty.");

    IsolatedJarClassLoader(String name, URL[] urls) {
        // Platform loader as parent: supplies java.* and the JDK modules, contains no application
        // classes of its own. The parent-last override below is what actually enforces isolation.
        super(name, urls, ClassLoader.getPlatformClassLoader());
    }

    /** True if {@code className} must be resolved from this loader's jars rather than delegated. */
    static boolean isIsolated(String className) {
        for (String prefix : NON_ISOLATED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        for (String prefix : ISOLATED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** The prefixes carved out of {@link #isolatedPrefixes()}. Exposed so tests can assert on it. */
    static List<String> nonIsolatedPrefixes() {
        return NON_ISOLATED_PREFIXES;
    }

    /** The prefixes this loader refuses to delegate. Exposed so tests can assert on the policy. */
    static List<String> isolatedPrefixes() {
        return ISOLATED_PREFIXES;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (isIsolated(name)) {
                    // Parent-last, and no fallback: if the jars do not have it, it does not exist.
                    // Falling back to the parent here would silently reintroduce self-comparison.
                    loaded = findClass(name);
                } else {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    public String toString() {
        return "IsolatedJarClassLoader[" + getName() + ", parent-last for "
                + String.join(", ", ISOLATED_PREFIXES) + ", urls=" + Arrays.toString(getURLs()) + "]";
    }
}
