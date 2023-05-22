package com.tapdata.tm.ds.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PdkVersionCheckDto {
    private String pdkId;
    private String pdkHash;
    private String pdkVersion;
    private String gitBuildTime;
    private boolean isLatest;
}
