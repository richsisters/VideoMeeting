package org.seekloud.VideoMeeting.rtpServer

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import org.seekloud.VideoMeeting.rtpServer.test.{TestPullActor, TestPushActor}
import org.seekloud.VideoMeeting.rtpClient.{Protocol, PullStreamClient, PushStreamClient}
import org.slf4j.LoggerFactory
import akka.actor.typed.scaladsl.adapter._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Author: wqf
  * Date: 2019/8/13
  * Time: 16:29
  */

object TestRtpClient {

  import org.seekloud.VideoMeeting.rtpServer.common.AppSettings._

  implicit val system: ActorSystem = ActorSystem("pencil", config)

  /**拉流数量*/
  private val pullStreamNum = 1

  /**拉流 liveId*/
  val liveId = "liveIdTest-1580"

  def main(args: Array[String]): Unit = {

    println("testRtpClient start...")

    /** 本地测试 */
//        val pushStreamDst = new InetSocketAddress("127.0.0.1", 61040)
//        val pullStreamDst = new InetSocketAddress("127.0.0.1", 61041)
//        val httpDst = "http://127.0.0.1:30390"

    /** super1 内网 */
//        val pushStreamDst = new InetSocketAddress("10.1.29.244", 61040)
//        val pullStreamDst = new InetSocketAddress("10.1.29.244", 61041)
//        val httpDst = "http://10.1.29.244:30390"

    /** super1 公网 */
    //    val pushStreamDst = new InetSocketAddress("media.seekloud.org", 61040)
    //    val pullStreamDst = new InetSocketAddress("media.seekloud.org", 61041)
    //    val httpDst = "https://media.seekloud.org:50443"

    /** super3 内网 */
//        val pushStreamDst = new InetSocketAddress("10.1.29.246", 61040)
//        val pullStreamDst = new InetSocketAddress("10.1.29.246", 61041)
//        val httpDst = "http://10.1.29.246:30390"

    /** super3 公网 */
//        val pushStreamDst = new InetSocketAddress("media.seekloud.com", 61040)
//        val pullStreamDst = new InetSocketAddress("media.seekloud.com", 61041)
//        val httpDst = "https://media.seekloud.com:50443"

    /** super5 内网 */
    val pushStreamDst = new InetSocketAddress("10.1.29.248", 61040)
    val pullStreamDst = new InetSocketAddress("10.1.29.248", 61041)
    val httpDst = "http://10.1.29.248:30390"

    /** 模拟推流（假数据） */
    val pushActor = system.spawn(TestPushActor.create(), "PushStreamActor")
    val pushClient = new PushStreamClient("0.0.0.0", 1234, pushStreamDst, pushActor, httpDst)
    pushActor ! TestPushActor.Ready(pushClient)


    Thread.sleep(2000)

    /** 模拟多路拉流 */
    for (i <- 0 until pullStreamNum) {
      val pullActor = system.spawn(TestPullActor.create(), s"PullStreamActor-$i")
      val pullClient = new PullStreamClient("0.0.0.0", 5803 + i, pullStreamDst, pullActor, httpDst)
      pullActor ! TestPullActor.Ready(pullClient)
    }







  }



}