package de.soderer.argonaut.helper;

import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.TrustManager;

import de.soderer.json.JsonArray;
import de.soderer.json.JsonNode;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.network.HttpMethod;
import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.network.TrustManagerUtilities;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.utilities.DateUtilities;
import de.soderer.utilities.JwtUtilities;

public class ArgoWfSchedulerClient {
	private final ProxyConfiguration proxyConfiguration;

	private final String idpUrl;
	private final String realmID;
	private final String clientID;
	private final String clientSecret;
	private final String argoWfSchedulerBaseUrl;

	private TrustManager trustManager = null;

	private String accesToken = null;
	private ZonedDateTime accessTokenValidUntil = null;

	public ArgoWfSchedulerClient(final ProxyConfiguration proxyConfiguration, final boolean tlsServerCertificateCheck, final String idpUrl, final String realmID, final String clientID, final String clientSecret, final String argoWfSchedulerBaseUrl) throws Exception {
		this.proxyConfiguration = proxyConfiguration;
		this.idpUrl = idpUrl;
		this.realmID = realmID;
		this.clientID = clientID;
		this.clientSecret = clientSecret;
		this.argoWfSchedulerBaseUrl = argoWfSchedulerBaseUrl;

		if (!tlsServerCertificateCheck) {
			trustManager = TrustManagerUtilities.createTrustAllTrustManager();
		}
	}

