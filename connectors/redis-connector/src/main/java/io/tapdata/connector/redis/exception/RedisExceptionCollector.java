package io.tapdata.connector.redis.exception;

import io.tapdata.exception.TapPdkTerminateByServerEx;
import io.tapdata.kit.ErrorKit;
import redis.clients.jedis.exceptions.JedisDataException;

public class RedisExceptionCollector {

    private final static String pdkId = "redis";

    public void collectRedisServerUnavailable(Throwable cause) {
        if (cause instanceof JedisDataException && cause.getMessage().contains("LOADING Redis is loading")) {
            throw new TapPdkTerminateByServerEx(pdkId, ErrorKit.getLastCause(cause));
        }
    }

}
