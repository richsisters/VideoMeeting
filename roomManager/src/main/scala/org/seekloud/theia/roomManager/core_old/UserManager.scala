/*
package org.seekloud.VideoMeeting.roomManager.core

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{AudienceInfo, LiveInfo, RoomInfo, UserInfo}
import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives.handleWebSocketMessages
import akka.stream.scaladsl.Flow
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.server2Manager.CommonProtocol.{VerifyError, VerifyRsp}
import org.seekloud.VideoMeeting.roomManager.core.UserActor.{ChildDead, JoinRefuseReq}

import scala.concurrent.Future
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil.nonceStr

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.concurrent.duration.{FiniteDuration, _}
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager, scheduler, timeout}
import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil
/**
  * created by ltm on
  * 2019/7/16
  */
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.util.ByteString

import org.slf4j.LoggerFactory

object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  private final case object BehaviorChangeKey

  case class TimeOut(msg:String) extends Command

  final case class WebSocketFlowSetup(userId:Long,roomId:Long,temporary:Boolean,replyTo:ActorRef[Flow[Message,Message,Any]]) extends Command

  final case class Register(code:String, email:String, userName:String, password:String, replyTo:ActorRef[SignUpRsp]) extends Command

  final case class SetupWs(uidOpt:Long, tokenOpt:String ,roomId:Long,replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  final case class GetUser(uid: Long, replyTo: ActorRef[ActorRef[UserActor.Command]]) extends Command

  final case class TemporaryUser(replyTo:ActorRef[GetTemporaryUserRsp]) extends Command

  final case class UpdateLiveInfo(uid:Long, liveInfo: LiveInfo) extends Command

  case class verifyLiveInfo(liveInfo: LiveInfo, replyTo:ActorRef[VerifyRsp]) extends Command

  case class DeleteTemporaryUser(userId:Long) extends Command


  private val tokenExistTime = AppSettings.tokenExistTime * 1000L // seconds
  private val deleteTemporaryDelay = AppSettings.guestTokenExistTime.seconds

  private[this] def switchBehavior(ctx: ActorContext[Command], behaviorName: String,
                                   behavior:Behavior[Command], durationOpt: Option[FiniteDuration] = None,
                                   timeOut:TimeOut = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer:TimerScheduler[Command]) ={
    println(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }


  def create():Behavior[Command] = {
    log.debug(s"RoomManager start...")
    Behaviors.setup[Command]{
      ctx =>
        implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command]{
          implicit timer =>
            val userIdGenerator = new AtomicLong(1L)
            val temporaryUserMap = mutable.HashMap[Long, (Long,UserInfo)]()
            idle(userIdGenerator,temporaryUserMap)
        }
    }
  }

  //todo 临时用户token过期处理
  private def idle(
                    userIdGenerator:AtomicLong,
                    temporaryUserMap:mutable.HashMap[Long,(Long,UserInfo)],//临时用户,userId,createTime,userInfo
                  )
      (implicit stashBuffer: StashBuffer[Command],timer:TimerScheduler[Command]):Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {

        case TemporaryUser(replyTo) =>
          //fixme 用户创建的时间，并触发定时器，到时见取消用户
          val userId = userIdGenerator.getAndIncrement()
          val userInfo = UserInfo(userId, s"Guest$userId", Common.DefaultImg.headImg,nonceStr(40),AppSettings.guestTokenExistTime)
          //fixme 临时用户申请userId,token和启动userActor，维护临时用户列表
          replyTo ! GetTemporaryUserRsp(Some(userInfo))
          temporaryUserMap.put(userId,(System.currentTimeMillis(),userInfo))
          timer.startSingleTimer(s"DeleteTemporaryUser_$userId",DeleteTemporaryUser(userId),deleteTemporaryDelay)
          Behaviors.same

        case DeleteTemporaryUser(userId) =>
          temporaryUserMap.remove(userId)
          Behaviors.same

        case SetupWs(uid, token, roomId,replyTo) =>
          UserInfoDao.verifyUserWithToken(uid, token).onComplete {
            case Success(f) =>
              if (f) {
                log.debug(s"${ctx.self.path} ws start")
                val flowFuture: Future[Flow[Message, Message, Any]] = ctx.self ? (WebSocketFlowSetup(uid,roomId,false, _))
                flowFuture.map(replyTo ! _)
              } else {
                temporaryUserMap.get(uid) match {
                  case Some((createTime,userInfo)) =>
                    //fixme 临时用户的token过期？
                    if(token == userInfo.token && (System.currentTimeMillis() / 1000 - createTime / 1000 < AppSettings.guestTokenExistTime)){
                      log.debug(s"${ctx.self.path} the user is temporary")
                      val flowFuture: Future[Flow[Message, Message, Any]] = ctx.self ? (WebSocketFlowSetup(uid,roomId,true, _))
                      flowFuture.map(replyTo ! _)
                    }else{
                      log.debug(s"${ctx.self.path} the user doesn't exist or the token is wrong")
                    }
                  case None =>
                    log.debug(s"${ctx.self.path} the websocket req is illegal")
                }

              }
            case Failure(e) =>
              log.error(s"getBindWx future error: $e")
          }
          Behaviors.same

        case JoinRefuseReq(roomId,userId,accept,liveIdHost) =>
          log.debug(s"${ctx.self.path} get the connection actor to send the msg of refuse connection")
          getUserActor(userId,false,ctx) ! JoinRefuseReq(roomId,userId,accept,liveIdHost)//只有登陆用户才会有连线申请，才会发拒绝信息
          Behaviors.same


        case GetUser(uid, replyTo) =>
          replyTo ! getUserActor(uid,false,ctx)//GetUser的逻辑暂时不动
          Behaviors.same

        case UserActor.UserLogin(roomId,userId) =>
          //todo roomId是否需要存储在userActor中
          getUserActor(userId,false,ctx) ! UserActor.UserLogin(roomId,userId)
          Behaviors.same

        case WebSocketFlowSetup(userId,roomId,temporary,replyTo) =>
          if(temporary){
            val existRoom:Future[Boolean] = roomManager ? (RoomManager.ExistRoom(roomId,_))
            existRoom.map{exist =>
              if(exist){
                log.info(s"${ctx.self.path} websocket will setup for user:$userId")
                getUserActorOpt(userId,temporary,ctx) match{
                  case Some(actor) =>
//                    actor ! UserActor.ChangeBehaviorToInit
                    log.debug(s"${ctx.self.path} 用户已经登陆")
                  case None =>
                    val userActor = getUserActor(userId, temporary,ctx)
                    println(s"user login: $userId")
                    userActor ! UserActor.UserLogin(roomId,userId)
                    replyTo ! setupWebSocketFlow(userActor)
                }

              }else{
                log.debug(s"${ctx.self.path} the room doesn't exist")
              }
            }
          }else{
            log.info(s"${ctx.self.path} websocket will setup for user:$userId")
            getUserActorOpt(userId,temporary,ctx) match{
              case Some(actor) =>
//                actor ! UserActor.ChangeBehaviorToInit
                log.debug(s"${ctx.self.path} 用户已经登陆")
              case None =>
                val userActor = getUserActor(userId, temporary,ctx)
                println(s"user login: $userId")
                userActor ! UserActor.UserLogin(roomId,userId)
                replyTo ! setupWebSocketFlow(userActor)
            }

          }

          Behaviors.same

        case ChildDead(userId,temporary,actor) =>
          if(temporary){
            //token过期的时候删除用户
//            temporaryUserMap.remove(userId)
          }
          log.debug(s"${ctx.self.path} the child = ${ctx.children}")
          Behaviors.same

        case x =>
          log.warn(s"unknown msg: $x")
          Behaviors.unhandled
      }
    }

  private def getUserActor(userId:Long,temporary:Boolean,ctx: ActorContext[Command]) = {
    val childrenName = s"userActor-$userId-temp-$temporary"
    ctx.child(childrenName).getOrElse {
      val actor = ctx.spawn(UserActor.create(userId,temporary), childrenName)
      ctx.watchWith(actor, ChildDead(userId, temporary,actor))
      actor
    }.unsafeUpcast[UserActor.Command]
  }

  private def getUserActorOpt(userId:Long,temporary:Boolean,ctx:ActorContext[Command]) = {
    val childrenName = s"userActor-$userId-temp-$temporary"
    ctx.child(childrenName).map(_.unsafeUpcast[UserActor.Command])
  }


  private def setupWebSocketFlow(userActor:ActorRef[UserActor.Command]):Flow[Message,Message,Any]  = {
    import org.seekloud.byteobject.ByteObject._
    import org.seekloud.byteobject.MiddleBufferInJvm
    import scala.language.implicitConversions
    Flow[Message]
      .collect {
        case BinaryMessage.Strict(m) =>
          val buffer = new MiddleBufferInJvm(m.asByteBuffer)
          bytesDecode[WsMsgClient](buffer) match {
            case Right(req) =>
              UserActor.WebSocketMsg(Some(req))
            case Left(e) =>
              log.debug(s"websocket decode error:$e")
              UserActor.WebSocketMsg(None)
          }

        case x =>
          log.debug(s"$userActor recv a unsupported msg from websocket:$x")
          UserActor.WebSocketMsg(None)

      }
      .via(UserActor.flow(userActor))
      .map{
        case t: Wrap =>
//          val buffer = new MiddleBufferInJvm(16384)
//          val message = bytesDecode[WsMsgRm](buffer) match {
//            case Right(rst) => rst
//            case Left(e) => DecodeError
//          }
//
//          message match {
//            case HeatBeat(ts) =>
//              log.debug(s"heartbeat: $ts")
//
//            case x =>
//              log.debug(s"unknown msg:$x")
//
//          }
          BinaryMessage.Strict(ByteString(t.ws))
        case x =>
          log.debug(s"websocket send an unknown msg:$x")
          TextMessage.apply("")

      }

      .withAttributes(ActorAttributes.supervisionStrategy(decider = decider))
  }


  private val decider:Supervision.Decider = {
    e:Throwable =>
      e.printStackTrace()
      Supervision.Resume
  }



}
*/
