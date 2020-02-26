package org.seekloud.VideoMeeting.pcClient.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.beans.property.{SimpleStringProperty, StringProperty}
import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.control._
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.effect.{DropShadow, Glow}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.scene.layout._
import javafx.scene.media.MediaPlayer.Status
import javafx.scene.media.{Media, MediaPlayer, MediaView}
import javafx.scene.{Group, Scene}
import org.seekloud.VideoMeeting.pcClient.common._
import javafx.scene.paint.Color
import javafx.scene.text.{Font, Text}
import javafx.util
import javafx.util.Duration
import org.seekloud.VideoMeeting.pcClient.common.Constants.AudienceStatus
import org.seekloud.VideoMeeting.pcClient.component._
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.core.stream.StreamPuller.{BandWidthInfo, PackageLossInfo}
import org.seekloud.VideoMeeting.pcClient.utils.{NetUsage, TimeUtil}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, RoomInfo, UserDes}
import org.slf4j.LoggerFactory

import scala.collection.mutable


/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:12
  */


object AudienceScene {

  case class AttendList(
                         attendInfo: StringProperty
                       ){
    def getUserInfo: String = attendInfo.get()

    def setUserInfo(info: String): Unit = attendInfo.set(info)

  }


  trait AudienceSceneListener {

    def joinReq(roomId: Long)

    def quitJoin(roomId: Long, userId: Long)

    def gotoHomeScene()

    def setFullScreen(isRecord: Boolean)

    def exitFullScreen(isRecord: Boolean)

    def changeOption(needImage: Boolean = true, needSound: Boolean = true)

    def ask4Loss()

    def pausePlayRec(recordInfo: RecordInfo)

    def continuePlayRec(recordInfo: RecordInfo)
  }


}

class AudienceScene(album: AlbumInfo, isRecord: Boolean = false, recordUrl: String = "") {
  import AudienceScene._

  private val width = Constants.AppWindow.width * 0.9
  private val height = Constants.AppWindow.height * 0.75

  private val group = new Group()

  private val timeline = new Timeline()

  val audAttendList: ObservableList[AttendList] = FXCollections.observableArrayList()

  def startPackageLoss(): Unit = {
    log.info("start to get package loss.")
    timeline.setCycleCount(Animation.INDEFINITE)
    val keyFrame = new KeyFrame(Duration.millis(2000), { _ =>
      listener.ask4Loss()
    })
    timeline.getKeyFrames.add(keyFrame)
    timeline.play()
  }

  override def finalize(): Unit = {
    //    println("release")
    super.finalize()
  }

  def stopPackageLoss(): Unit = {
    timeline.stop()
  }

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  var watchUrl: Option[String] = None
  var liveId4Connect:Option[String] = None
  var liveId4Live: Option[String] = None
  var commentPrefix = "effectType0"

  var isFullScreen = false
  var hasReqJoin = false

  var audienceStatus: Int = AudienceStatus.LIVE

  var watchingLs = List.empty[UserDes]
  val fullScreenImage = new StackPane()
  var leftArea: VBox = _
  var rightArea: VBox = _
  val waitPulling = new Image("img/waitPulling.gif")

  /*留言*/
  val commentFiled = new TextField()

  /*屏幕下方功能条*/
  val liveBar: LiveBar = new LiveBar(Constants.WindowStatus.AUDIENCE, width = Constants.DefaultPlayer.width, height = Constants.DefaultPlayer.height * 0.1)

  liveBar.fullScreenIcon.setOnAction{ _ =>
    if(!isFullScreen) listener.setFullScreen(isRecord)
    else listener.exitFullScreen(isRecord)
  }
  val imageToggleBtn: ToggleButton = liveBar.imageToggleButton
  val soundToggleBtn: ToggleButton = liveBar.soundToggleButton

  imageToggleBtn.setOnAction {
    _ =>
      listener.changeOption(needImage = imageToggleBtn.isSelected, needSound = soundToggleBtn.isSelected)
  }

  soundToggleBtn.setOnAction {
    _ =>
      listener.changeOption(needImage = imageToggleBtn.isSelected, needSound = soundToggleBtn.isSelected)
  }

