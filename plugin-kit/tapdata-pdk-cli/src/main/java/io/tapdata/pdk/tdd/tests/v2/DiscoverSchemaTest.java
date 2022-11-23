package io.tapdata.pdk.tdd.tests.v2;

import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.pdk.apis.TapConnector;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.connector.target.CreateTableFunction;
import io.tapdata.pdk.apis.functions.connector.target.CreateTableV2Function;
import io.tapdata.pdk.apis.functions.connector.target.WriteRecordFunction;
import io.tapdata.pdk.cli.commands.TapSummary;
import io.tapdata.pdk.core.api.ConnectorNode;
import io.tapdata.pdk.tdd.core.PDKTestBase;
import io.tapdata.pdk.tdd.core.SupportFunction;
import io.tapdata.pdk.tdd.tests.support.TapAssert;
import io.tapdata.pdk.tdd.tests.support.TapGo;
import io.tapdata.pdk.tdd.tests.support.TapTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.tapdata.entity.simplify.TapSimplify.list;

@DisplayName("discoverSchema.test")//discoverSchema发现表， 必测方法
@TapGo(sort = 99,goTest = true,subTest = {DiscoverSchemaTestV2.class})//6
public class DiscoverSchemaTest extends PDKTestBase{
    @DisplayName("discoverSchema.discover")//用例1， 发现表
    @Test
    @TapTestCase(sort = 1)
    /**
     * 执行discoverSchema之后， 至少返回一张表， 表里有表名即为成功
     * 表里没有字段描述时， 报警告
     * 表里有字段， 但是字段的name或者dataType为空时， 报警告， 具体哪些字段有问题
     * */
    void discover(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            PDKTestBase.TestNode prepare = this.prepare(nodeInfo);
            try {
                super.connectorOnStart(prepare);
                Method testCase = super.getMethod("discover");
                prepare.recordEventExecute().testCase(testCase);

                ConnectorNode connectorNode = prepare.connectorNode();
                TapConnector connector = connectorNode.getConnector();
                TapConnectorContext connectorContext = connectorNode.getConnectorContext();
                List<TapTable> tables = new ArrayList<>();
                connector.discoverSchema(connectorContext,new ArrayList<>(),1000,consumer->{if(null!=consumer) tables.addAll(consumer);});

                //执行discoverSchema之后， 至少返回一张表
                TapAssert.asserts(()->
                        Assertions.assertFalse(tables.isEmpty(), TapSummary.format("discover.notAnyTable"))
                ).acceptAsWarn(
                    testCase,
                    TapSummary.format("discover.succeed",tables.size())
                );

                Map<String,Map<String,String>> warnFieldMap = new HashMap<>();
                tables.stream().forEach(table->{
                    if (null == table){
                        TapAssert.asserts(()->
                            Assertions.fail(TapSummary.format("discover.nullTable"))
                        ).error(testCase);
                        return;
                    }
                    //表里有表名即为成功
                    String tableName = table.getId();
                    if (null == tableName || "".equals(tableName)){
                        TapAssert.asserts(()->
                            Assertions.fail(TapSummary.format("discover.emptyTableName"))
                        ).error(testCase);
                        return;
                    }
                    //表里没有字段描述时，报警告
                    LinkedHashMap<String, TapField> nameFieldMap = table.getNameFieldMap();
                    TapAssert.asserts(()->
                        Assertions.assertTrue(
                            null != nameFieldMap && !nameFieldMap.isEmpty(),
                            TapSummary.format("discover.emptyTFields",tableName))
                    ).warn(testCase);

                    //表里有字段， 但是字段的name或者dataType为空时， 报警告， 具体哪些字段有问题
                    if (null != nameFieldMap && !nameFieldMap.isEmpty()){
                        for (Map.Entry<String, TapField> field : nameFieldMap.entrySet()) {
                            TapField value = field.getValue();
                            String name = value.getName();
                            String type = value.getDataType();
                            if ( null == name || "".equals(name) || null == type || "".equals(type) ){
                                Map<String, String> stringStringMap = warnFieldMap.computeIfAbsent(tableName, ts -> null == warnFieldMap.get(ts) ? new HashMap<>() : warnFieldMap.get(ts));
                                stringStringMap.put(name,type);
                            }
                        }
                    }
                });
                StringBuilder warn = new StringBuilder();
                final String starLine = "\n\t\t\t\t\t";
                warnFieldMap.forEach((table,value)->{
                    warn.append(starLine).append(table).append(": ");
                    value.forEach((name,type)->warn.append(starLine).append("\t").append(name).append(":\t").append(type));
                });

                TapAssert.asserts(()->
                    Assertions.assertTrue(
                        warnFieldMap.isEmpty(),
                        TapSummary.format("discover.hasWarnFields",warn.toString()))
                ).acceptAsWarn(testCase,TapSummary.format("discover.notWarnFields"));
            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                super.connectorOnStop(prepare);
            }
        });
    }

    @DisplayName("discoverSchema.discoverAfterCreate")//用例2， 建表之后能发现表（依赖CreateTableFunction）
    @Test
    @TapTestCase(sort = 2,dump = true)
    /**
     * 通过CreateTableFunction创建一张表， 表名随机，
     * 表里的字段属性是通过TapType的全类型11个字段推演得来，
     * 建表之后执行discoverySchema获得表列表，
     * 表列表里包含随机创建的表，
     * 且所有字段的name和dataType一致即为成功。
     * 验证结束之后需要删掉随机建的表（依赖DropTableFunction）
     * */
    void discoverAfterCreate(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            PDKTestBase.TestNode prepare = this.prepare(nodeInfo);
            RecordEventExecute execute = prepare.recordEventExecute();
            boolean hasCreateTable = false;
            try {
                super.connectorOnStart(prepare);
                Method testCase = super.getMethod("discoverAfterCreate");
                execute.testCase(testCase);

                //通过CreateTableFunction创建一张表， 表名随机，
                //表里的字段属性是通过TapType的全类型11个字段推演得来，
                if (! ( hasCreateTable =this.createTable(prepare) ) ) return;
                String tableIdTarget = targetTable.getId();

                //建表之后执行discoverySchema获得表列表，
                ConnectorNode connectorNode = prepare.connectorNode();
                TapConnector connector = connectorNode.getConnector();
                TapConnectorContext connectorContext = connectorNode.getConnectorContext();
                ConnectorFunctions connectorFunctions = connectorNode.getConnectorFunctions();
                List<TapTable> consumer = new ArrayList<>();
                long discoverStart = System.currentTimeMillis();
                connector.discoverSchema(connectorContext,list(tableIdTarget),1, con->{if (null!=con) consumer.addAll(con);});
                long discoverEnd = System.currentTimeMillis();
                //表列表里包含随机创建的表，
                TapAssert.asserts(()->
                    Assertions.assertTrue(
                    !consumer.isEmpty() &&
                            null != consumer.get(0) && targetTable.getId().equals(consumer.get(0).getId()),
                        TapSummary.format("discoverAfterCreate.notFindTargetTable",targetTable.getId(),discoverEnd-discoverStart))
                ).acceptAsError(
                    testCase,
                    TapSummary.format("discoverAfterCreate.fundTargetTable",targetTable.getId(),discoverEnd-discoverStart)
                );

                boolean hasTargetTable = !consumer.isEmpty() &&
                        null != consumer.get(0) &&
                        targetTable.getId().equals(consumer.get(0).getId());
                if (hasTargetTable){
                    //且所有字段的name和dataType一致即为成功。
                    TapTable tapTable = consumer.get(0);
                    String tableId = tapTable.getId();
                    LinkedHashMap<String, TapField> tapTableFieldMap = tapTable.getNameFieldMap();
                    LinkedHashMap<String, TapField> targetTableFieldMap = super.modelDeduction(connectorNode);//targetTable.getNameFieldMap();
                    if ( null == tapTableFieldMap || null == targetTableFieldMap){
                        TapAssert.asserts(()->Assertions.fail(TapSummary.format("discoverAfterCreate.exitsNullFiledMap",tableId))).error(testCase);
                        return;
                    }
                    int tapTableSize = tapTableFieldMap.size();
                    int targetTableSize = targetTableFieldMap.size();
                    try {
                        TapAssert.asserts(()->{
                            Assertions.assertTrue(
                                    tapTableSize>targetTableSize,
                                    TapSummary.format("discoverAfterCreate.fieldsNotEqualsCount",
                                    tapTableSize,
                                    targetTableSize
                                )
                            );
                        }).acceptAsWarn(
                            testCase,
                            TapSummary.format(
                                "discoverAfterCreate.fieldsEqualsCount",
                                tapTableSize,
                                targetTableSize
                            )
                        );
                    }catch (Exception ignored){ }

                    boolean hasSuchField = true;
                    Iterator<Map.Entry<String, TapField>> iterator = targetTableFieldMap.entrySet().stream().iterator();
                    String targetFieldItem = "";
                    String tapFieldItem = "";
                    while (iterator.hasNext()){
                        Map.Entry<String, TapField> next = iterator.next();
                        TapField field = next.getValue();
                        String name = field.getName();
                        String dataType = field.getDataType();

                        TapField tapField = tapTableFieldMap.get(name);
                        if (null == tapField){
                            hasSuchField = false;
                            targetFieldItem = "("+name+":"+dataType+")";
                            tapFieldItem = "null";
                            break;
                        }
                        String tapDataType = tapField.getDataType();
                        if (null == dataType && tapDataType == null) {
                            continue;
                        }
                        if ((null == dataType && tapDataType != null) || !dataType.equals(tapDataType)) {
                            hasSuchField = false;
                            targetFieldItem = "("+name+":"+dataType+")";
                            tapFieldItem = "("+name+":"+tapDataType+")";
                            break;
                        }
                    }
                    final boolean hasSuchFieldFinal = hasSuchField;
                    final String tapFieldItemFinal = tapFieldItem;
                    final String targetFieldItemFinal = targetFieldItem;
                    TapAssert.asserts(()->{
                        Assertions.assertTrue(
                            hasSuchFieldFinal,
                            TapSummary.format("discoverAfterCreate.allFieldNotEquals",tableId,targetFieldItemFinal,tapFieldItemFinal));
                    }).acceptAsWarn(
                        testCase,
                        TapSummary.format("discoverAfterCreate.allFieldEquals",tableId)
                    );
                }
            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                //验证结束之后需要删掉随机建的表（依赖DropTableFunction）
                if (hasCreateTable) execute.dropTable();
                super.connectorOnStop(prepare);
            }
        });
    }

    @DisplayName("discoverSchema.discoverByTableName1")//用例3， 通过指定表明加载特定表（依赖已经存在多表）
    @Test
    @TapTestCase(sort = 3)
    /**
     * 执行discoverSchema之后，
     * 发现有大于1张表的返回，
     * 通过指定第一张表之后的任意一张表名，
     * 通过List<String> tables参数指定那张表，
     * 通过Consumer<List<TapTable>> consumer返回了这一张且仅此一张表为成功。
     * 如果只有一张表， 直接通过此测试。
     * */
    void discoverByTableName1(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            PDKTestBase.TestNode prepare = this.prepare(nodeInfo);
            try {
                super.connectorOnStart(prepare);
                Method testCase = super.getMethod("discoverByTableName1");
                prepare.recordEventExecute().testCase(testCase);

                ConnectorNode connectorNode = prepare.connectorNode();
                TapConnector connector = connectorNode.getConnector();
                TapConnectorContext connectorContext = connectorNode.getConnectorContext();

                AtomicInteger tableCount = new AtomicInteger();
                //通过指定第一张表之后的任意一张表名，
                AtomicInteger nextTable = new AtomicInteger();
                AtomicReference<TapTable> tapTableAto = new AtomicReference<>();

                long discoverStart = System.currentTimeMillis();
                connector.discoverSchema(connectorContext,new ArrayList<>(),1000,consumer->{
                    long discoverEnd = System.currentTimeMillis();
                    //执行discoverSchema之后， 至少返回一张表
                    TapAssert.asserts(()->{
                        Assertions.assertTrue(
                            null!=consumer && !consumer.isEmpty() && consumer.size()>1,
                            TapSummary.format("discoverByTableName1.notAnyTable",discoverEnd-discoverStart));
                    }).acceptAsWarn(
                        testCase,
                        TapSummary.format("discoverByTableName1.succeed",consumer.size(),discoverEnd-discoverStart)
                    );
                    tableCount.set(consumer.size());
                    //通过指定第一张表之后的任意一张表名，
                    nextTable.set(((new Random()).nextInt(tableCount.get()-1)+1));
                    tapTableAto.set(consumer.get(nextTable.get()));
                });

                //通过List<String> tables参数指定那张表，
                TapTable tapTable = tapTableAto.get();
                List<String> tables = list(tapTable.getId());
                try {
                    //通过Consumer<List<TapTable>> consumer返回了这一张且仅此一张表为成功。
                    long discoverStart2 = System.currentTimeMillis();
                    connector.discoverSchema(connectorContext,tables,1000,c->{
                        long discoverEnd = System.currentTimeMillis();
                        TapAssert.asserts(()->{
                            Assertions.assertTrue(
                                null!=c && c.size()==1,
                                TapSummary.format("discoverByTableName1.notAnyTableAfter",tableCount,tapTable.getId(),discoverEnd-discoverStart2));
                        }).acceptAsWarn(
                            testCase,
                            TapSummary.format("discoverByTableName1.succeedAfter",tableCount,tapTable.getId(),c.size(),discoverEnd-discoverStart2)
                        );
                        //如果只有一张表， 直接通过此测试。
//                        TapAssert.asserts(()->
//                                Assertions.assertTrue(
//                                        c.size()==1,
//                                        TapSummary.format("discoverByTableName1.notTable",tableCount,tapTable.getId(),c.size())
//                                )
//                        ).acceptAsWarn(testCase,TapSummary.format("discoverByTableName1.succeedTable",tableCount,tapTable.getId(),c.size()));
                    });
                }catch (Throwable e){

                }
            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                super.connectorOnStop(prepare);
            }
        });

    }

    @DisplayName("discoverSchema.discoverByTableName2")//用例4， 通过指定表名加载特定表（依赖CreateTableFunction）
    @Test
    @TapTestCase(sort = 4,dump = true)
    /**
     * 通过CreateTableFunction另外创建一张表，
     * 通过List<String> tables参数指定新创建的那张表，
     * 通过Consumer<List<TapTable>> consumer返回了这一张且仅此一张表为成功。
     * 验证结束之后需要删掉随机建的表（依赖DropTableFunction）
     * */
    void discoverByTableName2(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            PDKTestBase.TestNode prepare = this.prepare(nodeInfo);
            RecordEventExecute execute = prepare.recordEventExecute();
            boolean hasCreatedTable = false;
            try {
                super.connectorOnStart(prepare);
                Method testCase = super.getMethod("discoverByTableName2");
                execute.testCase(testCase);

                //通过CreateTableFunction另外创建一张表，
                if (! ( hasCreatedTable = this.createTable(prepare) )) return;
                String tableIdTarget = targetTable.getId();

                //通过List<String> tables参数指定新创建的那张表，
                ConnectorNode connectorNode = prepare.connectorNode();
                TapConnector connector = connectorNode.getConnector();
                TapConnectorContext connectorContext = connectorNode.getConnectorContext();
                List<TapTable> consumer = new ArrayList<>();
                //通过Consumer<List<TapTable>> consumer返回了这一张且仅此一张表为成功。
                long discoverStart = System.currentTimeMillis();
                connector.discoverSchema(connectorContext,list(tableIdTarget),1000, consumer::addAll);
                long discoverEnd = System.currentTimeMillis();
                TapAssert.asserts(()->{
                    Assertions.assertFalse(
                         !consumer.isEmpty() && consumer.size()!=1,
                        TapSummary.format("discoverByTableName2.notAnyTable",tableIdTarget,null==consumer?0:consumer.size(),discoverEnd-discoverStart));
                }).acceptAsError(
                    testCase,
                    TapSummary.format("discoverByTableName2.succeed",tableIdTarget,null==consumer?0:consumer.size(),discoverEnd-discoverStart)
                );
                TapTable tapTable = consumer.get(0);
                TapAssert.asserts(()->{
                    Assertions.assertTrue(
                        tableIdTarget.equals(tapTable.getId()),
                        TapSummary.format("discoverByTableName2.notEqualsTable",tableIdTarget,tapTable.getId(),discoverEnd-discoverStart));
                }).acceptAsError(
                    testCase,
                    TapSummary.format("discoverByTableName2.equalsTable",tableIdTarget,tapTable.getId(),discoverEnd-discoverStart)
                );


            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                //验证结束之后需要删掉随机建的表（依赖DropTableFunction）
                if(hasCreatedTable) execute.dropTable();
                super.connectorOnStop(prepare);
            }
        });

    }

    @DisplayName("discoverSchema.discoverByTableCount1")//用例5， 通过指定表数量加载固定数量的表（依赖已经存在多表）
    @Test
    @TapTestCase(sort = 5)
    /**
     * 执行discoverSchema之后，
     * 发现有大于1张表的返回，
     * 通过int tableSize参数指定为1，
     * 通过Consumer<List<TapTable>> consumer返回了一张表为成功。
     * 如果只有一张表， 直接通过此测试。
     * */
    void discoverByTableCount1(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            PDKTestBase.TestNode prepare = this.prepare(nodeInfo);
            try {
                super.connectorOnStart(prepare);
                Method testCase = super.getMethod("discoverByTableCount1");
                prepare.recordEventExecute().testCase(testCase);

                ConnectorNode connectorNode = prepare.connectorNode();
                TapConnector connector = connectorNode.getConnector();
                TapConnectorContext connectorContext = connectorNode.getConnectorContext();
                List<TapTable> consumer = new ArrayList<>();
                long discoverStart = System.currentTimeMillis();
                connector.discoverSchema(connectorContext,new ArrayList<>(),1000,con->{if(con != null) consumer.addAll(con);});
                long discoverEnd = System.currentTimeMillis();
                //执行discoverSchema之后， 发现有大于1张表的返回，
                TapAssert.asserts(()->{
                    Assertions.assertTrue(
                        !consumer.isEmpty() && consumer.size()>1,
                        TapSummary.format("discoverByTableCount1.notAnyTable",discoverEnd-discoverStart));
                }).acceptAsWarn(
                    testCase,
                    TapSummary.format("discoverByTableCount1.succeed",consumer.size(),discoverEnd-discoverStart)
                );

                //通过int tableSize参数指定为1，
                final int tableCount = 1;
                //通过Consumer<List<TapTable>> consumer返回了一张表为成功。
                List<TapTable> consumer2 = new ArrayList<>();
                long discoverStart2 = System.currentTimeMillis();
                connector.discoverSchema(connectorContext,list(),tableCount,con->{if (null!=con) consumer2.addAll(con);});
                long discoverEnd2 = System.currentTimeMillis();
                //如果只有一张表， 直接通过此测试。
                TapAssert.asserts(()->
                    Assertions.assertEquals(
                        tableCount,
                        consumer2.size(),
                        TapSummary.format(
                            "discoverByTableCount1.notTable",
                            tableCount,
                            tableCount,
                            consumer2.size(),
                            discoverEnd2 - discoverStart2
                        )
                    )
                ).acceptAsError(testCase,TapSummary.format("discoverByTableCount1.succeedTable",tableCount,consumer2.size(),discoverEnd2-discoverStart2));
            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                super.connectorOnStop(prepare);
            }
        });

    }

    @DisplayName("discoverSchema.discoverByTableCount2")//用例6， 通过指定表数量加载固定数量的表（依赖CreateTableFunction）
    @Test
    @TapTestCase(sort = 6,dump = true)
    /**
     * 通过CreateTableFunction另外创建一张表，
     * 通过int tableSize参数指定为1，
     * 通过Consumer<List<TapTable>> consumer返回了一张表为成功。
     * 验证结束之后需要删掉随机建的表（依赖DropTableFunction）
     * */
    void discoverByTableCount2(){
        super.consumeQualifiedTapNodeInfo(nodeInfo -> {
            PDKTestBase.TestNode prepare = this.prepare(nodeInfo);
            RecordEventExecute execute = prepare.recordEventExecute();
            boolean hasCreateTable = false;
            try {
                super.connectorOnStart(prepare);
                Method testCase = super.getMethod("discoverByTableCount2");
                execute.testCase(testCase);

                ConnectorNode connectorNode = prepare.connectorNode();
                TapConnector connector = connectorNode.getConnector();
                TapConnectorContext connectorContext = connectorNode.getConnectorContext();

                //通过CreateTableFunction另外创建一张表，
                if (! ( hasCreateTable =this.createTable(prepare) )) return;
                String targetTableId = targetTable.getId();

                //通过int tableSize参数指定为1，
                int tableCount = 1;
                try {
                    long discoverStart = System.currentTimeMillis();
                    connector.discoverSchema(connectorContext,new ArrayList<>(),tableCount,c->{
                        //通过Consumer<List<TapTable>> consumer返回了一张表为成功。
                        //如果只有一张表， 直接通过此测试。
                        long discoverEnd = System.currentTimeMillis();
                        TapAssert.asserts(()->
                            Assertions.assertTrue(
                                null!=c && c.size() == tableCount,
                                TapSummary.format(
                                    "discoverByTableCount2.error",
                                    targetTableId,
                                    tableCount,
                                    null!=c?c.size():0
                                    ,discoverEnd-discoverStart
                                )
                            )
                        ).acceptAsWarn(
                            testCase,
                            TapSummary.format(
                                "discoverByTableCount2.succeed",
                                targetTableId,
                                tableCount,
                                c.size(),
                                discoverEnd-discoverStart
                            )
                        );
                        throw new RuntimeException("Stop test consumer");
                    });
                }catch (RuntimeException e){
                    String msg = e.getMessage();
                    if (!"Stop test consumer".equals(msg)){
                        throw e;
                    }
                }

            }catch (Throwable e) {
                throw new RuntimeException(e);
            }finally {
                if(hasCreateTable) execute.dropTable();
                super.connectorOnStop(prepare);
            }
        });

    }

    public static List<SupportFunction> testFunctions() {
        return list(
                supportAny(list(
                        WriteRecordFunction.class, CreateTableFunction.class, CreateTableV2Function.class
                ), TapSummary.format(anyOneFunFormat,"WriteRecordFunction,CreateTableFunction,CreateTableV2Function"))
//                support(DropTableFunction.class, TapSummary.format(inNeedFunFormat,"DropTableFunction"))
        );
    }
}
