package org.seekloud.VideoMeeting.pcClient.controller

import akka.actor.typed.ActorRef
import javafx.geometry.{Insets, Pos}
import javafx.scene.Group
import javafx.scene.control.ButtonBar.ButtonData
import javafx.scene.control.{ButtonType, Dialog, Label, PasswordField, TextField}
import javafx.scene.image.ImageView
import javafx.scene.layout.{GridPane, HBox, VBox}
import javafx.scene.text.{Font, Text}
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common.StageContext
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.pcClient.core.RmManager


/**
  * User: shuai
  * Date: 2020/2/6
  * Time: 20:27
  */
class InviteController(
                        context: StageContext,
                        rmManager: ActorRef[RmManager.RmCommand]
                      ) {

  def inviteDialog(): Option[(String, String)] = {
    val dialog = new Dialog[(String, String)]()
    dialog.setTitle("邀请")

    val welcomeText = new Text("邀请好友加入会议")
    welcomeText.setStyle("-fx-font: 35 KaiTi;-fx-fill: #333f50")
    val upBox = new HBox()
    upBox.setAlignment(Pos.TOP_CENTER)
    upBox.setPadding(new Insets(40, 200, 0, 200))
    upBox.getChildren.add(welcomeText)

    val emailIcon = new ImageView("img/email.png")
    emailIcon.setFitHeight(28)
    emailIcon.setFitWidth(28)
    val emailLabel = new Label("邮箱:")
    emailLabel.setFont(Font.font(18))
    val emailField = new TextField()

    val meetingIcon = new ImageView("img/meeting.png")
    meetingIcon.setFitHeight(30)
    meetingIcon.setFitWidth(30)
    val meetingLabel = new Label("会议号:")
    meetingLabel.setFont(Font.font(18))
    val meetingField = new TextField()

    val grid = new GridPane
    grid.setHgap(20)
    grid.setVgap(30)
    grid.add(emailIcon, 0, 0)
    grid.add(emailLabel, 1, 0)
    grid.add(emailField, 2, 0)
    grid.add(meetingIcon, 0, 1)
    grid.add(meetingLabel, 1, 1)
    grid.add(meetingField, 2, 1)
    grid.setStyle("-fx-background-color:#d4dbe3;-fx-background-radius: 10")
    grid.setPadding(new Insets(60, 20, 60, 20))

    val bottomBox = new HBox()
    bottomBox.getChildren.add(grid)
    bottomBox.setAlignment(Pos.BOTTOM_CENTER)
    bottomBox.setPadding(new Insets(10, 100, 50, 100))

    val box = new VBox()
    box.getChildren.addAll(upBox, bottomBox)
    box.setAlignment(Pos.CENTER)
    box.setSpacing(30)
    box.setStyle("-fx-background-color:#f2f5fb")

    val confirmButton = new ButtonType("确定", ButtonData.OK_DONE)

    val group = new Group()
    group.getChildren.add(box)
    dialog.getDialogPane.getButtonTypes.add(confirmButton)
    dialog.getDialogPane.setContent(group)
    dialog.setResultConverter(dialogButton =>
      if (meetingField.getText().nonEmpty  && emailField.getText().nonEmpty) {
          if (dialogButton == confirmButton)
            (emailField.getText(), meetingField.getText())
          else
            null
      } else {
        Boot.addToPlatform(
          WarningDialog.initWarningDialog("输入不能为空！")
        )
        null
      }
    )
    var inviteInfo: Option[(String, String)] = None
    val rst = dialog.showAndWait()
    rst.ifPresent { a =>
      if (a._1 != null && a._2 != null && a._1 != "" && a._2 != "" )
        inviteInfo = Some((a._1, a._2))
      else
        None
    }
    inviteInfo
  }


}
