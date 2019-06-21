package org.scanamo

import java.nio.ByteBuffer

trait SimpleKey[A]

object SimpleKey {
  implicit case object BooleanSimpleKey extends SimpleKey[Boolean]
  implicit case object StringSimpleKey extends SimpleKey[String]
  implicit case object ByteSimpleKey extends SimpleKey[Byte]
  implicit case object ShortSimpleKey extends SimpleKey[Short]
  implicit case object IntSimpleKey extends SimpleKey[Int]
  implicit case object LongSimpleKey extends SimpleKey[Long]
  implicit case object FloatSimpleKey extends SimpleKey[Float]
  implicit case object DoubleSimpleKey extends SimpleKey[Double]
  implicit case object ByteBufferSimpleKey extends SimpleKey[ByteBuffer]
  implicit case object IntListSimpleKey extends SimpleKey[List[Int]]
  implicit case object StringListSimpleKey extends SimpleKey[List[String]]
  implicit case object ByteBufferListSimpleKey extends SimpleKey[List[ByteBuffer]]
}
