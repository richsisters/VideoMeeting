package org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http

import org.seekloud.VideoMeeting.protocol.ptcl.Request

/**
  * created by benyafang on 2019/9/26
  * 统计相关接口，时间戳都为毫秒级
  * */
object StatisticsProtocol {

  //看录像结束的请求res:CommonRsp
  case class WatchRecordEndReq(
                               recordId:Long,//录像id
                               userIdOpt:Option[Long],//观看的用户id
                               inTime:Long,//用户开始观看录像的时间
                               outTime:Long,//用户结束观看录像的时间
                               ) extends Request

  //登录统计功能根据天数，包括起始和结束时间
  case class LoginInDataByDayReq(
    startTime:Long,
    endTime:Long
  )

  //登录的统计功能根据小时，包括起始时间和结束时间，如果起始时间和结束时间非整点，则以所在时间的整点返回
  case class LoginInDataByHourReq(
                                startTime:Long,
                                endTime:Long
                                )

  case class LoginInDataInfo(
                           dayNum:Int,        //0:小时 1：天  7：周  30：月
                           timestamp:Long,
                           uv:Int,//访问量
                           pv:Int//访问次数
                           )

  case class LoginInDataRsp(
                                data:List[LoginInDataInfo],
                                errCode:Int = 0,
                                msg:String = "ok"
                                )


  //根据录像的id查询录像的概况数据
  case class WatchProfileDataByRecordIdReq(
                                     recordId:Long
                                     )

  case class WatchProfileInfo(
                            watchTime: Long = 0l,
                            uv4SU:Int,//登录用户的访问人数
                            pv4SU:Int,//登录用户(SU:SignUser)的访问次数
                            pv4TU:Int//临时用户的访问次数
                            )
  case class WatchProfileDataByRecordIdRsp(
                                        url: String = "",
                                        data:Option[WatchProfileInfo] = None,
                                        errCode:Int = 0,
                                        msg:String = "ok"
                                        )
  //按天统计录像的观看人数和次数
  case class WatchDataByDayReq(
    recordId:Long,
    startTime:Long,
    endTime:Long
  )
  //按小时统计录像的观看人数和次数
  case class WatchDataByHourReq(
                              recordId:Long,
                              startTime:Long,
                              endTime:Long
                              )
  case class WatchDataByHourInfo(
                                  timestamp:Long,
                                  uv4SU:Int,//登录用户的访问人数
                                  pv4SU:Int,//登录用户(SU:SignUser)的访问次数
                                  pv4TU:Int//临时用户(TU:TemporaryUser)的访问次数
                                  )
  case class WatchDataByHourRsp(
                                 data:List[WatchDataByHourInfo],
                                 errCode:Int = 0,
                                 msg:String = "ok"
                                 )
  //管理员查看的录像信息
  case class AdminRecordInfoReq(
    sortBy: String,
    pageNum: Int,
    pageSize: Int
  )
  case class AdminRecordInfo(
    recordId:Long,//数据库中的录像id，用于删除录像
    roomId:Long,
    recordName:String,
    recordDes:String,
    userId:Long,
    userName:String,
    startTime:Long,
    headImg:String,
    coverImg:String,
    observeNum:Int, //浏览量
    likeNum:Int,
    duration:String = "",
    uv4SU:Int,//登录用户的访问人数
    pv4SU:Int,//登录用户(SU:SignUser)的访问次数
    pv4TU:Int//临时用户的访问次数
  )
  case class getRecordDataByAdminRsp(
    data:List[AdminRecordInfo] = Nil,
    errCode:Int = 0,
    msg:String = "ok"
  )

}
