package org.seekloud.VideoMeeting.webClient.pages

import org.seekloud.VideoMeeting.webClient.common.PageSwitcher
import mhtml._
import org.scalajs.dom
import org.seekloud.VideoMeeting.webClient.util.{Http, JsFunc}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._

import scala.xml.Elem
import org.seekloud.VideoMeeting.webClient.common.Routes
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalajs.dom.Event
import org.scalajs.dom.html.Input
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.seekloud.VideoMeeting.webClient.common.Components.PopWindow

import scala.concurrent.ExecutionContext.Implicits.global
/**
  * create by zhaoyin
  * 2019/7/18  10:36 AM
  */
object MainPage extends PageSwitcher {
  //TODO 其他页面共用的数据都存在localStorage里面
  //用户信息，直播房间信息，录像信息，弹窗信息窗口
  var recordInfo = RecordInfo(-1l,-1l,"","","",-1l,"",-1l,"","")
  var userShowName = if (dom.window.localStorage.getItem("userName") == null | dom.window.localStorage.getItem("isTemUser") != null) Var("") else Var(dom.window.localStorage.getItem("userName"))
  var userShowImg = if (dom.window.localStorage.getItem("userHeaderImgUrl") == null | dom.window.localStorage.getItem("isTemUser") != null) Var("") else Var(dom.window.localStorage.getItem("userHeaderImgUrl"))
  var adminShowName = if(dom.window.localStorage.getItem("adminName") == null) Var("") else Var(dom.window.localStorage.getItem("adminName"))
  var recordId = -1l
  var recordTime = -1l
  var preRecord: Option[RecordPage] = None
  var showPersonCenter = Var(emptyHTML)
  var showRtmpInfo = Var(emptyHTML)
  val showAdminLogin = Var(emptyHTML)
  val fileName = "pcClient-19.9.24.zip"

  def isTemUser(): Boolean ={
    dom.window.localStorage.getItem("isTemUser") != null
  }
  private val exitButton: Elem =
    <div class="header-exit" onclick={()=> dom.window.location.hash = "#/Home"}>
      <img src="/VideoMeeting/roomManager/static/img/logo.png" title="主页"></img>
      <div>主页</div>
    </div>
  private val noUserShow: Elem =
    <div class="header-content">
      <div style="display:flex">
        <div class="obs-anchor">
          <img src="/VideoMeeting/roomManager/static/img/logo.png"></img>
          <div class="head-logo">视频会议系统</div>
        </div>
      </div>
      <div style="display:flex">
        <label class="header-login" id="login" for="pop-login">登录</label>
          {PopWindow.loginPop}
          {PopWindow.emailLoginPop}
        <label class="header-register" id="register" for="pop-register">注册</label>
          {PopWindow.registerPop}
      </div>
    </div>
  private val userShow: Elem =
    <div class="header-content">
      <div style="display:flex">
        <div class="obs-anchor">
          <img src="/VideoMeeting/roomManager/static/img/logo.png"></img>
          <div class="head-logo">视频会议系统</div>
        </div>
      </div>
      <div style="display:flex">
        <div class="header-defaultimg">
          <img src={userShowImg} onclick={() => showPersonCenter := PopWindow.personalCenter(if(isTemUser()) dom.window.sessionStorage.getItem("userId").toLong else dom.window.localStorage.getItem("userId").toLong,
            if(isTemUser()) dom.window.sessionStorage.getItem("userName") else dom.window.localStorage.getItem("userName"))} id="userHeadImg"></img>
          <div class="header-user">
            {userShowName}
          </div>
        </div>
        {showPersonCenter}
        <div class="header-button" onclick={() => loginOut()}>登出</div>
      </div>
    </div>


  private val menuShow = if(dom.window.localStorage.getItem("userName") != null
    && dom.window.localStorage.getItem("isTemUser") == null){
    Var(userShow)
  } else Var(noUserShow)
  private val exitShow = Var(emptyHTML)


  def hashChangeHandle(): Unit ={
    preRecord.foreach(_.exitRecord().foreach(_ => preRecord = None))
  }

