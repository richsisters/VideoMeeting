package org.seekloud.VideoMeeting.webClient.pages

import io.circe.syntax._
import io.circe.generic.auto._
import mhtml._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.ExecutionContext.Implicits.global
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._
import org.seekloud.VideoMeeting.protocol.ptcl.CommonInfo.{RecordInfo, RoomInfo}
import org.seekloud.VideoMeeting.webClient.common.Components.PopWindow
import org.seekloud.VideoMeeting.webClient.common.{Page, Routes}
import org.seekloud.VideoMeeting.webClient.util.{Globals, Http, TimeTool}
import org.seekloud.VideoMeeting.webClient.pages.MainPage.recordInfo
import org.seekloud.VideoMeeting.webClient.pages.MainPage.{recordId, recordTime}
import scala.xml.Elem

class RecordList extends Page{
  private val recordList = Var(List.empty[RecordInfo])
  private val recordNumber = Var(0)
  private var recordPageNum = 0
  private val showPages = 5
  private val perPageSize = 10 //使用分页插件，一页10个
  private val liveList = Var(List.empty[RoomInfo])
  private val liveNumber = Var(0)
  private var goToPage = 0
  //观看录像
  def watchRecord(record:RecordInfo) = {
    recordInfo = record
    dom.window.localStorage.setItem("recordName", record.recordName)
    dom.window.localStorage.setItem("recordCoverImg", record.coverImg)
    dom.window.localStorage.setItem("recordStartTime", record.startTime.toString)
    if(dom.window.localStorage.getItem("userName") == null){
      //如果没有登录，就获取临时用户信息
      MainPage.temUserLogin(-1l)
    }
    //若已经登录，就直接跳转进入观众页
    dom.window.location.hash = s"#/Record/${recordInfo.roomId}/${recordInfo.startTime}"
  }

  private val recordArea:Rx[Elem] = recordList.map{ rl =>

    def createRecordItem(item: RecordInfo)={

      <div class="recordItem" onclick={()=>watchRecord(item)}>
        <div class="recordVideo">
          <img class="record-img" src={item.coverImg}></img>
          <div class="s-info">
            <div class="record-user">{item.userName}</div>
            <img class="img-dianzan" src="/VideoMeeting/roomManager/static/img/homePage/like.png"></img>
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
    <div class="record" style="margin-top: 40px;">
      <div class="zone-title">
        <div class="record-head-img">
          <img class="img-record" src="/VideoMeeting/roomManager/static/img/homePage/record.png"></img>
        </div>
        <div class="record-head-text">录像</div>
        <div class="record-head-number">您有权限查看<span>{recordNumber}</span>个录像</div>
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
    val ele = dom.document.getElementById("bp-4-element").asInstanceOf[HTMLElement]
    for(i <- 0 until  ele.childElementCount){
      if(ele.childNodes.item(i).asInstanceOf[HTMLElement].className.contains("active")){
        goToPage = ele.childNodes.item(i).childNodes.item(0).asInstanceOf[HTMLElement].textContent.toInt
      }
    }
    getRecordList("time",goToPage,perPageSize)
  }

  def getRecordList(sortBy:String,pageNum:Int,pageSize:Int):Unit={
    val userId = dom.window.localStorage.getItem("userId").toLong
    val recordListUrl = Routes.UserRoutes.getRecordList(userId,sortBy,pageNum,pageSize)
    Http.getAndParse[GetRecordListRsp](recordListUrl).map{
      case Right(rsp) =>
        if(rsp.errCode == 0){
          recordList := rsp.recordInfo
          recordNumber := rsp.recordNum
          recordPageNum = if(rsp.recordNum % perPageSize != 0) (rsp.recordNum / perPageSize) +1 else rsp.recordNum / perPageSize
          Globals.pagePaginator("bp-4-element",pageNum,showPages,recordPageNum)
        }
      case Left(e) =>
        println(s"errors happen: $e")
    }
  }

  override def render: Elem = {
    //获取所有直播
    //获取第一页的录像
    getRecordList("time",1,perPageSize)
    <div style="min-height: 800px">
      {recordArea}
      <div id="record-pageContainer">
        <ul id="bp-4-element" onclick={()=>goToNextPage()}></ul>
      </div>
    </div>
  }

}
