package org.seekloud.VideoMeeting.processor.core_new

import java.io._
import java.nio.channels.Channels
import java.nio.channels.Pipe.{SinkChannel, SourceChannel}
import java.util.concurrent.TimeUnit

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.processor.common.AppSettings.recordPath
import org.seekloud.VideoMeeting.processor.stream.PipeStream
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import org.seekloud.VideoMeeting.processor.Boot.{executor, streamPullActor, streamPushActor}
import org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager.Processor.{RecordData, RecordInfo}

import scala.collection.mutable
import org.bytedeco.javacpp.Loader
import org.seekloud.byteobject.MiddleBufferInJvm

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by sky
  * Date on 2019/10/22
  * Time at 下午2:28
  *
  * actor由RoomManager创建
  * 连线房间
  * 管理多路grabber和一路recorder
  */
object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  private final case class SwitchBehavior(
                                           name: String,
                                           behavior: Behavior[Command],
                                           durationOpt: Option[FiniteDuration] = None,
                                           timeOut: TimeOut = TimeOut("busy time error")
                                         ) extends Command

  private case class TimeOut(msg: String) extends Command

  private final val InitTime = Some(5.minutes)

  private final case object BehaviorChangeKey

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer: TimerScheduler[Command]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  //case class NewRoom(roomId: Long, host: String, client1: String, client2: String, client3: String, pushLiveId: String, pushLiveCode: String, layout: Int) extends Command
  case class NewRoom(roomId: Long, host: String, clientInfo: List[String], pushLiveId: String, pushLiveCode: String, startTime:Long) extends Command

  case class Recorder(roomId: Long, recorderRef: ActorRef[RecorderActor.Command]) extends Command

  case class ForceExit4Client(roomId: Long, liveId: String) extends  Command

  case class BanOnClient(roomId: Long, liveId: String, isImg: Boolean, isSound: Boolean) extends Command

  case class CancelBan(roomId: Long, liveId: String, isImg: Boolean, isSound: Boolean) extends Command

  case class SpeakerRight(roomId: Long, liveId: String) extends  Command

  case class CloseRoom(roomId: Long) extends Command

  case class ChildDead4Grabber(childName: String, value: ActorRef[GrabberActor.Command]) extends Command// fixme liveID

  case class ChildDead4Recorder(childName: String, value: ActorRef[RecorderActor.Command]) extends Command

  case class ChildDead4PushPipe(roomId: Long, startTime:Long, liveId: String, childName: String, value: ActorRef[StreamPushPipe.Command]) extends Command

  case class ChildDead4PullPipe(liveId: String, childName: String, value: ActorRef[StreamPullPipe.Command]) extends Command

  case class ClosePipe(liveId: String) extends Command

  case object Timer4Stop

  case object Stop extends Command

  case class Timer4PipeClose(liveId: String)

  val pipeMap = mutable.Map[String, PipeStream]()

  val pullPipeMap = mutable.Map[String, ActorRef[StreamPullPipe.Command]]()
  val pushPipeMap = mutable.Map[String, ActorRef[StreamPushPipe.Command]]()

  def create(roomId: Long, host: String, clientInfo: List[String], pushLiveId: String, pushLiveCode: String,  startTime: Long): Behavior[Command]= {
    Behaviors.setup[Command]{ ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
      Behaviors.withTimers[Command] {
        implicit timer =>
          log.info(s"roomActor start----")
          work(mutable.Map[String, ActorRef[GrabberActor.Command]](), None, List[String](), None)
      }
    }
  }

  def work(
    grabberMap: mutable.Map[String, ActorRef[GrabberActor.Command]],
    recorderMap: Option[ActorRef[RecorderActor.Command]],
    pullInfoList: List[String],
    pushInfoOption: Option[String]
  )(implicit stashBuffer: StashBuffer[Command],
    sendBuffer:MiddleBufferInJvm,
    timer: TimerScheduler[Command]):Behavior[Command] = {
    Behaviors.receive[Command]{(ctx, msg) =>
      msg match {

        case msg:NewRoom =>
          log.info(s"${ctx.self} receive a msg $msg")
          val file = new File(s"$recordPath${msg.roomId}/${msg.startTime}/")
          if (!file.exists()) {
            log.debug(s"mkdirs $recordPath${msg.roomId}/${msg.startTime}/")
            file.mkdirs()
          }
          val pushPipe = new PipeStream
          val pushSink = pushPipe.getSink
          val pushSource = pushPipe.getSource
          val pushOut = Channels.newOutputStream(pushSink)

          val pullPipe4Host = new PipeStream
          val pullSink4Host = pullPipe4Host.getSink
          val pullSource4Host= pullPipe4Host.getSource
          val pullInput4Host = Channels.newInputStream(pullSource4Host)
          val pullOut4Host = Channels.newOutputStream(pullSink4Host)

          val recorderActor = getRecorderActor(ctx, msg.roomId, msg.host, msg.clientInfo, msg.pushLiveId, msg.pushLiveCode, pushOut)
          val grabber4host = getGrabberActor(ctx, msg.roomId, msg.host, pullInput4Host, recorderActor)

          grabberMap.put(msg.host, grabber4host)
          //grabberMap(msg.roomId).put(msg.host, grabber4host)

          val newPullInfoList = msg.host :: msg.clientInfo

          msg.clientInfo.foreach{ clientId =>

            val pullPipe4Client = new PipeStream
            val pullSink4Client  = pullPipe4Client.getSink
            val pullSource4Client = pullPipe4Client.getSource
            val pullInput4Client = Channels.newInputStream(pullSource4Client)
            val pullOut4Client  = Channels.newOutputStream(pullSink4Client)

            pipeMap.put(clientId, pullPipe4Client)
            val grabber4client = getGrabberActor(ctx, msg.roomId, clientId, pullInput4Client, recorderActor)
            val pullPipe4client = getPullPipe(ctx, msg.roomId, clientId, pullOut4Client)
            pullPipeMap.put(clientId, pullPipe4client)

            grabberMap.put(clientId, grabber4client)

          }

          pipeMap.put(msg.host, pullPipe4Host)
          pipeMap.put(msg.pushLiveId, pushPipe)

          val pullPipe4host = getPullPipe(ctx, msg.roomId, msg.host, pullOut4Host)
          val pushPipe4recorder = getPushPipe(ctx, msg.roomId, msg.pushLiveId, msg.pushLiveCode, pushSource, msg.startTime)

          pullPipeMap.put(msg.host, pullPipe4host)
          pushPipeMap.put(msg.pushLiveId, pushPipe4recorder)

          streamPushActor ! StreamPushActor.NewLive(msg.pushLiveId, msg.pushLiveCode)

          work(grabberMap, Some(recorderActor), newPullInfoList, Some(msg.pushLiveId))

        case msg:Recorder =>
          log.info(s"${ctx.self} receive a msg $msg")
          grabberMap.foreach(_._2 ! GrabberActor.Recorder(msg.recorderRef))
          Behaviors.same

        case msg:ForceExit4Client =>
          log.info(s"${ctx.self} receive a msg $msg")

          if(pullInfoList.nonEmpty){
            streamPullActor ! StreamPullActor.StopPull4Client(msg.liveId)
            pullInfoList.foreach{l =>
              if(l == msg.liveId){
                pullPipeMap.get(l).foreach( a => a ! StreamPullPipe.ClosePipe)
                pullPipeMap.remove(msg.liveId)
              }
            }
          } else {
            log.info(s"${msg.roomId}  pipe not exist when forceExit4Client")
          }

          grabberMap.foreach{grabber =>
            if(grabber._1 == msg.liveId){
              grabber._2 ! GrabberActor.StopGrabber
            }
          }

          if(recorderMap.isDefined) {
            recorderMap.foreach(_ ! RecorderActor.ClientExit(msg.liveId))
          } else {
            log.info(s"${msg.roomId}  recorder not exist when forceExit4Client")
          }

          Behaviors.same

        case msg: BanOnClient =>
          log.info(s"${ctx.self} receive a msg $msg")
          if(recorderMap.isDefined) {
            recorderMap.foreach(_ ! RecorderActor.BanOnClient(msg.liveId, msg.isImg, msg.isSound))
          } else {
            log.info(s"${msg.roomId}  recorder not exist when forceExit4Client")
          }
          Behaviors.same

        case msg: CancelBan =>
          log.info(s"${ctx.self} receive a msg $msg")
          if(recorderMap.isDefined) {
            recorderMap.foreach(_ ! RecorderActor.CancelBan(msg.liveId, msg.isImg, msg.isSound))
          } else {
            log.info(s"${msg.roomId}  recorder not exist when forceExit4Client")
          }
          Behaviors.same

        case msg: SpeakerRight =>
          log.info(s"${ctx.self} receive a msg $msg") // todo 指定某人发言
          Behaviors.same

        case CloseRoom(roomId) =>
          log.info(s"${ctx.self} receive a msg $msg")

          if(pullInfoList.nonEmpty){
            streamPullActor ! StreamPullActor.RoomClose(pullInfoList)
            pullInfoList.foreach{l =>
              pullPipeMap.get(l).foreach(a => a ! StreamPullPipe.ClosePipe)
            }
          } else {
            log.info(s"$roomId pipe not exist when closeRoom")
          }

          grabberMap.foreach(g => g._2 ! GrabberActor.StopGrabber)

          recorderMap.foreach(_ ! RecorderActor.StopRecorder)

          if(pushInfoOption.isDefined){
            pushPipeMap.get(pushInfoOption.get).foreach( a => a ! StreamPushPipe.ClosePipe)
          }else{
            log.info(s"$roomId  push live not exist when closeRoom")
          }

          Behaviors.same

        case ClosePipe(liveId) =>
          pullPipeMap.remove(liveId)
          pushPipeMap.remove(liveId)
          pipeMap.remove(liveId)
          Behaviors.same

        case Stop =>
          log.info(s"${ctx.self} stopped ------")
          Behaviors.stopped

        case ChildDead4Grabber(childName, value) =>
          log.info(s"$childName is dead ")
          grabberMap.clear()
          work(grabberMap, recorderMap, pullInfoList, pushInfoOption)

        case ChildDead4Recorder(childName, value) =>
          log.info(s"$childName is dead ")
          work(grabberMap, None, pullInfoList, pushInfoOption)

        case ChildDead4PullPipe(liveId, childName, value) =>
          log.info(s"$childName is dead ")
          pullPipeMap.remove(liveId)
          work(grabberMap, recorderMap, pullInfoList.filter(l => l != liveId), pushInfoOption)

        case ChildDead4PushPipe(roomId, startTime, liveId, childName, value) =>
          log.info(s"$childName is dead ")
          pushPipeMap.remove(liveId)
          saveRecord(roomId, startTime).onComplete{
            case Success(value) =>
              log.info(s"save record success!")
              ctx.self ! SwitchBehavior("work", work(grabberMap, recorderMap, pullInfoList, None))

            case Failure(exception) =>
              log.error(s"save record error! $exception")
          }
          switchBehavior(ctx,"busy",busy(),InitTime,TimeOut("busy"))

        case _ =>
          log.info(s"unknown msg:$msg")
          Behaviors.same
      }
    }
  }

  private def busy()(implicit stashBuffer: StashBuffer[Command],
    timer: TimerScheduler[Command],
    sendBuffer: MiddleBufferInJvm
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          ctx.self ! Stop
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
  }

  def getGrabberActor(ctx: ActorContext[Command], roomId: Long, liveId: String, source: InputStream, recorderRef: ActorRef[RecorderActor.Command]) = {
    val childName = s"grabberActor_$liveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(GrabberActor.create(roomId, liveId, source, recorderRef), childName)
      ctx.watchWith(actor,ChildDead4Grabber(childName, actor))
      actor
    }.unsafeUpcast[GrabberActor.Command]
  }

  def getRecorderActor(ctx: ActorContext[Command], roomId: Long, host: String, clientInfo: List[String], pushLiveId: String,  pushLiveCode: String, out: OutputStream) = {
    val childName = s"recorderActor_$pushLiveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(RecorderActor.create(roomId, host, clientInfo: List[String], 0, out), childName)
      ctx.watchWith(actor,ChildDead4Recorder(childName, actor))
      actor
    }.unsafeUpcast[RecorderActor.Command]
  }

  def getPullPipe(ctx: ActorContext[Command], roomId: Long, liveId: String, out: OutputStream) = {
    val childName = s"pullPipeActor_$liveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(StreamPullPipe.create(roomId: Long, liveId: String, out), childName)
      ctx.watchWith(actor, ChildDead4PullPipe(liveId, childName, actor))
      actor
    }.unsafeUpcast[StreamPullPipe.Command]
  }

  def getPushPipe(ctx: ActorContext[Command], roomId: Long, pushLiveId: String, pushLiveCode: String, source: SourceChannel, startTime: Long) = {
    val childName = s"pushPipeActor_$pushLiveId"
    ctx.child(childName).getOrElse{
      val actor = ctx.spawn(StreamPushPipe.create(roomId, pushLiveId, pushLiveCode, source, startTime), childName)
      ctx.watchWith(actor, ChildDead4PushPipe(roomId, startTime, pushLiveId, childName, actor) )
      actor
    }.unsafeUpcast[StreamPushPipe.Command]
  }

  def saveRecord(roomId: Long, startTime: Long): Future[Int] = {
    log.info("begin to save record...")
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    Future{
      val pb = new ProcessBuilder(ffmpeg, "-i", s"$recordPath$roomId/$startTime/out.ts", "-c:v", "libx264", "-c:a", "copy", "-preset", "faster", s"$recordPath$roomId/$startTime/record.mp4")
      val process = pb.start()
      process.waitFor(10, TimeUnit.SECONDS)
      val buffer4Error = new BufferedReader(new InputStreamReader(process.getErrorStream))
      var line = ""
      val sb = new StringBuilder()
      while ({
        line = buffer4Error.readLine()
        line != null
      }) {
        sb.append(line)
      }
//      log.info(s"${sb.toString()}")
      1
    }.recover{
      case e: Exception =>
        log.error(s"save record error! $e")
        -1
    }
  }
}
