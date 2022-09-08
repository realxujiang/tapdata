package com.tapdata.tm.externalStorage.dto;

import com.tapdata.tm.commons.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * External Storage
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ExternalStorageDto extends BaseDto {
	private String name;
	private String type;
    private String uri;
    private String table;
    private Integer ttlDay;
	private boolean canEdit = true;
	private boolean canDelete = true;
	private boolean defaultStorage = false;
}