package org.seekloud.VideoMeeting.rtpServer.test

import java.sql.Date
import java.text.SimpleDateFormat

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpClient.Protocol._
import org.seekloud.VideoMeeting.rtpClient.{Protocol, PullStreamClient}
import org.seekloud.VideoMeeting.rtpServer.TestRtpClient
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
  * Author: zwq
  * Date: 2019/10/11
  * Time: 15:13
  * 模拟拉流客户端
  */
object TestPullActor {

//  private var lastSeq =  0

//  var list = List.empty[Int]

  private val log = LoggerFactory.getLogger(this.getClass)

  case class Ready(client: PullStreamClient) extends Protocol.Command

  case class PushStream(liveId: String) extends Protocol.Command

  case class PullStream(liveId: List[String]) extends Protocol.Command

  case class Auth(liveId: String, liveCode: String) extends Protocol.Command

  def create(): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Protocol.Command] = StashBuffer[Protocol.Command](Int.MaxValue)
      Behaviors.withTimers[Protocol.Command] { implicit timer =>
        wait4Ready()
      }
    }
  }

  def wait4Ready()
    (implicit timer: TimerScheduler[Protocol.Command],
      stashBuffer: StashBuffer[Protocol.Command]): Behavior[Protocol.Command] = {
    Behaviors.receive[Protocol.Command] { (ctx, msg) =>
      msg match {
        case Ready(client) =>
          log.info("recv Ready")
          stashBuffer.unstashAll(ctx, work(client))

        case x =>
          stashBuffer.stash(x)
          Behavior.same
      }
    }
  }

  def work(client: PullStreamClient)
    (implicit timer: TimerScheduler[Protocol.Command],
      stashBuffer: StashBuffer[Protocol.Command]): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { context =>

      client.pullStreamStart()
      var last_seq = 0
      var lost_num = 0
      var init_seq = -1

      Behaviors.receiveMessage[Protocol.Command] {
        case PullStream(liveId) =>
          client.pullStreamData(liveId)
          Behaviors.same

        case PullStreamReady =>
          log.info(s"recv PullStreamReady")
          client.pullStreamData(List(TestRtpClient.liveId, "liveIdTest-1581"))
          Behaviors.same

        case PullStreamReqSuccess(liveIds) =>
          println(s"################# recv PullStreamReqSuccess, liveIds:$liveIds")
          Behaviors.same

        case PullStreamData(liveId, seq, data) =>
//          log.info(s"recv PullStreamData, seq:$seq")
          if (init_seq == -1) {
            //       println(s"=======init seq: $seq")
            init_seq = seq
          }
          if (seq % 5000 == 0) {
            //     println(s"recv seq====$seq,lost rate : ${lost_num.toDouble / (seq - init_seq).toDouble}")
            //val info = client.getPackageLoss()
            //  info.foreach{in =>
            //                println("===")
            //                println(s"liveId:${in._1}")
            //                println(s"60:${in._2.lossScale60}==10:${in._2.lossScale10}==2:${in._2.lossScale2}")
            //    }
          }
          //            val seq = client.parseData(data).header.seq
          //            println(s"actor recv seq-----$seq")
          if (seq - last_seq != 1) {
            //              println(s"========error=======last_seq:$last_seq, seq:$seq")
            lost_num += 1
            //              println(s"lost rate : ${lost_num.toDouble / (seq - init_seq).toDouble}")
          }
          last_seq = seq
          //            if (seq > 59998) client.close()

          //            println(s"$liveId pull success, seq:$seq")

          // println(liveId + " receive data:" + new String(data, "UTF-8"))
          if(seq == 1000) println(client.getBandWidth)
          Behaviors.same

        case CloseSuccess =>
          log.info(s"recv CloseSuccess")
          Behaviors.same

        case PullStreamPacketLoss =>
          log.info("recv PullStreamPacketLoss")
          Behaviors.same

        case StreamStop(liveId) =>
          log.info(s"recv $liveId stopped")
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same

      }
    }
  }


  def format(timeMs:Long,format:String = "yyyy-MM-dd HH:mm:ss.SSS"): String ={
    val data  = new Date(timeMs)
    val simpleDateFormat = new SimpleDateFormat(format)
    simpleDateFormat.format(data)
  }

}
