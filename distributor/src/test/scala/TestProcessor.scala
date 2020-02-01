import java.net.{InetSocketAddress, ServerSocket}
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

import org.bytedeco.javacpp.Loader
import org.seekloud.VideoMeeting.distributor.core.DistributorWorker.TsData

object TestProcessor {

  object PayloadType{
    val newLive = 1
    val packet = 2
    val closeLive = 3
    val heartbeat = 4
  }

  private val sendChannel =  DatagramChannel.open()
  sendChannel.bind(new InetSocketAddress("127.0.0.1", 47777))
  val socket = new ServerSocket(30391).accept()
  val input = socket.getInputStream

  val dst = new InetSocketAddress("127.0.0.1",45555)
  var n = 0



  def main(args: Array[String]): Unit = {
    encodeThread.start()
    while(true) {
      val buf = new Array[Byte](1327)
      n = input.read(buf)
      if(n!=1327) {
        println("packet length does not match 1327")
      }
      val data = unpackTs(buf)
      if(data.payload==PayloadType.packet) {
        println("--------------")
        sendChannel.send(data.body,dst)
      }else{
        println(s"got ${data.payload} type packet.")
      }
    }
  }

  val encodeThread = new Thread(() =>
    {
      val ffmpeg = Loader.load(classOf[org.bytedeco.ffmpeg.ffmpeg])
      val port = 45555
      val pb = new ProcessBuilder(ffmpeg, "-i", s"udp://127.0.0.1:$port", "-f", "flv", "/Users/litianyu/Downloads/test2/out.flv")
      pb.inheritIO().start().waitFor()
    }
  )

  def unpackTs(data: Array[Byte]) ={
    val payload = data(0)
    val roomId =byteArrayToLong(data.slice(1,9))
    val valid = toInt(data.slice(9,11))
    val body = data.slice(11, valid + 11)
    val dataBuf = ByteBuffer.allocate(body.length)
    dataBuf.put(body)
    dataBuf.flip()
    TsData(payload, roomId,dataBuf)
  }

  def byteArrayToLong(bytes:Array[Byte])= {
    var value = 0;
    // 由高位到低位
    for (i<-0 until 8) {
      val shift = (8 - 1 - i) * 8
      value += (bytes(i) & 0x000000FF) << shift;// 往高位游
    }
    value
  }
  def toInt(numArr: Array[Byte]) = {
    numArr.zipWithIndex.map { rst =>
      (rst._1 & 0xFF) << (8 * (numArr.length - rst._2 - 1))
    }.sum
  }
}
