package org.seekloud.VideoMeeting.processor.core_new

import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.{File, FileOutputStream, OutputStream}
import java.nio.{ByteBuffer, ShortBuffer}

import scala.collection.mutable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.model.Uri.Empty
import javax.imageio.ImageIO
import org.bytedeco.ffmpeg.global.{avcodec, avutil}
import org.bytedeco.javacv.{FFmpegFrameFilter, FFmpegFrameRecorder, Frame, Java2DFrameConverter}
import org.seekloud.VideoMeeting.processor.Boot.roomManager
import org.seekloud.VideoMeeting.processor.common.AppSettings.{addTs, bitRate}
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.processor.utils.TimeUtil

import scala.concurrent.duration._


/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午2:30
  *
  * actor由RoomActor创建
  * 编码线程 stream数据传入pipe
  * 合并连线线程
  */
object RecorderActor {

  var audioChannels = 2
  val sampleFormat = 1
  var frameRate = 30

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object Init extends Command

  case object RestartRecord extends Command

  case object StopRecorder extends Command

  case class ClientExit(liveId: String) extends Command

  case class BanOnClient(liveId: String, isImg: Boolean, isSound: Boolean) extends Command

  case class BanClientSound(liveId: String) extends Command

  case class CancelBan(liveId: String, isImg: Boolean, isSound: Boolean) extends Command // todo 取消屏蔽

  case object CloseRecorder extends Command

  case class NewFrame(liveId: String, frame: Frame) extends Command

  case class UpdateRecorder(channel: Int, sampleRate: Int, frameRate: Double, width: Int, height: Int, liveId: String) extends Command

  case object TimerKey4Close

  sealed trait VideoCommand

  case class TimeOut(msg: String) extends Command

  case class Image4Host(frame: Frame) extends VideoCommand

  case class Image4Client(frame: Frame, liveId: String) extends VideoCommand

  case object StartDrawing extends  VideoCommand

  case class ReStartDrawing(clientInfo: List[String], exitLiveId: String) extends VideoCommand

  case class BanClientImg(liveId: String,clientInfo: List[String]) extends VideoCommand

  case class CancelBanImg(liveId: String, clientInfo: List[String]) extends VideoCommand

  case object Close extends VideoCommand

  case class Ts4Host(var time: Long = 0)

  case class Ts4Client(var time: Long = 0)

  case class Image(var frame: Frame = null)

  case class Ts4LastImage(var time: Long = -1)

  case class Ts4LastSample(var time: Long = 0)

  private val emptyAudio = ShortBuffer.allocate(1024 * 2)
  private val emptyAudio4one = ShortBuffer.allocate(1152)

