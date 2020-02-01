package org.seekloud.VideoMeeting.rtpServer.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.core.StreamManager.{Command, work}
import org.seekloud.VideoMeeting.rtpServer.protocol.ApiProtocol.{AddressToStreams, AllInfo, StreamInfoDetail}
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.rtpServer.Boot._

/**
  * Author: wqf
  * Date: 2019/8/26
  * Time: 23:42
  */
object QueryInfoManager {

  case class Response(replyTo: ActorRef[AllInfo], response: AllInfo)

  sealed trait Command

  case class GetAllInfo(replyTo: ActorRef[AllInfo]) extends Command

  case class GetAddressToStreamsRsp(requestId: Int, addressToStream: List[AddressToStreams])  extends Command

  case class GetStreamInfoDetailRsp(request: Int, streamDetail: List[StreamInfoDetail]) extends Command

  private val log = LoggerFactory.getLogger(this.getClass)

  private val seq = new AtomicInteger(0)

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        work()
      }
    }
  }

  def work(requestInfo: Map[Int, Response] = Map())
    (implicit timer: TimerScheduler[Command],
      stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command]{(ctx, msg) =>
      msg match {
        case GetAllInfo(replyTo) =>
          val requestId = seq.getAndIncrement()
//          publishManager ! PublishManager.GetAddressToStreams(requestId, ctx.self)
//          streamManager ! StreamManager.GetStreamInfo(requestId, ctx.self)
          work(requestInfo + (requestId -> Response(replyTo, AllInfo(None, None))))

        case GetAddressToStreamsRsp(requestId, addressToStream) =>
          val res = requestInfo.get(requestId)
          if(res.isDefined){
            val streamInfo = res.get.response.streamInfo
            if(streamInfo.isDefined){
              res.get.replyTo ! AllInfo(Some(addressToStream), streamInfo)
              work(requestInfo - requestId)
            }else{
              val r = res.get
              val newData = Response(r.replyTo, AllInfo(Some(addressToStream), None))
              work(requestInfo + (requestId -> newData))
            }
          }else {
            Behaviors.same
          }

        case GetStreamInfoDetailRsp(requestId, streamDetail) =>
          val res = requestInfo.get(requestId)
          if(res.isDefined){
            val ats = res.get.response.pullStreamInfo
            if(ats.isDefined){
              res.get.replyTo ! AllInfo(ats, Some(streamDetail))
              work(requestInfo - requestId)
            }else{
              val r = res.get
              val newData = Response(r.replyTo, AllInfo(None, Some(streamDetail)))
              work(requestInfo + (requestId -> newData))
            }
          }else {
            Behaviors.same
          }
      }
    }
  }
}
