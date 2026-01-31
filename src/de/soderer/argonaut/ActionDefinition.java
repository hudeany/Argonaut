package de.soderer.argonaut;

public class ActionDefinition {
	private boolean verbose = false;

	private String executeWorkflow = null;

	public boolean isVerbose() {
		return verbose;
	}

	public ActionDefinition setVerbose(final boolean verbose) {
		this.verbose = verbose;
		return this;
	}

	public ActionDefinition checkParameters() throws ArgonautException {
		return this;
	}

	public String getExecuteWorkflow() {
		return executeWorkflow;
	}

	public void setExecuteWorkflow(final String executeWorkflow) {
		this.executeWorkflow = executeWorkflow;
	}
}
