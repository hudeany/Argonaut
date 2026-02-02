package de.soderer.argonaut.helper;

import java.time.ZonedDateTime;
import java.util.Map;

public class TaskStatus {
	private Integer taskID;
	private String taskName;
	private String workflowName;
	private ZonedDateTime created;
	private ZonedDateTime updated;
	private Map<Integer, TaskInstanceStatus> instances;
	private Map<String, String> parameters;

	public Integer getTaskID() {
		return taskID;
	}

	public TaskStatus setTaskID(final Integer taskID) {
		this.taskID = taskID;
		return this;
	}

	public String getTaskName() {
		return taskName;
	}

	public TaskStatus setTaskName(final String taskName) {
		this.taskName = taskName;
		return this;
	}

	public String getWorkflowName() {
		return workflowName;
	}

	public TaskStatus setWorkflowName(final String workflowName) {
		this.workflowName = workflowName;
		return this;
	}

	public ZonedDateTime getCreated() {
		return created;
	}

	public TaskStatus setCreated(final ZonedDateTime created) {
		this.created = created;
		return this;
	}

	public ZonedDateTime getUpdated() {
		return updated;
	}

	public TaskStatus setUpdated(final ZonedDateTime updated) {
		this.updated = updated;
		return this;
	}

	public Map<Integer, TaskInstanceStatus> getInstances() {
		return instances;
	}

	public TaskStatus setInstances(final Map<Integer, TaskInstanceStatus> instances) {
		this.instances = instances;
		return this;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public TaskStatus setParameters(final Map<String, String> parameters) {
		this.parameters = parameters;
		return this;
	}
}
