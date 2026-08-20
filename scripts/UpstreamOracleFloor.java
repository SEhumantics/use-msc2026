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
// ROUND 11 closes three defects of round 10
// (docs/port2/upstream-oracle-floor-verification.md):
//
//   F-01  `-Dexec.args=-version` produced a green build with ZERO [floor] lines. exec:exec's
//         `commandlineArgs` parameter carries the user property `exec.args` and REPLACES
//         `<arguments>`, so the checker was never invoked. `<skip>false</skip>` was necessary
//         and not sufficient. Closed by THREE independent mechanisms, so that defeating one
//         leaves the others standing:
//           (1) both poms now pin, in BOTH floor executions, every exec:exec parameter whose
//               user property could silence or divert the check: <skip>false</skip>,
//               <commandlineArgs>, <async>false</async>, <timeout>0</timeout>,
//               <quietLogs>false</quietLogs>, <executable> and <workingDirectory>. POM
//               <configuration> beats a @Parameter(property=...) default — proved on this tree
//               by round 10 break (f), where the pinned <executable> held while the unpinned
//               argument list was replaced by -Dexec.args. The one parameter that cannot be
//               PINNED is `outputFile` (there is no value meaning "the log"), so instead every
//               one of those user properties is handed BACK to this checker by both poms
//               (--exec-args=${exec.args} and its seven siblings) and setting any of them is a
//               BUILD FAILURE, check A2 below. Pinning makes an attack inert; A2 makes it loud,
//               and a silence nobody notices is what F-01 actually was.
//               HOW MANY THERE ARE, corrected 2026-08-18 (round 11 defect G-03, and the
//               refuter's own arithmetic corrected in turn — see the count below): goal
//               `exec` of exec-maven-plugin 3.5.0 declares TWENTY-TWO parameters carrying a
//               user-property expression, not "eight" and not "21". Measured, not recalled:
//                 unzip -p exec-maven-plugin-3.5.0.jar META-INF/maven/plugin.xml
//                 (mojo goal=exec; parameters whose <configuration> body holds a ${...})
//               Twenty of the twenty-two are `exec.*`; two are unprefixed (`${sourceRoot}`,
//               `${testSourceRoot}`). SEVEN are pinned in both poms, EIGHT are handed back to
//               check A2, and the remaining FOURTEEN are neither. That residual is DELIBERATE
//               and is written down in docs/port2/gate-threat-model.md sec. 3: defeating one
//               of the fourteen (e.g. -Dexec.useMavenLogger=true) requires a hand-typed -D
//               that no honest workflow produces, and the gate wrapper still fails such a run
//               on its announce-count and receipt checks;
//           (2) a Jupiter test, use-core/src/test/java/org/tzi/use/uncertainty/gate/
//               UpstreamOracleGateWiringTest.java, re-asserts that wiring from inside the test
//               phase, where no exec-maven-plugin property can reach it, and additionally
//               proves at RUNTIME that the initialize-phase stamp execution really ran;
//           (3) the receipt written by THIS file (see RECEIPT below), which
//               scripts/upstream-oracle-gate.sh verifies on disk after Maven has exited.
//         WHAT ARGV THIS CHECKER REQUIRES is stated under ARGV CONTRACT below, and what it
//         does when it does not receive it: it exits 2 without writing a receipt, which fails
//         the build. It cannot defend itself against not being invoked at all — that is
//         precisely why (2) and (3) exist and why the gate is a script, not a typed command.
//
//   F-02  a mistyped `-P` id (`-Pupstream-oracle-typo`) was a green, vacuous gate: Maven only
//         warns, and this checker never sees a request it was not given, so
//         "requested-but-not-effective" cannot fire. Closed twice:
//           (1) the gate is DEFINED as scripts/upstream-oracle-gate.sh, which hard-codes the
//               profile id, greps the log for `could not be activated`, and requires this checker
//               to have reported the expected mode and verdict for BOTH modules — so a typo now
//               fails to find a script instead of silently degrading a gate;
//           (2) check B2 below, which round 10 sec. 3.5 item 3 correctly said was available all
//               along: every profile id declared by any reactor pom is collected by the same text
//               parse this file already ran, and a requested id matching none FAILS the build. So
//               even the HAND-TYPED `mvn -Pupstream-oracle-typo` is now red.
//
//   F-03  `-pl use-core -Pupstream-oracle` printed an unqualified `PASS` for half a gate. This
//         checker now reads ${session.request.selectedProjects} and
//         ${session.request.resumeFrom} and reports `PARTIAL`, never `PASS`, when the reactor
//         was partial. Exit stays 0 — `-pl` is a deliberate developer flag, not a merge
//         accident — but the receipt records verdict=PARTIAL and the wrapper rejects it, so a
//         partial reactor can never be presented as the acceptance gate.
//
// RUN BY MAVEN, NOT BY A HUMAN. Bound in use-core/pom.xml and use-gui/pom.xml as
// exec-maven-plugin executions `upstream-oracle-floor-stamp` (initialize) and
// `upstream-oracle-floor` (verify), both with <skip>false</skip>, <async>false</async>,
// <timeout>0</timeout> and a pinned <commandlineArgs>.
// Java single-file source mode (JEP 330) — no dependency beyond the JDK that runs Maven, so
// the gate cannot be disabled by an artifact failing to resolve.
//
// ARGV CONTRACT — EXACT SET MATCHING, VALIDATED IN FULL BEFORE ANY OPTION IS ACTED ON.
// (Rewritten 2026-08-18, round 12, defect G-01 of docs/port2/upstream-oracle-gate-round11.md.)
//
//   stamp mode  (phase initialize) — EXACTLY these five option names, no more, no fewer:
//       --stamp=true --module-dir=<dir> --reactor-root=<dir>
//       --requested=<maven list> --allow-profiles=<comma list>
//   check mode  (phase verify) — EXACTLY these sixteen, no more, no fewer:
//       --module=<use-core|use-gui> --module-dir=<dir> --reactor-root=<dir>
//       --effective=<true|false> --selected=<maven list> --resume-from=<value>
//       --requested=<maven list> --allow-profiles=<comma list>
//       --exec-args=<v> --exec-skip=<v> --exec-async=<v> --exec-timeout=<v>
//       --exec-executable=<v> --exec-outputfile=<v> --exec-quietlogs=<v> --exec-workingdir=<v>
//
// THE ARGV IS PARSED AND VALIDATED COMPLETELY, AND ONLY THEN IS A MODE CHOSEN. Anything
// else is FATAL: exit 2, no receipt written, build fails. Fatal means: an unknown option
// name; an option given more than once; a token beginning with `--` that is not a
// well-formed `--name=value`; a continuation token before any option; an option set that is
// not EXACTLY one of the two above; and — see G-01 — any option VALUE that itself parses as
// an option (`--name=...`), because an interpolated Maven property is data and can never be
// argv.
//
// WHY EXACT-SET, AND NOT `if (opt.containsKey("stamp"))`. exec:exec splits
// <commandlineArgs> with CommandLineUtils.translateCommandline AFTER Maven interpolates it,
// so an operator-supplied property value containing a space contributes EXTRA ARGV TOKENS to
// this program. Until 2026-08-18 the stamp branch was tested FIRST, before the argv was
// fully validated, so `-Dexec.args='x --stamp=true'` (and the same payload through
// `use.floor.allowProfiles` or `use.upstreamOracle.effective`) made the VERIFY-phase
// execution rewrite the freshness stamp and return 0 — no wiring check, no tamper check, no
// floors, no sentinel, NO RECEIPT, and BUILD SUCCESS. One injected token defeated three of
// the four F-01 mechanisms at once. The exact-set rule closes it structurally: `stamp` is
// simply not a legal option name in a check argv, and the sixteen check options are not
// legal in a stamp argv. There is no ordering left to get wrong.
//
// CONTINUATIONS. Maven renders a List-valued expression as `[a, b]`, i.e. with a space, and
// exec:exec splits on whitespace, so a bare token that follows an option is appended to that
// option's value — this is how `--selected=[use-core, use-gui]` survives the split. A bare
// continuation may not itself look like an option.
//
// SET vs UNSET (defect G-02, same round). A Maven property the operator did not set arrives
// as its OWN PLACEHOLDER, ENTIRE: the value of `--exec-outputfile=${exec.outputFile}` is the
// nine-plus characters `${exec.outputFile}` and nothing else. The test is therefore
// `value.equals("${" + property + "}")` and NOT `value.contains("${")`, which read
// `/tmp/floorhide${z}.txt` as "unset" and let `-Dexec.outputFile` divert every [floor] line
// to a file with no TAMPERING violation raised.
//
// STALENESS. Surefire does not empty target/surefire-reports: a report written by an earlier
// -Pupstream-oracle run survives a later default run, so a checker that simply counted the
// files on disk could be handed 40 classes by a build that collected 7. This was observed on
// this tree, not imagined. The `--stamp=true` execution therefore writes
// target/upstream-oracle-floor.stamp at phase `initialize`, and the verify-phase check counts
// ONLY report files at least as new as that stamp. A missing stamp is a failure, never a pass.
//
// RECEIPT. The check writes target/upstream-oracle-floor.receipt in the module it checked,
// recording module, mode, verdict, partiality, the counts and the stamp instant — on FAIL as
// well as on PASS. It is written after the checks and before the exit, so its absence means
// the check did not run to completion. Nothing inside the same build can read the receipt of
// its own verify phase; scripts/upstream-oracle-gate.sh reads it after Maven exits, which is
// the point: that check lives outside Maven, where no Maven property can reach it.
//
// Exit 0 = every floor met (verdict PASS, or PARTIAL on a partial reactor).
// Exit 1 = at least one violation, ALL of them printed (the shape of
//          DifferentialSweep.requireStagePass: read all the clauses, not the first).
// Exit 2 = the argv contract was broken.
//
// FOUR CHECKS, in this order, none of them skippable:
//
//   A. WIRING (mode-independent, both poms, every run). Both use-core/pom.xml and
//      use-gui/pom.xml must still carry the upstream-oracle profile, the vintage engine
//      INSIDE it, and both floor executions with every silencing property pinned. Every
//      module's run checks BOTH poms, so deleting either module's profile block fails the
//      build even in the DEFAULT build, and even in the module that was not touched. This is
//      the merge accident of D-01. The Jupiter wiring test and the gate wrapper are also
//      asserted to exist, so removing either half of the F-01/F-02 fix fails the other half.
//   B. REQUESTED vs EFFECTIVE. `--requested` is `${session.request.activeProfiles}`, the
//      reactor-wide -P list from the command line, which a per-module pom edit CANNOT
//      change; `--effective` is a property set to `true` only by this module's own profile
//      block. Requested-but-not-effective is an ERROR, never a pass.
//   C. PER-MODULE, PER-TIER COUNT FLOORS, pinned below as literals. Losing any one of the
//      four populations fails. A reactor-wide total would not: use-core's 351 dwarf
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
    static final String RECEIPT_NAME = "upstream-oracle-floor.receipt";

    /** The other half of the F-01 fix; asserted to exist by check A. */
    static final String WIRING_TEST =
            "use-core/src/test/java/org/tzi/use/uncertainty/gate/UpstreamOracleGateWiringTest.java";
    /** The F-02 fix: the gate IS this invocation. Asserted to exist by check A. */
    static final String GATE_WRAPPER = "scripts/upstream-oracle-gate.sh";

    // -----------------------------------------------------------------------------------
    // THE FLOORS. Literals, pinned BEFORE the run that accepts them, from the measured state
    // recorded in docs/port2/upstream-oracle-profile.md:
    //   sec. 4.2 / the round-9 measurement  -> ORACLE: use-core surefire 40/350,
    //       use-gui surefire 8/17, use-core failsafe 1/1, use-gui failsafe 1/129
    //       (= 50 distinct classes / 497 distinct methods)
    //   sec. 3.1 + sec. 3.3                 -> DEFAULT: use-core surefire 7/79,
    //       use-gui surefire 1/1, use-core failsafe 1/1, use-gui failsafe 1/129
    //       (= 210 methods, 80 surefire + 130 failsafe)
    //
    // RE-PINNED 2026-08-17 (round 11, F-01). The F-01 fix adds ONE test class with ONE test
    // method — UpstreamOracleGateWiringTest — to use-core/src/test, in both modes. So
    // use-core/surefire goes 7/79 -> 8/80 (default) and 40/350 -> 41/351 (oracle), and the
    // totals go 10/210 -> 11/211 and 50/497 -> 51/498. The floors are RAISED in the same
    // commit that grows the suite, which is what harness-contract.md sec. 0.1 requires; they
    // are not lowered, and the arithmetic is +1/+1 in exactly one cell of the table.
    // Correctness beats a round number: 211 is the honest count of a gate that cannot be
    // silenced, 210 was the count of one that could.
    //
    //
    // RE-PINNED 2026-08-18 (S3 2/2). The lattice commit adds ONE test class with FIVE test
    // methods — UncertainTypeLatticeTest — to use-core/src/test. It is a Jupiter test, so it
    // is collected in BOTH modes. use-core/surefire goes 8/80 -> 9/85 (default) and
    // 41/351 -> 42/356 (oracle); totals go 11/211 -> 12/216 and 51/498 -> 52/503. Raised in
    // the same commit that grows the suite, per harness-contract.md sec. 0.1.
    //
    // The class exists because TypeTest#testSupertype was modified under waiver W-01. A
    // modified oracle is a weaker oracle unless something independent pins the behaviour the
    // modification was made for, and this floor is what stops that pin being quietly removed.
    //
    // RE-PINNED 2026-08-18 (TupleType cost fixes). Adds ONE Jupiter test class with FOUR test
    // methods — TupleTypeSupertypeCostTest — collected in BOTH modes. use-core/surefire goes
    // 9/85 -> 10/89 (default) and 42/356 -> 43/360 (oracle). Raised in the same commit that
    // grows the suite, per harness-contract.md sec. 0.1.
    //
    // RE-PINNED 2026-08-18 (Track B, SBoolean marshalling). Adds ONE Jupiter class with FOUR
    // methods — SBooleanMarshallingTest — collected in BOTH modes. use-core/surefire goes
    // 10/89 -> 11/93 (default) and 43/360 -> 44/364 (oracle).
    //
    // RE-PINNED 2026-08-18 (B1 vendoring). Adds TWO methods to the existing
    // HistoricalOracleIsolationTest guarding the org.tzi.use.uncertainty carve-out. No new class.
    // use-core/surefire methods 93 -> 95 (default) and 364 -> 366 (oracle).
    //
    // RE-PINNED 2026-08-18 (S4 first real differential). Adds ONE Jupiter class with ONE method —
    // FirstRealDifferentialTest — collected in BOTH modes. use-core/surefire 11/95 -> 12/96
    // (default) and 44/366 -> 45/367 (oracle).
    //
    // RE-PINNED 2026-08-18 (S7 grammar). Adds ONE Jupiter class with TWO methods —
    // IterationWarningTokenRotTest — collected in BOTH modes. use-core/surefire 12/96 -> 13/98
    // (default) and 45/367 -> 46/369 (oracle).
    //
    // RE-PINNED 2026-08-18 (S8 arithmetic). Adds ONE Jupiter class with FIVE methods —
    // UncertainExpressionTypingTest — collected in BOTH modes. use-core/surefire 13/98 -> 14/103
    // (default) and 46/369 -> 47/374 (oracle).
    //
    // RE-PINNED 2026-08-18 (S8 collection sum). UncertainExpressionTypingTest grows 5 -> 8 methods.
    // use-core/surefire 14/103 -> 14/106 (default) and 47/374 -> 47/377 (oracle). No new class.
    //
    // RE-PINNED 2026-08-20 (full-census fidelity sweep). Adds ONE Jupiter class with ONE method —
    // PortedFidelitySweepTest — collected in BOTH modes. use-core/surefire 14/106 -> 15/107
    // (default) and 47/377 -> 48/378 (oracle).
    //
    // RE-PINNED 2026-08-20 (B7 pre-registration and the corrections it adjudicates). Adds TWO
    // Jupiter test files, IntendedDeparturesTest and B7CorrectionsTest, with 28 and 26 methods.
    // They count as THIRTEEN classes rather than two: surefire counts each @Nested class as its own
    // report, and the two files carry 6 and 7 nested classes. So use-core/surefire
    // 15/107 -> 28/161 (default) and 48/378 -> 61/432 (oracle), in both modes, both measured.
    //
    // RE-PINNED 2026-08-20 (B7 F-2, MathUtil.round saturation). Adds ONE Jupiter test file,
    // MathUtilRoundSaturationTest, with 9 methods across 3 @Nested classes. use-core/surefire
    // 28/161 -> 31/170 (default) and 61/432 -> 64/441 (oracle).
    //
    // RE-PINNED 2026-08-20 (B7 type and dispatch layers). Adds ONE Jupiter test file,
    // B7TypeAndDispatchTest, with 12 methods across 5 @Nested classes. use-core/surefire
    // 31/170 -> 36/182 (default) and 64/441 -> 69/453 (oracle).
    //
    // RE-PINNED 2026-08-20 (B7 parser/constant layer, M-29/M-30/M-32/M-33). Adds ONE Jupiter test
    // file, B7ParserAndConstantsTest, with 12 methods across 4 @Nested classes. use-core/surefire
    // 36/182 -> 40/194 (default) and 69/453 -> 73/465 (oracle).
    //
    // RE-PINNED 2026-08-20 (uEquals coverage). Adds ONE Jupiter test file, UEqualsCoverageTest,
    // with 6 methods. use-core/surefire 40/194 -> 41/200 (default) and 73/465 -> 74/471 (oracle).
    //
    // RE-PINNED 2026-08-20 (uSelect/uSelectC, uncertainty-aware collection membership, and the
    // ExpQuery multi-variable forAll/exists accumulation fix). Adds ONE Jupiter test file,
    // UncertainQueryAndMembershipTest, with 5 methods across 5 @Nested classes (USelect,
    // USelectC, ForAllExists, Membership, MultiVariableAccumulation — each @Nested class is its
    // own surefire report). use-core/surefire 41/200 -> 46/216 (default) and 74/471 -> 79/487
    // (oracle). use-gui is unmeasured by this change (no use-gui source file was touched); its
    // floors are unchanged, and use-gui/failsafe methods=129 continues to hold because the
    // ExpQuery accumulation fix corrected t049/t022 rather than adding new shell fixtures.
    //
    // RE-PINNED 2026-08-20 (CF-8, the historical corpus test harness). Adds ONE Jupiter test
    // file, USECompilerUncertaintyTest (org.tzi.use.parser.uncertainty), with ONE method that
    // replays all 1427 entries of the ported UBooleanExpression.in/UCollectionOperations.in/
    // UIntegerExpression.in/URealExpression.in corpus. use-core/surefire 46/216 -> 47/217
    // (default) and 79/487 -> 80/488 (oracle). use-gui unchanged.
    //
    // RE-PINNED 2026-08-20 (B7 M-43, UBooleanValueTest). Adds ONE Jupiter test file with 5
    // methods, no @Nested classes. Two of the five are the fork's own out-of-range-probability
    // checks, revived LIVE (not @Disabled, as the ledger anticipated) after probing the real
    // historical jar directly and finding it also throws IllegalArgumentException on
    // valueOf(true, -2) and valueOf(true, 2) — see the test file's own javadoc for the full
    // evidence. use-core/surefire 47/217 -> 48/222 (default) and 80/488 -> 81/493 (oracle).
    // use-gui unchanged.
    //
    // RE-PINNED 2026-08-20 (B7 M-44, first file: ExpQueryUncertaintyTest). Adds ONE Jupiter test
    // file with 12 methods, no @Nested classes — the fork's own independently-written coverage of
    // uSelect/uSelectC/forAll/exists (ported this stage in the uSelect/membership commit) plus
    // sum(). All 12 pass with zero semantic corrections; three exception-message assertions were
    // corrected to match the port's actual wording (observed, not guessed) after the first run.
    // use-core/surefire 48/222 -> 49/234 (default) and 81/493 -> 82/505 (oracle). use-gui
    // unchanged.
    //
    // RE-PINNED 2026-08-20 (B7 M-44, file 2 of 4: UBooleanExpOpsTest). Adds ONE Jupiter test file
    // with 27 methods, no @Nested classes. All 27 pass with zero semantic corrections; 128
    // assertEquals and 8 assertTrue calls reordered mechanically (CF-7) by a script that parses
    // each call's balanced-paren argument list rather than by regex on raw text; 3 try/fail/catch
    // blocks converted to assertThrows with real (observed, not guessed) messages (M-44).
    // use-core/surefire 49/234 -> 50/261 (default) and 82/505 -> 83/532 (oracle). use-gui
    // unchanged.
    // Floors are >= : the suite may GROW, it may never shrink. No floor is 0 and no floor
    // may be lowered to make a run pass — see harness-contract.md sec. 8 step 7 clause 1,
    // "Do not lower the floor."
    // -----------------------------------------------------------------------------------
    record Floor(int classes, int methods) { }

    static final Map<String, Floor> ORACLE = Map.of(
            "use-core/surefire", new Floor(83, 532),
            "use-gui/surefire", new Floor(8, 17),
            "use-core/failsafe", new Floor(1, 1),
            "use-gui/failsafe", new Floor(1, 129));

    static final Map<String, Floor> DEFAULT = Map.of(
            "use-core/surefire", new Floor(50, 261),
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

    /**
     * The exec-maven-plugin user properties the poms hand back to this checker so that an
     * ATTEMPT to tamper with the floor execution is a build FAILURE and not merely inert.
     * Each pom passes {@code --exec-<x>=${exec.<X>}}; Maven interpolates it when the operator
     * set the property on the command line and leaves the literal {@code ${...}} when they did
     * not, which is how "unset" is told from "set".
     *
     * <p>exec-maven-plugin is bound in this reactor to the floor executions and to nothing else,
     * so there is no legitimate {@code -Dexec.*} on this build. {@code outputFile} is in this
     * list because it is the one parameter that CANNOT be pinned in the POM (no value of it means
     * "the Maven log"), so detection is the only defence against it.
     */
    static final Map<String, String> EXEC_PROPERTY_OF_OPTION = new LinkedHashMap<>(Map.of(
            "exec-args", "exec.args",
            "exec-skip", "exec.skip",
            "exec-async", "exec.async",
            "exec-timeout", "exec.timeout",
            "exec-executable", "exec.executable",
            "exec-outputfile", "exec.outputFile",
            "exec-quietlogs", "exec.quietLogs",
            "exec-workingdir", "exec.workingdir"));

    /**
     * Every option whose value is a Maven property expression, mapped to the property name.
     * An option is "unset" IFF its value is EXACTLY {@code "${" + property + "}"} — see G-02
     * in the header. {@code module}, {@code module-dir}, {@code reactor-root} and
     * {@code stamp} are absent because the poms pass them as literals, never as a property.
     */
    static final Map<String, String> PROPERTY_OF_OPTION;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("effective", "use.upstreamOracle.effective");
        m.put("selected", "session.request.selectedProjects");
        m.put("resume-from", "session.request.resumeFrom");
        m.put("requested", "session.request.activeProfiles");
        m.put("allow-profiles", "use.floor.allowProfiles");
        m.putAll(EXEC_PROPERTY_OF_OPTION);
        PROPERTY_OF_OPTION = Map.copyOf(m);
    }

    /**
     * THE TWO LEGAL ARGVs, as exact option-name sets. Mode is chosen by matching one of these
     * EXACTLY — never by asking whether some option happens to be present. See the ARGV
     * CONTRACT in the header, defect G-01.
     */
    static final Set<String> STAMP_OPTIONS = Set.of(
            "stamp", "module-dir", "reactor-root", "requested", "allow-profiles");

    static final Set<String> CHECK_OPTIONS;
    /** The complete set of option names this program accepts. Anything else is FATAL. */
    static final Set<String> KNOWN_OPTIONS;
    static {
        Set<String> check = new LinkedHashSet<>(List.of(
                "module", "module-dir", "reactor-root", "requested", "effective",
                "selected", "resume-from", "allow-profiles"));
        check.addAll(EXEC_PROPERTY_OF_OPTION.keySet());
        CHECK_OPTIONS = Set.copyOf(check);
        Set<String> known = new LinkedHashSet<>(check);
        known.addAll(STAMP_OPTIONS);
        KNOWN_OPTIONS = Set.copyOf(known);
    }

    static final List<String> violations = new ArrayList<>();

    static void fail(String message) {
        violations.add(message);
    }

    public static void main(String[] args) throws Exception {
        // ---- G-01: PARSE, THEN VALIDATE THE WHOLE ARGV, THEN choose a mode. In that order,
        // and never any other: an option must not be able to act before the argv it arrived in
        // has been proved legal.
        Map<String, String> opt = parseArgs(args);
        String argvMode = validateArgv(opt);

        if ("stamp".equals(argvMode)) {
            stampMode(opt);
            return;
        }

        String module = opt.get("module");
        Path moduleDir = Path.of(opt.get("module-dir")).toAbsolutePath().normalize();
        Path reactorRoot = Path.of(opt.get("reactor-root")).toAbsolutePath().normalize();
        String requestedRaw = opt.get("requested");
        String effectiveRaw = opt.get("effective");
        String selectedRaw = unsetIfUninterpolated(opt, "selected");
        String resumeRaw = unsetIfUninterpolated(opt, "resume-from");
        String allowRaw = unsetIfUninterpolated(opt, "allow-profiles");

        if (!POM_MODULES.contains(module)) {
            die("--module must be one of " + POM_MODULES + ", got: " + module);
        }

        Set<String> requested = parseMavenList(requestedRaw);
        boolean oracleRequested = requested.contains(PROFILE_ID);
        Set<String> selected = parseMavenList(selectedRaw);
        boolean partial = !selected.isEmpty() || !resumeRaw.isBlank();
        String mode = oracleRequested ? "ORACLE" : "DEFAULT";

        System.out.println("[floor] ===== upstream-oracle floor check: " + module + " =====");
        System.out.println("[floor] requested profiles (reactor-wide, from the command line): "
                + (requested.isEmpty() ? "(none)" : requested));
        System.out.println("[floor] this module's upstream-oracle profile effective: " + effectiveRaw);
        System.out.println("[floor] mode: " + mode);
        // G-05: the allow-set is an escape hatch that WIDENS check B2. An escape hatch that
        // leaves no trace in a green run is not auditable, so its value is printed here and
        // recorded in the receipt, whether it was used or not.
        System.out.println("[floor] allow-profiles (-Duse.floor.allowProfiles): "
                + allowSetDescription(allowRaw));
        System.out.println("[floor] reactor: " + (partial
                ? "PARTIAL — selected projects " + (selected.isEmpty() ? "(none)" : selected)
                  + ", resume-from " + (resumeRaw.isBlank() ? "(none)" : resumeRaw)
                : "FULL (no -pl/--projects, no -rf/--resume-from)"));

        // ---- A. WIRING -----------------------------------------------------------------
        for (String m : POM_MODULES) {
            checkWiring(reactorRoot.resolve(m).resolve("pom.xml"), m);
        }
        Path self = reactorRoot.resolve("scripts").resolve("UpstreamOracleFloor.java");
        if (!Files.isRegularFile(self)) {
            fail("WIRING: the floor checker itself is missing at " + self);
        }
        if (!Files.isRegularFile(reactorRoot.resolve(WIRING_TEST))) {
            fail("WIRING: " + WIRING_TEST + " is missing. That Jupiter test is the half of the"
                    + " F-01 fix that cannot be silenced by an exec-maven-plugin user property;"
                    + " without it, neutralising this exec binding would go unnoticed."
                    + " See docs/port2/upstream-oracle-floor-verification.md F-01.");
        }
        if (!Files.isRegularFile(reactorRoot.resolve(GATE_WRAPPER))) {
            fail("WIRING: " + GATE_WRAPPER + " is missing. The acceptance gate is DEFINED as that"
                    + " invocation (F-02): it hard-codes the profile id, so a typo cannot degrade"
                    + " the gate to a green default run. Hand-typing -P" + PROFILE_ID
                    + " is not the gate.");
        }

        // ---- A2. TAMPERING (F-01) ------------------------------------------------------
        // The pins in the poms make these properties INERT. Inert is not enough: an attempt to
        // silence the gate must be loud, or the next reader of a green log cannot tell that one
        // was made. So each -Dexec.* is a violation in its own right. `outputFile` is here
        // because it is the only exec:exec parameter with no pinnable value.
        //
        // G-02, 2026-08-18: the set/unset test used to be `value.contains("${")`, so ANY value
        // merely CONTAINING the two-character placeholder opener read as UNSET. That is how
        // `-Dexec.outputFile='/tmp/floorhide${z}.txt'` diverted every [floor] line to a file,
        // in a green build, with zero TAMPERING violations — against the one exec parameter
        // for which detection is the WHOLE defence, because it cannot be pinned. The test is
        // now exact: unset means the value IS the whole placeholder, end to end.
        for (Map.Entry<String, String> e : EXEC_PROPERTY_OF_OPTION.entrySet()) {
            if (isSet(opt, e.getKey())) {
                String value = opt.get(e.getKey());
                fail("TAMPERING: -D" + e.getValue() + "=" + value + " was set on the command line."
                        + " That property belongs to the floor's own exec-maven-plugin execution,"
                        + " which is the ONLY exec-maven-plugin binding in this reactor, so there"
                        + " is no legitimate use for it here. It is inert — the pom pins the"
                        + " corresponding element — but an attempt to switch the gate off is a"
                        + " BUILD FAILURE, not a silent no-op. This is defect F-01"
                        + " (docs/port2/upstream-oracle-floor-verification.md sec. 3.7), where"
                        + " -Dexec.args=-version produced BUILD SUCCESS with zero [floor] lines.");
            }
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

        // ---- B2. AN UNACTIVATABLE REQUESTED PROFILE (F-02) -----------------------------
        // Round 10 sec. 3.5 item 3 called the earlier refusal to do this refutable, and it was
        // right: "the checker would have to know every legitimate profile id, which it cannot"
        // is false, because it already text-parses both poms for <id>upstream-oracle</id>.
        // Collecting every <id> declared inside <profiles> in every reactor pom is the same
        // parse. So `mvn -Pupstream-oracle-typo` now FAILS the build instead of quietly running
        // the vacuous default gate. It over-collects rather than under-collects (any <id> inside
        // a <profiles> element counts), because a false failure here would be worse than a
        // slightly permissive allow-set.
        //
        // RESIDUAL, stated because a limit nobody writes down is a false claim: a profile
        // declared in a settings.xml or in an ancestor pom outside this reactor would not be
        // found. This machine has neither (upstream-oracle-verification.md sec. 11: no .mvn, no
        // ~/.m2/settings.xml, empty MAVEN_OPTS/MAVEN_ARGS), so the pom set is the complete
        // authority here. If a future machine has one, declare the id or pass
        // -Duse.floor.allowProfiles=<id>[,<id>] — which widens THIS check and nothing else.
        Set<String> declaredProfiles = declaredProfileIds(reactorRoot);
        Set<String> allowedProfiles = parseMavenList(allowRaw);
        for (String r : requested) {
            if (!declaredProfiles.contains(r) && !allowedProfiles.contains(r)) {
                fail("PROFILE: -P" + r + " was requested on the command line but NO pom in this"
                        + " reactor declares a profile with that id. Maven only WARNS about that"
                        + " and then builds on, so the gate would have run the vacuous"
                        + " default-build counts and printed PASS while the operator believed"
                        + " they had asked for the upstream oracle — defect F-02"
                        + " (docs/port2/upstream-oracle-floor-verification.md sec. 3.5). Declared"
                        + " profile ids in this reactor: " + declaredProfiles + ". The acceptance"
                        + " gate is " + GATE_WRAPPER + ", which hard-codes the id; do not hand-type"
                        + " -P. If this id IS legitimate, declare it in a pom or pass"
                        + " -Duse.floor.allowProfiles=" + r + ".");
            }
        }

        // ---- C. COUNT FLOORS -----------------------------------------------------------
        Path stampFile = moduleDir.resolve("target").resolve(STAMP_NAME);
        long stampMillis = Long.MAX_VALUE;
        String stampInstant = "(absent)";
        if (!Files.isRegularFile(stampFile)) {
            fail("FRESHNESS: no " + STAMP_NAME + " in " + moduleDir.resolve("target")
                    + ". The `upstream-oracle-floor-stamp` execution did not run, so this check"
                    + " cannot tell reports written by THIS build from stale reports left by an"
                    + " earlier -P" + PROFILE_ID + " run. Counting them would be exactly the"
                    + " vacuous pass D-01 is about.");
        } else {
            stampMillis = Files.getLastModifiedTime(stampFile).toMillis();
            stampInstant = Instant.ofEpochMilli(stampMillis).toString();
            System.out.println("[floor] freshness stamp: " + stampInstant
                    + " — reports older than this are stale and are NOT counted");
        }

        Map<String, Floor> floors = oracleRequested ? ORACLE : DEFAULT;
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (String tier : TIERS) {
            String key = module + "/" + tier;
            Floor floor = floors.get(key);
            if (floor == null) {
                die("no floor pinned for " + key);
            }
            Path dir = moduleDir.resolve("target").resolve(tier + "-reports");
            Tally t = tally(dir, stampMillis);
            tallies.put(tier, t);
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
        Tally sure = tallies.get("surefire");
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
        // F-03: a partial reactor never earns the word PASS. `-pl` is a legitimate developer
        // flag, so this is not a build failure; it is a refusal to be quoted as the gate.
        String verdict = !violations.isEmpty() ? "FAIL" : partial ? "PARTIAL" : "PASS";
        writeReceipt(moduleDir, module, mode, verdict, partial, selected, resumeRaw, requested,
                effectiveRaw, allowRaw, stampInstant, tallies);

        if (violations.isEmpty()) {
            if (partial) {
                System.out.println("[floor] PARTIAL — " + module + " met its own pinned floors in "
                        + mode + " mode, but THIS WAS A PARTIAL REACTOR"
                        + (selected.isEmpty() ? "" : " (-pl/--projects " + selected + ")")
                        + (resumeRaw.isBlank() ? "" : " (-rf/--resume-from " + resumeRaw + ")")
                        + ", so the other module's floors were NOT checked. A partial reactor is"
                        + " NOT the acceptance gate: run " + GATE_WRAPPER + ". Do not quote this"
                        + " line as a green gate (F-03).");
            } else {
                System.out.println("[floor] PASS — " + module + " met every pinned floor in "
                        + mode + " mode.");
            }
            return;
        }
        System.out.println();
        System.out.println("[floor] ###############################################################");
        System.out.println("[floor] FAIL — " + violations.size() + " floor violation(s) in " + module
                + " (" + mode + " mode):");
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

    // ---------------- the receipt (F-01 mechanism 3, F-02) ---------------------------------

    /**
     * Writes the machine-readable receipt scripts/upstream-oracle-gate.sh verifies after Maven
     * has exited. Written on FAIL as well as PASS: its ABSENCE is the signal that the check did
     * not run, which is the only thing a silenced exec binding leaves behind.
     */
    static void writeReceipt(Path moduleDir, String module, String mode, String verdict,
            boolean partial, Set<String> selected, String resumeFrom, Set<String> requested,
            String effective, String allowProfiles, String stampInstant,
            Map<String, Tally> tallies) {
        Path receipt = moduleDir.resolve("target").resolve(RECEIPT_NAME);
        StringBuilder sb = new StringBuilder();
        sb.append("# upstream-oracle floor receipt — written by scripts/UpstreamOracleFloor.java\n");
        sb.append("# Verified after the build by scripts/upstream-oracle-gate.sh. Its absence means\n");
        sb.append("# the verify-phase floor check did not run to completion (F-01).\n");
        sb.append("module=").append(module).append('\n');
        sb.append("mode=").append(mode).append('\n');
        sb.append("verdict=").append(verdict).append('\n');
        sb.append("partial-reactor=").append(partial).append('\n');
        sb.append("selected-projects=").append(selected.isEmpty() ? "(none)" : selected).append('\n');
        sb.append("resume-from=").append(resumeFrom.isBlank() ? "(none)" : resumeFrom).append('\n');
        sb.append("requested-profiles=").append(requested.isEmpty() ? "(none)" : requested).append('\n');
        sb.append("effective=").append(effective).append('\n');
        // G-05: the escape hatch is recorded in the receipt too, so that the ON-DISK evidence a
        // stage quotes says whether check B2 was widened. scripts/upstream-oracle-gate.sh
        // requires this line to read `(none)` on an acceptance run.
        sb.append("allow-profiles=").append(allowSetDescription(allowProfiles)).append('\n');
        sb.append("stamp=").append(stampInstant).append('\n');
        sb.append("violations=").append(violations.size()).append('\n');
        for (String tier : TIERS) {
            Tally t = tallies.get(tier);
            if (t == null) {
                continue;
            }
            sb.append(tier).append(".classes=").append(t.classes).append('\n');
            sb.append(tier).append(".methods=").append(t.methods).append('\n');
            sb.append(tier).append(".executions=").append(t.executions).append('\n');
        }
        sb.append("written=").append(Instant.now()).append('\n');
        try {
            Files.createDirectories(receipt.getParent());
            Files.writeString(receipt, sb.toString(), StandardCharsets.UTF_8);
            System.out.println("[floor] wrote receipt " + receipt + " (verdict=" + verdict + ")");
        } catch (IOException e) {
            // A receipt that cannot be written must not be silently absent.
            System.out.println("[floor] FATAL — cannot write receipt " + receipt + ": " + e);
            System.exit(2);
        }
    }

    // ---------------- A. wiring -----------------------------------------------------------

    /**
     * Asserts, from the pom text with XML comments stripped and all whitespace removed, that
     * the module still carries the profile, the engine inside it, both floor executions, and
     * every pin that keeps a command-line property from silencing them. Text rather than an
     * effective-model query on purpose: this must hold for the OTHER module too, which the
     * running Maven session does not expose.
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
        if (!x.contains("--module=" + module)) {
            fail("WIRING: " + module + "/pom.xml's floor execution does not pass --module="
                    + module + ".");
        }
        // ---- F-01: every exec:exec parameter that could silence the check, pinned ---------
        // Both floor executions must pin all four. exec:exec resolves a @Parameter's user
        // property ONLY when the POM leaves the element out, so a pinned element makes the
        // corresponding -D inert. Verified on this tree: round 10 break (f) showed the pinned
        // <executable> holding while the unpinned argument list was replaced by -Dexec.args.
        pin(x, module, "<skip>false</skip>", "exec.skip",
                "-Dexec.skip=true would skip the goal");
        pin(x, module, "<commandlineArgs>", "exec.args",
                "-Dexec.args=-version REPLACES the argument list, so `java -version` runs instead"
                + " of the checker and the build stays green with zero [floor] lines (F-01)");
        pin(x, module, "<async>false</async>", "exec.async",
                "-Dexec.async=true detaches the process, so its exit code is never examined");
        pin(x, module, "<timeout>0</timeout>", "exec.timeout",
                "-Dexec.timeout=<n> could kill the checker mid-flight");
        pin(x, module, "<quietLogs>false</quietLogs>", "exec.quietLogs",
                "-Dexec.quietLogs=true demotes every [floor] line to debug level, so a reader"
                + " sampling the log sees a green build and no gate");
        pin(x, module, "<executable>${java.home}/bin/java</executable>", "exec.executable",
                "-Dexec.executable=/bin/true would run something else entirely");
        pin(x, module, "<workingDirectory>${project.basedir}</workingDirectory>", "exec.workingdir",
                "-Dexec.workingdir=<elsewhere> would move the checker's cwd, and the floor"
                + " executions pass RELATIVE paths (which is what keeps them immune to"
                + " exec:exec's whitespace splitting of <commandlineArgs>)");
        // ---- G-04: BOTH executions carry the unactivatable-profile check's inputs -----------
        // The initialize-phase execution needs --reactor-root, --requested and --allow-profiles
        // for the early profile guard, so each of these three must appear TWICE — once per
        // execution. Two occurrences, not one: with only the verify-phase copy, `mvn test
        // -Pupstream-oracle-typo` is green again with no gate in it (G-04).
        for (String token : List.of("--reactor-root=..",
                "--requested=${session.request.activeProfiles}",
                "--allow-profiles=${use.floor.allowProfiles}")) {
            int n = count(x, token);
            if (n < 2) {
                fail("WIRING: " + module + "/pom.xml passes " + token + " " + n + " time(s), needs"
                        + " 2 — one in the `upstream-oracle-floor-stamp` execution (phase"
                        + " initialize) and one in `upstream-oracle-floor` (phase verify). The"
                        + " initialize-phase copy is what makes a mistyped -P id fail under a"
                        + " TRUNCATED lifecycle such as `mvn test`, where the verify-phase floor"
                        + " never runs at all (defect G-04,"
                        + " docs/port2/upstream-oracle-gate-round11.md sec. 5).");
            }
        }
        // ...and the tamper detector: the verify execution must hand every one of those user
        // properties back to this checker, so that setting one FAILS the build (check A2).
        for (Map.Entry<String, String> e : EXEC_PROPERTY_OF_OPTION.entrySet()) {
            String token = "--" + e.getKey() + "=${" + e.getValue() + "}";
            if (!x.contains(token)) {
                fail("WIRING: " + module + "/pom.xml's floor execution does not pass " + token
                        + ", so a -D" + e.getValue() + " on the command line would go undetected"
                        + " instead of failing the build (F-01, check A2).");
            }
        }
    }

    /**
     * Every profile id declared by any pom in this reactor: the reactor root's pom and the
     * {@code pom.xml} of every directory one level below it. Deliberately over-collecting — every
     * {@code <id>...</id>} inside a {@code <profiles>} element is taken, without checking that it
     * is a profile's own id rather than, say, an execution's — because the consequence of
     * over-collecting is a marginally wider allow-set, and the consequence of under-collecting
     * would be a build that fails on a legitimate profile.
     */
    static Set<String> declaredProfileIds(Path reactorRoot) {
        Set<String> ids = new TreeSet<>();
        List<Path> poms = new ArrayList<>();
        poms.add(reactorRoot.resolve("pom.xml"));
        try (Stream<Path> s = Files.list(reactorRoot)) {
            s.filter(Files::isDirectory).map(d -> d.resolve("pom.xml"))
                    .filter(Files::isRegularFile).sorted().forEach(poms::add);
        } catch (IOException e) {
            fail("PROFILE: cannot list the reactor root " + reactorRoot + " (" + e + "), so the"
                    + " declared-profile check cannot run.");
            return ids;
        }
        for (Path pom : poms) {
            if (!Files.isRegularFile(pom)) {
                continue;
            }
            String x;
            try {
                x = Files.readString(pom, StandardCharsets.UTF_8)
                        .replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", "");
            } catch (IOException e) {
                fail("PROFILE: cannot read " + pom + " (" + e + ").");
                continue;
            }
            int p0 = x.indexOf("<profiles>");
            int p1 = x.indexOf("</profiles>");
            if (p0 < 0 || p1 < p0) {
                continue;
            }
            String profiles = x.substring(p0, p1);
            for (int i = profiles.indexOf("<id>"); i >= 0; i = profiles.indexOf("<id>", i + 1)) {
                int end = profiles.indexOf("</id>", i);
                if (end > i) {
                    ids.add(profiles.substring(i + "<id>".length(), end));
                }
            }
        }
        return ids;
    }

    /** Both floor executions must carry {@code element}; two occurrences, no fewer. */
    static void pin(String strippedPom, String module, String element, String property,
            String consequence) {
        int n = count(strippedPom, element);
        if (n < 2) {
            fail("WIRING: " + module + "/pom.xml carries " + element + " " + n + " time(s), needs 2"
                    + " (one per floor execution). Without it the user property " + property
                    + " is honoured and " + consequence + ". See F-01 in"
                    + " docs/port2/upstream-oracle-floor-verification.md.");
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

    /**
     * The ARGV CONTRACT (see the header). A token of the form {@code --name=value} starts an
     * option; any other token is a continuation of the previous option's value, joined with a
     * single space — that is how a Maven List rendered as {@code [a, b]} survives exec:exec's
     * whitespace split. An unknown option name, a duplicate, a continuation before any option,
     * or a {@code --}-prefixed token that is not a well-formed option is FATAL.
     *
     * <p>This method does NOT decide anything. It parses. {@link #validateArgv} decides, and
     * nothing acts before it has run — that ordering IS the G-01 fix.
     */
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opt = new LinkedHashMap<>();
        String current = null;
        for (String a : args) {
            int eq = a.indexOf('=');
            if (a.startsWith("--") && eq > 2) {
                String name = a.substring(2, eq);
                if (!KNOWN_OPTIONS.contains(name)) {
                    die("unknown option --" + name + "=; accepted options are "
                            + new TreeSet<>(KNOWN_OPTIONS));
                }
                if (opt.containsKey(name)) {
                    die("option --" + name + "= given twice. Every option is passed EXACTLY once"
                            + " by the pom, so a second one arrived inside an interpolated"
                            + " property value — that is a command-line injection into this"
                            + " checker's argv (G-01), not a configuration mistake.");
                }
                opt.put(name, a.substring(eq + 1));
                current = name;
            } else if (a.startsWith("--")) {
                die("malformed option-looking token '" + a + "' (expected --name=value). A token"
                        + " that begins with -- is never accepted as a continuation, because"
                        + " that is how an injected payload would hide inside a Maven property"
                        + " value (G-01).");
            } else if (current != null) {
                opt.put(current, opt.get(current) + " " + a);
            } else {
                die("unparseable argument (expected --name=value): " + a);
            }
        }
        if (opt.isEmpty()) {
            die("no arguments. This program is run by Maven, not by hand; see the ARGV CONTRACT"
                    + " in scripts/UpstreamOracleFloor.java.");
        }
        return opt;
    }

    /**
     * Validates the ENTIRE argv and returns the mode it names — {@code "stamp"} or
     * {@code "check"} — or exits 2. Nothing else in this program is allowed to run first.
     *
     * <p>Three rules, in this order:
     * <ol>
     *   <li>no option value may itself parse as an option. An interpolated Maven property is
     *       DATA. If it looks like argv, someone put it there;</li>
     *   <li>the option-name set must match {@link #STAMP_OPTIONS} or {@link #CHECK_OPTIONS}
     *       EXACTLY — no extra name, no missing name. This is what makes an injected
     *       {@code --stamp=true} in the verify-phase argv fatal instead of a green
     *       short-circuit (G-01);</li>
     *   <li>in stamp mode, {@code --stamp} must be {@code true}.</li>
     * </ol>
     */
    static String validateArgv(Map<String, String> opt) {
        for (Map.Entry<String, String> e : opt.entrySet()) {
            for (String token : e.getValue().trim().split("\\s+")) {
                if (token.startsWith("--") && token.indexOf('=') > 2) {
                    die("ARGV INJECTION: the value of --" + e.getKey() + "= itself parses as an"
                            + " option ('" + token + "'). That value comes from the Maven"
                            + " property " + PROPERTY_OF_OPTION.getOrDefault(e.getKey(), "(a pom"
                            + " literal)") + ", which is DATA and may never contribute argv to"
                            + " this checker. See defect G-01,"
                            + " docs/port2/upstream-oracle-gate-round11.md sec. 2.");
                }
            }
        }
        Set<String> given = new TreeSet<>(opt.keySet());
        if (given.equals(new TreeSet<>(STAMP_OPTIONS))) {
            if (!"true".equals(opt.get("stamp"))) {
                die("--stamp must be =true, got: " + opt.get("stamp"));
            }
            return "stamp";
        }
        if (given.equals(new TreeSet<>(CHECK_OPTIONS))) {
            return "check";
        }
        Set<String> unexpectedForCheck = new TreeSet<>(given);
        unexpectedForCheck.removeAll(CHECK_OPTIONS);
        Set<String> missingForCheck = new TreeSet<>(CHECK_OPTIONS);
        missingForCheck.removeAll(given);
        die("the argv is neither of the two legal shapes. Got " + given
                + "; the stamp argv is EXACTLY " + new TreeSet<>(STAMP_OPTIONS)
                + " and the check argv is EXACTLY " + new TreeSet<>(CHECK_OPTIONS)
                + ". Relative to the check argv: unexpected " + unexpectedForCheck
                + ", missing " + missingForCheck
                + ". An option that arrived without the pom passing it came from an"
                + " interpolated property value (G-01): exec:exec splits <commandlineArgs>"
                + " AFTER Maven interpolates it, so a property value containing a space adds"
                + " argv tokens. This is fatal, exit 2, and no receipt is written.");
        return "unreachable";
    }

    /** The initialize-phase execution: freshness stamp, plus the G-04 early profile guard. */
    static void stampMode(Map<String, String> opt) throws IOException {
        Path moduleDir = Path.of(opt.get("module-dir")).toAbsolutePath().normalize();
        Path reactorRoot = Path.of(opt.get("reactor-root")).toAbsolutePath().normalize();
        String allowRaw = unsetIfUninterpolated(opt, "allow-profiles");

        // ---- G-04: the unactivatable-profile check, ONE PHASE THAT EVERY LIFECYCLE REACHES --
        // Until 2026-08-18 the whole gate hung off phase `verify`, so
        //     mvn -B test -Pupstream-oracle-typo   ->  BUILD SUCCESS, exit 0, no floor check at
        // all, the typo a [WARNING] in a 1393-line log. `mvn test` is disclaimed as a gate in
        // four normative places, but typing it from habit is the one realistic ACCIDENT on this
        // gate's list, and ground rule 4's sibling loop issues `mvn -B clean test` unattended.
        // `initialize` is the second phase of the default lifecycle, so this fires for `test`,
        // `compile`, `package`, `verify` and `install` alike. (It does NOT fire for `mvn clean`,
        // which is a different lifecycle — that is correct: `mvn -q clean` is what the wrapper
        // runs before each build, and it collects nothing to gate.)
        Set<String> requested = parseMavenList(opt.get("requested"));
        Set<String> declared = declaredProfileIds(reactorRoot);
        Set<String> allowed = parseMavenList(allowRaw);
        System.out.println("[floor] initialize: requested profiles "
                + (requested.isEmpty() ? "(none)" : requested.toString())
                + ", declared in this reactor " + declared
                + ", allow-profiles (-Duse.floor.allowProfiles) " + allowSetDescription(allowRaw));
        for (String r : requested) {
            if (!declared.contains(r) && !allowed.contains(r)) {
                die("PROFILE (initialize): -P" + r + " was requested on the command line but NO"
                        + " pom in this reactor declares a profile with that id. Maven only WARNS"
                        + " about that and builds on, so `mvn test -P" + r + "` would otherwise be"
                        + " a green build with no gate in it at all (defect G-04,"
                        + " docs/port2/upstream-oracle-gate-round11.md sec. 5). This check is"
                        + " bound to `initialize` precisely so that a TRUNCATED LIFECYCLE cannot"
                        + " outrun it. Declared profile ids: " + declared + ". The acceptance"
                        + " gate is " + GATE_WRAPPER + ", which hard-codes the id; do not"
                        + " hand-type -P. If this id IS legitimate, declare it in a pom or pass"
                        + " -Duse.floor.allowProfiles=" + r + ".");
            }
        }

        Path stamp = moduleDir.resolve("target").resolve(STAMP_NAME);
        Files.createDirectories(stamp.getParent());
        Files.writeString(stamp, "upstream-oracle floor stamp: reports older than this file\n"
                + "were not written by this build and are not counted.\n"
                + Instant.now() + "\n", StandardCharsets.UTF_8);
        System.out.println("[floor] wrote freshness stamp " + stamp);
    }

    /**
     * G-02. A Maven property the operator did not set arrives as its own placeholder ENTIRE:
     * the value is {@code "${" + property + "}"} and nothing else. Anything else — including a
     * value that merely CONTAINS a placeholder opener, and including the empty string — is SET.
     *
     * <p>The old test was {@code value.contains("${")}, which read
     * {@code /tmp/floorhide${z}.txt} as unset and let {@code -Dexec.outputFile} divert every
     * {@code [floor]} line with no violation raised.
     */
    static boolean isSet(Map<String, String> opt, String option) {
        String value = opt.get(option);
        if (value == null) {
            return false;
        }
        String property = PROPERTY_OF_OPTION.get(option);
        return property == null || !value.equals("${" + property + "}");
    }

    /** The option's value, or {@code ""} when the option is unset in the {@link #isSet} sense. */
    static String unsetIfUninterpolated(Map<String, String> opt, String option) {
        return isSet(opt, option) ? opt.get(option) : "";
    }

    /** G-05: how the allow-set is rendered in the log and in the receipt. */
    static String allowSetDescription(String allowRaw) {
        Set<String> allowed = parseMavenList(allowRaw);
        return allowed.isEmpty()
                ? "(none)"
                : allowed + "  <-- CHECK B2 WAS WIDENED BY THE COMMAND LINE";
    }

    /** Parses a Maven {@code List} rendering, e.g. {@code [upstream-oracle]} or {@code []}. */
    static Set<String> parseMavenList(String raw) {
        String s = raw == null ? "" : raw.trim();
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

    static void die(String message) {
        System.out.println("[floor] FATAL — " + message);
        System.out.println("[floor] usage — the check argv is EXACTLY: "
                + new TreeSet<>(CHECK_OPTIONS));
        System.out.println("[floor] usage — the stamp argv is EXACTLY: "
                + new TreeSet<>(STAMP_OPTIONS));
        System.out.println("[floor] No other argv is accepted. See the ARGV CONTRACT in"
                + " scripts/UpstreamOracleFloor.java and docs/port2/gate-threat-model.md.");
        System.exit(2);
    }

    private UpstreamOracleFloor() {
        throw new AssertionError("not instantiable");
    }
}
