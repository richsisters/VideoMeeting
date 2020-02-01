package org.seekloud.VideoMeeting.webClient.pages

import java.util.Date

import org.scalajs.dom
import org.scalajs.dom.html.{Div, Input}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.StatisticsProtocol.{LoginInDataByHourReq, LoginInDataInfo, LoginInDataRsp, WatchDataByHourInfo, WatchDataByHourReq, WatchDataByHourRsp, WatchProfileDataByRecordIdReq, WatchProfileDataByRecordIdRsp, WatchProfileInfo}
import org.seekloud.VideoMeeting.webClient.common.Components.{AdminHeader, PopWindow}
import org.seekloud.VideoMeeting.webClient.common.{Page, Routes}
import org.seekloud.VideoMeeting.webClient.util.{EChart, Http, TimeTool}
import io.circe.generic.auto._
import io.circe.syntax._
import mhtml.Var
import org.scalajs.dom.raw.Event
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.GetRecordListRsp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr
import scala.util.matching.Regex
import scala.xml.Elem

/**
  * User: 13
  * Date: 2019/9/29
  * Time: 16:03
  */
class AdminPeoplePage extends Page{

  def init() ={
    //初始化时间选择框
    import org.seekloud.VideoMeeting.webClient.util.layDate._
    laydate.render(
      new Options {
        override val elem: UndefOr[String] = "#people-date"
        override val `type`: UndefOr[String] = "datetime"
        override val range: UndefOr[Any] = true
        override val value: UndefOr[String] = TimeTool.dateFormatDefault(new Date().getTime - 86400000) + " - " + TimeTool.dateFormatDefault(new Date().getTime)
        override def done(): UndefOr[js.Function0[Any]] = dom.window.setTimeout(()=>obtainStat1(), 500).asInstanceOf[js.Function0[Any]]
      }
    )
    laydate.render(
      new Options {
        override val elem: UndefOr[String] = "#record-time"
        override val `type`: UndefOr[String] = "datetime"
        override val range: UndefOr[Any] = true
        override val value: UndefOr[String] = TimeTool.dateFormatDefault(new Date().getTime - 86400000) + " - " + TimeTool.dateFormatDefault(new Date().getTime)
        override def done(): UndefOr[js.Function0[Any]] = dom.window.setTimeout(()=>obtainStat2(), 500).asInstanceOf[js.Function0[Any]]
      }
    )
    //初始化表三
    val recordListUrl = Routes.UserRoutes.getRecordList("view",1,1)
    Http.getAndParse[GetRecordListRsp](recordListUrl).map{
      case Right(rsp) =>
        if(rsp.errCode == 0){
          dom.document.getElementById("record-id").asInstanceOf[Input].value = rsp.recordInfo.head.recordId.toString
          obtainStat2()
        }else{
          println(s"errors happen rsp: ${rsp.msg}")
        }
      case Left(e) =>
        println(s"errors happen: $e")
    }
    dom.document.getElementById("record-time").setAttribute("disabled","true")
    //初始化表一
    obtainStat0()
    //初始化表二，需等待laydate就绪，laydate没有找到合适的回调函数，暂时用延时等待
    dom.window.setTimeout(()=>obtainStat1(), 500)
  }

