package org.seekloud.VideoMeeting.webClient.util

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import org.scalajs.dom.raw._
/**
  * create by zhaoyin
  * 2019/7/21  3:05 PM
  */
object RtmpStreamerJs {

  @js.native
  @JSGlobal("DashType")
  class DashType(var url:String) extends js.Any {
    def initialize(that: DashType) = js.native
    def reset(that:DashType) = js.native
  }

  @js.native
  @JSGlobal("HlsType")
  class HlsType(var url:String) extends js.Any {
    def initialize(that: HlsType) = js.native
    def dispose(that:HlsType) = js.native
  }

  @js.native
  @JSGlobal("DisconnectType")
  class DisconnectType() extends js.Any {
    def disconnect(that:DisconnectType) = js.native
  }

  @js.native
  @JSGlobal("TakePhotoFile")
  class TakePhotoFile() extends js.Any {
    def takephoto(that:TakePhotoFile) = js.native
    def takephotoFile(that:TakePhotoFile):File = js.native
  }

  @js.native
  @JSGlobal("ChangeImg2File")
  class ChangeImg2File(src:String) extends js.Any {
    def changeImg2File(that:ChangeImg2File):File = js.native
  }

}
