package org.seekloud.VideoMeeting.capture.core

import java.io.{File, OutputStream}
import java.util.concurrent.LinkedBlockingDeque

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import javax.sound.sampled._
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avcodec._
import org.bytedeco.javacv.{FFmpegFrameGrabber, FFmpegFrameRecorder, JavaFXFrameConverter1, OpenCVFrameGrabber}
import org.seekloud.VideoMeeting.capture.processor.ImageConverter
import org.seekloud.VideoMeeting.capture.protocol.Messages
import org.seekloud.VideoMeeting.capture.protocol.Messages._
import org.seekloud.VideoMeeting.capture.sdk.MediaCapture.{blockingDispatcher, executor}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}
import concurrent.duration._
import language.postfixOps

/**
  * User: TangYaruo
  * Date: 2019/8/28
  * Time: 11:57
  */
object CaptureManager {

  private val log = LoggerFactory.getLogger(this.getClass)
  private var debug: Boolean = true
  private var needTimeMark: Boolean = false
  var timeGetter: () => Long = _

  private def debug(msg: String): Unit = {
    if (debug) log.debug(msg)
  }

  /*Data*/
  private val latestFrame = new java.util.concurrent.LinkedBlockingDeque[Messages.LatestFrame](1)
  //  private val latestSound = new java.util.concurrent.LinkedBlockingDeque[Messages.LatestSound]()
  def setLatestFrame(): Unit = latestFrame.clear()

  private val imageConverter = new ImageConverter

  //  private val imageConverter = new JavaFXFrameConverter()

  trait Command

  case class MediaSettings(
    imageWidth: Int,
    imageHeight: Int,
    frameRate: Int,
    outputBitrate: Int,
    needImage: Boolean,
    sampleRate: Float,
    sampleSizeInBits: Int,
    channels: Int,
    needSound: Boolean,
    camDeviceIndex: Int,
    audioDeviceIndex: Int
  )

  private case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class CameraGrabberStarted(grabber: OpenCVFrameGrabber) extends Command

  final case class StartCameraFailed(ex: Throwable) extends Command

  final case class DesktopGrabberStarted(grabber: FFmpegFrameGrabber) extends Command

  final case class StartedDesktopFailed(ex: Throwable) extends Command

  final case class TargetDataLineStarted(line: TargetDataLine) extends Command

  final case class StartTargetDataLineFailed(ex: Throwable) extends Command

  final case class SetTimerGetter(func: () => Long) extends Command

  final case object StartCapture extends Command

  final case object StopCapture extends Command

  final case object StopDelay extends Command

  final case object ShowDesktop extends Command

  final case object ShowPerson extends Command

  final case object ShowBoth extends Command

  private object STOP_DELAY_TIMER_KEY


