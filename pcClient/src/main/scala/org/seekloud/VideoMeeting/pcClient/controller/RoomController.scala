package org.seekloud.VideoMeeting.pcClient.controller


import java.io.File

import org.seekloud.VideoMeeting.pcClient.scene.RoomScene.RoomSceneListener
import akka.actor.typed.ActorRef
import javafx.geometry.{Insets, Pos}
import javafx.scene.Group
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control._
import javafx.scene.layout.VBox
import javafx.stage.{FileChooser, Stage}
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common._
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.scene.{HomeScene, HostScene, RoomScene}
import org.seekloud.VideoMeeting.pcClient.utils.RMClient
import org.seekloud.VideoMeeting.pcClient.Boot.executor
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.RmManager.{GetRecordDetail, GetRoomDetail, GoToWatch}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, RoomInfo}
import org.slf4j.LoggerFactory

import scala.collection.immutable.VectorBuilder

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:33
  */
class RoomController(
  context: StageContext,
  roomScene: RoomScene,
  rmManager: ActorRef[RmManager.RmCommand]
) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  //  private var roomList: List[RoomInfo] = Nil
  //  private var recordList: List[RecordInfo] = Nil
  var hasWaitingGif = false

  def updateRoomList(): Unit = {
    RMClient.getRoomList.map {
      case Right(rst) =>
        if (rst.errCode == 0) {
          Boot.addToPlatform {
            removeLoading()
            roomScene.roomList = rst.roomList.get
//            roomScene.updateRoomList(roomList = roomScene.roomList)
          }
        } else {
          removeLoading()
          Boot.addToPlatform(
            WarningDialog.initWarningDialog(s"${rst.msg}")
          )
        }
      case Left(e) =>
        log.error(s"get room list error: $e")
        removeLoading()
        Boot.addToPlatform(
          WarningDialog.initWarningDialog("获取房间列表失败")
        )
    }
  }

  roomScene.setListener(new RoomSceneListener {
    override def enter(roomId: Long, timestamp: Long = 0L): Unit = {
      Boot.addToPlatform {
        showLoading()
        if (roomScene.liveMode && roomScene.roomList.exists(_.roomId == roomId)) {
          rmManager ! GetRoomDetail(roomScene.roomList.find(_.roomId == roomId).get.roomId)
        } else if (!roomScene.liveMode && roomScene.recordList.flatMap(_._2).exists(r => r.roomId == roomId && r.startTime == timestamp)) {
          rmManager ! GetRecordDetail(roomScene.recordList.flatMap(_._2).filter(r => r.roomId == roomId && r.startTime == timestamp).head)
        } else {
          removeLoading()
        }
      }
    }

    override def refresh(): Unit = {
      Boot.addToPlatform {
        showLoading()
        if (roomScene.liveMode) {
          //          updateRoomList()
        } else {
          if (!hasWaitingGif) {
            roomScene.recordList = Nil
            for (i <- 1 to 10) {
              //              updateRecordList(sortBy = roomScene.recordSort, pageNum = i)
            }
          }
        }
      }
    }


    override def gotoHomeScene(): Unit = {
      rmManager ! RmManager.BackToHome
    }

    override def find(meeting: String) = {
//      println("wwwwwww" + meeting)
      Boot.addToPlatform{
        if(meeting == ""){
          WarningDialog.initWarningDialog("会议号不能为空")
        }else{
          rmManager ! GetRoomDetail(roomScene.roomList.find(_.roomId == meeting.toLong).get.roomId)//进入会议室
        }
      }
    }

  })


  def showScene(): Unit = {
    Boot.addToPlatform {
//      if (roomScene.liveMode) updateRoomList()
      context.switchScene(roomScene.getScene, title = "直播间online")
    }
  }

  def showLoading(): Unit = {
    Boot.addToPlatform {
      if (!hasWaitingGif) {
        roomScene.group.getChildren.add(roomScene.waitingGif)
        hasWaitingGif = true
      }
    }
  }

  def removeLoading(): Unit = {
    Boot.addToPlatform {
      if (hasWaitingGif) {
        roomScene.group.getChildren.remove(roomScene.waitingGif)
        hasWaitingGif = false
      }
    }
  }

}
