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
                         clientInfo: List[String], //存放多个参会人的liveId
                         pushLiveId:String,
                         pushLiveCode:String,
                         startTime: Long
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


//  /**  url:processor/update
//    *  post
//    */
//  case class UpdateRoomInfo(
//                             roomId: Long,
//                             layout: Int
//                           )
//
//  case class UpdateRsp(
//                        errCode: Int = 0,
//                        msg:String = "ok"
//                      ) extends CommonRsp

  /**  url:processor/forceExit
    *  post
    */
  case class ForceExit(
                      roomId: Long,
                      liveId: String,
                      startTime: Long
                      )

  case class ExitRsp(
                    errCode: Int = 0,
                    msg: String = "ok"
                    ) extends CommonRsp

  /**  url:processor/banOnClient
    *  post
    */
  case class BanOnClient(
                        roomId: Long,
                        liveId: String,
                        isImg: Boolean,
                        isSound: Boolean
                        )

  case class BanRsp(
                   errCode: Int = 0,
                   msg: String ="ok"
                   ) extends CommonRsp


  /**  url:processor/cancelBan
    *  post
    */
  case class CancelBan(
                      roomId: Long,
                      liveId: String,
                      isImg: Boolean,
                      isSound: Boolean
                      )
  case class CancelBanRsp(
                         errCode: Int = 0,
                         msg: String = "ok"
                         ) extends CommonRsp

  /**  url:processor/speakerRight
    *  post
    */
  case class SpeakerRight(
                         roomId: Long,
                         liveId: String,
                         startTime: Long
                         )

  case class SpeakerRightRsp(
                              errCode: Int = 0,
                              msg: String ="ok"
                            )extends  CommonRsp



  /**  url:processor/record
    *  post
    */
  case class SeekRecord(
                         roomId:Long,
                         startTime:Long
                       )

  case class RecordInfoRsp(
                            errCode:Int = 0,
                            msg:String = "ok",
                            duration:String
                          ) extends CommonRsp

  case class RecordList(
                         records:List[RecordData]
                       )

  case class RecordData(
                         roomId:Long,
                         startTime:Long
                       )

  case class RecordInfo(fileExist:Boolean,
                        duration:String)

}
