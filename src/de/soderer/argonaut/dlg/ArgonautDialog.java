package de.soderer.argonaut.dlg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import de.soderer.argonaut.Argonaut;
import de.soderer.argonaut.ServerConfiguration;
import de.soderer.argonaut.helper.ArgoWfSchedulerClient;
import de.soderer.argonaut.image.ImageManager;
import de.soderer.json.JsonArray;
import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.json.JsonWriter;
import de.soderer.network.NetworkUtilities;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;
import de.soderer.utilities.ConfigurationProperties;
import de.soderer.utilities.Credentials;
import de.soderer.utilities.IoUtilities;
import de.soderer.utilities.LangResources;
import de.soderer.utilities.Utilities;
import de.soderer.utilities.appupdate.ApplicationUpdateUtilities;
import de.soderer.utilities.swt.ApplicationConfigurationDialog;
import de.soderer.utilities.swt.CredentialsDialog;
import de.soderer.utilities.swt.ErrorDialog;
import de.soderer.utilities.swt.MultiInputDialog;
import de.soderer.utilities.swt.QuestionDialog;
import de.soderer.utilities.swt.ShowDataDialog;
import de.soderer.utilities.swt.SwtColor;
import de.soderer.utilities.swt.SwtUtilities;
import de.soderer.utilities.swt.UpdateableGuiApplication;

/**
 * Main Class
 */
public class ArgonautDialog extends UpdateableGuiApplication {
	private final ProxyConfiguration proxyConfiguration;
	private ArgoWfSchedulerClient argoWfSchedulerClient = null;

	private Composite serverSelectionBox;
	private Combo serverSelectioncombo;
	private Button removeServerButton;
	private Button editServerButton;

	private Composite rightPart = null;
	private Composite parametersPart;
	private ScrolledComposite scrolledPart;
	private Map<String, Text> parametersTextFields;
	private Table taskInstancesTable;
	private Listener currentFillDataListener;
	private Listener columnSortListener;

	private Composite workflowTemplateBox;
	private Combo workflowTemplateCombo;

	private Button startTaskButton;
	private Button showLogDataButton;
	private Button closeButton;

	private final ConfigurationProperties applicationConfiguration;
	private Map<String, ServerConfiguration> serverConfigurations = new LinkedHashMap<>();

	public ArgonautDialog(final Display display, final ConfigurationProperties applicationConfiguration) throws Exception {
		super(display, Argonaut.APPLICATION_NAME, Argonaut.VERSION, Argonaut.KEYSTORE_FILE);

		this.applicationConfiguration = applicationConfiguration;
		loadConfiguration();

		final Monitor[] monitorArray = display.getMonitors();
		if (monitorArray != null) {
			getShell().setLocation((monitorArray[0].getClientArea().width - getSize().x) / 2, (monitorArray[0].getClientArea().height - getSize().y) / 2);
		}

		final ProxyConfigurationType proxyConfigurationType = ProxyConfigurationType.getFromString(applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_CONFIGURATION_TYPE));
		final String proxyUrl = applicationConfiguration.get(ApplicationConfigurationDialog.CONFIG_PROXY_URL);
		proxyConfiguration = new ProxyConfiguration(proxyConfigurationType, proxyUrl);

		if (Utilities.isNotBlank(Argonaut.VERSIONINFO_DOWNLOAD_URL) && dailyUpdateCheckIsPending()) {
			setDailyUpdateCheckStatus(true);
			try {
				if (ApplicationUpdateUtilities.checkForNewVersionAvailable(Argonaut.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, Argonaut.APPLICATION_NAME, Argonaut.VERSION) != null) {
					ApplicationUpdateUtilities.executeUpdate(this, Argonaut.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, Argonaut.APPLICATION_NAME, Argonaut.VERSION, Argonaut.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true);
				}
			} catch (final Exception e) {
				showErrorMessage(LangResources.get("updateCheck"), LangResources.get("error.cannotCheckForUpdate", e.getMessage()));
			}
		}

		@SuppressWarnings("unused")
		final
		ImageManager imageManager = new ImageManager(getShell());
		final SashForm sashForm = new SashForm(this, SWT.SMOOTH | SWT.HORIZONTAL);
		setImage(ImageManager.getImage("Argonaut.png"));
		setText(LangResources.get("window_title"));
		setLayout(new FillLayout());
		createLeftPart(sashForm);
		createRightPart(sashForm);
		setSize(1000, 450);
		setMinimumSize(450, 300);

		addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(final Event event) {
				close();
			}
		});

		checkButtonStatus();
	}

	private void loadConfiguration() throws Exception {
		final File serversFile = new File(System.getProperty("user.home") + File.separator + "." + Argonaut.APPLICATION_NAME + File.separator + "Servers.json");
		if (serversFile.exists()) {
			try (JsonReader reader = new JsonReader(new FileInputStream(serversFile))) {
				final JsonArray serversArray = (JsonArray) reader.read();
				serverConfigurations = new LinkedHashMap<>();
				for (final JsonNode itemJsonNode : serversArray.items()) {
					final JsonObject itemJsonObject = (JsonObject) itemJsonNode;

					final ServerConfiguration serverConfiguration = new ServerConfiguration();
					serverConfiguration.setDisplayName((String) itemJsonObject.getSimpleValue("displayName"));
					serverConfiguration.setIdpUrl((String) itemJsonObject.getSimpleValue("idpUrl"));
					serverConfiguration.setRealmID((String) itemJsonObject.getSimpleValue("realmID"));
					serverConfiguration.setArgoWfSchedulerBaseUrl((String) itemJsonObject.getSimpleValue("argoWfSchedulerBaseUrl"));
					serverConfiguration.setClientID((String) itemJsonObject.getSimpleValue("clientID"));
					serverConfiguration.setClientSecret((String) itemJsonObject.getSimpleValue("clientSecret"));

					serverConfigurations.put(serverConfiguration.getDisplayName(), serverConfiguration);
				}
			}
		}

		checkButtonStatus();
	}

	private void saveConfiguration() throws Exception {
		final File serversFile = new File(System.getProperty("user.home") + File.separator + "." + Argonaut.APPLICATION_NAME + File.separator + "Servers.json");
		try (JsonWriter writer = new JsonWriter(new FileOutputStream(serversFile))) {
			final JsonArray serversArray = new JsonArray();
			for (final ServerConfiguration serversConfiguration : serverConfigurations.values()) {
				final JsonObject serverJsonObject = new JsonObject();
				serverJsonObject.add("displayName", serversConfiguration.getDisplayName());
				serverJsonObject.add("idpUrl", serversConfiguration.getIdpUrl());
				serverJsonObject.add("realmID", serversConfiguration.getRealmID());
				serverJsonObject.add("argoWfSchedulerBaseUrl", serversConfiguration.getArgoWfSchedulerBaseUrl());
				serverJsonObject.add("clientID", serversConfiguration.getClientID());
				if (serversConfiguration.getClientSecret() != null) {
					serverJsonObject.add("clientSecret", serversConfiguration.getClientSecret());
				}

				serversArray.add(serverJsonObject);
			}
			writer.add(serversArray);
		}
	}

	private void createLeftPart(final SashForm parent) throws Exception {
		final Composite leftPart = new Composite(parent, SWT.BORDER);
		leftPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));
		leftPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));

		final Label applicationLabel = new Label(leftPart, SWT.NONE);
		applicationLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		applicationLabel.setText(LangResources.get("title"));
		applicationLabel.setFont(new Font(getDisplay(), "Arial", 12, SWT.BOLD));

		final Composite buttonSection = new Composite(leftPart, SWT.NONE);
		buttonSection.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));
		buttonSection.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, true, false, 1, 1));

		final Button configButton = new Button(buttonSection, SWT.PUSH);
		configButton.setImage(ImageManager.getImage("wrench.png"));
		configButton.setToolTipText(LangResources.get("configuration"));
		configButton.addSelectionListener(new ConfigButtonSelectionListener());

		final Button helpButton = new Button(buttonSection, SWT.PUSH);
		helpButton.setImage(ImageManager.getImage("question.png"));
		helpButton.setToolTipText(LangResources.get("help"));
		helpButton.addSelectionListener(new HelpButtonSelectionListener(this));

		// Server selection
		serverSelectionBox = new Composite(leftPart, SWT.BORDER);
		serverSelectionBox.setLayout(SwtUtilities.createSmallMarginGridLayout(4, false));
		serverSelectionBox.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		final Label serverSelectionLabel = new Label(serverSelectionBox, SWT.NONE);
		serverSelectionLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 10, 1));
		serverSelectionLabel.setText("Server");
		serverSelectionLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		serverSelectioncombo = new Combo(serverSelectionBox, SWT.DROP_DOWN);
		serverSelectioncombo.setItems(serverConfigurations.keySet().toArray(new String[0]));
		serverSelectioncombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1));
		SwtUtilities.addAutoCompleteFeature(serverSelectioncombo);
		serverSelectioncombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				configureArgoWfSchedulerClient();

				loadWorflowTemplates();
				fillParametersPart(null, false);

				checkButtonStatus();
			}
		});

		final Button addServerButton = new Button(serverSelectionBox, SWT.PUSH);
		addServerButton.setImage(ImageManager.getImage("plus.png"));
		addServerButton.setToolTipText(LangResources.get("addServer"));
		addServerButton.setLayoutData(new GridData(25, 25));
		addServerButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				try {
					final String[] serverItems = new String[] {
							LangResources.get("displayName"),
							LangResources.get("idpUrl"),
							LangResources.get("realmID"),
							LangResources.get("argoWfSchedulerBaseUrl"),
							LangResources.get("clientID"),
							LangResources.get("clientSecret")};
					final MultiInputDialog dialog = new MultiInputDialog(getShell(), Argonaut.APPLICATION_NAME, LangResources.get("addServer"), serverItems);
					final List<String> serverValues = dialog.open();
					if (serverValues != null) {
						final ServerConfiguration serverConfiguration = new ServerConfiguration();
						serverConfiguration.setDisplayName(serverValues.get(0));
						serverConfiguration.setIdpUrl(serverValues.get(1));
						serverConfiguration.setRealmID(serverValues.get(2));
						serverConfiguration.setArgoWfSchedulerBaseUrl(serverValues.get(3));
						serverConfiguration.setClientID(serverValues.get(4));
						if (Utilities.isNotEmpty(serverValues.get(5))) {
							serverConfiguration.setClientSecret(serverValues.get(5));
						}
						if (serverConfigurations.containsKey(serverConfiguration.getDisplayName())) {
							throw new Exception("Server configuration with display name '" + serverConfiguration.getDisplayName() + "' already exists. Delete before readding");
						}
						serverConfigurations.put(serverConfiguration.getDisplayName(), serverConfiguration);
						saveConfiguration();
						serverSelectioncombo.setItems(serverConfigurations.keySet().toArray(new String[0]));
						serverSelectioncombo.setText(serverConfiguration.getDisplayName());

						checkButtonStatus();
					}
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("addServer"), "Cannot add server: " + e.getMessage());
				}
			}
		});

		removeServerButton = new Button(serverSelectionBox, SWT.PUSH);
		removeServerButton.setImage(ImageManager.getImage("trash.png"));
		removeServerButton.setToolTipText(LangResources.get("removeServer"));
		removeServerButton.setLayoutData(new GridData(25, 25));
		removeServerButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				try {
					if (Utilities.isNotBlank(serverSelectioncombo.getText())) {
						final QuestionDialog dialog = new QuestionDialog(getShell(), Argonaut.APPLICATION_NAME, LangResources.get("reallyRemoveServer", serverSelectioncombo.getText()), LangResources.get("yes"), LangResources.get("no"));
						final Integer result = dialog.open();
						if (result == 0) {
							serverConfigurations.remove(serverSelectioncombo.getText());
							saveConfiguration();
							serverSelectioncombo.setItems(serverConfigurations.keySet().toArray(new String[0]));
							serverSelectioncombo.setText("");
						}

						checkButtonStatus();
					}
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("addServer"), "Cannot add server: " + e.getMessage());
				}
			}
		});

		editServerButton = new Button(serverSelectionBox, SWT.PUSH);
		editServerButton.setImage(ImageManager.getImage("newProperty.png"));
		editServerButton.setToolTipText(LangResources.get("editServer"));
		editServerButton.setLayoutData(new GridData(25, 25));
		editServerButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				try {
					if (Utilities.isNotBlank(serverSelectioncombo.getText()) && serverConfigurations.containsKey(serverSelectioncombo.getText())) {
						final ServerConfiguration serverConfiguration = serverConfigurations.get(serverSelectioncombo.getText());
						final String[] serverItems = new String[] {
								LangResources.get("displayName"),
								LangResources.get("idpUrl"),
								LangResources.get("realmID"),
								LangResources.get("argoWfSchedulerBaseUrl"),
								LangResources.get("clientID"),
								LangResources.get("clientSecret")};
						final MultiInputDialog dialog = new MultiInputDialog(getShell(), Argonaut.APPLICATION_NAME, LangResources.get("addServer"), serverItems);
						dialog.setWidth(300);
						dialog.setDefaultTexts(new String[] {
								serverConfiguration.getDisplayName(),
								serverConfiguration.getIdpUrl(),
								serverConfiguration.getRealmID(),
								serverConfiguration.getArgoWfSchedulerBaseUrl(),
								serverConfiguration.getClientID(),
								serverConfiguration.getClientSecret() == null ? "" : serverConfiguration.getClientSecret()});
						final List<String> serverValues = dialog.open();
						if (serverValues != null) {
							serverConfiguration.setDisplayName(serverValues.get(0));
							serverConfiguration.setIdpUrl(serverValues.get(1));
							serverConfiguration.setRealmID(serverValues.get(2));
							serverConfiguration.setArgoWfSchedulerBaseUrl(serverValues.get(3));
							serverConfiguration.setClientID(serverValues.get(4));
							if (Utilities.isNotEmpty(serverValues.get(5))) {
								serverConfiguration.setClientSecret(serverValues.get(5));
							} else {
								serverConfiguration.setClientSecret(null);
							}
							saveConfiguration();
						}
					}
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("addServer"), "Cannot add server: " + e.getMessage());
				}
			}
		});

		// Workflow template selection
		workflowTemplateBox = new Composite(leftPart, SWT.BORDER);
		workflowTemplateBox.setLayout(SwtUtilities.createSmallMarginGridLayout(4, false));
		workflowTemplateBox.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		final Label workflowTemplateLabel = new Label(workflowTemplateBox, SWT.NONE);
		workflowTemplateLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 10, 1));
		workflowTemplateLabel.setText("Workflow template");
		workflowTemplateLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		workflowTemplateCombo = new Combo(workflowTemplateBox, SWT.DROP_DOWN);
		workflowTemplateCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 4, 1));

		SwtUtilities.addAutoCompleteFeature(workflowTemplateCombo);
		workflowTemplateCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				loadTaskParameters();
				//				loadTaskInstances(); // TODO

				checkButtonStatus();
			}
		});

		// Task selection
		final Composite taskInstancesBox = new Composite(leftPart, SWT.BORDER);
		taskInstancesBox.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));
		taskInstancesBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));

		final Label propertiesTableLabel = new Label(taskInstancesBox, SWT.NONE);
		propertiesTableLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		propertiesTableLabel.setText("Executed task instances");
		propertiesTableLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		taskInstancesTable = new Table(taskInstancesBox, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		taskInstancesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		taskInstancesTable.setHeaderVisible(true);
		taskInstancesTable.setLinesVisible(true);
		taskInstancesTable.addSelectionListener(new TableItemSelectionListener());

		columnSortListener = new ColumnSortListener();

		// WindowsBug: First column can not be set to right alignment
		final TableColumn column = new TableColumn(taskInstancesTable, SWT.RIGHT);
		column.setWidth(0);
		column.setText(LangResources.get("columnheader_dummy"));

		final TableColumn columnId = new TableColumn(taskInstancesTable, SWT.RIGHT);
		columnId.setMoveable(false);
		columnId.setWidth(25);
		columnId.setText(LangResources.get("columnheader_id"));

		final TableColumn columnName = new TableColumn(taskInstancesTable, SWT.LEFT);
		columnName.setMoveable(true);
		columnName.setWidth(200);
		columnName.setText(LangResources.get("columnheader_name"));
		final int columnPathIndex = Arrays.asList(taskInstancesTable.getColumns()).indexOf(columnName);
		columnName.addListener(SWT.Selection, columnSortListener);

		final TableColumn columnSTatus = new TableColumn(taskInstancesTable, SWT.LEFT);
		columnSTatus.setMoveable(true);
		columnSTatus.setWidth(175);
		columnSTatus.setText(LangResources.get("columnheader_status"));
		final int columnKeyIndex = Arrays.asList(taskInstancesTable.getColumns()).indexOf(columnSTatus);
		columnSTatus.addListener(SWT.Selection, columnSortListener);
	}

	protected void configureArgoWfSchedulerClient() {
		try {
			if (Utilities.isNotBlank(serverSelectioncombo.getText())) {
				final ServerConfiguration serverConfiguration = serverConfigurations.get(serverSelectioncombo.getText());
				String clientSecret = serverConfiguration.getClientSecret();
				if (Utilities.isEmpty(serverConfiguration.getClientSecret())) {
					final CredentialsDialog dialog = new CredentialsDialog(getShell(),
							Argonaut.APPLICATION_NAME,
							LangResources.get("enterClientSecretForServer", serverConfiguration.getClientID(), serverConfiguration.getDisplayName()),
							false,
							true,
							"Client ID",
							"Client Secret",
							LangResources.get("ok"),
							LangResources.get("cancel"));
					final Credentials credentials = dialog.open();
					if (credentials != null) {
						clientSecret = new String(credentials.getPassword());
					}
				}

				if (Utilities.isNotEmpty(clientSecret)) {
					argoWfSchedulerClient = new ArgoWfSchedulerClient(
							proxyConfiguration,
							Utilities.interpretAsBool(applicationConfiguration.get(Argonaut.CONFIG_TLS_SERVER_CERTIFICATE_CHECK)),
							serverConfiguration.getIdpUrl(),
							serverConfiguration.getRealmID(),
							serverConfiguration.getClientID(),
							clientSecret,
							serverConfiguration.getArgoWfSchedulerBaseUrl());
				} else {
					argoWfSchedulerClient = null;
					showErrorMessage(LangResources.get("loadWorkflowTemplates"), "Cannot create ArgoWfSchedulerClient: Missing Client Secret");
				}
			} else {
				argoWfSchedulerClient = null;
			}
		} catch (final Exception e) {
			argoWfSchedulerClient = null;
			showErrorMessage(LangResources.get("loadWorkflowTemplates"), "Cannot create ArgoWfSchedulerClient: " + e.getMessage());
		}
	}

	protected void loadWorflowTemplates() {
		try {
			if (argoWfSchedulerClient != null) {
				final List<String> workflowNames = argoWfSchedulerClient.getWorkflowNames();
				workflowTemplateCombo.setItems(workflowNames.toArray(new String[0]));

				checkButtonStatus();
			} else {
				workflowTemplateCombo.setItems(new String[0]);
			}
		} catch (final Exception e) {
			workflowTemplateCombo.setItems(new String[0]);
			showErrorMessage(LangResources.get("loadWorkflowTemplates"), "Cannot load workflow templates: " + e.getMessage());
		}
	}

	private void loadTaskParameters() {
		Map<String, String> taskParameters = null;
		try {
			if (argoWfSchedulerClient != null) {
				taskParameters = argoWfSchedulerClient.getWorkflowTemplateParameters(workflowTemplateCombo.getText());
			}
		} catch (final Exception e) {
			showErrorMessage(LangResources.get("loadTaskParameters"), "Cannot load task parameters: " + e.getMessage());
		}

		fillParametersPart(taskParameters, false);

		checkButtonStatus();
	}

	private void setupTable() {
		if (currentFillDataListener != null) {
			taskInstancesTable.removeListener(SWT.SetData, currentFillDataListener);
			currentFillDataListener = null;
			taskInstancesTable.setItemCount(0);
		}
		taskInstancesTable.clearAll();
		for (final TableColumn column : taskInstancesTable.getColumns()) {
			if (!column.getText().equals(LangResources.get("columnheader_dummy"))
					&& !column.getText().equals(LangResources.get("columnheader_id"))
					&& !column.getText().equals(LangResources.get("columnheader_name"))
					&& !column.getText().equals(LangResources.get("columnheader_status"))) {
				column.dispose();
			}
		}

		// TODO
		//		if (languageProperties != null) {
		//			for (final String sign : availableLanguageSigns) {
		//				final TableColumn column = new TableColumn(taskInstancesTable, SWT.CENTER);
		//				column.setMoveable(true);
		//				column.setWidth(sign.length() > 3 ? 50 : 25);
		//				if (sign.equals(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT)) {
		//					column.setText(LangResources.get("columnheader_default"));
		//				}
		//				else column.setText(sign);
		//				column.addListener(SWT.Selection, columnSortListener);
		//			}
		//
		//			currentFillDataListener = new FillDataListener();
		//			taskInstancesTable.addListener(SWT.SetData, currentFillDataListener);
		//
		//			taskInstancesTable.setItemCount(languageProperties.size());
		//
		//			taskInstancesTable.setSortColumn(taskInstancesTable.getColumn(1));
		//			taskInstancesTable.setSortDirection(SWT.UP);
		//
		//			for (final Control field : parametersPart.getChildren()) {
		//				field.dispose();
		//			}
		//
		//			for (final String sign : availableLanguageSigns) {
		//				final Label languageLabel = new Label(parametersPart, SWT.NONE);
		//				if (LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT.equals(sign)) {
		//					languageLabel.setText(LangResources.get("columnheader_default") + ":");
		//				} else {
		//					languageLabel.setText(sign + ":");
		//				}
		//				final Text languageTextfield = new Text(parametersPart, SWT.BORDER);
		//				languageTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		//				languageTextfield.addModifyListener(new DetailModifyListener());
		//				languageTextFields.put(sign, languageTextfield);
		//			}
		//		} else {
		//			for (final Control field : parametersPart.getChildren()) {
		//				field.dispose();
		//			}
		//		}
		parametersPart.layout();
	}

	private void createRightPart(final SashForm parent) throws Exception {
		rightPart = new Composite(parent, SWT.NONE);
		rightPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		rightPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		final Label parametersLabel = new Label(rightPart, SWT.NONE);
		parametersLabel.setLayoutData(new GridData(SWT.LEFT, SWT.UP, true, false, 1, 1));
		parametersLabel.setText("Parameters");
		parametersLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		final Composite parametersRegion = new Composite(rightPart, SWT.NONE);
		parametersRegion.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true, 1, 1));
		parametersRegion.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));

		scrolledPart = new ScrolledComposite(parametersRegion, SWT.H_SCROLL | SWT.V_SCROLL);

		parametersPart = new Composite(scrolledPart, SWT.NONE);
		parametersPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true, 1, 1));
		parametersPart.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));

		parametersTextFields = new LinkedHashMap<>();

		scrolledPart.setContent(parametersPart);
		scrolledPart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		scrolledPart.setMinSize(200, 250);
		scrolledPart.setExpandHorizontal(true);
		scrolledPart.setExpandVertical(true);

		final Composite buttonRegion = new Composite(rightPart, SWT.NONE);
		buttonRegion.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));
		buttonRegion.setLayout(SwtUtilities.createSmallMarginGridLayout(2, true));

		final Label buttonSeparatorLabel = new Label(buttonRegion, SWT.SEPARATOR | SWT.HORIZONTAL);
		buttonSeparatorLabel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, true, 2, 1));

		showLogDataButton = new Button(buttonRegion, SWT.PUSH);
		showLogDataButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
		showLogDataButton.setText(LangResources.get("showLogData"));
		showLogDataButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				// TODO
			}
		});

		startTaskButton = new Button(buttonRegion, SWT.PUSH);
		startTaskButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		startTaskButton.setText(LangResources.get("startTask"));
		startTaskButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				int taskID;
				try {
					final Map<String, String> parameters = new LinkedHashMap<>();
					for (final Entry<String, Text> parameterTextFieldEntry : parametersTextFields.entrySet()) {
						parameters.put(parameterTextFieldEntry.getKey(), parameterTextFieldEntry.getValue().getText());
					}
					taskID = argoWfSchedulerClient.createTask(workflowTemplateCombo.getText(), parameters, true);
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("startTask"), "Cannot create new task: " + e.getMessage());
					return;
				}

				try {
					argoWfSchedulerClient.startTask(taskID);
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("startTask"), "Cannot start newly created task: " + e.getMessage());
				}
			}
		});

		closeButton = new Button(buttonRegion, SWT.PUSH);
		closeButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		closeButton.setText(LangResources.get("close"));
		closeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				close();
			}
		});

		checkButtonStatus();
	}

	private class TableItemSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			// TODO
			//			technicalDataChange = true;
			//			if (!dataWasModified || askForDiscardChanges()) {
			//				// make a new selection
			//				refreshDetailView();
			//				currentSelectedKeys = getSelectedKeys();
			//			} else {
			//				// reselect old entry
			//				taskInstancesTable.deselectAll();
			//				for (int i = 0; i < languageProperties.size(); i++) {
			//					final LanguageProperty languageProperty = languageProperties.get(i);
			//					if (languageProperty.getPath().equals(currentSelectedKeys.get(0).getFirst()) && languageProperty.getKey().equals(currentSelectedKeys.get(0).getSecond())) {
			//						taskInstancesTable.select(i);
			//						break;
			//					}
			//				}
			//			}
			//			technicalDataChange = false;

			checkButtonStatus();
		}
	}

	private void fillParametersPart(final Map<String, String> taskParameters, final boolean makeParametersReadOnly) {
		for (final Control field : parametersPart.getChildren()) {
			field.dispose();
		}

		if (taskParameters != null) {
			for (final Entry<String, String> parametersEntry : taskParameters.entrySet()) {
				final Label keyLabel = new Label(parametersPart, SWT.NONE);
				keyLabel.setText(parametersEntry.getKey() + ":");
				final Text parameterTextfield = new Text(parametersPart, SWT.BORDER);
				parameterTextfield.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
				parameterTextfield.setText(parametersEntry.getValue());
				parameterTextfield.setEnabled(!makeParametersReadOnly);
				parametersTextFields.put(parametersEntry.getKey(), parameterTextfield);
			}
		}
		scrolledPart.layout();
		parametersPart.layout();
	}

	private class ConfigButtonSelectionListener extends SelectionAdapter {
		@Override
		public void widgetSelected(final SelectionEvent e) {
			try {
				byte[] iconData;
				try (InputStream inputStream = ImageManager.class.getResourceAsStream("Argonaut.ico")) {
					iconData = IoUtilities.toByteArray(inputStream);
				}

				final ApplicationConfigurationDialog dialog = new ApplicationConfigurationDialog(getShell(), applicationConfiguration, Argonaut.APPLICATION_NAME, Argonaut.APPLICATION_STARTUPCLASS_NAME, iconData, ImageManager.getImage("Argonaut.png"));
				if (dialog.open()) {
					applicationConfiguration.save();

					loadConfiguration();
				}
			} catch (final Exception ex) {
				new ErrorDialog(getShell(), Argonaut.APPLICATION_NAME, Argonaut.VERSION.toString(), Argonaut.APPLICATION_ERROR_EMAIL_ADRESS, ex).open();
			}
		}
	}

	private class HelpButtonSelectionListener extends SelectionAdapter {
		private final ArgonautDialog applicationDialog;

		public HelpButtonSelectionListener(final ArgonautDialog applicationDialog) {
			this.applicationDialog = applicationDialog;
		}

		@Override
		public void widgetSelected(final SelectionEvent e) {
			new HelpDialog(applicationDialog, Argonaut.APPLICATION_NAME + " (" + Argonaut.VERSION.toString() + ") " + LangResources.get("help"), applicationConfiguration).open();
		}
	}

	public void checkButtonStatus() {
		if (removeServerButton != null) {
			removeServerButton.setEnabled(Utilities.isNotBlank(serverSelectioncombo.getText()));
		}

		if (editServerButton != null) {
			editServerButton.setEnabled(Utilities.isNotBlank(serverSelectioncombo.getText()));
		}

		if (workflowTemplateCombo != null) {
			workflowTemplateCombo.setEnabled(workflowTemplateCombo.getItems().length > 0);
		}

		if (startTaskButton != null) {
			startTaskButton.setEnabled(Utilities.isNotBlank(workflowTemplateCombo.getText()));
		}

		if (taskInstancesTable != null) {
			taskInstancesTable.setEnabled(taskInstancesTable.getItemCount() > 0);
		}
	}

	@Override
	public void close() {
		applicationConfiguration.save();
		dispose();
	}

	private class ColumnSortListener implements Listener {
		@Override
		public void handleEvent(final Event event) {
			//			final TableColumn columnToSort = (TableColumn) event.widget;
			//			final Table table = columnToSort.getParent();
			//			if (columnToSort == table.getSortColumn()) {
			//				if (table.getSortDirection() == SWT.UP) {
			//					table.setSortDirection(SWT.DOWN);
			//				} else {
			//					table.setSortDirection(SWT.UP);
			//				}
			//			} else {
			//				table.setSortDirection(SWT.UP);
			//			}
			//			table.setSortColumn(columnToSort);
			//
			//			if (columnToSort.getText().equals(LangResources.get("columnheader_key"))) {
			//				final Comparator<LanguageProperty> compareByName = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getKey);
			//				if (table.getSortDirection() == SWT.UP) {
			//					languageProperties = languageProperties.stream().sorted(compareByName).collect(Collectors.toList());
			//				} else {
			//					languageProperties = languageProperties.stream().sorted(compareByName.reversed()).collect(Collectors.toList());
			//				}
			//			} else if (columnToSort.getText().equals(LangResources.get("columnheader_original_index")) || columnToSort.getText().equals(LangResources.get("columnheader_path"))) {
			//				final Comparator<LanguageProperty> compareByIndex = Comparator.comparing(LanguageProperty::getPath).thenComparing(LanguageProperty::getOriginalIndex);
			//				if (table.getSortDirection() == SWT.UP) {
			//					languageProperties = languageProperties.stream().sorted(compareByIndex).collect(Collectors.toList());
			//				} else {
			//					languageProperties = languageProperties.stream().sorted(compareByIndex.reversed()).collect(Collectors.toList());
			//				}
			//			} else if (columnToSort.getText().equals(LangResources.get("columnheader_default"))) {
			//				if (table.getSortDirection() == SWT.UP) {
			//					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))).compareTo(getEmptyForNull(o2.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT)))).collect(Collectors.toList());
			//				} else {
			//					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))).compareTo(getEmptyForNull(o2.getLanguageValue(LanguagePropertiesFileSetReader.LANGUAGE_SIGN_DEFAULT))) * -1).collect(Collectors.toList());
			//				}
			//			} else {
			//				if (table.getSortDirection() == SWT.UP) {
			//					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(columnToSort.getText()))).compareTo(getEmptyForNull(o2.getLanguageValue(columnToSort.getText())))).collect(Collectors.toList());
			//				} else {
			//					languageProperties = languageProperties.stream().sorted((o1, o2) -> (getEmptyForNull(o1.getLanguageValue(columnToSort.getText()))).compareTo(getEmptyForNull(o2.getLanguageValue(columnToSort.getText()))) * -1).collect(Collectors.toList());
			//				}
			//			}

			refreshTable();
		}
	}

	private static String getEmptyForNull(final String string) {
		return string == null ? "" : string;
	}

	private void refreshTable() {
		taskInstancesTable.deselectAll();

		taskInstancesTable.setRedraw(false);
		taskInstancesTable.clearAll();
		taskInstancesTable.setRedraw(true);

		//		if (currentSelectedKeys != null && currentSelectedKeys.size() > 0) {
		//			final int[] indices = new int[currentSelectedKeys.size()];
		//			for (int i = 0; i < currentSelectedKeys.size(); i++) {
		//				for (int searchIndex = 0; i < languageProperties.size(); searchIndex++) {
		//					final LanguageProperty languageProperty = languageProperties.get(searchIndex);
		//					if (languageProperty.getPath().equals(currentSelectedKeys.get(i).getFirst()) && languageProperty.getKey().equals(currentSelectedKeys.get(i).getSecond())) {
		//						indices[i] = searchIndex;
		//						break;
		//					}
		//				}
		//			}
		//			taskInstancesTable.setSelection(indices);
		//			taskInstancesTable.showSelection();
		//		}
	}

	//	private List<Tuple<String, String>> getSelectedKeys() {
	//		final List<Tuple<String, String>> returnList = new ArrayList<>();
	//		for (final TableItem item : taskInstancesTable.getSelection()) {
	//			returnList.add(new Tuple<>(item.getText(columnPathIndex), item.getText(columnKeyIndex)));
	//		}
	//		return returnList;
	//	}

	public static String[] getTextValues(final TableItem item) {
		final String[] returnValue = new String[item.getParent().getColumnCount()];
		for (int i = 0; i < returnValue.length; i++) {
			returnValue[i] = item.getText(i);
		}
		return returnValue;
	}

	@Override
	protected void setDailyUpdateCheckStatus(final boolean checkboxStatus) {
		applicationConfiguration.set(Argonaut.CONFIG_DAILY_UPDATE_CHECK, checkboxStatus);
		applicationConfiguration.set(Argonaut.CONFIG_NEXT_DAILY_UPDATE_CHECK, LocalDateTime.now().plusDays(1));
		applicationConfiguration.save();
	}

	@Override
	protected Boolean isDailyUpdateCheckActivated() {
		return applicationConfiguration.getBoolean(Argonaut.CONFIG_DAILY_UPDATE_CHECK);
	}

	protected boolean dailyUpdateCheckIsPending() {
		return applicationConfiguration.getBoolean(Argonaut.CONFIG_DAILY_UPDATE_CHECK)
				&& (applicationConfiguration.getDate(Argonaut.CONFIG_NEXT_DAILY_UPDATE_CHECK) == null || applicationConfiguration.getDate(Argonaut.CONFIG_NEXT_DAILY_UPDATE_CHECK).isBefore(LocalDateTime.now()))
				&& NetworkUtilities.checkForNetworkConnection();
	}

	public void showData(final String title, final String text) {
		new ShowDataDialog(getShell(), title, text, true).open();
	}

	public void showMessage(final String title, final String text) {
		new QuestionDialog(getShell(), title, text, LangResources.get("ok")).open();
	}

	public void showErrorMessage(final String title, final String text) {
		new QuestionDialog(getShell(), title, text, LangResources.get("ok")).setBackgroundColor(SwtColor.LightRed).open();
	}
}
