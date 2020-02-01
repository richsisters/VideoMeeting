package org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{AudienceInfo, LiveInfo, RoomInfo, UserDes}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 17:03
  */
object AuthProtocol {

  sealed trait WsMsgFront

  sealed trait WsMsgManager

  sealed trait WsMsgClient extends WsMsgFront

  sealed trait WsMsgRm extends WsMsgManager

  case object CompleteMsgClient extends WsMsgFront

  case class FailMsgClient(ex: Exception) extends WsMsgFront

  case object CompleteMsgRm extends WsMsgManager

  case class FailMsgRm(ex: Exception) extends WsMsgManager

  case class Wrap(ws: Array[Byte]) extends WsMsgRm


  case class TextMsg(msg: String) extends WsMsgRm

  case object DecodeError extends WsMsgRm

  /**
    *
    * 主播端
    *
    **/

  /*client发送*/
  sealed trait WsMsgHost extends WsMsgClient


  /*roomManager发送*/
  sealed trait WsMsgRm2Host extends WsMsgRm

  /*心跳包*/

  case object PingPackage extends WsMsgClient with WsMsgRm

  case class HeatBeat(ts: Long) extends WsMsgRm

  case object AccountSealed extends WsMsgRm// 被封号

  case object NoUser extends WsMsgRm

  case object NoAuthor extends WsMsgRm

  //fixme url
  case class UpdateAudienceInfo(AudienceList: List[UserDes]) extends WsMsgRm   //当前房间内所有观众的id和昵称,新加入--join--true

  case class ReFleshRoomInfo(roomInfo: RoomInfo) extends WsMsgRm
  /*申请直播*/
  case class GetTokenReq(
    userId: Long
  ) extends WsMsgClient

  case class GetTokenRsp(
    tokenOpt: Option[String],
    SecureKeyOpt: Option[String],
    errCode: Int = 0,
    msg: String = "ok"
  ) extends WsMsgRm2Host

  case class StartLiveReq(
    userId: Long,
    token: String,
    clientType: Int
  ) extends WsMsgHost

