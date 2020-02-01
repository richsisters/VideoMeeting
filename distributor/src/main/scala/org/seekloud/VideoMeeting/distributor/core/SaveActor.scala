package org.seekloud.VideoMeeting.distributor.core

import java.io.File

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.bytedeco.javacpp.Loader
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.distributor.common.AppSettings._

import scala.language.implicitConversions
import scala.concurrent.duration._
object SaveActor {

  class CreateFFmpeg(roomId: Long, startTime:Long) {
    private var process: Process = _

    def removeFile(): AnyVal = {
      val f = new File(s"$fileLocation$roomId/")
      if (f.exists()) {
        f.listFiles().map {
          e =>
            e.delete()
        }
        f.delete()
      }
    }


    def saveRecord(): Unit = {
      val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
      val pb = new ProcessBuilder(ffmpeg, "-i", s"$recordLocation$roomId/$startTime/record.ts","-b:v","1M","-movflags", "faststart", s"$recordLocation$roomId/$startTime/record.mp4")
      val process = pb.start()
      this.process = process
    }

    def close(): Unit = {
      this.process.destroyForcibly()
      log.info(s"ffmpeg close successfully---")
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
          log.info(s"$roomId -- $startTime saveActor start")
          val fFmpeg = new CreateFFmpeg(roomId,  startTime)
          fFmpeg.saveRecord()
          timer.startSingleTimer(Timer4Die, Stop, 5.minutes)
          work(roomId, fFmpeg)
      }
    }
  }
    def work(roomId: Long, ffmpeg:CreateFFmpeg)(implicit timer: TimerScheduler[Command],
                                                           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
      Behaviors.receive[Command] { (_, msg) =>
        msg match {
          case Stop =>
            ffmpeg.close()
            log.info(s"$roomId saveActor stopped --")
            Behaviors.stopped
        }
      }
    }
}
