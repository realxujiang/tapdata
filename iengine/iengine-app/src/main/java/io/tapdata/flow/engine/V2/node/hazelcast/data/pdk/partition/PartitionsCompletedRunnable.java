package io.tapdata.flow.engine.V2.node.hazelcast.data.pdk.partition;

import io.tapdata.aspect.BatchReadFuncAspect;
import io.tapdata.aspect.DataFunctionAspect;
import io.tapdata.async.master.AsyncParallelWorker;
import io.tapdata.entity.aspect.AspectManager;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.flow.engine.V2.node.hazelcast.data.pdk.HazelcastSourcePartitionReadDataNode;
import io.tapdata.pdk.apis.partition.ReadPartition;

import java.util.List;
import java.util.Map;

/**
 * @author aplomb
 */
public class PartitionsCompletedRunnable implements Runnable {
	private TapTable tapTable;
	private AsyncParallelWorker partitionsReader;
	private AspectManager aspectManager;
	private BatchReadFuncAspect batchReadFuncAspect;
	private List<ReadPartition> readPartitionList;
	private HazelcastSourcePartitionReadDataNode sourcePdkDataNodeEx1;
	public PartitionsCompletedRunnable(TapTable tapTable, AsyncParallelWorker partitionsReader, AspectManager aspectManager, BatchReadFuncAspect batchReadFuncAspect, List<ReadPartition> readPartitionList, HazelcastSourcePartitionReadDataNode sourcePdkDataNodeEx1) {
		this.tapTable = tapTable;
		this.partitionsReader = partitionsReader;
		this.aspectManager = aspectManager;
		this.batchReadFuncAspect = batchReadFuncAspect;
		this.readPartitionList = readPartitionList;
		this.sourcePdkDataNodeEx1 = sourcePdkDataNodeEx1;
	}
	@Override
	public void run() {
		sourcePdkDataNodeEx1.getObsLogger().info("Partitions has been split for table {}, wait until all partitions has been read.", tapTable.getId());
		Object batchOffsetObj = sourcePdkDataNodeEx1.getSyncProgress().getBatchOffsetObj();
		PartitionTableOffset partitionTableOffset = null;
		if(batchOffsetObj instanceof Map) {
			partitionTableOffset = (PartitionTableOffset) ((Map<?, ?>) batchOffsetObj).get(tapTable.getId());
			if(partitionTableOffset == null) {
				partitionTableOffset = new PartitionTableOffset();
				partitionTableOffset.partitions(readPartitionList);
				((Map<String, PartitionTableOffset>) batchOffsetObj).put(tapTable.getId(), partitionTableOffset);
			} else {
				partitionTableOffset.partitions(readPartitionList);
			}
		}
		while(!partitionsReader.runningQueueWorkers().isEmpty() || !partitionsReader.pendingQueueWorkers().isEmpty()) {
			try {
				Thread.sleep(500L);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		if(partitionTableOffset != null) {
			partitionTableOffset.setTableCompleted(true);
			partitionTableOffset.setPartitions(null);
			partitionTableOffset.setCompletedPartitions(null);
		}

		aspectManager.executeAspect(batchReadFuncAspect.state(DataFunctionAspect.STATE_END));
		//partition split done and read partitions done, start entering CDC stage.
		sourcePdkDataNodeEx1.handleEnterCDCStage(partitionsReader, tapTable);
	}
}
