package org.seekloud.VideoMeeting.roomManager.http

import org.seekloud.VideoMeeting.roomManager.Boot._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.AdminProtocol
import org.seekloud.VideoMeeting.roomManager.common.AppSettings
import org.seekloud.VideoMeeting.roomManager.core.UserManager
import org.seekloud.VideoMeeting.roomManager.http.SessionBase.{AdminInfo, AdminSession}
import org.seekloud.VideoMeeting.roomManager.models.dao.{AdminDAO, RecordDao, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.utils.DistributorClient
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.UserInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.roomManager.protocol.ActorProtocol

import scala.concurrent.Future
/**
  * created by benyafang on 2019/9/23 13:12
  * */
trait AdminService extends ServiceUtils with SessionBase{

  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._

  private def adminSignInErrorRsp(msg:String) = CommonRsp(100034,msg)
  private val adminSignIn = (path("adminSignIn") & post){
    entity(as[Either[Error,AdminProtocol.AdminSignIn]]){
      case Right(req) =>
        if(req.admin == AppSettings.adminAccount && req.password == AppSettings.adminPassword){
          addSession(
            AdminSession(AdminInfo(req.admin,req.password),System.currentTimeMillis()).toAdminSessionMap
          ){
            complete(CommonRsp())
          }
        }else{
          complete(adminSignInErrorRsp(s"管理员账号错误"))
        }
      case Left(error) =>
        complete(adminSignInErrorRsp(s"管理员登陆错误，接口错误error=$error"))

    }
  }

  private def deleteRecordErrorRsp(msg:String) = CommonRsp(100034,msg)
  private val deleteRecord = (path("deleteRecord") & post){
    AdminAction{ _ =>
      entity(as[Either[Error,AdminProtocol.DeleteRecordReq]]) {
        case Right(req) =>
          dealFutureResult {
            RecordDao.searchRecordById(req.recordIdList).flatMap { r =>
              DistributorClient.deleteRecord(r).map {
                case Right(data) =>
                  dealFutureResult {
                    RecordDao.deleteRecordById(req.recordIdList).map { t =>
                      complete(CommonRsp())
                    }.recover {
                      case e: Exception =>
                        complete(deleteRecordErrorRsp(s"删除录像id失败，error:$e"))
                    }
                  }
                case Left(error) =>
                  complete(deleteRecordErrorRsp(s"请求distributor删除录像失败，error:$error"))
              }
            }.recover {
              case e: Exception =>
                complete(deleteRecordErrorRsp(s"删除录像id失败，error:$e"))
            }
          }
        case Left(error) =>
          complete(deleteRecordErrorRsp(s"录像删除失败，接口请求错误，error:$error"))
      }
    }
  }

  private def sealAccountErrorRsp(msg:String) = CommonRsp(100034,msg)

  private val sealAccount = (path("sealAccount") & post){
    AdminAction { _ =>
      entity(as[Either[Error, AdminProtocol.SealAccountReq]]) {
        case Right(req) =>
//          if (req.sealedUtilTime < System.currentTimeMillis()) {
//            complete(sealAccountErrorRsp("封号失败，截止时间不得小于系统当前时间"))
//          } else {
            val sealFuture: Future[CommonRsp] = userManager ? (UserManager.SealUserInfo(req.userId, req.sealedUtilTime, _))
            dealFutureResult {
              sealFuture.map(complete(_))
            }
//          }

        case Left(error) =>
          complete(sealAccountErrorRsp(s"封号失败，接口请求错误，error:$error"))

      }
    }
  }

  private def cancelSealAccountErrorRsp(msg:String) = CommonRsp(100034,msg)

  private val cancelSealAccount = (path("cancelSealAccount") & post){
    AdminAction{ _ =>
      entity(as[Either[Error,AdminProtocol.CancelSealAccountReq]]){
        case Right(req) =>
          val sealFuture:Future[CommonRsp] = userManager ? (UserManager.CancelSealUserInfo(req.userId,_))
          dealFutureResult{sealFuture.map(complete(_))}

        case Left(error) =>
         complete(cancelSealAccountErrorRsp(s"取消封号失败，接口请求错误，error:$error"))

      }
    }

  }

  private def getUserListErrorRsp(msg:String) = CommonRsp(100034,msg)
  private val getUserList = (path("getUserList") & post){
    AdminAction{ _ =>
      entity(as[Either[Error,AdminProtocol.GetUserListReq]]){
        case Right(req) =>
          dealFutureResult{
            AdminDAO.getUserList(req.pageNum,req.pageSize).map{r =>
              complete(AdminProtocol.GetUserListRsp(r._1,r._2.map{w => UserInfo(w.uid,w.userName,UserInfoDao.getHeadImg(w.headImg),w.token,w.tokenCreateTime,w.`sealed`)}.toList))
            }.recover{
              case e:Exception =>
                log.debug(s"获取用户列表失败，recover error:$e")
                complete(getUserListErrorRsp(s"获取用户列表失败，recover error:$e"))
            }
          }
        case Left(error) =>
          complete(getUserListErrorRsp(s"获取用户列表失败，接口请求错误，error:$error"))
      }
    }
  }

//  private def banOnAnchorErrorRsp(msg:String) = CommonRsp(100054,msg)
//
//  private val banOnAnchor = (path("banOnAnchor") & post){
//    AdminAction{ _ =>
//      entity(as[Either[Error,AdminProtocol.BanOnAnchor]]){
//        case Right(req) =>
//          roomManager ! ActorProtocol.BanOnAnchor(req.roomId)
//          dealFutureResult{
//            UserInfoDao.searchByRoomId(req.roomId).map{r =>
//              if(r.nonEmpty){
//                dealFutureResult{
//                  AdminDAO.updateAllowAnchor(r.head.uid,false).map{w =>
//                    complete(CommonRsp())
//                  }.recover{
//                    case e:Exception =>
//                      log.debug(s"禁播失败，error=$e")
//                      complete(banOnAnchorErrorRsp(s"禁播失败，error=$e"))
//                  }
//                }
//              }else{
//                complete(banOnAnchorErrorRsp(s"该直播间不存在"))
//              }
//
//            }.recover{
//              case e:Exception =>
//                log.debug(s"禁播失败，error=$e")
//                complete(banOnAnchorErrorRsp(s"禁播失败，error=$e"))
//            }
//          }
//
//        case Left(error) =>
//          log.debug(s"禁播失败，解析错误error=$error")
//          complete(banOnAnchorErrorRsp(s"禁播失败，解析错误error=$error"))
//
//      }
//    }
//  }
//
//  private val cancelBanOnAnchor = (path("cancelBanOnAnchor") & post){
//    AdminAction{ _ =>
//      entity(as[Either[Error,AdminProtocol.BanOnAnchor]]){
//        case Right(req) =>
//          complete(CommonRsp())
//
//        case Left(error) =>
//          log.debug(s"禁播失败，解析错误error=$error")
//          complete(CommonRsp())
//
//      }
//    }
//  }



  val admin = pathPrefix("admin"){
    adminSignIn ~ deleteRecord ~ sealAccount ~ cancelSealAccount ~ getUserList
  }

}
