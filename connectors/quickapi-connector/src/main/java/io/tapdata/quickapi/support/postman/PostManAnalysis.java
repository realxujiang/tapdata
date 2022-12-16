package io.tapdata.quickapi.support.postman;

import cn.hutool.json.JSONUtil;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.logger.TapLogger;
import io.tapdata.pdk.apis.api.APIInvoker;
import io.tapdata.pdk.apis.api.APIResponse;
import io.tapdata.pdk.apis.api.APIResponseInterceptor;
import io.tapdata.pdk.apis.api.comom.APIDocument;
import io.tapdata.quickapi.core.annotation.ApiType;
import io.tapdata.quickapi.support.postman.entity.ApiEvent;
import io.tapdata.quickapi.support.postman.entity.ApiInfo;
import io.tapdata.quickapi.support.postman.entity.ApiMap;
import io.tapdata.quickapi.support.postman.entity.ApiVariable;
import io.tapdata.quickapi.support.postman.entity.params.Api;
import io.tapdata.quickapi.support.postman.entity.params.Body;
import io.tapdata.quickapi.support.postman.entity.params.Header;
import io.tapdata.quickapi.support.postman.entity.params.Url;
import io.tapdata.quickapi.support.postman.enums.PostParam;
import io.tapdata.quickapi.core.emun.TapApiTag;

