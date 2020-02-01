import java.nio.ByteBuffer

object TestLong2Bytes {


  val buf = ByteBuffer.allocate(188)

  def long2Bytes(d:Long) = {
    buf.putLong(d)
    buf.array()
  }

  def bytes2Long(bytes:Array[Byte]) = {
    val buf = ByteBuffer.allocate(8)
    buf.put(bytes)
    buf.flip()
    buf.getLong()
  }


  def main(args: Array[String]): Unit = {
    val a = 111l
    val bytes = long2Bytes(a).take(8)
    println(s"len:${bytes.length}")
    val r = bytes2Long(bytes)
    println(s"res:$r")

  }
}
