package com.gu.scanamo

import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import org.scalacheck._
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class DynamoFormatTest extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {

  // Test that an arbitrary DynamoFormat can be written to dynamo, and then read, producing the same result
  def testReadWrite[A: DynamoFormat: TypeTag](gen: Gen[A]): Unit = {
    val typeLabel = typeTag[A].tpe.toString
    it(s"should write and then read a $typeLabel from dynamo") {
      val client = LocalDynamoDB.client()
      LocalDynamoDB.usingRandomTable(client)('name -> S) { t =>
        final case class Person(name: String, item: A)
        forAll(gen) { a: A =>
          val person = Person("bob", a)
          client.putItem(t, DynamoFormat[Person].write(person).getM)
          val resp = client.getItem(t, Map("name" -> new AttributeValue().withS("bob")).asJava)
          DynamoFormat[Person].read(new AttributeValue().withM(resp.getItem)) shouldBe Right(person)
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
}
