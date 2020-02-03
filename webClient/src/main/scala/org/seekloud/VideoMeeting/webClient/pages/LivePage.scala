package org.seekloud.VideoMeeting.webClient.pages

import java.util.Date
import java.util.concurrent.TimeUnit
import mhtml._
import io.circe.syntax._
import io.circe.generic.auto._
import scala.xml.{Elem, Node}
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom
import org.scalajs.dom.html._
import org.scalajs.dom.raw.{Event, HTMLElement}
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{ClientType, RoomInfo, UserDes}
import org.seekloud.VideoMeeting.webClient.common.{Page, Routes}
import org.seekloud.VideoMeeting.webClient.util.{Globals, Http, JsFunc, ScalaWebSocket}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol._
import org.seekloud.VideoMeeting.webClient.actors.WebSocketRoom
import org.seekloud.VideoMeeting.webClient.common.Components.InteractiveText.Gift
import org.seekloud.VideoMeeting.webClient.common.Components.{InteractiveText, PopWindow}
import org.seekloud.VideoMeeting.webClient.common.UserMsg._
import org.seekloud.VideoMeeting.webClient.common.Routes._
import org.seekloud.VideoMeeting.webrtcMessage.ptcl.BrowserJson
import org.seekloud.VideoMeeting.webClient.util.RtmpStreamerJs._
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent.Future
/**
  * create by zhaoyin
  * 2019/6/5  10:48 AM
  * 观看直播页
  */
class LivePage(userId:Long, roomId:Long) extends Page{
  val websocketClient = WebSocketRoom(wsMessageHandler, wsStartHandler)
  private val barragesList = Var(List[(String,String,String)]())
  private var isRoomLike = false
  private var roomInUse = Var(List.empty[RoomInfo])
  private var audienceLiveId = ""
  private var audienceLiveCode = ""
  private val audienceLists = Var(List[UserDes]())
  var active = Var(0)
  val likeNumber = Var(0)
  var sendFlag = 0
  var hlsurl = ""
  var dashurl = ""
  var dashStreamer = new DashType("")
  var hlsStreamer = new HlsType("")
  var dColor = ""
  val dColorList = List("FFFFFF","79A8F5","6415E7","F58C1B", "0040FF","FFDD5C","6BCEFF","FE9DD7","D6524E","C92141",
  "0DAEC2","F93200","FF8C08","FFCF75","FEB84A","D6544A", "66CCFF","E33FFF","00FFFC","7EFF00","FFED4F","FF9800", "FF739A")
  var userSetDColor = "FFFFFF"
  var showShield = Var(false)
  var ssw = Var(List[String]())
  var ssw_copy = ListBuffer[String]()
  var showDeleteAll = Var(false)
  var showAllTag = Var(false)
  var showDeleteAllTag = 0
  var streamerFlag = false
  var anchorName = Var("")
  var anchorHeadImgUrl =  Var("")
  private var watchType = Var("dash")

