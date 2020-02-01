package org.seekloud.VideoMeeting.rtpServer.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor

/**
  * User: Taoz
  * Date: 8/26/2016
  * Time: 10:27 PM
  */
trait HttpService extends  ResourceService
  with ServiceUtils with ApiService
{
//  import akka.actor.typed.scaladsl.AskPattern._
//  import org.seekloud.VideoMeeting.rtpServer.utils.CirceSupport._
//  import io.circe.generic.auto._

  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  implicit val scheduler: Scheduler


  lazy val routes: Route =
    ignoreTrailingSlash {
      pathPrefix("VideoMeeting") {
        pathPrefix("rtpServer") {
          pathEndOrSingleSlash {
            getFromResource("html/index.html")
          } ~ resourceRoutes ~ apiRoutes
        }
      }

    }


}
