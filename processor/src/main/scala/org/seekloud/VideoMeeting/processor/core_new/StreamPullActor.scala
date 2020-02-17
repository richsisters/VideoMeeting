package org.seekloud.VideoMeeting.processor.core_new

import java.io.{File, FileOutputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, Pipe}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.processor.Boot
import org.seekloud.VideoMeeting.processor.Boot.{blockingDispatcher, roomManager}
import org.seekloud.VideoMeeting.processor.common.AppSettings.{debugPath, isDebug, rtpServerDst, rtpToHost}
import org.seekloud.VideoMeeting.processor.core_new.RoomManager.CloseRoom
import org.seekloud.VideoMeeting.processor.core_new.StreamPullPipe.{ClosePipe, NewBuffer}
import org.seekloud.VideoMeeting.rtpClient.Protocol.{Command, NoStream, PullStreamData, PullStreamPacketLoss, PullStreamReqSuccess, StreamStop}
import org.seekloud.VideoMeeting.rtpClient.PullStreamClient
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration._

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午2:20
  *
  * actor由Boot创建
  * 修改ChannelWorker到本文件，实现从media server拉流
  */
object StreamPullActor {

  val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)

  //  sealed trait Command

  case object Stop extends Command

  case class RoomClose(lives: List[String]) extends Command

  case class DispatchData(data: Array[Byte]) extends Command

  case class NewSend(live: String) extends Command

  case class StopPull4Client(liveId: String) extends Command

  case object TimerKey

  case object TimeKey4Clean

  case object CloseWriter extends Command

  case class NewLive(live: String, roomId:Long, ref: ActorRef[StreamPullPipe.Command]) extends Command

  case class Recorder(rec: ActorRef[RecorderActor.Command]) extends Command

  case class RecorderRef(roomId: Long, liveId:String, ref: ActorRef[RecorderActor.Command]) extends Command

  case object Listen extends Command

  case class Timer4NewLive(live:String)

  case object StartNewLive extends Command

  case class Ready(client: PullStreamClient) extends Command
  case object PullStream extends Command
  case class CleanStream(liveId:String) extends Command

  case class LiveInfo(roomId:Long, status:String)

  var pullClient:PullStreamClient = _

  private val liveInfoMap = mutable.Map[String, Long]()
  private val liveCountMap = mutable.Map[String, Int]()
  private val livePipeMap = mutable.Map[String, ActorRef[StreamPullPipe.Command]]()

  object Rtp_header {
    var m = 0
    var timestamp = 0l
    var payloadType = 111.toByte //负载类型号96
  }

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"stramPullActor start----")
          val pullStreamDst = new InetSocketAddress(rtpToHost, 42018)
          val host = "0.0.0.0"
          val port = getRandomPort()
          val client = new PullStreamClient(host, port, pullStreamDst, ctx.self, rtpServerDst)
          pullClient = client
          ctx.self ! Ready(client)
          wait()
      }
    }
  }

  def getRandomPort() = {
    val channel = DatagramChannel.open()
    val port = channel.socket().getLocalPort
    channel.close()
    port
  }

  def wait()(implicit timer: TimerScheduler[Command],
             stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m @ Ready(client) =>
          log.info(s"got msg: $m")
          stashBuffer.unstashAll(ctx, work(client))

        case x =>
          stashBuffer.stash(x)
          Behavior.same
      }
    }
  }

  def work(client:PullStreamClient)(implicit timer: TimerScheduler[Command],
                                    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    client.pullStreamStart()
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m @ NewLive(live, roomId, ref) =>
          log.info(s"${ctx.self} got msg: $m")
          livePipeMap.put(live, ref)
          liveInfoMap.put(live, roomId)
          liveCountMap.put(live, 0)
          ctx.self ! StartNewLive
          Behaviors.same

        case m @ StartNewLive =>
          log.info(s"got msg: $m")
          ctx.self ! PullStream
          Behaviors.same

        case PullStream =>
          val lives = liveInfoMap.keys.toList
          client.pullStreamData(lives)
          Behaviors.same

        case StreamStop(liveId) =>
          ctx.self ! CleanStream(liveId)
          Behaviors.same

        case msg: StopPull4Client =>
          log.info(s"${ctx.self} receive a msg $msg")
          if(liveInfoMap.get(msg.liveId).isDefined){
            liveInfoMap.remove(msg.liveId)
            livePipeMap.remove(msg.liveId)
            liveCountMap.remove(msg.liveId)
          }
          Behaviors.same

        case CleanStream(liveId) =>
          log.info(s"$liveId does not have stream for 30 secs and kill it")
          val infoOpt = liveInfoMap.get(liveId)
          if(infoOpt.isDefined) {
            val roomId = infoOpt.get
            //roomManager ! CloseRoom(roomId)
            liveInfoMap.remove(liveId)
            livePipeMap.remove(liveId)
            liveCountMap.remove(liveId)
          }else{
            log.debug(s"$liveId info not exist.")
          }
          Behaviors.same

        case m @ PullStreamReqSuccess(liveIds) =>
          log.info(s"got msg: $m")
          Behaviors.same

        case m @ NoStream(lives) =>
          log.error(s"got msg: $m")
          Behaviors.same

        case PullStreamPacketLoss =>
          Behaviors.same

        case PullStreamData(liveId, seq, data) =>
          if(Boot.showStreamLog){
            log.info(s"streamData $liveId $seq")
          }
          if(livePipeMap.get(liveId).isDefined) {
            val pullPipe = livePipeMap(liveId)
            pullPipe ! NewBuffer(data)
          }
          Behaviors.same

        case RoomClose(lives) =>
          log.info(s"got RoomClose Msg: $lives")
          lives.foreach { l =>
            livePipeMap.remove(l)
            liveInfoMap.remove(l)
            liveCountMap.remove(l)
          }
          ctx.self ! PullStream
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }
}
