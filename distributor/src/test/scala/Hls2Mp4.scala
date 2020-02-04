import org.bytedeco.javacpp.Loader

object Hls2Mp4 {

  def testFlvSize(): Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i", "udp://127.0.0.1:41100", "-f",
      "dash", "-hls_playlist", "1", "/Users/angel/Downloads/dash/record.ts")
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

  def stream2Dash():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg, "-i","udp:127.0.0.1:41100","-b:v","1M","-b:a","1M","-c","copy","-f","dash", "-window_size","20",
      "-extra_window_size","20","-hls_playlist","1","/Users/angel/Downloads/dash/index.mpd"
    )
    val processor = pb.inheritIO().start()
    Thread.sleep(500000)
  }

  def hlsMulti():Unit = {
    val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
    val pb = new ProcessBuilder(ffmpeg,"-i",s"D:/helloMedia/0.mp4",
      "-b:v:0","2496k","-b:a:0","64k","-s:v:0","1280x720",
      "-b:v:1","1216k","-b:a:1","64k","-s:v:1","720x480",
      "-b:v:2","896k","-b:a:2","64k","-s:v:2","640x360",
      "-map","0:v","-map","0:a","-map","0:v","-map","0:a","-map","0:v","-map","0:a",
      "-f","hls",
      "-var_stream_map","\"v:0,a:0 v:1,a:1 v:2,a:2\"",
      "-c:v","libx264","-c:a","aac",
      "-hls_time","10","-master_pl_name","master.m3u8",
      "D:/test/out%v/out.m3u8")

  }

  def main(args: Array[String]): Unit = {
//    testFlvSize()
//    hls2Mp4()
//    hls2Mp41()
//    flv2Mp4()
//    ts2Mp4()
    stream2Dash()
  }

}
