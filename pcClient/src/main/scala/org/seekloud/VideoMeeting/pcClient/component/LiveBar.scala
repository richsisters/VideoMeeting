package org.seekloud.VideoMeeting.pcClient.component

import javafx.animation.AnimationTimer
import javafx.geometry.{Insets, Pos}
import javafx.scene.Node
import javafx.scene.control.{Button, ToggleButton, Tooltip}
import javafx.scene.image.ImageView
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.scene.text.{Font, Text}
import org.seekloud.VideoMeeting.pcClient.common.Constants.WindowStatus

/**
  * Author: zwq
  * Date: 2019/9/19
  * Time: 10:38
  */
class LiveBar(val windowStatus: Int, width: Double, height: Double) {

  /**
    * live & record & play
    */

  protected var startLiveTime: Long = 0L  //开始会议的时间

  protected var animationTimerStart = false

  val liveTimeText = new Text("设备准备中")
  liveTimeText.setFont(Font.font(15))
  liveTimeText.setWrappingWidth(100)

  private val animationTimer = new AnimationTimer() {
    override def handle(now: Long): Unit = {
      writeLiveTime()
    }
  }


  def writeLiveTime(): Unit = {
    val liveTime = System.currentTimeMillis() - startLiveTime
    val hours = liveTime / 3600000
    val minutes = (liveTime % 3600000) / 60000
    val seconds = (liveTime % 60000) / 1000
    liveTimeText.setText(s"${hours.toInt}:${minutes.toInt}:${seconds.toInt}")
  }

  def startTimer(): Unit = {
    if(!animationTimerStart){
      animationTimer.start()
      animationTimerStart = true
    }
  }

  def resetStartLiveTime(startTime: Long): Unit = {
    imageToggleButton.setDisable(true)
    soundToggleButton.setDisable(true)
    startLiveTime = startTime
  }

  def endTimer(): Unit = {
    if(animationTimerStart){
      animationTimer.stop()
      animationTimerStart = false
    }
  }

  /**
    * needSound & needImage & fullScreen
    */

  val soundToggleButton = new ToggleButton("")
  soundToggleButton.getStyleClass.add("hostScene-rightArea-soundBtn")
  soundToggleButton.setSelected(true)
  soundToggleButton.setDisable(false)
  Tooltip.install(soundToggleButton, new Tooltip("点击关闭声音"))


  val imageToggleButton = new ToggleButton("")
  imageToggleButton.getStyleClass.add("hostScene-rightArea-imageBtn")
  imageToggleButton.setSelected(true)
  imageToggleButton.setDisable(false)
  Tooltip.install(imageToggleButton, new Tooltip("点击关闭画面"))


  /**
    * barBox
    */

  // liveTime
  val liveBox = new HBox(5, liveTimeText)
  liveBox.setAlignment(Pos.CENTER)

  // needSound & needImage
  val box2 = new HBox(5, soundToggleButton, imageToggleButton)
  box2.setPadding(new Insets(0,50,0,0))

  val barBox: HBox = new HBox(liveBox, box2)

  HBox.setHgrow(barBox, Priority.ALWAYS)

  barBox.setAlignment(Pos.CENTER)
  barBox.setPadding(new Insets(2,0,2,0))
  barBox.setPrefWidth(width)
  barBox.setMaxHeight(height)
  barBox.setStyle("-fx-background-color: #66808080")

  def addNode4Bar(): Unit = {
    barVBox.getChildren.addAll(barBox)
  }

  val barVBox = new VBox(-1,barBox)
  barVBox.setAlignment(Pos.BOTTOM_LEFT)
  VBox.setVgrow(barVBox, Priority.ALWAYS)
}
