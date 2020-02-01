import java.lang.Integer.{highestOneBit, numberOfTrailingZeros}
import java.lang.Math.max

import scala.collection.mutable

/**
 * Type class which defines how to calculate a hash code for values of type [[A]].
 */
trait Hash[A] {
  /**
   * Calculate a hash code for the specified value.
   */
  def hash(value: A): Int
}

object Hash {
  def hash[A : Hash](value: A) = implicitly[Hash[A]].hash(value)
}

/**
 * A type used for mapping keys to values, like Scala's [[mutable.Map]] but with an as simple as possible API.
 */
trait EasyMap[K, V] {
  /**
   * Return the value associated with the specified key, if any.
   */
  def get(key: K): Option[V]

  /**
   * Return a sequence of all entries currently in the map.
   */
  def items: Seq[(K, V)]

  /**
   * Associate the specified value with the specified key,
   */
  def set(key: K, value: V): Unit

  /**
   * Remove the entry for the specified key, if any.
   */
  def remove(key: K): Unit
}

/**
 * Implementation of [[EasyMap]] which is backed by Scala's [[mutable.Map]].
 */
class ScalaEasyMap[K, V] extends EasyMap[K, V] {
  private val map = mutable.Map[K, V]()
  override def get(key: K) = map.get(key)
  override def items = map.toSeq
  override def set(key: K, value: V) = map += key -> value
  override def remove(key: K) = map -= key
}

/**
 * An implementation of the [[EasyMap]] interface using a robin hood style hash table.
 */
class RobinHoodEasyMap[K : Hash, V](table: Table[K, V]) extends EasyMap[K, V] {
  import Table._

  /**
   * Value from which the table size and other parameters are derived. Initially deduced from the size of the table passed to the instance.
   */
  private var shift = numberOfTrailingZeros(highestOneBit(max(1, table.size)))
  private def mask = (1 << shift) - 1
  private def maxDisplacement = shift

  // Initially set up the empty table.
  resizeTable()

  private def idealPosition(key: K) = Hash.hash(key) & mask

  /**
   * Grow the table and move elements as necessary to make it conform to the current value set for [[shift]].
   */
  private def resizeTable() = {
    val oldSize = table.size

    // Make the table the proper size.
    table.size = mask + 1 + maxDisplacement

    // Move entries which are now in the wrong position. These are entries whose ideal position is after their current position, which never happens without changing the table size.
    @scala.annotation.tailrec
    def moveAt(pos: Int): Unit =
      if (pos >= 0) {
        table(pos) match {
          case Entry(k, v) if idealPosition(k) > pos =>
            removeAt(pos)
            set(k, v)
          case _ =>
        }

        moveAt(pos - 1)
      }

    // We have to process items in reverse order because an entry which needs to be moved and whose ideal position was at the end of the old table size might get moved outside the old table size while moving items and never get moved.
    moveAt(oldSize - 1)
  }

  @scala.annotation.tailrec
  private def insertAt(key: K, value: V, ipos: Int, pos: Int): Unit = {
    if (pos - ipos > maxDisplacement) {
      // The current element des not fit into the table without violating maxDisplacement. We need to grow the table.
      shift += 1
      resizeTable()

      // Retry to insert the entry after resizing.
      set(key, value)
    } else {
      table(pos) match {
        case Hole | Entry(`key`, _) =>
          table(pos) = Entry(key, value)
        case Entry(k, v) =>
          val i = idealPosition(k)

          if (i > ipos) {
            table(pos) = Entry(key, value)
            insertAt(k, v, i, pos + 1)
          } else {
            insertAt(key, value, ipos, pos + 1)
          }
      }
    }
  }

  private def insert(key: K, value: V, ipos: Int) =
    insertAt(key, value, ipos, ipos)

  @scala.annotation.tailrec
  private def findAt(key: K, ipos: Int, pos: Int): Option[(Int, V)] =
    if (pos - ipos > maxDisplacement)
      None
    else
      table(pos) match {
        case Hole =>
          None
        case Entry(`key`, v) =>
          Some(pos, v)
        case Entry(k, _) if idealPosition(k) > ipos =>
          None
        case _ =>
          findAt(key, ipos, pos + 1)
      }

  /**
   * Search for an entry with the specified key and its ideal position.
   *
   * Returns the position and the value currently at that position or `None`, if the position is more than `maxDistance` after the ideal position of an entry with this key.
   */
  private def find(key: K, ipos: Int) =
    findAt(key, ipos, ipos)

  /**
   * Remove the element at the specified position and shift the following elements backwards as necessary.
   */
  @scala.annotation.tailrec
  private def removeAt(pos: Int): Unit =
    // The last position in the table can be treated as if it was followed by a hole.
    (if (pos + 1 < table.size) table(pos + 1) else Hole) match {
      case Entry(k, v) if idealPosition(k) < pos + 1 =>
        table(pos) = Entry(k, v)
        removeAt(pos + 1)
      case _ =>
        table(pos) = Hole
    }

  override def get(key: K) =
    find(key, idealPosition(key)).map({ case (_, value) => value })

  override def items =
    (0 until table.size).map(table(_)).collect({ case Entry(k, v) => (k, v) })

  override def set(key: K, value: V) =
    insert(key, value, idealPosition(key))

  override def remove(key: K) =
    find(key, idealPosition(key)).foreach({ case (pos, _) => removeAt(pos) })

  def debugPrintTable() = {
    println(f"size: ${table.size}, shift: $shift")

    (0 until table.size).foreach({ i =>
      def elementStr =
        table(i) match {
          case Hole => "-"
          case Entry(k, v) => s"(${idealPosition(k)}) $k -> $v"
        }

      println(f"$i%3s: $elementStr")
    })
  }
}