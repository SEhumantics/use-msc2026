package org.tzi.use.uncertainty.gate;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The half of the acceptance gate that a command-line property cannot switch off.
 *
 * <p><b>Why this test exists.</b> Round 10's independent refutation
 * ({@code docs/port2/upstream-oracle-floor-verification.md} §3.7, defect <b>F-01</b>) defeated the
 * entire floor of the {@code -Pupstream-oracle} gate with one property and no edit to any tracked
 * file:
 *
 * <pre>
 * $ mvn -B verify -Djava.awt.headless=true -Dexec.args=-version
 * [driver] number of [floor] lines in the log: 0
 * [INFO] BUILD SUCCESS
 * </pre>
 *
 * <p>{@code exec-maven-plugin}'s {@code exec} goal declares {@code commandlineArgs} with the user
 * property {@code exec.args}, and that parameter <i>replaces</i> the configured {@code <arguments>}
 * list. So {@code java -version} ran in place of {@code scripts/UpstreamOracleFloor.java}: no
 * freshness stamp, no counts, no wiring check, no sentinel — and exit 0.
 * {@code <skip>false</skip>} had been pinned and was necessary but not sufficient.
 *
 * <p><b>A guard that the person being guarded can turn off is not a guard.</b> The fix has three
 * independent mechanisms, so that defeating one leaves the others standing:
 *
 * <ol>
 *   <li>both poms now pin every {@code exec:exec} parameter whose user property could silence or
 *       divert the check — {@code skip}, {@code commandlineArgs}, {@code async}, {@code timeout},
 *       {@code quietLogs}, {@code executable}, {@code workingDirectory} — which makes the
 *       corresponding {@code -D} inert, because a POM {@code <configuration>} element beats a
 *       {@code @Parameter(property=...)} default;</li>
 *   <li><b>this test</b>, which re-asserts that wiring from inside the {@code test} phase, where no
 *       {@code exec-maven-plugin} property reaches, and which additionally proves at <i>runtime</i>
 *       that the {@code initialize}-phase stamp execution really ran (§3 below);</li>
 *   <li>the receipt {@code target/upstream-oracle-floor.receipt}, written by the checker and
 *       verified on disk, after Maven has exited, by {@code scripts/upstream-oracle-gate.sh}.</li>
 * </ol>
 *
 * <p><b>Division of labour.</b> A test in {@code use-core} cannot see {@code use-gui}'s classpath or
 * its surefire reports — {@code use-gui} has not been built yet when this runs — and ground rule 2
 * forbids adding a test under {@code use-gui/src}. So this test asserts the <i>wiring</i> of both
 * poms and nothing about {@code use-gui}'s counts; the counts stay with
 * {@code scripts/UpstreamOracleFloor.java}, which each module runs at its own {@code verify} phase.
 * The two halves guard each other: the checker's WIRING clause fails if this file is deleted, and
 * this file fails if the checker's binding is removed or unpinned.
 *
 * <p><b>Cost, stated plainly.</b> This is one test class with one test method, so it moves the
 * gate's own figures by exactly {@code +1/+1} in one cell: the default build becomes 11 classes /
 * <b>211</b> methods and the profile 51 / <b>498</b>, and the floors in
 * {@code scripts/UpstreamOracleFloor.java} were raised in the same commit. 210 was the count of a
 * gate that could be silenced from the command line; 211 is the count of one that cannot.
 * Correctness beats a round number.
 *
 * <p>Every clause is checked and all failures are reported together, rather than stopping at the
 * first — the shape {@code DifferentialSweep.requireStagePass} uses, and the shape the checker uses.
 */
class UpstreamOracleGateWiringTest {

    private static final String PROFILE_ID = "upstream-oracle";
    private static final String FLOOR_CHECKER = "scripts/UpstreamOracleFloor.java";
    private static final String GATE_WRAPPER = "scripts/upstream-oracle-gate.sh";
    private static final String STAMP = "target/upstream-oracle-floor.stamp";
    private static final String RECEIPT = "target/upstream-oracle-floor.receipt";
    private static final List<String> MODULES = List.of("use-core", "use-gui");

    /**
     * Every {@code exec:exec} parameter that must be pinned in BOTH floor executions of BOTH poms,
     * with the user property it neutralises. {@code outputFile} is absent on purpose: no value of
     * it means "the Maven log", so it cannot be pinned — it is covered by
     * {@link #REQUIRED_TAMPER_TOKENS} instead.
     */
    private static final List<String[]> REQUIRED_PINS = List.of(
            new String[] {"<skip>false</skip>", "exec.skip"},
            new String[] {"<commandlineArgs>", "exec.args"},
            new String[] {"<async>false</async>", "exec.async"},
            new String[] {"<timeout>0</timeout>", "exec.timeout"},
            new String[] {"<quietLogs>false</quietLogs>", "exec.quietLogs"},
            new String[] {"<executable>${java.home}/bin/java</executable>", "exec.executable"},
            new String[] {"<workingDirectory>${project.basedir}</workingDirectory>",
                          "exec.workingdir"});

