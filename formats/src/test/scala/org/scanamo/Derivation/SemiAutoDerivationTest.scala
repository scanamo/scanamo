package org.scanamo.Derivation

import org.scanamo.DynamoFormat
import org.scalatest.{ FunSuite, Matchers }

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

  def write[T](t: T)(implicit f: DynamoFormat[T]) = f.write(t)
}

case class Person(name: String, age: Int)