	public List<String> getWorkflowNames() throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/workflow/names");
			request.addHeader("Authorization", "Bearer " + accessToken);

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				if (response.getHeaders().containsKey("Content-Type") && !"application/json".equals(response.getHeaders().get("Content-Type"))) {
					throw new Exception("Invalid WorkflowNames data type: " + response.getHeaders().get("Content-Type"));
				}

				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid WorkflowNames JSON data", e);
				}
				final JsonArray workflowNamesArray = ((JsonArray) contentJson);
				final List<String> returnList = new ArrayList<>();
				for (final Object workflowNameObject : workflowNamesArray) {
					returnList.add((String) workflowNameObject);
				}
				return returnList;
			} else {
				throw new Exception("getWorkflowNames failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public Map<String, String> getWorkflowTemplateParameters(final String workflowName) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/workflow/" + URLEncoder.encode(workflowName, StandardCharsets.UTF_8) + "/parameter");
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid WorkflowParameters JSON data", e);
				}

				final JsonObject workflowParameters = ((JsonObject) contentJson);
				final Map<String, String> returnMap = new LinkedHashMap<>();
				for (final Entry<String, Object> workflowParameterEntry : workflowParameters.simpleEntrySet()) {
					returnMap.put(workflowParameterEntry.getKey(), (String) workflowParameterEntry.getValue());
				}
				return returnMap;
			} else {
				throw new Exception("getWorkflowTemplateParameters failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public int createTask(final String workflowName, final Map<String, String> taskParameters, final boolean executeOnlyOnce) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.POST, argoWfSchedulerBaseUrl + "/tasks");
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");
			request.addHeader("Content-Type", "application/json");

			final JsonObject requestBodyJsonObject = new JsonObject();
			requestBodyJsonObject.add("name", "Task_" + workflowName + "_" + DateUtilities.formatDate(DateUtilities.ISO_8601_DATETIME_FORMAT_NO_TIMEZONE, ZonedDateTime.now()));
			if (executeOnlyOnce) {
				requestBodyJsonObject.add("cronExpression", "0 0 0 31 2 *"); // 31.02.yyyy => repeat never
			} else {
				requestBodyJsonObject.add("cronExpression", "0 0 0 * * *");
			}
			requestBodyJsonObject.add("workflowRef", workflowName);

			final JsonArray parametersJsonArray = new JsonArray();
			for (final Entry<String, String> taskParameter : taskParameters.entrySet()) {
				final JsonObject parameterJsonObject = new JsonObject();

				parameterJsonObject.add("name", taskParameter.getKey());
				parameterJsonObject.add("value", taskParameter.getValue());

				parametersJsonArray.add(parameterJsonObject);
			}
			requestBodyJsonObject.add("parameters", parametersJsonArray);

			request.setRequestBody(requestBodyJsonObject.toString());

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 201) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid Task JSON data", e);
				}

				final JsonObject jsonObject = ((JsonObject) contentJson);
				return (Integer) jsonObject.getSimpleValue("id");
			} else {
				throw new Exception("createTask failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public JsonArray getAllTasks() throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/search");
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid Tasks JSON data", e);
				}

				final JsonArray jsonArray = ((JsonArray) contentJson);
				return jsonArray;
			} else {
				throw new Exception("getAllTasks failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public Map<String, String> getTaskParameters(final int taskID) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/" + taskID);
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid Task JSON data", e);
				}

				final JsonObject jsonObject = ((JsonObject) contentJson);
				final JsonArray taskParameters = (JsonArray) jsonObject.get("parameters");

				final Map<String, String> returnMap = new LinkedHashMap<>();
				for (final Object taskParameterObject : taskParameters) {
					final JsonObject taskParameterJsonObject = (JsonObject) taskParameterObject;
					returnMap.put((String) taskParameterJsonObject.getSimpleValue("name"), (String) taskParameterJsonObject.getSimpleValue("value"));
				}
				return returnMap;
			} else {
				throw new Exception("getTaskStatus failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public void startTask(final int taskID) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/" + taskID + "/run");
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() != 200) {
				throw new Exception("startTask failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public boolean deleteTask(final int taskID) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.DELETE, argoWfSchedulerBaseUrl + "/tasks/" + taskID);
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 204) {
				return true;
			} else {
				throw new Exception("deleteTask failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	private String aquireAccessTokenByClientId() throws Exception {
		try {
			if (accesToken == null || ZonedDateTime.now().isAfter(accessTokenValidUntil.minusSeconds(10))) {
				final HttpRequest request = new HttpRequest(HttpMethod.POST, idpUrl + "/realms/" + realmID + "/protocol/openid-connect/token");
				request.addPostParameter("grant_type", "client_credentials");
				request.addPostParameter("client_id", clientID);
				request.addPostParameter("client_secret", clientSecret);

				final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
				if (response.getHttpCode() == 200) {
					JsonNode contentJson;
					try {
						contentJson = JsonReader.readJsonItemString(response.getContent());
					} catch (final Exception e) {
						throw new Exception("Invalid AccessToken JSON data", e);
					}
					accesToken = (String) ((JsonObject) contentJson).getSimpleValue("access_token");
					accessTokenValidUntil = JwtUtilities.getJwtTokenValidity(accesToken);
				} else {
					accesToken = null;
					accessTokenValidUntil = null;
					throw new Exception("aquireAccessToken failed. Http Code: " + response.getHttpCode());
				}
			}

			return accesToken;
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public List<Integer> getWorkflowTemplateTaskIds(final String workflowTemplateName) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/search");
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");
			request.addUrlParameter("name", workflowTemplateName);

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid Task JSON data", e);
				}

				final List<Integer> returnList = new ArrayList<>();
				final JsonArray jsonArray = ((JsonArray) contentJson);
				for (final JsonNode item : jsonArray.items()) {
					final JsonObject itemJsonObject = (JsonObject) item;
					returnList.add((Integer) itemJsonObject.getSimpleValue("id"));
				}
				return returnList;
			} else {
				throw new Exception("getWorkflowTemplateTaskIds failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	public TaskStatus getTaskStatus(final int taskID) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/" + taskID);
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid Task JSON data", e);
				}

				final JsonObject jsonObject = ((JsonObject) contentJson);

				final TaskStatus status = new TaskStatus();

				status.setTaskID((Integer) jsonObject.getSimpleValue("id"));
				status.setTaskName((String) jsonObject.getSimpleValue("name"));
				status.setWorkflowName((String) jsonObject.getSimpleValue("workflowRef"));
				status.setCreated(DateUtilities.parseZonedDateTime(DateUtilities.ISO_8601_DATETIME_WITH_NANOS_FORMAT, (String) jsonObject.getSimpleValue("createdAt"), ZoneId.systemDefault()));
				status.setUpdated(DateUtilities.parseZonedDateTime(DateUtilities.ISO_8601_DATETIME_WITH_NANOS_FORMAT, (String) jsonObject.getSimpleValue("updatedAt"), ZoneId.systemDefault()));

				final Map<Integer, TaskInstanceStatus> instances = new LinkedHashMap<>();
				for (final JsonNode instanceItem : ((JsonArray) jsonObject.get("instances")).items()) {
					TaskInstanceStatus instanceStatus = readTaskInstanceStatus(((JsonObject) instanceItem));

					if (instanceStatus.getStatus().equals("PENDING") || instanceStatus.getStatus().equals("RUNNING")) {
						// Workaround for missing status updates in GET /tasks/{taskID}
						instanceStatus = getTaskInstanceStatus(instanceStatus.getTaskInstanceID());
					}

					instances.put(instanceStatus.getTaskInstanceID(), instanceStatus);
				}
				status.setInstances(instances);

				final Map<String, String> parametersMap = new LinkedHashMap<>();
				for (final JsonNode parameterItem : ((JsonArray) jsonObject.get("parameters")).items()) {
					final JsonObject taskParameterJsonObject = (JsonObject) parameterItem;
					parametersMap.put((String) taskParameterJsonObject.getSimpleValue("name"), (String) taskParameterJsonObject.getSimpleValue("value"));
				}
				status.setParameters(parametersMap);

				return status;
			} else {
				throw new Exception("getTaskStatus failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	private TaskInstanceStatus getTaskInstanceStatus(final int taskInstanceID) throws Exception {
		try {
			final String accessToken = aquireAccessTokenByClientId();

			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/instances/" + taskInstanceID);
			request.addHeader("Authorization", "Bearer " + accessToken);
			request.addHeader("accept", "application/json");

			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
			if (response.getHttpCode() == 200) {
				JsonNode contentJson;
				try {
					contentJson = JsonReader.readJsonItemString(response.getContent());
				} catch (final Exception e) {
					throw new Exception("Invalid Task JSON data", e);
				}

				return readTaskInstanceStatus((JsonObject) contentJson);
			} else {
				throw new Exception("getTaskStatus failed. Http Code: " + response.getHttpCode());
			}
		} catch (final UnknownHostException e) {
			throw new Exception("UnknownHost '" + e.getMessage() + "'");
		}
	}

	private static TaskInstanceStatus readTaskInstanceStatus(final JsonObject jsonObject) {
		final TaskInstanceStatus status = new TaskInstanceStatus();

		status.setTaskID((Integer) jsonObject.getSimpleValue("taskId"));
		status.setTaskInstanceID((Integer) jsonObject.getSimpleValue("id"));
		status.setWorkflowId((String) jsonObject.getSimpleValue("workflowId"));
		status.setCreated(DateUtilities.parseZonedDateTime(DateUtilities.ISO_8601_DATETIME_WITH_NANOS_FORMAT, (String) jsonObject.getSimpleValue("createdAt"), ZoneId.systemDefault()));
		status.setUpdated(DateUtilities.parseZonedDateTime(DateUtilities.ISO_8601_DATETIME_WITH_NANOS_FORMAT, (String) jsonObject.getSimpleValue("updatedAt"), ZoneId.systemDefault()));
		status.setStatus((String) jsonObject.getSimpleValue("status"));
		status.setLogMessage((String) jsonObject.getSimpleValue("message"));

		return status;
	}

	//	public List<JsonObject> getTaskLog(final int taskID) throws Exception {
	//		try {
	//			final String accessToken = aquireAccessTokenByClientId();
	//
	//			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/" + taskID + "/log");
	//			request.addHeader("Authorization", "Bearer " + accessToken);
	//			request.addHeader("accept", "application/json");
	//
	//			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
	//			if (response.getHttpCode() == 200) {
	//				JsonNode contentJson;
	//				try {
	//					contentJson = JsonReader.readJsonItemString(response.getContent());
	//				} catch (final Exception e) {
	//					throw new Exception("Invalid Tasks JSON data", e);
	//				}
	//
	//				final JsonArray jsonArray = ((JsonArray) contentJson);
	//				final List<JsonObject> returnList = new ArrayList<>();
	//				for (final Object logItemObject : jsonArray.simpleItems()) {
	//					final JsonObject logItemJsonObject = (JsonObject) logItemObject;
	//					final String logContentString = (String) logItemJsonObject.getSimpleValue("content");
	//					final JsonObject logContent = (JsonObject) JsonReader.readJsonItemString(logContentString);
	//					returnList.add(logContent);
	//				}
	//				return returnList;
	//			} else {
	//				throw new Exception("getAllTasks failed. Http Code: " + response.getHttpCode());
	//			}
	//		} catch (final UnknownHostException e) {
	//			throw new Exception("UnknownHost '" + e.getMessage() + "'");
	//		}
	//	}

	//	public Map<Integer, TaskInstanceStatus> getStatusOfTaskInstances(final int taskID) throws Exception {
	//		try {
	//			final String accessToken = aquireAccessTokenByClientId();
	//
	//			final HttpRequest request = new HttpRequest(HttpMethod.GET, argoWfSchedulerBaseUrl + "/tasks/" + taskID);
	//			request.addHeader("Authorization", "Bearer " + accessToken);
	//			request.addHeader("accept", "application/json");
	//
	//			final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxyConfiguration.getProxy(request.getUrl()), trustManager);
	//			if (response.getHttpCode() == 200) {
	//				JsonNode contentJson;
	//				try {
	//					contentJson = JsonReader.readJsonItemString(response.getContent());
	//				} catch (final Exception e) {
	//					throw new Exception("Invalid Task JSON data", e);
	//				}
	//
	//				final JsonObject jsonObject = ((JsonObject) contentJson);
	//				final JsonArray taskInstances = (JsonArray) jsonObject.get("instances");
	//
	//				final Map<Integer, TaskInstanceStatus> returnMap = new LinkedHashMap<>();
	//				for (final Object taskInstanceObject : taskInstances) {
	//					final JsonObject taskInstanceJsonObject = (JsonObject) taskInstanceObject;
	//					returnMap.put((Integer) taskInstanceJsonObject.getSimpleValue("id"), getTaskInstanceStatus((Integer) taskInstanceJsonObject.getSimpleValue("id")));
	//				}
	//
	//				return returnMap;
	//			} else {
	//				throw new Exception("getTaskStatus failed. Http Code: " + response.getHttpCode());
	//			}
	//		} catch (final UnknownHostException e) {
	//			throw new Exception("UnknownHost '" + e.getMessage() + "'");
	//		}
	//	}
}

