package de.soderer.argonaut.helper;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import de.soderer.utilities.DateUtilities;

public class TaskInstanceStatus {
	private Integer taskID;
	private Integer taskInstanceID;
	private String workflowId;
	private ZonedDateTime created;
	private ZonedDateTime updated;
	private String status;
	private String logMessage;
	private TaskStatus taskStatus;

	public Integer getTaskID() {
		return taskID;
	}

	public TaskInstanceStatus setTaskID(final Integer taskID) {
		this.taskID = taskID;
		return this;
	}

	public Integer getTaskInstanceID() {
		return taskInstanceID;
	}

	public TaskInstanceStatus setTaskInstanceID(final Integer taskInstanceID) {
		this.taskInstanceID = taskInstanceID;
		return this;
	}

	public String getWorkflowId() {
		return workflowId;
	}

	public TaskInstanceStatus setWorkflowId(final String workflowId) {
		this.workflowId = workflowId;
		return this;
	}

	public ZonedDateTime getCreated() {
		return created;
	}

	public TaskInstanceStatus setCreated(final ZonedDateTime created) {
		this.created = created;
		return this;
	}

	public ZonedDateTime getUpdated() {
		return updated;
	}

	public TaskInstanceStatus setUpdated(final ZonedDateTime updated) {
		this.updated = updated;
		return this;
	}

	public String getStatus() {
		return status;
	}

	public TaskInstanceStatus setStatus(final String status) {
		this.status = status;
		return this;
	}

	public String getLogMessage() {
		return logMessage;
	}

	public TaskInstanceStatus setLogMessage(final String logMessage) {
		this.logMessage = logMessage;
		return this;
	}

	public TaskInstanceStatus setTaskStatus(final TaskStatus taskStatus) {
		this.taskStatus = taskStatus;
		return this;
	}

	public TaskStatus getTaskStatus() {
		return taskStatus;
	}

	@Override
	public String toString() {
		return "'" + workflowId + "' " + status + " at " + DateUtilities.formatDate(DateUtilities.ISO_8601_DATETIME_FORMAT_NO_TIMEZONE, updated.withZoneSameInstant(ZoneId.systemDefault())) + "\n";
	}
}
