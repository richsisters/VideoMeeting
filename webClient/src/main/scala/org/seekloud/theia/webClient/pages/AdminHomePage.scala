package org.seekloud.VideoMeeting.webClient.pages

import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl._

import scala.xml.Elem
import org.seekloud.VideoMeeting.webClient.common.{Page, Routes}
import org.seekloud.VideoMeeting.webClient.util.{Globals, Http, TimeTool}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.AdminProtocol._

import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.generic.auto._
import mhtml.{Rx, Var}
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo._
import org.seekloud.VideoMeeting.webClient.pages.MainPage.recordInfo
import io.circe.syntax._
import org.seekloud.VideoMeeting.webClient.common.Components.{AdminHeader, PopWindow}

/**
  * create by zhaoyin
  * 2019/9/26  3:02 PM
  */
class AdminHomePage extends Page{
  //管理员在获取页面信息时，如果发现该用户没有登录，则直接扔回主页面
  //同时，也要修改顶部的内容

  private val recordList = Var(List.empty[RecordInfo])
  private val recordNumber = Var(0)
  private var recordPageNum = 0
  private val showPages = 5
  private val perPageSize = 10 //使用分页插件，一页10个
  private val liveList = Var(List.empty[RoomInfo])
  private val liveNumber = Var(0)
  private val userNumber = Var(0)
  private var goToPage = 0
  private val userList = Var(List.empty[UserInfo])
  private var userNum = 0
  private var goToUserPage = 0

  def removeViedo(info: CommonInfo.RecordInfo):Unit={
    val data = DeleteRecordReq(List(info.recordId)).asJson.noSpaces
    Http.postJsonAndParse[CommonRsp](Routes.AdminRoutes.adminDeleteRecord,data).map{
      case Right(rsp) =>
        if(rsp.errCode==0){
          getRecordList("time",1,perPageSize)
        }else{
          PopWindow.commonPop("删除录像失败！")
        }
      case Left(e) =>
        println(s"removeViedo error: $e")
    }
  }

  private val recordArea:Rx[Elem] = recordList.map{ rl =>

    def createRecordItem(item: RecordInfo)={

      <div class="adminVideoItem admin-record-item">
        <div class="recordVideo">
          <img class="record-img" src={item.coverImg}></img>
          <div class="removeVideo" onclick={()=> removeViedo(item)}>× 移除</div>
          <div class="s-info">
            <div class="record-user">{item.userName}</div>
            <img class="img-dianzan" src="/VideoMeeting/roomManager/static/img/homePage/like.png"></img>
            <div class="record-like">{item.likeNum}</div>
          </div>
        </div>
        <div class="recordDesc">
          <div class="r-title">{item.recordName}</div>
          <div class="r-u-info">
            <div class="r-id"></div>
            <div class="r-time">{TimeTool.parseDateLikeBiliBili(item.startTime)}</div>
          </div>
        </div>
      </div>
    }

    <div class="record" style="margin-top: 40px;max-width:1000px">
      <div class="zone-title">
        <div class="record-head-img">
          <img class="img-record" src="/VideoMeeting/roomManager/static/img/homePage/record.png"></img>
        </div>
        <div class="record-head-text">录像</div>
        <div class="record-head-number">当前共有<span>{recordNumber}</span>个录像</div>
        <div class="record-head-refresh">
          <img class="img-refresh" src="/VideoMeeting/roomManager/static/img/homePage/refresh.png" style="float: right;" onclick={()=>getRecordList("time",1,10)}></img>
        </div>
      </div>
      <div class="recordItem-list">
        {rl.map(createRecordItem)}
      </div>
    </div>

  }

  def goToNextPage() = {
    val ele = dom.document.getElementById("bp-3-element").asInstanceOf[HTMLElement]
    for(i <- 0 until  ele.childElementCount){
      if(ele.childNodes.item(i).asInstanceOf[HTMLElement].className.contains("active")){
        goToPage = ele.childNodes.item(i).childNodes.item(0).asInstanceOf[HTMLElement].textContent.toInt
      }
    }
    getRecordList("time",goToPage,perPageSize)
  }

  def banOnAnchor(item: RoomInfo):Unit = {
    val data = BanOnAnchor(item.roomId).asJson.noSpaces
    Http.postJsonAndParse[CommonRsp](Routes.AdminRoutes.adminbanOnAnchor,data).map{
      case Right(rsp) =>
        if(rsp.errCode == 0){
          //刷新页面
          getRoomList()
        }else{
          PopWindow.commonPop("禁播失败！")
        }
      case Left(e) =>
        println(s"banOnAnchor error: $e")
    }
  }

  private val liveArea:Rx[Elem] = liveList.map{list =>

    def createLiveItem(item: RoomInfo)={
      <div class="adminVideoItem">
        <div class="recordVideo">
          <img class="record-img" src={item.coverImgUrl}></img>
          <div class="admincloseLive" onclick={() => banOnAnchor(item)}>禁播</div>
          <div class="s-info">
            <div class="record-user">{item.userName}</div>
            <img class="img-dianzan" src="/VideoMeeting/roomManager/static/img/homePage/like.png"></img>
            <div class="record-like">{item.like}</div>
          </div>
        </div>
        <div class="recordDesc">
          <div class="r-title">{s"房间：${item.roomId}"}</div>
          <div class="r-u-info">
            <div class="r-id"></div>
            <div class="r-time"></div>
          </div>
        </div>
      </div>
    }

    <div class="record">
      <div class="zone-title">
        <div class="record-head-img">
          <img class="img-record" src="/VideoMeeting/roomManager/static/img/正在直播.gif"></img>
        </div>
        <div class="record-head-text">直播</div>
        <div class="record-head-number">当前共有<span>{liveNumber}</span>个直播</div>
        <div class="record-head-refresh">
          <img class="img-refresh" src="/VideoMeeting/roomManager/static/img/homePage/refresh.png" style="float: right;" onclick={()=>getRoomList()}></img>
        </div>
      </div>
      <div class="recordItem-list">
        {list.map(createLiveItem)}
      </div>
    </div>
  }

