import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Path, StandardOpenOption}

import scala.collection.mutable

/**
 * Type class which defines how to convert between values of type [[A]] and a byte sequence of fixed length.
 */
trait Format[A] {
  /**
   * The number of bytes used to represent a value of type [[A]] as a sequence of bytes.
   */
  def serializedSize: Int

  /**
   * Convert the specified value to a sequence of bytes, writing to the specified buffer.
   */
  def serialize(value: A, destination: ByteBuffer): Unit

  /**
   * Read a value from the specified buffer.
   */
  def deserialize(source: ByteBuffer): A
}

object Format {
  def apply[A : Format]: Format[A] = implicitly
}

/**
 * API for the [[Vector]]-like storage backing a hash table instance, but which contains only the operations necessary for that use case. Each element of the table is either a [[Table.Hole]] or a [[Table.Entry]] with a specific key and value.
 */
// TODO: Maybe parameterize with single type parameter.
trait Table[K, V] {
  /**
   * The number of element in the table.
   */
  def size: Int

  /**
   * Resize the table to contain the specified number of elements. New elements will be initialized to [[Table.Hole]].
   */
  def size_=(newSize: Int): Unit

  /**
   * Return the current element at the specified index.
   */
  def apply(index: Int): Table.Item[K, V]

  /**
   * Update the element at the specified index to contain the specified item.
   */
  def update(index: Int, item: Table.Item[K, V]): Unit
}

object Table {
  /**
   * Sum type for the elements of a [[Table]] instance.
   */
  sealed trait Item[+K, +V]
  case object Hole extends Item[Nothing, Nothing]
  case class Entry[K, V](key: K, value: V) extends Item[K, V]

  object Item {
    /**
     * Construct a [[Format]] instance for an [[Item]] type from the individual [[Format]] instances of the key and value types.
     */
    implicit def format[K: Format, V: Format]: Format[Item[K, V]] = new Format[Item[K, V]] {
      override def serializedSize =
        Format[K].serializedSize + Format[V].serializedSize

      override def serialize(value: Item[K, V], destination: ByteBuffer) =
        value match {
          case Hole =>
            // Fill the whole item with zeros to denote a hole.
            destination.put(ByteBuffer.allocate(destination.remaining()))
          case Entry(k, v) =>
            Format[K].serialize(k, destination)
            destination.position(Format[K].serializedSize)
            Format[V].serialize(v, destination)
        }

      override def deserialize(source: ByteBuffer) =
        // Check whether we have a hole by checking that the buffer only contains zero bytes.
        if (source.mismatch(ByteBuffer.allocate(source.remaining())) < 0) {
          Hole
        } else {
          val key = Format[K].deserialize(source)
          source.position(Format[K].serializedSize)
          val value = Format[V].deserialize(source)

          Entry(key, value)
        }
    }
  }
}

/**
 * Implementation of [[Table]] which stores the element in memory.
 */
class MemoryBackedTable[K, V] extends Table[K, V] {
  private val backingBuffer = mutable.ArrayBuffer[Table.Item[K, V]]()

  override def size =
    backingBuffer.size

  override def size_=(newSize: Int) = {
    // We don't need to be able to shrink the buffer.
    require(newSize >= size, s"newSize: $newSize, size: $size")

    backingBuffer ++= LazyList.fill(newSize - backingBuffer.size)(Table.Hole)
  }

  override def apply(index: Int) =
    backingBuffer(index)

  override def update(index: Int, item: Table.Item[K, V]) =
    backingBuffer(index) = item
}

/**
 * Implementation of [[Table]] which stores the element in a file.
 *
 * The [[Format]] instances for [[K]] and [[V]] are used to serialize and de-serialize the entries.
 *
 * One caveat applies for [[K]]'s [[Format]] instance. The byte sequence of all zeros in place of the key is used to denote a hole instead of an entry. The [[Format]] instance for [[K]] therefore should never serialize an element to a sequence of all zeros.
 */
class FileBackedTable[K, V](path: Path)(implicit itemFormat: Format[Table.Item[K, V]]) extends Table[K, V] {
  import Table._

  private val channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)

  private val itemSize = Format[Item[K, V]].serializedSize

  override def size = (channel.size() / itemSize).toInt

  override def size_=(newSize: Int) = {
    require(newSize >= size)

    // FileChannel.truncate() does not work to make the file larger. Writing a hole to the last element does the trick.
    if (newSize > size)
      this(newSize - 1) = Hole
  }

  override def apply(index: Int) = {
    val buffer = ByteBuffer.allocate(itemSize)

    // Read the complete entry from the file into the buffer.
    channel.read(buffer, index * itemSize)
    buffer.flip()
    Format[Item[K, V]].deserialize(buffer)
  }

  override def update(index: Int, item: Item[K, V]) = {
    val buffer = ByteBuffer.allocate(itemSize)

    Format[Item[K, V]].serialize(item, buffer)
    buffer.flip()
    channel.write(buffer, index * itemSize)
  }
}
