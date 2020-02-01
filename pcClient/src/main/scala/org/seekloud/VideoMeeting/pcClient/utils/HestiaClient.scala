package org.seekloud.VideoMeeting.pcClient.utils

import org.seekloud.VideoMeeting.pcClient.Boot.executor
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: TangYaruo
  * Date: 2019/9/11
  * Time: 12:25
  */
object HestiaClient extends HttpUtil with CirceSupport {

  private val log = LoggerFactory.getLogger(this.getClass)


  def getHestiaImage(url: String): Future[Either[Throwable, Array[Byte]]] = {
    val methodName = "getHestiaImage"
    getImageContent(methodName, url, Nil, needLogRsp = false)
  }


  def main1(args: Array[String]): Unit = {

    val url = "http://pic.neoap.com/hestia/files/image/roomManager/b8116fee146544809733802a2c3a1319.jpg"
    getHestiaImage(url).onComplete {
      case Success(value) =>
        log.info(s"get image success: $value")
      case Failure(exception) =>
        log.error(s"get image error: $exception")
    }
  }


}
