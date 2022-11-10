package io.tapdata.flow.engine.V2.script;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import com.tapdata.constant.BeanUtil;
import com.tapdata.constant.JobUtil;
import com.tapdata.entity.JavaScriptFunctions;
import com.tapdata.mongo.ClientMongoOperator;
import com.tapdata.processor.ScriptUtil;
import com.tapdata.processor.constant.JSEngineEnum;
import io.tapdata.Application;
import io.tapdata.entity.script.ScriptOptions;
import io.tapdata.pdk.apis.error.NotSupportedException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import javax.script.*;
import java.io.Reader;
import java.util.List;
import java.util.concurrent.Callable;

public class TapJavaScriptEngine implements ScriptEngine, Invocable {

    private final ScriptEngine scriptEngine;
    private final Invocable invocable;
    private final String buildInScript;

    public TapJavaScriptEngine(ScriptOptions scriptOptions) {
        ClientMongoOperator clientMongoOperator = BeanUtil.getBean(ClientMongoOperator.class);
        List<JavaScriptFunctions> javaScriptFunctions = JobUtil.getJavaScriptFunctions(clientMongoOperator);
        this.buildInScript = ScriptUtil.initBuildInMethod(javaScriptFunctions, clientMongoOperator);
        this.scriptEngine = initScriptEngine(scriptOptions.getEngineName());
        invocable = (Invocable) scriptEngine;
    }

    private ScriptEngine initScriptEngine(String jsEngineName) {
        JSEngineEnum jsEngineEnum = JSEngineEnum.getByEngineName(jsEngineName);
        ScriptEngine scriptEngine;
        if (jsEngineEnum == JSEngineEnum.GRAALVM_JS) {
            //need to change as engine classLoader
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(Application.class.getClassLoader());
            try {
                scriptEngine = GraalJSScriptEngine
                        .create(null,
                                Context.newBuilder("js")
                                        .allowAllAccess(true)
                                        .allowHostAccess(HostAccess.newBuilder(HostAccess.ALL)
                                                .targetTypeMapping(Value.class, Object.class
                                                        , v -> v.hasArrayElements() && v.hasMembers()
                                                        , v -> v.as(List.class)
                                                ).build()
                                        )
                        );
            } finally {
                //return pdk classLoader
                Thread.currentThread().setContextClassLoader(classLoader);
            }

        } else {
            scriptEngine = new ScriptEngineManager().getEngineByName(jsEngineEnum.getEngineName());
        }
        return scriptEngine;
    }

    @Override
    public Object eval(String script, ScriptContext context) {
        return applyClassLoaderContext(() -> scriptEngine.eval(combineFunctions(script), context));
    }

    //merge customize functions
    private String combineFunctions(String script) {
        return buildInScript + "\n" + script;
    }

    public Object applyClassLoaderContext(Callable<?> callable) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Application.class.getClassLoader());
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) {
        throw new NotSupportedException();
    }

    @Override
    public Object eval(String script) {
        return applyClassLoaderContext(() -> scriptEngine.eval(combineFunctions(script)));
    }

    @Override
    public Object eval(Reader reader) {
        throw new NotSupportedException();
    }

    @Override
    public Object eval(String script, Bindings n) {
        return applyClassLoaderContext(() -> scriptEngine.eval(combineFunctions(script), n));
    }

    @Override
    public Object eval(Reader reader, Bindings n) {
        throw new NotSupportedException();
    }

    @Override
    public void put(String key, Object value) {
        scriptEngine.put(key, value);
    }

    @Override
    public Object get(String key) {
        return scriptEngine.get(key);
    }

    @Override
    public Bindings getBindings(int scope) {
        return scriptEngine.getBindings(scope);
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {
        scriptEngine.setBindings(bindings, scope);
    }

    @Override
    public Bindings createBindings() {
        return scriptEngine.createBindings();
    }

    @Override
    public ScriptContext getContext() {
        return scriptEngine.getContext();
    }

    @Override
    public void setContext(ScriptContext context) {
        scriptEngine.setContext(context);
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return scriptEngine.getFactory();
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
        return invocable.invokeMethod(thiz, name, args);
    }

    @Override
    public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
        return invocable.invokeFunction(name, args);
    }

    @Override
    public <T> T getInterface(Class<T> clasz) {
        return invocable.getInterface(clasz);
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz) {
        return invocable.getInterface(thiz, clasz);
    }
}
