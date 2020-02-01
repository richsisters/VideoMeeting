package org.seekloud.VideoMeeting.pcClient.component

import javafx.animation.ParallelTransition
import javafx.scene.layout.{Background, BackgroundFill, BorderPane, HBox, Pane, Priority, StackPane}
import javafx.geometry.{Insets,Pos}
import javafx.scene.control.{Label, Slider}
import javafx.scene.input.MouseEvent
import javafx.util.Duration
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.paint.Color
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.media.MediaPlayer.Status
import javafx.scene.media.{Media, MediaPlayer, MediaView}
import javafx.util
import org.seekloud.VideoMeeting.pcClient.common.Constants
import org.seekloud.VideoMeeting.pcClient.common.Constants.WindowStatus

/**
  * User: Jason
  * Date: 2019/9/24
  * Time: 11:45
  */
class PlayerPane(media: Media, liveBar: LiveBar) {
  private val mediaPlayer: MediaPlayer = if (media == null) null else new MediaPlayer(media)
  private val mediaView: MediaView = if (media == null) null else new MediaView(mediaPlayer)
  private var repeat = false
  private var stopRequested = false
  private var atEndOfMedia = false
  private var duration = media.getDuration
  //  private var timeSlider: Slider = _
  //  private var playTime: Label = _
  //  private var volumeSlider: Slider = _
  //  var mediaTopBar: HBox = _
  //  var mediaBottomBar: HBox = _
  private var transition: ParallelTransition = _
  //  private val all: Pane = new Pane()

  val mediaTopBar = new HBox()
  mediaTopBar.setPrefSize(600, 200)
  //  mediaTopBar.setPadding(new Insets(5, 10, 5, 10))
  mediaTopBar.setAlignment(Pos.BOTTOM_RIGHT)

  //  BorderPane.setAlignment(mediaTopBar, Pos.CENTER)

  val mediaBottomBar = new HBox()
  mediaBottomBar.setPadding(new Insets(5, 10, 5, 10))
  mediaBottomBar.setAlignment(Pos.CENTER)
  //  BorderPane.setAlignment(mediaBottomBar, Pos.CENTER)


  //  mediaView.addEventHandler(MouseEvent.MOUSE_ENTERED, (_: MouseEvent) => {
  //    println("i'm in")
  //    if (transition != null) transition.stop()
  //    val topFade = new FadeTransition(Duration.millis(200))
  //    topFade.setNode(mediaTopBar)
  //    topFade.setToValue(1)
  //    topFade.setInterpolator(Interpolator.EASE_OUT)
  //    val bottomFade = new FadeTransition(Duration.millis(200))
  //    bottomFade.setNode(mediaTopBar)
  //    bottomFade.setToValue(1)
  //    bottomFade.setInterpolator(Interpolator.EASE_OUT)
  //    transition.getChildren.addAll(topFade, bottomFade)
  //    transition.play()
  //  })
  //
  //  mediaView.addEventHandler(MouseEvent.MOUSE_EXITED, (_: MouseEvent) => {
  //    println("i'm out")
  //    if (transition != null) transition.stop()
  //    val topFade = new FadeTransition(Duration.millis(800))
  //    topFade.setNode(mediaTopBar)
  //    topFade.setToValue(0)
  //    topFade.setInterpolator(Interpolator.EASE_OUT)
  //    val bottomFade = new FadeTransition(Duration.millis(800))
  //    bottomFade.setNode(mediaTopBar)
  //    bottomFade.setToValue(0)
  //    bottomFade.setInterpolator(Interpolator.EASE_OUT)
  //    transition.getChildren.removeAll(topFade, bottomFade)
  //    transition.play()
  //  })

  private val changeListener = new ChangeListener[Duration]() {
    override def changed(observable: ObservableValue[_ <: Duration], oldValue: Duration, newValue: Duration): Unit = {
      val currentTime = mediaPlayer.getCurrentTime
      if (newValue.toSeconds == media.getDuration.toSeconds || newValue.toSeconds == 0)  {
        //        println("reset")
        liveBar.playToggleButton.setSelected(true)
      }
      timeSlider.setValue(currentTime.toSeconds / media.getDuration.toSeconds * 100)
      liveBar.playTimeText.setText(formattedTime(currentTime, duration))
      //      updateValues()
    }
  }
  def addTimeListener() : Unit = {
    mediaPlayer.currentTimeProperty.addListener(changeListener)
  }

  def removeTimeListener() : Unit = {
    mediaPlayer.currentTimeProperty.addListener(changeListener)
  }

  addTimeListener()

