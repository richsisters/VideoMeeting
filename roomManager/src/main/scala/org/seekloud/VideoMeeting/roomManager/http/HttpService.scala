package org.seekloud.VideoMeeting.roomManager.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * User: Taoz
  * Date: 8/26/2016
  * Time: 10:27 PM
  */
trait HttpService extends ServiceUtils
  with UserService
  with RtpService
  with TestService
  with FileService
  with RtmpService
  with RecordService
  with AdminService
  with RecordCommentService
  with StatisticService
  with ResourceService{

  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  implicit val scheduler: Scheduler


  private val home:Route = pathPrefix("webClient"){
    pathEndOrSingleSlash{
      getFromResource("html/webClient.html")
    } ~ mobile ~ statistics
  }

//  private val pc =(pathPrefix("pc") & get){
//    pathEndOrSingleSlash{
//      getFromResource("html/webClient.html")
//    }
//  }

  private val statistics =(pathPrefix("statistics") & get){
    pathEndOrSingleSlash{
      getFromResource("html/statistics.html")
    }
  }

  private val mobile =(pathPrefix("mobile") & get){
    pathEndOrSingleSlash{
      getFromResource("html/phoneClient.html")
    }
  }

  val Routes: Route =
    ignoreTrailingSlash {
      pathPrefix("VideoMeeting") {
        home ~ statistics ~
        pathPrefix("roomManager"){
          resourceRoutes ~ userRoutes ~ rtpRoutes ~ recordRoutes ~ test ~ file ~ rtmp ~ admin ~ recordComment ~ statistic
        }
      }
    }


}
