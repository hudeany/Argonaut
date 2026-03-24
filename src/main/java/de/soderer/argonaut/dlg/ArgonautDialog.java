package de.soderer.argonaut.dlg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
import de.soderer.argonaut.helper.TaskInstanceStatus;
import de.soderer.argonaut.helper.TaskStatus;
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
import de.soderer.utilities.DateUtilities;
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
	private Button reloadButton;

	private Composite rightPart = null;
	private Composite parametersPart;
	private ScrolledComposite scrolledPart;
	private Map<String, Text> parametersTextFields;
	private Table tasksTable;
	private Table taskInstancesTable;
	
	private Listener currentFillTasksDataListener;
	private Listener currentFillTaskInstancesDataListener;

	private Composite workflowTemplateBox;
	private Combo workflowTemplateCombo;

	private Button activateTimeTriggerButton;
	private Text cronExpressionText;
	
	private Button startTaskButton;
	private Button createParameterConfigurationButton;
	private Button showLogDataButton;
	private Button closeButton;

	private final ConfigurationProperties applicationConfiguration;
	private Map<String, ServerConfiguration> serverConfigurations = new LinkedHashMap<>();

	private String currentServerSelection = null;
	private String currentWorkflowTemplateName = null;
	
	private List<TaskStatus> listOfTasksStatus = new ArrayList<>();
	private TaskStatus currentTaskStatus = null;
	
	private List<TaskInstanceStatus> listOfTaskInstancesStatus = new ArrayList<>();
	private TaskInstanceStatus currentTaskInstanceStatus = null;

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
					ApplicationUpdateUtilities.executeUpdate(this, Argonaut.VERSIONINFO_DOWNLOAD_URL, proxyConfiguration, Argonaut.APPLICATION_NAME, Argonaut.VERSION, Argonaut.TRUSTED_UPDATE_CA_CERTIFICATES, null, null, null, true, false);
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
		setSize(1000, 600);
		setMinimumSize(450, 450);

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
					serverConfiguration.setCookieData((String) itemJsonObject.getSimpleValue("cookieData"));

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
				if (Utilities.isNotBlank(serversConfiguration.getCookieData())) {
					serverJsonObject.add("cookieData", serversConfiguration.getCookieData());
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
				PleaseWaitDialog pleaseWaitDialog = null;
				try {
					pleaseWaitDialog = new PleaseWaitDialog(getShell(), Argonaut.APPLICATION_NAME);
					pleaseWaitDialog.open();

					currentServerSelection = ((Combo) arg0.getSource()).getText();
					configureArgoWfSchedulerClient();

					loadWorflowTemplates();
					fillParametersPart(null, false);
					
					currentTaskStatus = null;
					setupTasksTable();
					currentTaskInstanceStatus = null;
					setupTaskInstancesTable();

					checkButtonStatus();
				} finally {
					if (pleaseWaitDialog != null) {
						pleaseWaitDialog.hide();
					}
				}
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
							LangResources.get("clientSecret"),
							LangResources.get("cookieData")};
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
						if (Utilities.isNotEmpty(serverValues.get(6))) {
							serverConfiguration.setCookieData(serverValues.get(6));
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
					if (Utilities.isNotBlank(currentServerSelection)) {
						final QuestionDialog dialog = new QuestionDialog(getShell(), Argonaut.APPLICATION_NAME, LangResources.get("reallyRemoveServer", currentServerSelection), LangResources.get("yes"), LangResources.get("no"));
						final Integer result = dialog.open();
						if (result == 0) {
							serverConfigurations.remove(currentServerSelection);
							saveConfiguration();
							serverSelectioncombo.setItems(serverConfigurations.keySet().toArray(new String[0]));
							serverSelectioncombo.setText("");
						}

						checkButtonStatus();
					}
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("removeServer"), "Cannot remove server: " + e.getMessage());
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
					if (Utilities.isNotBlank(currentServerSelection) && serverConfigurations.containsKey(currentServerSelection)) {
						final ServerConfiguration serverConfiguration = serverConfigurations.get(currentServerSelection);
						final String[] serverItems = new String[] {
								LangResources.get("displayName"),
								LangResources.get("idpUrl"),
								LangResources.get("realmID"),
								LangResources.get("argoWfSchedulerBaseUrl"),
								LangResources.get("clientID"),
								LangResources.get("clientSecret"),
								LangResources.get("cookieData")};
						final MultiInputDialog dialog = new MultiInputDialog(getShell(), Argonaut.APPLICATION_NAME, LangResources.get("editServer"), serverItems);
						dialog.setWidth(300);
						dialog.setDefaultTexts(new String[] {
								serverConfiguration.getDisplayName(),
								serverConfiguration.getIdpUrl(),
								serverConfiguration.getRealmID(),
								serverConfiguration.getArgoWfSchedulerBaseUrl(),
								serverConfiguration.getClientID(),
								serverConfiguration.getClientSecret() == null ? "" : serverConfiguration.getClientSecret(),
								serverConfiguration.getCookieData() == null ? "" : serverConfiguration.getCookieData()});
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
							if (Utilities.isNotEmpty(serverValues.get(6))) {
								serverConfiguration.setCookieData(serverValues.get(6));
							} else {
								serverConfiguration.setCookieData(null);
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
		workflowTemplateBox.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));
		workflowTemplateBox.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 1, 1));

		final Label workflowTemplateLabel = new Label(workflowTemplateBox, SWT.NONE);
		workflowTemplateLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 10, 1));
		workflowTemplateLabel.setText(LangResources.get("workflowTemplate"));
		workflowTemplateLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		workflowTemplateCombo = new Combo(workflowTemplateBox, SWT.DROP_DOWN);
		workflowTemplateCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1));

		SwtUtilities.addAutoCompleteFeature(workflowTemplateCombo);
		workflowTemplateCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent arg0) {
				PleaseWaitDialog pleaseWaitDialog = null;
				try {
					pleaseWaitDialog = new PleaseWaitDialog(getShell(), Argonaut.APPLICATION_NAME);
					pleaseWaitDialog.open();

					currentWorkflowTemplateName = ((Combo) arg0.getSource()).getText();

					setupTasksTable();
					loadTaskParameters();

					checkButtonStatus();
				} finally {
					if (pleaseWaitDialog != null) {
						pleaseWaitDialog.hide();
					}
				}
			}
		});

		reloadButton = new Button(workflowTemplateBox, SWT.PUSH);
		reloadButton.setImage(ImageManager.getImage("reload.png"));
		reloadButton.setToolTipText(LangResources.get("reloadTaskInstances"));
		reloadButton.setLayoutData(new GridData(25, 25));
		reloadButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				try {
					if (Utilities.isNotBlank(currentWorkflowTemplateName)) {
						PleaseWaitDialog pleaseWaitDialog = null;
						try {
							pleaseWaitDialog = new PleaseWaitDialog(getShell(), Argonaut.APPLICATION_NAME);
							pleaseWaitDialog.open();

							setupTasksTable();
							loadTaskParameters();

							checkButtonStatus();
						} finally {
							if (pleaseWaitDialog != null) {
								pleaseWaitDialog.hide();
							}
						}
						
						listOfTaskInstancesStatus = new ArrayList<>();
						setupTaskInstancesTable();

						checkButtonStatus();
					}
				} catch (final Exception e) {
					showErrorMessage(LangResources.get("reloadTaskInstances"), "Cannot reload task instances: " + e.getMessage());
				}
			}
		});
		
		// Tasks
		createTasksTable(leftPart);
		
		// Instances
		createTaskInstancesTable(leftPart);
	}

	private void createTasksTable(Composite parent) {
		final Composite tasksBox = new Composite(parent, SWT.BORDER);
		tasksBox.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		GridData tasksGridData = new GridData(SWT.FILL, SWT.TOP, true, false, 7, 1);
		tasksGridData.heightHint = 200;
		tasksBox.setLayoutData(tasksGridData);

		final Label tasksLabel = new Label(tasksBox, SWT.NONE);
		tasksLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		tasksLabel.setText("Tasks");
		tasksLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.NONE));

		tasksTable = new Table(tasksBox, SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);
		tasksTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tasksTable.setHeaderVisible(true);
		tasksTable.setLinesVisible(true);
		
		final TableColumn columnTaskId = new TableColumn(tasksTable, SWT.LEFT);
		columnTaskId.setWidth(30);
		columnTaskId.setText("ID");

		final TableColumn columnTaskName = new TableColumn(tasksTable, SWT.LEFT);
		columnTaskName.setWidth(120);
		columnTaskName.setText("Task Name");
		
		final TableColumn columnCreated = new TableColumn(tasksTable, SWT.LEFT);
		columnCreated.setWidth(100);
		columnCreated.setText("Anlagedatum");

		final TableColumn columnCronExpression = new TableColumn(tasksTable, SWT.LEFT);
		columnCronExpression.setWidth(100);
		columnCronExpression.setText("Cron Expression");
		
		final TableColumn columnNextStart = new TableColumn(tasksTable, SWT.LEFT);
		columnNextStart.setWidth(100);
		columnNextStart.setText("Next Start");
		
		de.soderer.argonaut.utilities.SwtUtilities.makeSortable(
			tasksTable,
			listOfTasksStatus,
			Arrays.asList(
				TaskStatus::getTaskID,
				TaskStatus::getTaskName,
				TaskStatus::getCreated,
				t -> t.getCronExpression() == null ? "" : t.getCronExpression(),
				TaskStatus::getNextScheduledTime
			)
		);
		
		tasksTable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent ev) {
				try {
					TableItem item = (TableItem) ev.item;
					if (item != null) {
						int taskID = Integer.parseInt(item.getText());
						currentTaskStatus = argoWfSchedulerClient.getTaskStatus(taskID);
						currentTaskInstanceStatus = null;
						setupTaskInstancesTable();
						
						if (currentTaskStatus != null) {
							fillParametersPart(currentTaskStatus.getParameters(), true);
						} else {
							fillParametersPart(null, false);
						}
					}
				} catch (Exception e) {
					showErrorMessage(LangResources.get("loadTaskInstances"), "Cannot load task instances: " + e.getMessage());
				}
			}
		});
	}

	private void createTaskInstancesTable(Composite leftPart) {
		final Composite instancesBox = new Composite(leftPart, SWT.BORDER);
		instancesBox.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));
		instancesBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));

		final Label taskInstancesTableLabel = new Label(instancesBox, SWT.NONE);
		taskInstancesTableLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		taskInstancesTableLabel.setText(LangResources.get("taskInstances"));
		taskInstancesTableLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		taskInstancesTable = new Table(instancesBox, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);
		taskInstancesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		taskInstancesTable.setHeaderVisible(true);
		taskInstancesTable.setLinesVisible(true);

		final TableColumn columnTaskId = new TableColumn(taskInstancesTable, SWT.RIGHT);
		columnTaskId.setMoveable(false);
		columnTaskId.setWidth(50);
		columnTaskId.setText(LangResources.get("columnheader_taskid"));

		final TableColumn columnInstanceId = new TableColumn(taskInstancesTable, SWT.RIGHT);
		columnInstanceId.setMoveable(true);
		columnInstanceId.setWidth(40);
		columnInstanceId.setText(LangResources.get("columnheader_instanceid"));

		final TableColumn columnTaskName = new TableColumn(taskInstancesTable, SWT.LEFT);
		columnTaskName.setMoveable(true);
		columnTaskName.setWidth(175);
		columnTaskName.setText(LangResources.get("columnheader_name"));

		final TableColumn columnStart = new TableColumn(taskInstancesTable, SWT.LEFT);
		columnStart.setMoveable(true);
		columnStart.setWidth(100);
		columnStart.setText(LangResources.get("columnheader_start"));

		final TableColumn columnSatus = new TableColumn(taskInstancesTable, SWT.LEFT);
		columnSatus.setMoveable(true);
		columnSatus.setWidth(150);
		columnSatus.setText(LangResources.get("columnheader_status"));
		
		de.soderer.argonaut.utilities.SwtUtilities.makeSortable(
			taskInstancesTable,
			listOfTaskInstancesStatus,
			Arrays.asList(
				TaskInstanceStatus::getTaskID,
				TaskInstanceStatus::getTaskInstanceID,
				TaskInstanceStatus::getWorkflowId,
				TaskInstanceStatus::getUpdated,
				TaskInstanceStatus::getStatus
			)
		);
		
		taskInstancesTable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent ev) {
				try {
					TableItem item = (TableItem) ev.item;
					if (item != null) {
						int taskInstanceID = Integer.parseInt(item.getText(1));
						currentTaskInstanceStatus = argoWfSchedulerClient.getTaskInstance(taskInstanceID);
						
						checkButtonStatus();
					}
				} catch (Exception e) {
					showErrorMessage(LangResources.get("loadTaskInstances"), "Cannot load task instances: " + e.getMessage());
				}
			}
		});
	}

	protected void configureArgoWfSchedulerClient() {
		try {
			if (Utilities.isNotBlank(currentServerSelection)) {
				final ServerConfiguration serverConfiguration = serverConfigurations.get(currentServerSelection);
				String clientSecret = serverConfiguration.getClientSecret();
				if (Utilities.isEmpty(clientSecret)) {
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
							serverConfiguration.getCookieData(),
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
				taskParameters = argoWfSchedulerClient.getWorkflowTemplateParameters(currentWorkflowTemplateName);
			}
		} catch (final Exception e) {
			showErrorMessage(LangResources.get("loadTaskParameters"), "Cannot load task parameters: " + e.getMessage());
		}

		fillParametersPart(taskParameters, false);

		checkButtonStatus();
	}

	private void setupTasksTable() {
		try {
			if (currentFillTasksDataListener != null) {
				tasksTable.removeListener(SWT.SetData, currentFillTasksDataListener);
				currentFillTasksDataListener = null;
				tasksTable.setItemCount(0);
			}

			tasksTable.clearAll();

			listOfTasksStatus = new ArrayList<>();
			currentTaskStatus = null;
			currentTaskInstanceStatus = null;
			listOfTasksStatus = argoWfSchedulerClient.getTasksByWorkflowTemplate(currentWorkflowTemplateName);

			currentFillTasksDataListener = new Listener() {
				@Override
				public void handleEvent(Event event) {
					final TableItem item = (TableItem) event.item;
					final int index = tasksTable.indexOf(item);
					
					final TaskStatus taskStatus = listOfTasksStatus.get(index);
					
					item.setText(0, Integer.toString(taskStatus.getTaskID()));
					item.setText(1, taskStatus.getTaskName());
					final String start = DateUtilities.formatDate(DateUtilities.DD_MM_YYYY_HH_MM, taskStatus.getCreated().withZoneSameInstant(ZoneId.systemDefault()));
					item.setText(2, start);
					item.setText(3, taskStatus.getCronExpression() == null ? "" : taskStatus.getCronExpression());
					item.setText(4, taskStatus.getNextScheduledTime() == null ? "" : DateUtilities.formatDate(DateUtilities.DD_MM_YYYY_HH_MM, taskStatus.getNextScheduledTime().withZoneSameInstant(ZoneId.systemDefault())));
				}
			};
			tasksTable.addListener(SWT.SetData, currentFillTasksDataListener);

			tasksTable.setItemCount(listOfTasksStatus.size());

			tasksTable.setSortColumn(tasksTable.getColumn(0));
			tasksTable.setSortDirection(SWT.UP);

			for (final Control field : parametersPart.getChildren()) {
				field.dispose();
			}

			parametersPart.layout();

			checkButtonStatus();
		} catch (final Exception e) {
			showErrorMessage(LangResources.get("startTask"), "Cannot show task in table: " + e.getMessage());
		}
	}

	private void setupTaskInstancesTable() {
		try {
			if (currentFillTaskInstancesDataListener != null) {
				taskInstancesTable.removeListener(SWT.SetData, currentFillTaskInstancesDataListener);
				currentFillTaskInstancesDataListener = null;
				taskInstancesTable.setItemCount(0);
			}

			taskInstancesTable.clearAll();

			currentTaskInstanceStatus = null;
			if (currentTaskStatus != null) {
				listOfTaskInstancesStatus = argoWfSchedulerClient.getTaskInstances(currentTaskStatus.getTaskID());
			} else {
				listOfTaskInstancesStatus = new ArrayList<>();
			}

			currentFillTaskInstancesDataListener = new Listener() {
				@Override
				public void handleEvent(Event event) {
					final TableItem item = (TableItem) event.item;
					final int index = taskInstancesTable.indexOf(item);
					
					final TaskInstanceStatus taskInstanceStatus = listOfTaskInstancesStatus.get(index);
					
					item.setText(0, Integer.toString(taskInstanceStatus.getTaskID()));
					item.setText(1, Integer.toString(taskInstanceStatus.getTaskInstanceID()));
					item.setText(2, taskInstanceStatus.getWorkflowId());
					item.setText(3, DateUtilities.formatDate(DateUtilities.DD_MM_YYYY_HH_MM, taskInstanceStatus.getCreated().withZoneSameInstant(ZoneId.systemDefault())));
					item.setText(4, taskInstanceStatus.getStatus() == null ? "" : taskInstanceStatus.getStatus());
				}
			};
			taskInstancesTable.addListener(SWT.SetData, currentFillTaskInstancesDataListener);

			taskInstancesTable.setItemCount(listOfTaskInstancesStatus.size());

			taskInstancesTable.setSortColumn(taskInstancesTable.getColumn(0));
			taskInstancesTable.setSortDirection(SWT.UP);

			for (final Control field : parametersPart.getChildren()) {
				field.dispose();
			}

			parametersPart.layout();

			checkButtonStatus();
		} catch (final Exception e) {
			showErrorMessage(LangResources.get("startTask"), "Cannot show task instance in table: " + e.getMessage());
		}
	}

	private void createRightPart(final SashForm parent) throws Exception {
		rightPart = new Composite(parent, SWT.NONE);
		rightPart.setLayoutData(new GridData(SWT.FILL, SWT.UP, true, true));
		rightPart.setLayout(SwtUtilities.createSmallMarginGridLayout(1, false));

		final Label parametersLabel = new Label(rightPart, SWT.NONE);
		parametersLabel.setLayoutData(new GridData(SWT.LEFT, SWT.UP, true, false, 1, 1));
		parametersLabel.setText(LangResources.get("parameters"));
		parametersLabel.setFont(new Font(getDisplay(), "Arial", 10, SWT.None));

		final Composite parametersRegion = new Composite(rightPart, SWT.NONE);
		parametersRegion.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true, 1, 1));
		parametersRegion.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));

		scrolledPart = new ScrolledComposite(parametersRegion, SWT.H_SCROLL | SWT.V_SCROLL);

		parametersPart = new Composite(scrolledPart, SWT.NONE);
		parametersPart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		parametersPart.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));

		parametersTextFields = new LinkedHashMap<>();

		scrolledPart.setContent(parametersPart);
		scrolledPart.setAlwaysShowScrollBars(true);
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
				showData(LangResources.get("showLogData"), currentTaskInstanceStatus.getLogMessage());
			}
		});
		
		final Composite timeTriggerRegion = new Composite(buttonRegion, SWT.NONE);
		timeTriggerRegion.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
		timeTriggerRegion.setLayout(SwtUtilities.createSmallMarginGridLayout(2, false));
		
		activateTimeTriggerButton = new Button(timeTriggerRegion, SWT.CHECK);
		activateTimeTriggerButton.setLayoutData(new GridData(SWT.LEFT, SWT.BOTTOM, false, false));
		activateTimeTriggerButton.setText(LangResources.get("timeTrigger"));
		activateTimeTriggerButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				if (activateTimeTriggerButton.getSelection()) {
					startTaskButton.setText(LangResources.get("createTimeTriggeredTask"));
				} else {
					startTaskButton.setText(LangResources.get("createAndStartTaskOnce"));
				}
			}
		});
		
		cronExpressionText = new Text(timeTriggerRegion, SWT.BORDER);
		cronExpressionText.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		ZonedDateTime now = ZonedDateTime.now();
		String cronExpressionNow = DateUtilities.formatDate("* m H d M y", now.withZoneSameInstant(ZoneId.of("UTC")));
		cronExpressionText.setText(cronExpressionNow); // "0 0 0 31 2 *"
		
		createParameterConfigurationButton = new Button(buttonRegion, SWT.PUSH);
		createParameterConfigurationButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
		createParameterConfigurationButton.setText(LangResources.get("createParameterConfiguration"));
		createParameterConfigurationButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				if (currentTaskInstanceStatus == null) {
					String parameterConfiguration;
					try {
						final Map<String, String> parameters = new LinkedHashMap<>();
						for (final Entry<String, Text> parameterTextFieldEntry : parametersTextFields.entrySet()) {
							parameters.put(parameterTextFieldEntry.getKey(), parameterTextFieldEntry.getValue().getText());
						}
						
						if (activateTimeTriggerButton.getSelection()) {
							parameterConfiguration = argoWfSchedulerClient.createParameterConfiguration(currentWorkflowTemplateName, parameters, cronExpressionText.getText());
						} else {
							parameterConfiguration = argoWfSchedulerClient.createParameterConfiguration(currentWorkflowTemplateName, parameters, null);
						}
					} catch (final Exception e) {
						showErrorMessage(LangResources.get("createAndStartTaskOnce"), "Cannot create new task: " + e.getMessage());
						return;
					}

					showData(LangResources.get("createParameterConfiguration"), parameterConfiguration);
				} else {
					currentTaskInstanceStatus = null;

					loadTaskParameters();

					checkButtonStatus();
				}
			}
		});

		startTaskButton = new Button(buttonRegion, SWT.PUSH);
		startTaskButton.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		startTaskButton.setText(LangResources.get("createAndStartTaskOnce"));
		startTaskButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent ev) {
				PleaseWaitDialog pleaseWaitDialog = null;
				try {
					pleaseWaitDialog = new PleaseWaitDialog(getShell(), Argonaut.APPLICATION_NAME);
					pleaseWaitDialog.open();

					if (currentTaskInstanceStatus == null) {
						int taskID;
						try {
							final Map<String, String> parameters = new LinkedHashMap<>();
							for (final Entry<String, Text> parameterTextFieldEntry : parametersTextFields.entrySet()) {
								parameters.put(parameterTextFieldEntry.getKey(), parameterTextFieldEntry.getValue().getText());
							}
							
							if (activateTimeTriggerButton.getSelection()) {
								taskID = argoWfSchedulerClient.createTask(currentWorkflowTemplateName, parameters, cronExpressionText.getText());
							} else {
								taskID = argoWfSchedulerClient.createTask(currentWorkflowTemplateName, parameters, null);
							}
						} catch (final Exception e) {
							showErrorMessage(LangResources.get("createTimeTriggeredTask"), "Cannot create new task: " + e.getMessage());
							return;
						}

						if (activateTimeTriggerButton.getSelection()) {
							showMessage(LangResources.get("createAndStartTaskOnce"), LangResources.get("startedTask", taskID));
						} else {
							try {
								argoWfSchedulerClient.startTask(taskID);
							} catch (final Exception e) {
								showErrorMessage(LangResources.get("createAndStartTaskOnce"), "Cannot start newly created task: " + e.getMessage());
							}
	
							pleaseWaitDialog.hide();
							pleaseWaitDialog = null;
	
							showMessage(LangResources.get("createAndStartTaskOnce"), LangResources.get("startedTask", taskID));
						} 
					} else {
						currentTaskInstanceStatus = null;

						loadTaskParameters();

						checkButtonStatus();
					}
				} finally {
					if (pleaseWaitDialog != null) {
						pleaseWaitDialog.hide();
					}
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

	private void fillParametersPart(final Map<String, String> taskParameters, final boolean makeParametersReadOnly) {
		for (final Control field : parametersPart.getChildren()) {
			field.dispose();
		}

		parametersTextFields = new LinkedHashMap<>();

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
		
		scrolledPart.setMinSize(parametersPart.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		scrolledPart.layout(true, true);
		rightPart.layout(true, true);
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
			removeServerButton.setEnabled(Utilities.isNotBlank(currentServerSelection));
		}

		if (editServerButton != null) {
			editServerButton.setEnabled(Utilities.isNotBlank(currentServerSelection));
		}

		if (workflowTemplateCombo != null) {
			workflowTemplateCombo.setEnabled(workflowTemplateCombo.getItems().length > 0);
		}

		if (startTaskButton != null) {
			if (currentTaskStatus == null) {
				startTaskButton.setText(LangResources.get("createAndStartTaskOnce"));
			} else {
				startTaskButton.setText(LangResources.get("prepareTask"));
			}
			startTaskButton.setEnabled(Utilities.isNotBlank(currentWorkflowTemplateName));
		}

		if (tasksTable != null) {
			tasksTable.setEnabled(tasksTable.getItemCount() > 0);
			
			activateTimeTriggerButton.setEnabled(Utilities.isNotBlank(currentWorkflowTemplateName));
			cronExpressionText.setEnabled(Utilities.isNotBlank(currentWorkflowTemplateName));
			startTaskButton.setEnabled(Utilities.isNotBlank(currentWorkflowTemplateName));
			createParameterConfigurationButton.setEnabled(Utilities.isNotBlank(currentWorkflowTemplateName));
		}

		if (taskInstancesTable != null) {
			taskInstancesTable.setEnabled(taskInstancesTable.getItemCount() > 0);
		}

		if (showLogDataButton != null) {
			showLogDataButton.setEnabled(currentTaskInstanceStatus != null && currentTaskInstanceStatus.getLogMessage() != null);
		}

		if (reloadButton != null) {
			reloadButton.setEnabled(Utilities.isNotEmpty(workflowTemplateCombo.getText()));
		}
	}

	@Override
	public void close() {
		applicationConfiguration.save();
		dispose();
	}

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
