package org.seekloud.VideoMeeting.webClient.util

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSGlobal, ScalaJSDefined}

/**
  * @author Jingyi
  * @version 创建时间：2018/11/1
  */
object layDate {

  @js.native
  @JSGlobal("laydate")
  object laydate extends js.Object{
    def render(option: Options) : Unit = js.native
  }


  @ScalaJSDefined
  trait Options extends js.Object{
    val elem: js.UndefOr[String] = js.undefined
    def done: js.UndefOr[js.Function0[Any]] = js.undefined
    def ready: js.UndefOr[js.Function0[Any]] = js.undefined
    val range: js.UndefOr[Any] = js.undefined
    val max: js.UndefOr[Any] = js.undefined
    val value: js.UndefOr[String] = js.undefined
    val isInitValue: js.UndefOr[Boolean] = js.undefined
    def change:js.UndefOr[js.Function0[Any]] = js.undefined
    val format: js.UndefOr[String] = js.undefined
    val `type`: js.UndefOr[String] = js.undefined
    val theme: js.UndefOr[String] = js.undefined
    val trigger:js.UndefOr[String] = js.undefined
  }
}
