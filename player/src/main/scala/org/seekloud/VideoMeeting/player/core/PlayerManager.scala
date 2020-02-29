package org.seekloud.VideoMeeting.player.core

import java.io.{File, InputStream}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import javafx.scene.canvas.GraphicsContext
import org.seekloud.VideoMeeting.player.core.PlayerGrabber._
import org.seekloud.VideoMeeting.player.core.ImageActor._
import org.seekloud.VideoMeeting.player.core.SoundActor._
import org.seekloud.VideoMeeting.player.protocol.Messages
import org.seekloud.VideoMeeting.player.protocol.Messages._
import org.seekloud.VideoMeeting.player.util.RecordUtil
import org.seekloud.VideoMeeting.player.sdk.MediaPlayer.executor
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
  * User: TangYaruo
  * Date: 2019/8/28
  * Time: 15:34
  *
  * @author zwq
  */
object PlayerManager {

  private val log = LoggerFactory.getLogger(this.getClass)
  private var debug = true
  private var needTime = true
  private var timeGetter: () => Long = _

  private def debug(str: String): Unit = {
    log.debug(str)
  }

  sealed trait SupervisorCmd

  case class MediaSettings(
    imageWidth: Int = 640,
    imageHeight: Int = 360,
    frameRate: Int = 30,
    needImage: Boolean = true,
    needSound: Boolean = true,
    outputFile: Option[File] = None
  )

  case class MediaInfo(
    playId: String,
    graphContext: Option[GraphicsContext],
    replyTo: ActorRef[Messages.RTCommand],
    hasVideo: Boolean,
    hasAudio: Boolean,
    format: String,
    frameRate: Double = -1.0,
    width: Int = -1,
    height: Int = -1,
    sampleRate: Int = -1,
    channels: Int = -1
  ) extends SupervisorCmd

  final case class GrabberFailInit(playId: String, replyTo: ActorRef[Messages.RTCommand], ex: Throwable) extends SupervisorCmd

  final case class StartPlay(
    playId: String,
    replyTo: ActorRef[Messages.RTCommand],
    graphContext: Option[GraphicsContext],
    input: Either[String, InputStream],
    settings: MediaSettings
  ) extends SupervisorCmd

  final case class StopPlay(
    playId: String,
    reSetFunc: () => Unit
  ) extends SupervisorCmd


  final case class SetTimeGetter(playId: String, func: () => Long) extends SupervisorCmd


  def create(isDebug: Boolean, needTimestamp: Boolean): Behavior[SupervisorCmd] =
    Behaviors.setup[SupervisorCmd] { ctx =>
      log.info(s"PlayerManager is starting...")
      debug = isDebug
      needTime = needTimestamp
      implicit val stashBuffer: StashBuffer[SupervisorCmd] = StashBuffer[SupervisorCmd](Int.MaxValue)
      Behaviors.withTimers[SupervisorCmd] { implicit timer =>
        idle(mutable.HashMap.empty, mutable.HashMap.empty, mutable.HashMap.empty, mutable.HashMap.empty, mutable.HashMap.empty, mutable.HashMap.empty)
      }
    }


