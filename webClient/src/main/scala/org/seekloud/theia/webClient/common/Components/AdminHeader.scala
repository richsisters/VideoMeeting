package org.seekloud.VideoMeeting.webClient.common.Components

import org.scalajs.dom
import org.scalajs.dom.html.Input

import scala.xml.Elem

/**
  * User: 13
  * Date: 2019/9/29
  * Time: 15:57
  */
object AdminHeader {

  def changePage(page:Int)={
    page match {
      case 0 => dom.window.location.hash = "#/Admin"
      case 1 => dom.window.location.hash = "#/Admin/People"
      case _ => dom.window.location.hash = "#/Admin"
    }
  }

  def render: Elem ={
    dom.window.setTimeout(() => dom.document.getElementById("admin-header-out").asInstanceOf[Input].click(), 0)
    <div class="admin-header">
      <label class="admin-header-out" for="admin-header-out"></label>
      <input id="admin-header-out" type="checkbox" style="display: none;"></input>
      <div class="admin-header-list">
        <div class="admin-header-button" onclick={()=>changePage(0)}>主页</div>
        <div class="admin-header-button" onclick={()=>changePage(1)}>人数统计</div>
      </div>
    </div>
  }

}
