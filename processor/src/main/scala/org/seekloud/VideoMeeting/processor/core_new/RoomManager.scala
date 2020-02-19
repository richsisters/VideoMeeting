package org.seekloud.VideoMeeting.processor.core_new

import java.io.{BufferedReader, File, InputStreamReader}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.bytedeco.javacpp.Loader
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import org.seekloud.VideoMeeting.processor.common.AppSettings.recordPath

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午2:27
  *
  * actor由Boot创建
  * 连线房间管理
  * 对接roomManager
  */
object RoomManager {

  private  val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class NewConnection(roomId: Long, host: String, clientInfo: List[String], pushLiveId: String, pushLiveCode: String, startTime: Long) extends Command

  case class ForceExit(roomId: Long, liveId: String) extends Command //主持人强制某人退出

  case class BanOnClient(roomId: Long, liveId: String,isImg: Boolean, isSound: Boolean) extends Command

  case class CancelBan(roomId: Long, liveId: String, isImg: Boolean, isSound: Boolean) extends Command

  case class SpeakerRight(roomId: Long, liveId: String) extends Command

  case class CloseRoom(roomId: Long) extends Command

  case class RecorderRef(roomId: Long, ref: ActorRef[RecorderActor.Command]) extends Command

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[RoomActor.Command]) extends Command

  case class SeekRecord(roomId:Long, startTime:Long, reply:ActorRef[RecordInfo]) extends Command

  case class RemoveRecords(records:List[RecordData]) extends Command

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"roomManager start----")
          work( mutable.Map[Long,ActorRef[RoomActor.Command]]())
      }
    }
  }

  def work(roomInfoMap: mutable.Map[Long, ActorRef[RoomActor.Command]])
          (implicit stashBuffer: StashBuffer[Command],
    timer:TimerScheduler[Command]):Behavior[Command] = {
    log.info(s"roomManager is working")
    Behaviors.receive[Command]{ (ctx, msg) =>
      msg match {

        case msg:NewConnection =>
          log.info(s"${ctx.self} receive a msg${msg}")
          val roomActor = getRoomActor(ctx, msg.roomId, msg.host, msg.clientInfo, msg.pushLiveId, msg.pushLiveCode, msg.startTime) //fixme 参数更改
          roomActor ! RoomActor.NewRoom(msg.roomId, msg.host, msg.clientInfo,msg.pushLiveId, msg.pushLiveCode, msg.startTime)
          roomInfoMap.put(msg.roomId, roomActor)
          Behaviors.same

        case RecorderRef(roomId, ref) =>
          log.info(s"${ctx.self} receive a msg${msg}")
          val roomActor = roomInfoMap.get(roomId)
          if(roomActor.nonEmpty){
            roomActor.foreach(_ ! RoomActor.Recorder(roomId, ref) )
          }
          Behaviors.same

        case msg: ForceExit =>
          log.info(s"${ctx.self} receive a msg:${msg} ")
          val roomInfo = roomInfoMap.get(msg.roomId)
          if(roomInfo.nonEmpty){
            roomInfo.get ! RoomActor.ForceExit4Client(msg.roomId, msg.liveId)
          }
          Behaviors.same

        case msg: BanOnClient =>
          log.info(s"${ctx.self} receive a msg $msg")
          val roomInfo = roomInfoMap.get(msg.roomId)
          if(roomInfo.nonEmpty){
            roomInfo.get ! RoomActor.BanOnClient(msg.roomId, msg.liveId, msg.isImg, msg.isSound)
          }
          Behaviors.same

        case msg: CancelBan =>
          log.info(s"${ctx.self} receive a msg $msg")
          val roomInfo = roomInfoMap.get(msg.roomId)
          if(roomInfo.nonEmpty){
            roomInfo.get ! RoomActor.CancelBan(msg.roomId, msg.liveId, msg.isImg, msg.isSound)
          }
          Behaviors.same

        case msg: SpeakerRight =>
          log.info(s"${ctx.self} receive a msg $msg")
          val roomInfo = roomInfoMap.get(msg.roomId)
          if(roomInfo.nonEmpty){
            roomInfo.get ! RoomActor.SpeakerRight(msg.roomId, msg.liveId)
          }
          Behaviors.same

        case msg:CloseRoom =>
          log.info(s"${ctx.self} receive a msg:${msg} ")
          val roomInfo = roomInfoMap.get(msg.roomId)
          if(roomInfo.nonEmpty){
            roomInfo.get ! RoomActor.CloseRoom(msg.roomId)
          }
          roomInfoMap.remove(msg.roomId)
          Behaviors.same

        case ChildDead(roomId, childName, value) =>
          log.info(s"${childName} is dead ")
          roomInfoMap.remove(roomId)
          Behaviors.same

        case msg: SeekRecord =>
          log.info(s"${ctx.self} receive a msg $msg")
          val file = new File(s"$recordPath${msg.roomId}/${msg.startTime}/record.mp4")
          if(file.exists()){
            val d = getVideoDuration(msg.roomId, msg.startTime)
            log.info(s"duration:$d")
            msg.reply ! RecordInfo(fileExist = true,d)
          }else{
            log.info(s"no record for roomId:${msg.roomId} and startTime:${msg.startTime}")
            msg.reply ! RecordInfo(fileExist = false,"00:00:00.00")
          }
          Behaviors.same

        case RemoveRecords(records) =>
          log.info(s"${ctx.self} receive a msg $msg")
          records.foreach{r =>
            removeRecord(r.roomId,r.startTime)
          }
          Behaviors.same

        case x =>
          log.info(s"${ctx.self} receive an unknown msg $x")
          Behaviors.same
      }
    }
  }

  def getRoomActor(ctx: ActorContext[Command], roomId:Long, host: String, clientInfo: List[String], pushLiveId: String,pushLiveCode: String,  startTime: Long) = {
    val childName = s"roomActor_${roomId}_${host}"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(RoomActor.create(roomId, host, clientInfo, pushLiveId, pushLiveCode, startTime), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor
    }.unsafeUpcast[RoomActor.Command]
  }

  def removeRecord(roomId:Long,startTime:Long):Unit = {
    val f = new File(s"$recordPath$roomId/$startTime/")
    if(f.exists()){
      f.listFiles().foreach{
        e =>
          e.delete()
      }
      f.delete()
    }
  }

  private def getVideoDuration(roomId:Long, startTime:Long) ={
    val ffprobe = Loader.load(classOf[org.bytedeco.ffmpeg.ffprobe])
    val pb = new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration", "-of","csv=\"p=0\"","-i", s"$recordPath$roomId/$startTime/record.mp4")
    val processor = pb.start()
    val br = new BufferedReader(new InputStreamReader(processor.getInputStream))
    val s = br.readLine()
    var duration = 0
    if(s!= null){
      duration = (s.toDouble * 1000).toInt
    }
    br.close()
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

}