  val watchTypeChoose = Var(<select onchange={(e:Event) => changeWatchType(e)} style="position:relative;left:85%;margin-bottom:10px">
    <option value="dash">使用dash观看</option>
    <option value="hls">使用Hls观看</option>
  </select>)
  private val anchorInfo = {
    <div class="showInfo">
      <img src={dom.window.localStorage.getItem("coverImgUrl")} class="showInfo-coverImg"></img>
      <div style="margin-left:20px;height:80px;margin-top:10px">
        <div style="color:#2f3e4e;font-size:20px">{dom.window.localStorage.getItem("roomName")}</div>
        <div style="margin-bottom:10px;color:#555;font-size:14px;display:flex;margin-top:10px;align-items:center">
          <img id="headImg" src={anchorHeadImgUrl} class="showInfo-headImg"></img>
          <div>{anchorName}</div>
        </div>
      </div>
      <div class="info-blank"></div>
      <div class="attention">
        <div class="attention-button-close" id="attention-button" onclick={()=>userLike()}></div>
        <div class="attention-number" disabled="true">{likeNumber}</div>
      </div>
    </div>
  }
  private val audienceNums = Var(0)
  var barrageItem = mutable.HashMap.empty[String,(Double,Double,Int)]
  private var roomLists:Rx[Node] = roomInUse.map{roomList =>
    {
      def danmuColor(cStr:String)={
        dColor = "background-color: #"+cStr
        <div class="color-wrapper">
          <span class="iconfont color-item verify-ict" style={dColor} onclick={(e:Event)=>setDcolor(e,cStr)}></span>
        </div>
      }
      <div class="roomContainer">
        {
        active.map(item =>
          if(item == 0){
            <div class="roomContainer-main" style="justify-content:space-between">
              <div>
                <div class="roomLists">
                  <div class="roomLists-active" onclick={() => active.update(i=>0)}>弹幕乁( ˙ ω˙乁)</div>
                  <div class="roomLists-noactive" onclick={() => active.update(i=>1)}>当前观众<span>({audienceNums})</span></div>
                </div>
                <div class="roomContains" id="comments-contain">
                  <ul id="comments" style="list-style-type:none">
                    {barragesList.map{ b_list =>
                    def createBList(item:(String,String,String))={
                      val color = "color: #"+item._3
                      if(item._1.equals("[系统消息]")){
                        <li class=" barrage-item " style="color:red"><span class=" barrage-item-user">{item._1}: </span>
                          <span class=" barrage-item-content">{item._2}</span></li>
                      }else if(item._1.equals("[自己]")){
                        <li class=" barrage-item "><span class=" barrage-item-user">{item._1}: </span>
                          <span class=" barrage-item-content" style={color}>{item._2}</span></li>
                      }else{
                        <li class=" barrage-item "><span class=" barrage-item-user">{item._1}: </span>
                          <span class=" barrage-item-content" style={color}>{item._2}</span></li>
                      }

                    }
                  {b_list.map(createBList)}
                  }
                    }
                  </ul>
                </div>
              </div>
              <div class="chatsend">
                <div class="chatToolBar">
                  <div class="ctb-item" >
                    <span class="iconfont icon-emoji verify-ict" onclick={()=>showTool(1)}>&#xe65e;</span>
                    <span class="iconfont icon-color verify-ict" onclick={()=>showTool(2)}>&#xe671;</span>
                    <span class="iconfont icon-shield " style="font-size:25px" onclick={()=>isShowSelectAll()}>&#xe78d;</span>
                    <div id="emoji-container-out" class="emoji-container-out verify-ict">
                      <div class="emoji-container-mid verify-ict">
                        <span class="emoji-group verify-ict" onclick={()=>Globals.fetchEmoji(1)}>😀</span>
                        <span class="emoji-group verify-ict" onclick={()=>Globals.fetchEmoji(2)}>🐈</span>
                        <span class="emoji-group verify-ict" onclick={()=>Globals.fetchEmoji(3)}>🍎</span>
                      </div>
                      <ul id="emoji-container-in" class="emoji-container-in verify-ict">
                      </ul>
                    </div>

                    <div id="danmuka-color" class="danmuka-color verify-ict" >
                      <h1 class="section-title verify-ict">弹幕颜色</h1>
                      <div class="color-choices verify-ict">
                        {dColorList.map(c => danmuColor(c))}
                      </div>
                    </div>
                    { showShield.map{ s =>
                        if(s){
                          <div id="shield-danmu" class="shield-danmu">
                            <h1 class="section-title">关键词屏蔽</h1>
                            <div class="input-keyword">
                              <input type="text" placeholder="请输入您要屏蔽的内容" maxlength="15" class="shield-data" id="shield-data"></input>
                              <button class="ipb-button" onclick={()=>addShieldWord()}><span>添加</span></button>
                            </div>
                            <h2 class="sub-title">屏蔽关键词列表</h2>
                            <div class="shield-word-list">
                              <ul class="list-block">
                                {ssw.map{ s =>
                                    def createSw(item:String)={
                                      <li class="list-block-row">
                                        <span class="iconfont icon-shield-delete" onclick={()=>deleteShieldWord(item)}>&#xe612;</span>
                                        <span class="lbr-word">{item}</span>
                                      </li>
                                    }
                                    {s.map(createSw)}
                                  }

                                }
                              </ul>
                            </div>
                            <div class="select-all-word">
                              {showAllTag.map{ sat =>
                                  if(sat){
                                    <div style="display:flex">
                                      <span class="iconfont tran-for-all-tag" id="tran-for-all-tag" onclick={(e:Event)=>showDeAll(e,showDeleteAllTag)}></span>
                                      <span class="tran-for-all">全选</span>
                                    </div>
                                  }else{
                                    emptyHTML
                                  }
                                }
                              }

                              {showDeleteAll.map{ sda =>
                              if(sda){
                                <button class="sb-button" onclick={()=>tDeleteAll()}><span>删除</span></button>
                              }else{
                               emptyHTML
                              }
                            }
                              }
                            </div>
                          </div>
                        }else{
                          emptyHTML
                        }

                      }
                    }
                  </div>
                </div>
                <div class="message">
                  <input placeholder="我来增加弹幕！！！*罒▽罒*" maxlength="40" id="s-commment" class="s-commment"
                         onkeydown={(e:dom.KeyboardEvent)=> if (e.keyCode==13) sendBarrage(roomId)}></input>
                  <div class="chatsend-button" onclick={()=>sendBarrage(roomId)}>发送</div>
                </div>
              </div>
            </div>
          }else{
            //当前观众
            <div class="roomContainer-main">
              <div class="roomLists">
                <div class="roomLists-noactive" onclick={() => active.update(i=>0)}>弹幕乁( ˙ ω˙乁)</div>
                <div class="roomLists-active" onclick={() => active.update(i=>1)}>当前观众<span>({audienceNums})</span></div>
              </div>
              {audienceLists.map{lists =>
              def createAudienceList(item:UserDes) = {
                <div style="display:flex; line-height:35px" class="roomItem">
                  <img src={item.headImgUrl} style="margin-right:10px"></img>
                  <div style="#333">{item.userName}</div>
                </div>
              }
              <div class="roomContain">
                {lists.map(createAudienceList)}
              </div>}}
            </div>
          }
        )
        }
      </div>
    }
  }
  def giftPop(gift: Gift): Elem =
    <div class="gift-content">
      <div class="gift-desc">
        <img class="gift-img" src={gift.img}></img>
        <div class="gift-text">
          <div class="gift-text-title">
            <div class="gift-text-title-name">{gift.name}</div>
            <img class="gift-text-title-coinImg" src="/VideoMeeting/roomManager/static/img/gifts/coin.png"></img>
            <div class="gift-text-title-coin">{gift.cost}</div>
          </div>
          <div class="gift-text-desc">{gift.desc}</div>
          <div class="gift-text-tip">{gift.tip}</div>
        </div>
      </div>
      <div class="gift-confirm">
        <div class="gift-number">
          <input type="radio" name="gift-numbers" style="display: none;" id={gift.number + "number1"} class="number1" onclick={() => changeGiftNumber(1, gift.number + "send")}></input>
          <label class="gift-number-check" for={gift.number + "number1"}>1</label>
          <input type="radio" name="gift-numbers" style="display: none;" id={gift.number + "number10"} class="number10" onclick={() => changeGiftNumber(10, gift.number + "send")}></input>
          <label class="gift-number-check" for={gift.number + "number10"}>10</label>
          <input type="radio" name="gift-numbers" style="display: none;" id={gift.number + "number100"} class="number100" onclick={() => changeGiftNumber(100, gift.number + "send")}></input>
          <label class="gift-number-check" for={gift.number + "number100"}>100</label>
        </div>
        <input class="gift-number-input" id={gift.number + "send"} type="number" value="1"></input>
        <button class="gift-send" onclick={() => sendGift(gift.number + "send", gift.name)}>发送</button>
      </div>
    </div>
  def isShowSelectAll(): Unit ={
    showShield.update(i => !i)
    val tfa = dom.document.getElementById("tran-for-all-tag")
    if(showDeleteAllTag==1 && tfa !=null){tfa.innerHTML = "&#xe666;";tfa.setAttribute("style","background-color:#23ade5")}
  }
  def tDeleteAll(): Unit ={
    ssw := List[String]()
    ssw_copy = new ListBuffer[String]()
    showAllTag.update(i=>false)
    showDeleteAll.update(i => false)
    showDeleteAllTag = 0
  }
  def showDeAll(e:dom.Event,sda:Int): Unit ={
    showDeleteAll.update(i => !i)
    val eta = e.target.asInstanceOf[Span]
    if(sda==0){eta.innerHTML = "&#xe666;" ;showDeleteAllTag =1;eta.setAttribute("style","background-color:#23ade5")}
    else{eta.innerHTML = "" ;showDeleteAllTag =0;eta.setAttribute("style","background-color:#f7f8f9")}
  }

