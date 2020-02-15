package org.seekloud.VideoMeeting.pcClient.controller

import akka.actor.typed.ActorRef
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common.{Constants, StageContext}
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.core.RmManager.HeartBeat
import org.seekloud.VideoMeeting.pcClient.scene.{HostScene, AudienceScene}
import org.seekloud.VideoMeeting.pcClient.scene.HostScene.{HostSceneListener, AudienceListInfo}
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
//  var isConnecting = false
  var isLive = false
//  var likeNum: Int = RmManager.roomInfo.get.like

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

    override def stopLive(): Unit = {
      rmManager ! RmManager.StopLive
    }

    override def modifyRoomInfo(name: Option[String], des: Option[String]): Unit = {
      rmManager ! RmManager.ModifyRoom(name, des)
    }

    override def changeRoomMode(isJoinOpen: Option[Boolean], aiMode: Option[Int], screenLayout: Option[Int]): Unit = {
      rmManager ! RmManager.ChangeMode(isJoinOpen, aiMode, screenLayout)
    }

    override def audienceAcceptance(userId: Long, accept: Boolean, newRequest: AudienceListInfo): Unit = {
//      if (!isConnecting) {
//        rmManager ! RmManager.AudienceAcceptance(userId, accept)
//        hostScene.audObservableList.remove(newRequest)
//      } else {
//        if ( !accept) {
          rmManager ! RmManager.AudienceAcceptance(userId, accept)
          hostScene.audObservableList.remove(newRequest)
//        } else {
//          Boot.addToPlatform {
//            WarningDialog.initWarningDialog(s"无法重复连线，请先断开当前连线。")
//          }
//        }
//      }
    }

    override def startMeeting(roomId: Long): Unit = {
      rmManager ! RmManager.HostStartMeeting(roomId)
    }

    override def shutJoin(): Unit = {
      rmManager ! RmManager.ShutJoin
    }

    override def gotoHomeScene(): Unit = {
      rmManager ! RmManager.BackToHome
    }

    override def setFullScreen(): Unit = {
      if (!hostScene.isFullScreen) {
        hostScene.removeAllElement()

        context.getStage.setFullScreen(true)

        hostScene.liveImage.setWidth(context.getStageWidth)
        hostScene.liveImage.setHeight(context.getStageHeight)
        hostScene.statisticsCanvas.setWidth(context.getStageWidth)
        hostScene.statisticsCanvas.setHeight(context.getStageHeight)
        hostScene.gc.drawImage(hostScene.backImg, 0, 0, context.getStageWidth, context.getStageWidth)

        hostScene.isFullScreen = true
      }
    }

    override def exitFullScreen(): Unit = {
      if (hostScene.isFullScreen) {
        hostScene.liveImage.setWidth(Constants.DefaultPlayer.width)
        hostScene.liveImage.setHeight(Constants.DefaultPlayer.height)
        hostScene.statisticsCanvas.setWidth(Constants.DefaultPlayer.width)
        hostScene.statisticsCanvas.setHeight(Constants.DefaultPlayer.height)
        hostScene.gc.drawImage(hostScene.backImg, 0, 0, Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)

        hostScene.addAllElement()
        context.getStage.setFullScreen(false)

        hostScene.isFullScreen = false
      }
    }


    override def changeOption(bit: Option[Int] = None, re: Option[String] = None, frameRate: Option[Int] = None, needImage: Boolean = true, needSound: Boolean = true): Unit = {
      rmManager ! RmManager.ChangeOption(bit, re, frameRate, needImage, needSound)
    }

    override def recordOption(recordOrNot: Boolean, recordType: String, path: Option[String] = None): Unit = {
      if (!isLive) {
        recordType match {
          case "录制自己" => rmManager ! RmManager.RecordOption(recordOrNot, path)
          case "录制别人" => path.foreach(i => rmManager ! RmManager.StartRecord(i))
        }
      } else {
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("直播中不能更改设置哦~")
        }
      }
    }

    override def ask4Loss(): Unit = {
      rmManager ! RmManager.GetPackageLoss
    }

    override def gotoInviteDialog(): Unit = {
      //弹出邀请窗口
      val inviteInfo = inviteController.inviteDialog()
      if (inviteInfo.nonEmpty) {
        rmManager ! RmManager.InviteReq(inviteInfo.get._1.toString, inviteInfo.get._2.toString)
      }
    }

    override def exitMember(userId: Long): Unit = {
      rmManager ! RmManager.ForceExit(userId)
    }

    override def banMember(userId: Long, image: Boolean, sound: Boolean): Unit = {
      rmManager ! RmManager.BanOnMember(userId, image, sound)
    }

    override def cancelBan(userId: Long, image: Boolean, sound: Boolean): Unit = {
      //TODO 取消屏蔽
      println("cancel")
    }
  })


  def wsMessageHandle(data: WsMsgRm): Unit = {
    data match {

      case msg: HeatBeat =>
//        log.debug(s"heartbeat: ${msg.ts}")
        rmManager ! HeartBeat

      case msg: StartLiveRsp =>
//        log.debug(s"get StartLiveRsp: $msg")
        if (msg.errCode == 0) {
          rmManager ! RmManager.StartLive(msg.liveInfo.get.liveId, msg.liveInfo.get.liveCode)
        } else {
          Boot.addToPlatform {
            WarningDialog.initWarningDialog(s"${msg.msg}")
          }
        }

      case msg:StartMeetingRsp =>
        if (msg.errCode == 0) {
          rmManager ! RmManager.PullFromProcessor(msg.liveId)
          Boot.addToPlatform{
            hostScene.connectionStateText.setText(s"会议进行中")
          }
        }else{
          WarningDialog.initWarningDialog("转接错误 test")
        }

      case InviteRsp =>
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("邀请邮件已发送")
        }


      case msg: ModifyRoomRsp =>
        //若失败，信息改成之前的信息
//        log.debug(s"get ModifyRoomRsp: $msg")
        if (msg.errCode == 0) {
          //          log.debug(s"更改房间信息成功！")
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("更改房间信息成功！")
          }
          // do nothing
        } else {
          log.debug(s"更改房间信息失败！原房间信息为：${hostScene.roomInfoMap}")
          Boot.addToPlatform {
            val roomName = hostScene.roomInfoMap(RmManager.roomInfo.get.roomId).head
            val roomDes = hostScene.roomInfoMap(RmManager.roomInfo.get.roomId)(1)
            hostScene.roomNameField.setText(roomName)
            hostScene.roomDesArea.setText(roomDes)
          }
        }

      case msg: ChangeModeRsp =>
        if (msg.errCode != 0) {
          Boot.addToPlatform {
            WarningDialog.initWarningDialog("该项设置目前不可用！")
          }
        }

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

