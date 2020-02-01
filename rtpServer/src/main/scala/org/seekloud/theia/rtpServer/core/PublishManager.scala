package org.seekloud.VideoMeeting.rtpServer.core

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.channels.DatagramChannel

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.Boot.streamManager
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings
import org.seekloud.VideoMeeting.rtpServer.core.PublishActor.{Subscribe, Unsubscribe}
import org.seekloud.VideoMeeting.rtpServer.core.StreamManager.PacketLoss
import org.seekloud.VideoMeeting.rtpServer.protocol.StreamServiceProtocol.StreamInfo
import org.seekloud.VideoMeeting.rtpServer.protocol.ApiProtocol.AddressToStreams
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol._
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.{Address, LiveInfo}
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil.{sendRtpPackage, toInt}

import scala.concurrent.duration._
import scala.util.Random
/**
  * Created by haoshuhan on 2019/7/16.
  */
object PublishManager {

  sealed trait Command

  case class PullStreamRequest(clientId: Int,
                               liveIdList: List[String],
                               channel: DatagramChannel,
                               remoteAddress: InetSocketAddress) extends Command

  case class ParsePullStreamRequest(liveIdByte: Array[Byte],
                                    channel: DatagramChannel,
                                    remoteAddress: InetSocketAddress) extends Command

  //todo subscriber收到rtpPackage(ssrc)的回复消息
  case class SubscriberRcvSSRC(data: Array[Byte],
                               channel: DatagramChannel,
                               remoteAddress: InetSocketAddress) extends Command

  case class CheckIpBytes(clientId: Int,
                          channel: DatagramChannel,
                          remoteAddress: InetSocketAddress) extends Command

  case class StreamData(liveId: String, data: Array[Byte]) extends Command

  case class TimerKey(clientId: Int)

  case class ChildDead(liveId: String, childName: String, value: ActorRef[PublishActor.Command]) extends Command

  case class GetSubscribers(liveId: String, replyTo: ActorRef[List[Address]]) extends Command

  case class StopStream(liveId: String, replyTo: ActorRef[Boolean]) extends Command

  case class StopPushing(liveId: List[String]) extends Command

  case class GetAddressToStreams(replyTo: ActorRef[List[AddressToStreams]]) extends Command

  case class StopPulling(clientId: Int) extends Command

