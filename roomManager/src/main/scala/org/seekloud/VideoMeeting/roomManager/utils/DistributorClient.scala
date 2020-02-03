package org.seekloud.VideoMeeting.roomManager.utils

//import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.ProcessorProtocol
//import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.ProcessorProtocol.{RecordData, RecordInfoRsp, RecordList}
import org.seekloud.VideoMeeting.protocol.ptcl.distributor2Manager.DistributorProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.distributor2Manager.DistributorProtocol.{StartPullReq, StartPullRsp, FinishPullReq, CheckStreamReq, CheckStreamRsp, SeekRecord, RecordInfoRsp}
import org.seekloud.VideoMeeting.roomManager.common.AppSettings.{distributorDomain, distributorUseIp, distributorIp, distributorPort}
import org.seekloud.VideoMeeting.roomManager.http.ServiceUtils.CommonRsp
import org.seekloud.VideoMeeting.roomManager.utils.ProcessorClient.{log, postJsonRequestSend}
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler, system, timeout}
import scala.concurrent.Future

/**
  * created by benyafang on 2019/9/23 15:59
  * */
object DistributorClient {

  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser.decode

  private val log = LoggerFactory.getLogger(this.getClass)
//  val distributorBaseUrl = s"https://$distributorDomain/VideoMeeting/distributor"
//  val distributorBaseUrl = s"http://10.1.29.248:30389/VideoMeeting/distributor"

  val distributorBaseUrl = s"${if (!distributorUseIp) s"https://$distributorDomain" else s"http://$distributorIp:$distributorPort"}/VideoMeeting/distributor"

  def seekRecord(roomId:Long, startTime:Long):Future[Either[String,RecordInfoRsp]] = {
    val url = distributorBaseUrl + "/seekRecord"
    val jsonString = SeekRecord(roomId, startTime).asJson.noSpaces
    postJsonRequestSend("seekRecord",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[DistributorProtocol.RecordInfoRsp](v) match{
          case Right(data) =>
            log.debug(s"$data")
            if(data.errCode == 0){
              Right(data)
            }else{
              log.error(s"seekRecord decode error1 : ${data.msg}")
              Left(s"seekRecord decode error1 :${data.msg}")
            }
          case Left(e) =>
            log.error(s"seekRecord decode error : $e")
            Left(s"seekRecord decode error : $e")
        }
      case Left(error) =>
        log.error(s"seekRecord postJsonRequestSend error : $error")
        Left(s"seekRecord postJsonRequestSend error : $error")
    }
  }

  def deleteRecord(ls:List[DistributorProtocol.RecordData]):Future[Either[String,CommonRsp]] = {
    val url = distributorBaseUrl + "/removeRecords "
    val jsonString = DistributorProtocol.RecordList(ls).asJson.noSpaces
    postJsonRequestSend("removeRecords ",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[CommonRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"removeRecords  decode error : $e")
            Left(s"removeRecords  decode error : $e")
        }
      case Left(error) =>
        log.error(s"removeRecords  postJsonRequestSend error : $error")
        Left(s"removeRecords  postJsonRequestSend error : $error")
    }
  }

  def startPull(roomId: Long, liveId: String): Future[Either[String, StartPullRsp]] = {
    val url = distributorBaseUrl + "/startPull"
    val jsonString = DistributorProtocol.StartPullReq(roomId, liveId).asJson.noSpaces
    postJsonRequestSend("startPull", url, List(), jsonString, timeOut = 60 * 1000, needLogRsp = false).map{
      case Right(rsp) =>
        log.debug(s"startPull rsp: $rsp")
        decode[DistributorProtocol.StartPullRsp](rsp) match {
          case Right(data) =>
            if(data.errCode == 0)
              Right(data)
            else{
              log.error(s"startPullStream rsp error: ${data.msg}")
              Left(s"startPullStream rsp error: ${data.msg}")
            }
          case Left(e) =>
            log.error(s"startPullStream decode error : $e")
            Left(s"startPullStream decode error : $e")
        }
      case Left(error) =>
        log.error(s"startPullStream postJsonRequestSend error :$error")
        Left(s"startPullStream postJsonRequestSend error :$error")
    }
  }

  def finishPull(liveId: String): Future[Either[String, CommonRsp]] = {
    val url = distributorBaseUrl + "/finishPull"
    val jsonString = DistributorProtocol.FinishPullReq(liveId).asJson.noSpaces
    postJsonRequestSend("finishPull", url, List(), jsonString, timeOut = 60 * 1000, needLogRsp = false).map{
      case Right(rsp) =>
        decode[CommonRsp](rsp) match {
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"finishPull decode error :$e")
            Left(s"finishPull decode error :$e")
        }
      case Left(error) =>
        log.error(s"finishPull postJsonRequestSend error :$error")
        Left(s"finishPull postJsonRequestSend error :$error")
    }
  }

  def checkStream(liveId:String): Future[Either[String, CheckStreamRsp]] = {
    val url = distributorBaseUrl + "/checkStream"
    val jsonString = DistributorProtocol.CheckStreamReq(liveId).asJson.noSpaces
    postJsonRequestSend("checkStream", url, List(), jsonString, timeOut = 60 * 1000, needLogRsp = false).map {
      case Right(rsp) =>
        decode[DistributorProtocol.CheckStreamRsp](rsp) match {
          case Right(data) =>
            if(data.errCode == 0)
              Right(data)
            else{
              log.error(s"checkStream rsp error :${data.msg}")
              Left(s"checkStream rsp error :${data.msg}")
            }
          case Left(e) =>
            log.error(s"checkStream decode error :$e")
            Left(s"checkStream decode error :$e")
        }
      case Left(error) =>
        log.error(s"checkStream postJsonRequestSend error :$error")
        Left(s"checkStream postJsonRequestSend error :$error")
    }
  }


}
