package org.seekloud.VideoMeeting.distributor


import java.io.File

import org.bytedeco.javacpp.Loader
import org.seekloud.VideoMeeting.distributor.utils.CmdUtil
import org.seekloud.VideoMeeting.distributor.Boot.executor

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * User: Jason
 * Date: 2019/10/11
 * Time: 11:24
 *
 * mpd转mp4测试用脚本（也可测试各类命令行）
 *
 */

object CmdTest {
  var process: Process = _
  def main(args: Array[String]): Unit = {
//    System.out.println( System.getProperty("java.library.path"))
    val ffmpeg =
//      "C:\\Users\\Shinobi\\.javacpp\\cache\\ffmpeg-4.1.3-1.5-windows-x86_64.jar\\org\\bytedeco\\ffmpeg\\windows-x86_64\\ffmpeg.exe"
      Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val os = new File(System.getProperty("os.name"))
    println(os)
//    val path  = "D:\\videos\\2019-10-18_16-52-32.mp4"
//    val fCommandStr = ffmpeg + s" -re -i $path -profile:v high -pix_fmt yuv420p -level:v 4.1 -preset ultrafast -tune zerolatency -vcodec libx264 -r 10 -b:v 512k -s 640x360 -acodec aac -ac 2 -ab 32k -ar 44100  -f rtp_mpegts rtp://127.0.0.1:50101"
//    process = CmdUtil.exeFFmpeg(fCommandStr)

    if (os.toString.startsWith("Windows")) {
      //Windows运行
      val path = "D:\\test\\rtp\\"
      val commandStr4Win1 = s"copy /b ${path}init-stream0.m4s+${path}chunk-stream0*.m4s ${path}video.mp4"
      val commandStr4Win2 = s"copy /b ${path}init-stream1.m4s+${path}chunk-stream1*.m4s ${path}audio.mp4"
      val r1 = CmdUtil.exeCmd4Windows(commandStr4Win1)
      val r2 = CmdUtil.exeCmd4Windows(commandStr4Win2)
      Future.sequence(List(r1, r2)).onComplete{
        case Success(a) =>
          println(s"exe successfully, $a")
          val fCommandStr = ffmpeg + s" -i ${path}video.mp4 -i ${path}audio.mp4 -c:v copy -c:a copy ${path}final.mp4"
          process = CmdUtil.exeFFmpeg(fCommandStr)
        case Failure(exception) =>
          println(s"exe error, $exception")
      }
    }
    else {
      //Linux运行
//      val path = "/Users/angel/Downloads/dash"
      val path = "/Users/litianyu/Downloads/dash"
      val commandStr = s"cat $path/init-stream0.m4s /$path/chunk-stream0*.m4s >> $path/video.mp4"+
                       s"; cat $path/init-stream1.m4s $path/chunk-stream1*.m4s >> $path/audio.mp4"
      CmdUtil.exeCmd4Linux(commandStr)
      val fCommandStr = ffmpeg + s" -i $path/video.mp4 -i $path/audio.mp4 -c:v copy -c:a copy $path/final.mp4"
      process = CmdUtil.exeFFmpeg(fCommandStr)
    }

    Thread.sleep(200000)
    process.destroy()


    /*在服务器上测试可解开以下代码以删除无用的文件*/
    //    val cmd = s"rm -r /home/sk75/distributor/test/video.mp4; rm -r /home/sk75/distributor/test/audio.mp4"
    //    CmdUtil.exeCmd4Linux(cmd)
  }

}

