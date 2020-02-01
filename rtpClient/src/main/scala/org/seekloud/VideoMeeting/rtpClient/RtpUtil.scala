package org.seekloud.VideoMeeting.rtpClient

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
  * Created by haoshuhan on 2019/7/16.
  */
object RtpUtil {
  val maxLength = 1500
  val header_length = 9
  lazy val rtp_buf: ByteBuffer = ByteBuffer.allocate(maxLength)

  def toInt(numArr: Array[Byte]) = {
    val b0 = numArr(3) & 0xff
    val b1 = numArr(2) & 0xff
    val b2 = numArr(1) & 0xff
    val b3 = numArr(0) & 0xff
    (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
  }


  def seqToInt(numArr: Array[Byte]) = {
    numArr.reverse.zipWithIndex.map { rst =>
      rst._1 << (8 * rst._2)
    }.sum
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
    Array(byte1, byte2, byte3, byte4)
  }

    def sendUdpPackage(payloadType: Int, ssrc: Int, data: Array[Byte], channel: DatagramChannel, dst: InetSocketAddress) = {
      (0 until (data.length / (maxLength - header_length)) + 1).foreach { i =>
        val dropData = data.drop(i * (maxLength - header_length))
        val length = scala.math.min(dropData.length, maxLength - header_length)
        val payload = dropData.take(length)
        val rtp_buf: ByteBuffer = ByteBuffer.allocate(header_length + length)
        rtp_buf.put(payloadType.toByte)
        rtp_buf.putInt(0)
        rtp_buf.putInt(ssrc)
        rtp_buf.put(payload)
        rtp_buf.flip()
        channel.send(rtp_buf, dst)
      }
    }


  def rtpPackage(payLoadType: Int, ssrc: Array[Byte], data: Array[Byte]) = {
    (0 until (data.length / (maxLength - 12)) + 1).map { i =>
      val dropData = data.drop(i * (maxLength - 12))
      val length = scala.math.min(dropData.length, maxLength - 12)
      val payload = dropData.take(length)
      rtp_buf.clear()
      rtp_buf.put(0x80.toByte)
      rtp_buf.put(payLoadType.toByte)
      (0 until 6).foreach(_ => rtp_buf.put(0.toByte))
      ssrc.foreach(b => rtp_buf.put(b))
      rtp_buf.put(payload)
      rtp_buf.flip()
      rtp_buf.array().take(rtp_buf.remaining())
    }.toList
  }

}