  liveBar.resetStartLiveTime(System.currentTimeMillis())
  liveBar.startTimer()

  val liveBarBox: VBox = liveBar.barVBox

  /*emoji*/
  val emoji = new Emoji(commentFiled, width * 0.6, height * 0.6)
  val emojiFont: String = emoji.emojiFont

  /*liveImage view*/

  val imgView = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
  val gc: GraphicsContext = imgView.getGraphicsContext2D

  val statisticsCanvas = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
  val ctx: GraphicsContext = statisticsCanvas.getGraphicsContext2D

  val backImg = new Image("img/background.jpg")
  gc.drawImage(backImg, 0, 0, gc.getCanvas.getWidth, gc.getCanvas.getHeight)
  val connectionBg = new Image("img/connectionBg.jpg")

  def resetBack(): Unit = {
    val sWidth = gc.getCanvas.getWidth
    val sHeight = gc.getCanvas.getHeight
    gc.drawImage(connectionBg, 0, 0, sWidth, sHeight)
    gc.drawImage(waitPulling, 0, 0, sWidth / 2, sHeight / 2)
    gc.drawImage(waitPulling, 0, sHeight / 2, sWidth / 2, sHeight / 2)
    gc.drawImage(waitPulling, sWidth / 2, 0, sWidth / 2, sHeight / 2)
    gc.drawImage(waitPulling, sWidth / 2, sHeight / 2, sWidth / 2, sHeight / 2)
    gc.setFont(Font.font(25))
    gc.setFill(Color.BLACK)
    //gc.fillText(s"录像中", liveImage.getWidth / 2 - 40, liveImage.getHeight / 8)
   // gc.fillText(s"等待会议开启～", sWidth / 2 - 40, sHeight / 2)
  }

  def loadingBack(): Unit = {
    gc.drawImage(waitPulling, 0, 0, gc.getCanvas.getWidth, gc.getCanvas.getHeight)
  }

  def autoReset(): Unit = {
    audienceStatus match {
      case AudienceStatus.LIVE =>
        loadingBack()
      case AudienceStatus.CONNECT =>
        resetBack()
    }
  }

  private val scene = new Scene(group, width, height)
  scene.getStylesheets.add(
    this.getClass.getClassLoader.getResource("css/common.css").toExternalForm
    )
  scene.setOnKeyPressed { e =>
    if (e.getCode == javafx.scene.input.KeyCode.ESCAPE) listener.exitFullScreen(isRecord)
  }

  def getScene: Scene = this.scene

  def getRoomInfo: RoomInfo = this.album.toRoomInfo

  def getRecordInfo: RecordInfo = this.album.toRecordInfo

  def getIsRecord: Boolean = this.isRecord

  var listener: AudienceSceneListener = _

  def setListener(listener: AudienceSceneListener): Unit = {
    this.listener = listener
  }

  def updateAttendList(userId: Long, userName: String, add:Boolean): Unit = {

    val newRequest = AttendList(
      new SimpleStringProperty(s"$userName")
    )

    if(add)
      audAttendList.add(newRequest)
    else
      audAttendList.remove(newRequest)
  }

  def createIDcard: HBox = {

    val header = Pictures.getPic(album.headImgUrl)
    header.setFitHeight(40)
    header.setFitWidth(40)

    val userName = new Label(s"${album.userName}")
    userName.getStyleClass.add("hostScene-rightArea-label")

    val userId = new Label(s"${album.userId}")
    userName.getStyleClass.add("hostScene-rightArea-label")

    val userInfo = new VBox()
    userInfo.getChildren.addAll(userName, userId)
    userInfo.setSpacing(5)
    userInfo.setPadding(new Insets(0, 5, 0, 5))
    userInfo.setAlignment(Pos.CENTER_LEFT)


    val viewIcon = Common.getImageView("img/view.png", 30, 30)

    val IDcard = if(!isRecord){
      new HBox(header, userInfo)
    } else {
      new HBox(header, userInfo)
    }
    IDcard.setAlignment(Pos.CENTER_LEFT)
    IDcard.setPadding(new Insets(6, 5, 6, 3))
    IDcard.getStyleClass.add("hostScene-rightArea-IDcard")

    IDcard
  }

