import org.bytedeco.javacpp.Loader

object Hls2Mp4 {

  def testFlvSize(): Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", "udp://127.0.0.1:41100", "-f",
      "dash", "-hls_playlist", "1", "/Users/litianyu/Downloads/test2/out.mpd",
      "/Users/litianyu/Downloads/test2/record.ts")
    val processor = pb.inheritIO().start()
    Thread.sleep(1000000)
  }

  def hls2Mp4():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", "/Users/litianyu/Downloads/test/master.m3u8","-movflags","faststart",
      "/Users/litianyu/Downloads/mp4/out2.mp4")
    val processor = pb.inheritIO().start()
    Thread.sleep(100000)

  }

  def hls2Mp41():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", "/Users/litianyu/Downloads/test3/master.m3u8","-movflags","faststart",
      "/Users/litianyu/Downloads/mp4/out2.mp4")
    val processor = pb.inheritIO().start()
    Thread.sleep(100000)

  }

  def flv2Mp4():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", "/Users/litianyu/Downloads/test3/record.flv","-movflags","faststart",
      "/Users/litianyu/Downloads/mp4/out4.mp4")
    val processor = pb.inheritIO().start()
    Thread.sleep(100000)
  }

  def ts2Mp4():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", "/Users/litianyu/Downloads/test2/record.ts","-movflags","faststart",
      "/Users/litianyu/Downloads/test2/out.mp4")
    val processor = pb.inheritIO().start()
    Thread.sleep(100000)
  }

  def main(args: Array[String]): Unit = {
//    testFlvSize()
//    hls2Mp4()
//    hls2Mp41()
//    flv2Mp4()
    ts2Mp4()
  }

}
