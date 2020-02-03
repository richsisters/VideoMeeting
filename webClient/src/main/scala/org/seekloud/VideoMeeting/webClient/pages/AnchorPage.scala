package org.seekloud.VideoMeeting.webClient.pages

import org.scalajs.dom
import org.scalajs.dom.html.{Embed, Input, TextArea}

import scala.xml.{Elem, Node}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Elem
import io.circe.syntax._
import io.circe.generic.auto._
import mhtml._
import org.scalajs.dom.raw.{FileReader, FormData, HTMLElement, URL}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{ClientType, UserDes}
import org.seekloud.VideoMeeting.webClient.common.{Page, Routes}
import org.seekloud.VideoMeeting.webClient.util.{Globals, Http, JsFunc, ScalaWebSocket}
import org.seekloud.VideoMeeting.webrtcMessage.ptcl.BrowserJson
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.webClient.actors.WebSocketRoom
import org.seekloud.VideoMeeting.webClient.common.Page
import org.seekloud.VideoMeeting.webClient.common.Routes.rmWebScocketUri
import org.seekloud.VideoMeeting.webClient.common.UserMsg._
import org.seekloud.VideoMeeting.webClient.util.video.{exitFullScreen, fullScreen}
import org.scalajs.dom.{Event, FileList}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.ImgChangeRsp
import org.seekloud.VideoMeeting.webClient.util.RtmpStreamerJs._
import scala.collection.mutable.ListBuffer
/**
  * create by zhaoyin
  * 2019/6/5  10:47 AM
  * 主播页
  */
