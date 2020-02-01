package org.seekloud.VideoMeeting.rtpServer.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.VideoMeeting.rtpServer.Boot.{executor, publishManager, scheduler, streamManager, timeout}
import org.seekloud.VideoMeeting.rtpServer.protocol.ApiProtocol._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.duration.FiniteDuration
import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.Address

object DataStoreActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class PackageLoss(clientId:Int, packageLossMap: mutable.Map[String, PackageLossInfo]) extends Command

  case class Delay(liveId: String, delay: Double) extends Command

  case class NeedStreamData(replyTo:ActorRef[AllStreamData]) extends Command

  case class TimeOut(msg:String) extends Command

  private final case object BehaviorChangeKey

  case class AllStreamData(addressStreams: List[AddressPackageInfo], streamDetail: List[StreamInfoDetailPlus])

  case class StreamsPackageLoss(stream: String, packageLoss: PackageLossInfo)

  case class stopGetData(liveId:String) extends Command

  final case class SwitchBehavior(
                                   name: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut = TimeOut("busy time error")
                                 ) extends Command

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None,timeOut: TimeOut  = TimeOut("wait error"))
                                  (implicit stashBuffer: StashBuffer[Command],
                                   timer:TimerScheduler[Command]) = {
//    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey,timeOut,_))
    stashBuffer.unstashAll(ctx,behavior)
  }

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      Behaviors.withTimers[Command] { implicit timer =>
        work(delayStore = mutable.Map())
      }
    }
  }

  def work(packageLossStore: mutable.Map[Int, mutable.Map[String, PackageLossInfo]] = mutable.Map(),
          delayStore: mutable.Map[String, Double] = mutable.Map())
          (implicit timer: TimerScheduler[Command],
           stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match{
        case PackageLoss(clientId, packageLossMap) =>
          packageLossStore.update(clientId, packageLossMap)
          work(packageLossStore, delayStore)

        case Delay(liveId, delay) =>
          delayStore.update(liveId, delay)
          work(packageLossStore, delayStore)

        case NeedStreamData(replyTo) =>
          val addressToStreamsMsg: Future[List[AddressToStreams]] = publishManager ? PublishManager.GetAddressToStreams
          val streamDetailMsg: Future[List[StreamInfoDetail]] = streamManager ? StreamManager.GetStreamInfo
          addressToStreamsMsg.flatMap{asm =>
            streamDetailMsg.map{sdm =>
              val lst = asm.map{addressToStreams =>
                val streamBandwidth = addressToStreams.streams.map {liveId =>
                  val streamOp = sdm.find(_.liveId == liveId)
                  if (streamOp.isDefined) streamOp.get.bandwidth
                  else List((1, 0), (3, 0), (10, 0))
                }
                val allStreamBandwidth = streamBandwidth.flatten.groupBy(_._1).map {l =>
                  (l._1, l._2.map(_._2).sum)
                }
                val bandwidthInfo = BandwidthInfo(
                  allStreamBandwidth.getOrElse(1, -1),
                  allStreamBandwidth.getOrElse(3, -1),
                  allStreamBandwidth.getOrElse(10, -1))
                val packageLossOfClient = packageLossStore.getOrElse(addressToStreams.clientId, mutable.Map())
                val packageLossOfStream =
                  if(addressToStreams.streams.nonEmpty) {
                    addressToStreams.streams.map(s =>
                      StreamPackageLoss(s, packageLossOfClient.getOrElse(s, PackageLossInfo(-1, -1, -1, -1, -1, -1, -1))))
                  }else{
                    List(StreamPackageLoss("-1",  PackageLossInfo(-1, -1, -1, -1, -1, -1, -1)))
                  }
                AddressPackageInfo(addressToStreams.address, packageLossOfStream, bandwidthInfo)
              }
              val sdmp = sdm.map{as =>
                StreamInfoDetailPlus(as.liveId, as.ssrc, as.ip, as.port, as.bandwidth, delayStore.getOrElse(as.liveId, -1))
              }
              replyTo ! AllStreamData(lst, sdmp)
              ctx.self ! SwitchBehavior("work",work(packageLossStore.filter(p => asm.map(_.clientId).contains(p._1)), delayStore))
            }
          }

          switchBehavior(ctx,"wait4Data", wait4Data(packageLossStore, delayStore))

        case stopGetData(liveId) =>
          log.info(s"$liveId is stopped")
          work()
      }
    }

  def wait4Data(packageLossStore: mutable.Map[Int, mutable.Map[String, PackageLossInfo]] = mutable.Map(),
                delayStore: mutable.Map[String, Double] = mutable.Map())
               (implicit timer: TimerScheduler[Command],
                stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match{
        case SwitchBehavior(name, behavior, durationOpt, timeOut) =>
//          log.debug(s"${ctx.self.path} recv a SwitchBehavior Msg=${name}")
          switchBehavior(ctx, name, behavior, durationOpt, timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when wait,msg=${m}")
          switchBehavior(ctx, "work", work(packageLossStore, delayStore))

        case unknownMsg =>
          stashBuffer.stash(unknownMsg)
          Behavior.same

      }

    }

}