//            hostScene.connectionStateText.setText(s"与${msg.joinInfo.get.userName}连线中")
//            hostScene.connectStateBox.getChildren.add(hostScene.shutConnectionBtn)
//            isConnecting = true
          }

        } else {
          Boot.addToPlatform {
            WarningDialog.initWarningDialog(s"参会者加入出错:${msg.msg}")
          }
        }

//      case AudienceDisconnect =>
//        //观众断开，提醒主播，去除连线观众信息
//        rmManager ! RmManager.JoinStop
//        Boot.addToPlatform {
//          if (!hostScene.tb3.isSelected) {
//            hostScene.tb3.setGraphic(hostScene.connectionIcon1)
//          }
//          hostScene.connectionStateText.setText(s"目前状态：无连接")
//          hostScene.connectStateBox.getChildren.remove(hostScene.shutConnectionBtn)
//          isConnecting = false
//        }

//      case msg: ReFleshRoomInfo =>
////        log.debug(s"host receive likeNum update: ${msg.roomInfo.like}")
//        likeNum = msg.roomInfo.like
//        Boot.addToPlatform {
//          hostScene.likeLabel.setText(likeNum.toString)
//        }

      case HostStopPushStream2Client =>
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("直播成功停止，已通知所有观众。")
        }

      case BanOnAnchor =>
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("你的直播已被管理员禁止！")
        }
        rmManager ! RmManager.BackToHome

      case x =>
        log.warn(s"host recv unknown msg from rm: $x")
    }

  }

}
