package org.seekloud.VideoMeeting.pcClient.controller

import akka.actor.typed.ActorRef
import javafx.beans.property.{SimpleObjectProperty, SimpleStringProperty}
import javafx.scene.control.{Button, ToggleButton}
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common.{Constants, StageContext}
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.core.RmManager.HeartBeat
import org.seekloud.VideoMeeting.pcClient.scene.{AudienceScene, HostScene}
import org.seekloud.VideoMeeting.pcClient.scene.HostScene.{AudienceListInfo, HostSceneListener}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.slf4j.LoggerFactory

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:33
  */
class HostController(
  context: StageContext,
  hostScene: HostScene,
  inviteController: InviteController,
  rmManager: ActorRef[RmManager.RmCommand]
) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  def showScene(): Unit = {
    Boot.addToPlatform(
      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
        context.switchScene(hostScene.getScene, title = s"${RmManager.roomInfo.get.roomName}")
      } else {
        WarningDialog.initWarningDialog(s"无房间信息！")
      }
    )
  }

  hostScene.setListener(new HostSceneListener {
    override def startLive(): Unit = {
      rmManager ! RmManager.HostLiveReq
    }

    override def modifyRoomInfo(name: Option[String], des: Option[String]): Unit = {
      rmManager ! RmManager.ModifyRoom(name, des)
    }

    override def changeRoomMode(isJoinOpen: Option[Boolean], aiMode: Option[Int], screenLayout: Option[Int]): Unit = {
      rmManager ! RmManager.ChangeMode(isJoinOpen, aiMode, screenLayout)
    }

    override def audienceAcceptance(userId: Long, accept: Boolean, newRequest: AudienceListInfo): Unit = {
        rmManager ! RmManager.AudienceAcceptance(userId, accept)
        hostScene.audObservableList.remove(newRequest)
    }

    override def startMeetingRecord(): Unit = {
      rmManager ! RmManager.HostStartMeetingRecord
    }

    override def stopMeeting(): Unit = {
      rmManager ! RmManager.HostFinishMeeting
    }

    override def gotoHomeScene(): Unit = {
      rmManager ! RmManager.BackToHome
    }

    override def changeOption(bit: Option[Int] = None, re: Option[String] = None, frameRate: Option[Int] = None, needImage: Boolean = true, needSound: Boolean = true): Unit = {
      rmManager ! RmManager.ChangeOption(bit, re, frameRate, needImage, needSound)
    }

    override def gotoInviteDialog(): Unit = {
      //弹出邀请窗口
      val inviteInfo = inviteController.inviteDialog()
      if (inviteInfo.nonEmpty) {
        rmManager ! RmManager.InviteReq(inviteInfo.get._1.toString, inviteInfo.get._2.toString)
      }
    }

    override def exitMember(userId: Long, userName:String): Unit = {
      rmManager ! RmManager.ForceExit(userId, userName)
    }

    override def banMember(userId: Long, image: Boolean, sound: Boolean): Unit = {
      rmManager ! RmManager.BanOnMember(userId, image, sound)
    }

    override def cancelBan(userId: Long, image: Boolean, sound: Boolean): Unit = {
      rmManager ! RmManager.CancelBan(userId, image, sound)
    }

    override def Designated2Speak(userId: Long): Unit = {
      rmManager ! RmManager.Speak(userId)
    }
  })


  def wsMessageHandle(data: WsMsgRm): Unit = {
    data match {

      case msg: HeatBeat =>
        rmManager ! HeartBeat

      case msg: StartLiveRsp =>
        if (msg.errCode == 0) {
          rmManager ! RmManager.StartLive(msg.liveInfo.get.liveId, msg.liveInfo.get.liveCode)
        } else {
          Boot.addToPlatform {
            WarningDialog.initWarningDialog(s"${msg.msg}")
          }
        }

      case InviteRsp =>
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("邀请邮件已发送")
        }

      case msg:ForceExitRsp =>
        rmManager ! RmManager.AudienceExit(msg.liveId)


      case msg: AudienceJoin =>
        //将该条信息展示在host页面(TableView)
        log.debug(s"Audience-${msg.userName} send join req.")
        Boot.addToPlatform {
          hostScene.updateAudienceList(msg.userId, msg.userName)
        }

      case msg: AudienceJoinRsp =>
        if (msg.errCode == 0) {
          //显示连线观众信息
          rmManager ! RmManager.JoinBegin(msg.joinInfo.get)
          Boot.addToPlatform {
            if (!hostScene.tb3.isSelected) {
              hostScene.tb3.setGraphic(hostScene.connectionIcon1)
            }
              val userId = msg.joinInfo.get.userId
              val userName = msg.joinInfo.get.userName
              hostScene.updateAcceptList(userId, userName)
          }

        } else {
          Boot.addToPlatform {
            WarningDialog.initWarningDialog(s"参会者加入出错:${msg.msg}")
          }
        }

      case msg: StartMeetingRsp =>
        Boot.addToPlatform{
          WarningDialog.initWarningDialog(s"${msg.msg}")
        }

      case BanOnAnchor =>
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("你的会议已被管理员禁止！")
        }
        rmManager ! RmManager.BackToHome

      case msg: AudienceDisconnect =>
        Boot.addToPlatform {
          WarningDialog.initWarningDialog(s"用户${msg.userId}退出了会议室")
        }
        rmManager ! RmManager.AudienceExit(msg.audienceLiveId)


      case x =>
        log.warn(s"host recv unknown msg from rm: $x")
    }

  }

}
