package io.tapdata.modules.api.async.master;

/**
 * @author aplomb
 */
public interface AsyncJob {
	JobContext run(JobContext jobContext);
}