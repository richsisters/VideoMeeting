package org.seekloud.VideoMeeting.webClient.common

import scala.language.implicitConversions
import scala.xml.Elem
/**
  * create by zhaoyin
  * 2019/1/31  5:08 PM
  */
trait Page extends Component {

  def render: Elem

}

object Page{
  implicit def page2Element(page: Page): Elem = page.render
}

