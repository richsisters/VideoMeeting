package org.seekloud.VideoMeeting.pcClient.core.stream

import java.nio.ByteBuffer
import java.nio.channels.{Channels, Pipe}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common.Constants.AudienceStatus
import org.seekloud.VideoMeeting.pcClient.common.Ids
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.stream.LiveManager.PullInfo
import org.seekloud.VideoMeeting.player.sdk.MediaPlayer
import org.seekloud.VideoMeeting.rtpClient.Protocol._
import org.seekloud.VideoMeeting.rtpClient.{Protocol, PullStreamClient}
import org.seekloud.VideoMeeting.pcClient.core.player.VideoPlayer
import org.seekloud.VideoMeeting.pcClient.scene.{AudienceScene, HostScene}
import org.slf4j.LoggerFactory

import concurrent.duration._

/**
  * User: TangYaruo
  * Date: 2019/8/20
  * Time: 13:41
  */
object StreamPuller {

  private val log = LoggerFactory.getLogger(this.getClass)

  type PullCommand = Protocol.Command

  final case class InitRtpClient(pullClient: PullStreamClient) extends PullCommand

  final case object PullStartTimeOut extends PullCommand

  final case object PullStream extends PullCommand

  final case object PullTimeOut extends PullCommand

  final case object StopPull extends PullCommand

  final case object StopSelf extends PullCommand

  private case class TimeOut(msg: String) extends PullCommand

  private final case object BehaviorChangeKey


  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[PullCommand],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends PullCommand

  private[this] def switchBehavior(ctx: ActorContext[PullCommand],
    behaviorName: String, behavior: Behavior[PullCommand], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[PullCommand],
      timer: TimerScheduler[PullCommand]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }


  def create(
    liveId: String,
    pullInfo: PullInfo,
    parent: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    audienceScene: Option[AudienceScene],
    hostScene: Option[HostScene],
    index: Int
  ): Behavior[PullCommand] =
    Behaviors.setup[PullCommand] { ctx =>
      log.info(s"StreamPuller-$liveId is starting.")
      implicit val stashBuffer: StashBuffer[PullCommand] = StashBuffer[PullCommand](Int.MaxValue)
      Behaviors.withTimers[PullCommand] { implicit timer =>
        init(liveId, pullInfo, parent, mediaPlayer, audienceScene, hostScene, None, index)
      }

    }

  private def init(
    liveId: String,
    pullInfo: PullInfo,
    parent: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    audienceScene: Option[AudienceScene],
    hostScene: Option[HostScene],
    pullClient: Option[PullStreamClient],
    index: Int
  )(
    implicit timer: TimerScheduler[PullCommand],
    stashBuffer: StashBuffer[PullCommand]
  ): Behavior[PullCommand] =
    Behaviors.receive[PullCommand] { (ctx, msg) =>
      msg match {
        case msg: InitRtpClient =>
          log.info(s"StreamPuller-$liveId init rtpClient.")
          msg.pullClient.pullStreamStart()
          timer.startSingleTimer(PullStartTimeOut, PullStartTimeOut, 5.seconds)
          init(liveId, pullInfo, parent, mediaPlayer, audienceScene, hostScene, Some(msg.pullClient), index)

        case PullStreamReady =>
          log.info(s"StreamPuller-$liveId ready for pull.")
          timer.cancel(PullStartTimeOut)
          ctx.self ! PullStream
          Behaviors.same

        case PullStartTimeOut =>
          pullClient.foreach(_.getClientId())
          timer.startSingleTimer(PullStartTimeOut, PullStartTimeOut, 5.seconds)
          Behaviors.same

        case PullStream =>
          log.info(s"StreamPuller-$liveId PullStream.")
          pullClient.foreach(_.pullStreamData(List(liveId)))
          timer.startSingleTimer(PullTimeOut, PullTimeOut, 30.seconds)
          Behaviors.same

        case msg: PullStreamReqSuccess =>
          log.info(s"StreamPuller-$liveId PullStream-${msg.liveIds} success.")
          timer.cancel(PullTimeOut)
          val mediaPipe = Pipe.open() // server -> sink -> source -> client
          val sink = mediaPipe.sink()
          val source = mediaPipe.source()
          sink.configureBlocking(false)
          val inputStream = Channels.newInputStream(source)
          audienceScene.foreach(_.autoReset())
          hostScene.foreach(_.resetBack())
          val playId = if(index == 1) Ids.getPlayId(AudienceStatus.CONNECT, roomId = pullInfo.roomId) else Ids.getPlayId(AudienceStatus.CONNECT2Third, roomId = pullInfo.roomId)
          mediaPlayer.setTimeGetter(playId, pullClient.get.getServerTimestamp)
          val videoPlayer = ctx.spawn(VideoPlayer.create(playId, audienceScene, None, None), s"videoPlayer$playId")
          mediaPlayer.start(playId, videoPlayer, Right(inputStream), Some(pullInfo.gc), None)

          stashBuffer.unstashAll(ctx, pulling(liveId, parent, pullClient.get, mediaPlayer, sink, audienceScene, hostScene,pullInfo, index))

        case PullStreamPacketLoss =>
          log.info(s"StreamPuller-$liveId PullStreamPacketLoss.")
          timer.startSingleTimer(PullStream, PullStream, 30.seconds)
          Behaviors.same

        case msg: NoStream =>
          log.info(s"No stream ids: ${msg.liveIds}")
          if (msg.liveIds.contains(liveId)) {
            log.info(s"Stream-$liveId unavailable now, try later.")
            timer.startSingleTimer(PullStream, PullStream, 30.seconds)
          }
          Behaviors.same

        case PullTimeOut =>
          log.info(s"StreamPuller-$liveId pull timeout, try again.")
          ctx.self ! PullStream
          Behaviors.same

        case StopPull =>
          log.info(s"StreamPuller-$liveId stopped in init.")
          val playId = if(index == 1) Ids.getPlayId(AudienceStatus.CONNECT, roomId = pullInfo.roomId) else Ids.getPlayId(AudienceStatus.CONNECT2Third, roomId = pullInfo.roomId)
          mediaPlayer.stop(playId, audienceScene.get.resetBack)
          parent ! LiveManager.PullerStopped(liveId)
          Behaviors.stopped

        case x =>
          log.warn(s"unhandled msg in init: $x")
          stashBuffer.stash(x)
          Behaviors.same
      }
    }

