package org.seekloud.VideoMeeting.roomManager.http

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager, scheduler, userManager}
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.StatisticsProtocol._
import org.seekloud.VideoMeeting.roomManager.common.AppSettings
import org.seekloud.VideoMeeting.roomManager.models.dao.{RecordDao, StatisticDao}
import org.seekloud.VideoMeeting.roomManager.utils.TimeUtil
import org.slf4j.LoggerFactory

import scala.concurrent.Future
/**
  * created by benyafang on 2019/9/26 15:59
  * 统计
  * */
trait StatisticService extends ServiceUtils{
  import io.circe._
  import io.circe.generic.auto._

  private val log = LoggerFactory.getLogger(this.getClass)


  val watchRecordEnd = (path("watchRecordEnd") & post){
    entity(as[Either[Error,WatchRecordEndReq]]){
      case Right(req) =>
        dealFutureResult{
          if (req.userIdOpt.isEmpty){
            StatisticDao.updateObserveEvent(req.recordId,1,true,req.inTime,req.outTime).map{rst =>
              complete(CommonRsp())
            }.recover{case e : Exception =>
              log.debug("watch record end fail: internal error")
              complete(CommonRsp(1000040, s"$e"))
            }
          }else {
            StatisticDao.updateObserveEvent(req.recordId, req.userIdOpt.get, false, req.inTime, req.outTime).map { rst =>
              complete(CommonRsp())
            }.recover { case e: Exception =>
              log.debug("watch record end fail: internal error")
              complete(CommonRsp(1000040, s"$e"))
            }
          }
        }
      case Left(e) =>
        log.debug("watch record end fail: parse error")
        complete(CommonRsp(1000041, s"$e"))
    }
  }

  val getLoginData = (path("getLoginData") & get) {
    val zero = TimeUtil.todayBegin()
    val yesterday = zero - 24 * 60 * 60 * 1000l
    val aWeekAgo = zero - 7 * 24 * 60 * 60 * 1000l
    val aMonthAgo = zero - 30 * 24 * 60 * 60 * 1000l
    dealFutureResult{
      val res = for{
        loginYesterday <- StatisticDao.getLoginInDataByTime(yesterday, zero)
        loginWeek <- StatisticDao.getLoginInDataByTime(aWeekAgo, zero)
        loginMonth <- StatisticDao.getLoginInDataByTime(aMonthAgo, zero)
      }yield{
        val resultList = List(LoginInDataInfo(1, yesterday, loginYesterday.map(_.uid).toSet.size, loginYesterday.length),
          LoginInDataInfo(7, aWeekAgo, loginWeek.map(_.uid).toSet.size, loginWeek.length),
          LoginInDataInfo(30, aMonthAgo, loginMonth.map(_.uid).toSet.size, loginMonth.length))
        complete(LoginInDataRsp(resultList))
      }
      res.recover{ case e: Exception =>
        log.debug(s"get loginData error: recover error: $e")
        complete(CommonRsp(2000002, "get login data error"))
      }
    }
  }

  val loginDataByDay = (path("loginDataByDay") & post){
    entity(as[Either[Error,LoginInDataByDayReq]]){
      case Right(req) =>
        dealFutureResult {
          val timeSpanList = TimeUtil.getSplitTimeSpanByDay(req.startTime, req.endTime)
          StatisticDao.getLoginInDataByTimeList(timeSpanList).map { r =>
            val data = r.map { w =>
              LoginInDataInfo(0, w._2, w._1.map(_.uid).toSet.size, w._1.length)
            }
            complete(LoginInDataRsp(data))
          }.recover{ case e: Exception =>
            log.debug(s"获取登陆的统计信息失败：${e}")
            complete(CommonRsp(2000002, s"获取登陆的统计信息失败：${e}"))
          }
        }
      case Left(error) =>
        log.debug(s"获取登陆的统计信息失败：${error}")
        complete(CommonRsp(100056,s"获取登陆的统计信息失败：${error}"))

    }
  }

  val loginDataByHour = (path("loginDataByHour") & post){
    entity(as[Either[Error,LoginInDataByHourReq]]){
      case Right(req) =>
        dealFutureResult {
          val timeSpanList = TimeUtil.getSplitTimeSpanByHour(req.startTime, req.endTime)
          StatisticDao.getLoginInDataByTimeList(timeSpanList).map { r =>
            val data = r.map { w =>
              LoginInDataInfo(0, w._2, w._1.map(_.uid).toSet.size, w._1.length)
            }
            complete(LoginInDataRsp(data))
          }.recover{ case e: Exception =>
            log.debug(s"获取登陆的统计信息失败：${e}")
            complete(CommonRsp(2000002, s"获取登陆的统计信息失败：${e}"))
          }
        }
      case Left(error) =>
        log.debug(s"获取登陆的统计信息失败：${error}")
        complete(CommonRsp(100056,s"获取登陆的统计信息失败：${error}"))

    }
  }

