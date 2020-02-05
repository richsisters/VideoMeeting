package org.seekloud.VideoMeeting.processor.core_new

import java.io.File

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.model.Uri.Host
import org.seekloud.VideoMeeting.processor.common.AppSettings.{debugPath, isDebug, isTest}
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor._
import org.slf4j.LoggerFactory

import scala.collection.mutable

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

  //case class NewConnection(roomId: Long, host: String, client1: String, client2: String, client3: String, pushLiveId: String, pushLiveCode: String, layout: Int) extends Command
  case class NewConnection(roomId: Long, host: String, clientInfo: List[String], pushLiveId: String, pushLiveCode: String, layout: Int) extends Command

  case class CloseRoom(roomId: Long) extends Command

  case class UpdateRoomInfo(roomId: Long, layout:Int ) extends Command

  case class RecorderRef(roomId: Long, ref: ActorRef[RecorderActor.Command]) extends Command

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[RoomActor.Command]) extends Command

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
          val roomActor = getRoomActor(ctx, msg.roomId, msg.host, msg.clientInfo, msg.pushLiveId, msg.pushLiveCode, msg.layout) //fixme 参数更改
          roomActor ! RoomActor.NewRoom(msg.roomId, msg.host, msg.clientInfo,msg.pushLiveId, msg.pushLiveCode, msg.layout)
          roomInfoMap.put(msg.roomId, roomActor)
          Behaviors.same

        case msg:UpdateRoomInfo =>
          log.info(s"${ctx.self} receive a msg${msg}")
          val roomInfo = roomInfoMap.get(msg.roomId)
          if(roomInfo.nonEmpty){
            roomInfo.get ! RoomActor.UpdateRoomInfo(msg.roomId, msg.layout)
          }
          Behaviors.same

        case RecorderRef(roomId, ref) =>
          log.info(s"${ctx.self} receive a msg${msg}")
          val roomActor = roomInfoMap.get(roomId)
          if(roomActor.nonEmpty){
            roomActor.foreach(_ ! RoomActor.Recorder(roomId, ref) )
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

        case x =>
          log.info(s"${ctx.self} receive an unknown msg $x")
          Behaviors.same
      }
    }
  }

  def getRoomActor(ctx: ActorContext[Command], roomId:Long, host: String, clientInfo: List[String], pushLiveId: String,pushLiveCode: String,  layout: Int) = {
    val childName = s"roomActor_${roomId}_${host}"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(RoomActor.create(roomId, host, clientInfo, pushLiveId, pushLiveCode, layout), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor
    }.unsafeUpcast[RoomActor.Command]
  }




}
