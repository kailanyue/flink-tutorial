package cn.ngt.day10;

import cn.ngt.bean.WaterSensor;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.descriptors.Elasticsearch;
import org.apache.flink.table.descriptors.Json;
import org.apache.flink.table.descriptors.Schema;

import static org.apache.flink.table.api.Expressions.$;

/**
 * Created on 2021-06-05 22:12.
 *
 * @author ngt
 */
public class _08_Sink_ES_Upsert {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取端口数据创建流并装换为JavaBean
        SingleOutputStreamOperator<WaterSensor> waterSensorDS = env.socketTextStream("hadoop102", 9999)
                .map(data -> {
                    String[] split = data.split(",");
                    return new WaterSensor(split[0],
                            Long.parseLong(split[1]),
                            Integer.parseInt(split[2]));
                });

        // 3.创建表执行环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 4.将流转换为动态表
        Table sensorTable = tableEnv.fromDataStream(waterSensorDS);

        // 5.使用TableAPI过滤出"ws_001"的数据
        Table selectTable = sensorTable
                .groupBy($("id"), $("vc"))
                .select($("id"),
                        $("ts").count().as("ct"),
                        $("vc"));


        // 6.将selectTable写入ES
        tableEnv.connect(new Elasticsearch()
                .index("sensor_sql")
                .documentType("_doc")    // 此处使用 groupBy 的 key 值作为docid
                .version("7")
                .host("192.168.100.102", 9200, "http")
                .keyDelimiter("-")    // 主键的链接字符 默认使用 _
                .bulkFlushMaxActions(1))  // 为每个批量请求设置要缓冲的最大操作数 ，工作中通常根据具体业务来设置通常很大
                .withSchema(new Schema()
                        .field("id", DataTypes.STRING())
                        .field("ts", DataTypes.BIGINT())
                        .field("vc", DataTypes.INT()))
                .withFormat(new Json())
                .inUpsertMode()   // 更新模式
                .createTemporaryTable("sensor");
        selectTable.executeInsert("sensor"); //Sink

        //7.执行任务
        env.execute();
    }
}
