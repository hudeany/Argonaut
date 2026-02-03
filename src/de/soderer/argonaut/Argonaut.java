package de.soderer.argonaut;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.widgets.Display;

import de.soderer.argonaut.dlg.ArgonautDialog;
import de.soderer.pac.PacScriptParser;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.ParameterException;
import de.soderer.utilities.UpdateableConsoleApplication;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.Version;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.console.ConsoleType;
import de.soderer.utilities.console.ConsoleUtilities;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.ErrorDialog;
import de.soderer.utilities.worker.WorkerParentDual;

public class Argonaut extends UpdateableConsoleApplication implements WorkerParentDual {
	/** The Constant APPLICATION_NAME. */
	public static final String APPLICATION_NAME = "Argonaut";
	public static final String APPLICATION_STARTUPCLASS_NAME = "de-soderer-Argonaut";
	public static final String APPLICATION_ERROR_EMAIL_ADRESS = "Argonaut.Error@soderer.de";

	public static final File KEYSTORE_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + File.separator + "." + APPLICATION_NAME + ".keystore");
	public static final String HOME_URL = "https://soderer.de/index.php?menu=tools";

	/** The Constant VERSION_RESOURCE_FILE, which contains version number and versioninfo download url. */
	public static final String VERSION_RESOURCE_FILE = "/application_version.txt";

	/** The version is filled in at application start from the application_version.txt file. */
	public static Version VERSION = null;

	/** The version build time is filled in at application start from the application_version.txt file */
	public static LocalDateTime VERSION_BUILDTIME = null;

	/** The versioninfo download url is filled in at application start from the application_version.txt file. */
	public static String VERSIONINFO_DOWNLOAD_URL = null;

	/** Trusted CA certificate for updates **/
	public static String TRUSTED_UPDATE_CA_CERTIFICATES = null;

	public static final String HELP_RESOURCE_FILE_DEFAULT = "/help.txt";
	public static final String HELP_RESOURCE_FILE_DE = "/help.txt";

	/** The Constant CONFIGURATION_FILE. */
	public static final File CONFIGURATION_FILE = new File(System.getProperty("user.home") + File.separator + "." + APPLICATION_NAME + ".config");

	public static final String CONFIG_VERSION = "Application.Version";
	public static final String CONFIG_LANGUAGE = "Application.Language";
	public static final String CONFIG_DAILY_UPDATE_CHECK = "DailyUpdateCheck";
	public static final String CONFIG_NEXT_DAILY_UPDATE_CHECK = "NextDailyUpdateCheck";
	public static final String CONFIG_PROXY_CONFIGURATION_TYPE = ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE;
	public static final String CONFIG_PROXY_URL = ApplicationConfigurationDialog.CONFIG_PROXY_URL;
	public static final String CONFIG_TLS_SERVER_CERTIFICATE_CHECK = "TlsServerCertificateCheck";

	private ActionDefinition actionDefinitionToExecute;

	public static void setupDefaultConfig(final ConfigurationProperties applicationConfiguration) {
		applicationConfiguration.set(ApplicationConfigurationDialog.CONFIG_LANGUAGE + ConfigurationProperties.ENUM_EXTENSION, "de,en");
		if (!applicationConfiguration.containsKey(Argonaut.CONFIG_LANGUAGE)) {
			applicationConfiguration.set(Argonaut.CONFIG_LANGUAGE, Locale.getDefault().getLanguage());
		}

		applicationConfiguration.set(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE + ConfigurationProperties.ENUM_EXTENSION, "None,System,Proxy-URL,WPAD,PAC-URL");
		if (!applicationConfiguration.containsKey(Argonaut.CONFIG_DAILY_UPDATE_CHECK)) {
			applicationConfiguration.set(Argonaut.CONFIG_DAILY_UPDATE_CHECK, false);
		}
		if (!applicationConfiguration.containsKey(Argonaut.CONFIG_NEXT_DAILY_UPDATE_CHECK)) {
			applicationConfiguration.set(Argonaut.CONFIG_NEXT_DAILY_UPDATE_CHECK, "");
		}
		if (!applicationConfiguration.containsKey(Argonaut.CONFIG_PROXY_CONFIGURATION_TYPE)) {
			applicationConfiguration.set(Argonaut.CONFIG_PROXY_CONFIGURATION_TYPE, ProxyConfiguration.ProxyConfigurationType.None.name());
		}
		if (!applicationConfiguration.containsKey(Argonaut.CONFIG_PROXY_URL)) {
			applicationConfiguration.set(Argonaut.CONFIG_PROXY_URL, "");
		}
		if (!applicationConfiguration.containsKey(Argonaut.CONFIG_TLS_SERVER_CERTIFICATE_CHECK)) {
			applicationConfiguration.set(Argonaut.CONFIG_TLS_SERVER_CERTIFICATE_CHECK, "true");
		}
	}

	/** The usage message. */
	private static String getUsageMessage() {
		try (InputStream helpInputStream = Argonaut.class.getResourceAsStream(Argonaut.HELP_RESOURCE_FILE_DEFAULT)) {
			return "Argonaut (by Andreas Soderer, mail: Argonaut@soderer.de)\n"
					+ "VERSION: " + VERSION.toString() + " (" + DateUtilities.formatDate(DateUtilities.YYYY_MM_DD_HHMMSS, VERSION_BUILDTIME) + ")" + "\n\n"
					+ new String(IoUtilities.toByteArray(helpInputStream), StandardCharsets.UTF_8);
		} catch (@SuppressWarnings("unused") final Exception e) {
			return "Help info is missing";
		}
	}

	/**
	 * The main method.
	 *
	 * @param arguments the arguments
	 */
	public static void main(final String[] arguments) {
		final int returnCode = _main(arguments);
		if (returnCode >= 0) {
			System.exit(returnCode);
		}
	}

	/**
	 * Method used for main but with no System.exit call to make it junit testable
	 *
	 * @param arguments
	 * @return
	 */
	protected static int _main(final String[] args) {
		ApplicationUpdateUtilities.removeUpdateLeftovers();

		try (InputStream resourceStream = Argonaut.class.getResourceAsStream(VERSION_RESOURCE_FILE)) {
			// Try to fill the version and versioninfo download url
			final List<String> versionInfoLines = Utilities.readLines(resourceStream, StandardCharsets.UTF_8);
			VERSION = new Version(versionInfoLines.get(0));
			if (versionInfoLines.size() >= 2) {
				VERSION_BUILDTIME = DateUtilities.parseLocalDateTime(DateUtilities.YYYY_MM_DD_HHMMSS, versionInfoLines.get(1));
			}
			if (versionInfoLines.size() >= 3) {
				VERSIONINFO_DOWNLOAD_URL = versionInfoLines.get(2);
			}
			if (versionInfoLines.size() >= 4) {
				TRUSTED_UPDATE_CA_CERTIFICATES = versionInfoLines.get(3);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			// Without the application_version.txt file we may not go on
			System.err.println("Invalid application_version.txt");
			return 1;
		}

		ConfigurationProperties applicationConfiguration;
		try {
			applicationConfiguration = new ConfigurationProperties(Argonaut.APPLICATION_NAME, true);
			Argonaut.setupDefaultConfig(applicationConfiguration);
			if ("de".equalsIgnoreCase(applicationConfiguration.get(Argonaut.CONFIG_LANGUAGE))) {
				Locale.setDefault(Locale.GERMAN);
			} else {
				Locale.setDefault(Locale.ENGLISH);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			System.err.println("Invalid application configuration");
			return 1;
		}

		if (!applicationConfiguration.containsKey(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE)) {
			if (PacScriptParser.findPacFileUrlByWpad() != null) {
				applicationConfiguration.set(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE, ProxyConfigurationType.WPAD.name());
			} else {
				applicationConfiguration.set(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE, ProxyConfigurationType.None.name());
			}
			applicationConfiguration.save();
		}

		final ProxyConfigurationType proxyConfigurationType = ProxyConfigurationType.getFromString(applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE));
		final String proxyUrl = applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_URL);
		final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(proxyConfigurationType, proxyUrl);

		try {
			String[] arguments = args;

			boolean openGui = false;

			if (arguments.length == 0) {
				// If started without any parameter we check for headless mode and show the GUI or help
				if (GraphicsEnvironment.isHeadless()) {
					System.out.println(getUsageMessage());
				} else {
					openGui = true;
				}
			} else {
				for (int i = 0; i < arguments.length; i++) {
					if ("help".equalsIgnoreCase(arguments[i]) || "-help".equalsIgnoreCase(arguments[i]) || "--help".equalsIgnoreCase(arguments[i]) || "-h".equalsIgnoreCase(arguments[i]) || "--h".equalsIgnoreCase(arguments[i])
							|| "-?".equalsIgnoreCase(arguments[i]) || "--?".equalsIgnoreCase(arguments[i])) {
						System.out.println(getUsageMessage());
						return 1;
					} else if ("version".equalsIgnoreCase(arguments[i]) && arguments.length == 1) {
						System.out.println(VERSION.toString());
						return 1;
					} else if ("update".equalsIgnoreCase(arguments[i]) && arguments.length == 1) {
						final Argonaut argonaut = new Argonaut();
						if (arguments.length > i + 2) {
							ApplicationUpdateUtilities.executeUpdate(argonaut, Argonaut.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, Argonaut.APPLICATION_NAME, Argonaut.VERSION, Argonaut.TRUSTED_UPDATE_CA_CERTIFICATES, arguments[i + 1], arguments[i + 2].toCharArray(), null, false);
						} else if (arguments.length > i + 1) {
							ApplicationUpdateUtilities.executeUpdate(argonaut, Argonaut.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, Argonaut.APPLICATION_NAME, Argonaut.VERSION, Argonaut.TRUSTED_UPDATE_CA_CERTIFICATES, arguments[i + 1], null, null, false);
						} else {
							ApplicationUpdateUtilities.executeUpdate(argonaut, Argonaut.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, Argonaut.APPLICATION_NAME, Argonaut.VERSION, Argonaut.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, false);
						}
						return 1;
					} else if ("gui".equalsIgnoreCase(arguments[i])) {
						if (GraphicsEnvironment.isHeadless()) {
							throw new Exception("GUI can only be shown on a non-headless environment");
						}
						openGui = true;
						arguments = Utilities.removeItemAtIndex(arguments, i--);
					}
				}
			}

			final ActionDefinition actionDefinition = new ActionDefinition();

			// Read the parameters
			for (int i = 0; i < arguments.length; i++) {
				boolean wasAllowedParam = false;

				if ("-executeWorkflow".equalsIgnoreCase(arguments[i])) {
					i++;
					if (i >= arguments.length) {
						throw new ParameterException(arguments[i - 1], "Missing parameter for importFromExcel");
					} else if (Utilities.isBlank(arguments[i])) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Invalid parameter for importFromExcel");
					} else if (actionDefinition.getExecuteWorkflow() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter importFromExcel");
					} else if (actionDefinition.getExecuteWorkflow() != null) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Only one of parameters importToExcel and exportFromExcel is allowed");
					} else {
						actionDefinition.setExecuteWorkflow(arguments[i]);
					}
					wasAllowedParam = true;
				} else if ("-v".equalsIgnoreCase(arguments[i])) {
					if (actionDefinition.isVerbose()) {
						throw new ParameterException(arguments[i - 1] + " " + arguments[i], "Duplicate parameter 'v'");
					} else {
						actionDefinition.setVerbose(true);
					}
					wasAllowedParam = true;
				}

				if (!wasAllowedParam) {
					throw new ParameterException(arguments[i], "Invalid parameter");
				}
			}

			if (openGui) {
				Display display = null;
				try {
					display = new Display();
					final ArgonautDialog mainDialog = new ArgonautDialog(display, applicationConfiguration);
					mainDialog.run();
					return -1;
				} catch (final Exception ex) {
					if (display != null) {
						new ErrorDialog(display.getActiveShell(), Argonaut.APPLICATION_NAME, Argonaut.VERSION.toString(), Argonaut.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
					} else {
						System.out.println(ex.toString());
						ex.printStackTrace();
					}
					return 1;
				} finally {
					if (display != null) {
						display.dispose();
					}
				}
			} else {
				LangResources.enforceDefaultLocale();

				// Validate all given parameters
				actionDefinition.checkParameters();

				// Start the worker for terminal output
				try {
					new Argonaut().execute(actionDefinition, applicationConfiguration);
					return 0;
				} catch (final ArgonautException e) {
					System.err.println(e.getMessage());
					return 1;
				} catch (final Exception e) {
					e.printStackTrace();
					return 1;
				}
			}
		} catch (final ParameterException e) {
			System.err.println(e.getMessage());
			System.err.println();
			System.err.println(getUsageMessage());
			return 1;
		} catch (final Exception e) {
			System.err.println(e.getMessage());
			return 1;
		}
	}

	public Argonaut() throws Exception {
		super(APPLICATION_NAME, VERSION);
	}

	private void execute(final ActionDefinition actionDefinition, @SuppressWarnings("unused") final ConfigurationProperties applicationConfiguration) throws Exception {
		try {
			actionDefinitionToExecute = actionDefinition;

			if (actionDefinition.getExecuteWorkflow() != null) {
				System.out.println();
			}
		} catch (final Exception e) {
			throw e;
		}
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#showUnlimitedProgress()
	 */
	@Override
	public void receiveUnlimitedProgressSignal() {
		// Do nothing
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#showProgress(java.util.Date, long, long)
	 */
	@Override
	public void receiveProgressSignal(final LocalDateTime start, final long itemsToDo, final long itemsDone, final String itemsUnitSign) {
		if (actionDefinitionToExecute.isVerbose()) {
			printProgressBar(start, itemsToDo, itemsDone, itemsUnitSign);
		}
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#showDone(java.util.Date, java.util.Date, long)
	 */
	@Override
	public void receiveDoneSignal(final LocalDateTime start, final LocalDateTime end, final long itemsDone, final String itemsUnitSign, final String resultText) {
		if (actionDefinitionToExecute.isVerbose()) {
			@SuppressWarnings("unused")
			int currentTerminalWidth;
			try {
				currentTerminalWidth = ConsoleUtilities.getTerminalSize().getWidth();
			} catch (@SuppressWarnings("unused") final Exception e) {
				currentTerminalWidth = 80;
			}

			if (Utilities.isNotBlank(resultText)) {
				System.out.println("Result: \n" + resultText);
			}

			System.out.println();
			System.out.println();
		}
	}

	/* (non-Javadoc)
	 * @see de.soderer.utilities.WorkerParentSimple#cancel()
	 */
	@Override
	public boolean cancel() {
		System.out.println("Canceled");
		return true;
	}

	@Override
	public void changeTitle(final String text) {
		if (actionDefinitionToExecute.isVerbose()) {
			System.out.println(text);
		}
	}

	@Override
	public void receiveUnlimitedSubProgressSignal() {
		// Do nothing
	}

	@Override
	public void receiveItemStartSignal(final String itemName, final String description) {
		if (actionDefinitionToExecute.isVerbose()) {
			System.out.println(description);
		}
	}

	@Override
	public void receiveItemProgressSignal(final LocalDateTime itemStart, final long subItemToDo, final long subItemDone, final String itemsUnitSign) {
		if (actionDefinitionToExecute.isVerbose() && subItemToDo > 0) {
			printProgressBar(itemStart, subItemToDo, subItemDone, itemsUnitSign);
		}
	}

	private static void printProgressBar(final LocalDateTime itemStart, final long subItemToDo, final long subItemDone, final String itemsUnitSign) {
		try {
			if (ConsoleUtilities.getConsoleType() == ConsoleType.ANSI) {
				int currentTerminalWidth;
				try {
					currentTerminalWidth = ConsoleUtilities.getTerminalSize().getWidth();
				} catch (@SuppressWarnings("unused") final Exception e) {
					currentTerminalWidth = 80;
				}

				ConsoleUtilities.saveCurrentCursorPosition();

				ConsoleUtilities.moveCursorToSavedPosition();

				System.out.print(ConsoleUtilities.getConsoleProgressString(currentTerminalWidth - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign));

				ConsoleUtilities.moveCursorToSavedPosition();
			} else if (ConsoleUtilities.getConsoleType() == ConsoleType.TEST) {
				System.out.print(ConsoleUtilities.getConsoleProgressString(80 - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign) + "\n");
			} else {
				System.out.print("\r" + ConsoleUtilities.getConsoleProgressString(80 - 1, itemStart, subItemToDo, subItemDone, itemsUnitSign) + "\r");
			}
		} catch (final Throwable e) {
			// Do nothing => no progress bar
			e.printStackTrace();
		}
	}

	@Override
	public void receiveItemDoneSignal(final LocalDateTime itemStart, final LocalDateTime itemEnd, final long subItemsDone, final String itemsUnitSign, final String resultText) {
		if (actionDefinitionToExecute.isVerbose()) {
			if (subItemsDone > 0) {
				printProgressBar(itemStart, subItemsDone, subItemsDone, itemsUnitSign);
			}
			System.out.println();
			if (itemsUnitSign != null) {
				System.out.println("End (" + Utilities.getHumanReadableNumber(subItemsDone, itemsUnitSign, true, 5, true, Locale.ENGLISH) + " done in " + DateUtilities.getHumanReadableTimespanEnglish(Duration.between(itemStart, itemEnd), true) + ")");
			} else {
				System.out.println("End (" + NumberFormat.getNumberInstance(Locale.ENGLISH).format(subItemsDone) + " data items done in " + DateUtilities.getHumanReadableTimespanEnglish(Duration.between(itemStart, itemEnd), true) + ")");
			}

			if (Utilities.isNotBlank(resultText)) {
				System.out.println("Result: \n" + resultText);
			}

			System.out.println();
		}
	}
}
