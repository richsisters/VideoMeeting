package org.seekloud.VideoMeeting.processor.http

import java.io.{File, FileInputStream, FileOutputStream}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol._
import org.seekloud.VideoMeeting.processor.protocol.CommonErrorCode.{fileNotExistError, parseJsonError, updateRoomError}
import org.seekloud.VideoMeeting.processor.utils.ServiceUtils
import org.seekloud.VideoMeeting.processor.Boot.{executor, recorderManager, roomManager, scheduler, timeout}
import io.circe.Error
import io.circe.generic.auto._
import org.seekloud.VideoMeeting.processor.core.RoomManager
import org.seekloud.VideoMeeting.processor.core.{RecorderManager, RoomManager}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.headers.HttpOriginRange
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import org.seekloud.VideoMeeting.processor.models.MpdInfoDao
import org.slf4j.LoggerFactory

import scala.concurrent.Future

trait ProcessorService extends ServiceUtils {

  private val log = LoggerFactory.getLogger(this.getClass)

  private def updateRoomInfo = (path("updateRoomInfo") & post) {
    entity(as[Either[Error, UpdateRoom]]) {
      case Right(req) =>
        log.info(s"post method updateRoomInfo.")
        if(req.liveIdList.nonEmpty) {
          roomManager ! RoomManager.UpdateRoomInfo(req.roomId, req.liveIdList, req.startTime, req.layout, req.aiMode)
          complete(SuccessRsp())
        }else{
          roomManager ! RoomManager.CloseRoom(req.roomId)
          complete(SuccessRsp(0,"close room successfully."))
        }

      case Left(e) =>
        complete(parseJsonError)
    }
  }

  private def closeRoom = (path("closeRoom") & post) {
    entity(as[Either[Error, CloseRoom]]) {
      case Right(req) =>
        log.info(s"post method closeRoom ${req.roomId}.")
        roomManager ! RoomManager.CloseRoom(req.roomId)
        complete(SuccessRsp())

      case Left(e) =>
        complete(parseJsonError)
    }
  }

  private def getMpd = (path("getMpd") & post) {
    entity(as[Either[Error, GetMpd]]) {
      case Right(req) =>
        log.info(s"post method getMpd.")
        val msg:Future[MpdRsp] = recorderManager ? (RecorderManager.GetMpdAndRtmp(req.roomId, _))
        dealFutureResult(
          msg.map{ rsp =>
            println()
            println(rsp)
            complete(rsp)
          }
        )

      case Left(e) =>
        complete(parseJsonError)
    }
  }
  private val settings = CorsSettings.defaultSettings.withAllowedOrigins(
    HttpOriginMatcher.*
  )

  val getDash = (path("getDash"/Segments(2))& get & pathEndOrSingleSlash & cors(settings)){
    case dir :: file :: Nil =>
      val f = new File(s"/tmp/dash/$dir/$file").getAbsoluteFile
      getFromFile(f,ContentTypes.`application/octet-stream`)

    case x =>
      log.error(s"errs in getm: $x")
      complete(fileNotExistError)
  }

  private def getRtmpUrl = (path("getRtmpUrl") & post) {
    entity(as[Either[Error, GetRtmpUrl]]) {
      case Right(req) =>
        log.info(s"post method getRtmpUrl.")
        val msg:Future[RtmpRsp] = recorderManager ? (RecorderManager.GetRtmpUrl(req.roomId, _))
        dealFutureResult(
          msg.map{ rsp =>
            complete(rsp)
          }
        )

      case Left(e) =>
        complete(parseJsonError)
    }
  }

  private val getMpd4Record = (path("getMpd4Record") & post) {
    entity(as[Either[Error, GetMpd4Record]]) {
      case Right(req) =>
        dealFutureResult(
          MpdInfoDao.getMpd4Record(req.roomId, req.startTime).map { rs =>
            complete(RtmpRsp(rs))
          }
        )
      case Left(e) =>
        complete(parseJsonError)
    }
  }

  private val getRecordList = (path("getRecordList") & post) {
    entity(as[Either[Error, GetRecordList]]) {
      case Right(req) =>
        dealFutureResult(
          MpdInfoDao.getRecordList(req.roomId).map { list =>
            val data = list.map( r => Record(r.roomId,r.startTime,r.endTime,r.mpdAddr)).toList
            complete(GetRecordListRsp(Some(data)))
          }
        )
      case Left(e) =>
        complete(parseJsonError)
    }
  }

  def tempDestination(fileInfo: FileInfo): File =
    File.createTempFile(fileInfo.fileName, ".tmp")

  def createNewFile(file:File, name:String): Boolean = {
    val fis =new FileInputStream(file)
    val picFile = new File("D:\\image\\"+ name)
    picFile.createNewFile()
    val fos = new FileOutputStream(picFile)
    var byteRead = 0
    val bytes = new Array[Byte](1024)
    byteRead = fis.read(bytes, 0, bytes.length)
    while(byteRead != -1){
      fos.write(bytes, 0, byteRead)
      byteRead = fis.read(bytes, 0, bytes.length)
    }
    fos.flush()
    fos.close()
    fis.close()
    file.delete()
  }

  private val upLoadImg = (path("upLoadImg") & post) {
    storeUploadedFile("imgFile", tempDestination) {
      case (metadata, file) =>
        createNewFile(file, metadata.fileName)
        complete(UploadSuccessRsp(metadata.fileName))
    }
  }

  val processorRoute:Route = pathPrefix("processor") {
    updateRoomInfo ~ closeRoom ~ getMpd ~ getRtmpUrl ~ getDash ~ getMpd4Record ~ getRecordList ~ upLoadImg
  }
}
