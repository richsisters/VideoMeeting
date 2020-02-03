package org.seekloud.VideoMeeting.roomManager.utils

import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.GetLiveInfoRsp
import SecureUtil.genPostEnvelope
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler, system, timeout}
import org.seekloud.VideoMeeting.roomManager.common.AppSettings

import scala.concurrent.Future
/**
  * created by byf on 2019.8.28
  * */
object RtpClient extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser.decode

  private val log = LoggerFactory.getLogger(this.getClass)
  val processorBaseUrl = s"http://${AppSettings.rtpIp}:${AppSettings.rtpPort}/VideoMeeting/rtpServer/api"

  def getLiveInfoFunc():Future[Either[String,GetLiveInfoRsp]] = {
    log.debug("get live info")
    val url = processorBaseUrl + "/getLiveInfo"
    val req = genPostEnvelope("org/seekloud/VideoMeeting/roomManager",System.nanoTime().toString,"{}","484ec7db9e39bc4b5e3d").asJson.noSpaces
    postJsonRequestSend("getLiveInfo",url,List(),req,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[GetLiveInfoRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"getLiveInfo decode error : $e")
            Left(s"getLiveInfo decode error : $e")
        }
      case Left(error) =>
        log.error(s"getLiveInfo postJsonRequestSend error : $error")
        Left(s"getLiveInfo postJsonRequestSend error : $error")
    }
  }

  def testLiveInfoFunc():Future[Either[String,GetLiveInfoRsp]] = {
    val url = "http://localhost:30382/VideoMeeting/roomManager/rtp" + "/getLiveInfo"
    val req = genPostEnvelope("processor",System.nanoTime().toString,"{}","0379a0aaff63c1ce").asJson.noSpaces
    postJsonRequestSend("getLiveInfo",url,List(),req,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[GetLiveInfoRsp](v) match{
          case Right(data) =>
            log.debug("success")
            Right(data)
          case Left(e) =>
            log.error(s"getLiveInfo decode error : $e")
            Left(s"getLiveInfo decode error : $e")
        }
      case Left(error) =>
        log.error(s"getLiveInfo postJsonRequestSend error : $error")
        Left(s"getLiveInfo postJsonRequestSend error : $error")
    }
  }

}
