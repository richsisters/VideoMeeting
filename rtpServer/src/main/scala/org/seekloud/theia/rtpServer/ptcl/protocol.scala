package org.seekloud.VideoMeeting.rtpServer.ptcl

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

/**
  * Created by haoshuhan on 2019/7/17.
  */
object protocol {

  case class LiveInfo(liveIdList: List[String],
                      channel: DatagramChannel,
                      remoteAddress: InetSocketAddress
                     )

  case class liveIds(data: Array[Byte], seq: Int, m: Int)

  case class LiveInfoByte(liveIds: List[liveIds],
                      channel: DatagramChannel,
                      remoteAddress: InetSocketAddress
  )

  case class Address(host: String, port: Int)
  case class LiveInfo4Actor(channel: DatagramChannel,
                            remoteAddress: InetSocketAddress)



}
