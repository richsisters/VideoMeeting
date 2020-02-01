package org.seekloud.VideoMeeting.protocol

/**
  * User: Arrow
  * Date: 2019/7/15
  * Time: 16:36
  */
package object ptcl {

  trait Request

  trait Response {
    val errCode: Int
    val msg: String
  }

  case class CommonRsp(
    errCode: Int = 0,
    msg: String = "ok"
  ) extends Response

}