  def deleteShieldWord(item:String)={
    if(ssw_copy.nonEmpty) ssw_copy -= item
    ssw.update(s =>
      if (!s.contains(item)) s else s.filter(!_.equals(item))
    )
    if(ssw_copy.isEmpty) tDeleteAll()
  }
  def addShieldWord(): Unit ={
    val sData = dom.document.getElementById("shield-data").asInstanceOf[Input]
    if(sData!=null && sData.value.trim != "" ) {
      if (!ssw_copy.contains(sData.value)) ssw_copy.append(sData.value)
      ssw.update(s =>
        if (!s.contains(sData.value)) s:+sData.value else s
      )
      showAllTag.update( i => true)
    }

  }

  //9.29如果是切换url造成的closeWs，watchType需要一个hash值来说明此页面没有播放器，不然会找不到标签
  def closeWS()={
    watchType.map(i=>
      if(i=="dash"){
        dashStreamer.reset(dashStreamer)
      }else{
        if(dom.document.getElementById("hls-video") != null){
          hlsStreamer.dispose(hlsStreamer)
        }
      }
    )
    websocketClient.closeWs
  }

  def setDcolor(e:dom.Event,str: String): Unit ={
    userSetDColor = str
    val cl = dom.document.getElementsByClassName("color-item").length
    for(i <- 0 until cl){
      dom.document.getElementsByClassName("color-item").item(i).asInstanceOf[Span].innerHTML = ""
    }
    e.target.asInstanceOf[Span].innerHTML = "&#xe666;"
  }

