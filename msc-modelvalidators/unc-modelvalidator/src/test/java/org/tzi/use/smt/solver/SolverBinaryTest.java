package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HexFormat;
import org.junit.Test;

public class SolverBinaryTest {

    private static final String VENDORED_BINARY_SHA256 =
            "b4e0b3483ce37817230b20d6cad48390eb6a3aefde1d93342ad6dc763f24bc23";

    @Test
    public void resolvesTheVendoredBinaryFromAnyWorkingDirectory() {
        SolverBinary binary = SolverBinary.resolve();
        System.out.println("Resolved solver: " + binary.path());
        assertTrue("solver path must exist: " + binary.path(), Files.isExecutable(binary.path()));
        assertTrue("must resolve the vendored copy, not one from PATH: " + binary.path(),
                binary.path().toString().replace('\\', '/').contains("tools/z3/bin/z3"));
    }

    /**
     * The vendored binary is experimental apparatus. If it is ever swapped, every recorded result
     * becomes incomparable, so its identity is asserted rather than assumed.
     */
    @Test
    public void vendoredBinaryMatchesItsRecordedChecksum() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(SolverBinary.resolve().path()));
        assertEquals(VENDORED_BINARY_SHA256, HexFormat.of().formatHex(digest));
    }

    /** Asserts against the pin file, rather than the self-consistent SolverBinary accessors. */
    @Test
    public void reportedVersionMatchesThePinFile() throws Exception {
        java.util.Properties pin = new java.util.Properties();
        try (java.io.InputStream in = SolverBinary.class.getResourceAsStream("/solver.properties")) {
            pin.load(in);
        }
        assertEquals(pin.getProperty("solver.expectedVersion").strip(),
                SolverBinary.resolve().reportedVersion());
    }

    @Test
    public void aVersionMismatchIsRejectedWithAnActionableMessage() {
        String realPath = SolverBinary.resolve().path().toString();
        try {
            SolverBinary.resolveFrom(realPath, "0.0.0");
            fail("expected SolverConfigurationException");
        } catch (SolverConfigurationException expected) {
            assertTrue(expected.getMessage().contains("0.0.0"));
            assertTrue(expected.getMessage().contains("version mismatch"));
        }
    }

    @Test
    public void missingBinaryFailsLoudlyWithAnActionableMessage() {
        try {
            SolverBinary.resolveFrom("definitely-not-a-real-solver-binary", "5.1.0");
            fail("expected SolverConfigurationException");
        } catch (SolverConfigurationException expected) {
            assertTrue(expected.getMessage().contains("definitely-not-a-real-solver-binary"));
            assertTrue(expected.getMessage().contains("solver.properties"));
        }
    }

    /**
     * Regression guard for the blocking-read bug fixed 2026-08-24 (master plan Appendix B):
     * {@code probeVersion} used to call {@code readAllBytes()} on the child's stdout before
     * {@code waitFor(timeout)}, which blocks until the child exits and makes the timeout
     * unreachable. A hung solver would hang solver resolution forever instead of failing after
     * the configured timeout.
     *
     * <p>Points {@code resolveFrom} at a script that ignores its arguments and sleeps, so
     * {@code binary --version} never returns. Uses the package-visible timeout overload with a
     * short duration so the test is fast and deterministic rather than waiting out the real
     * 30-second production timeout.
     */
    @Test(timeout = 15_000)
    public void aHangingBinaryIsReportedAsTimedOutRatherThanHangingForever() throws Exception {
        Path script = Files.createTempFile("msc-hanging-solver-", ".sh");
        Files.writeString(script, "#!/bin/sh\nsleep 999\n");
        Files.setPosixFilePermissions(script, EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));

        try {
            SolverBinary.resolveFrom(script.toString(), "5.1.0", Duration.ofMillis(200));
            fail("expected SolverConfigurationException");
        } catch (SolverConfigurationException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Timed out"));
        } finally {
            Files.deleteIfExists(script);
        }
    }
}