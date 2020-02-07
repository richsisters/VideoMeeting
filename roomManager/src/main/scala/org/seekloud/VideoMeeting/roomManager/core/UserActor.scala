package org.seekloud.VideoMeeting.roomManager.core

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager, scheduler}
import org.seekloud.VideoMeeting.roomManager.common.Common
import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol
import org.seekloud.VideoMeeting.roomManager.utils.RtpClient
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * created by ltm on
  * 2019/7/16
  */

object UserActor {


  import org.seekloud.byteobject.ByteObject._

  import scala.language.implicitConversions

  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)
  private final val BusyTime = Some(5.minutes)

  trait Command

  /**web socket 消息*/
  final case class WebSocketMsg(msg:Option[WsMsgClient]) extends Command
  final case class DispatchMsg(msg:WsMsgRm,closeRoom:Boolean) extends Command
  case object CompleteMsgClient extends Command
  case class FailMsgClient(ex:Throwable) extends Command
  case class UserClientActor(actor:ActorRef[WsMsgRm]) extends Command

  /**http消息*/
  final case class UserLogin(roomId:Long,userId:Long) extends Command with UserManager.Command//新用户请求mpd的时候处理这个消息，更新roomActor中的列表

  case class UserLeft[U](actorRef: ActorRef[U]) extends Command
  final case class ChildDead[U](userId: Long,temporary:Boolean, childRef: ActorRef[U]) extends Command with UserManager.Command
  final case object ChangeBehaviorToInit extends Command
  final case object SendHeartBeat extends  Command

  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends Command

  private case class TimeOut(msg: String) extends Command

  private final case object BehaviorChangeKey

  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  /**
    * userId
    * temporary:true--临时用户，false--登陆用户
    * */
  def create(userId: Long,temporary:Boolean): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      log.info(s"userActor-$userId is starting...")
      ctx.setReceiveTimeout(30.seconds, CompleteMsgClient)
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
        init(userId,temporary,None)
      }
    }
  }

  private def init(
                    userId:Long,
                    temporary:Boolean,
                    roomIdOpt:Option[Long]
                  )(
    implicit stashBuffer:StashBuffer[Command],
    sendBuffer: MiddleBufferInJvm,
    timer: TimerScheduler[Command]
  ):Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case UserClientActor(clientActor) =>
            ctx.watchWith(clientActor, UserLeft(clientActor))
            timer.startPeriodicTimer("HeartBeatKey_" + userId, SendHeartBeat, 10.seconds)
            switchBehavior(ctx, "audience", audience(userId,temporary,clientActor,roomIdOpt.get))


          case UserLogin(roomId,`userId`) =>
            //先发一个用户登陆，再切换到其他的状态
            roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.join,roomId,userId,temporary,Some(ctx.self))
            init(userId,temporary,Some(roomId))

          case TimeOut(m) =>
            log.debug(s"${ctx.self.path} is time out when busy,msg=${m}")
            Behaviors.stopped

          case unknown =>
            if(userId == Common.TestConfig.TEST_USER_ID){
              log.debug(s"${ctx.self.path} 测试房间的房主actor，不处理其他类型的消息msg=$unknown")
            }else{
              log.debug(s"${ctx.self.path} recv an unknown msg:${msg} in init state...")
              stashBuffer.stash(unknown)
            }

            Behavior.same
        }
    }
  }

  //主播，主播肯定不是临时用户，主播的房间id
  private def anchor(
                      userId: Long,
                      clientActor:ActorRef[WsMsgRm],
                      roomId:Long
                    )
                    (
                    implicit stashBuffer: StashBuffer[Command],
                    timer:TimerScheduler[Command],
                    sendBuffer:MiddleBufferInJvm
                    ):Behavior[Command] =
    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {
        case SendHeartBeat =>
//          log.debug(s"${ctx.self.path} 发送心跳给userId=$userId,roomId=$roomId")
          ctx.scheduleOnce(10.seconds, clientActor, Wrap(HeatBeat(System.currentTimeMillis()).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
          Behaviors.same

        case DispatchMsg(message,closeRoom) =>
          clientActor ! message
          Behaviors.same

        case WebSocketMsg(reqOpt) =>
          if(reqOpt.contains(PingPackage)){
            if(timer.isTimerActive("HeartBeatKey_" + userId)) timer.cancel("HeartBeatKey_" + userId)
            ctx.self ! SendHeartBeat
            Behaviors.same
          }
          else{
            reqOpt match{
              case Some(req) =>
                UserInfoDao.searchById(userId).map{
                  case Some(v) =>
                    if(v.`sealed`){
                      log.debug(s"${ctx.self.path} 该用户已经被封号，无法发送ws消息")
                      clientActor ! Wrap(AuthProtocol.AccountSealed.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                      ctx.self ! CompleteMsgClient
                      ctx.self ! SwitchBehavior("anchor",anchor(userId,clientActor,roomId))
                    }else{
                      req match {
                        case StartLiveReq(`userId`,token,clientType) =>
                          roomManager ! ActorProtocol.StartLiveAgain(roomId)
                          ctx.self ! SwitchBehavior("anchor",anchor(userId,clientActor,roomId))

                        case x =>
                          roomManager ! ActorProtocol.WebSocketMsgWithActor(userId,roomId,x)
                          ctx.self ! SwitchBehavior("anchor",anchor(userId,clientActor,roomId))

                      }
                    }
                  case None =>
                    log.debug(s"${ctx.self.path} 该用户不存在，无法直播")
                    clientActor !Wrap(AuthProtocol.NoUser.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                    ctx.self ! CompleteMsgClient
                    ctx.self ! SwitchBehavior("anchor",anchor(userId,clientActor,roomId))
                }
                switchBehavior(ctx,"busy",busy(),BusyTime,TimeOut("busy"))
              case None =>
                log.debug(s"${ctx.self.path} there is no web socket msg in anchor state")
                Behaviors.same
            }
          }

        case CompleteMsgClient =>
          //主播需要关闭房间，通知所有观众
          //观众需要清楚房间中对应的用户信息映射
          log.debug(s"${ctx.self.path.name} 主播关闭房间，roomId=$roomId,userId=$userId")
          roomManager ! ActorProtocol.HostCloseRoom(roomId)
          Behaviors.stopped

        case FailMsgClient(ex) =>
          log.debug(s"${ctx.self.path} websocket消息错误，断开ws=${userId} error=$ex")
          roomManager ! ActorProtocol.HostCloseRoom(roomId)
          Behaviors.stopped

        case ChangeBehaviorToInit =>
          log.debug(s"${ctx.self.path} 切换到init状态")
          init(userId,false,None)

        case unknown =>
          log.debug(s"${ctx.self.path} recv an unknown msg:${msg} in anchor state...")
          stashBuffer.stash(unknown)
          Behavior.same
      }
    }

  //观众
  private def audience(
                        userId: Long,
                        temporary:Boolean,
                        clientActor:ActorRef[WsMsgRm],
                        roomId:Long//观众所在的房间id
                      )
                    (
                      implicit stashBuffer: StashBuffer[Command],
                      timer:TimerScheduler[Command],
                      sendBuffer:MiddleBufferInJvm
                    ):Behavior[Command] =
    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {
        case SendHeartBeat =>
//          log.debug(s"${ctx.self.path} 发送心跳给userId=$userId,roomId=$roomId")
          ctx.scheduleOnce(10.seconds, clientActor, Wrap(HeatBeat(System.currentTimeMillis()).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
          Behaviors.same

        case DispatchMsg(message,closeRoom) =>
          clientActor ! message
          if(closeRoom){
            Behaviors.stopped
          }else{
            Behaviors.same
          }

        case CompleteMsgClient =>
          //主播需要关闭房间，通知所有观众
          //观众需要清楚房间中对应的用户信息映射
          log.debug(s"${ctx.self.path.name} complete msg")
          timer.cancelAll()
          roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.left,roomId,userId,temporary,Some(ctx.self))
          Behaviors.stopped

        case FailMsgClient(ex) =>
          log.debug(s"${ctx.self.path} websocket消息错误，断开ws=${userId} error=$ex")
          roomManager ! ActorProtocol.UpdateSubscriber(Common.Subscriber.left,roomId,userId,temporary,Some(ctx.self))
          Behaviors.stopped

        case WebSocketMsg(reqOpt) =>
          if(reqOpt.contains(PingPackage)){
            if(timer.isTimerActive("HeartBeatKey_" + userId)) timer.cancel("HeartBeatKey_" + userId)
            ctx.self ! SendHeartBeat
            Behaviors.same
          }
          else{
            reqOpt match{
              case Some(req) =>
                if(temporary){
                  //                log.debug(s"${ctx.self.path} the user is temporary, no privilege,userId=$userId in room=$roomId")
                  Behaviors.same
                }else{
                  UserInfoDao.searchById(userId).map{
                    case Some(v) =>
                      if(v.`sealed`){
                        log.debug(s"${ctx.self.path} 该用户已经被封号，无法发送ws消息")
                        clientActor !Wrap(AuthProtocol.AccountSealed.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                        ctx.self ! SwitchBehavior("audience",audience(userId,temporary,clientActor,roomId))
                      }else{
                        log.debug(s"########${ctx.self.path} 接收到ws消息")
                        req match{
                          case StartLiveReq(`userId`,token,clientType) =>
                            roomManager ! ActorProtocol.StartRoom4Anchor(userId,roomId,ctx.self)
                            ctx.self ! SwitchBehavior("anchor",anchor(userId,clientActor,roomId))

                          case x =>
                            roomManager ! ActorProtocol.WebSocketMsgWithActor(userId,roomId,req)
                            ctx.self ! SwitchBehavior("audience",audience(userId,temporary,clientActor,roomId))
                        }
                      }
                    case None =>
                      log.debug(s"${ctx.self.path} 该用户不存在，无法直播")
                      clientActor !Wrap(AuthProtocol.NoUser.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
                      ctx.self ! CompleteMsgClient
                      ctx.self ! SwitchBehavior("audience",audience(userId,temporary,clientActor,roomId))
                  }
                  switchBehavior(ctx,"busy",busy(),BusyTime,TimeOut("busy"))
                }

              case None =>
                log.debug(s"${ctx.self.path} there is no web socket msg in anchor state")
                Behaviors.same
            }
          }


        case ChangeBehaviorToInit =>
          log.debug(s"${ctx.self.path} 切换到init状态")
          init(userId,temporary,None)

        case unknown =>
          log.debug(s"${ctx.self.path} recv an unknown msg:${msg} in audience state...")
          stashBuffer.stash(unknown)
          Behavior.same
      }
    }

  private def busy()
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]
    ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path.name} is time out when busy, msg=$m")
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }

//  private def searchUser(uid:Long,actor:ActorRef[UserActor.Command],idleState:IdleState,msg:Command) = {
//    UserInfoDao.SearchById(uid).onComplete{
//      case Success(resOpt) =>
//        if(resOpt.nonEmpty){
//
//
//        }else{
//
//        }
//      case Failure(error) =>
//        actor ! SwitchBehavior("idle",idle(idleState.userId,idleState.temporary,idleState.clientActor,idleState.liveIdOpt))
//    }
//
//  }

  private def sink(userActor: ActorRef[UserActor.Command]) = ActorSink.actorRef[Command](
    ref = userActor,
    onCompleteMessage = CompleteMsgClient,
    onFailureMessage = { e =>
      e.printStackTrace()
      FailMsgClient(e)
    }
  )


  def flow(userActor: ActorRef[UserActor.Command]):Flow[WebSocketMsg,WsMsgManager,Any] = {
    val in = Flow[WebSocketMsg].to(sink(userActor))
    val out = ActorSource.actorRef[WsMsgManager](
      completionMatcher = {
        case CompleteMsgRm =>
          println("flow got CompleteMsgRm msg")
//          userActor ! HostCloseRoom(None)
      },
      failureMatcher = {
        case FailMsgRm(e) =>
          e.printStackTrace()
          e
      },
      bufferSize = 256,
      overflowStrategy = OverflowStrategy.dropHead
    ).mapMaterializedValue(outActor => userActor ! UserClientActor(outActor))
    Flow.fromSinkAndSource(in,out)
  }
}
