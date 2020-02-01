import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.seekloud.VideoMeeting.processor.utils.HttpUtil
import org.seekloud.VideoMeeting.processor.protocol.SharedProtocol.UpdateRoom
//import org.seekloud.VideoMeeting.processor.Boot.executor
import scala.concurrent.ExecutionContext.Implicits.global

object TestReconnect extends HttpUtil {
  case class Rsp(errCode:Int = 0, msg:String = "ok")


  def main(args: Array[String]): Unit = {
    val source = 1116
    val source2 = 1114
    val ls1 = List(s"liveIdTest-${source}")
    val ls2 = List(s"liveIdTest-${source}",s"liveIdTest-${source2}")

    val url = "http://10.1.29.248:41669/VideoMeeting/processor/updateRoomInfo"
    val data = UpdateRoom(8888,ls1, System.currentTimeMillis(),1,0).asJson.noSpaces
    postJsonRequestSend("post", url, List(), data).map{
      case Right(value) =>
        decode[Rsp](value) match {
          case Right(rsp) =>
            println(rsp.errCode)
            println(s"msg: ${rsp.msg}")

          case Left(error) =>
            println(error)
        }
      case Left(error) =>
        println(error)
        println("send request error!")

    }



  }
}