    /**
     * Pinning makes a {@code -Dexec.*} inert; inert is not loud. The verify-phase floor execution
     * of each pom must hand every one of these user properties back to the checker, which fails
     * the build when one of them arrives interpolated — so an ATTEMPT to switch the gate off is a
     * BUILD FAILURE rather than a silent no-op. {@code exec.outputFile} is the parameter that
     * cannot be pinned at all, so for it this is the whole defence.
     */
    private static final List<String> REQUIRED_TAMPER_TOKENS = List.of(
            // not a tamper property: the allow-set for the unactivatable-profile check (F-02),
            // which is what makes `mvn -Pupstream-oracle-typo` fail instead of running the
            // vacuous default gate.
            "--allow-profiles=${use.floor.allowProfiles}",
            "--exec-args=${exec.args}",
            "--exec-skip=${exec.skip}",
            "--exec-async=${exec.async}",
            "--exec-timeout=${exec.timeout}",
            "--exec-executable=${exec.executable}",
            "--exec-outputfile=${exec.outputFile}",
            "--exec-quietlogs=${exec.quietLogs}",
            "--exec-workingdir=${exec.workingdir}");

    private final List<String> violations = new ArrayList<>();

    @Test
    void theUpstreamOracleGateIsWiredAndCannotBeSilencedFromTheCommandLine() throws IOException {
        Path root = reactorRoot();

        // ---- 1. the two halves of the fix, and the checker itself, exist ------------------
        requireFile(root.resolve(FLOOR_CHECKER),
                "the floor checker is gone; the gate's counts would be unasserted (D-01).");
        requireFile(root.resolve(GATE_WRAPPER),
                "the gate wrapper is gone. The acceptance gate is DEFINED as that invocation"
                + " (F-02): it hard-codes the profile id, so `-Pupstream-oracle-typo` cannot"
                + " degrade the gate into a green default run. Hand-typing -P is not the gate.");
        String wrapper = readOrEmpty(root.resolve(GATE_WRAPPER));
        if (!wrapper.contains("-P" + PROFILE_ID)) {
            violations.add(GATE_WRAPPER + " no longer hard-codes -P" + PROFILE_ID
                    + ", so it is not a single source of truth for the profile id (F-02).");
        }

        // ---- 2. both poms: profile, engine, both floor executions, every pin --------------
        for (String module : MODULES) {
            checkPom(root.resolve(module).resolve("pom.xml"), module);
        }

        // ---- 3. RUNTIME: the initialize-phase stamp execution really ran ------------------
        // Everything above is text, and text cannot tell a bound execution from a silenced one.
        // This clause can, because the stamp is written at `initialize`, which is before the
        // `test` phase this test runs in.
        //   - after `mvn clean`, a silenced stamp execution leaves NO stamp at all;
        //   - without a clean, an old stamp survives — but so does the PREVIOUS build's receipt,
        //     which that build wrote at `verify`, i.e. AFTER its own stamp. So this build's stamp
        //     must not be older than the newest receipt on disk. A silenced stamp execution
        //     inverts that order and is caught.
        Path basedir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path stamp = basedir.resolve(STAMP);
        Path receipt = basedir.resolve(RECEIPT);
        if (!Files.isRegularFile(stamp)) {
            violations.add("RUNTIME: " + STAMP + " does not exist in " + basedir
                    + ". The `upstream-oracle-floor-stamp` execution did not run in THIS build,"
                    + " so the verify-phase check would have no way to tell this build's reports"
                    + " from an earlier -P" + PROFILE_ID + " run's. That is exactly what"
                    + " -Dexec.args=-version did (F-01).");
        } else if (Files.isRegularFile(receipt)) {
            long stampAt = Files.getLastModifiedTime(stamp).toMillis();
            long receiptAt = Files.getLastModifiedTime(receipt).toMillis();
            if (stampAt < receiptAt) {
                violations.add("RUNTIME: " + STAMP + " (" + stampAt + ") is OLDER than "
                        + RECEIPT + " (" + receiptAt + "). A receipt is written at `verify`,"
                        + " after the stamp of the same build, so this stamp is a previous"
                        + " build's: the `initialize` floor execution did not run in THIS build"
                        + " and the counts about to be checked are not this build's (F-01).");
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("The -P").append(PROFILE_ID).append(" acceptance gate is not wired as the")
              .append(" record claims — ").append(violations.size()).append(" violation(s).")
              .append(" This test IS the gate's guard against being silenced; do not weaken it")
              .append(" to make a build pass. See docs/port2/upstream-oracle-floor-verification.md")
              .append(" F-01/F-02 and docs/port2/harness-contract.md sec. 0.\n");
            for (int i = 0; i < violations.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append('\n');
            }
            fail(sb.toString());
        }
    }

    /**
     * Asserts a module's pom, from its text with XML comments stripped and all whitespace removed.
     * Text and not the effective model on purpose: this must hold for the other module too, and a
     * test JVM in {@code use-core} has no access to {@code use-gui}'s model.
     */
    private void checkPom(Path pom, String module) {
        if (!Files.isRegularFile(pom)) {
            violations.add(module + "/pom.xml does not exist at " + pom);
            return;
        }
        String x = readOrEmpty(pom).replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", "");

        int p0 = x.indexOf("<profiles>");
        int p1 = x.indexOf("</profiles>");
        if (p0 < 0 || p1 < p0) {
            violations.add(module + "/pom.xml HAS NO <profiles> ELEMENT — the " + PROFILE_ID
                    + " profile has been deleted, so -P" + PROFILE_ID + " would silently collect"
                    + " the default build's tests in this module (D-01's merge accident).");
        } else {
            String profiles = x.substring(p0, p1 + "</profiles>".length());
            if (!profiles.contains("<id>" + PROFILE_ID + "</id>")) {
                violations.add(module + "/pom.xml has <profiles> but no <id>" + PROFILE_ID
                        + "</id> inside it, so -P" + PROFILE_ID + " activates nothing here.");
            }
            if (!profiles.contains("<artifactId>junit-vintage-engine</artifactId>")) {
                violations.add(module + "/pom.xml's " + PROFILE_ID + " profile no longer declares"
                        + " junit-vintage-engine; without that engine the JUnit Platform collects"
                        + " none of upstream's JUnit 3/4 tree.");
            }
            if (count(x, "<artifactId>junit-vintage-engine</artifactId>")
                    != count(profiles, "<artifactId>junit-vintage-engine</artifactId>")) {
                violations.add(module + "/pom.xml declares junit-vintage-engine OUTSIDE the "
                        + PROFILE_ID + " profile. The default product build must stay"
                        + " vintage-free.");
            }
        }
        if (x.contains("<activeByDefault>true</activeByDefault>")) {
            violations.add(module + "/pom.xml activates a profile by default; the upstream oracle"
                    + " must be requested on purpose.");
        }
        if (!x.contains("<properties><use.upstreamOracle.effective>true"
                + "</use.upstreamOracle.effective></properties>")) {
            violations.add(module + "/pom.xml's " + PROFILE_ID + " profile does not set"
                    + " use.upstreamOracle.effective=true, so the gate loses its"
                    + " requested-but-not-effective detector.");
        }
        if (!x.contains(FLOOR_CHECKER)) {
            violations.add(module + "/pom.xml no longer runs " + FLOOR_CHECKER
                    + "; that module's counts would go unasserted — the D-01 defect itself.");
        }
        if (!x.contains("<id>upstream-oracle-floor</id><phase>verify</phase>")) {
            violations.add(module + "/pom.xml has no `upstream-oracle-floor` execution bound to"
                    + " the `verify` phase.");
        }
        if (!x.contains("<id>upstream-oracle-floor-stamp</id><phase>initialize</phase>")) {
            violations.add(module + "/pom.xml has no `upstream-oracle-floor-stamp` execution bound"
                    + " to the `initialize` phase, so stale reports from an earlier -P"
                    + PROFILE_ID + " run could be counted as this build's.");
        }
        if (!x.contains("--module=" + module)) {
            violations.add(module + "/pom.xml's floor execution does not pass --module=" + module
                    + ".");
        }
        for (String[] pin : REQUIRED_PINS) {
            int n = count(x, pin[0]);
            if (n < 2) {
                violations.add(module + "/pom.xml carries " + pin[0] + " " + n + " time(s), needs 2"
                        + " — one per floor execution. Unpinned, the user property -D" + pin[1]
                        + " is honoured on the command line and the floor check can be silenced"
                        + " or diverted with no edit to any tracked file (F-01).");
            }
        }
        for (String token : REQUIRED_TAMPER_TOKENS) {
            if (!x.contains(token)) {
                violations.add(module + "/pom.xml's floor execution does not pass " + token
                        + ", so that -D would go undetected instead of failing the build. An"
                        + " attempt to silence the gate must be loud, not merely inert (F-01).");
            }
        }
    }

    private void requireFile(Path p, String why) {
        if (!Files.isRegularFile(p)) {
            violations.add("missing " + p + " — " + why);
        }
    }

    /** Walks up from the surefire working directory to the reactor root. */
    private Path reactorRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("use-core/pom.xml"))
                    && Files.isRegularFile(p.resolve("use-gui/pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("cannot find the reactor root above " + dir
                + " (looked for a directory holding both use-core/pom.xml and use-gui/pom.xml)");
    }

    private String readOrEmpty(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add("cannot read " + p + " (" + e + ")");
            return "";
        }
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