  //全局索引图表信息获取
  def obtainStat0(): Unit ={
    Http.getAndParse[LoginInDataRsp](Routes.AdminRoutes.getLoginData).map{
      case Right(rsp) =>
        if(rsp.errCode==0){
          addChart0(rsp.data)
        }else{
          println(s"error happen:${rsp.msg}")
        }
      case Left(e) =>
        println(s"error happen:$e")
    }
  }
  //时间索引图表信息获取
  def obtainStat1(): Unit ={
    val timeStr = dom.document.getElementById("people-date").asInstanceOf[Input].value
    val startTime = TimeTool.parseDateTime(timeStr.split(" - ").head).getTime().toLong
    val endTime = TimeTool.parseDateTime(timeStr.split(" - ").last).getTime().toLong
    val data = LoginInDataByHourReq(startTime, endTime).asJson.noSpaces
    Http.postJsonAndParse[LoginInDataRsp](Routes.AdminRoutes.loginDataByHour,data).map{
      case Right(rsp) =>
        if(rsp.errCode==0){
          addChart1(rsp.data)
        }else{
          println(s"error happen:${rsp.msg}")
        }
      case Left(e) =>
        println(s"error happen:$e")
    }
  }
  //视频图表信息获取
  def obtainStat2(): Unit ={
    val recordId = dom.document.getElementById("record-id").asInstanceOf[Input].value.toLong
    dom.document.getElementById("record-time-select").asInstanceOf[Input].value match{
      case "hour" =>
        val watchObserveUrl = Routes.AdminRoutes.watchObserveByHour
        val timeStr = dom.document.getElementById("record-time").asInstanceOf[Input].value
        val startTime = TimeTool.parseDateTime(timeStr.split(" - ").head).getTime().toLong
        val endTime = TimeTool.parseDateTime(timeStr.split(" - ").last).getTime().toLong
        Http.postJsonAndParse[WatchDataByHourRsp](watchObserveUrl, WatchDataByHourReq(recordId, startTime, endTime).asJson.noSpaces).map{
          case Right(rsp) =>
            if(rsp.errCode==0){
              addChart3(rsp.data)
            }else{
              println(s"error happen:${rsp.msg}")
            }
          case Left(e) =>
            println(s"error happen:$e")
        }
      case "all" =>
        val watchObserveUrl = Routes.AdminRoutes.watchObserve
        Http.postJsonAndParse[WatchProfileDataByRecordIdRsp](watchObserveUrl, WatchProfileDataByRecordIdReq(recordId).asJson.noSpaces).map{
          case Right(rsp) =>
            if(rsp.errCode==0 && rsp.data.isDefined){
              rsp.data.foreach(dat => addChart2(dat, recordId))
            }
            else if(rsp.errCode==0 && rsp.data.isEmpty){
              PopWindow.commonPop(s"视频(id $recordId)没有数据")
            }
            else{
              println(s"error happen:${rsp.msg}")
            }
          case Left(e) =>
            println(s"error happen:$e")
        }
      case _ => println("error")
    }
  }

  //全局索引图表
  def addChart0(stat: List[LoginInDataInfo]): Unit ={

    def dayNum2Str(dayNum: Int) ={
      dayNum match {
        case 0 => "时"
        case 1 => "天"
        case 7 => "周"
        case 30 => "月"
        case _ => "none"
      }
    }

    val eChart = EChart.ECharts
    val newChart = eChart.init(dom.document.getElementById("all-chart").asInstanceOf[Div], "light")
    val myChart = newChart.get

    val tooltip = new EChart.Tooltip()
    val xAxis = new EChart.XAxis(stat.map(t => dayNum2Str(t.dayNum)).toJSArray)
    val yAxis = new EChart.YAxis()

    val title = new EChart.Title("平台登录数目统计汇总")
    val legend = new EChart.Legend(Array("访问次数","访问人数").toJSArray)
    val series = Array(
      new EChart.Series("访问次数","bar",true,stat.map(_.pv).toJSArray),
      new EChart.Series("访问人数","bar",true,stat.map(_.uv).toJSArray)
    ).toJSArray
    val option = new EChart.Option(title, tooltip, legend, xAxis, yAxis, series)
    myChart.clear()
    myChart.setOption(option)
  }
  //按时间索引图表
  def addChart1(stat: List[LoginInDataInfo]): Unit ={
    val eChart = EChart.ECharts
    val newChart = eChart.init(dom.document.getElementById("people-chart").asInstanceOf[Div], "light")
    val myChart = newChart.get

    val tooltip = new EChart.Tooltip()
    val xAxis = new EChart.XAxis(stat.map(t => TimeTool.dateFormatDefault(t.timestamp)).toJSArray)
    val yAxis = new EChart.YAxis()

    val title = new EChart.Title("小时平台登录数目统计")
    val legend = new EChart.Legend(Array("访问次数","访问人数").toJSArray)
    val series = Array(
      new EChart.Series("访问次数","line",true,stat.map(_.pv).toJSArray),
      new EChart.Series("访问人数","line",true,stat.map(_.uv).toJSArray)
    ).toJSArray
    val option = new EChart.Option(title, tooltip, legend, xAxis, yAxis, series)
    myChart.clear()
    myChart.setOption(option)
  }
  //视频Id索引图表
  def addChart2(stat: WatchProfileInfo, id: Long): Unit ={
    val eChart = EChart.ECharts
    val newChart = eChart.init(dom.document.getElementById("record-chart").asInstanceOf[Div], "light")
    val myChart = newChart.get

    val tooltip = new EChart.Tooltip()
    val xAxis = new EChart.XAxis(Array("用户访问次数", "游客访问次数", "用户访问人数").toJSArray)
    val yAxis = new EChart.YAxis()

    val title = new EChart.Title("录像登录数目统计")
    val legend = new EChart.Legend(Array(s"$id").toJSArray)
    val series = Array(
      new EChart.Series(s"$id","bar",true,Array(stat.pv4SU, stat.pv4TU, stat.uv4SU).toJSArray,"50%"),
    ).toJSArray
    val option = new EChart.Option(title, tooltip, legend, xAxis, yAxis, series)
    myChart.clear()
    myChart.setOption(option)
  }
  //视频Id时间索引图表
  def addChart3(stat: List[WatchDataByHourInfo]): Unit ={
    val eChart = EChart.ECharts
    val newChart = eChart.init(dom.document.getElementById("record-chart").asInstanceOf[Div], "light")
    val myChart = newChart.get

    val tooltip = new EChart.Tooltip()
    val xAxis = new EChart.XAxis(stat.map(t => TimeTool.dateFormatDefault(t.timestamp)).toJSArray)
    val yAxis = new EChart.YAxis()

    val title = new EChart.Title("录像观看数目统计")
    val legend = new EChart.Legend(Array("用户访问次数", "游客访问次数", "用户访问人数").toJSArray)
    val series = Array(
      new EChart.Series(s"用户访问次数","line",true,stat.map(_.pv4SU).toJSArray),
      new EChart.Series(s"游客访问次数","line",true,stat.map(_.pv4TU).toJSArray),
      new EChart.Series(s"用户访问人数","line",true,stat.map(_.uv4SU).toJSArray)
    ).toJSArray
    val option = new EChart.Option(title, tooltip, legend, xAxis, yAxis, series)
    myChart.clear()
    myChart.setOption(option)
  }

