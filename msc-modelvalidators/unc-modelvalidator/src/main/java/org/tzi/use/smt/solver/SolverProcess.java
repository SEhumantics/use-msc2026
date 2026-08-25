package org.tzi.use.smt.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Runs SMT-LIB text through the pinned solver as a subprocess. */
public final class SolverProcess {

    private final SolverBinary binary;
    private final Duration timeout;

    public SolverProcess(SolverBinary binary, Duration timeout) {
        this.binary = binary;
        this.timeout = timeout;
    }

    public SolverResult run(String smtLib) {
        Path scriptFile = null;
        Path outputFile = null;
        long started = System.nanoTime();
        try {
            scriptFile = Files.createTempFile("msc-smt-", ".smt2");
            Files.writeString(scriptFile, smtLib, StandardCharsets.UTF_8);

            outputFile = Files.createTempFile("msc-smt-out-", ".txt");
            Process process = new ProcessBuilder(binary.path().toString(), "-smt2", scriptFile.toString())
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
            return classify(output, millis);
        } catch (IOException e) {
            throw new SolverConfigurationException("Failed to run " + binary.path(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolverConfigurationException("Interrupted running " + binary.path(), e);
        } finally {
            deleteQuietly(scriptFile);
            deleteQuietly(outputFile);
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
            case "sat" -> new SolverResult(SolverOutcome.SAT, output, stripTrailingError(remainder), millis);
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
