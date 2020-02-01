package org.seekloud.VideoMeeting.protocol.ptcl.server2Manager

import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.LiveInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.LiveInfo
import org.seekloud.VideoMeeting.protocol.ptcl._


/**
  * User: Arrow
  * Date: 2019/7/17
  * Time: 16:24
  */
object CommonProtocol {


  case class Verify(
    liveInfo: LiveInfo
  ) extends Request

  case class VerifyRsp(
    access: Boolean = false,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends Response

  val VerifyError = VerifyRsp(errCode = 100002, msg = "verification error.")

  final case class ExchangeLiveIdByTokenReq(
                                             token: String
                                           ) extends Request

  final case class ExchangeLiveIdRsp(
                                      liveInfoOpt: Option[LiveInfo],
                                      errCode: Int = 0,
                                      msg: String = "ok"
                                    ) extends Response

}
