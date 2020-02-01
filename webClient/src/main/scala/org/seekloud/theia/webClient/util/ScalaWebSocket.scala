package org.seekloud.VideoMeeting.webClient.util

import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import org.scalajs.dom.raw.{Event, MessageEvent, WebSocket}
import org.seekloud.VideoMeeting.webrtcMessage.ptcl.BrowserJson
import org.seekloud.VideoMeeting.webrtcMessage.ptcl.BrowserJson.EventId

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/**
  * Created by sky
  * Date on 2019/7/1
  * Time at 下午12:59
  *
  * scala.js实现socket连接
  * 将发送消息函数设置为全局
  */

@JSExportTopLevel("ScalaWebSocket")
object ScalaWebSocket {
  /**
    * 0:disconnect
    * 1:connect for anchor
    * 2:connect for audience
    **/
  var state: Int = 0
  private var webSocketStreamOpt: Option[WebSocket] = None

  def setup(wsUrl: String, callBack: => Unit = {}): Unit = {
    println("url:", wsUrl)
    if (webSocketStreamOpt.nonEmpty) {
      println(s"webRtc_websocket已经启动")
    } else {
      val websocketStream = new WebSocket(wsUrl)

      webSocketStreamOpt = Some(websocketStream)
      webSocketStreamOpt.get.onopen = { event: Event =>
        println(s"webRtc_websocket已经建立")
        state = 1
        Shortcut.schedule(() => sendMessage(Json.obj(("id",Json.fromString(EventId.PING))).asJson.noSpaces), 10000)
        callBack
      }

      webSocketStreamOpt.get.onerror = { event: Event =>
        println("webRtc_error", event.eventPhase)
        webSocketStreamOpt = None
      }

      webSocketStreamOpt.get.onmessage = { event: MessageEvent =>
        //        println(s"recv msg:${event.data.toString}")
        event.data match {
          case jsonStringMsg: String =>
            messageHandler(jsonStringMsg)
          case unknow => println(s"webRtc_recv unknow msg:${unknow}")
        }
      }

      webSocketStreamOpt.get.onclose = { event: Event =>
        println("webRtc_close", event.eventPhase)
        webSocketStreamOpt = None
      }
    }
  }

  private def messageHandler(message: String) = {
    val json = parse(message).getOrElse(Json.Null).hcursor
    json.get[String]("id").map {
      case EventId.PONG =>
        println("pong")
      case EventId.Anchor_SDP_OFFER =>
      case EventId.Audience_SDP_OFFER =>
      case EventId.PROCESS_SDP_ANSWER =>
        Globals.messageHandler(message)
      case EventId.ADD_ICE_CANDIDATE =>
        Globals.messageHandler(message)
      case EventId.CONNECT =>
        json.get[String]("msg").foreach { m =>
          JsFunc.alert(m)
        }
      case _ =>
    }
  }

  @JSExport
  def sendMessage(s: String) = {
    println("scalaWebSocket:", s)
    webSocketStreamOpt.foreach(_.send(s))
  }
}