import io.tapdata.quickapi.support.postman.util.ReplaceTagUtil;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@ApiType
public class PostManAnalysis
        implements APIDocument<APIInvoker>, APIInvoker{
    private final boolean filterUselessApi = false;//是否过滤没有被标记的api

    private final String sourcePath = "/resources/api/apiJson.json";//默认的API JSON 存放位置。

    private static final String TAG = PostManAnalysis.class.getSimpleName();

    PostManApiContext apiContext;

    public PostManApiContext apiContext(){
        return this.apiContext;
    }

    public static PostManAnalysis create(){
        return new PostManAnalysis();
    }


    Map<String, Object> getMap(String key,Map<String,Object> collection){
        Object infoObj = collection.get(key);
        return (null != infoObj && infoObj instanceof Map)?((Map<String,Object>)infoObj):null;
    }
    List<Object> getList(String key, Map<String,Object> collection){
        Object infoObj = collection.get(key);
        return (null != infoObj && infoObj instanceof Collection)?(List<Object>)infoObj:null;
    }

    Map<String,List<Object>> item(List<Object> item){
        if (null == item) return null;
        List<Object> list = new ArrayList<>();
        put(list,item);

        Map<String, List<Object>> listMap = list.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(obj -> {
            Map<String, Object> request = (Map<String, Object>) ((Map<String, Object>) obj).get(PostParam.REQUEST);
            Object urlObj = request.get(PostParam.URL);
            String url = "UN_KNOW_URL";
            if (null != urlObj){
                if (urlObj instanceof String){
                    url = (String) urlObj;
                }else if (urlObj instanceof Map){
                    url = String.valueOf(((Map<String,Object>) urlObj).get(PostParam.RAW));
                }
            }
            return ReplaceTagUtil.replace(url);
        }));
        return listMap;
    }

    void put(List<Object> list,Object obj){
        if (null == obj) return;
        if (obj instanceof Map){
            Map<String,Object> map = (Map<String, Object>) obj;
            if (isMapFromItem(map)){
                list.add(map);
            }else{
                map.forEach( (key,value) -> put(list,value) );
            }
        }else if (obj instanceof Collection){
            Collection<Object> list1 = (Collection<Object>) obj;
            for (Object it : list1) {
                if (null == it) continue;
                put(list,it);
            }
        }
    }

    boolean isMapFromItem(Object mapObj){
        if (null != mapObj && mapObj instanceof Map){
            Map<String,Object> map = (Map<String, Object>) mapObj;
            Object request = map.get(PostParam.REQUEST);
            return null != request;
        }
        return false;
    }

    @Override
    public APIInvoker analysis(String apiJson,String type, Map<String, Object> params) {
        Map<String, Object> json = null;
        if (Objects.nonNull(apiJson)) {
            json = JSONUtil.parseObj(apiJson);
        }else {
            try {
                json = JSONUtil.readJSONObject(new File(sourcePath),StandardCharsets.UTF_8);
            }catch (Throwable ignored){
                json = null;
            }
            if (Objects.isNull(json)){
                TapLogger.error(TAG,"Can't analysis api JSON document, please save this JSON file into " + sourcePath + ". ");
            }
        }

        List<Object> item = this.getList(PostParam.ITEM, json);
        Map<String, List<Object>> listMap = this.item(item);
        ApiMap apiMap = ApiMap.create();
        listMap.forEach((key,apiItem)->{
            apiItem.stream().filter(api->{
                if (null == api) return false;
                if (!filterUselessApi) return true;
                try {
                    Map<String,Object> apiDetail = (Map<String,Object>)api;
                    String name = (String)apiDetail.get(PostParam.NAME);
                    return TapApiTag.isLabeled(name);
                }catch (Exception e){
                    return false;
                }
            }).forEach(api->{
                try {
                    Map<String,Object> apiDetail = (Map<String,Object>)api;
                    String name = (String)apiDetail.get(PostParam.NAME);
                    Map<String,Object> request = (Map<String,Object>) apiDetail.get(PostParam.REQUEST);
                    Object urlObj = request.get(PostParam.URL);
                    String url = null;
                    if (urlObj instanceof String){
                        url = (String) urlObj;
                    }else if(urlObj instanceof Map){
                        url = (String) ((Map<String,Object>)urlObj).get(PostParam.RAW);
                    }
                    String method = (String)request.get(PostParam.METHOD);
                    apiMap.add(ApiMap.ApiEntity.create(url,name,ApiMap.ApiEntity.generateApiEntity(apiDetail)).method(method));
                }catch (Exception ignored){ }
            });
        });

        if (Objects.isNull(this.apiContext)){
            this.apiContext = PostManApiContext.create();
        }
        this.apiContext.info(ApiInfo.create(this.getMap(PostParam.INFO,json)))
                .event(ApiEvent.create(this.getList(PostParam.EVENT,json)))
                .variable(ApiVariable.create(this.getList(PostParam.VARIABLE,json)))
                .apis(apiMap)
                .table(
                    apiMap.stream()
                    .filter(entity -> Objects.nonNull(entity) && Objects.nonNull(entity.name()) && TapApiTag.isTableName(entity.name()))
                    .map(entity -> TapApiTag.analysisTableName(entity.name()) ).collect(Collectors.toList())
                );
        if ( Objects.nonNull(params) && !params.isEmpty() ) {
            this.apiContext.variableAdd(params);
        }
        return this;
    }

    public Request httpPrepare(String uriOrName, String method, Map<String, Object> params) {
        ApiMap.ApiEntity api = this.apiContext.apis().quickGet(uriOrName, method);
        if (Objects.isNull(api)) {
            throw new CoreException(String.format("No such api name or url is [%s],method is [%s]",uriOrName,method));
        }

        ApiVariable variable = ApiVariable.create();
        variable.putAll(this.apiContext.variable());
        if(Objects.nonNull(params) && !params.isEmpty()) {
            variable.putAll(params);
        }

        //对url、body、header中的属性进行替换
        Api assignmentApi = api.variableAssignment(variable);

        //封装http
        io.tapdata.quickapi.support.postman.entity.params.Request apiRequest = assignmentApi.request();
        Url apiUrl = apiRequest.url();
        String apiMethod = apiRequest.method();
        List<Header> apiHeader = apiRequest.header();
        Map<String,String> headMap = new HashMap<>();
        if (Objects.nonNull(apiHeader) && !apiHeader.isEmpty()){
            apiHeader.stream().filter(Objects::nonNull).forEach(head -> headMap.put(head.key(),head.value()));
        }
        Body apiBody = apiRequest.body();
        MediaType mediaType = MediaType.parse("application/json");
        Map<String,Object> bodyMap = JSONUtil.parseObj(apiBody.raw());
        List<Map<String, Object>> query = apiUrl.query();
        for (Map<String, Object> queryMap : query) {
            String key = String.valueOf(queryMap.get(PostParam.KEY));
            //Object value = queryMap.get(PostParam.VALUE);
            Object value = params.get(key);
            queryMap.put(PostParam.VALUE,value);
            String desc = String.valueOf(queryMap.get(PostParam.DESCRIPTION));
            if(TapApiTag.isTapPageParam(desc)){
                bodyMap.put(key,value);
            }
        }
        RequestBody body = RequestBody.create(mediaType, JSONUtil.toJsonStr(bodyMap));
        Request request = new Request.Builder()
                .url(apiUrl.raw())
                .method(apiMethod, body)
                .headers(Headers.of(headMap))
                .addHeader("Content-Type", "application/json")
                .build();
        return request;
    }
    public APIResponse http(Request request) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder().build();
        Response response = client.newCall(request).execute();
        return APIResponse.create().httpCode(response.code())
                .result(JSONUtil.parseObj(response.body().string()))
                .headers(JSONUtil.parseObj(response.headers()));
    }
    public APIResponse http(String uriOrName, String method, Map<String, Object> params) {
        try {
            return this.http(this.httpPrepare(uriOrName, method, params));
        } catch (IOException e) {
            throw new CoreException(String.format("Http request falid ,the api name or url is [%s],method is [%s], error message : %s",uriOrName,method,e.getMessage()));
        }
    }

    @Override
    public APIResponse invoke(String uriOrName, String method, Map<String, Object> params) {
        APIResponse response = this.http(uriOrName, method, params);
        response = this.interceptor.intercept(response,uriOrName,method,params);
        return response;
    }

//    @Override
//    public APIResponse intercept(APIResponse response, String urlOrName, String method, Map<String, Object> params) {
//        if( Objects.isNull(response) ) {
//            throw new CoreException(String.format("Http request call failed, unable to get the request result: url or name [%s], method [%s].",urlOrName,method));
//        }
//        APIResponse interceptorResponse = response;
//        if (ExpireHandel.create(response,this).builder()){
//            interceptorResponse = this.http(urlOrName,method,params);
//        }
//        return interceptorResponse;
//    }

    APIResponseInterceptor interceptor;
    @Override
    public void setAPIResponseInterceptor(APIResponseInterceptor interceptor) {
        this.interceptor = interceptor;
    }
}
