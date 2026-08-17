// =====================================================================================
// UpstreamOracleFloor — the asserted floor of the -Pupstream-oracle acceptance gate.
//
// Answers static-review defect D-01 (docs/port2/upstream-oracle-static-review.md:171):
//
//     "the gate has no asserted floor; it can silently revert to vacuity and still be
//      BUILD SUCCESS ... the rule is 'quote the deduplicated class and method counts' —
//      a HUMAN-READ number."
//
// and holds the gate to harness-contract.md sec. 8 step 2: "A floor chosen after the run is
// not a floor", "0 is rejected outright".
//
// RUN BY MAVEN, NOT BY A HUMAN. Bound in use-core/pom.xml and use-gui/pom.xml as
// exec-maven-plugin execution `upstream-oracle-floor`, phase `verify`, <skip>false</skip>.
// Java single-file source mode (JEP 330) — no dependency beyond the JDK that runs Maven, so
// the gate cannot be disabled by an artifact failing to resolve.
//
// Usage:
//   java scripts/UpstreamOracleFloor.java --stamp=true --module-dir=<dir>     (phase initialize)
//   java scripts/UpstreamOracleFloor.java --module=use-core --module-dir=<dir>
//        --reactor-root=<dir> --requested=<[profile,...]> --effective=<true|false>
//
// STALENESS. Surefire does not empty target/surefire-reports: a report written by an earlier
// -Pupstream-oracle run survives a later default run, so a checker that simply counted the
// files on disk could be handed 40 classes by a build that collected 7. This was observed on
// this tree, not imagined. The `--stamp=true` execution therefore writes
// target/upstream-oracle-floor.stamp at phase `initialize`, and the verify-phase check counts
// ONLY report files at least as new as that stamp. A missing stamp is a failure, never a pass.
//
// Exit 0 = every floor met. Exit 1 = at least one violation, ALL of them printed
// (the shape of DifferentialSweep.requireStagePass: read all the clauses, not the first).
//
// FOUR CHECKS, in this order, none of them skippable:
//
//   A. WIRING (mode-independent, both poms, every run). Both use-core/pom.xml and
//      use-gui/pom.xml must still carry the upstream-oracle profile, the vintage engine
//      INSIDE it, and this floor execution. Every module's run checks BOTH poms, so
//      deleting either module's profile block fails the build even in the DEFAULT build,
//      and even in the module that was not touched. This is the merge accident of D-01.
//   B. REQUESTED vs EFFECTIVE. `--requested` is `${session.request.activeProfiles}`, the
//      reactor-wide -P list from the command line, which a per-module pom edit CANNOT
//      change; `--effective` is a property set to `true` only by this module's own profile
//      block. Requested-but-not-effective is an ERROR, never a pass.
//   C. PER-MODULE, PER-TIER COUNT FLOORS, pinned below as literals. Losing any one of the
//      four populations fails. A reactor-wide total would not: use-core's 350 dwarf
//      use-gui's 17.
//   D. SENTINEL. One vintage-only class per module (junit.framework.TestCase, so no
//      engine but vintage can collect it) MUST have produced a report under the profile and
//      MUST NOT have produced one in the default build.
//
// Counting matches docs/port2/upstream-oracle-profile.md sec. 2 exactly: a CLASS is a
// report file whose root <testsuite> holds at least one <testcase>; METHODS are DISTINCT
// <testcase> @name values within that file. Surefire's headline counts method EXECUTIONS
// and the 14 JUnit-3 AllTests aggregators re-run their members, so the headline overcounts
// by up to 4x and is never used here.
// =====================================================================================

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

public class UpstreamOracleFloor {

    static final String PROFILE_ID = "upstream-oracle";
    static final String STAMP_NAME = "upstream-oracle-floor.stamp";

    // -----------------------------------------------------------------------------------
    // THE FLOORS. Literals, pinned 2026-08-17 BEFORE the run that accepted them, from the
    // measured state recorded in docs/port2/upstream-oracle-profile.md:
    //   sec. 4.2 / the round-9 measurement  -> ORACLE: use-core surefire 40/350,
    //       use-gui surefire 8/17, use-core failsafe 1/1, use-gui failsafe 1/129
    //       (= 50 distinct classes / 497 distinct methods)
    //   sec. 3.1 + sec. 3.3                 -> DEFAULT: use-core surefire 7/79,
    //       use-gui surefire 1/1, use-core failsafe 1/1, use-gui failsafe 1/129
    //       (= 210 methods, 80 surefire + 130 failsafe)
    // Floors are >= : the suite may GROW, it may never shrink. No floor is 0 and no floor
    // may be lowered to make a run pass — see harness-contract.md sec. 8 step 7 clause 1,
    // "Do not lower the floor."
    // -----------------------------------------------------------------------------------
    record Floor(int classes, int methods) { }

