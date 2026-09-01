package org.tzi.msc.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

/**
 * Regression test for BUG A: {@code runOne}'s hang-watchdog cleanup ({@code
 * watchdog.interrupt()}) used to run INSIDE the timed {@code [t0,t1]} window -- in a {@code
 * finally} block that executed BEFORE {@code t1} was captured -- while {@code runOneSmt} (the SMT
 * path) has no watchdog at all. That is a small but systematic (measured ~17-70us per call)
 * timing asymmetry favoring whichever side lacks the watchdog overhead, present by default since
 * {@code watchdogEnabled} defaults to {@code true} and {@code run-benchmark.sh} defaults
 * {@code safety=on}.
 *
 * <p>The real overhead is far too small (tens of microseconds) to assert on reliably by timing an
 * actual solve, so this exercises {@link BenchmarkRunner#timeThenCleanup} -- the exact method
 * {@code runOne} now uses for its {@code [t0,t1]} window -- directly and deterministically: a fake
 * "cleanup" that sleeps a large, easily-measured amount must NOT be able to inflate the reported
 * {@code t1 - t0} window at all, and must always run strictly after {@code t1} was captured.
 */
public class BenchmarkRunnerTimingTest {

	private static final long CLEANUP_SLEEP_MS = 200;

	@Test
	public void cleanupRunsAfterT1AndDoesNotInflateTheTimedWindow() throws InterruptedException {
		AtomicBoolean cleanupCalled = new AtomicBoolean(false);
		AtomicLong cleanupCalledAtNanos = new AtomicLong(-1);

		long beforeCall = System.nanoTime();
		long[] window = BenchmarkRunner.timeThenCleanup(() -> {
			// The "work" itself is intentionally near-instant -- the whole point is that the SLOW part
			// (cleanup) must not leak into [t0,t1].
		}, () -> {
			try {
				Thread.sleep(CLEANUP_SLEEP_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			cleanupCalledAtNanos.set(System.nanoTime());
			cleanupCalled.set(true);
		});
		long afterCall = System.nanoTime();

		long t0 = window[0];
		long t1 = window[1];
		double timedWindowMs = (t1 - t0) / 1_000_000.0;
		double totalCallMs = (afterCall - beforeCall) / 1_000_000.0;

		assertTrue("cleanup must have run", cleanupCalled.get());
		// THE regression this test guards: before the fix, cleanup ran inside [t0,t1], so a
		// CLEANUP_SLEEP_MS-long cleanup would have inflated timedWindowMs by roughly that much. After
		// the fix, t1 is captured before cleanup ever starts, so the timed window stays near-zero
		// while the whole call (which does include the sleep, via the finally) does not.
		assertTrue("timed window must stay near-instant (work itself does nothing): was " + timedWindowMs
				+ "ms -- BUG A would show this inflated by ~" + CLEANUP_SLEEP_MS + "ms", timedWindowMs < 50);
		assertTrue("the whole call (work + cleanup) must actually take the cleanup's sleep time, proving"
				+ " cleanup really ran and really was slow, not skipped: was " + totalCallMs + "ms",
				totalCallMs >= CLEANUP_SLEEP_MS);
		// The direct ordering assertion: cleanup's own timestamp must be AFTER t1, never at-or-before.
		assertTrue("cleanup must be timestamped strictly after t1 -- BUG A had it running before t1 was"
				+ " ever captured", cleanupCalledAtNanos.get() > t1);
	}

	@Test
	public void cleanupStillRunsWhenWorkThrows() {
		AtomicBoolean cleanupCalled = new AtomicBoolean(false);
		RuntimeException thrown = new RuntimeException("simulated validate() failure");

		RuntimeException caught = null;
		try {
			BenchmarkRunner.timeThenCleanup(() -> {
				throw thrown;
			}, () -> cleanupCalled.set(true));
		} catch (RuntimeException e) {
			caught = e;
		}

		assertEquals("the original exception must propagate out unchanged", thrown, caught);
		assertTrue("cleanup must still run even when work throws (mirrors the watchdog needing to be"
				+ " interrupted even on a failing validate() call)", cleanupCalled.get());
	}

	@Test
	public void nullCleanupIsAllowed() {
		AtomicBoolean workRan = new AtomicBoolean(false);
		long[] window = BenchmarkRunner.timeThenCleanup(() -> workRan.set(true), null);

		assertTrue(workRan.get());
		assertFalse("t1 must be >= t0", window[1] < window[0]);
	}
}