  private val userArea:Rx[Elem] = userList.map{list =>

    def creatUserItem(item:UserInfo) = {
      val seal = Var(item.seal)
      <div style="display:flex; line-height:35px;position:relative;
      align-item:center;background:none!important" class="roomItem">
        <img src={item.headImgUrl} style="margin-right:10px"></img>
        <div style="#333">{item.userName}</div>
        {seal.map(i=>
        if(i){
          <div onclick={() =>{
            def cancelSealAccount():Unit={
              val data = CancelSealAccountReq(item.userId).asJson.noSpaces
              Http.postJsonAndParse[CommonRsp](Routes.AdminRoutes.admincancelSealAccount,data).map{
                case Right(rsp) =>
                  if(rsp.errCode==0){
                    seal:=false
                  }
                case Left(e) =>
                  println(s"error happen:$e")
              }
            }
            cancelSealAccount()
          }} class="cancelSealed">解封</div>
        }else{
          <div onclick={() =>{
            def sealAccountReq():Unit = {
              val time = System.currentTimeMillis()
              val data = SealAccountReq(item.userId,time).asJson.noSpaces
              Http.postJsonAndParse[CommonRsp](Routes.AdminRoutes.adminsealAccount,data).map{
                case Right(rsp) =>
                  if(rsp.errCode==0){
                    //成功封禁，并改为 已封禁
                    seal:=true
                  }
                case Left(e) =>
                  println(s"error happen:$e")
              }
            }
            sealAccountReq()
          }} class="sealed">封号</div>
        }
      )}
      </div>
    }
    <div class="userContain">
      <div class="zone-title">
        <img class="img-record" src="/VideoMeeting/roomManager/static/img/用户.png"></img>
        <div class="record-head-number">当前共有<span>{userNumber}</span>个用户</div>
      </div>
      <div class="roomContain" style="overflow-y:unset">
        {list.map(creatUserItem)}
      </div>
    </div>

  }

  def goToNextUserPage():Unit = {
    val ele = dom.document.getElementById("user-3-element").asInstanceOf[HTMLElement]
    for(i <- 0 until ele.childElementCount){
      if(ele.childNodes.item(i).asInstanceOf[HTMLElement].className.contains("active")){
        goToUserPage = ele.childNodes.item(i).childNodes.item(0).asInstanceOf[HTMLElement].textContent.toInt
      }
    }
    getUserList(goToUserPage,8)
  }

  def getRoomList():Unit = {
    Http.getAndParse[RoomListRsp](Routes.UserRoutes.getRoomList).map{
      case Right(rsp) =>
        if(rsp.errCode == 0){
          if(rsp.roomList.isDefined){
            liveList := rsp.roomList.get
            liveNumber := rsp.roomList.get.length
          }
        }else{
          dom.window.location.hash = s"#/Home"
        }
      case Left(e) =>
        println(s"RoomListRsp error: $e")
    }
  }

  def getRecordList(sortBy:String,pageNum:Int,pageSize:Int):Unit={
    val recordListUrl = Routes.UserRoutes.getRecordList(sortBy,pageNum,pageSize)
    Http.getAndParse[GetRecordListRsp](recordListUrl).map{
      case Right(rsp) =>
        if(rsp.errCode == 0){
          recordList := rsp.recordInfo
          recordNumber := rsp.recordNum
          recordPageNum = if(rsp.recordNum % perPageSize != 0) (rsp.recordNum / perPageSize) + 1 else rsp.recordNum / perPageSize
          Globals.pagePaginator("bp-3-element",pageNum,showPages,recordPageNum)
        }else{
          dom.window.location.hash = s"#/Home"
        }
      case Left(e) =>
        println(s"errors happen: $e")
    }
  }

  def getUserList(pageNum:Int,pageSize:Int) = {
    val data = GetUserListReq(pageNum, pageSize).asJson.noSpaces
    Http.postJsonAndParse[GetUserListRsp](Routes.AdminRoutes.admingetUserList,data).map{
      case Right(rsp) =>
        if(rsp.errCode==0){
          userList := rsp.userList
          userNum = rsp.totalNum
          userNumber := rsp.totalNum
          val userPageNum = if(rsp.totalNum % 8 != 0) (rsp.totalNum / 8) + 1 else rsp.totalNum / 8
          Globals.pagePaginator("user-3-element",pageNum,3,userPageNum)
        }else{
          dom.window.location.hash = s"#/Home"
        }
      case Left(e) =>
        println(s"error happen:$e")
    }
  }


  def init() = {
    //获取所有直播
    getRoomList()
    //获取第一页的录像
    getRecordList("time",1,perPageSize)
    //获取用户列表
    getUserList(1,8)
  }

  override def render: Elem = {
    init()
    <div class="admin-main">
      {AdminHeader.render}
      <div class="adminContain">
        <div>
          {liveArea}
          {recordArea}
          <div id="record-pageContainer">
            <ul id="bp-3-element" onclick={()=>goToNextPage()}></ul>
          </div>
        </div>
        <div>
          {userArea}
          <div id="user-pageContainer" style="text-align:center">
            <ul id="user-3-element" onclick={()=>goToNextUserPage()}></ul>
          </div>
        </div>
      </div>
    </div>

  }

}
