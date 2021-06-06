package cn.ngt.day11;

import cn.ngt.bean.WaterSensor;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import static org.apache.flink.table.api.Expressions.$;

/**
 * Created on 2021-06-06 21:36.
 *
 * @author ngt
 */
public class _01_ProcessTime_StreamToTable {
    public static void main(String[] args) {
        // 1.获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2.读取文本数据创建流并转换为JavaBean对象
        SingleOutputStreamOperator<WaterSensor> waterSensorDS = env.readTextFile("input/sensor.txt")
                .map(line -> {
                    String[] split = line.split(",");
                    return new WaterSensor(split[0],
                            Long.parseLong(split[1]),
                            Integer.parseInt(split[2]));
                });

        // 3.将流转换成表并指定处理时间
        Table table = tableEnv.fromDataStream(waterSensorDS,
                $("id"), $("ts"), $("vc"),
                $("pt").proctime());

        table.printSchema();

        /*
        root
         |-- id: STRING
         |-- ts: BIGINT
         |-- vc: INT
         |-- pt: TIMESTAMP(3) *PROCTIME*
         */
    }
}
