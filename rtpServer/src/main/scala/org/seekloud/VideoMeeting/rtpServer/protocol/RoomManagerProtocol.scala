package org.seekloud.VideoMeeting.rtpServer.protocol

/**
  * Author: wqf
  * Date: 2019/7/17
  * Time: 22:31
  */
object RoomManagerProtocol {

  case class  SendReq(
    appId: String,
    sn: String,
    timestamp: String,
    nonce: String,
    signature: String,
    data: String
  )

  case class SendPostData(
    liveInfo: LiveInfo
  )

  case class LiveInfo(
                     liveId: String,
                     liveCode: String
                     )

  case class PostRsp(
    access: Boolean,
    errCode: Int,
    msg: String
  )

}
