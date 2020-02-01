package org.seekloud.VideoMeeting.rtpServer.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio._
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicInteger

import org.seekloud.VideoMeeting.rtpServer.utils.RtpUtil


/**
  * Created by haoshuhan on 2019/7/16.
  */
object ReceiveManager {
  private val log = LoggerFactory.getLogger(this.getClass)

//  var last_seq = 0

  sealed trait Command

  case object FetchData extends Command

  case class ChildDead(id: Int, childName: String, value: ActorRef[ReceiveActor.Command]) extends Command

  def create(host: String, port: Int): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        val channel = DatagramChannel.open()
        channel.socket().bind(new InetSocketAddress(host, port))
        channel.socket().setReuseAddress(true)
        val buf = ByteBuffer.allocate(1024 * 32)
        ctx.self ! FetchData
        (0 until 10).foreach(getRecvActor(ctx, _))
        work(channel, buf, new AtomicInteger(0))
      }
    }
  }

  def work(channel: DatagramChannel,
           buf: ByteBuffer,
           seq: AtomicInteger)
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case FetchData =>
//          println(s"fetch data")
          buf.clear()
          val remoteAddress = channel.receive(buf)
          buf.flip()
          val byteArray = new Array[Byte](buf.remaining())
          buf.get(byteArray)
//          val seq2 = RtpUtil.parseData(byteArray).header.seq
//          if (seq2 - last_seq != 1) {
//            println(s"********ERROR, seq: $seq2, last_seq: $last_seq")
//          }
//          last_seq = seq2
          getRecvActor(ctx, seq.getAndIncrement() % 10) !
            ReceiveActor.OriginalData(byteArray, channel, remoteAddress)
          ctx.self ! FetchData
          Behaviors.same

        case ChildDead(id, childName, actor) =>
          log.error(s"$childName dead!")
          Behaviors.same

        case x =>
          log.info(s"recv unknown msg: $x")
          Behaviors.same

      }

    }
  }

  private def getRecvActor(ctx: ActorContext[Command], id: Int) = {
    val childName = s"recvActor_$id"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(ReceiveActor.create(id), childName)
      ctx.watchWith(actor, ChildDead(id, childName, actor))
      actor
    }.unsafeUpcast[ReceiveActor.Command]
  }




}
