package org.scanamo

import java.nio.ByteBuffer

trait IsSimpleKey[A]

object IsSimpleKey {
  implicit case object BooleanSimpleKey extends IsSimpleKey[Boolean]
  implicit case object StringSimpleKey extends IsSimpleKey[String]
  implicit case object ByteSimpleKey extends IsSimpleKey[Byte]
  implicit case object ShortSimpleKey extends IsSimpleKey[Short]
  implicit case object IntSimpleKey extends IsSimpleKey[Int]
  implicit case object LongSimpleKey extends IsSimpleKey[Long]
  implicit case object FloatSimpleKey extends IsSimpleKey[Float]
  implicit case object DoubleSimpleKey extends IsSimpleKey[Double]
  implicit case object ByteBufferSimpleKey extends IsSimpleKey[ByteBuffer]
  implicit case object IntListSimpleKey extends IsSimpleKey[List[Int]]
  implicit case object StringListSimpleKey extends IsSimpleKey[List[String]]
  implicit case object ByteBufferListSimpleKey extends IsSimpleKey[List[ByteBuffer]]
}
