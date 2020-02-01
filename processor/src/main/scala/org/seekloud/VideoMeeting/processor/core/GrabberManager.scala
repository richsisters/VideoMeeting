package org.seekloud.VideoMeeting.processor.core

import java.io.{InputStream, PipedInputStream, PipedOutputStream}
import java.nio.channels.{Channels, Pipe}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import net.sf.ehcache.transaction.xa.commands.Command
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.slf4j.LoggerFactory
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import org.seekloud.VideoMeeting.processor.Boot.blockingDispatcher

import scala.language.implicitConversions
import scala.collection.mutable
import scala.concurrent.duration._
import org.seekloud.VideoMeeting.processor.Boot.{channelWorker, recorderManager}
import org.seekloud.VideoMeeting.processor.core.GrabberActor.{GrabFrame, StartGrabber}
import org.seekloud.VideoMeeting.processor.stream.PipeStream
/**
  * User: yuwei
  * Date: 7/15/2019
  */
object GrabberManager {

  sealed trait Command

  case class TimeOut(msg: String) extends Command

  case class CloseRoom(roomId:Long) extends Command

  case class ChildDead(liveId: String, childName: String, value: ActorRef[GrabberActor.Command]) extends Command

  case class UpdateRole(roomId: Long, roles: List[String]) extends Command

  case class RecorderRef(roomId: Long, ref: ActorRef[RecorderActor.Command]) extends Command

  case class PullStreamSuccess(liveId:String) extends Command

  case class Timer4Pipe(liveId:String)

  case class ClosePipe(liveId:String) extends Command

  private val pipeMap = mutable.Map[String, PipeStream]()

  private val log = LoggerFactory.getLogger(this.getClass)

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"grabberManager start----")
          work(mutable.Map[Long, List[ActorRef[GrabberActor.Command]]](),
            mutable.Map[Long, List[String]]())
      }
    }
  }

  def work(roomRoleRefMap: mutable.Map[Long, List[ActorRef[GrabberActor.Command]]],
    roomRoleLiveMap: mutable.Map[Long, List[String]])(implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case m@RecorderRef(roomId, ref) =>
          log.info(s"got msg: $m")
          val grabbers = roomRoleRefMap.get(roomId)
          if(grabbers.isDefined){
            grabbers.get.foreach {g=>
              g ! GrabberActor.Recorder(ref)
            }
          } else {
            log.info(s"$roomId grabbers not exist")
          }
          Behaviors.same

        case GrabberManager.PullStreamSuccess(liveId)=>
          val grabber = getGrabberActorOpt(ctx,liveId)
          if(grabber.isDefined){
            grabber.get ! StartGrabber
          } else {
            log.info(s"$liveId grabber not exist")
          }
          Behaviors.same

        case m@UpdateRole(roomId, roles) =>
          log.info(s"got msg: $m")
          val pipe = new PipeStream
          val sink = pipe.getSink
//          sink.configureBlocking(false)
          val source = pipe.getSource
          val out = Channels.newInputStream(source)
          val oldRole = roomRoleLiveMap.get(roomId)
          if(oldRole.isEmpty || roles.length > oldRole.get.length){
            if (roles.length < 2) {
              if(oldRole.isEmpty) {
                val grabber = getGrabberActor(ctx, roles.head,"host", roomId, out)
                channelWorker ! ChannelWorker.NewLive(roles.head, roomId, "host", sink)
                roomRoleRefMap.put(roomId, List(grabber))
                pipeMap.put(roles.head, pipe)
              }else if(oldRole.get.length == 2){
                if (roomRoleRefMap.get(roomId).isDefined && roomRoleRefMap(roomId).length == 2) {
                  roomRoleRefMap(roomId).last ! GrabberActor.Stop
                  channelWorker ! ChannelWorker.RoomClose(List(oldRole.get.last))
                  roomRoleRefMap.put(roomId, List(roomRoleRefMap(roomId).head))
                }
                timer.startSingleTimer(Timer4Pipe(oldRole.get.last), ClosePipe(oldRole.get.last), 500.milli)
              }
            } else {
              val grabber = getGrabberActor(ctx, roles.last,"client", roomId, out)
              channelWorker ! ChannelWorker.NewLive(roles.last, roomId, "client", sink)
              if (roomRoleRefMap.get(roomId).isDefined) {
                roomRoleRefMap.put(roomId, List(roomRoleRefMap(roomId).head, grabber))
              }
              pipeMap.put(roles.last, pipe)
            }
          } else if(roles.length == 1 && !oldRole.get.contains(roles.head)) {
            channelWorker ! ChannelWorker.RoomClose(oldRole.get)
            roomRoleRefMap.get(roomId).foreach{_.foreach{g=> g ! GrabberActor.Stop}}
            val grabber = getGrabberActor(ctx, roles.head,"host", roomId, out)
            channelWorker ! ChannelWorker.NewLive(roles.head, roomId, "host", sink)
            oldRole.get.foreach{liveId =>
              pipeMap.get(liveId).foreach{p =>
                log.info(s"--- release $liveId grabber pipe ---")
                p.getSink.close()
                p.getSource.close()
              }
              pipeMap.remove(liveId)
            }
            pipeMap.put(roles.head, pipe)
            roomRoleRefMap.put(roomId, List(grabber))
          }

          roomRoleLiveMap.put(roomId, roles)
          Behaviors.same

        case CloseRoom(roomId) =>
          roomRoleRefMap.get(roomId).foreach{_.foreach{g=> g ! GrabberActor.Stop}}
          roomRoleLiveMap.get(roomId).foreach(lives=> lives.foreach(l=> timer.startSingleTimer(Timer4Pipe(l), ClosePipe(l), 500.milli)))
          roomRoleLiveMap.remove(roomId)
          roomRoleRefMap.remove(roomId)
          Behaviors.same

        case ClosePipe(liveId) =>
          pipeMap.get(liveId).foreach{
            p=>
              log.info(s"--- release $liveId grabber pipe ---")
//              p.getSink.close()
              p.getSource.close()
          }
          pipeMap.remove(liveId)
          Behaviors.same

        case ChildDead(liveId, childName, value) =>
          log.info(s"$childName id dead ---")
          Behaviors.same
      }
    }
  }

  private def getGrabberActor(ctx: ActorContext[Command], liveId: String, role:String, roomId: Long, buf:InputStream) = {
    val childName = s"grabberActor_$liveId"
    val actor = ctx.child(childName)
    if(actor.isDefined){
      log.info(s"$childName has existed-----")
//      actor.get.unsafeUpcast[GrabberActor.Command] ! GrabberActor.ToInit(buf)
      actor.get.unsafeUpcast[GrabberActor.Command]
    } else {
      val actor = ctx.spawn(GrabberActor.create(roomId,role, liveId, buf), childName)
      ctx.watchWith(actor, ChildDead(liveId, childName, actor))
      actor
    }.unsafeUpcast[GrabberActor.Command]
  }

  private def getGrabberActorOpt(ctx: ActorContext[Command], liveId: String) = {
    val childName = s"grabberActor_$liveId"
    val actor = ctx.child(childName)
    if(actor.isDefined) {
      Some(actor.get.unsafeUpcast[GrabberActor.Command])
    }else{
      None
    }
  }
}