  def create(roomId: Long, host: String, clientInfo: List[String], layout: Int, output: OutputStream): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"recorderActor start----")
          avutil.av_log_set_level(-8)
          val recorder4ts = new FFmpegFrameRecorder(output, 640, 480, audioChannels)
          recorder4ts.setFrameRate(frameRate)
          recorder4ts.setVideoBitrate(bitRate)
          recorder4ts.setVideoCodec(avcodec.AV_CODEC_ID_MPEG2VIDEO)
          recorder4ts.setAudioCodec(avcodec.AV_CODEC_ID_MP2)
          recorder4ts.setMaxBFrames(0)
          recorder4ts.setFormat("mpegts")
          try {
            recorder4ts.startUnsafe()
          } catch {
            case e: Exception =>
              log.error(s" recorder meet error when start:$e")
          }
          roomManager ! RoomManager.RecorderRef(roomId, ctx.self)
          ctx.self ! Init
          single(roomId,  host, clientInfo, layout, recorder4ts, null, null, null,  null,null, mutable.Map[String, BufferedImage](), output, 30000, (0, 0))
      }
    }
  }

  def single(roomId: Long,  host: String, clientInfo: List[String], layout: Int,
  recorder4ts: FFmpegFrameRecorder,
  ffFilter: FFmpegFrameFilter,
  drawer: ActorRef[VideoCommand],
  ts4Host: Ts4Host,
  ts4Client: mutable.Map[String,Ts4Client],
  hostImage: BufferedImage,
  clientImage: mutable.Map[String, BufferedImage],
  out: OutputStream,
  tsDiffer: Int = 30000, canvasSize: (Int, Int))(implicit timer: TimerScheduler[Command],
  stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Init =>
          if (ffFilter != null) {
            ffFilter.close()
          }
          val ffFilterN = if(clientInfo.size == 1){
            new FFmpegFrameFilter(s"[0:a][1:a] amix=inputs=2:duration=longest:dropout_transition=3:weights=1 1[a]", audioChannels)
          } else if(clientInfo.size == 2){
            new FFmpegFrameFilter(s"[0:a][1:a][2:a] amix=inputs=3:duration=longest:dropout_transition=3:weights=1 1[a]", audioChannels)
          } else if( clientInfo.size == 3){
            new FFmpegFrameFilter(s"[0:a][1:a][2:a][3:a] amix=inputs=4:duration=longest:dropout_transition=3:weights=1 1[a]", audioChannels)
          } else {
            new FFmpegFrameFilter(s"[0:a][1:a] amix=inputs=2:duration=longest:dropout_transition=3:weights=1 1[a]", audioChannels)
          }
          //val ffFilterN = new FFmpegFrameFilter("[0:a][1:a] amix=inputs=2:duration=longest:dropout_transition=3:weights=1 1[a]", audioChannels)
          ffFilterN.setAudioChannels(audioChannels)
          ffFilterN.setSampleFormat(sampleFormat)
          ffFilterN.setAudioInputs(clientInfo.size + 1)
          ffFilterN.start()
          single(roomId,  host, clientInfo,layout, recorder4ts, ffFilterN, drawer, ts4Host, ts4Client, hostImage, clientImage, out, tsDiffer, canvasSize)

        case UpdateRecorder(channel, sampleRate, f, width, height, liveId) =>
          if(liveId == host) {
            log.info(s"$roomId updateRecorder channel:$channel, sampleRate:$sampleRate, frameRate:$f, width:$width, height:$height")
            recorder4ts.setFrameRate(f)
            recorder4ts.setAudioChannels(channel)
            recorder4ts.setSampleRate(sampleRate)
            ffFilter.setAudioChannels(channel)
            ffFilter.setSampleRate(sampleRate)
            recorder4ts.setImageWidth(width)
            recorder4ts.setImageHeight(height)
            single(roomId,  host, clientInfo, layout, recorder4ts, ffFilter, drawer, ts4Host, ts4Client,hostImage, clientImage, out, tsDiffer,  (640,  480))
          }else{
            Behaviors.same
          }

        case NewFrame(liveId, frame) =>
          if(liveId == host){
            recorder4ts.record(frame)
            Behaviors.same
          }else{
            val canvas = new BufferedImage(640, 480, BufferedImage.TYPE_3BYTE_BGR)
            val drawer = ctx.spawn(draw(canvas, canvas.getGraphics, Ts4LastImage(), hostImage, clientImage, clientInfo, recorder4ts,
              new Java2DFrameConverter(), new Java2DFrameConverter, layout, "defaultImg.jpg", roomId, (640, 480)), s"drawer_$roomId")
            ctx.self ! NewFrame(liveId, frame)
            work(roomId,host,clientInfo,layout,recorder4ts,ffFilter, drawer,ts4Host,ts4Client,out,tsDiffer,canvasSize)
          }

        case CloseRecorder =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          try {
            ffFilter.close()
            drawer ! Close
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped

        case StopRecorder =>
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 1.seconds)
          Behaviors.same
      }
    }
  }

  def work(roomId: Long,  host: String, clientInfo: List[String], layout: Int,
           recorder4ts: FFmpegFrameRecorder,
           ffFilter: FFmpegFrameFilter,
           drawer: ActorRef[VideoCommand],
           ts4Host: Ts4Host,
           ts4Client: mutable.Map[String,Ts4Client],
           out: OutputStream,
           tsDiffer: Int = 30000, canvasSize: (Int, Int))
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$roomId recorder to couple behavior")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewFrame(liveId, frame) =>
          if (frame.image != null) {
            if (liveId == host) {
              drawer ! Image4Host(frame)
            } else if(clientInfo.contains(liveId)){
              drawer ! Image4Client(frame, liveId)
            }else {
              log.debug(s"wrong, liveId, work got wrong img")
            }
          }
          if (frame.samples != null) {
            try {
              if (liveId == host) {
                ffFilter.pushSamples(0, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*)
              } else if (clientInfo.contains(liveId)) {
                 ffFilter.pushSamples(clientInfo.indexOf(liveId) + 1, frame.audioChannels, frame.sampleRate, ffFilter.getSampleFormat, frame.samples: _*) //fixme 声音合并
              } else {
                log.debug(s"wrong liveId, couple got wrong audio")
              }
              val f = ffFilter.pullSamples().clone()
              if (f != null) {
                recorder4ts.recordSamples(f.sampleRate, f.audioChannels, f.samples: _*)

              }
            } catch {
              case ex: Exception =>
               // log.debug(s"$liveId record sample error system: $ex")
            }
          }
          Behaviors.same

        case msg: ClientExit =>
          log.info(s"${ctx.self} receive a msg $msg")
          val newClientInfo = clientInfo.filter(c => c != msg.liveId)
          drawer ! ReStartDrawing(newClientInfo, msg.liveId)
          work(roomId,  host, newClientInfo, layout, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

        case msg: BanOnClient =>
          log.info(s"${ctx.self} receive a msg $msg")
          val newClientInfo = clientInfo.filter(c => c != msg.liveId)
          if(msg.isImg){
            drawer ! BanClientImg(msg.liveId, newClientInfo)
          } else if(msg.isSound){
            ctx.self ! BanClientSound(msg.liveId)
          }
          work(roomId,  host, newClientInfo, layout, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

        case msg: BanClientSound =>
          log.info(s"${ctx.self} receive a msg ${msg}")
          val newClientInfo = clientInfo.filter(c => c != msg.liveId)
          work(roomId,  host, newClientInfo, layout, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

        case msg: CancelBan =>
          log.info(s"${ctx.self} receive a msg ${msg}")
          val newClientInfo = if(clientInfo.contains(msg.liveId))  clientInfo else clientInfo :+ msg.liveId
          if(msg.isImg){
            drawer ! CancelBanImg(msg.liveId, newClientInfo)
          }
          work(roomId,  host, newClientInfo, layout, recorder4ts, ffFilter, drawer, ts4Host, ts4Client, out, tsDiffer, canvasSize)

        case m@RestartRecord =>
          log.info(s"couple state get $m")
          Behaviors.same

        case CloseRecorder =>
          try {
            if (out != null)
              out.close()
          } catch {
            case e: Exception =>
              log.info(s"pipeStream has already been closed.")
          }
          try {
            ffFilter.close()
            drawer ! Close
          } catch {
            case e: Exception =>
              log.error(s"$roomId recorder close error ---")
          }
          Behaviors.stopped

        case StopRecorder =>
          timer.startSingleTimer(TimerKey4Close, CloseRecorder, 1.seconds)
          Behaviors.same

        case x =>
          Behaviors.same
      }
    }
  }

  def draw(canvas: BufferedImage, graph: Graphics, lastTime: Ts4LastImage, hostFrame: BufferedImage, clientFrame: mutable.Map[String, BufferedImage], clientInfo: List[String],
           recorder4ts: FFmpegFrameRecorder, convert4Host: Java2DFrameConverter, convert:Java2DFrameConverter,
           layout: Int = 0, bgImg: String, roomId: Long, canvasSize: (Int, Int)): Behavior[VideoCommand] = {
    Behaviors.setup[VideoCommand] { ctx =>
      Behaviors.receiveMessage[VideoCommand] {
        case t: Image4Host =>
          val img = convert4Host.convert(t.frame)
          ctx.self ! StartDrawing
          draw(canvas, graph, lastTime, img, clientFrame, clientInfo, recorder4ts, convert4Host, convert, layout, bgImg, roomId, canvasSize)

        case t: Image4Client =>
          //得到各个参会人的frame信息
          clientInfo.foreach { client =>
            if(client == t.liveId){
              val clientConvert = new Java2DFrameConverter()
              val clientImg = clientConvert.convert(t.frame)
              clientFrame.put(client, clientImg)
            }
          }
          ctx.self ! StartDrawing
          draw(canvas, graph, lastTime, hostFrame, clientFrame, clientInfo, recorder4ts, convert4Host, convert, layout, bgImg, roomId, canvasSize)


        case t: ReStartDrawing =>
          ctx.self ! StartDrawing
          clientFrame.remove(t.exitLiveId)
          graph.clearRect(0,0,canvasSize._1, canvasSize._2)
          draw(canvas, graph, lastTime, hostFrame, clientFrame, t.clientInfo, recorder4ts, convert4Host, convert, layout, bgImg, roomId, canvasSize)

        case t: BanClientImg =>
          log.info(s"${ctx.self} receive a msg $t")
          graph.clearRect(0,0,canvasSize._1, canvasSize._2)
//          val img = ImageIO.read(this.getClass.getResource("/img/禁止.jpg"))
//          val banImg = new BufferedImage(640, 480, img.getType) //覆盖不成功
          ctx.self ! StartDrawing
          clientFrame.remove(t.liveId)
         // clientFrame.put(t.liveId, banImg)
          draw(canvas, graph, lastTime, hostFrame, clientFrame, t.clientInfo, recorder4ts, convert4Host, convert, layout, bgImg, roomId, canvasSize)

        case t:CancelBanImg =>
          log.info(s"${ctx.self} receive a msg $t")
          graph.clearRect(0,0,canvasSize._1, canvasSize._2)
          ctx.self ! StartDrawing
          draw(canvas, graph, lastTime, hostFrame, clientFrame, t.clientInfo, recorder4ts, convert4Host, convert, layout, bgImg, roomId, canvasSize)

        case StartDrawing =>
          //根据不同的参会人数设置不同的排列方式
          if(clientInfo.size == clientFrame.values.toList.size) {
            clientInfo.size match {
              case 0 =>
                graph.drawImage(hostFrame, 0, canvasSize._2 / 4, canvasSize._1 , canvasSize._2, null)
                graph.drawString("主持人", 24, 25)
              case 1 =>
                graph.drawImage(hostFrame, 0, canvasSize._2 / 4, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("主持人", 24, 25)
                graph.drawImage(clientFrame.values.toList.head, canvasSize._1 / 2, canvasSize._2 / 4, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("参会人1", 344, 25)
              case 2 =>
                graph.drawImage(hostFrame, canvasSize._1 / 4, 0, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("主持人", 310, 0)
                graph.drawImage(clientFrame.values.head, 0, canvasSize._2 / 2, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("参会人1", 150, 250)
                graph.drawImage(clientFrame.values.toList(1), canvasSize._1 / 2, canvasSize._2 / 2, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("参会人2", 470, 250)
              case 3 =>
                graph.drawImage(hostFrame, 0, 0, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("主持人", 150, 0)
                graph.drawImage(clientFrame.values.head, canvasSize._1 / 2, 0, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("参会人1", 470, 0)
                graph.drawImage(clientFrame.values.toList(1), 0, canvasSize._2 / 2, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("参会人2", 150, 250)
                graph.drawImage(clientFrame.values.toList(2), canvasSize._1 / 2, canvasSize._2 / 2, canvasSize._1 / 2, canvasSize._2 / 2, null)
                graph.drawString("参会人3", 470, 25)
              case 4 =>
                graph.drawImage(hostFrame, canvasSize._2 / 6, 0, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("主持人", 200, 0)
                graph.drawImage(clientFrame.values.head, canvasSize._1 / 2, 0, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人1", 300, 0)
                graph.drawImage(clientFrame.values.toList(1), 0, canvasSize._2 / 2, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人2", 100, 250)
                graph.drawImage(clientFrame.values.toList(2), canvasSize._1 / 3, canvasSize._2 / 2, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人3", 300, 250)
                graph.drawImage(clientFrame.values.toList(3), canvasSize._1 * 2 / 3, canvasSize._2 / 2, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人4", 500, 250)

              case 5 =>
                graph.drawImage(hostFrame, 0, 0, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("主持人", 100, 0)
                graph.drawImage(clientFrame.values.head, canvasSize._1 / 3, 0, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人1", 300, 0)
                graph.drawImage(clientFrame.values.toList(1), canvasSize._1 * 2 / 3, 0, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人2", 500, 0)
                graph.drawImage(clientFrame.values.toList(2), 0, canvasSize._2 / 2, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人3", 100, 250)
                graph.drawImage(clientFrame.values.toList(3), canvasSize._1 / 3, canvasSize._2 / 2, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人4", 300, 250)
                graph.drawImage(clientFrame.values.toList(4), canvasSize._1 * 2 / 3, canvasSize._2 / 2, canvasSize._1 / 3, canvasSize._2 / 2, null)
                graph.drawString("参会人5", 500, 250)

            }
          } else {
            log.info(s"${ctx.self} is waiting to drawing")
          }

          val frame = convert.convert(canvas)
          recorder4ts.record(frame.clone())
          Behaviors.same


        case Close =>
          log.info(s"drawer stopped")
          recorder4ts.releaseUnsafe()
          Behaviors.stopped
      }
    }
  }

}
