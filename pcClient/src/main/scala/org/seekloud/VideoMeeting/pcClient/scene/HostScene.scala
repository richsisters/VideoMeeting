package org.seekloud.VideoMeeting.pcClient.scene

import java.io.File

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.beans.property.{ObjectProperty, SimpleObjectProperty, SimpleStringProperty, StringProperty}
import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.Insets
import javafx.scene.{Group, Scene}
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.control._
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.effect.{DropShadow, Glow}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.scene.layout._
import javafx.scene.text.Text
import org.seekloud.VideoMeeting.pcClient.common.{Constants, Pictures}
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.slf4j.LoggerFactory
import javafx.geometry.Pos
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.{DirectoryChooser, Stage}
import javafx.util.Duration
import org.seekloud.VideoMeeting.capture.sdk.DeviceUtil
import org.seekloud.VideoMeeting.capture.sdk.DeviceUtil.VideoOption
import org.seekloud.VideoMeeting.pcClient.component._

import scala.collection.mutable


/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:12
  */

object HostScene {

  case class AudienceListInfo(
    userInfo: StringProperty,
    agreeBtn: ObjectProperty[Button],
    refuseBtn: ObjectProperty[Button]
  ) {
    def getUserInfo: String = userInfo.get()

    def setUserInfo(info: String): Unit = userInfo.set(info)

    def getAgreeBtn: Button = agreeBtn.get()

    def setAgreeBtn(btn: Button): Unit = agreeBtn.set(btn)

    def getRefuseBtn: Button = refuseBtn.get()

    def setRefuseBtn(btn: Button): Unit = refuseBtn.set(btn)
  }

  case class AcceptList(
                         userInfo: StringProperty,
                         banOnImage: ObjectProperty[ToggleButton],
                         banOnSound: ObjectProperty[ToggleButton],
                         exitBtn: ObjectProperty[Button]
                       ){
    def getUserInfo: String = userInfo.get()

    def setUserInfo(info: String): Unit = userInfo.set(info)

    def getExitBtn: Button = exitBtn.get()

    def setExitBtn(btn: Button): Unit = exitBtn.set(btn)

    def getBanOnImage: ToggleButton = banOnImage.get()

    def setBanOnImage(btn: ToggleButton): Unit = banOnImage.set(btn)

    def getBanOnSound: ToggleButton = banOnSound.get()

    def setBanOnSound(btn: ToggleButton): Unit = banOnSound.set(btn)
  }

  trait HostSceneListener {

    def startLive()

    def modifyRoomInfo(
      name: Option[String] = None,
      des: Option[String] = None
    )

    def changeRoomMode(
      isJoinOpen: Option[Boolean] = None,
      aiMode: Option[Int] = None,
      screenLayout: Option[Int] = None
    )

    def audienceAcceptance(userId: Long, accept: Boolean, newRequest: AudienceListInfo)

    def startMeetingRecord()

    def stopMeeting()

    def gotoHomeScene()

    def changeOption(bit: Option[Int] = None, re: Option[String] = None, frameRate: Option[Int] = None, needImage: Boolean = true, needSound: Boolean = true)

    def gotoInviteDialog()

    def exitMember(userId: Long, userName:String)

    def banMember(userId: Long, image: Boolean, sound:Boolean)

    def cancelBan(userId: Long, image: Boolean, sound:Boolean)
  }

}

class HostScene(stage: Stage) {

  import HostScene._

  private[this] val log = LoggerFactory.getLogger(this.getClass)

  private val width = Constants.AppWindow.width * 0.9
  private val height = Constants.AppWindow.height * 0.75

  private val group = new Group()
  private val scene = new Scene(group, width, height)
  scene.getStylesheets.add(
    this.getClass.getClassLoader.getResource("css/common.css").toExternalForm
    )

  private val timeline = new Timeline()

  var isLive = false
  var roomInfoMap = Map.empty[Long, List[String]]
  val audObservableList: ObservableList[AudienceListInfo] = FXCollections.observableArrayList()
  val audAcceptList: ObservableList[AcceptList] = FXCollections.observableArrayList()
  var commentPrefix = "effectType0"

  var listener: HostSceneListener = _

  val fullScreenImage = new StackPane()

