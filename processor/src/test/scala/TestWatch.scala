import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.seekloud.VideoMeeting.processor.utils.HttpUtil
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol.UpdateRoom
//import org.seekloud.VideoMeeting.processor.Boot.executor
import scala.concurrent.ExecutionContext.Implicits.global




object TestWatch extends HttpUtil {
  def main(args: Array[String]): Unit = {
    val url = "http://10.1.29.248:41665/VideoMeeting/distributor/getFile/8888/index.mpd"
    getRequestSend("get", url, List()).map{
      case Right(value) =>
        println(s"get value $value")
      case Left(error) =>
        println(s"get error $error")
    }
  }
}
