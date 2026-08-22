package org.tzi.msc.benchmark;

import java.util.List;

/**
 * Outcome of actually executing one standalone SOIL validation fixture (a hand-built instance,
 * opened via {@code open *.soil} and checked via {@code check -v} -- no solver involved) through a
 * real {@code use-gui.jar -nogui <model> <cmd>} subprocess, the same invocation documented and
 * manually confirmed in each fixture's own header comment (see also
 * {@code benchmark/examples/run-example.sh}).
 */
public class SoilValidationResult {
	public String exampleId;
	public String cmdFile; // "valid-instance.cmd" | "invalid-instance.cmd"
	public String kind; // "valid" | "invalid"
	public boolean passed; // true if the fixture behaved as its own name promises
	public Integer numInvariantsChecked; // null if the summary line couldn't be parsed
	public Integer numFailures; // null if the summary line couldn't be parsed
	public List<String> failedInvariants; // qualified names ("Class::invariant"), possibly empty
	public int exitCode;
	public String note; // human-readable explanation, especially on failure/timeout/unparseable output
}
