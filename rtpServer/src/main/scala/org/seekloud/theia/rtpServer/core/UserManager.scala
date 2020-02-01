package org.seekloud.VideoMeeting.rtpServer.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.protocol.ApiProtocol.LiveInfo
import org.seekloud.VideoMeeting.rtpServer.utils.SecureUtil
import org.slf4j.LoggerFactory
/**
  * Created by haoshuhan on 2019/8/28.
  */
object UserManager {
  sealed trait Command

  case class GenLiveIdAndLiveCode(replyTo: ActorRef[LiveInfo]) extends Command

  case class Auth(liveId: String, liveCode: String, replyTo: ActorRef[Either[String, Int]]) extends Command

  private val log = LoggerFactory.getLogger(this.getClass)

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        val seq = new AtomicInteger(100001)
        work(seq)
      }
    }
  }

  def work(seq: AtomicInteger,
           liveInfo: Map[String, String] = Map())
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case GenLiveIdAndLiveCode(replyTo) =>
          val liveId = s"user${seq.getAndIncrement()}"
          val liveCode = SecureUtil.nonceStr(10)
          log.info(s"gen liveId: $liveId, liveCode: $liveCode success")
          replyTo ! LiveInfo(liveId, liveCode)
          work(seq, liveInfo + (liveId -> liveCode))

        case Auth(liveId, liveCode, replyTo) =>
          liveInfo.get(liveId) match {
            case Some(code) if code == liveCode =>
              replyTo ! Right(1)
              log.info(s"liveId:$liveId auth success!")

            case Some(code) =>
              replyTo ! Left("wrong liveCode!")

            case None =>
              replyTo ! Left("liveId 不存在！")

          }
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same

      }

    }
  }

}
