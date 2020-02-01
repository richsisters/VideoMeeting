//package org.seekloud.VideoMeeting.roomManager.core
//
//import akka.actor.Status.{Failure, Success}
//import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
//import akka.stream.scaladsl.Flow
//import net.sf.ehcache.transaction.xa.commands.Command
//import org.seekloud.byteobject.{ByteObject, MiddleBufferInJvm}
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
//import org.seekloud.VideoMeeting.roomManager.core.RoomActor._
//import org.slf4j.LoggerFactory
//import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler, timeout}
//import org.seekloud.VideoMeeting.roomManager.core.UserActor.JoinAcceptReq
//import akka.actor.typed.scaladsl.AskPattern._
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{LiveInfo, RoomInfo}
//import org.seekloud.VideoMeeting.protocol.ptcl.server2Manager.CommonProtocol.VerifyRsp
//import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
//import org.seekloud.VideoMeeting.protocol.ptcl.{CommonRsp, Response}
//import org.seekloud.VideoMeeting.roomManager.utils.ProcessorClient
//import org.seekloud.VideoMeeting.roomManager.common.AppSettings._
//import org.seekloud.VideoMeeting.roomManager.models.dao.{RecordDao, UserInfoDao}
//import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
//
//import scala.collection.mutable
//import scala.concurrent.Future
//
///**
//  * created by benyafang on 2019.7.16 am 10:32
//  *
//  * */
//object RoomManager {
//  private val log = LoggerFactory.getLogger(this.getClass)
//
//  trait Command
//
//  case class getRoomList(replyTo: ActorRef[RoomListRsp]) extends Command
//
//  case class RoomStart(roomInfo: RoomInfo, liveInfo:LiveInfo,hostActor:ActorRef[UserActor.Command]) extends Command
//
//  case class searchRoom(userId:  Option[Long], roomId: Long,  replyTo:ActorRef[SearchRoomRsp]) extends Command
//
//  case class ModifyObserverNumInRoom(roomId:Long,num:Int,like:Int) extends Command
//
//  case class ModifyLikeNumInRoom(userId:Long, roomId:Long, upDown:Int) extends Command with RoomActor.Command
//
//  case class JudgeLikeReq(userId:Long, roomId:Long, userActorOpt:Option[ActorRef[UserActor.Command]]) extends Command with RoomActor.Command
//
//  case class UserInfoChange(userId:Long,temporary:Boolean) extends Command
//
//  case class ExistRoom(roomId:Long,replyTo:ActorRef[Boolean]) extends Command
//
//  case class GetRtmpLiveInfo(roomId:Long, replyTo:ActorRef[GetLiveInfoRsp4RM]) extends Command with RoomActor.Command
//
//  def create():Behavior[Command] = {
//    Behaviors.setup[Command]{ctx =>
//      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
//      log.info(s"${ctx.self.path} setup")
//      Behaviors.withTimers[Command]{implicit timer =>
//        val testRoomInfo = RoomInfo(1000029,"test_room","这是一个没有主播的测试房间",100029,"byf1",
//          "http://pic.neoap.com/hestia/files/image/roomManager/b2eab30365a2a81cf1a13d1de6332c8f.png",
//          "http://pic.neoap.com/hestia/files/image/roomManager/1c6af4509f95701ffeae9999059d66d9.png",
//          0,0,
//          Some(s"https://$distributorDomain/VideoMeeting/distributor/getFile/test/index.mpd")
//        )
//        getRoomActor(1000029,ctx) ! TestRoom(testRoomInfo)
//        idle(mutable.HashMap(1000029L->testRoomInfo))
//      }
//    }
//  }
//  //roomInfo,liveInfo
//
//  private def idle(
//                    roomInUse:mutable.HashMap[Long,RoomInfo]
//                  ) //roomId -> (roomInfo, liveInfo)
//                  (implicit stashBuffer: StashBuffer[Command],timer:TimerScheduler[Command]):Behavior[Command] = {
//
//    Behaviors.receive[Command]{(ctx,msg) =>
//      msg match {
//        case getRoomList(replyTo) =>
//          log.info(s"${ctx.self.path} get room list,list=${roomInUse.values.toList}")
//          //todo 可考虑rm中不再维护map
////          val roomInfoListFuture = ctx.children.map(_.unsafeUpcast[RoomActor.Command]).map{r =>
////            val roomInfoFuture:Future[RoomInfo] = r ? (GetRoomInfo(_))
////            roomInfoFuture
////          }.toList
////          Future.sequence(roomInfoListFuture).map{seq =>
////            replyTo ! RoomListRsp(Some(seq))
////          }
//          replyTo ! RoomListRsp(if(roomInUse.isEmpty) Some(Nil) else Some(roomInUse.values.toList))
//          Behaviors.same
//
//        case RoomStart(room, live,hostActor) =>
//          log.info(s"${ctx.self.path} room is starting:room=${room.roomName} for user=${room.userId}")
//          getRoomActorOpt(room.roomId, ctx) match{
//            case Some(roomActor) =>
//              roomActor ! StartLiveAgain(live)
//            case None =>
//              log.debug(s"${ctx.self.path} the room actor doesn't exist ,will be setup for roomId=${room.roomId}")
//              val roomActor = getRoomActor(room.roomId,ctx)
//              roomActor ! RoomActor.UpdateRoomInfo(room,live,hostActor)
//              roomInUse.put(room.roomId, room)
//          }
//          idle(roomInUse)
//
//        case r@GetRtmpLiveInfo(roomId, replyTo)=>
//          if (roomInUse.contains(roomId)){
//            getRoomActorOpt(roomId, ctx).get ! r
//          }
//          else{
//            log.debug("房间未建立")
//            replyTo ! GetLiveInfoRsp4RM(None,100041,s"获取live info 请求失败:房间不存在")
//          }
//          Behaviors.same
//
//        case ModifyObserverNumInRoom(roomId,num,like) =>
//          roomInUse.get(roomId) match{
//            case Some(r) =>
//              roomInUse.update(roomId,RoomInfo(roomId,r.roomName,r.roomDes,r.userId,r.userName,r.headImgUrl,r.coverImgUrl,num,like,r.mpd,r.rtmp))
//            case None =>
//              log.debug(s"${ctx.self.path} the room:$roomId doesn't exist")
//          }
//          Behaviors.same
//
//        case ModifyLikeNumInRoom(userId, roomId, upDown) =>
//            log.debug(s"$roomId 收到点赞请求")
//            getRoomActorOpt(roomId, ctx) match{
//              case Some(roomActor) =>
//                roomActor ! ModifyLikeNumInRoom(userId, roomId, upDown)
//              case None=>
//                println("no room info")
//            }
//          Behaviors.same
//
//        case JudgeLikeReq(userId, roomId, userActorOpt) =>
//            log.debug(s"$roomId 收到判定点赞请求")
//            getRoomActorOpt(roomId, ctx) match{
//              case Some(roomActor) =>
//                roomActor ! JudgeLikeReq(userId, roomId, userActorOpt)
//              case None=>
//                println("no room info")
//            }
//          Behaviors.same
//
//        case CommentDispatch(userId,userName,roomId,comment,color) =>
//          log.info(s"${ctx.self.path} dispatch the comment from user:$userId,name=$userName in room=$roomId")
//          getRoomActorOpt(roomId,ctx) match{
//            case Some(actor) =>
//              actor !CommentDispatch(userId,userName,roomId,comment,color)
//            case None =>
//              log.debug(s"${ctx.self.path} the host has been close")
//          }
//          Behaviors.same
//
//        case searchRoom(userId, roomId, replyTo) =>
//          if(roomId == 1000029L){
//            log.debug(s"${ctx.self.path} get test room mpd,roomId=${roomId}")
//            if(roomInUse.get(roomId).nonEmpty){
//              replyTo ! SearchRoomRsp(Some(roomInUse.get(roomId).head))
//            }else{
//              log.debug(s"${ctx.self.path} test room dead")
//              replyTo ! SearchRoomError
//            }
//          }else{
//            val mpd = s"https://$distributorDomain/VideoMeeting/distributor/getFile/$roomId/index.mpd"
//            ProcessorClient.getmpd(roomId).map{
//              case Right(rsp)=>
//                val rtmpOpt = Some(rsp.rtmp)
//                if (roomInUse.get(roomId).nonEmpty) {
//                  val room = roomInUse(roomId)
//                  replyTo ! SearchRoomRsp(Some(RoomInfo(room.roomId,room.roomName,room.roomDes,room.userId,room.userName,if(room.headImgUrl == "")Common.DefaultImg.headImg else room.headImgUrl,if(room.coverImgUrl == "")Common.DefaultImg.coverImg else room.coverImgUrl,room.observerNum,room.like,Some(mpd),rtmpOpt)))
//
//                }
//                else
//                  replyTo ! SearchRoomError
//              case Left(error)=>
//                log.debug(s"get rtmp error: ${error}")
//                replyTo ! GetRtmpError
//            }
//          }
//          Behaviors.same
//
//        case ExistRoom(roomId,replyTo) =>
//          getRoomActorOpt(roomId,ctx) match {
//            case Some(actor) =>
//              replyTo ! true
//            case None =>
//              replyTo ! false
//          }
//          Behaviors.same
//
//        case UpdateSubscriber(join,roomId,userId,temporary,userActor) =>
//          getRoomActorOpt(roomId,ctx) match{
//            case Some(roomActor) =>
//              log.debug(s"${ctx.self.path}new audience join ready,userId=$userId,in room=$roomId")
//              roomActor ! UpdateSubscriber(join,roomId,userId,temporary,userActor)
//            case None =>
//              log.debug(s"${ctx.self.path} the room doesn't exist maybe the host")
//              log.debug(s"need: $roomId have:${ctx.children}")
//          }
//          Behaviors.same
//
//        case UserInfoChange(userId,temporary) =>
//          roomInUse.foreach{room=>
//            getRoomActorOpt(room._1, ctx) match{
//              case Some(roomActor) =>
//                println("user info change ready")
//                roomActor ! UpdateSubscriber(2,room._1,userId,temporary,None)
//              case None =>
//                log.debug(s"${ctx.self.path} the room doesn't exist maybe the host")
//                log.debug(s"need: ${room._1} have:${ctx.children}")
//            }
//          }
//          Behaviors.same
//
//
//        case UserActor.HostCloseRoom(userIdOpt,temporary,roomIdOpt)=>
//          //如果断开websocket的用户的id能够和已经开的房间里面的信息匹配上，就说明是主播
//          var host = false
//          roomInUse.values.foreach{r =>
//            if(userIdOpt.get == r.userId ){
//              if(roomIdOpt.nonEmpty){
//                log.debug(s"${ctx.self.path} the host close the room,roomId=${roomIdOpt.get}")
//                roomInUse.remove(roomIdOpt.get)
//              }else{
//                log.debug(s"${ctx.self.path} the roomId is None")
//              }
//              host = true
//              getRoomActorOpt(r.roomId, ctx) match{
//                case Some(roomActor) =>
//                  roomActor ! UserActor.HostCloseRoom(userIdOpt,temporary,roomIdOpt)
//                case None =>
//              }
//            }
//          }
//          if(! host && roomIdOpt.nonEmpty){
//            log.debug(s"${ctx.self.path} not host and will clear the info of this user")
//            getRoomActorOpt(roomIdOpt.get,ctx) match{
//              case Some(roomActor) =>
//                roomActor ! UpdateSubscriber(0,roomIdOpt.get,userIdOpt.get,temporary,None)
//              case None =>
//                log.debug(s"${ctx.self.path} the room actor has been stop")
//
//            }
//          }
//          Behaviors.same
//
//        case RoomActor.HostStopStream(roomId) =>
//          getRoomActorOpt(roomId, ctx) match {
//            case Some(roomActor) =>
//              roomActor ! RoomActor.HostStopStream(roomId)
//            case None =>
//          }
//          Behaviors.same
//
//        case AudienceShutJoinReq(roomId) =>
//          getRoomActorOpt(roomId, ctx) match {
//            case Some(roomActor) =>
//              roomActor ! AudienceShutJoinReq(roomId)
//            case None =>
//          }
//          Behaviors.same
//
//        case UpdateRoomInfo2Processor(userId,isConnectOpen,isFaceRec,screenLayout) =>
//          //TODO 优化，匹配到某个房间则退出循环
//          roomInUse.foreach{kv =>
//            if(kv._2.userId == userId){
//              getRoomActorOpt(kv._1, ctx) match {
//                case Some(roomActor) =>
//                  roomActor ! UpdateRoomInfo2Processor(userId,isConnectOpen,isFaceRec,screenLayout)
//                case None =>
//              }
//            }
//          }
//          Behaviors.same
//
//        case JoinAcceptReq(roomId,userId,accept,liveIdHost,liveInfoAudience) =>
//          getRoomActorOpt(roomId, ctx) match {
//            case Some(roomActor) =>
//              roomActor ! JoinAcceptReq(roomId,userId,accept,liveIdHost,liveInfoAudience)
//            case None =>
//          }
//          Behaviors.same
//
//        case RoomActor.HostShutJoinReq(roomId) =>
//          getRoomActorOpt(roomId, ctx) match {
//            case Some(roomActor) =>
//              roomActor ! RoomActor.HostShutJoinReq(roomId)
//            case None =>
//          }
//          Behaviors.same
//
//        case RoomActor.ApplyConnect(userId,userName,clientType,roomId,replyTo) =>
//          getRoomActorOpt(roomId,ctx) match{
//            case Some(actor) =>
//              val rspFuture:Future[Boolean] = actor ? (RoomActor.ApplyConnect(userId,userName,clientType,roomId,_))
//              rspFuture.map(replyTo ! _)
//            case None =>
//              log.debug(s"${ctx.self.path} the room has been closed ,roomId=$roomId from userId=$userId")
//              replyTo ! false
//          }
//          Behaviors.same
//
//        case RoomActor.ModifyRoomInfoReq(userId,roomName,roomDes) =>
//          roomInUse.foreach{room =>
//            if (room._2.userId == userId){
//              getRoomActorOpt(room._1, ctx) match {
//                case Some(roomActor) =>
//                  if(roomName.nonEmpty && roomDes.nonEmpty){
//                    roomInUse.put(room._1,RoomInfo(room._2.roomId,roomName.get,roomDes.get,
//                      room._2.userId,room._2.userName,room._2.headImgUrl,room._2.coverImgUrl,
//                      room._2.observerNum,room._2.like,room._2.mpd,room._2.rtmp))
//                  }else if(roomName.nonEmpty){
//                    roomInUse.put(room._1,RoomInfo(room._2.roomId,roomName.get,room._2.roomDes,
//                      room._2.userId,room._2.userName,room._2.headImgUrl,room._2.coverImgUrl,
//                      room._2.observerNum,room._2.like,room._2.mpd,room._2.rtmp))
//                  }else if(roomDes.nonEmpty){
//                    roomInUse.put(room._1,RoomInfo(room._2.roomId,room._2.roomName,roomDes.get,
//                      room._2.userId,room._2.userName,room._2.headImgUrl,room._2.coverImgUrl,
//                      room._2.observerNum,room._2.like,room._2.mpd,room._2.rtmp))
//                  }
//                  roomActor ! RoomActor.ModifyRoomInfoReq(userId,roomName,roomDes)
//                case None =>
//              }
//            }
//          }
//          Behaviors.same
//
//        case ChildDead(name,childRef) =>
////          val roomId = name.split("-").drop(1).head.toLong
////          roomInUse.remove(roomId)
//          log.debug(s"${ctx.self.path} the child = ${ctx.children}")
//          Behaviors.same
//
//        case x =>
//          log.debug(s"${ctx.self.path} recv an unknown msg")
//          Behaviors.same
//      }
//    }
//  }
//
//
//  private def getRoomActor(roomId:Long, ctx: ActorContext[Command]) = {
//    val childrenName = s"roomActor-${roomId}"
//    ctx.child(childrenName).getOrElse {
//      val actor = ctx.spawn(RoomActor.create(roomId), childrenName)
//      ctx.watchWith(actor,ChildDead(childrenName,actor))
//      actor
//    }.unsafeUpcast[RoomActor.Command]
//  }
//
//  private def getRoomActorOpt(roomId:Long, ctx: ActorContext[Command]) = {
//    val childrenName = s"roomActor-${roomId}"
//    log.debug(s"${ctx.self.path} the child = ${ctx.children},get the roomActor opt = ${ctx.child(childrenName).map(_.unsafeUpcast[RoomActor.Command])}")
//    ctx.child(childrenName).map(_.unsafeUpcast[RoomActor.Command])
//
//  }
//
//
//}
