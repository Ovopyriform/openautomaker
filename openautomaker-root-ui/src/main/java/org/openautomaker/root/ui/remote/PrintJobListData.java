package org.openautomaker.root.ui.remote;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author taldhous
 */
public class PrintJobListData
{
	public enum ListStatus {
		OK,
		NO_SUITABLE_JOBS,
		NO_JOBS,
		NO_MEDIA,
		NO_PRINTER,
		ERROR
	}

	@JsonIgnore
	private List<PrintJobData> jobs = new ArrayList<>();
	@JsonIgnore
	private ListStatus status = ListStatus.OK;

	public PrintJobListData()
	{
		// Jackson deserialization
	}

	@JsonProperty
	public ListStatus getStatus()
	{
		return status;
	}

	@JsonProperty
	public void setStatus(ListStatus status)
	{
		this.status = status;
	}

	@JsonProperty
	public List<PrintJobData> getJobs()
	{
		return jobs;
	}

	@JsonProperty
	public void setJobs(List<PrintJobData> jobs)
	{
		this.jobs = jobs;
	}
}
