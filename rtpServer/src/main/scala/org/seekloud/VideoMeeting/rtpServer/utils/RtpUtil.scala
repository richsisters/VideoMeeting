package org.seekloud.VideoMeeting.rtpServer.utils

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import org.seekloud.VideoMeeting.rtpClient.Protocol.{Data, Header}

/**
  * Created by haoshuhan on 2019/8/12.
  */
object RtpUtil {

def sendRtpPackage(payloadType: Int, ssrc: Int, data: Array[Byte], channel: DatagramChannel, dst: InetSocketAddress): Unit ={
    sendData(channel, dst, Header(payloadType, 0, 0, ssrc, System.currentTimeMillis()), data)
  }

  def sendData(channel: DatagramChannel, dst: InetSocketAddress, header: Header, data: Array[Byte]) = {
    val seq = header.seq
    val realTimestamp = header.timestamp
    val seq_byte_list = toByte(seq, 2)
    val ts_byte_list = toByte(realTimestamp, 4)
    val rtp_buf: ByteBuffer = ByteBuffer.allocate(12 + data.length)
    rtp_buf.put(0x80.toByte)
    rtp_buf.put(header.payloadType.toByte)
    seq_byte_list.foreach(rtp_buf.put(_))
    ts_byte_list.foreach(rtp_buf.put(_))
    rtp_buf.putInt(header.ssrc)
    rtp_buf.put(data)
    rtp_buf.flip()
    channel.send(rtp_buf, dst)
  }

  def parseData(byteBuffer: ByteBuffer) = {
    byteBuffer.flip()
    val first_byte = byteBuffer.get()
    val payloadTypeM = byteBuffer.get()
    val payloadType = payloadTypeM & 0x7F
    val m = if ((payloadTypeM & 0x80) == 0x80) 1 else 0
    val seq_h = byteBuffer.get()
    val seq_l = byteBuffer.get()
    val ts = byteBuffer.getInt()
    val ssrc = byteBuffer.getInt()
    val seq = toInt(Array(seq_h, seq_l))
    val data = new Array[Byte](byteBuffer.remaining())
    byteBuffer.get(data)
    Data(Header(payloadType, m, seq, ssrc, ts), data)
  }

  def parseData(bytes: Array[Byte]) = {
    val first_byte = bytes(0)
    val payloadTypeM = bytes(1)
    val payloadType = payloadTypeM & 0x7F
    val m = if ((payloadTypeM & 0x80) == 0x80) 1 else 0
    val seq = toInt(bytes.slice(2, 4))
    val ts = toInt(bytes.slice(4, 8))
    val ssrc = toInt(bytes.slice(8, 12))
    val data = bytes.drop(12)
    Data(Header(payloadType, m, seq, ssrc, ts), data)
  }

  def toByte(num: Long, byte_num: Int) = {
    (0 until byte_num).map { index =>
      (num >> ((byte_num - index - 1) * 8) & 0xFF).toByte
    }.toList
  }

  def toInt(numArr: Array[Byte]) = {
    numArr.zipWithIndex.map { rst =>
      (rst._1 & 0xFF) << (8 * (numArr.length - rst._2 - 1))
    }.sum
  }

}
