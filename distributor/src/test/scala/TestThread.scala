import java.io.{File, FileInputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors}

import org.seekloud.VideoMeeting.distributor.Boot.executor
import org.seekloud.VideoMeeting.distributor.protocol.SharedProtocol.{FinishPullReq, StartPullReq, SuccessRsp}
import org.seekloud.VideoMeeting.distributor.utils.HttpUtil
import org.slf4j.LoggerFactory

import scala.concurrent.Future
/**
  * Author: lenovo
  * Date: 2019-09-24
  * Time: 20:05
  */
object TestThread extends HttpUtil{

  import io.circe.generic.auto._
  import io.circe.parser.decode
  import io.circe.syntax._

  val maxTsNum = 7
  var ts = 0l

  object Rtp_header{
    var m = 0
    var timestamp = 0l
    var payloadType = 96.toByte //负载类型号96
  }

  private val log = LoggerFactory.getLogger(this.getClass)
  val distributerBaseUrl = "http://0.0.0.0:30389/VideoMeeting/distributor"

  def main(args: Array[String]) {

    //    test1(2)

    test2(1)
  }

  def test2(n:Int) = {
    val num = n
    single(num)
    Thread.sleep(120*1000)
    for(i <- 1 until 1+num) {
      stopLive(s"$i").map{a =>
        println(a)
      }
//      stopLive(s"liveIdTest-$i").map{a =>
//        println(a)
//      }
      Thread.sleep(30)
    }

  }

  def single(num:Int):Unit = {
    val threadPool:ExecutorService=Executors.newFixedThreadPool(2)
    try {
      var ssrc = 0
      for(i <- 1000 until 1000+num){
        ssrc += 1
//        newLive(i.toLong,s"liveIdTest-$ssrc",System.currentTimeMillis())
        newLive(i.toLong,s"$ssrc",System.currentTimeMillis())
        Thread.sleep(2000)
        threadPool.execute(new ThreadTest(ssrc))
      }
    }finally {
      threadPool.shutdown()
    }
  }

