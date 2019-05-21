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

  val genBuffers = arbitrary[List[Array[Byte]]].map(xs => DynamoValue.fromByteBuffers(xs.map(ByteBuffer.wrap): _*))

  def fromSize(size: Int): Gen[DynamoValue] = size match {
    case 0 => genNull
    case 1 => Gen.oneOf(genNumber, genString, genBuffer)
    case n =>
      Gen.oneOf(
        Gen.listOf(fromSize(n - 1)).map(xs => DynamoValue.fromValues(xs: _*)),
        for { xs <- Gen.listOf(fromSize(n - 1)); ks <- Gen.listOfN(xs.size, Gen.alphaNumStr) } yield
          DynamoValue.fromFields((ks zip xs): _*)
      )
  }

  val genDV: Gen[DynamoValue] = Gen.sized(fromSize)

  implicit def dynamoValue: Arbitrary[DynamoValue] = Arbitrary(genDV)

}

private[scanamo] trait DynamoObjectInstances {
  implicit val arb: Arbitrary[DynamoValue]

  private val genEmpty: Gen[DynamoObject] = Gen.const(DynamoObject.empty)

  private val genLeaf: Gen[DynamoObject] = arbitrary[(String, DynamoValue)].map(DynamoObject.singleton.tupled)

  private def genNode(size: Int): Gen[DynamoObject] = ???

  private def genObject(size: Int) = size match {
    case 0 => genEmpty
    case 1 => genLeaf
    case n => genNode(n)
  }

  implicit val arbObject: Arbitrary[DynamoObject] = Arbitrary(Gen.sized(genObject))
}

private[scanamo] trait DynamoArrayInstances {
  implicit val arb: Arbitrary[DynamoValue]

  private val genEmpty: Gen[DynamoArray] = Gen.const(DynamoArray.empty)

  private val genL(n: Int): Gen[DynamoArray] = Gen.listOfN(n, arbitrary[DynamoValue]).map(xs => DynamoArray(xs: _*))

  private val genSS(n: Int): Gen[DynamoArray] = Gen.listOfN(n, arbitrary[String]).map(xs => DynamoArray.strings(xs: _*))

  private val genNS(n: Int): Gen[DynamoArray] =
    Gen.listOfN(n, arbitrary[BigDecimal]).map(xs => DynamoArray.numbers(xs: _*))

  private val genBS(n: Int): Gen[DynamoArray] =
    Gen.listOfN(n, arbitrary[Array[Byte]]).map(xs => DynamoArray.numbers(xs.map(ByteByffer.wrap): _*))

  def genArray(size: Int) = size match {
    case 0 => genEmpty
    case n => Gen.oneOf(genL(n), genValues[String], genValues[BigDecimal], genValues[ByteBuffer])
  }

  implicit val arbArray: Arbitrary[DynamoArray] = Arbitrary(Gen.sized(genArray))
}
