package org.seekloud.VideoMeeting.rtpServer

import java.io.{BufferedReader, File, FileReader}

import akka.actor.ActorSystem
import akka.actor.typed.DispatcherSelector
import akka.dispatch.MessageDispatcher
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.util.{Failure, Success}
import org.seekloud.VideoMeeting.rtpServer.http.HttpService

import scala.language.postfixOps
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.Future
import akka.actor.typed.ActorRef

import scala.language.postfixOps
import akka.actor.typed.scaladsl.adapter._
import org.seekloud.VideoMeeting.rtpServer.common.AppSettings
import org.seekloud.VideoMeeting.rtpServer.core._
import org.seekloud.VideoMeeting.rtpServer.core.ReceiveManager.Command
import org.seekloud.VideoMeeting.rtpServer.http.HttpService
//import org.seekloud.rtpServer.core.JobManager

/**
  * Author: Tao Zhang
  * Date: 4/29/2019
  * Time: 11:28 PM
  */
object Boot extends HttpService {

  import org.seekloud.VideoMeeting.rtpServer.common.AppSettings._
  import concurrent.duration._

  implicit val system: ActorSystem = ActorSystem("rtpServer", config)

  override implicit val materializer: Materializer = ActorMaterializer()

  override implicit val scheduler = system.scheduler

  override implicit val timeout: Timeout = Timeout(10 seconds)

  val log: LoggingAdapter = Logging(system, getClass)

  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")

  val blockingDispatcher: DispatcherSelector = DispatcherSelector.fromConfig("akka.actor.my-blocking-dispatcher")

  val receiveManager4Push: ActorRef[Command] =
    system.spawn(ReceiveManager.create(AppSettings.receiverHost, AppSettings.receiverPort), "receiveManager4Push")
  val receiveManager4Pull: ActorRef[ReceiveManager.Command] =
    system.spawn(ReceiveManager.create(AppSettings.senderHost, AppSettings.senderPort), "receiveManager4Pull")
  val streamManager: ActorRef[StreamManager.Command] = system.spawn(StreamManager.create(),"streamManager")
  val publishManager: ActorRef[PublishManager.Command] = system.spawn(PublishManager.create(),"publishManager")
  val authActor: ActorRef[AuthActor.Command] = system.spawn(AuthActor.create(),"authActor")
  val userManager: ActorRef[UserManager.Command] = system.spawn(UserManager.create(), "userManager")
  val queryInfoManager: ActorRef[QueryInfoManager.Command] = system.spawn(QueryInfoManager.create(), "queryInfoManager")
  val dataStoreActor: ActorRef[DataStoreActor.Command] = system.spawn(DataStoreActor.create(), "dataStoreActor")


  def main(args: Array[String]): Unit = {
    log.info("Starting.")
    val binding = Http().bindAndHandle(routes, httpInterface, httpPort)
    binding.onComplete {
      case Success(b) ⇒
        val localAddress = b.localAddress
        log.info(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
      case Failure(e) ⇒
        println(s"Binding failed with ${e.getMessage}")
        system.terminate()
        System.exit(-1)
    }
  //  TestRtpClient.main(Array(""))
  }
}


