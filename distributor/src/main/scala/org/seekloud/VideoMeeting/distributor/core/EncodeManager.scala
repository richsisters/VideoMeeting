package org.seekloud.VideoMeeting.distributor.core

import java.net.ServerSocket

import scala.language.implicitConversions
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import org.seekloud.VideoMeeting.distributor.Boot.{pullActor,liveManager}

/**
  * User: yuwei
  * Date: 2019/8/26
  * Time: 20:09
  */

object EncodeManager {
  sealed trait Command

  private val log = LoggerFactory.getLogger(this.getClass)

  case class UpdateEncode(roomId: Long, startTime:Long) extends Command

  case class removeEncode(roomId: Long) extends Command

  case class ChildDead(roomId: Long, childName: String, value: ActorRef[EncodeActor.Command]) extends Command

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { _ =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"encodeManager start----")
          work(mutable.Map[Long,(Int, ActorRef[EncodeActor.Command])]())
      }
    }
  }

  def work(enCodeRefMap: mutable.Map[Long, (Int, ActorRef[EncodeActor.Command])])
    (implicit timer: TimerScheduler[Command],
      stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@UpdateEncode(roomId, startTime) =>
          log.info(s"got msg: $m")
          //fixme 连线流程
          if(enCodeRefMap.get(roomId).isDefined){
            log.info(s"room $roomId exist.")
//            enCodeRefMap(roomId)._2 ! EncodeActor.ReStart(port, startTime)
          }else{
            val port = getFreePort
            pullActor ! PullActor.RoomWithPort(roomId, port)
            liveManager ! LiveManager.RoomWithPort(roomId, port)
            log.info(s"assign free port $port")
            val encoder = getEncodeActor(ctx, roomId, port, startTime)
            enCodeRefMap.put(roomId, (port,encoder))
          }
          Behaviors.same

        case removeEncode(roomId) =>
          enCodeRefMap.get(roomId).foreach{ e => e._2 ! EncodeActor.Stop}
          enCodeRefMap.remove(roomId)
          Behaviors.same

        case ChildDead(_, childName, _) =>
          log.info(s"$childName id dead ---")
          Behaviors.same
      }
    }
  }

  private def getEncodeActor(ctx: ActorContext[Command], roomId: Long ,port: Int, startTime:Long) = {
    val childName = s"encodeActor_$roomId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(EncodeActor.create(roomId, port, startTime), childName)
      ctx.watchWith(actor, ChildDead(roomId, childName, actor))
      actor
    }.unsafeUpcast[EncodeActor.Command]
  }

  private def getFreePort: Int = {
    val serverSocket =  new ServerSocket(0) //读取空闲的可用端口
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

}