  var roomNameField = new TextField(s"${RmManager.roomInfo.get.roomName}")
  roomNameField.setPrefWidth(width * 0.15)
  var roomDesArea = new TextArea(s"${RmManager.roomInfo.get.roomDes}")
  roomDesArea.setPrefSize(width * 0.15, height * 0.1)

  val connectionStateText = new Text("目前状态：录像未开始～")
  connectionStateText.getStyleClass.add("hostScene-leftArea-text")

  val startIcon = new ImageView("img/会议.png")
  startIcon.setFitHeight(15)
  startIcon.setFitWidth(15)
  val startBtn = new ToggleButton("开始录像", startIcon)
  startBtn.setSelected(false)
  startBtn.getStyleClass.add("hostScene-leftArea-start")

  startBtn.setOnAction {
    _ =>
//      if(startBtn.isSelected && isLive){
      listener.startMeetingRecord()
      connectionStateText.setText("录像中")
//        startBtn.setText("结束会议")
//      }else{
//        listener.stopMeeting()
//        liveBar.soundToggleButton.setDisable(false)
//        liveBar.imageToggleButton.setDisable(false)
//        liveBar.endTimer()
//        isLive = false
//        startBtn.setText("开始会议")
//      }
  }


  val endIcon = new ImageView("img/end.png")
  endIcon.setFitHeight(15)
  endIcon.setFitWidth(15)
  val endButton = new ToggleButton("结束会议", endIcon)
  endButton.setSelected(false)
  endButton.getStyleClass.add("hostScene-leftArea-start")
  endButton.setOnAction{
    _ =>
      isLive = false
      listener.stopMeeting()
      connectionStateText.setText("会议已结束")
      WarningDialog.initWarningDialog("您结束了本次会议~")
  }

  val startBox = new HBox()
  startBox.getChildren.addAll(startBtn, endButton)
  startBox.setAlignment(Pos.CENTER_LEFT)
  startBox.setSpacing(30)


  val connectStateBox = new HBox()
  connectStateBox.getChildren.add(connectionStateText)
  connectStateBox.setSpacing(10)
  connectStateBox.setAlignment(Pos.CENTER_LEFT)
  connectStateBox.setSpacing(10)

  /**
    * 左侧导航栏
    *
    **/
  val roomInfoIcon = new ImageView("img/roomInfo.png")
  roomInfoIcon.setFitWidth(30)
  roomInfoIcon.setFitHeight(30)
  val connectionIcon = new ImageView("img/connection.png")
  connectionIcon.setFitWidth(30)
  connectionIcon.setFitHeight(30)
  val connectionIcon1 = new ImageView("img/connection1.png")
  connectionIcon1.setFitWidth(30)
  connectionIcon1.setFitHeight(30)
  val audienceIcon: ImageView = Common.getImageView("img/watching.png", 20, 20)

  val tb1 = new ToggleButton("房间 ", roomInfoIcon)
  tb1.setPrefWidth(140)
  tb1.getStyleClass.add("hostScene-leftArea-toggleButton")
  val tb3 = new ToggleButton("开会 ", connectionIcon)
  tb3.getStyleClass.add("hostScene-leftArea-toggleButton")
  tb3.setPrefWidth(140)

  /**
    * canvas
    *
    **/
  val liveImage = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
  val gc: GraphicsContext = liveImage.getGraphicsContext2D
  val backImg = new Image("img/background.jpg")
  gc.drawImage(backImg, 0, 0, Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)

//  val statisticsCanvas = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
//  val ctx: GraphicsContext = statisticsCanvas.getGraphicsContext2D

  val waitPulling = new Image("img/waitPulling.gif")
  val wait1 = new Image("img/wait1.png")
  val wait2 = new Image("img/wait2.png")
  val wait3 = new Image("img/wait3.png")

  def resetBack(): Unit = {
    val sWidth = gc.getCanvas.getWidth
    val sHeight = gc.getCanvas.getHeight
    gc.drawImage(waitPulling, 0, 0, sWidth / 2, sHeight / 2)
    gc.drawImage(wait1, 0, sHeight / 2, sWidth / 2, sHeight / 2)
    gc.drawImage(wait2, sWidth / 2, 0, sWidth / 2, sHeight / 2)
    gc.drawImage(wait3, sWidth / 2, sHeight / 2, sWidth / 2, sHeight / 2)
    gc.setFont(Font.font(25))
    gc.setFill(Color.BLACK)
//    gc.fillText(s"录像中", liveImage.getWidth / 2 - 40, liveImage.getHeight / 8)
  }


