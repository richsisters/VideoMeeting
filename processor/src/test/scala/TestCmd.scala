import java.io.File
import java.util.concurrent.TimeUnit

import org.bytedeco.javacpp.Loader

object TestCmd {

  def main(args: Array[String]): Unit = {

    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])

//    val pb = new ProcessBuilder(ffmpeg, "-i", s"udp://127.0.0.1:41100", "-f", "hls", "-hls_time", "10", "-segment_list_flags", "+live","C:\\Users\\yuwei\\Desktop\\test\\test.m3u8")
//    val pb = new ProcessBuilder(ffmpeg, "-re", "-i", "/Users/litianyu/Downloads/test.mp4", "-c:v", "libx264", "-s", "720x576",
//      "-c:a", "copy", "-f", "hls","-b:v","1M", "-hls_time", "3", "-segment_list_flags", "+live","-hls_list_size", "20","-vcodec", "copy", "-acodec", "copy",
//      "/Users/litianyu/Downloads/test2/test.m3u8")
    val pb = new ProcessBuilder(ffmpeg, "-i", "udp://127.0.0.1:41100", "-f", "dash", "-hls_playlist", "1", "C:\\Users\\yuwei\\Desktop\\test.mpd")
    val processor = pb.inheritIO().start()
    Thread.sleep(1000000)
  }
}
