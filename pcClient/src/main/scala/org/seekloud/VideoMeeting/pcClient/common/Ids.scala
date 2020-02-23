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
      case AudienceStatus.CONNECT => s"room$roomId-connect"
      case _ =>
        //do nothing
        ""
    }

    playId

  }



}
