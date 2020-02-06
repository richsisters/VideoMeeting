package org.seekloud.VideoMeeting.webClient.common.Components

import scala.xml.Elem
import org.scalajs.dom
import org.scalajs.dom.html._
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.CommonProtocol.{SearchRoomReq, SearchRoomRsp}
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.{Comment, LikeRoom}
import org.seekloud.VideoMeeting.webClient.actors.WebSocketRoom
import org.seekloud.VideoMeeting.webClient.common.Routes
import org.seekloud.VideoMeeting.webClient.util.{Globals, Http}
import io.circe.syntax._
import io.circe.generic.auto._
import mhtml.Var
import org.scalajs.dom.raw._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * create by 13
  * 2019/8/27
  *
  * 用来处理点赞，关注，礼物等互动元素的前端样式
  */

object InteractiveText {

  case class Gift(
                 number: Int,
                 cost:Int,
                 name: String,
                 img: String,
                 desc: String,
                 tip: String
                 )

  val giftsList = List(
    Gift(1, 10, "面包", "/VideoMeeting/roomManager/static/img/gifts/bread.png", "一个面包", "花费10金币，获得10点好感"),
    Gift(2, 15, "蛋糕", "/VideoMeeting/roomManager/static/img/gifts/cake.png", "一个蛋糕", "花费15金币，获得10点好感"),
    Gift(3, 2, "西蓝花", "/VideoMeeting/roomManager/static/img/gifts/broccoli.png", "一个西蓝花", "花费2金币，获得10点好感"),
    Gift(4, 8, "雪糕", "/VideoMeeting/roomManager/static/img/gifts/ice-cream.png", "一个雪糕", "花费8金币，获得10点好感"),
    Gift(5, 5, "棒棒糖", "/VideoMeeting/roomManager/static/img/gifts/lollipop.png", "一个棒棒糖", "花费5金币，获得10点好感"),
    Gift(6, 12, "果汁", "/VideoMeeting/roomManager/static/img/gifts/juice.png", "一个果汁", "花费12金币，获得10点好感")
  )

//  val giftsList = List(
//    Gift(1, 8, "麻婆豆腐", "/VideoMeeting/static/img/gifts/bread.png", "麻婆豆腐，是四川省传统名菜之一。这道菜突出了川菜“麻辣”的特点。其口味独特，口感顺滑。", "花费10金币，获得10点好感"),
//    Gift(2, 25, "北京烤鸭", "/VideoMeeting/static/img/gifts/cake.png", "烤鸭是具有世界声誉的北京著名菜式。它以色泽红艳，肉质细嫩，味道醇厚，肥而不腻的特色，被誉为“天下美味”。", "花费15金币，获得10点好感"),
//    Gift(3, 28, "东坡肉", "/VideoMeeting/static/img/gifts/broccoli.png", "东坡肉，又名滚肉、东坡焖肉，是眉山和江南地区特色传统名菜。它红得透亮，色如玛瑙，软而不烂，肥而不腻。", "花费2金币，获得10点好感"),
//    Gift(4, 18, "辣子鸡", "/VideoMeeting/static/img/gifts/ice-cream.png", "辣子鸡，是一道经典的川渝地区的特色传统名肴。辣子鸡因各地的不同制作方法也有不同的特色。此菜色泽棕红油亮，麻辣味浓。", "花费8金币，获得10点好感"),
//    Gift(5, 21, "棒棒糖", "/VideoMeeting/static/img/gifts/lollipop.png", "龙井虾仁是一道具有浓厚地方风味的杭州名菜。成菜后，虾仁白嫩、茶叶翠绿，色泽淡雅，味美清口。", "花费5金币，获得10点好感"),
//    Gift(6, 12, "八仙过海闹罗汉", "/VideoMeeting/static/img/gifts/juice.png", "八仙过海闹罗汉是一道名菜，属于孔府菜。。旧时此菜上席即开锣唱戏，在品尝美味的同时听戏，热闹非凡，也奢侈至极。", "花费12金币，获得10点好感")
//  )

//
//      <input type="checkbox" name="gift-numbers" style="display: none;" id="bottom-like-button" disabled="1"></input>
//      <label class="bottom-like" for="bottom-like-button" onclick={()=>userLike()}></label>

}
