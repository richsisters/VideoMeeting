package org.seekloud.VideoMeeting.roomManager.http

import akka.http.scaladsl.server.Directives._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.GetLiveInfoRsp4RM

import scala.concurrent.Future
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, timeout, roomManager, scheduler}
import org.seekloud.VideoMeeting.protocol.ptcl.rtmp2Manager.RtmpProtocol
import org.seekloud.VideoMeeting.roomManager.core.RoomManager.GetRtmpLiveInfo
import org.seekloud.VideoMeeting.roomManager.models.dao.UserInfoDao
import org.seekloud.VideoMeeting.protocol.ptcl.Response
import org.seekloud.VideoMeeting.roomManager.utils.RtpClient
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil.nonceStr
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}
/**
  *
  * created by benyafang on 2019.8.30
  * */
trait RtmpService extends ServiceUtils{
  import io.circe._
  import io.circe.generic.auto._

  private val log = LoggerFactory.getLogger(this.getClass)

  private def getTokenErrorRsp(msg:String) = CommonRsp(100038,msg)
  private val getToken = (path("getToken") & post){

      entity(as[Either[Error, RtmpProtocol.GetTokenReq]]) {
        case Right(req) =>
          dealFutureResult {
            UserInfoDao.searchById(req.userId).map { r =>
              if (r.nonEmpty) {
                if (r.get.rtmpToken != "") {
                  if(r.get.`sealed`){
                    complete(getTokenErrorRsp(s"该用户已经被封号哦"))
                  }else{
                    complete(RtmpProtocol.GetTokenRsp(Some(r.get.rtmpToken), Some(nonceStr(40))))
                  }

                } else {
                  dealFutureResult {
                    UserInfoDao.updateRtmpToken(req.userId).map { t =>
                      if (t.nonEmpty) {
                        log.debug(s"rtmp获取用户token成功")
                        if(r.get.`sealed`){
                          complete(getTokenErrorRsp(s"该用户已经被封号哦"))
                        }else{
                          complete(RtmpProtocol.GetTokenRsp(Some(r.get.rtmpToken), Some(nonceStr(40))))
                        }
                      } else {
                        log.debug(s"rtmp获取用户token失败,数据库更新失败")
                        complete(CommonRsp(errCode = 100037, s"rtmp获取用户token失败,数据库更新失败"))
                      }
                    }.recover {
                      case e: Exception =>
                        log.debug(s"rtmp获取用户token失败,数据库更新失败$e")
                        complete(CommonRsp(errCode = 100038, s"rtmp获取用户token失败,数据库更新失败$e"))
                    }
                  }

                }
              } else {
                complete(CommonRsp(errCode = 100034, "该用户不存在"))
              }

            }.recover {
              case e: Exception =>
                log.debug(s"获取token失败，inter error：${e}")
                complete(CommonRsp(errCode = 100036, s"获取token失败，inter error：${e}"))
            }
          }
        case Left(error) =>
          complete(CommonRsp(100035, s"decode error:$error"))
      }

  }

  private val getLiveInfo = (path("getLiveInfo") & post){

      entity(as[Either[Error, RtmpProtocol.GetLiveInfoReq]]) {
        case Right(req) =>
          dealFutureResult {
            UserInfoDao.checkRtmpToken(req.userId, req.token).map { r =>
              if (r.nonEmpty) {
                val FutureLiveInfo: Future[GetLiveInfoRsp4RM] = roomManager ? (GetRtmpLiveInfo(r.get.roomid, _))
                dealFutureResult(
                  FutureLiveInfo.map(rsp => complete(rsp))
                )
              } else {
                log.debug(s"获取live info失败,用户不存在或者token错误")
                complete(CommonRsp(1000043, s"获取live info失败,用户不存在或者token错误"))
              }

            }.recover {
              case e: Exception =>
                log.debug(s"获取live info 请求失败，recover error:$e")
                complete(CommonRsp(100042, s"获取live info 请求失败，recover error:$e"))
            }
          }

        case Left(error) =>
          log.debug(s"获取live info 请求失败，decode error:$error")
          complete(CommonRsp(1000044, s"获取live info 请求失败，decode error:$error"))
      }

  }

  private val updateToken = (path("updateToken") & post){
    authUser { _ =>
      entity(as[Either[Error, RtmpProtocol.UpdateTokenReq]]) {
        case Right(req) =>
          dealFutureResult {
            UserInfoDao.updateRtmpToken(req.userId).map { r =>
              if (r.nonEmpty) {
                complete(CommonRsp())
              } else {
                log.debug(s"更新rtmp token失败")
                complete(CommonRsp(1000051, s"更新rtmp token失败"))
              }

            }.recover {
              case e: Exception =>
                log.debug(s"更新rtmp token失败,recover error:$e")
                complete(CommonRsp(1000052, s"更新rtmp token失败,recover error:$e"))
            }
          }
        case Left(error) =>
          log.debug(s"更新rtmp token失败,left error:$error")
          complete(CommonRsp(1000053, s"更新rtmp token失败,left error:$error"))
      }
    }
  }

  val rtmp = pathPrefix("rtmp"){
    getToken ~ getLiveInfo ~ updateToken
  }


}
