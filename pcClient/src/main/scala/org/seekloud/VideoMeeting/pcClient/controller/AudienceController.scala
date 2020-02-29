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
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, UserDes, UserInfo}

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
      context.switchScene(audienceScene.getScene, title = s"${audienceScene.getRoomInfo.roomId}")
    }

  }

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
        WarningDialog.initWarningDialog("加入会议申请已发送！")
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

    override def changeOption(needImage: Boolean, needSound: Boolean): Unit = {
      rmManager ! RmManager.ChangeOption4Audience(needImage, needSound)
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


        case msg: JoinRsp =>
          assert(RmManager.userInfo.nonEmpty)
          if (msg.errCode == 0) {
            rmManager ! RmManager.StartJoin(msg.hostLiveId.get, msg.joinInfo.get, msg.attendInfo)
            audienceScene.updateAttendList(RmManager.userInfo.get.userId, RmManager.userInfo.get.userName, true)

          } else{
            WarningDialog.initWarningDialog(msg.msg)
            audienceScene.hasReqJoin = false
            audienceScene.isLive = false
          }

        case msg:ForceExitRsp =>
          if(RmManager.userInfo.nonEmpty && msg.userId == RmManager.userInfo.get.userId){
            WarningDialog.initWarningDialog(s"主持人强制您退出会议")
            rmManager ! RmManager.StopJoinAndWatch
          } else if(RmManager.userInfo.nonEmpty && msg.userId != RmManager.userInfo.get.userId){
            WarningDialog.initWarningDialog(s"主持人强制用户${msg.userId}退出会议")
            rmManager ! RmManager.AudienceExit(msg.liveId)
          }
          audienceScene.updateAttendList(msg.userId, msg.userName, false)


        case msg: BanOnMemberRsp =>
          assert(RmManager.userInfo.nonEmpty)
          val userId = RmManager.userInfo.get.userId
          if(msg.userId == userId){
            if(msg.image){
//              Boot.addToPlatform{
//                WarningDialog.initWarningDialog(s"主持人屏蔽你的的画面")
//              }
              audienceScene.imageToggleBtn.setDisable(true)
              audienceScene.imageToggleBtn.setSelected(false)
            }
            if(msg.sound){
//              Boot.addToPlatform{
//                WarningDialog.initWarningDialog(s"主持人屏蔽你的声音")
//              }
              audienceScene.soundToggleBtn.setDisable(true)
              audienceScene.soundToggleBtn.setSelected(false)
            }
            rmManager ! RmManager.HostBan4Rm(!msg.image, !msg.sound)
          } else{
            if(msg.image){
              Boot.addToPlatform{
                WarningDialog.initWarningDialog(s"主持人屏蔽用户${msg.userId}的画面")
              }
            }
            if(msg.sound){
              Boot.addToPlatform{
                WarningDialog.initWarningDialog(s"主持人屏蔽用户${msg.userId}的声音")
              }
            }
          }

        case msg: CancelBanOnMemberRsp =>
          assert(RmManager.userInfo.nonEmpty)
          val userId = RmManager.userInfo.get.userId
          if(msg.userId == userId){
            if(msg.image){
//              Boot.addToPlatform{
//                WarningDialog.initWarningDialog(s"主持人取消屏蔽你的的画面")
//              }
              audienceScene.imageToggleBtn.setDisable(false)
              audienceScene.imageToggleBtn.setSelected(true)
            }
            if(msg.sound){
//              Boot.addToPlatform{
//                WarningDialog.initWarningDialog(s"主持人取消屏蔽你的声音")
//              }
              audienceScene.soundToggleBtn.setDisable(false)
              audienceScene.soundToggleBtn.setSelected(true)
            }
            rmManager ! RmManager.HostBan4Rm(msg.image, msg.sound)
          } else{
            if(msg.image){
              Boot.addToPlatform{
                WarningDialog.initWarningDialog(s"主持人取消屏蔽用户${msg.userId}的画面")
              }
            }
            if(msg.sound){
              Boot.addToPlatform{
                WarningDialog.initWarningDialog(s"主持人取消屏蔽用户${msg.userId}的声音")
              }
            }
          }

        case HostDisconnect(hostLiveId) =>
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("主持人连接断开，互动功能已关闭！")
          }
          audienceScene.isLive = false
          rmManager ! RmManager.StopJoinAndWatch


        case msg: HostCloseRoom =>
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("主持人结束会议")
          }
          audienceScene.isLive = false
          rmManager ! RmManager.MeetingFinished

        case msg: AudienceJoinRsp =>
          if (msg.errCode == 0) {
            //显示连线观众信息
            Boot.addToPlatform {

              val userId = msg.joinInfo.get.userId
              val userName = msg.joinInfo.get.userName
              audienceScene.updateAttendList(userId, userName, true)

            }
            rmManager ! RmManager.OtherAudienceJoin(msg.joinInfo.get.liveId)

          } else {
            Boot.addToPlatform {
              WarningDialog.initWarningDialog(s"参会者加入出错:${msg.msg}")
            }
          }

        case msg: AudienceDisconnect =>
          WarningDialog.initWarningDialog(s"用户${msg.userId}退出会议")
          if(RmManager.userInfo.nonEmpty && msg.userId == RmManager.userInfo.get.userId){
            rmManager ! RmManager.StopJoinAndWatch
          } else if(RmManager.userInfo.nonEmpty && msg.userId != RmManager.userInfo.get.userId){
            rmManager ! RmManager.AudienceExit(msg.audienceLiveId)
          }
         // audienceScene.updateAttendList(msg.userId, "", false)


        case x =>
          log.warn(s"audience recv unknown msg from rm: $x")
      }
    }
  }


}
