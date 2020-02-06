package org.seekloud.VideoMeeting.pcClient.controller

import java.io._

import akka.actor.typed.ActorRef
import org.seekloud.VideoMeeting.pcClient.Boot
import org.seekloud.VideoMeeting.pcClient.common.{Constants, StageContext}
import org.seekloud.VideoMeeting.pcClient.core.RmManager
import org.seekloud.VideoMeeting.pcClient.scene.HomeScene
import org.seekloud.VideoMeeting.pcClient.scene.HomeScene.HomeSceneListener
import org.seekloud.VideoMeeting.pcClient.utils.RMClient
import org.seekloud.VideoMeeting.pcClient.Boot.executor
import org.seekloud.VideoMeeting.pcClient.component.WarningDialog
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RoomInfo, UserInfo}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: Arrow
  * Date: 2019/7/16
  * Time: 12:32
  */
class HomeController(
  context: StageContext,
  homeScene: HomeScene,
  loginController: LoginController,
  editController: EditController,
  rmManager: ActorRef[RmManager.RmCommand]) {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  var hasWaitingGif = false


  homeScene.setListener(new HomeSceneListener {
    override def liveCheck(): Unit = {
      if (RmManager.userInfo.nonEmpty && RmManager.roomInfo.nonEmpty) {
        rmManager ! RmManager.GoToLive
      } else {
        gotoLoginDialog(isToLive = true)
      }
    }

    override def gotoRoomPage(): Unit = {
      rmManager ! RmManager.GoToRoomHall
    }

    override def gotoLoginDialog(
      userName: Option[String] = None,
      pwd: Option[String] = None,
      isToLive: Boolean,
      isToWatch: Boolean
    ): Unit = {
      // 弹出登陆窗口
      val userInfo = loginController.loginDialog()
      if (userInfo.nonEmpty) {
        loginBySelf(userInfo, isToLive, isToWatch)
      }
    }

    override def gotoRegisterDialog(): Unit = {
      //弹出注册窗口
      val signUpInfo = loginController.registerDialog()
      if (signUpInfo.nonEmpty) {
        showLoading()
        Boot.addToPlatform {
          WarningDialog.initWarningDialog("邮件已发送到您的邮箱，请查收邮件完成注册！")
        }
        RMClient.signUp(signUpInfo.get._1.toString, signUpInfo.get._2.toString, signUpInfo.get._3.toString).map {
          case Right(signUpRsp) =>
            if (signUpRsp.errCode == 0) {
              removeLoading()
              Boot.addToPlatform {
                WarningDialog.initWarningDialog("注册成功！")
              }
            } else {
              log.error(s"sign up error: ${signUpRsp.msg}")
              removeLoading()
              Boot.addToPlatform {
                WarningDialog.initWarningDialog(s"${signUpRsp.msg}")
              }
            }
          case Left(error) =>
            log.error(s"sign up server error:$error")
            removeLoading()
            Boot.addToPlatform {
              WarningDialog.initWarningDialog(s"验证超时！")
            }
        }
      }
    }

    override def logout(): Unit = {
      rmManager ! RmManager.Logout
      deleteLoginTemp()

    }

//    override def editInfo(): Unit = {
//      val editInfo = editController.editDialog()
//      if (editInfo.nonEmpty) {
//        log.debug("start changeUserName...")
//        if (editInfo.get._3 != RmManager.userInfo.get.userName) {
//          RMClient.changeUserName(RmManager.userInfo.get.userId, editInfo.get._3).map {
//            case Right(rsp) =>
//              if (rsp.errCode == 0) {
//                rmManager ! RmManager.ChangeUserName(editInfo.get._3)
//                log.debug(s"changeUserName success.")
//              } else {
//                log.error(s"changeUserName error: ${rsp.msg},errCode:${rsp.errCode}")
//                Boot.addToPlatform {
//                  WarningDialog.initWarningDialog(s"${rsp.msg}")
//                }
//              }
//            case Left(error) =>
//              log.error(s"upload header server error:$error")
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"服务器出错: $error")
//              }
//          }
//        }
//        if (editInfo.get._1 != null) {
//          log.debug("start uploading header...")
//          RMClient.uploadImg(editInfo.get._1, RmManager.userInfo.get.userId, CommonInfo.ImgType.headImg).map {
//            case Right(imgChangeRsp) =>
//              if (imgChangeRsp.errCode == 0) {
//                val headerUrl = imgChangeRsp.url
//                rmManager ! RmManager.ChangeHeader(headerUrl)
//                log.debug(s"upload header success,url:$headerUrl")
//              } else {
//                log.error(s"upload header error: ${imgChangeRsp.msg},errCode:${imgChangeRsp.errCode}")
//                Boot.addToPlatform {
//                  WarningDialog.initWarningDialog(s"${imgChangeRsp.msg}")
//
//                }
//              }
//            case Left(error) =>
//              log.error(s"upload header server error:$error")
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"服务器出错: $error")
//              }
//          }
//        }
//        if (editInfo.get._2 != null) {
//          log.debug(s"start uploading cover...")
//          RMClient.uploadImg(editInfo.get._2, RmManager.userInfo.get.userId, CommonInfo.ImgType.coverImg).map {
//            case Right(imgChangeRsp) =>
//              if (imgChangeRsp.errCode == 0) {
//                val coverUrl = imgChangeRsp.url
//                rmManager ! RmManager.ChangeCover(coverUrl)
//                log.debug(s"upload cover success,url:$coverUrl")
//              } else {
//                log.error(s"upload cover error: ${imgChangeRsp.msg}")
//                Boot.addToPlatform {
//                  WarningDialog.initWarningDialog(s"${imgChangeRsp.msg}")
//
//                }
//              }
//            case Left(error) =>
//              log.error(s"upload cover server error:$error")
//              Boot.addToPlatform {
//                WarningDialog.initWarningDialog(s"服务器出错: $error")
//              }
//          }
//        }
//      }
//
//    }
  })

  def showScene(): Unit = {
    Boot.addToPlatform(
      context.switchScene(homeScene.getScene, title = "pc客户端-主页")
    )
  }

  def showLoading(): Unit = {
    Boot.addToPlatform {
      if (!hasWaitingGif) {
        homeScene.group.getChildren.add(homeScene.waitingGif)
        hasWaitingGif = true
      }
    }
  }

  def removeLoading(): Unit = {
    Boot.addToPlatform {
      if (hasWaitingGif) {
        homeScene.group.getChildren.remove(homeScene.waitingGif)
        hasWaitingGif = false
      }
    }
  }

  /**
    * 用户自己输入信息登录
    */
  def loginBySelf(userInfo: Option[(String, String, String)], isToLive: Boolean, isToWatch: Boolean): Future[Unit] = {
    showLoading()
    val r =
      if (userInfo.get._3 == "userName") {
        RMClient.signIn(userInfo.get._1, userInfo.get._2) //用户名登录
      } else {
        RMClient.signInByMail(userInfo.get._1, userInfo.get._2) //邮箱登录
      }
    r.map {
      case Right(rsp) =>
        if (rsp.errCode == 0) {
          rmManager ! RmManager.SignInSuccess(rsp.userInfo.get, rsp.roomInfo.get)
//          RmManager.userInfo = rsp.userInfo
//          RmManager.roomInfo = rsp.roomInfo
          if (isToLive) {
            rmManager ! RmManager.GoToLive
          } else {
            if (isToWatch) {
              rmManager ! RmManager.GoToRoomHall
            } else {
              Boot.addToPlatform {
                removeLoading()
                showScene()
              }
            }
          }
          deleteLoginTemp()
          createLoginTemp(userInfo.get._2, rsp.userInfo.get, rsp.roomInfo.get)
        } else {
          log.error(s"sign in error: ${rsp.msg}")
          Boot.addToPlatform {
            removeLoading()
            WarningDialog.initWarningDialog(s"${rsp.msg}")
          }
        }
      case Left(e) =>
        log.error(s"sign in server error: $e")
        Boot.addToPlatform {
          removeLoading()
          WarningDialog.initWarningDialog(s"服务器错误: $e")
        }
    }

  }

  /**
    * 用临时文件内信息登录
    */
  def loginByTemp(): Unit = {
    showLoading()
    val dir = Constants.loginInfoCache
    val files = dir.list.toList
    val prefix = "VideoMeeting".r
    val suffix = "cacheLogin".r
    var fileName = ""
    files.foreach { r =>
      if (prefix.findFirstIn(r).isDefined && suffix.findFirstIn(r).isDefined) fileName = r
    }

    if (fileName == "") {
      log.debug(s"no VideoMeeting login temp")
      removeLoading()
    } else {
      log.debug(s"login by cache.")
      var userInfo: Option[UserInfo] = None
      var roomInfo: Option[RoomInfo] = None
      var getTokenTime: Option[Long] = None
      val file = new File(Constants.loginInfoCachePath, fileName)
      if (file.canRead && file.exists()) {
        val bufferedReader = new BufferedReader(new FileReader(file))
        val password = bufferedReader.readLine().split(":").last
        userInfo = Some(UserInfo(
          bufferedReader.readLine().split(":").last.toLong,
          bufferedReader.readLine().split(":").last,
          bufferedReader.readLine().split(":").last,
          bufferedReader.readLine().split(":").last,
          bufferedReader.readLine().split(":").last.toLong,

        ))
        roomInfo = Some(RoomInfo(
          bufferedReader.readLine().split(":").last.toLong,
          bufferedReader.readLine().split(":").last,
          bufferedReader.readLine().split(":").last,
          userInfo.get.userId,
          userInfo.get.userName,
          userInfo.get.headImgUrl,
          bufferedReader.readLine().split(":").last,
          0,
          0
        ))
        getTokenTime = Some(bufferedReader.readLine().split(":").last.toLong)
        bufferedReader.close()
      }

      rmManager ! RmManager.SignInSuccess(userInfo.get, roomInfo.get, getTokenTime)
      RmManager.userInfo = userInfo
      RmManager.roomInfo = roomInfo
      removeLoading()
      Boot.addToPlatform {
        showScene()
      }
    }
  }

  /**
    * 更新缓存文件 token
    */
  def updateCache() = {
    val dir = Constants.loginInfoCache
    val files = dir.list.toList
    val prefix = "VideoMeeting".r
    val suffix = "cacheLogin".r
    var fileName = ""
    files.foreach { r =>
      if (prefix.findFirstIn(r).isDefined && suffix.findFirstIn(r).isDefined) fileName = r
    }
    if (fileName == "") {
      log.debug(s"no VideoMeeting login temp")
    } else {
      log.debug(s"login by cache.")
      var userName: Option[String] = None
      var password: Option[String] = None
      val file = new File(Constants.loginInfoCachePath, fileName)
      if (file.canRead && file.exists()) {
        val bufferedReader = new BufferedReader(new FileReader(file))
        password = Some(bufferedReader.readLine().split(":").last)
        val userId = Some(bufferedReader.readLine().split(":").last.toLong)
        userName = Some(bufferedReader.readLine().split(":").last)
        bufferedReader.close()
      }
      RMClient.signIn(userName.getOrElse(""), password.getOrElse("")).map{
        case Right(rsp) =>
          if (rsp.errCode == 0) {
            rmManager ! RmManager.SignInSuccess(rsp.userInfo.get, rsp.roomInfo.get)
            deleteLoginTemp()
            createLoginTemp(password.getOrElse(""), rsp.userInfo.get, rsp.roomInfo.get)
          } else {
            log.error(s"sign in error: ${rsp.msg}")
            Boot.addToPlatform {
              WarningDialog.initWarningDialog(s"${rsp.msg}")
            }
          }
        case Left(e) =>
          log.error(s"sign in server error: $e")
          Boot.addToPlatform {
            WarningDialog.initWarningDialog(s"服务器错误: $e")
          }
      }

    }



  }


  /**
    * 创建VideoMeeting登录临时文件
    */
  def createLoginTemp(password: String, userInfo: UserInfo, roomInfo: RoomInfo): Unit = {

    val file = Constants.loginInfoCache
    val temp = File.createTempFile("VideoMeeting", "cacheLogin", file) //为临时文件名称添加前缀和后缀
    if (temp.exists() && temp.canWrite) {
      val bufferedWriter = new BufferedWriter(new FileWriter(temp))
      bufferedWriter.write(s"passWord:$password\n")
      bufferedWriter.write(s"userId:${userInfo.userId}\n")
      bufferedWriter.write(s"userName:${userInfo.userName}\n")
      bufferedWriter.write(s"headImgUrl:${userInfo.headImgUrl}\n")
      bufferedWriter.write(s"token:${userInfo.token}\n")
      bufferedWriter.write(s"tokenExistTime:${userInfo.tokenExistTime}\n")
      bufferedWriter.write(s"roomId:${roomInfo.roomId}\n")
      bufferedWriter.write(s"roomName:${roomInfo.roomName}\n")
      bufferedWriter.write(s"roomDes:${roomInfo.roomDes}\n")
      bufferedWriter.write(s"coverImgUrl:${roomInfo.coverImgUrl}\n")
      bufferedWriter.write(s"getTokenTime:${System.currentTimeMillis()}\n")
      bufferedWriter.close()
    }
    log.debug(s"create VideoMeeting temp: $temp")
  }

  /**
    * 删除VideoMeeting登录临时文件
    */
  def deleteLoginTemp(): Unit = {
    val dir = Constants.loginInfoCache
    dir.listFiles().foreach { file =>
      if (file.exists()) file.delete()
      log.debug(s"delete VideoMeeting temps: ${file.getName}")
    }
  }

}
