import com.gu.scanamo.DynamoFormat

val a = Array("hi", "bye")
val ldf = DynamoFormat[Array[String]].write(a)
val ldfInv = DynamoFormat[Array[String]].read(ldf)

val v = Vector("hi", "bye")
val vdf = DynamoFormat[Vector[String]].write(v)
val vdfInv = DynamoFormat[Vector[String]].read(vdf)


val c = ldfInv.right.getOrElse(Array("boo"))

val b: Array[String] = ldfInv match {
  case Right(a) => a
  case Left(m) => Array("boo")
}

a == b
a.deep == b.deep



Right(a)