  var showToolBar = 0

  def showTool(i: Int): Unit ={
    val sd = dom.document.getElementById("shield-danmu")
    if(sd!=null)sd.setAttribute("style","display:none")
    if(i==1){
      if(showToolBar == 1){
        dom.document.getElementById("emoji-container-out").setAttribute("style","display:none")
        showToolBar = 0
        return
      }
      dom.document.getElementById("danmuka-color").setAttribute("style","display:none")
      Globals.fetchEmoji(1)
      dom.document.getElementById("emoji-container-out").setAttribute("style","display:block")
      showToolBar = 1
    }else{
      if(showToolBar == 2){
        dom.document.getElementById("danmuka-color").setAttribute("style","display:none")
        showToolBar = 0
        return
      }
      dom.document.getElementById("emoji-container-out").setAttribute("style","display:none")
      dom.document.getElementById("danmuka-color").setAttribute("style","display:block")
      showToolBar = 2
    }
  }

  var cmInitFlag = false

  def sendBarrage(roomId:Long)={
    if(cmInitFlag) {
      Globals.cmInit()
      cmInitFlag = false
    }
    dom.document.getElementById("emoji-container-out").setAttribute("style","display:none")
    dom.document.getElementById("danmuka-color").setAttribute("style","display:none")
    val b_area = dom.document.getElementById("s-commment").asInstanceOf[TextArea]
    if(dom.window.localStorage.getItem("isTemUser") != null){
      PopWindow.commonPop("请先登录以使用功能")
    }
    else if(b_area.value.length>20){
      val barrage = Comment(userId,roomId,b_area.value.substring(0,20),userSetDColor)
      b_area.value = ""
      websocketClient.sendMsg(barrage)
    }
    else if(b_area.value.trim.length == 0){
      JsFunc.alert("请勿发送空消息")
    }
    else{
      val barrage = Comment(userId,roomId,b_area.value,userSetDColor)
      b_area.value = ""
      websocketClient.sendMsg(barrage)
    }
  }

