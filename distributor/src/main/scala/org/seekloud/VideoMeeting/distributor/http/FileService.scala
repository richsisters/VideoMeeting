package org.seekloud.VideoMeeting.distributor.http

import java.io.File

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.server.Directives.{Segments, as, complete, entity, getFromFile, path, pathPrefix}
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.circe.Error
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import org.seekloud.VideoMeeting.distributor.utils._
import org.seekloud.VideoMeeting.distributor.protocol.CommonErrorCode._
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.Future
import org.seekloud.VideoMeeting.distributor.Boot.{executor, saveManager, scheduler, timeout}
import org.seekloud.VideoMeeting.distributor.common.AppSettings.{fileLocation, recordLocation}
import org.seekloud.VideoMeeting.distributor.core.SaveManager.{RecordInfo, RemoveRecords}
import org.seekloud.VideoMeeting.distributor.core.SaveManager
import org.seekloud.VideoMeeting.distributor.protocol.SharedProtocol._
trait FileService extends ServiceUtils {

  private val log = LoggerFactory.getLogger(this.getClass)


  private val settings = CorsSettings.defaultSettings.withAllowedOrigins(
    HttpOriginMatcher.*
  )

  val getFile: Route = (path("getFile" / Segments(2)) & get & pathEndOrSingleSlash & cors(settings)){
    case dir :: file :: Nil =>
      println(s"getFile req for $dir/$file.")
      val f = new File(s"$fileLocation$dir/$file").getAbsoluteFile
      getFromFile(f,ContentTypes.`application/octet-stream`)

    case x =>
      log.error(s"errs in getm: $x")
      complete(fileNotExistError)
  }

  val getRecord: Route = (path("getRecord" / Segments(3)) & get & pathEndOrSingleSlash & cors(settings)){
    case roomId :: startTime :: file :: Nil =>
      println(s"getRecord req for $roomId/$startTime/$file.")
      val f = new File(s"$recordLocation$roomId/$startTime/$file").getAbsoluteFile
      getFromFile(f,ContentTypes.`application/octet-stream`)

    case x =>
      log.error(s"errs in getRecord: $x")
      complete(fileNotExistError)
  }

  val seekRecord: Route = (path("seekRecord") & post) {
    entity(as[Either[Error, SeekRecord]]) {
      case Right(req) =>
        log.info("seekRecord.")
        val msg:Future[RecordInfo] = saveManager ? (SaveManager.SeekRecord(req.roomId,req.startTime,_))
        dealFutureResult(
          msg.map{rst =>
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
        saveManager ! RemoveRecords(req.records)
        complete(SuccessRsp())

      case Left(e) =>
        log.error(s"err:$e in removeRecords.")
        complete(parseJsonError)
    }
  }

//  val newLive: Route = (path("newLive") & post) {
//    entity(as[Either[Error, NewLive]]) {
//      case Right(req) =>
//        log.info(s"post method newLiveInfo.")
//        complete(SuccessRsp(0,"got liveId."))
//
//      case Left(e) =>
//        complete(parseJsonError)
//    }
//  }





  val fileRoute:Route = pathPrefix("distributor") {
    getFile ~ getRecord ~ seekRecord ~ removeRecords
  }
}
