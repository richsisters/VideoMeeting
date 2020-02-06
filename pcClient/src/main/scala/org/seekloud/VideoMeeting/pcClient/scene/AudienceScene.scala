package org.seekloud.VideoMeeting.pcClient.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.{Insets, Pos}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.control._
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
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.Comment
import org.slf4j.LoggerFactory

import scala.collection.mutable


/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:12
  */


object AudienceScene {

  trait AudienceSceneListener {

//    def sendCmt(comment: Comment)

//    def sendRecCmt(
//      comment: String,       //评论内容
//      commentTime: Long,     //评论的时间
//      relativeTime: Long,    //相对视频的时间
//      authorUidOpt: Option[Long] = None
//    )

//    def refreshRecCmt()

    def joinReq(roomId: Long)

    def quitJoin(roomId: Long, userId: Long)

    def gotoHomeScene()

    def setFullScreen(isRecord: Boolean)

    def exitFullScreen(isRecord: Boolean)

//    def like(userId: Long, roomId: Long, UpDown: Int)

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

  def startPackageLoss(): Unit = {
    log.info("start to get package loss.")
    timeline.setCycleCount(Animation.INDEFINITE)
    val keyFrame = new KeyFrame(Duration.millis(2000), { _ =>
      listener.ask4Loss()
      //      ppp
    })
    timeline.getKeyFrames.add(keyFrame)
    timeline.play()
  }

  //  def ppp = {
  //    val  CPUMemInfo= NetUsage.getCPUMemInfo
  //    CPUMemInfo.foreach { i =>
  //      val (cpu, memPer, memByte, proName) = (i.CPU, i.memPer, i.memByte, i.proName)
  ////      println(f"cpu：$cpu%.3f" + " %  " + f"mem percent：$memPer%.2f" + " %" + f"mem：$memByte")
  //    }
  //  }
  override def finalize(): Unit = {
    //    println("release")
    super.finalize()
  }

  def stopPackageLoss(): Unit = {
    timeline.stop()
  }

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  var watchUrl: Option[String] = None
  var liveId: Option[String] = None
  //  var recordUrl: Option[String] = None
  var commentPrefix = "effectType0"

  var isFullScreen = false
  var hasReqJoin = false

  var audienceStatus: Int = AudienceStatus.LIVE

  var watchingLs = List.empty[UserDes]
  val fullScreenImage = new StackPane()
  var leftArea: VBox = _
  var rightArea: VBox = _
  val gift4Img = new Image("img/rose.png")
//  val gift5Img = new Image("img/gift5.png")
//  val gift6Img = new Image("img/gift6.png")
  val waitPulling = new Image("img/waitPulling.gif")


  /*点赞*/
  val unLikeIcon: ImageView = Common.getImageView("img/like.png", 30, 30)
  val likeIcon: ImageView = Common.getImageView("img/liked.png", 30, 30)

//  val likeNum: Label = new Label(s"${album.like}")
//  likeNum.getStyleClass.add("hostScene-rightArea-label")

