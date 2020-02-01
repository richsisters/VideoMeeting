package org.seekloud.VideoMeeting.pcClient.component

import javafx.animation.AnimationTimer
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.image.Image
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.seekloud.VideoMeeting.pcClient.common.Constants.barrageColors
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.websocket.AuthProtocol.RcvComment
import org.slf4j.LoggerFactory
import org.seekloud.VideoMeeting.pcClient.common.Constants.WindowStatus
import org.seekloud.VideoMeeting.protocol.ptcl.client2Manager.http.RecordCommentProtocol.{CommentInfo, GetRecordCommentListRsp}

import scala.util.Random

/**
  * Author: zwq
  * Date: 2019/9/12
  * Time: 13:45
  */
object Barrage {
  /**
    * windowsStatus.HOST & WindowsStatus.AUDIENCE_LIVE
    */
  case class LiveCommentBarrage(
    userId: Long,
    userName: String,
    comment: String,
    receiveTime: Long,
    xPos: Double,
    yPos: Double,
    xSpeed: Double,
    ySpeed: Double,
    color: Color,
    fontSize: Double,
    effectType: Int // 0-普通弹幕  1-放大缩小  2-闪入闪出
  )

  case class GiftBarrage(
    userId: Long,
    userName: String,
    giftType: Int,
    receiveTime: Long,
    xPos: Double,
    yPos: Double,
    xSpeed: Double,
    ySpeed: Double
  )

  /**
    * WindowsStatus.AUDIENCE_REC
    */

  case class RecCommentBarrage(
    userId: Long,
    userName: String,
    comment: String,
    playTime: Long,
    xPos: Double,
    yPos: Double,
    xSpeed: Double,
    ySpeed: Double,
    color: Color,
    fontSize: Double
  )
}

class Barrage(windowStatus: Int, canvasWidth: Double, canvasHeight: Double, player: Option[MediaPlayer] = None) {

  import Barrage._

  private[this] val log = LoggerFactory.getLogger(this.getClass)
  val random = new Random(System.nanoTime())
  var animationTimerStart = false

  private val gift4Img = new Image("img/rose.png")
  private val gift5Img = new Image("img/gift5.png")
  private val gift6Img = new Image("img/gift6.png")

  protected val emojiFont = "Segoe UI Emoji"

  /*canvas*/
  val barrageView = new Canvas(canvasWidth, canvasHeight)
  val barrageGc: GraphicsContext = barrageView.getGraphicsContext2D

  /*直播弹幕*/
  var commentsList = List.empty[LiveCommentBarrage]
  var giftsList = List.empty[GiftBarrage]

  /*录像弹幕*/
  var recCommentsList = List.empty[CommentInfo]
  var recBarragesList = List.empty[RecCommentBarrage]


  private val animationTimer = new AnimationTimer() {
    override def handle(now: Long): Unit = {
      windowStatus match{
        case WindowStatus.HOST =>
          drawLiveBarrages()
        case WindowStatus.AUDIENCE_LIVE =>
          drawLiveBarrages()
        case WindowStatus.AUDIENCE_REC =>
          drawRecBarrages()
      }

    }
  }

  /**
    * 直播
    */

