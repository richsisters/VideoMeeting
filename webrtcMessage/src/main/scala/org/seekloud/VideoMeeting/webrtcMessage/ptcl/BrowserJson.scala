package org.seekloud.VideoMeeting.webrtcMessage.ptcl

/**
  * Created by sky
  * Date on 2019/6/17
  * Time at 下午1:07
  * 对接前端页面webSocket
  */
object BrowserJson {

  object EventId {
    val PING = "PING"   //前端定时发送
    val PONG = "PONG"   //后台回复
    val Anchor_SDP_OFFER = "Anchor_SDP_OFFER"  //主播连入
    val Audience_SDP_OFFER = "Audience_SDP_OFFER"  //connect 消息之后发送
    val PROCESS_SDP_ANSWER = "PROCESS_SDP_ANSWER"  //自动处理
    val ADD_ICE_CANDIDATE = "ADD_ICE_CANDIDATE"  //自动处理
    val CONNECT = "CONNECT"  //建立连线
    val DISCONNECT = "DISCONNECT"  //断连
  }

  case class Connect(
                      id: String = EventId.CONNECT,
                      anchor: String, //主播liveId
                      audience: String //连线liveId
                    )

  case class DisConnect(
                         id: String = EventId.DISCONNECT,
                         liveId: String //被断开的liveId
                       )

  trait WsMsg

  case object Complete extends WsMsg

  case class Fail(ex: Throwable) extends WsMsg

  case class ProtocolMsg(m: String) extends WsMsg

}
