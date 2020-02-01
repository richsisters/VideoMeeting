import java.io.{BufferedReader, File, InputStreamReader}
import java.util.regex.Pattern

import org.bytedeco.javacpp.Loader

object VideoDuration {

  def main(args: Array[String]): Unit = {
//    val f = new File("/Users/litianyu/Downloads/test.mp4")
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg,"-i","/Users/litianyu/Downloads/record.ts")
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
    if(m.find()){
      println(s"${m.group(1)}")
    }
  }
}
