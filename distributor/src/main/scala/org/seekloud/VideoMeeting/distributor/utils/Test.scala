package org.seekloud.VideoMeeting.distributor.utils

import org.seekloud.VideoMeeting.distributor.Boot.executor
import org.seekloud.VideoMeeting.distributor.utils.SecureUtil.genPostEnvelope
import org.slf4j.LoggerFactory

import scala.concurrent.Future
/**
  * created by byf on 2019.7.17 13:09
  * */
object ProcessorClient extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.parser.decode
  import io.circe.syntax._
  case class UpdateRoom(
                         roomId:Long,
                         liveIdList:List[String], //主播放在第一个
                         layout:Int,
                         aiMode:Int //为了之后拓展多种模式，目前0为不开ai，1为人脸目标检测
                       )

  case class NewLive(
    roomId:Long,
    liveId:String,
    startTime: Long
  )

  case class RoomInfo(roomId:Long, roles:List[String], layout:Int, aiMode:Int=0)

  case class CloseRoom(
                        roomId:Long
                      )

  case class GetMpd(
                     roomId:Long
                   )

  case class GetRtmpUrl(
                         roomId:Long
                       )

  case class GetMpd4Record(
                            roomId:Long,
                            startTime:Long
                          )

  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  case class MpdRsp(
                     mpd: String,
                     rtmp:String,
                     errCode: Int = 0,
                     msg: String = "ok"
                   ) extends CommonRsp

  case class RtmpRsp(
                      mpd: String,
                      errCode: Int = 0,
                      msg: String = "ok"
                    ) extends CommonRsp

  final case class ErrorRsp(
                             errCode: Int,
                             msg: String
                           ) extends CommonRsp

  final case class SuccessRsp(
                               errCode: Int = 0,
                               msg: String = "ok"
                             ) extends CommonRsp

  private val log = LoggerFactory.getLogger(this.getClass)

  val processorBaseUrl = "http://0.0.0.0:30389/VideoMeeting/distributor"


  def newLive(roomId:Long,liveId:String,startTime: Long):Future[Either[String,SuccessRsp]] = {
    val url = processorBaseUrl + "/newLive"
    val jsonString = NewLive(roomId,liveId,startTime).asJson.noSpaces
    val jsonData = genPostEnvelope("",System.nanoTime().toString,jsonString,"").asJson.noSpaces
    postJsonRequestSend("post", url, List(), jsonString, timeOut = 60 * 1000).map{
      case Right(v) =>
        decode[SuccessRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"updateRoomInfo decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"updateRoomInfo postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def getMpd(roomId:Long):Future[Either[String,MpdRsp]] = {
    val url = processorBaseUrl + "/getMpd"
    val jsonString = GetMpd(roomId).asJson.noSpaces
    //    val jsonData = genPostEnvelope("",System.nanoTime().toString,jsonString,"").asJson.noSpaces
    postJsonRequestSend("post",url,List(),jsonString,timeOut = 60 * 1000).map{
      case Right(v) =>
        decode[MpdRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"get mpd decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"get mpd postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def getRtmp(roomId:Long):Future[Either[String,RtmpRsp]] = {
    val url = processorBaseUrl + "/getRtmpUrl"
    val jsonString = GetRtmpUrl(roomId).asJson.noSpaces
    //    val jsonData = genPostEnvelope("",System.nanoTime().toString,jsonString,"").asJson.noSpaces
    postJsonRequestSend("post",url,List(),jsonString,timeOut = 60 * 1000).map{
      case Right(v) =>
        decode[RtmpRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"get rtmp decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"get rtmp postJsonRequestSend error : $error")
        Left("Error")
    }
  }


  def main(args: Array[String]): Unit = {
    newLive(1,"1",System.currentTimeMillis()).map{
      a=>
        println(a)
    }

//    Thread.sleep(5000)
//
//    updateRoomInfo(8888,List("1000","2000"),2,0).map{
//      a=>
//        println(a)
//    }
  }


}
