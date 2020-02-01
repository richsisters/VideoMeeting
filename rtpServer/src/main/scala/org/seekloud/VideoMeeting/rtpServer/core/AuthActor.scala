package org.seekloud.VideoMeeting.rtpServer.core

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil._
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil.toInt
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.Boot.streamManager
import org.seekloud.VideoMeeting.rtpServer.Boot.timeout
import org.seekloud.VideoMeeting.rtpServer.Boot.scheduler
import org.seekloud.VideoMeeting.rtpServer.core.StreamManager.AuthSuccess

import scala.util.Random
import org.seekloud.VideoMeeting.rtpServer.Boot.executor
import org.seekloud.VideoMeeting.rtpServer.Boot.userManager
import org.seekloud.VideoMeeting.rtpServer.utils.RoomManagerClient._
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil._
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.Address

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by haoshuhan on 2019/7/16.
  */
object AuthActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command
  case class AuthData(payload: Array[Byte], channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command
  case class AuthSuccess(payload: Array[Byte], channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command


  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        work()
      }
    }
  }

  def work(liveIdAndSsrc: mutable.Map[String, Int] = mutable.Map())
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@AuthData(payloadBytes, channel, remoteAddress) =>
          val payload = new String(payloadBytes, "UTF-8")
          val split = payload.split("#")
          if (split.length < 2)
            log.info(s"wrong data format when auth, drop it")
          else {
            val liveId = split(0)
            val liveCode = split(1)
            val liveIdBytes = liveId.getBytes("UTF-8")
            val future: Future[Either[String, Int]] = userManager ? (UserManager.Auth(liveId, liveCode, _))
            future.map {
              case Right(_) =>
                val ssrcInt = if (liveIdAndSsrc.get(liveId).isDefined) liveIdAndSsrc(liveId) else {
                  val ssrcArray = new Array[Byte](4)
                  new Random().nextBytes(ssrcArray)
                  toInt(ssrcArray)
                }
                liveIdAndSsrc.update(liveId, ssrcInt)
//                timer.startPeriodicTimer("123", m, 2000.millis) //test
                sendRtpPackage(AppSettings.authResponse, ssrcInt, liveIdBytes, channel, remoteAddress)
                log.info(s"liveId: $liveId, ssrcLong: $ssrcInt")
//                log.info(s"ssrcByte${ssrcArray.map(_.toHexString).toList}")
                val ip = remoteAddress.getAddress.getHostAddress
                val host = remoteAddress.getPort
                streamManager ! StreamManager.AuthSuccess(liveId, ssrcInt, Address(ip, host))
              case Left(error) =>
                sendRtpPackage(AppSettings.authRefuseResponse, 0, liveIdBytes, channel, remoteAddress)
                log.error(s"liveId: $liveId, auth error: $error")
            }
          }
          work(liveIdAndSsrc)

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }

    }
  }
}
