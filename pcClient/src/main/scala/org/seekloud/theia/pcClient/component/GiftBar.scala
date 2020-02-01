package org.seekloud.VideoMeeting.pcClient.component


import javafx.geometry.{Insets, Pos}
import javafx.scene.Group
import javafx.scene.control.{Button, Label, TextArea, TextField}
import javafx.scene.effect.DropShadow
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.seekloud.VideoMeeting.pcClient.common.Constants
import org.seekloud.VideoMeeting.pcClient.scene.AudienceScene.AudienceSceneListener
import org.slf4j.{Logger, LoggerFactory}


/**
  * Author: zwq
  * Date: 2019/9/10
  * Time: 21:24
  */
class GiftBar(group: Group) {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  val width: Double = Constants.AppWindow.width * 0.9
  val height: Double = Constants.AppWindow.height * 0.75

  var gift1Clicked = false
  var gift2Clicked = false
  var gift3Clicked = false
  var gift4Clicked = false
  var gift5Clicked = false
  var gift6Clicked = false

  def createGiftBox(pic: String, name: String): VBox = {
    val giftIcon = new ImageView(pic)
    giftIcon.setFitHeight(32)
    giftIcon.setFitWidth(32)
    val giftName = new Text(name)
    val giftBox = new VBox()
    giftBox.getChildren.addAll(giftIcon, giftName)
    giftBox.setSpacing(3)
    giftBox.setAlignment(Pos.CENTER)
    giftBox.setPadding(new Insets(3, 4, 3, 4))
    giftBox.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    addBoxEffect(giftBox)
    giftBox
  }

