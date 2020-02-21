import java.io.{BufferedReader, File, InputStreamReader}
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import org.bytedeco.javacpp.Loader

object VideoDuration {

  def ts2mp4():String = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", s"/Users/wang/Downloads/out.ts", "-c:v", "libx264", "-c:a", "copy", "-preset", "faster", s"/Users/wang/Desktop/out_1.mp4")
    println(s"start == ${System.currentTimeMillis()}")
    val process = pb.start()
    val code = process.waitFor(10, TimeUnit.SECONDS)
    val bw = new BufferedReader(new InputStreamReader(process.getErrorStream))
    var line = ""
    while ({
      line = bw.readLine()
      line != null
    }) {
      println(line)
    }
    bw.close()
    println(s"code == $code")
    if(code == 0){
      println(s"end == ${System.currentTimeMillis()}")
    }
    ""
  }

  private def getVideoDuration(src: String) ={
    val ffprobe = Loader.load(classOf[org.bytedeco.ffmpeg.ffprobe])
    //容器时长（container duration）
    val pb = new ProcessBuilder(ffprobe,"-v","error","-show_entries","format=duration", "-of","csv=p=0","-i", s"$src")
    val processor = pb.start()
    val br = new BufferedReader(new InputStreamReader(processor.getInputStream))
    val sb = new StringBuilder()
    var line:String = ""
    while ({
      line = br.readLine()
      line != null
    }){
      sb.append(line)
    }
    br.close()
    val duration = (sb.toString().toDouble * 1000).toInt
    processor.destroy()
    millis2HHMMSS(duration)
  }

  private def getVideoDuration_1(src: String) = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", s"$src")
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

  def main(args: Array[String]): Unit = {
//    val f = new File("/Users/litianyu/Downloads/test.mp4")
//    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
//    val pb = new ProcessBuilder(ffmpeg,"-i","/Users/litianyu/Downloads/record.ts")
//    val processor = pb.start()
//
//    val br = new BufferedReader(new InputStreamReader(processor.getErrorStream))
//    val sb = new StringBuilder()
//    var s = ""
//    s = br.readLine()
//    while(s!=null){
//      sb.append(s)
//      s = br.readLine()
//    }
//    br.close()
//
//    val regex = "Duration: (.*?),"
//    val p = Pattern.compile(regex)
//    val m = p.matcher(sb.toString())
//    if(m.find()){
//      println(s"${m.group(1)}")
//    }
    println("start...")
    val a = ts2mp4()
    println(s"===$a")
    println(s"duration:${getVideoDuration_1("/Users/wang/Downloads/out_1.ts")}")
  }
}
