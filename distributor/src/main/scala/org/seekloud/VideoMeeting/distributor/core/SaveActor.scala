package org.seekloud.VideoMeeting.distributor.core

import java.io.File

import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.bytedeco.javacpp.Loader
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.distributor.common.AppSettings._
import org.seekloud.VideoMeeting.distributor.utils.CmdUtil
import org.seekloud.VideoMeeting.distributor.Boot.{executor, scheduler}

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{Failure, Success}
object SaveActor {

  class CreateFFmpeg(roomId: Long, startTime:Long) {
    private var process: Process = _

    var isDelete = false

    var tryTimes = 0
    var tryTime = 0

    var firstTimer: Cancellable = _
    var secondTimer: Cancellable = _

    def removeFile(): AnyVal = {
      val f = new File(s"$fileLocation$roomId/")
      if (f.exists()) {
        f.listFiles().map {
          e =>
            isDelete = true
            e.delete()
        }
        f.delete()
      }
    }

    def removeVideo(path: String): AnyVal = {
      val f = new File(path)
      if (f.exists()) {
        f.delete()
      }
    }

    def removeFile4F(): Unit= {
      log.info(s"Ready to delete $roomId-$startTime video and audio files, ${tryTime}th")
      tryTime += 1
      val f = new File(s"$recordLocation$roomId/$startTime/record.mp4")
      if (f.exists()) {
        removeVideo(s"$recordLocation$roomId/$startTime/video.mp4")
        removeVideo(s"$recordLocation$roomId/$startTime/audio.mp4")
      }
      else if(tryTime < 5){
        secondTimer = scheduler.scheduleOnce(10 seconds, () => removeFile4F())
      }
      else {
        secondTimer.cancel()
        removeVideo(s"$recordLocation$roomId/$startTime")
      }
    }


    def saveRecord(): Unit = {
      val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
      //      val pb = new ProcessBuilder(ffmpeg, "-i", s"$recordLocation$roomId/$startTime/record.ts", "-b:v", "1M", "-movflags", "faststart", s"$recordLocation$roomId/$startTime/record.mp4")
      //      val process = pb.start()
      //      this.process = process

      if(!testModel){
        val commandStr = s"cat $fileLocation$roomId/init-stream0.m4s $fileLocation$roomId/chunk-stream0*.m4s >> $recordLocation$roomId/$startTime/video.mp4" +
                         s"; cat $fileLocation$roomId/init-stream1.m4s $fileLocation$roomId/chunk-stream1*.m4s >> $recordLocation$roomId/$startTime/audio.mp4"
        CmdUtil.exeCmd4Linux(commandStr).onComplete {
          case Success(a) =>
            periodlyRm()
            if (a == 1) {
              val f = new File(s"$recordLocation$roomId/$startTime/video.mp4")
              if (f.exists()) {
                log.info(s"record startTime: $startTime")
                val cmdStr = ffmpeg + s" -i $recordLocation$roomId/$startTime/video.mp4 -i $recordLocation$roomId/$startTime/audio.mp4 -c copy -b:v 1M -movflags faststart $recordLocation$roomId/$startTime/record.mp4"
                this.process =  CmdUtil.exeFFmpeg(cmdStr)
              }
              else {
                log.info("video and audio files don't exist")
              }
            }
            else {
              log.info("cat video and audio files failed")
            }

          case Failure(e) =>
            periodlyRm()
            log.info(s"record error: $e")
        }
      }else{
        val path = "/Users/litianyu/Downloads/dash/"
//        val path = "D:\\test\\"
        val commandStr4Win1 = s"copy /b ${path}init-stream0.m4s+${path}chunk-stream0*.m4s ${path}video.mp4"
        val commandStr4Win2 = s"copy /b ${path}init-stream1.m4s+${path}chunk-stream1*.m4s ${path}audio.mp4"
        val r1 = CmdUtil.exeCmd4Windows(commandStr4Win1)
        val r2 = CmdUtil.exeCmd4Windows(commandStr4Win2)
        Future.sequence(List(r1, r2)).onComplete{
          case Success(a) =>
            println(s"exe successfully, $a")
            val fCommandStr = ffmpeg + s" -i ${path}video.mp4 -i ${path}audio.mp4 -c:v copy -c:a copy ${path}final.mp4"
            process = CmdUtil.exeFFmpeg(fCommandStr)
          case Failure(exception) =>
            println(s"exe error, $exception")
        }
      }
    }

    def close(): Unit = {
      if (this.process != null) this.process.destroyForcibly()
      log.info(s"ffmpeg close successfully---")
    }

    def periodlyRm(): Unit = {
      log.info(s"Ready to delete useless $roomId-$startTime files, ${tryTimes}th")
      tryTimes += 1
      val f = new File(s"$recordLocation$roomId/$startTime/video.mp4")
      if (!isDelete) {
        if (f.exists()) {
          removeFile()
        }
        else if(tryTimes < 5){
          firstTimer = scheduler.scheduleOnce(10 seconds, () => periodlyRm())
        }
        else {
          log.info(s"Files below $fileLocation$roomId/ weren't deleted regularly.")
          firstTimer.cancel()
          removeFile()
        }
      }
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case object Timer4Die

  case object Stop extends Command

  def create(roomId: Long, startTime:Long): Behavior[Command] = {
    Behaviors.setup[Command] { _ =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"$roomId -- $startTime saveActor start...")
          val fFmpeg = new CreateFFmpeg(roomId,  startTime)
          fFmpeg.saveRecord()
          timer.startSingleTimer(Timer4Die, Stop, 5.minutes)
          work(roomId, fFmpeg)
      }
    }
  }
    def work(roomId: Long, ffmpeg: CreateFFmpeg)(implicit timer: TimerScheduler[Command],
                                                           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
      Behaviors.receive[Command] { (_, msg) =>
        msg match {
          case Stop =>
            ffmpeg.close()
            ffmpeg.removeFile4F()
            log.info(s"$roomId saveActor stopped --")
            Behaviors.stopped
        }
      }
    }
}
