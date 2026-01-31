package de.soderer.argonaut;

public class ArgonautException extends Exception {
	private static final long serialVersionUID = -7240533232921526907L;

	public ArgonautException(final String errorMessage) {
		super(errorMessage);
	}

	public ArgonautException(final String errorMessage, final Exception e) {
		super(errorMessage, e);
	}
}
