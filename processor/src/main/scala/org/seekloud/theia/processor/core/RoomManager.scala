package org.seekloud.VideoMeeting.processor.core

/**
  * User: yuwei
  * Date: 7/15/2019
  */

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler,StashBuffer}
import org.seekloud.VideoMeeting.processor.utils.SecureUtil
import org.slf4j.LoggerFactory
import scala.collection.mutable
import scala.concurrent.duration._
import org.seekloud.VideoMeeting.processor.utils.CpuUtils
import scala.util.{Failure, Success}
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol.RoomInfo
import org.seekloud.VideoMeeting.processor.Boot.{recorderManager,grabberManager, channelWorker}
import org.bytedeco.ffmpeg.global.avutil
import org.seekloud.VideoMeeting.processor.common.AppSettings.isTest

object RoomManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class CloseRoom(roomId: Long) extends Command

  case class UpdateRoomInfo(roomId: Long, liveIdList: List[String], startTime:Long, layout: Int, aiMode: Int) extends Command

  case object Timer4CpuInfo

  case object PrintCpu extends Command

  case class Switch2Single(roomId:Long) extends Command

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"roomManager start----")
          if(isTest){
//            timer.startPeriodicTimer(Timer4CpuInfo, PrintCpu, 5.seconds)
//            CpuUtils.init()
          }
          work(mutable.Map[Long, RoomInfo]())
      }
    }
  }

  def work(roomInfoMap: mutable.Map[Long, RoomInfo])(implicit timer: TimerScheduler[Command],
                                                     stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"roomManager turn to work state--")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@UpdateRoomInfo(roomId, liveIdList, startTime, layout, aiMode) =>
            log.info(s"got msg: $m")
            val roomInfo = roomInfoMap.get(roomId)
            if (roomInfo.isEmpty || liveIdList != roomInfo.get.roles) {
              grabberManager ! GrabberManager.UpdateRole(roomId, liveIdList)
            }
            recorderManager ! RecorderManager.UpdateRoomInfo(roomId, liveIdList, startTime, layout, aiMode)
            roomInfoMap.put(roomId, RoomInfo(roomId, liveIdList, layout, aiMode))
          Behaviors.same

        case RoomManager.Switch2Single(roomId) =>
          val roomInfoOpt = roomInfoMap.get(roomId)
          if(roomInfoOpt.isDefined){
            val roomInfo = roomInfoOpt.get
            ctx.self ! UpdateRoomInfo(roomId, List(roomInfo.roles.head), 0, roomInfo.layout, roomInfo.aiMode)
          }else{
            log.debug(s"$roomId info not exist.")
          }
          Behaviors.same

        case PrintCpu =>
          val cpuUse = CpuUtils.getProcessCpu()
          val single = roomInfoMap.values.count(_.roles.length == 1)
          val couple = roomInfoMap.values.size - single
          log.info(s"single:$single, couple:$couple   --- cpu use $cpuUse ----")
          Behaviors.same

        case CloseRoom(roomId) =>
          roomInfoMap.get(roomId).foreach { info =>
            channelWorker ! ChannelWorker.RoomClose(info.roles)
          }
          roomInfoMap.remove(roomId)
          recorderManager ! RecorderManager.CloseRoom(roomId)
          grabberManager ! GrabberManager.CloseRoom(roomId)
          Behaviors.same
      }
    }
  }

}
