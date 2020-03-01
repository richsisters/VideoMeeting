package org.seekloud.VideoMeeting.pcClient.core.stream

import java.io.OutputStream

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import akka.actor.typed.scaladsl.AskPattern._
import javafx.scene.canvas.GraphicsContext
import org.seekloud.VideoMeeting.capture.sdk.MediaCapture
import org.seekloud.VideoMeeting.pcClient.common.AppSettings
import org.seekloud.VideoMeeting.pcClient.core.collector.CaptureActor
import org.seekloud.VideoMeeting.pcClient.core.rtp._
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.scene.{AudienceScene, HostScene}
import org.seekloud.VideoMeeting.pcClient.utils.{NetUtil, RtpUtil}
import org.seekloud.VideoMeeting.rtpClient.{PullStreamClient, PushStreamClient}
import org.seekloud.VideoMeeting.pcClient.utils.RtpUtil.{clientHost, clientHostQueue}
import org.seekloud.VideoMeeting.player.sdk.MediaPlayer
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.pcClient.Boot.{executor, scheduler, timeout}

import concurrent.duration._
import scala.collection.mutable
import language.postfixOps
import scala.util.{Failure, Success}

/**
  * User: Arrow
  * Date: 2019/7/19
  * Time: 12:25
  *
  * 推拉流鉴权及控制
  *
  */
object LiveManager {

  private val log = LoggerFactory.getLogger(this.getClass)
  private var validHost = clientHost

  val dispatcher: DispatcherSelector = DispatcherSelector.fromConfig("akka.actor.my-blocking-dispatcher")

  case class PullInfo(roomId: Long, gc: GraphicsContext)

  sealed trait LiveCommand

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends LiveCommand

  final case class DevicesOn(gc: GraphicsContext, isJoin: Boolean = false, callBackFunc: Option[() => Unit] = None) extends LiveCommand

  final case object DeviceOff extends LiveCommand

  final case class SwitchMediaMode(isJoin: Boolean, reset: () => Unit) extends LiveCommand

  final case class ChangeMediaOption(bit: Option[Int], re: Option[String], frameRate: Option[Int],
    needImage: Boolean = true, needSound: Boolean = true, reset: () => Unit) extends LiveCommand with CaptureActor.CaptureCommand

  final case class PushStream(liveId: String, liveCode: String) extends LiveCommand

  final case object InitRtpFailed extends LiveCommand

  final case object StopPush extends LiveCommand

  final case class Ask4State(reply: ActorRef[Boolean]) extends LiveCommand

  final case class PullStream(liveId: String, pullInfo: PullInfo, audienceScene: Option[AudienceScene] = None, hostScene: Option[HostScene] = None) extends LiveCommand

  final case class  StopPull(liveId: String) extends LiveCommand

  final case object StopPullAll extends LiveCommand

  final case object PusherStopped extends LiveCommand

  final case class PullerStopped(liveId: String) extends LiveCommand

  final case class HostBan4Live(out: Option[OutputStream], image: Boolean, sound: Boolean) extends LiveCommand with CaptureActor.CaptureCommand

  private object PUSH_RETRY_TIMER_KEY

  private object PULL_RETRY_TIMER_KEY


  def create(parent: ActorRef[RmManager.RmCommand], mediaPlayer: MediaPlayer): Behavior[LiveCommand] =
    Behaviors.setup[LiveCommand] { ctx =>
      log.info(s"LiveManager is starting...")
      implicit val stashBuffer: StashBuffer[LiveCommand] = StashBuffer[LiveCommand](Int.MaxValue)
      Behaviors.withTimers[LiveCommand] { implicit timer =>
        idle(parent, mediaPlayer, mutable.Map[String, ActorRef[StreamPuller.PullCommand]](), isStart = false, isRegular = false)
      }
    }


