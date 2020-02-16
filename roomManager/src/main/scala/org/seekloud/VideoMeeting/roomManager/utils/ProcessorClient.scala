package org.seekloud.VideoMeeting.roomManager.utils

import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler, system, timeout}
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor._
import org.seekloud.VideoMeeting.roomManager.common.AppSettings
import scala.concurrent.Future
/**
  * created by byf on 2019.7.17 13:09
  * */
object ProcessorClient extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser.decode

  private val log = LoggerFactory.getLogger(this.getClass)

  val processorBaseUrl = s"http://${AppSettings.processorIp}:${AppSettings.processorPort}/VideoMeeting/processor"
//  val distributorBaseUrl = s"https://$distributorDomain/VideoMeeting/distributor"

  def newConnect(roomId:Long, liveId4host: String, liveId4Client: List[String], liveId4push: String, liveCode4push: String, layout: Int):Future[Either[String,NewConnectRsp]] = {
    val url = processorBaseUrl + "/newConnect"
    log.debug(s"new connect url $url")
    val jsonString = NewConnect(roomId, liveId4host, liveId4Client, liveId4push, liveCode4push, layout).asJson.noSpaces
    postJsonRequestSend("newConnect",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[NewConnectRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"newConnect decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"newConnect postJsonRequestSend error : $error")
        Left("Error")
    }
  }

//  def updateRoomInfo(roomId:Long,layout:Int):Future[Either[String,UpdateRsp]] = {
//    val url = processorBaseUrl + "/updateRoomInfo"
//    val jsonString = ProcessorProtocol.UpdateRoomInfo(roomId, layout).asJson.noSpaces
//    postJsonRequestSend("updateRoomInfo",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
//      case Right(v) =>
//        decode[UpdateRsp](v) match{
//          case Right(data) =>
//            Right(data)
//          case Left(e) =>
//            log.error(s"updateRoomInfo decode error : $e")
//            Left(s"updateRoomInfo decode error : $e")
//        }
//      case Left(error) =>
//        log.error(s"updateRoomInfo postJsonRequestSend error : $error")
//        Left(s"updateRoomInfo postJsonRequestSend error : $error")
//    }
//  }

//  def getmpd(roomId:Long):Future[Either[String,MpdRsp]] = {
//    val url = processorBaseUrl + "/getMpd"
//    val jsonString = GetMpd(roomId).asJson.noSpaces
//    postJsonRequestSend("get mpd",url,List(),jsonString,timeOut = 60 * 1000, needLogRsp = false).map{
//      case Right(v) =>
//        decode[MpdRsp](v) match{
//          case Right(data) =>
//            Right(data)
//          case Left(e) =>
//            log.error(s"getmpd decode error : $e")
//            Left("Error")
//        }
//      case Left(error) =>
//        log.error(s"getmpd postJsonRequestSend error : $error")
//        Left("Error")
//    }
//  }

  def closeRoom(roomId:Long):Future[Either[String,CloseRoomRsp]] = {
    val url = processorBaseUrl + "/closeRoom"
    val jsonString = CloseRoom(roomId).asJson.noSpaces
    postJsonRequestSend("closeRoom",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[CloseRoomRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"closeRoom decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"closeRoom postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def forceExit(roomId: Long, liveId: String, startTime: Long): Future[Either[String, ExitRsp]] = {
    val url = processorBaseUrl + "/forceExit"
    val jsonString = ForceExit(roomId, liveId, startTime).asJson.noSpaces
    postJsonRequestSend("forceExit",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[ExitRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"forceExit decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"forceExit postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def banOnClient(roomId: Long, liveId: String, isImg: Boolean, isSound: Boolean) = {
    val url = processorBaseUrl + "/banOnClient"
    val jsonString = BanOnClient(roomId, liveId, isImg, isSound).asJson.noSpaces
    postJsonRequestSend("banOnClient",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[BanRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"banOnClient decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"banOnClient postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def cancelBan(roomId: Long, liveId: String, isImg: Boolean, isSound: Boolean) = {
    val url = processorBaseUrl + "/cancelBan"
    val jsonString = CancelBan(roomId, liveId, isImg, isSound).asJson.noSpaces
    postJsonRequestSend("cancelBan",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[CancelBanRsp](v) match{
          case Right(value) =>
            Right(value)
          case Left(e) =>
            log.error(s"cancelBan decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"cancelBan postJsonRequestSend error : $error")
        Left("Error")
    }
  }


}
