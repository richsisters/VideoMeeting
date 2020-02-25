import java.text.SimpleDateFormat
import java.util.TimeZone

import org.bytedeco.javacpp.Loader

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.parsing.json._
//import it.sauronsoftware.jave.Encoder
//import it.sauronsoftware.jave.MultimediaInfo
import java.io.{BufferedReader, File, InputStreamReader}
import java.util.regex.Pattern

object TestRecord {
  def main(args: Array[String]): Unit = {
//    val a = System.currentTimeMillis()
//    println("first",getVideoDuration("D:/helloMedia/2.mp4"))
//    val b = System.currentTimeMillis()
//    println("third",calDuration("D:/helloMedia/2.mp4"))
//    val c = System.currentTimeMillis()
//    println("four",getDuration("D:/helloMedia/2.mp4"))
//    var d = System.currentTimeMillis()
//    println("b-a", b - a)
//    println("c-b", c - b)
//    println("d-c", d - c)
//    multiHls("D:/helloMedia/0.mp4")
//    Thread.sleep(1000000)
    println("start record...")
    ts2mp4()
  }

  private def multiHls(scr:String) = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])

    //    val pb = new ProcessBuilder(ffmpeg,"-i","udp://127.0.0.1:41100","-f","dash","-window_size","20","-extra_window_size",
    //      "20","-hls_playlist","1","/Users/litianyu/Downloads/test/index.mpd","/Users/litianyu/Downloads/test/out.flv")

    //hls多码率
    //    val pb = new ProcessBuilder(ffmpeg,"-i",s"D:/helloMedia/0.mp4",
    //             "-b:v:0","2496k","-b:a:0","64k","-s:v:0","1280x720",
    //             "-b:v:1","1216k","-b:a:1","64k","-s:v:1","720x480",
    //             "-b:v:2","896k","-b:a:2","64k","-s:v:2","640x360",
    //             "-map","0:v","-map","0:a","-map","0:v","-map","0:a","-map","0:v","-map","0:a",
    //             "-f","hls","-var_stream_map","\"v:0,a:0 v:1,a:1 v:2,a:2\"",
    //             "-c:v","libx264","-c:a","aac",
    //             "-hls_time","10","-master_pl_name","master.m3u8","D:/test/out%v/out.m3u8")

    //生成三种码率（共用m4s文件）
    val pb = new ProcessBuilder(ffmpeg,"-i",s"D:/helloMedia/0.mp4",
      "-b:v:0","2496k","-b:a:0","64k","-s:v:0","1280x720",
      "-b:v:1","1216k","-b:a:1","64k","-s:v:1","720x480",
      "-b:v:2","896k","-b:a:2","64k","-s:v:2","640x360",
      "-map","0:v","-map","0:a","-map","0:v","-map","0:a","-map","0:v","-map","0:a",
      "-f","dash","-window_size","20","-extra_window_size","20","-hls_playlist","1","-c:v:0","copy",
      "-hls_time","10","D:/test/index.mpd")

    val processor = pb.inheritIO().start()
  }

  private def calDuration(scr: String) ={
    val ffprobe = Loader.load(classOf[org.bytedeco.ffmpeg.ffprobe])

    //容器时长（container duration）
    //    val pb = new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration", "-of","default=noprint_wrappers=1:nokey=1","-i", "D:/helloMedia/0.mp4")
    val pb = new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration", "-of","csv=\"p=0\"","-i", scr)

    //音视频流时长（stream duration）
    //    val pb = new ProcessBuilder(ffprobe,"-v","error","-select_streams", "v", "-show_entries","stream=duration", "-of","default=noprint_wrappers=1:nokey=1","-i", "D:/helloMedia/0.mp4")

    val processor = pb.start()
    val br = new BufferedReader(new InputStreamReader(processor.getInputStream))
    val s = br.readLine()
    var duration = 0
    if(s!= null){
      duration = (s.toDouble * 1000).toInt
    }
    if(processor != null){
      processor.waitFor()
      processor.destroy()
    }
    millis2HHMMSS(duration)
  }

  def millis2HHMMSS(sec: Double): String = {
    val hours = (sec / 3600000).toInt
    val h =  if (hours >= 10) hours.toString else "0" + hours
    val minutes = ((sec % 3600000) / 60000).toInt
    val m = if (minutes >= 10) minutes.toString else "0" + minutes
    val seconds = ((sec % 60000) / 1000).toInt
    val s = if (seconds >= 10) seconds.toString else "0" + seconds
    val dec = ((sec % 1000) / 10).toInt
    val d = if (dec >= 10) dec.toString else "0" + dec
    s"$h:$m:$s.$d"
  }



//    private def getDuration(scr:String) ={
//    val file = new File(scr)
//    val encoder = new Encoder()
//    val duration = encoder.getInfo(file).getDuration
//    val hours = duration/60/60/1000
//    val minutes = (duration - hours*60*60*1000)/60/1000
//    val seconds = (duration - hours*60*60*1000 - minutes*60*1000)/1000
//    val dec = (duration - hours*60*60*1000 - minutes*60*1000 - seconds*1000)/10
//    println("second",duration)
//    val h = if (hours>10) s"${hours.toInt}" else "0"+s"$hours"
//    val m = if (minutes >10) s"${minutes.toInt}" else "0"+s"$minutes"
//    val s = if (seconds > 10) s"${seconds.toInt}" else "0"+s"$seconds"
//    val d= if (dec > 10) s"${dec.toInt}" else "0"+s"$dec"
//    h +":" + m + ":" + s + "." + d
//  }

  private def getVideoDuration(scr:String) = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", scr)
    val processor = pb.start()

    val br = new BufferedReader(new InputStreamReader(processor.getErrorStream))
    val sb = new StringBuilder()
    var s = ""
    s = br.readLine()
    while(s!=null){
      sb.append(s)
      s = br.readLine()
    }
    br.close()

    val regex = "Duration: (.*?),"
    val p = Pattern.compile(regex)
    val m = p.matcher(sb.toString())
    if(m.find()) {
      m.group(1)
    }else{
      "00:00:00.00"
    }
  }

  def ts2mp4() = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", s"/Users/wang/Downloads/out.ts", "-b:v", "1M", "-movflags", "faststart", s"/Users/wang/Desktop/test.mp4")
    val process = pb.start()
    println("change end...")
  }

}