class AnchorPage(userId:Long,userName:String, roomId:Long,roomName:String, token:String) extends Page {
  private val barragesList = Var(List[(String,String)]())
  private val myself = userId
  private val showRemote: Var[Boolean] = Var(false)
  private var audienceLiveId = ""
  private var audienceName = ""
  private var audienceId = -1l
  private var liveId = ""
  private var liveCode = ""
  private var svip = 0
  private var myroomName = Var(roomName)
  private val showStyle = showRemote map {
    case true => "display:block;"
    case false => "display:none;"
  }
  private val audienceConnectLists = Var(ListBuffer[(Long,String)]())
  private val audienceLists = Var(List[UserDes]())
  private val audienceNums = Var(0)
  var allowAId = Var(-1l)
  private val controlLists = {
    <div class="roomContainer">
      {var active = Var(0)
    active.map(i=>
      if(i==0){
        <div class="roomContainer-main">
          <div class="roomLists">
            <div class="roomLists-active" onclick={() => active.update(i=>0)}>弹幕乁( ˙ ω˙乁)</div>
            <div class="roomLists-noactive" onclick={() => active.update(i=>1)}>连线列表</div>
            <div class="roomLists-noactive" onclick={() => active.update(i=>2)}>当前观众<span>({audienceNums})</span></div>
          </div>
          <div class="roomContains " id="comments-contain">
            <ul id="comments" style="list-style-type:none">
              {barragesList.map{ b_list =>
              def createBList(item:(String,String))={
                if(item._1.equals("[系统消息]")){
                  <li class=" barrage-item " style="color:red"><span class=" barrage-item-user">{item._1}: </span>
                    <span class=" barrage-item-content">{item._2}</span></li>
                }else if(item._1.equals("[自己]")){
                  <li class=" barrage-item "><span class=" barrage-item-user">{item._1}: </span>
                    <span class=" barrage-item-content" style="color:#9b39f4">{item._2}</span></li>
                }else{
                  <li class=" barrage-item "><span class=" barrage-item-user">{item._1}: </span>
                    <span class=" barrage-item-content">{item._2}</span></li>
                }
              }
            {b_list.map(createBList)}}}
            </ul>
          </div>
          <div class="chatsend">
            <div class="chatToolBar">
              <div class="ctb-item" ><img src="/VideoMeeting/roomManager/static/img/表情.png" onclick={()=>showEmoji()}></img>
                <div id="emoji-container-out" class="emoji-container-out">
                  <div class="emoji-container-mid">
                    <span class="emoji-group" onclick={()=>Globals.fetchEmoji(1)}>😀</span>
                    <span class="emoji-group" onclick={()=>Globals.fetchEmoji(2)}>🐈</span>
                    <span class="emoji-group" onclick={()=>Globals.fetchEmoji(3)}>🍎</span>
                  </div>
                  <ul id="emoji-container-in" class="emoji-container-in">
                  </ul>
                </div>
              </div>
            </div>
            <input placeholder="这里输入聊天内容" style="color: rgb(169, 169, 169);" maxlength="40" id="s-commment"
                      onkeydown={(e:dom.KeyboardEvent)=> if (e.keyCode==13) sendBarrage(roomId)}></input>
            <div class="chatsend-button" onclick={()=>sendBarrage(roomId)}>发送</div>
          </div>
        </div>
      }else if(i==1){
        <div class="roomContainer-main">
          <div class="roomLists">
            <div class="roomLists-noactive" onclick={() => active.update(i=>0)}>弹幕乁( ˙ ω˙乁)</div>
            <div class="roomLists-active" onclick={() => active.update(i=>1)}>连线列表</div>
            <div class="roomLists-noactive" onclick={() => active.update(i=>2)}>当前观众<span>({audienceNums})</span></div>
          </div>
          {audienceConnectLists.map{lists =>
            def createAudienceC(item:(Long,String)) = {

                <div>{allowAId.map(i=>
                  if(i == item._1){
                    <div style="display:flex; justify-content: space-between" class="roomItem" onclick={() => allowAudienceC(false,item._1)}>
                      <div>{item._2}({item._2})</div>
                      <div style="color:#6ca9f3;cursor:pointer">连线中</div>
                    </div>
                  }else{
                    <div style="display:flex; justify-content: space-between" class="roomItem" onclick={() => allowAudienceC(true,item._1)}>
                      <div>{item._2}({item._2})</div>
                      <div style="color:#f29a38;cursor:pointer">未连线</div>
                    </div>
                  }
                )}
              </div>
            }
          <div class="roomContain">
            <div>{lists.map(createAudienceC)}</div>
          </div>
          }}
        </div>
      }else{
        <div class="roomContainer-main">
          <div class="roomLists">
            <div class="roomLists-noactive" onclick={() => active.update(i=>0)}>弹幕乁( ˙ ω˙乁)</div>
            <div class="roomLists-noactive" onclick={() => active.update(i=>1)}>连线列表</div>
            <div class="roomLists-active" onclick={() => active.update(i=>2)}>当前观众<span>({audienceNums})</span></div>
          </div>
          {audienceLists.map{lists =>
          def createAudienceList(item:UserDes) = {
            <div style="display:flex; justify-content: space-between;" class="roomItem">
              <div>{item.userName}({item.userId})</div>
            </div>
          }
          <div class="roomContain">
            <div>{lists.map(createAudienceList)}</div>
          </div>
        }}
        </div>
      }
    )}
    </div>
  }

  private val showLayout = Var(false)
  private val layoutChange = showLayout.map{show =>
    if(show){
      <div class="showLayout">
        <div class="showLayout-item">
          <input name="layout" type="radio" value="layout0" checked="checked" onclick={(e:Event) => chooseLayout(e)}/>
          <img src="/VideoMeeting/roomManager/static/img/布局0.png"></img>
        </div>
        <div class="showLayout-item">
          <input name="layout" type="radio" value="layout1" onclick={(e:Event) => chooseLayout(e)}/>
          <img src="/VideoMeeting/roomManager/static/img/布局1.png"></img>
        </div>
        <div class="showLayout-item">
          <input name="layout" type="radio" value="layout2" onclick={(e:Event) => chooseLayout(e)}/>
          <img src="/VideoMeeting/roomManager/static/img/布局2.png"></img>
        </div>
      </div>
    }else <div class="showLayout" style="height:30px"></div>
  }

