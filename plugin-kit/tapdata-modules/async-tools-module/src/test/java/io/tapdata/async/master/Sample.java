package io.tapdata.async.master;

import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.utils.InstanceFactory;
import io.tapdata.modules.api.async.master.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author aplomb
 */
public class Sample {
	public static void main(String[] args) {
		AsyncMaster asyncMaster = InstanceFactory.instance(AsyncMaster.class);
		asyncMaster.start();


		AsyncParallelWorker parallelWorker = asyncMaster.createAsyncParallelWorker("", 1);
		parallelWorker.start("", JobContext.create(""));
		parallelWorker.start("", JobContext.create(""));
		parallelWorker.start("", JobContext.create(""));
		parallelWorker.start("", JobContext.create(""));



		AsyncQueueWorker asyncQueueWorker = asyncMaster.createAsyncQueueWorker("");
		AsyncJobChain asyncJobChain = asyncMaster.createAsyncJobChain();
		asyncJobChain.add("batchRead", (jobContext) -> {
			String id = jobContext.getId();
			List<TapEvent> eventList = (List<TapEvent>) jobContext.getResult();
			//batch read
//			List<TapEvent> eventList = new ArrayList<>();
			jobContext.foreach(eventList, event -> {

				return null;
			});
			Map<String, TapField> fieldMap = new HashMap<>();
			jobContext.foreach(fieldMap, stringTapFieldEntry -> {
				stringTapFieldEntry.getKey();
				return null;
			});
			jobContext.runOnce(() -> {
				eventList.add(null);
			});
			if(true)
				return JobContext.create("").jumpToId("streamRead");
			else
				return JobContext.create("");
		}).add("streamRead", (jobContext) -> {
			Object o = jobContext.getResult();
			//stream read
			return JobContext.create("");
		});

		asyncQueueWorker.add(asyncJobChain);
		asyncQueueWorker.cancelAll().add("doSomeThing", (jobContext) -> {
			//Do something.
			return null;
		}).add(asyncJobChain);
		asyncQueueWorker.start(JobContext.create("").context(new Object()));
	}
}