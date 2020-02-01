package org.seekloud.VideoMeeting.rtpServer.core

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol._
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.{Address, LiveInfo4Actor}
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil
import org.seekloud.VideoMeeting.rtpServer.Boot.streamManager
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings
import org.seekloud.VideoMeeting.rtpServer.core.StreamManager.GetSSRC
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil.sendRtpPackage

import concurrent.duration._
/**
  * Created by haoshuhan on 2019/7/17.
  */
object PublishActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class StreamData(data: Array[Byte]) extends Command

  case class Subscribe(clientId: Int,
                       channel: DatagramChannel,
                      remoteAddress: InetSocketAddress
  ) extends Command

  /*subscriber收到 rtpPackage(ssrc)的回复*/
  case class SubscriberRcvSSRC(clientId: Int,
                               channel: DatagramChannel,
                               remoteAddress: InetSocketAddress
  ) extends Command

  case class GetSSRC(info: (Int, LiveInfo4Actor)) extends Command

  case class Unsubscribe(clientId: Int) extends Command

  case class ChangeAddress(clientId: Int, channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command

  case class GetSubscribers(replyTo: ActorRef[List[Address]]) extends Command

  case class StopStream(replyTo: ActorRef[Boolean]) extends Command

  case object StopPushing extends Command

  def create(liveId: String): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      log.info(s"liveId -$liveId is starting......")
      Behaviors.withTimers[Command] { implicit timer =>
        waitData(liveId)
      }
    }
  }

  def waitData(
    liveId: String,
    liveInfo: Map[Int, LiveInfo4Actor] = Map(), //该 liveId的订阅用户
    liveInfoWithSSRC: Map[Int, LiveInfo4Actor] = Map(), //收到 rtpPackage(ssrc)的订阅用户
  )
    (implicit timer: TimerScheduler[Command],
      stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Subscribe(clientId, channel, remoteAddress) =>
          val host = remoteAddress.getAddress.getHostAddress
          val port = remoteAddress.getPort
          log.info(s"recv subscribe $liveId info from:$host:$port when waiting data")
          waitData(liveId, liveInfo + (clientId -> LiveInfo4Actor(channel, remoteAddress)), liveInfoWithSSRC)

        //该消息为subscriber收到 rtpPackage(ssrc)的回复消息
//        case SubscriberRcvSSRC(clientId, channel, remoteAddress) =>
//          val host = remoteAddress.getAddress.getHostAddress
//          val port = remoteAddress.getPort
//          log.info(s"recv SubscriberRcvSSRC $liveId info from:$host:$port when waiting data")
//          waitData(liveId, liveInfo, liveInfoWithSSRC + (clientId -> LiveInfo4Actor(channel, remoteAddress)))

        case Unsubscribe(clientId) =>
          log.info(s"recv unsubscribe $liveId info from :$clientId when waiting data")
          waitData(liveId, liveInfo - clientId, liveInfoWithSSRC - clientId)

        case GetSubscribers(replyTo) =>
          println("pbactor GetSubscribers")
          replyTo ! liveInfo.values.map(l =>
            Address(l.remoteAddress.getAddress.getHostAddress, l.remoteAddress.getPort)).toList
          Behaviors.same

        case ChangeAddress(clientId, channel, remoteAddress) =>
          log.info(s"clientId: $clientId change address to $remoteAddress ")
          if(liveInfoWithSSRC.contains(clientId)){
            waitData(liveId, liveInfo + (clientId -> LiveInfo4Actor(channel, remoteAddress)), liveInfoWithSSRC + (clientId -> LiveInfo4Actor(channel, remoteAddress)))
          } else {
            waitData(liveId, liveInfo + (clientId -> LiveInfo4Actor(channel, remoteAddress)), liveInfoWithSSRC)
          }

        case StopStream(replyTo) =>
          liveInfo.foreach{info =>
            val liveIdBytes = liveId.getBytes("UTF-8")
            sendRtpPackage(AppSettings.streamStopped, 0, liveIdBytes, info._2.channel, info._2.remoteAddress)
          }
          log.info(s"stream $liveId stopped")
          replyTo ! true
          Behaviors.stopped

        case StopPushing =>
          liveInfo.foreach{info =>
            val liveIdBytes = liveId.getBytes("UTF-8")
            sendRtpPackage(AppSettings.streamStopped, 0, liveIdBytes, info._2.channel, info._2.remoteAddress)
          }
          log.info(s"stream $liveId stopped")
          Behaviors.stopped

        case StreamData(data) =>
          log.info(s"$liveId rece first data")
          liveInfo.foreach{live =>
            streamManager ! StreamManager.GetSSRC(List(liveId), live._2.channel, live._2.remoteAddress)
            //start a 5s timer, send ssrc to client again.
            timer.startSingleTimer(
              GetSSRC(live),
              GetSSRC(live),
              5.seconds
            )
          }
          work(liveId, liveInfo, liveInfoWithSSRC)

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }

  def work(
    liveId: String,
    liveInfo: Map[Int, LiveInfo4Actor] = Map(), //该 liveId的订阅用户
    liveInfoWithSSRC: Map[Int, LiveInfo4Actor] = Map(), //收到 rtpPackage(ssrc)的订阅用户
  )
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Subscribe(clientId, channel, remoteAddress) =>
          val host = remoteAddress.getAddress.getHostAddress
          val port = remoteAddress.getPort
          log.info(s"recv subscribe $liveId info from:$host:$port when work")
          //start a timer, send ssrc to client again.
          val info = (clientId, LiveInfo4Actor(channel, remoteAddress))
          timer.startSingleTimer(
            GetSSRC(info),
            GetSSRC(info),
            5.seconds
          )
          work(liveId, liveInfo + (clientId -> LiveInfo4Actor(channel, remoteAddress)), liveInfoWithSSRC)

        //该消息为subscriber收到 rtpPackage(ssrc)的回复消息
        case SubscriberRcvSSRC(clientId, channel, remoteAddress) =>
          val host = remoteAddress.getAddress.getHostAddress
          val port = remoteAddress.getPort
          log.info(s"recv SubscriberRcvSSRC $liveId info from:$host:$port when work")
          // cancel timer
          val info = (clientId, LiveInfo4Actor(channel, remoteAddress))
          timer.cancel(GetSSRC(info))
          work(liveId, liveInfo, liveInfoWithSSRC + (clientId -> LiveInfo4Actor(channel, remoteAddress)))

        case Unsubscribe(clientId) =>
          log.info(s"recv unsubscribe $liveId info from :$clientId when work")
          work(liveId, liveInfo - clientId, liveInfoWithSSRC - clientId)

        case GetSubscribers(replyTo) =>
          replyTo ! liveInfo.values.map(l =>
            Address(l.remoteAddress.getAddress.getHostAddress, l.remoteAddress.getPort)).toList
          Behaviors.same

        case ChangeAddress(clientId, channel, remoteAddress) =>
          log.info(s"clientId: $clientId change address to $remoteAddress when work")
          if(liveInfoWithSSRC.contains(clientId)){
            work(liveId, liveInfo + (clientId -> LiveInfo4Actor(channel, remoteAddress)), liveInfoWithSSRC + (clientId -> LiveInfo4Actor(channel, remoteAddress)))
          } else {
            work(liveId, liveInfo + (clientId -> LiveInfo4Actor(channel, remoteAddress)), liveInfoWithSSRC)
          }

        case StopStream(replyTo) =>
          liveInfo.foreach{info =>
            val liveIdBytes = liveId.getBytes("UTF-8")
            sendRtpPackage(AppSettings.streamStopped, 0, liveIdBytes, info._2.channel, info._2.remoteAddress)
          }
          log.info(s"stream $liveId stopped")
          replyTo ! true
          Behaviors.stopped

        case StopPushing =>
          liveInfo.foreach{info =>
            val liveIdBytes = liveId.getBytes("UTF-8")
            sendRtpPackage(AppSettings.streamStopped, 0, liveIdBytes, info._2.channel, info._2.remoteAddress)
          }
          log.info(s"stream $liveId stopped")
          Behaviors.stopped

        case StreamData(data) =>
          //给收到 rtpPackage(ssrc)的订阅者转流
          liveInfoWithSSRC.foreach {info =>
            val rtp_buf: ByteBuffer = ByteBuffer.allocate(data.length)
            rtp_buf.put(data)
            rtp_buf.flip()
            val d = RtpUtil.parseData(data)
            info._2.channel.send(rtp_buf, info._2.remoteAddress)
//            println(s"订阅者：$liveInfoWithSSRC")
//            println(s"转流，${info._2.remoteAddress}")
          }
          Behaviors.same

        case GetSSRC(info) =>
          //给没收到 rtpPackage(ssrc)的订阅者重发 ssrc
          log.info(s"send GetSSRC again, liveId:$liveId, clientId: ${info._1}")
          streamManager ! StreamManager.GetSSRC(List(liveId), info._2.channel, info._2.remoteAddress)
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }

    }
  }

}