    static final Map<String, Floor> ORACLE = Map.of(
            "use-core/surefire", new Floor(40, 350),
            "use-gui/surefire", new Floor(8, 17),
            "use-core/failsafe", new Floor(1, 1),
            "use-gui/failsafe", new Floor(1, 129));

    static final Map<String, Floor> DEFAULT = Map.of(
            "use-core/surefire", new Floor(7, 79),
            "use-gui/surefire", new Floor(1, 1),
            "use-core/failsafe", new Floor(1, 1),
            "use-gui/failsafe", new Floor(1, 129));

    // Vintage-only sentinels: both extend junit.framework.TestCase, so the JUnit Platform
    // cannot collect either one without the vintage engine.
    //   use-core/src/test/java/org/tzi/use/parser/USECompilerTest.java:25  -> junit.framework.TestCase
    //   use-gui/src/test/java/org/tzi/use/gui/views/diagrams/util/DirectedLineTest.java:23 -> idem
    static final Map<String, String> SENTINEL = Map.of(
            "use-core", "org.tzi.use.parser.USECompilerTest",
            "use-gui", "org.tzi.use.gui.views.diagrams.util.DirectedLineTest");

    static final List<String> TIERS = List.of("surefire", "failsafe");
    static final List<String> POM_MODULES = List.of("use-core", "use-gui");

    static final List<String> violations = new ArrayList<>();

