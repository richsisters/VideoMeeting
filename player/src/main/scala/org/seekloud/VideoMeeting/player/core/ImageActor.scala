package org.seekloud.VideoMeeting.player.core

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import javafx.application.Platform
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import org.seekloud.VideoMeeting.player.protocol.Messages
import org.seekloud.VideoMeeting.player.protocol.Messages._
import org.slf4j.LoggerFactory

import scala.collection.immutable
import concurrent.duration._

/**
  * Author: zwq
  * Date: 2019/8/28
  * Time: 21:59
  */
object ImageActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  private var debug = true

  private def debug(str: String): Unit = {
    if (debug) log.debug(str)
  }

  trait ImageCmd

  final case object PausePlayImage extends ImageCmd

  final case object ContinuePlayImage extends ImageCmd

  final case class AudioPlayedTimeUpdated(audioPlayedTime: Long) extends ImageCmd // in us

  private final object TryPlayImageTick extends ImageCmd

  final object FRAME_RATE_TIMER_KEY

  //init state
  private[this] var frameRate = -1
  private var hasPictureTs = false
  private var hasAudio = false
  private var needSound = true
  //  private var hasTimer = false
  //  val waiting = new Image("img/waiting.gif")

  def create(
    id: String,
    gc: GraphicsContext,
    playerGrabber: ActorRef[PlayerGrabber.MonitorCmd],
    _frameRate: Int,
    _hasPictureTs: Boolean,
    _hasAudio: Boolean,
    _needSound: Boolean,
    isDebug: Boolean = true
  ): Behavior[ImageCmd] = Behaviors.setup { context =>

    log.info(s"ImageActor-$id is starting......")
    debug = isDebug
    frameRate = _frameRate
    hasPictureTs = _hasPictureTs
    hasAudio = _hasAudio
    needSound = _needSound
    //    debug(s"frameRate: $frameRate, timeBetweenFrames: ${(1000 / frameRate) millis}")
    Behaviors.withTimers[ImageCmd] { implicit timer =>
      //      log.info(s"start Image Timer in ImageActor-$id.")
      timer.startPeriodicTimer(
        FRAME_RATE_TIMER_KEY,
        TryPlayImageTick,
        (1000 / frameRate) millis //interval between two frames
      )
      //      hasTimer = true
      playing(id, gc, playerGrabber, immutable.Queue[AddPicture](), 0, 0L, 0L, 0L)
    }
  }


  def playing(
    id: String,
    gc: GraphicsContext,
    playerGrabber: ActorRef[PlayerGrabber.MonitorCmd],
    queue: immutable.Queue[AddPicture],
    playedImages: Int,
    lastPlayTimeInWallClock: Long,
    ImagePlayedTime: Long,
    audioPlayedTime: Long
  )(
    implicit timer: TimerScheduler[ImageCmd]
  ): Behavior[ImageCmd] = Behaviors.receive { (ctx, msg) =>

    msg match {
      case PausePlayImage =>
        //todo check detail 播放时长计算是否正确
        log.info(s"ImageActor-$id got PausePlay.")
        timer.cancel(FRAME_RATE_TIMER_KEY)
        //        hasTimer = false
        log.info(s"ImageActor-$id cancel Image Timer.")
        Behaviors.same

      case ContinuePlayImage =>
        log.info(s"ImageActor-$id got ContinuePlay.")
        log.info(s"start Image Timer in ImageActor-$id.")
        timer.startPeriodicTimer(
          FRAME_RATE_TIMER_KEY,
          TryPlayImageTick,
          (1000 / frameRate) millis
        )
        //        hasTimer = true
        Behaviors.same

      case m: AddPicture =>
        //        debug(s"PicturePlayActor got $m")
        val newQueue = queue.enqueue(m)
        playing(
          id,
          gc,
          playerGrabber,
          newQueue,
          playedImages,
          lastPlayTimeInWallClock,
          ImagePlayedTime,
          audioPlayedTime
        )

      case AudioPlayedTimeUpdated(apt) =>

        //        debug(s"--------------- audioTime[$apt] - videoTime[$videoPlayedTime] = ${apt - videoPlayedTime}")
        playing(
          id,
          gc,
          playerGrabber,
          queue,
          playedImages,
          lastPlayTimeInWallClock,
          ImagePlayedTime,
          apt
        )

      case TryPlayImageTick =>
        if (queue.length < 2) playerGrabber ! PlayerGrabber.AskPicture(Left(ctx.self))
//        if (needSound && hasAudio && ImagePlayedTime - audioPlayedTime > 50000) {
//          println(s"image play time over")
//          Behaviors.same
//        } else {
          if (queue.nonEmpty) {
            if (needSound && hasAudio && (audioPlayedTime != Long.MaxValue) && audioPlayedTime - ImagePlayedTime > 50000) {
              ctx.self ! TryPlayImageTick
            }
            val (newQueue, newImagePlayedTime, playTimeInWallClock) = drawPicture(id, gc, queue, ImagePlayedTime)
            playing(
              id,
              gc,
              playerGrabber,
              newQueue,
              playedImages + 1,
              playTimeInWallClock,
              newImagePlayedTime,
              audioPlayedTime)
          } else {
            playerGrabber ! PlayerGrabber.AskPicture(Left(ctx.self))
            Behaviors.same
          }

//        }

      case msg: PictureFinish =>
        log.info(s"ImageActor-$id got PictureFinish")
        timer.cancelAll()
        msg.resetFunc.foreach(f => f())
        //        hasTimer = false
        log.info(s"ImageActor-$id cancel Image Timer.")
        Behaviors.stopped

      case x =>
        log.warn(s"unknown msg in playing: $x")
        Behaviors.unhandled
    }

  }


  private def drawPicture(id: String, gc: GraphicsContext, queue: immutable.Queue[AddPicture], imagePlayedTime: Long) = {
    //draw picture
    val (AddPicture(img, pictureTs), newQueue) = queue.dequeue
    val playTimeInWallClock = System.currentTimeMillis() //实际播放时间
    Platform.runLater { () =>
      val sW = gc.getCanvas.getWidth
      val sH = gc.getCanvas.getHeight
      val w = img.getWidth
      val h = img.getHeight
      if (id.contains("--")) { //三人连线状态
//        if (w / sW > h / sH) {
          gc.drawImage(img, 0, sH / 2, sW / 2, sH / 2)
//        } else {
//          gc.drawImage(img, (sW - w * sH / h) / 2 + sW / 2, 0, (w * sH / h) / 2, sH / 2)
//        }

      }else if (id.contains("-")) { //连线状态
        if (w / sW > h / sH) {
          gc.drawImage(img, sW / 2, 0, sW / 2, sH / 2)
        } else {
          gc.drawImage(img, (sW - w * sH / h) / 2 + sW / 2, 0, (w * sH / h) / 2, sH / 2)
        }

      } else { //普通观看
        if (w / sW > h / sH) {
          gc.drawImage(img, 0, (sH - h * sW / w) / 2, sW, h * sW / w)
        } else {
          gc.drawImage(img, (sW - w * sH / h) / 2, 0, w * sH / h, sH)
        }
      }

    }
    val newImagePlayedTime = //时间戳
      if (hasPictureTs) {
        pictureTs
      } else {
        imagePlayedTime + (1000000 / frameRate)
      }
    (newQueue, newImagePlayedTime, playTimeInWallClock)
  }

  //  private def drawWaiting(gc: GraphicsContext): Unit = {
  //    val sWidth = gc.getCanvas.getWidth
  //    val sHeight = gc.getCanvas.getHeight
  //    gc.drawImage(waiting, sWidth/2 +25, sHeight/2 + 25, 50, 50)
  //  }

}
