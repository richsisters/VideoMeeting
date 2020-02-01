//package org.seekloud.VideoMeeting.roomManager.core
//
//
//import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
//import org.seekloud.VideoMeeting.roomManager.Boot.{emailActor, registerManager}
//import org.seekloud.VideoMeeting.roomManager.common.AppSettings._
//import org.slf4j.LoggerFactory
//
//import scala.util.{Failure, Success}
//import akka.actor.typed.{ActorRef, Behavior}
//import org.seekloud.VideoMeeting.protocol.ptcl
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.UserInfo
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
//import akka.actor.typed.scaladsl.AskPattern._
//import org.seekloud.byteobject.MiddleBufferInJvm
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.{RegisterInfo, RegisterSuccessRsp, SignUpRsp, UserInfoRsp}
//import org.seekloud.VideoMeeting.roomManager.core.RegisterManager.RegisterFinished
//import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
//import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil
//
//import concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//import scala.concurrent.duration._
///**
//  * Created by ltm on 2019/8/26.
//  */
//object RegisterActor {
//  private val log = LoggerFactory.getLogger(this.getClass)
//
//  sealed trait Command
//
//  case class SendEmail(code: String, url:String, email: String, userName:String, password: String, replyTo: ActorRef[SignUpRsp]) extends Command with RegisterManager.Command
//
//  case class ConfirmEmail(code: String, email: String, replyTo: ActorRef[ptcl.Response]) extends Command with RegisterManager.Command
//
//  case class GetInfo(code: String, replyTo: ActorRef[ptcl.Response]) extends Command with RegisterManager.Command
//
//  case class TimeOut(msg: String) extends Command
//
//  case object SignUpFinish extends Command
//
//  private case object TimeoutKey
//
//  private case object BehaviorChangeKey
//
//  private val timeOutDuration = 24 * 60 * 60
//
//  private final case class SwitchBehavior(
//                                           name: String,
//                                           behavior: Behavior[Command],
//                                           durationOpt: Option[FiniteDuration] = None,
//                                           timeOut: TimeOut = TimeOut("busy time error")
//                                         ) extends Command
//
//  private[this] def switchBehavior(ctx: ActorContext[Command],
//                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
//                                  (implicit stashBuffer: StashBuffer[Command],
//                                   timer: TimerScheduler[Command]) = {
//    timer.cancel(BehaviorChangeKey)
//    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
//    stashBuffer.unstashAll(ctx, behavior)
//  }
//
//  def create(email: String) = {
//    log.debug(s"RegisterActor_$email start...")
//    Behaviors.setup[Command] {ctx =>
//      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
//      Behaviors.withTimers[Command] { implicit timer =>
//        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
//        idle()
//      }
//    }
//  }
//
//
//  def idle()
//          (implicit stashBuffer:StashBuffer[Command],
//           sendBuffer: MiddleBufferInJvm,
//           timer: TimerScheduler[Command]): Behavior[Command] = {
//    Behaviors.receive[Command] { (ctx, msg) =>
//      msg match {
//        case SendEmail(code, redirectUrl, email, name, password, replyTo) =>
//          val url = serverProtocol + "://" + serverHost + ":"  + serverPort + "/" + serverUrl + "/roomManager/user/confirmEmail" + s"?email=$email&code=$code"
//          emailActor ! EmailActor.SendConfirmEmail(url, email)
//          val timeOut: TimeOut = TimeOut("waiting for confirming time out")
//          timer.startSingleTimer(TimeoutKey, timeOut, timeOutDuration.seconds)
//          waitingForConfirm(code, redirectUrl, email, name, password, replyTo)
//
//
//        //未知消息
//        case x =>
//          log.warn(s"unknown msg: $x")
//          Behaviors.unhandled
//      }
//    }
//  }
//
//  def waitingForConfirm(code: String,
//                      redirectUrl: String,
//                      email:String,
//                      name: String,
//                      password: String,
//                      reply: ActorRef[SignUpRsp]
//                     )(implicit stashBuffer:StashBuffer[Command],
//                       sendBuffer: MiddleBufferInJvm,
//                       timer: TimerScheduler[Command]): Behavior[Command] = {
//  Behaviors.receive[Command] { (ctx, msg) =>
//    msg match {
//      case ConfirmEmail(receiveCode, _, replyTo) =>
//        if (receiveCode == code) {
//          val timestamp = System.currentTimeMillis()
//          val token = SecureUtil.nonceStr(40)
//          UserInfoDao.addUser(
//            email,name,SecureUtil.getSecurePassword(password, email, timestamp),token,timestamp,SecureUtil.nonceStr(40)
//          ).onComplete {
//            case Success(id) =>
//              println("add user success")
//              replyTo ! RegisterSuccessRsp(redirectUrl)
//              reply ! SignUpRsp()
//              registerManager ! RegisterFinished(email)
//              println(s"register actor $email stopped")
////              switchBehavior(ctx, "waitingForConfirm", waitingForConfirm(code, receiveCode, email, name, password, reply))
////              ctx.self ! SignUpFinish
//            case Failure(e) =>
//              log.debug(s"add register user failed, error: $e")
//              replyTo ! ptcl.CommonRsp(180004, "add register user failed")
////              ctx.self ! SignUpFinish
//          }
//        } else {
//          replyTo ! CommonRsp(180005, "code error")
//        }
//        val timeOut: TimeOut = TimeOut("waiting for getting info time out")
//        timer.startSingleTimer(TimeoutKey, timeOut, timeOutDuration.seconds)
//
////        switchBehavior(ctx, "busy", busy(), Some(10.minutes))
//        Behaviors.stopped
//        //未知消息
//
//      case SignUpFinish =>
//        log.debug("signUp finish")
//        Behaviors.stopped
//
//      case m: SendEmail =>
//        m.replyTo ! SignUpRsp(180003, "邮件已发送，请在注册邮箱中确认")
//        Behaviors.same
//
//      case TimeOut(m) =>
//        log.debug(s"time out: $m")
//        Behaviors.stopped
//
//      case x =>
//        log.warn(s"unknown msg: $x")
//        Behaviors.unhandled
//      }
//    }
//  }
//
//  private def busy()
//                  (
//                    implicit stashBuffer: StashBuffer[Command],
//                    timer: TimerScheduler[Command]
//                  ): Behavior[Command] =
//    Behaviors.receive[Command] { (ctx, msg) =>
//      msg match {
//        case SwitchBehavior(name, b, durationOpt, timeOut) =>
//          switchBehavior(ctx, name, b, durationOpt, timeOut)
//
//        case TimeOut(m) =>
//          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
//          Behaviors.stopped
//
//        case x =>
//          stashBuffer.stash(x)
//          Behavior.same
//
//      }
//    }
//}
//
