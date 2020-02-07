package org.seekloud.VideoMeeting.pcClient.controller

import java.util.Timer

import akka.actor.typed.ActorRef
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common.{Constants, StageContext}
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.scene.AudienceScene
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.slf4j.LoggerFactory
import akka.actor.typed.{ActorRef, Behavior}
import org.seekloud.VideoMeeting.pcClient.Boot.executor
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.RmManager.HeartBeat
import org.seekloud.VideoMeeting.pcClient.scene.AudienceScene.AudienceSceneListener
import org.seekloud.VideoMeeting.pcClient.utils.RMClient
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, UserDes}

import scala.concurrent.Future

//import scala.concurrent.Future

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 17:00
  */
class AudienceController(
  context: StageContext,
  audienceScene: AudienceScene,
  rmManager: ActorRef[RmManager.RmCommand]
) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
//  var likeNum: Int = audienceScene.getRoomInfo.like
  var updateRecCmt = true

  def showScene(): Unit = {
    Boot.addToPlatform {
      //每 5秒更新一次留言
      if(audienceScene.getIsRecord) {
        Future{
          while (updateRecCmt) {
//            updateRecCommentList()
            Thread.sleep(5000)
          }
        }
      }
      context.switchScene(audienceScene.getScene, title = s"${audienceScene.getRoomInfo.userId}的会议室-${audienceScene.getRoomInfo.roomId}")
    }

  }

