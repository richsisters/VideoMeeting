package org.seekloud.VideoMeeting.roomManager.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.{HostCloseRoom, _}
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager}
import org.seekloud.VideoMeeting.roomManager.common.Common
import org.seekloud.VideoMeeting.roomManager.models.dao.{RecordDao, StatisticDao, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol
import org.seekloud.VideoMeeting.roomManager.utils.{ProcessorClient, RtpClient}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}


object RoomActor {

  import org.seekloud.byteobject.ByteObject._

  import scala.language.implicitConversions

  private val log = LoggerFactory.getLogger(this.getClass)

  trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command with RoomManager.Command

  final case object Stop extends Command

  //private final case object DelayUpdateRtmpKey

  private final case class SwitchBehavior(
    name: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends Command

  private case class TimeOut(msg: String) extends Command

  private final case object BehaviorChangeKey

  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]) = {
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  final case class TestRoom(roomInfo: RoomInfo) extends Command

  final case class GetRoomInfo(replyTo: ActorRef[RoomInfo]) extends Command //考虑后续房间的建立不依赖ws

  private final val InitTime = Some(5.minutes)

  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
      log.debug(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(1024)  //8192
        val subscribers = mutable.HashMap.empty[(Long, Boolean), ActorRef[UserActor.Command]]
        init(roomId, subscribers)
      }
    }
  }

  private def init(
    roomId: Long,
    subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]],
    roomInfoOpt: Option[RoomInfo] = None
  )
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case ActorProtocol.StartRoom4Anchor(userId, `roomId`, actor) =>
          log.debug(s"${ctx.self.path} 用户id=$userId 开启了的新的会议室id=$roomId")
          subscribers.put((userId, false), actor)
          for {
            data <- RtpClient.getLiveInfoFunc()
            userTableOpt <- UserInfoDao.searchById(userId)
          } yield {
            data match {
              case Right(rsp) =>
                if (userTableOpt.nonEmpty) {
                  val roomInfo = RoomInfo(roomId, s"${userTableOpt.get.userName}的会议室", "", Common.DefaultImg.coverImg,
                    userTableOpt.get.uid, userTableOpt.get.userName, UserInfoDao.getHeadImg(userTableOpt.get.headImg)
                    , Some(rsp.liveInfo.liveId))
                  dispatchTo(subscribers)(List((userId, false)), StartLiveRsp(Some(rsp.liveInfo)))
                  ctx.self ! SwitchBehavior("idle", idle(roomInfo, mutable.HashMap[Long, LiveInfo](), subscribers, System.currentTimeMillis(), false))
                } else {
                  log.debug(s"${ctx.self.path} 开始直播被拒绝，数据库中没有该用户的数据，userId=$userId")
                  dispatchTo(subscribers)(List((userId, false)), StartLiveRefused)
                  ctx.self ! SwitchBehavior("init", init(roomId, subscribers))
                }
              case Left(error) =>
                log.debug(s"${ctx.self.path} 开始直播被拒绝，请求rtp server解析失败，error:$error")
                dispatchTo(subscribers)(List((userId, false)), StartLiveRefused)
                ctx.self ! SwitchBehavior("init", init(roomId, subscribers))
            }
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case GetRoomInfo(replyTo) =>
          if (roomInfoOpt.nonEmpty) {
            replyTo ! roomInfoOpt.get
          }else {
            log.debug("房间信息未更新")
            replyTo ! RoomInfo(-1, "", "", "", -1l, "", "")
          }
          Behaviors.same

        case TestRoom(roomInfo) =>
          //仅用户测试使用空房间
          idle(roomInfo,  mutable.HashMap[Long, LiveInfo](), subscribers, System.currentTimeMillis(), false)

        case ActorProtocol.AddUserActor4Test(userId, `roomId`, userActor) =>
          subscribers.put((userId, false), userActor)
          Behaviors.same

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$x in init state...")
          Behaviors.same
      }
    }
  }

  private def idle(
    roomInfo: RoomInfo,
    liveInfoMap: mutable.HashMap[Long, LiveInfo], //除主持人外的所有参会者的liveInfo
    subscribe: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]], //需要区分订阅的用户的身份，注册用户还是临时用户(uid,是否是临时用户true:是)
    startTime: Long,
    roomState: Boolean //房间目前是否处于已录像状态
  )(implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case ActorProtocol.AddUserActor4Test(userId, roomId, userActor) =>
          subscribe.put((userId, false), userActor)
          Behaviors.same

        case GetRoomInfo(replyTo) =>
          replyTo ! roomInfo
          Behaviors.same

        case ActorProtocol.WebSocketMsgWithActor(userId, roomId, wsMsg) =>
          handleWebSocketMsg(roomInfo, subscribe, liveInfoMap, startTime, roomState, dispatch(subscribe), dispatchTo(subscribe))(ctx, userId, roomId, wsMsg)

        case ActorProtocol.UpdateSubscriber(join, roomId, userId, temporary, userActorOpt) =>
          //虽然房间存在，但其实主播已经关闭房间，这时的startTime=-1
          //向所有人发送主播已经关闭房间的消息
          log.info(s"-----roomActor get UpdateSubscriber id: $roomId")
          if (startTime == -1) {
            dispatchTo(subscribe)(List((userId, temporary)), NoAuthor)
          }
          else {
            if (join == Common.Subscriber.join) {
              log.debug(s"${ctx.self.path}新用户加入房间roomId=$roomId,userId=$userId")
              subscribe.put((userId, temporary), userActorOpt.get)
            } else if (join == Common.Subscriber.left) {
              log.debug(s"${ctx.self.path}用户离开房间roomId=$roomId,userId=$userId")
              subscribe.remove((userId, temporary))
              if(liveInfoMap.contains(userId)){
                dispatch(subscribe)(AuthProtocol.AudienceDisconnect(userId, liveInfoMap(userId).liveId))
              }
              }
            }
          //所有的注册用户
          val audienceList = subscribe.filterNot(_._1 == (roomInfo.userId, false)).keys.toList.filter(r => !r._2).map(_._1)
          val temporaryList = subscribe.filterNot(_._1 == (roomInfo.userId, false)).keys.toList.filter(r => r._2).map(_._1)
          UserInfoDao.getUserDes(audienceList).onComplete {
            case Success(rst) =>
              val temporaryUserDesList = temporaryList.map(r => UserDes(r, s"guest_$r", Common.DefaultImg.headImg))
              dispatch(subscribe)(UpdateAudienceInfo(rst ++ temporaryUserDesList))
            case Failure(_) =>

          }
          idle(roomInfo, liveInfoMap, subscribe, startTime, false)

        case ActorProtocol.HostCloseRoom(roomId) =>
          dispatchTo(subscribe)(subscribe.filter(r => r._1 != (roomInfo.userId, false)).keys.toList, HostCloseRoom)
          if(roomState){
            log.debug(s"room-$roomId need close room, send msg to processor...")
            val pcRes = ProcessorClient.closeRoom(roomId)
            pcRes.map{
              case Right(rsp) =>
                val attendList = liveInfoMap.keys.toList
                roomManager ! RoomManager.DelaySeekRecord(roomInfo, roomId, attendList, startTime)
                ctx.self ! Stop

              case Left(e) =>
                log.error(s"close room error, $e")
                ctx.self ! Stop
            }
            switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))
          } else{
            Behaviors.stopped
          }

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg $x")
          Behaviors.same
      }
    }
  }

  private def busy()
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, b, durationOpt, timeOut) =>
          switchBehavior(ctx, name, b, durationOpt, timeOut)

        case Stop =>
          Behaviors.stopped

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
          Behaviors.stopped

        case x =>
          stashBuffer.stash(x)
          Behavior.same

      }
    }

  //websocket处理消息的函数
  /**
    * userActor --> roomManager --> roomActor --> userActor
    * roomActor
    * subscribers:map(userId,userActor)
    *
    *
    *
    **/
  private def handleWebSocketMsg(
    roomInfo: RoomInfo,
    subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]], //包括主持人在内的所有用户
    liveInfoMap: mutable.HashMap[Long, LiveInfo], //除主持人外所有用户在内的liveinfo
    startTime: Long,
    roomState: Boolean, //该房间是否开始录像
    dispatch: WsMsgRm => Unit,
    dispatchTo: (List[(Long, Boolean)], WsMsgRm) => Unit
  )
    (ctx: ActorContext[Command], userId: Long, roomId: Long, msg: WsMsgClient)
    (
      implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    msg match {
      case JoinAccept(`roomId`, userId4Audience, clientType, accept) =>
        log.info(s"${ctx.self.path} 接受加入会议请求，roomId=$roomId")
        if (accept && !roomState) {
          for {
            userInfoOpt <- UserInfoDao.searchById(userId4Audience)
            clientLiveInfo <- RtpClient.getLiveInfoFunc()
          } yield {
            clientLiveInfo match {
              case Right(rsp) =>
                if(rsp.errCode == 0){
                  log.info(s"user$userId4Audience 申请liveInfo成功" + rsp.liveInfo)
                  if (userInfoOpt.nonEmpty) {
                    liveInfoMap.put(userId4Audience, rsp.liveInfo)
                    val audienceInfo = AudienceInfo(userId4Audience, userInfoOpt.get.userName, userInfoOpt.get.headImg, rsp.liveInfo.liveId)
                    log.debug("向除该用户以外的参会者群发该用户信息....")
                    dispatchTo(subscribers.filter(_._1._1 != userId4Audience).keys.toList, AudienceJoinRsp(Some(audienceInfo)))
                    log.debug("向该用户发送JoinRsp...")
                    dispatchTo(List((userId4Audience, false)), JoinRsp(roomInfo.rtmp, Some(rsp.liveInfo), liveInfoMap.map(_._2.liveId).toList))
                  } else {
                    log.debug(s"${ctx.self.path} 错误的userId,可能是数据库里没有用户,userId=$userId4Audience")
                    dispatchTo(List((roomInfo.userId, false)), AudienceJoinError)
                    dispatchTo(List((userId4Audience, false)), JoinAccountError)
                  }
                } else{
                  log.debug(s"${ctx.self.path} 获取liveInfo失败，${rsp.msg}")
                  dispatchTo(List((roomInfo.userId, false)), AudienceJoinError)
                  dispatchTo(List((userId4Audience, false)), JoinInternalError)
                }
              case Left(e) =>
                log.debug(s"${ctx.self.path.name} join accept get liveInfo4Client left error:$e")
                dispatchTo(List((roomInfo.userId, false)), AudienceJoinError)
                dispatchTo(List((userId4Audience, false)), JoinInternalError)
            }
          }
        } else {
          dispatchTo(List((roomInfo.userId, false)), AudienceJoinError)
          dispatchTo(List((userId4Audience, false)), JoinRefused)
        }
        Behaviors.same

      case JoinReq(userId4Audience, `roomId`, clientType) =>
        UserInfoDao.searchById(userId4Audience).map { r =>
          if (r.nonEmpty) {
            dispatchTo(List((roomInfo.userId, false)), AudienceJoin(userId4Audience, r.get.userName, clientType))
          } else {
            log.debug(s"${ctx.self.path} 连线请求失败，用户id错误id=$userId4Audience in roomId=$roomId")
            dispatchTo(List((userId4Audience, false)), JoinAccountError)
          }
        }.recover {
          case e: Exception =>
            log.debug(s"${ctx.self.path} 连线请求失败，内部错误error=$e")
            dispatchTo(List((userId4Audience, false)), JoinInternalError)
        }
        Behaviors.same

      case AudienceShutJoin(`roomId`, `userId`) =>
        log.debug(s"${ctx.self.path} 退出了会议...")
        if(liveInfoMap.contains(userId)){
          liveInfoMap.remove(userId)
          dispatchTo(subscribers.filter(_._1._1 != userId).keys.toList, AuthProtocol.AudienceDisconnect(userId, liveInfoMap(userId).liveId))
        }
        Behaviors.same

      case ForceExit(userId4Member, userName4Member) =>
        if(liveInfoMap.contains(userId4Member)){
          log.debug(s"host force user-$userId4Member to leave")
          dispatchTo(subscribers.keys.toList, ForceExitRsp(userId4Member, userName4Member, liveInfoMap(userId4Member).liveId))
          if(roomState){
            ProcessorClient.forceExit(roomId, liveInfoMap(userId4Member).liveId, System.currentTimeMillis())
          } else{
            log.debug(s"room-$roomId has not started record...")
          }
          liveInfoMap.remove(userId4Member)
        } else{
          log.debug(s"host force user-$userId4Member to leave, but there is no user!")
        }
        Behaviors.same

      case BanOnMember(userId4Member, image, sound) =>
        if(liveInfoMap.contains(userId4Member)){
          log.debug(s"user-$userId4Member can't ${if(image) "show up" else ""} ${if(sound) "and speak" else ""}")
          dispatchTo(subscribers.filter(_._1._1 != userId).values.toList, BanOnMemberRsp(userId4Member, image, sound))
          if(roomState){
            ProcessorClient.banOnClient(roomId, liveInfoMap(userId4Member).liveId, image, sound)
          } else{
            log.debug(s"room-$roomId has not started record...")
          }
        } else{
          log.debug(s"host ban user-$userId4Member, but there is no user!")
        }
        Behaviors.same

      case CancelBan(userId4Member, image, sound) =>
        if(liveInfoMap.contains(userId4Member)){
          log.debug(s"user-$userId4Member begin to ${if(image) "show up" else ""} ${if(sound) "and speak" else ""}")
          dispatchTo(subscribers.filter(_._1._1 != userId).values.toList, CancelBanOnMemberRsp(userId4Member, image, sound))
          if(roomState){
            ProcessorClient.cancelBan(roomId, liveInfoMap(userId4Member).liveId, image, sound)
          } else{
            log.debug(s"room-$roomId has not started record...")
          }
        } else{
          log.debug(s"host cancel banning user-$userId4Member, but there is no user!")
        }
        Behaviors.same

      case StartMeetingRecord =>
        val pcRes = ProcessorClient.newConnect(roomId, roomInfo.rtmp.getOrElse(""), liveInfoMap.values.map(_.liveId).toList, startTime)
        pcRes.map{
          case Right(rsp) =>
            if(rsp.errCode == 0){
              ctx.self ! SwitchBehavior("idle", idle(roomInfo, liveInfoMap, subscribers, startTime, true))
            } else{
              log.debug(s"send msg to processor error, ${rsp.msg}")
              ctx.self ! SwitchBehavior("idle", idle(roomInfo, liveInfoMap, subscribers, startTime, false))
              dispatchTo(List((userId, false)), StartMeetingRsp("开启录像失败，processor暂未准备好"))
            }

          case Left(e) =>
            log.debug(s"send msg to processor error, $e")
            ctx.self ! SwitchBehavior("idle", idle(roomInfo, liveInfoMap, subscribers, startTime, false))
            dispatchTo(List((userId, false)), StartMeetingRsp("开启录像失败，解析问题"))

        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))


      case PingPackage =>
        Behaviors.same

      case x =>
        log.debug(s"${ctx.self.path} recv an unknown msg:$x")
        Behaviors.same
    }
  }

  private def dispatch(subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]])(msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.debug(s"${subscribers}分发消息：$msg")
    subscribers.values.foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
  }

  /**
    * subscribers:所有的订阅者
    * targetUserIdList：要发送的目标用户
    * msg：发送的消息
    **/
  private def dispatchTo(subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]])(targetUserIdList: List[(Long, Boolean)], msg: WsMsgRm)(implicit sendBuffer: MiddleBufferInJvm): Unit = {
    log.debug(s"${subscribers}定向分发消息：$msg")
    targetUserIdList.foreach { k =>
      subscribers.get(k).foreach(r => r ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()), msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
    }
  }


}