  case class GetClientId(channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command

  case class PullStreamUserHeartbeat(clientId: Int, channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command


  private val log = LoggerFactory.getLogger(this.getClass)

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        work()
      }
    }
  }

  def work(
    liveInfo: Map[Int, LiveInfo] = Map(),   //clientId -> LiveInfo(liveIdList, channel, remoteAddress)
    liveByteInfo: Map[Int, LiveInfoByte]=Map()
  )(
    implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case PullStreamRequest(clientId, liveIdList, channel, remoteAddress) =>
          log.info(s"recv new liveIdList: $liveIdList from clientId: $clientId")
          val ip = remoteAddress.getAddress.getHostAddress
          val port = remoteAddress.getPort
          liveInfo.get(clientId) match {
            case Some(info) =>
              val previousLiveIds = info.liveIdList
              log.info(s"previousLiveIds: $previousLiveIds")
              val subscribeLiveIds = liveIdList
              val unsubscribeLiveIds = previousLiveIds.filterNot(liveIdList.contains(_))
              log.info(s"unsubscribeLiveIds: $unsubscribeLiveIds")
              subscribeLiveIds.foreach(i => getPublishActor(ctx, i) ! Subscribe(clientId, channel, remoteAddress))
              unsubscribeLiveIds.foreach(i => getPublishActor(ctx, i) ! Unsubscribe(clientId))

            case None =>
              liveIdList.foreach(i => getPublishActor(ctx, i) ! Subscribe(clientId, channel, remoteAddress))
          }
          for(index <- 0 until Math.ceil(liveIdList.length.toDouble/20.toDouble).toInt){
            val end = Math.min((index+1)*20, liveIdList.length)
            val start = index*20
            streamManager ! StreamManager.GetSSRC(liveIdList.slice(start, end),
              channel, remoteAddress)
          }
          work(liveInfo + (clientId -> LiveInfo(liveIdList, channel, remoteAddress)), liveByteInfo)

        case GetAddressToStreams(replyTo) =>
          val response = liveInfo.map{info =>
            val ip = info._2.remoteAddress.getAddress.getHostAddress
            val port = info._2.remoteAddress.getPort
            AddressToStreams(info._1, Address(ip, port), info._2.liveIdList)
          }.toList
          replyTo ! response
          Behaviors.same

        case ParsePullStreamRequest(liveIdByte, channel, remoteAddress) =>
          val parseData = RtpUtil.parseData(liveIdByte)
          val seq = parseData.header.seq
          val m = parseData.header.m
          val clientId = parseData.header.ssrc
          val data = parseData.body
          val ip = remoteAddress.getAddress.getHostAddress
          val port = remoteAddress.getPort
          //println("ip=="+ip+"host=="+host+"seq=="+seq+"==m"+m+"==data"+new String(data, "UTF-8"))
          val address = Address(ip, port)
          log.info("ParsePullStreamRequest====>  ip:"+ip+"   port:"+port)
          val tempLiveByteInfo =
          liveByteInfo.get(clientId) match {
            case Some(info) =>
              val infoList = (info.liveIds :+ liveIds(data, seq, m)).sortBy(_.seq).reverse
              if(infoList.head.m == 1 && infoList.head.seq == infoList.length-1){
                val idsByte = infoList.reverse.flatMap(_.data).toArray
                log.info("PullStreamRequest:"+new String(idsByte, "UTF-8"))
                ctx.self ! PullStreamRequest(clientId, new String(idsByte, "UTF-8").split("#").toList,
                  channel, remoteAddress)
                timer.cancel(TimerKey(clientId))
                liveByteInfo - clientId
              }else{
                timer.startSingleTimer(TimerKey(clientId), CheckIpBytes(clientId, channel, remoteAddress), 1000.millis)
                liveByteInfo + (clientId ->
                                   LiveInfoByte(infoList, channel, remoteAddress))
              }
            case None =>
              if(seq == 0 && m == 1){
                log.info("seq == 0 && m == 1" + new String(data, "UTF-8"))
                ctx.self ! PullStreamRequest(clientId, new String(data, "UTF-8").split("#").toList,
                  channel, remoteAddress)
                liveByteInfo
              }else{
                timer.startSingleTimer(TimerKey(clientId), CheckIpBytes(clientId, channel, remoteAddress), 1000.millis)
                liveByteInfo + (clientId ->
                                LiveInfoByte(List[liveIds](liveIds(data, seq, m)),
                                  channel, remoteAddress))
              }
          }
          work(liveInfo, tempLiveByteInfo)

        //todo 该消息为subscriber收到 rtpPackage(ssrc)的回复消息
        case SubscriberRcvSSRC(data, channel, remoteAddress) =>
//          println(s"正在推的流：${ctx.children}")
          val parseData = RtpUtil.parseData(data)
          val clientId = parseData.header.ssrc
          val liveIds = new String(parseData.body, "UTF-8").split(";")
          liveIds.foreach {liveId =>
            getPublishActor(ctx, liveId) ! PublishActor.SubscriberRcvSSRC(clientId, channel, remoteAddress)
          }
          Behaviors.same

        case StopPushing(liveIds) =>
          liveIds.foreach{liveId =>
            val childName = s"publishActor_$liveId"
            val childOption = ctx.child(childName)
            if(childOption.isDefined){
              childOption.get.unsafeUpcast[PublishActor.Command] ! PublishActor.StopPushing
            }
          }
          val newLiveInfo = liveInfo.map{info =>
            val newLiveIds = info._2.liveIdList.filterNot(liveIds.contains(_))
            info._1 -> LiveInfo(newLiveIds, info._2.channel, info._2.remoteAddress)
          }
          work(newLiveInfo)

        case GetSubscribers(liveId, replyTo) =>
          val childName = s"publishActor_$liveId"
          val child = ctx.child(childName)
          if(child.isDefined){
            println("GetSubscribers is define")
            child.get.unsafeUpcast[PublishActor.Command] ! PublishActor.GetSubscribers(replyTo)
          }else{
            println("GetSubscribers is not define")
            replyTo ! List.empty[Address]
          }
          Behaviors.same

        case GetClientId(channel, remoteAddress) =>
          val ip = remoteAddress.getAddress.getHostAddress
          val port = remoteAddress.getPort
          val clientIdAndIp = liveInfo.map(info =>
            (info._1,info._2.remoteAddress.getAddress.getHostAddress,info._2.remoteAddress.getPort)
          )
          val find = clientIdAndIp.find(l => l._2 == ip && l._3 == port)
          if(find.isDefined){
            val preClientId = find.get._1
            log.info(s"gen client id $preClientId has been send for $remoteAddress")
            sendRtpPackage(AppSettings.getClientIdResponse, preClientId, Array.empty[Byte], channel, remoteAddress)
            work(liveInfo,liveByteInfo)
          }else{
            val ssrcArray = new Array[Byte](4)
            new Random().nextBytes(ssrcArray)
            val ssrcInt = toInt(ssrcArray)
            log.info(s"gen client id {$ssrcInt} for $remoteAddress")
            sendRtpPackage(AppSettings.getClientIdResponse, ssrcInt, Array.empty[Byte], channel, remoteAddress)
            work(liveInfo + (ssrcInt ->  LiveInfo(Nil, channel, remoteAddress)), liveByteInfo)
          }


        case StopPulling(clientId) =>
          log.info(s"clientId: $clientId stop pulling")
          liveInfo.get(clientId) match {
            case None =>
            case Some(info) =>
              info.liveIdList.foreach{l =>
                getPublishActor(ctx, l) ! PublishActor.Unsubscribe(clientId)
              }
          }
          work(liveInfo - clientId)

        case PullStreamUserHeartbeat(clientId, channel, remoteAddress) =>
          timer.startSingleTimer(s"heartBeat-$clientId", StopPulling(clientId), 10.seconds)
          val (liveIdList, ip, port) = liveInfo.get(clientId) match {
            case None => (Nil, None, None)
            case Some(info) => (info.liveIdList,
              Some(info.remoteAddress.getAddress.getHostAddress), Some(info.remoteAddress.getPort))
          }
          val newIp = remoteAddress.getAddress.getHostAddress
          val newPort = remoteAddress.getPort
          if (ip.isDefined && port.isDefined && ip.get == newIp && port.get == newPort) {
            // ip port 不变
          } else {
            liveIdList.foreach(l => getPublishActor(ctx, l) ! PublishActor.ChangeAddress(clientId, channel, remoteAddress))
          }
          work(liveInfo + (clientId -> LiveInfo(liveIdList, channel, remoteAddress)), liveByteInfo)

        case StopStream(liveId, replyTo) =>
          val childName = s"publishActor_$liveId"
          val child = ctx.child(childName)
          if(child.isDefined){
            child.get.unsafeUpcast[PublishActor.Command] ! PublishActor.StopStream(replyTo)
            val newLiveInfo = liveInfo.map{info =>
              val newLiveIds = info._2.liveIdList.filter(_ != liveId)
              info._1 -> LiveInfo(newLiveIds, info._2.channel, info._2.remoteAddress)
            }
            work(newLiveInfo)
          }else{
            replyTo ! false
            Behaviors.same
          }

        case CheckIpBytes(clientId, channel, remoteAddress) =>
          liveByteInfo.get(clientId) match {
            case Some(info) =>
              val tempLiveByteInfo = liveByteInfo - clientId
              streamManager ! PacketLoss(channel, remoteAddress)
              work(liveInfo, tempLiveByteInfo)
            case None =>
              Behaviors.same
          }

        case StreamData(liveId, data) =>
          getPublishActor(ctx, liveId) ! PublishActor.StreamData(data)
          Behaviors.same

        case ChildDead(id, childName, actor) =>
          log.error(s"$childName dead!")
          Behaviors.same


        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same





      }

    }
  }

  private def getPublishActor(ctx: ActorContext[Command], liveId: String) = {
    val childName = s"publishActor_$liveId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(PublishActor.create(liveId), childName)
      ctx.watchWith(actor, ChildDead(liveId, childName, actor))
      actor
    }.unsafeUpcast[PublishActor.Command]
  }


}
