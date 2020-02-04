package org.seekloud.VideoMeeting.distributor.core

import java.io.{File, FileOutputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.distributor.common.AppSettings.{testFile,testLocation}

/**
  * Author: tldq
  * Date: 2019-10-23
  * Time: 16:53
  */
object SendActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  case class SendData(data: Array[Byte]) extends Command

  case class GetUdp(udp:InetSocketAddress) extends Command

  case object StopSend extends Command

  private val sendChannel =  DatagramChannel.open()

  def create(roomId:Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"${ctx.self} init ----")
          init(roomId)
      }
    }
  }

  def init(roomId:Long)(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$roomId sender switch to init state.")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@GetUdp(udp) =>
          var output:FileOutputStream = null
          if(testFile){
            log.info(s"create new test file for room id : ${roomId}")
            val file = new File(s"$testLocation",s"${roomId}_in.ts")
            file.delete()
            file.createNewFile()
            output = new FileOutputStream(file)
          }
          work(roomId, udp, output)

        case x=>
          log.info(s"${ctx.self} got an unknown msg:$x")
          Behaviors.same
      }
    }
  }

  def work(roomId:Long,udp:InetSocketAddress,output:FileOutputStream)(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    log.info(s"$roomId sender switch to work state.")
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@SendData(data) =>
          sendChannel.send(ByteBuffer.wrap(data), udp)
          if(output!=null){
            output.write(data)
          }
          Behaviors.same

        case StopSend =>
          if(output!=null)
            output.close()
          Behaviors.stopped

        case x=>
          log.info(s"${ctx.self} got an unknown msg:$x")
          Behaviors.same
      }
    }
  }


}