  private def idle(
    parent: ActorRef[RmManager.RmCommand],
    mediaPlayer: MediaPlayer,
    streamPuller: mutable.Map[String, ActorRef[StreamPuller.PullCommand]],
    captureActor: Option[ActorRef[CaptureActor.CaptureCommand]] = None,
    streamPusher: Option[ActorRef[StreamPusher.PushCommand]] = None,
    mediaCapture: Option[MediaCapture] = None,
    isStart: Boolean,
    isRegular: Boolean,
    beforeSound: Boolean = true
  )(
    implicit stashBuffer: StashBuffer[LiveCommand],
    timer: TimerScheduler[LiveCommand]
  ): Behavior[LiveCommand] =
    Behaviors.receive[LiveCommand] { (ctx, msg) =>
      msg match {
        case msg: DevicesOn =>
          val captureActor = getCaptureActor(ctx, msg.gc, msg.isJoin, msg.callBackFunc)
          val mediaCapture = MediaCapture(captureActor, debug = AppSettings.captureDebug, needTimestamp = AppSettings.needTimestamp)
          captureActor ! CaptureActor.GetMediaCapture(mediaCapture)
          mediaCapture.start()
          idle(parent, mediaPlayer, streamPuller, Some(captureActor), streamPusher, Some(mediaCapture), isStart, isRegular, beforeSound)

        case DeviceOff =>
          captureActor.foreach(_ ! CaptureActor.StopCapture)
          idle(parent, mediaPlayer, streamPuller, None, streamPusher, isStart = isStart, isRegular = isRegular)

        case msg: SwitchMediaMode =>
          captureActor.foreach(_ ! CaptureActor.SwitchMode(msg.isJoin, msg.reset))
          Behaviors.same

        case msg: ChangeMediaOption =>
          captureActor.foreach(_ ! msg)
          Behaviors.same

        case msg: HostBan4Live =>
          if(msg.out.isEmpty && msg.sound != beforeSound){
            streamPusher.foreach(_ ! StreamPusher.NewOutPut(msg.image, msg.sound))
          }else{
            captureActor.foreach(_ ! msg)
          }
          idle(parent, mediaPlayer, streamPuller, captureActor, streamPusher, isStart = isStart, isRegular = isRegular, beforeSound = msg.sound)

        case msg: PushStream =>
          log.debug(s"prepare push stream.")
          assert(captureActor.nonEmpty)
          if (streamPusher.isEmpty) {
            val pushChannel = new PushChannel
            val pusher = getStreamPusher(ctx, msg.liveId, msg.liveCode, captureActor.get)
            RtpUtil.initIpPool()
            validHost = clientHostQueue.dequeue()
            val rtpClient = new PushStreamClient(AppSettings.host, NetUtil.getFreePort, pushChannel.serverPushAddr, pusher,AppSettings.rtpServerDst)
            mediaCapture.foreach(_.setTimeGetter(rtpClient.getServerTimestamp))
            pusher ! StreamPusher.InitRtpClient(rtpClient)
            idle(parent, mediaPlayer, streamPuller, captureActor, Some(pusher), isStart = isStart, isRegular = isRegular, beforeSound = beforeSound)
          } else {
            log.info(s"waiting for old pusher stop.")
            ctx.self ! StopPush
            timer.startSingleTimer(PUSH_RETRY_TIMER_KEY, msg, 100.millis)
            Behaviors.same
          }

        case InitRtpFailed =>
          ctx.self ! StopPush
          Behaviors.same

        case StopPush =>
          log.info(s"LiveManager stop pusher!")
          streamPusher.foreach {
            pusher =>
              log.info(s"stopping pusher...")
              pusher ! StreamPusher.StopPush
          }
          Behaviors.same

        case msg: PullStream =>
          if (streamPuller.get(msg.liveId).isEmpty) {
            val pullChannel = new PullChannel
            val pullSize = streamPuller.toList.size
            val puller = getStreamPuller(ctx, msg.liveId, msg.pullInfo, mediaPlayer,  msg.audienceScene, msg.hostScene, pullSize + 1)
            val rtpClient = new PullStreamClient(AppSettings.host, NetUtil.getFreePort, pullChannel.serverPullAddr, puller, AppSettings.rtpServerDst)
            puller ! StreamPuller.InitRtpClient(rtpClient)
            streamPuller.put(msg.liveId, puller)
            idle(parent, mediaPlayer, streamPuller, captureActor, streamPusher, isStart = true, isRegular = isRegular, beforeSound = beforeSound)
          } else {
            log.info(s"waiting for old puller-${msg.liveId} stop.")
            ctx.self ! StopPull(msg.liveId)
            timer.startSingleTimer(PULL_RETRY_TIMER_KEY, msg, 100.millis)
            streamPuller.remove(msg.liveId)
            idle(parent, mediaPlayer, streamPuller, captureActor, streamPusher, isStart = true, isRegular = isRegular, beforeSound = beforeSound)
          }

        case msg: StopPull =>
          log.info(s"LiveManager stop puller")
          streamPuller.foreach { puller =>
            if(puller._1 == msg.liveId){
              log.info(s"stopping puller-${puller._1}")
              puller._2 ! StreamPuller.StopPull
            }
          }
          Behaviors.same

        case StopPullAll => // 停止房间内的所有拉流信息
          log.info(s"LiveManager stop puller4All")
          if(streamPuller.nonEmpty){
            streamPuller.foreach { puller =>
              log.info(s"stopping puller-${puller._1}")
              puller._2 ! StreamPuller.StopPull
            }
          }
          Behaviors.same

        case PusherStopped =>
          log.info(s"LiveManager got pusher stopped.")
          idle(parent, mediaPlayer, streamPuller, captureActor, None, isStart = isStart, isRegular = isRegular, beforeSound = beforeSound)

        case msg:PullerStopped =>
          log.info(s"LiveManager got puller stopped.")
          streamPuller.remove(msg.liveId)
          idle(parent, mediaPlayer, streamPuller, captureActor, streamPusher, isStart = false, isRegular = false, beforeSound = beforeSound)

        case Ask4State(reply) =>
          reply ! isStart
          idle(parent, mediaPlayer,streamPuller, captureActor, streamPusher, isStart = isStart, isRegular = true, beforeSound = beforeSound)

        case ChildDead(child, childRef) =>
          log.debug(s"LiveManager unWatch child-$child")
          ctx.unwatch(childRef)
          Behaviors.same

        case x =>
          log.warn(s"unknown msg in idle: $x")
          Behaviors.unhandled
      }
    }

