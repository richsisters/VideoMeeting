package org.seekloud.VideoMeeting.webClient.pages

import org.seekloud.VideoMeeting.webClient.common.{Page}
import scala.xml.Elem

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