  def drawLiveBarrages(): Unit = {
    barrageGc.clearRect(0, 0, barrageView.getWidth, barrageView.getHeight)
    //画弹幕
    commentsList = commentsList.filter(comment => System.currentTimeMillis() - comment.receiveTime < 20000)
    commentsList = commentsList.map {
      case l@LiveCommentBarrage(userId, userName, comment, receiveTime, xPos, yPos, xSpeed, ySpeed, color, fontSize, effectType) =>
        val x = xPos + xSpeed
        val y = yPos + ySpeed
        userId match {
          case -1 =>  //系统消息
            barrageGc.setFont(Font.font(emojiFont, 22))
            barrageGc.setFill(color)
            barrageGc.fillText(s"[系统消息]：$comment", x, y)
            l.copy(xPos = x, yPos = y)

          case _ =>  //用户消息
            effectType match{
              case 0 =>
                barrageGc.setFont(Font.font(emojiFont, fontSize))
                barrageGc.setFill(color)
                barrageGc.fillText(s"[$userName]：$comment", x, y)
                l.copy(xPos = x, yPos = y)
              case 1 =>
                barrageGc.setFont(Font.font(emojiFont, fontSize))
                barrageGc.setFill(color)
                barrageGc.fillText(s"[$userName]：$comment", x, y)
                if (xPos > barrageView.getWidth / 2) {
                  l.copy(xPos = x, yPos = y, fontSize = fontSize + 0.05)
                } else {
                  l.copy(xPos = x, yPos = y, fontSize = fontSize - 0.05)
                }
              case 2 =>
                barrageGc.setFont(Font.font(emojiFont, fontSize))
                barrageGc.setFill(color)
                barrageGc.fillText(s"[$userName]：$comment", x, y)
                if (xPos > barrageView.getWidth * 0.3 && xPos < barrageView.getWidth * 0.34) {
                  l.copy(xPos = x, yPos = y, xSpeed = -0.03)
                } else {
                  l.copy(xPos = x, yPos = y, xSpeed = -10)
                }
              case 3 =>
                barrageGc.setFont(Font.font(emojiFont, fontSize))
                barrageGc.setFill(color)
                barrageGc.fillText(s"[$userName]：$comment", x, y)
                if (System.currentTimeMillis() - receiveTime < 3000) {
                  l.copy(xPos = xPos - 0.7, fontSize = fontSize + 0.15)
                } else if (System.currentTimeMillis() - receiveTime >= 3000 && System.currentTimeMillis() - receiveTime < 5000) {
                  l.copy()
                } else {
                  if (fontSize - 0.2 >= 0) l.copy(xPos = xPos + 0.7, fontSize = fontSize - 0.15)
                  else l.copy()
                }
              case _ => l
            }
        }
      case x => x

    }
    //画礼物
    giftsList = giftsList.filter(gift => System.currentTimeMillis() - gift.receiveTime < 20000)
    giftsList = giftsList.map {
      case l@GiftBarrage(userId, userName, giftType, receiveTime, xPos, yPos, xSpeed, ySpeed) =>
        val x = xPos + xSpeed
        val y = yPos + ySpeed
        giftType match{
          case 4 => // 鲜花
            barrageGc.drawImage(gift4Img, x, y, 50, 50)
          case 5 => // 飞船
            barrageGc.drawImage(gift5Img, x, y, 150, 150)
          case 6 => // 火箭
            barrageGc.drawImage(gift6Img, x, y, 150, 150)
          case _ => // do nothing
        }
        l.copy(xPos = x, yPos = y)

      case x => x


    }
  }

