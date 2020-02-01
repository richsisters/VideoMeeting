package org.seekloud.VideoMeeting.pcClient.scene

import javafx.beans.property.{ObjectProperty, SimpleObjectProperty, SimpleStringProperty, StringProperty}
import javafx.collections.{FXCollections, ObservableList}
import javafx.geometry.{Insets, Pos, Side}
import javafx.scene.control._
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.effect.{DropShadow, Glow}
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout._
import javafx.scene.text.{Font, Text}
import javafx.scene.{Group, Scene}
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.Boot.executor
import org.seekloud.VideoMeeting.pcClient.common._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, RoomInfo, UserInfo}
import org.slf4j.LoggerFactory
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Stage
import org.seekloud.VideoMeeting.pcClient.component.{Common, WarningDialog}
import org.seekloud.VideoMeeting.pcClient.component.Common._
import org.seekloud.VideoMeeting.pcClient.utils.{RMClient, TimeUtil}

import scala.collection.mutable
import scala.concurrent.Future

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:13
  */

object RoomScene {

  case class RoomListInfo(
    roomId: StringProperty,
    roomName: StringProperty,
    roomDes: StringProperty,
    userId: StringProperty,
    enterBtn: ObjectProperty[Button]
  ) {
    def getRoomId: String = roomId.get()

    def setRoomId(id: String): Unit = roomId.set(id)

    def getRoomName: String = roomName.get()

    def setRoomName(id: String): Unit = roomName.set(id)

    def getRoomDes: String = roomDes.get()

    def setRoomDes(id: String): Unit = roomDes.set(id)

    def getUserId: String = userId.get()

    def setUserId(id: String): Unit = userId.set(id)

    def getEnterBtn: Button = enterBtn.get()

    def setEnterBtn(btn: Button): Unit = enterBtn.set(btn)

  }

  trait RoomSceneListener {

    def enter(roomId: Long, timestamp: Long = 0L)

    //    def create()

    def refresh()

    def gotoHomeScene()
  }

}

class RoomScene {

  import RoomScene._

  private val log = LoggerFactory.getLogger(this.getClass)

  private val width = Constants.AppWindow.width * 0.9
  private val height = Constants.AppWindow.height * 0.75

  val group = new Group()
  private val scene = new Scene(group, width, height)

  val waitingGif = new ImageView("img/waiting.gif")
  waitingGif.setFitHeight(50)
  waitingGif.setFitWidth(50)
  waitingGif.setLayoutX(width / 2 - 25)
  waitingGif.setLayoutY(height / 2 - 25)

  scene.getStylesheets.add(
    this.getClass.getClassLoader.getResource("css/common.css").toExternalForm
  )

  def getScene: Scene = this.scene

  //  private val roomMap = mutable.HashMap.empty[Long, (String, String, Long, String, String, Int)] //roomId -> (roomName, roomDes, userId, userName, coverImgUrl, observerNum)

  /*tab nav*/
  //  val roomTableLabel = new Label("房间列表")
  //  roomTableLabel.getStyleClass.add("roomScene-roomTableLabel")
  var liveMode: Boolean = true

  /*live*/
  var roomList: List[RoomInfo] = Nil

  /*record*/
  val recordsPerPage: Int = 6
  val maxPagiNum: Int = 10
  var recordList: List[(Int, List[RecordInfo])] = Nil
  var recordSort: String = "time"
  var recordPageIndex: Int = 1
  var recordsSize: Int = 0
  var pendingPage: Int = -1


  val liveIcon = new ImageView("img/liveRooms.png")
  liveIcon.setFitHeight(55)
  liveIcon.setFitWidth(55)
  val recordIcon = new ImageView("img/recordRooms.png")
  recordIcon.setFitHeight(35)
  recordIcon.setFitWidth(35)

  val liveBtn = new Button("", liveIcon)
  Tooltip.install(liveBtn, new Tooltip("观看直播"))
  val recordBtn = new Button("", recordIcon)
  Tooltip.install(recordBtn, new Tooltip("观看录像"))

  liveBtn.getStyleClass.add("roomScene-topBtn")
  recordBtn.getStyleClass.add("roomScene-topBtn")


  val shadow1 = new DropShadow(10, Color.GRAY)
  liveBtn.setEffect(shadow1)
  recordBtn.setEffect(shadow1)

