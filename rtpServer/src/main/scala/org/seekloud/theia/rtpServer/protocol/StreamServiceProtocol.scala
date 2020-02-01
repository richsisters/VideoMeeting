package org.seekloud.VideoMeeting.rtpServer.protocol

import org.seekloud.VideoMeeting.rtpServer.ptcl.protocol.Address

/**
  * Author: wqf
  * Date: 2019/8/25
  * Time: 12:59
  */
object StreamServiceProtocol {

  case class GetAllStreamRsp(
    infos: Option[List[StreamInfo]],
    errCode: Int = 0,
    msg: String = "ok"
  )

  case class StreamInfo(
    liveId: String,
    ssrc: Int
  )



  case class GetSubscribers(
    subscribers: Option[List[Address]],
    errCode: Int = 0,
    msg: String = "ok"
  )



}
