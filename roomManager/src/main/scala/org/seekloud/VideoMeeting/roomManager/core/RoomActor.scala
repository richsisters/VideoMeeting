package org.seekloud.VideoMeeting.roomManager.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.{GetLiveInfoRsp, GetLiveInfoRsp4RM}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.{HostCloseRoom, _}
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager}
import org.seekloud.VideoMeeting.roomManager.common.Common
import org.seekloud.VideoMeeting.roomManager.core.RoomManager.GetRtmpLiveInfo
import org.seekloud.VideoMeeting.roomManager.models.dao.{RecordDao, StatisticDao, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol
import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol.BanOnAnchor
import org.seekloud.VideoMeeting.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
import org.seekloud.VideoMeeting.roomManager.utils.{DistributorClient, ProcessorClient, RtpClient}
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
  final case class UpdateRTMP(rtmp: String) extends Command

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
                    , None, Some(rsp.liveInfo.liveId))

                  DistributorClient.startPull(roomId, rsp.liveInfo.liveId).map {
                    case Right(r) =>
                      log.info(s"distributor startPull succeed, get live address: ${r.liveAdd}")
                      dispatchTo(subscribers)(List((userId, false)), StartLiveRsp(Some(rsp.liveInfo)))
                      roomInfo.mpd = Some(r.liveAdd)
                      val startTime = r.startTime
                        ctx.self ! SwitchBehavior("idle", idle(WholeRoomInfo(roomInfo, rsp.liveInfo), mutable.HashMap[Long, LiveInfo](), subscribers, mutable.Set[Long](), startTime, 0))

                    case Left(e) =>
                      log.error(s"distributor startPull error: $e")
                      dispatchTo(subscribers)(List((userId, false)), StartLiveRefused4LiveInfoError)
                      ctx.self ! SwitchBehavior("init", init(roomId, subscribers))
                  }

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
          idle(WholeRoomInfo(roomInfo, LiveInfo("", "")),  mutable.HashMap[Long, LiveInfo](), subscribers, mutable.Set[Long](), System.currentTimeMillis(), 0)

        case ActorProtocol.AddUserActor4Test(userId, roomId, userActor) =>
          subscribers.put((userId, false), userActor)
          Behaviors.same

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$x in init state...")
          Behaviors.same
      }
    }
  }

  private def idle(
    wholeRoomInfo: WholeRoomInfo, //包含该房间主持人的LiveInfo
    liveInfoMap: mutable.HashMap[Long, LiveInfo], //除主持人外的所有参会者的liveInfo
    subscribe: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]], //需要区分订阅的用户的身份，注册用户还是临时用户(uid,是否是临时用户true:是)
    liker: mutable.Set[Long],
    startTime: Long,
    totalView: Int
  )
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer: MiddleBufferInJvm
    ): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case ActorProtocol.AddUserActor4Test(userId, roomId, userActor) =>
          subscribe.put((userId, false), userActor)
          Behaviors.same

        case GetRoomInfo(replyTo) =>
          replyTo ! wholeRoomInfo.roomInfo
          Behaviors.same

        case UpdateRTMP(rtmp) =>
          val newRoomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(rtmp = Some(rtmp)))
          log.debug(s"${ctx.self.path} 更新liveId=$rtmp,更新后的liveId=${newRoomInfo.roomInfo.rtmp}")
          idle(newRoomInfo, liveInfoMap, subscribe, liker, startTime, totalView)

        case ActorProtocol.WebSocketMsgWithActor(userId, roomId, wsMsg) =>
          handleWebSocketMsg(wholeRoomInfo, subscribe, liveInfoMap, liker, startTime, totalView, dispatch(subscribe), dispatchTo(subscribe))(ctx, userId, roomId, wsMsg)

        case GetRtmpLiveInfo(_, replyTo) =>
          log.debug(s"room${wholeRoomInfo.roomInfo.roomId}获取liveId成功")
          replyTo ! GetLiveInfoRsp4RM(Some(wholeRoomInfo.liveInfo))
          Behaviors.same

        case ActorProtocol.UpdateSubscriber(join, roomId, userId, temporary, userActorOpt) =>
          var viewNum = totalView
          //虽然房间存在，但其实主播已经关闭房间，这时的startTime=-1
          //向所有人发送主播已经关闭房间的消息
          log.info(s"-----roomActor get UpdateSubscriber id: $roomId")
          if (startTime == -1) {
            dispatchTo(subscribe)(List((userId, temporary)), NoAuthor)
          }
          else {
            if (join == Common.Subscriber.join) {
              viewNum += 1
              log.debug(s"${ctx.self.path}新用户加入房间roomId=$roomId,userId=$userId")
              subscribe.put((userId, temporary), userActorOpt.get)
            } else if (join == Common.Subscriber.left) {
              log.debug(s"${ctx.self.path}用户离开房间roomId=$roomId,userId=$userId")
              subscribe.remove((userId, temporary))
              if(liveInfoMap.contains(userId)){
                wholeRoomInfo.roomInfo.rtmp match {
                  case  Some(v) =>
                    if(v != wholeRoomInfo.liveInfo.liveId){
                      val hostLive = wholeRoomInfo.liveInfo
                      //TODO 当一个用户发起离开房间请求后，整个房间关闭，应该更改为其余人继续开会
                      liveInfoMap.clear()
                     // ProcessorClient.closeRoom(roomId)
                      ctx.self ! UpdateRTMP(hostLive.liveId)
                      dispatch(subscribe)(AuthProtocol.AudienceDisconnect(hostLive.liveId))
                      dispatch(subscribe)(RcvComment(-1l, "", s"the audience has shut the join in room $roomId"))
                    }

                  case None =>
                    log.debug("no host liveId when audience left room")
                }
              }
            }
          }
          //所有的注册用户
          val audienceList = subscribe.filterNot(_._1 == (wholeRoomInfo.roomInfo.userId, false)).keys.toList.filter(r => !r._2).map(_._1)
          val temporaryList = subscribe.filterNot(_._1 == (wholeRoomInfo.roomInfo.userId, false)).keys.toList.filter(r => r._2).map(_._1)
          UserInfoDao.getUserDes(audienceList).onComplete {
            case Success(rst) =>
              val temporaryUserDesList = temporaryList.map(r => UserDes(r, s"guest_$r", Common.DefaultImg.headImg))
              dispatch(subscribe)(UpdateAudienceInfo(rst ++ temporaryUserDesList))
            case Failure(_) =>

          }
          idle(wholeRoomInfo, liveInfoMap, subscribe, liker, startTime, viewNum)

        case ActorProtocol.HostCloseRoom(roomId) =>
          log.debug(s"${ctx.self.path} host close the room")
          wholeRoomInfo.roomInfo.rtmp match {
            case Some(v) =>
              if(v != wholeRoomInfo.liveInfo.liveId){
                ProcessorClient.closeRoom(wholeRoomInfo.roomInfo.roomId)
              }
            case None =>
          }
          if (startTime != -1l) {
            log.debug(s"${ctx.self.path} 主播向distributor发送finishPull请求")
            DistributorClient.finishPull(wholeRoomInfo.liveInfo.liveId)
            roomManager ! RoomManager.DelaySeekRecord(wholeRoomInfo, totalView, roomId, startTime, wholeRoomInfo.liveInfo.liveId)
          }
          dispatchTo(subscribe)(subscribe.filter(r => r._1 != (wholeRoomInfo.roomInfo.userId, false)).keys.toList, HostCloseRoom())
          Behaviors.stopped

        case ActorProtocol.StartLiveAgain(roomId) =>
          log.debug(s"${ctx.self.path} the room actor has been exist,the host restart the room")
          for {
            data <- RtpClient.getLiveInfoFunc()
          } yield {
            data match {
              case Right(rsp) =>
                DistributorClient.startPull(roomId, rsp.liveInfo.liveId).map {
                  case Right(r) =>
                    log.info("distributor startPull succeed")
                    val startTime = r.startTime
                    val newWholeRoomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(mpd = Some(r.liveAdd), rtmp = Some(rsp.liveInfo.liveId)), liveInfo = rsp.liveInfo)
                    dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId, false)), StartLiveRsp(Some(rsp.liveInfo)))
                    ctx.self ! SwitchBehavior("idle", idle(newWholeRoomInfo, liveInfoMap, subscribe, liker, startTime, 0))
                  case Left(e) =>
                    log.error(s"distributor startPull error: $e")
                    val newWholeRoomInfo = wholeRoomInfo.copy(liveInfo = rsp.liveInfo)
                    dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId, false)), StartLiveRsp(Some(rsp.liveInfo)))
                    ctx.self ! SwitchBehavior("idle", idle(newWholeRoomInfo, liveInfoMap, subscribe, liker, startTime, 0))
                }


              case Left(str) =>
                log.debug(s"${ctx.self.path} 重新开始直播失败=$str")
                dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId, false)), StartLiveRefused4LiveInfoError)
                ctx.self ! ActorProtocol.HostCloseRoom(wholeRoomInfo.roomInfo.roomId)
                ctx.self ! SwitchBehavior("idle", idle(wholeRoomInfo, liveInfoMap, subscribe, liker, startTime, 0))
            }
          }
          switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

        case BanOnAnchor(roomId) =>
          //ProcessorClient.closeRoom(wholeRoomInfo.roomInfo.roomId)
          dispatchTo(subscribe)(subscribe.filter(r => r._1 != (wholeRoomInfo.roomInfo.userId, false)).keys.toList, HostCloseRoom())
          dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId, false)), AuthProtocol.BanOnAnchor)
          Behaviors.stopped

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
    wholeRoomInfo: WholeRoomInfo,
    subscribers: mutable.HashMap[(Long, Boolean), ActorRef[UserActor.Command]], //包括主持人在内的所有用户
    liveInfoMap: mutable.HashMap[Long, LiveInfo], //除主持人外所有用户在内的liveinfo
    liker: mutable.Set[Long],
    startTime: Long,
    totalView: Int,
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
      case ChangeLiveMode(isConnectOpen, aiMode, screenLayout) =>
        val connect = isConnectOpen match {
          case Some(v) => v
          case None => wholeRoomInfo.isJoinOpen
        }
        val liveList = wholeRoomInfo.liveInfo.liveId :: liveInfoMap.toList.map(r => r._2.liveId)
        if (aiMode.isEmpty && screenLayout.nonEmpty) {
          wholeRoomInfo.layout = screenLayout.get
        } else if (aiMode.nonEmpty && screenLayout.isEmpty) {
          wholeRoomInfo.aiMode = aiMode.get
        } else if (aiMode.nonEmpty && screenLayout.nonEmpty) {
          wholeRoomInfo.layout = screenLayout.get
          wholeRoomInfo.aiMode = aiMode.get
        }
        if (!(aiMode.isEmpty && screenLayout.isEmpty)) {
          changeMode(ctx, userId, dispatchTo)(roomId, liveList, wholeRoomInfo.layout, wholeRoomInfo.aiMode, 0l)
        } else {
          dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), ChangeModeRsp())
        }
        idle(wholeRoomInfo.copy(isJoinOpen = connect), liveInfoMap, subscribers, liker, startTime, totalView)

      case JoinAccept(`roomId`, userId4Audience, clientType, accept) =>
        log.info(s"${ctx.self.path} 接受加入会议请求，roomId=$roomId")
        if (accept) {
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
                    val audienceInfo = AudienceInfo(userId4Audience, userInfoOpt.get.userName, userInfoOpt.get.headImg, rsp.liveInfo.liveId )
                    dispatch(RcvComment(-1l, "", s"user:$userId join in room:$roomId")) //群发评论
                    log.debug("群发观众信息....")
                    dispatch(AudienceJoinRsp(Some(audienceInfo)))
                    log.debug("向该用户发送JoinRsp...")
                    dispatchTo(List((userId4Audience, false)), JoinRsp(Some(wholeRoomInfo.liveInfo.liveId), Some(rsp.liveInfo)))
                  } else {
                    log.debug(s"${ctx.self.path} 错误的userId,可能是数据库里没有用户,userId=$userId4Audience")
                    dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), AudienceJoinError)
                  }
                } else{
                  log.debug(s"${ctx.self.path} 获取liveInfo失败，${rsp.msg}")
                  dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), AudienceJoinError)
                }


              case Left(e) =>
                log.debug(s"${ctx.self.path.name} join accept get liveInfo4Client left error:$e")
                dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), AudienceJoinError)
            }
          }
        } else {
          dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), AudienceJoinRsp(None))
          dispatchTo(List((userId4Audience, false)), JoinRefused)
        }

        Behaviors.same

      case StartMeeting(`roomId`) =>
        log.info(s"${ctx.self.path}发起了开始会议请求，roomId=$roomId")
        RtpClient.getLiveInfoFunc().map{
          case Right(rsp) =>
            if(rsp.errCode == 0){
              log.info(s"roomId=$roomId 开始会议！")
              val liveIdHost = wholeRoomInfo.liveInfo.liveId
              val liveIdAudience = liveInfoMap.values.map(_.liveId).toList
              val liveIdMix = rsp.liveInfo
              DistributorClient.startPull(roomId, liveIdMix.liveId)
              ProcessorClient.newConnect(roomId, liveIdHost, liveIdAudience, liveIdMix.liveId, liveIdMix.liveCode, wholeRoomInfo.layout)
              ctx.self ! UpdateRTMP(liveIdMix.liveId)
              dispatch(RcvComment(-1l, "", s"roomId$roomId start the meeting!"))
              log.debug("send start meeting rsp...")
              dispatch(StartMeetingRsp(roomId, liveIdMix.liveId))
            } else{
              log.error(s"start a meeting error, error:${rsp.msg}")
              dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), StartMeetingError(rsp.msg))
            }

          case Left(e) =>
            log.error(s"parse json error, $e")
            dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), StartMeetingError(s"$e"))
        }
        Behaviors.same


      case HostShutJoin(`roomId`) =>
        log.debug(s"${ctx.self.path} the host has shut the join in room$roomId")
        liveInfoMap.clear()
        val liveIdHost = wholeRoomInfo.liveInfo.liveId
        ctx.self ! UpdateRTMP(liveIdHost)
        ProcessorClient.closeRoom(roomId)
        dispatch(HostDisconnect(liveIdHost))
        dispatch(RcvComment(-1l, "", s"the host has shut the join in room $roomId"))
        Behaviors.same

      case ModifyRoomInfo(roomName, roomDes) =>
        val roomInfo = if (roomName.nonEmpty && roomDes.nonEmpty) {
          wholeRoomInfo.roomInfo.copy(roomName = roomName.get, roomDes = roomDes.get)
        } else if (roomName.nonEmpty) {
          wholeRoomInfo.roomInfo.copy(roomName = roomName.get)
          wholeRoomInfo.roomInfo.copy(roomName = roomName.get)
        } else if (roomDes.nonEmpty) {
          wholeRoomInfo.roomInfo.copy(roomDes = roomDes.get)
        } else {
          wholeRoomInfo.roomInfo
        }
        val info = WholeRoomInfo(roomInfo, wholeRoomInfo.liveInfo, wholeRoomInfo.isJoinOpen, wholeRoomInfo.layout, wholeRoomInfo.aiMode)
        log.debug(s"${ctx.self.path} modify the room info$info")
        dispatch(UpdateRoomInfo2Client(roomInfo.roomName, roomInfo.roomDes))
        dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), ModifyRoomRsp())
        idle(info, liveInfoMap, subscribers, liker, startTime, totalView)


      case HostStopPushStream(`roomId`) =>
        //val liveId = wholeRoomInfo.roomInfo.rtmp.get
       /* wholeRoomInfo.roomInfo.rtmp match {
          case Some(v) =>
          case None =>
        }*/

        log.debug(s"${ctx.self.path} host stop stream in room${wholeRoomInfo.roomInfo.roomId},name=${wholeRoomInfo.roomInfo.roomName}")
        //前端需要自行处理主播主动断流的情况，后台默认连线者也会断开
        dispatch(HostStopPushStream2Client)
        wholeRoomInfo.roomInfo.rtmp match {
          case Some(v) =>
            if(v != wholeRoomInfo.liveInfo.liveId)
              ProcessorClient.closeRoom(roomId)
            log.debug(s"roomId:$roomId 主播停止推流，向distributor发送finishpull消息")
            DistributorClient.finishPull(v)
            if (startTime != -1l) {
              roomManager ! RoomManager.DelaySeekRecord(wholeRoomInfo, totalView, roomId, startTime, v)
            }
          case None =>
        }