  def create(
    replyTo: ActorRef[Messages.ReplyToCommand],
    mediaSettings: MediaSettings,
    outputStream: Option[OutputStream],
    outputFile: Option[File],
    isDebug: Boolean,
    needTimestamp: Boolean
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"CaptureManager is starting...")
      debug = isDebug
      needTimeMark = needTimestamp
      if(needTimestamp) imageConverter.setNeedTimestamp()
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)

      if (mediaSettings.needImage) {

        val cameraGrabber = new OpenCVFrameGrabber(mediaSettings.camDeviceIndex)
        cameraGrabber.setImageWidth(mediaSettings.imageWidth)
        cameraGrabber.setImageHeight(mediaSettings.imageHeight)

        Future {
          debug(s"cameraGrabber-${mediaSettings.camDeviceIndex} is starting...")
          cameraGrabber.start()
          debug(s"cameraGrabber-${mediaSettings.camDeviceIndex} started.")
          cameraGrabber
        }.onComplete {
          case Success(grabber) => ctx.self ! CameraGrabberStarted(grabber)
          case Failure(ex) => ctx.self ! StartCameraFailed(ex)
        }

        val desktopGrabber = new FFmpegFrameGrabber("desktop")
        desktopGrabber.setFormat("gdigrab")
//        desktopGrabber.setImageWidth(mediaSettings.imageWidth)
//        desktopGrabber.setImageHeight(mediaSettings.imageHeight)
        Future{
          debug(s"desktopGrabber is starting...")
          desktopGrabber.start()
          debug(s"desktopGrabber started.")
          desktopGrabber
        }.onComplete{
          case Success(grabber) => ctx.self ! DesktopGrabberStarted(grabber)
          case Failure(ex) => ctx.self ! StartedDesktopFailed(ex)
        }
      }

      if (mediaSettings.needSound) {
        val audioFormat = new AudioFormat(mediaSettings.sampleRate, mediaSettings.sampleSizeInBits, mediaSettings.channels, true, false)
        val minfoSet: Array[Mixer.Info] = AudioSystem.getMixerInfo
        val mixer: Mixer = AudioSystem.getMixer(minfoSet(mediaSettings.audioDeviceIndex))
        val dataLineInfo = new DataLine.Info(classOf[TargetDataLine], audioFormat)

        Future {
          debug(s"audioSystem is starting...")
          val line = AudioSystem.getLine(dataLineInfo).asInstanceOf[TargetDataLine]
          line.open(audioFormat)
          line.start()
          debug(s"audioSystem started")
          line
        }.onComplete {
          case Success(line) => ctx.self ! TargetDataLineStarted(line)
          case Failure(ex) => ctx.self ! StartTargetDataLineFailed(ex)
        }
      }

      Behaviors.withTimers[Command] { implicit timer =>
        init(replyTo, mediaSettings, outputStream, outputFile)
      }
    }


  private def init(
    replyTo: ActorRef[Messages.ReplyToCommand],
    mediaSettings: MediaSettings,
    outputStream: Option[OutputStream],
    outputFile: Option[File],
    grabber: Option[OpenCVFrameGrabber] = None,
    desktopGrabber: Option[FFmpegFrameGrabber] = None,
    line: Option[TargetDataLine] = None,
    imageFail: Boolean = false,
    soundFail: Boolean = false
  )(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command]
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case msg: SetTimerGetter =>
          timeGetter = msg.func
          imageConverter.setTimeGetter(msg.func)
          Behaviors.same

        case CameraGrabberStarted(g) =>
          log.info(s"Start camera success.")
          if ((line.nonEmpty || soundFail || !mediaSettings.needSound)) {
            ctx.self ! StartCapture
            if (soundFail)
              replyTo ! Messages.CannotAccessSound(ctx.self)
            else
              replyTo ! Messages.CaptureStartSuccess(ctx.self)
          }
          init(replyTo, mediaSettings, outputStream, outputFile, Some(g), desktopGrabber, line, imageFail, soundFail)

        case TargetDataLineStarted(l) =>
          log.info(s"Start targetDataLine success.")
          if (grabber.nonEmpty || imageFail || !mediaSettings.needImage) {
            ctx.self ! StartCapture
            if (imageFail)
              replyTo ! Messages.CannotAccessImage(ctx.self)
            else
              replyTo ! Messages.CaptureStartSuccess(ctx.self)
          }
          init(replyTo, mediaSettings, outputStream, outputFile, grabber, desktopGrabber, Some(l), imageFail, soundFail)

        case StartCameraFailed(ex) =>
          log.info(s"Start Camera failed: $ex")
          if (line.nonEmpty) {
            ctx.self ! StartCapture
            replyTo ! Messages.CannotAccessImage(ctx.self)
          }
          else if (soundFail || !mediaSettings.needSound) {
            ctx.self ! StopCapture
          }
          init(replyTo, mediaSettings, outputStream, outputFile, grabber, desktopGrabber, line, imageFail = true, soundFail)

        case StartTargetDataLineFailed(ex) =>
          log.info(s"Start targetDataLine failed: $ex")
          if (grabber.nonEmpty) {
            ctx.self ! StartCapture
            replyTo ! Messages.CannotAccessSound(ctx.self)
          }
          else if (imageFail || !mediaSettings.needImage) {
            ctx.self ! StopCapture
          }
          init(replyTo, mediaSettings, outputStream, outputFile, grabber, desktopGrabber, line, imageFail, soundFail = true)

        case DesktopGrabberStarted(g) =>
          log.info(s"Start desktop grab success.")
          if ((line.nonEmpty || soundFail || !mediaSettings.needSound) && grabber.nonEmpty) {
            ctx.self ! StartCapture
            if (soundFail)
              replyTo ! Messages.CannotAccessSound(ctx.self)
            else
              replyTo ! Messages.CaptureStartSuccess(ctx.self)
          }
          init(replyTo, mediaSettings, outputStream, outputFile, grabber, Some(g), line, imageFail, soundFail)

        case StartedDesktopFailed(ex) =>
          log.info(s"Start desktop failed: $ex")
          replyTo ! Messages.CannotAccessDesktop(ctx.self)
          Behaviors.same

        case StartCapture =>
          val encodeActorMap = mutable.HashMap[EncoderType.Value, ActorRef[EncodeActor.Command]]()
          val montageActor = getMontageActor(ctx, latestFrame, mediaSettings)

          val imageCaptureOpt = if (grabber.nonEmpty) {
            Some(getImageCapture(ctx, grabber.get, montageActor, mediaSettings.frameRate, debug))
          } else None

          val desktopCaptureOpt = if(desktopGrabber.nonEmpty){
            Some(getDesktopCapture(ctx, desktopGrabber.get, montageActor, mediaSettings.frameRate, debug))
          }else None

          val soundCaptureOpt = if (line.nonEmpty) {
            Some(getSoundCapture(ctx, replyTo, line.get, encodeActorMap, mediaSettings.frameRate, mediaSettings.sampleRate, mediaSettings.channels, mediaSettings.sampleSizeInBits, debug))
          } else None

          if (outputStream.nonEmpty) {
            val streamEncoder = if (grabber.nonEmpty && line.isEmpty) { // image only
              new FFmpegFrameRecorder(outputStream.get, grabber.get.getImageWidth, grabber.get.getImageHeight)
            } else if (grabber.isEmpty && line.nonEmpty) { //sound only
              new FFmpegFrameRecorder(outputStream.get, mediaSettings.channels)
            } else {
              new FFmpegFrameRecorder(outputStream.get, grabber.get.getImageWidth, grabber.get.getImageHeight, mediaSettings.channels)
            }
            setEncoder(ctx, mediaSettings, streamEncoder, EncoderType.STREAM, imageCaptureOpt, soundCaptureOpt, encodeActorMap, replyTo)
          }

          if (outputFile.nonEmpty) {
            val fileEncoder = if (grabber.nonEmpty && line.isEmpty) { // image only
              new FFmpegFrameRecorder(outputFile.get, grabber.get.getImageWidth, grabber.get.getImageHeight)
            } else if (grabber.isEmpty && line.nonEmpty) { //sound only
              new FFmpegFrameRecorder(outputFile.get, mediaSettings.channels)
            } else {
              new FFmpegFrameRecorder(outputFile.get, grabber.get.getImageWidth, grabber.get.getImageHeight, mediaSettings.channels)
            }
            setEncoder(ctx, mediaSettings, fileEncoder, EncoderType.FILE, imageCaptureOpt, soundCaptureOpt, encodeActorMap, replyTo)
          }

          stashBuffer.unstashAll(ctx, idle(replyTo, mediaSettings, grabber, line, imageCaptureOpt, desktopCaptureOpt, montageActor, soundCaptureOpt, None, encodeActorMap))

        case StopCapture =>
          log.info(s"CaptureManager stopped in init.")
          replyTo ! Messages.CaptureStartFailed
          Behaviors.stopped

        case StopMediaCapture =>
          log.info(s"Your stop command executed. Capture stopped in init.")
          try {
            grabber.foreach(_.close())
            line.foreach {
              l =>
                l.stop()
                l.flush()
                l.close()
            }
          } catch {
            case ex: Exception =>
              log.warn(s"release resources in init error: $ex")
          }
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in init: $x")
          stashBuffer.stash(x)
          Behaviors.same
      }
    }

  private def idle(
    replyTo: ActorRef[Messages.ReplyToCommand],
    mediaSettings: MediaSettings,
    grabber: Option[OpenCVFrameGrabber] = None,
    line: Option[TargetDataLine] = None,
    imageCaptureOpt: Option[ActorRef[ImageCapture.Command]] = None,
    desktopCaptureOpt: Option[ActorRef[DesktopCapture.Command]] = None,
    montageActor: ActorRef[MontageActor.Command],
    soundCaptureOpt: Option[ActorRef[SoundCapture.Command]] = None,
    desktopGrabber: Option[FFmpegFrameGrabber] = None,
    encodeActorMap: mutable.HashMap[EncoderType.Value, ActorRef[EncodeActor.Command]])(
    implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command]
  ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case msg: SetTimerGetter =>
          timeGetter = msg.func
          imageConverter.setTimeGetter(msg.func)
          Behaviors.same

        case AskImage =>
          val targetImage = latestFrame.peek()
          if (targetImage != null) {
            val image = imageConverter.convert(targetImage.frame.clone())
            replyTo ! Messages.ImageRsp(LatestImage(image, System.currentTimeMillis()))
          } else {
            log.info(s"No image captured yet.")
            replyTo ! Messages.NoImage
          }

          Behaviors.same

        case AskSamples =>
          soundCaptureOpt.foreach(_ ! SoundCapture.AskSamples)
          Behaviors.same

        case msg: StartEncodeStream =>
          log.info(s"Start encode stream.")
          val streamEncoder = if (grabber.nonEmpty && line.isEmpty) { // image only
            new FFmpegFrameRecorder(msg.outputStream, grabber.get.getImageWidth, grabber.get.getImageHeight)
          } else if (grabber.isEmpty && line.nonEmpty) { //sound only
            new FFmpegFrameRecorder(msg.outputStream, mediaSettings.channels)
          } else {
            new FFmpegFrameRecorder(msg.outputStream, grabber.get.getImageWidth, grabber.get.getImageHeight, mediaSettings.channels)
          }
          setEncoder(ctx, mediaSettings, streamEncoder, EncoderType.STREAM, imageCaptureOpt, soundCaptureOpt, encodeActorMap, replyTo)
          Behaviors.same

        case StopEncodeStream =>
          encodeActorMap.get(EncoderType.STREAM).foreach(_ ! EncodeActor.StopEncode)
          encodeActorMap.remove(EncoderType.STREAM)
          Behaviors.same

        case ShowPerson =>
          montageActor ! MontageActor.ShowCamera
          if(desktopCaptureOpt.nonEmpty) desktopCaptureOpt.get ! DesktopCapture.SuspendGrab
          if(imageCaptureOpt.nonEmpty) imageCaptureOpt.get ! ImageCapture.StartCamera
          Behaviors.same

        case ShowDesktop =>
          montageActor ! MontageActor.ShowDesktop
          if(imageCaptureOpt.nonEmpty) imageCaptureOpt.get ! ImageCapture.SuspendCamera
          if(desktopCaptureOpt.nonEmpty) desktopCaptureOpt.get ! DesktopCapture.StartGrab
          Behaviors.same

        case ShowBoth =>
          montageActor ! MontageActor.ShowBoth
          if(desktopCaptureOpt.nonEmpty) desktopCaptureOpt.get ! DesktopCapture.StartGrab
          if(imageCaptureOpt.nonEmpty) imageCaptureOpt.get ! ImageCapture.StartCamera
          Behaviors.same

        case msg: StartEncodeFile =>
          val fileEncoder = if (grabber.nonEmpty && line.isEmpty) { // image only
            new FFmpegFrameRecorder(msg.file, grabber.get.getImageWidth, grabber.get.getImageHeight)
          } else if (grabber.isEmpty && line.nonEmpty) { //sound only
            new FFmpegFrameRecorder(msg.file, mediaSettings.channels)
          } else {
            new FFmpegFrameRecorder(msg.file, grabber.get.getImageWidth, grabber.get.getImageHeight, mediaSettings.channels)
          }
          setEncoder(ctx, mediaSettings, fileEncoder, EncoderType.FILE, imageCaptureOpt, soundCaptureOpt, encodeActorMap, replyTo)
          Behaviors.same

        case StopEncodeFile =>
          encodeActorMap.get(EncoderType.FILE).foreach(_ ! EncodeActor.StopEncode)
          encodeActorMap.remove(EncoderType.FILE)
          Behaviors.same

        case StopCapture =>
          log.info(s"CaptureManager stopped in idle.")
          imageCaptureOpt.foreach(_ ! ImageCapture.StopCamera)
          soundCaptureOpt.foreach(_ ! SoundCapture.StopSample)
          encodeActorMap.foreach(_._2 ! EncodeActor.StopEncode)
          timer.startSingleTimer(STOP_DELAY_TIMER_KEY, StopDelay, 1.second)
          Behaviors.same

        case StopMediaCapture =>
          log.info(s"Your stop command executed. Capture stopping in idle.")
          imageCaptureOpt.foreach(_ ! ImageCapture.StopCamera)
          soundCaptureOpt.foreach(_ ! SoundCapture.StopSample)
          encodeActorMap.foreach(_._2 ! EncodeActor.StopEncode)
          timer.startSingleTimer(STOP_DELAY_TIMER_KEY, StopDelay, 1.second)
          Behaviors.same

        case StopDelay =>
          log.info(s"Capture Manager stopped.")
          replyTo ! Messages.ManagerStopped
          Behaviors.stopped

        case ChildDead(child, childRef) =>
          debug(s"CaptureManager unWatch child-$child")
          ctx.unwatch(childRef)
          Behaviors.same


        case x =>
          log.warn(s"unknown msg in idle: $x")
          Behaviors.unhandled
      }
    }
  }

  def setEncoder(
    ctx: ActorContext[Command],
    mediaSettings: MediaSettings,
    encoder: FFmpegFrameRecorder,
    encoderType: EncoderType.Value,
    imageCaptureOpt: Option[ActorRef[ImageCapture.Command]] = None,
    soundCaptureOpt: Option[ActorRef[SoundCapture.Command]] = None,
    encodeActorMap: mutable.HashMap[EncoderType.Value, ActorRef[EncodeActor.Command]],
    replyTo: ActorRef[Messages.ReplyToCommand]
  ): Unit = {

    encoder.setFormat("mpegts")

    /*video*/
    encoder.setVideoOption("tune", "zerolatency")
    encoder.setVideoOption("preset", "ultrafast")
    encoder.setVideoOption("crf", "25")
//    encoder.setVideoOption("keyint", "1")
    encoder.setVideoBitrate(mediaSettings.outputBitrate)
    //                    encoder.setVideoCodec(avcodec.AV_CODEC_ID_H264)
    encoder.setFrameRate(mediaSettings.frameRate)

    /*audio*/
    encoder.setAudioOption("crf", "0")
    encoder.setAudioQuality(0)
    encoder.setAudioBitrate(192000)
    encoder.setSampleRate(mediaSettings.sampleRate.toInt)
    encoder.setAudioChannels(mediaSettings.channels)
    //          encoder.setAudioCodec(avcodec.AV_CODEC_ID_AAC)
    encoderType match {
      case EncoderType.STREAM =>
        log.info(s"streamEncoder start success.")
        val encodeActor = getEncodeActor(ctx, replyTo, EncoderType.STREAM, encoder, latestFrame, mediaSettings.needImage, mediaSettings.needSound, debug)
        encodeActorMap.put(EncoderType.STREAM, encodeActor)
      case EncoderType.FILE =>
        log.info(s"fileEncoder start success.")
        val encodeActor = getEncodeActor(ctx, replyTo, EncoderType.FILE, encoder, latestFrame, mediaSettings.needImage, mediaSettings.needSound, debug)
        encodeActorMap.put(EncoderType.FILE, encodeActor)
    }
  }


  private def getImageCapture(
    ctx: ActorContext[Command],
    grabber: OpenCVFrameGrabber,
    montageActor: ActorRef[MontageActor.Command],
    frameRate: Int,
    debug: Boolean
  ) = {
    val childName = "ImageCapture"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(ImageCapture.create(grabber, frameRate, debug, montageActor), childName, blockingDispatcher)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[ImageCapture.Command]
  }

  private def getDesktopCapture(
    ctx: ActorContext[Command],
    grabber: FFmpegFrameGrabber,
    montageActor: ActorRef[MontageActor.Command],
    frameRate: Int,
    debug: Boolean
  ) = {
    val childName = "DesktopCapture"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(DesktopCapture.create(grabber, frameRate, debug, montageActor), childName, blockingDispatcher)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[DesktopCapture.Command]
  }

  private def getMontageActor(
    ctx: ActorContext[Command],
    imageQueue: LinkedBlockingDeque[LatestFrame],
    mediaSettings: MediaSettings
  ) = {
    val childName = "MontageActor"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(MontageActor.create(imageQueue, mediaSettings), childName, blockingDispatcher)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[MontageActor.Command]
  }

  private def getSoundCapture(
    ctx: ActorContext[Command],
    replyTo: ActorRef[Messages.ReplyToCommand],
    line: TargetDataLine,
    //    soundQueue: LinkedBlockingDeque[LatestSound],
    encoders: mutable.HashMap[EncoderType.Value, ActorRef[EncodeActor.Command]],
    frameRate: Int,
    sampleRate: Float,
    channels: Int,
    sampleSize: Int,
    debug: Boolean
  ) = {
    val childName = "SoundCapture"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(SoundCapture.create(replyTo, line, encoders, frameRate, sampleRate, channels, sampleSize, debug), childName, blockingDispatcher)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[SoundCapture.Command]
  }

  private def getEncodeActor(
    ctx: ActorContext[Command],
    replyTo: ActorRef[Messages.ReplyToCommand],
    encodeType: EncoderType.Value,
    encoder: FFmpegFrameRecorder,
    imageCache: LinkedBlockingDeque[LatestFrame],
    //    soundCache: LinkedBlockingDeque[LatestSound],
    needImage: Boolean,
    needSound: Boolean,
    debug: Boolean
  ) = {
    val childName = s"EncodeActor-$encodeType"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(EncodeActor.create(replyTo, encodeType, encoder, imageCache, needImage, needSound, debug, needTimeMark), childName, blockingDispatcher)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.unsafeUpcast[EncodeActor.Command]

  }


}
