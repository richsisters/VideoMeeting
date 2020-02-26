
package org.seekloud.VideoMeeting.processor

import java.io._
import java.nio.{ByteBuffer, ShortBuffer}
import java.util.{HashMap, Map}

import akka.actor.{ActorSystem, Scheduler}
import akka.actor.typed.{ActorRef, DispatcherSelector}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.seekloud.VideoMeeting.processor.http.HttpService
import akka.actor.typed.scaladsl.adapter._
import akka.dispatch.MessageDispatcher
import org.seekloud.VideoMeeting.processor.core_new.{RoomManager, StreamPullActor}
import org.seekloud.VideoMeeting.rtpClient.Protocol.Command

import scala.collection.mutable
import scala.language.postfixOps
import org.seekloud.VideoMeeting.processor.utils.CpuUtils

import scala.util.{Failure, Success}
/**
  * User: yuwei
  * Date: 7/15/2019
  */
object Boot extends HttpService {

  import concurrent.duration._
  import org.seekloud.VideoMeeting.processor.common.AppSettings._

  override implicit val system: ActorSystem = ActorSystem("processor", config)
  // the executor should not be the default dispatcher.
  override implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  override implicit val scheduler: Scheduler = system.scheduler

  override implicit val timeout: Timeout = Timeout(20 seconds) // for actor asks

  val blockingDispatcher: DispatcherSelector = DispatcherSelector.fromConfig("akka.actor.my-blocking-dispatcher")

  val log: LoggingAdapter = Logging(system, getClass)

  val roomManager:ActorRef[RoomManager.Command] = system.spawn(RoomManager.create(),"roomManager")

  val streamPullActor:ActorRef[Command] = system.spawn(StreamPullActor.create(), "streamPullActor")

  //fixme 此处用以判断流是否存在
  var showStreamLog = false

	def main(args: Array[String]) {

    val httpsBinding = Http().bindAndHandle(routes, httpInterface, httpPort)

    httpsBinding.onComplete {
      case Success(b) ⇒
        val localAddress = b.localAddress
        println(s"Server is listening on https://${localAddress.getHostName}:${localAddress.getPort}")
      case Failure(e) ⇒
        println(s"httpsBinding failed with ${e.getMessage}")
        system.terminate()
        System.exit(-1)
    }

  }






}
