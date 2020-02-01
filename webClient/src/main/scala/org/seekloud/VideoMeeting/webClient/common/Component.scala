package org.seekloud.VideoMeeting.webClient.common

import scala.language.implicitConversions
import scala.xml.Elem
/**
  * create by zhaoyin
  * 2019/1/31  5:09 PM
  */
trait Component {

  def render: Elem

}

object Component {
  implicit def component2Element(comp: Component): Elem = comp.render
}