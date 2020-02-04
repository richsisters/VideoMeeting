package org.seekloud.VideoMeeting.distributor.core

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.{Logger, LoggerFactory}
import org.seekloud.VideoMeeting.distributor.common.AppSettings._
import org.seekloud.VideoMeeting.rtpClient.Protocol._
import org.seekloud.VideoMeeting.rtpClient.PullStreamClient
import org.seekloud.VideoMeeting.distributor.Boot.{liveManager, pullActor}
import org.seekloud.VideoMeeting.distributor.core.SendActor.{SendData, StopSend}

import scala.collection.mutable
/**
  * Author: tldq
  * Date: 2019-10-22
  * Time: 12:57
  */
object PullActor {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  case class RoomClose(roomId: Long) extends Command

  case class NewLive(live: String, roomId:Long) extends Command

  case class Ready(client: PullStreamClient) extends Command

  case object PullStream extends Command

  case class CleanStream(liveId:String) extends Command

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[SendActor.Command]) extends Command

  case class RoomWithPort(roomId: Long, port: Int) extends Command

  private val liveSenderMap = mutable.Map[String, ActorRef[SendActor.Command]]()
  private val liveCountMap = mutable.Map[String,Int]()
  private val roomLiveMap = mutable.Map[Long, String]()

  //test
  private val pullChannel = DatagramChannel.open()
  val recvStreamBuf: ByteBuffer = ByteBuffer.allocate(1024 * 64)
  val pullLiveIdMap: mutable.Map[Int, String] = mutable.Map.empty[Int, String]
  for(i <- 1 to 2){
    pullLiveIdMap.put(i,s"$i")
  }

  def toInt(numArr: Array[Byte]): Int = {
    numArr.zipWithIndex.map { rst =>
      (rst._1 & 0xFF) << (8 * (numArr.length - rst._2 - 1))
    }.sum
  }

  def parseData(bytes: Array[Byte]): Data = {
    val first_byte = bytes(0)
    val payloadType = bytes(1)
    val seq = toInt(bytes.slice(2, 4))
    val ts = toInt(bytes.slice(4, 8))
    val ssrc = toInt(bytes.slice(8, 12))
    val data = bytes.drop(12)
    Data(Header(payloadType, 0, seq, ssrc, ts), data)
  }

  val recvThread = new Thread(() => {
    try {
      while(true) {
        recvStreamBuf.clear()
        pullChannel.receive(recvStreamBuf)
        recvStreamBuf.flip()
        val byteArray = new Array[Byte](recvStreamBuf.remaining())
        recvStreamBuf.get(byteArray)
        val data = parseData(byteArray)
        val ssrc = data.header.ssrc
        val seq = data.header.seq
        val liveId = pullLiveIdMap.get(ssrc)
        liveId match {
          case Some(id) =>
            //            log.info(s"------------------------------------start pull$id")
            pullActor ! PullStreamData(id, seq, data.body)
          //            log.info(s"------------------------------------pull$id")
          case None =>
            log.warn(s"unknown data with ssrc $ssrc")
        }
      }
    } catch {
      case e: Exception =>
        log.error(s"recvThread catch exception: $e")
    }
  })

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"pullActor start----")
          if(testModel){
            pullChannel.socket().bind(new InetSocketAddress("0.0.0.0", 41660))
            work()
          }else {
            val pullStreamDst = new InetSocketAddress(rtpToHost, 61041)
            val host = "0.0.0.0"
            val port = getRandomPort
            val client = new PullStreamClient(host, port, pullStreamDst, ctx.self, rtpServerDst)
            ctx.self ! Ready(client)
            wait()
          }
      }
    }
  }

  def getRandomPort: Int = {
    val channel = DatagramChannel.open()
    val port = channel.socket().getLocalPort
    channel.close()
    port
  }

  def wait()(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@Ready(client) =>
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
        case m@NewLive(liveId, roomId) =>
          liveCountMap.put(liveId, 0)
          if(roomLiveMap.get(roomId).isEmpty){
            log.info(s"got msg: $m")
            roomLiveMap.put(roomId,liveId)
            val sender = getSendActor(ctx,roomId)
            liveSenderMap.put(liveId, sender)
          }else{
            log.info(s"roomId:$roomId has existed.")
            val oId = roomLiveMap(roomId)
            if(liveSenderMap.get(oId).isDefined){
              log.info("status switch.")
              log.info(s"liveId change from $oId to $liveId")
              val s = liveSenderMap(oId)
              roomLiveMap.update(roomId,liveId)
              liveSenderMap.remove(oId)
              liveSenderMap.put(liveId,s)
            }else{
              log.warn(s"liveId:$oId not has sender, not change to $liveId")
            }
          }
          ctx.self ! PullStream
          Behaviors.same

        case PullStream =>
          val lives = liveSenderMap.keys.toList
          client.pullStreamData(lives)
          Behaviors.same

        case m@RoomWithPort(roomId, port) =>
          log.info(s"got msg: $m")
          roomLiveMap.get(roomId).foreach(liveId =>
            liveSenderMap.get(liveId).foreach( s => s! SendActor.GetUdp(new InetSocketAddress("127.0.0.1", port)))
          )
          Behaviors.same

        case StreamStop(liveId) =>
          ctx.self ! CleanStream(liveId)
          Behaviors.same

        case CleanStream(liveId) =>
          log.info(s"$liveId does not have stream for 30 secs and kill it")
          liveManager ! LiveManager.liveStop(liveId)
          Behaviors.same

        case m@PullStreamReqSuccess(liveIds) =>
          log.info(s"got msg: $m")
          Behaviors.same

        case m@NoStream(lives) =>
          log.error(s"got msg: $m")
          Behaviors.same

        case PullStreamPacketLoss =>
          log.info("PullStreamPacketLoss")
          Behaviors.same

        case PullStreamData(liveId, seq, data) =>
          if(liveSenderMap.get(liveId).isDefined){
            val sender = liveSenderMap(liveId)
            if(liveCountMap.getOrElse(liveId,0) < 5){
              log.info(s"$liveId get stream --")
              liveCountMap.update(liveId, liveCountMap(liveId) +1)
            }
            sender ! SendData(data)
          }else{
            log.warn(s"$liveId's sender is not existed.")
          }
          Behaviors.same

        case RoomClose(roomId) =>
          roomLiveMap.get(roomId).foreach{ liveId =>
            liveSenderMap.get(liveId).foreach(
              s =>
                s ! StopSend
            )
            liveSenderMap.remove(liveId)
          }
          roomLiveMap.remove(roomId)
          ctx.self ! PullStream
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }

  def work()(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]):Behavior[Command] = {
    log.info("-----------------------测试中，自己收流")
    recvThread.start()
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@NewLive(liveId, roomId) =>
          log.info(s"got msg: $m")
          liveCountMap.put(liveId, 0)
          roomLiveMap.put(roomId,liveId)
          val sender = getSendActor(ctx,roomId)
          liveSenderMap.put(liveId, sender)
          Behaviors.same

        case RoomWithPort(roomId, port) =>
          roomLiveMap.get(roomId).foreach(liveId =>
            liveSenderMap.get(liveId).foreach( s => s! SendActor.GetUdp(new InetSocketAddress("127.0.0.1", port)))
          )
          Behaviors.same

        case PullStreamData(liveId, seq, data) =>
          if(liveSenderMap.get(liveId).isDefined){
            val sender = liveSenderMap(liveId)
            if(liveCountMap.getOrElse(liveId,0) < 5){
              log.info(s"$liveId get stream --")
              liveCountMap.update(liveId, liveCountMap(liveId) +1)
            }
            sender ! SendData(data)
          }
          Behaviors.same

        case RoomClose(roomId) =>
          roomLiveMap.get(roomId).foreach( liveId =>
            liveSenderMap.remove(liveId)
          )
          roomLiveMap.remove(roomId)
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same
      }
    }
  }

  private def getSendActor(ctx: ActorContext[Command], roomId:Long) = {
    val childName = s"wrapActor_$roomId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(SendActor.create(roomId), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor
    }.unsafeUpcast[SendActor.Command]
  }

}
