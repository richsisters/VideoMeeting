package org.seekloud.VideoMeeting.distributor

import java.net.{InetSocketAddress, ServerSocket}
import java.nio.ByteBuffer

/**
  * User: yuwei
  * Date: 2019/8/31
  * Time: 16:16
  */
object SocketServer {
  import java.nio.channels.SocketChannel
  val buf = new Array[Byte](1024)
  import java.nio.channels.ServerSocketChannel
  val serverSocket = new ServerSocket(30391)
  def main(args: Array[String]): Unit = {
    val socket = serverSocket.accept()
    val input = socket.getInputStream
    var n = 2;
    while(n>0){
        n = input.read(buf)
        println(n)
    }
//    println(System.currentTimeMillis())
    Thread.sleep(50000000)
  }

  def byteArrayToInt(bytes:Array[Byte])= {
    var value = 0;
    // 由高位到低位
    for (i<-0 until 8) {
      val shift = (8 - 1 - i) * 8
      value += (bytes(i) & 0x000000FF) << shift;// 往高位游
    }
    value
  }
}