  val borderPane: BorderPane = addBorderPane()
  group.getChildren.addAll(borderPane)


  def addBorderPane(): BorderPane = {
    leftArea = addLeftArea()
    rightArea = addRightArea()
    val borderPane = new BorderPane
    borderPane.setLeft(leftArea)
    borderPane.setRight(rightArea)
    borderPane
  }

  def addLeftArea(): VBox = {

    val leftAreaBox = new VBox
    leftAreaBox.getChildren.addAll(createRoomInfoBox, createButtonBox, createAcceptArea)
    leftAreaBox.setSpacing(15)
    leftAreaBox.setPadding(new Insets(25, 10, 10, 10))
//    leftAreaBox.setPrefWidth(width*0.3)
    leftAreaBox.setPrefHeight(height)
    leftAreaBox.getStyleClass.add("hostScene-leftArea-wholeBox")

    def createRoomInfoBox: VBox = {
      //roomName
      val roomNameIcon = Common.getImageView("img/roomName.png", 30, 30)
      val roomNameText = new Text(album.roomName)
      roomNameText.setWrappingWidth(width * 0.2)
      roomNameText.getStyleClass.add("audienceScene-leftArea-roomNameText")

      val roomName = new HBox()
      roomName.getChildren.addAll(roomNameIcon, roomNameText)
      roomName.setPadding(new Insets(20, 0, 0, 0))
      roomName.setAlignment(Pos.CENTER_LEFT)
      roomName.setSpacing(8)

      //roomDes
      val roomDesIcon = Common.getImageView("img/roomDes.png", 30, 30)
      val roomDesText = if(album.roomDes.nonEmpty){
        new Text(album.roomDes)
      } else {
        new Text("TA还没有描述哦~")
      }
      roomDesText.setWrappingWidth(width * 0.2)
      roomDesText.getStyleClass.add("audienceScene-leftArea-roomDesText")

      val roomDes = new HBox()
      roomDes.getChildren.addAll(roomDesIcon, roomDesText)
      roomDes.setAlignment(Pos.CENTER_LEFT)
      roomDes.setSpacing(8)

      val infoBox = new VBox(roomName, roomDes)
      infoBox.setSpacing(20)
      infoBox.setPadding(new Insets(0, 0, 40, 0))

      infoBox
    }

    def createButtonBox: HBox = {
      val linkBtn = new Button("加入会议", new ImageView("img/link.png"))
      linkBtn.getStyleClass.add("audienceScene-leftArea-linkBtn")
      linkBtn.setOnAction{ _ =>
        if(!hasReqJoin) {
          listener.joinReq(album.roomId)
          hasReqJoin = true
        }
        else WarningDialog.initWarningDialog("已经发送过申请啦~")
      }
      Common.addButtonEffect(linkBtn)

      val exitBtn = new Button("退出会议", new ImageView("img/shutdown.png"))
      exitBtn.getStyleClass.add("audienceScene-leftArea-linkBtn")
      exitBtn.setOnAction(_ => listener.quitJoin(album.roomId, album.userId))
      Common.addButtonEffect(exitBtn)

      val buttonBox = new HBox(linkBtn, exitBtn)
      buttonBox.setSpacing(15)
      buttonBox.setAlignment(Pos.CENTER)

      buttonBox

    }

    def createAcceptArea: TableView[AttendList] = {
      val AttendTable = new TableView[AttendList]()
      AttendTable.getStyleClass.add("table-view")

      val userInfoCol = new TableColumn[AttendList, String]("已加入成员")
      userInfoCol.setPrefWidth(width * 0.15)
      userInfoCol.setCellValueFactory(new PropertyValueFactory[AttendList, String]("userInfo"))

      AttendTable.setItems(audAttendList)
      AttendTable.getColumns.addAll(userInfoCol)
      AttendTable.setPrefHeight(height * 0.3)
      AttendTable
    }

    leftAreaBox

  }

