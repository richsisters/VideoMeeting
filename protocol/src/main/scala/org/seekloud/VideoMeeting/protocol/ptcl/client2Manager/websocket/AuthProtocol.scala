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
    * 主持人端
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

  /*会议控制*/
  case class AudienceJoin(userId: Long, userName: String, clientType: Int) extends WsMsgRm2Host //申请加入会议者信息

  case class JoinAccept(roomId: Long, userId: Long, clientType: Int, accept: Boolean) extends WsMsgHost //主持人审批某个用户加入请求

  case object StartMeetingRecord extends WsMsgHost //开始会议录像

  case class StartMeetingRsp(msg: String) extends WsMsgRm2Host

  /*邀请好友*/
  case class Invite(
                     email: String,
                     meetingNum: String //会议号
                   ) extends WsMsgHost


  case object InviteRsp extends WsMsgRm2Host

  case object BanOnAnchor extends WsMsgRm2Host//禁播消息

  /*主持人权限*/
  case class ForceExit(userId4Member: Long, userName4Member: String) extends WsMsgHost //强制某个用户退出

  case class BanOnMember(userId4Member: Long, image: Boolean, sound: Boolean) extends WsMsgHost //屏蔽某人声音活着图像

  case class CancelBan(userId4Member: Long, image: Boolean, sound: Boolean) extends WsMsgHost //取消屏蔽某人声音活着图像

  case class SpeakerRight(userId4Member: Long) extends WsMsgHost //指定某人发言

  case object CancelSpeakerRight extends WsMsgHost //取消指定某人发言


  /**
    *
    * 参会者端
    *
    **/


  /*client发送*/
  sealed trait WsMsgAudience extends WsMsgClient

  /*room manager发送*/
  sealed trait WsMsgRm2Audience extends WsMsgRm


  /*申请加入会议*/
  case class JoinReq(userId: Long, roomId: Long, clientType: Int) extends WsMsgAudience


  case class JoinRsp(
    hostLiveId: Option[String] = None, //主持人liveId
    joinInfo: Option[LiveInfo] = None, //用户liveInfo
    attendInfo: List[String] = Nil,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends WsMsgRm2Audience

  val JoinInvalid = JoinRsp(errCode = 300001, msg = "房主未开通连线功能")

  val JoinInternalError = JoinRsp(errCode = 300001, msg = "网络问题")
  val JoinAccountError = JoinRsp(errCode = 300001, msg = "用户信息出错")

  val JoinRefused = JoinRsp(errCode = 300001, msg = "主持人拒绝您的参会申请") //房主拒绝连线申请

  case class AudienceShutJoin(roomId: Long, userId: Long) extends WsMsgAudience //某个用户退出会议

  case class HostCloseRoom() extends WsMsgRm2Audience //房主关闭房间通知房间所有用户

  case class UpdateRoomInfo2Client(
                                    roomName: String,
                                    roomDec: String
                                  ) extends WsMsgRm2Audience

  case class ForceExitRsp(userId: Long, userName: String, liveId: String) extends WsMsgRm2Audience//用户被主持人强制退出

  case class BanOnMemberRsp(userId: Long, image: Boolean, sound: Boolean) extends WsMsgRm2Audience//用户被主持人屏蔽声音或者图像

  case class CancelBanOnMemberRsp(userId: Long, image: Boolean, sound: Boolean) extends WsMsgRm2Audience//用户被主持人取消屏蔽声音或者图像

  case class SpeakerRightRsp(userId: Long) extends WsMsgRm2Audience //用户被主持人指定为发言者

  case object CancelSpeakerRightRsp extends WsMsgRm2Audience //用户被主持人取消指定为发言者

  /**
    * 所有用户
    **/
  case class AudienceDisconnect(userId: Long, audienceLiveId: String) extends WsMsgRm //通知房间内所有用户，某参会者退出

  case class AudienceJoinRsp(
                              joinInfo: Option[AudienceInfo] = None, //参会者者信息
                              errCode: Int = 0,
                              msg: String = "ok"
                            ) extends WsMsgRm //拒绝成功不发joinInfo，仅发送默认状态信息

  val AudienceJoinError = AudienceJoinRsp(errCode = 400020, msg = "用户加入会议失败")

  val NoHostLiveInfoError = AudienceJoinRsp(errCode = 400030, msg = "没有流信息")

}
