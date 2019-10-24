package org.scanamo

import java.nio.ByteBuffer
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.forAll

class DynamoObjectTest extends Properties("DynamoValue") with DynamoValueInstances {

  ////
  // Monoid laws

  property("left identity") = forAll { (v: DynamoObject) =>
    DynamoObject.empty <> v == v
  }

  property("right identity") = forAll { (v: DynamoObject) =>
    v <> DynamoObject.empty == v
  }

  property("associativity") = forAll { (x: DynamoObject, y: DynamoObject, z: DynamoObject) =>
    (x <> y) <> z == x <> (y <> z)
  }

}

private[scanamo] trait DynamoValueInstances extends DynamoObjectInstances with DynamoArrayInstances {
  private def genN[N](implicit N: Numeric[N]): Gen[N] = arbitrary[Int].map(N.fromInt)

  val genNull = Gen.const(DynamoValue.nil)

  val genNumber = genN[BigDecimal].map(DynamoValue.fromNumber[BigDecimal])

  val genString = arbitrary[String].map(DynamoValue.fromString)

  val genBuffer = arbitrary[Array[Byte]].map(xs => DynamoValue.fromByteBuffer(ByteBuffer.wrap(xs)))

  val genArray = arbitrary[DynamoArray].map(DynamoValue.fromDynamoArray)

  val genMap = arbitrary[DynamoObject].map(DynamoValue.fromDynamoObject)

  val genNumbers = Gen.listOf(genN[BigDecimal]).map(DynamoValue.fromNumbers[BigDecimal])

  val genStrings = arbitrary[List[String]].map(DynamoValue.fromStrings)

  val genBuffers = arbitrary[List[Array[Byte]]].map(xs => DynamoValue.fromByteBuffers(xs.map(ByteBuffer.wrap)))

  def fromSize(size: Int): Gen[DynamoValue] = size match {
    case 0 => genNull
    case 1 => Gen.oneOf(genNumber, genString, genBuffer)
    case n =>
      Gen.oneOf(
        Gen.listOf(fromSize(n - 1)).map(xs => DynamoValue.fromValues(xs)),
        for { xs <- Gen.listOf(fromSize(n - 1)); ks <- Gen.listOfN(xs.size, Gen.alphaNumStr) } yield DynamoValue
          .fromFields((ks zip xs): _*)
      )
  }

  val genDV: Gen[DynamoValue] = Gen.sized(fromSize)

  implicit val arbDynamoValue: Arbitrary[DynamoValue] = Arbitrary(genDV)

}

private[scanamo] trait DynamoObjectInstances {
  implicit def arbDynamoValue: Arbitrary[DynamoValue]

  private val genEmpty: Gen[DynamoObject] = Gen.const(DynamoObject.empty)

  private val genLeaf: Gen[DynamoObject] = arbitrary[(String, Int)].map {
    case (k, v) => DynamoObject.singleton(k, DynamoValue.fromNumber(v))
  }

  private def genNode(size: Int): Gen[DynamoObject] =
    for {
      m <- Gen.choose(0, size / 2)
      xs <- Gen
        .containerOfN[List, (String, Int)](m, arbitrary[(String, Int)])
        .map(xs => DynamoObject(xs.toMap.mapValues(DynamoValue.fromNumber[Int]).toMap))
    } yield xs

  private def genObject(size: Int) = size match {
    case 0 => genEmpty
    case 1 => genLeaf
    case n => genNode(n)
  }

  implicit val arbObject: Arbitrary[DynamoObject] = Arbitrary(Gen.sized(genObject))
}

private[scanamo] trait DynamoArrayInstances {
  implicit def arbDynamoValue: Arbitrary[DynamoValue]

  private val genEmpty: Gen[DynamoArray] = Gen.const(DynamoArray.empty)

  private def genL(n: Int): Gen[DynamoArray] = Gen.listOfN(n, arbitrary[DynamoValue]).map(DynamoArray(_))

  private def genSS(n: Int): Gen[DynamoArray] = Gen.listOfN(n, arbitrary[String]).map(DynamoArray.strings(_))

  private def genNS(n: Int): Gen[DynamoArray] =
    Gen.listOfN(n, arbitrary[BigDecimal]).map(DynamoArray.numbers(_))

  private def genBS(n: Int): Gen[DynamoArray] =
    Gen.listOfN(n, arbitrary[Array[Byte]]).map(xs => DynamoArray.byteBuffers(xs.map(ByteBuffer.wrap)))

  def genArray(size: Int) = size match {
    case 0 => genEmpty
    case n => Gen.oneOf(genL(n), genSS(n), genNS(n), genBS(n))
  }

  implicit val arbArray: Arbitrary[DynamoArray] = Arbitrary(Gen.sized(genArray))
}
