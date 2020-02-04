package org.seekloud.VideoMeeting.distributor.core

import java.io.{BufferedReader, File, InputStreamReader}
import java.util.regex.Pattern


import scala.language.implicitConversions
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.bytedeco.javacpp.Loader
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.distributor.common.AppSettings.recordLocation
import org.seekloud.VideoMeeting.distributor.protocol.SharedProtocol.RecordData

import scala.collection.mutable

object SaveManager {

  sealed trait Command

  case class NewSave(startTime:Long, roomId:Long) extends Command

  case class SeekRecord(roomId:Long,startTime:Long,reply:ActorRef[RecordInfo]) extends Command

  case class RecordInfo(fileExist:Boolean,duration:String)

  case class RemoveRecords(records:List[RecordData]) extends Command

  private val log = LoggerFactory.getLogger(this.getClass)

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[SaveActor.Command]) extends Command
  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { _ =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"saveManager start----")
          work(mutable.Map[Long,(Int, ActorRef[EncodeActor.Command])]())
      }
    }
  }

  def work(enCodeRefMap: mutable.Map[Long, (Int, ActorRef[EncodeActor.Command])])
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewSave(startTime, roomId) =>
          getSaveActor(ctx, roomId,startTime)
          Behaviors.same

        case m@SeekRecord(roomId, startTime, reply)=>
          log.info(s"got msg: $m")
          val file = new File(s"$recordLocation$roomId/$startTime/record.mp4")
          if(file.exists()){
            val d = getVideoDuration(roomId,startTime)
            log.info(s"duration:$d")
            reply ! RecordInfo(fileExist = true,d)
          }else{
            log.info(s"no record for roomId:$roomId and startTime:$startTime")
            reply ! RecordInfo(fileExist = false,"00:00:00.00")
          }
          Behaviors.same

        case m@RemoveRecords(records) =>
          log.info(s"got msg: $m")
          records.foreach{r =>
            removeRecord(r.roomId,r.startTime)
          }
          Behaviors.same

        case t:ChildDead =>
          log.info(s"${t.childName} stopped--------")
          Behaviors.same

      }
    }
  }

  def removeRecord(roomId:Long,startTime:Long):Unit = {
    val f = new File(s"$recordLocation$roomId/$startTime/")
    if(f.exists()){
      f.listFiles().foreach{
        e =>
          e.delete()
      }
      f.delete()
    }
  }

  private def getVideoDuration(roomId:Long,startTime:Long) ={
    val ffprobe = Loader.load(classOf[org.bytedeco.ffmpeg.ffprobe])
    //容器时长（container duration）
    val pb = new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration", "-of","csv=\"p=0\"","-i", s"$recordLocation$roomId/$startTime/record.mp4")
    val processor = pb.start()
    val br = new BufferedReader(new InputStreamReader(processor.getInputStream))
    val s = br.readLine()
    var duration = 0
    if(s!= null){
      duration = (s.toDouble * 1000).toInt
    }
    br.close()
//    if(processor != null){
//      processor.destroyForcibly()
//    }
    millis2HHMMSS(duration)
  }

  def millis2HHMMSS(sec: Double): String = {
    val hours = (sec / 3600000).toInt
    val h =  if (hours >= 10) hours.toString else "0" + hours
    val minutes = ((sec % 3600000) / 60000).toInt
    val m = if (minutes >= 10) minutes.toString else "0" + minutes
    val seconds = ((sec % 60000) / 1000).toInt
    val s = if (seconds >= 10) seconds.toString else "0" + seconds
    val dec = ((sec % 1000) / 10).toInt
    val d = if (dec >= 10) dec.toString else "0" + dec
    s"$h:$m:$s.$d"
  }

//  private def getVideoDuration(roomId:Long,startTime:Long) = {
//    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
//    val pb = new ProcessBuilder(ffmpeg, "-i", s"$recordLocation$roomId/$startTime/record.ts")
//    val processor = pb.start()
//
//    val br = new BufferedReader(new InputStreamReader(processor.getErrorStream))
//    val sb = new StringBuilder()
//    var s = ""
//    s = br.readLine()
//    while(s!=null){
//      sb.append(s)
//      s = br.readLine()
//    }
//    br.close()
//
//    val regex = "Duration: (.*?),"
//    val p = Pattern.compile(regex)
//    val m = p.matcher(sb.toString())
//    if(m.find()) {
//      m.group(1)
//    }else{
//      "00:00:00.00"
//    }
//  }

  private def getSaveActor(ctx: ActorContext[Command], roomId: Long , startTime:Long) = {
    val childName = s"saveActor_${roomId}_$startTime"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(SaveActor.create(roomId, startTime), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor
    }.unsafeUpcast[SaveActor.Command]
  }
}
