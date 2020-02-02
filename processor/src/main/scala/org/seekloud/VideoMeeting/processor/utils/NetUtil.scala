package org.seekloud.VideoMeeting.processor.utils

import java.nio.channels.DatagramChannel

/**
  * Created by sky
  * Date on 2019/10/18
  * Time at 上午10:39
  * 网络端口工具
  */
object NetUtil {
  def getRandomPort() = {
    val channel = DatagramChannel.open()
    val port = channel.socket().getLocalPort
    channel.close()
    port
  }
}