//  def updateRecCommentList(): Unit = {
//    RMClient.getRecCommentList(audienceScene.getRecordInfo.roomId, audienceScene.getRecordInfo.startTime).map {
//      case Right(rst) =>
//        if (rst.errCode == 0) {
//          Boot.addToPlatform {
////            log.debug(s"${System.currentTimeMillis()},update recCommentList success:${rst.recordCommentList}")
//            audienceScene.barrage.refreshRecBarrage(rst.recordCommentList)
//            audienceScene.recCommentBoard.updateCommentsList(rst.recordCommentList)
//          }
//        } else {
//          Boot.addToPlatform(
//            WarningDialog.initWarningDialog(s"${rst.msg}")
//          )
//        }
//      case Left(e) =>
//        log.error(s"getRecCommentList error: $e")
//        Boot.addToPlatform(
//          WarningDialog.initWarningDialog(s"获取评论失败:$e")
//        )
//    }
//  }

  def addRecComment(
    roomId:Long,          //录像的房间id
    recordTime:Long,      //录像的时间戳
    comment:String,       //评论内容
    commentTime:Long,     //评论的时间
    relativeTime:Long,    //相对视频的时间
    commentUid:Long,      //评论的用户id
    authorUidOpt:Option[Long] = None
  ): Unit = {
    RMClient.addRecComment(roomId, recordTime, comment, commentTime, relativeTime, commentUid, authorUidOpt).map {
      case Right(rst) =>
        if (rst.errCode == 0) {
          log.debug(s"audience send recordComment success: ${(roomId, recordTime, comment, commentTime, relativeTime, commentUid, authorUidOpt)}")
          //发送评论后重新获取评论列表
//          updateRecCommentList()
        } else {
          log.debug(s"rst: $rst")
          Boot.addToPlatform(
            WarningDialog.initWarningDialog(s"audience send recComment failed: ${rst.msg}")
          )
        }
      case Left(e) =>
        log.error(s"addRecComment error: $e")
        Boot.addToPlatform(
          WarningDialog.initWarningDialog(s"send recComment failed: $e")
        )
    }
  }

  audienceScene.setListener(new AudienceSceneListener {

    override def joinReq(roomId: Long): Unit = {
      if (RmManager.userInfo.nonEmpty) {
        WarningDialog.initWarningDialog("连线申请已发送！")
        rmManager ! RmManager.JoinRoomReq(roomId)
      } else {
        WarningDialog.initWarningDialog("请先登录哦~")
      }

    }

    override def quitJoin(roomId: Long, userId: Long): Unit = {
      if (RmManager.userInfo.nonEmpty) {
        rmManager ! RmManager.ExitJoin(roomId, userId)
      } else {
        WarningDialog.initWarningDialog("请先登录哦~")
      }
    }

    override def gotoHomeScene(): Unit = {
      updateRecCmt = false
      rmManager ! RmManager.BackToHome
    }

    override def setFullScreen(isRecord: Boolean): Unit = {
      if (!audienceScene.isFullScreen) {
        audienceScene.removeAllElement()
        //        context.getStage.setFullScreenExitHint("s")
        context.getStage.setFullScreen(true)
        if (isRecord) {
          audienceScene.recView.setLayoutX(0)
          audienceScene.recView.setLayoutY(0)
          audienceScene.recView.setFitWidth(context.getStageWidth)
          audienceScene.recView.setFitHeight(context.getStageHeight)
        }
        else {
          audienceScene.imgView.setLayoutX(0)
          audienceScene.imgView.setLayoutY(0)
          audienceScene.imgView.setWidth(context.getStageWidth)
          audienceScene.imgView.setHeight(context.getStageHeight)
          audienceScene.statisticsCanvas.setLayoutX(0)
          audienceScene.statisticsCanvas.setLayoutY(0)
          audienceScene.statisticsCanvas.setWidth(context.getStageWidth)
          audienceScene.statisticsCanvas.setHeight(context.getStageHeight)
          audienceScene.gc.drawImage(audienceScene.backImg, 0, 0, context.getStageWidth, context.getStageHeight)
        }
        audienceScene.isFullScreen = true
      }
    }

    override def exitFullScreen(isRecord: Boolean): Unit = {
      if (audienceScene.isFullScreen) {
        audienceScene.imgView.setWidth(Constants.DefaultPlayer.width)
        audienceScene.imgView.setHeight(Constants.DefaultPlayer.height)
        audienceScene.statisticsCanvas.setWidth(Constants.DefaultPlayer.width)
        audienceScene.statisticsCanvas.setHeight(Constants.DefaultPlayer.height)

        if (isRecord) {
          audienceScene.recView.setFitWidth(Constants.DefaultPlayer.width)
          audienceScene.recView.setFitHeight(Constants.DefaultPlayer.height)
        }

        audienceScene.addAllElement()
        context.getStage.setFullScreen(false)
        audienceScene.isFullScreen = false
      }
    }

    override def changeOption(needImage: Boolean, needSound: Boolean): Unit = {
      rmManager ! RmManager.ChangeOption4Audience(needImage, needSound)
    }

    override def ask4Loss(): Unit = {
      rmManager ! RmManager.GetPackageLoss
    }

    override def continuePlayRec(recordInfo: RecordInfo): Unit = {
      rmManager ! RmManager.ContinuePlayRec(recordInfo)

    }

    override def pausePlayRec(recordInfo: RecordInfo): Unit = {
      rmManager ! RmManager.PausePlayRec(recordInfo)

    }

  })

  def wsMessageHandle(data: WsMsgRm): Unit = {

    Boot.addToPlatform {
      data match {
        case msg: HeatBeat =>
//          log.debug(s"heartbeat: ${msg.ts}")
          rmManager ! HeartBeat

        case msg: RcvComment =>
          //判断userId是否为-1，是的话当广播处理
//          log.debug(s"receive comment: ${msg.comment}")
          Boot.addToPlatform {
//            audienceScene.commentBoard.updateComment(msg)
//            audienceScene.barrage.updateBarrage(msg)
          }


        case msg: JoinRsp =>
          if (msg.errCode == 0) {
            rmManager ! RmManager.StartJoin(msg.hostLiveId.get, msg.joinInfo.get)
            audienceScene.hasReqJoin = false
          } else if (msg.errCode == 300001) {
            WarningDialog.initWarningDialog("房主未开通连线功能!")
            audienceScene.hasReqJoin = false
          } else if (msg.errCode == 300002) {
            WarningDialog.initWarningDialog("房主拒绝连线申请!")
            audienceScene.hasReqJoin = false
          }

//        case HostDisconnect =>
//          Boot.addToPlatform {
//            WarningDialog.initWarningDialog("主播已断开连线~")
//          }
//          rmManager ! RmManager.StopJoinAndWatch


        case HostCloseRoom =>
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("房主连接断开，互动功能已关闭！")
          }


//        case msg: UpdateAudienceInfo =>
//          //          log.info(s"update audienceList.")
//          Boot.addToPlatform {
//            audienceScene.watchingList.updateWatchingList(msg.AudienceList)
//          }

        case msg: LikeRoomRsp =>
        //          log.debug(s"audience receive likeRoomRsp: ${msg}")

//        case msg: ReFleshRoomInfo =>
//          //          log.debug(s"audience receive likeNum update: ${msg.roomInfo.like}")
//          likeNum = msg.roomInfo.like
//          Boot.addToPlatform {
//            audienceScene.likeNum.setText(likeNum.toString)
//          }

        case HostStopPushStream2Client =>
          Boot.addToPlatform({
            WarningDialog.initWarningDialog("主播已停止直播，请换个房间观看哦~")
          })

        case x =>
          log.warn(s"audience recv unknown msg from rm: $x")


      }
    }
  }


}
