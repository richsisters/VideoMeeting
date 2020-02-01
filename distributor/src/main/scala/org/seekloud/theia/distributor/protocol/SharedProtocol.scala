package org.seekloud.VideoMeeting.distributor.protocol

/**
  * User: yuwei
  * Date: 2019/7/16
  * Time: 11:25
  */
object SharedProtocol {

  case class UpdateRoom(
    roomId:Long,
    liveIdList:List[String], //主播放在第一个
    startTime: Long,
    layout:Int,
    aiMode:Int //为了之后拓展多种模式，目前0为不开ai，1为人脸目标检测
  )

  case class RoomInfo(roomId:Long, roles:List[String], layout:Int, aiMode:Int=0)

  case class CloseRoom(
                        roomId:Long
                      )

  case class GetMpd(
                     roomId:Long
                   )

  case class GetRtmpUrl(
                       roomId:Long
                       )

  case class GetMpd4Record(
                          roomId:Long,
                          startTime:Long
                          )

  trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  case class MpdRsp(
                     mpd: String,
                     rtmp:String,
                     errCode: Int = 0,
                     msg: String = "ok"
                   ) extends CommonRsp

  case class RtmpRsp(
                     mpd: String,
                     errCode: Int = 0,
                     msg: String = "ok"
                   ) extends CommonRsp

  final case class ErrorRsp(
                             errCode: Int,
                             msg: String
                           ) extends CommonRsp

  final case class SuccessRsp(
                               errCode: Int = 0,
                               msg: String = "ok"
                             ) extends CommonRsp


  //录像
  case class SeekRecord(
                         roomId:Long,
                         startTime:Long
                       )

  case class RecordList(
                       records:List[RecordData]
                       )

  case class RecordData(
                       roomId:Long,
                       startTime:Long
                       )

  case class RecordInfoRsp(
                            errCode:Int = 0,
                            msg:String = "ok",
                            duration:String
                          ) extends CommonRsp

}
