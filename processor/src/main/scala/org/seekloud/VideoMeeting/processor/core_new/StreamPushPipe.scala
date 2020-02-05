package org.seekloud.VideoMeeting.processor.core_new

import java.io.{File, FileInputStream, FileOutputStream, PipedInputStream, PipedOutputStream}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.language.implicitConversions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.Pipe.SourceChannel

import org.seekloud.VideoMeeting.processor.Boot.streamPushActor
import org.seekloud.VideoMeeting.processor.common.AppSettings._

import scala.concurrent.duration._
import scala.collection.mutable

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午3:03
  *
  * actor由RoomActor创建，在初始化recorder时
  * 建立recorder->pushActor 管道
  * pipe ! pushActor
  */
object StreamPushPipe {
  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object SendData extends Command

  case object ClosePipe extends Command

  case class NewLive(startTime: Long) extends Command

  case class NewHostLive(startTime: Long, source: SourceChannel) extends Command

  case object Timer4Stop

  case object Timer4Send

  case object Stop extends Command

  private val liveCountMap = mutable.Map[String, Int]()

  def create(roomId: Long, liveId: String, liveCode:String, source: SourceChannel, startTime: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"${ctx.self} init ----")
          ctx.self ! NewLive(startTime)
          val out = if(isDebug){
            val file = new File(s"$debugPath$roomId/${liveId}_out.ts")
            Some(new FileOutputStream(file))
          }else{
            None
          }
          work(roomId, liveId, liveCode, source,ByteBuffer.allocate(1316), out)
      }
    }
  }

  def work(roomId: Long,liveId:String, liveCode: String, source:SourceChannel, dataBuf:ByteBuffer, out:Option[FileOutputStream])
    (implicit timer: TimerScheduler[Command],
      stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewLive(startTime) =>
          liveCountMap.put(liveId, 0)
          ctx.self ! SendData
          dataBuf.clear()
          Behaviors.same

        case SendData =>
          val r = source.read(dataBuf)
          dataBuf.flip()
          if (r > 0) {
            val data = dataBuf.array().clone()
            streamPushActor ! StreamPushActor.PushData(liveId,  data.take(r))
            if (liveCountMap.getOrElse(liveId, 0) < 5) {
              log.info(s"$liveId send data --")
              liveCountMap.update(liveId, liveCountMap(liveId) + 1)
            }
            ctx.self ! SendData
            dataBuf.clear()
          } else {
            log.info(s"${ctx.self} got nothing, $r")
          }
          Behaviors.same

        case ClosePipe =>
          timer.startSingleTimer(Timer4Stop, Stop, 500.milli)
          Behaviors.same

        case Stop =>
          log.info(s"$roomId pushPipe stopped ----")
          source.close()
          dataBuf.clear()
          out.foreach(_.close())
          Behaviors.stopped

        case x =>
          log.info(s"${ctx.self} got an unknown msg:$x")
          Behaviors.same
      }
    }
  }
}