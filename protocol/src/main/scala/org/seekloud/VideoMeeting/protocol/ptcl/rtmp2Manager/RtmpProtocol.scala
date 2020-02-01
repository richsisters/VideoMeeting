package org.seekloud.VideoMeeting.protocol.ptcl.rtmp2Manager

import org.seekloud.VideoMeeting.protocol.ptcl.{Request, Response}

/**
  * created by benyafang on 2019.8.30
  * */
object RtmpProtocol {
  //获取token请求
  case class GetTokenReq(
                        userId:Long
                        ) extends Request

  case class GetTokenRsp(
                        tokenOpt:Option[String],
                        SecureKeyOpt:Option[String],
                        errCode:Int = 0,
                        msg:String = "ok"
                        ) extends Response

  //获取liveinfo请求

  case class GetLiveInfoReq(
                           userId:Long,
                           token:String
                           ) extends Request

  //更新token返回GetTokenRsp
  case class UpdateTokenReq(
                           userId:Long
                           )extends Request

}
