package org.scanamo.Derivation

import com.amazonaws.services.dynamodbv2.model.AttributeValue
import org.scalatest.{FunSuite, Matchers}
import org.scanamo.DynamoFormat

class SemiAutoDerivationTest extends FunSuite with Matchers {

  test("Derivation should fail if no derived format or automatic derivation") {
    """write(Person("Alice", 65))""" shouldNot compile
  }

  test("Derivation should succeed if derived format in scope") {
    """
      |import org.scanamo.semiauto._
      |implicit val formatPerson: DynamoFormat[Person] = deriveDynamoFormat[Person]
      |
      |write(Person("Bob", 12))
      |""".stripMargin should compile
  }

  def write[T](t: T)(implicit f: DynamoFormat[T, AttributeValue]) = f.write(t)
}

case class Person(name: String, age: Int)
