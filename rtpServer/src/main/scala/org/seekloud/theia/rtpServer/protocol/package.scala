package org.seekloud.VideoMeeting.rtpServer

/**
  * Author: wqf
  * Date: 2019/8/25
  * Time: 13:57
  */
package object protocol {
  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  final case class ErrorRsp(
    errCode: Int,
    msg: String
  ) extends CommonRsp

  final case class SuccessRsp(
    errCode: Int = 0,
    msg: String = "ok"
  ) extends CommonRsp
}
