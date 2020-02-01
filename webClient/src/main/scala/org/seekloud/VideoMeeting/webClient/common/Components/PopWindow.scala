package org.seekloud.VideoMeeting.webClient.common.Components

import mhtml.Var
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Image, Input}
import org.seekloud.VideoMeeting.webClient.pages.{AnchorPage, MainPage}
import mhtml._
import io.circe.syntax._
import io.circe.generic.auto._

import scala.xml.Elem
import MainPage.{showPersonCenter, showRtmpInfo, userShowName, showAdminLogin}
import org.scalajs.dom.html
import org.scalajs.dom.raw.{File, FileList, FileReader, FormData, HTMLElement}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonRsp
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.ImgChangeRsp
import org.seekloud.VideoMeeting.webClient.actors.WebSocketRoom
import org.seekloud.VideoMeeting.webClient.common.Routes
import org.seekloud.VideoMeeting.webClient.util.{Http, JsFunc}
import org.seekloud.VideoMeeting.webClient.util.RtmpStreamerJs._

import scala.concurrent.ExecutionContext.Implicits.global
/**
  * create by 13
  * 2019/7/19  12:17 AM
  */
object PopWindow {

  val showPop = Var(emptyHTML)
  val loginButton = Var(<div class="pop-button" onclick={(e: Event) => MainPage.login(e, "pop-login")}>GO</div>)
  val emailLoginButton = Var(<div class="pop-button" onclick={(e: Event) => MainPage.emailLogin(e, "pop-emailLogin")}>GO</div>)
  val registerButton = Var(<div class="pop-button" onclick={(e: Event) => MainPage.register(e, "pop-register")}>GO</div>)
  val adminLoginButton = Var(<div class="pop-button" onclick={(e :Event) => MainPage.adminLogin(e, "pop-adminLogin")}>GO</div>)

  //防止弹窗消失
  def stopCancel(e: Event, id: String): Unit = {
    //stopPropagation防止事件冒泡
    e.stopPropagation()
    dom.document.getElementById(id).setAttribute("disabled", "")
  }
  //生成令弹窗消失的必须条件（用于解除防止弹窗消失）
  def closeReport(e: Event, id: String): Unit ={
    e.stopPropagation()
    dom.document.getElementById(id).removeAttribute("disabled")
  }

  //使用input标签for属性的弹窗的关闭方法（因为input标签可以自己检测到点击事件，此方法主要用于在不允许点击关闭的情况下自动关闭或强制关闭）
  def closePop(e: Event, id: String): Unit ={
    closeReport(e, id)
    dom.document.getElementById(id).asInstanceOf[Input].checked = false
  }

  /** 这里使用了input标签和for属性来生成弹窗。相对于使用Var的方式（手机端生成弹窗的方式）生成的弹窗，它可以不使用Js而使用css样式伪类checked来制作页面动画。
    * 但是其弹窗上的元素交互功能写起来会相对麻烦，所以对于静止的和有交互的弹窗不推荐使用（推荐手机端的方式生成弹窗）
  */
  def commonPop(text: String): Unit ={
    showPop :={
      <div>
        <input id="pop-common" style="display: none;" type="checkbox" checked="checked"></input>
        <label class="pop-background" for="pop-common" style="z-index: 3;">
          <div class="pop-main" onclick={(e: Event)=>stopCancel(e,"pop-common")}>
            <div class="pop-header"></div>
            <div class="pop-content">
              <div class="pop-text">{text}</div>
            </div>
            <div class="pop-confirm">
              <div class="pop-button" onclick={(e: Event)=>closeReport(e,"pop-common")}>确认</div>
            </div>
          </div>
        </label>
      </div>
    }
  }

  // 'for' is 'pop-login'
  def loginPop: Elem =
    <div>
      <input id="pop-login" style="display: none;" type="checkbox"></input>
      <label class="pop-background" for="pop-login" onclick={(e: Event)=>closeReport(e,"pop-login")}>
        <div class="pop-main" onclick={(e: Event)=>stopCancel(e,"pop-login")}>
          <div class="pop-header"></div>
          <div class="pop-title">用户登录</div>
          <div class="pop-content">
            <input class="pop-input" id="login-account" placeholder="用户名"></input>
            <input class="pop-input" type="password" id="login-password" placeholder="密码"></input>
          </div>
          <label class="pop-tip" for="pop-emailLogin" onclick={ (e: Event) => closePop(e, "pop-login")}>试试邮箱登录？</label>
          <div class="pop-confirm">
            {loginButton}
          </div>
        </div>
      </label>
    </div>

