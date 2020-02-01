package org.seekloud.VideoMeeting.capture

import java.io.File

import akka.actor.ActorSystem
import org.seekloud.VideoMeeting.capture.sdk.MediaCapture
import akka.actor.typed.scaladsl.adapter._
import akka.dispatch.MessageDispatcher
import javafx.application.Platform
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.stage.Stage
import org.seekloud.VideoMeeting.capture.demo.TestCaptureActor


/**
  * User: TangYaruo
  * Date: 2019/9/2
  * Time: 22:49
  */
object Boot {

  import org.seekloud.VideoMeeting.capture.common.AppSettings._
  implicit val system: ActorSystem = ActorSystem("test", config)
  implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")

  def addToPlatform(fun: => Unit): Unit = {
    Platform.runLater(() => fun)
  }


}

class Boot extends javafx.application.Application {

  import Boot._

  override def start(primaryStage: Stage): Unit = {

    val canvas = new Canvas(640, 360)
    val gc = canvas.getGraphicsContext2D

    val testActor = system.spawn(TestCaptureActor.create(gc), "testActor")
    val mediaCapture = MediaCapture(testActor)

    val outFile = new File("D:\\test_video\\testCapture1.ts")
//    mediaCapture.setOutputFile(outFile)
//    mediaCapture.needImage(true)
//    mediaCapture.needSound(true)
    mediaCapture.start()

    val group = new Group()
    group.getChildren.addAll(canvas)
    val scene = new Scene(group)
    primaryStage.setScene(scene)
    primaryStage.show()


  }

}
