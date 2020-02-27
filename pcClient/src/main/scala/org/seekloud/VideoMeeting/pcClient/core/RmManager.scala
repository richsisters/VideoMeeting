package org.seekloud.VideoMeeting.pcClient.core

import java.io.File

import akka.Done
import akka.actor.typed.{ActorRef, Behavior, scaladsl}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.util.{ByteString, ByteStringBuilder}
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.controller.{AudienceController, HomeController, HostController, InviteController, RoomController}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.pcClient.Boot.{executor, materializer, scheduler, system, timeout}
import org.seekloud.VideoMeeting.pcClient.common.Constants.{AudienceStatus, HostStatus}
import org.seekloud.VideoMeeting.pcClient.common._
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.stream.LiveManager.PullInfo
import org.seekloud.VideoMeeting.pcClient.core.stream.LiveManager
import org.seekloud.VideoMeeting.pcClient.scene.{AudienceScene, HomeScene, HostScene, RoomScene}
import org.seekloud.VideoMeeting.pcClient.utils.RMClient
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.player.sdk.MediaPlayer
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol

import scala.concurrent.Future
import scala.util.{Failure, Success}
import concurrent.duration._
import scala.collection.mutable


/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:29
  *
  * 与roomManager的交互管理
  */
object RmManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  //  case class User(username: String, loginInfo: UserInfo)

  var userInfo: Option[UserInfo] = None
  var roomInfo: Option[RoomInfo] = None
  val likeMap: mutable.HashMap[(Long, Long), Boolean] = mutable.HashMap.empty //(userId,roomId) -> true
  //  var userHeader: Option[ImageView] = None

  /*未登录观众建立ws临时使用信息*/
  var guestInfo: Option[UserInfo] = None

  sealed trait RmCommand

  final case class GetHomeItems(homeScene: HomeScene, homeController: HomeController) extends RmCommand

  final case class SignInSuccess(userInfo: UserInfo, roomInfo: RoomInfo, getTokenTime: Option[Long] = None) extends RmCommand

  final case object GoToLive extends RmCommand

  final case object GoToRoomHall extends RmCommand

  final case class GetRoomDetail(roomId: Long) extends RmCommand

  final case class GoToWatch(roomInfo: RoomInfo) extends RmCommand

  final case class GetSender(sender: ActorRef[WsMsgFront]) extends RmCommand

  final case class ChangeUserName(newUserName: String) extends RmCommand

  final case class PullFromProcessor(newId:String) extends RmCommand

  final case class PullStream4Others(liveId: List[String]) extends RmCommand

  final case object BackToHome extends RmCommand

  final case object HeartBeat extends RmCommand

  final case object PingTimeOut extends RmCommand

  final case object StopSelf extends RmCommand

  final case object Logout extends RmCommand

  final case object PullDelay extends RmCommand


  /*主播*/

  final case object HostWsEstablish extends RmCommand

  final case object HostLiveReq extends RmCommand

  final case class StartLive(liveId: String, liveCode: String) extends RmCommand

  final case class ModifyRoom(name: Option[String], des: Option[String]) extends RmCommand

  final case class ChangeMode(isJoinOpen: Option[Boolean], aiMode: Option[Int], screenLayout: Option[Int]) extends RmCommand

  final case class ChangeOption(bit: Option[Int], re: Option[String], frameRate: Option[Int], needImage: Boolean = true, needSound: Boolean = true, recordOrNot: Boolean = false) extends RmCommand

  final case class AudienceAcceptance(userId: Long, accept: Boolean) extends RmCommand

  final case object HostStartMeetingRecord extends RmCommand

  final case object HostFinishMeeting extends RmCommand

  final case class JoinBegin(audienceInfo: AudienceInfo) extends RmCommand //开始和某观众连线

  final case class InviteReq(email: String, meeting: String) extends RmCommand

  final case class ForceExit(userId4Member: Long, userName4Member: String) extends RmCommand //主持人主动停止和某个观众连线

  final case class BanOnMember(userId4Member: Long, image: Boolean, sound: Boolean) extends RmCommand

  final case class CancelBan(userId4Member: Long, image: Boolean, sound: Boolean) extends RmCommand

  final case class SpeakerRight(userId4Member: Long) extends RmCommand


  /*观众*/

  final case class ChangeOption4Audience(needImage: Boolean = true, needSound: Boolean = true) extends RmCommand

  final case object PullerStopped extends RmCommand

  final case object AudienceWsEstablish extends RmCommand

  final case class JoinRoomReq(roomId: Long) extends RmCommand

  final case class StartJoin(hostLiveId: String, audienceLiveInfo: LiveInfo, attendLiveId: List[String]) extends RmCommand //开始和主播连线

  final case object StopJoinAndWatch extends RmCommand //停止和主播连线

  final case class OtherAudienceJoin(liveId: String) extends RmCommand

  final case class ExitJoin(roomId: Long, userId: Long) extends RmCommand //主动关闭和主播的连线

  final case class StartRecord(outFilePath: String) extends  RmCommand //开始录制

  final case object StopRecord extends RmCommand  //结束录制

  final case class PausePlayRec(recordInfo: RecordInfo) extends RmCommand  //暂停播放录像

  final case class ContinuePlayRec(recordInfo: RecordInfo) extends RmCommand //继续播放录像

  final case object MeetingFinished extends RmCommand

  private object ASK4STATE_RETRY_TIMER_KEY


  private[this] def switchBehavior(ctx: ActorContext[RmCommand],
    behaviorName: String,
    behavior: Behavior[RmCommand])
    (implicit stashBuffer: StashBuffer[RmCommand]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    stashBuffer.unstashAll(ctx, behavior)
  }


  def create(stageCtx: StageContext): Behavior[RmCommand] =
    Behaviors.setup[RmCommand] { ctx =>
      log.info(s"RmManager is starting...")
      implicit val stashBuffer: StashBuffer[RmCommand] = StashBuffer[RmCommand](Int.MaxValue)
      Behaviors.withTimers[RmCommand] { implicit timer =>
        val mediaPlayer = new MediaPlayer()
        mediaPlayer.init(isDebug = AppSettings.playerDebug, needTimestamp = AppSettings.needTimestamp)
        val liveManager = ctx.spawn(LiveManager.create(ctx.self, mediaPlayer), "liveManager")
        idle(stageCtx, liveManager, mediaPlayer)
      }
    }



  private def idle(
    stageCtx: StageContext,
    liveManager: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    homeController: Option[HomeController] = None,
    roomController: Option[RoomController] = None,
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] =
    Behaviors.receive[RmCommand] { (ctx, msg) =>
      msg match {

        case msg: GetHomeItems =>
          idle(stageCtx, liveManager, mediaPlayer, Some(msg.homeController), roomController)

        case msg: SignInSuccess =>
          userInfo = Some(msg.userInfo)
          roomInfo = Some(msg.roomInfo)
          //token维护（缓存登录时）
          if(msg.getTokenTime.nonEmpty){
            val getTokenTime = msg.getTokenTime.get
            val tokenExistTime = msg.userInfo.tokenExistTime
            if(System.currentTimeMillis() - getTokenTime > tokenExistTime * 0.8){
              homeController.get.updateCache()
            }
          }
          Behaviors.same

        case GoToLive =>
          log.debug(s"########user-${userInfo.get.userName} start to live")
          val hostScene = new HostScene(stageCtx.getStage)
          val inviteController = new InviteController(stageCtx, ctx.self)
          val hostController = new HostController(stageCtx, hostScene, inviteController, ctx.self)

          def callBack(): Unit = Boot.addToPlatform(hostScene.changeToggleAction())

          liveManager ! LiveManager.DevicesOn(hostScene.gc, callBackFunc = Some(callBack))
          ctx.self ! HostWsEstablish
          Boot.addToPlatform {
            if (homeController != null) {
              homeController.get.removeLoading()
            }
            hostController.showScene()
          }
          switchBehavior(ctx, "hostBehavior", hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer))

        case GoToRoomHall =>
          val roomScene = new RoomScene()
          val roomController = new RoomController(stageCtx, roomScene, ctx.self)
          Boot.addToPlatform {
            if (homeController != null) {
              homeController.get.removeLoading()
            }
            roomController.showScene()
          }
          idle(stageCtx, liveManager, mediaPlayer, homeController, Some(roomController))

        case msg: GetRoomDetail =>
          val userId = if (userInfo.nonEmpty) { //观众已登录
            Some(userInfo.get.userId)
          } else None

          RMClient.searchRoom(userId, msg.roomId).onComplete {
            case Success(rst) =>
              rst match {
                case Right(room) =>
                  if (room.errCode == 0) {
                    ctx.self ! GoToWatch(room.roomInfo.get)
                  } else {
                    Boot.addToPlatform {
                      roomController.get.removeLoading()
                      WarningDialog.initWarningDialog(s"该会议室还没有准备好～～")
                    }
                  }
                case Left(error) =>
                  Boot.addToPlatform {
                    roomController.get.removeLoading()
                  }
                  log.error(s"search room rsp error: $error")
              }

            case Failure(exception) =>
              log.error(s"search room-${msg.roomId} future error: $exception")
              Boot.addToPlatform {
                roomController.get.removeLoading()
              }
          }
          Behaviors.same

        case msg: GoToWatch =>
          val audienceScene = new AudienceScene(msg.roomInfo.toAlbum)
          val audienceController = new AudienceController(stageCtx, audienceScene, ctx.self)
          liveManager ! LiveManager.DevicesOn(audienceScene.gc) //参会者进入房间后首先画自己的流

          if (msg.roomInfo.rtmp.nonEmpty) {
            ctx.self ! AudienceWsEstablish

            Boot.addToPlatform {
              roomController.foreach(_.removeLoading())
              audienceController.showScene()
            }
            switchBehavior(ctx, "audienceBehavior", audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, audienceStatus = AudienceStatus.LIVE))
          } else {
            Boot.addToPlatform {
              roomController.foreach(_.removeLoading())
              WarningDialog.initWarningDialog("该会议室还没有准备好～～")
            }
            Behaviors.same
          }

        case msg: ChangeUserName =>
          log.info("change userName.")
          this.userInfo = userInfo.map(_.copy(userName = msg.newUserName))
          Boot.addToPlatform {
            homeController.foreach(_.showScene())
          }
          Behaviors.same

        case BackToHome =>
          log.info("back to home.")
          Boot.addToPlatform {
            homeController.foreach(_.showScene())
          }
          System.gc()
          Behaviors.same

        case StopSelf =>
          log.info(s"rmManager stopped in idle.")
          Behaviors.stopped


        case Logout =>
          log.info(s"logout.")
          this.roomInfo = None
          this.userInfo = None
          homeController.get.showScene()
          Behaviors.same

        case msg: ModifyRoom =>
          if (msg.name.isDefined) {
            this.roomInfo = roomInfo.map(_.copy(roomName = msg.name.get))
          }
          if (msg.des.isDefined) {
            this.roomInfo = roomInfo.map(_.copy(roomDes = msg.des.get))
          }
          Behaviors.same

        case x =>

          log.warn(s"unknown msg in idle: $x")
          stashBuffer.stash(x)
          Behaviors.same
      }
    }


  private def hostBehavior(
    stageCtx: StageContext,
    homeController: Option[HomeController] = None,
    hostScene: HostScene,
    hostController: HostController,
    liveManager: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    sender: Option[ActorRef[WsMsgFront]] = None,
    hostStatus: Int = HostStatus.LIVE, //0-直播，1-连线
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] =
    Behaviors.receive[RmCommand] { (ctx, msg) =>
      msg match {
        case HostWsEstablish =>
          //与roomManager建立ws
          assert(userInfo.nonEmpty && roomInfo.nonEmpty)

          def successFunc(): Unit = {
            log.info(s"establish ws success!")
          }

          def failureFunc(): Unit = {
            Boot.addToPlatform {
              WarningDialog.initWarningDialog("连接失败！")
            }
          }

          val url = Routes.linkRoomManager(userInfo.get.userId, userInfo.get.token, roomInfo.map(_.roomId).get)
          buildWebSocket(ctx, url, Right(hostController), successFunc(), failureFunc())
          Behaviors.same

        case msg: GetSender =>
          hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer, Some(msg.sender), hostStatus)


        case HeartBeat =>
          timer.cancel(PingTimeOut)
          timer.startSingleTimer(PingTimeOut, PingTimeOut, 30.seconds)
          Behaviors.same


        case PingTimeOut =>
          log.info(s"lose connection with roomManager!")
          //ws断了
          //          timer.cancel(HeartBeat)
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("连接断开！")
          }
          Behaviors.same


        case BackToHome =>
          timer.cancel(HeartBeat)
          timer.cancel(PingTimeOut)
          sender.foreach(_ ! CompleteMsgClient)
          val playId = if(hostStatus == HostStatus.CONNECT)
            Ids.getPlayId(audienceStatus = AudienceStatus.CONNECT, roomId = roomInfo.get.roomId)
          else
            Ids.getPlayId(audienceStatus = AudienceStatus.LIVE, roomId = roomInfo.get.roomId)
          mediaPlayer.stop(playId, hostScene.resetBack)
          liveManager ! LiveManager.StopPullAll
          liveManager ! LiveManager.StopPush
          liveManager ! LiveManager.DeviceOff
          Boot.addToPlatform {
            homeController.foreach(_.showScene())
          }
          System.gc()
          switchBehavior(ctx, "idle", idle(stageCtx, liveManager, mediaPlayer, homeController))

        case HostLiveReq =>
          log.debug(s"Host req live.")
          assert(userInfo.nonEmpty && roomInfo.nonEmpty)
          sender.foreach(_ ! StartLiveReq(userInfo.get.userId, userInfo.get.token, ClientType.PC))
          Behaviors.same

        case msg: StartLive =>
          log.info(s"${msg.liveId} start live.")
          hostController.isLive = true
          ctx.self ! ChangeMode(Some(true), None, None)
          liveManager ! LiveManager.PushStream(msg.liveId, msg.liveCode)
          Behaviors.same

        case InviteReq(email, meeting) =>
          sender.foreach(_ ! Invite(email, meeting))
          Behaviors.same

        case msg: ChangeOption =>
          liveManager ! LiveManager.ChangeMediaOption(msg.bit, msg.re, msg.frameRate, msg.needImage, msg.needSound, hostScene.resetLoading)
          Behaviors.same

        case msg: AudienceAcceptance =>
          log.debug(s"accept join user-${msg.userId} join.")
          liveManager ! LiveManager.SwitchMediaMode(isJoin = true, hostScene.resetBack)
          assert(roomInfo.nonEmpty)
          sender.foreach(_ ! JoinAccept(roomInfo.get.roomId, msg.userId, ClientType.PC, msg.accept))
          Behaviors.same

        case HostStartMeetingRecord =>
          assert(roomInfo.nonEmpty)
          sender.foreach(_ ! StartMeetingRecord)
          Behaviors.same

        case HostFinishMeeting => //主持人结束会议，房间内所有流都停止
          log.debug(s"videoMeeting ${roomInfo.get.roomId} stop.")
          timer.cancel(HeartBeat)
          timer.cancel(PingTimeOut)
          sender.foreach(_ ! CompleteMsgClient)
          liveManager ! LiveManager.SwitchMediaMode(isJoin = false, hostScene.resetBack)
          //          val playId = if(hostStatus == HostStatus.CONNECT)
          //            Ids.getPlayId(audienceStatus = AudienceStatus.CONNECT, roomId = roomInfo.get.roomId)
          //          else
          //            Ids.getPlayId(audienceStatus = AudienceStatus.LIVE, roomId = roomInfo.get.roomId)
          //          mediaPlayer.stop(playId, hostScene.resetBack)
          liveManager ! LiveManager.StopPullAll
          liveManager ! LiveManager.StopPush
          System.gc()
          hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer, sender, hostStatus = HostStatus.LIVE)

        case msg: JoinBegin =>
          log.debug(s"======== ${msg.audienceInfo.userName} join begin")
          val info = PullInfo(roomInfo.get.roomId,hostScene.gc)
          liveManager ! LiveManager.PullStream(msg.audienceInfo.liveId, pullInfo = info)
          if(hostStatus == HostStatus.LIVE)
            hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer, sender, HostStatus.CONNECT)
          else
            Behaviors.same

        case StopSelf =>
          log.info(s"rmManager stopped in host.")
          Behaviors.stopped

        case ForceExit(userId4Member, userName4Member) =>
          log.debug("send forceexit to roomManager...")
          sender.foreach(_ ! AuthProtocol.ForceExit(userId4Member, userName4Member))
          Behaviors.same

        case BanOnMember(userId4Member, image, sound) =>
          log.debug(s"send banOnMember-${image}-${sound} to roomManager...")
          sender.foreach(_ ! AuthProtocol.BanOnMember(userId4Member, image, sound))
          Behaviors.same

        case CancelBan(userId4Member, image, sound) =>
          log.debug(s"send cancelBan-${image}-${sound} to roomManager...")
          sender.foreach(_ ! AuthProtocol.CancelBan(userId4Member, image, sound))
          Behaviors.same

        case x =>
          log.warn(s"unknown msg in host: $x")
          Behaviors.unhandled
      }
    }

  private def audienceBehavior(
    stageCtx: StageContext,
    homeController: Option[HomeController] = None,
    roomController: Option[RoomController] = None,
    audienceScene: AudienceScene,
    audienceController: AudienceController,
    liveManager: ActorRef[LiveManager.LiveCommand],
    mediaPlayer: MediaPlayer,
    sender: Option[ActorRef[WsMsgFront]] = None,
    isStop: Boolean = false,
    audienceStatus: Int = AudienceStatus.LIVE //0-观看直播， 1-连线, 2-观看录像
  )(
    implicit stashBuffer: StashBuffer[RmCommand],
    timer: TimerScheduler[RmCommand]
  ): Behavior[RmCommand] =
    Behaviors.receive[RmCommand] { (ctx, msg) =>
      msg match {
        case AudienceWsEstablish =>
          //与roomManager建立ws
          val userFuture = if (userInfo.nonEmpty) { //观众已登录
            Future((userInfo.get.userId, userInfo.get.token))
          } else if (guestInfo.nonEmpty) { //已经申请过临时账号
            Future((guestInfo.get.userId, guestInfo.get.token))
          } else { //申请临时账号
            RMClient.getTemporaryUser.map {
              case Right(rst) =>
                if (rst.errCode == 0) {
                  val info = rst.userInfoOpt.get
                  this.guestInfo = Some(info)
                  (info.userId, info.token)
                } else (-1L, "")

              case Left(_) => (-1L, "")
            }
          }

          userFuture.onComplete {
            case Success(user) =>
              if (user._1 != -1L && user._2.nonEmpty) {
                def successFunc(): Unit = {
                  if (userInfo.nonEmpty) {
//                    ctx.self ! SendJudgeLike(JudgeLike(userInfo.get.userId, audienceScene.getRoomInfo.roomId))
                  }
                }

                def failureFunc(): Unit = {
//                  val playId = s"room${audienceScene.getRoomInfo.roomId}"
//                  mediaPlayer.stop(playId, audienceScene.resetBack)
                  Boot.addToPlatform {
                    WarningDialog.initWarningDialog("连接失败！")
                  }
                }

                val url = Routes.linkRoomManager(user._1, user._2, audienceScene.getRoomInfo.roomId)
                buildWebSocket(ctx, url, Left(audienceController), successFunc(), failureFunc())
              } else {
                log.warn(s"User-$user is invalid.")
              }
            case Failure(error) =>
              log.error(s"userFuture failed in audience ws building: $error")
          }
          Behaviors.same


        case msg: GetSender =>
          audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, Some(msg.sender), isStop, audienceStatus)

        case HeartBeat =>
          timer.cancel(PingTimeOut)
          timer.startSingleTimer(PingTimeOut, PingTimeOut, 30.seconds)
          Behaviors.same

        case PingTimeOut =>
          //ws断了
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("连接断开！")
          }
          Behaviors.same

        case BackToHome =>
          log.debug(s"audience back to previous page.")
          timer.cancel(HeartBeat)
          timer.cancel(PingTimeOut)
          sender.foreach(_ ! CompleteMsgClient)

          audienceStatus match {
            case AudienceStatus.LIVE =>
              liveManager ! LiveManager.StopPullAll
              val playId = Ids.getPlayId(audienceStatus, roomId = audienceScene.getRoomInfo.roomId)
              mediaPlayer.stop(playId, audienceScene.autoReset)
            case AudienceStatus.CONNECT =>
              assert(userInfo.nonEmpty)
              val userId = userInfo.get.userId
              liveManager ! LiveManager.StopPullAll
              val playId = Ids.getPlayId(audienceStatus, roomId = audienceScene.getRoomInfo.roomId)
              mediaPlayer.stop(playId, audienceScene.autoReset)
              liveManager ! LiveManager.StopPush
              liveManager ! LiveManager.DeviceOff
            case _ =>
              //do nothing
          }

          Boot.addToPlatform {
            audienceScene.stopPackageLoss()
            roomController.foreach {
              r =>
                r.showScene()
            }
          }
          audienceScene.stopPackageLoss()
          audienceScene.finalize()
          System.gc()
          switchBehavior(ctx, "idle", idle(stageCtx, liveManager, mediaPlayer, homeController, roomController))

        case msg: JoinRoomReq =>
          assert(userInfo.nonEmpty)
          val userId = userInfo.get.userId
          liveManager ! LiveManager.SwitchMediaMode(isJoin = true, audienceScene.resetBack)
          sender.foreach(_ ! JoinReq(userId, msg.roomId, ClientType.PC))
          Behaviors.same

        case msg: ChangeOption4Audience =>
          audienceStatus match{
            case AudienceStatus.CONNECT =>
              log.debug(s"change option in connect image=${msg.needImage}, sound=${msg.needSound}")
              liveManager ! LiveManager.ChangeMediaOption(None, None, None, msg.needImage, msg.needSound, () => audienceScene.loadingBack())
              Behaviors.same

            case AudienceStatus.LIVE =>
              val playId = Ids.getPlayId(audienceStatus, roomId = audienceScene.getRoomInfo.roomId)
              mediaPlayer.stop(playId, audienceScene.autoReset)
              mediaPlayer.needImage(msg.needImage)
              mediaPlayer.needSound(msg.needSound)
              liveManager ! LiveManager.StopPullAll

            case _ =>
              log.info("audienceState is record!!!")
          }

          Behaviors.same

        case PullerStopped =>
          assert(userInfo.nonEmpty)
          log.info(s"options were set, continue to play")
          val info = PullInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
          if(audienceStatus == AudienceStatus.LIVE)
            liveManager ! LiveManager.PullStream(audienceScene.liveId4Live.get, pullInfo = info)
          else if(audienceStatus == AudienceStatus.CONNECT)
            liveManager ! LiveManager.PullStream(audienceScene.liveId4Connect.get, pullInfo = info)
          else
            log.info("audiencestatus is unkonwn...")
          Behaviors.same

        case msg: StartJoin =>
          log.info(s"Start join.")
          liveManager ! LiveManager.PushStream(msg.audienceLiveInfo.liveId, msg.audienceLiveInfo.liveCode)
          audienceScene.audienceStatus = AudienceStatus.CONNECT
          audienceScene.autoReset()
          val playId = Ids.getPlayId(AudienceStatus.LIVE, roomId = audienceScene.getRoomInfo.roomId)
          mediaPlayer.stop(playId, audienceScene.autoReset)
          timer.startSingleTimer(PullDelay, PullStream4Others(msg.hostLiveId :: msg.attendLiveId.filter(_ != msg.audienceLiveInfo.liveId)), 1.seconds)
          audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, sender, isStop, AudienceStatus.CONNECT)

        case msg: PullStream4Others =>
          timer.cancel(PullDelay)
          msg.liveId.foreach{ l =>
            val info = PullInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
            liveManager ! LiveManager.PullStream(l, pullInfo = info, audienceScene = Some(audienceScene))
          }
          Behaviors.same

        case msg: OtherAudienceJoin =>
          log.info(s"${ctx.self} receive a msg $msg")
          val info = PullInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
          liveManager ! LiveManager.PullStream(msg.liveId, pullInfo = info, audienceScene = Some(audienceScene))
          Behaviors.same

        case MeetingFinished =>
          timer.cancel(HeartBeat)
          timer.cancel(PingTimeOut)
          sender.foreach(_ ! CompleteMsgClient)
          liveManager ! LiveManager.SwitchMediaMode(isJoin = false, audienceScene.resetBack)
          liveManager ! LiveManager.StopPullAll
          liveManager ! LiveManager.StopPush
          System.gc()

          Behavior.same

        case msg: ExitJoin =>
          log.debug("disconnection with host.")
          liveManager ! LiveManager.SwitchMediaMode(isJoin = false, audienceScene.resetBack)
          if (audienceStatus == AudienceStatus.CONNECT) {
            sender.foreach(_ ! AudienceShutJoin(msg.roomId, msg.userId))
            ctx.self ! StopJoinAndWatch
          }
          Behaviors.same

        case StopJoinAndWatch =>
          assert(userInfo.nonEmpty)

          if (audienceStatus == AudienceStatus.CONNECT) {

            audienceScene.audienceStatus = AudienceStatus.LIVE

            /*停止播放rtp混流*/
            val playId = Ids.getPlayId(AudienceStatus.CONNECT2Third, roomId = audienceScene.getRoomInfo.roomId)
            mediaPlayer.stop(playId, audienceScene.autoReset)

            /*断开连线，停止推拉*/
            liveManager ! LiveManager.StopPullAll
            liveManager ! LiveManager.StopPush
            liveManager ! LiveManager.DeviceOff

            /*恢复主播播放*/
//            val info = PullInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
//            liveManager ! LiveManager.PullStream(audienceScene.liveId4Live.get, pullInfo = info)
          }

          audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, sender, isStop, AudienceStatus.LIVE)

        case StopSelf =>
          log.info(s"rmManager stopped in audience.")
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in audience: $x")
          Behaviors.unhandled
      }

    }


  def buildWebSocket(
    ctx: ActorContext[RmCommand],
    url: String,
    controller: Either[AudienceController, HostController],
    successFunc: => Unit,
    failureFunc: => Unit)(
    implicit timer: TimerScheduler[RmCommand]
  ): Unit = {
    log.debug(s"build ws with roomManager: $url")
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
    val source = getSource(ctx.self)

    val sink = controller match {
      case Right(hc) => getRMSink(hController = Some(hc))
      case Left(ac) => getRMSink(aController = Some(ac))
    }
    val (stream, response) =
      source
        .viaMat(webSocketFlow)(Keep.both)
        .toMat(sink)(Keep.left)
        .run()
    val connected = response.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        ctx.self ! GetSender(stream)
        successFunc
        Future.successful(s"link room manager success.")
      } else {
        failureFunc
        throw new RuntimeException(s"link room manager failed: ${upgrade.response.status}")
      }
    } //链接建立时
    connected.onComplete(i => log.info(i.toString))
  }


  def getSource(rmManager: ActorRef[RmCommand]): Source[BinaryMessage.Strict, ActorRef[WsMsgFront]] =
    ActorSource.actorRef[WsMsgFront](
      completionMatcher = {
        case CompleteMsgClient =>
          log.info("disconnected from room manager.")
      },
      failureMatcher = {
        case FailMsgClient(ex) ⇒
          log.error(s"ws failed: $ex")
          ex
      },
      bufferSize = 8,
      overflowStrategy = OverflowStrategy.fail
    ).collect {
      case message: WsMsgClient =>
        //println(message)
        val sendBuffer = new MiddleBufferInJvm(409600)
        BinaryMessage.Strict(ByteString(
          message.fillMiddleBuffer(sendBuffer).result()
        ))
    }

  def getRMSink(
    hController: Option[HostController] = None,
    aController: Option[AudienceController] = None
  )(
    implicit timer: TimerScheduler[RmCommand]
  ): Sink[Message, Future[Done]] = {
    Sink.foreach[Message] {
      case TextMessage.Strict(msg) =>
        hController.foreach(_.wsMessageHandle(TextMsg(msg)))
        aController.foreach(_.wsMessageHandle(TextMsg(msg)))

      case BinaryMessage.Strict(bMsg) =>
        val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
        val message = bytesDecode[WsMsgRm](buffer) match {
          case Right(rst) =>
            rst
          case Left(_) =>
            DecodeError
        }
        hController.foreach(_.wsMessageHandle(message))
        aController.foreach(_.wsMessageHandle(message))


      case msg: BinaryMessage.Streamed =>
        val futureMsg = msg.dataStream.runFold(new ByteStringBuilder().result()) {
          case (s, str) => s.++(str)
        }
        futureMsg.map { bMsg =>
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val message = bytesDecode[WsMsgRm](buffer) match {
            case Right(rst) => rst
            case Left(_) => DecodeError
          }
          hController.foreach(_.wsMessageHandle(message))
          aController.foreach(_.wsMessageHandle(message))
        }

      case _ => //do nothing
      log.debug("nothing")

    }
  }


}