  def emailLoginPop: Elem =
    <div>
      <input id="pop-emailLogin" style="display: none;" type="checkbox"></input>
      <label class="pop-background" for="pop-emailLogin" onclick={(e: Event)=>closeReport(e,"pop-emailLogin")}>
        <div class="pop-main" onclick={(e: Event)=>stopCancel(e,"pop-emailLogin")}>
          <div class="pop-header"></div>
          <div class="pop-title">邮箱登录</div>
          <div class="pop-content">
            <input class="pop-input" id="login-email-account" placeholder="邮箱账号"></input>
            <input class="pop-input" type="password" id="login-email-password" placeholder="密码"></input>
          </div>
          <label class="pop-tip" for="pop-login" onclick={ (e: Event)=>closePop(e, "pop-emailLogin")}>返回用户登录？</label>
          <div class="pop-confirm">
            {emailLoginButton}
          </div>
        </div>
      </label>
    </div>


  def registerPop: Elem =
    <div>
      <input id="pop-register" style="display: none;" type="checkbox"></input>
      <label class="pop-background" for="pop-register" onclick={(e: Event)=>closeReport(e,"pop-register")}>
        <div class="pop-main" onclick={(e: Event)=>stopCancel(e,"pop-register")}>
          <div class="pop-header"></div>
          <div class="pop-title">用户注册</div>
          <div class="pop-content">
            <input class="pop-input" id="register-email" placeholder="邮箱"></input>
            <input class="pop-input" id="register-account" placeholder="注册用户名"></input>
            <input class="pop-input" id="register-password" type="password" placeholder="注册密码"></input>
            <input class="pop-input" id="register-password2" type="password" placeholder="确认密码"></input>
          </div>
          <div class="pop-confirm">
            {registerButton}
          </div>
        </div>
      </label>
    </div>


  var anchorTmp :Option[AnchorPage] = None
  val isLive = Var(false)

  def rtmpPop(rtmptoken:String,secureKey:String,userId:Long,roomId:Long,userToken:String):Elem ={
    if(anchorTmp.isEmpty){
      anchorTmp =  Some(new AnchorPage(userId,"",roomId,"",userToken))
    }
    val url = s"rtmp://${dom.window.location.hostname}:62040/live?rtmpToken=$rtmptoken&userId=$userId"
    val linkButton = <div onclick={()=>anchorTmp.get.rtmpLive(); isLive.update(_ => true)} class="pop-button">确认直播</div>
    val closeButton = <div onclick={()=>anchorTmp.get.rtmpClose(); isLive.update(_ => false)} class="pop-button">关闭直播</div>;
    <div class="pop-background" style="display:flex" onclick={(e: Event)=> showRtmpInfo := emptyHTML}>
      <div class="pop-main" onclick={(e: Event)=>e.stopPropagation()} style="padding-top:50px;padding:50px 10px 30px 10px">
        <div style="margin-right:10px;margin-bottom:10px;color:#f70">url:</div>
        <input value={url} style="width:400px;margin-bottom:20px;height:30px"></input>
        <div style="margin-right:10px;color:#f70;margin-bottom:10px">secureKey:</div>
        <input value={secureKey} style="width:400px;height:30px"></input>
        {isLive.map{
        case true =>
          closeButton
        case false =>
          linkButton
      }}
      </div>
    </div>
  }

  var currImgNum = Math.floor(Math.random()*8)+1
  var initHeadImg = "/VideoMeeting/roomManager/static/img/headPortrait/"+currImgNum+".jpg"
  def changeHeadImg()={
    //    dom.document.getElementById("imgBubble").setAttribute("style","display:none")
    var headNum = Math.floor(Math.random()*9)
    while (currImgNum == headNum){
      headNum = Math.floor(Math.random()*9)
    }
    currImgNum = headNum
    dom.document.getElementById("random-head").setAttribute("src","/VideoMeeting/roomManager/static/img/headPortrait/"+headNum.toInt+".jpg")
  }

