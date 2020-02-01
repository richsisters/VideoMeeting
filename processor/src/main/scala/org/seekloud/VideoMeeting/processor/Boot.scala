
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
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import org.bytedeco.javacv.{FFmpegFrameGrabber, FFmpegFrameGrabber1, FFmpegFrameRecorder, Frame}
import org.seekloud.VideoMeeting.processor.core.RoomManager.UpdateRoomInfo
import org.seekloud.VideoMeeting.processor.core.{ChannelWorker, GrabberManager, RecorderManager, RoomManager, SendActor}
import org.seekloud.VideoMeeting.rtpClient.Protocol.Command

import scala.collection.mutable
import scala.language.postfixOps
import org.seekloud.VideoMeeting.processor.utils.CpuUtils
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

  val grabberManager:ActorRef[GrabberManager.Command] = system.spawn(GrabberManager.create(),"grabberManager")

  val recorderManager:ActorRef[RecorderManager.Command] = system.spawn(RecorderManager.create(),"recorderManager")

  val channelWorker:ActorRef[Command] = system.spawn(ChannelWorker.create(),"channelWorker")

  val sendActor:ActorRef[Command] = system.spawn(SendActor.create(), "sender")

	def main(args: Array[String]) {

//    Thread.sleep(5000)
//    roomManager ! UpdateRoomInfo(8888,List("liveIdTest-1111"),1,0)


    Http().bindAndHandle(routes, httpInterface, httpPort)
    log.info(s"Listen to the $httpInterface:$httpPort")
    log.info("Done.")

  }






}
