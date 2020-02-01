package org.seekloud.VideoMeeting.distributor

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicInteger

/**
  * Created by haoshuhan on 2019/6/27.
  */
object Test_Sender_rtp {

  val maxTsNum = 7
  var ts = 0l
  val tsBuf = (0 until maxTsNum).map {i =>
    ByteBuffer.allocate(188 * 1)
  }.toList

  object Rtp_header{
    var m = 0
    var timestamp = 0l
    var payloadType = 96.toByte //负载类型号96
  }
  def main(args: Array[String]): Unit = {

    val port = 61041
//    val host = "127.0.0.1"
    val host = "10.1.29.246"
    val increasedSequence = new AtomicInteger(0)
    val frameRate = 25
    val timestamp_increse=90000/frameRate//framerate是帧率

    val channel = DatagramChannel.open()
    channel.socket().bind(new InetSocketAddress("127.0.0.1", 41100))
    val buf = ByteBuffer.allocate(1024 * 32)

    //setup sink
    val dst = new InetSocketAddress(host, port)
    val udpSender = DatagramChannel.open()
    val ssrc = 1116

    println("Begin.")

    buf.clear()
    var buf_temp = Array[Byte]()
    var totalSize = 0
    var count = 0
    while(true) {
      buf.clear()
      channel.receive(buf)
      buf.flip()
      buf_temp = (buf_temp.toList ::: buf.array().take(buf.remaining()).toList).toArray

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
              tsBuf(count).put(ts_packet)
              tsBuf(count).flip()
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
                println(s"seq", seq)
                rtp_buf.put(0x80.toByte)
                rtp_buf.put(33.toByte)
                toByte(seq, 2).map(rtp_buf.put(_))
                toByte(System.currentTimeMillis().toInt, 4).map(rtp_buf.put(_))
                rtp_buf.putInt(ssrc)
                (0 until count).foreach(i => rtp_buf.put(tsBuf(i)))

//                println(s"-----------------------")

                rtp_buf.flip()

                udpSender.send(rtp_buf, dst) //此rtp包是最后一个包
//                println(s"send")

                (0 until count).foreach{i =>
                  tsBuf(i).clear()}
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
                println(s"seq", seq)
                rtp_buf.put(0x80.toByte)
                rtp_buf.put(33.toByte)
                toByte(seq, 2).map(rtp_buf.put(_))
                toByte(System.currentTimeMillis().toInt, 4).map(rtp_buf.put(_))
                rtp_buf.putInt(ssrc)

                (0 until count).foreach(i => rtp_buf.put(tsBuf(i)))
//                println(s"-----------------------")

                rtp_buf.flip()
                udpSender.send(rtp_buf, dst)
//                println(s"send")
                (0 until count).foreach{i =>
                  tsBuf(i).clear()}
                rtp_buf.clear()
                count = 0
              }
            }
          }
        }
      }

    }

    println("DONE.")
  }

  def to16Bit(num: Long) = {
    val byte1 = (num >>> 8 & 0xFF).toByte
    val byte2 = (num & 0xFF).toByte
    (byte1, byte2)

  }

  def to32Bit(num: Long) = {
    val byte1 = (num >>> 24 & 0xFF).toByte
    val byte2 = (num >>> 16 & 0xFF).toByte
    val byte3 = (num >>> 8 & 0xFF).toByte
    val byte4 = (num & 0xFF).toByte
    (byte1, byte2, byte3, byte4)
  }

  def toHexFromByte(b: Byte): String = {
    val hexSymbols = Array("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f")
    val leftSymbol = ((b >>> 4) & 0x0f).toByte
    val rightSymbol = (b & 0x0f).toByte
    hexSymbols(leftSymbol) + hexSymbols(rightSymbol)
  }

  def toByte(num: Long, byte_num: Int) = {
    (0 until byte_num).map { index =>
      (num >> ((byte_num - index - 1) * 8) & 0xFF).toByte
    }.toList
  }



}