    static void fail(String message) {
        violations.add(message);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = new LinkedHashMap<>();
        for (String a : args) {
            int eq = a.indexOf('=');
            if (!a.startsWith("--") || eq < 0) {
                die("unparseable argument: " + a);
            }
            opt.put(a.substring(2, eq), a.substring(eq + 1));
        }
        if ("true".equals(opt.get("stamp"))) {
            Path stamp = Path.of(require(opt, "module-dir")).toAbsolutePath().normalize()
                    .resolve("target").resolve(STAMP_NAME);
            Files.createDirectories(stamp.getParent());
            Files.writeString(stamp, "upstream-oracle floor stamp: reports older than this file\n"
                    + "were not written by this build and are not counted.\n"
                    + Instant.now() + "\n", StandardCharsets.UTF_8);
            System.out.println("[floor] wrote freshness stamp " + stamp);
            return;
        }

        String module = require(opt, "module");
        Path moduleDir = Path.of(require(opt, "module-dir")).toAbsolutePath().normalize();
        Path reactorRoot = Path.of(require(opt, "reactor-root")).toAbsolutePath().normalize();
        String requestedRaw = require(opt, "requested");
        String effectiveRaw = require(opt, "effective");

        if (!POM_MODULES.contains(module)) {
            die("--module must be one of " + POM_MODULES + ", got: " + module);
        }

        Set<String> requested = parseProfileList(requestedRaw);
        boolean oracleRequested = requested.contains(PROFILE_ID);

        System.out.println("[floor] ===== upstream-oracle floor check: " + module + " =====");
        System.out.println("[floor] requested profiles (reactor-wide, from the command line): "
                + (requested.isEmpty() ? "(none)" : requested));
        System.out.println("[floor] this module's upstream-oracle profile effective: " + effectiveRaw);
        System.out.println("[floor] mode: " + (oracleRequested ? "ORACLE" : "DEFAULT"));

        // ---- A. WIRING -----------------------------------------------------------------
        for (String m : POM_MODULES) {
            checkWiring(reactorRoot.resolve(m).resolve("pom.xml"), m);
        }
        Path self = reactorRoot.resolve("scripts").resolve("UpstreamOracleFloor.java");
        if (!Files.isRegularFile(self)) {
            fail("WIRING: the floor checker itself is missing at " + self);
        }

        // ---- B. REQUESTED vs EFFECTIVE -------------------------------------------------
        Boolean effective = switch (effectiveRaw.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
        if (effective == null) {
            fail("EFFECTIVENESS: --effective is neither true nor false but '" + effectiveRaw
                    + "'. The property use.upstreamOracle.effective is missing from "
                    + module + "/pom.xml, so the gate cannot tell whether the profile applied.");
        } else if (oracleRequested && !effective) {
            fail("EFFECTIVENESS: -P" + PROFILE_ID + " WAS REQUESTED ON THE COMMAND LINE BUT IS NOT"
                    + " EFFECTIVE IN " + module + ". Its profile block is missing, renamed or"
                    + " inactive, so this module collected DEFAULT-build tests while the gate was"
                    + " asked for the upstream oracle. That is D-01's merge accident, and it is an"
                    + " error, not a pass.");
        } else if (!oracleRequested && effective) {
            fail("EFFECTIVENESS: the " + PROFILE_ID + " profile is effective in " + module
                    + " without being requested (activeByDefault, or a settings.xml activation)."
                    + " The default build must stay vintage-free.");
        }

        // ---- C. COUNT FLOORS -----------------------------------------------------------
        Path stampFile = moduleDir.resolve("target").resolve(STAMP_NAME);
        long stampMillis = Long.MAX_VALUE;
        if (!Files.isRegularFile(stampFile)) {
            fail("FRESHNESS: no " + STAMP_NAME + " in " + moduleDir.resolve("target")
                    + ". The `upstream-oracle-floor-stamp` execution did not run, so this check"
                    + " cannot tell reports written by THIS build from stale reports left by an"
                    + " earlier -P" + PROFILE_ID + " run. Counting them would be exactly the"
                    + " vacuous pass D-01 is about.");
        } else {
            stampMillis = Files.getLastModifiedTime(stampFile).toMillis();
            System.out.println("[floor] freshness stamp: " + Instant.ofEpochMilli(stampMillis)
                    + " — reports older than this are stale and are NOT counted");
        }

        Map<String, Floor> floors = oracleRequested ? ORACLE : DEFAULT;
        for (String tier : TIERS) {
            String key = module + "/" + tier;
            Floor floor = floors.get(key);
            if (floor == null) {
                die("no floor pinned for " + key);
            }
            Path dir = moduleDir.resolve("target").resolve(tier + "-reports");
            Tally t = tally(dir, stampMillis);
            System.out.printf("[floor] %-9s %-9s classes=%-3d (floor %-3d)  methods=%-4d (floor %-4d)"
                            + "  executions=%-4d failures=%d errors=%d skipped=%d stale-ignored=%d%n",
                    tier, module, t.classes, floor.classes(), t.methods, floor.methods(),
                    t.executions, t.failures, t.errors, t.skipped, t.stale);
            if (!t.dirPresent) {
                fail("FLOOR " + key + ": the report directory does not exist (" + dir
                        + "). Nothing was collected. 0 is rejected outright"
                        + " (harness-contract.md sec. 8 step 2).");
            }
            if (t.classes < floor.classes()) {
                fail("FLOOR " + key + ": " + t.classes + " distinct test classes < floor "
                        + floor.classes() + ". " + explain(oracleRequested, module));
            }
            if (t.methods < floor.methods()) {
                fail("FLOOR " + key + ": " + t.methods + " distinct test methods < floor "
                        + floor.methods() + ". " + explain(oracleRequested, module));
            }
            if (t.failures != 0 || t.errors != 0) {
                fail("FLOOR " + key + ": failures=" + t.failures + " errors=" + t.errors
                        + ", both must be 0.");
            }
            if (t.skipped != 0) {
                fail("FLOOR " + key + ": skipped=" + t.skipped + ", must be 0 — a skipped test is"
                        + " silence, which is the defect this check exists to abolish.");
            }
        }

        // ---- D. SENTINEL ---------------------------------------------------------------
        String sentinel = SENTINEL.get(module);
        Tally sure = tally(moduleDir.resolve("target").resolve("surefire-reports"), stampMillis);
        boolean present = sure.classNames.contains(sentinel);
        System.out.println("[floor] vintage-only sentinel " + sentinel + ": "
                + (present ? "collected" : "absent"));
        if (oracleRequested && !present) {
            fail("SENTINEL " + module + ": " + sentinel + " produced no report under -P"
                    + PROFILE_ID + ". It extends junit.framework.TestCase, so its absence means"
                    + " the vintage engine was not on the test classpath — the profile was"
                    + " requested and did nothing.");
        }
        if (!oracleRequested && present) {
            fail("SENTINEL " + module + ": " + sentinel + " was collected by the DEFAULT build."
                    + " The vintage engine has leaked out of the profile; the product build is"
                    + " supposed to be JUnit 5 Jupiter only.");
        }

        // ---- verdict -------------------------------------------------------------------
        if (violations.isEmpty()) {
            System.out.println("[floor] PASS — " + module + " met every pinned floor in "
                    + (oracleRequested ? "ORACLE" : "DEFAULT") + " mode.");
            return;
        }
        System.out.println();
        System.out.println("[floor] ###############################################################");
        System.out.println("[floor] FAIL — " + violations.size() + " floor violation(s) in " + module
                + " (" + (oracleRequested ? "ORACLE" : "DEFAULT") + " mode):");
        for (int i = 0; i < violations.size(); i++) {
            System.out.println("[floor]   " + (i + 1) + ". " + violations.get(i));
        }
        System.out.println("[floor] Do NOT lower a floor to make this pass. See D-01 in"
                + " docs/port2/upstream-oracle-static-review.md and harness-contract.md sec. 8.");
        System.out.println("[floor] ###############################################################");
        System.exit(1);
    }

    static String explain(boolean oracle, String module) {
        return oracle
                ? "The upstream JUnit 3/4 tree was not collected: check that"
                  + " junit-vintage-engine is present at <scope>test</scope> inside " + module
                  + "'s upstream-oracle profile and that junit:junit is still on the test"
                  + " classpath."
                : "The default build lost tests it used to run.";
    }

    // ---------------- A. wiring -----------------------------------------------------------

    /**
     * Asserts, from the pom text with XML comments stripped and all whitespace removed, that
     * the module still carries the profile, the engine inside it, and this floor execution.
     * Text rather than an effective-model query on purpose: this must hold for the OTHER
     * module too, which the running Maven session does not expose.
     */
    static void checkWiring(Path pom, String module) {
        String raw;
        try {
            raw = Files.readString(pom, StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("WIRING: cannot read " + pom + " (" + e + ")");
            return;
        }
        String x = raw.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", "");
        int p0 = x.indexOf("<profiles>");
        int p1 = x.indexOf("</profiles>");
        if (p0 < 0 || p1 < p0) {
            fail("WIRING: " + module + "/pom.xml HAS NO <profiles> ELEMENT — the "
                    + PROFILE_ID + " profile has been deleted. The upstream JUnit 3/4 oracle"
                    + " cannot be activated in this module and -P" + PROFILE_ID
                    + " would silently collect the default build's tests instead (D-01).");
            return;
        }
        String profiles = x.substring(p0, p1 + "</profiles>".length());
        if (!profiles.contains("<id>" + PROFILE_ID + "</id>")) {
            fail("WIRING: " + module + "/pom.xml has a <profiles> element but no <id>"
                    + PROFILE_ID + "</id> in it, so -P" + PROFILE_ID + " activates nothing here.");
        }
        if (!profiles.contains("<artifactId>junit-vintage-engine</artifactId>")) {
            fail("WIRING: " + module + "/pom.xml's " + PROFILE_ID + " profile no longer declares"
                    + " junit-vintage-engine. Without that engine the JUnit Platform collects"
                    + " none of upstream's JUnit 3/4 tree.");
        }
        int total = count(x, "<artifactId>junit-vintage-engine</artifactId>");
        int inside = count(profiles, "<artifactId>junit-vintage-engine</artifactId>");
        if (total != inside) {
            fail("WIRING: " + module + "/pom.xml declares junit-vintage-engine OUTSIDE the "
                    + PROFILE_ID + " profile (" + (total - inside) + " occurrence(s)). The default"
                    + " product build must stay vintage-free.");
        }
        if (x.contains("<activeByDefault>true</activeByDefault>")) {
            fail("WIRING: " + module + "/pom.xml activates a profile by default. The upstream"
                    + " oracle must be requested on purpose.");
        }
        if (!x.contains("<properties><use.upstreamOracle.effective>true"
                + "</use.upstreamOracle.effective></properties>")) {
            fail("WIRING: " + module + "/pom.xml's " + PROFILE_ID + " profile does not set"
                    + " use.upstreamOracle.effective=true, so the gate loses its"
                    + " requested-but-not-effective detector.");
        }
        if (!x.contains("scripts/UpstreamOracleFloor.java")) {
            fail("WIRING: " + module + "/pom.xml no longer runs scripts/UpstreamOracleFloor.java."
                    + " That module's counts would go unasserted — the D-01 defect itself.");
        }
        if (!x.contains("<id>upstream-oracle-floor</id><phase>verify</phase>")) {
            fail("WIRING: " + module + "/pom.xml has no `upstream-oracle-floor` execution bound to"
                    + " the `verify` phase.");
        }
        if (!x.contains("<id>upstream-oracle-floor-stamp</id><phase>initialize</phase>")) {
            fail("WIRING: " + module + "/pom.xml has no `upstream-oracle-floor-stamp` execution"
                    + " bound to the `initialize` phase, so stale reports from an earlier"
                    + " -P" + PROFILE_ID + " run could be counted as this build's.");
        }
        if (!x.contains("<skip>false</skip>")) {
            fail("WIRING: " + module + "/pom.xml no longer pins <skip>false</skip> on the floor"
                    + " execution, so -Dexec.skip=true would silence the gate.");
        }
        if (!x.contains("--module=" + module)) {
            fail("WIRING: " + module + "/pom.xml's floor execution does not pass --module="
                    + module + ".");
        }
    }

    static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    // ---------------- C. counting ---------------------------------------------------------

    static final class Tally {
        boolean dirPresent;
        int classes;
        int methods;
        int executions;
        int failures;
        int errors;
        int skipped;
        int stale;
        final Set<String> classNames = new TreeSet<>();
    }

    static Tally tally(Path dir, long stampMillis) throws Exception {
        Tally t = new Tally();
        t.dirPresent = Files.isDirectory(dir);
        if (!t.dirPresent) {
            return t;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("TEST-") && n.endsWith(".xml");
            }).sorted().toList();
        }
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        for (Path p : files) {
            if (Files.getLastModifiedTime(p).toMillis() < stampMillis) {
                t.stale++;
                continue;
            }
            String cls = null;
            Set<String> names = new LinkedHashSet<>();
            int execs = 0;
            boolean rootSeen = false;
            try (InputStream in = Files.newInputStream(p)) {
                XMLStreamReader r = f.createXMLStreamReader(in);
                while (r.hasNext()) {
                    if (r.next() != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    String el = r.getLocalName();
                    if ("testsuite".equals(el) && !rootSeen) {
                        rootSeen = true;
                        cls = r.getAttributeValue(null, "name");
                        t.failures += attrInt(r, "failures");
                        t.errors += attrInt(r, "errors");
                        t.skipped += attrInt(r, "skipped");
                    } else if ("testcase".equals(el)) {
                        names.add(r.getAttributeValue(null, "name"));
                        execs++;
                    }
                }
                r.close();
            }
            t.executions += execs;
            if (!names.isEmpty()) {
                t.classes++;
                t.methods += names.size();
                t.classNames.add(cls == null ? p.getFileName().toString() : cls);
            }
        }
        return t;
    }

    static int attrInt(XMLStreamReader r, String name) {
        String v = r.getAttributeValue(null, name);
        if (v == null || v.isBlank()) {
            return 0;
        }
        return Integer.parseInt(v.trim());
    }

    // ---------------- helpers -------------------------------------------------------------

    /** Parses Maven's {@code ${session.request.activeProfiles}}, e.g. {@code [upstream-oracle]}. */
    static Set<String> parseProfileList(String raw) {
        String s = raw.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : s.split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    static String require(Map<String, String> opt, String key) {
        String v = opt.get(key);
        if (v == null) {
            die("missing required argument --" + key + "=; got " + opt.keySet());
        }
        return v;
    }

    static void die(String message) {
        System.out.println("[floor] FATAL — " + message);
        System.out.println("[floor] usage: java scripts/UpstreamOracleFloor.java --module=<m>"
                + " --module-dir=<dir> --reactor-root=<dir> --requested=<list> --effective=<bool>");
        System.exit(2);
    }

    private UpstreamOracleFloor() {
        throw new AssertionError("not instantiable");
    }
}
