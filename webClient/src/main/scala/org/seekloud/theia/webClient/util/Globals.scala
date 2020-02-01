package org.seekloud.VideoMeeting.webClient.util

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

/**
  * Created by sky
  * Date on 2019/7/5
  * Time at 下午3:38
  * 可以直接调用其他js文件中定义的global对象
  */
@js.native
@JSGlobalScope
object Globals extends js.Object {
  def messageHandler(s: String): Unit = js.native
  def webRtcStart(s: String): Unit = js.native
  def webRtcStop(): Unit = js.native
  def setCmtData(str:String,isMe:Int,cl:String):Unit=js.native
  def clearMsg():Unit = js.native
  def cmInit():Unit = js.native
  def fetchEmoji(emojiType:Int):Unit = js.native
  def sendGifts(str:String):Unit=js.native
  def pagePaginator(tagId:String,currentPage:Int,showTagNum:Int, totalPages:Int):Unit = js.native
  def playMp4(mp4Url:String):Unit = js.native
}