  def chooseLayout(e:Event) = {
    e.target.asInstanceOf[Input].value match {
      case "layout0" =>
        val data = ChangeLiveMode(None,None,Some(0))
        websocketClient.sendMsg(data)
      case "layout1" =>
        val data = ChangeLiveMode(None,None,Some(1))
        websocketClient.sendMsg(data)
      case "layout2" =>
        val data = ChangeLiveMode(None,None,Some(2))
        websocketClient.sendMsg(data)
    }
  }
  var showEmo = false
  def showEmoji(): Unit ={
    if(showEmo){
      dom.document.getElementById("emoji-container-out").setAttribute("style","display:none")
      showEmo = false
    }else{
      Globals.fetchEmoji(1)
      dom.document.getElementById("emoji-container-out").setAttribute("style","display:block")
      showEmo = true
    }
  }
  def sendBarrage(roomId:Long)={
    dom.document.getElementById("emoji-container-out").setAttribute("style","display:none")
    showEmo = false
    val b_area = dom.document.getElementById("s-commment").asInstanceOf[TextArea]
    if(b_area.value.length>20){
      val barrage = Comment(userId,roomId,b_area.value.substring(0,20))
      b_area.value = ""
      websocketClient.sendMsg(barrage)
    }else if(b_area.value.trim.length == 0){
      JsFunc.alert("请勿发送空消息")
    }else{
      val barrage = Comment(userId,roomId,b_area.value)
      println("barrage:"+barrage)
      b_area.value = ""
      websocketClient.sendMsg(barrage)
    }

  }
  var allow = Var(0) //默认不允许
  private val allowConnection = allow.map(i=>
    if(i==0){
      <div class="noallowC" onclick={() => audienceConnect(true)}>当前房间不能连线</div>
    }else{
      <div class="allowC" onclick={() => audienceConnect(false)}>当前房间可以连线</div>
    }
  )

  val websocketClient = new WebSocketRoom(wsMessageHandler, (event: Event)=> () )

  def allowAudienceC(allow:Boolean,id:Long) = {
    if(allow){
      //允许连线
      val closeLastData = HostShutJoin(roomId)
      websocketClient.sendMsg(closeLastData)
      val data = JoinAccept(roomId,id,ClientType.WEB,allow)
      websocketClient.sendMsg(data)
      allowAId := id
    }else{
      //停止连线
      showRemote:=false
      val data = HostShutJoin(roomId)
      websocketClient.sendMsg(data)
      allowAId := -1l
      audienceConnectLists.update( i =>
        i -= ((audienceId,audienceName))
      )
    }
  }

  //TODO:LiveId
  def connectWebRTC(liveId: String, liveCode:String):Unit = {
    println("start to connectWebRTC: liveId["+liveId+"] liveCode["+liveCode+"]")
    def callback() = {
      Globals.webRtcStart(BrowserJson.EventId.Anchor_SDP_OFFER)
      ScalaWebSocket.state = 1
    }
    ScalaWebSocket.setup(Routes.getWsSocketUri(liveId,liveCode),callback())
  }

