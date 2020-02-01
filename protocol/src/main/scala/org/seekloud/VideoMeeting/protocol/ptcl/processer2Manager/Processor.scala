package org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager

object Processor {

  sealed trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  /**  url:processor/newConnect
    *  post
    */
  case class NewConnect(
                         roomId: Long,
                         host: String,
                         client: String,
                         pushLiveId:String,
                         pushLiveCode:String,
                         layout: Int = 1
                       )

  case class NewConnectRsp(
                          errCode: Int = 0,
                          msg:String = "ok"
                          ) extends CommonRsp

  /**  url:processor/closeRoom
    *  post
    */
  case class CloseRoom(
                        roomId: Long
                      )

  case class CloseRoomRsp(
                            errCode: Int = 0,
                            msg:String = "ok"
                          ) extends CommonRsp


  /**  url:processor/update
    *  post
    */
  case class UpdateRoomInfo(
                             roomId: Long,
                             layout: Int
                           )

  case class UpdateRsp(
                        errCode: Int = 0,
                        msg:String = "ok"
                      ) extends CommonRsp


}
