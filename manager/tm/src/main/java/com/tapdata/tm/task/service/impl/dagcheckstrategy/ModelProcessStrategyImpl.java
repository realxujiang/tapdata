package com.tapdata.tm.task.service.impl.dagcheckstrategy;

import cn.hutool.core.date.DateUtil;
import com.tapdata.tm.commons.dag.nodes.DatabaseNode;
import com.tapdata.tm.commons.schema.Field;
import com.tapdata.tm.commons.schema.MetadataInstancesDto;
import com.tapdata.tm.commons.task.dto.TaskDto;
import com.tapdata.tm.commons.util.NoPrimaryKeyTableSelectType;
import com.tapdata.tm.config.security.UserDetail;
import com.tapdata.tm.message.constant.Level;
import com.tapdata.tm.metadatainstance.service.MetadataInstancesService;
import com.tapdata.tm.task.constant.DagOutputTemplateEnum;
import com.tapdata.tm.task.entity.TaskDagCheckLog;
import com.tapdata.tm.task.service.DagLogStrategy;
import com.tapdata.tm.utils.Lists;
import com.tapdata.tm.utils.MessageUtil;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component("modelProcessStrategy")
@Setter(onMethod_ = {@Autowired})
public class ModelProcessStrategyImpl implements DagLogStrategy {

    private final DagOutputTemplateEnum templateEnum = DagOutputTemplateEnum.MODEL_PROCESS_CHECK;

    private MetadataInstancesService metadataInstancesService;

    @Override
    public List<TaskDagCheckLog> getLogs(TaskDto taskDto, UserDetail userDetail, Locale locale) {
        String taskId = taskDto.getId().toHexString();
        long total = 0L;
        if (TaskDto.SYNC_TYPE_MIGRATE.equals(taskDto.getSyncType())) {
            LinkedList<DatabaseNode> databaseNodes = taskDto.getDag().getSourceNode();
            if (CollectionUtils.isEmpty(databaseNodes)) {
                return null;
            }
            DatabaseNode sourceNode = databaseNodes.getFirst();
            if ("expression".equals(sourceNode.getMigrateTableSelectType())) {
                List<MetadataInstancesDto> metaInstances = metadataInstancesService.findSourceSchemaBySourceId(sourceNode.getConnectionId(), null, userDetail, "original_name");
                if (CollectionUtils.isNotEmpty(metaInstances)) {
									Function<MetadataInstancesDto, Boolean> filterTableByNoPrimaryKey = Optional
										.of(NoPrimaryKeyTableSelectType.parse(sourceNode.getNoPrimaryKeyTableSelectType()))
										.map(type -> {
											switch (type) {
												case HasKeys:
													return (Function<MetadataInstancesDto, Boolean>) metadataInstancesDto -> {
														if (null != metadataInstancesDto.getFields()) {
															for (Field field : metadataInstancesDto.getFields()) {
																if (Boolean.TRUE.equals(field.getPrimaryKey())) return false;
															}
														}
														return true;
													};
												case NoKeys:
													return (Function<MetadataInstancesDto, Boolean>) metadataInstancesDto -> {
														if (null != metadataInstancesDto.getFields()) {
															for (Field field : metadataInstancesDto.getFields()) {
																if (Boolean.TRUE.equals(field.getPrimaryKey())) return true;
															}
														}
														return false;
													};
												default:
											}
											return null;
										}).orElse(metadataInstancesDto -> false);

                    total = metaInstances.stream()
											.map(metadataInstancesDto -> {
												if (filterTableByNoPrimaryKey.apply(metadataInstancesDto)) {
													return null;
												}
												return metadataInstancesDto.getOriginalName();
											})
                            .filter(originalName -> {
															if (null == originalName) {
																return false;
															} else if (StringUtils.isEmpty(sourceNode.getTableExpression())) {
                                    return false;
                                } else {
                                    return Pattern.matches(sourceNode.getTableExpression(), originalName);
                                }
                            })
                            .count();
                }
            } else {
                total = sourceNode.getTableNames().size();
            }

        } else {
            total = 1;
        }

        BigDecimal time = new BigDecimal(total).divide(new BigDecimal(50), 1, RoundingMode.HALF_UP);

        TaskDagCheckLog preLog = new TaskDagCheckLog();
        String preContent = MessageFormat.format(MessageUtil.getDagCheckMsg(locale, "MODEL_PROCESS_INFO_PRELOG"), total, time);
        preLog.setTaskId(taskId);
        preLog.setCheckType(templateEnum.name());
        preLog.setCreateAt(DateUtil.date());
        preLog.setCreateUser(userDetail.getUserId());
        preLog.setLog(preContent);
        preLog.setGrade(Level.INFO);

        return Lists.newArrayList(preLog);
    }
}
