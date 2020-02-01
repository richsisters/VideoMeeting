//package org.seekloud.VideoMeeting.roomManager.core
//
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.actor.typed.scaladsl.{ActorContext, AskPattern, Behaviors, StashBuffer}
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
//import org.seekloud.VideoMeeting.roomManager.core.RegisterActor.{ConfirmEmail, GetInfo, SendEmail}
//import org.slf4j.LoggerFactory
//import akka.actor.typed.scaladsl.StashBuffer
//import org.seekloud.VideoMeeting.roomManager.Boot.executor
//import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil
//
//import scala.collection.mutable
//
///**
//  * Created by ltm on 2019/8/26.
//  */
//object RegisterManager {
//  private val log = LoggerFactory.getLogger(this.getClass)
//
//  trait Command
//
//  case class RegisterFinished(email: String) extends Command
//
//  case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command
//
//  val behavior = create()
//
//  def create():Behavior[Command] = {
//    Behaviors.setup[Command] { ctx =>
//      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
//      Behaviors.withTimers[Command] { implicit timer =>
//        idle(mutable.HashMap[String, String]()) //(email, code)
//      }
//    }
//  }
//
//  def idle(emailMap: mutable.HashMap[String, String]): Behavior[Command] = {
//    Behaviors.receive[Command] { (ctx, msg) =>
//      msg match {
//
//        case ChildDead(name, childRef) =>
//          log.debug(s"${ctx.self.path} the child ${name} dead")
//          Behaviors.same
//
//
//        case msg: SendEmail =>
//          getRegisterActor(ctx, msg.email) ! msg
//          emailMap += (msg.email -> msg.code)
//          Behaviors.same
//
//
//        case msg: ConfirmEmail =>
//          getRegisterActor(ctx, msg.email) ! msg
//          Behaviors.same
//
//
//
//        case RegisterFinished(email) =>
//          emailMap -= email
//          log.info(s"RegisterManager 不再监管 RegisterActor_$email")
//          Behaviors.same
//
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
//
//
//  private def getRegisterActor(ctx: ActorContext[Command], email: String) = {
//    val name = "registerActor_" + email
//    ctx.child(name).getOrElse {
//      val actor = ctx.spawn(RegisterActor.create(email), name)
//      ctx.watchWith(actor,ChildDead(name ,actor))
//      actor
//    }.unsafeUpcast[RegisterActor.Command]
//  }
//
//}
