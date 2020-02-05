package org.seekloud.VideoMeeting.protocol.ptcl.processer2Manager

/**
  * User: LTy
  * Data: 2019/7/17
  * Time: 14:00
  */
object ProcessorProtocol {

  sealed trait CommonRsp{
    val errCode:Int
    val msg:String
  }
  // newConnect
  case class newConnectInfo(
    roomId: Long,
    host: String,
    clientInfo: List[String], //存放多个参会人的liveId
    pushLiveId: String,
    pushLiveCode: String,
    layout: Int,
  )
  case class newConnectRsp(
    errCode:Int = 0,
    msg:String = "ok"
  ) extends CommonRsp

  // update
  case class UpdateRoomInfo(
                             roomId:Long,
                             layout:Int,
//                             aiMode:Int //为了之后拓展多种模式，目前0为不开ai，1为人脸目标检测
                           )
  case class UpdateRsp(
    errCode:Int = 0,
    msg:String = "ok"
  ) extends CommonRsp

  //closeRoom
  case class CloseRoom(
                        roomId:Long
                      )

  case class CloseRsp(
    errCode:Int = 0,
    msg:String = "ok"
  ) extends CommonRsp

  case class SeekRecord(
                         roomId:Long,
                         startTime:Long
                       )

  //mpd
  case class GetMpd(
                     roomId:Long
                   )

  case class MpdRsp(
    mpd:String,
    rtmp:String,
    errCode:Int = 0,
    msg:String = "ok"
  ) extends CommonRsp

  //录像
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


  //rebuild=========================================

}