  def resetLoading(): Unit = {
    val sWidth = gc.getCanvas.getWidth
    val sHeight = gc.getCanvas.getHeight
    gc.drawImage(waitPulling, 0, 0, sWidth, sHeight)
  }

  /*屏幕下方功能条*/
  val liveBar = new LiveBar(Constants.WindowStatus.HOST, liveImage.getWidth, liveImage.getHeight * 0.1)
  val imageToggleBtn: ToggleButton = liveBar.imageToggleButton
  val soundToggleBtn: ToggleButton = liveBar.soundToggleButton

  imageToggleBtn.setOnAction {
    _ =>
      if (!isLive) {
        listener.changeOption(needImage = imageToggleBtn.isSelected, needSound = soundToggleBtn.isSelected)
        if(imageToggleBtn.isSelected) Tooltip.install(imageToggleBtn, new Tooltip("点击关闭画面"))
        else  Tooltip.install(imageToggleBtn, new Tooltip("点击开启画面"))
      } else {
        WarningDialog.initWarningDialog("会议中无法更改设置哦~")
      }
  }

  soundToggleBtn.setOnAction {
    _ =>
      if (!isLive) {
        listener.changeOption(needImage = imageToggleBtn.isSelected, needSound = soundToggleBtn.isSelected)
        if(soundToggleBtn.isSelected) Tooltip.install(soundToggleBtn, new Tooltip("点击关闭声音"))
        else  Tooltip.install(soundToggleBtn, new Tooltip("点击开启声音"))
      } else {
        WarningDialog.initWarningDialog("会议中无法更改设置哦~")
      }
  }

  val barBox: VBox = liveBar.barVBox


  /*layout*/
  var leftArea: VBox = addLeftArea()
  var rightArea: VBox = addRightArea()

  val borderPane = new BorderPane
  borderPane.setLeft(leftArea)
  borderPane.setRight(rightArea)
  group.getChildren.add(borderPane)

