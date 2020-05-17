package org.scanamo

import scala.reflect.runtime.universe._

import software.amazon.awssdk.services.dynamodb.model._
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._
import org.scalacheck._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scanamo.generic.auto._

class DynamoFormatTest extends AnyFunSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  // Test that an arbitrary DynamoFormat can be written to dynamo, and then read, producing the same result
  def testReadWrite[A: DynamoFormat: TypeTag](gen: Gen[A]): Unit = {
    val typeLabel = typeTag[A].tpe.toString
    it(s"should write and then read a $typeLabel from dynamo") {
      val client = LocalDynamoDB.client()
      LocalDynamoDB.usingRandomTable(client)("name" -> S) { t =>
        final case class Person(name: String, item: A)
        val format = DynamoFormat[Person]
        forAll(gen) { a: A =>
          val person = Person("bob", a)
          client.putItem(PutItemRequest.builder.tableName(t).item(format.write(person).toAttributeValue.m).build).get
          val resp =
            client.getItem(GetItemRequest.builder.tableName(t).key(DynamoObject("name" -> "bob").toJavaMap).build).get
          format.read(DynamoObject(resp.item).toDynamoValue) shouldBe Right(person)
        }
      }
    }
  }

  def testReadWrite[A: DynamoFormat: TypeTag]()(implicit arb: Arbitrary[A]): Unit =
    testReadWrite(arb.arbitrary)

  testReadWrite[Set[Int]]()
  testReadWrite[Set[Long]]()
  // Generate limited values for double and big decimal
  // see: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.NamingRulesDataTypes.html#HowItWorks.DataTypes.Number
  testReadWrite[Set[Double]](Gen.containerOf[Set, Double](Arbitrary.arbLong.arbitrary.map(_.toDouble)))
  testReadWrite[Set[BigDecimal]](
    Gen.containerOf[Set, BigDecimal](Arbitrary.arbLong.arbitrary.map(BigDecimal(_)))
  )
  val nonEmptyStringGen: Gen[String] =
    Gen.nonEmptyContainerOf[Array, Char](Arbitrary.arbChar.arbitrary).map(arr => new String(arr))
  testReadWrite[Set[String]](Gen.containerOf[Set, String](nonEmptyStringGen))
  testReadWrite[Option[String]](Gen.option(nonEmptyStringGen))
  testReadWrite[Option[Int]]()
  testReadWrite[Map[String, Long]](Gen.mapOf[String, Long] {
    for {
      key <- nonEmptyStringGen
      value <- Arbitrary.arbitrary[Long]
    } yield key -> value
  })
  testReadWrite[List[String]](Gen.listOf(nonEmptyStringGen))
  testReadWrite[List[Int]](Gen.listOfN(0, Gen.posNum[Int]))
}
