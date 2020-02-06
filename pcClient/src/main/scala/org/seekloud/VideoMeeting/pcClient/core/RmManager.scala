package org.seekloud.VideoMeeting.pcClient.core

import java.io.File

import akka.Done
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.scaladsl.AskPattern._
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
import org.seekloud.VideoMeeting.pcClient.controller.{AudienceController, HomeController, HostController, RoomController, InviteController}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.pcClient.Boot.{executor, materializer, scheduler, system, timeout}
import org.seekloud.VideoMeeting.pcClient.common.Constants.{AudienceStatus, HostStatus}
import org.seekloud.VideoMeeting.pcClient.common._
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.player.VideoPlayer
import org.seekloud.VideoMeeting.pcClient.core.stream.LiveManager.{JoinInfo, WatchInfo}
import org.seekloud.VideoMeeting.pcClient.core.stream.LiveManager
import org.seekloud.VideoMeeting.pcClient.scene.{AudienceScene, HomeScene, HostScene, RoomScene}
import org.seekloud.VideoMeeting.pcClient.utils.RMClient
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.player.sdk.MediaPlayer

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

  var userInfo: Option[UserInfo] = Some(UserInfo(100l, "a", "", "", 1000000l, false))
  var roomInfo: Option[RoomInfo] = Some(RoomInfo(10000l, "as", "", "", 100l, "", ""))
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

  final case class GetRecordDetail(recordInfo: RecordInfo) extends RmCommand

  final case class GoToWatch(roomInfo: RoomInfo) extends RmCommand

  final case class GoToRecord(recordInfo: RecordInfo, url: String) extends RmCommand

  final case class GetSender(sender: ActorRef[WsMsgFront]) extends RmCommand

//  final case class ChangeHeader(newHeaderUrl: String) extends RmCommand

