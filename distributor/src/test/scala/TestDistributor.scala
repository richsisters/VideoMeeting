import java.io.{PipedInputStream, PipedOutputStream}
import java.net.{InetSocketAddress, Socket}
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel, Pipe}

object TestDistributor {
  import java.nio.channels.SocketChannel

  object PayloadType{
    val newLive:Byte = 1
    val packet:Byte = 2
    val closeLive:Byte = 3
    val heartBeat:Byte = 4
  }

//  val distributorAddr = "10.1.29.248"
  val distributorAddr = "127.0.0.1"

  private val socket = new Socket(distributorAddr, 30391) //tcp
  println("-----------")
  private val roomId = 1000L
  private val receiveBuf = ByteBuffer.allocate(4096)
  private val packBuf = ByteBuffer.allocate(188)
//  private val packBuf = ByteBuffer.allocate(1327)
  private val output = socket.getOutputStream
  private val streamChannel = DatagramChannel.open()
  private val inetSocketAddress = new InetSocketAddress("127.0.0.1",41100)//udp
  streamChannel.bind(inetSocketAddress)
  //  val dst = new InetSocketAddress("127.0.0.1", 45501)

  private val num = 1
  private val duration = 180000

  val pipe: Pipe = Pipe.open()
  val sink: Pipe.SinkChannel = pipe.sink()
  val source: Pipe.SourceChannel = pipe.source()

  def main(args: Array[String]): Unit = {
    receiveThread.start()
    sendNewLive()
    sendThread.start()
    Thread.sleep(duration)
    sendClose()
    //    println(System.currentTimeMillis())
  }

  val receiveThread = new Thread(() =>{
    while (true) {
      if(streamChannel.receive(receiveBuf) != null){
        receiveBuf.flip()
        sink.write(receiveBuf)
        receiveBuf.clear()
      }
    }
  })

  private val sendThread = new Thread(()=>{
    while (true) {
      packBuf.clear()
      val n = source.read(packBuf)
      packBuf.flip()
      val a = packBuf.array()
      for(i <- 1000l until 1000l+num) {
        val data = packTs4Dispatcher(PayloadType.packet, i, a, n)
        output.write(data)
      }
      //      streamChannel.send(packBuf, dst)
    }
  })

  private def sendNewLive(): Unit = {
    val startTime = System.currentTimeMillis()
    println(s"sT:$startTime")
    packBuf.clear()
    packBuf.putLong(startTime)
    val a = packBuf.array()
    for(i <- 1000l until 1000l+num) {
      val data = packTs4Dispatcher(PayloadType.newLive, i, a, 8)
      output.write(data)
      Thread.sleep(1)
    }
  }

  private def sendClose(): Unit = {
    receiveThread.interrupt()
    Thread.sleep(10)
    val a = packBuf.array()
    for(i <- 1000l until 1000l+num) {
      val data = packTs4Dispatcher(PayloadType.closeLive, i, a, 0)
      output.write(data)
      Thread.sleep(2)
    }
    Thread.sleep(5)
    sendThread.interrupt()
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
