package org.seekloud.VideoMeeting.pcClient.common

import javafx.scene.Scene
import javafx.stage.Stage

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 11:26
  */


class StageContext(stage: Stage) {

  def getStage: Stage = stage

  def getStageWidth: Double = stage.getWidth

  def getStageHeight: Double = stage.getHeight

  def isFullScreen: Boolean = stage.isFullScreen


  def switchScene(scene: Scene, title: String = "VideoMeeting", resize: Boolean = false, fullScreen: Boolean = false, isSetOffX: Boolean = false): Unit = {
    //    stage.centerOnScreen()
    stage.setScene(scene)
    stage.sizeToScene()
    stage.setResizable(resize)
    stage.setTitle(title)
    stage.setFullScreen(fullScreen)
    if (isSetOffX) {
      stage.setX(0)
      stage.setY(0)
    }
    stage.show()
  }


}
