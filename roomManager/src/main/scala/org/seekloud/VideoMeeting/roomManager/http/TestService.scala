package org.seekloud.VideoMeeting.roomManager.http

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler}
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.ProcessorProtocol.{CloseRoom, GetMpd, SeekRecord, UpdateRoomInfo}
import org.seekloud.VideoMeeting.roomManager.utils.ProcessorClient

import scala.concurrent.Future

/**
  * created by byf on 2019.7.19 10:46
  * 测试processor临时接口
  * */
trait TestService extends ServiceUtils{
  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._

//  private val testUpdateRoomInfo = (path("testUpdateRoomInfo") & post){
//    entity(as[Either[Error,UpdateRoomInfo]]){
//      case Right(req) =>
//        dealFutureResult{
//          ProcessorClient.updateRoomInfo(req.roomId,req.layout).map{
//            case Right(v) =>
//              complete(v)
//            case Left(e) =>
//              complete(s"$e")
//          }
//        }
//
//      case Left(error) =>
//        println(s"$error")
//        complete("error")
//    }
//  }

  private val testSeekRecord = (path("testSeekRecord") & post){
    entity(as[Either[Error,SeekRecord]]){
      case Right(req) =>
        dealFutureResult{
          ProcessorClient.seekRecord(req.roomId,req.startTime).map{
            case Right(v) =>
              complete(v)
            case Left(e) =>
              complete(s"$e")
          }
        }
      case Left(_) =>
        complete("")
    }
  }

  private val testCloseRoom = (path("testCloseRoom") & post){
    entity(as[Either[Error,CloseRoom]]){
      case Right(req) =>
        dealFutureResult{
          ProcessorClient.closeRoom(req.roomId).map{
            case Right(v) =>complete(v)
            case Left(e) =>complete(s"$e")

          }
        }

      case Left(error) =>
        println(s"$error")
        complete("error")
    }
  }



  val test = pathPrefix("test"){
    testCloseRoom ~ testSeekRecord
  }





}
