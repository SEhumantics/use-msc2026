package org.tzi.use.kodkod.plugin;

import java.io.IOException;

import org.tzi.use.main.shell.Shell;
import org.tzi.use.util.input.Readline;

/**
 * Adapts a running {@link Shell}'s own prompt as a {@link Readline} source, so a plugin command can
 * read further interactive input from the same session that invoked it. Recreated here because
 * upstream USE's {@code org.tzi.use.util.input.ShellReadline}, which the original ModelValidator
 * plugin depended on, no longer exists anywhere in the current USE codebase (it lived in use-core's
 * own package at USE 5.1.0 and was removed some time before this fork).
 */
final class ShellReadlineAdapter implements Readline {

	private final Shell shell;

	ShellReadlineAdapter(Shell shell) {
		this.shell = shell;
	}

	@Override
	public String readline(String prompt) throws IOException {
		return shell.readline(prompt);
	}

	@Override
	public void usingHistory() {
		// The shell manages its own history; nothing to do here.
	}

	@Override
	public void readHistory(String filename) throws IOException {
		// The shell manages its own history; nothing to do here.
	}

	@Override
	public void writeHistory(String filename) throws IOException {
		// The shell manages its own history; nothing to do here.
	}

	@Override
	public void close() throws IOException {
		// Nothing to release; the shell outlives this adapter.
	}

	@Override
	public boolean doEcho() {
		return true;
	}
}
