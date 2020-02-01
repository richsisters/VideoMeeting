package org.seekloud.VideoMeeting.protocol.ptcl.distributor2Manager

/**
  * User: LTy
  * Data: 2019/7/17
  * Time: 14:00
  */
object DistributorProtocol {

  sealed trait CommonRsp{
    val errCode:Int
    val msg:String
  }

  final case class ErrorRsp(
                             errCode: Int,
                             msg: String
                           ) extends CommonRsp

  final case class SuccessRsp(
                               errCode: Int = 0,
                               msg: String = "ok"
                             ) extends CommonRsp

  // 控制流相关
  case class StartPullReq(
                           roomId:Long,
                           liveId:String
                         )

  case class StartPullRsp(
                           errCode:Int,
                           msg:String,
                           liveAdd:String,
                           startTime:Long
                         ) extends CommonRsp

  case class FinishPullReq(
                            liveId:String
                          )

  case class CheckStreamReq(
                             liveId:String
                           )

  case class CheckStreamRsp(
                             errCode:Int,
                             msg:String,
                             status:String
                           ) extends CommonRsp


  //录像相关
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

  //管理页面展示信息
  case class GetAllLiveInfoReq()

  case class GetAllLiveInfoRsp(
                                errCode:Int = 0,
                                msg: String ="ok",
                                info: List[liveInfo]
                              )extends CommonRsp

  case class liveInfo(
                      roomId: Long,
                      liveId: String,
                      port: Long,
                      status: Int,
                      Url: String
                    )

  case class CloseStreamReq(liveId: String)

  case class CloseStreamRsp(
                      errCode:Int = 0,
                      msg: String ="ok",
                      info: String
                    )extends CommonRsp
}
