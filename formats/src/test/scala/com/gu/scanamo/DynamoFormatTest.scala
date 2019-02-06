package org.scanamo

import scala.collection.JavaConverters._
import scala.reflect.runtime.universe._
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import org.scalacheck._
import org.scalatest._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scanamo.Foo.DynamoFormatV1
import org.scanamo.auto._

class DynamoFormatTest extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {

  val localDb = LocalDynamoDB.v1
  val client = localDb.clientSync()

  // Test that an arbitrary DynamoFormat can be written to dynamo, and then read, producing the same result
  def testReadWrite[A: DynamoFormatV1: TypeTag](gen: Gen[A]): Unit = {
    val typeLabel = typeTag[A].tpe.toString
    it(s"should write and then read a $typeLabel from dynamo") {
      localDb.usingRandomTable(client)('name -> S) { t =>
        final case class Person(name: String, item: A)
        forAll(gen) { a: A =>
          val person = Person("bob", a)

          client.putItem(t, DynamoFormatV1[Person].write(person).getM)
          val resp = client.getItem(t, Map("name" -> new AttributeValue().withS("bob")).asJava)
          DynamoFormatV1[Person].read(new AttributeValue().withM(resp.getItem)) shouldBe Right(person)
        }
      }
    }
  }

  def testReadWrite[A: DynamoFormatV1: TypeTag]()(implicit arb: Arbitrary[A]): Unit =
    testReadWrite(arb.arbitrary)

  import DynamoFormatV1._

  testReadWrite[List[String]]()
  testReadWrite[List[Long]]()
  // Generate limited values for double and big decimal
  // see: https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.NamingRulesDataTypes.html#HowItWorks.DataTypes.Number
  testReadWrite[List[Double]](Gen.containerOf[List, Double](Arbitrary.arbLong.arbitrary.map(_.toDouble)))
  testReadWrite[List[BigDecimal]](
    Gen.containerOf[List, BigDecimal](Arbitrary.arbLong.arbitrary.map(BigDecimal(_)))
  )
  val nonEmptyStringGen: Gen[String] =
    Gen.nonEmptyContainerOf[Array, Char](Arbitrary.arbChar.arbitrary).map(arr => new String(arr))
//  testReadWrite[Set[String]](Gen.containerOf[Set, String](nonEmptyStringGen))

//  testReadWrite[Set[Int]](Gen.containerOf[Set, Int](???))

  testReadWrite[Option[String]](Gen.option(nonEmptyStringGen))
  testReadWrite[Option[String]](Gen.const(Some("ssss")))
    testReadWrite[Option[Int]]()
}
