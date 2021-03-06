package com.ngt.chain

import org.apache.flink.api.common.functions.{FilterFunction, FlatMapFunction}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector
/**
 * @author ngt
 * @create 2021-02-06 22:09
 */
object StartNewChainDemo {
  def main(args: Array[String]): Unit = {
    val configuration: Configuration = new Configuration()
    configuration.setInteger("rest.port", 8181)

    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(configuration)
    val lines: DataStream[String] = env.socketTextStream("192.168.31.8", 8888)

    // 系统默认开启 OperatorChaining ，可以手动关闭
    // env.disableOperatorChaining()

    val words: DataStream[String] = lines.flatMap(new FlatMapFunction[String, String] {
      override def flatMap(value: String, out: Collector[String]): Unit = {
        val words: Array[String] = value.split(" ")
        for (elem <- words) {
          out.collect(elem)
        }
      }
    })

    val filterd: DataStream[String] = words.filter(new FilterFunction[String] {
      override def filter(value: String): Boolean = {
        value.startsWith("error")
      }
    })

    filterd.map((_, 1))
      .startNewChain()
      .keyBy(_._1)
      .sum(1)
      .print()
    env.execute()
  }
}


/*
    Source: Socket Stream  (Parallelism：1)  --rebalance-->
    Flat Map -> Filter     (Parallelism：8)  --forward-->
    Map                    (Parallelism：8)  --hash-->
    Keyed Aggregation -> Sink: Print to Std. Out(Parallelism：8)
 */
