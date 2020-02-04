package org.seekloud.VideoMeeting.distributor.utils

import java.io.{BufferedReader, InputStreamReader}
import java.nio.charset.Charset
import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import org.bytedeco.javacpp.Loader
import org.seekloud.VideoMeeting.distributor.common.AppSettings.recordLocation


object StreamUtil {
  val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
  val ffprobe = Loader.load(classOf[org.bytedeco.ffmpeg.ffprobe])
  val fileLocation = "/Users/angel/Downloads/test.mp4"
  val streamLocation = "udp://127.0.0.1:41100"

  val flag :AtomicBoolean = new AtomicBoolean(false)
  val logInfo = new StringBuilder()
  val logError = new StringBuilder()

  def testThread(process:ProcessBuilder,outInfo:StringBuilder) = new Thread(()=>
  {
    val processor = process.start()
    val br = new BufferedReader(new InputStreamReader(processor.getInputStream))
    //    val br4err = new BufferedReader(new InputStreamReader(processor.getErrorStream))
    var line:String = ""
    while ({
      line = br.readLine()
      line != null
    }){
      outInfo.append(line + "\n")
    }
    flag.set(true)
    br.close()
    //    br4err.close()
    processor.destroy()

  })

  def test_stream(url:String) = {
    val pb_streams = new ProcessBuilder(ffprobe,"-of","csv","-show_packets",url)
    val log = new StringBuilder()
    val thread = testThread(pb_streams, log)
    thread.start()
    val start = System.currentTimeMillis()
    var end = start
    try {
      while (end - start < 1000 && !flag.get()){
        TimeUnit.MILLISECONDS.sleep(100)
        end = System.currentTimeMillis()
      }
    }catch {
      case e:Exception =>
        e.printStackTrace()
    }
    thread.interrupt()
    if (log.length != 0) true
    else false
  }



  def main(args: Array[String]): Unit = {
    //    getVideoDuration
    println(test_stream(fileLocation))
  }
}
