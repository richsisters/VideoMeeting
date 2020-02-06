package org.seekloud.VideoMeeting.roomManager.http

import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.roomManager.Boot._
import org.seekloud.VideoMeeting.roomManager.core.UserManager.{log => _, _}

import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.seekloud.VideoMeeting.roomManager.Boot.{executor, roomManager, scheduler, userManager}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Flow
import org.seekloud.VideoMeeting.protocol.ptcl.{CommonRsp, Response}
import org.seekloud.VideoMeeting.roomManager.core.RegisterActor.{ConfirmEmail, SendEmail}
import org.seekloud.VideoMeeting.roomManager.core.RoomManager
import org.seekloud.VideoMeeting.roomManager.models.dao.{StatisticDao, RecordDao, UserInfoDao}
import org.seekloud.VideoMeeting.roomManager.utils.HestiaClient
import akka.http.scaladsl.model.headers._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RoomInfo, UserInfo}
import org.seekloud.VideoMeeting.roomManager.common.{AppSettings, Common}
import org.seekloud.VideoMeeting.roomManager.core.RoomManager.{GetRoomList, UserInfoChange}
import org.seekloud.VideoMeeting.roomManager.http.SessionBase.UserSession
import org.seekloud.VideoMeeting.roomManager.utils.SecureUtil

import scala.concurrent.Future

trait UserService extends ServiceUtils {
  import io.circe._
  import io.circe.syntax._
  import io.circe.generic.auto._

  private val tokenExistTime = AppSettings.tokenExistTime * 1000L // seconds

  private val signUp = (path("signUp") & post) {
    entity(as[Either[Error, SignUp]]) {
      case Right(data) =>
        //TODO: 正则表达式有点问题
        val emailReg = "^([a-z0-9A-Z]+[-|\\.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$"
        data.email.matches(emailReg) match {
          case true =>
            val code = SecureUtil.nonceStr(20)
            dealFutureResult {
              UserInfoDao.checkEmail(data.email).map {
                case Some(_) =>
                  complete(CommonRsp(180002, "邮箱已注册"))
                case None =>
                  dealFutureResult {
                    UserInfoDao.searchByName(data.userName).map {
                      case Some(_) =>
                        complete(CommonRsp(180010, "用户名已注册"))
                      case None =>
                        val signFutureRsp: Future[SignUpRsp] = registerManager ? (SendEmail(code, data.url, data.email, data.userName, data.password, _))
                        dealFutureResult {
                          signFutureRsp.map {
                            rsp =>
                              complete(rsp)
                          }
                        }
                    }
                  }
              }
            }
          case false =>
            complete(CommonRsp(180001, "邮箱地址不合法"))

        }
      case Left(error) =>
        complete(CommonRsp(200001, s"error :${error}"))
    }
  }

  private val confirmEmail = (path("confirmEmail") & get & pathEndOrSingleSlash) { //收到用户点击确认链接
    parameter(
      'email.as[String],
      'code.as[String]
    ) { case (email, code) =>
      log.info(s"receive confirmEmail:$email, $code")
      val rstF: Future[Response] = registerManager ? (ConfirmEmail(code, email, _))
      dealFutureResult {
        rstF.map {
          case RegisterSuccessRsp(url, _, _) =>
            log.info(s"注册成功！")
            if (url == "") {
              complete(CommonRsp())
            } else {
              //println("返回重定向url")
              redirect(url, StatusCodes.SeeOther)
            }
          case rsp@CommonRsp(errCode, msg) =>
            complete(rsp)
        }
      }
    }
  }