  private def getCaptureActor(
    ctx: ActorContext[LiveCommand],
    gc: GraphicsContext,
    isJoin: Boolean,
    callBackFunc: Option[() => Unit],
    frameRate: Int = 30
  ) = {
    val childName = s"captureActor-${System.currentTimeMillis()}"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(CaptureActor.create(frameRate, gc, isJoin, callBackFunc), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[CaptureActor.CaptureCommand]
  }

  private def getStreamPusher(
    ctx: ActorContext[LiveCommand],
    liveId: String,
    liveCode: String,
    //    mediaActor: ActorRef[MediaActor.MediaCommand]
    captureActor: ActorRef[CaptureActor.CaptureCommand]
  ) = {
    val childName = s"streamPusher-$liveId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(StreamPusher.create(liveId, liveCode, ctx.self, captureActor), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[StreamPusher.PushCommand]
  }

  private def getStreamPuller(
    ctx: ActorContext[LiveCommand],
    liveId: String,
    pullInfo: PullInfo,
    mediaPlayer: MediaPlayer,
    audienceScene : Option[AudienceScene],
    hostScene: Option[HostScene],
    index: Int
  ) = {
    val childName = s"streamPuller-$liveId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(StreamPuller.create(liveId, pullInfo, ctx.self, mediaPlayer, audienceScene, hostScene, index), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[StreamPuller.PullCommand]
  }


}