//  final case class ChangeCover(newCoverUrl: String) extends RmCommand

  final case class ChangeUserName(newUserName: String) extends RmCommand

  final case object BackToHome extends RmCommand

  final case object HeartBeat extends RmCommand

  final case object PingTimeOut extends RmCommand

  final case object StopSelf extends RmCommand

  final case object Logout extends RmCommand


  /*主播*/

  final case object HostWsEstablish extends RmCommand

  final case object HostLiveReq extends RmCommand

  final case class StartLive(liveId: String, liveCode: String) extends RmCommand

  final case object StopLive extends RmCommand

  final case class ModifyRoom(name: Option[String], des: Option[String]) extends RmCommand

  final case class ChangeMode(isJoinOpen: Option[Boolean], aiMode: Option[Int], screenLayout: Option[Int]) extends RmCommand

  final case class RecordOption(recordOrNot: Boolean, path: Option[String] = None) extends RmCommand

  final case class ChangeOption(bit: Option[Int], re: Option[String], frameRate: Option[Int], needImage: Boolean = true, needSound: Boolean = true, recordOrNot: Boolean = false) extends RmCommand

  final case class AudienceAcceptance(userId: Long, accept: Boolean) extends RmCommand

  final case class StartMeeting(userId: List[Long]) extends RmCommand

  final case class JoinBegin(audienceInfo: AudienceInfo, liveInfo: LiveInfo) extends RmCommand //开始和某观众连线

  final case object JoinStop extends RmCommand //停止和某观众连线

  final case object ShutJoin extends RmCommand //主动关闭和某观众的连线


  /*观众*/
  final case object GetPackageLoss extends RmCommand

  final case class ChangeOption4Audience(needImage: Boolean = true, needSound: Boolean = true) extends RmCommand

  final case object PullerStopped extends RmCommand

  final case object AudienceWsEstablish extends RmCommand

  final case class SendComment(comment: Comment) extends RmCommand

  final case class SendJudgeLike(judgeLike: JudgeLike) extends RmCommand

  final case class SendLikeRoom(likeRoom: LikeRoom) extends RmCommand

  final case class JoinRoomReq(roomId: Long) extends RmCommand

  final case class StartJoin(hostLiveId: String, audienceLiveInfo: LiveInfo) extends RmCommand //开始和主播连线

  final case object StopJoinAndWatch extends RmCommand //停止和主播连线

  final case class ExitJoin(roomId: Long, userId: Long) extends RmCommand //主动关闭和主播的连线

  final case class StartRecord(outFilePath: String) extends  RmCommand //开始录制

  final case object StopRecord extends RmCommand  //结束录制

  final case class PausePlayRec(recordInfo: RecordInfo) extends RmCommand  //暂停播放录像

  final case class ContinuePlayRec(recordInfo: RecordInfo) extends RmCommand //继续播放录像

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
                      WarningDialog.initWarningDialog(s"processor还没准备好哦~~~")
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

        case msg: GetRecordDetail =>
          RMClient.searchRecord(msg.recordInfo.roomId, msg.recordInfo.startTime, userInfo.map(_.userId)).onComplete {
            case Success(rst) =>
              rst match {
                case Right(record) =>
                  if (record.errCode == 0) {
                    ctx.self ! GoToRecord(msg.recordInfo, record.url)
                  } else {
                    Boot.addToPlatform {
                      roomController.foreach(_.removeLoading())
                      WarningDialog.initWarningDialog(s"processor还没准备好哦~~~")
                    }
                  }
                case Left(error) =>
                  log.error(s"search record rsp error: $error")
                  Boot.addToPlatform {
                    roomController.foreach(_.removeLoading())
                  }
              }
            case Failure(ex) =>
              log.error(s"search record-${msg.recordInfo.roomId} future error: $ex")
              Boot.addToPlatform {
                roomController.foreach(_.removeLoading())
              }
          }
          Behaviors.same

        case msg: GoToWatch =>
          val audienceScene = new AudienceScene(msg.roomInfo.toAlbum)
          val audienceController = new AudienceController(stageCtx, audienceScene, ctx.self)
          if (msg.roomInfo.rtmp.nonEmpty) {
            //            audienceScene.watchUrl = msg.roomInfo.rtmp
            //            playManager ! PlayManager.StartPlay(msg.roomInfo.roomId, audienceScene.gc, msg.roomInfo.rtmp.get)
            audienceScene.liveId = msg.roomInfo.rtmp
            val info = WatchInfo(msg.roomInfo.roomId, audienceScene.gc)
            liveManager ! LiveManager.PullStream(msg.roomInfo.rtmp.get, watchInfo = Some(info), audienceScene = Some(audienceScene))

            ctx.self ! AudienceWsEstablish

            Boot.addToPlatform {
              roomController.foreach(_.removeLoading())
              audienceController.showScene()
            }
            switchBehavior(ctx, "audienceBehavior", audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, audienceLiveInfo = None, audienceStatus = AudienceStatus.LIVE))
          } else {
            Boot.addToPlatform {
              roomController.foreach(_.removeLoading())
              WarningDialog.initWarningDialog("processor没有给我们liveId哦~~")
            }
            Behaviors.same
          }

        case msg: GoToRecord =>
          val audienceScene = new AudienceScene(msg.recordInfo.toAlbum, isRecord = true, msg.url)
          val audienceController = new AudienceController(stageCtx, audienceScene, ctx.self)
          audienceScene.playRecord()
          /*media player播放*/
          //          val playId = Ids.getPlayId(AudienceStatus.RECORD, roomId = Some(msg.recordInfo.roomId), startTime = Some(msg.recordInfo.startTime))
          //          val videoPlayer = ctx.spawn(VideoPlayer.create(playId), s"videoPlayer$playId")
          //          mediaPlayer.start(playId, videoPlayer, Left(msg.url), Some(audienceScene.gc), None)
          Boot.addToPlatform {
            roomController.foreach(_.removeLoading())
            audienceController.showScene()
          }
          switchBehavior(ctx, "audienceBehavior", audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, audienceLiveInfo = None, audienceStatus = AudienceStatus.RECORD))

//        case msg: ChangeHeader =>
//          log.info("change header.")
//          this.userInfo = userInfo.map(_.copy(headImgUrl = msg.newHeaderUrl))
//          homeController.foreach(_.showScene())
//          Behaviors.same

