package org.seekloud.VideoMeeting.distributor.core
import java.net.{InetSocketAddress, ServerSocket, Socket, SocketException}
import java.nio._
import java.nio.channels.{DatagramChannel, Pipe, SocketChannel}
import java.io._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.server.util.TupleOps.Join
import org.slf4j.LoggerFactory

import scala.language.implicitConversions
import scala.collection.mutable
import org.seekloud.VideoMeeting.distributor.common.AppSettings.{fileLocation, indexPath, isTest}
import org.bytedeco.javacpp.Loader
import org.seekloud.VideoMeeting.shared.rtp.Protocol._
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.distributor.Boot.{distributor, encodeManager}
object RevActor {
  private val log = LoggerFactory.getLogger(this.getClass)
  object PayloadType{
    val newLive = 1
    val packet = 2
    val closeLive = 3
    val heartbeat = 4
  }

  trait Command

  case object Stop extends Command
  def removeTestFile():Unit = {
    val f = new File(s"${fileLocation}test/")
    if(f.exists()) {
      f.listFiles().foreach{
        e =>
          e.delete()
      }
    }
  }

  var n = 0
  private val serverSocket = new ServerSocket(30391)
  private var inputStream:InputStream = _
  private var socket:Socket = _ //tcp
  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"ready to build socket")
          socket = serverSocket.accept() //tcp
          log.info(s"socket build successfully")
          inputStream = socket.getInputStream
          revThread.start()
          if(isTest) {
            removeTestFile()
//            testThread.start()
          }
          work()
      }
    }
  }

  def work()(implicit timer: TimerScheduler[Command],
                                              stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Stop =>
          Behaviors.stopped
      }
    }
  }

  val testThread = new Thread(()=>
  {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-re", "-stream_loop", "-1", "-i", indexPath+"test.mp4", "-c:v", "libx264", "-s", "720x576",
      "-c:a", "copy", "-f", "dash","-window_size","20","-extra_window_size","20","-hls_playlist", "1", fileLocation+"test/index.mpd")
    pb.inheritIO().start()
  })

  val revThread = new Thread({()=>
    while(true) {
      val buf = new Array[Byte](199)
      try{
        n = inputStream.read(buf)
        if (n > 0) {
          distributor ! DistributorWorker.Data(buf)
        } else if(n == -1){
          reStart()
        }
      } catch {
        case e: SocketException =>
          reStart()
      }
    }
  })

  def reStart() = {
    log.info("socket disconnected, close all the room, Listen again.")
    if(socket != null){
      if(inputStream != null){
        inputStream.close()
      }
      socket.close()
    }
    DistributorWorker.roomUdpMap.keySet.foreach {roomId =>
      DistributorWorker.roomUdpMap.remove(roomId)
      DistributorWorker.roomPortMap.remove(roomId)
      encodeManager ! EncodeManager.removeEncode(roomId)
    }
    socket = serverSocket.accept()
    log.info("socket reconnect successfully!")
    inputStream = socket.getInputStream
  }

}