  def userJoinRoom(): Unit ={
    val data = SearchRoomReq(Some(userId),roomId).asJson.noSpaces
    Http.postJsonAndParse[SearchRoomRsp](Routes.UserRoutes.searchRoom,data).map{
      case Right(rsp) =>
        if(rsp.errCode==0 && rsp.roomInfo.isDefined){
          anchorName := rsp.roomInfo.get.userName
          anchorHeadImgUrl := rsp.roomInfo.get.headImgUrl
          val url = rsp.roomInfo.get.mpd.getOrElse("")
          dashurl = url
          hlsurl = url.replace("index","master").replace("mpd","m3u8")
//          dashStreamer = new DashType("http://10.1.120.144:80/dash/123.mpd")//测试用
          dashStreamer = new DashType(dashurl)
          hlsStreamer = new HlsType(hlsurl)
          dashStreamer.initialize(dashStreamer)
          println("dashurl；   "+url)
          streamerFlag = true
          //进入房间默认使用dash播放
          watchType := "dash"
          watchTypeChoose := <select onchange={(e:Event) => changeWatchType(e)} style="position:relative;left:85%;margin-bottom:10px">
            <option value="dash">使用dash观看</option>
            <option value="hls">使用Hls观看</option>
          </select>
          likeNumber := rsp.roomInfo.get.like
          //建立ws
          connectWebsocket()
          barragesList := List[(String,String,String)]()
        }else{
          PopWindow.commonPop(s"获取视频内容失败: ${rsp.msg}")
        }
      case Left(e) =>
        println("SearchRoomRsp error")
    }
  }

  def enterRoom():Unit = {
    //清空弹幕
    Globals.clearMsg()
    cmInitFlag = true
    isRoomLike = false
    userJoinRoom()
  }

  def askToStop()={
    ScalaWebSocket.sendMessage(BrowserJson.DisConnect(BrowserJson.EventId.DISCONNECT, audienceLiveId).asJson.noSpaces)
  }

  def askToConnect(userId:Long, roomId:Long)={
    //fixme ClientType
    val data = JoinReq(userId,roomId,ClientType.WEB)
    websocketClient.sendMsg(data)
  }

  def connectAnchor(anId:String, auId:String): Unit ={
    def callback()={
      ScalaWebSocket.sendMessage(BrowserJson.Connect(BrowserJson.EventId.CONNECT,anId,auId).asJson.noSpaces)
      Globals.webRtcStart(BrowserJson.EventId.Audience_SDP_OFFER)
      ScalaWebSocket.state = 2
    }
    ScalaWebSocket.setup(Routes.getWsSocketUri(auId,audienceLiveCode),callback())
  }

