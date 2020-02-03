package org.seekloud.VideoMeeting.roomManager.http

import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.roomManager.Boot._

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.RecordInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.AdminProtocol.DeleteRecordReq
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.StatisticsProtocol
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, scheduler, userManager}
import org.seekloud.VideoMeeting.roomManager.models.dao.{RecordDao, StatisticDao, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}

trait RecordService {
  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._

  private val getRecordList = (path("getRecordList") & get) {
    parameters(
      'sortBy.as[String],
      'pageNum.as[Int],
      'pageSize.as[Int]
    ) { case (sortBy, pageNum, pageSize) =>
      dealFutureResult {
        RecordDao.getRecordAll(sortBy, pageNum, pageSize).flatMap { recordList =>
          RecordDao.getTotalNum.map { num =>
            complete(GetRecordListRsp(num, recordList))
          }
        }.recover {
          case e: Exception =>
            log.debug(s"获取录像列表失败：$e")
            complete(GetRecordListRsp(0, Nil))
        }
      }
    }
  }

  private val searchRecord = (path("searchRecord") & post) {
    entity(as[Either[Error, SearchRecord]]) {
      case Right(req) =>
        dealFutureResult {
          RecordDao.searchRecord(req.roomId, req.startTime).map {
            case Some(recordInfo) =>
              dealFutureResult {
                StatisticDao.addObserveEvent(if (req.userIdOpt.nonEmpty) req.userIdOpt.get else 1l, recordInfo.recordId, false, req.userIdOpt.isEmpty, req.inTime).map { r =>
                  RecordDao.updateViewNum(req.roomId, req.startTime, recordInfo.observeNum + 1)
                  val url = s"https://${AppSettings.distributorDomain}/VideoMeeting/distributor/getRecord/${req.roomId}/${req.startTime}/record.mp4"
                  complete(SearchRecordRsp(url, recordInfo))

                }
              }

            case None =>
              complete(CommonRsp(100070, s"没有该录像"))
          }
        }
      case Left(e) =>
        complete(CommonRsp(100070, s"parse error:$e"))
    }
  }

  private val watchRecordOver = (path("watchRecordOver") & post) {
    entity(as[Either[Error, StatisticsProtocol.WatchRecordEndReq]]) {
      case Right(req) =>
        dealFutureResult {
          StatisticDao.updateObserveEvent(req.recordId, if (req.userIdOpt.nonEmpty) req.userIdOpt.get else 1l, req.userIdOpt.isEmpty, req.inTime, req.outTime).map { r =>
            complete(CommonRsp())
          }.recover {
            case e: Exception =>
              complete(CommonRsp(100046, s"数据库查询错误error=$e"))
          }

        }
      case Left(error) =>
        complete(CommonRsp(100045, s"watch over error decode error:$error"))

    }
  }

  private val getAuthorRecordList = (path("getAuthorRecordList") & get) {
    parameters(
      'roomId.as[Long],
    ) { case roomId =>
      dealFutureResult {
        RecordDao.getAuthorRecordList(roomId).flatMap { recordList =>
          log.info("获取主播录像列表成功")
          RecordDao.getAuthorRecordTotalNum(roomId).map { n =>
            complete(GetAuthorRecordListRsp(n, recordList))
          }
        }.recover {
          case e: Exception =>
            log.debug(s"获取录像列表失败：$e")
            complete(GetAuthorRecordListRsp(0, Nil))
        }
      }
    }
  }

  private val deleteRecord = (path("deleteRecord") & post) {
    entity(as[Either[Error, AuthorDeleteRecordReq]]) {
      case Right(req) =>
        dealFutureResult {
          RecordDao.deleteAuthorRecord(req.recordId).map { r =>
            log.info("主播删除录像成功")
            complete(CommonRsp())
          }.recover {
            case e: Exception =>
              complete(CommonRsp(100048, s"主播删除录像id失败，error:$e"))
          }
        }
      case Left(e) => complete(CommonRsp(100048, s"delete author record error: $e"))
    }
  }

  private val addRecordAddr = (path("addRecordAddr") & post) {
    entity(as[Either[Error, AddRecordAddrReq]]) {
      case Right(req) =>
        dealFutureResult {
          RecordDao.addRecordAddr(req.recordId, req.recordAddr).map{r =>
            if(r == 1){
              log.info("添加录像地址成功")
              complete(CommonRsp())
            } else {
              log.info("添加录像地址失败")
              complete(CommonRsp(100049, s"add record failed"))
            }
          }
        }
      case Left(e) =>
        complete(CommonRsp(100050, s"add record req error: $e"))
    }
  }


  val recordRoutes: Route = pathPrefix("record") {
    getRecordList ~ searchRecord ~ watchRecordOver ~ getAuthorRecordList ~ deleteRecord ~ addRecordAddr
  }
}
