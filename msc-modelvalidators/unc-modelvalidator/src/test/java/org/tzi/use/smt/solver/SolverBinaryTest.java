package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.Test;

public class SolverBinaryTest {

    private static final String VENDORED_BINARY_SHA256 =
            "b4e0b3483ce37817230b20d6cad48390eb6a3aefde1d93342ad6dc763f24bc23";

    @Test
    public void resolvesTheVendoredBinaryFromAnyWorkingDirectory() {
        SolverBinary binary = SolverBinary.resolve();
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
}
