package org.tzi.use.smt.solver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the pinned SMT solver executable and asserts its version.
 *
 * <p>Resolution order for the path: the system property {@code msc.solver.path}, then the
 * environment variable {@code MSC_SOLVER_PATH}, then {@code solver.path} from
 * {@code solver.properties} on the classpath. A bare name is resolved against {@code PATH}.
 *
 * <p>A version mismatch is a hard failure. The whole point of pinning is that an unnoticed solver
 * change cannot silently alter experimental results.
 */
public final class SolverBinary {

    private static final String PIN_RESOURCE = "/solver.properties";
    private static final Pattern VERSION = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    /** How far up the directory tree a repository-relative solver path is searched. */
    private static final int MAX_UPWARD_SEARCH = 8;

    private final Path path;
    private final String expectedVersion;
    private final String reportedVersion;

    private SolverBinary(Path path, String expectedVersion, String reportedVersion) {
        this.path = path;
        this.expectedVersion = expectedVersion;
        this.reportedVersion = reportedVersion;
    }

    public static SolverBinary resolve() {
        Properties pin = loadPin();
        String configured = System.getProperty("msc.solver.path");
        if (configured == null) {
            configured = System.getenv("MSC_SOLVER_PATH");
        }
        if (configured == null) {
            configured = pin.getProperty("solver.path");
        }
        String expected = pin.getProperty("solver.expectedVersion");
        if (configured == null || expected == null) {
            throw new SolverConfigurationException(
                    "solver.properties must define solver.path and solver.expectedVersion");
        }
        return resolveFrom(configured, expected);
    }

    public static SolverBinary resolveFrom(String pathOrName, String expectedVersion) {
        Path resolved = locate(pathOrName);
        if (resolved == null) {
            throw new SolverConfigurationException(
                    "SMT solver '" + pathOrName + "' was not found on PATH and is not an executable"
                            + " file. Install it, or override the location with -Dmsc.solver.path=..."
                            + " or MSC_SOLVER_PATH, or correct solver.properties.");
        }
        String reported = probeVersion(resolved);
        if (!expectedVersion.equals(reported)) {
            throw new SolverConfigurationException(
                    "SMT solver version mismatch: solver.properties pins " + expectedVersion
                            + " but " + resolved + " reports " + reported
                            + ". Results from a different solver version are not comparable;"
                            + " install the pinned version or deliberately update solver.properties.");
        }
        return new SolverBinary(resolved, expectedVersion, reported);
    }

    private static Path locate(String pathOrName) {
        Path direct = Paths.get(pathOrName);
        if (direct.isAbsolute()) {
            return Files.isExecutable(direct) ? direct : null;
        }
        if (pathOrName.contains("/")) {
            // Surefire runs in the module directory while a plain Maven run uses the reactor root.
            // Resolve repository-relative paths from either location by walking upward.
            Path base = Paths.get("").toAbsolutePath();
            for (int depth = 0; depth <= MAX_UPWARD_SEARCH && base != null; depth++) {
                Path candidate = base.resolve(direct);
                if (Files.isExecutable(candidate)) {
                    return candidate.normalize();
                }
                base = base.getParent();
            }
            return null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String entry : pathEnv.split(java.io.File.pathSeparator)) {
            Path candidate = Paths.get(entry, pathOrName);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String probeVersion(Path binary) {
        try {
            Process process = new ProcessBuilder(binary.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SolverConfigurationException(
                        "Timed out probing the version of " + binary);
            }
            Matcher matcher = VERSION.matcher(output);
            if (!matcher.find()) {
                throw new SolverConfigurationException(
                        "Could not parse a version from '" + binary + " --version' output: " + output);
            }
            return matcher.group(1);
        } catch (IOException e) {
            throw new SolverConfigurationException("Failed to execute " + binary, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolverConfigurationException("Interrupted probing " + binary, e);
        }
    }

    private static Properties loadPin() {
        Properties properties = new Properties();
        try (InputStream in = SolverBinary.class.getResourceAsStream(PIN_RESOURCE)) {
            if (in == null) {
                throw new SolverConfigurationException(
                        "solver.properties was not found on the classpath at " + PIN_RESOURCE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new SolverConfigurationException("Failed to read solver.properties", e);
        }
        return properties;
    }

    public Path path() {
        return path;
    }

    public String expectedVersion() {
        return expectedVersion;
    }

    public String reportedVersion() {
        return reportedVersion;
    }
}
