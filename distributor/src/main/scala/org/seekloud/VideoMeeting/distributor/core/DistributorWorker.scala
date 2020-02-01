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
import org.seekloud.VideoMeeting.distributor.Boot.encodeManager

/**
  * User: yuwei
  * Date: 2019/8/26
  * Time: 20:05
  */
object DistributorWorker {
  case class TsData(payload:Byte, roomId:Long, body:ByteBuffer)

  trait Command
  case class RoomWithPort(roomId: Long, port: Int) extends Command
  case class SocketDead(roomId:Long) extends Command
  case class Data(data:Array[Byte]) extends Command
  object PayloadType{
    val newLive = 1
    val packet = 2
    val closeLive = 3
    val heartbeat = 4
  }
  var n = 0
  val roomUdpMap = mutable.Map[Long, InetSocketAddress]()
  private val sendChannel =  DatagramChannel.open()
  val roomPortMap = mutable.Map[Long, Int]()
  private val log = LoggerFactory.getLogger(this.getClass)


  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          work()
      }
    }
  }

  def work()(implicit timer: TimerScheduler[Command],
             stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case RoomWithPort(roomId, port) =>
          roomUdpMap.put(roomId, new InetSocketAddress("127.0.0.1", port))
          roomPortMap.put(roomId, port)
          Behaviors.same

        case Data(buf) =>
          val data = unpackTs(buf)
          if (data.payload == PayloadType.packet) {
            if (roomUdpMap.get(data.roomId).isDefined) {
              sendChannel.send(data.body, roomUdpMap(data.roomId))
            } else {
              log.info(s"map does not have roomId:${data.roomId}")
            }
          }else if (data.payload == PayloadType.heartbeat) {

          }else if (data.payload == PayloadType.newLive) {
            log.info(s"new room --${data.roomId}--")
            val bytes = data.body.array().take(8)
            val startTime = bytes2Long(bytes)
            encodeManager ! EncodeManager.UpdateEncode(data.roomId, startTime)
          } else if (data.payload == PayloadType.closeLive) {
            log.info(s"${data.roomId} close ------")
            encodeManager ! EncodeManager.removeEncode(data.roomId)
            roomPortMap.remove(data.roomId)
            roomUdpMap.remove(data.roomId)
          } else {
            log.info(s"payloadType ${data.payload} is invalid")
          }
          Behaviors.same
      }
    }
  }

  def unpackTs(data: Array[Byte]) ={
    val payload = data(0)
    val roomId =byteArrayToLong(data.slice(1,9))
    val valid = toInt(data.slice(9,11))
    val body = data.slice(11, valid + 11)
    val dataBuf = ByteBuffer.allocate(valid)
    dataBuf.put(body)
    dataBuf.flip()
    TsData(payload, roomId,dataBuf)
  }

  def bytes2Long(bytes:Array[Byte]) = {
    val buf = ByteBuffer.allocate(8)
    buf.put(bytes)
    buf.flip()
    buf.getLong()
  }

  def byteArrayToLong(bytes:Array[Byte])= {
    var value = 0;
    // 由高位到低位
    for (i<-0 until 8) {
      val shift = (8 - 1 - i) * 8
      value += (bytes(i) & 0x000000FF) << shift;// 往高位游
    }
    value
  }
  def byteArrayToLLong(bytes:Array[Byte])= {
    var value = 0;
    // 由高位到低位
    for (i<-0 until 16) {
      val shift = (16 - 1 - i) * 8
      value += (bytes(i) & 0x000000FF) << shift;// 往高位游
    }
    value
  }
  def toInt(numArr: Array[Byte]) = {
    numArr.zipWithIndex.map { rst =>
      (rst._1 & 0xFF) << (8 * (numArr.length - rst._2 - 1))
    }.sum
  }

}