//        if (wholeRoomInfo.roomInfo.rtmp.get != liveInfoMap(Role.host)(wholeRoomInfo.roomInfo.userId).liveId)
//          ProcessorClient.closeRoom(roomId)
        liveInfoMap.clear()

        val newroomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(rtmp = None, mpd = None))
        log.debug(s"${ctx.self.path} 主播userId=${userId}已经停止推流，更新房间信息，liveId=${newroomInfo.roomInfo.rtmp}")
        subscribers.get((wholeRoomInfo.roomInfo.userId, false)) match {
          case Some(hostActor) =>
            idle(newroomInfo, liveInfoMap, mutable.HashMap((wholeRoomInfo.roomInfo.userId, false) -> hostActor), mutable.Set[Long](), -1l, totalView)
          case None =>
            idle(newroomInfo, liveInfoMap, mutable.HashMap.empty[(Long, Boolean), ActorRef[UserActor.Command]], mutable.Set[Long](), -1l, totalView)
        }

      case JoinReq(userId4Audience, `roomId`, clientType) =>
        if (wholeRoomInfo.isJoinOpen) {
          UserInfoDao.searchById(userId4Audience).map { r =>
            if (r.nonEmpty) {
              dispatchTo(List((wholeRoomInfo.roomInfo.userId, false)), AudienceJoin(userId4Audience, r.get.userName, clientType))
            } else {
              log.debug(s"${ctx.self.path} 连线请求失败，用户id错误id=$userId4Audience in roomId=$roomId")
              dispatchTo(List((userId4Audience, false)), JoinAccountError)
            }
          }.recover {
            case e: Exception =>
              log.debug(s"${ctx.self.path} 连线请求失败，内部错误error=$e")
              dispatchTo(List((userId4Audience, false)), JoinInternalError)
          }
        } else {
          dispatchTo(List((userId4Audience, false)), JoinInvalid)
        }
        Behaviors.same

      case AudienceShutJoin(`roomId`, `userId`) =>
        log.debug(s"${ctx.self.path} the audience connection has been shut")
        //TODO 目前是某个观众退出则关闭会议，应该修改为不关闭整个会议
        liveInfoMap.clear()
        ctx.self ! UpdateRTMP(wholeRoomInfo.liveInfo.liveId)
        //            val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
        //            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId, liveList, wholeRoomInfo.layout, wholeRoomInfo.aiMode, 0l)
        dispatch(AuthProtocol.AudienceDisconnect(wholeRoomInfo.liveInfo.liveId))
        dispatch(RcvComment(-1l, "", s"the audience has shut the join in room $roomId"))
        Behaviors.same

      case JudgeLike(`userId`, `roomId`) =>
        dispatchTo(List((userId, false)), JudgeLikeRsp(liker.contains(userId)))
        Behaviors.same

      case Comment(`userId`, `roomId`, comment, color, extension) =>
        UserInfoDao.searchById(userId).onComplete {
          case Success(value) =>
            value match {
              case Some(v) =>
                dispatch(RcvComment(userId, v.userName, comment, color, extension))
              case None =>
                log.debug(s"${ctx.self.path.name} the database doesn't have the user")
            }
            ctx.self ! SwitchBehavior("idle", idle(wholeRoomInfo, liveInfoMap, subscribers, liker, startTime, totalView))
          case Failure(e) =>
            log.debug(s"s${ctx.self.path.name} the search by userId error:$e")
            ctx.self ! SwitchBehavior("idle", idle(wholeRoomInfo, liveInfoMap, subscribers, liker, startTime, totalView))
        }
        switchBehavior(ctx, "busy", busy(), InitTime, TimeOut("busy"))

      case ForceExit(userId4Member) =>
        if(liveInfoMap.contains(userId4Member)){
          log.debug(s"host force user-$userId4Member to leave")
          ProcessorClient.forceExit(roomId, liveInfoMap(userId4Member).liveId, System.currentTimeMillis())
          liveInfoMap.remove(userId4Member)
          dispatchTo(List((userId4Member, false)), ForceExitRsp)
        } else{
          log.debug(s"host force user-$userId4Member to leave, but there is no user!")
        }
        Behaviors.same

      case BanOnMember(userId4Member, image, sound) =>
        if(liveInfoMap.contains(userId4Member)){
          log.debug(s"user-$userId4Member can't ${if(image) "show up" else ""} ${if(sound) "and speak" else ""}")
          ProcessorClient.banOnClient(roomId, liveInfoMap(userId4Member).liveId, image, sound)
          dispatchTo(List((userId4Member, false)), BanOnMemberRsp(image, sound))
        } else{
          log.debug(s"host ban user-$userId4Member, but there is no user!")
        }
        Behaviors.same

      case CancelBan(userId4Member, image, sound) =>
        if(liveInfoMap.contains(userId4Member)){
          log.debug(s"user-$userId4Member begin to ${if(image) "show up" else ""} ${if(sound) "and speak" else ""}")
          ProcessorClient.cancelBan(roomId, liveInfoMap(userId4Member).liveId, image, sound)
          dispatchTo(List((userId4Member, false)), CancelBanOnMemberRsp(image, sound))
        } else{
          log.debug(s"host cancel banning user-$userId4Member, but there is no user!")
        }
        Behaviors.same

      case PingPackage =>
        Behaviors.same

      case x =>
        log.debug(s"${ctx.self.path} recv an unknown msg:$x")
        Behaviors.same
    }
  }

  private def changeMode(ctx: ActorContext[RoomActor.Command], anchorUid: Long, dispatchTo: (List[(Long, Boolean)], WsMsgRm) => Unit)(roomId: Long, liveIdList: List[String], screenLayout: Int, aiMode: Int, startTime: Long) = {
//    ProcessorClient.updateRoomInfo(roomId, screenLayout).map {
//      case Right(rsp) =>
//        log.debug(s"${ctx.self.path} modify the mode success")
//        dispatchTo(List((anchorUid, false)), ChangeModeRsp())
//      case Left(error) =>
//        log.debug(s"${ctx.self.path} there is some error:$error")
//        dispatchTo(List((anchorUid, false)), ChangeModeError)
//    }
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