  def changeUserInfo(userId:Long):Unit = {
    val name = dom.document.getElementById("change-username").asInstanceOf[Input].value
    val src = dom.document.getElementById("random-head").asInstanceOf[Image].src
    if(name==""){
      JsFunc.alert("昵称不能为空！")
    }else{
      Http.getAndParse[CommonRsp](Routes.UserRoutes.nickNameChange(userId,name)).map{
        case Right(nameRsp)=>
          userShowName := name
        case Left(value)=>
      }
      val img2file = new ChangeImg2File(src)
      val imgFile = img2file.changeImg2File(img2file)
      var form = new FormData()
      form.append("fileUpload", imgFile)
      Http.postFormAndParse[ImgChangeRsp](Routes.UserRoutes.uploadImg(0,userId.toString),form).map{
        case Right(imgRsp)=>
          JsFunc.alert("更改个人信息成功！")
          showPersonCenter := emptyHTML
          dom.document.getElementById("userHeadImg").asInstanceOf[Image].src= imgRsp.url
        case Left(value)=>
          JsFunc.alert("更改个人信息失败")
          showPersonCenter := emptyHTML
      }
    }
  }

  //上传图片例子，目前没有交互
  def changeImgByFile(userId: Long, fileUrl: String, files: FileList):Unit ={
    println(fileUrl)
    println(files(0).name)
    val reader = new FileReader()
    reader.readAsDataURL(files(0))
    reader.onload = { e: Event =>
//      println(e.target.asInstanceOf[FileReader].result.toString)
      val name = e.target.asInstanceOf[FileReader].result.toString
      dom.document.getElementById("random-head").asInstanceOf[Image].setAttribute("src", name)
    }

    val form = new FormData()
    form.append("fileUpload", files(0))
    Http.postFormAndParse[ImgChangeRsp](Routes.UserRoutes.uploadImg(0,userId.toString),form).map{
      case Right(imgRsp)=>
        JsFunc.alert("更改个人信息成功！")
        showPersonCenter := emptyHTML
      case Left(value)=>
        JsFunc.alert("更改个人信息失败")
        showPersonCenter := emptyHTML
    }
  }

  def personalCenter(userId:Long,userName:String):Elem =
    <div class="pop-background" style="display:flex" onclick={(e: Event)=>showPersonCenter := emptyHTML}>
      <div class="pop-main" onclick={(e: Event)=>e.stopPropagation()} style="padding-top:50px">
        <div class="imgBubble" id="imgBubble">
          <span>你动我一下试试！</span>
        </div>
        <div class="change-userImg">
          <input style="display: none" type="file" id="userImg-file" onchange={(e: Event)=>changeImgByFile(userId, e.target.asInstanceOf[Input].value, e.target.asInstanceOf[Input].files)}>okokok</input>
          <img src={initHeadImg} onclick={()=>changeHeadImg()} id="random-head"></img>
          <!--<div class="change-userImg-file" onclick={()=>dom.document.getElementById("userImg-file").asInstanceOf[HTMLElement].click()}>选择文件</div>-->
        </div>
        <input class="pop-input" id="change-username" placeholder="修改昵称" value={userName}></input>
        <div class="pop-button" onclick={()=>changeUserInfo(userId)}>确认修改</div>
      </div>
    </div>

  def adminLogin():Elem =
    <div class="pop-background" style="display:flex" onclick={(e: Event)=> showAdminLogin := emptyHTML}>
      <div class="pop-main" onclick={(e: Event)=>e.stopPropagation()} style="padding-top:50px">
        <div class="pop-title">管理员登录</div>
        <div class="pop-content">
          <input class="pop-input" id="adminName" placeholder="管理员名"></input>
          <input class="pop-input" id="password" type="password"  placeholder="密码"></input>
        </div>
        <div class="pop-confirm">
          {adminLoginButton}
        </div>
      </div>
    </div>

}
