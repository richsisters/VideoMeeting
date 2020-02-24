package org.seekloud.VideoMeeting.webClient.pages

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.Input
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.{SignIn, SignInRsp, SignUp, SignUpRsp}
import org.seekloud.VideoMeeting.webClient.common.Components.PopWindow
import org.seekloud.VideoMeeting.webClient.common.{Page, Routes}
import org.seekloud.VideoMeeting.webClient.pages.MainPage.{menuShow, refresh, userShowImg, userShowName}
import org.seekloud.VideoMeeting.webClient.util.Http
import org.seekloud.VideoMeeting.webClient.common.PageSwitcher
import mhtml._
import org.scalajs.dom
import org.seekloud.VideoMeeting.webClient.util.{Http, JsFunc}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol._

import scala.xml.Elem
import org.seekloud.VideoMeeting.webClient.common.Routes
import io.circe.syntax._
import io.circe.generic.auto._
import org.seekloud.VideoMeeting.webClient.common.Components.PopWindow

import scala.concurrent.ExecutionContext.Implicits.global

class Home extends Page{

  override def render: Elem = {
    <div style="min-height: 800px">
      <div class="record" style="margin-top: 40px;">
        <div class="zone-title">
          <div class="record-head-img">
            <img class="img-record" src="/VideoMeeting/roomManager/static/img/homePage/record.png"></img>
          </div>
          <div class="record-head-text">录像</div>
          <div class="record-head-number">登录后才可以查看会议录像哦～</div>
        </div>
      </div>
    </div>
  }

}
