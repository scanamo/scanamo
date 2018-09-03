package com.gu.scanamo

import scala.collection.JavaConverters._

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import org.scalacheck._
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class DynamoFormatTest extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {

  // Test that an arbitrary DynamoFormat can be written to dynamo, and then read, producing the same result
  def testReadWrite[A: DynamoFormat](label: String, gen: Gen[A]): Unit = {
    it(s"should write and then read a $label from dynamo") {
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

  def testReadWrite[A: DynamoFormat](label: String)(implicit arb: Arbitrary[A]): Unit =
    testReadWrite(label, arb.arbitrary)

  testReadWrite[Option[String]]("Option[String]")
  testReadWrite[Option[Int]]("Option[Int]")
}
