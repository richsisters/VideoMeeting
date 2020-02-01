package org.seekloud.VideoMeeting.roomManager.http

import org.seekloud.VideoMeeting.roomManager.Boot._

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.{GetLiveInfoRsp, GetLiveInfoRsp4RM}
import org.seekloud.VideoMeeting.roomManager.http.SessionBase._
import org.seekloud.VideoMeeting.protocol.ptcl.server2Manager.CommonProtocol.{Verify, VerifyError, VerifyRsp}
import org.seekloud.VideoMeeting.roomManager.core.{RoomManager, UserManager}
import org.seekloud.VideoMeeting.roomManager.utils.RtpClient

import scala.concurrent.Future

trait RtpService extends ServiceUtils{
  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._


  case class GetLiveInfoReq()
  private val getLiveInfo = (path("getLiveInfo") & post){
    dealPostReq[GetLiveInfoReq]{req =>
      RtpClient.getLiveInfoFunc().map{
        case Right(rsp) =>
          log.debug(s"获取liveInfo  ..${rsp}")
          complete(GetLiveInfoRsp4RM(Some(rsp.liveInfo)))
        case Left(error) =>
          complete(CommonRsp(1000023, s"获取live info失败：${error}"))

      }.recover{
        case e:Exception =>
          complete(CommonRsp(1000024, s"获取live info失败：${e}"))
      }
    }
  }

  val rtpRoutes: Route = pathPrefix("rtp") {
    getLiveInfo
  }
}