  private val currentPage = {
    currentHashVar.map { current =>
      //hash值变化时必须执行的函数
      hashChangeHandle()
      current match{
        case "Home" :: Nil =>
          exitShow := emptyHTML
          //返回首页时关闭websocket
          if(dom.window.localStorage.getItem("roomId") != null){
            clearRecordInfo()
            clearRoomInfo()
          }
          new Home().render
        case "recordList" :: Nil =>
          exitShow := emptyHTML
          //返回首页时关闭websocket
          if(dom.window.localStorage.getItem("roomId") != null){
            clearRecordInfo()
            clearRoomInfo()
          }
          new RecordList().render
        case "Record":: roomId :: time :: Nil =>
          clearRoomInfo()
          exitShow := exitButton
          preRecord = Some(new RecordPage(roomId.toLong,time.toLong))
          preRecord.get.render
        case x =>
          clearRoomInfo()
          emptyHTML
      }
  }

  }
  def clearRoomInfo() = {
    if(dom.window.localStorage.getItem("roomId") != null){
      dom.window.localStorage.removeItem("roomId")
      dom.window.localStorage.removeItem("coverImgUrl")
      dom.window.localStorage.removeItem("headImgUrl")
      dom.window.localStorage.removeItem("roomName")
    }
  }
  def openOrClose()={
    if(dom.window.localStorage.getItem("isTemUser")!=null){
      preRecord.foreach(_.closeComment())
    }else{
      preRecord.foreach(_.openComment())
    }
  }
  def clearRecordInfo()={
    if(dom.window.localStorage.getItem("recordName") != null){
      dom.window.localStorage.removeItem("recordName")
      dom.window.localStorage.removeItem("recordCoverImg")
      dom.window.localStorage.removeItem("recordStartTime")
    }
  }
  def downLoad(e: dom.Event)={
    dom.window.open(s"/VideoMeeting/roomManager/file/download/$fileName")
    e.stopPropagation()
  }
  //main--------------------------------------------
  def show(): Cancelable = {
    switchPageByHash()
    val page =
      <div>
        {PopWindow.showPop}
        <div class="header">
          {exitShow}
          {menuShow}
        </div>
        {currentPage}
      </div>
    mount(dom.document.body, page)
  }

  //function-----------------------------------------
  def register(e: Event, popId: String): Unit = {
    val emial = dom.document.getElementById("register-email").asInstanceOf[Input].value
    val account = dom.document.getElementById("register-account").asInstanceOf[Input].value
    val password = dom.document.getElementById("register-password").asInstanceOf[Input].value
    val password2 = dom.document.getElementById("register-password2").asInstanceOf[Input].value
    if (!emial.trim.equals("") && !account.trim.equals("") && !password.trim.equals("") && !password2.trim.equals("")) {
      if (password.equals(password2)) {
        PopWindow.registerButton := <img src="/VideoMeeting/roomManager/static/img/loading.gif"></img>
        val redirectUrl = s"https://${dom.document.location.host}/VideoMeeting/webClient"
        val data = SignUp(emial, account, password, redirectUrl).asJson.noSpaces
        Http.postJsonAndParse[SignUpRsp](Routes.UserRoutes.userRegister, data).map {
          case Right(rsp) =>
            if (rsp.errCode == 0) {
              PopWindow.closePop(e, popId)
              //注册之后还需要登录
            } else {
              PopWindow.commonPop(s"error happened: ${rsp.msg}")
            }
          case Left(error) =>
            PopWindow.commonPop(s"error: $error")
        }.foreach(_ => PopWindow.registerButton := <div class="pop-button" onclick={(e: Event) => MainPage.register(e, "pop-register")}>GO</div>)
      }
      else {
        PopWindow.commonPop("输入相同的密码！")
      }
    } else {
      PopWindow.commonPop("注册项均不能为空！")
    }
  }

  def temUserLogin(roomId: Long): Unit ={
    Http.getAndParse[GetTemporaryUserRsp](Routes.UserRoutes.temporaryUser).map{
      case Right(rsp) =>
        if(rsp.errCode == 0){
          rsp.userInfoOpt.foreach{ userNewInfo =>
            dom.window.localStorage.setItem("userName", userNewInfo.userName)
            dom.window.localStorage.setItem("userHeaderImgUrl", userNewInfo.headImgUrl)
            dom.window.localStorage.setItem("userId", userNewInfo.userId.toString)
            dom.window.localStorage.setItem("token", userNewInfo.token.toString)
            //更改mainPage里的无用户为临时用户
            dom.window.localStorage.setItem("isTemUser", "1")
            //更新用户刷新页面
            if(dom.window.location.hash.contains("Live")){
              dom.window.location.hash = s"#/Live/$roomId"
              dom.window.location.reload()
            }
          }
        }
        else{
          PopWindow.commonPop(s"error in temUserLogin: ${rsp.msg}")
        }
      case Left(e) =>
        PopWindow.commonPop(s"left error in temUserLogin: $e")
    }.foreach(_ => openOrClose())
  }