  liveBtn.setOnAction { _ =>
    liveMode = true
    liveIcon.setFitHeight(55)
    liveIcon.setFitWidth(55)
    recordIcon.setFitHeight(35)
    recordIcon.setFitWidth(35)
    listener.refresh()
  }
  recordBtn.setOnAction { _ =>
    liveMode = false
    recordIcon.setFitHeight(55)
    recordIcon.setFitWidth(55)
    liveIcon.setFitHeight(35)
    liveIcon.setFitWidth(35)
    listener.refresh()
  }

  /*liveBox*/
  val liveLabelIcon = new ImageView("img/liveLabel.png")
  liveLabelIcon.setFitHeight(30)
  liveLabelIcon.setFitWidth(30)
  val liveLabel = new Label("直播", liveLabelIcon)
  liveLabel.setFont(Font.font(25))
  val liveInfo = new Text("")
  liveInfo.setFont(Font.font(15))
  val liveBox = new HBox(20, liveLabel, liveInfo)
  liveBox.setAlignment(Pos.CENTER_LEFT)
  liveBox.setPadding(new Insets(10, 110, 0, 110))

  /*recordBox*/
  val recordLabelIcon = new ImageView("img/recordLabel.png")
  recordLabelIcon.setFitHeight(45)
  recordLabelIcon.setFitWidth(45)
  val recordLabel = new Label("录像", recordLabelIcon)
  recordLabel.setFont(Font.font(25))
  val recordInfo = new Text("")
  recordInfo.setFont(Font.font(15))

  /*选择式按钮*/
//  val btn1 = new Button("按时间排序")
//  btn1.setOnAction { _ =>
//    recordSort = "time"
//    listener.refresh()
//  }
//  val btn2 = new Button("按播放量排序")
//  btn2.setOnAction { _ =>
//    recordSort = "view"
//    listener.refresh()
//  }
//  val btn3 = new Button("按点赞量排序")
//  btn3.setOnAction { _ =>
//    recordSort = "like"
//    listener.refresh()
//  }
//  val btnBox = new HBox(5, btn1, btn2, btn3)
//  btnBox.setAlignment(Pos.CENTER_LEFT)


  /*下拉式列表*/
  val filterIcon = getImageView("img/filter.png", 20, 20)
  val filterLabel = new Label("筛选:", filterIcon)
  filterLabel.setFont(Font.font(15))

  val filters = List("按时间排序", "按播放量排序", "按点赞量排序")

  val filterOptions: ObservableList[String] = FXCollections.observableArrayList()
  filters.foreach(filterOptions.add)

  val filterChoiceBx = new ComboBox(filterOptions)
  filterChoiceBx.setStyle("-fx-background-color: #a3b7d2;")
  filterChoiceBx.getSelectionModel.select(0)

  filterChoiceBx.setOnAction {
    _ =>
      filterChoiceBx.getValue match {
        case "按时间排序" =>
          recordSort = "time"
          listener.refresh()
        case "按播放量排序" =>
          recordSort = "view"
          listener.refresh()
        case "按点赞量排序" =>
          recordSort = "like"
          listener.refresh()
        case _ => // do nothing
      }
  }


  val leftBox = new HBox(20, recordLabel, recordInfo)
  leftBox.setPrefWidth(625)
  leftBox.setAlignment(Pos.CENTER_LEFT)

  val filterBox = new HBox(filterLabel, filterChoiceBx)
  filterBox.setPrefWidth(200)
  filterBox.setSpacing(10)
  filterBox.setAlignment(Pos.CENTER_RIGHT)


  val recordBox = new HBox(leftBox, filterBox)
//  recordBox.setAlignment(Pos.CENTER_LEFT)
  recordBox.setPadding(new Insets(5, 110, 0, 110))

  val recCenter = new VBox(recordBox)


  /*buttons*/
  private val refreshBtn = new Button("", new ImageView("img/refreshBtn.png"))
  refreshBtn.getStyleClass.add("roomScene-refreshBtn")

  val backBtn = new Button("", new ImageView("img/backBtn1.png"))
  backBtn.getStyleClass.add("roomScene-backBtn")

  val shadow = new DropShadow()

  refreshBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
    refreshBtn.setEffect(shadow)
  })
  refreshBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
    refreshBtn.setEffect(null)
  })

  backBtn.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
    backBtn.setEffect(shadow)
  })
  backBtn.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
    backBtn.setEffect(null)
  })

  refreshBtn.setOnAction(_ => listener.refresh())
  backBtn.setOnAction(_ => listener.gotoHomeScene())

  /*layout*/
  val backBtnBox = new HBox()
  backBtnBox.getChildren.add(backBtn)
  backBtnBox.setPadding(new Insets(15, 0, 0, 20))
  backBtnBox.setAlignment(Pos.TOP_LEFT)

  val refreshBtnBox = new HBox()
  refreshBtnBox.getChildren.add(refreshBtn)
  refreshBtnBox.setPadding(new Insets(15, 20, 0, 0))
  refreshBtnBox.setAlignment(Pos.TOP_RIGHT)

  val roomTableLabelBox = new HBox(25, liveBtn, recordBtn)
  roomTableLabelBox.setPadding(new Insets(0, 0, 15, 0))
  roomTableLabelBox.setAlignment(Pos.BOTTOM_CENTER)

  val topBox = new HBox()
  topBox.getChildren.addAll(backBtnBox, roomTableLabelBox, refreshBtnBox)
  topBox.getStyleClass.add("hostScene-leftArea-wholeBox")
  topBox.setPrefSize(width, height * 0.15)
  topBox.setSpacing(width * 0.33)
  topBox.setAlignment(Pos.CENTER)


  def createOnePage(pageIndex: Int, itemsPerPage: Int, albumList: List[AlbumInfo]): VBox = {
    val vBox = new VBox()
    vBox.setPadding(new Insets(10, 110, 20, 110))
    vBox.setSpacing(30)
    val hBox1 = new HBox()
    hBox1.setSpacing(25)
    val hBox2 = new HBox()
    hBox2.setSpacing(25)
    val totalLen = albumList.length
    val start = pageIndex * itemsPerPage + 1
    val end = (pageIndex + 1) * itemsPerPage
    for (i <- start to (start + 2)) {
      if (i <= totalLen) {
        val roomBox = new VBox(3)
        // stackPane: roomPic & picBar(userName & viewNum & likeNum)
        val roomPic = Pictures.getPic(albumList(i - 1).coverImgUrl, isHeader = false)
        roomPic.setFitHeight(Constants.DefaultPlayer.height / 2.5)
        roomPic.setFitWidth(Constants.DefaultPlayer.width / 2.5)
        roomPic.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
          listener.enter(albumList(i - 1).roomId, albumList(i - 1).timestamp)
        })

        val userName = new Label(s"${albumList(i - 1).userName}")
        userName.setPrefWidth(120)
        userName.getStyleClass.add("roomScene-userName")

        val audienceNumIcon = Common.getImageView("img/roomScene-view.png", 25, 25)
        val audienceNum = new Label(s"${albumList(i - 1).observerNum}", audienceNumIcon)
        audienceNum.setPrefWidth(80)
        audienceNum.getStyleClass.add("roomScene-userName")

        val likeNumIcon = Common.getImageView("img/roomScene-like.png", 20, 20)
        val likeNum = new Label(s"${albumList(i - 1).like}", likeNumIcon)
        likeNum.setPrefWidth(80)
        likeNum.getStyleClass.add("roomScene-userName")

        val picBar = new HBox(userName, audienceNum, likeNum)
        picBar.setMaxSize(roomPic.getFitWidth, roomPic.getFitHeight * 0.15)
        picBar.setPadding(new Insets(3,0,3,0))
        picBar.setAlignment(Pos.CENTER_LEFT)
        picBar.getStyleClass.add("roomScene-picBar")

        val picPane = new StackPane()
        picPane.setAlignment(Pos.BOTTOM_CENTER)
        picPane.getChildren.addAll(roomPic)

        // roomName
        val roomName = new Label(s"${albumList(i - 1).roomName}")
        roomName.setPrefWidth(200)
        roomName.getStyleClass.add("roomScene-roomName")

        // timeBox(startTime & duration)
        val timeIcon = getImageView("img/date.png", 20, 20)
        val liveTime = if (albumList(i - 1).timestamp != 0L) new Label(TimeUtil.timeStamp2DetailDate(albumList(i - 1).timestamp), timeIcon) else new Label("")
        liveTime.setPrefWidth(160)
        liveTime.getStyleClass.add("roomScene-time")

        val durationIcon = getImageView("img/clock.png", 20, 20)
        val duration = if(albumList(i - 1).timestamp != 0L) new Label(s"${albumList(i - 1).duration}", durationIcon) else new Label("")
        duration.setPrefWidth(100)
        duration.getStyleClass.add("roomScene-time")

        val timeBox = new HBox(liveTime, duration)
        timeBox.setAlignment(Pos.CENTER_LEFT)

        //roomBox
        roomBox.getChildren.addAll(picPane, roomName, timeBox)
        roomBox.setStyle("-fx-cursor: hand;")
        val shadow = new DropShadow(10, Color.GRAY)
        roomBox.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
          picPane.getChildren.add(picBar)
          roomPic.setEffect(shadow)
        })
        roomBox.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
          picPane.getChildren.remove(picBar)
          roomPic.setEffect(null)
        })
        hBox1.getChildren.add(roomBox)
      }
    }
    for (i <- (start + 3) to end) {

      if (i <= totalLen) {
//        println(s"i${i}, sum: ${totalLen}")
        val roomBox = new VBox(3)
        // stackPane: roomPic & picBar(userName & viewNum & likeNum)
        val roomPic = Pictures.getPic(albumList(i - 1).coverImgUrl, isHeader = false)
        roomPic.setFitHeight(Constants.DefaultPlayer.height / 2.5)
        roomPic.setFitWidth(Constants.DefaultPlayer.width / 2.5)
        roomPic.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
          listener.enter(albumList(i - 1).roomId, albumList(i - 1).timestamp)
        })

        val userName = new Label(s"${albumList(i - 1).userName}")
        userName.setPrefWidth(120)
        userName.getStyleClass.add("roomScene-userName")

        val audienceNumIcon = Common.getImageView("img/roomScene-view.png", 25, 25)
        val audienceNum = new Label(s"${albumList(i - 1).observerNum}", audienceNumIcon)
        audienceNum.setPrefWidth(80)
        audienceNum.getStyleClass.add("roomScene-userName")

        val likeNumIcon = Common.getImageView("img/roomScene-like.png", 20, 20)
        val likeNum = new Label(s"${albumList(i - 1).like}", likeNumIcon)
        likeNum.setPrefWidth(80)
        likeNum.getStyleClass.add("roomScene-userName")

        val picBar = new HBox(userName, audienceNum, likeNum)
        picBar.setMaxSize(roomPic.getFitWidth, roomPic.getFitHeight * 0.2)
        picBar.setPadding(new Insets(3,0,3,0))
        picBar.setAlignment(Pos.CENTER_LEFT)
        picBar.getStyleClass.add("roomScene-picBar")

        val picPane = new StackPane()
        picPane.setAlignment(Pos.BOTTOM_CENTER)
        picPane.getChildren.addAll(roomPic)

        // roomName
        val roomName = new Label(s"${albumList(i - 1).roomName}")
        roomName.setPrefWidth(200)
        roomName.getStyleClass.add("roomScene-roomName")

        // timeBox(startTime & duration)
        val timeIcon = getImageView("img/date.png", 20, 20)
        val liveTime = if (albumList(i - 1).timestamp != 0L) new Label(TimeUtil.timeStamp2DetailDate(albumList(i - 1).timestamp), timeIcon) else new Label("")
        liveTime.setPrefWidth(160)
        liveTime.getStyleClass.add("roomScene-time")

        val durationIcon = getImageView("img/clock.png", 20, 20)
        val duration = new Label(s"${albumList(i - 1).duration}", durationIcon)
        duration.setPrefWidth(100)
        duration.getStyleClass.add("roomScene-time")

        val timeBox = new HBox(liveTime, duration)
        timeBox.setAlignment(Pos.CENTER_LEFT)

        //roomBox
        roomBox.getChildren.addAll(picPane, roomName, timeBox)
        roomBox.setStyle("-fx-cursor: hand;")
        val shadow = new DropShadow(10, Color.GRAY)
        roomBox.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
          picPane.getChildren.add(picBar)
          roomPic.setEffect(shadow)
        })
        roomBox.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
          picPane.getChildren.remove(picBar)
          roomPic.setEffect(null)
        })
        hBox2.getChildren.add(roomBox)
      }
    }
    vBox.getChildren.addAll(hBox1, hBox2)
    vBox
  }

  val loading = new Label("房间加载中……")
  loading.setFont(Font.font("Verdana", 30))
  loading.setPadding(new Insets(200, 0, 0, 0))
  val borderPane = new BorderPane()
  borderPane.setTop(topBox)
  borderPane.setCenter(loading)
  group.getChildren.addAll(borderPane)

  /**
    * update roomList  func
    *
    **/
  def updateRoomList(roomList: List[RoomInfo] = Nil): Unit = {
    //    log.debug(s"update room list: r$roomList")
    if (roomList.isEmpty) {
      val label = new Label("暂无房间")
      label.setFont(Font.font("Verdana", 30))
      label.setPadding(new Insets(200, 0, 0, 0))
      borderPane.setCenter(label)
    } else {
      val itemsPerPage = 6
      val pageNum = if (roomList.length % itemsPerPage.toInt == 0) {
        roomList.length / itemsPerPage.toInt
      }
      else {
        roomList.length / itemsPerPage.toInt + 1
      }
      val pagination = new Pagination(pageNum, 0)
      pagination.setPageFactory((pageIndex: Integer) => {
        if (pageIndex >= pageNum)
          null
        else {
          createOnePage(pageIndex, itemsPerPage, roomList.map(_.toAlbum))
        }
      })
      val center = new VBox(10)
      liveInfo.setText(s"当前共有${roomList.length}个直播")
      center.getChildren.addAll(liveBox, pagination)
      borderPane.setCenter(center)
    }

  }

  /*record*/

  def getCurRecordList: List[AlbumInfo] = recordList.sortBy(_._1).flatMap(_._2.map(_.toAlbum))

  def getExistPageNum: Int = {
    if (getCurRecordList.size % recordsPerPage == 0) getCurRecordList.size / recordsPerPage else getCurRecordList.size / recordsPerPage + 1
  }

  def updateNextPage(page: Int, pagination: Pagination): Unit = {
    RMClient.getRecordList(recordSort, page, recordsPerPage).map {
      case Right(rst) =>
        if (rst.errCode == 0) {
          Boot.addToPlatform {
            recordList = (page, rst.recordInfo) :: recordList
            recordsSize = rst.recordNum
            if (pendingPage != -1) {
              if (pendingPage <= getExistPageNum - 1 && recordList.exists(_._1 == pendingPage + 1)) {
                pagination.setCurrentPageIndex(pendingPage)
              }
            }
          }
        } else {
          Boot.addToPlatform(
            WarningDialog.initWarningDialog(s"${rst.msg}")
          )
        }
      case Left(e) =>
        log.error(s"get record list error: $e")
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("获取录像列表失败")
        }
    }
  }


  def updateRecordList(): Unit = {
    if (recordList.isEmpty) {
      val label = new Label("暂无录像")
      label.setFont(Font.font("Verdana", 30))
      label.setPadding(new Insets(200, 0, 0, 0))
      borderPane.setCenter(label)
    } else {
      val pageNum = if (recordsSize % recordsPerPage == 0) recordsSize / recordsPerPage else recordsSize / recordsPerPage + 1
      val pagination = new Pagination(pageNum, 0)
      pagination.setMaxPageIndicatorCount(maxPagiNum)
      pagination.setPageFactory((pageIndex: Integer) => {
        if (pageIndex >= pageNum) {
          null
        }
        else {
          this.recordPageIndex = pageIndex + 1
          if (pageIndex > 0) {
            val tmp = ((pageIndex + maxPagiNum) / maxPagiNum) * maxPagiNum
            if (pageNum - pageIndex > maxPagiNum && recordList.flatMap(_._2).size < tmp * recordsPerPage + 1) {
              for (i <- tmp + 1 to tmp + 10) {
                updateNextPage(i, pagination)
              }
            }
          }
          if (pageIndex <= getExistPageNum - 1 && recordList.exists(_._1 == pageIndex + 1)) {
            if (pendingPage != -1) {
              this.group.getChildren.remove(this.waitingGif)
              pendingPage = -1
            }
            createOnePage(pageIndex, recordsPerPage, getCurRecordList)
          } else {
            pendingPage = pageIndex
            this.group.getChildren.add(this.waitingGif)
            null
          }
//          createOnePage(pageIndex, recordsPerPage, curList)
        }
      })
//      val center = new VBox(10)
      recordInfo.setText(s"当前共有${recordsSize}个录像")
//      center.getChildren.addAll(recordBox, pagination)
      if (recCenter.getChildren.size() > 1) {
        recCenter.getChildren.remove(1)
      }
      recCenter.getChildren.add(pagination)
      borderPane.setCenter(recCenter)

    }
  }

  var listener: RoomSceneListener = _

  def setListener(listener: RoomSceneListener): Unit = {
    this.listener = listener
  }

}
