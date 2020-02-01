package org.seekloud.VideoMeeting.roomManager.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.GetLiveInfoRsp4RM
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.{HostCloseRoom, _}
import org.seekloud.VideoMeeting.roomManager.Boot.executor
import org.seekloud.VideoMeeting.roomManager.common.AppSettings.{distributorIp, distributorPort}
import org.seekloud.VideoMeeting.roomManager.common.Common
import org.seekloud.VideoMeeting.roomManager.common.Common.{Like, Role}
import org.seekloud.VideoMeeting.roomManager.core.RoomManager.GetRtmpLiveInfo
import org.seekloud.VideoMeeting.roomManager.models.dao.{StatisticDao, RecordDao, UserInfoDao}
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

  final case class TestRoom(roomInfo:RoomInfo) extends Command

  final case class GetRoomInfo(replyTo:ActorRef[RoomInfo]) extends Command//考虑后续房间的建立不依赖ws
  final case class UpdateRTMP(rtmp:String) extends Command
  private final val InitTime = Some(5.minutes)

  def create(roomId:Long):Behavior[Command] = {
    Behaviors.setup[Command]{ctx =>
      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
      log.debug(s"${ctx.self.path} setup")
      Behaviors.withTimers[Command]{implicit timer =>
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
        val subscribers = mutable.HashMap.empty[(Long,Boolean),ActorRef[UserActor.Command]]
        init(roomId,subscribers)
      }
    }
  }

  private def init(
                    roomId:Long,
                    subscribers: mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]],
                    roomInfoOpt: Option[RoomInfo] = None
                  )
                  (
                    implicit stashBuffer:StashBuffer[Command],
                    timer: TimerScheduler[Command],
                    sendBuffer:MiddleBufferInJvm
                  ):Behavior[Command] = {
    Behaviors.receive[Command]{(ctx,msg) =>
      msg match{
        case ActorProtocol.StartRoom4Anchor(userId,`roomId`,actor) =>
          log.debug(s"${ctx.self.path} 用户id=$userId 开启了的新的直播间id=$roomId")
          subscribers.put((userId,false),actor)
          for{
            data <- RtpClient.getLiveInfoFunc()
            userTableOpt <- UserInfoDao.searchById(userId)
            rtmpOpt <- ProcessorClient.getmpd(roomId)
          }yield {
            data match{
              case Right(rsp) =>
                if(userTableOpt.nonEmpty){
                  val roomInfo = rtmpOpt match {
                    case Right(value) =>
                      if(value.errCode == 0){
                        log.debug(s"${ctx.self.path} 获取rtmp成功，rtmp=${value.rtmp}")
                        RoomInfo(roomId, s"${userTableOpt.get.userName}的直播间", "", userTableOpt.get.uid,userTableOpt.get.userName,
                          UserInfoDao.getHeadImg(userTableOpt.get.headImg),
                          UserInfoDao.getHeadImg(userTableOpt.get.coverImg),0,0,
                          Some(Common.getMpdPath(roomId)),
                          Some(value.rtmp)
                        )
                      }else{
                        log.debug(s"${ctx.self.path} 获取rtmp失败，err=${value.msg}")
                        RoomInfo(roomId, s"${userTableOpt.get.userName}的直播间", "", userTableOpt.get.uid,userTableOpt.get.userName,
                          UserInfoDao.getHeadImg(userTableOpt.get.headImg),
                          UserInfoDao.getHeadImg(userTableOpt.get.coverImg),0,0,
                          Some(Common.getMpdPath(roomId))
                        )
                      }
                    case Left(error) =>
                      RoomInfo(roomId, s"${userTableOpt.get.userName}的直播间", "", userTableOpt.get.uid,userTableOpt.get.userName,
                      UserInfoDao.getHeadImg(userTableOpt.get.headImg),
                      UserInfoDao.getHeadImg(userTableOpt.get.coverImg),0,0,
                      Some(Common.getMpdPath(roomId))
                    )
                  }
                  val startTime = System.currentTimeMillis()
                  ProcessorClient.updateRoomInfo(roomId,List(rsp.liveInfo.liveId),ScreenLayout.EQUAL,AiMode.close,startTime)
                  dispatchTo(subscribers)(List((userId,false)),StartLiveRsp(Some(rsp.liveInfo)))
                  ctx.self ! SwitchBehavior("idle",idle(WholeRoomInfo(roomInfo),mutable.HashMap(Role.host -> mutable.HashMap(userId -> rsp.liveInfo)),subscribers,mutable.Set[Long](),startTime,0))
                }else{
                  log.debug(s"${ctx.self.path} 开始直播被拒绝，数据库中没有该用户的数据，userId=$userId")
                  dispatchTo(subscribers)(List((userId,false)),StartLiveRefused)
                  ctx.self ! SwitchBehavior("init",init(roomId,subscribers))
                }
              case Left(error) =>
                log.debug(s"${ctx.self.path} 开始直播被拒绝，请求rtp server解析失败，error:$error")
                dispatchTo(subscribers)(List((userId,false)),StartLiveRefused)
                ctx.self ! SwitchBehavior("init",init(roomId,subscribers))
            }
          }
          switchBehavior(ctx,"busy",busy(),InitTime,TimeOut("busy"))

        case GetRoomInfo(replyTo) =>
          if(roomInfoOpt.nonEmpty){
            replyTo ! roomInfoOpt.get
          }
          Behaviors.same

        case TestRoom(roomInfo) =>
          //仅用户测试使用空房间
          idle(WholeRoomInfo(roomInfo),mutable.HashMap[Int,mutable.HashMap[Long,LiveInfo]](),subscribers,mutable.Set[Long](),System.currentTimeMillis(),0)

        case ActorProtocol.AddUserActor4Test(userId,roomId,userActor) =>
          subscribers.put((userId,false),userActor)
          Behaviors.same

        case x =>
          log.debug(s"${ctx.self.path} recv an unknown msg:$x in init state...")
          Behaviors.same
      }
    }
  }

  private def idle(
                    wholeRoomInfo: WholeRoomInfo,//可以考虑是否将主路的liveinfo加在这里，单独存一份连线者的liveinfo列表
                    liveInfoMap: mutable.HashMap[Int,mutable.HashMap[Long,LiveInfo]],
                    subscribe:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]],//需要区分订阅的用户的身份，注册用户还是临时用户(uid,是否是临时用户true:是)
                    liker:mutable.Set[Long],
                    startTime:Long,
                    totalView:Int,
                    isJoinOpen:Boolean = false,
                  )
    ( implicit stashBuffer:StashBuffer[Command],
      timer: TimerScheduler[Command],
      sendBuffer:MiddleBufferInJvm
    ):Behavior[Command] = {
    Behaviors.receive[Command]{(ctx,msg) =>
      msg match {
        case ActorProtocol.AddUserActor4Test(userId,roomId,userActor) =>
          subscribe.put((userId,false),userActor)
          Behaviors.same

        case GetRoomInfo(replyTo) =>
          replyTo ! wholeRoomInfo.roomInfo
          Behaviors.same

        case UpdateRTMP(rtmp) =>
          val newRoomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(rtmp = Some(rtmp)))
          log.debug(s"${ctx.self.path} 更新liveId=$rtmp,更新后的liveId=${newRoomInfo.roomInfo.rtmp}")
          idle(newRoomInfo,liveInfoMap,subscribe,liker,startTime,totalView,isJoinOpen)

        case ActorProtocol.WebSocketMsgWithActor(userId,roomId,wsMsg) =>
          handleWebSocketMsg(wholeRoomInfo,subscribe,liveInfoMap,liker,startTime,totalView,isJoinOpen,dispatch(subscribe),dispatchTo(subscribe))(ctx,userId,roomId,wsMsg)

        case GetRtmpLiveInfo(_, replyTo) =>
          log.debug(s"room${wholeRoomInfo.roomInfo.roomId}获取liveId成功")
          liveInfoMap.get(Role.host) match{
            case Some(value) =>
              replyTo ! GetLiveInfoRsp4RM(Some(value.values.head))
            case None =>
              log.debug(s"${ctx.self.path} no host live info,roomId=${wholeRoomInfo.roomInfo.roomId}")
              replyTo ! GetLiveInfoRsp4RM(None)

          }
          Behaviors.same

        case ActorProtocol.UpdateSubscriber(join,roomId,userId,temporary,userActorOpt) =>
          var viewNum = totalView
          if(join == Common.Subscriber.join){
            // todo observe event
            viewNum += 1
            log.debug(s"${ctx.self.path}新用户加入房间roomId=$roomId,userId=$userId")
            subscribe.put((userId,temporary),userActorOpt.get)
          }else if (join == Common.Subscriber.left){
            // todo observe event
            log.debug(s"${ctx.self.path}用户离开房间roomId=$roomId,userId=$userId")
            subscribe.remove((userId,temporary))
          }
          //所有的注册用户
          val audienceList = subscribe.filterNot(_._1 == (wholeRoomInfo.roomInfo.userId,false)).keys.toList.filter(r => !r._2).map(_._1)
          val temporaryList = subscribe.filterNot(_._1 == (wholeRoomInfo.roomInfo.userId,false)).keys.toList.filter(r => r._2).map(_._1)
          UserInfoDao.getUserDes(audienceList).onComplete{
            case Success(rst)=>
              val temporaryUserDesList = temporaryList.map(r => UserDes(r,s"guest_$r",Common.DefaultImg.headImg))
              dispatch(subscribe)(UpdateAudienceInfo(rst ++ temporaryUserDesList))
            case Failure(_) =>

          }
          wholeRoomInfo.roomInfo.observerNum = subscribe.size - 1
          idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,viewNum,isJoinOpen)

        case ActorProtocol.HostCloseRoom(roomId) =>
          log.debug(s"${ctx.self.path} host close the room")
          ProcessorClient.closeRoom(wholeRoomInfo.roomInfo.roomId)
          if(startTime != -1l){
            DistributorClient.seekRecord(roomId,startTime).onComplete{
              case Success(v) =>
                v match{
                  case Right(rsp) =>
                    log.debug(s"${ctx.self.path}获取录像时长为duration=${rsp.duration}")
                    RecordDao.addRecord(wholeRoomInfo.roomInfo.roomId,wholeRoomInfo.roomInfo.roomName,
                      wholeRoomInfo.roomInfo.roomDes,startTime,
                      UserInfoDao.getVideoImg(wholeRoomInfo.roomInfo.coverImgUrl),
                      totalView,wholeRoomInfo.roomInfo.like,rsp.duration)
                  case Left(err) =>
                    log.debug(s"${ctx.self.path} 查询录像文件信息失败,error:$err")
                }
              case Failure(error) =>
                log.debug(s"${ctx.self.path} 查询录像文件失败,error:$error")
            }
          }
          dispatchTo(subscribe)(subscribe.filter(r => r._1 != (wholeRoomInfo.roomInfo.userId,false)).keys.toList,HostCloseRoom())
          Behaviors.stopped

        case ActorProtocol.StartLiveAgain(roomId)  =>
          log.debug(s"${ctx.self.path} the room actor has been exist,the host restart the room")
          for{
            data <- RtpClient.getLiveInfoFunc()
            rtmpOpt <- ProcessorClient.getmpd(roomId)
          }yield {
            data match{
              case Right(rsp) =>
                liveInfoMap.put(Role.host,mutable.HashMap(wholeRoomInfo.roomInfo.userId -> rsp.liveInfo))
                val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
                val startTime = System.currentTimeMillis()
                ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,startTime)
                val newWholeRoomInfo = rtmpOpt match {
                  case Right(rtmpRsp) =>wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(observerNum = 0,like = 0,rtmp = Some(rtmpRsp.rtmp)))
                  case Left(error) => wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(observerNum = 0,like = 0))
                }
                dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId,false)),StartLiveRsp(Some(rsp.liveInfo)))
                ctx.self !SwitchBehavior("idle",idle(newWholeRoomInfo,liveInfoMap,subscribe,liker,startTime,0,isJoinOpen))
              case Left(str) =>
                log.debug(s"${ctx.self.path} 重新开始直播失败=$str")
                dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId,false)),StartLiveRefused4LiveInfoError)
                ctx.self ! ActorProtocol.HostCloseRoom(wholeRoomInfo.roomInfo.roomId)
                ctx.self !SwitchBehavior("idle",idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,0,isJoinOpen))
            }
          }
          switchBehavior(ctx,"busy",busy(),InitTime,TimeOut("busy"))

        case BanOnAnchor(roomId) =>
          ProcessorClient.closeRoom(wholeRoomInfo.roomInfo.roomId)
          dispatchTo(subscribe)(subscribe.filter(r => r._1 != (wholeRoomInfo.roomInfo.userId,false)).keys.toList,HostCloseRoom())
          dispatchTo(subscribe)(List((wholeRoomInfo.roomInfo.userId,false)),AuthProtocol.BanOnAnchor)
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
                    sendBuffer:MiddleBufferInJvm
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
    * */
  private def handleWebSocketMsg(
                                  wholeRoomInfo:WholeRoomInfo,
                                  subscribers:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]],//包括主播在内的所有用户
                                  liveInfoMap:mutable.HashMap[Int,mutable.HashMap[Long,LiveInfo]],//"audience"/"anchor"->Map(userId->LiveInfo)
                                  liker:mutable.Set[Long],
                                  startTime:Long,
                                  totalView:Int,
                                  isJoinOpen:Boolean = false,
                                  dispatch:WsMsgRm => Unit,
                                  dispatchTo:(List[(Long,Boolean)],WsMsgRm) => Unit
                                )
                                (ctx:ActorContext[Command],userId:Long,roomId:Long,msg:WsMsgClient)
                                (
                                  implicit stashBuffer:StashBuffer[Command],
                                  timer: TimerScheduler[Command],
                                  sendBuffer:MiddleBufferInJvm
                                ):Behavior[Command] = {
    msg match{
      case ChangeLiveMode(isConnectOpen,aiMode,screenLayout) =>
        val connect = isConnectOpen match{
          case Some(v) =>v
          case None =>isJoinOpen
        }
        val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
        if(aiMode.isEmpty && screenLayout.nonEmpty){
          wholeRoomInfo.layout = screenLayout.get
        }else if(aiMode.nonEmpty && screenLayout.isEmpty){
          wholeRoomInfo.aiMode = aiMode.get
        }else if(aiMode.nonEmpty && screenLayout.nonEmpty) {
          wholeRoomInfo.layout = screenLayout.get
          wholeRoomInfo.aiMode = aiMode.get
        }
        if(!(aiMode.isEmpty &&screenLayout.isEmpty)){
          changeMode(ctx,userId,dispatchTo)(roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
        }else{
          dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),ChangeModeRsp())
        }
        idle(wholeRoomInfo,liveInfoMap,subscribers,liker,startTime,totalView,connect)

      case JoinAccept(`roomId`,userId4Audience,clientType,accept) =>
        log.debug(s"${ctx.self.path} 接受连线者请求，roomId=$roomId")
        if(accept){
          for{
            userInfoOpt <- UserInfoDao.searchById(userId4Audience)
            data <- RtpClient.getLiveInfoFunc()
          }yield {
            data match{
              case Right(rsp) =>
                if(userInfoOpt.nonEmpty){
                  liveInfoMap.get(Role.host) match{
                    case Some(value) =>
                      val liveIdHost = value.get(wholeRoomInfo.roomInfo.userId)
                      if(liveIdHost.nonEmpty){
                        liveInfoMap.get(Role.audience) match{
                          case Some(value4Audience) =>
                            value4Audience.put(userId4Audience,rsp.liveInfo)
                            liveInfoMap.put(Role.audience,value4Audience)
                          case None =>
                            liveInfoMap.put(Role.audience,mutable.HashMap(userId4Audience -> rsp.liveInfo))
                        }
                        val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
                        log.debug(s"${ctx.self.path} 更新房间信息，连线者liveIds=${liveList}")
                        ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)

                        val audienceInfo = AudienceInfo(userId4Audience,userInfoOpt.get.userName,rsp.liveInfo.liveId)
                        dispatch(RcvComment(-1l,"",s"user:$userId join in room:$roomId"))//群发评论
                        dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),AudienceJoinRsp(Some(audienceInfo)))
                        dispatchTo(List((userId4Audience,false)),JoinRsp(Some(liveIdHost.get.liveId),Some(rsp.liveInfo)))
                      }else{
                        log.debug(s"${ctx.self.path} 没有主播的liveId,roomId=$roomId")
                        dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),AudienceJoinError)

                      }
                    case None =>
                      log.debug(s"${ctx.self.path} 没有主播的liveId,roomId=$roomId")
                      dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),AudienceJoinError)
                  }
                }

              case Left(error) =>
                log.debug(s"${ctx.self.path.name} join accept get live info left error:$error")
                dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),AudienceJoinError)
            }
          }
        }else{
          dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),AudienceJoinRsp(None))
          dispatchTo(List((userId4Audience,false)),JoinRefused)
        }

        Behaviors.same

      case HostShutJoin(`roomId`) =>
        log.debug(s"${ctx.self.path} the host has shut the join in room${roomId}")
        liveInfoMap.remove(Role.audience)
        val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
        ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
        dispatch(HostDisconnect)
        dispatch(RcvComment(-1l,"",s"the host has shut the join in room $roomId"))
        Behaviors.same

      case ModifyRoomInfo(roomName,roomDes) =>
        val roomInfo = if(roomName.nonEmpty && roomDes.nonEmpty){
          wholeRoomInfo.roomInfo.copy(roomName=roomName.get,roomDes=roomDes.get)
        }else if(roomName.nonEmpty){
          wholeRoomInfo.roomInfo.copy(roomName=roomName.get)
          wholeRoomInfo.roomInfo.copy(roomName=roomName.get)
        }else if(roomDes.nonEmpty){
          wholeRoomInfo.roomInfo.copy(roomDes=roomDes.get)
        }else{
          wholeRoomInfo.roomInfo
        }
        val info = WholeRoomInfo(roomInfo,wholeRoomInfo.layout,wholeRoomInfo.aiMode)
        log.debug(s"${ctx.self.path} modify the room info$info")
        dispatch(UpdateRoomInfo2Client(roomInfo.roomName,roomInfo.roomDes))
        dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),ModifyRoomRsp())
        idle(info,liveInfoMap,subscribers,liker,startTime,totalView,isJoinOpen)


      case HostStopPushStream(`roomId`) =>
        log.debug(s"${ctx.self.path} host stop stream in room${wholeRoomInfo.roomInfo.roomId},name=${wholeRoomInfo.roomInfo.roomName}")
        //前端需要自行处理主播主动断流的情况，后台默认连线者也会断开
        dispatch(HostStopPushStream2Client)
        ProcessorClient.closeRoom(roomId)
        liveInfoMap.clear()
        if(startTime != -1l) {
          DistributorClient.seekRecord(roomId,startTime).onComplete{
            case Success(v) =>
              v match{
                case Right(rsp) =>
                  log.debug(s"${ctx.self.path}获取录像时长为duration=${rsp.duration}")
                  RecordDao.addRecord(wholeRoomInfo.roomInfo.roomId,
                    wholeRoomInfo.roomInfo.roomName,wholeRoomInfo.roomInfo.roomDes,startTime,
                    UserInfoDao.getVideoImg(wholeRoomInfo.roomInfo.coverImgUrl),totalView,wholeRoomInfo.roomInfo.like,rsp.duration)
                case Left(err) =>
                  log.debug(s"${ctx.self.path} 查询录像文件信息失败,error:$err")
              }

            case Failure(error) =>
              log.debug(s"${ctx.self.path} 查询录像文件失败,error:$error")
          }

        }
        subscribers.get((wholeRoomInfo.roomInfo.userId,false)) match{
          case Some(hostActor) =>
            idle(wholeRoomInfo,liveInfoMap,mutable.HashMap((wholeRoomInfo.roomInfo.userId,false) -> hostActor),mutable.Set[Long](),-1l,totalView,isJoinOpen)
          case None =>
            idle(wholeRoomInfo,liveInfoMap,mutable.HashMap.empty[(Long,Boolean),ActorRef[UserActor.Command]],mutable.Set[Long](),-1l,totalView,isJoinOpen)
        }

      case JoinReq(userId4Audience,`roomId`,clientType) =>
        if(isJoinOpen){
          UserInfoDao.searchById(userId4Audience).map{r =>
            if(r.nonEmpty){
              dispatchTo(List((wholeRoomInfo.roomInfo.userId,false)),AudienceJoin(userId4Audience,r.get.userName,clientType))
            }else{
              log.debug(s"${ctx.self.path} 连线请求失败，用户id错误id=$userId4Audience in roomId=$roomId")
              dispatchTo(List((userId4Audience,false)),JoinAccountError)
            }
          }.recover{
            case e:Exception =>
              log.debug(s"${ctx.self.path} 连线请求失败，内部错误error=$e")
              dispatchTo(List((userId4Audience,false)),JoinInternalError)
          }
        }else{
          dispatchTo(List((userId4Audience,false)),JoinInvalid)
        }
        Behaviors.same

      case AudienceShutJoin(`roomId`) =>
        //切断所有的观众连线
        liveInfoMap.get(Role.audience) match{
          case Some(value) =>
            log.debug(s"${ctx.self.path} the audience connection has been shut")
            liveInfoMap.remove(Role.audience)
            val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
            dispatch(AuthProtocol.AudienceDisconnect)
            dispatch(RcvComment(-1l, "", s"the audience has shut the join in room ${roomId}"))
          case None =>
            log.debug(s"${ctx.self.path} no audience liveId")
        }
        Behaviors.same

      case AudienceShutJoinPlus(userId4Audience) =>
        //切换某个单一用户的连线
        liveInfoMap.get(Role.audience) match{
          case Some(value) =>
            value.get(userId4Audience) match{
              case Some(liveInfo) =>
                log.debug(s"${ctx.self.path} the audience connection has been shut")
                value.remove(userId4Audience)
                liveInfoMap.put(Role.audience,value)
                val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
                ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
                dispatch(AudienceDisconnect)
                dispatch(RcvComment(-1l, "", s"the audience ${userId4Audience} has shut the join in room ${roomId}"))
              case None =>
                log.debug(s"${ctx.self.path} no audience liveId")
            }
          case None =>
            log.debug(s"${ctx.self.path} no audience liveId")
        }
        Behaviors.same


      case LikeRoom(`userId`,`roomId`,upDown) =>
        upDown match{
          case Like.up =>
            if(liker.contains(userId)){
              dispatchTo(List((userId,false)),LikeRoomRsp(1001, "该用户已经点过赞了"))
              Behaviors.same
            }else{
              liker.add(userId)
              val newWholeRoomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(like = liker.size))
              log.debug(s"${ctx.self.path} 更新房间信息like=${newWholeRoomInfo.roomInfo.like}")
              dispatchTo(List((userId,false)),LikeRoomRsp(0, "点赞成功"))
              dispatch(ReFleshRoomInfo(newWholeRoomInfo.roomInfo))
              idle(newWholeRoomInfo,liveInfoMap,subscribers,liker,startTime,totalView,isJoinOpen)
            }
          case Like.down =>
            if(liker.contains(userId)){
              liker.remove(userId)
              val newWholeRoomInfo = wholeRoomInfo.copy(roomInfo = wholeRoomInfo.roomInfo.copy(like = liker.size))
              log.debug(s"${ctx.self.path} 更新房间信息like=${newWholeRoomInfo.roomInfo.like}")
              dispatchTo(List((userId,false)),LikeRoomRsp(0, "取消点赞成功"))
              dispatch(ReFleshRoomInfo(newWholeRoomInfo.roomInfo))
              idle(newWholeRoomInfo,liveInfoMap,subscribers,liker,startTime,totalView,isJoinOpen)
            }else{
              dispatchTo(List((userId,false)),LikeRoomRsp(1002, "该用户还没有点过赞"))
              Behaviors.same
            }
          case _ =>
            Behaviors.same
        }
