package org.seekloud.VideoMeeting.rtpServer.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.Boot.publishManager
import org.seekloud.VideoMeeting.rtpServer.Boot.streamManager
import org.seekloud.VideoMeeting.rtpServer.Boot.dataStoreActor
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil

import concurrent.duration._
import scala.collection.mutable


/**
  * Created by haoshuhan on 2019/7/16.
  */
object StreamActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class StreamData(data: Array[Byte], ssrc: Int, timeStamp: Long) extends Command

  case class AuthSuccess(ssrc: Int) extends Command

  case object StopPushing2Manager extends Command

  case class CalculateBandwidth(unit: Int) extends Command

  case class GetBandwidth(replyTo: ActorRef[(String, List[(Int, Int)])]) extends Command

  case object LoopKey

  def create(liveId: String): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        waiting4Auth(liveId)
      }
    }
  }

  def waiting4Auth(liveId: String)
                  (implicit timer: TimerScheduler[Command],
                         stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case AuthSuccess(ssrc) =>
          timer.startSingleTimer(s"LoopKey-$liveId", StopPushing2Manager, 30.seconds)
          log.info(s"liveId $liveId auth success, turn to work behavior")

          //入口带宽计算
          val countMap = mutable.Map(1 -> 0, 3 -> 0, 10 -> 0)
          countMap.keys.foreach {i =>
            timer.startPeriodicTimer(s"BandwidthCal-$i", CalculateBandwidth(i), i.seconds)
          }
          work(liveId, ssrc, countMap, mutable.Map(1 -> 0, 3 -> 0, 10 -> 0))

        case GetBandwidth(replyTo) =>
          replyTo ! (liveId, List((1, -1), (3, -1), (10, -1)))
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }

    }
  }


  def work(liveId: String,
           ssrc: Int,
           dataCountMap: mutable.Map[Int, Int],
           bandwidthMap: mutable.Map[Int, Int]
        )
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StreamData(data, ssrcRecv, timeStamp) =>
//          println(s"liveId:$liveId, recv new stream data, length: ${data.length}")
          if (ssrcRecv == ssrc) {
            //transfer
            publishManager ! PublishManager.StreamData(liveId, data)
          } else {
          //  println(s"liveId: $liveId, ssrc match error: recv:$ssrcRecv; real:$ssrc")
          }
          dataCountMap.foreach { i =>
            dataCountMap.update(i._1, i._2 + data.length)
          }
          val present = RtpUtil.toInt(RtpUtil.toByte(System.currentTimeMillis(), 4).toArray)
         // log.info(s"present:$present===present1:${System.currentTimeMillis()}===timeStamp:$timeStamp")
          val differ = Math.abs(present - timeStamp).toInt
          timer.startSingleTimer(s"LoopKey-$liveId", StopPushing2Manager, 30.seconds)
          Behaviors.same

        case AuthSuccess(ssrcRecv) =>
          log.info(s"liveId $liveId auth another time success")
          timer.startSingleTimer(s"LoopKey-$liveId", StopPushing2Manager, 30.seconds)
          work(liveId, ssrcRecv, dataCountMap, bandwidthMap)

        case StopPushing2Manager =>
          streamManager ! StreamManager.StopPushing(liveId :: Nil)
          dataStoreActor ! DataStoreActor.stopGetData(liveId)
          Behaviors.same

        case CalculateBandwidth(unit) => //计算unit时间内带宽
          val dataCountOption = dataCountMap.get(unit)
          if (dataCountOption.isDefined) {
            val dataCount = dataCountOption.get
            val dataBandwidth = dataCount * 8 / unit
            bandwidthMap.update(unit, dataBandwidth)
            dataCountMap.update(unit, 0)
          } else log.error(s"unit $unit not exists")

          Behaviors.same

        case GetBandwidth(replyTo) =>
          replyTo ! (liveId, bandwidthMap.toList)
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same

      }

    }
  }

}
