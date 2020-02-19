package org.seekloud.VideoMeeting.processor.core_new

import java.io.{File, FileOutputStream, OutputStream}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.processor.Boot
import org.seekloud.VideoMeeting.processor.Boot.streamPullActor
import org.seekloud.VideoMeeting.processor.common.AppSettings.recordPath
import org.seekloud.VideoMeeting.processor.core_new.StreamPullActor.NewLive
import org.slf4j.LoggerFactory
import scala.concurrent.duration._


import scala.collection.mutable

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午3:04
  *
  * actor由RoomActor创建，初始化GrabberActor时
  * 建立pullActor->grabber 管道
  * 存储map liveId->ActorRef
  */
object StreamPullPipe {

  sealed trait Command

  case class NewBuffer(data: Array[Byte]) extends Command

  case object ClosePipe extends Command

  case object Timer4Stop

  case object Stop extends Command

  val log = LoggerFactory.getLogger(this.getClass)

  def create(roomId: Long, liveId: String, out: OutputStream): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          streamPullActor ! NewLive(liveId, roomId, ctx.self)
          val file = new File(s"$recordPath${liveId}_in.ts")
          if(file.exists()){
            file.delete()
            file.createNewFile()
          } else{
            file.createNewFile()
          }
          work(roomId, liveId, out, new FileOutputStream(file))
      }
    }
  }

  def work(roomId: Long, liveId: String, out: OutputStream, fileOut:FileOutputStream)(implicit timer: TimerScheduler[Command],
                                                                                   stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewBuffer(data) =>
          if (Boot.showStreamLog) {
            log.info(s"NewBuffer $liveId ${data.length}")
          }
          fileOut.write(data)
          out.write(data)
          Behaviors.same

        case ClosePipe =>
          timer.startSingleTimer(Timer4Stop, Stop, 50.milli)
          Behaviors.same

        case Stop =>
          log.info(s"$liveId pullPipe stopped ----")
          fileOut.close()
          out.close()
          Behaviors.stopped

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }
}