  private def pulling(
    liveId: String,
    parent: ActorRef[LiveManager.LiveCommand],
    pullClient: PullStreamClient,
    mediaPlayer: MediaPlayer,
    //    joinInfo: Option[JoinInfo],
    mediaSink: Pipe.SinkChannel,
    audienceScene: Option[AudienceScene],
    hostScene: Option[HostScene],
    pullInfo: PullInfo,
    index: Int
  )(
    implicit timer: TimerScheduler[PullCommand],
    stashBuffer: StashBuffer[PullCommand]
  ): Behavior[PullCommand] =
    Behaviors.receive[PullCommand] { (ctx, msg) =>
      msg match {
        case msg: PullStreamData =>
          if (msg.data.nonEmpty) {
            try {
//              log.debug(s"StreamPuller-$liveId pull-${msg.data.length}.")
              mediaSink.write(ByteBuffer.wrap(msg.data))
              //              log.debug(s"StreamPuller-$liveId  write success.")
              ctx.self ! SwitchBehavior("pulling", pulling(liveId, parent, pullClient, mediaPlayer, mediaSink, audienceScene, hostScene, pullInfo, index))
            } catch {
              case ex: Exception =>
                log.warn(s"sink write pulled data error: $ex. Stop StreamPuller-$liveId")
                ctx.self ! StopPull
            }
          } else {
            log.debug(s"StreamPuller-$liveId pull null.")
            ctx.self ! SwitchBehavior("pulling", pulling(liveId, parent, pullClient, mediaPlayer, mediaSink, audienceScene, hostScene, pullInfo,index))
          }
          busy(liveId, parent, pullClient, mediaPlayer, audienceScene, hostScene, pullInfo, index)

        case StopPull =>
          log.info(s"StreamPuller-$liveId is stopping while pulling.")
          val playId = if(index == 1) Ids.getPlayId(AudienceStatus.CONNECT, roomId = pullInfo.roomId) else Ids.getPlayId(AudienceStatus.CONNECT2Third, roomId = pullInfo.roomId)
          println("1111111111111111111111" + playId)
          mediaPlayer.stop(playId, audienceScene.get.resetBack)
          try pullClient.close()
          catch {
            case  e: Exception =>
              log.info(s"StreamPuller-$liveId close error: $e")
          }
          Behaviors.same

        case CloseSuccess =>
          log.info(s"StreamPuller-$liveId stopped.")
          parent ! LiveManager.PullerStopped(liveId)
          Behaviors.stopped

        case msg: StreamStop =>
          log.info(s"Pull stream-${msg.liveId} thread has been closed.")
          parent ! LiveManager.PullerStopped(liveId)
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("播放中的流已被关闭!")
            hostScene.foreach(h => h.listener.stopMeeting())
//            audienceScene.foreach(a => a.listener.quitJoin(a.getRoomInfo.roomId))
          }
          Behaviors.stopped

        case PullStream =>
          Behaviors.same

        case x =>
          log.warn(s"unknown msg in pulling: $x")
          Behaviors.unhandled
      }
    }


  private def busy(
    liveId: String,
    parent: ActorRef[LiveManager.LiveCommand],
    pullClient: PullStreamClient,
    mediaPlayer: MediaPlayer,
    audienceScene: Option[AudienceScene],
    hostScene: Option[HostScene],
    pullInfo: PullInfo,
    index: Int
  )
    (
      implicit stashBuffer: StashBuffer[PullCommand],
      timer: TimerScheduler[PullCommand]
    ): Behavior[PullCommand] =
    Behaviors.receive[PullCommand] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
          Behaviors.stopped

        case StopPull =>
          log.info(s"StreamPuller-$liveId is stopping while busy.")
          val playId = if(index == 1) Ids.getPlayId(AudienceStatus.CONNECT, roomId = pullInfo.roomId) else Ids.getPlayId(AudienceStatus.CONNECT2Third, roomId = pullInfo.roomId)
          mediaPlayer.stop(playId, audienceScene.get.resetBack)
          try pullClient.close()
          catch {
            case  e: Exception =>
              log.info(s"StreamPuller-$liveId close error: $e")
          }
          Behaviors.same

        case CloseSuccess =>
          log.info(s"StreamPuller-$liveId stopped.")
          parent ! LiveManager.PullerStopped(liveId)
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }

}
