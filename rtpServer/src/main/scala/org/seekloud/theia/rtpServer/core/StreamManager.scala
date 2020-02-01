package org.seekloud.VideoMeeting.rtpServer.core

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.Boot.publishManager
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings
import org.seekloud.VideoMeeting.rtpServer.protocol.StreamServiceProtocol.StreamInfo
import org.seekloud.VideoMeeting.rtpServer.protocol.ApiProtocol.StreamInfoDetail
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.Address
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil._
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.VideoMeeting.rtpServer.Boot.{timeout, executor, scheduler}

import scala.concurrent.Future

/**
  * Created by haoshuhan on 2019/7/16.
  */
object StreamManager {

  sealed trait Command

  case class PushInfo(ssrc: Int, address: Address)

  case class StreamData(data: Array[Byte], ssrc: Int, address: Address, timeStamp: Long) extends Command

  case class AuthSuccess(liveId: String, ssrc: Int, address: Address) extends Command

  case class GetSSRC(liveIdList: List[String], channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command

  case class ChildDead(liveId: String, childName: String, value: ActorRef[StreamActor.Command]) extends Command

  case class PacketLoss(channel: DatagramChannel, remoteAddress: InetSocketAddress) extends Command

  case class GetAllStream(replyTo: ActorRef[List[StreamInfo]]) extends Command

  case class StopStream(liveId: String, replyTo: ActorRef[Boolean]) extends Command

  case class StopPushing(liveIds: List[String]) extends Command

  case class GetStreamInfo(replyTo: ActorRef[List[StreamInfoDetail]]) extends Command

  private val log = LoggerFactory.getLogger(this.getClass)


  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timrer =>
        work()
      }
    }
  }

  def work(liveInfo: Map[String, PushInfo] = Map())
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case StreamData(data, ssrc, address, timeStamp) =>
          val liveIdOption = liveInfo.find(_._2.ssrc == ssrc)
          if (liveIdOption.isDefined) {
            val liveId = liveIdOption.get._1
            getStreamActor(ctx, liveId) ! StreamActor.StreamData(data, ssrc, timeStamp)
//            timer.startSingleTimer(s"loopKey-$liveId", StopPushing(liveId :: Nil), 30.seconds)
            Behaviors.same
          } else {
           // log.info(s"未鉴权，ssrc: $ssrc")
//            log.info(s"未鉴权， drop it，ssrc: $ssrc")
//             测试
            val liveId = s"liveIdTest-$ssrc"
            log.info(s"未鉴权，分配测试liveId: $liveId")
           // println(s"liveId:$liveId====")
            getStreamActor(ctx, liveId) ! StreamActor.AuthSuccess(ssrc)
            getStreamActor(ctx, liveId) ! StreamActor.StreamData(data, ssrc, timeStamp)
//            timer.startSingleTimer(s"loopKey-$liveId", StopPushing(liveId :: Nil), 30.seconds)
            work(liveInfo ++ Map(liveId -> PushInfo(ssrc, address))) //测试
          }
//          Behaviors.same

//
        case GetAllStream(replyTo) =>
          replyTo ! liveInfo.map(info =>
            StreamInfo(info._1, info._2.ssrc)
          ).toList
          Behaviors.same


        case GetStreamInfo(replyTo) =>
          val rstF = Future.sequence {
            liveInfo.map { info =>
              getStreamActor(ctx, info._1) ? StreamActor.GetBandwidth
            }
          }
            rstF.map {r =>
              val response = r.map {i =>
                val liveId = i._1
                val bandwidth = i._2
                val info = liveInfo(liveId)
                StreamInfoDetail(liveId, info.ssrc, info.address.host, info.address.port, bandwidth)
              }.toList
              replyTo ! response
          }
          Behaviors.same

        case GetSSRC(liveIdList, channel, remoteAddress) =>
          val ssrcMap = liveIdList.map {id =>
            val ssrc = liveInfo.get(id)
            (id, ssrc)
          }
          val realSubcribeId = ssrcMap.filter(_._2.isDefined).map(_._1)
          val rsp = liveIdList.map {id =>
            val info = liveInfo.get(id)
            //liveInfo.getOrElse(id, "null")
            val ssrc = if(info.isDefined) info.get.ssrc else "null"
            s"$id#$ssrc"
          }.reduce(_ + ";" + _).getBytes("UTF-8")
          log.info("GetSSRC:"+new String(rsp, "UTF-8")+ "  " +remoteAddress.getHostName+"  "+remoteAddress.getPort)
          sendRtpPackage(AppSettings.pullStreamResponse, 0, rsp, channel, remoteAddress)
         // publishManager ! PublishManager.PullStreamRequest(realSubcribeId, channel, remoteAddress)
          Behaviors.same

        case PacketLoss(channel, remoteAddress) =>
          sendRtpPackage(AppSettings.pullStreamRefuseResponse, 0, "packetloss".getBytes("UTF-8"), channel, remoteAddress)
          Behaviors.same

        case StopStream(liveId, replyTo) =>
          val child = ctx.child(s"streamer_$liveId")
          if(child.isDefined){
            log.info(s"roomManager stop stream of liveId:$liveId")
            ctx.stop(child.get)
          }
          publishManager ! PublishManager.StopStream(liveId, replyTo)
          if(liveInfo.get(liveId).isDefined){
            work(liveInfo - liveId)
          }else {
            Behaviors.same
          }

        case StopPushing(liveIds) =>
          var templiveInfo = liveInfo
          liveIds.foreach{liveId =>
            val child = ctx.child(s"streamer_$liveId")
            if(child.isDefined){
              log.info(s"userManager stop stream of liveId:$liveId")
              ctx.stop(child.get)
            }
            if(liveInfo.get(liveId).isDefined){
              templiveInfo = templiveInfo - liveId
            }
          }
          publishManager ! PublishManager.StopPushing(liveIds)
          work(templiveInfo)

        case AuthSuccess(liveId, ssrc, address) =>
          log.info(s"$liveId auth success")
          getStreamActor(ctx, liveId) ! StreamActor.AuthSuccess(ssrc)
          work(liveInfo ++ Map(liveId -> PushInfo(ssrc, address)))

        case ChildDead(id, childName, actor) =>
          log.error(s"$childName dead!")
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same

      }

    }
  }

  private def getStreamActor(ctx: ActorContext[Command], liveId: String) = {
    val childName = s"streamer_$liveId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(StreamActor.create(liveId), childName)
      ctx.watchWith(actor, ChildDead(liveId, childName, actor))
      actor
    }.unsafeUpcast[StreamActor.Command]
  }


}