  def newLive(roomId:Long,liveId:String,startTime: Long):Future[Either[String,SuccessRsp]] = {
    val url = distributerBaseUrl + "/startPull"
    val jsonString = StartPullReq(roomId,liveId).asJson.noSpaces
    postJsonRequestSend("post", url, List(), jsonString, timeOut = 60 * 1000).map{
      case Right(v) =>
        decode[SuccessRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"updateRoomInfo decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"updateRoomInfo postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  def stopLive(liveId:String):Future[Either[String,SuccessRsp]] = {
    val url = distributerBaseUrl + "/finishPull"
    val jsonString = FinishPullReq(liveId).asJson.noSpaces
    postJsonRequestSend("post",url,List(),jsonString,timeOut = 60 * 1000).map{
      case Right(v) =>
        decode[SuccessRsp](v) match{
          case Right(data) =>
            Right(data)
          case Left(e) =>
            log.error(s"updateRoomInfo decode error : $e")
            Left("Error")
        }
      case Left(error) =>
        log.error(s"updateRoomInfo postJsonRequestSend error : $error")
        Left("Error")
    }
  }

  class ThreadTest(ssrc:Int) extends Runnable{
    override def run(){
      println( "ssrc",ssrc)
//      val port = 61040
//      val host = "10.1.29.246"
            val port = 41660
            val host = "127.0.0.1"
      val increasedSequence = new AtomicInteger(0)
      val frameRate = 25
      val timestamp_increse=90000/frameRate//framerate是帧率
      val tsBuf1 = (0 until maxTsNum).map {i =>
        ByteBuffer.allocate(188 * 1)
      }.toList
      var buf_temp = Array[Byte]()
      var count = 0

      //setup sink
      val dst = new InetSocketAddress(host, port)
      val udpSender = DatagramChannel.open()

      //读取视频文件
//      val src = "D:/helloMedia/222.ts"
      val src = "/Users/litianyu/Downloads/record.ts"
      val fis = new FileInputStream(new File(src))
      var countRead = 0
      var totalReadSize = 0
      val bufRead = ByteBuffer.allocate(1024 * 32)
      val buf_tempRead = new Array[Byte](188)
      bufRead.clear()
      var len = fis.read(buf_tempRead,0,188)

      while(len != -1){
        countRead += 1
        totalReadSize += len.toInt
        for(i <- buf_tempRead.indices){
          bufRead.put(buf_tempRead(i))
        }
        bufRead.flip()
        Thread.sleep(2)
        //          println(s"thread = $threadCount read count=$countRead, len=$len totalSize=$totalReadSize")

        buf_temp = (buf_temp.toList ::: bufRead.array().take(bufRead.remaining()).toList).toArray

        while(buf_temp.length >= 188 *2) {
          var first_flag = true
          while(first_flag && buf_temp.length >= 188 * 2) {
            first_flag = false
            if (buf_temp(0) != 0x47) { //drop掉188字节以外的数据
              var ifFindFlag = -1
              var i = 0
              for(a <- buf_temp if ifFindFlag == -1) {
                if (a == 0x47.toByte) {ifFindFlag = i; buf_temp = buf_temp.drop(i)}
                i += 1
              }

            }
            while (count < 7 && !first_flag && buf_temp.length >= 188){
              val ts_packet = buf_temp.take(188)
              buf_temp = buf_temp.drop(188)
              if (ts_packet(0) != 0x47) {
                println("===========================error========================")
              }
              else {
                tsBuf1(count).put(ts_packet)
                tsBuf1(count).flip()
                count += 1
                if ((ts_packet(1) | 191.toByte).toByte == 255.toByte) {
                  val total_len = 12 + count * 188
                  val rtp_buf = ByteBuffer.allocate(total_len)
                  Rtp_header.m = 1
                  Rtp_header.timestamp += timestamp_increse //到下一个起始帧或者满了7个包，填充完毕
                  first_flag = true
                  //设置rtp header

                  //设置序列号
                  val seq = increasedSequence.getAndIncrement()

                  rtp_buf.put(0x80.toByte)
                  rtp_buf.put(33.toByte)
                  toByte(seq, 2).map(rtp_buf.put(_))
                  toByte(System.currentTimeMillis().toInt, 4).map(rtp_buf.put(_))
                  rtp_buf.putInt(ssrc)
                  //                    println(s"threadCount = $threadCount, seq", seq,"ssrc",ssrc)
                  (0 until count).foreach(i => rtp_buf.put(tsBuf1(i)))

                                  println(s"-----------------------")

                  rtp_buf.flip()

                  udpSender.send(rtp_buf, dst) //此rtp包是最后一个包
                  //                println(s"send")

                  (0 until count).foreach{i =>
                    tsBuf1(i).clear()}
                  rtp_buf.clear()
                  count = 0
                } else
                if (count == 7) {
                  val total_len = 12 + count * 188
                  val rtp_buf = ByteBuffer.allocate(total_len)
                  //                println("满7片，send rtp包")
                  Rtp_header.m = 0

                  //设置序列号
                  val seq = increasedSequence.getAndIncrement()
                  rtp_buf.put(0x80.toByte)
                  rtp_buf.put(33.toByte)
                  toByte(seq, 2).map(rtp_buf.put(_))
                  toByte(System.currentTimeMillis().toInt, 4).map(rtp_buf.put(_))
                  rtp_buf.putInt(ssrc)
                  //                    println(s"threadCount = $threadCount, seq", seq,"ssrc",ssrc)
                  (0 until count).foreach(i => rtp_buf.put(tsBuf1(i)))
                  //                println(s"-----------------------")

                  rtp_buf.flip()
                  udpSender.send(rtp_buf, dst)
                                  println(s"send")
                  (0 until count).foreach{i =>
                    tsBuf1(i).clear()}
                  rtp_buf.clear()
                  count = 0
                }
              }
            }
          }
        }
        bufRead.clear()
        len = fis.read(buf_tempRead,0,188)
      }
    }
  }

  def toByte(num: Long, byte_num: Int) = {
    (0 until byte_num).map { index =>
      (num >> ((byte_num - index - 1) * 8) & 0xFF).toByte
    }.toList
  }
}