  val likeBtn = new ToggleButton("", unLikeIcon)
  likeBtn.getStyleClass.add("hostScene-middleArea-tableBtn")
  if (!isRecord) {
    likeBtn.setOnAction(_ => {
      if (RmManager.userInfo.nonEmpty) {
        if (likeBtn.isSelected) {
//          listener.like(RmManager.userInfo.get.userId, album.roomId, 1)
          likeBtn.setGraphic(likeIcon)
        } else {
//          listener.like(RmManager.userInfo.get.userId, album.roomId, 0)
          likeBtn.setGraphic(unLikeIcon)
        }
      } else {
        WarningDialog.initWarningDialog("请先登陆哦~")
      }
    }
                        )
    val shadow = new DropShadow(10, Color.GRAY)
    likeBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      likeBtn.setEffect(shadow)
    })
    likeBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      likeBtn.setEffect(null)
    })
  } else{
    likeBtn.setDisable(true)
  }

  /*留言*/
  val commentFiled = new TextField()
  val sendIcon: ImageView = Common.getImageView("img/confirm.png", 20, 20)
  val sendBtn = new Button("发送", sendIcon)

  /*屏幕下方功能条*/
  val liveBar: LiveBar = if(!isRecord){
    //看直播
    new LiveBar(Constants.WindowStatus.AUDIENCE_LIVE, width = Constants.DefaultPlayer.width, height = Constants.DefaultPlayer.height * 0.1)
  } else {
    //看录像
    new LiveBar(Constants.WindowStatus.AUDIENCE_REC, width = Constants.DefaultPlayer.width, height = Constants.DefaultPlayer.height * 0.1, Some(album.toRecordInfo.duration))
  }

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

  liveBar.startTimer()

  liveBar.playToggleButton.setOnAction {
    _ =>
      if (liveBar.playToggleButton.isSelected) {
        listener.continuePlayRec(album.toRecordInfo)
        liveBar.resetStartPlayTime(System.currentTimeMillis())
        Tooltip.install(liveBar.playToggleButton, new Tooltip("点击暂停"))
      } else {
        listener.pausePlayRec(album.toRecordInfo)
        liveBar.isPlaying = false
        //        liveBar.hasplayedTime = liveBar.showedPlayTime
        Tooltip.install(liveBar.playToggleButton, new Tooltip("点击继续"))
      }

  }

  val liveBarBox: VBox = liveBar.barVBox

  /*emoji*/
  val emoji = new Emoji(commentFiled, width * 0.6, height * 0.6)
  val emojiFont: String = emoji.emojiFont

  /*liveImage view*/

  val imgView = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
  val gc: GraphicsContext = imgView.getGraphicsContext2D

  val statisticsCanvas = new Canvas(Constants.DefaultPlayer.width, Constants.DefaultPlayer.height)
  val ctx: GraphicsContext = statisticsCanvas.getGraphicsContext2D

  val backImg = new Image("img/loading.jpg")
  gc.drawImage(backImg, 0, 0, gc.getCanvas.getWidth, gc.getCanvas.getHeight)
  val connectionBg = new Image("img/connectionBg.jpg")


  /*record*/
  val media: Media = if (isRecord) new Media(recordUrl) else null
  val playerPane: PlayerPane = if (isRecord) new PlayerPane(media, liveBar) else null
  private val player: MediaPlayer = if (isRecord) playerPane.getMediaPlayer else null
  val recView: MediaView = if (isRecord) playerPane.getMediaView else null
  if (isRecord) {
    recView.setFitHeight(Constants.DefaultPlayer.width)
    recView.setFitHeight(Constants.DefaultPlayer.height)
  }

  def playRecord(): Unit = {
    player.setStartTime(new util.Duration(0))
    player.play()
  }

  def pauseRecord(): Unit = {
    player.pause()
  }

  def stopRecord(): Unit = {
    player.stop()
  }

  def continueRecord(): Unit = {
    val status = player.getStatus
    if ((status eq Status.UNKNOWN) || (status eq Status.HALTED)) return
    if ((status eq Status.PAUSED) || (status eq Status.STOPPED) || (status eq Status.READY)) player.play()
  }

  def resetBack(): Unit = {
    gc.drawImage(connectionBg, 0, 0, gc.getCanvas.getWidth, gc.getCanvas.getHeight)
    val sWidth = gc.getCanvas.getWidth
    val sHeight = gc.getCanvas.getHeight
    gc.drawImage(waitPulling, sWidth / 2, sHeight / 4, sWidth / 2, sHeight / 2)
    gc.drawImage(waitPulling, 0, sHeight / 4, sWidth / 2, sHeight / 2)
    gc.setFont(Font.font(emojiFont, 25))
    gc.setFill(Color.BLACK)
    gc.fillText(s"连线中", imgView.getWidth / 2 - 40, imgView.getHeight / 8)
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

  /*弹幕*/
//  val barrage: Barrage = if(!isRecord){
//    new Barrage(Constants.WindowStatus.AUDIENCE_LIVE, imgView.getWidth, imgView.getHeight)
//  } else {
//    new Barrage(Constants.WindowStatus.AUDIENCE_REC, imgView.getWidth, imgView.getHeight, Some(player))
//  }
//  val barrageCanvas: Canvas = barrage.barrageView

  /*留言板*/
//  val commentBoard = new CommentBoard(imgView.getWidth, height * 0.18)
//  val commentArea: VBox = commentBoard.commentArea

  /*观看列表*/
//  val watchingList = new WatchingList(width * 0.1, width * 0.15, height * 0.3, None)
//  val watchingState: Text = if (!isRecord) watchingList.watchingState else new Text(s"有${album.observerNum}人已经看过该录像")
//  val watchingTable: TableView[WatchingList.WatchingListInfo] = watchingList.watchingTable

  /*录像留言列表*/
  //  val refreshBtn = new Button("刷新")
  //  refreshBtn.setOnAction(_ => listener.refreshRecCmt())
//  val recCommentBoard = new RecordCommentBoard(width * 0.25, 400, commentFiled)
//  val recCmtBox: ListView[VBox] = recCommentBoard.floorListView

  private val scene = new Scene(group, width, height)
  scene.getStylesheets.add(
    this.getClass.getClassLoader.getResource("css/common.css").toExternalForm
    )
  scene.setOnKeyPressed { e =>
    if (e.getCode == javafx.scene.input.KeyCode.ESCAPE) listener.exitFullScreen(isRecord)
  }
//  if(isRecord){
//    scene.setOnMouseClicked { e =>
//      commentFiled.setPromptText(s"^_^点我发弹幕~")
//      recCommentBoard.sayTo = None
//    }
//  }

  def getScene: Scene = this.scene

  def getRoomInfo: RoomInfo = this.album.toRoomInfo

  def getRecordInfo: RecordInfo = this.album.toRecordInfo

  def getIsRecord: Boolean = this.isRecord

  var listener: AudienceSceneListener = _

  def setListener(listener: AudienceSceneListener): Unit = {
    this.listener = listener
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
//    val viewLabel = new Label(album.observerNum.toString, viewIcon)
//    viewLabel.setPadding(new Insets(0,0,0,6))

    val IDcard = if(!isRecord){
      new HBox(header, userInfo, likeBtn)
    } else {
      new HBox(header, userInfo, likeBtn)
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
      val linkBtn = new Button("申请连线", new ImageView("img/link.png"))
      linkBtn.getStyleClass.add("audienceScene-leftArea-linkBtn")
      linkBtn.setOnAction{ _ =>
        if(!hasReqJoin) {
          listener.joinReq(album.roomId)
          hasReqJoin = true
        }
        else WarningDialog.initWarningDialog("已经发送过申请啦~")
      }
      Common.addButtonEffect(linkBtn)

      val exitBtn = new Button("中断连线", new ImageView("img/shutdown.png"))
      exitBtn.getStyleClass.add("audienceScene-leftArea-linkBtn")
      exitBtn.setOnAction(_ => listener.quitJoin(album.roomId, album.userId))
      Common.addButtonEffect(exitBtn)

      val buttonBox = new HBox(linkBtn, exitBtn)
      buttonBox.setSpacing(15)
      buttonBox.setAlignment(Pos.CENTER)

      buttonBox

    }

    def createAudLbArea: Label = {

      val audienceIcon = Common.getImageView("img/watching.png",30,30)
      val audienceLabel = new Label("观众列表",audienceIcon)
      audienceLabel.getStyleClass.add("hostScene-leftArea-label")
      audienceLabel

    }

    val leftAreaBox = if (!isRecord) {
      //看直播
      new VBox(createRoomInfoBox, createButtonBox, createAudLbArea)
    } else {
      //看录像
      new VBox(createRoomInfoBox)
    }
    if(!isRecord) {
      leftAreaBox.setSpacing(5)
      leftAreaBox.setPadding(new Insets(25, 10, 10, 10))
    } else {
      leftAreaBox.setSpacing(15)
      leftAreaBox.setPadding(new Insets(25, 10, 10, 10))
      leftAreaBox.setPrefWidth(width*0.3)
    }
    leftAreaBox.setPrefHeight(height)
    leftAreaBox.getStyleClass.add("hostScene-leftArea-wholeBox")
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
    if (!isRecord) {
      livePane.getChildren.addAll(imgView, statisticsCanvas)
    } else {
      livePane.getChildren.addAll(playerPane.getMediaView)
    }
    livePane.setAlignment(Pos.BOTTOM_RIGHT)

    livePane.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      livePane.setAlignment(Pos.BOTTOM_RIGHT)
      livePane.getChildren.add(liveBarBox)

      //      if (isRecord) {
      //        livePane.setAlignment(Pos.BOTTOM_RIGHT)
      //        livePane.getChildren.add(playerPane.mediaTopBar)
      //      }
      //        playerPane.addTopAndBottom()
    })

    livePane.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      livePane.setAlignment(Pos.BASELINE_RIGHT)
      livePane.getChildren.remove(liveBarBox)
      //      if (isRecord) {
      //        livePane.setAlignment(Pos.BOTTOM_RIGHT)
      //        livePane.getChildren.remove(playerPane.mediaTopBar)
      //      }
      //        playerPane.removeTopAndBottom()
    })

