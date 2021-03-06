package org.seekloud.VideoMeeting.roomManager.core

import java.util.{Date, Properties}

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import javax.mail.Message.RecipientType
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import org.seekloud.VideoMeeting.roomManager.common.AppSettings
import org.seekloud.VideoMeeting.roomManager.utils.TimeUtil
import org.slf4j.LoggerFactory

/**
  * Created by haoshuhan on 2018/12/5.
  * Userd by ltm on 2019/8/27
  */
object EmailActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  case class SendConfirmEmail(url: String, email: String) extends Command

  case class SendInviteEmail(email: String, meetingNumber: String) extends Command

  val behavior = idle()

  def idle(): Behavior[Command] = {
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case x@SendConfirmEmail(url, email) =>
          log.info(s"I receive msg:$x")
          val session = getEbuptSession
          val message = new MimeMessage(session)
          message.setFrom(new InternetAddress(AppSettings.emailAddresserEmail))
          message.setRecipient(RecipientType.TO,new InternetAddress(email))
          message.setSubject(s"欢迎加入VideoMeeting")
          message.setSentDate(new Date)
          val mainPart = new MimeMultipart
          val html = new MimeBodyPart
          val content = getRegisterEamilHtml(url, email)
          html.setContent(content, "text/html; charset=utf-8")
          mainPart.addBodyPart(html)
          message.setContent(mainPart)
          Transport.send(message)
          Behaviors.same

        case x@SendInviteEmail(email, meetingNumber) =>
          log.info(s"I receive msg:$x")
          val session = getEbuptSession
          val message = new MimeMessage(session)
          message.setFrom(new InternetAddress(AppSettings.emailAddresserEmail))
          message.setRecipient(RecipientType.TO,new InternetAddress(email))
          message.setSubject(s"您的好友邀请您加入VideoMeeting会议！")
          message.setSentDate(new Date)
          val mainPart = new MimeMultipart
          val html = new MimeBodyPart
          val content = getInviteEmailHtml(meetingNumber)
          html.setContent(content, "text/html; charset=utf-8")
          mainPart.addBodyPart(html)
          message.setContent(mainPart)
          Transport.send(message)
          Behaviors.same

        case x =>
          log.warn(s"${ctx.self.path} unknown msg: $x")
          Behaviors.unhandled
      }
    }
  }

  def getProperties = {
    val p = new Properties
    p.put("mail.smtp.host", AppSettings.emailHost)
    p.put("mail.smtp.port", AppSettings.emailPort)
    p.put("mail.transport.protocol", "smtp")
    p.put("mail.smtp.auth", "true")
    // 以下配置用在aliyun服务器上,aliyun服务器上25端口被禁用
    p.put("mail.smtp.socketFactory.port", "465")
    p.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
    p.put("mail.smtp.socketFactory.fallback", "false")
    p.put("mail.smtp.ssl.enable", "true")
    p
  }

  def getEbuptSession = {
    Session.getInstance(getProperties, new MyAuthenticator(AppSettings.emailAddresserEmail, AppSettings.emailAddresserPwd))
  }

  def getRegisterEamilHtml(confirmUrl:String,email:String) = {
    val sb: StringBuilder = new StringBuilder
    sb.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>")

    sb.append("""<table width="100%" bgcolor="#f4f9fd" cellpadding="0" cellspacing="10"><tbody>""")
    sb.append(s"""<tr>	<td height="50" valign="top"><b><font size="4" color="#555555" face="Arial, Helvetica, sans-serif">你好， <span style="border-bottom-width: 1px; border-bottom-style: dashed; border-bottom-color: rgb(204, 204, 204); z-index: 1; position: static;" t="7" onclick="return false;"  isout="1">${email}</span></font></b><br><font size="3" color="#555555" face="Arial, Helvetica, sans-serif">请点击下面的链接激活注册邮箱：</font></td></tr>""")
    sb.append(s"""<tr>	<td height="50" valign="top"><a href="$confirmUrl" target="_blank"><font size="3" color="#339adf" face="Arial, Helvetica, sans-serif"></font>$confirmUrl</a><font></font><br><font size="3" color="#909090" face="Arial, Helvetica, sans-serif">(此链接1天内有效，超时需要重新获取邮件)</font></td></tr>""")
    sb.append(s"""<tr>	<td height="40" valign="top">	<font size="3" color="#555555" face="Arial, Helvetica, sans-serif">祝使用愉快！<br>VideoMeeting <span style="border-bottom-width: 1px; border-bottom-style: dashed; border-bottom-color: rgb(204, 204, 204); position: relative;" >${TimeUtil.format(System.currentTimeMillis())}<br>	</font></td></tr>""")
    sb.append(s"""<tr><td height="80" valign="top"><font size="2" color="#909090" face="Arial, Helvetica, sans-serif">如果你没有注册过VideoMeeting平台，请忽略此邮件。<br>""")
    sb.append("""</tbody></table>""")

    sb.append("</body></html>")
    sb.toString()
  }

  def getInviteEmailHtml(meetingNumber: String) = {
    val sb: StringBuilder = new StringBuilder
    sb.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head><body>")
    sb.append(s"""<p>您好，</p><p>邀请您加入<span>VideoMeeting</span>会议</p><p>会议号<span>$meetingNumber</span></p>""")
    sb.append("</body></html>")
    sb.toString()
  }

  case class MyAuthenticator(userName: String, password: String) extends Authenticator {

    override def getPasswordAuthentication: PasswordAuthentication = {
      new PasswordAuthentication(userName, password)
    }
  }

}