  def login(e: Event, popId: String): Unit = {
    PopWindow.loginButton := <img src="/VideoMeeting/roomManager/static/img/loading.gif"></img>
    val account = dom.document.getElementById("login-account").asInstanceOf[Input].value
    val password = dom.document.getElementById("login-password").asInstanceOf[Input].value
    val data = SignIn(account, password).asJson.noSpaces
    Http.postJsonAndParse[SignInRsp](Routes.UserRoutes.userLogin, data).map {
      case Right(rsp) =>
        if (rsp.errCode == 0) {
          //登录之后获取到房间信息和用户信息
          if (rsp.userInfo.isDefined) {
            dom.window.localStorage.setItem("userName", account)
            dom.window.localStorage.setItem("userHeaderImgUrl", rsp.userInfo.get.headImgUrl)
            dom.window.localStorage.setItem("token", rsp.userInfo.get.token)
            dom.window.localStorage.setItem("userId", rsp.userInfo.get.userId.toString)
            dom.window.localStorage.setItem("myRoomId",rsp.roomInfo.get.roomId.toString)
            userShowName := dom.window.localStorage.getItem("userName")
            userShowImg := dom.window.localStorage.getItem("userHeaderImgUrl")
            //userInfo = rsp.userInfo.get
            dom.window.localStorage.removeItem("isTemUser")
            menuShow := userShow
            dom.window.location.hash = s"#/recordList"
          } else {
            println("don't get userInfo")
            PopWindow.commonPop(s"don't get userInfo")
          }
          PopWindow.loginButton := <div class="pop-button" onclick={(e: Event) => MainPage.login(e, "pop-login")}>GO</div>
          refresh()
          PopWindow.closePop(e, popId)
        } else {
          PopWindow.commonPop(s"error happened: ${rsp.msg}")
        }
      case Left(error) =>
        PopWindow.commonPop(s"error: $error")
        //FIXME 下面的foreach不能每次都执行
    }.foreach(_ => PopWindow.loginButton := <div class="pop-button" onclick={(e: Event) => MainPage.login(e, "pop-login")}>GO</div>)
  }

  def emailLogin(e: Event, popId: String): Unit = {
    PopWindow.emailLoginButton := <img src="/VideoMeeting/roomManager/static/img/loading.gif"></img>
    val account = dom.document.getElementById("login-email-account").asInstanceOf[Input].value
    val password = dom.document.getElementById("login-email-password").asInstanceOf[Input].value
    val data = SignInByMail(account, password).asJson.noSpaces
    Http.postJsonAndParse[SignInRsp](Routes.UserRoutes.userLoginByMail, data).map {
      case Right(rsp) =>
        if (rsp.errCode == 0) {
          if (rsp.userInfo.isDefined) {
            dom.window.localStorage.setItem("userName", rsp.userInfo.get.userName)
            dom.window.localStorage.setItem("userHeaderImgUrl", rsp.userInfo.get.headImgUrl)
            dom.window.localStorage.setItem("token", rsp.userInfo.get.token)
            dom.window.localStorage.setItem("userId", rsp.userInfo.get.userId.toString)
            dom.window.localStorage.setItem("myRoomId",rsp.roomInfo.get.roomId.toString)
            //            userInfo = rsp.userInfo.get
            dom.window.localStorage.removeItem("isTemUser")
            menuShow := userShow
            dom.window.location.hash = s"#/recordList"
          } else {
            PopWindow.commonPop(s"don't get userInfo")
          }
          //刷新本页面
          PopWindow.emailLoginButton := <div class="pop-button" onclick={(e: Event) => MainPage.emailLogin(e, "pop-emailLogin")}>GO</div>
          refresh()
          //关闭弹窗
          PopWindow.closePop(e, popId)
        }
        else {
          PopWindow.commonPop(s"error happened: ${rsp.msg}")
        }
      case Left(error) =>
        PopWindow.commonPop(s"error: $error")
    }.foreach(_ => PopWindow.emailLoginButton := <div class="pop-button" onclick={(e: Event) => MainPage.emailLogin(e, "pop-emailLogin")}>GO</div>)
  }

  def loginOut(): Unit = {
    menuShow := noUserShow
    //录像退出需发送消息
    preRecord.foreach(_.exitRecord())
    dom.window.localStorage.removeItem("userName")
    dom.window.localStorage.removeItem("userHeaderImgUrl")
    dom.window.localStorage.removeItem("token")
    dom.window.localStorage.removeItem("userId")
    if(dom.window.localStorage.getItem("myRoomId") != null){
      dom.window.localStorage.removeItem("myRoomId")
    }
    dom.window.location.hash = s"#/Home"
    refresh()
  }


  def refresh() = {
    //登录登出时重置页面
    //需要判断通过hash本页面是哪个页面
    //在home界面不需要任何操作
    if(dom.window.location.hash.contains("Home")){
//      goHome
    }
    //live界面登出后以游客方式进入房间，在live里面refresh只用reload就行了
    if(dom.window.location.hash.contains("Live")){
      if(dom.window.localStorage.getItem("userName") == null && dom.window.localStorage.getItem("roomId") != null){
        temUserLogin(dom.window.localStorage.getItem("roomId").toLong)
      }
      else{
        dom.window.location.reload()
      }
    }
    //record界面登出后游客方式进入房间
    if(dom.window.location.hash.contains("Record")){
      if(dom.window.localStorage.getItem("userName") == null){
        temUserLogin(-1l)
      }
      else{
        openOrClose()
      }
    }
  }

  def goRecord = {
    dom.window.location.hash = s"#/Record/$recordId/$recordTime"
  }


}