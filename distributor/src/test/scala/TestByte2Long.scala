import java.nio.ByteBuffer

object TestByte2Long {



  def bytes2Long(bytes:Array[Byte]) = {
    val buf = ByteBuffer.allocate(8)
    buf.put(bytes)
    buf.flip()
    buf.getLong()
  }

  def toByte(num: Long, byte_num: Int) = {
    (0 until byte_num).map { index =>
      (num >> ((byte_num - index - 1) * 8) & 0xFF).toByte
    }.toArray
  }

  def toInt(numArr: Array[Byte]) = {
    numArr.zipWithIndex.map { rst =>
      (rst._1 & 0xFF) << (8 * (numArr.length - rst._2 - 1))
    }.sum
  }

  def main(args: Array[String]): Unit = {
    val a = 111l
    val b = 222l
    val byteA = toByte(a,8)
    val st = bytes2Long(byteA)
    println(s"after:$st")
    val byteB = toByte(b,8)
    val r = bytes2Long(byteB)
    println(s"r:$r")
  }

}
