import org.bytedeco.javacv.{FFmpegFrameGrabber, FFmpegFrameRecorder}

object TestUdp2Dash {

  def main(args: Array[String]): Unit = {
    val src = "udp://127.0.0.1:41100"
    val dst = "/Users/litianyu/Downloads/test2/index.mpd"

    val grabber = new FFmpegFrameGrabber(src)
    grabber.start()

    val recorder = new FFmpegFrameRecorder(dst,2)
    recorder.setFormat("dash")
    recorder.start()

    var frame = grabber.grab()
    var count = 0
    while(frame.image!=null || frame.samples!=null) {

      println(s"count: $count")
      recorder.record(frame)
      frame = grabber.grab()

      count += 1

    }


  }
}
