package org.seekloud.VideoMeeting.pcClient.common

import org.seekloud.VideoMeeting.pcClient.common.Constants.AudienceStatus
import org.slf4j.LoggerFactory

/**
  * Author: zwq
  * Date: 2019/9/23
  * Time: 10:59
  */
object Ids {

  private[this] val log = LoggerFactory.getLogger(this.getClass)


  def getPlayId(audienceStatus: Int, roomId: Long): String = {

    val playId = audienceStatus match {
      case AudienceStatus.LIVE => s"room$roomId"
      case AudienceStatus.CONNECT => s"room$roomId-connect" // 第二个人进入时拉流
      case AudienceStatus.CONNECT2Third => s"room$roomId--connect" // 第三个人进入时
      case AudienceStatus.CONNECT2Fourth => s"room$roomId---connect" //第四个人进入时
      case _ =>
        //do nothing
        ""
    }

    playId

  }



}
