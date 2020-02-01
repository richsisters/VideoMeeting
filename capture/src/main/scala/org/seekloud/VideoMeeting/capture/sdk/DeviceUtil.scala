package org.seekloud.VideoMeeting.capture.sdk

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.Charset
import java.util

import org.seekloud.VideoMeeting.capture.sdk.MediaCapture.executor
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.Future


/**
  * User: TangYaruo
  * Date: 2019/8/28
  * Time: 13:42
  *
  * Edited by: Jason
  * Time: 2019/9/6
  */
object DeviceUtil {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val cmd4inputDevices = "ffmpeg -list_devices true -f dshow -i dummy"

  private val deviceOptionMap: mutable.HashMap[Device, List[DeviceOption]] = mutable.HashMap.empty

  sealed trait DeviceOption

  case class VideoOption(`type`: String, typeName: String, s_min: String, fps_min: Int, s_max: String, fps_max: Int) extends DeviceOption

  case class AudioOption(ch_min: Int, bits_min: Int, rate_min: Int, ch_max: Int, bits_max: Int, rate_max: Int) extends DeviceOption

  case class Device(dType: String, dName: String)

  private def exeCmd(commandStr: String) = {
    var br: BufferedReader = null
    var br4Error: BufferedReader = null
    val sb: StringBuilder = new StringBuilder()
    try {
      //      val p: Process = Runtime.getRuntime.exec(commandStr)
      val cmd: util.ArrayList[String] = new util.ArrayList[String]()
      commandStr.split(" ").foreach(cmd.add)
      val pb: ProcessBuilder = new ProcessBuilder(cmd)
      //      pb.inheritIO().start().waitFor()
      val p = pb.start()
      br = new BufferedReader(new InputStreamReader(p.getInputStream, Charset.forName("GBK")))
      br4Error = new BufferedReader(new InputStreamReader(p.getErrorStream, Charset.forName("GBK")))
      var line: String = null
      val duFuture = Future(br.read()).map {
        rst =>
          if (rst != -1) {
            line = br.readLine()
            while (line != null) {
              sb.append(line + "\n")
              line = br.readLine()
            }
            br.close()
            Future(Right(sb.toString()))
          } else {
            log.warn(s"normal stream get null, try error stream.")
            br.close()
            Future(br4Error.read()).map {
              rst =>
                if (rst != -1) {
                  line = br4Error.readLine()
                  while (line != null) {
                    sb.append(line + "\n")
                    line = br4Error.readLine()
                  }
                } else {
                  log.warn(s"error info get null.")
                }
                br4Error.close()
                Right(sb.toString())

            }.recover {
              case ex: Exception =>
                log.warn(s"errorStream read error: $ex")
                br4Error.close()
                Left(ex)
            }

          }
      }.recover {
        case ex: Exception =>
          log.warn(s"inputStream read error: $ex, try errorStream")
          br.close()
          Future(br4Error.read()).map {
            rst =>
              if (rst != -1) {
                line = br4Error.readLine()
                while (line != null) {
                  sb.append(line + "\n")
                  line = br4Error.readLine()
                }
              } else {
                log.warn(s"error info get null.")
              }
              br4Error.close()
              Right(sb.toString())

          }.recover {
            case ex: Exception =>
              log.warn(s"errorStream read error: $ex")
              br4Error.close()
              Left(ex)
          }
      }
      duFuture.flatMap(f => f)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        Future(Left(e))
    }
    finally {
      if (br != null) {
        try {
          br.close()
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      }
    }
  }

  //  @scala.annotation.tailrec
  private def getAllDevices = {
    exeCmd(cmd4inputDevices).map {
      case Right(output) =>
        val reg = """\[dshow @(.*?)](.*?)\n""".r
        val rst = reg.findAllIn(output)
        var deviceName = ""
        var deviceMap: Map[String, String] = Map.empty
        rst.foreach {
          case reg(_, b) =>
            //              println(b)
            var a = b
            while (a.startsWith(" ")) a = a.drop(1)
            if (a.startsWith("Alternative")) a = a.drop("Alternative name ".length)
            if (a.contains("devices")) {
              deviceName = a
            }
            else {
              deviceMap += (a -> deviceName)
            }
          case x@_ =>
            log.info(s"match error, $x")
        }
        //        println(deviceMap)
        deviceMap

      case Left(e) =>
        log.info(s"get all devices failed! error: ${e.getMessage}")
        Map.empty
    }
  }


  private def getDeviceOption(dName: String, dType: String) = {
    val `type` = if (dType.contains("video")) "video" else "audio"
    val useADevice = s"""ffmpeg -f dshow -list_options true -i ${`type`}=$dName"""
    exeCmd(useADevice).map {
      case Right(output) =>
        val reg = """\[dshow @(.*?)](.*?)\n""".r
        val pixelReg = """pixel_format=(.*?) {2}min s=(.*?) fps=([.0-9]+) max s=(.*?) fps=([.0-9]+)""".r
        val vcodecReg = """vcodec=(.*?) {2}min s=(.*?) fps=([.0-9]+) max s=(.*?) fps=([.0-9]+)""".r
        val audioReg = """min ch=([.0-9]+) bits=([.0-9]+) rate= ([.0-9]+) max ch=([.0-9]+) bits=([.0-9]+) rate= ([.0-9]+)""".r
        val rst = reg.findAllIn(output)
        var optionList: List[DeviceOption] = List.empty
        rst.foreach {
          case reg(_, b) =>
            var a = b
            while (a.startsWith(" ")) a = a.drop(1)
            a match {
              case pixelReg(format, s_min, fps_min, s_max, fps_max) =>
                optionList = optionList :+ VideoOption("pixel_format", format, s_min, fps_min.toInt, s_max, fps_max.toInt)

              case vcodecReg(vcodec, s_min, fps_min, s_max, fps_max) =>
                optionList = optionList :+ VideoOption("vcodec", vcodec, s_min, fps_min.toInt, s_max, fps_max.toInt)

              case audioReg(ch_min, bits_min, rate_min, ch_max, bits_max, rate_max) =>
                optionList = optionList :+ AudioOption(ch_min.toInt, bits_min.toInt, rate_min.toInt, ch_max.toInt, bits_max.toInt, rate_max.toInt)

              case _ =>
              //                log.info(s"useless info or format error, $e")
            }
            deviceOptionMap.put(Device(dType, dName), optionList)

          case x@_ =>
            log.info(s"match error, $x")
        }
          //        println(deviceOptionMap)
      case Left(e) =>
        log.info(s"get device option failed! error: ${e.getMessage}")
        //        Map.empty
    }
  }

  def init: Future[Unit] = {
    //    log.debug(s"${availableDevices.size}")
    log.info(s"Try to get pc device info...")
    val availableDevices = getAllDevices
    availableDevices.map { ad =>
      ad.foreach { i =>
        getDeviceOption(i._1, i._2)
      }
    }
  }

  def getDeviceOptions: mutable.HashMap[Device, List[DeviceOption]] = {
    deviceOptionMap
  }

  def parseImgResolution(re: String): (Int, Int) = {
    val reg = """([.0-9]+)x([.0-9]+)""".r
    re match {
      case reg(w, h) =>
        (w.toInt, h.toInt)
      case _ =>
        log.info("parse resolution error, use default resolution")
        (640, 360)
    }
  }


  def main(args: Array[String]): Unit = {

    //    def useADevice(dName: String) = s"ffmpeg -f dshow -list_options true -i video=\"${dName}\"";
    //    val useDevices = "ffmpeg -f dshow -i video=\"Integrated Camera\":audio=\"Microphone name here\" out.mp4"
    //    val ip = "ipconfig"
    val availableDevices = getAllDevices
    availableDevices.foreach(i => i.foreach(j => getDeviceOption(j._1, j._2)))
    //    println(deviceOptionMap)
    //    println(parseImgResolution(AppSettings.imgResolution))
  }


}