  def audienceConnect(connect:Boolean) = {
    if(connect){
      //允许当前房间连线
      val data = ChangeLiveMode(Some(true),None,None)
      println("audienceConnect",data)
      websocketClient.sendMsg(data)
    }else{
      //不允许当前房间连线
      val data = ChangeLiveMode(Some(false),None,None)
      websocketClient.sendMsg(data)
      //清除所有之前连线的观众
      audienceConnectLists := ListBuffer[(Long,String)]()
    }
    allow.update(i => if(i==0) 1 else 0)
  }
  //onclick={(e: Event)=>{showAnchorImg.update(i => 0)}}
  private val showAnchorImg = Var(0)
  private val anchorState = Var(0) //0 未开播状态 1 开播状态
  private val takePhoto = Var(false) //false 上传图片 true 拍照
  private val startAndEnd = anchorState.map(i=>
    if(i==0){
      <div class="anchorControl">
        <div id="anchor" onclick={() => startLive()} class="anchor">开始直播</div>
        <div id="streamer-disconnect" class="closeAnchorG">结束直播</div>
        {showAnchorImg.map{i=>
        if(i==1){
          var widthX = dom.document.documentElement.clientWidth/2 - 130
          var x = s"position: absolute;opacity: 0;width:100px;left: ${widthX}px;"
          <div class="pop-background-anImg"  id="pbaI" >
            <div class="pop-main-anImg">
              <div class="imgHeader">
                <div style="font-size:22px">上传你的直播间封面ᕕ(ᐛ)ᕗ</div>
                <div onclick={()=> sendCoverImg()} class="sendImg">发送</div>
              </div>
              <div class="pop-content">
                {takePhoto.map(i =>
                if(i){
                  //拍照
                  <div id="contentHolder">
                    <video id="video" width="300" height="250" autoplay="autoplay"></video>
                    <canvas style="display:none;" id="canvas" width="300" height="250"></canvas>
                  </div>
                }else{
                  //上传图片
                  <img id="test123" src="/VideoMeeting/roomManager/static/img/默认图片.png" style="width:300px;height:250px;margin-bottom:20px"></img>
                }
              )}
                <div style="display:flex;justify-content:space-around">
                  <div class="upPhoto">
                    <img src="/VideoMeeting/roomManager/static/img/上传图片.png"></img>
                    <div>上传图片</div>
                  </div>
                  <input type="file" accept="image/*" class="pop-input" id="login-account" style={x}
                         onchange={(e:dom.Event)=>uploadImg(e.target.asInstanceOf[Input],e.target.asInstanceOf[Input].files,1)}></input>
                  {takePhoto.map(i=>
                  if(i){
                    <div class="takePhoto" onclick={()=> takephotoSure()} id="btn_snap" style="width:130px">
                      <img src="/VideoMeeting/roomManager/static/img/拍照1.png"></img>
                      <div>点击拍照</div>
                    </div>
                  }else{
                    <div class="takePhoto" onclick={()=> takephoto()}>
                      <img src="/VideoMeeting/roomManager/static/img/拍照1.png"></img>
                      <div>拍照</div>
                    </div>
                  }
                )}
                </div>
              </div>
            </div>
          </div>
        }else{
          emptyHTML
        }
      }}
      </div>
    }else{
      <div class="anchorControl">
        <div id="anchor" class="anchorGrey">开始直播</div>
        <div id="streamer-disconnect" onclick={() => terminateRoom()} class="closeAnchor">结束直播</div>
      </div>
    }
  )

  def takephoto():Unit = {
    //拍照
    takePhoto := true
    val photo = new TakePhotoFile()
    photo.takephoto(photo)
  }

  def takephotoSure() = {
    val photo = new TakePhotoFile()
    val file = photo.takephotoFile(photo)
    dom.window.console.log(file)
    fileLength = 1
    //填充预览图片
    form.append("fileUpload", file)
    takePhoto:= false
    dom.document.getElementById("test123").setAttribute("src", URL.createObjectURL(file))
  }


  var form = new FormData()
  var fileLength = 0

  def sendCoverImg():Unit = {
    if(fileLength==1){
      Http.postFormAndParse[ImgChangeRsp](Routes.UserRoutes.uploadImg(1,userId.toString),form).map{
        case Right(rsp) =>
          showAnchorImg := 0
          val wsurl = Routes.rmWebScocketUri(userId,token,roomId)
          websocketClient.setup(wsurl)
          val data = StartLiveReq(userId,token,CommonInfo.ClientType.WEB)
          timer = dom.window.setInterval(() => {
            if(websocketClient.wsFlag) {
              websocketClient.sendMsg(data)
              showLayout := true
              anchorState:=1
              dom.window.clearInterval(timer)
            }}, 500)
        case Left(e) =>
          showAnchorImg := 0
          JsFunc.alert("上传封面图失败！")
      }
    }else{
      JsFunc.alert("请选择封面图！")
    }
  }

  def uploadImg(input:Input,files:FileList,dataType:Int):Unit={
    if(files.length>1 || files.length==0){
      JsFunc.alert("error")
    }else{
      if(input.value != null){
        val file = files(0)
        fileLength = files.length
//        form = new FormData()
        //填充预览图片
        dom.document.getElementById("test123").setAttribute("src", URL.createObjectURL(file))
        form.append("fileUpload", file)
        //发送post请求
      }
    }
  }

