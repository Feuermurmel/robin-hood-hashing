import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.security.MessageDigest

import scala.util.Random

object Main {
  def sha256(data: Array[Byte]) =
    MessageDigest.getInstance("SHA-256").digest(data)

  // Very predictable hash function, useful for diagnosis.
  implicit val intHash: Hash[Int] = identity

  // Well-distributed hash function, useful for random testing.
  implicit val stringHash: Hash[String] =
    a => ByteBuffer.wrap(sha256(a.getBytes(UTF_8))).getInt

  implicit val intFormat: Format[Int] = new Format[Int] {
    override def serializedSize = 4
    override def serialize(value: Int, destination: ByteBuffer) = destination.putInt(value)
    override def deserialize(source: ByteBuffer) = source.getInt()
  }

  def stressTestInsert(): Unit = {
    // The hash table to test.
    val t = new RobinHoodEasyMap[String, Int](new MemoryBackedTable)

    // The map which is used to check the results of the tested implementation.
    val t2 = new ScalaEasyMap[String, Int]()

    val random = new Random(0)
    val keys = ('a' to 'z').flatMap(i => ('a' to 'z').map(j => s"$i$j"))

    // Insert or remove an entry using a randomly selected key 100000 times. And check the contents of the map every 100 iterations.
    (1 to 100000).foreach({ i =>
      val k = keys(random.nextInt(keys.size))

      if (t2.get(k).isDefined) {
        t.remove(k)
        t2.remove(k)
      } else {
        t.set(k, i)
        t2.set(k, i)
      }

      if (i % 100 == 0) {
        keys.foreach({ k =>
          require(t.get(k) == t2.get(k), s"t($k) == ${t.get(k)}, t2($k) == ${t2.get(k)}")
        })
      }
    })
  }

  def interactiveConsole(): Unit = {
    def makeTable() =
      new RobinHoodEasyMap[Int, Int](new FileBackedTable(Paths.get("file")))

    var t = makeTable()

    t.debugPrintTable()

    @scala.annotation.tailrec
    def processCommand(): Unit = {
      print("? ")
      val line = scala.io.StdIn.readLine()

      "\\s".r.split(line) match {
        case Array("s", key, value) =>
          t.set(key.toInt, value.toInt)
          t.debugPrintTable()
        case Array("r", key) =>
          t.remove(key.toInt)
          t.debugPrintTable()
        case Array("g", key) =>
          println(s"${t.get(key.toInt)}")
        case Array("rebuild") =>
          val tNew = makeTable()
          t.items.foreach({ case (k, v) => tNew.set(k, v) })
          t = tNew
          t.debugPrintTable()
        case Array() =>
        case Array("q") =>
          return
        case _ =>
          println("???")
      }

      processCommand()
    }

    processCommand()
  }

  def main(args: Array[String]): Unit = {
//    stressTestInsert()
    interactiveConsole()
  }
}
