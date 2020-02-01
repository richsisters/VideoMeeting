//package org.seekloud.VideoMeeting.roomManager.core
//
//import akka.actor.SupervisorStrategy.Stop
//import akka.actor.typed.{ActorRef, Behavior, PostStop, Terminated}
//import akka.http.scaladsl.server.Directives._
//import akka.actor.typed.scaladsl.AskPattern._
//import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
//import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
//import org.slf4j.LoggerFactory
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
//import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
//import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil._
//import akka.actor.typed.{ActorRef, Behavior}
//import akka.actor.typed.scaladsl.Behaviors
//import akka.http.scaladsl.model.ws.Message
//import akka.stream.OverflowStrategy
//import akka.stream.scaladsl.Flow
//import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
//import org.seekloud.byteobject.MiddleBufferInJvm
//import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol
//import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
//import org.seekloud.VideoMeeting.roomManager.Boot.roomManager
//import org.slf4j.LoggerFactory
//import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler, timeout, userManager}
//import org.seekloud.VideoMeeting.roomManager.common.Common.Source
//import org.seekloud.VideoMeeting.roomManager.core.RoomActor._
//import org.seekloud.VideoMeeting.roomManager.core.RoomManager.{JudgeLikeReq, ModifyLikeNumInRoom}
//import org.seekloud.VideoMeeting.roomManager.core.UserManager.UpdateLiveInfo
//import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol
//
//import scala.concurrent.Future
//import scala.concurrent.duration.{FiniteDuration, _}
//import scala.language.postfixOps
//import scala.util.{Failure, Success}
//import org.seekloud.VideoMeeting.roomManager.utils.RtpClient
//
///**
//  * created by ltm on
//  * 2019/7/16
//  */
//
//object UserActor {
//
//
//  import scala.language.implicitConversions
//  import org.seekloud.byteobject.ByteObject._
//
//  private val log = LoggerFactory.getLogger(this.getClass)
//  private final val InitTime = Some(5.minutes)
//  private final val BusyTime = Some(5.minutes)
//
//  trait Command
//
//  /**web socket 消息*/
//  final case class WebSocketMsg(msg:Option[WsMsgClient]) extends Command
//  final case class DispatchMsg(msg:WsMsgRm) extends Command
//  case object CompleteMsgClient extends Command
//  case class FailMsgClient(ex:Throwable) extends Command
//  case class UserClientActor(actor:ActorRef[WsMsgRm]) extends Command
//  final case class JoinAcceptReq(roomId:Long, userId: Long, accept: Boolean,liveIdHostOpt:String, liveInfoAudience:LiveInfo) extends Command with UserManager.Command with RoomActor.Command with RoomManager.Command
//
//  final case class JoinRefuseReq(roomId:Long, userId: Long, accept: Boolean,liveIdHostOpt:String) extends Command with UserManager.Command
////  final case object WebSocketClose extends Command with RoomManager.Command with RoomActor.Command
//  case class HostCloseRoom(userIdOpt:Option[Long],temporary:Boolean,roomIdOpt:Option[Long]) extends Command with RoomManager.Command with RoomActor.Command// 主播关闭房间
//  case class HostCloseRoomByHand(roomId:Long,temporary: Boolean) extends Command with RoomManager.Command with RoomActor.Command// 主播关闭房间
//
//  case object HeartBeatKey
//
//  /**http消息*/
//  final case class UserLogin(roomId:Long,userId:Long) extends Command with UserManager.Command//新用户请求mpd的时候处理这个消息，更新roomActor中的列表
//  final case object StopSelf extends Command
//  case class UserLeft[U](actorRef: ActorRef[U]) extends Command
//  final case class ChildDead[U](userId: Long,temporary:Boolean, childRef: ActorRef[U]) extends Command with UserManager.Command
//  final case object ChangeBehaviorToInit extends Command
//  final case class SendHeartBeat(clientActor:ActorRef[WsMsgRm]) extends  Command
//
//  private final case class SwitchBehavior(
//    name: String,
//    behavior: Behavior[Command],
//    durationOpt: Option[FiniteDuration] = None,
//    timeOut: TimeOut = TimeOut("busy time error")
//  ) extends Command
//
//  private case class TimeOut(msg: String) extends Command
//
//  private final case object BehaviorChangeKey
//
//  private[this] def switchBehavior(ctx: ActorContext[Command],
//    behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
//    (implicit stashBuffer: StashBuffer[Command],
//      timer: TimerScheduler[Command]) = {
//    timer.cancel(BehaviorChangeKey)
//    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
//    stashBuffer.unstashAll(ctx, behavior)
//  }
//
//  /**
//    * userId
//    * temporary:true--临时用户，false--登陆用户
//    * */
//  def create(userId: Long,temporary:Boolean): Behavior[Command] = {
//    Behaviors.setup[Command] { ctx =>
//      log.info(s"userActor-$userId is starting...")
//      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
//      Behaviors.withTimers[Command] { implicit timer =>
//        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(8192)
//        init(userId,temporary,None)
//      }
//    }
//  }
//
//  private def init(
//                    userId:Long,
//                    temporary:Boolean,
//                    roomIdOpt:Option[Long]
//                  )(
//    implicit stashBuffer:StashBuffer[Command],
//    sendBuffer: MiddleBufferInJvm,
//    timer: TimerScheduler[Command]
//  ):Behavior[Command] = {
//    Behaviors.receive[Command] {
//      (ctx, msg) =>
//        msg match {
//          case UserClientActor(clientActor) =>
//            log.debug(s"${ctx.self.path} switch the idle")
//            ctx.watchWith(clientActor, UserLeft(clientActor))
//            timer.startPeriodicTimer("HeartBeatKey_" + userId, SendHeartBeat(clientActor), 5.seconds)
//            switchBehavior(ctx, "idle", idle(userId,temporary,clientActor,roomIdOpt))
//
//
//          case UserLogin(roomId,`userId`) =>
//            //todo roomId是否需要存储在userActor中
//            roomManager ! UpdateSubscriber(1,roomId,userId,temporary,Some(ctx.self))
//            init(userId,temporary,Some(roomId))
//
//          case UserLeft(actor) =>
//            log.debug(s"${ctx.self.path} user left init")
//            Behaviors.same
//
//          case TimeOut(m) =>
//            log.debug(s"${ctx.self.path} is time out when busy,msg=${m}")
//            Behaviors.stopped
//
//          case unknown =>
//            log.debug(s"${ctx.self.path} recv an unknown msg:${msg} in init state...")
//            stashBuffer.stash(unknown)
//            Behavior.same
//        }
//    }
//  }
//
//  private def idle(
//    userId: Long,
//    temporary:Boolean,
//    clientActor:ActorRef[WsMsgRm],
//    roomIdOpt:Option[Long],
//    liveIdOpt:Option[String] = None
//  )(
//    implicit stashBuffer: StashBuffer[Command],
//    timer: TimerScheduler[Command],
//    sendBuffer: MiddleBufferInJvm
//  ): Behavior[Command] =
//
//
//    Behaviors.receive[Command] { (ctx, msg) =>
//      msg match {
//        case SendHeartBeat(clientActor) =>
////          log.debug(s"${ctx.self.path} send heartBeat")
//          clientActor ! Wrap(HeatBeat(System.currentTimeMillis()).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//          Behaviors.same
//
//        case JoinAcceptReq(roomId,`userId`,accept,liveIdHost,liveInfoAudience) =>
//          //todo 在roomActor中进行分发
//          log.debug(s"${ctx.self.path.name} recv a join accept msg for user:$userId in room:$roomId")
//          //如果房间没有开通连线申请，则不会给主播发送申请连线消息，所以这里不用判断没有开通得情况
//          if(accept){
//            roomManager ! CommentDispatch(-1l, "", roomId, s"user:$userId join in room:$roomId")
//            log.debug(s"response to clientActor: ${JoinRsp(Some(liveIdHost),Some(liveInfoAudience))}")
//            clientActor ! Wrap(JoinRsp(Some(liveIdHost),Some(liveInfoAudience)).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())//给申请者发送主播得LiveID和自己得
//            Behaviors.same
//          }else{
//            clientActor ! Wrap(JoinRefused.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//            Behaviors.same
//          }
//
//        case JoinRefuseReq(roomId,`userId`,accept,liveIdHost) =>
//          log.debug(s"${ctx.self.path.name} recv a refuse msg for $userId in room:$roomId")
//          clientActor ! Wrap(JoinRefused.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//          Behaviors.same
//
//        case ApplyConnect(userId4Audience,applyName,clientType,roomId,replyTo) =>
//          //主播收到的申请者申请连线的消息，需要通过websocket传递给主播
//          //这个是主播的actor，所以消息中的id是观众的id和主播id不同
//          clientActor ! Wrap(AudienceJoin(userId4Audience,applyName,clientType).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())//给主播发送申请者得userId和userName
//          Behaviors.same
//
//        case HostShutJoinReq(_) =>
//          clientActor ! Wrap(HostDisconnect.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//          Behaviors.same
//
//        case UserLogin(roomId,`userId`) =>
//          println("login 2")
//          //todo roomId是否需要存储在userActor中
//          roomManager ! UpdateSubscriber(1,roomId,userId,temporary,Some(ctx.self))
//          Behaviors.same
//
//        case AudienceShutJoinReq(_) =>
//          clientActor ! Wrap(AudienceDisconnect.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//          Behaviors.same
//
//        case DispatchMsg(message) =>
//          log.info(s"${ctx.self.path.name} dispatch msg=$message")
//          clientActor ! message
//          Behaviors.same
//
//        case WebSocketMsg(reqOpt) =>
//          reqOpt match {
//            case Some(req) =>
////              log.debug(s"${ctx.self.path} recv a msg:$req")
//              req match{
//                case StartLiveReq(`userId`,token,clientType) =>
//                  val clientType1 = clientType match{
//                    case CommonInfo.ClientType.WEB => Source.web
//                    case CommonInfo.ClientType.PC => Source.pc
//                  }
//                  UserInfoDao.searchById(userId).onComplete{
//                    case Success(rst)=>
//                      if (rst.nonEmpty ){
//                        val rsp = rst.get
//                        if (rsp.token == token){
//                          log.debug(s"${ctx.self.path.name} recv a start live msg")
//                          val roomInfo = RoomInfo(rsp.roomid, s"room:${rsp.roomid}", "", rsp.uid,rsp.userName,if(rsp.headImg == "")Common.DefaultImg.headImg else rsp.headImg,if(rsp.coverImg == "")Common.DefaultImg.coverImg else rsp.coverImg,0,0)
////                          val liveInfo = generateLiveInfo(clientType1)
//                          //fixme get live info
//                          RtpClient.getLiveInfoFunc().onComplete{
//                            case Success(data) =>
//                              data match{
//                                case Right(rsp) =>
//                                  clientActor ! Wrap(StartLiveRsp(Some(rsp.liveInfo)).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                                  roomManager ! RoomManager.RoomStart(roomInfo, rsp.liveInfo, ctx.self)
//                                  ctx.self ! SwitchBehavior("idle",idle(userId,temporary,clientActor,roomIdOpt,Some(rsp.liveInfo.liveId)))
//                                case Left(error) =>
//                                  log.debug(s"${ctx.self.path} get live info left error:${error}")
//                                  clientActor ! Wrap(StartLiveRefused.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                                  ctx.self ! SwitchBehavior("idle",idle(userId,temporary,clientActor,None))
//                              }
//                            case Failure(error) =>
//                              log.debug(s"${ctx.self.path} get live info error:${error}")
//                              clientActor ! Wrap(StartLiveRefused.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                              ctx.self ! SwitchBehavior("idle",idle(userId,temporary,clientActor,None))
//
//                          }
//                        }
//                        else {
//                          log.debug(s"${ctx.self.path} token error")
//                          clientActor ! Wrap(StartLiveRefused.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                          ctx.self ! SwitchBehavior("idle",idle(userId,temporary,clientActor,None))
//                        }
//
//                      }else{
//                        log.debug(s"${ctx.self.path} user not exist")
//                        clientActor ! Wrap(StartLiveRefused.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                        ctx.self ! SwitchBehavior("idle",idle(userId,temporary,clientActor,None))
//                      }
//                    case Failure(e) =>
//                      log.error(s"${ctx.self.path} internal error: ${e}")
//                      clientActor ! Wrap(StartLiveRsp(None,200002,"internal error").asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                      ctx.self ! SwitchBehavior("idle",idle(userId,temporary,clientActor,None))
//
//                  }
//                  switchBehavior(ctx,"busy",busy(),BusyTime,TimeOut("busy"))
//
//
//                case PingPackage =>
//                  log.debug(s"${ctx.self.path.name} recv a msg : PingPackage")
//                  clientActor ! Wrap(PingPackage.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                  Behaviors.same
//
//                case ChangeLiveMode(isJoinOpen,aiMode,screenLayout) =>
//                  log.debug(s"${ctx.self.path.name} the host change mode ")
//                  roomManager ! RoomActor.UpdateRoomInfo2Processor(userId,isJoinOpen,aiMode,screenLayout)
////                  clientActor ! Wrap(ChangeModeRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                  Behaviors.same
//
//                case LikeRoom(`userId`, roomId, upDown) =>
//                  log.debug(s"$roomId 收到一条like信息")
//                  roomManager ! ModifyLikeNumInRoom(userId, roomId, upDown)
//                  Behaviors.same
//
//                case JudgeLike(`userId`, roomId) =>
//                  log.debug(s"${ctx.self.path} judge user like,userId=${userId},in room=$roomId")
//                  roomManager ! JudgeLikeReq(userId, roomId, Some(ctx.self))
//                  Behaviors.same
//
//                case JoinAccept(roomId,userId4Client,clientType,accept) =>
//                  log.debug(s"${ctx.self.path.name} recv a msg=${msg}")
//                  if(accept){
//                    log.debug(s"${ctx.self.path.name} the host accept the apply from user:$userId4Client in room:$roomId")
//                    UserInfoDao.searchById(userId4Client).onComplete{
//                      case Success(value) =>
//                        value match {
//                          case Some(v) =>
//                            /**观众的连线信息*/
//                            val clientType1 = clientType match{
//                              case ClientType.PC => Source.pc
//                              case ClientType.WEB => Source.web
//                            }
////                            val liveInfoAudience = generateLiveInfo(clientType1)
//                            RtpClient.getLiveInfoFunc().onComplete{
//                              case Success(data) =>
//                                data match{
//                                  case Right(rsp) =>
//                                    val audienceInfo = AudienceInfo(userId4Client,v.userName,rsp.liveInfo.liveId)
//                                    liveIdOpt match {
//                                      case Some(liveId) =>
//                                        userManager ! UpdateLiveInfo(userId4Client, rsp.liveInfo)
//                                        roomManager ! JoinAcceptReq(roomId,userId4Client,accept,liveId,rsp.liveInfo)//clientType这里的已经转化完
//                                      case None =>
//                                        log.debug(s"${ctx.self.path.name} idle state error :the liveId doesn't update")
//                                        clientActor ! Wrap(AudienceJoinError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                                    }
//                                    clientActor ! Wrap(AudienceJoinRsp(Some(audienceInfo)).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                                  case Left(error) =>
//                                    log.debug(s"${ctx.self.path.name} join accept get live info left error:${error}")
//                                    clientActor ! Wrap(AudienceJoinError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                                }
//                              case Failure(error) =>
//                                log.debug(s"${ctx.self.path.name} join accept get live info failure error:${error}")
//                                clientActor ! Wrap(AudienceJoinError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//
//                            }
////                            val audienceInfo = AudienceInfo(userId4Client,v.userName,liveInfoAudience.liveId)
////                            liveIdOpt match {
////                              case Some(liveId) =>
////                                userManager ! UpdateLiveInfo(userId4Client, liveInfoAudience)
////                                roomManager ! JoinAcceptReq(roomId,userId4Client,accept,liveId,liveInfoAudience)//clientType这里的已经转化完
////                              case None =>
////                                log.debug(s"${ctx.self.path.name} idle state error :the liveId doesn't update")
////                                clientActor ! Wrap(AudienceJoinError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
////                            }
////                            clientActor ! Wrap(AudienceJoinRsp(Some(audienceInfo)).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                          case None =>
//                            //fixme 临时用户查询userName这里需要注意
//                            log.debug(s"${ctx.self.path.name} the database doesn't have the user")
//                            clientActor ! Wrap(AudienceJoinError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                        }
//                      case Failure(e) =>
//                        log.debug(s"s${ctx.self.path.name} the search by userId error:$e")
//                        clientActor ! Wrap(AudienceJoinError.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                    }
//                  }else{
//                    //todo 给申请者发送拒绝信息
//                    log.debug(s"${ctx.self.path.name} the host refuse the apply from user:$userId in room:$roomId")
//                    userManager ! JoinRefuseReq(roomId,userId,accept,liveIdOpt.get)
//                    clientActor ! Wrap(AudienceJoinRsp(None).asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                  }
//                  Behaviors.same
//
//                case HostShutJoin(roomId) =>
//                  //fixme 如果是多人连线，主播切断连线消息需要带上userId
//                  //断开与观众的连线请求
//                  log.debug(s"${ctx.self.path.name} the host shut the connection from audience in room:$roomId")
//                  roomManager ! RoomActor.HostShutJoinReq(roomId)
////                  clientActor ! Wrap(HostDisconnect.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                  Behaviors.same
//
//                case ModifyRoomInfo(roomName,roomDes) =>
//                  roomManager ! ModifyRoomInfoReq(userId,roomName,roomDes)
//                  clientActor ! Wrap(ModifyRoomRsp().asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                  Behaviors.same
//
//                case HostStopPushStream(roomId) =>
//                  //主播停止推流,通知所有观众主播停止推流，给processor更新房间信息的消息
//                  roomManager ! RoomActor.HostStopStream(roomId)
//                  Behaviors.same
//
//                case JoinReq(`userId`,roomId,clientType) =>
//                  UserInfoDao.searchById(userId).onComplete{
//                    case Success(value) =>
//                      value match {
//                        case Some(v) =>
//                          val rspFuture:Future[Boolean] = roomManager ? (RoomActor.ApplyConnect(userId,v.userName,clientType,roomId,_))
//                          rspFuture.map{rsp =>
//                            if(! rsp)//房间不可申请
//                            {
//                              log.debug(s"${ctx.self.path.name} the room-$roomId is not open")
//                              clientActor ! Wrap(JoinInvalid.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                            }
//                          }
//                        case None =>
//                          //fixme 临时用户查询userName这里需要注意
//                          log.debug(s"${ctx.self.path.name} the database doesn't have the user")
//                      }
//                    case Failure(e) =>
//                      log.debug(s"s${ctx.self.path.name} the search by userId error:$e")
//                  }
//                  Behaviors.same
//
//                case AudienceShutJoin(roomId) =>
//                  log.debug(s"${ctx.self.path.name} the audience close the connection in room:$roomId")
//                  roomManager ! AudienceShutJoinReq(roomId)
////                  clientActor ! Wrap(AudienceDisconnect.asInstanceOf[WsMsgRm].fillMiddleBuffer(sendBuffer).result())
//                  Behaviors.same
//
//                case Comment(`userId`,roomId,comment,color) =>
//                  //fixme 查询userName临时用户
//                  UserInfoDao.searchById(userId).onComplete{
//                    case Success(value) =>
//                      value match {
//                        case Some(v) =>
//                          roomManager ! RoomActor.CommentDispatch(userId,v.userName,roomId,comment,color)
//                        case None =>
//                          //fixme 临时用户查询userName这里需要注意
//                          log.debug(s"${ctx.self.path.name} the database doesn't have the user")
//                      }
//                    case Failure(e) =>
//                      log.debug(s"s${ctx.self.path.name} the search by userId error:$e")
//                  }
//                  Behaviors.same
//              }
//            case None =>
//              log.debug(s"${ctx.self.path.name} there is no websocket msg")
//              Behaviors.same
//          }
//
//        case CompleteMsgClient =>
//          //主播需要关闭房间，通知所有观众
//          //观众需要清楚房间中对应的用户信息映射
//          log.debug(s"${ctx.self.path.name} complete msg")
////          Terminated(clientActor)
////          ctx.stop(clientActor)
////          clientActor ! PostStop.asInstanceOf[WsMsgRm]
//          roomManager ! HostCloseRoom(Some(userId),temporary,roomIdOpt)
//          Behaviors.stopped
////          Behaviors.same
//
//        case ChangeBehaviorToInit =>
//          switchBehavior(ctx,"init",init(userId,temporary,roomIdOpt),InitTime,TimeOut("init"))
//
//        case UserLeft(actor) =>
//          log.debug(s"${ctx.self.path.name} the user left")
////          log.debug(s"${ctx.self.path} the websocket has been shut down,the actor stopped")
////          roomManager ! HostCloseRoom(Some(userId))
////          Behaviors.stopped
//          Behaviors.same
//
//        case StopSelf =>
//          log.info(s"userActor-$userId is stopping...")
//          Behaviors.stopped
//
//        case x =>
//          log.warn(s"unknown msg: $x")
//          Behaviors.unhandled
//      }
//    }
//
//  private def busy()
//    (
//      implicit stashBuffer: StashBuffer[Command],
//      timer: TimerScheduler[Command]
//    ): Behavior[Command] =
//    Behaviors.receive[Command] { (ctx, msg) =>
//      msg match {
//        case SwitchBehavior(name, b, durationOpt, timeOut) =>
//          switchBehavior(ctx, name, b, durationOpt, timeOut)
//
//        case TimeOut(m) =>
//          log.debug(s"${ctx.self.path.name} is time out when busy, msg=$m")
//          Behaviors.stopped
//
//        case x =>
//          stashBuffer.stash(x)
//          Behavior.same
//
//      }
//    }
//
////  private def searchUser(uid:Long,actor:ActorRef[UserActor.Command],idleState:IdleState,msg:Command) = {
////    UserInfoDao.SearchById(uid).onComplete{
////      case Success(resOpt) =>
////        if(resOpt.nonEmpty){
////
////
////        }else{
////
////        }
////      case Failure(error) =>
////        actor ! SwitchBehavior("idle",idle(idleState.userId,idleState.temporary,idleState.clientActor,idleState.liveIdOpt))
////    }
////
////  }
//
//  private def sink(userActor: ActorRef[UserActor.Command]) = ActorSink.actorRef[Command](
//    ref = userActor,
//    onCompleteMessage = CompleteMsgClient,
//    onFailureMessage = { e =>
//      e.printStackTrace()
//      FailMsgClient(e)
//    }
//  )
//
//
//  def flow(userActor: ActorRef[UserActor.Command]):Flow[WebSocketMsg,WsMsgManager,Any] = {
//    val in = Flow[WebSocketMsg].to(sink(userActor))
//    val out = ActorSource.actorRef[WsMsgManager](
//      completionMatcher = {
//        case CompleteMsgRm =>
//          println("flow got CompleteMsgRm msg")
////          userActor ! HostCloseRoom(None)
//      },
//      failureMatcher = {
//        case FailMsgRm(e) =>
//          e.printStackTrace()
//          e
//      },
//      bufferSize = 256,
//      overflowStrategy = OverflowStrategy.dropHead
//    ).mapMaterializedValue(outActor => userActor ! UserClientActor(outActor))
//    Flow.fromSinkAndSource(in,out)
//  }
//}