  var timer =0
  var startLiveFlag = false
  def startLive():Unit = {
    Globals.cmInit()
    val vcb = dom.document.getElementById("viv")
    vcb.setAttribute("style","display:none")
    startLiveFlag = true
    connecWebsocket()
  }

  def rtmpLive():Unit = {
    val wsurl = Routes.rmWebScocketUri(userId,token,roomId)
    println("wsurl:    "+wsurl)
    websocketClient.setup(wsurl)
    val data = StartLiveReq(userId,token,CommonInfo.ClientType.WEB)
    timer = dom.window.setInterval(() => {
      if(websocketClient.wsFlag) {
        websocketClient.sendMsg(data)
        JsFunc.alert("创建房间成功！")
        dom.window.clearInterval(timer)
      }}, 500)
  }

  def rtmpClose():Unit ={
    //窗口关闭直播时间，修改可以参考terminateRoom()
    if(websocketClient.wsFlag){
      websocketClient.closeWs
    }
  }

  def terminateRoom():Unit = {
//    val data = HostCloseRoom(roomId)
//    websocketClient.sendMsg(data)
    showLayout := false
    Globals.clearMsg()
    barragesList := List[(String,String)]()
    ScalaWebSocket.sendMessage(BrowserJson.DisConnect(BrowserJson.EventId.DISCONNECT,liveId).asJson.noSpaces)
    anchorState:=0
    showRemote:=false
    allow:=0
    audienceConnectLists := ListBuffer[(Long,String)]()
    Globals.webRtcStop()
    websocketClient.closeWs
    val vcb = dom.document.getElementById("viv")
    vcb.setAttribute("style","display:none")
    startLiveFlag = false
  }


  def connectAudience(userId:Long)={
    //fixme ClientType
    val data = JoinAccept(roomId, userId, ClientType.WEB,true)
    websocketClient.sendMsg(data)
  }
  private def wsMessageHandler(data:WsMsgRm):Unit ={
    println("receive",data)
    data match {
      case StartLiveRsp(liveInfo,errCode,msg) =>
      //开启直播
        if(liveInfo.isDefined) {
          liveId = liveInfo.get.liveId
          liveCode = liveInfo.get.liveCode
          connectWebRTC(liveId, liveCode)
        }else{
          println("---|---: "+liveInfo)
        }

      case ModifyRoomRsp(errCode,msg) =>
      //修改房间信息

      case ChangeModeRsp(errCode,msg) =>
      //设置直播内容


      case AudienceJoin(userId,userName,clientType) =>
      //申请连线者信息
        //fixme ClientType
        println("AudienceJoin",userId,userName)
        audienceConnectLists.update(i => if(!i.contains((userId,userName))){i:+(userId,userName)}else{ i})


      case AudienceJoinRsp(joinInfo,errCode,msg) =>
      //成功连线
        println("AudienceJoinRsp",joinInfo,errCode,msg)
        showRemote := true
        if(joinInfo.isDefined) {
          audienceId = joinInfo.get.userId
          audienceName = joinInfo.get.userName
          audienceLiveId = joinInfo.get.liveId
        }

      case AudienceDisconnect(_) =>
      //观众断开连线通知
        println("AudienceDisconnect")
        audienceConnectLists.update( i =>
          i -= ((audienceId,audienceName))
        )
        allowAId := -1l
        showRemote := false
        ScalaWebSocket.sendMessage(BrowserJson.DisConnect(BrowserJson.EventId.DISCONNECT,audienceLiveId).asJson.noSpaces)

      case RcvComment(userId,userName,comment,color,extension) =>
      //所有用户留言通知
        val commentArea = dom.document.getElementById("comments").asInstanceOf[HTMLElement]
        var ctp = ""
        if(comment.substring(0,1).equals("+")){
          ctp = comment.substring(1)
        }else{
          ctp = comment
        }
        if(userId == -1l){
          barragesList.update(b => b:+("[系统消息]",ctp))
        }else if(userId == myself){
          barragesList.update(b => b:+("[自己]",ctp))
        } else{
          barragesList.update(b => b:+(userName,ctp))
        }
        commentArea.scrollTop = commentArea.scrollHeight
        if(userId == myself) Globals.setCmtData(comment,1,color)
        else Globals.setCmtData(comment,0,color)

      case UpdateAudienceInfo(audienceList) =>
        println("audienceList:  "+audienceList)
        audienceLists := audienceList
        audienceNums := audienceList.length

      case PingPackage =>
//        println("Pong")
      case msg@_ =>
        println(s"unknown $msg")
    }
  }


