package org.seekloud.VideoMeeting.processor.protocol

/**
  * User: yuwei
  * Date: 2019/7/16
  * Time: 11:25
  */
object SharedProtocol {

  case class UpdateRoom(
    roomId:Long,
    liveIdList:List[String], //主播放在第一个
    startTime:Long,//视频开始时间，若没有则为0
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

  case class GetRecordList(
    roomId:Long
  )

  case class Record(
    roomId: Long,
    startTime: Long,
    endTime: Long ,
    mpdAddr: String
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

  case class GetRecordListRsp(
    list: Option[List[Record]],
    errCode: Int = 0,
    msg: String = "Ok"
  ) extends CommonRsp

  final case class UploadSuccessRsp(
    fileName: String,
    errCode: Int = 0,
    msg: String = ""
  ) extends CommonRsp
}