//        case msg: ChangeCover =>
//          log.info("change cover.")
//          this.roomInfo = roomInfo.map(_.copy(coverImgUrl = msg.newCoverUrl))
//          Behaviors.same

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
    joinAudience: Option[AudienceInfo] = None
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
//            hostScene.allowConnect()
            //            Boot.addToPlatform {
            //              hostController.showScene()
            //            }
          }

          def failureFunc(): Unit = {
//            liveManager ! LiveManager.DeviceOff
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
          if (hostStatus == HostStatus.CONNECT) {
            //            playManager ! PlayManager.StopPlay(roomInfo.get.roomId, hostScene.resetBack, joinAudience.map(_.userId))
            val playId = joinAudience match {
              case Some(joinAud) =>
                Ids.getPlayId(audienceStatus = AudienceStatus.CONNECT, roomId = Some(roomInfo.get.roomId),audienceId = Some(joinAud.userId))
              case None =>
                Ids.getPlayId(audienceStatus = AudienceStatus.LIVE, roomId = Some(roomInfo.get.roomId))
            }
            mediaPlayer.stop(playId, hostScene.resetBack)
            liveManager ! LiveManager.StopPull
          }
          liveManager ! LiveManager.StopPush
          liveManager ! LiveManager.DeviceOff
          Boot.addToPlatform {
            hostScene.stopPackageLoss()
            homeController.foreach(_.showScene())
          }
          hostScene.stopPackageLoss()
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
          Boot.addToPlatform {
            hostScene.allowConnect()
          }
          liveManager ! LiveManager.PushStream(msg.liveId, msg.liveCode)
          Behaviors.same

        case StopLive =>
          liveManager ! LiveManager.StopPush
          sender.foreach(_ ! HostStopPushStream(roomInfo.get.roomId))
          hostController.isLive = false
          Behaviors.same

        case StartRecord(outFilePath) =>
          mediaPlayer.startRecord(outFilePath)
          log.debug(s"rmManager send startRecord.")
          Behaviors.same

        case StopRecord =>
          mediaPlayer.stopRecord()
          log.debug(s"rmManager send stopRecord.")
          Behaviors.same

        case msg: ModifyRoom =>
          sender.foreach(_ ! ModifyRoomInfo(msg.name, msg.des))
          Behaviors.same

        case msg: ChangeMode =>
          sender.foreach(_ ! ChangeLiveMode(msg.isJoinOpen, msg.aiMode, msg.screenLayout))
          Behaviors.same

        case msg: ChangeOption =>
          liveManager ! LiveManager.ChangeMediaOption(msg.bit, msg.re, msg.frameRate, msg.needImage, msg.needSound, hostScene.resetLoading)
          Behaviors.same

        case msg: RecordOption =>
          liveManager ! LiveManager.RecordOption(msg.recordOrNot, msg.path, hostScene.resetLoading)
          Behaviors.same

        case msg: AudienceAcceptance =>
          log.debug(s"accept join user-${msg.userId} join.")
          assert(roomInfo.nonEmpty)
          sender.foreach(_ ! JoinAccept(roomInfo.get.roomId, msg.userId, ClientType.PC, msg.accept))
          Behaviors.same

//        case msg: StartMeeting =>
//          log.debug(s"accept join user-${msg.userId} join.")
//          assert(roomInfo.nonEmpty)
//          sender.foreach(_ ! startMeeting( msg.userId, ClientType.PC))
//          Behaviors.same

        case msg: JoinBegin =>
          /*背景改变*/
          hostScene.resetBack()

          /*媒体画面模式更改*/
          liveManager ! LiveManager.SwitchMediaMode(isJoin = true, reset = hostScene.resetBack)

          /*拉取观众的rtp流并播放*/
          val joinInfo = JoinInfo(roomInfo.get.roomId, msg.audienceInfo.userId, hostScene.gc)
          liveManager ! LiveManager.PullStream(msg.liveInfo.liveId, joinInfo = Some(joinInfo), hostScene = Some(hostScene))

          hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer, sender, hostStatus = HostStatus.CONNECT, Some(msg.audienceInfo))

        case ShutJoin =>
          log.debug("disconnection with current audience.")
          assert(roomInfo.nonEmpty)
          if (hostStatus == HostStatus.CONNECT) {
            Boot.addToPlatform {
              hostScene.connectionStateText.setText(s"目前状态：无连接~")
              hostScene.connectStateBox.getChildren.remove(hostScene.shutConnectionBtn)
              hostController.isConnecting = false
            }
            sender.foreach(_ ! HostShutJoin(roomInfo.get.roomId))
            ctx.self ! JoinStop
          }
          Behaviors.same

        case JoinStop =>
          /*媒体画面模式更改*/
          liveManager ! LiveManager.SwitchMediaMode(isJoin = false, hostScene.resetBack)

          /*停止播放和拉取观众rtp流*/
          //          playManager ! PlayManager.StopPlay(roomInfo.get.roomId, hostScene.resetBack, joinAudience.map(_.userId))
          val playId = joinAudience match {
            case Some(joinAud) =>
              Ids.getPlayId(audienceStatus = AudienceStatus.CONNECT, roomId = Some(roomInfo.get.roomId),audienceId = Some(joinAud.userId))
            case None =>
              Ids.getPlayId(audienceStatus = AudienceStatus.LIVE, roomId = Some(roomInfo.get.roomId))

          }
          mediaPlayer.stop(playId, hostScene.resetBack)
          liveManager ! LiveManager.StopPull
          hostBehavior(stageCtx, homeController, hostScene, hostController, liveManager, mediaPlayer, sender, hostStatus = HostStatus.LIVE, None)

        case StopSelf =>
          log.info(s"rmManager stopped in host.")
          Behaviors.stopped

        case msg: SendComment =>
