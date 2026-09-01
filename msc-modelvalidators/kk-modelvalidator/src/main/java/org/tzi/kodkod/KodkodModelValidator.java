package org.tzi.kodkod;

import kodkod.engine.Evaluator;
import kodkod.engine.Solution;
import kodkod.engine.Statistics;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IModel;

/**
 * Abstract base class for all validation functionalities.
 * 
 * @author Hendrik Reitmann
 */
public abstract class KodkodModelValidator {

	private static final Logger LOG = Logger.getLogger(KodkodModelValidator.class);

	protected IModel model;
	protected Solution solution;
	protected Evaluator evaluator;
	private Throwable validationError;

	/**
	 * Validates the given model.
	 *
	 * @param model
	 */
	public void validate(IModel model) {
		this.model = model;
		evaluator = null;
		validationError = null;

		KodkodSolver kodkodSolver = new KodkodSolver();
		try {
			solution = kodkodSolver.solve(model);
		} catch (Exception e) {
			LOG.error(LogMessages.validationException + " (" + e.getMessage() + ")");
			validationError = e;
			return;
		} catch (OutOfMemoryError oome) {
			LOG.error(LogMessages.validationOutOfMemory + " (" + oome.getMessage() + ")");
			validationError = oome;
			return;
		}

		LOG.info(solution.outcome());

		Statistics statistics = solution.stats();
		LOG.info(LogMessages.kodkodStatistics(statistics));

		switch (solution.outcome()) {
		case SATISFIABLE:
			storeEvaluator(kodkodSolver);
			satisfiable();
			break;
		case TRIVIALLY_SATISFIABLE:
			storeEvaluator(kodkodSolver);
			trivially_satisfiable();
			break;
		case TRIVIALLY_UNSATISFIABLE:
			trivially_unsatisfiable();
			break;
		case UNSATISFIABLE:
			unsatisfiable();
			break;
		default:
			throw new IllegalStateException("Kodkod returned unknown solution outcome.");
		}

		if(KodkodQueryCache.INSTANCE.isQueryEnabled()){
			KodkodQueryCache.INSTANCE.setEvaluator(evaluator);
		}
	}

	private void storeEvaluator(KodkodSolver kodkodSolver) {
		evaluator = kodkodSolver.evaluator();
	}

	/**
	 * The outcome of the most recent {@link #validate(IModel)} call, or {@code null} if validate()
	 * has not run yet or raised an exception before Kodkod returned a solution. Added so callers can
	 * report the outcome through a channel independent of this class's own log4j calls, which route
	 * through a per-classloader-fragile static Hierarchy in this reactor's multi-plugin setup (see
	 * docs/kk-modelvalidator-port.md, "Known non-blocking issue: log4j output is unreliable").
	 */
	public Solution solution() {
		return solution;
	}

	/**
	 * The {@link Exception} or {@link OutOfMemoryError} that {@link KodkodSolver#solve} raised during
	 * the most recent {@link #validate(IModel)} call, or {@code null} if that call did not throw (this
	 * includes: validate() has not run yet, the most recent run solved without incident, or an earlier
	 * run's error was superseded by a later, successful run on the same instance -- reset at the top of
	 * every {@link #validate(IModel)} call exactly like {@link #solution}). Added for the same reason as
	 * {@link #solution()}: {@code validate} deliberately still swallows the exception and returns
	 * normally rather than propagating it (existing callers -- the GUI command, {@link
	 * InvariantIndepChecker}, recursive re-solves via {@code newSolution} -- all rely on that), but a
	 * caller that DOES want to know what went wrong (e.g. to populate a benchmark result's own error
	 * field) previously had no way to recover it: the message only ever reached this class's own log4j
	 * {@code LOG.error} call, which is unreliable in this reactor's multi-plugin setup (see {@link
	 * #solution()}'s own javadoc) and, even when it does print, is not attributable to a specific
	 * validate() call by any caller holding just this object.
	 */
	public Throwable validationError() {
		return validationError;
	}

	protected abstract void satisfiable();

	protected abstract void trivially_satisfiable();

	protected abstract void trivially_unsatisfiable();

	protected abstract void unsatisfiable();
}