  /**
    * 更新连线请求
    *
    **/
  def updateAudienceList(audienceId: Long, audienceName: String): Unit = {
    if (!tb3.isSelected) {
      tb3.setGraphic(connectionIcon1)
    }
    val agreeBtn = new Button("", new ImageView("img/agreeBtn.png"))
    val refuseBtn = new Button("", new ImageView("img/refuseBtn.png"))

    agreeBtn.getStyleClass.add("hostScene-middleArea-tableBtn")
    refuseBtn.getStyleClass.add("hostScene-middleArea-tableBtn")
    val glow = new Glow()
    agreeBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      agreeBtn.setEffect(glow)
    })
    agreeBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      agreeBtn.setEffect(null)
    })
    refuseBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      refuseBtn.setEffect(glow)
    })
    refuseBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      refuseBtn.setEffect(null)
    })
    val newRequest = AudienceListInfo(
      new SimpleStringProperty(s"$audienceName($audienceId)"),
      new SimpleObjectProperty[Button](agreeBtn),
      new SimpleObjectProperty[Button](refuseBtn)
      )
    audObservableList.add(newRequest)

    agreeBtn.setOnAction {
      _ =>
        listener.audienceAcceptance(userId = audienceId, accept = true, newRequest)
    }
    refuseBtn.setOnAction {
      _ =>
        listener.audienceAcceptance(userId = audienceId, accept = false, newRequest)
    }

  }

  def updateAcceptList(userId: Long, userName: String): Unit = {
    if (!tb3.isSelected) {
      tb3.setGraphic(connectionIcon1)
    }
    val exitBtn = new Button("", new ImageView("img/exit.png"))
    exitBtn.getStyleClass.add("hostScene-middleArea-tableBtn")

    val banOnImage = new ToggleButton("")
    banOnImage.getStyleClass.add("hostScene-bottomArea-tableBtn")

    val banOnSound = new ToggleButton("")
    banOnSound.getStyleClass.add("hostScene-bottomArea-tableBtn")

    val btnList = List(exitBtn, banOnImage, banOnSound)
    val glow = new Glow()
    btnList.foreach{btn =>
      btn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
        exitBtn.setEffect(glow)
      })
      btn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
        btn.setEffect(null)
      })
    }

    val newRequest = AcceptList(
      new SimpleStringProperty(s"$userName($userId)"),
      new SimpleObjectProperty[ToggleButton](banOnImage),
      new SimpleObjectProperty[ToggleButton](banOnSound),
      new SimpleObjectProperty[Button](exitBtn)
    )

    exitBtn.setOnAction{event =>
      log.debug(s"强制用户$userId 退出...")
      listener.exitMember(userId, userName)
      audAcceptList.remove(newRequest)
    }

    banOnImage.setOnAction{_ =>
      if(banOnImage.isSelected){
        log.debug(s"屏蔽用户$userId 图像...")
        listener.banMember(userId, true, false)
      } else{
        log.debug(s"开启用户$userId 图像...")
        listener.cancelBan(userId, true, false)
      }
    }

    banOnSound.setOnAction{_ =>
      if(banOnSound.isSelected){
        log.debug(s"屏蔽用户$userId 声音...")
        listener.banMember(userId, false, true)
        Tooltip.install(banOnImage, new Tooltip("点击开启该用户声音"))
      } else{
        log.debug(s"开启用户$userId 声音...")
        listener.cancelBan(userId, false, true)
        Tooltip.install(banOnImage, new Tooltip("点击屏蔽该用户声音"))
      }
    }

    audAcceptList.add(newRequest)
  }

  def getScene: Scene = this.scene

  def setListener(listener: HostSceneListener): Unit = {
    this.listener = listener
  }

  def addLeftArea(): VBox = {
    tb1.setSelected(true)

    val group = new ToggleGroup
    tb1.setToggleGroup(group)
    tb3.setToggleGroup(group)

    val tbBox = new HBox()
    tbBox.getChildren.addAll(tb1, tb3)

    val content = new VBox()
    val left1Area = addLeftChild1Area()
    val left3Area = addLeftChild3Area()
    content.getChildren.add(left1Area)
    content.setPrefSize(width * 0.27, height)


    tb1.setOnAction(_ => {
      tb1.setGraphic(roomInfoIcon)
      content.getChildren.clear()
      content.getChildren.add(left1Area)
    })

    tb3.setOnAction(_ => {
      tb3.setGraphic(connectionIcon)
      content.getChildren.clear()
      content.getChildren.add(left3Area)
    })

    val leftArea = new VBox()
    leftArea.getChildren.addAll(tbBox, content)

    leftArea
  }

  def addLeftChild1Area(): VBox = {
    val backIcon = new ImageView("img/hideBtn.png")
    val backBtn = new Button("", backIcon)
    backBtn.getStyleClass.add("roomScene-backBtn")
    backBtn.setOnAction{_ =>
      isLive = false
      listener.gotoHomeScene()}
    Common.addButtonEffect(backBtn)



    val leftAreaBox = new VBox()
    leftAreaBox.getChildren.addAll(createRoomInfoLabel, createRoomInfoBox)
    leftAreaBox.setSpacing(10)
    leftAreaBox.setPadding(new Insets(5, 0, 0, 0))
    leftAreaBox.getStyleClass.add("hostScene-leftArea-wholeBox")
    leftAreaBox.setPrefHeight(height)


    def createRoomInfoLabel: HBox = {
      val box = new HBox()
      box.getChildren.addAll(backBtn)
      box.setSpacing(170)
      box.setAlignment(Pos.CENTER_LEFT)
      box.setPadding(new Insets(0, 0, 0, 5))
      box

    }

    def createRoomInfoBox: VBox = {
      val roomId = new Text(s"会议 ID：${RmManager.roomInfo.get.roomId}")
      roomId.getStyleClass.add("hostScene-leftArea-text")

      val userId = new Text(s"主持人 ID：${RmManager.roomInfo.get.userId}")
      userId.getStyleClass.add("hostScene-leftArea-text")

      val roomNameText = new Text("会议名称:")
      roomNameText.getStyleClass.add("hostScene-leftArea-text")

      val confirmIcon1 = new ImageView("img/confirm.png")
      confirmIcon1.setFitHeight(15)
      confirmIcon1.setFitWidth(15)

      val roomNameBtn = new Button("确认", confirmIcon1)
      roomNameBtn.getStyleClass.add("hostScene-leftArea-confirmBtn")
      roomNameBtn.setOnAction {
        _ =>
          roomInfoMap = Map(RmManager.roomInfo.get.roomId -> List(RmManager.roomInfo.get.roomName, RmManager.roomInfo.get.roomDes))
          listener.modifyRoomInfo(name = Option(roomNameField.getText()))
      }
      Common.addButtonEffect(roomNameBtn)

      val roomName = new HBox()
      roomName.setAlignment(Pos.CENTER_LEFT)
      roomName.getChildren.addAll(roomNameField, roomNameBtn)
      roomName.setSpacing(5)

      val roomDesText = new Text("会议描述:")
      roomDesText.getStyleClass.add("hostScene-leftArea-text")

      val confirmIcon2 = new ImageView("img/confirm.png")
      confirmIcon2.setFitHeight(15)
      confirmIcon2.setFitWidth(15)

      val roomDesBtn = new Button("确认", confirmIcon2)
      roomDesBtn.getStyleClass.add("hostScene-leftArea-confirmBtn")
      roomDesBtn.setOnAction {
        _ =>
          roomInfoMap = Map(RmManager.roomInfo.get.roomId -> List(RmManager.roomInfo.get.roomName, RmManager.roomInfo.get.roomDes))
          listener.modifyRoomInfo(des = Option(roomDesArea.getText()))
      }
      Common.addButtonEffect(roomDesBtn)


      val roomDes = new HBox()
      roomDes.setAlignment(Pos.CENTER_LEFT)
      roomDes.getChildren.addAll(roomDesArea, roomDesBtn)
      roomDes.setSpacing(5)

      val inviteIcon = new ImageView("img/邀请.png")
      inviteIcon.setFitHeight(30)
      inviteIcon.setFitWidth(30)
      val inviteButton = new Button("邀请好友", inviteIcon)
      inviteButton.getStyleClass.add("hostScene-leftArea-invite")
      inviteButton.setOnAction(_ => listener.gotoInviteDialog())

      val roomInfoBox = new VBox()
      roomInfoBox.getChildren.addAll(roomId, userId, roomNameText, roomName, roomDesText, roomDes, inviteButton)
      roomInfoBox.setPadding(new Insets(5, 30, 0, 30))
      roomInfoBox.setSpacing(15)
      roomInfoBox
    }
    leftAreaBox
  }


  def addLeftChild3Area(): VBox = {
    val vBox = new VBox()
    vBox.getChildren.addAll(startBox, connectStateBox, createCntTbArea, createAcceptArea)
    vBox.setSpacing(20)
    vBox.setPrefHeight(height)
    vBox.setPadding(new Insets(20, 10, 5, 10))
    vBox.getStyleClass.add("hostScene-leftArea-wholeBox")

    def createCntTbArea: TableView[AudienceListInfo] = {
      val AudienceTable = new TableView[AudienceListInfo]()
      AudienceTable.getStyleClass.add("table-view")

      val userInfoCol = new TableColumn[AudienceListInfo, String]("申请加入成员")
      userInfoCol.setPrefWidth(width * 0.15)
      userInfoCol.setCellValueFactory(new PropertyValueFactory[AudienceListInfo, String]("userInfo"))

      val agreeBtnCol = new TableColumn[AudienceListInfo, Button]("同意")
      agreeBtnCol.setCellValueFactory(new PropertyValueFactory[AudienceListInfo, Button]("agreeBtn"))
      agreeBtnCol.setPrefWidth(width * 0.05)

      val refuseBtnCol = new TableColumn[AudienceListInfo, Button]("拒绝")
      refuseBtnCol.setCellValueFactory(new PropertyValueFactory[AudienceListInfo, Button]("refuseBtn"))
      refuseBtnCol.setPrefWidth(width * 0.05)

      AudienceTable.setItems(audObservableList)
      AudienceTable.getColumns.addAll(userInfoCol, agreeBtnCol, refuseBtnCol)
      AudienceTable.setPrefHeight(height * 0.4)
      AudienceTable
    }

    def createAcceptArea: TableView[AcceptList] = {
      val AcceptTable = new TableView[AcceptList]()
      AcceptTable.getStyleClass.add("table-view")

      val userInfoCol = new TableColumn[AcceptList, String]("已加入成员")
      userInfoCol.setPrefWidth(width * 0.1)
      userInfoCol.setCellValueFactory(new PropertyValueFactory[AcceptList, String]("userInfo"))

      val imageBtnCol = new TableColumn[AcceptList, Button]("图像")
      imageBtnCol.setCellValueFactory(new PropertyValueFactory[AcceptList, Button]("banOnImage"))
      imageBtnCol.setPrefWidth(width * 0.05)

      val soundBtnCol = new TableColumn[AcceptList, Button]("声音")
      soundBtnCol.setCellValueFactory(new PropertyValueFactory[AcceptList, Button]("banOnSound"))
      soundBtnCol.setPrefWidth(width * 0.05)

      val exitBtnCol = new TableColumn[AcceptList, Button]("退出")
      exitBtnCol.setCellValueFactory(new PropertyValueFactory[AcceptList, Button]("exitBtn"))
      exitBtnCol.setPrefWidth(width * 0.05)

      AcceptTable.setItems(audAcceptList)
      AcceptTable.getColumns.addAll(userInfoCol, imageBtnCol, soundBtnCol, exitBtnCol)
      AcceptTable.setPrefHeight(height * 0.3)
      AcceptTable
    }

    vBox

  }


  def addRightArea(): VBox = {

    def createUpBox = {

      val header = Pictures.getPic(RmManager.userInfo.get.headImgUrl)
      header.setFitHeight(40)
      header.setFitWidth(40)

      val userName = new Label(s"${RmManager.roomInfo.get.userName}")
      userName.getStyleClass.add("hostScene-rightArea-label")

      val userId = new Label(s"${RmManager.roomInfo.get.userId}")
      userId.getStyleClass.add("hostScene-rightArea-label")

      val userInfo = new VBox()
      userInfo.getChildren.addAll(userName, userId)
      userInfo.setSpacing(3)
      userInfo.setAlignment(Pos.CENTER_LEFT)

      val IDcard = new HBox()
      IDcard.getChildren.addAll(header, userInfo)
      IDcard.setSpacing(5)
      IDcard.setAlignment(Pos.CENTER_LEFT)
      IDcard.setPadding(new Insets(3, 3, 3, 3))
      IDcard.getStyleClass.add("hostScene-rightArea-IDcard")

      val upBox = new HBox()
      upBox.getChildren.addAll(IDcard)
      upBox.setAlignment(Pos.CENTER_LEFT)
      upBox.setSpacing(130)

      upBox
    }

    def createLivePane = {
      val livePane = new StackPane()
      livePane.setAlignment(Pos.BOTTOM_RIGHT)
      livePane.getChildren.addAll(liveImage)


      livePane.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
        livePane.setAlignment(Pos.BOTTOM_RIGHT)
        livePane.getChildren.add(barBox)
      })

      livePane.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
        livePane.setAlignment(Pos.BOTTOM_RIGHT)
        livePane.getChildren.remove(barBox)
      })

      livePane
    }

    val vBox = new VBox(createUpBox, createLivePane)
    vBox.getStyleClass.add("hostScene-rightArea-wholeBox")
    vBox.setSpacing(10)
    vBox.setPadding(new Insets(15, 65, 5, 65))
    vBox.setAlignment(Pos.TOP_CENTER)

    vBox
  }


  def addAllElement(): Unit = {
    group.getChildren.clear()
    fullScreenImage.getChildren.clear()
    rightArea = addRightArea()
    borderPane.setRight(rightArea)
    group.getChildren.add(borderPane)
  }

  def removeAllElement(): Unit = {
    group.getChildren.clear()
    fullScreenImage.getChildren.addAll(liveImage)
    fullScreenImage.setLayoutX(0)
    fullScreenImage.setLayoutY(0)
    group.getChildren.add(fullScreenImage)
  }

  def changeToggleAction(): Unit = {
    liveBar.startTimer()
    listener.startLive()
    liveBar.resetStartLiveTime(System.currentTimeMillis())
    isLive = true
  }
}