  liveBar.playToggleButton.setOnAction { _ =>
    //    println(millis2HHMMSS(mediaPlayer.getCurrentTime.toMillis))
    if (!liveBar.playToggleButton.isSelected) {
      pauseRecord()
    }
    else {
      if (mediaPlayer.getCurrentTime.toSeconds < media.getDuration.toSeconds) continueRecord()
      else {
        playRecord()
      }
    }
  }

  def playRecord(): Unit = {
    timeSlider.setValue(0)
    mediaPlayer.setStartTime(new util.Duration(0))
    mediaPlayer.setVolume(40)
    mediaPlayer.play()
  }

  def pauseRecord(): Unit = {
    mediaPlayer.pause()
  }

  def stopRecord(): Unit = {
    mediaPlayer.stop()
  }

  def continueRecord(): Unit = {
    val status = mediaPlayer.getStatus
    if ((status eq Status.UNKNOWN) || (status eq Status.HALTED)) return
    if ((status eq Status.PAUSED) || (status eq Status.STOPPED) || (status eq Status.READY)) mediaPlayer.play()
  }



  mediaPlayer.setOnPlaying(() => {
    if (stopRequested) {
      mediaPlayer.pause()
      stopRequested = false
    }
  })
  mediaPlayer.setOnReady(() => {
    duration = mediaPlayer.getMedia.getDuration
    updateValues()
  })
  mediaPlayer.setOnEndOfMedia(() => {
    if (!repeat) {
      stopRequested = true
      atEndOfMedia = true
    }
  })

  mediaPlayer.setCycleCount(if (repeat) MediaPlayer.INDEFINITE else 1)

  // Time label
  val timeLabel = new Label("Time")
  timeLabel.setTextFill(Color.BLACK)
  mediaTopBar.getChildren.add(timeLabel)


