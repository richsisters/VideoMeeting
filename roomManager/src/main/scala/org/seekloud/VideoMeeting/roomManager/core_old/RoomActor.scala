//package org.seekloud.VideoMeeting.roomManager.core
//
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
//import akka.http.scaladsl.model.ws.Message
//import akka.stream.scaladsl.Flow
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
//import org.seekloud.VideoMeeting.roomManager.common.Common.{Role, Source}
//import org.seekloud.VideoMeeting.roomManager.core.UserActor.{Command, DispatchMsg, JoinAcceptReq, UserLeft}
//import org.slf4j.LoggerFactory
//import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager, scheduler, timeout, userManager}
//
//import scala.collection.mutable
//import org.seekloud.VideoMeeting.roomManager.Boot.userManager
//import akka.actor.typed.scaladsl.AskPattern._
//import org.seekloud.byteobject.MiddleBufferInJvm
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.GetLiveInfoRsp4RM
//import org.seekloud.VideoMeeting.roomManager.common.Common
//import org.seekloud.VideoMeeting.roomManager.core.RoomManager.{GetRtmpLiveInfo, JudgeLikeReq, ModifyLikeNumInRoom}
//import org.seekloud.VideoMeeting.roomManager.models.dao.{UserInfoDao,RecordDao}
//import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
//import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol
//import org.seekloud.VideoMeeting.roomManager.protocol.CommonInfoProtocol.WholeRoomInfo
//import org.seekloud.VideoMeeting.roomManager.utils.{ProcessorClient, RtpClient}
//import shapeless.Succ
//
//import scala.concurrent.Future
//import scala.concurrent.duration.FiniteDuration
//import scala.util.{Failure, Success}
//import scala.concurrent.duration.{FiniteDuration, _}
//object RoomActor {
//
//  import scala.language.implicitConversions
//  import org.seekloud.byteobject.ByteObject._
//
//  private val log = LoggerFactory.getLogger(this.getClass)
//
//  trait Command
//  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command with RoomManager.Command
//
//  private final case class SwitchBehavior(
//                                           name: String,
//                                           behavior: Behavior[Command],
//                                           durationOpt: Option[FiniteDuration] = None,
//                                           timeOut: TimeOut = TimeOut("busy time error")
//                                         ) extends Command
//
//  private case class TimeOut(msg: String) extends Command
//
//  private final case object BehaviorChangeKey
//
//  private[this] def switchBehavior(ctx: ActorContext[Command],
//                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
//                                  (implicit stashBuffer: StashBuffer[Command],
//                                   timer: TimerScheduler[Command]) = {
//    timer.cancel(BehaviorChangeKey)
//    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
//    stashBuffer.unstashAll(ctx, behavior)
//  }
//
//  case object roomStart extends Command
//  final case class ApplyConnect(userId:Long,userName:String,clientType:Int,roomId:Long,replyTo:ActorRef[Boolean]) extends Command with RoomManager.Command with UserActor.Command
//
//  final case class NewSubcriberJoin(uid:Long) extends Command
//
//  final case class CommentDispatch(userId:Long,userName:String,roomId:Long,comment:String,color:String = "#FFFFFF") extends Command with RoomManager.Command with UserActor.Command
//
//  final case class UpdateRoomInfo(roomInfo:RoomInfo,liveInfo: LiveInfo,hostActor:ActorRef[UserActor.Command]) extends Command
//
//  final case class UpdateRoomInfo2Processor(userId:Long,isJoinOpen:Option[Boolean] = None,aiMode:Option[Int] = None,screenLayout:Option[Int] = None) extends Command with RoomManager.Command
//
//  final case class AudienceShutJoinReq(roomId:Long) extends Command with RoomManager.Command with UserActor.Command
//
//  final case class HostShutJoinReq(roomId:Long) extends Command with RoomManager.Command with UserManager.Command with UserActor.Command
//
//  final case class HostCloseRoomReq(roomId:Long) extends Command with RoomManager.Command
//
//  final case class ModifyRoomInfoReq(userId:Long,roomName:Option[String],roomDes:Option[String]) extends Command with RoomManager.Command
//
//  final case class HostStopStream(roomId:Long) extends Command with RoomManager.Command
//
//  final case class StartLiveAgain(liveInfo:LiveInfo) extends Command
//
//  final case class UpdateSubscriber(join:Int,roomId:Long,userId:Long,temporary:Boolean,userActorOpt:Option[ActorRef[UserActor.Command]]) extends Command with RoomManager.Command//新用户加入的时候或者退出的时候更新订阅列表
//
//  final case class TestRoom(roomInfo:RoomInfo) extends Command
//
//  final case class GetRoomInfo(replyTo:ActorRef[RoomInfo]) extends Command//考虑后续房间的建立不依赖ws
//  private final val InitTime = Some(5.minutes)
//
//  def create(roomId:Long):Behavior[Command] = {
//    Behaviors.setup[Command]{ctx =>
//      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
//      log.debug(s"${ctx.self.path} setup")
//      Behaviors.withTimers[Command]{implicit timer =>
//        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
//        init(roomId)
//      }
//    }
//  }
//
//  private def init(roomId:Long,roomInfoOpt: Option[RoomInfo] = None)
//                  (
//                    implicit stashBuffer:StashBuffer[Command],
//                    timer: TimerScheduler[Command],
//                    sendBuffer:MiddleBufferInJvm
//                  ):Behavior[Command] = {
//    Behaviors.receive[Command]{(ctx,msg) =>
//      msg match{
//        case UpdateRoomInfo(roomInfo,liveInfo,hostActor) =>
//          val startTime = System.currentTimeMillis()
//          log.debug(s"${ctx.self.path} update the room info for room:roomId=${roomInfo.roomId},roomName=${roomInfo.roomName}")
//          ProcessorClient.updateRoomInfo(roomInfo.roomId,List(liveInfo.liveId),ScreenLayout.EQUAL,AiMode.close,System.currentTimeMillis())
//          idle(WholeRoomInfo(roomInfo),mutable.HashMap(Role.host -> mutable.HashMap(roomInfo.userId -> liveInfo)),mutable.HashMap((roomInfo.userId,false) -> hostActor),List[Long](),startTime,0)
//
//        case GetRoomInfo(replyTo) =>
//          if(roomInfoOpt.nonEmpty){
//            replyTo ! roomInfoOpt.get
//          }
//          Behaviors.same
//
//        case TestRoom(roomInfo) =>
//          //仅用户测试使用空房间
//          idle(WholeRoomInfo(roomInfo),mutable.HashMap[Int,mutable.HashMap[Long,LiveInfo]](),mutable.HashMap.empty[(Long,Boolean),ActorRef[UserActor.Command]],List[Long](),System.currentTimeMillis(),0)
//
//        case x =>
//          log.debug(s"${ctx.self.path} recv an unknown msg:$x in init state...")
//          Behaviors.same
//      }
//    }
//  }
//
//  //fixme wholeRoomInfo/subscribers只要这两个字段
//  /**
//    * wholeRoomInfo:(RoomInfo,aiMode,layout,isJoinOpen,liveInfoMap)
//    * subscribers:Map[(type,userId),userActor]
//    * type,userId,userActor -- searchRoom
//    *
//    * */
//  private def idle(
//                    wholeRoomInfo: WholeRoomInfo,//可以考虑是否将主路的liveinfo加在这里，单独存一份连线者的liveinfo列表
//                    liveInfoMap: mutable.HashMap[Int,mutable.HashMap[Long,LiveInfo]],
//                    subscribe:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]],//需要区分订阅的用户的身份，注册用户还是临时用户(uid,是否是临时用户true:是)
//                    liker:List[Long],
//                    startTime:Long,
//                    totalView:Int,
//                    isJoinOpen:Boolean = false
//                  )
//    ( implicit stashBuffer:StashBuffer[Command],
//      timer: TimerScheduler[Command],
//      sendBuffer:MiddleBufferInJvm
//    ):Behavior[Command] = {
//    Behaviors.receive[Command]{(ctx,msg) =>
//      msg match {
//        case GetRoomInfo(replyTo) =>
//          replyTo ! wholeRoomInfo.roomInfo
//          Behaviors.same
//
//        case ApplyConnect(userId,userName,clientType,roomId,replyTo) =>
//          log.debug(s"${ctx.self.path} apply connect from user=$userId in room=$roomId")
//          replyTo ! isJoinOpen
//          if(isJoinOpen){
//            subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//              case Some(actor) =>
//                actor ! ApplyConnect(userId,userName,clientType,roomId,replyTo)
//              case None =>
//                log.debug(s"${ctx.self.path} there is no host actor,apply connect failed userId=${userId}")
//            }
//
//          }
//          Behaviors.same
//
//        case JudgeLikeReq(userId, _, userActorOpt) =>
//          log.debug(s"${ctx.self.path} judge user like,userId=$userId")
//          if (userActorOpt.nonEmpty){
//            if (liker.contains(userId)){
//              log.debug(s"用户${userId}已点过赞")
//              userActorOpt.get ! DispatchMsg(Wrap(JudgeLikeRsp(true).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//            }
//            else{
//              log.debug(s"用户${userId}未点过赞")
//              userActorOpt.get ! DispatchMsg(Wrap(JudgeLikeRsp(false).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//            }
//          }
//          Behaviors.same
//
//        case GetRtmpLiveInfo(_, replyTo) =>
//          log.debug(s"room${wholeRoomInfo.roomInfo.roomId}获取liveId成功")
//          liveInfoMap.get(Role.host) match{
//            case Some(value) =>
//              replyTo ! GetLiveInfoRsp4RM(Some(value.values.head))
//            case None =>
//              log.debug(s"${ctx.self.path} no host live info,roomId=${wholeRoomInfo.roomInfo.roomId}")
//              replyTo ! GetLiveInfoRsp4RM(None)
//
//          }
//          Behaviors.same
//
//        case ModifyLikeNumInRoom(userId, _, upDown)=>
//          if (upDown == 1){
//            if (liker.contains(userId)){
//              log.debug(s"用户${userId}已经点过赞了")
//              if (subscribe.contains((userId,false))){
//                subscribe((userId,false)) ! DispatchMsg(Wrap(LikeRoomRsp(1001, "该用户已经点过赞了").asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//              }
//              Behaviors.same
//            }
//            else{
//              log.debug(s"${ctx.self.path}点赞成功userId=$userId")
//              val newLiker = liker :+ userId
//              val r = wholeRoomInfo.roomInfo
//              val newRoom = RoomInfo(r.roomId,r.roomName,r.roomDes,r.userId,r.userName,r.headImgUrl,r.coverImgUrl,r.observerNum,r.like + 1,r.mpd,r.rtmp)
//              subscribe((userId,false)) ! DispatchMsg(Wrap(LikeRoomRsp(0, "点赞成功").asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//              subscribe.foreach(_._2 ! DispatchMsg(Wrap(ReFleshRoomInfo(newRoom).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//              roomManager ! RoomManager.ModifyObserverNumInRoom(wholeRoomInfo.roomInfo.roomId,newRoom.observerNum,newRoom.like)
//              idle(WholeRoomInfo(newRoom, wholeRoomInfo.layout, wholeRoomInfo.aiMode),liveInfoMap,subscribe,newLiker,startTime,totalView,isJoinOpen)
//            }
//          }
//          else if (upDown == 0){
//            if (! liker.contains(userId)){
//              log.debug(s"用户${userId}还没有点过赞")
//              if (subscribe.contains((userId,false))){
//                subscribe((userId,false)) ! DispatchMsg(Wrap(LikeRoomRsp(1002, "该用户还没有点过赞").asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//              }
//              Behaviors.same
//            }
//            else{
//              log.debug(s"${ctx.self.path}取消赞成功,userId=$userId")
//              val newLiker = liker.filterNot(r=> r == userId)
//              val r = wholeRoomInfo.roomInfo
//              val newRoom = RoomInfo(r.roomId,r.roomName,r.roomDes,r.userId,r.userName,r.headImgUrl,r.coverImgUrl,r.observerNum,r.like - 1,r.mpd,r.rtmp)
//              subscribe((userId,false)) ! DispatchMsg(Wrap(LikeRoomRsp(0, "取消点赞成功").asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//              subscribe.foreach(_._2 ! DispatchMsg(Wrap(ReFleshRoomInfo(newRoom).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//              roomManager ! RoomManager.ModifyObserverNumInRoom(wholeRoomInfo.roomInfo.roomId,newRoom.observerNum,newRoom.like)
//
//              idle(WholeRoomInfo(newRoom, wholeRoomInfo.layout, wholeRoomInfo.aiMode),liveInfoMap,subscribe,newLiker,startTime,totalView,isJoinOpen)
//            }
//          }else{
//            Behaviors.same
//          }
//
//
//        case UpdateSubscriber(join,roomId,userId,temporary,userActorOpt) =>
//          var viewNum = totalView
//          if(join == 1){
////            println("new audience join")
//            viewNum += 1
//            subscribe.put((userId,temporary),userActorOpt.get)
//          }else if (join == 0){
//            subscribe.remove((userId,temporary))
//          }
//          //所有的注册用户
//          var audienceList = subscribe.filterNot(_._1 == (wholeRoomInfo.roomInfo.userId,false)).keys.toList.filter(r => !r._2).map(_._1)
//          val temporaryList = subscribe.filterNot(_._1 == (wholeRoomInfo.roomInfo.userId,false)).keys.toList.filter(r => r._2).map(_._1)
////          if (audienceList.nonEmpty){
//            log.debug(s"${ctx.self.path} update audience info,subscribes=${subscribe.values}")
//            subscribe.foreach(user =>
//              UserInfoDao.getUserDes(audienceList).onComplete{
//                case Success(rst)=>
//                  val temporaryUserDesList = temporaryList.map(r => UserDes(r,s"guest_$r",Common.DefaultImg.headImg))
//                  user._2 ! DispatchMsg(Wrap(UpdateAudienceInfo(rst ++ temporaryUserDesList).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                case Failure(_) =>
//
//              }
//
//            )
////          }
//          roomManager ! RoomManager.ModifyObserverNumInRoom(wholeRoomInfo.roomInfo.roomId,(audienceList++temporaryList).length,wholeRoomInfo.roomInfo.like)
//          idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,viewNum,isJoinOpen)
//
//        case ModifyRoomInfoReq(userId,roomName,roomDes) =>
//          val roomInfo = if(roomName.nonEmpty && roomDes.nonEmpty){
//            RoomInfo(
//              wholeRoomInfo.roomInfo.roomId,roomName.get,roomDes.get,wholeRoomInfo.roomInfo.userId,wholeRoomInfo.roomInfo.userName,wholeRoomInfo.roomInfo.headImgUrl,wholeRoomInfo.roomInfo.coverImgUrl,wholeRoomInfo.roomInfo.observerNum,wholeRoomInfo.roomInfo.like,wholeRoomInfo.roomInfo.mpd
//            )
//          }else if(roomName.nonEmpty){
//            RoomInfo(
//              wholeRoomInfo.roomInfo.roomId,roomName.get,wholeRoomInfo.roomInfo.roomDes,wholeRoomInfo.roomInfo.userId,wholeRoomInfo.roomInfo.userName,wholeRoomInfo.roomInfo.headImgUrl,wholeRoomInfo.roomInfo.coverImgUrl,wholeRoomInfo.roomInfo.observerNum,wholeRoomInfo.roomInfo.like,wholeRoomInfo.roomInfo.mpd
//            )
//          }else if(roomDes.nonEmpty){
//            RoomInfo(
//              wholeRoomInfo.roomInfo.roomId,wholeRoomInfo.roomInfo.roomName,roomDes.get,wholeRoomInfo.roomInfo.userId,wholeRoomInfo.roomInfo.userName,wholeRoomInfo.roomInfo.headImgUrl,wholeRoomInfo.roomInfo.coverImgUrl,wholeRoomInfo.roomInfo.observerNum,wholeRoomInfo.roomInfo.like,wholeRoomInfo.roomInfo.mpd
//            )
//          }else{
//            wholeRoomInfo.roomInfo
//          }
//          val info = WholeRoomInfo(roomInfo,wholeRoomInfo.layout,wholeRoomInfo.aiMode)
//          log.debug(s"${ctx.self.path} modify the room info$info")
//          subscribe.foreach(_._2 ! DispatchMsg(Wrap(UpdateRoomInfo2Client(wholeRoomInfo.roomInfo.roomName,wholeRoomInfo.roomInfo.roomDes).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//          idle(info,liveInfoMap,subscribe,liker,startTime,totalView,isJoinOpen)
//
//        case UserActor.HostCloseRoom(userIdOpt,temporary,roomIdOpt) =>
//          log.debug(s"${ctx.self.path} host close the room")
//          ProcessorClient.closeRoom(wholeRoomInfo.roomInfo.roomId)
//          val room = wholeRoomInfo.roomInfo
//          log.debug(s"host close room ${room.roomId},save record to database")
//          RecordDao.addRecord(room.roomId,room.roomName,room.roomDes,startTime,room.coverImgUrl,totalView,room.like)
//          subscribe.filter(r => r._1 != (wholeRoomInfo.roomInfo.userId,false)).foreach(_._2 ! DispatchMsg(Wrap(HostCloseRoom.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//          Behaviors.stopped
//
//        case HostStopStream(roomId) =>
//          log.debug(s"${ctx.self.path} host stop stream in room${wholeRoomInfo.roomInfo.roomId},name=${wholeRoomInfo.roomInfo.roomName}")
//          //前端需要自行处理主播主动断流的情况，后台默认连线者也会断开
//          subscribe.foreach(_._2 ! DispatchMsg(Wrap(HostStopPushStream2Client.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//          ProcessorClient.closeRoom(roomId)
//          liveInfoMap.remove(Role.audience)
//
//          val room = wholeRoomInfo.roomInfo
//          log.debug(s"host stop stream in room${room.roomId},save record to database")
//          RecordDao.addRecord(room.roomId,room.roomName,room.roomDes,startTime,room.coverImgUrl,totalView,room.like)
//          subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//            case Some(hostActor) =>
//              idle(wholeRoomInfo,liveInfoMap,mutable.HashMap((wholeRoomInfo.roomInfo.userId,false) -> hostActor),List[Long](),startTime,totalView,isJoinOpen)
//            case None =>
//              idle(wholeRoomInfo,liveInfoMap,mutable.HashMap.empty[(Long,Boolean),ActorRef[UserActor.Command]],List[Long](),startTime,totalView,isJoinOpen)
//          }
//
//        case StartLiveAgain(liveInfo)  =>
//          log.debug(s"${ctx.self.path} the room actor has been exist,the host restart the room")
//          liveInfoMap.put(Role.host,mutable.HashMap(wholeRoomInfo.roomInfo.userId->liveInfo))
//          //fixme 这里待验证liveId的顺序是否正确
//          val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
//          val newStartTime = System.currentTimeMillis()
//          ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,newStartTime)
//          idle(wholeRoomInfo,liveInfoMap,mutable.HashMap.empty[(Long,Boolean),ActorRef[UserActor.Command]],List[Long](),newStartTime,0,isJoinOpen)
//
//        case CommentDispatch(userId,userName,roomId,comment,color) =>
//          log.debug(s"${ctx.self.path} comment dispatch")
//          subscribe.foreach(_._2 ! UserActor.DispatchMsg(Wrap(RcvComment(userId,userName,comment,color).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//          Behaviors.same
//
//        case RoomActor.HostShutJoinReq(roomId) =>
//          liveInfoMap.get(Role.audience) match{
//            case Some(value) =>
//              log.debug(s"${ctx.self.path} the host has shut the join in room${roomId}")
//              liveInfoMap.remove(Role.audience)
//              val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
//              ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
//              subscribe.foreach{
//                audience => audience._2 ! HostShutJoinReq(roomId)
//              }
//              //              userManager ! RoomActor.HostShutJoinReq(roomId,Some(decodeLiveId(value.liveId).userId))
//              ctx.self ! CommentDispatch(-1l, "", roomId, s"the host has shut the join in room ${roomId}")
//              idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,totalView,isJoinOpen)
//            case None =>
//              log.debug(s"${ctx.self.path} no liveId for this audience")
//              Behaviors.same
//          }
//
//        case AudienceShutJoinReq(roomId) =>
//          liveInfoMap.get(Role.audience) match{
//            case Some(value) =>
//              log.debug(s"${ctx.self.path} the audience connection has been shut")
//              liveInfoMap.remove(Role.audience)
//              val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
//              ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
//              subscribe.foreach{
//                audience => audience._2 ! AudienceShutJoinReq(roomId)
//              }
//              ctx.self ! CommentDispatch(-1l, "", roomId, s"the audience has shut the join in room ${roomId}")
//              idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,totalView,isJoinOpen)
//            case None =>
//              log.debug(s"${ctx.self.path} no audience liveId")
//              Behaviors.same
//          }
//
//        case JoinAcceptReq(roomId,userId,accept,liveIdHost,liveInfoAudience) =>
//          log.debug(s"${ctx.self.path} the host the connection apply from user=$userId in room=$roomId")
//          log.debug(s"${ctx.self.path} now the subscribers = ${subscribe.keys}")
//          subscribe.get((userId,false)).foreach(_ ! JoinAcceptReq(roomId,userId,accept,liveIdHost,liveInfoAudience))
//          if(accept){
//            liveInfoMap.get(Role.audience) match{
//              case Some(value) =>
//                value.put(userId,liveInfoAudience)
//                liveInfoMap.put(Role.audience,value)
//              case None =>
//                liveInfoMap.put(Role.audience,mutable.HashMap(userId -> liveInfoAudience))
//            }
//            val liveList = liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId)
//            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveList,wholeRoomInfo.layout,wholeRoomInfo.aiMode,0l)
//            idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,totalView,isJoinOpen)
//          }else{
//            log.debug(s"${ctx.self.path} the connection has been done")
//            Behaviors.same
//          }
//
//
//        case UpdateRoomInfo2Processor(userId,isConnectOpen,aiMode,screenLayout) =>
//          val connect = isConnectOpen match{
//            case Some(v) =>v
//            case None =>isJoinOpen
//          }
//          val startTime = if (userId == wholeRoomInfo.roomInfo.userId) System.currentTimeMillis() else 0l
//          if(aiMode.isEmpty && screenLayout.nonEmpty){
////            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId),screenLayout.get,wholeRoomInfo.aiMode,startTime).map{
//            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId),screenLayout.get,wholeRoomInfo.aiMode,startTime).map{
//              case Right(rsp) =>
//                log.debug(s"${ctx.self.path} modify the mode success")
//                subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//                  case Some(hostActor) =>
//                    hostActor ! DispatchMsg(Wrap(ChangeModeRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                  case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//                }
////                hostActor ! DispatchMsg(Wrap(ChangeModeRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//              case Left(error) =>
//                log.debug(s"${ctx.self.path} there is some error:$error")
//                subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//                  case Some(hostActor) =>
//                    hostActor ! DispatchMsg(Wrap(ChangeModeError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                  case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//                }
//
//
//            }
//            idle(WholeRoomInfo(wholeRoomInfo.roomInfo,screenLayout.get,wholeRoomInfo.aiMode),liveInfoMap,subscribe,liker,startTime,totalView,connect)
//          }else if(aiMode.nonEmpty && screenLayout.isEmpty){
////            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId),wholeRoomInfo.layout,aiMode.get).map{
//            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId),wholeRoomInfo.layout,aiMode.get,startTime).map{
//              case Right(rsp) =>
//                log.debug(s"${ctx.self.path} modify the mode success")
//                subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//                  case Some(hostActor) =>
//                    hostActor ! DispatchMsg(Wrap(ChangeModeRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                  case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//                }
//              case Left(error) =>
//                log.debug(s"${ctx.self.path} there is some error:$error")
//                subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//                  case Some(hostActor) =>
//                    hostActor ! DispatchMsg(Wrap(ChangeModeError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                  case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//                }
//
//            }
//            idle(WholeRoomInfo(wholeRoomInfo.roomInfo,wholeRoomInfo.layout,aiMode.get),liveInfoMap,subscribe,liker,startTime,totalView,connect)
//          }else if(aiMode.nonEmpty && screenLayout.nonEmpty){
////            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId),screenLayout.get,aiMode.get).map{
//            ProcessorClient.updateRoomInfo(wholeRoomInfo.roomInfo.roomId,liveInfoMap.toList.sortBy(_._1).flatMap(r => r._2).map(_._2.liveId),screenLayout.get,aiMode.get,startTime).map{
//              case Right(rsp) =>
//                log.debug(s"${ctx.self.path} modify the mode success")
//                subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//                  case Some(hostActor) =>
//                    hostActor ! DispatchMsg(Wrap(ChangeModeRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                  case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//                }
//              case Left(error) =>
//                log.debug(s"${ctx.self.path} there is some error:$error")
//                subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//                  case Some(hostActor) =>
//                    hostActor ! DispatchMsg(Wrap(ChangeModeError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//                  case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//                }
//
//            }
//            idle(WholeRoomInfo(wholeRoomInfo.roomInfo,screenLayout.get,aiMode.get),liveInfoMap,subscribe,liker,startTime,totalView,connect)
//          }else{
//            subscribe.get((wholeRoomInfo.roomInfo.userId,false)) match{
//              case Some(hostActor) =>
//                hostActor ! DispatchMsg(Wrap(ChangeModeRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result()))
//              case None =>log.debug(s"${ctx.self.path} there is no host actor,change mode failed")
//            }
//            idle(wholeRoomInfo,liveInfoMap,subscribe,liker,startTime,totalView,connect)
//          }
//
//        case x =>
//          log.debug(s"${ctx.self.path} recv an unknown msg $x")
//          Behaviors.same
//      }
//    }
//  }
//
//  private def busy()
//                  (
//                    implicit stashBuffer: StashBuffer[Command],
//                    timer: TimerScheduler[Command]
//                  ): Behavior[Command] =
//    Behaviors.receive[Command] { (ctx, msg) =>
//      msg match {
//        case SwitchBehavior(name, b, durationOpt, timeOut) =>
//          switchBehavior(ctx, name, b, durationOpt, timeOut)
//
//        case TimeOut(m) =>
//          log.debug(s"${ctx.self.path} is time out when busy, msg=$m")
//          Behaviors.stopped
//
//        case x =>
//          stashBuffer.stash(x)
//          Behavior.same
//
//      }
//    }
//
//  //websocket处理消息的函数
//  /**
//    * userActor --> roomManager --> roomActor --> userActor
//    * roomActor
//    * subscribers:map(userId,userActor)
//    *
//    *
//    *
//    * */
//  private def handleWebSocketMsg(
//                                  wholeRoomInfo:WholeRoomInfo,
//                                  subscribers:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]],//包括主播在内的所有用户
//                                  liveInfoMap:mutable.HashMap[Int,mutable.HashMap[Long,LiveInfo]],//"audience"/"anchor"->Map(userId->LiveInfo)
//                                  liker:List[Long],
//                                  isJoinOpen:Boolean = false,
//                                  dispatch:(WsMsgRm) => Unit,
//                                  dispatchTo:(List[(Long,Boolean)],WsMsgRm) => Unit
//                                )(ctx:ActorContext[Command],userId:Long,roomId:Long,msg:WsMsgClient):Unit = {
//    msg match{
//      case StartLiveReq(`userId`,token,clientType) =>
//      case PingPackage =>
//        //接收即发送，可考虑直接user actor处理
//      case ChangeLiveMode(isJoinOpen,aiMode,screenLayout) =>
//      case JoinAccept(`roomId`,`userId`,clientType,accept) =>
//      case HostShutJoin(`roomId`) =>
//      case ModifyRoomInfo(roomName,roomDes) =>
//      case HostStopPushStream(`roomId`) =>
//      case JoinReq(`userId`,`roomId`,clientType) =>
//      case AudienceShutJoin(`roomId`) =>
//      case LikeRoom(`userId`,`roomId`,upDown) =>
//      case JudgeLike(`userId`,`roomId`) =>
//      case Comment(`userId`,`roomId`,comment,color) =>
//      case x =>
//        log.debug(s"${ctx.self.path} recv an unknown msg:$x")
//    }
//  }
//
//  private def dispatch(subscribers:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]])(msg:WsMsgRm)(implicit sendBuffer:MiddleBufferInJvm):Unit = {
//    subscribers.values.foreach(_ ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//  }
//
//  /**
//    * subscribers:所有的订阅者
//    * targetUserIdList：要发送的目标用户
//    * msg：发送的消息
//    * */
//  private def dispatchTo(subscribers:mutable.HashMap[(Long,Boolean),ActorRef[UserActor.Command]])(targetUserIdList:List[(Long,Boolean)],msg:WsMsgRm)(implicit sendBuffer:MiddleBufferInJvm):Unit = {
//    targetUserIdList.foreach{k =>
//      subscribers.get(k).foreach(r => r ! UserActor.DispatchMsg(Wrap(msg.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())))
//    }
//  }
//
//
//}
