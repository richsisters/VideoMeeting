package org.seekloud.VideoMeeting.pcClient.component

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{Button, Label, ListView, TextField}
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.text.{Font, Text}
import org.seekloud.VideoMeeting.pcClient.common.Pictures
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.RecordCommentProtocol.CommentInfo
import org.slf4j.LoggerFactory
import javafx.collections.FXCollections
import javafx.collections.ObservableList

import scala.collection.mutable
import java.text.SimpleDateFormat

/**
  * Author: zwq
  * Date: 2019/9/25
  * Time: 13:54
  */
class RecordCommentBoard(width: Double, height: Double, commentField: TextField) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  val emojiFont="Segoe UI Emoji"

  var firstUpdate = true
  var commentsList: List[CommentInfo] = List.empty[CommentInfo]
  val floorMap: mutable.HashMap[Long, VBox]  = mutable.HashMap.empty  // commentId -> VBox
  val observableList: ObservableList[VBox] = FXCollections.observableArrayList()
  var sayTo: Option[Long] = None

  //加载按钮
  val btn = new Button("点击加载更多")
  btn.setStyle("-fx-cursor: hand;" +
               "-fx-background-color:#00000000;-" +
               "fx-border-color:#00000000;" +
               "-fx-text-fill: #333f50;"
  )
  val btnBox = new VBox(btn)
  btnBox.setPrefWidth(200)
  btnBox.setAlignment(Pos.CENTER)
  btn.setOnAction( _ => getMoreFloors())


  //评论列表
  val floorListView  = new ListView(observableList)
  floorListView.setItems(observableList)
  floorListView.setPrefHeight(height)
  floorListView.setStyle("-fx-border-color: #f2f5fb; -fx-background-color: #f2f5fb;")


  def addOneFloor(userId: Long, commentId: Long, headerUrl: String, userName: String, commentTime: Long, comment: String, authorName: Option[String]): VBox = {
    //nodes
    val header = Pictures.getPic(headerUrl)
    header.setFitHeight(25)
    header.setFitWidth(25)

    val title = if(authorName.isEmpty) new Label(s"$userName：") else new Label(s"${userName}对${authorName.get}说：")
    title.setPrefWidth(250)
    title.setStyle("-fx-font-size: 12;" + "-fx-text-fill: #333f50;")

    val timeIcon = Common.getImageView("img/clock.png",15,15)
    val time = new Label(s"${dateFormat.format(commentTime)}", timeIcon)
    time.setStyle("-fx-font-size: 10;" + "-fx-text-fill: #696969;")
    time.setPrefWidth(120)

    val content = new Text(s"$comment")
    content.setFont(Font.font(emojiFont, 14))
    content.setWrappingWidth(220)
    content.setStyle("-fx-text-fill: #333f50;")

    //layout
    val hBox1 = new HBox(3, header, title)
    hBox1.setAlignment(Pos.CENTER_LEFT)

    val hBox2 = new HBox(content)
    hBox2.setAlignment(Pos.CENTER_LEFT)
    hBox2.setPadding(new Insets(0,0,0,28))

    val hBox3 = new HBox(time)
    hBox3.setAlignment(Pos.CENTER_RIGHT)
    hBox3.setPadding(new Insets(0,10,0,0))

    val vBox = new VBox(3, hBox1, hBox2, hBox3)
    vBox.setAlignment(Pos.CENTER_LEFT)
    vBox.setPadding(new Insets(0,0,5,0))

    vBox.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
      commentField.setPromptText(s"对${userName}说")
      sayTo = Some(userId)
//      log.debug(s"change sayTo:$sayTo")
    })

    floorMap.put(commentId, vBox)
    observableList.add(vBox)
    vBox

  }

  /*更新评论*/
  def updateCommentsList(list: List[CommentInfo]): Unit = {
    val newComments = list.filterNot(l => commentsList.contains(l))
    newComments.foreach{ l =>
      commentsList = l :: commentsList
    }
    commentsList = commentsList.sortBy(l => l.commentTime)

//    if(firstUpdate) {
//      getMoreFloors()
//      firstUpdate = false
//    }
    if(!observableList.contains(btnBox)){
      getMoreFloors()
    }
//    getMoreFloors()
  }

  /*加载评论*/
  def getMoreFloors(): Unit = {
    if(observableList.contains(btnBox)){
      observableList.remove(btnBox)
    }
    val addComments = commentsList.filterNot(l => floorMap.contains(l.commentId)) //新加载的评论

    //每次最多加载 10 条评论
    var i = 0
    addComments.foreach{ l =>
      if(i < 10){
        addOneFloor(l.commentUid, l.commentId,l.commentHeadImgUrl,l.commentUserName, l.commentTime,l.comment,l.authorUserNameOpt)
        i = i + 1
      }
    }

    if(floorMap.size < commentsList.size && !observableList.contains(btnBox)){
      observableList.add(btnBox)
    }

  }



}