  def addBoxEffect(box: VBox): Unit = {
    val shadow = new DropShadow(10, Color.GRAY)
    box.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
      box.setEffect(shadow)
    })
    box.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
      box.setEffect(null)
    })


  }

  def getDesBox(pic: String, name: String, price: Int, des: String, input: TextField, btn: Button): VBox = {
    val giftIcon = new ImageView(pic)
    giftIcon.setFitHeight(80)
    giftIcon.setFitWidth(80)
    giftIcon.setStyle("-fx-background-color: #333f50")
    val nameLabel = new Label(name)
    val priceLabel = new Label(price.toString, new ImageView("img/coin.png"))
    val desText = new Text(des)
    desText.setWrappingWidth(120)
    val numLabel = new Label("数量:")
    input.setPrefWidth(45)
    val sendBtn = btn
    sendBtn.setStyle("-fx-cursor: hand;")

    /*layout*/
    val nameBox = new HBox()
    nameBox.getChildren.addAll(nameLabel, priceLabel)
    nameBox.setSpacing(10)
    nameBox.setAlignment(Pos.CENTER_LEFT)

    val infoBox = new VBox()
    infoBox.getChildren.addAll(nameBox, desText)
    infoBox.setSpacing(10)

    val upBox = new HBox()
    upBox.getChildren.addAll(giftIcon, infoBox)
    upBox.setSpacing(10)
    upBox.setAlignment(Pos.CENTER)

    val downBox = new HBox()
    downBox.getChildren.addAll(numLabel, input, sendBtn)
    downBox.setSpacing(8)
    downBox.setAlignment(Pos.CENTER_RIGHT)

    val box = new VBox()
    box.getChildren.addAll(upBox, downBox)
    box.setSpacing(10)
    box.setPadding(new Insets(10, 10, 10, 10))

    box.getStyleClass.add("audienceScene-gift-giftDes")
    box.setLayoutX(width * 0.67)
    box

  }

  def setClickedStyle(): Unit = {
    if (gift1Clicked) {
      gift1.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#dee7f4;" +
                     "-fx-cursor: hand;")
    } else {
      gift1.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    }
    if (gift2Clicked) {
      gift2.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#dee7f4;" +
                     "-fx-cursor: hand;")
    } else {
      gift2.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    }
    if (gift3Clicked) {
      gift3.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#dee7f4;" +
                     "-fx-cursor: hand;")
    } else {
      gift3.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    }
    if (gift4Clicked) {
      gift4.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#dee7f4;" +
                     "-fx-cursor: hand;")
    } else {
      gift4.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    }
    if (gift5Clicked) {
      gift5.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#dee7f4;" +
                     "-fx-cursor: hand;")
    } else {
      gift5.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    }
    if (gift6Clicked) {
      gift6.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#dee7f4;" +
                     "-fx-cursor: hand;")
    } else {
      gift6.setStyle("-fx-border-color: #dee7f4;" +
                     "-fx-background-color:#f2f5fb;" +
                     "-fx-cursor: hand;")
    }

  }

  val gift1: VBox = createGiftBox("img/gift1.png", "冰可乐")
  val gift2: VBox = createGiftBox("img/gift2.png", "雪糕")
  val gift3: VBox = createGiftBox("img/gift3.png", "巧克力")
  val gift4: VBox = createGiftBox("img/gift4.png", "花花")
  val gift5: VBox = createGiftBox("img/gift5.png", "飞船")
  val gift6: VBox = createGiftBox("img/gift6.png", "火箭")


  val sendBtn1 = new Button("发送")
  val input1 = new TextField("5")
  val gift1Des: VBox = getDesBox("img/gift1.png", "冰可乐", 3, "大哥，霍冰阔落！", input1, sendBtn1)
  gift1Des.setLayoutY(50)
  gift1.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    if (!gift1Clicked) {
      group.getChildren.removeAll(gift1Des, gift2Des, gift3Des, gift4Des, gift5Des, gift6Des)
      gift1Clicked = false
      gift2Clicked = false
      gift3Clicked = false
      gift4Clicked = false
      gift5Clicked = false
      gift6Clicked = false
      group.getChildren.add(1, gift1Des)
      gift1Clicked = true
      setClickedStyle()
    } else {
      group.getChildren.remove(gift1Des)
      gift1Clicked = false
      setClickedStyle()
    }
  })


  val sendBtn2 = new Button("发送")
  val input2 = new TextField("5")
  val gift2Des: VBox = getDesBox("img/gift2.png", "雪糕", 5, "吃个雪糕吧！", input2, sendBtn2)
  gift2Des.setLayoutY(100)
  gift2.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    if (!gift2Clicked) {
      //      log.debug(s"add des")
      group.getChildren.removeAll(gift1Des, gift2Des, gift3Des, gift4Des, gift5Des, gift6Des)
      gift1Clicked = false
      gift2Clicked = false
      gift3Clicked = false
      gift4Clicked = false
      gift5Clicked = false
      gift6Clicked = false
      group.getChildren.add(1, gift2Des)
      gift2Clicked = true
      setClickedStyle()
    } else {
      //      log.debug(s"remove des")
      group.getChildren.remove(gift2Des)
      gift2Clicked = false
      setClickedStyle()
    }
  })


  val sendBtn3 = new Button("发送")
  val input3 = new TextField("5")
  val gift3Des: VBox = getDesBox("img/gift3.png", "巧克力", 10, "巧克力可没有你甜！", input3, sendBtn3)
  gift3Des.setLayoutY(150)
  gift3.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    if (!gift3Clicked) {
      //      log.debug(s"add des")
      group.getChildren.removeAll(gift1Des, gift2Des, gift3Des, gift4Des, gift5Des, gift6Des)
      gift1Clicked = false
      gift2Clicked = false
      gift3Clicked = false
      gift4Clicked = false
      gift5Clicked = false
      gift6Clicked = false
      group.getChildren.add(1, gift3Des)
      gift3Clicked = true
      setClickedStyle()
    } else {
      //      log.debug(s"remove des")
      group.getChildren.remove(gift3Des)
      gift3Clicked = false
      setClickedStyle()
    }
  })


  val sendBtn4 = new Button("发送")
  val input4 = new TextField("99")
  val gift4Des: VBox = getDesBox("img/gift4.png", "花花", 20, "为你献花！", input4, sendBtn4)
  gift4Des.setLayoutY(200)
  gift4.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    if (!gift4Clicked) {
      //      log.debug(s"add des")
      group.getChildren.removeAll(gift1Des, gift2Des, gift3Des, gift4Des, gift5Des, gift6Des)
      gift1Clicked = false
      gift2Clicked = false
      gift3Clicked = false
      gift4Clicked = false
      gift5Clicked = false
      gift6Clicked = false
      group.getChildren.add(1, gift4Des)
      gift4Clicked = true
      setClickedStyle()
    } else {
      //      log.debug(s"remove des")
      group.getChildren.remove(gift4Des)
      gift4Clicked = false
      setClickedStyle()
    }
  })


  val sendBtn5 = new Button("发送")
  val input5 = new TextField("1")
  val gift5Des: VBox = getDesBox("img/gift5.png", "飞船", 50, "都让开，飞船来咯！", input5, sendBtn5)
  gift5Des.setLayoutY(250)
  gift5.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    if (!gift5Clicked) {
      //      log.debug(s"add des")
      group.getChildren.removeAll(gift1Des, gift2Des, gift3Des, gift4Des, gift5Des, gift6Des)
      gift1Clicked = false
      gift2Clicked = false
      gift3Clicked = false
      gift4Clicked = false
      gift5Clicked = false
      gift6Clicked = false
      group.getChildren.add(1, gift5Des)
      gift5Clicked = true
      setClickedStyle()
    } else {
      //      log.debug(s"remove des")
      group.getChildren.remove(gift5Des)
      gift5Clicked = false
      setClickedStyle()
    }
  })


  val sendBtn6 = new Button("发送")
  val input6 = new TextField("1")
  val gift6Des: VBox = getDesBox("img/gift6.png", "火箭", 100, "都让开，火箭来咯！", input6, sendBtn6)
  gift6Des.setLayoutY(300)
  gift6.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    if (!gift6Clicked) {
      //      log.debug(s"add des")
      group.getChildren.removeAll(gift1Des, gift2Des, gift3Des, gift4Des, gift5Des, gift6Des)
      gift1Clicked = false
      gift2Clicked = false
      gift3Clicked = false
      gift4Clicked = false
      gift5Clicked = false
      gift6Clicked = false
      group.getChildren.add(1, gift6Des)
      gift6Clicked = true
      setClickedStyle()
    } else {
      //      log.debug(s"remove des")
      group.getChildren.remove(gift6Des)
      gift6Clicked = false
      setClickedStyle()
    }
  })


  //礼物栏
  val giftBox = new VBox()
  giftBox.getChildren.addAll(gift1, gift2, gift3, gift4, gift5, gift6)
  giftBox.setAlignment(Pos.CENTER)

}
