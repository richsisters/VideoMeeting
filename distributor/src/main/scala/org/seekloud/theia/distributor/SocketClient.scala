package org.seekloud.VideoMeeting.distributor

import java.io.{PipedInputStream, PipedOutputStream}
import java.net.{InetSocketAddress, Socket}
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, Pipe}

/**
  * User: yuwei
  * Date: 2019/8/31
  * Time: 16:22
  */
object SocketClient {
  import java.nio.channels.SocketChannel

  object PayloadType{
    val newLive:Byte = 1
    val packet:Byte = 2
    val closeLive:Byte = 3
    val heartBeat:Byte = 4
  }
  private val socket = new Socket("127.0.0.1", 30391) //tcp
  private val roomId = 1000l
  private val receiveBuf = ByteBuffer.allocate(4096)
  private val packArray = new Array[Byte](188)
  private val packBuf = ByteBuffer.allocate(188)
  private val output = socket.getOutputStream
  private val streamChannel = DatagramChannel.open()
  private val inetSocketAddress =new InetSocketAddress("127.0.0.1",41100)//udp
  streamChannel.bind(inetSocketAddress)
  val dst = new InetSocketAddress("127.0.0.1", 45501)

  val pipe = Pipe.open()
  val sink = pipe.sink()
  val source = pipe.source()


  def main(args: Array[String]): Unit = {
    receiveThread.start()
    sendNewLive()
    sendThread.start()
//    println(System.currentTimeMillis())
  }

  val receiveThread = new Thread(() =>{
      while (true) {
        if(streamChannel.receive(receiveBuf) != null){
          receiveBuf.flip()
          sink.write(receiveBuf)
          println("00000000000000000000000000000")
          receiveBuf.clear()
        }
      }
  })

  private val sendThread = new Thread(()=>{
    while (true) {
      packBuf.clear()
      val n = source.read(packBuf)
      packBuf.flip()
      val data = packTs4Dispatcher(PayloadType.packet, roomId, packBuf.array(), n)
      output.write(data)
      println("===================")
//      streamChannel.send(packBuf, dst)
    }
  })

  private def sendNewLive() = {
    val startTime = System.currentTimeMillis()
    println(s"sT:$startTime")
    packBuf.clear()
    packBuf.putLong(startTime)
    val data = packTs4Dispatcher(PayloadType.newLive, roomId, packBuf.array(), 8)
    output.write(data)
  }

  private def sendClose() = {
    val data = packTs4Dispatcher(PayloadType.closeLive, roomId, packBuf.array(), 0)
//    output.write(data)
  }

  private def packTs4Dispatcher(payload:Byte, roomId: Long, ts:Array[Byte], valid:Long) ={
    val packBuf = ByteBuffer.allocate(199)
    packBuf.clear()
    packBuf.put(payload)
    packBuf.put(toByte(roomId, 8))
    packBuf.put(toByte(valid, 2))
    packBuf.put(ts)
    packBuf.flip()
    packBuf.array()
  }

  private def toByte(num: Long, byte_num: Int) = {
    (0 until byte_num).map { index =>
      (num >> ((byte_num - index - 1) * 8) & 0xFF).toByte
    }.toArray
  }


}