  //变更表三的类型
  def changeSelectAct(): Unit ={
    dom.document.getElementById("record-time-select").asInstanceOf[Input].value match{
      case "hour" =>
        dom.document.getElementById("record-time").removeAttribute("disabled")
        obtainStat2()
      case _ =>
        dom.document.getElementById("record-time").setAttribute("disabled","true")
        obtainStat2()
    }
  }

  //限制只输入数字
  def onlyNumber(str: String): Option[String] ={
    val number = new Regex("[^0-9]")
    number.findFirstIn(str).map{ _ =>
      PopWindow.commonPop("只能输入数字")
      number.replaceAllIn(str, "")
    }
  }

  override def render: Elem = {
    dom.window.setTimeout(() => init(), 0)
    <div class="admin-main">
      {AdminHeader.render}
      <div class="adminContain">
        <div class="adminContain-chart">
          <div class="people-cantain">
            <div class="people-input"></div>
            <div id="all-chart" style="width: 800px; height: 400px;"></div>
          </div>
          <div class="people-cantain">
            <div class="people-input">
              <div class="people-date-text">选择时间</div>
              <input type="text" id="people-date" class="people-date"></input>
            </div>
            <div id="people-chart" style="width: 800px; height: 400px;"></div>
          </div>
          <div class="people-cantain">
            <div class="people-input">
              <div class="record-id-text">录像Id</div>
              <input type="text" id="record-id" class="record-id" onkeyup={(e: Event) => onlyNumber(e.target.asInstanceOf[Input].value).foreach(a => e.target.asInstanceOf[Input].value = a)} onchange={(e: Event) => obtainStat2()}></input>
              <div class="record-time-text">时间</div>
              <select id="record-time-select" onchange={ () => changeSelectAct()}>
                <option value="all">总量</option>
                <option value="hour">小时</option>
              </select>
              <input type="text" id="record-time" class="record-time"></input>
            </div>
            <div id="record-chart" style="width: 800px; height: 400px;"></div>
          </div>
        </div>
      </div>
    </div>
  }

}
