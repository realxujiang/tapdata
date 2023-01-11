package io.tapdata.quickapi.server;

import io.tapdata.common.api.APIResponse;
import io.tapdata.common.api.APIResponseInterceptor;
import io.tapdata.common.support.postman.PostManAnalysis;
import io.tapdata.common.support.postman.PostManApiContext;
import io.tapdata.common.support.postman.entity.ApiMap;
import io.tapdata.common.support.postman.util.ApiMapUtil;
import io.tapdata.entity.error.CoreException;
import io.tapdata.quickapi.common.QuickApiConfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class QuickAPIResponseInterceptor implements APIResponseInterceptor {
    private QuickApiConfig config;
    private PostManAnalysis invoker;
    public static QuickAPIResponseInterceptor create(QuickApiConfig config,PostManAnalysis invoker){
        return new QuickAPIResponseInterceptor().config(config).invoker(invoker);
    }
    public QuickAPIResponseInterceptor config(QuickApiConfig config){
        this.config = config;
        return this;
    }
    public QuickAPIResponseInterceptor invoker(PostManAnalysis invoker){
        this.invoker = invoker;
        return this;
    }

    @Override
    public APIResponse intercept(APIResponse response, String urlOrName, String method, Map<String, Object> params) {
        if( Objects.isNull(response) ) {
            throw new CoreException(String.format("Http request call failed, unable to get the request result: url or name [%s], method [%s].",urlOrName,method));
        }
        APIResponse interceptorResponse = response;
        ExpireHandel expireHandel = ExpireHandel.create(response, config.expireStatus(),config.tokenParams());
        if (expireHandel.builder()){
            PostManApiContext postManApiContext = invoker.apiContext();
            List<ApiMap.ApiEntity> apiEntities = ApiMapUtil.tokenApis(postManApiContext.apis());
            if ( !apiEntities.isEmpty() ){
                ApiMap.ApiEntity apiEntity = apiEntities.get(0);
                APIResponse tokenResponse = invoker.invoke(apiEntity.name(), apiEntity.method(), params,true);
                if (expireHandel.refreshComplete(tokenResponse,params)) {
                    //再调用
                    interceptorResponse = invoker.invoke(urlOrName, method, params,true);
                }
            }
        }
        return interceptorResponse;
    }
}