  // Time slider
  val timePane = new StackPane()
  timePane.setAlignment(Pos.CENTER_LEFT)
  private var timeFlag = true
  val timeSlider = new Slider()
  timeSlider.setId("media-slider")
  timeSlider.getStyleClass.add("playPane-slider")
  timeSlider.setMinWidth(240)
  timeSlider.setMaxWidth(Double.MaxValue)
  timeSlider.setMax(100)
  timeSlider.setMin(0)
  timeSlider.addEventFilter(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    timeFlag = false
  })
  timeSlider.addEventFilter(MouseEvent.MOUSE_RELEASED, (_: MouseEvent) => {
    timeFlag = true
  })
  timeSlider.valueProperty.addListener(new ChangeListener[Number]() {
    override def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number): Unit = {
      if (timeSlider.isValueChanging) {
        val currentTime = newValue.doubleValue()
        if (!timeFlag) {
          timeSlider.setValue(currentTime)
        }
        timeTrack1.setPrefWidth(timeSlider.getValue / timeSlider.getMax * timeSlider.getWidth)
        mediaPlayer.seek(duration.multiply(timeSlider.getValue / timeSlider.getMax))
        //        updateValues()
      }
      else if(newValue.doubleValue() - oldValue.doubleValue() > 100 / media.getDuration.toSeconds || oldValue.doubleValue() - newValue.doubleValue() > 100 / media.getDuration.toSeconds) {
        val currentTime = newValue.doubleValue()
        timeSlider.setValue(currentTime)
        timeTrack1.setPrefWidth(currentTime / timeSlider.getMax * timeSlider.getWidth)
        mediaPlayer.seek(duration.multiply(currentTime / timeSlider.getMax))
      }
      else {
        val currentTime = newValue.doubleValue()
        timeTrack1.setPrefWidth(currentTime / timeSlider.getMax * timeSlider.getWidth)
      }
    }
  })

  //Time Track
  val timeTrack1 = new Label()
  timeTrack1.setBackground(new Background(new BackgroundFill(Color.LIGHTSKYBLUE, null, null)))
  timeTrack1.setPrefHeight(8)
  timeTrack1.setMaxHeight(8)
  timeTrack1.setScaleY(0.5)
  timeTrack1.setLayoutY(2)
  timeTrack1.setPrefWidth(0)
  val timeTrack2 = new Label()
  timeTrack2.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)))
  timeTrack2.setPrefHeight(8)
  timeTrack2.setMaxHeight(8)
  timeTrack2.setScaleY(0.5)
  timeTrack2.setLayoutY(2)
  timeTrack2.setPrefWidth(640)

  timePane.getChildren.addAll(timeTrack2, timeTrack1, timeSlider)



  HBox.setHgrow(timeSlider, Priority.ALWAYS)
  //  mediaTopBar.getChildren.add(timeSlider)
  if (liveBar.windowStatus == WindowStatus.AUDIENCE_REC) {
    liveBar.barVBox.getChildren.clear()
    liveBar.barVBox.getChildren.add(timePane)
    liveBar.addNode4Bar()
  }

  // Play label
  val playTime = new Label()
  playTime.setPrefWidth(130)
  playTime.setMinWidth(50)
  playTime.setTextFill(Color.BLACK)
  mediaTopBar.getChildren.add(playTime)

  // Volume label
  val volumeLabel = new Label("Vol")
  volumeLabel.setTextFill(Color.BLACK)
  //  mediaTopBar.getChildren.add(volumeLabel)


  // Volume slider
  val volumePane = new StackPane()
  volumePane.setAlignment(Pos.CENTER_LEFT)
  val volumeSlider = new Slider()
  volumeSlider.setId("volume-slider")
  volumeSlider.getStyleClass.add("playPane-slider")
  volumeSlider.setMax(100)
  volumeSlider.setMin(0)
  volumeSlider.setValue(40)
  //  volumeSlider.setShowTickLabels(true)
  volumeSlider.setMajorTickUnit(50)
  volumeSlider.setMinorTickCount(1)
  volumeSlider.setBlockIncrement(1)
  volumeSlider.setMaxWidth(100)
  volumeSlider.setMinWidth(100)
  volumeSlider.setPrefWidth(100)
  //  volumeSlider.setOrientation(Orientation.VERTICAL)
  //  volumeSlider.setMaxSize(10, 10)
  //  volumeSlider.setMinSize(10, 10)
  //  volumeSlider.setPrefSize(10, 10)
  //  volumeSlider.setOnMouseEntered { _ =>
  //    volumeSlider.setMax(100)
  //    volumeSlider.setMin(0)
  //    volumeSlider.setValue(40)
  //    volumeSlider.setMinSize(10, 100)
  //    volumeSlider.setShowTickLabels(true)
  //    volumeSlider.setShowTickMarks(true)
  //    volumeSlider.setMajorTickUnit(50)
  //    volumeSlider.setMinorTickCount(1)
  //    volumeSlider.setBlockIncrement(1)
  //  }
  //
  //  volumeSlider.setOnMouseExited { _ =>
  //    volumeSlider.setMaxSize(10, 10)
  //    volumeSlider.setMinSize(10, 10)
  //    volumeSlider.setShowTickLabels(false)
  //    volumeSlider.setPrefSize(10, 10)
  //  }
  //  volumeSlider.valueProperty.addListener(new InvalidationListener() {
  //    def invalidated(ov: Observable): Unit = {
  //    }
  //  })

  val volumeTrack1 = new Label()
  volumeTrack1.setBackground(new Background(new BackgroundFill(Color.LIGHTSKYBLUE, null, null)))
  volumeTrack1.setPrefHeight(8)
  volumeTrack1.setMaxHeight(8)
  volumeTrack1.setScaleY(0.5)
  volumeTrack1.setLayoutY(2)
  volumeTrack1.setPrefWidth(40 * 0.9)
  val volumeTrack2 = new Label()
  volumeTrack2.setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)))
  volumeTrack2.setPrefHeight(8)
  volumeTrack2.setMaxHeight(8)
  volumeTrack2.setScaleY(0.5)
  volumeTrack2.setLayoutY(2)
  volumeTrack2.setPrefWidth(100)

  volumeSlider.valueProperty.addListener(new ChangeListener[Number]() {
    override def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number): Unit = {
      //      println("new: " + newValue.intValue())
      //      if (volumeSlider.isValueChanging) {
      mediaPlayer.setVolume(volumeSlider.getValue / 100.0)
      volumeTrack1.setPrefWidth(volumeSlider.getWidth * volumeSlider.getValue / 100 * 0.9)
      //      }
    }
  })



  volumePane.getChildren.addAll(volumeTrack2, volumeTrack1 , volumeSlider)

  //  mediaTopBar.getChildren.add(volumeSlider)
  if (liveBar.windowStatus == WindowStatus.AUDIENCE_REC) {
    liveBar.add4Rec(volumeLabel, volumePane)
  }



  val backAction: EventHandler[ActionEvent] = (e: ActionEvent) => {
    mediaPlayer.seek(Duration.ZERO)
  }
  val stopAction: EventHandler[ActionEvent] = (e: ActionEvent) => {
    mediaPlayer.stop()
  }
  val playAction: EventHandler[ActionEvent] = (e: ActionEvent) => {
    mediaPlayer.play()
  }
  val pauseAction: EventHandler[ActionEvent] = (e: ActionEvent) => {
    mediaPlayer.pause()
  }
  def forwardAction(): Unit = {
    val currentTime = mediaPlayer.getCurrentTime
    //Todo 小于视频全长5秒
    //        if (currentTime.toSeconds < mp.getTotalDuration.toSeconds)
    timeSlider.setValue(currentTime.toSeconds + 5.0)
    timeTrack1.setPrefWidth(timeSlider.getValue / timeSlider.getMax * timeSlider.getWidth)
    mediaPlayer.seek(Duration.seconds(currentTime.toSeconds + 5.0))
  }

  def backwardAction(): Unit = {
    val currentTime = mediaPlayer.getCurrentTime
    timeSlider.setValue(currentTime.toSeconds + 5.0)
    timeTrack1.setPrefWidth(timeSlider.getValue / timeSlider.getMax * timeSlider.getWidth)
    if (currentTime.toSeconds > 5.0) mediaPlayer.seek(Duration.seconds(currentTime.toSeconds - 5.0))
  }

  mediaBottomBar.setId("bottom")
  mediaBottomBar.setSpacing(0)
  mediaBottomBar.setAlignment(Pos.CENTER)
  mediaBottomBar.getChildren.addAll()
  liveBar.forwardButton.addEventFilter(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    forwardAction()
  })
  liveBar.backwardButton.addEventFilter(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => {
    backwardAction()
  })

  private val all: BorderPane = new BorderPane(mediaView)

  all.setMaxSize(Constants.DefaultPlayer.width,Constants.DefaultPlayer.height)
  all.setMinSize(Constants.DefaultPlayer.width,Constants.DefaultPlayer.height)
  //  all.setBackground(new Background(new BackgroundFill(Color.RED,null,null)))

  def getMediaPlayer: MediaPlayer = this.mediaPlayer

  def getMediaView: MediaView = this.mediaView

  def getAllView: Pane = all



  protected def updateValues(): Unit = {
    val currentTime = mediaPlayer.getCurrentTime
    timeSlider.setValue(currentTime.toSeconds / media.getDuration.toSeconds * 100)
    //      playTime.setText(formatTime(currentTime, duration))
    timeSlider.setDisable(duration.isUnknown)
    if (!timeSlider.isDisabled && duration.greaterThan(Duration.ZERO) && !timeSlider.isValueChanging) timeSlider.setValue(currentTime.divide(duration).toMillis * 100.0)
    //      if (!volumeSlider.isValueChanging) volumeSlider.setValue(Math.round(mediaPlayer.getVolume * 100).asInstanceOf[Int])
  }

  private def formatTime(elapsed: Duration, duration: Duration): String = {
    var intElapsed = Math.floor(elapsed.toSeconds).toInt
    val elapsedHours = intElapsed / (60 * 60)
    if (elapsedHours > 0) intElapsed -= elapsedHours * 60 * 60
    val elapsedMinutes = intElapsed / 60
    val elapsedSeconds = intElapsed - elapsedHours * 60 * 60 - elapsedMinutes * 60
    if (duration.greaterThan(Duration.ZERO)) {
      var intDuration = Math.floor(duration.toSeconds).toInt
      val durationHours = intDuration / (60 * 60)
      if (durationHours > 0) intDuration -= durationHours * 60 * 60
      val durationMinutes = intDuration / 60
      val durationSeconds = intDuration - durationHours * 60 * 60 - durationMinutes * 60
      if (durationHours > 0) (elapsedHours, elapsedMinutes, elapsedSeconds).formatted("%d:%d:%d")
      else (elapsedMinutes, elapsedSeconds).formatted("%d:%d")
    }
    else if (elapsedHours > 0) (elapsedHours, elapsedMinutes, elapsedSeconds).formatted("%d:%d:%d")
    else (elapsedMinutes, elapsedSeconds).formatted("%d:%d")
  }

  private def formattedTime(elapsed: Duration, duration: Duration): String = {
    val playedSec = elapsed.toMillis
    val allSec = duration.toMillis
    s"${millis2HHMMSS(playedSec)} / ${millis2HHMMSS(allSec)}"
  }

  def millis2HHMMSS(sec: Double): String = {
    val hours = (sec / 3600000).toInt
    val h =  if (hours >= 10) hours.toString else "0" + hours
    val minutes = ((sec % 3600000) / 60000).toInt
    val m = if (minutes >= 10) minutes.toString else "0" + minutes
    val seconds = ((sec % 60000) / 1000).toInt
    val s = if (seconds >= 10) seconds.toString else "0" + seconds

    if (hours > 0)s"$h:$m:$s" else s"$m:$s"
  }


}