//          log.debug(s"sending ${msg.comment}")
          sender.foreach(_ ! msg.comment)
          Behaviors.same

        case GetPackageLoss =>
          liveManager ! LiveManager.GetPackageLoss
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
    audienceLiveInfo: Option[(LiveInfo, String)],
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
                    ctx.self ! SendJudgeLike(JudgeLike(userInfo.get.userId, audienceScene.getRoomInfo.roomId))
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
          audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, Some(msg.sender), isStop, audienceLiveInfo, audienceStatus)

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
              liveManager ! LiveManager.StopPull
              val playId = Ids.getPlayId(audienceStatus, roomId = Some(audienceScene.getRoomInfo.roomId))
              mediaPlayer.stop(playId, audienceScene.autoReset)
            case AudienceStatus.CONNECT =>
              assert(userInfo.nonEmpty)
              val userId = userInfo.get.userId
              liveManager ! LiveManager.StopPull
              val playId = Ids.getPlayId(audienceStatus, roomId = Some(audienceScene.getRoomInfo.roomId), audienceId = Some(userId))
              mediaPlayer.stop(playId, audienceScene.autoReset)
              liveManager ! LiveManager.StopPush
              liveManager ! LiveManager.DeviceOff
            case AudienceStatus.RECORD =>
//              val playId = s"record${audienceScene.getRecordInfo.roomId}time${audienceScene.getRecordInfo.startTime}"
//              mediaPlayer.stop(playId, audienceScene.resetBack)
              audienceScene.pauseRecord()
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

        case msg: SendComment =>
          sender.foreach(_ ! msg.comment)
          Behaviors.same

        case msg: SendJudgeLike =>
          sender.foreach(_ ! msg.judgeLike)
          log.debug(s"audience send judgeLike.")
          Behaviors.same

        case msg: SendLikeRoom =>
          sender.foreach(_ ! msg.likeRoom)
          log.debug(s"audience send a like, UpDown:${msg.likeRoom.upDown}")
          Behaviors.same

        case msg: JoinRoomReq =>
          assert(userInfo.nonEmpty)
          val userId = userInfo.get.userId
          sender.foreach(_ ! JoinReq(userId, msg.roomId, ClientType.PC))
          Behaviors.same

        case msg: ChangeOption4Audience =>
          assert(userInfo.nonEmpty)
          val rst = liveManager ? LiveManager.Ask4State
          rst.onComplete {
            case Success(isStart) =>
              log.info(s"change option need image: ${msg.needImage}, need sound: ${msg.needSound}")
              val userId = userInfo.get.userId

              val playId = audienceStatus match {
                case AudienceStatus.LIVE => Ids.getPlayId(audienceStatus, roomId = Some(audienceScene.getRoomInfo.roomId))
                case AudienceStatus.CONNECT => Ids.getPlayId(audienceStatus, roomId = Some(audienceScene.getRoomInfo.roomId), audienceId = Some(userId))
                case AudienceStatus.RECORD => Ids.getPlayId(audienceStatus, roomId = Some(audienceScene.getRecordInfo.roomId), startTime = Some(audienceScene.getRecordInfo.startTime))
              }
              if(isStart) {
                log.info(s"player has started, now stop it")
                mediaPlayer.stop(playId, audienceScene.autoReset)
              }
              mediaPlayer.needImage(msg.needImage)
              mediaPlayer.needSound(msg.needSound)

              audienceStatus match {
                case AudienceStatus.LIVE =>
                  if (isStart) {
                    liveManager ! LiveManager.StopPull
                  }
                  else {
                    val info = WatchInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
                    liveManager ! LiveManager.PullStream(audienceScene.liveId.get, watchInfo = Some(info))
                  }

                case AudienceStatus.CONNECT =>
                  audienceLiveInfo.foreach { i =>
                    liveManager ! LiveManager.StopPull

                  }
                case AudienceStatus.RECORD =>

              }

            case Failure(e) =>
              log.info(s"get player isStart error: ${e.getMessage}")
              timer.startSingleTimer(ASK4STATE_RETRY_TIMER_KEY, msg, 100 milliseconds)
          }


          Behaviors.same

        case PullerStopped =>
          assert(userInfo.nonEmpty)
          log.info(s"options were set, continue to play")
          val userId = userInfo.get.userId
          audienceStatus match {
            case AudienceStatus.LIVE =>
              val info = WatchInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
              liveManager ! LiveManager.PullStream(audienceScene.liveId.get, watchInfo = Some(info))

            case AudienceStatus.CONNECT =>
              audienceLiveInfo.foreach { i =>
                val joinInfo = JoinInfo(
                  audienceScene.getRoomInfo.roomId, //观看房间id
                  userId, //观众id
                  audienceScene.gc //观众页画布gc
                )
                liveManager ! LiveManager.PullStream(i._2, joinInfo = Some(joinInfo))
              }
            case AudienceStatus.RECORD => // do nothing

          }
          Behaviors.same

        case msg: StartJoin =>
          log.info(s"Start join.")
          assert(userInfo.nonEmpty)

          val userId = userInfo.get.userId

          /*背景改变*/
          audienceScene.audienceStatus = AudienceStatus.CONNECT
          audienceScene.autoReset()

          /*暂停第三方播放*/
          val playId = Ids.getPlayId(AudienceStatus.LIVE, roomId = Some(audienceScene.getRoomInfo.roomId))