//        wholeRoomInfo.roomInfo.like = liker.size
//        Behaviors.same

      case JudgeLike(`userId`,`roomId`) =>
        dispatchTo(List((userId,false)),JudgeLikeRsp(liker.contains(userId)))
        Behaviors.same

      case Comment(`userId`,`roomId`,comment,color,extension) =>
        UserInfoDao.searchById(userId).onComplete{
          case Success(value) =>
            value match {
              case Some(v) =>
                dispatch(RcvComment(userId,v.userName,comment,color,extension))
              case None =>
                log.debug(s"${ctx.self.path.name} the database doesn't have the user")
            }
            ctx.self ! SwitchBehavior("idle",idle(wholeRoomInfo,liveInfoMap,subscribers,liker,startTime,totalView,isJoinOpen))
          case Failure(e) =>
            log.debug(s"s${ctx.self.path.name} the search by userId error:$e")
            ctx.self ! SwitchBehavior("idle",idle(wholeRoomInfo,liveInfoMap,subscribers,liker,startTime,totalView,isJoinOpen))
        }
        switchBehavior(ctx,"busy",busy(),InitTime,TimeOut("busy"))

      case PingPackage =>
        Behaviors.same

      case x =>
        log.debug(s"${ctx.self.path} recv an unknown msg:$x")
        Behaviors.same
    }
  }

  private def changeMode(ctx:ActorContext[RoomActor.Command],anchorUid:Long,dispatchTo:(List[(Long,Boolean)],WsMsgRm) => Unit)(roomId:Long,liveIdList:List[String],screenLayout:Int,aiMode:Int,startTime:Long) = {
    ProcessorClient.updateRoomInfo(roomId,liveIdList,screenLayout,aiMode,startTime).map{
      case Right(rsp) =>
        log.debug(s"${ctx.self.path} modify the mode success")
        dispatchTo(List((anchorUid,false)),ChangeModeRsp())
      case Left(error) =>
        log.debug(s"${ctx.self.path} there is some error:$error")
        dispatchTo(List((anchorUid,false)),ChangeModeError)
    }
  }

  private def dispatch(subscribers:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]])(msg:WsMsgRm)(implicit sendBuffer:MiddleBufferInJvm):Unit = {
    log.debug(s"${subscribers}分发消息：$msg")
    subscribers.values.foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()),msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
  }

  /**
    * subscribers:所有的订阅者
    * targetUserIdList：要发送的目标用户
    * msg：发送的消息
    * */
  private def dispatchTo(subscribers:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]])(targetUserIdList:List[(Long,Boolean)],msg:WsMsgRm)(implicit sendBuffer:MiddleBufferInJvm):Unit = {
    log.debug(s"${subscribers}定向分发消息：$msg")
    targetUserIdList.foreach{k =>
      subscribers.get(k).foreach(r => r ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()),msg.isInstanceOf[AuthProtocol.HostCloseRoom]))
    }
  }


}
