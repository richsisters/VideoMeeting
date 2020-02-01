package org.seekloud.VideoMeeting.roomManager.utils

import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.ProcessorProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.ProcessorProtocol.{RecordData, RecordInfoRsp, RecordList}
import org.seekloud.VideoMeeting.roomManager.common.AppSettings.distributorDomain
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
  val distributorBaseUrl = s"https://$distributorDomain/VideoMeeting/distributor"

  def seekRecord(roomId:Long, startTime:Long):Future[Either[String,RecordInfoRsp]] = {
    val url = distributorBaseUrl + "/seekRecord"
    val jsonString = ProcessorProtocol.SeekRecord(roomId, startTime).asJson.noSpaces
    postJsonRequestSend("seekRecord",url,List(),jsonString,timeOut = 60 * 1000,needLogRsp = false).map{
      case Right(v) =>
        decode[ProcessorProtocol.RecordInfoRsp](v) match{
          case Right(data) =>
            if(data.errCode == 0){
              Right(data)
            }else{
              log.error(s"seekRecord decode error : ${data.msg}")
              Left(s"seekRecord decode error :${data.msg}")
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

  def deleteRecord(ls:List[RecordData]):Future[Either[String,CommonRsp]] = {
    val url = distributorBaseUrl + "/removeRecords "
    val jsonString = ProcessorProtocol.RecordList(ls).asJson.noSpaces
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


}