  private val signIn = (path("signIn") & post) {
    entity(as[Either[Error, SignIn]]) {
      case Right(data) =>
        dealFutureResult {
          UserInfoDao.searchByName(data.userName).map {
            case Some(rst) =>
              if (rst.password != SecureUtil.getPoorSecurePassword(data.password, rst.email)) {
                log.error(s"login error: wrong pw")
                complete(WrongPwError)
              }
              else if (rst.tokenCreateTime + tokenExistTime < System.currentTimeMillis()) {
                log.debug("update token")
                val token = SecureUtil.nonceStr(40)
                UserInfoDao.updateToken(rst.uid, token, System.currentTimeMillis())
                val userInfo = UserInfo(rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg, token, tokenExistTime)
                val roomInfo = RoomInfo(rst.roomid, s"${rst.userName}的会议室", "", Common.DefaultImg.coverImg, rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg)
                StatisticDao.addLoginEvent(userInfo.userId, System.currentTimeMillis())
                val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                addSession(session) {
                  log.info(s"${rst.uid} login success")
                  complete(SignInRsp(Some(userInfo), Some(roomInfo)))
                }
              }
              else {
                val userInfo = UserInfo(rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg, rst.token, tokenExistTime)
                val roomInfo = RoomInfo(rst.roomid, s"${rst.userName}的会议室", "", Common.DefaultImg.coverImg, rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg)
                StatisticDao.addLoginEvent(userInfo.userId, System.currentTimeMillis())
                val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                addSession(session) {
                  log.info(s"${rst.uid} login success")
                  complete(SignInRsp(Some(userInfo), Some(roomInfo)))
                }
              }
            case None =>
              log.error(s"login error: no user")
              complete(NoUserError)
          }
        }
      case Left(error) =>
        complete(SignInRsp(None, None, 200002, s"error :${error}"))
    }
  }

  private val signInByMail = (path("signInByMail") & post) {
    entity(as[Either[Error, SignInByMail]]) {
      case Right(data) =>
        dealFutureResult {
          UserInfoDao.checkEmail(data.email).map {
            case Some(rst) =>
              if (rst.password != SecureUtil.getPoorSecurePassword(data.password, rst.email)) {
                log.error(s"login error: wrong pw")
                complete(WrongPwError)
              }
              else if (rst.tokenCreateTime + tokenExistTime < System.currentTimeMillis()) {
                log.debug("update token")
                val token = SecureUtil.nonceStr(40)
                UserInfoDao.updateToken(rst.uid, token, System.currentTimeMillis())
                val userInfo = UserInfo(rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg, token, tokenExistTime)
                val roomInfo = RoomInfo(rst.roomid, s"${rst.userName}的会议室", "", Common.DefaultImg.coverImg, rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg)
                StatisticDao.addLoginEvent(userInfo.userId, System.currentTimeMillis())
                val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                addSession(session) {
                  log.info(s"${rst.uid} login success")
                  complete(SignInRsp(Some(userInfo), Some(roomInfo)))
                }
              }
              else {
                log.info(s"${rst.uid} login success")
                val userInfo = UserInfo(rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg, rst.token, tokenExistTime)
                val roomInfo = RoomInfo(rst.roomid, s"room:${rst.roomid}", "", Common.DefaultImg.coverImg, rst.uid, rst.userName, if (rst.headImg == "") Common.DefaultImg.headImg else rst.headImg)
                StatisticDao.addLoginEvent(userInfo.userId, System.currentTimeMillis())
                val session = UserSession(rst.uid.toString, rst.userName, System.currentTimeMillis().toString).toSessionMap
                addSession(session) {
                  log.info(s"${rst.uid} login success")
                  complete(SignInRsp(Some(userInfo), Some(roomInfo)))
                }
              }
            case None =>
              log.error(s"login error: no user")
              complete(NoUserError)
          }
        }
      case Left(error) =>
        complete(SignInRsp(None, None, 200002, s"error :${error}"))
    }
  }

  private val setupWebSocket = (path("setupWebSocket") & get) {
    parameter(
      'userId.as[Long],
      'token.as[String],
      'roomId.as[Long]
    ) { (uid, token, roomId) =>
      val setWsFutureRsp: Future[Option[Flow[Message, Message, Any]]] = userManager ? (SetupWs(uid, token, roomId, _))
      dealFutureResult(
        setWsFutureRsp.map {
          case Some(rsp) => handleWebSocketMessages(rsp)
          case None =>
            log.debug(s"建立websocket失败，userId=$uid,roomId=$roomId,token=$token")
            complete("setup error")
        }
      )

    }
  }


