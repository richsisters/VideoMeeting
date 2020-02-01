package org.seekloud.VideoMeeting.processor.core

/**
  * User: yuwei
  * Date: 2019/5/26
  * Time: 12:21
  */

import java.io.{File, OutputStream}
import java.net.InetSocketAddress
import java.nio.channels.Pipe.SourceChannel
import java.nio.channels.{Channels, DatagramChannel, Pipe}
import org.seekloud.VideoMeeting.processor.stream.PipeStream
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration._
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol.{MpdRsp, RoomInfo, RtmpRsp}
import java.util.concurrent.atomic.AtomicLong

import org.seekloud.VideoMeeting.processor.common.AppSettings._
import org.seekloud.VideoMeeting.processor.Boot.{executor, sendActor}
import org.seekloud.VideoMeeting.processor.models.MpdInfoDao
import org.seekloud.VideoMeeting.processor.utils.RMClient
/**
  * User: yuwei
  * Date: 7/15/2019
  */
object RecorderManager {

  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)

  private val startTime4RC = mutable.Map[Long,Long]()

  sealed trait Command

  case class TimeOut(msg: String) extends Command

  case class CloseRoom(roomId:Long) extends Command

  case class UpdateRoomInfo(roomId:Long, liveIdList:List[String], startTime:Long, layout:Int, aiMode:Int) extends Command

  case class StartRecord(roomId:Long, liveIdList:List[String], startTime:Long, layout:Int, aiMode:Int) extends Command

  case class StartKey(roomId:Long)

  case class HostReconnect(roomId: Long, liveId: String, startTime: Long, pipe: PipeStream) extends Command

  case class CloseWrapper(roomId:Long) extends Command

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[RecorderActor.Command]) extends Command

  case class ChildDead4Send(roomId: Long, childName: String, value: ActorRef[WrapperActor.Command]) extends Command

  case class GetMpdAndRtmp(roomId:Long, reply:ActorRef[MpdRsp]) extends Command

  case class GetRtmpUrl(roomId:Long, reply:ActorRef[RtmpRsp]) extends Command

  case class Timer4Pipe(roomId:Long)

  case class ClosePipe(roomId:Long) extends Command

  private val counter = new AtomicLong(10000l)

  private val pipeMap = mutable.Map[Long, PipeStream]()

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"grabberManager start----")
          work(mutable.Map[Long, RoomInfo](), mutable.Map[Long, ActorRef[RecorderActor.Command]](),
            mutable.Map[Long, ActorRef[WrapperActor.Command]](),
            mutable.Map[Long,String]())
      }
    }
  }

  def work(roomInfoMap:mutable.Map[Long, RoomInfo],
           roomRefMap:mutable.Map[Long, ActorRef[RecorderActor.Command]],
           roomWrapperMap:mutable.Map[Long, ActorRef[WrapperActor.Command]],
           roomLiveMap:mutable.Map[Long, String])(implicit timer: TimerScheduler[Command],
  stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case UpdateRoomInfo(roomId, liveIdList, startTime, layout, aiMode) =>
          if(roomInfoMap.get(roomId).isEmpty ) {
            timer.startSingleTimer(StartKey(roomId),StartRecord(roomId,liveIdList,startTime,layout,aiMode),50.millis)
          }else{
            roomRefMap(roomId) ! RecorderActor.UpdateRoomInfo(roomId, liveIdList,startTime, layout, aiMode)
          }
          Behaviors.same

          //fixme 此处为何等待50ms
        case m@RecorderManager.StartRecord(roomId, liveIdList, startTime, layout, aiMode)=>
          log.info(s"got msg: $m")
          val pipe = new PipeStream
          val sink = pipe.getSink
          val source = pipe.getSource
          pipeMap.put(roomId, pipe)
//          if(roomRefMap.get(roomId).isDefined){
//            roomRefMap(roomId) ! RecorderActor.Stop
//          }
          val recorderActor = getRecorderActor(ctx, roomId, RoomInfo(roomId, liveIdList, layout, aiMode), Channels.newOutputStream(sink))
          RMClient.getLiveId().map{
            case Right(liveInfo) =>
              log.info(s"get liveInfo successfully:$liveInfo")
              val wrapperActor = getWrapperActor(ctx, startTime, roomId,liveInfo.liveId,source)
              roomLiveMap.put(roomId, liveInfo.liveId)
              sendActor ! SendActor.NewLive(liveInfo.liveId, liveInfo.liveCode)
              roomWrapperMap.put(roomId, wrapperActor)
            case Left(e) =>
              log.info(s"ask liveInfo from RM wrong: $e")
          }
          roomInfoMap.put(roomId, RoomInfo(roomId,liveIdList,layout,aiMode))
          roomRefMap.put(roomId, recorderActor)

//          // todo 添加录像----------------地址是假地址-----------gy
//          if(isRecord){
//            startTime4RC.put(roomId,startTime)
//            MpdInfoDao.addRecord(roomId, startTime,0l, "recordAddr")
//          }

          Behaviors.same

        case HostReconnect(roomId, liveId, startTime, pipe) =>
          val wrapperOpt = roomWrapperMap.get(roomId)
          if(wrapperOpt.isDefined){
            wrapperOpt.get ! WrapperActor.NewHostLive(startTime, pipe.getSource)
          }
          val pipeOpt = pipeMap.get(roomId)
          if(pipeOpt.isDefined){
            val p = pipeOpt.get
//            if(p.getSink.isBlocking){
//              println(s"room $roomId recorder sink is blocking.")
//            }
//            if(p.getSource.isOpen){
//              println(s"room $roomId wrapper source is open.")
//            }
            try{
              p.getSource.close()
              p.getSink.close()
            } catch {
              case e: Exception =>
                log.info(s"room $roomId record-wrapper pipe close error: $e")
            }
          }
          pipeMap.put(roomId, pipe)
          Behaviors.same

        case CloseRoom(roomId) =>
          roomRefMap.get(roomId).foreach(r => r!RecorderActor.Stop)
          roomWrapperMap.get(roomId).foreach(r=> r ! WrapperActor.Close)
          roomRefMap.remove(roomId)
          roomWrapperMap.remove(roomId)
          roomInfoMap.remove(roomId)
          roomLiveMap.remove(roomId)
          timer.startSingleTimer(Timer4Pipe(roomId), ClosePipe(roomId), 800.milli)

          //colseRoom时，更新录像结束时间---------gy
//          if(isRecord){
//            val endTime4Rc = System.currentTimeMillis()
//            MpdInfoDao.updateEndTime(roomId, startTime4RC(roomId), endTime4Rc)
//            startTime4RC.remove(roomId)
//          }

          Behaviors.same

        case ClosePipe(roomId) =>
          if(pipeMap.get(roomId).isDefined){
            log.info(s"--- release $roomId recorder push back pipe ---")
            pipeMap.remove(roomId)
          }
          Behaviors.same

        case m@GetMpdAndRtmp(roomId, reply) =>
          log.info(s"got msg: $m")
          val rtmpOpt = roomLiveMap.get(roomId)
          if(rtmpOpt.isDefined) {
            reply ! MpdRsp("", rtmpOpt.get)
          }else{
            reply ! MpdRsp("","",100024, "resources do not exist")
          }
          Behaviors.same

        case m@GetRtmpUrl(roomId, reply) =>
          log.info(s"got msg: $m")
          val liveOpt = roomLiveMap.get(roomId)
          if(liveOpt.nonEmpty){
            reply ! RtmpRsp(liveOpt.get)
          }else{
            reply ! RtmpRsp("", 1000211, "rtmpUrl is not exist")
          }
          Behaviors.same

        case ChildDead(roomId, childName, value) =>
          log.info(s"$childName is Dead")
          Behaviors.same

        case ChildDead4Send(roomId, childName, value) =>
          log.info(s"$childName is Dead")
          Behaviors.same

      }
    }
  }

  private def getRecorderActor(ctx: ActorContext[Command], roomId: Long, roomInfo:RoomInfo,out:OutputStream) = {
    val childName = s"recorderActor_${roomId}"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(RecorderActor.create(roomId, roomInfo.roles.head, roomInfo.layout, roomInfo.aiMode, out), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor
    }.unsafeUpcast[RecorderActor.Command]
  }

  private def getWrapperActor(ctx: ActorContext[Command],startTime:Long, roomId: Long, liveId:String, source:SourceChannel) = {
    val childName = s"wrapperActor_${roomId}"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(WrapperActor.create(roomId,liveId, source, startTime), childName)
      ctx.watchWith(actor, ChildDead4Send(roomId, childName, actor))
      actor
    }.unsafeUpcast[WrapperActor.Command]
  }

}