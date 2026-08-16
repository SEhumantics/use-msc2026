package org.tzi.use.uncertainty.differential;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the historical USE "uncertainty" implementation out of the 2018-era jars, inside a class
 * loader that is isolated from the application class loader.
 *
 * <h2>Why the loader must be isolated</h2>
 * The ported classes carry the <em>same</em> fully-qualified names as the historical ones
 * ({@code org.tzi.use.uml.ocl.value.URealValue} and friends). A plain
 * {@code new URLClassLoader(urls)} inherits the system class loader as its parent and therefore
 * delegates <em>upwards first</em>: once a port exists on the test classpath, every historical name
 * would resolve to the <em>ported</em> class and the harness would compare the port against itself.
 * That failure mode is silent and looks green, which makes it the worst one available.
 *
 * <p>Parenting the loader on {@link ClassLoader#getPlatformClassLoader()} is the usual remedy and it
 * is <em>not sufficient in this repository</em>: {@code use-core} is a JPMS module on the module
 * path, and a builtin loader resolves any package in the boot-layer module graph before consulting
 * its parent, so the platform loader returns application classes. This oracle therefore uses
 * {@link IsolatedJarClassLoader}, which is parent-last for {@code org.tzi.use.*} and
 * {@code uDataTypes.*}. See that class for the measurement.
 *
 * <p>{@link #open()} additionally asserts, at construction time, that every historical class it
 * resolved was in fact defined by its own loader — see {@link #assertIsolated(Class)}. That guard is
 * what caught the platform-parent problem in the first place. The dedicated regression test is
 * {@code HistoricalOracleIsolationTest}.
 *
 * <h2>Failure policy</h2>
 * A missing jar, an unreadable jar or a sha256 mismatch throws
 * {@link HistoricalOracleUnavailableException} naming every path that was tried. The oracle never
 * degrades to a no-op and never signals "skip": a harness that quietly disables itself is
 * indistinguishable from a harness that passes.
 *
 * <p>Test-scoped. Not part of the product, not on the product classpath.
 */
public final class HistoricalOracle implements Candidate {

    /** File name of the historical USE core jar, as committed under {@code src/test/resources/historical/}. */
    public static final String USE_JAR = "use.jar";

    /** File name of the historical uncertainty jar (package {@code uDataTypes}). */
    public static final String UNCERTAINTY_JAR = "atenearesearchgroup.uncertainty.jar";

    /** Expected sha256 of {@link #USE_JAR}. */
    public static final String USE_JAR_SHA256 =
            "80ac8ae433b8345677472019991356950f094f4a104cfbce1f75783a7308788d";

    /** Expected sha256 of {@link #UNCERTAINTY_JAR}. */
    public static final String UNCERTAINTY_JAR_SHA256 =
            "53b2a43feb0a0a39844a60278dd80a7d4b975ef324fb05c6db28831e835e59d0";

    /** Directory, relative to the test class output root, holding the two jars. */
    public static final String RESOURCE_DIR = "historical";

    /**
     * Optional override for the directory holding the jars. Intended for out-of-tree runs only.
     *
     * <p>When set it is <em>authoritative</em>: no other location is consulted, and the sha256 check
     * still applies. Pointing it at a directory without the jars, or at the wrong jars, therefore
     * fails loudly instead of quietly falling back to the committed copies.
     */
    public static final String JAR_DIR_PROPERTY = "use.historical.jars.dir";

    private static final String VALUE_PKG = "org.tzi.use.uml.ocl.value.";

    private final URLClassLoader loader;
    private final Path useJar;
    private final Path uncertaintyJar;
    private final List<Path> temporaries;
    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private final Class<?> valueClass;
    private volatile boolean closed;

    // ------------------------------------------------------------------ construction

    private HistoricalOracle(URLClassLoader loader, Path useJar, Path uncertaintyJar,
                             List<Path> temporaries) {
        this.loader = loader;
        this.useJar = useJar;
        this.uncertaintyJar = uncertaintyJar;
        this.temporaries = temporaries;
        this.valueClass = load("Value");
        // Resolve, and isolation-check, every class the harness depends on. Doing this eagerly
        // means a delegation leak is caught at open() rather than in the middle of a sweep.
        for (String simple : new String[] {
                "Value", "URealValue", "UIntegerValue", "UBooleanValue", "UStringValue",
                "UncertainValue", "UncertainBooleanValue",
                "RealValue", "IntegerValue", "BooleanValue", "StringValue", "SequenceValue" }) {
            assertIsolated(load(simple));
        }
    }

    /**
     * Opens the oracle: locates the two jars, verifies their sha256, and builds the isolated loader.
     *
     * @throws HistoricalOracleUnavailableException if a jar cannot be found, cannot be read, or does
     *                                              not hash to the recorded value.
     */
    public static HistoricalOracle open() {
        List<String> tried = new ArrayList<>();
        List<Path> temporaries = new ArrayList<>();
        Path useJar = materialise(USE_JAR, USE_JAR_SHA256, tried, temporaries);
        Path uncertaintyJar = materialise(UNCERTAINTY_JAR, UNCERTAINTY_JAR_SHA256, tried, temporaries);

        URL[] urls;
        try {
            urls = new URL[] { useJar.toUri().toURL(), uncertaintyJar.toUri().toURL() };
        } catch (MalformedURLException e) {
            throw new HistoricalOracleUnavailableException(
                    "cannot turn historical jar paths into URLs: " + useJar + ", " + uncertaintyJar, e);
        }

        // Parent-LAST for org.tzi.use.* and uDataTypes.*, platform loader for everything else.
        // A plain URLClassLoader will not do here, with either parent -- see IsolatedJarClassLoader
        // for the measured reason why the platform-parent version still returns application classes
        // in this JPMS reactor.
        URLClassLoader isolated = new IsolatedJarClassLoader("historical-oracle", urls);
        try {
            return new HistoricalOracle(isolated, useJar, uncertaintyJar, temporaries);
        } catch (RuntimeException | Error e) {
            try {
                isolated.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ jar location and hashing

    /**
     * Finds {@code fileName}, verifies its digest, and returns a readable filesystem path. If the
     * only copy found is not on the filesystem (e.g. nested in a jar), it is extracted to a
     * temporary file registered for deletion by {@link #close()}.
     */
    private static Path materialise(String fileName, String expectedSha256, List<String> tried,
                                    List<Path> temporaries) {
        for (Path candidate : candidateLocations(fileName, tried)) {
            if (candidate != null && Files.isReadable(candidate)) {
                verifyDigest(candidate, fileName, expectedSha256);
                return candidate;
            }
        }
        if (System.getProperty(JAR_DIR_PROPERTY) != null) {
            // The override is authoritative; do not fall back to the committed copies behind the
            // operator's back, because that would hide a mis-pointed override.
            throw new HistoricalOracleUnavailableException(
                    "historical oracle jar '" + fileName + "' was not found and -D" + JAR_DIR_PROPERTY
                            + " is set, so no other location was consulted. Paths tried:\n  "
                            + String.join("\n  ", tried));
        }
        // Last resort: pull the bytes through the class loader and spill them to a temp file.
        String resource = RESOURCE_DIR + "/" + fileName;
        tried.add("classloader resource " + resource);
        try (InputStream in = HistoricalOracle.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                Path tmp = Files.createTempFile("historical-oracle-", "-" + fileName);
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                temporaries.add(tmp);
                verifyDigest(tmp, fileName, expectedSha256);
                return tmp;
            }
        } catch (IOException e) {
            throw new HistoricalOracleUnavailableException(
                    "failed to extract historical jar '" + fileName + "' from the class loader", e);
        }
        throw new HistoricalOracleUnavailableException(
                "historical oracle jar '" + fileName + "' was not found. It must be committed at "
                        + "use-core/src/test/resources/" + RESOURCE_DIR + "/" + fileName
                        + " so that Maven copies it to target/test-classes/" + RESOURCE_DIR + "/. "
                        + "Paths tried, in order:\n  " + String.join("\n  ", tried));
    }

    /** Ordered lookup strategies; every one is recorded in {@code tried} for the failure message. */
    private static List<Path> candidateLocations(String fileName, List<String> tried) {
        Set<Path> out = new LinkedHashSet<>();

        String override = System.getProperty(JAR_DIR_PROPERTY);
        if (override != null) {
            Path p = Paths.get(override).resolve(fileName).toAbsolutePath();
            tried.add("-D" + JAR_DIR_PROPERTY + " (authoritative) -> " + p);
            return new ArrayList<>(java.util.Collections.singletonList(p));
        }

        // The directory this very class was loaded from, i.e. target/test-classes.
        CodeSource cs = HistoricalOracle.class.getProtectionDomain().getCodeSource();
        if (cs != null && cs.getLocation() != null) {
            try {
                Path root = Paths.get(cs.getLocation().toURI());
                if (Files.isDirectory(root)) {
                    Path p = root.resolve(RESOURCE_DIR).resolve(fileName).toAbsolutePath();
                    tried.add("code source of HistoricalOracle -> " + p);
                    out.add(p);
                }
            } catch (URISyntaxException | IllegalArgumentException ignored) {
                tried.add("code source of HistoricalOracle -> unusable (" + cs.getLocation() + ")");
            }
        } else {
            tried.add("code source of HistoricalOracle -> unavailable");
        }

        // Resource lookup, both module-relative and via the class loader.
        for (URL url : new URL[] {
                HistoricalOracle.class.getResource("/" + RESOURCE_DIR + "/" + fileName),
                HistoricalOracle.class.getClassLoader().getResource(RESOURCE_DIR + "/" + fileName) }) {
            if (url == null) {
                continue;
            }
            if ("file".equals(url.getProtocol())) {
                try {
                    Path p = Paths.get(url.toURI()).toAbsolutePath();
                    tried.add("resource URL -> " + p);
                    out.add(p);
                } catch (URISyntaxException | IllegalArgumentException ignored) {
                    tried.add("resource URL -> unusable (" + url + ")");
                }
            } else {
                tried.add("resource URL -> non-file protocol (" + url + ")");
            }
        }

        // Working-directory fallbacks, so an IDE or a bare `java` run still finds the jars.
        for (String rel : new String[] {
                "use-core/target/test-classes/" + RESOURCE_DIR + "/" + fileName,
                "target/test-classes/" + RESOURCE_DIR + "/" + fileName,
                "use-core/src/test/resources/" + RESOURCE_DIR + "/" + fileName,
                "src/test/resources/" + RESOURCE_DIR + "/" + fileName }) {
            Path p = Paths.get(rel).toAbsolutePath();
            tried.add("relative to cwd -> " + p);
            out.add(p);
        }

        return new ArrayList<>(out);
    }

    private static void verifyDigest(Path path, String fileName, String expected) {
        String actual = sha256(path);
        if (!expected.equalsIgnoreCase(actual)) {
            throw new HistoricalOracleUnavailableException(
                    "historical oracle jar '" + fileName + "' at " + path
                            + " does not match the recorded digest.\n  expected sha256 " + expected
                            + "\n  actual   sha256 " + actual
                            + "\nThe oracle refuses to run against an unverified jar.");
        }
    }

    /** sha256 of a file, lower-case hex. Public so reports can restate what was actually loaded. */
    public static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            try (InputStream in = Files.newInputStream(path)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest.digest()) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path + " for hashing", e);
        }
    }

    // ------------------------------------------------------------------ isolation

    /**
     * Guarantees the class really came out of the isolated loader and not from the application
     * class loader. Once a port exists, a regression here is exactly the self-comparison failure
     * this harness exists to prevent, so it is checked at open() and not merely in a test.
     */
    private void assertIsolated(Class<?> historical) {
        if (historical.getClassLoader() != loader) {
            throw new HistoricalOracleUnavailableException(
                    "class " + historical.getName() + " was defined by " + historical.getClassLoader()
                            + " rather than the isolated historical loader " + loader
                            + ". The oracle would be comparing the port against itself; refusing to continue.");
        }
    }

    /** The isolated loader. Exposed so the isolation test can assert on it directly. */
    public ClassLoader loader() {
        return loader;
    }

    /** Resolves a historical class in {@code org.tzi.use.uml.ocl.value} by simple name. */
    public Class<?> historicalClass(String simpleName) {
        return load(simpleName);
    }

    /** Absolute path of the {@code use.jar} actually loaded. */
    public Path useJarPath() {
        return useJar;
    }

    /** Absolute path of the uncertainty jar actually loaded. */
    public Path uncertaintyJarPath() {
        return uncertaintyJar;
    }

    private Class<?> load(String simpleName) {
        return classes.computeIfAbsent(simpleName, name -> {
            checkOpen();
            try {
                return Class.forName(VALUE_PKG + name, true, loader);
            } catch (ClassNotFoundException e) {
                throw new HistoricalOracleUnavailableException(
                        "historical class " + VALUE_PKG + name + " not present in "
                                + useJar + " / " + uncertaintyJar, e);
            }
        });
    }

    // ------------------------------------------------------------------ Candidate

    @Override
    public String name() {
        return "historical";
    }

    @Override
    public boolean supports(UOp op) {
        try {
            resolve(op);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public UValue invoke(UOp op, List<UValue> args) throws Throwable {
        checkOpen();
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(args, "args");
        if (args.size() != op.arity()) {
            throw new IllegalArgumentException(
                    op.key() + " needs " + op.arity() + " values (receiver + " + op.params().size()
                            + " params) but got " + args.size());
        }
        Method method = resolve(op);
        Object receiver = toHistorical(args.get(0));
        Class<?> receiverClass = load(op.receiverType());
        if (!receiverClass.isInstance(receiver)) {
            throw new IllegalArgumentException(
                    op.key() + " expects a receiver of " + receiverClass.getName() + " but the supplied "
                            + args.get(0).canonical() + " maps to " + receiver.getClass().getName());
        }
        Object[] marshalled = new Object[op.params().size()];
        for (int i = 0; i < marshalled.length; i++) {
            marshalled[i] = marshal(op.params().get(i), args.get(i + 1));
        }
        try {
            return fromHistorical(method.invoke(receiver, marshalled));
        } catch (InvocationTargetException e) {
            // Surface what the historical code actually threw, not the reflection wrapper.
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    /** Convenience for the common shape: receiver plus zero or more {@code Value} arguments. */
    public UValue call(String receiverType, String methodName, UValue receiver, UValue... valueArgs) {
        UOp.ParamKind[] kinds = new UOp.ParamKind[valueArgs.length];
        Arrays.fill(kinds, UOp.ParamKind.VALUE);
        List<UValue> all = new ArrayList<>(1 + valueArgs.length);
        all.add(receiver);
        all.addAll(Arrays.asList(valueArgs));
        try {
            return invoke(UOp.of(receiverType, methodName, kinds), all);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(receiverType + "." + methodName + " threw", t);
        }
    }

    private Method resolve(UOp op) {
        return methods.computeIfAbsent(op.key(), key -> {
            Class<?> owner = load(op.receiverType());
            Class<?>[] paramTypes = new Class<?>[op.params().size()];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = parameterClass(op.params().get(i));
            }
            try {
                return owner.getMethod(op.methodName(), paramTypes);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "historical " + owner.getName() + " has no method " + op.key(), e);
            }
        });
    }

    private Class<?> parameterClass(UOp.ParamKind kind) {
        switch (kind) {
            case VALUE:  return valueClass;
            case INT:    return int.class;
            case DOUBLE: return double.class;
            case FLOAT:  return float.class;
            default:     throw new IllegalArgumentException("unhandled param kind " + kind);
        }
    }

    private Object marshal(UOp.ParamKind kind, UValue value) {
        switch (kind) {
            case VALUE:  return toHistorical(value);
            case INT:    return numeric(value).intValue();
            case DOUBLE: return numeric(value).doubleValue();
            case FLOAT:  return numeric(value).floatValue();
            default:     throw new IllegalArgumentException("unhandled param kind " + kind);
        }
    }

    private Number numeric(UValue value) {
        switch (value.kind()) {
            case INTEGER:
            case UINTEGER:
                return value.asInt();
            case REAL:
            case UREAL:
                return value.asDouble();
            default:
                throw new IllegalArgumentException(
                        "a primitive parameter needs a numeric UValue, got " + value.canonical());
        }
    }

    // ------------------------------------------------------------------ marshalling

    /** Builds the historical object corresponding to a plain-Java {@link UValue}. */
    public Object toHistorical(UValue value) {
        checkOpen();
        Objects.requireNonNull(value, "value");
        try {
            switch (value.kind()) {
                case UREAL:
                    return ctor("URealValue", double.class, double.class)
                            .newInstance(value.asDouble(), value.uncertainty());
                case UINTEGER:
                    return ctor("UIntegerValue", int.class, double.class)
                            .newInstance(value.asInt(), value.uncertainty());
                case UBOOLEAN:
                    // The (uDataTypes.UBoolean) constructor is package-private; the documented
                    // public factory is used instead of setAccessible.
                    return load("UBooleanValue").getMethod("valueOf", boolean.class, double.class)
                            .invoke(null, value.asBoolean(), value.probability());
                case USTRING:
                    return ctor("UStringValue", String.class, double.class)
                            .newInstance(value.asString(), value.confidence());
                case REAL:
                    return ctor("RealValue", double.class).newInstance(value.asDouble());
                case INTEGER:
                    return load("IntegerValue").getMethod("valueOf", int.class)
                            .invoke(null, value.asInt());
                case BOOLEAN:
                    return load("BooleanValue").getMethod("get", boolean.class)
                            .invoke(null, value.asBoolean());
                case STRING:
                    return ctor("StringValue", String.class).newInstance(value.asString());
                default:
                    throw new IllegalArgumentException(
                            "cannot construct a historical value for kind " + value.kind());
            }
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("historical constructor threw for " + value.canonical(),
                    e.getCause() == null ? e : e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot construct historical " + value.canonical(), e);
        }
    }

    /** Unwraps a historical object into a plain-Java {@link UValue}. Never returns a reflective type. */
    public UValue fromHistorical(Object result) {
        if (result == null) {
            return UValue.nullValue();
        }
        String className = result.getClass().getName();
        try {
            switch (className) {
                case VALUE_PKG + "URealValue":
                    return UValue.uReal(d(result, "value"), d(result, "uncertainty"));
                case VALUE_PKG + "UIntegerValue":
                    return UValue.uInteger(i(result, "value"), d(result, "uncertainty"));
                case VALUE_PKG + "UBooleanValue":
                    return UValue.uBoolean(b(result, "value"), d(result, "probability"));
                case VALUE_PKG + "UStringValue":
                    return UValue.uString(s(result, "value"), d(result, "confidence"));
                case VALUE_PKG + "RealValue":
                    return UValue.real(d(result, "value"));
                case VALUE_PKG + "IntegerValue":
                    return UValue.integer(i(result, "value"));
                case VALUE_PKG + "BooleanValue":
                    return UValue.bool(b(result, "value"));
                case VALUE_PKG + "StringValue":
                    return UValue.string(s(result, "value"));
                case VALUE_PKG + "SequenceValue": {
                    List<UValue> items = new ArrayList<>();
                    Object it = result.getClass().getMethod("iterator").invoke(result);
                    Iterator<?> iterator = (Iterator<?>) it;
                    while (iterator.hasNext()) {
                        items.add(fromHistorical(iterator.next()));
                    }
                    return UValue.sequence(items);
                }
                default:
                    if (result instanceof Boolean) {
                        return UValue.bool((Boolean) result);
                    }
                    if (result instanceof Integer) {
                        return UValue.integer((Integer) result);
                    }
                    if (result instanceof Double) {
                        return UValue.real((Double) result);
                    }
                    if (result instanceof CharSequence) {
                        return UValue.string(result.toString());
                    }
                    return UValue.opaque(className, String.valueOf(result));
            }
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("historical accessor threw while unwrapping " + className,
                    e.getCause() == null ? e : e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot unwrap historical " + className, e);
        }
    }

    private Constructor<?> ctor(String simpleName, Class<?>... params) throws NoSuchMethodException {
        return load(simpleName).getConstructor(params);
    }

    private double d(Object target, String accessor) throws ReflectiveOperationException {
        return (Double) target.getClass().getMethod(accessor).invoke(target);
    }

    private int i(Object target, String accessor) throws ReflectiveOperationException {
        return (Integer) target.getClass().getMethod(accessor).invoke(target);
    }

    private boolean b(Object target, String accessor) throws ReflectiveOperationException {
        return (Boolean) target.getClass().getMethod(accessor).invoke(target);
    }

    private String s(Object target, String accessor) throws ReflectiveOperationException {
        return (String) target.getClass().getMethod(accessor).invoke(target);
    }

    // ------------------------------------------------------------------ lifecycle

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("HistoricalOracle has already been closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            loader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot close the isolated historical class loader", e);
        } finally {
            for (Path tmp : temporaries) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // A leftover temp file is not worth failing a test run over.
                }
            }
        }
    }

    /** Digests of the jars actually loaded, keyed by file name; goes verbatim into the report header. */
    public Map<String, String> loadedDigests() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(USE_JAR, sha256(useJar));
        out.put(UNCERTAINTY_JAR, sha256(uncertaintyJar));
        return out;
    }

    @Override
    public String toString() {
        return "HistoricalOracle[" + useJar + ", " + uncertaintyJar + ", loader=" + loader + "]"
                + (closed ? " CLOSED" : "");
    }

    /**
     * Thrown when the oracle cannot be established. Deliberately unchecked and deliberately fatal:
     * there is no "skip" path.
     */
    public static final class HistoricalOracleUnavailableException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public HistoricalOracleUnavailableException(String message) {
            super(message);
        }

        public HistoricalOracleUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
