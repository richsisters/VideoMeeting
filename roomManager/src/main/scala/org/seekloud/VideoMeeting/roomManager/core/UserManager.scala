package org.seekloud.VideoMeeting.roomManager.core

import java.util.concurrent.atomic.AtomicLong

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, StashBuffer, TimerScheduler}
import akka.stream.scaladsl.Flow
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{LiveInfo, RoomInfo, UserInfo}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl.server2Manager.CommonProtocol.VerifyRsp
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager, scheduler, timeout}
import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
import org.seekloud.VideoMeeting.roomManager.core.UserActor.ChildDead
import org.seekloud.VideoMeeting.roomManager.models.dao.{AdminDAO, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil.nonceStr

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}
/**
  * created by ltm on
  * 2019/7/16
  */
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.util.ByteString
import org.slf4j.LoggerFactory

object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  private final case object BehaviorChangeKey

  case class TimeOut(msg:String) extends Command

  final case class WebSocketFlowSetup(userId:Long,roomId:Long,temporary:Boolean,replyTo:ActorRef[Option[Flow[Message,Message,Any]]]) extends Command

  final case class Register(code:String, email:String, userName:String, password:String, replyTo:ActorRef[SignUpRsp]) extends Command

  final case class SetupWs(uidOpt:Long, tokenOpt:String ,roomId:Long,replyTo: ActorRef[Option[Flow[Message, Message, Any]]]) extends Command

  final case class TemporaryUser(replyTo:ActorRef[GetTemporaryUserRsp]) extends Command

  case class DeleteTemporaryUser(userId:Long) extends Command

  case class SealUserInfo(userId:Long,sealUtilTime:Long,replyTo:ActorRef[CommonRsp]) extends Command//封号

  case class CancelSealUserInfo4Timer(userId:Long) extends Command
  case class CancelSealUserInfo(userId:Long,replyTo:ActorRef[CommonRsp]) extends Command


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
            roomManager ! ActorProtocol.AddUserActor4Test(Common.TestConfig.TEST_USER_ID,Common.TestConfig.TEST_ROOM_ID,getUserActor(Common.TestConfig.TEST_USER_ID,false,ctx))
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
          val userId = userIdGenerator.getAndIncrement()
          val userInfo = UserInfo(userId, s"Guest$userId", Common.DefaultImg.headImg,nonceStr(40),AppSettings.guestTokenExistTime)
          replyTo ! GetTemporaryUserRsp(Some(userInfo))
          temporaryUserMap.put(userId,(System.currentTimeMillis(),userInfo))
          timer.startSingleTimer(s"DeleteTemporaryUser_$userId",DeleteTemporaryUser(userId),deleteTemporaryDelay)
          Behaviors.same

        case DeleteTemporaryUser(userId) =>
          temporaryUserMap.remove(userId)
          Behaviors.same

        case SealUserInfo(userId,sealUtilTime,replyTo) =>
          AdminDAO.sealUserInfo(userId,sealUtilTime).map{r =>
            log.debug(s"${ctx.self.path} 封号成功，userId=$userId")
//            timer.startSingleTimer(s"取消封号_$userId",CancelSealUserInfo4Timer(userId),(sealUtilTime - System.currentTimeMillis()).millis)
            replyTo ! CommonRsp()
          }.recover{
            case e:Exception =>
              replyTo ! CommonRsp(1000345,s"封号失败，数据库错误，error:$e")
          }
          Behaviors.same

        case CancelSealUserInfo4Timer(userId) =>
          AdminDAO.cancelSealUserInfo(userId).map{r =>
            log.debug(s"${ctx.self.path} 取消封号成功，userId=$userId")
          }.recover{
            case e:Exception =>
              log.debug(s"${ctx.self.path} 取消封号失败，userId=$userId ，error=$e")
          }
          Behaviors.same

        case CancelSealUserInfo(userId,replyTo) =>
          AdminDAO.cancelSealUserInfo(userId).map{r =>
            replyTo ! CommonRsp()
          }.recover{
            case e:Exception =>
              replyTo ! CommonRsp(100045,s"取消封号失败 ，error=$e")
          }
          Behaviors.same

        case SetupWs(uid, token, roomId,replyTo) =>
          UserInfoDao.verifyUserWithToken(uid, token).onComplete {
            case Success(f) =>
              if (f) {
                log.debug(s"${ctx.self.path} ws start")
                val flowFuture: Future[Option[Flow[Message, Message, Any]]] = ctx.self ? (WebSocketFlowSetup(uid,roomId,false, _))
                flowFuture.map(replyTo ! _)
              } else {
                temporaryUserMap.get(uid) match {
                  case Some((createTime,userInfo)) =>
                    if(token == userInfo.token && (System.currentTimeMillis() / 1000 - createTime / 1000 < AppSettings.guestTokenExistTime)){
                      log.debug(s"${ctx.self.path} the user is temporary")
                      val flowFuture: Future[Option[Flow[Message, Message, Any]]] = ctx.self ? (WebSocketFlowSetup(uid,roomId,true, _))
                      flowFuture.map(replyTo ! _)
                    }else{
                      log.debug(s"${ctx.self.path}setup websocket error: the user doesn't exist or the token is wrong")
                      replyTo ! None
                    }
                  case None =>
                    log.debug(s"${ctx.self.path} setup websocket error:the websocket req is illegal")
                    replyTo ! None
                }

              }
            case Failure(e) =>
              log.error(s"getBindWx future error: $e")
              replyTo ! None
          }
          Behaviors.same

        case WebSocketFlowSetup(userId,roomId,temporary,replyTo) =>
          if(temporary){
            val existRoom:Future[Boolean] = roomManager ? (RoomManager.ExistRoom(roomId,_))
            existRoom.map{exist =>
              if(exist){
                log.info(s"${ctx.self.path} websocket will setup for user:$userId")
                getUserActorOpt(userId,temporary,ctx) match{
                  case Some(actor) =>
                    log.debug(s"${ctx.self.path} setup websocket error:该账户已经登录userId=$userId,temporary=$temporary")
                    //TODO 重复登录相关处理
//                    actor ! UserActor.UserLogin(roomId,userId)
//                    replyTo ! Some(setupWebSocketFlow(actor))
                    replyTo ! None
                  case None =>
                    val userActor = getUserActor(userId, temporary,ctx)
                    userActor ! UserActor.UserLogin(roomId,userId)
                    replyTo ! Some(setupWebSocketFlow(userActor))
                }


              }else{
                log.debug(s"${ctx.self.path} setup websocket error:the room doesn't exist")
                replyTo ! None
              }
            }
          }else{
            log.info(s"${ctx.self.path} websocket will setup for user:$userId")
            getUserActorOpt(userId,temporary,ctx) match{
              case Some(actor) =>
                log.debug(s"${ctx.self.path} setup websocket error:该账户已经登录userId=$userId,temporary=$temporary")
                //TODO 重复登录相关处理
//                actor ! UserActor.UserLogin(roomId,userId)
//                replyTo ! Some(setupWebSocketFlow(actor))
                replyTo ! None
              case None =>
                val userActor = getUserActor(userId, temporary,ctx)
                userActor ! UserActor.UserLogin(roomId,userId)
                replyTo ! Some(setupWebSocketFlow(userActor))
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

    implicit def parseJsonString2WsMsgClient(s: String): Option[WsMsgClient] = {
      import io.circe.generic.auto._
      import io.circe.parser._

      try {
        val wsMsg = decode[WsMsgClient](s).right.get
        Some(wsMsg)
      } catch {
        case e: Exception =>
          log.warn(s"parse front msg failed when json parse,s=${s},e=$e")
          None
      }
    }
    Flow[Message]
      .collect {
        case TextMessage.Strict(m) =>
          log.debug(s"接收到ws消息，类型TextMessage.Strict，msg-${m}")
          UserActor.WebSocketMsg(m)

        case BinaryMessage.Strict(m) =>
//          log.debug(s"接收到ws消息，类型Binary")
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
