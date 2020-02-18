package org.seekloud.VideoMeeting.processor.http

import akka.http.scaladsl.server.Directives.{as, complete, entity, path}
import akka.http.scaladsl.server.Route
import io.circe.Error
import org.seekloud.VideoMeeting.processor.utils.ServiceUtils
import akka.http.scaladsl.server.Directives._
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol.SuccessRsp
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor.{RecordInfo, RecordInfoRsp, RecordList, SeekRecord}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import org.seekloud.VideoMeeting.processor.Boot.{executor, roomManager, scheduler, timeout}
import org.seekloud.VideoMeeting.processor.core_new.RoomManager
import akka.actor.typed.scaladsl.AskPattern._
import io.circe.generic.auto._
import org.seekloud.VideoMeeting.processor.utils.ServiceUtils.CommonRsp

trait RecordService extends ServiceUtils {

  private val log = LoggerFactory.getLogger(this.getClass)

  val seekRecord: Route = (path("seekRecord") & post) {
    entity(as[Either[Error, SeekRecord]]) {
      case Right(req) =>
        log.info("seekRecord.")
        val msgFuture: Future[RecordInfo] = roomManager ? (RoomManager.SeekRecord(req.roomId, req.startTime, _))
        dealFutureResult(
          msgFuture.map{rst =>
            if(rst.fileExist){
              complete(RecordInfoRsp(duration = rst.duration))
            }else{
              complete(RecordInfoRsp(1000100,"record file not exist.",""))
            }
          }
        )

      case Left(e) =>
        log.info(s"err in seekRecord. error: ${e.getMessage}")
        complete(RecordInfoRsp(1000103,"parse json error",""))
    }
  }

  val removeRecords: Route = (path("removeRecords") & post) {
    entity(as[Either[Error, RecordList]]) {
      case Right(req) =>
        roomManager ! RoomManager.RemoveRecords(req.records)
        complete(SuccessRsp())

      case Left(e) =>
        log.error(s"err:$e in removeRecords.")
        complete(ServiceUtils.JsonParseError)
    }
  }

  val recordRoutes:Route = seekRecord ~ removeRecords

}
