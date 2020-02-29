package org.seekloud.VideoMeeting.capture.core

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.{File, OutputStream}
import java.nio.ShortBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.{LinkedBlockingDeque, ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import javax.imageio.ImageIO
import org.bytedeco.javacv.{FFmpegFrameRecorder, Java2DFrameConverter}
import org.seekloud.VideoMeeting.capture.Boot
import org.seekloud.VideoMeeting.capture.sdk.MediaCapture.executor
import org.seekloud.VideoMeeting.capture.protocol.Messages
import org.seekloud.VideoMeeting.capture.protocol.Messages.{EncodeException, EncoderType}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: TangYaruo
  * Date: 2019/8/28
  * Time: 17:54
  * Description: 用来存储视频对视频的编码
  */
object EncodeActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val noSoundImg = ImageIO.read(Boot.getClass.getResourceAsStream("/img/noSound.png"))

  private val noImageImg = ImageIO.read(Boot.getClass.getResourceAsStream("/img/noImage.png"))

  private val imageConverter = new Java2DFrameConverter()

  sealed trait Command

  final case object StartEncodeLoop extends Command

  final case object EncodeLoop extends Command

  final case class EncodeSamples(sampleRate: Int, channel: Int, samples: ShortBuffer) extends Command

  final case object StopEncode extends Command

  final case class HostBan4Encode(image:Boolean, sound:Boolean) extends Command

  final case class ChangeEncoder(encoder: FFmpegFrameRecorder, needImage:Boolean, needSound:Boolean) extends Command

  final case object StartNewEncoder extends Command

  def create(
    replyTo: ActorRef[Messages.ReplyToCommand],
    encodeType: EncoderType.Value,
    encoder: FFmpegFrameRecorder,
    imageCache: LinkedBlockingDeque[Messages.LatestFrame],
    needImage: Boolean,
    needSound: Boolean
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"EncodeActor-$encodeType is starting...")
      Future {
        log.info(s"Encoder-$encodeType is starting...")
        encoder.startUnsafe()
        log.info(s"Encoder-$encodeType started.")
      }.onComplete {
        case Success(_) =>
          ctx.self ! StartEncodeLoop
        case Failure(e) =>
          encodeType match {
            case EncoderType.STREAM =>
              log.info(s"streamEncoder start failed: $e")
              replyTo ! Messages.StreamCannotBeEncoded
            case EncoderType.FILE =>
              log.info(s"fileEncoder start failed: $e")
              replyTo ! Messages.CannotSaveToFile
              ctx.self ! StopEncode
            case EncoderType.BILIBILI =>
              log.info(s"fileEncoder start failed: $e")
              replyTo ! Messages.CannotRecordToBiliBili
          }
      }
      working(replyTo, encodeType, encoder, imageCache, needImage, needSound)
    }

  private def working(
    replyTo: ActorRef[Messages.ReplyToCommand],
    encodeType: EncoderType.Value,
    encoder: FFmpegFrameRecorder,
    imageCache: LinkedBlockingDeque[Messages.LatestFrame],
    needImage: Boolean,
    needSound: Boolean,
    encodeLoop: Option[ScheduledFuture[_]] = None,
    encodeExecutor: Option[ScheduledThreadPoolExecutor] = None,
    frameNumber: Int = 0
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StartNewEncoder =>
          Future {
            log.info(s"Encoder-$encodeType is starting...")
            encoder.startUnsafe()
            log.info(s"Encoder-$encodeType started.")
          }.onComplete {
            case Success(_) =>
              ctx.self ! StartEncodeLoop
            case Failure(e) =>
              encodeType match {
                case EncoderType.STREAM =>
                  log.info(s"streamEncoder start failed: $e")
                  replyTo ! Messages.StreamCannotBeEncoded
                case EncoderType.FILE =>
                  log.info(s"fileEncoder start failed: $e")
                  replyTo ! Messages.CannotSaveToFile
                  ctx.self ! StopEncode
                case EncoderType.BILIBILI =>
                  log.info(s"fileEncoder start failed: $e")
                  replyTo ! Messages.CannotRecordToBiliBili
              }
          }
          Behaviors.same

        case StartEncodeLoop =>
          log.debug(s"frameRate: ${encoder.getFrameRate}, interval: ${1000 / encoder.getFrameRate}")

          val encodeLoopExecutor = new ScheduledThreadPoolExecutor(1)
          val loop = encodeLoopExecutor.scheduleAtFixedRate(
            () => {
              ctx.self ! EncodeLoop
            },
            0,
            ((1000.0 / encoder.getFrameRate) * 1000).toLong,
            TimeUnit.MICROSECONDS
          )
          working(replyTo, encodeType, encoder, imageCache, needImage, needSound, Some(loop), Some(encodeLoopExecutor), frameNumber)

        case EncodeLoop =>
          if(needImage) {
            try {
              val latestImage = imageCache.peek()
              if (latestImage != null){
                encoder.setTimestamp((frameNumber * (1000.0 / encoder.getFrameRate) * 1000).toLong)
                if(needSound) {
                  encoder.record(latestImage.frame)
                }else {
                  val iw = latestImage.frame.imageWidth
                  val ih = latestImage.frame.imageHeight
                  val bImg = imageConverter.convert(latestImage.frame)
                  bImg.getGraphics.drawImage(noSoundImg, iw * 7/8, ih * 7/8, iw/8, ih/8, null)
                  encoder.record(imageConverter.convert(bImg))
                }
              }
            }  catch {
              case ex: Exception =>
                log.error(s"[$encodeType] encode image frame error: $ex")
                if(ex.getMessage.startsWith("av_interleaved_write_frame() error")){
                replyTo ! EncodeException(ex)
                ctx.self ! StopEncode
                }
            }
          } else{
            encoder.setTimestamp((frameNumber * (1000.0 / encoder.getFrameRate) * 1000).toLong)
            encoder.record(imageConverter.convert(noImageImg))
          }
          working(replyTo, encodeType, encoder, imageCache, needImage, needSound, encodeLoop, encodeExecutor, frameNumber + 1)

        case msg: EncodeSamples =>
          if (encodeLoop.nonEmpty && needSound) {
            try {
                encoder.recordSamples(msg.sampleRate, msg.channel, msg.samples)
            } catch {
              case ex: Exception =>
                log.warn(s"Encoder-$encodeType encode samples error: $ex")
            }
          }
          Behaviors.same

        case ChangeEncoder(newEncoder, newNeedImage, newNeedSound) =>
          encodeLoop.foreach(_.cancel(false))
          encodeExecutor.foreach(_.shutdown())
          try {
            encoder.releaseUnsafe()
            log.info(s"release encode resources.")
            ctx.self ! StartNewEncoder
          } catch {
            case ex: Exception =>
              log.warn(s"release encode error: $ex")
              ex.printStackTrace()
          }
          working(replyTo, encodeType, newEncoder, imageCache, newNeedImage, newNeedSound, None, None, 0)

        case msg:HostBan4Encode =>
          log.debug(s"change state, do not change encoder")
          working(replyTo, encodeType, encoder, imageCache, msg.image, msg.sound, encodeLoop, encodeExecutor, frameNumber)

        case StopEncode =>
          log.info(s"encoding stopping...")
          encodeLoop.foreach(_.cancel(false))
          encodeExecutor.foreach(_.shutdown())
          try {
            encoder.releaseUnsafe()
            log.info(s"release encode resources.")
          } catch {
            case ex: Exception =>
              log.warn(s"release encode error: $ex")
              ex.printStackTrace()
          }
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in working: $x")
          Behaviors.unhandled
      }
    }

}
