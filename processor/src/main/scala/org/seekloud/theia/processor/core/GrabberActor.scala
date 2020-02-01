package org.seekloud.VideoMeeting.processor.core

import java.io.{InputStream, PipedInputStream, PipedOutputStream}
import java.nio.{ByteBuffer, ShortBuffer}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import org.slf4j.LoggerFactory
import org.bytedeco.ffmpeg.global._
import org.bytedeco.javacv.FFmpegFrameGrabber1
import org.seekloud.VideoMeeting.processor.Boot.{grabberManager, recorderManager}
import org.seekloud.VideoMeeting.processor.core.RecorderActor.NewFrame
import org.seekloud.VideoMeeting.processor.Boot.roomManager

import scala.language.implicitConversions
import scala.collection.mutable
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._
import scala.util._
/**
  * User: yuwei
  * Date: 7/15/2019
  */
object GrabberActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class ToInit(buf:InputStream) extends Command

  case object Stop extends Command

  case object GrabFrame extends Command

  case object GrabFrameFirst extends Command

  case object TimerKey4ReGrab

  case object StartGrabber extends Command

  case object CloseGrabber extends Command

  case object UpdateRoom extends Command

  case object Timer4Update

  case object TimerKey4Start

  case object TimerKey4Close

  case class Recorder(rec: ActorRef[RecorderActor.Command]) extends Command

//  private val liveCountMap = mutable.Map[String,Int]()

  def create(roomId: Long,role:String, liveId: String, buf:InputStream): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"${ctx.self} init ----")

          init(roomId, role,liveId, buf, None)
      }
    }
  }

  def init(roomId: Long,role:String, liveId: String, buf:InputStream,
           recorder:Option[ActorRef[RecorderActor.Command]])(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$liveId grabber switch to init state.")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@Recorder(rec) =>
          log.info(s"got msg: $m.")
          val grab = new FFmpegFrameGrabber1(buf)
          try{
            grab.start()
          }catch{
            case e:Exception =>
              log.info(s"exception in create grabber: $e")
          }
          ctx.self ! GrabFrameFirst
          work(roomId,role, liveId,rec,buf, grab)

        case m@StartGrabber =>
          log.info(s"got msg: $m.")
          Behaviors.same

        case Stop =>
          log.info(s"grabber $liveId stopped when init")
//          grabber.releaseUnsafe()
//          recorderManager ! RecorderManager.CloseRoom(roomId)
          Behaviors.stopped

        case x=>
          log.info(s"${ctx.self} got an unknown msg:$x")
          Behaviors.same
      }
    }
  }


  def work(roomId: Long, role:String, liveId: String, recorder:ActorRef[RecorderActor.Command],
    buf:InputStream, grabber: FFmpegFrameGrabber1)(implicit timer: TimerScheduler[Command],
  stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$liveId grabber switch to work state.")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Recorder(rec) =>
          Behaviors.same

        case m@GrabFrameFirst =>
          log.info(s"got msg: $m")
//          liveCountMap.put(liveId,0)
          val frame = grabber.grab()
          val channel = grabber.getAudioChannels
          val sampleRate = grabber.getSampleRate
          val height = grabber.getImageHeight
          val width = grabber.getImageWidth
          if(role == "host") {
            recorder ! RecorderActor.UpdateRecorder(channel, sampleRate, grabber.getFrameRate, width, height, liveId)
          }
          if(frame != null) {
            recorder ! RecorderActor.NewFrame(liveId, frame.clone())
            ctx.self ! GrabFrame
          }else{
            log.info(s"$liveId --- frame is null")
            ctx.self ! Stop
          }
          Behaviors.same

        case StartGrabber =>
          Behaviors.same

        case GrabFrame =>
//          if(liveCountMap.getOrElse(liveId,0) < 5){
//            log.info(s"$liveId grab frame --")
//            liveCountMap.update(liveId, liveCountMap(liveId) +1)
//          }
          val frame = grabber.grab()
          if(frame != null) {
            recorder ! RecorderActor.NewFrame(liveId, frame.clone())
            ctx.self ! GrabFrame
//            log.info(s"send frame ${frame.clone()}")
          }else{
            log.info(s"$liveId --- frame is null")
            ctx.self ! Stop
//            timer.startSingleTimer(Timer4Update, UpdateRoom, 3.seconds)
          }
          Behaviors.same

        case UpdateRoom =>
          log.info(s"$liveId grabber send 2 rm to update roomInfo: $role")
          role match {
            case "host" =>
              roomManager ! RoomManager.CloseRoom(roomId)
            case "client" =>
          }
          Behaviors.same

        case Stop =>
          timer.startSingleTimer(TimerKey4Close, CloseGrabber, 400.milli)
          Behaviors.same

        case CloseGrabber =>
          try {
            log.info(s"${ctx.self} stop ----")
            grabber.release()
            grabber.close()
            buf.close()
          }catch {
            case e:Exception =>
              log.error(s"${ctx.self} close error:$e")
          }
          Behaviors.stopped


        case ToInit(buf) =>
          log.info(s"$liveId grab to init because did not die last time")
          init(roomId,role, liveId, buf, None)
      }

    }
  }

}
