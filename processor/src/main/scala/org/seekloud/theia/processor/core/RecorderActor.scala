package org.seekloud.VideoMeeting.processor.core

import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.{File, OutputStream}
import java.nio.ShortBuffer
import java.nio.channels.Pipe.SourceChannel
import java.nio.channels.{Channels, DatagramChannel, Pipe}
import org.seekloud.VideoMeeting.processor.stream.PipeStream
import java.nio.channels.Pipe
import java.util.HashMap

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import org.bytedeco.javacv._

import scala.collection.mutable
import org.seekloud.VideoMeeting.processor.core.GrabberManager.RecorderRef
import org.seekloud.VideoMeeting.processor.Boot.{channelWorker, executor, grabberManager, recorderManager, scheduler, timeout}
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol.RoomInfo
import org.seekloud.VideoMeeting.processor.common.AppSettings._
import org.bytedeco.ffmpeg.global.{avcodec, avutil}
import org.seekloud.VideoMeeting.processor.core.ChannelWorker.GetTs
import org.seekloud.VideoMeeting.processor.utils.TimeUtil

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
  * User: yuwei
  * Date: 2019/7/15
  * Time: 22:40
  */
object RecorderActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  var audioChannels = 2 //todo 待议
  val sampleFormat = 1 //todo 待议
  var frameRate = 30


  //  var switchLayoutMap : mutable.Map[Long, SwitchLayout] =mutable.Map()

  case class SwitchLayout(var oldLayout: Int = 0, var switchFrame: Int = 1, var from0to1: Boolean = false,
                          var from0to2: Boolean = false, var from1to0: Boolean = false, var from2to0: Boolean = false)

  sealed trait Command

  case class UpdateRoomInfo(roomId: Long, liveIdList: List[String], startTime: Long, layout: Int, aiMode: Int) extends Command

  case class NewFrame(liveId: String, frame: Frame) extends Command

  case object Stop extends Command

  case object CloseRecorder extends Command

  case object TimerKey4Close

  trait VideoCommand

  case class TimeOut(msg: String) extends Command

  case class Image4Host(frame: Frame) extends VideoCommand

  case class Image4Client(frame: Frame) extends VideoCommand

  case class SetLayout(layout: Int) extends VideoCommand

  case class NewRecord4Ts(recorder4ts: FFmpegFrameRecorder) extends VideoCommand

  case object Close extends VideoCommand

  case class Image(var frame: Frame = null)

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  case class Ts4LastImage(var time: Long = -1)

  case class Ts4LastSample(var time: Long = 0)

  case object SingleInit extends Command

  case object RestartRecord extends Command

  case class Ts4Host(var time: Long = 0)

  case class Ts4Client(var time: Long = 0)

  case class UpdateRecorder(channel: Int, sampleRate: Int, frameRate: Double, width: Int, height: Int, liveId: String) extends Command

  private val emptyAudio = ShortBuffer.allocate(1024 * 2)
  //  private val emptyAudio4one = ShortBuffer.allocate(1024)
  private val emptyAudio4one = ShortBuffer.allocate(1152)
  //  private val liveCountMap = mutable.Map[Long,Int]()

  def create(roomId: Long, host: String, layout: Int, aiMode: Int, out: OutputStream): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"${ctx.self} init.")
          avutil.av_log_set_level(-8)
          //          switchLayoutMap.put(roomId,SwitchLayout())
          //rtmp流
          val recorder4ts = new FFmpegFrameRecorder(out, 1280, 720, audioChannels)
          recorder4ts.setFrameRate(frameRate)
          recorder4ts.setVideoBitrate(bitRate)
          //          recorder4ts.setVideoCodec(avcodec.AV_CODEC_ID_H264)
          recorder4ts.setFormat("mpegts")
          try {
            recorder4ts.startUnsafe()
          } catch {
            case e: Exception =>
              log.error(s" recorder meet error when start:$e")
          }
          grabberManager ! RecorderRef(roomId, ctx.self)
          ctx.self ! SingleInit
          single(roomId, null, null, new Java2DFrameConverter(), Ts4LastImage(), Ts4LastSample(), host, "", layout, aiMode, recorder4ts, null, null, out, 30000, (0, 0))
      }
    }
  }


  def restart(roomId: Long, canvas: BufferedImage, graph: Graphics,
              convert: Java2DFrameConverter, ts4LastImage: Ts4LastImage,
              ts4LastSample: Ts4LastSample, host: String, client: String,
              layout: Int, aiMode: Int, recorder4ts: FFmpegFrameRecorder,
              ffFilter: FFmpegFrameFilter, drawer: ActorRef[VideoCommand],
              out: OutputStream, tsDifer: Int = 30000,
              canvasSize: (Int, Int))
             (implicit timer: TimerScheduler[Command],
              stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"----$roomId recorderActor to restart behavior----")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case RestartRecord =>
          if (recorder4ts != null) {
            recorder4ts.releaseUnsafe()
          }

          val newRecorder4ts = new FFmpegFrameRecorder(out, 1280, 720, audioChannels)
          newRecorder4ts.setFrameRate(frameRate)
          newRecorder4ts.setVideoBitrate(bitRate)
          recorder4ts.setVideoCodec(avcodec.AV_CODEC_ID_H264)
          newRecorder4ts.setFormat("mpegts")
          try {
            newRecorder4ts.startUnsafe()
          } catch {
            case e: Exception =>
              log.error(s" recorder meet error when start:$e")
          }
          log.info(s"new recorder create successfully.")
          drawer ! NewRecord4Ts(newRecorder4ts)
          ctx.self ! SingleInit
          single(roomId, canvas, graph, convert, Ts4LastImage(), Ts4LastSample(),
            host, client, layout, aiMode, newRecorder4ts, ffFilter, drawer, out,
            tsDifer, canvasSize)

        case m@NewFrame(liveId, frame) =>
          if (liveId == host || liveId == client)
            log.info(s"got msg when reStart: $m")
          Behaviors.same

        case Stop =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 10.milli)
          Behaviors.same

        case CloseRecorder =>
          try {
            log.info(s"$roomId recorder dead when reStart------")
            if (ffFilter != null) {
              ffFilter.close()
            }
            recorder4ts.releaseUnsafe()
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped
      }
    }
  }

  def single(roomId: Long, canvas: BufferedImage, graph: Graphics, convert: Java2DFrameConverter,
             ts4LastImage: Ts4LastImage, ts4LastSample: Ts4LastSample, host: String,
             client: String, layout: Int, aiMode: Int, recorder4ts: FFmpegFrameRecorder,
             ffFilter: FFmpegFrameFilter, drawer: ActorRef[VideoCommand], out: OutputStream,
             tsDifer: Int = 30000, canvasSize: (Int, Int))(implicit timer: TimerScheduler[Command],
                                                           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$roomId to single behavior ----")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SingleInit =>
          if (ffFilter != null) {
            ffFilter.close()
          }
          //          liveCountMap.put(roomId,0)
          val ffFilterN = new FFmpegFrameFilter("[0:a][1:a] amix=inputs=2:duration=longest:dropout_transition=3:weights=3 2[a]", audioChannels)
          ffFilterN.setAudioChannels(audioChannels)
          ffFilterN.setSampleFormat(sampleFormat)
          ffFilterN.setAudioInputs(2)
          ffFilterN.start()
          single(roomId, canvas, graph, convert, ts4LastImage, ts4LastSample, host, client, layout, aiMode, recorder4ts, ffFilterN, drawer, out, tsDifer, canvasSize)

        case UpdateRecorder(channel, sampleRate, f, width, height, liveId) =>
          log.info(s"$roomId updateRecorder channel:$channel, sampleRate:$sampleRate, frameRate:$f, width:$width, height:$height")
          recorder4ts.setFrameRate(f)
          recorder4ts.setAudioChannels(channel)
          recorder4ts.setSampleRate(sampleRate)
          ffFilter.setAudioChannels(channel)
          ffFilter.setSampleRate(sampleRate)
          recorder4ts.setImageWidth(width)
          recorder4ts.setImageHeight(height)
          val canvas = new BufferedImage(Math.max(width, 10), Math.max(height, 10), BufferedImage.TYPE_3BYTE_BGR)
          if (drawer != null) {
            drawer ! Close
          }
          val newDrawer = ctx.spawn(draw(canvas, canvas.getGraphics, Ts4LastImage(), Image(), recorder4ts,
            new Java2DFrameConverter(), new Java2DFrameConverter(), layout, "defaultImg.jpg", roomId, (width, height)), s"drawer_$liveId")
          val canvas4Ts = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
          single(roomId, canvas4Ts, canvas4Ts.getGraphics, new Java2DFrameConverter(), Ts4LastImage(), Ts4LastSample(), host, client, layout, aiMode, recorder4ts, ffFilter, newDrawer, out, tsDifer, (width, height))


        case NewFrame(liveId, frame) =>
          if (liveId == host) {
            if (frame.image != null) {
              if (frame.timestamp > ts4LastImage.time) {
                recorder4ts.setTimestamp(frame.timestamp)
                //                if(liveCountMap.getOrElse(roomId,0) < 5){
                //                  log.info(s"$liveId record frame --")
                //                  liveCountMap.update(roomId, liveCountMap(roomId) +1)
                //                }
                val f = if (addTs) {
                  graph.clearRect(0, 0, canvasSize._1, canvasSize._2)
                  val img = convert.convert(frame)
                  graph.drawImage(img, 0, 0, canvasSize._1, canvasSize._2, null)
                  val serverTime = ChannelWorker.pullClient.getServerTimestamp()
                  val ts = TimeUtil.format(serverTime)
                  graph.drawString(ts, canvasSize._1 - 180, 40)
                  convert.convert(canvas)
                } else {
                  frame
                }
                Try(recorder4ts.record(f)) match {
                  case Success(a) =>
                  case Failure(e) =>
                    log.error(s"recorder Ts frame $e liveId:$liveId")
                    except(roomId, out)
                }
                ts4LastImage.time = frame.timestamp
              } else {
                log.error(s"$liveId recorder frame ts wrong")
              }
            } else if (frame.samples != null) {
              if (frame.timestamp - ts4LastSample.time > tsDifer) {
                if (frame.audioChannels == 2) {
                  (2 to ((frame.timestamp - ts4LastSample.time) / 23000l).toInt).foreach { i =>
                    Try(recorder4ts.recordSamples(emptyAudio)) match {
                      case Success(a) =>
                      case Failure(e) =>
                        log.error(s"recorderSilentFrame $e liveId:$liveId")
                    }
                  }
                } else {
                  (2 to ((frame.timestamp - ts4LastSample.time) / 25000l).toInt).foreach { i =>
                    Try(recorder4ts.recordSamples(emptyAudio4one)) match {
                      case Success(a) =>
                      case Failure(e) =>
                        log.error(s"recorderSilentFrame $e liveId:$liveId")
                    }
                  }
                }
              }
              Try(recorder4ts.record(frame)) match {
                case Success(a) =>
                case Failure(e) =>
                  log.error(s"recorder $e liveId:$liveId")
              }
              if (frame.timestamp > ts4LastSample.time) {
                ts4LastSample.time = frame.timestamp
              }
            }
            Behaviors.same
          } else {
            if (frame.image != null && client != "") {
              couple(roomId, canvas, graph, convert, host, client, layout, aiMode, recorder4ts, ffFilter, drawer, Ts4Host(ts4LastSample.time), Ts4Client(), out, tsDifer, canvasSize)
            } else {
              Behaviors.same
            }
          }

        case t: UpdateRoomInfo =>
          log.info(s"$roomId recorder got msg: $t")
          grabberManager ! RecorderRef(roomId, ctx.self)
          if (t.liveIdList.contains(host)) {
            if (t.liveIdList.size > 1) {
              val client = t.liveIdList(1)
              if (t.layout != layout) {
                drawer ! SetLayout(t.layout)
              }
              single(roomId, canvas, graph, convert, ts4LastImage, ts4LastSample, host, client, t.layout, t.aiMode, recorder4ts, ffFilter, drawer, out, tsDifer, canvasSize)
            } else {
              Behaviors.same
            }
          } else if (t.liveIdList.length == 1) {

            val pipe = new PipeStream
            val sink = pipe.getSink
            val source = pipe.getSource

            ctx.self ! RestartRecord
            recorderManager ! RecorderManager.HostReconnect(roomId, t.liveIdList.head, t.startTime, pipe)
            restart(roomId, canvas, graph, convert, Ts4LastImage(), Ts4LastSample(),
              t.liveIdList.head, "", t.layout, t.aiMode, recorder4ts, ffFilter,
              drawer, Channels.newOutputStream(sink), tsDifer, canvasSize)
          } else {
            Behaviors.same
          }

        case m@RestartRecord =>
          log.info(s"single state get $m")
          Behaviors.same

        case SwitchBehavior(name, behavior, durationOpt, timeOut) =>
          behavior


        case CloseRecorder =>
          try {
            log.info(s"$roomId recorder dead ------")
            ffFilter.close()
            recorder4ts.releaseUnsafe()
            out.close()
            //            switchLayoutMap.remove(roomId)
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped

        case Stop =>
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 500.milli)
          Behaviors.same
      }
    }
  }

  def couple(roomId: Long, canvas: BufferedImage, graph: Graphics, convert: Java2DFrameConverter, host: String, client: String, layout: Int, aiMode: Int,
             recorder4ts: FFmpegFrameRecorder,
             ffFilter: FFmpegFrameFilter,
             drawer: ActorRef[VideoCommand],
             ts4Host: Ts4Host,
             ts4Client: Ts4Client,
             out: OutputStream,
             tsDiffer: Int = 30000, canvasSize: (Int, Int))(implicit timer: TimerScheduler[Command],
                                                            stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$roomId recorder to couple behavior")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewFrame(liveId, frame) =>
          if (frame.image != null) {
            if (liveId == host) {
              drawer ! Image4Host(frame)
            } else if (liveId == client) {
              drawer ! Image4Client(frame)
            } else {
              log.info(s"wrong, liveId, couple got wrong img")
            }
          }
          if (frame.samples != null) {
            try {
              if (liveId == host) {
                if ((frame.timestamp - ts4Host.time > tsDiffer) && ts4Host.time != -1) {
                  if (frame.audioChannels == 2) {
                    (2 to ((frame.timestamp - ts4Host.time) / 23000l).toInt).foreach { i =>
                      ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio)
                    }
                  } else {
                    (2 to ((frame.timestamp - ts4Host.time) / 25000l).toInt).foreach { i =>
                      ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio4one)
                    }
                  }
                }
                ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)
                if (frame.timestamp > ts4Host.time) {
                  ts4Host.time = frame.timestamp
                }
              } else if (liveId == client) {
                if ((frame.timestamp - ts4Client.time > tsDiffer) && ts4Client.time != 0) {
                  if (frame.audioChannels == 2) {
                    (2 to ((frame.timestamp - ts4Client.time) / 23000l).toInt).foreach { i =>
                      ffFilter.pushSamples(1, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio)
                    }
                  } else {
                    (2 to ((frame.timestamp - ts4Client.time) / 25000l).toInt).foreach { i =>
                      ffFilter.pushSamples(1, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, emptyAudio4one)
                    }
                  }
                }
                ffFilter.pushSamples(1, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)
                if (frame.timestamp > ts4Client.time) {
                  ts4Client.time = frame.timestamp
                }
              } else {
                log.info(s"wrong liveId, couple got wrong audio")
              }
              val f = ffFilter.pullSamples().clone()
              if (f != null) {
                recorder4ts.recordSamples(f.sampleRate, f.audioChannels, f.samples: _*)
              }
            } catch {
              case ex: Exception =>
                log.debug(s"$liveId record sample error system: $ex")
            }
          }
          Behaviors.same

        case SwitchBehavior(name, behavior, durationOpt, timeOut) =>
          behavior

        case t: UpdateRoomInfo =>
          log.info(s"$roomId got msg: $t in couple.")
          grabberManager ! RecorderRef(roomId, ctx.self)
          if (t.liveIdList.size > 1) {
            val c = t.liveIdList(1)
            if (t.layout != layout) {
              drawer ! SetLayout(t.layout)
            }
            couple(roomId, canvas, graph, convert, host, c, t.layout, aiMode, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)
          } else if (t.liveIdList.contains(host)) {
            ctx.self ! SingleInit
            single(roomId, canvas, graph, convert, Ts4LastImage(), Ts4LastSample(ts4Host.time), host, "", t.layout, aiMode, recorder4ts, ffFilter, drawer, out, tsDiffer, canvasSize)
          } else {
            try {
              out.close()
            } catch {
              case e: Exception =>
                log.info(s"outputStream is null.")
            }
            val pipe = new PipeStream
            val sink = pipe.getSink
            ctx.self ! RestartRecord
            recorderManager ! RecorderManager.HostReconnect(roomId, t.liveIdList.head, t.startTime, pipe)
            restart(roomId, canvas, graph, convert, Ts4LastImage(), Ts4LastSample(),
              t.liveIdList.head, "", t.layout, aiMode, recorder4ts, ffFilter,
              drawer, Channels.newOutputStream(sink), tsDiffer, canvasSize)
          }

        case m@RestartRecord =>
          log.info(s"couple state get $m")
          Behaviors.same

        case CloseRecorder =>
          try {
            ffFilter.close()
            recorder4ts.releaseUnsafe()
            drawer ! Close
            //            switchLayoutMap.remove(roomId)
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped

        case Stop =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 1.seconds)
          Behaviors.same

        case x =>
          Behaviors.same
      }
    }
  }

  def draw(canvas: BufferedImage, graph: Graphics, lastTime: Ts4LastImage, clientFrame: Image,
           recorder4ts: FFmpegFrameRecorder, convert1: Java2DFrameConverter, convert2: Java2DFrameConverter,
           layout: Int = 0, bgImg: String, roomId: Long, canvasSize: (Int, Int)): Behavior[VideoCommand] = {
    Behaviors.setup[VideoCommand] { ctx =>
      Behaviors.receiveMessage[VideoCommand] {
        case t: Image4Host =>
          val time = t.frame.timestamp
          val img = convert1.convert(t.frame)
          val clientImg = convert2.convert(clientFrame.frame)
          graph.clearRect(0, 0, canvasSize._1, canvasSize._2)
          layout match {
            case 0 =>
              graph.drawImage(img, 0, canvasSize._2 / 4, canvasSize._1 / 2, canvasSize._2 / 2, null)
              graph.drawString("主播", 24, 24)
              graph.drawImage(clientImg, canvasSize._1 / 2, canvasSize._2 / 4, canvasSize._1 / 2, canvasSize._2 / 2, null)
              graph.drawString("观众", 344, 24)
            case 1 =>
              graph.drawImage(img, 0, 0, canvasSize._1, canvasSize._2, null)
              graph.drawString("主播", 24, 24)
              graph.drawImage(clientImg, canvasSize._1 / 4 * 3, 0, canvasSize._1 / 4, canvasSize._2 / 4, null)
              graph.drawString("观众", 584, 24)

            case 2 =>
              graph.drawImage(clientImg, 0, 0, canvasSize._1, canvasSize._2, null)
              graph.drawString("观众", 24, 24)
              graph.drawImage(img, canvasSize._1 / 4 * 3, 0, canvasSize._1 / 4, canvasSize._2 / 4, null)
              graph.drawString("主播", 584, 24)

          }
          if (addTs) {
            val serverTime = ChannelWorker.pullClient.getServerTimestamp()
            val ts = TimeUtil.format(serverTime)
            graph.drawString(ts, canvasSize._1 - 200, 40)
          }
          //fixme 此处为何不直接recordImage
          val frame = convert1.convert(canvas)
          if (time > lastTime.time) {
            try {
              recorder4ts.setTimestamp(time)
              recorder4ts.record(frame)
            } catch {
              case e: Exception =>
                log.error(s"in draw state record error $e")
            }
            lastTime.time = time
          }
          Behaviors.same

        case t: Image4Client =>
          clientFrame.frame = t.frame
          Behaviors.same

        case m@NewRecord4Ts(recorder4ts) =>
          log.info(s"got msg: $m")
          draw(canvas, graph, lastTime, clientFrame, recorder4ts, convert1, convert2, layout, bgImg, roomId, canvasSize)

        case Close =>
          log.info(s"drawer stopped")
          Behaviors.stopped

        case t: SetLayout =>
          log.info(s"got msg: $t")
          draw(canvas, graph, lastTime, clientFrame, recorder4ts, convert1, convert2, t.layout, bgImg, roomId, canvasSize)
      }
    }
  }

  def except(roomId: Long, out: OutputStream)(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      log.info(s"$roomId recorder turn to except state")
      msg match {
        case Stop =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          log.info(s"$roomId recorder stop in except")
          Behaviors.stopped
        case x =>
          Behaviors.same

      }
    }
  }


}