  case class StartLiveRsp(
    liveInfo: Option[LiveInfo] = None,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends WsMsgRm2Host

  val StartLiveRefused = StartLiveRsp(errCode = 200001, msg = "start live refused.")
  val StartLiveRefused4Seal = StartLiveRsp(errCode = 200001, msg = "start live refused.account has been sealed")
  val StartLiveRefused4LiveInfoError = StartLiveRsp(errCode = 200001, msg = "start live refused because of getting live info from distributor error.")


  /*修改房间信息*/

  case class ModifyRoomInfo(
    roomName: Option[String] = None,
    roomDes: Option[String] = None
  ) extends WsMsgHost

  case class ModifyRoomRsp(errCode: Int = 0, msg: String = "ok") extends WsMsgRm2Host

  val ModifyRoomError = ModifyRoomRsp(errCode = 200010, msg = "modify room error.")


  /*设置直播内容*/

  case class ChangeLiveMode(
    isJoinOpen: Option[Boolean] = None, //是否开启连线
    aiMode: Option[Int] = None, //是否开启人脸识别
    screenLayout: Option[Int] = None //调整画面布局（对等窗口/大小窗口）
  ) extends WsMsgHost

  case class ChangeModeRsp(errCode: Int = 0, msg: String = "ok") extends WsMsgRm2Host

  val ChangeModeError = ChangeModeRsp(errCode = 200020, msg = "change live mode error.")


  /*连线控制*/

  case class AudienceJoin(userId: Long, userName: String, clientType: Int) extends WsMsgRm2Host //申请连线者信息

  case class JoinAccept(roomId: Long, userId: Long, clientType: Int, accept: Boolean) extends WsMsgHost //审批某个用户连线请求

  case class AudienceJoinRsp(
    joinInfo: Option[AudienceInfo] = None, //连线者信息
    errCode: Int = 0,
    msg: String = "ok"
  ) extends WsMsgRm2Host //拒绝成功不发joinInfo，仅发送默认状态信息

  val AudienceJoinError = AudienceJoinRsp(errCode = 400020, msg = "audience join error")

  val NoHostLiveInfoError = AudienceJoinRsp(errCode = 400030, msg = "no liveInfo")

  case class HostShutJoin(roomId: Long) extends WsMsgHost //断开与观众连线请求//fixme 多人连线需要修改消息添加userId

  case class AudienceDisconnect(hostLiveId: String) extends WsMsgRm2Host //观众断开连线通知（同时rm断开与观众ws）

  case class HostStopPushStream(roomId: Long) extends WsMsgHost //房主停止推流


  /**
    *
    * 观众端
    *
    **/


  /*client发送*/
  sealed trait WsMsgAudience extends WsMsgClient

  /*room manager发送*/
  sealed trait WsMsgRm2Audience extends WsMsgRm


  /*申请连线*/
  case class JoinReq(userId: Long, roomId: Long, clientType: Int) extends WsMsgAudience


  case class JoinRsp(
    hostLiveId: Option[String] = None, //房主liveId
    joinInfo: Option[LiveInfo] = None, //连线者live信息
    errCode: Int = 0,
    msg: String = "ok"
  ) extends WsMsgRm2Audience

  case class Join4AllRsp(
                          mixLiveId: Option[String] = None,
                          errCode: Int = 0,
                          msg: String = "ok",
                        ) extends WsMsgRm2Audience
  /*
  点赞
   */
  case class LikeRoom(userId: Long, roomId: Long, upDown:Int) extends WsMsgClient

  case class LikeRoomRsp(
                          errCode: Int = 0,
                          msg: String = "ok"
                        ) extends WsMsgRm2Audience

  case class JudgeLike(userId: Long, roomId:Long) extends WsMsgClient


  case class JudgeLikeRsp(
                          like:Boolean,                 //true->已点过赞  false->未点过赞
                          errCode: Int = 0,
                          msg: String = "ok"
                        ) extends WsMsgRm2Audience

  val JoinInvalid = JoinRsp(errCode = 300001, msg = "join not open.") //房主未开通连线功能

  val JoinInternalError = JoinRsp(errCode = 300001, msg = "internal error") //房主未开通连线功能
  val JoinAccountError = JoinRsp(errCode = 300001, msg = "userId error") //房主未开通连线功能

  val JoinRefused = JoinRsp(errCode = 300002, msg = "host refuse your request.") //房主拒绝连线申请

  case class AudienceShutJoin(roomId: Long) extends WsMsgAudience //断开与房主的连线请求

  //fixme 切断与某个用户的连线，增加userId，拓展多个用户连线的情况
  case class AudienceShutJoinPlus(userId:Long) extends WsMsgAudience //断开与房主的连线请求

  case class HostDisconnect(hostLiveId: String) extends WsMsgRm2Audience //房主断开连线通知 (之后rm断开ws连接)

  case object HostCloseRoom extends WsMsgRm2Audience //房主关闭房间通知房间所有用户
  case class HostCloseRoom() extends WsMsgRm2Audience //房主关闭房间通知房间所有用户，class方便后台一些代码的处理


  case object BanOnAnchor extends WsMsgRm2Host//禁播消息

  /**
    * 所有用户
    * 留言
    **/

  case class Comment(
    userId: Long,
    roomId: Long,
    comment: String,
    color:String = "#FFFFFF",
    extension: Option[String] = None
  ) extends WsMsgClient

  case class RcvComment(
    userId: Long,
    userName: String,
    comment: String,
    color:String = "#FFFFFF",
    extension: Option[String] = None
  ) extends WsMsgRm

  case class UpdateRoomInfo2Client(
    roomName: String,
    roomDec: String
  ) extends WsMsgRm2Audience

  case object HostStopPushStream2Client extends WsMsgRm2Audience

}
