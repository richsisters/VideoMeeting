package org.seekloud.VideoMeeting.pcClient.component

import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.text.Font
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.RcvComment
import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2019/9/12
  * Time: 14:47
  */
object CommentBoard{

}
class CommentBoard(boardWidth: Double, boardHeight: Double) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  var count = 0

  protected val emojiFont = "Segoe UI Emoji"

  //label
  val messageIcon = new ImageView("img/messageIcon.png")
  messageIcon.setFitHeight(25)
  messageIcon.setFitWidth(30)
  val messageLabel = new Label("留言区", messageIcon)
  messageLabel.getStyleClass.add("hostScene-rightArea-label")

  //default message
  val defaultText = new Label("还没有留言哦~")
  defaultText.setFont(Font.font("Verdana", 18))
  defaultText.setPrefSize(640, 15)

  //commentBoard
  val commentBoard = new VBox(5)
  commentBoard.getChildren.add(defaultText)
  commentBoard.setPrefWidth(boardWidth)
  commentBoard.setPrefHeight(boardHeight)
  commentBoard.getStyleClass.add("hostScene-rightArea-commentBoard")

  //commentArea
  val commentArea = new VBox(5)
  commentArea.getChildren.addAll(messageLabel, commentBoard)


  def updateComment(RecComment: RcvComment): Unit = {
    val userName = RecComment.userId match {
      case -1 =>
        "[系统消息]："
      case _ =>
        s"[${RecComment.userName}]："
    }

    val nameLabel = new Label(userName)
    val commentLabel = new Label(RecComment.comment)
//    val commentLabel =
//      comments.substring(0,2) match{
//        case "#g" =>
//          new Label(s"${comments.substring(7)}")
//        case "#e" =>
//          new Label(comments.substring(13))
//        case _ =>
//          new Label(comments)
//
//      }
    nameLabel.setFont(Font.font(emojiFont, 15))
    nameLabel.setWrapText(false)
    commentLabel.setFont(Font.font(emojiFont, 15))
    commentLabel.setWrapText(false)

    if (RecComment.userId == -1) {
      nameLabel.getStyleClass.add("hostScene-rightArea-system_comment")
      commentLabel.getStyleClass.add("hostScene-rightArea-system_comment")
    } else {
      nameLabel.getStyleClass.add("hostScene-rightArea-user_comment_name")
      commentLabel.getStyleClass.add("hostScene-rightArea-user_comment_text")
    }

    val oneCommentBox = new HBox(0)
    oneCommentBox.getChildren.addAll(nameLabel, commentLabel)

    if (count == 0) {
      commentBoard.getChildren.remove(0)
      commentBoard.getChildren.add(oneCommentBox)
      count += 1
    } else {
      if (count < 5) { //最多显示5条留言
        commentBoard.getChildren.add(oneCommentBox)
        count += 1
      } else {
        commentBoard.getChildren.remove(0)
        commentBoard.getChildren.add(oneCommentBox)
      }
    }
  }


}
