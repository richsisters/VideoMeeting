package org.seekloud.VideoMeeting.distributor.protocol

/**
  * User: yuwei
  * Date: 2019/7/16
  * Time: 11:25
  */
object SharedProtocol {

  case class StartPullReq(
    roomId:Long,
    liveId:String
  )

  case class CheckStream(
    liveId:String
  )

  case class RoomInfo(roomId:Long, roles:List[String], layout:Int, aiMode:Int=0)

  case class FinishPullReq(
    liveId:String
  )

  case class CheckStreamReq(
    liveId:String
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

  case class StartPullRsp(
    errCode:Int,
    msg:String,
    liveAdd:String,
    startTime:Long
  ) extends CommonRsp

  case class FinishPullRsp(
    errCode: Int = 0,
    msg: String = "ok"
  ) extends CommonRsp

  case class CheckStreamRsp(
    errCode:Int = 0,
    msg:String = "ok",
    status:String = "正常"
  ) extends CommonRsp

  val StreamError = CheckStreamRsp(100001, "streamError", "没有流信息")
  val RoomError = CheckStreamRsp(100002, "roomError", "没有流对应的房间")

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


}
