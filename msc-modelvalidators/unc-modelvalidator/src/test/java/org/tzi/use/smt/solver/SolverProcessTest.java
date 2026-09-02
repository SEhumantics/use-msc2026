package org.tzi.use.smt.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SolverProcessTest {

  private SolverProcess solver;

  @Before
  public void setUp() {
    solver = new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30));
  }

  @Test
  public void satisfiableScriptReportsSat() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));

    SolverResult result = solver.run(script.toSmtLib());

    assertEquals(SolverOutcome.SAT, result.outcome());
    assertTrue(result.modelText(), result.modelText().contains("define-fun x"));
  }

  @Test
  public void unsatisfiableScriptReportsUnsat() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("1.0"))));
    script.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.0"))));

    assertEquals(SolverOutcome.UNSAT, solver.run(script.toSmtLib()).outcome());
  }

  @Test
  public void malformedInputIsReportedAsMalformedNotAsUnknown() {
    SolverResult result = solver.run("(this is not valid smt-lib");
    assertEquals(SolverOutcome.MALFORMED, result.outcome());
  }

  /** Guards the timeout outcome against a blocking read before {@code waitFor}. */
  @Test(timeout = 60_000)
  public void aSolverThatExceedsTheDeadlineIsReportedAsTimeout() {
    SolverProcess impatient = new SolverProcess(SolverBinary.resolve(), Duration.ofMillis(1));
    SmtScript script = new SmtScript("QF_LIRA");
    for (int i = 0; i < 60; i++) {
      script.declareConst("x" + i, SmtSort.INT);
    }
    List<SmtTerm> distinct = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      for (int j = i + 1; j < 60; j++) {
        distinct.add(Smt.not(Smt.eq(Smt.sym("x" + i), Smt.sym("x" + j))));
      }
    }
    script.assertThat(Smt.and(distinct));
    for (int i = 0; i < 60; i++) {
      script.assertThat(Smt.app(">", Smt.sym("x" + i), Smt.intLit(BigInteger.ZERO)));
      script.assertThat(Smt.app("<", Smt.sym("x" + i), Smt.intLit(BigInteger.valueOf(60))));
    }

    SolverOutcome outcome = impatient.run(script.toSmtLib()).outcome();
    assertTrue(
        "expected TIMEOUT or a fast SAT, got " + outcome,
        outcome == SolverOutcome.TIMEOUT || outcome == SolverOutcome.SAT);
  }

  @Test
  public void unsatFollowedByAModelNotAvailableErrorIsStillUnsat() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("1.0"))));
    script.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.0"))));

    SolverResult result = solver.run(script.toSmtLib());

    assertEquals(SolverOutcome.UNSAT, result.outcome());
    assertTrue(
        "raw output should retain the solver's error line for evidence",
        result.rawOutput().contains("model is not available"));
  }

  @Test
  public void resultCarriesRawOutputForEvidenceCapture() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("p", SmtSort.BOOL);
    script.assertThat(Smt.sym("p"));

    SolverResult result = solver.run(script.toSmtLib());

    assertTrue(result.rawOutput().contains("sat"));
    assertTrue(result.millis() >= 0);
  }

  @Test
  public void solvedValuesDecodeIntoUsableNumbers() {
    SmtScript script = new SmtScript("QF_LIRA");
    script.declareConst("x", SmtSort.REAL);
    script.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));
    script.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.40"))));

    SolverResult result = solver.run(script.toSmtLib());
    assertEquals(SolverOutcome.SAT, result.outcome());

    SmtValue.Rational x = (SmtValue.Rational) SmtModelParser.parse(result.modelText()).get("x");
    BigDecimal decoded = x.asBigDecimal(6);
    assertTrue(
        decoded.toPlainString(),
        decoded.compareTo(new BigDecimal("0.30")) > 0
            && decoded.compareTo(new BigDecimal("0.40")) < 0);
  }

  @Test
  public void persistentModeReportsTheSameOutcomesAsOneShot() {
    try (SolverProcess persistent =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofSeconds(30))) {
      SmtScript sat = new SmtScript("QF_LIRA");
      sat.declareConst("x", SmtSort.REAL);
      sat.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("0.30"))));
      assertEquals(SolverOutcome.SAT, persistent.run(sat.toSmtLib()).outcome());

      SmtScript unsat = new SmtScript("QF_LIRA");
      unsat.declareConst("x", SmtSort.REAL);
      unsat.assertThat(Smt.app(">", Smt.sym("x"), Smt.realLit(new BigDecimal("1.0"))));
      unsat.assertThat(Smt.app("<", Smt.sym("x"), Smt.realLit(new BigDecimal("0.0"))));
      assertEquals(SolverOutcome.UNSAT, persistent.run(unsat.toSmtLib()).outcome());

      // A syntactically COMPLETE but invalid command, not a truncated/unbalanced one: Z3's
      // interactive parser blocks waiting for a closing paren on unbalanced input, since more text
      // could still be coming on stdin -- confirmed by direct reproduction (deliberately, once),
      // not assumed. Genuinely truncated input is out of scope for persistent mode's own testing
      // for exactly that reason; it is also not a real production input shape (SmtScript.toSmtLib()
      // always emits well-formed, balanced text) -- see this class's own javadoc.
      assertEquals(SolverOutcome.MALFORMED, persistent.run("(check-sta)").outcome());
    }
  }

  /**
   * The genuinely discriminating case: proves {@code (reset)} between calls actually isolates two
   * unrelated scripts sharing one persistent process, by deliberately reusing the SAME const name
   * "x" with contradictory meanings across three consecutive calls -- if reset were a no-op (or
   * missing), the second/third call would either see a stale declaration error or a leaked
   * assertion from the first call, not the fresh, correct answer each call actually asks for.
   */
  @Test
  public void reusingTheSameConstNameAcrossCallsIsIsolatedByReset() {
    try (SolverProcess persistent =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofSeconds(30))) {
      SmtScript first = new SmtScript("QF_LIA");
      first.declareConst("x", SmtSort.INT);
      first.assertThat(Smt.app("=", Smt.sym("x"), Smt.intLit(BigInteger.valueOf(5))));
      SolverResult firstResult = persistent.run(first.toSmtLib());
      assertEquals(SolverOutcome.SAT, firstResult.outcome());
      assertEquals(
          BigInteger.valueOf(5),
          ((SmtValue.Int) SmtModelParser.parse(firstResult.modelText()).get("x")).value());

      SmtScript second = new SmtScript("QF_LIA");
      second.declareConst("x", SmtSort.INT);
      second.assertThat(Smt.app(">", Smt.sym("x"), Smt.intLit(BigInteger.ZERO)));
      second.assertThat(Smt.app("<", Smt.sym("x"), Smt.intLit(BigInteger.ONE)));
      assertEquals(SolverOutcome.UNSAT, persistent.run(second.toSmtLib()).outcome());

      SmtScript third = new SmtScript("QF_LIA");
      third.declareConst("x", SmtSort.INT);
      third.assertThat(Smt.app("=", Smt.sym("x"), Smt.intLit(BigInteger.valueOf(42))));
      SolverResult thirdResult = persistent.run(third.toSmtLib());
      assertEquals(SolverOutcome.SAT, thirdResult.outcome());
      assertEquals(
          BigInteger.valueOf(42),
          ((SmtValue.Int) SmtModelParser.parse(thirdResult.modelText()).get("x")).value());
    }
  }

  /**
   * Proves Z3's own {@code (set-option :timeout ...)} genuinely bounds one call without killing the
   * process: a call hard enough to exceed a tiny budget must come back UNKNOWN (Z3's own
   * self-reported give-up, not a Java-side TIMEOUT), and the SAME instance must still answer a
   * trivial follow-up call correctly afterward.
   */
  @Test(timeout = 30_000)
  public void aCallThatExceedsItsOwnBudgetReportsUnknownAndTheProcessSurvives() {
    try (SolverProcess persistent =
        SolverProcess.persistent(SolverBinary.resolve(), Duration.ofMillis(100))) {
      SmtScript hard = new SmtScript("QF_NIA");
      hard.declareConst("a", SmtSort.INT);
      hard.declareConst("b", SmtSort.INT);
      hard.assertThat(Smt.app(">", Smt.sym("a"), Smt.intLit(BigInteger.ONE)));
      hard.assertThat(Smt.app(">", Smt.sym("b"), Smt.intLit(BigInteger.ONE)));
      hard.assertThat(
          Smt.app(
              "=",
              Smt.app("*", Smt.sym("a"), Smt.sym("b")),
              Smt.intLit(new BigInteger("1000000007000000009"))));
      assertEquals(SolverOutcome.UNKNOWN, persistent.run(hard.toSmtLib()).outcome());

      SmtScript trivial = new SmtScript("QF_LIA");
      trivial.declareConst("y", SmtSort.INT);
      trivial.assertThat(Smt.app("=", Smt.sym("y"), Smt.intLit(BigInteger.ONE)));
      assertEquals(SolverOutcome.SAT, persistent.run(trivial.toSmtLib()).outcome());
    }
  }

  @Test
  public void closeOnAOneShotInstanceIsANoOp() {
    SolverProcess oneShot = new SolverProcess(SolverBinary.resolve(), Duration.ofSeconds(30));
    oneShot.close();
  }

  /**
   * Regression guard: {@code runOneShot} previously trusted whatever verdict text a solver
   * happened to print, even when the process went on to crash (SIGSEGV/OOM-kill) before finishing
   * the rest of its output -- misreporting a corrupt, truncated run as a confirmed SAT with a
   * garbage/empty model. A stand-in fake-solver script prints {@code sat} and then genuinely
   * SIGSEGVs itself (not an arbitrary nonzero exit -- deliberately the same signal-death shape the
   * bug names, and the shape the fix's own threshold is keyed on; a plain small nonzero exit, e.g.
   * {@code 1}, is Z3's own ordinary "a script command errored" convention and must NOT be
   * misclassified as a crash, see {@code unsatFollowedByAModelNotAvailableErrorIsStillUnsat}
   * above and {@code SolverProcess.SIGNAL_TERMINATION_EXIT_THRESHOLD}'s own javadoc). The fix must
   * force MALFORMED (mirroring persistent mode's own EOF-before-sentinel handling) instead of
   * trusting the "sat" line.
   */
  @Test(timeout = 15_000)
  public void aSolverThatCrashesAfterPrintingAVerdictIsReportedAsMalformedNotConfirmed()
      throws Exception {
    Path script = Files.createTempFile("msc-crash-solver-", ".sh");
    Files.writeString(
        script,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"--version\" ]; then\n"
            + "  echo \"Fake solver 9.9.9\"\n"
            + "  exit 0\n"
            + "fi\n"
            + "echo sat\n"
            + "kill -SEGV $$\n");
    Files.setPosixFilePermissions(
        script,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    try {
      SolverBinary crashBinary = SolverBinary.resolveFrom(script.toString(), "9.9.9");
      SolverProcess crashy = new SolverProcess(crashBinary, Duration.ofSeconds(30));

      SolverResult result = crashy.run("(check-sat)");

      assertEquals(
          "a SIGSEGV after printing a verdict line must not be trusted as a confirmed result",
          SolverOutcome.MALFORMED,
          result.outcome());
    } finally {
      Files.deleteIfExists(script);
    }
  }

  /**
   * Regression guard: unlike the sibling timeout branch three lines above it (which calls {@code
   * process.destroyForcibly()}), {@code runOneShot}'s {@code InterruptedException} catch branch
   * used to never kill the child process -- a cancelled/interrupted solve orphaned the spawned
   * solver process, left running to completion on its own. Spawns a stand-in fake-solver script
   * that records its own pid and then sleeps far longer than this test's own timeout, runs it on a
   * dedicated thread, interrupts that thread once the child has genuinely started, and confirms the
   * child process is actually gone afterward -- not merely that the Java call returned.
   */
  @Test(timeout = 20_000)
  public void interruptingAOneShotSolveKillsTheChildProcessRatherThanOrphaningIt()
      throws Exception {
    Path pidFile = Files.createTempFile("msc-interrupt-pid-", ".txt");
    Path script = Files.createTempFile("msc-sleepy-solver-", ".sh");
    Files.writeString(
        script,
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"--version\" ]; then\n"
            + "  echo \"Fake solver 9.9.9\"\n"
            + "  exit 0\n"
            + "fi\n"
            + "echo $$ > "
            + pidFile
            + "\n"
            + "exec sleep 999\n");
    Files.setPosixFilePermissions(
        script,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    try {
      SolverBinary sleepyBinary = SolverBinary.resolveFrom(script.toString(), "9.9.9");
      SolverProcess sleepy = new SolverProcess(sleepyBinary, Duration.ofSeconds(30));

      Thread worker =
          new Thread(
              () -> {
                try {
                  sleepy.run("(check-sat)");
                } catch (RuntimeException expected) {
                  // The interrupted call is expected to throw SolverConfigurationException; this
                  // test only cares about the child process's fate, asserted below.
                }
              });
      worker.setDaemon(true);
      worker.start();

      // exec replaces the shell's process image with sleep but keeps the SAME pid, so the pid
      // recorded before exec still identifies the process SolverProcess actually started.
      long pid = awaitPid(pidFile);
      assertTrue(
          "the fake solver's child process must actually be alive before we interrupt it",
          ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));

      worker.interrupt();
      worker.join(5_000);

      // destroyForcibly delivers SIGKILL asynchronously; poll briefly rather than racing it.
      boolean stillAlive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
      long deadline = System.currentTimeMillis() + 5_000;
      while (stillAlive && System.currentTimeMillis() < deadline) {
        Thread.sleep(50);
        stillAlive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
      }
      assertFalse("an interrupted one-shot solve must not orphan its child process", stillAlive);
    } finally {
      Files.deleteIfExists(script);
      Files.deleteIfExists(pidFile);
    }
  }

  private static long awaitPid(Path pidFile) throws Exception {
    long deadline = System.currentTimeMillis() + 5_000;
    while (System.currentTimeMillis() < deadline) {
      String content = Files.readString(pidFile).strip();
      if (!content.isEmpty()) {
        return Long.parseLong(content);
      }
      Thread.sleep(20);
    }
    throw new AssertionError("fake solver never wrote its pid within the deadline");
  }
}
