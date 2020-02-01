package org.seekloud.VideoMeeting.processor.core

import java.io.OutputStream
import java.net.{InetSocketAddress, Socket}
import java.nio.{Buffer, ByteBuffer}
import java.nio.channels.DatagramChannel

import scala.concurrent.duration._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.processor.core.ChannelWorker.{Ready, log, wait}
import org.seekloud.VideoMeeting.rtpClient.Protocol._
import org.seekloud.VideoMeeting.rtpClient.PushStreamClient
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.processor.common.AppSettings._

import scala.collection.mutable
import scala.concurrent.duration._



/**
  * User: yuwei
  * Date: 2019/8/28
  * Time: 20:32
  * 对接distributor
  */
object SendActor {

  var client:PushStreamClient = _

  case object HeartBeat extends Command

  case object Timer4Heart

  case object Timer4InitSocket

  val heartBeat:Byte = 4

  val buf = new Array[Byte](188)

  private var count = 0

  val log = LoggerFactory.getLogger(this.getClass)

  case class NewLive(liveId:String, liveCode:String) extends Command

  case class Packet4Dispatcher(dataArray:Array[Byte]) extends Command

  case class Packet4Rtp(data:Array[Byte]) extends Command

  case object InitSocket extends Command

  case class Time4AuthAgain(liveId:String)

  private val liveIdCodeMap = mutable.Map[String, String]()
  private val authCountMap = mutable.Map[String, Int]()

  var output:OutputStream = null

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"sender start----")
          val pushStreamChannel = DatagramChannel.open()
          pushStreamChannel.socket().setReuseAddress(true)
          val pushStreamDst = new InetSocketAddress(rtpToHost, 61041)
          val socket = try {
            new Socket(distributorHost, 30391)
          } catch {
            case e: Exception =>
              log.info(s"sendActor connect tcp to $distributorHost failed $e")
              null
          }
          output = if(socket!=null){
            log.info("tcp connect successfully")
            socket.getOutputStream
          } else null
          val host = "0.0.0.0"
          val port = getRandomPort()
          val client = new PushStreamClient(host,port,pushStreamDst,ctx.self,rtpServerDst)
          this.client = client
          client.authStart()
          if(socket==null) timer.startSingleTimer(Timer4InitSocket,InitSocket,10.seconds)
          timer.startPeriodicTimer(Timer4Heart, HeartBeat, 2.minutes)
          work(client)
      }
    }
  }

  def getRandomPort() = {
    val channel = DatagramChannel.open()
    val port = channel.socket().getLocalPort
    channel.close()
    port
  }

  def work(client:PushStreamClient)(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match{
        case t:NewLive =>
          client.auth(t.liveId, t.liveCode)
          authCountMap.put(t.liveId, 0)
          liveIdCodeMap.put(t.liveId, t.liveCode)
          Behaviors.same

        case InitSocket =>
          count += 1
          val socket = try {
            new Socket(distributorHost, 30391)
          } catch {
            case e: Exception =>
              log.info(s"sendActor connect tcp to $distributorHost failed $e")
              null
          }
          val outputStream = if(socket!=null)socket.getOutputStream else null
          if(socket==null) {
//            if(count<5)
            timer.startSingleTimer(Timer4InitSocket,InitSocket,10.seconds)
            Behaviors.same
          }else{
            work(client)
          }

        case t:AuthRsp =>
          if(t.ifSuccess){
            log.info(s"${t.liveId} auth successfully ---")
            liveIdCodeMap.remove(t.liveId)
            authCountMap.remove(t.liveId)
          }else{
            log.info(s"${t.liveId} auth fails -----")
            val authTime = authCountMap.getOrElse(t.liveId, 1)
            if(liveIdCodeMap.get(t.liveId).isDefined && authTime < 5) {
              timer.startSingleTimer(Time4AuthAgain(t.liveId), NewLive(t.liveId, liveIdCodeMap(t.liveId)), 5.seconds)
              authCountMap.put(t.liveId, authTime + 1)
            }
          }
          Behaviors.same

        case t:Packet4Dispatcher =>
//          output.write(t.dataArray)
          Behaviors.same

        case HeartBeat =>
          if(output!=null) {
            output.write(packTs4Dispatcher(heartBeat, 0, buf.clone(), 1))
          }
          Behaviors.same

        case t:PushStreamError =>
          log.info(t.msg)
          Behaviors.same
      }
    }
  }

  def packTs4Dispatcher(payload:Byte, roomId: Long, ts:Array[Byte], valid:Long) ={
    val packBuf = ByteBuffer.allocate(199) // 1 + 2 + 8 + 188*7
    packBuf.put(payload)
    packBuf.put(toByte(roomId, 8))
    packBuf.put(toByte(valid, 2))
    packBuf.put(ts)
    packBuf.flip()
    packBuf.array()
  }
  def toByte(num: Long, byte_num: Int) = {
    (0 until byte_num).map { index =>
      (num >> ((byte_num - index - 1) * 8) & 0xFF).toByte
    }.toArray
  }

}