//    val gift = new GiftBar(group)

//    def sendGiftAction(input: TextField, btn: Button, name: String, giftDes: VBox, giftType: Int): Unit = {
//      btn.setOnAction(_ => {
//        if (input.getText.nonEmpty) {
////          listener.sendCmt(Comment(RmManager.userInfo.get.userId, album.roomId,s"送出${input.getText()}个$name！", extension = Some(s"gift$giftType")))
//          input.clear()
//          group.getChildren.remove(giftDes)
//        }
//        else WarningDialog.initWarningDialog("请输入数量")
//      }
//                      )
//    }

//    sendGiftAction(gift.input1, gift.sendBtn1, "冰可乐", gift.gift1Des, 1)
//    sendGiftAction(gift.input2, gift.sendBtn2, "雪糕", gift.gift2Des, 2)
//    sendGiftAction(gift.input3, gift.sendBtn3, "巧克力", gift.gift3Des, 3)
//    sendGiftAction(gift.input4, gift.sendBtn4, "鲜花", gift.gift4Des, 4)
//    sendGiftAction(gift.input5, gift.sendBtn5, "飞船", gift.gift5Des, 5)
//    sendGiftAction(gift.input6, gift.sendBtn6, "火箭", gift.gift6Des, 6)

    val hBox = new HBox()
    if (!isRecord) {
      hBox.getChildren.addAll(livePane)
    } else {
      hBox.getChildren.addAll(livePane)
    }

  /*  def createWriteLiveComment: HBox = {

      val effectOptions: ObservableList[String] =
        FXCollections.observableArrayList(
          "普通弹幕",
          "放大缩小",
          "闪入闪出",
          "定点放缩"
          )
      val effectChoiceCBx = new ComboBox(effectOptions)
      effectChoiceCBx.setValue("普通弹幕")

      effectChoiceCBx.setOnAction {
        _ => {
          effectChoiceCBx.getValue match {
            case "普通弹幕" =>
              commentPrefix = "effectType0"
            case "放大缩小" =>
              commentPrefix = "effectType1"
            case "闪入闪出" =>
              commentPrefix = "effectType2"
            case "定点放缩" =>
              commentPrefix = "effectType3"
            case _ =>
              commentPrefix = "effectType0"
          }
        }
      }

//      commentFiled.setPrefWidth(imgView.getWidth * 0.65)
//      commentFiled.setPrefHeight(30)
//      commentFiled.setFont(Font.font(emojiFont, 15))
//      commentFiled.setPromptText("输入你的留言~")
//      commentFiled.getStyleClass.add("text-area")
//      commentFiled.setOnKeyPressed { e =>
//        if (e.getCode == javafx.scene.input.KeyCode.ENTER) {
//          val comment = Comment(RmManager.roomInfo.get.userId, album.roomId, s"${commentFiled.getText}", extension = Some(commentPrefix))
//          listener.sendCmt(comment)
//          commentFiled.clear()
//        }
//      }

      val emojiBtn = new Button("\uD83D\uDE00")
      emojiBtn.setStyle("-fx-background-radius: 5px;")
      emojiBtn.setFont(Font.font(emojiFont, 15))
      val emojiArea = emoji.getEmojiGridPane

      var emojiBtnClick = true
      emojiBtn.setOnAction { _ =>
        if (emojiBtnClick) {
          group.getChildren.add(1, emojiArea)
        } else {
          group.getChildren.remove(emojiArea)
        }
        emojiBtnClick = !emojiBtnClick
      }
      Common.addButtonEffect(emojiBtn)

      val sendIcon = Common.getImageView("img/confirm.png", 20, 20)
      val sendBtn = new Button("发送", sendIcon)
      sendBtn.getStyleClass.add("audienceScene-leftArea-sendBtn")
      sendBtn.setOnAction { _ =>
        if (commentFiled.getText != null) {
          if (RmManager.userInfo.nonEmpty) {
            val comment = Comment(RmManager.roomInfo.get.userId, album.roomId, s"${commentFiled.getText}", extension = Some(commentPrefix))
//            listener.sendCmt(comment)
            commentFiled.clear()
          } else {
            WarningDialog.initWarningDialog("请先登录哦~")
          }
        } else {
          WarningDialog.initWarningDialog("评论输入不能为空！")
        }
      }
      Common.addButtonEffect(sendBtn)


      val commentBox = new HBox(commentFiled, emojiBtn, effectChoiceCBx, sendBtn)
      commentBox.setAlignment(Pos.CENTER)
      commentBox.setSpacing(10)
      commentBox

    } */

 /*   def createWriteRecComment: HBox = {
      commentFiled.setPrefWidth(imgView.getWidth * 0.8)
      commentFiled.setPrefHeight(30)
      commentFiled.setFont(Font.font(emojiFont, 15))
      commentFiled.setPromptText("^_^点我发弹幕~")
      commentFiled.getStyleClass.add("text-area")
      commentFiled.setOnKeyPressed { e =>
        if (e.getCode == javafx.scene.input.KeyCode.ENTER) {
//          listener.sendRecCmt(s"${commentFiled.getText}", System.currentTimeMillis(), player.getCurrentTime.toMillis.toLong)
          commentFiled.clear()
        }
      }

      val emojiBtn = new Button("\uD83D\uDE00")
      emojiBtn.setStyle("-fx-background-radius: 5px;")
      emojiBtn.setFont(Font.font(emojiFont, 15))
      val emojiArea = emoji.getEmojiGridPane

      var emojiBtnClick = true
      emojiBtn.setOnAction { _ =>
        if (emojiBtnClick) {
          group.getChildren.add(1, emojiArea)
        } else {
          group.getChildren.remove(emojiArea)
        }
        emojiBtnClick = !emojiBtnClick
      }
      Common.addButtonEffect(emojiBtn)


      sendBtn.getStyleClass.add("audienceScene-leftArea-sendBtn")
      sendBtn.setOnAction { _ =>
        if (commentFiled.getText != null) {
          if (RmManager.userInfo.nonEmpty) {
//            listener.sendRecCmt(s"${commentFiled.getText}", System.currentTimeMillis(), player.getCurrentTime.toMillis.toLong)
            commentFiled.clear()
          } else {
            WarningDialog.initWarningDialog("请先登录哦~")
          }
        } else {
          WarningDialog.initWarningDialog("评论输入不能为空！")
        }
      }
      Common.addButtonEffect(sendBtn)


      val commentBox = new HBox(commentFiled, emojiBtn, sendBtn)
      commentBox.setAlignment(Pos.CENTER)
      commentBox.setSpacing(10)
      commentBox

    } */

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
    if (isRecord) fullScreenImage.getChildren.addAll(recView, liveBarBox)
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
