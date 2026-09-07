package org.tzi.use.smt.solver;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs SMT-LIB text through the pinned solver. Two modes, chosen at construction:
 *
 * <p><b>One-shot</b> (the public constructor, unchanged since Phase 1): a fresh OS process per
 * {@link #run}, exactly as before -- correct in isolation, but a real fixed cost per call (process
 * spawn plus two temp files) that dominates every solve on small scenarios, confirmed by direct
 * measurement (see the persistent-mode task's own design rationale in the master plan).
 *
 * <p><b>Persistent</b> ({@link #persistent}): one {@code z3 -in} process kept alive and reused
 * across every {@link #run} call on this instance, for a caller doing many solves in one run (see
 * {@code BenchmarkRunner}). Every call sends {@code (reset)} first, so reusing a slot name like
 * "Car_0" across two unrelated scripts is exactly as safe as two separate one-shot processes would
 * be -- verified directly against this repository's own pinned Z3 binary before this was written,
 * not assumed: {@code (reset)} genuinely clears all prior declarations and assertions, an {@code
 * (echo "&lt;sentinel&gt;")} sentinel reliably delimits one call's output from the next even across
 * a multi-line {@code (get-model)}, and Z3's own {@code (set-option :timeout ...)} genuinely aborts
 * an individual {@code (check-sat)} (returning {@code unknown}) without killing the process, so
 * later calls on the same instance still work. No Java-side per-call read timeout is layered on top
 * of that: this repository already rejected same-JVM thread-interrupt-based timeouts as unsafe for
 * exactly this reason (see {@code BenchmarkRunner}'s own class javadoc) and already relies on an
 * outer OS-level {@code timeout} wrapper (see {@code scripts/run-benchmark.sh}) as the backstop for
 * a solver that hangs despite its own configured budget -- persistent mode relies on that same
 * existing backstop rather than inventing a second, riskier one. The one caveat that follows: every
 * call sharing one persistent instance is bounded by that instance's own single, construction-time
 * {@code timeout} value (no per-call override) -- not a real limitation on this project's own
 * corpus today (no example configures a non-default {@code timeout}), recorded here rather than
 * silently assumed.
 *
 * <p><b>Precondition: {@code smtLib} must be well-formed (balanced parentheses).</b> Z3's own
 * {@code :timeout} option only bounds the SEARCH phase, not parsing -- reproduced directly, once,
 * deliberately: feeding {@code -in} mode a script with an unclosed paren hangs the process
 * indefinitely, since the parser is still waiting for more tokens to complete the expression and
 * never reaches {@code (check-sat)} at all. {@code SmtScript#toSmtLib()} always emits balanced text
 * by construction, so this is not reachable from any real caller in this codebase today; it is not
 * handled defensively here because doing so would mean the same Java-side blocking-read timeout
 * already rejected as unsafe elsewhere in this project (see above). A caller feeding this class
 * raw, untrusted SMT-LIB text would need to validate it is well-formed first.
 */
public final class SolverProcess implements AutoCloseable {

  /**
   * The POSIX/Java convention for a child process terminated BY A SIGNAL (a crash or an OS kill),
   * rather than a normal, deliberate {@code exit(N)} call: {@code 128 + signal number} -- confirmed
   * directly against this JVM (a deliberately SIGSEGV'd child reports {@code exitValue() == 139 ==
   * 128 + 11}). Z3's own deliberate exit codes never reach this range: {@code 0} on success, and
   * {@code 1} whenever ANY command in the script produced an {@code (error ...)} response --
   * confirmed directly against the pinned binary for SAT, UNSAT, UNKNOWN, and malformed-input
   * scripts alike. That {@code 1} case is not rare or exotic: every script this codebase generates
   * ends with an unconditional {@code (get-model)} (see {@code SmtScript#toSmtLib}), so it fires on
   * literally every UNSAT or UNKNOWN result (get-model fails with "model is not available") --
   * exactly the case {@link #classify} already handles correctly via {@link #stripTrailingError}.
   * This threshold exists to catch an ACTUAL crash without misclassifying that entirely ordinary,
   * already-tested exit-1 shape as one.
   */
  private static final int SIGNAL_TERMINATION_EXIT_THRESHOLD = 128;

  private final SolverBinary binary;
  private final Duration timeout;
  private final boolean persistentMode;
  private final AtomicLong sentinelCounter = new AtomicLong();
  private Process persistentProcess;
  private Writer persistentStdin;
  private BufferedReader persistentStdout;

  public SolverProcess(SolverBinary binary, Duration timeout) {
    this(binary, timeout, false);
  }

  private SolverProcess(SolverBinary binary, Duration timeout, boolean persistentMode) {
    this.binary = binary;
    this.timeout = timeout;
    this.persistentMode = persistentMode;
  }

  public static SolverProcess persistent(SolverBinary binary, Duration timeout) {
    return new SolverProcess(binary, timeout, true);
  }

  public SolverResult run(String smtLib) {
    // Instrumentation only -- see SolveInstrumentation. Wrapping the single dispatch point counts
    // both the one-shot and the persistent path without duplicating the accounting in each.
    long started = System.nanoTime();
    try {
      return persistentMode ? runPersistent(smtLib) : runOneShot(smtLib);
    } finally {
      SolveInstrumentation.recordSolverCall(smtLib.length(), System.nanoTime() - started);
    }
  }

  /** Releases the persistent process, if one was ever started. A no-op in one-shot mode. */
  @Override
  public void close() {
    if (persistentProcess == null) {
      return;
    }
    try {
      persistentStdin.write("(exit)\n");
      persistentStdin.flush();
      persistentProcess.waitFor(2, TimeUnit.SECONDS);
    } catch (IOException ignored) {
      // Best-effort: the process is about to be force-killed below regardless.
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      killPersistentProcess();
    }
  }

  private SolverResult runOneShot(String smtLib) {
    Path scriptFile = null;
    Path outputFile = null;
    Process process = null;
    long started = System.nanoTime();
    try {
      scriptFile = Files.createTempFile("msc-smt-", ".smt2");
      Files.writeString(scriptFile, smtLib, StandardCharsets.UTF_8);

      outputFile = Files.createTempFile("msc-smt-out-", ".txt");
      process =
          new ProcessBuilder(binary.path().toString(), "-smt2", scriptFile.toString())
              .redirectErrorStream(true)
              .redirectOutput(outputFile.toFile())
              .start();
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      long millis = (System.nanoTime() - started) / 1_000_000L;
      String output = Files.readString(outputFile, StandardCharsets.UTF_8);
      if (!finished) {
        process.destroyForcibly();
        return new SolverResult(SolverOutcome.TIMEOUT, output, "", millis);
      }
      // A signal-terminated exit (SIGSEGV, OOM-kill, ...) is a crash, not a completed answer --
      // even when it happened AFTER the process had already printed a verdict line (e.g. "sat")
      // but BEFORE finishing the rest of its output. Trusting that verdict text would misreport a
      // corrupt, truncated run as a confirmed result with a garbage/empty model. This mirrors
      // runPersistent's own EOF-before-sentinel handling below, which forces MALFORMED for exactly
      // the same reason: an incomplete response is not a real answer, regardless of what partial
      // text it contains. See SIGNAL_TERMINATION_EXIT_THRESHOLD's own javadoc for why this is
      // deliberately NOT "any nonzero exit" -- that would misclassify every ordinary UNSAT/UNKNOWN
      // result too.
      if (process.exitValue() >= SIGNAL_TERMINATION_EXIT_THRESHOLD) {
        return new SolverResult(SolverOutcome.MALFORMED, output, "", millis);
      }
      return classify(output, millis);
    } catch (IOException e) {
      throw new SolverConfigurationException("Failed to run " + binary.path(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // Matches the timeout branch above: a cancelled/interrupted solve must not orphan the
      // spawned solver process. Without this, an interrupted caller (e.g. a cancelled analysis)
      // leaves the child running to completion on its own, untracked by anything in this JVM.
      if (process != null) {
        process.destroyForcibly();
      }
      throw new SolverConfigurationException("Interrupted running " + binary.path(), e);
    } finally {
      deleteQuietly(scriptFile);
      deleteQuietly(outputFile);
    }
  }

  private SolverResult runPersistent(String smtLib) {
    long started = System.nanoTime();
    try {
      ensurePersistentProcessAlive();
      String sentinel = "msc-smt-done-" + sentinelCounter.incrementAndGet();
      persistentStdin.write("(reset)\n");
      persistentStdin.write("(set-option :timeout " + timeout.toMillis() + ")\n");
      persistentStdin.write(smtLib);
      persistentStdin.write('\n');
      persistentStdin.write("(echo \"" + sentinel + "\")\n");
      persistentStdin.flush();

      StringBuilder output = new StringBuilder();
      String line;
      while ((line = persistentStdout.readLine()) != null) {
        if (line.strip().equals(sentinel)) {
          long millis = (System.nanoTime() - started) / 1_000_000L;
          return classify(output.toString(), millis);
        }
        output.append(line).append('\n');
      }
      // EOF before the sentinel: the process died mid-response. The next call transparently
      // starts a fresh one (see ensurePersistentProcessAlive); this call reports what little
      // output there was.
      killPersistentProcess();
      long millis = (System.nanoTime() - started) / 1_000_000L;
      return new SolverResult(SolverOutcome.MALFORMED, output.toString(), "", millis);
    } catch (IOException e) {
      killPersistentProcess();
      throw new SolverConfigurationException(
          "Failed to run " + binary.path() + " (persistent mode)", e);
    }
  }

  private void ensurePersistentProcessAlive() throws IOException {
    if (persistentProcess != null && persistentProcess.isAlive()) {
      return;
    }
    // Defense-in-depth, not a confirmed leak fix: if the previous process died on its own (a
    // crash between calls, not via killPersistentProcess) its streams were never explicitly
    // closed before being overwritten below. 200 crash/respawn cycles showed zero FD growth
    // without this, so it is not chasing a demonstrated bug -- just cheap and safe to add.
    closeQuietly(persistentStdin);
    closeQuietly(persistentStdout);
    // stderr is routed straight to this JVM's own stderr (not merged into stdout, and not left as
    // an unread pipe either) -- merging would risk a stray warning line landing between a
    // response and its sentinel and corrupting the parse; leaving it as a separate, undrained pipe
    // risks a classic subprocess deadlock if Z3 ever writes enough to fill that pipe's buffer.
    persistentProcess =
        new ProcessBuilder(binary.path().toString(), "-in")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    persistentStdin =
        new OutputStreamWriter(persistentProcess.getOutputStream(), StandardCharsets.UTF_8);
    persistentStdout =
        new BufferedReader(
            new InputStreamReader(persistentProcess.getInputStream(), StandardCharsets.UTF_8));
  }

  private void killPersistentProcess() {
    if (persistentProcess == null) {
      return;
    }
    persistentProcess.destroyForcibly();
    closeQuietly(persistentStdin);
    closeQuietly(persistentStdout);
    persistentProcess = null;
    persistentStdin = null;
    persistentStdout = null;
  }

  /** Defense-in-depth stream cleanup (see {@link #ensurePersistentProcessAlive}'s own note). */
  private static void closeQuietly(Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException ignored) {
      // Best-effort: the process is already being force-killed regardless.
    }
  }

  private static SolverResult classify(String output, long millis) {
    String verdict = "";
    String remainder = "";
    for (String line : output.split("\n", -1)) {
      if (!line.isBlank()) {
        verdict = line.strip();
        int offset = output.indexOf(line) + line.length();
        remainder = output.substring(offset).strip();
        break;
      }
    }
    return switch (verdict) {
      case "sat" ->
          new SolverResult(SolverOutcome.SAT, output, stripTrailingError(remainder), millis);
      case "unsat" -> new SolverResult(SolverOutcome.UNSAT, output, "", millis);
      case "unknown" -> new SolverResult(SolverOutcome.UNKNOWN, output, "", millis);
      default -> new SolverResult(SolverOutcome.MALFORMED, output, "", millis);
    };
  }

  /** Drops a trailing solver error so it is never fed to the model parser. */
  private static String stripTrailingError(String modelText) {
    int errorAt = modelText.lastIndexOf("(error");
    return errorAt < 0 ? modelText : modelText.substring(0, errorAt).strip();
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // A leaked temp file is not worth failing a solve over.
    }
  }
}
