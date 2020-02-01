package org.seekloud.VideoMeeting.rtpServer.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.headers.{CacheDirective, `Cache-Control`}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.mapResponseHeaders
import akka.stream.Materializer
import akka.util.Timeout
//import org.seekloud.pencil.http.ServiceUtils
import org.seekloud.VideoMeeting.rtpServer.utils.CirceSupport

import scala.concurrent.ExecutionContextExecutor

/**
  * Created by dry on 2018/4/26.
  **/
trait BaseService extends CirceSupport with ServiceUtils{

  def addCacheControlHeaders(first: CacheDirective, more: CacheDirective*): Directive0 = {
    mapResponseHeaders { headers =>
      `Cache-Control`(first, more: _*) +: headers
    }
  }

  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  implicit val scheduler: Scheduler

}