  val getRecordDataByAdmin = (path("getRecordDataByAdmin") & post){
    entity(as[Either[Error,AdminRecordInfoReq]]){
      case Right(req) =>
        dealFutureResult{
          val res = for {
            v <- StatisticDao.AdminGetRecordAll(req.sortBy, req.pageNum, req.pageSize)._1
            q <- StatisticDao.AdminGetRecordAll(req.sortBy, req.pageNum, req.pageSize)._2
          }yield {
            if (v.nonEmpty) {
              val rsp = v.map{
                case (a, b) =>
                  AdminRecordInfo(
                    a.id,
                    a.roomid,
                    a.recordName,
                    a.recordDes,
                    b.uid,
                    b.userName,
                    a.startTime,
                    b.headImg,
                    a.coverImg,
                    a.viewNum,
                    a.likeNum,
                    a.duration,
                    q.filter(t => !t._2.temporary && a.id == t._2.recordid).map(_._2.uid).toSet.size,
                    q.count(t => !t._2.temporary && a.id == t._2.recordid),
                    q.count(t => t._2.temporary && a.id == t._2.recordid)
                  )
                }.toList
              complete(getRecordDataByAdminRsp(rsp))
            }
            else
              complete(getRecordDataByAdminRsp())
          }
          res.recover{ case e: Exception =>
            log.debug(s"get observeData error: recover error: $e")
            complete(CommonRsp(2000004, "get watch data error"))
          }
        }
      case Left(e) =>
        log.debug(s"获取录像的统计信息失败：${e}")
        complete(CommonRsp(2000003,s"获取录像的统计信息失败：${e}"))

    }
  }

  val getObserveDataById = (path("watchObserve") & post) {
    entity(as[Either[Error,WatchProfileDataByRecordIdReq]]){
      case Right(req) =>
        dealFutureResult{
          val res = for {
            v <- StatisticDao.getObserveDataByRid(req.recordId)
            q <- RecordDao.searchRecordById(req.recordId)
            p <- StatisticDao.getObserveTimeByRid(req.recordId)
          }yield {
            if (v.nonEmpty) {
              val rsp = WatchProfileInfo(p.getOrElse(0l) / 1000, v.filter(!_.temporary).map(_.uid).toSet.size,v.count(!_.temporary),v.count(_.temporary))
              val roomId = q.map(_.roomid).getOrElse(-1l)
              val startTime = q.map(_.startTime).getOrElse(-1l)
              val url = s"https://${AppSettings.distributorDomain}/VideoMeeting/distributor/getRecord/$roomId/$startTime/record.mp4"
              complete(WatchProfileDataByRecordIdRsp(url, Some(rsp)))
            }
            else
              complete(WatchProfileDataByRecordIdRsp())
          }
          res.recover{ case e: Exception =>
            log.debug(s"get observeData error: recover error: $e")
            complete(CommonRsp(2000004, "get watch data error"))
          }
        }
      case Left(e) =>
        log.debug(s"获取录像的统计信息失败：${e}")
        complete(CommonRsp(2000003,s"获取录像的统计信息失败：${e}"))

    }
  }

  val getObserveDataByHour = (path("watchObserveByHour") & post){
    entity(as[Either[Error,WatchDataByHourReq]]){
      case Right(req) =>
        dealFutureResult{
          val timeSpanList = TimeUtil.getSplitTimeSpanByHour(req.startTime, req.endTime)
          StatisticDao.getObserveDataByTimeList(req.recordId,timeSpanList).map { r =>
            val data = r.map { w =>
              WatchDataByHourInfo(w._2, w._1.filter(!_.temporary).map(_.uid).toSet.size, w._1.count(!_.temporary),w._1.count(_.temporary))
            }
            complete(WatchDataByHourRsp(data))
          }.recover{ case e: Exception =>
            log.debug(s"获取录像的统计信息失败：${e}")
            complete(CommonRsp(2000006, s"获取录像的统计信息失败：${e}"))
          }
        }
      case Left(e) =>
        log.debug(s"获取录像的统计信息失败：${e}")
        complete(CommonRsp(2000005,s"获取录像的统计信息失败：${e}"))

    }
  }

  val getObserveDataByDay = (path("watchObserveByDay") & post){
    entity(as[Either[Error,WatchDataByDayReq]]){
      case Right(req) =>
        dealFutureResult{
          val timeSpanList = TimeUtil.getSplitTimeSpanByDay(req.startTime, req.endTime)
          StatisticDao.getObserveDataByTimeList(req.recordId,timeSpanList).map { r =>
            val data = r.map { w =>
              WatchDataByHourInfo(w._2, w._1.filter(!_.temporary).map(_.uid).toSet.size, w._1.count(!_.temporary),w._1.count(_.temporary))
            }
            complete(WatchDataByHourRsp(data))
          }.recover{ case e: Exception =>
            log.debug(s"获取录像的统计信息失败：${e}")
            complete(CommonRsp(2000006, s"获取录像的统计信息失败：${e}"))
          }
        }
      case Left(e) =>
        log.debug(s"获取录像的统计信息失败：${e}")
        complete(CommonRsp(2000005,s"获取录像的统计信息失败：${e}"))

    }
  }

  val statistic = pathPrefix("statistic") {
    watchRecordEnd ~ getLoginData ~ loginDataByDay ~ loginDataByHour ~ getRecordDataByAdmin ~ getObserveDataById ~
    getObserveDataByHour ~ getObserveDataByDay
  }

}

