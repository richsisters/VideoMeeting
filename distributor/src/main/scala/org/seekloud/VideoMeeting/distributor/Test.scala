package org.seekloud.VideoMeeting.distributor

import java.io.{File, FileInputStream}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
/**
  * Author: lenovo
  * Date: 2019-09-15
  * Time: 20:40
  */
object Test {
  def main(args: Array[String]): Unit = {
    encode1.start()
//    encode2.start()
  }

  val encode1 = new Thread(()=>{
    val port = 41100
    val host = "127.0.0.1"

    //setup source
    val src = "F:/obs-records/video3.ts"
    val fis = new FileInputStream(new File(src))
    //  val videoDataChannel = fis.getChannel
    val buf = ByteBuffer.allocate(1024 * 32)
    var buf_temp = new Array[Byte](188)

    //setup sink
    val dst = new InetSocketAddress(host, port)
    val udpSender = DatagramChannel.open()


    println("Begin1.")

    buf.clear()
    var len = fis.read(buf_temp,0,188)

    var totalSize = 0
    var count = 0
    while (len != -1) {
      count += 1
      totalSize += len.toInt
      for(i <- buf_temp.indices){
        buf.put(buf_temp(i))
      }
      buf.flip()
      udpSender.send(buf, dst)
      Thread.sleep(1)
      println(s"send count1111111111=$count, len=$len totalSize=$totalSize")
      buf.clear()
      len = fis.read(buf_temp,0,188)
    }

    println("DONE.")

  }
  )
  val encode2 = new Thread(()=>{
    val port = 41101
    val host = "127.0.0.1"

    //setup source
    val src = "F:/obs-records/video4.ts"
    val fis = new FileInputStream(new File(src))
    //  val videoDataChannel = fis.getChannel
    val buf = ByteBuffer.allocate(1024 * 32)
    var buf_temp = new Array[Byte](188)

    //setup sink
    val dst = new InetSocketAddress(host, port)
    val udpSender = DatagramChannel.open()


    println("Begin2.")

    buf.clear()
    var len = fis.read(buf_temp,0,188)

    var totalSize = 0
    var count = 0
    while (len != -1) {
      count += 1
      totalSize += len.toInt
      for(i <- buf_temp.indices){
        buf.put(buf_temp(i))
      }
      buf.flip()
      udpSender.send(buf, dst)
      Thread.sleep(1)
      println(s"send count222222222222=$count, len=$len totalSize=$totalSize")
      buf.clear()
      len = fis.read(buf_temp,0,188)
    }

    println("DONE.")

  }
  )
}