//            s"room${audienceScene.getRoomInfo.roomId}"
          mediaPlayer.stop(playId, audienceScene.autoReset)

          /*开启媒体设备，开始推流*/
          liveManager ! LiveManager.StopPull
          liveManager ! LiveManager.DevicesOn(audienceScene.gc, isJoin = true)
          liveManager ! LiveManager.PushStream(msg.audienceLiveInfo.liveId, msg.audienceLiveInfo.liveCode)

          /*开始拉取并播放主播rtp流*/
          val joinInfo = JoinInfo(
            audienceScene.getRoomInfo.roomId, //观看房间id
            userId, //观众id
            audienceScene.gc //观众页画布gc
          )

          liveManager ! LiveManager.PullStream(msg.hostLiveId, joinInfo = Some(joinInfo))
          audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, sender, isStop, Some((msg.audienceLiveInfo, msg.hostLiveId)), audienceStatus = AudienceStatus.CONNECT)

        case msg: ExitJoin =>
          log.debug("disconnection with host.")
          if (audienceStatus == AudienceStatus.CONNECT) {
            sender.foreach(_ ! AudienceShutJoin(msg.roomId, msg.userId))
            ctx.self ! StopJoinAndWatch
          }
          Behaviors.same

        case StopJoinAndWatch =>
          assert(userInfo.nonEmpty)

          if (audienceStatus == AudienceStatus.CONNECT) {

            audienceScene.audienceStatus = AudienceStatus.LIVE

            /*停止播放主播rtp流*/
            val userId = userInfo.get.userId
            val playId = Ids.getPlayId(AudienceStatus.CONNECT, roomId = Some(audienceScene.getRoomInfo.roomId), audienceId = Some(userId))
            //            s"room${audienceScene.getRoomInfo.roomId}-audience$userId"
            mediaPlayer.stop(playId, audienceScene.autoReset)

            /*断开连线，停止推拉*/
            liveManager ! LiveManager.StopPull
            liveManager ! LiveManager.StopPush
            liveManager ! LiveManager.DeviceOff

            /*恢复第三方播放*/
            val info = WatchInfo(audienceScene.getRoomInfo.roomId, audienceScene.gc)
            liveManager ! LiveManager.PullStream(audienceScene.liveId.get, watchInfo = Some(info))
          }

          audienceBehavior(stageCtx, homeController, roomController, audienceScene, audienceController, liveManager, mediaPlayer, sender, isStop, audienceLiveInfo, audienceStatus = AudienceStatus.LIVE)


        case StartRecord(outFilePath) =>
          mediaPlayer.startRecord(outFilePath)
          log.debug(s"rmManager send startRecord.")
          Behaviors.same

        case GetPackageLoss =>
          liveManager ! LiveManager.GetPackageLoss
          Behaviors.same

        case StopRecord =>
          mediaPlayer.stopRecord()
          log.debug(s"rmManager send stopRecord.")
          Behaviors.stopped

        case msg: PausePlayRec =>
          val playId = Ids.getPlayId(AudienceStatus.RECORD, roomId = Some(msg.recordInfo.roomId), startTime = Some(msg.recordInfo.startTime))
          mediaPlayer.pause(playId)
          log.debug(s"pause playReC")
          Behaviors.same

        case msg: ContinuePlayRec =>
          val playId = Ids.getPlayId(AudienceStatus.RECORD, roomId = Some(msg.recordInfo.roomId), startTime = Some(msg.recordInfo.startTime))
          mediaPlayer.continue(playId)
          log.debug(s"continue playReC")
          Behaviors.same

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
          case Right(rst) => rst
          case Left(_) => DecodeError
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

    }
  }


}
