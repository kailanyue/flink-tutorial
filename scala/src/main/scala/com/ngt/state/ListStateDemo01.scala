package com.ngt.state

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.api.common.time.Time
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

import java.util
import scala.collection.mutable.ListBuffer

/**
 * @author ngt
 * @create 2021-02-07 22:33
 * user1,A
 * user1,B
 * user2,D
 * user2,C
 * ueer1,C
 * user1 -> (A,B,D)
 */
object ListStateDemo01 {
  def main(args: Array[String]): Unit = {
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    val lines: DataStream[String] = env.socketTextStream("192.168.31.8", 8888)

    // 启用 Checkpointing
    env.enableCheckpointing(5000)
    // 未设置重启策略的时候程序出现异常就会退出，设置重启策略
    env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, Time.seconds(5)))

    val tpDataStream: DataStream[(String, String)] = lines.map(data => {
      val words: Array[String] = data.split(",")
      (words(0), words(1))
    })

    tpDataStream.keyBy(_._1)
      .process(new KeyedProcessFunction[String, (String, String), (String, ListBuffer[String])] {
        // scala 中的状态可以使用 lazy，也可以使用 open，使用open的时候和 Java 写法一样
        lazy val listState: ListState[String] =
          getRuntimeContext.getListState(new ListStateDescriptor[String]("listState", classOf[String]))

        override def processElement(value: (String, String),
                                    ctx: KeyedProcessFunction[String, (String, String), (String, ListBuffer[String])]#Context, out: Collector[(String, ListBuffer[String])]): Unit = {
          val action: String = value._2
          listState.add(action)
          val events: ListBuffer[String] = new ListBuffer[String]
          val iterator: util.Iterator[String] = listState.get().iterator()
          while (iterator.hasNext) {
            events += iterator.next()
          }
          out.collect((value._1, events))
        }
      })
      .print()
    env.execute()
  }
}
/*
user1,A
user1,B
user2,D
user2,C
ueer1,C


6> (user1,ListBuffer(A))
6> (user1,ListBuffer(A, B))
1> (user2,ListBuffer(C))
1> (user2,ListBuffer(C, D))
7> (ueer1,ListBuffer(C))
 */