  private def idle(
    mediaSettingsMap: mutable.HashMap[String, MediaSettings], //playId -> mediaSettings
    gcMap: mutable.HashMap[String, GraphicsContext], //playId -> gc
    playerGrabberMap: mutable.HashMap[String, (ActorRef[PlayerGrabber.MonitorCmd], Either[String, InputStream])], //playId -> (input, playerGrabber)
    imageActorMap: mutable.HashMap[String, ActorRef[ImageActor.ImageCmd]], //playId -> imageActor
    soundActorMap: mutable.HashMap[String, ActorRef[SoundActor.SoundCmd]], //playId -> soundActor
    replyToMap: mutable.HashMap[String, ActorRef[Messages.RTCommand]] // playId -> replyTo
  ): Behavior[SupervisorCmd] =
    Behaviors.receive[SupervisorCmd] { (ctx, msg) =>
      msg match {
        case msg: SetTimeGetter =>
          timeGetter = msg.func
          playerGrabberMap.get(msg.playId).foreach(_._1 ! PlayerGrabber.SetTimeGetter(msg.func))
          Behaviors.same

        case StartPlay(playId, replyTo, gc, input, settings) =>
          log.info(s"StartPlay video - $input")

          if (replyToMap.get(playId).isEmpty) {
            log.debug(s"save replyTo actor to map.")
            replyToMap.put(playId, replyTo)
          }

          val playerGrabber = getPlayerGrabber(replyTo, gc, ctx, playId, input, settings)
          playerGrabberMap.put(playId, (playerGrabber, input))

          if (!mediaSettingsMap.contains(playId)) {
            mediaSettingsMap.put(playId, settings)
          }
          if (gc.nonEmpty && !gcMap.contains(playId)) {
            gcMap.put(playId, gc.get)
          }

          idle(mediaSettingsMap, gcMap, playerGrabberMap, imageActorMap, soundActorMap, replyToMap)

        case msg@MediaInfo(playId, gc, replyTo, hasVideo, hasAudio, format, frameRate, width, height, sampleRate, channels) =>
          val playerGrabberOpt = playerGrabberMap.get(playId)
          if (playerGrabberOpt.nonEmpty) {
            replyTo ! GrabberInitialed(playerGrabberMap(playId)._1, msg, mediaSettingsMap(playId), gc)
            val hasTs = format match {
              case "flv" => true
              case "mpegts" => true
              case "mp4" => true
              case "h264" => false
              case "aac" => false
              case x =>
                println(s"warning: unknown format[$x].")
                false
            }
            val nbSample = 1024
            if (gc.nonEmpty) { //需要自主播放
              val imageActorOpt =
                if (mediaSettingsMap(playId).needImage && hasVideo) { //需要播放画面 && hasVideo
                  if (imageActorMap.get(playId).isEmpty) {
                    val imageActorName = s"imageActor-$playId"
                    val imageActor = Some(ctx.spawn(ImageActor.create(playId, gc.get, playerGrabberOpt.get._1, frameRate.toInt, hasTs, hasAudio, mediaSettingsMap(playId).needSound, debug), imageActorName))
                    imageActorMap.put(playId, imageActor.get)
                    imageActor
                  } else {
                    imageActorMap.get(playId)
                  }
                } else {
                  None
                }
              val soundActorOpt =
                if (mediaSettingsMap(playId).needSound && hasAudio) { //需要播放声音 && hasAudio
                  if (soundActorMap.get(playId).isEmpty) {
                    val soundActorName = s"audioPlayer-$playId"
                    val soundActor = Some(ctx.spawn(SoundActor.create(playId, playerGrabberOpt.get._1, sampleRate, channels, nbSample, imageActorOpt, debug), soundActorName))
                    soundActorMap.put(playId, soundActor.get)
                    soundActor
                  } else {
                    soundActorMap.get(playId)
                  }
                } else {
                  None
                }
            } else {
              debug(s"不需要自主播放")
            }
          } else {
            log.error(s"playerGrabber-$playId not exist in the map!!!")
          }
          Behaviors.same


        case GrabberFailInit(playId, replyTo, ex) =>
          // todo
          replyTo ! GrabberInitFailed(playId, ex)
          log.debug(s"PlayerGrabber-$playId init failed: $ex")
          if (playerGrabberMap.contains(playId)) {
            playerGrabberMap -= playId
          }
          idle(mediaSettingsMap, gcMap, playerGrabberMap, imageActorMap, soundActorMap, replyToMap)

        case StopPlay(playId, reSetFunc) =>
          if (mediaSettingsMap.contains(playId)) {
            mediaSettingsMap.remove(playId)
          }
          if (gcMap.contains(playId)) {
            gcMap.remove(playId)
          }
          if (playerGrabberMap.contains(playId)) {
            playerGrabberMap(playId)._1 ! StopGrab(reSetFunc)
            playerGrabberMap.remove(playId)
          }
          if (imageActorMap.contains(playId)) {
            imageActorMap(playId) ! PictureFinish(Some(reSetFunc))
            imageActorMap.remove(playId)
          }
          if (soundActorMap.contains(playId)) {
            soundActorMap(playId) ! SoundFinish
            soundActorMap.remove(playId)
          }
          if (replyToMap.contains(playId)) {
            replyToMap(playId) ! StopVideoPlayer
            replyToMap.remove(playId)
          }
          Behaviors.same

        case x =>
          log.warn(s"unknown msg in idle: $x")
          Behaviors.unhandled
      }

    }


  private def getPlayerGrabber(
    replyTo: ActorRef[Messages.RTCommand],
    graphContext: Option[GraphicsContext],
    ctx: ActorContext[SupervisorCmd],
    playId: String,
    input: Either[String, InputStream],
    settings: MediaSettings
  ) = {
    val childName = s"playerGrabber-$playId-${System.currentTimeMillis()}"
    log.debug(s"create PlayerGrabber-$playId")
    val playerGrabber = ctx.child(childName).getOrElse {
      log.debug(s"getting player grabber.")
      ctx.spawn(PlayerGrabber.create(playId, replyTo, graphContext, input, ctx.self, settings, debug, needTime, timeGetter), childName)
    }.unsafeUpcast[PlayerGrabber.MonitorCmd]
    playerGrabber
  }

}
