package io.tapdata.js.connector.base;

public class TapConfigContext {
    private Long streamReadIntervalSecond = 300L;

    public Long getStreamReadIntervalSeconds(){
        return this.streamReadIntervalSecond;
    }

    public void setStreamReadIntervalSeconds(Object streamReadIntervalSecond){
        if (streamReadIntervalSecond instanceof Number){
            this.streamReadIntervalSecond = ((Number)streamReadIntervalSecond).longValue();
        }
    }
}