  private def wsStartHandler(event: Event): Unit ={
    val barrage = JudgeLike(userId, roomId)
    websocketClient.sendMsg(barrage)
  }
  private def wsMessageHandler(data:WsMsgRm):Unit ={
    data match {
      case JoinRsp(hostLiveId,joinInfo,errCode,msg) =>
      //申请连线
        if(errCode==300001){
          JsFunc.alert("房主未开通连线功能")
        }
        if(errCode==300002){
          JsFunc.alert("房主拒绝连线申请")
        }
        println("JoinRsp",errCode)
        if(errCode ==0 && hostLiveId.isDefined && joinInfo.isDefined) {
          audienceLiveId = joinInfo.get.liveId
          audienceLiveCode = joinInfo.get.liveCode
          connectAnchor(hostLiveId.get,audienceLiveId)
        }

      case LikeRoomRsp(errCode, msg) =>
        if(errCode == 0){
          if(! isRoomLike){
            isRoomLike = !isRoomLike
            dom.document.getElementById("attention-button").setAttribute("class", "attention-button-open")
          }
          else{
            isRoomLike = !isRoomLike
            dom.document.getElementById("attention-button").setAttribute("class", "attention-button-close")
          }
        }
        else{
           PopWindow.commonPop(s"点赞失败: $msg")
          if(errCode == 1001){
            isRoomLike = !isRoomLike
            dom.document.getElementById("attention-button").setAttribute("class", "attention-button-close")

            }
        }


      case JudgeLikeRsp(like, errCode, msg) =>
        println("have received JudgeLike")
        if(errCode == 0){
          if(like){
            isRoomLike = true
            dom.document.getElementById("attention-button").setAttribute("class", "attention-button-open")
          }
          else{
            isRoomLike = false
            dom.document.getElementById("attention-button").setAttribute("class", "attention-button-close")
          }
        }
        else{
          println(s"无法获取点赞状态，${msg}")
        }

      case ReFleshRoomInfo(roomInfo) =>
        //目前仅用于点赞数更新
        likeNumber := roomInfo.like


      case HostDisconnect(_) =>
      //房主断开连线通知
        enterRoom()
        ScalaWebSocket.sendMessage(BrowserJson.DisConnect(BrowserJson.EventId.DISCONNECT, audienceLiveId).asJson.noSpaces)

      case RcvComment(euserId,euserName,comment,color,extension) =>
      //所有用户留言通知
        val commentArea = dom.document.getElementById("comments").asInstanceOf[HTMLElement]
        var ctp = ""
        if(comment.substring(0,1).equals("+")){
          ctp = comment.substring(1)
        }else{
          ctp = comment
        }
        if(euserId == -1l){
          barragesList.update(b => b:+("[系统消息]",ctp,color))
        }else if(euserId == userId){
          barragesList.update(b => b:+("[自己]",ctp, if(color.equals("FFFFFF")) "9b39f4" else color))
        } else{
          barragesList.update(b => b:+(euserName,ctp,if(color.equals("FFFFFF")) "9b39f4" else color))
        }
        commentArea.scrollTop = commentArea.scrollHeight
        var flag = false
        for(i <- ssw_copy.indices){
          if(comment.contains(ssw_copy(i)))flag = true
        }
        if(!flag){
          // 当前comment中未添加礼物属性，此处只采用简单判断
//          if(sendFlag == 1){
//            Globals.sendGifts(comment)
//            sendFlag = 0
//          }else{
//            if(userId == myself) Globals.setCmtData(comment,1 ,color)
//            else Globals.setCmtData(comment,0,color)
//          }
            if(comment.contains("赠送礼物")){
              Globals.sendGifts(comment)
            }else{
              if(euserId == userId) Globals.setCmtData(comment,1 ,color)
              else Globals.setCmtData(comment,0,color)
            }
        }

      case UpdateRoomInfo2Client(newroomName,roomDec) =>


      case HostCloseRoom() =>
        //主播关闭房间
        println("HostCloseRoom-----------")
        websocketClient.closeWs
        PopWindow.commonPop("主播离开房间")

      case UpdateAudienceInfo(audienceList) =>
        audienceLists := audienceList
        audienceNums := audienceList.length

      case PingPackage =>
      case AccountSealed =>
          PopWindow.commonPop("您已经被封号，无法发送评论、点赞，解封请联系管理员！谢谢！")
      case msg@_ =>


    }
  }

  def connectWebsocket(): Unit = {
    val wsurl = rmWebScocketUri(dom.window.localStorage.getItem("userId").toLong,
      dom.window.localStorage.getItem("token").toString,
      dom.window.localStorage.getItem("roomId").toLong)
    websocketClient.setup(wsurl)
  }

  def changeWatchType(e:Event):Unit = {
    if(roomId == -1l){
      JsFunc.alert("切换无效，请先进入房间！")
    }else{
      watchType := e.target.asInstanceOf[Select].value
      if(e.target.asInstanceOf[Select].value == "hls"){
        hlsStreamer.initialize(hlsStreamer)
      }else{
        hlsStreamer.dispose(hlsStreamer)
        dashStreamer = new DashType(dashurl)
        dashStreamer.initialize(dashStreamer)
      }
    }
    cmInitFlag = true
  }

  def userLike(): Unit ={
    //消赞&点赞
    if(dom.window.localStorage.getItem("isTemUser") != null){
      PopWindow.commonPop("请先登录以使用功能")
    }
    else if(roomId != -1l) {
      if (isRoomLike) {
        val barrage = LikeRoom(userId, roomId, 0)
        websocketClient.sendMsg(barrage)
      }
      else {
        val barrage = LikeRoom(userId, roomId, 1)
        websocketClient.sendMsg(barrage)
      }
    }
    else{
      println("还没有进入房间，不要点赞")
    }
  }

