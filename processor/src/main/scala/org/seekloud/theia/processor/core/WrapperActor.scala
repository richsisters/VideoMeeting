package org.seekloud.VideoMeeting.processor.core

import java.io.{FileInputStream, PipedInputStream, PipedOutputStream}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.language.implicitConversions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.Pipe.SourceChannel

import org.seekloud.VideoMeeting.processor.Boot.sendActor
import org.seekloud.VideoMeeting.processor.core.SendActor.Packet4Dispatcher

import scala.concurrent.duration._
import scala.collection.mutable

object WrapperActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  case object SendData extends Command

  case object Close extends Command

  case class NewLive(startTime:Long) extends Command

  case class NewHostLive(startTime: Long,source: SourceChannel) extends Command

  case object Timer4Stop

  case object Timer4Send

  case object Stop extends Command


  object PayloadType{
    val newLive:Byte = 1
    val packet:Byte = 2
    val closeLive:Byte = 3
    val heartBeat:Byte = 4
  }

  //fixme 有何作用？
  private val liveCountMap = mutable.Map[String,Int]()


  def create(roomId: Long, liveId:String, source:SourceChannel, startTime:Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"${ctx.self} init ----")
          ctx.self ! NewLive(startTime)
          //fixme 为何一次读取一个ts长度
          work(roomId, liveId,source,ByteBuffer.allocate(188))
      }
    }
  }


  def work(roomId: Long,liveId:String, source:SourceChannel, dataBuf:ByteBuffer)
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case NewLive(startTime) =>
          log.info(s"startTime:$startTime")
          liveCountMap.put(liveId,0)
          dataBuf.clear()
          dataBuf.putLong(startTime)
          if(SendActor.output != null) {
            SendActor.output.write(packTs4Dispatcher(PayloadType.newLive, roomId, dataBuf.array().clone(), 8))
          }
//          timer.startSingleTimer(Timer4Send, SendData, 100.millis)
          ctx.self ! SendData
          dataBuf.clear()
          Behaviors.same

        case SendData=>
          //fixme dataBuf 何种情况下会==null
          if(dataBuf != null) {
            if (liveCountMap.getOrElse(liveId, 0) < 5) {
              log.info(s"$liveId send data --")
              liveCountMap.update(liveId, liveCountMap(liveId) + 1)
            }
            try{
              val r = source.read(dataBuf)
              dataBuf.flip()
              if (r > 0) {
                val data = dataBuf.array().clone()
                SendActor.client.pushStreamData(liveId, data.take(r))
                val dataArray = packTs4Dispatcher(PayloadType.packet, roomId, data, r)
                if(SendActor.output != null) {
                  SendActor.output.write(dataArray)
                }
                //fixme 释放资源? dataArray.finalize()
                ctx.self ! SendData
                dataBuf.clear()
              } else {
                log.info(s"wrapperActor got nothing, $r")
              }
            } catch {
              case e: AsynchronousCloseException =>
                log.info("pipe is closed")
            }
          }
          Behaviors.same

        case m@NewHostLive(startTime,newSource) =>
          log.info(s"got msg: $m")
          timer.startSingleTimer(Timer4Send, NewLive(startTime), 500.millis)
          work(roomId, liveId, newSource, dataBuf)

        case Close =>
          timer.startSingleTimer(Timer4Stop, Stop, 500.milli)
          Behaviors.same

        case Stop =>
          log.info(s"$roomId wrapper stopped ----")
          source.close()
          if(SendActor.output != null) {
            SendActor.output.write(packTs4Dispatcher(PayloadType.closeLive, roomId, dataBuf.array().clone(), 0))
          }
          dataBuf.clear()
          Behaviors.stopped

        case x=>
          log.info(s"${ctx.self} got an unknown msg:$x")
          Behaviors.same
      }
    }
  }

  def packTs4Dispatcher(payload:Byte, roomId: Long, ts:Array[Byte], valid:Long) ={
    val packBuf = ByteBuffer.allocate(199) // 1 + 8 + 2 + 188
    packBuf.clear()
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
