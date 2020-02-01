package org.seekloud.VideoMeeting.capture.core

import java.util.concurrent.LinkedBlockingDeque

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.bytedeco.javacv.OpenCVFrameConverter.ToMat
import org.bytedeco.javacv.{FFmpegFrameGrabber, Frame, OpenCVFrameConverter, OpenCVFrameGrabber}
import org.bytedeco.opencv.opencv_core.{Mat, Rect, Scalar, Size}
import org.seekloud.VideoMeeting.capture.protocol.Messages.LatestFrame
import org.slf4j.LoggerFactory
import org.bytedeco.opencv.global.{opencv_imgproc => OpenCVProc}
import org.bytedeco.opencv.global.{opencv_core => OpenCVCore}
import org.seekloud.VideoMeeting.capture.core.CaptureManager.MediaSettings

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


/**
  * Author: wqf
  * Date: 2019/10/14
  * Time: 14:57
  */
object MontageActor {

  private val log = LoggerFactory.getLogger(this.getClass)
  var debug: Boolean = true

  def debug(msg: String): Unit = {
    if (debug) log.debug(msg)
  }

  sealed trait Command

  case class CameraImage(frame: Frame) extends Command

  case class DesktopImage(frame: Frame) extends Command

  case object ShowCamera extends Command

  case object ShowDesktop extends Command

  case object ShowBoth extends Command

  final case object Stop extends Command

  val converter = new OpenCVFrameConverter.ToIplImage()


  def create(imageQueue: LinkedBlockingDeque[LatestFrame], mediaSettings: MediaSettings): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      log.info(s"ImageCapture is staring...")
      val dSize = new Size(mediaSettings.imageWidth, mediaSettings.imageHeight)
      val pSize = new Size(mediaSettings.imageWidth/4, mediaSettings.imageHeight/4)
      val cameraMask = new Mat(pSize, OpenCVCore.CV_8UC1, new Scalar(1d))
      val desktopMask = new Mat(dSize, OpenCVCore.CV_8UC1, new Scalar(1d))
      val toMat = new ToMat()
      val cameraOutMat = new Mat()
      val desktopOutMat = new Mat()
      working(imageQueue,
        new LinkedBlockingDeque[Frame](),
        new LinkedBlockingDeque[Frame](),
        dSize,
        pSize,
        cameraMask,
        desktopMask,
        cameraOutMat,
        desktopOutMat,
        toMat,
        0,
        0)
    }

  private def working(
    imageQueue: LinkedBlockingDeque[LatestFrame],
    cameraQueue: LinkedBlockingDeque[Frame],
    desktopQueue: LinkedBlockingDeque[Frame],
    dSize: Size,
    pSize: Size,
    cameraMask: Mat,
    desktopMask: Mat,
    cameraOutMat: Mat,
    desktopOutMat: Mat,
    toMat: ToMat,
    state: Int,  //0：摄像头；1：桌面；2：拼接
    frameNumber: Int
  ): Behavior[Command] =
    Behaviors.receive[Command]{(ctx, msg) =>
      msg match {
        case ShowDesktop =>
          cameraQueue.clear()
          working(imageQueue, cameraQueue, desktopQueue, dSize, pSize, cameraMask, desktopMask, cameraOutMat, desktopOutMat, toMat, 1, frameNumber)

        case ShowCamera =>
          desktopQueue.clear()
          working(imageQueue, cameraQueue, desktopQueue, dSize, pSize, cameraMask, desktopMask, cameraOutMat, desktopOutMat, toMat, 0, frameNumber)

        case ShowBoth =>
          working(imageQueue, cameraQueue, desktopQueue, dSize, pSize, cameraMask, desktopMask, cameraOutMat, desktopOutMat, toMat, 2, frameNumber)

        case CameraImage(frame: Frame) =>
          if(state != 1){
            cameraQueue.clear()
            cameraQueue.offer(frame)
            if(state == 0){
              imageQueue.clear()
              imageQueue.offer(LatestFrame(frame, System.currentTimeMillis()))
            }else if(state == 2){
              if(!desktopQueue.isEmpty){
                val desktopImage = desktopQueue.peek().clone()
                val canvas = new Mat(dSize, OpenCVCore.CV_8UC3, new Scalar(0, 0, 0, 0))
                val cameraRoi = canvas(new Rect(0, 0, pSize.width(), pSize.height()))
                val desktopRoi = canvas(new Rect(0, 0, dSize.width(), dSize.height()))
                val desktopMat = toMat.convert(desktopImage)
                desktopMat.copyTo(desktopRoi, desktopMask)
                val cameraMat = toMat.convert(frame)
                OpenCVProc.resize(cameraMat, cameraOutMat, pSize)
                cameraOutMat.copyTo(cameraRoi, cameraMask)
                val convertFrame = converter.convert(canvas)
                imageQueue.offer(LatestFrame(convertFrame, System.currentTimeMillis()))
              }
            }
            working(imageQueue, cameraQueue, desktopQueue, dSize, pSize, cameraMask, desktopMask, cameraOutMat, desktopOutMat, toMat, state, frameNumber + 1)
          }

          Behaviors.same

        case DesktopImage(frame: Frame) =>
          if(state != 0){
            desktopQueue.clear()
            imageQueue.clear()
            val mat = toMat.convert(frame)
            OpenCVProc.resize(mat, desktopOutMat, dSize)
            val convertFrame1 = converter.convert(desktopOutMat)
            desktopQueue.offer(convertFrame1)
            if(state == 1){
              imageQueue.offer(LatestFrame(convertFrame1, System.currentTimeMillis()))
            }else if(state == 2){
              if(!cameraQueue.isEmpty){
                val cameraImage = cameraQueue.peek().clone()
                val canvas = new Mat(dSize, OpenCVCore.CV_8UC3, new Scalar(0, 0, 0, 0))
                val cameraRoi = canvas(new Rect(0, 0, pSize.width(), pSize.height()))
                val desktopRoi = canvas(new Rect(0, 0, dSize.width(), dSize.height()))
                desktopOutMat.copyTo(desktopRoi, desktopMask)
                val cameraMat =toMat.convert(cameraImage)
                OpenCVProc.resize(cameraMat, cameraOutMat, pSize)
                cameraOutMat.copyTo(cameraRoi, cameraMask)
                val convertFrame = converter.convert(canvas)
                imageQueue.offer(LatestFrame(convertFrame, System.currentTimeMillis()))
              }
            }
            working(imageQueue, cameraQueue, desktopQueue, dSize, pSize, cameraMask, desktopMask, cameraOutMat, desktopOutMat, toMat, state, frameNumber + 1)
          }
          Behaviors.same

        case Stop =>
          Behaviors.stopped

        case x =>
          log.warn(s"unknown msg in working: $x")
          Behaviors.unhandled

      }
    }

}