  def addRightArea(): VBox = {
    def createTopBox() = {
      val backBtn = new Button("", new ImageView("img/audienceBack.png"))
      backBtn.getStyleClass.add("audienceScene-leftArea-backBtn")
      backBtn.setOnAction(_ => listener.gotoHomeScene())
      Common.addButtonEffect(backBtn)

      val IDcard: HBox = createIDcard

      val leftBox = new HBox(IDcard)
      leftBox.setPrefWidth(imgView.getWidth * 0.6)
      leftBox.setAlignment(Pos.CENTER_LEFT)

      val rightBox = new HBox(backBtn)
      rightBox.setPrefWidth(imgView.getWidth * 0.4)
      rightBox.setAlignment(Pos.CENTER_RIGHT)

      val topBox = new HBox(leftBox, rightBox)
      topBox
    }

    val livePane = new StackPane()
    livePane.getChildren.addAll(imgView, statisticsCanvas)
    livePane.setAlignment(Pos.BOTTOM_RIGHT)
    livePane.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      livePane.setAlignment(Pos.BOTTOM_RIGHT)
      livePane.getChildren.add(liveBarBox)
    })
    livePane.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      livePane.setAlignment(Pos.BASELINE_RIGHT)
      livePane.getChildren.remove(liveBarBox)
    })

    val hBox = new HBox()
    if (!isRecord) {
      hBox.getChildren.addAll(livePane)
    } else {
      hBox.getChildren.addAll(livePane)
    }

    val vBox = if (!isRecord) {
      new VBox(createTopBox(), hBox)
    } else {
      new VBox(createTopBox(), hBox)
    }
    vBox.getStyleClass.add("hostScene-rightArea-wholeBox")
    if(!isRecord){
      vBox.setSpacing(10)
      vBox.setPadding(new Insets(15, 35, 0, 35))
    } else{
      vBox.setSpacing(30)
      vBox.setPadding(new Insets(50, 44, 0, 44))
    }
    vBox.setAlignment(Pos.TOP_CENTER)
    vBox
  }

  def addAllElement(): Unit = {
    group.getChildren.clear()
    fullScreenImage.getChildren.clear()
    rightArea = addRightArea()
    borderPane.setRight(rightArea)
    group.getChildren.addAll(borderPane)

  }

  def removeAllElement(): Unit = {
    group.getChildren.clear()
    fullScreenImage.getChildren.addAll(imgView, statisticsCanvas)
    fullScreenImage.setLayoutX(0)
    fullScreenImage.setLayoutY(0)
    group.getChildren.add(fullScreenImage)
  }

  def drawPackageLoss(info: mutable.Map[String, PackageLossInfo], bandInfo: Map[String, BandWidthInfo]): Unit = {
    ctx.save()
    //    println(s"draw loss, ${ctx.getCanvas.getWidth}, ${ctx.getCanvas.getHeight}")
    ctx.setFont(new Font("Comic Sans Ms", if(!isFullScreen) 10 else 20))
    ctx.setFill(Color.WHITE)
    val loss: Double = if (info.values.headOption.nonEmpty) info.values.head.lossScale2 else 0
    val band: Double = if (bandInfo.values.headOption.nonEmpty) bandInfo.values.head.bandWidth2s else 0
    val  CPUMemInfo= NetUsage.getCPUMemInfo
    ctx.clearRect(0, 0, ctx.getCanvas.getWidth, ctx.getCanvas.getHeight)
    CPUMemInfo.foreach { i =>
      val (memPer, memByte, proName) = (i.memPer, i.memByte, i.proName)
      ctx.fillText(f"内存占比：$memPer%.2f" + " % " + f"内存：$memByte" , statisticsCanvas.getWidth - 210, 15)
    }
    ctx.fillText(f"丢包率：$loss%.3f" + " %  " + f"带宽：$band%.2f" + " bit/s", 0, 15)
    //    info.values.headOption.foreach(i => ctx.fillText(f"丢包率：${i.lossScale2}%.2f" + " %", Constants.DefaultPlayer.width / 5 * 4, 20))
    ctx.restore()
  }

}
