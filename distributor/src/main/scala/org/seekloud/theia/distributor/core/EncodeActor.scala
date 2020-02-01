package org.seekloud.VideoMeeting.distributor.core

import java.io.File

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.bytedeco.javacpp.Loader
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.distributor.common.AppSettings._
import org.seekloud.VideoMeeting.distributor.core.EncodeManager.Command
import org.seekloud.VideoMeeting.distributor.Boot.saveManager
import scala.language.implicitConversions
import scala.concurrent.duration._
/**
  * User: yuwei
  * Date: 2019/8/26
  * Time: 20:09
  */
object EncodeActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object Stop extends Command

  case class ReStart(port: Int, startTime:Long) extends Command

  case object CloseEncode extends Command

  case object SaveRecord extends Command

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[SaveActor.Command]) extends Command

  case object TimerKey4Close

  case object TimerKey4SaveRecord

  case object NewFFmpeg extends Command

  class CreateFFmpeg(roomId: Long, port: Int, startTime:Long){
    private var process:Process = _
    private var recordProcess: Process = _

    def createDir() = {
      val fileLoc = new File(s"$fileLocation$roomId/")
      if(!fileLoc.exists()){
        fileLoc.mkdir()
      }
      val recordLoc = new File(s"$recordLocation$roomId/")
      if(!recordLoc.exists()){
        recordLoc.mkdir()
      }
      val recordLocST = new File(s"$recordLocation$roomId/$startTime/")
      if(!recordLocST.exists()){
        recordLocST.mkdir()
      }
    }

    def removeFile() = {
      val f = new File(s"$fileLocation$roomId/")
      if(f.exists()) {
        f.listFiles().map{
          e =>
            e.delete()
        }
        f.delete()
      }
    }

    def start(): Unit = {
      val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
//      val pb = new ProcessBuilder(ffmpeg, "-i", s"udp://127.0.0.1:$port","-b:v","1M",s"$dashLocation$roomId/index.mpd", "-f", "hls","-b:v","1M", "-hls_time", "3", "-segment_list_flags",
//        "+live","-hls_list_size", "20",s"$hlsLocation$roomId/index.m3u8")
      val pb = new ProcessBuilder(ffmpeg,"-i",s"udp://127.0.0.1:$port","-b:v","1M","-f","dash","-window_size","20","-extra_window_size","20","-hls_playlist","1",s"$fileLocation$roomId/index.mpd"
      ,"-b:v","1M",s"$recordLocation$roomId/$startTime/record.ts")
      val process = pb.inheritIO().start()
      this.process = process
    }

    def saveRecord(): Unit = {
      val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
      val pb = new ProcessBuilder(ffmpeg,"-i",s"$recordLocation$roomId/$startTime/record.flv","-movflags","faststart","-b:v","1M",s"$recordLocation$roomId/$startTime/record.mp4")
      val process = pb.start()
      this.recordProcess = process
    }

    def close(): Unit ={
      if(this.process != null){
        this.process.destroyForcibly()
      }
      if(this.recordProcess != null){
        this.recordProcess.destroyForcibly()
      }
//      this.process4Record.destroyForcibly()
      log.info(s"ffmpeg close successfully---")
    }
  }

  def create(roomId: Long, port: Int, startTime:Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          val fFmpeg = new CreateFFmpeg(roomId, port, startTime)
          fFmpeg.createDir()
          fFmpeg.start()
//          fFmpeg.saveRecord()
          work(roomId, port, fFmpeg, startTime)
      }
    }
  }

  def work(roomId: Long, port: Int, ffmpeg:CreateFFmpeg, startTime:Long)(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Stop =>
          log.info("stop the encode video.")
          ffmpeg.close()
          ffmpeg.removeFile()
          saveManager ! SaveManager.NewSave(startTime, roomId)
          Behaviors.stopped

        case ReStart(newPort, newStartTime) =>
          ffmpeg.close()
          ffmpeg.removeFile()
          saveManager ! SaveManager.NewSave(newStartTime, roomId)
          val newFfmpeg = new CreateFFmpeg(roomId, newPort, newStartTime)
          newFfmpeg.createDir()
          newFfmpeg.start()
          log.info("reStart the encode video.")
          work(roomId, newPort, newFfmpeg, newStartTime)
      }
    }
  }


}
