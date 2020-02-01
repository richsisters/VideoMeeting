package org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.UserInfo

/**
  * created by benyafang on 2019/9/23 13:37
  * */
object AdminProtocol {

  //管理员登陆
  //3
  case class AdminSignIn(
                        admin:String,
                        password:String
                        )

  //删除录像
  //4
  case class DeleteRecordReq(
                            recordIdList:List[Long]//录像的id
                            )

  //封号
  //6
  case class SealAccountReq(
                           userId:Long,
                           sealedUtilTime:Long//封号的截止时间戳，不得小于系统当前时间
                           )
  //取消封号
  //7
  case class CancelSealAccountReq(
                                   userId:Long
                                   )

  //获取用户列表
  //5
  case class GetUserListReq(
                        pageNum:Int,
                        pageSize:Int
                        )

  case class GetUserListRsp(
                           totalNum:Int,
                           userList:List[UserInfo],
                           errCode:Int = 0,
                           msg:String = "ok"
                           )

  //禁播--直播间
  //8
  case class BanOnAnchor(
                        roomId:Long//直播间的roomId
                        )

  case class CancelBanOnAnchor(
                              roomId:Long
                              )

}
