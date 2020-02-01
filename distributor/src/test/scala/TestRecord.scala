import org.bytedeco.javacpp.Loader

object TestRecord {


  def main(args: Array[String]): Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])

    val pb = new ProcessBuilder(ffmpeg,"-i","udp://127.0.0.1:41100","-f","dash","-window_size","20","-extra_window_size",
      "20","-hls_playlist","1","/Users/litianyu/Downloads/test/index.mpd","/Users/litianyu/Downloads/test/out.flv")
    val processor = pb.inheritIO().start()
    Thread.sleep(1000000)
  }

}