  def updateBarrage(recComment: RcvComment): Unit = {
    if (!animationTimerStart) {
      animationTimer.start()
      animationTimerStart = true
    }
    //弹幕
    commentsList = commentsList.filter(comment => System.currentTimeMillis() - comment.receiveTime < 30000)
    val effectType = recComment.extension match {
      case Some("effectType0") => 0
      case Some("effectType1") => 1
      case Some("effectType2") => 2
      case Some("effectType3") => 3
      case _ => 0
    }

    val xPos = effectType match {
      case 0 => barrageView.getWidth
      case 1 => barrageView.getWidth
      case 2 => barrageView.getWidth
      case 3 => random.nextInt(barrageView.getWidth.toInt - 300) + 150
    }
    val yPos = effectType match {
      case 0 => random.nextInt(barrageView.getHeight.toInt - 50) + 25
      case 1 => barrageView.getHeight / 2
      case 2 => random.nextInt(barrageView.getHeight.toInt - 50) + 25
      case 3 => random.nextInt(barrageView.getHeight.toInt - 50) + 25
    }

    val xSpeed = recComment.userId match {
      case -1 => -1
      case _ =>
        effectType match {
          case 0 => -1.5
          case 1 => -1.5
          case 2 => -1.5
          case 3 => 0
        }
    }
    val ySpeed = recComment.userId match {
      case -1 => 0
      case _ =>
        effectType match {
          case 0 => 0
          case 1 => 0
          case 2 => 0
          case 3 => 0
        }
    }
    val color = recComment.userId match {
      case -1 => Color.RED
      case _ => barrageColors(Random.nextInt(barrageColors.size))
    }

    val fontSize = recComment.userId match {
      case -1 => 22
      case _ =>
        effectType match {
          case 0 => 22
          case 1 => 22
          case 2 => 22
          case 3 => 0
        }
    }

    val oneComment = LiveCommentBarrage(recComment.userId, recComment.userName, recComment.comment, System.currentTimeMillis(), xPos, yPos, xSpeed, ySpeed, color, fontSize, effectType)
    commentsList ::= oneComment

    //礼物
    recComment.extension match {
      case Some("gift4") =>
        val xSpeed = 0
        val ySpeed = 1
        for (i <- 0 to 15) {
          giftsList ::= GiftBarrage(recComment.userId, recComment.userName, 4, System.currentTimeMillis(), random.nextInt(barrageView.getWidth.toInt), random.nextInt(150) - 150, xSpeed, ySpeed)
        }
      case Some("gift5") =>
        val xPos = barrageView.getWidth
        val yPos = barrageView.getHeight
        val xSpeed = -1.5
        val ySpeed = -1
        val oneGift = GiftBarrage(recComment.userId, recComment.userName, 5, System.currentTimeMillis(), xPos, yPos, xSpeed, ySpeed)
        giftsList ::= oneGift
      case Some("gift6") =>
        val xPos = 0
        val yPos = barrageView.getHeight - 30
        val xSpeed = 1.5
        val ySpeed = -1
        val oneGift = GiftBarrage(recComment.userId, recComment.userName, 6, System.currentTimeMillis(), xPos, yPos, xSpeed, ySpeed)
        giftsList ::= oneGift
      case _ =>  // do nothing

    }

  }


  /**
    * 看录像
    */

  def drawRecBarrages(): Unit = {
    barrageGc.clearRect(0, 0, barrageView.getWidth, barrageView.getHeight)

    val drawBarrages = recBarragesList.filter(l => l.playTime < player.get.getCurrentTime.toMillis.toLong && player.get.getCurrentTime.toMillis - l.playTime < 20000)

    recBarragesList = recBarragesList.map{
      case l@RecCommentBarrage(userId, userName, comment, playTime, xPos, yPos, xSpeed, ySpeed, color, fontSize) =>
        if(drawBarrages.contains(l)){
          val x = xPos + xSpeed
          val y = yPos + ySpeed
          barrageGc.setFont(Font.font(emojiFont, fontSize))
          barrageGc.setFill(color)
          barrageGc.fillText(s"[$userName]：$comment", x, y)
          l.copy(xPos = x, yPos = y)
        } else {
          l
        }

    }
  }

  def refreshRecBarrage(list: List[CommentInfo]): Unit = {

//    log.debug(s"refreshRecBarrage:$recCommentList")
    if (!animationTimerStart) {
      animationTimer.start()
      animationTimerStart = true
    }

    val addList = list.filterNot(l => recCommentsList.contains(l))

    addList.filter(l => l.authorUidOpt.isEmpty) foreach { l =>

      val playTime = l.relativeTime
      val xPos = barrageView.getWidth
      val yPos = random.nextInt(barrageView.getHeight.toInt - 50) + 25
      val xSpeed = -1.5
      val ySpeed = 0
      val color = barrageColors(Random.nextInt(barrageColors.size))
      val fontSize = 22
      val oneBarrage = RecCommentBarrage(l.commentId, l.commentUserName, l.comment, playTime, xPos, yPos, xSpeed, ySpeed, color, fontSize)
      recBarragesList = oneBarrage :: recBarragesList
      recCommentsList = l :: recCommentsList
    }
  }


}
