package com._4paradigm.dataimporter.initialization;

import com._4paradigm.rtidb.client.TableSyncClient;
import com._4paradigm.rtidb.client.ha.RTIDBClientConfig;
import com._4paradigm.rtidb.client.ha.impl.NameServerClientImpl;
import com._4paradigm.rtidb.client.ha.impl.RTIDBClusterClient;
import com._4paradigm.rtidb.client.impl.TableSyncClientImpl;
import com._4paradigm.rtidb.client.schema.ColumnDesc;
import com._4paradigm.rtidb.client.schema.ColumnType;
import com._4paradigm.rtidb.ns.NS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class InitClient {
    private static Logger logger = LoggerFactory.getLogger(InitClient.class);

    private static final String ZKENDPOINTS = Constant.ZKENDPOINTS;
    private static final String ZKROOTPATH = Constant.ZKROOTPATH;

    // 下面这几行变量定义不需要改
    // NameServerClientImpl要么做成单例, 要么用完之后就调用close, 否则会导致fd泄露
    private static NameServerClientImpl nsc = new NameServerClientImpl(ZKENDPOINTS, ZKROOTPATH + "/leader");
    private static RTIDBClientConfig config = new RTIDBClientConfig();
    public static final int MAX_THREAD_NUM = Constant.MAX_THREAD_NUM;
    private static final int REPLICA_NUM = Constant.REPLICA_NUM;
    private static final int PARTITION_NUM = Constant.PARTITION_NUM;
    private static final long TTL = Constant.TTL;


    private static TableSyncClient[] tableSyncClient = new TableSyncClient[MAX_THREAD_NUM];
    private static RTIDBClusterClient[] clusterClient = new RTIDBClusterClient[MAX_THREAD_NUM];

    /**
     * 初始化客户端
     */
    public static void initClient() {
        try {
            nsc.init();
            config.setZkEndpoints(ZKENDPOINTS);
            config.setZkRootPath(ZKROOTPATH);
            //初始化最大线程个数的client
            for (int i = 0; i < MAX_THREAD_NUM; i++) {
                clusterClient[i] = new RTIDBClusterClient(config);
                clusterClient[i].init();
                tableSyncClient[i] = new TableSyncClientImpl(clusterClient[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createSchemaTable(String tableName, List<ColumnDesc> schemaList) {
        NS.TableInfo.Builder builder = NS.TableInfo.newBuilder()
                .setName(tableName)  // 设置表名
                .setReplicaNum(REPLICA_NUM)    // 设置副本数. 此设置是可选的, 默认为3
                .setPartitionNum(PARTITION_NUM)  // 设置分片数. 此设置是可选的, 默认为16
                //.setCompressType(NS.CompressType.kSnappy) // 设置数据压缩类型. 此设置是可选的默认为不压缩
                //.setTtlType("kLatestTime")  // 设置ttl类型. 此设置是可选的, 默认为"kAbsoluteTime"按时间过期
                .setTtl(TTL);      // 设置ttl. 如果ttl类型是kAbsoluteTime, 那么ttl的单位是分钟.
        for (ColumnDesc schema : schemaList) {
            builder.addColumnDesc(NS.ColumnDesc.newBuilder()
                    .setType(stringOf(schema.getType()))
                    .setName(schema.getName())
                    .setAddTsIdx(schema.isAddTsIndex()));
        }
        NS.TableInfo table = builder.build();
        // 可以通过返回值判断是否创建成功
        boolean ok = nsc.createTable(table);
        if (ok) {
            logger.info("the RrtidbSchemaTable is created ：" + ok);
        } else {
            logger.error("the RrtidbSchemaTable is created ：" + ok);
        }
        for (int i = 0; i < MAX_THREAD_NUM; i++) {
            clusterClient[i].refreshRouteTable();
        }
    }

    /**
     * ColumnType转换为对应的String类型
     *
     * @param columnType
     * @return
     */
    private static String stringOf(ColumnType columnType) {
        if (ColumnType.kString.equals(columnType)) {
            return "string";
        } else if (ColumnType.kInt16.equals(columnType)) {
            return "int16";
        } else if (ColumnType.kInt32.equals(columnType)) {
            return "int32";
        } else if (ColumnType.kInt64.equals(columnType)) {
            return "int64";
        } else if (ColumnType.kUInt16.equals(columnType)) {
            return "uint16";
        } else if (ColumnType.kUInt32.equals(columnType)) {
            return "uint32";
        } else if (ColumnType.kUInt64.equals(columnType)) {
            return "uint64";
        } else if (ColumnType.kFloat.equals(columnType)) {
            return "float";
        } else if (ColumnType.kDouble.equals(columnType)) {
            return "double";
        } else if (ColumnType.kTimestamp.equals(columnType)) {
            return "timestamp";
        } else if (ColumnType.kDate.equals(columnType)) {
            return "date";
        } else if (ColumnType.kBool.equals(columnType)) {
            return "bool";
        } else if (ColumnType.kEmptyString.equals(columnType)) {
            return "emptyString";
        } else {
            throw new RuntimeException("not supported type with " + columnType);
        }
    }

    public static List<ColumnDesc> getSchemaOfRtidb(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            logger.warn("filePath is null or empty ");
            return null;
        }
        return clusterClient[0].getHandler(tableName).getSchema();
    }

    public static boolean dropTable(String tableName) {
        return nsc.dropTable(tableName);
    }

    public static TableSyncClient[] getTableSyncClient() {
        return tableSyncClient;
    }

}