  def connecWebsocket() = {
    //建立websocket连接
    //保存token
    if(userId == -1l){
      JsFunc.alert("请先登录！")
    }else{
      showAnchorImg:=1
    }
  }
  var fullFlag = false
  def fullScreen():Unit={
    val f = new fullScreen()
    f.a()
    fullFlag = true
  }
  var isMove = false
  var cTimer = -1
  var t = -1
  def showVideoCover()={
    val vcb = dom.document.getElementById("videoControlBottom")
    dom.window.clearTimeout(t)
    vcb.setAttribute("style","bottom:50px;opacity:1")

  }
  def fixVideoCover()={
    val vcb = dom.document.getElementById("videoControlBottom")
    t = dom.window.setTimeout(()=>{vcb.setAttribute("style","bottom:10px;opacity:0")

    },3000)
  }
  def hideVideoCover()={
    isMove = true
    dom.window.clearTimeout(cTimer)
    val vcb = dom.document.getElementById("videoControlBottom")
    vcb.setAttribute("style","bottom:50px;opacity:1")
    cTimer = dom.window.setTimeout(()=>{
      isMove = false
      fixVideoCover()
    },500)
  }
  def stopAnchor():Unit={
    if(startLiveFlag) {
      terminateRoom()
    }
    if(fullFlag){
      val e = new exitFullScreen()
      e.b()
      fullFlag = false
    }

  }
  var currImgNum = Math.floor(Math.random()*8)+1
  var initHeadImg = "/VideoMeeting/roomManager/static/img/headPortrait/"+currImgNum+".jpg"
  def changeHeadImg()={
//    dom.document.getElementById("imgBubble").setAttribute("style","display:none")
    var headNum = Math.floor(Math.random()*8)+1
    while (currImgNum == headNum){
      headNum = Math.floor(Math.random()*8)+1
    }
    currImgNum = headNum
    dom.document.getElementById("headImg").setAttribute("src","/VideoMeeting/roomManager/static/img/headPortrait/"+headNum.toInt+".jpg")
  }
  override def render: Elem = {
    <div class="anchor-contain">
      <div style="width:70%">
        <div class="imgBubble" id="imgBubble">
          <span>你动我一下试试！</span>
        </div>
        <div class="anchorInfo">
          <div class="showInfo">
            <img id="headImg" src={initHeadImg} onclick={()=>changeHeadImg()}></img>
            <div style="margin-left:20px">
              <div style="margin-bottom:10px">用户名：{userName}</div>
              <div style="display:flex;align-items:center">
                <div style="color:#555;font-size:14px">房间名：{myroomName}</div>
                {anchorState.map(i=>
                if(i==0){
                  //开播状态
                  emptyHTML
                }else{
                  //未开播状态
                  <div>{allowConnection}</div>
                }
              )}
              </div>
            </div>
          </div>
          {startAndEnd}
        </div>
        <div class="anchor-all" id="anchor-all" ondblclick={() => fullScreen()} >
          <video id="uiLocalVideo" class="videoBig" autoplay="autoplay" onmousemove={()=>hideVideoCover()}></video>
          <video id="uiRemoteVideo" class="videoSmall1" style={showStyle} autoplay="autoplay"></video>
          {layoutChange}
          <div class="ccl-panel">
            <div class="abp">
              <div id="commentCanvas" class="container"></div>
            </div>
          </div>
          <div id="viv" style="display:none">
          <div id="videoControlBottom" onmousemove={()=>showVideoCover()}>
            <div id="stop">
              <img src="/VideoMeeting/roomManager/static/img/挂断.png" onclick={()=>stopAnchor()}></img>
            </div>
          </div>
          </div>
        </div>
        <div class="tool-button">
        </div>
      </div>
      {controlLists}
    </div>
  }
}
