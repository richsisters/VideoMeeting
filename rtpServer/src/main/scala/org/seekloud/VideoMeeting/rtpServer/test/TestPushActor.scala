package org.seekloud.VideoMeeting.rtpServer.test

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpClient.Protocol.{AuthRsp, Header, PushStreamError}
import org.seekloud.VideoMeeting.rtpClient.{Protocol, PushStreamClient}
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings.tsStreamType
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

/**
  * Author: zwq
  * Date: 2019/10/11
  * Time: 15:12
  * 模拟推流客户端
  */
object TestPushActor {

//  private var lastSeq =  0

//  var list = List.empty[Int]

  val seq = new AtomicInteger(0)

  private val log = LoggerFactory.getLogger(this.getClass)

  case class Ready(client: PushStreamClient) extends Protocol.Command

  case class PushStream(liveId: String, ssrc: Int) extends Protocol.Command

  case class PushAnotherStream(ssrc: Int) extends Protocol.Command

  case class PullStream(liveId: List[String]) extends Protocol.Command

  case class Auth(liveId: String, liveCode: String) extends Protocol.Command

  def create(): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Protocol.Command] = StashBuffer[Protocol.Command](Int.MaxValue)
      Behaviors.withTimers[Protocol.Command] { implicit timer =>
        wait4Ready()
      }
    }
  }

  def wait4Ready()
    (implicit timer: TimerScheduler[Protocol.Command],
      stashBuffer: StashBuffer[Protocol.Command]): Behavior[Protocol.Command] = {
    Behaviors.receive[Protocol.Command] { (ctx, msg) =>
      msg match {
        case Ready(client) =>
          log.info("recv Ready")
          stashBuffer.unstashAll(ctx, work(client))

        case x =>
          stashBuffer.stash(x)
          Behavior.same
      }
    }
  }

  def work(client: PushStreamClient)
    (implicit timer: TimerScheduler[Protocol.Command],
      stashBuffer: StashBuffer[Protocol.Command]): Behavior[Protocol.Command] = {
    Behaviors.setup[Protocol.Command] { context =>

      client.authStart()
      timer.startPeriodicTimer(1, PushStream("", 1888), 8.millis)

      Behaviors.receiveMessage[Protocol.Command] {

        case Auth(liveId, liveCode) =>
          client.auth(liveId, liveCode)
          Behaviors.same

        case AuthRsp(liveId, ifSuccess) =>
          log.info("recv AuthRsp:" +  liveId + " auth " + ifSuccess)
          Behaviors.same

        case PushStream(liveId, ssrc) =>
          val realSeq = seq.getAndIncrement()
          val data = (0 until 1012).map(_.toByte).toArray
          client.sendData(Header(tsStreamType, 0, realSeq, 1580, System.currentTimeMillis()), data, client.pushStreamDst1, client.pushChannel, calcDelay = true)
          client.sendData(Header(tsStreamType, 0, realSeq, 1581, System.currentTimeMillis()), data, client.pushStreamDst1, client.pushChannel, calcDelay = true)
          Behaviors.same

        case PushAnotherStream(ssrc) =>
          //            timer.startPeriodicTimer(2, P ushStream("", ssrc), 2.5.millis)
          Behaviors.same

        case PushStreamError(liveId, errCode, msg) =>
          log.info("recv PushStreamError" + msg)
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }
}
