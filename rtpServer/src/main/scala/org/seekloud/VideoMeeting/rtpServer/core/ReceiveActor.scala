package org.seekloud.VideoMeeting.rtpServer.core

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.DatagramChannel

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.Boot.authActor
import org.seekloud.VideoMeeting.rtpServer.Boot.streamManager
import org.seekloud.VideoMeeting.rtpServer.Boot.publishManager
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.Address
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil.sendRtpPackage
import org.seekloud.VideoMeeting.rtpClient.Protocol
//import org.seekloud.VideoMeeting.shared.kcp.KCP

/**
  * Created by haoshuhan on 2019/7/16.
  */
object ReceiveActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class OriginalData(data: Array[Byte], channel: DatagramChannel, remoteAddress: SocketAddress) extends Command

  def create(id: Int): Behavior[Command] = Behaviors.setup[Command] { ctx =>
    implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
    Behaviors.withTimers[Command] { implicit timer =>

      work(id)
    }
  }



  def work(id: Int)
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case OriginalData(data, channel, remoteAddress) =>
          remoteAddress match {
            case address: InetSocketAddress =>
              if (data.length < 9) log.error("length error, drop it")
              else {
                val parseData: Protocol.Data = RtpUtil.parseData(data)
                val header: Protocol.Header = parseData.header
                val payloadType = header.payloadType

                payloadType match {
                    //鉴权
                  case AppSettings.authPusher =>
                    authActor ! AuthActor.AuthData(parseData.body, channel, address)

                  case AppSettings.tsStreamType =>
                    val ssrc = header.ssrc
                    val seq = header.seq
                    val ip = address.getAddress.getHostAddress
                    val host = address.getPort
                    val timeStamp = header.timestamp
                    if(seq % 1000 == 0) log.info(s"=====seq: $seq, ssrc: $ssrc, data len: ${parseData.body.length}")
                    streamManager ! StreamManager.StreamData(data, ssrc, Address(ip, host), timeStamp)

                  case AppSettings.calcDelayReq =>
                    val rspHeader = header.copy(payloadType = AppSettings.calcDelayRsp)
                    RtpUtil.sendData(channel, address, rspHeader, parseData.body)

                  case AppSettings.pullStreamRequest =>
                    val payload = parseData.body

//                    val payload = new String(parseData.body, "UTF-8")
//                    val liveIdList = payload.split("#").toList
//                    println(s"liveIdList:$liveIdList")
//                    streamManager ! StreamManager.GetSSRC(liveIdList, channel, address)
                    publishManager ! PublishManager.ParsePullStreamRequest(data, channel, address)

                  case AppSettings.stopPushingReq =>
                    log.info("stopPushingRequest" + "===" + payloadType)
                    val liveIds = new String(parseData.body, "UTF-8").split("#").toList
                    streamManager ! StreamManager.StopPushing(liveIds)
                    sendRtpPackage(AppSettings.stopPushingRsp, 0, Array.empty[Byte], channel, address)

                  case AppSettings.getClientIdRequest =>
                    publishManager ! PublishManager.GetClientId(channel, address)

                  case AppSettings.pullStreamUserHeartbeat =>
                    val clientId = parseData.header.ssrc
                    publishManager ! PublishManager.PullStreamUserHeartbeat(clientId, channel, address)

                  case AppSettings.stopPullingReq =>
                    val clientId = parseData.header.ssrc
                    publishManager ! PublishManager.StopPulling(clientId)
                    sendRtpPackage(AppSettings.stopPullingRsp, 0, Array.empty[Byte], channel, address)

                  //todo 该消息为subscriber收到 pullStreamRsp的回复消息
                  case AppSettings.subscriberRcvSSRC =>
                    publishManager ! PublishManager.SubscriberRcvSSRC(data, channel, address)

                  case e =>
                    log.info(s"payload type error: $e")
                    //drop
                }
              }
            case e =>
              println(s"recv unkown address:$e")

          }


          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same


      }

    }
  }

  def toHexFromByte(b: Byte): String = {
    val hexSymbols = Array("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")
    val leftSymbol = ((b >>> 4) & 0x0f).toByte
    val rightSymbol = (b & 0x0f).toByte
    hexSymbols(leftSymbol) + hexSymbols(rightSymbol)
  }

}