  private val getRoomList = (path("getRoomList") & get) {

    val roomListFutureRsp: Future[RoomListRsp] = roomManager ? (GetRoomList(_))
    dealFutureResult(
      roomListFutureRsp.map(rsp => complete(rsp))
    )
  }


  private val searchRoom = (path("searchRoom") & post) {
    entity(as[Either[Error, SearchRoomReq]]) {
      case Right(rsp) =>
        if (rsp.roomId < 0) {
          complete(SearchRoomError4RoomId)
        } else {
          val searchRoomFutureRsp: Future[SearchRoomRsp] = roomManager ? (RoomManager.SearchRoom(rsp.userId, rsp.roomId, _))
          dealFutureResult(
            searchRoomFutureRsp.map(rsp => complete(rsp))
          )
        }

      case Left(error) =>
        log.debug(s"search room 接口请求错误,error=$error")
        println(error)
        complete(SearchRoomRsp(None, 100005, msg = s"接口请求错误，error:$error"))
    }
  }

  private val nickNameChange = (path("nickNameChange") & get) {
    //    authUser { _ =>
    parameter('userId.as[Long], 'newName.as[String]) {
      (userId, newName) =>
        dealFutureResult {
          UserInfoDao.searchById(userId).map {
            case Some(_) =>
              dealFutureResult {
                UserInfoDao.searchByName(newName).map {
                  case Some(_) =>
                    complete(CommonRsp(1000051, "用户名已被注册"))
                  case None =>
                    dealFutureResult {
                      UserInfoDao.updateName(userId, newName).map { rst =>
                        roomManager ! UserInfoChange(userId, false)
                        complete(CommonRsp(0, "ok"))
                      }
                    }
                }
              }
            case None =>
              complete(CommonRsp(1000050, "user not exist"))
          }
        }
    }
    //    }
  }

  /** 临时用户申请userId和token接口 */
  private val temporaryUser = (path("temporaryUser") & get) {
    val rspFuture: Future[GetTemporaryUserRsp] = userManager ? (TemporaryUser(_))
    dealFutureResult(rspFuture.map(complete(_)))
  }

  case class DeleteUser(email: String)

  private val deleteUserByEmail = (path("deleteUser") & post) {
    entity(as[Either[Error, DeleteUser]]) {
      case Right(req) =>
        dealFutureResult(UserInfoDao.deleteUserByEmail(req.email, "").map(_ => complete(CommonRsp())))
      case Left(error) =>
        complete(CommonRsp(100034, s"decode error:$error"))
    }
  }

  private val getRoomInfo = (path("getRoomInfo") & post) {
    entity(as[Either[Error, GetRoomInfoReq]]) {
      case Right(req) =>
        dealFutureResult {
          for {
            verify <- UserInfoDao.verifyUserWithToken(req.userId, req.token)
          } yield {
            if (verify) {
              dealFutureResult {
                UserInfoDao.searchById(req.userId).map { r =>
                  val rsp = r.get
                  complete(RoomInfoRsp(Some(RoomInfo(rsp.roomid, s"room:${rsp.roomid}", "", Common.DefaultImg.coverImg, rsp.uid, rsp.userName, if (rsp.headImg == "") Common.DefaultImg.headImg else rsp.headImg))))
                }
              }
            } else {
              complete(CommonRsp(100046, s"userId和token验证失败"))
            }
          }
        }

      case Left(error) =>
        log.debug(s"获取房间信息失败，解码失败，error:$error")
        complete(CommonRsp(100045, s"decode error:$error"))
    }
  }

  val userRoutes: Route = pathPrefix("user") {
    signUp ~ signIn ~ confirmEmail ~ deleteUserByEmail ~
    nickNameChange ~ getRoomList ~ searchRoom ~ setupWebSocket ~ temporaryUser ~ signInByMail ~ getRoomInfo
  }
}