  def sendGift(id: String, name: String) ={
    //id代表礼物种类， giftNumber代表礼物数量
    if(cmInitFlag) {
      Globals.cmInit()
      cmInitFlag = false
    }
    val giftNumber = dom.document.getElementById(id).asInstanceOf[Input].value.toInt
    if(dom.window.localStorage.getItem("isTemUser") != null){
      PopWindow.commonPop("请先登录以使用功能")
    }
    else if(roomId != -1l){
      val barrage = Comment(userId, roomId, s"用户${userId}赠送礼物: $name，${giftNumber}个")
      websocketClient.sendMsg(barrage)
      sendFlag = 1
    }
  }

  def changeGiftNumber(number: Int, id: String) ={
    dom.document.getElementById(id).asInstanceOf[Input].value = number.toString
  }

  def init() = {
    enterRoom()
  }

  override def render: Elem = {
    init()
    <div class="audience_body">
        <div class="audienceInfo">
          <div class="anchorInfo">
            {anchorInfo}
          </div>
          <div class="dash-video-player anchor-all" id="dash-video-player">
            {watchTypeChoose}
            {watchType.map(i =>
            if(i=="dash"){
              //dashjs
              <div>
                <video id="videoplayer" class="dash-video-player-video dash-js"></video>
                <div class="loading ccl-panel">
                  <img src="/VideoMeeting/roomManager/static/img/loading2.png"  class="load2" id="load2"></img>
                </div>
                <div class="ccl-panel">
                  <div class="abp">
                    <div id="commentCanvas" class="container"></div>
                  </div>
                </div>
                <div id="videoController" class="video-controller unselectable">
                  <div id="playPauseBtn" class="btn-play-pause" title="Play/Pause">
                    <span id="iconPlayPause" class="icon-play"></span>
                  </div>
                  <span id="videoTime" class="time-display">00:00:00</span>
                  <div id="fullscreenBtn" class="btn-fullscreen control-icon-layout" title="Fullscreen">
                    <span class="icon-fullscreen-enter"></span>
                  </div>
                  <div id="bitrateListBtn" class="control-icon-layout" title="Bitrate List">
                    <span class="icon-bitrate"></span>
                  </div>
                  <input type="range" id="volumebar" class="volumebar" value="1" min="0" max="1" step=".01"/>
                  <div id="muteBtn" class="btn-mute control-icon-layout" title="Mute">
                    <span id="iconMute" class="icon-mute-off"></span>
                  </div>
                  <div id="trackSwitchBtn" class="control-icon-layout" title="A/V Tracks">
                    <span class="icon-tracks"></span>
                  </div>
                  <div id="captionBtn" class="btn-caption control-icon-layout" title="Closed Caption">
                    <span class="icon-caption"></span>
                  </div>
                  <span id="videoDuration" class="duration-display">00:00:00</span>
                  <div class="seekContainer">
                    <div id="seekbar" class="seekbar seekbar-complete" >
                      <div id="seekbar-buffer" class="seekbar seekbar-buffer"></div>
                      <div id="seekbar-play" class="seekbar seekbar-play"></div>
                    </div>
                  </div>
                </div>
              </div>
            }else{
              //hls
              <div>
                <video id="hls-video" class="dash-video-player-video video-js vjs-default-skin" controls="controls" autoplay="autoplay" playsinline="true">
                  <source type="application/x-mpegURL" src={hlsurl} />
                </video>
                <div class="ccl-panel">
                  <div class="abp">
                    <div id="commentCanvas" class="container"></div>
                  </div>
                </div>
              </div>
            }
          )}
            <div class ="bottom-content">
              <input type="checkbox" name="gift-numbers" style="display: none;" id="bottom-like-button" disabled="1"></input>
              <label class="bottom-like" for="bottom-like-button"></label>
              <div class="bottom-gifts">
                {InteractiveText.giftsList.sortBy(_.cost).map{ gift =>
                <div class="bottom-gift">
                  <img src={gift.img} class="bottom-gift-img"></img>
                  {giftPop(gift)}
                </div>
              }}
              </div>
            </div>
          </div>
        </div>
        {roomLists}
      </div>
  }

}
